package ar.tresmodos.shooter;

import ar.tresmodos.Armas;
import ar.tresmodos.Claves;
import ar.tresmodos.DatosJugador;
import ar.tresmodos.Hud;
import ar.tresmodos.Modo;
import ar.tresmodos.ModoJuego;
import ar.tresmodos.TresModos;
import ar.tresmodos.Util;
import ar.tresmodos.mundo.Plano;
import ar.tresmodos.mundo.PuebloAtomico;
import ar.tresmodos.shooter.ClasesShooter.Ventaja;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Shooter "Pueblo Atómico": todos contra todos estilo Modern Warfare. Meta de bajas o 15 minutos,
 * 5 s de espectador al morir, reaparición al azar, rachas, clases personalizadas y bomba atómica.
 */
public class ModoShooter implements ModoJuego, Listener {
    public static final int DURACION = 20 * 60 * 15;
    private static final int ESPECTADOR = 100;
    private static final int PODIO = 300;
    private static final int PROTECCION = 40;

    enum Estado { ESPERANDO, EN_CURSO, PODIO }

    static final class Stats {
        int bajas, muertes, racha, maniquies;
    }

    private final TresModos plugin;
    private final Random rnd = new Random();
    private final ClasesShooter clases;
    private final Equipamiento equipamiento;
    private final Rachas rachas;
    private final Minimapa minimapa;

    private final List<int[]> puntos = new ArrayList<>();
    private final Map<UUID, Stats> stats = new HashMap<>();
    private final Map<UUID, Integer> proteccion = new HashMap<>();
    private final Map<UUID, Integer> ultimoDanio = new HashMap<>();
    private final Map<UUID, Integer> espectando = new HashMap<>();
    private final Map<UUID, UUID> asesinoDe = new HashMap<>();
    private final Map<UUID, Location> lugarMuerte = new HashMap<>();
    private final Set<UUID> enRacha = new HashSet<>();
    private final Map<UUID, Integer> ultimoCuchillo = new HashMap<>();
    private final Set<UUID> maniquies = new HashSet<>();
    private final List<int[]> quemados = new ArrayList<>();

    private Estado estado = Estado.ESPERANDO;
    private int inicio;
    private int vacioDesde = -1;
    private int bajasPartida;
    private boolean bombaEnCurso;
    private UUID contador;

    public ModoShooter(TresModos plugin) {
        this.plugin = plugin;
        this.clases = new ClasesShooter(plugin, this);
        this.equipamiento = new Equipamiento(plugin, this);
        this.rachas = new Rachas(plugin, this);
        this.minimapa = new Minimapa(plugin, this);
        var pm = plugin.getServer().getPluginManager();
        pm.registerEvents(equipamiento, plugin);
        pm.registerEvents(rachas, plugin);
        calcularPuntos();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5, 5);
        Bukkit.getScheduler().runTask(plugin, this::prepararMundo);
    }

    // ------------------------------------------------------------------ acceso

    public World mundo() {
        return plugin.mundos().de(Modo.SHOOTER);
    }

    private boolean enShooter(Entity e) {
        return Modo.de(e.getWorld()) == Modo.SHOOTER;
    }

    public List<Player> jugadores() {
        return mundo().getPlayers();
    }

    public ClasesShooter clases() { return clases; }
    public Equipamiento equipamiento() { return equipamiento; }
    public Rachas rachas() { return rachas; }
    public Minimapa minimapa() { return minimapa; }

    private Stats stats(Player p) {
        return stats.computeIfAbsent(p.getUniqueId(), u -> new Stats());
    }

    /** Meta de bajas: 20 con hasta 4 jugadores, 30 con 5 o más. */
    public int meta() {
        return jugadores().size() >= 5 ? 30 : 20;
    }

    void anunciar(String s) {
        for (Player o : jugadores()) Util.msg(o, s);
    }

    // ------------------------------------------------------------------ ModoJuego

    @Override public Modo modo() { return Modo.SHOOTER; }
    @Override public boolean guardaEstado() { return false; }
    @Override public GameMode modoJuego() { return GameMode.ADVENTURE; }

    @Override
    public Location ubicacionEntrada(Player p) {
        return estado == Estado.PODIO ? lugarTribuna() : elegirAparicion(p);
    }

    @Override
    public Location respawn(Player p) {
        Location l = lugarMuerte.remove(p.getUniqueId());
        return l != null ? l.add(0, 1.5, 0) : elegirAparicion(p);
    }

    @Override
    public void kitInicial(Player p) {
        if (estado != Estado.PODIO) clases.darEquipo(p);
    }

    @Override
    public void alEntrar(Player p, boolean primeraVez) {
        stats(p);
        vacioDesde = -1;
        if (estado == Estado.ESPERANDO) {
            nuevaPartida();
            return;
        }
        if (estado == Estado.PODIO) {
            p.getInventory().clear();
            return;
        }
        proteger(p, PROTECCION + 20);
        Util.titulo(p, "<red><bold>PUEBLO ATÓMICO", "<gray>Todos contra todos · primero a " + meta() + " bajas", 200, 2200, 500);
        Util.msg(p, "<gray>Clase: <aqua>" + ClasesShooter.NOMBRES[plugin.almacen().de(p).shooterClase]
                + "<gray> (libro o <white>/clase<gray>). <white>Q<gray> recarga · <white>F<gray> cuchillazo · "
                + "<white>Shift corriendo<gray> desliza · <white>doble Shift<gray> cuerpo a tierra.");
        for (Player o : jugadores()) if (o != p) Util.msg(o, "<gray>" + p.getName() + " llegó al pueblo.");
    }

    @Override
    public void alSalir(Player p) {
        UUID id = p.getUniqueId();
        plugin.armas().dejarDeApuntar(p);
        proteccion.remove(id);
        espectando.remove(id);
        asesinoDe.remove(id);
        lugarMuerte.remove(id);
        enRacha.remove(id);
        equipamiento.olvidar(id);
        rachas.olvidar(id);
        minimapa.olvidar(id);
        plugin.movilidad().olvidar(p);
        if (p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(null);
    }

    @Override
    public void alReaparecer(Player p) {
        if (estado == Estado.PODIO) {
            p.setGameMode(GameMode.ADVENTURE);
            p.teleport(lugarTribuna());
            p.getInventory().clear();
            return;
        }
        if (estado != Estado.EN_CURSO) {
            reaparecerEnMapa(p);
            return;
        }
        p.getInventory().clear();
        p.setGameMode(GameMode.SPECTATOR);
        UUID asesino = asesinoDe.remove(p.getUniqueId());
        Player a = asesino == null ? null : Bukkit.getPlayer(asesino);
        if (a != null && enShooter(a) && !a.isDead() && a.getGameMode() == GameMode.ADVENTURE) {
            p.setSpectatorTarget(a);
        }
        espectando.put(p.getUniqueId(), Bukkit.getCurrentTick() + ESPECTADOR);
        Util.titulo(p, "<red><bold>CAÍSTE", "<gray>Reaparecés en 5 s · Shift: cámara libre", 0, 1500, 300);
    }

    private void reaparecerEnMapa(Player p) {
        espectando.remove(p.getUniqueId());
        if (p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(null);
        p.setGameMode(GameMode.ADVENTURE);
        p.teleport(elegirAparicion(p));
        p.setFireTicks(0);
        p.setFallDistance(0);
        for (PotionEffect ef : p.getActivePotionEffects()) p.removePotionEffect(ef.getType());
        clases.darEquipo(p);
        proteger(p, PROTECCION);
        plugin.sidebar().actualizar(p);
    }

    /** Durante una racha que saca al jugador del cuerpo (Predator): no cuenta como vivo. */
    void entrarEnRacha(Player p) {
        enRacha.add(p.getUniqueId());
        plugin.armas().dejarDeApuntar(p);
    }

    void salirDeRacha(Player p, Location volver) {
        enRacha.remove(p.getUniqueId());
        if (!p.isOnline() || Modo.de(p.getWorld()) != Modo.SHOOTER) return;
        if (espectando.containsKey(p.getUniqueId())) return;
        p.setGameMode(GameMode.ADVENTURE);
        if (volver != null && volver.getWorld() == mundo()) p.teleport(volver);
    }

    /** true si se puede cambiar de clase en el acto (recién aparecido y protegido). */
    boolean puedeCambiarYa(Player p) {
        return protegido(p) && enShooter(p) && p.getGameMode() == GameMode.ADVENTURE;
    }

    // ------------------------------------------------------------------ aparición

    /** Precalcula los lugares donde se puede parar un jugador (calle, patios, interiores, vehículos). */
    private void calcularPuntos() {
        Plano plano = PuebloAtomico.plano();
        plano.paraCadaPiso((x, y, z, d) -> {
            if (!PuebloAtomico.enZonaJugable(x, z) || y > PuebloAtomico.Y_MAX_APARICION) return;
            BlockData piso = plano.get(x, y - 1, z);
            if (piso == null || !piso.getMaterial().isSolid()) return;
            if (!libre(plano.get(x, y, z)) || !libre(plano.get(x, y + 1, z))) return;
            if (Math.abs(x) >= PuebloAtomico.X_MAX || Math.abs(z) >= PuebloAtomico.Z_MAX) return;
            puntos.add(new int[]{x, y, z});
        });
        plugin.getLogger().info("Pueblo Atómico: " + puntos.size() + " lugares de aparición.");
    }

    private static boolean libre(BlockData d) {
        if (d == null) return true;
        Material m = d.getMaterial();
        return !m.isSolid() || m.name().endsWith("_CARPET");
    }

    /**
     * Un lugar al azar del mapa. Si está activa la regla, nunca a menos de 8 bloques de un enemigo
     * que lo vea; si no encuentra uno así en 50 intentos, el más lejano de los enemigos.
     */
    Location elegirAparicion(Player p) {
        World w = mundo();
        if (puntos.isEmpty()) return w.getSpawnLocation();
        boolean regla = plugin.getConfig().getBoolean("shooter.reaparicion-segura", true);
        List<Player> enemigos = new ArrayList<>();
        for (Player o : w.getPlayers()) {
            if (o != p && !o.isDead() && o.getGameMode() == GameMode.ADVENTURE) enemigos.add(o);
        }
        Location mejor = null;
        double mejorDist = -1;
        for (int i = 0; i < 50; i++) {
            int[] c = puntos.get(rnd.nextInt(puntos.size()));
            Location l = new Location(w, c[0] + 0.5, c[1], c[2] + 0.5);
            double min = Double.MAX_VALUE;
            boolean seguro = true;
            for (Player e : enemigos) {
                double d = e.getLocation().distance(l);
                min = Math.min(min, d);
                if (d < 3 || (regla && d < 8 && ve(e, l))) seguro = false;
            }
            if (seguro) return orientar(l);
            if (min > mejorDist) {
                mejorDist = min;
                mejor = l;
            }
        }
        return orientar(mejor);
    }

    private static boolean ve(Player e, Location l) {
        Location ojo = e.getEyeLocation();
        Vector hacia = l.clone().add(0, 1.6, 0).toVector().subtract(ojo.toVector());
        double d = hacia.length();
        if (d < 0.1) return true;
        return ojo.getWorld().rayTraceBlocks(ojo, hacia.normalize(), d, FluidCollisionMode.NEVER, true) == null;
    }

    /** Mirando hacia el centro de la calle, que es donde está la acción. */
    private static Location orientar(Location l) {
        Vector hacia = new Vector(0, l.getY(), 0).subtract(l.toVector()).setY(0);
        if (hacia.lengthSquared() > 0.01) l.setDirection(hacia);
        l.setPitch(0);
        return l;
    }

    private Location lugarTribuna() {
        Location l = new Location(mundo(), 17.5 + rnd.nextInt(3) - 1, PuebloAtomico.SUELO + 1, 0.5 + rnd.nextInt(7) - 3);
        l.setYaw(270);
        return l;
    }

    private void proteger(Player p, int ticks) {
        proteccion.put(p.getUniqueId(), Bukkit.getCurrentTick() + ticks);
    }

    private boolean protegido(Player p) {
        Integer t = proteccion.get(p.getUniqueId());
        return t != null && Bukkit.getCurrentTick() < t;
    }

    /** Las armas no disparan fuera de la partida, como espectador ni en sprint táctico. Disparar quita la protección. */
    public boolean bloqueaDisparo(Player p) {
        if (!enShooter(p)) return false;
        if (estado != Estado.EN_CURSO || bombaEnCurso || p.getGameMode() != GameMode.ADVENTURE) return true;
        if (plugin.movilidad().sprintTactico(p)) return true;
        proteccion.remove(p.getUniqueId());
        return false;
    }

    // ------------------------------------------------------------------ eventos

    @EventHandler(priority = EventPriority.HIGH)
    public void alInteractuar(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!enShooter(p) || e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (Util.marca(e.getItem(), Claves.CLASE_MENU) != null) {
            e.setCancelled(true);
            clases.abrirMenu(p);
        } else if (e.getClickedBlock() != null) {
            Material m = e.getClickedBlock().getType();
            // Nada de abrir barriles, camas ni la rockola del living.
            if (m == Material.BARREL || m.name().endsWith("_BED") || m == Material.JUKEBOX || m == Material.SMOKER
                    || m == Material.BLAST_FURNACE || m == Material.CAULDRON) e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alDanio(EntityDamageEvent e) {
        if (!enShooter(e.getEntity())) return;
        if (e.getEntity() instanceof Mannequin) {
            e.setCancelled(true);
            return;
        }
        if (!(e.getEntity() instanceof Player p)) return;
        if (estado != Estado.EN_CURSO || protegido(p) || enRacha.contains(p.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        ultimoDanio.put(p.getUniqueId(), Bukkit.getCurrentTick());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alGolpe(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victima) || !enShooter(victima)) return;
        Player atacante = TresModos.jugadorAtacante(e.getDamager());
        if (atacante != null) proteccion.remove(atacante.getUniqueId());
        // Golpe con la mano o con un arma en la mano: muy poco (el cuchillo va con F).
        if (atacante != null && e.getDamager() == atacante && !Armas.aplicandoBala
                && e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            e.setDamage(2);
        }
    }

    /** Cuchillazo rápido con F: dos golpes matan; por la espalda, uno. Comando le suma alcance. */
    @EventHandler
    public void alCuchillo(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!enShooter(p)) return;
        e.setCancelled(true);
        if (bloqueaDisparo(p)) return;
        int ahora = Bukkit.getCurrentTick();
        if (ahora - ultimoCuchillo.getOrDefault(p.getUniqueId(), -100) < 12) return;
        ultimoCuchillo.put(p.getUniqueId(), ahora);
        p.swingMainHand();
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8f, 1.4f);
        double alcance = clases.tiene(p, Ventaja.COMANDO) ? 4.0 : 2.6;
        Location ojo = p.getEyeLocation();
        RayTraceResult r = p.getWorld().rayTrace(ojo, ojo.getDirection(), alcance, FluidCollisionMode.NEVER, true, 0.45,
                ent -> ent instanceof LivingEntity le && plugin.armas().blanco(p, le));
        if (r == null || !(r.getHitEntity() instanceof LivingEntity v)) return;
        Vector mira = v.getLocation().getDirection().setY(0);
        Vector hacia = v.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
        boolean espalda = mira.lengthSquared() > 0.01 && hacia.lengthSquared() > 0.01
                && mira.normalize().dot(hacia.normalize()) > 0.5;
        if (v instanceof Mannequin) return;
        Armas.danioDirecto(v, espalda ? 40 : 11, p, "Cuchillo", false);
        Hud.marcador(p, v.isDead());
        p.playSound(p, espalda ? Sound.ENTITY_PLAYER_ATTACK_CRIT : Sound.ENTITY_PLAYER_ATTACK_STRONG, 1f, espalda ? 0.7f : 1.2f);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void alMorir(PlayerDeathEvent e) {
        Player victima = e.getPlayer();
        if (!enShooter(victima)) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.deathMessage(null);
        e.setKeepInventory(false);
        lugarMuerte.put(victima.getUniqueId(), victima.getLocation());
        if (estado != Estado.EN_CURSO) return;

        Stats sv = stats(victima);
        sv.muertes++;
        sv.racha = 0;
        plugin.almacen().de(victima).codMuertes++;
        soltarMunicion(victima.getLocation());

        Armas.Impacto imp = plugin.armas().ultimoImpacto(victima.getUniqueId());
        Player asesino = imp != null ? Bukkit.getPlayer(imp.autor()) : victima.getKiller();
        String arma = imp != null ? imp.arma() : "Puños";
        boolean cabeza = imp != null && imp.cabeza();

        if (asesino == null || asesino == victima || !enShooter(asesino)) {
            anunciar("<gray>☠ <white>" + victima.getName() + " <gray>se murió solo");
            return;
        }
        asesinoDe.put(victima.getUniqueId(), asesino.getUniqueId());
        if (bombaEnCurso) return;
        Stats sa = stats(asesino);
        sa.bajas++;
        sa.racha++;
        bajasPartida++;
        actualizarContador();
        plugin.almacen().de(asesino).codBajas++;
        double dist = asesino.getLocation().distance(victima.getLocation());
        anunciar("<red>" + asesino.getName() + " <gray>[" + arma + "] <white>" + victima.getName()
                + " <dark_gray>" + Math.round(dist) + " m" + (cabeza ? " <gold>✦ headshot" : ""));
        asesino.playSound(asesino, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.6f);
        Util.barra(asesino, "<green>+1 baja  <gray>racha <yellow>" + sa.racha + (cabeza ? "  <gold>headshot" : ""));
        rachas.alSumarBaja(asesino, sa.racha);
        if (sa.bajas >= meta()) terminar(asesino, "llegó a " + meta() + " bajas");
    }

    /** Caja de munición que queda donde murió alguien: la juntan los que tienen Carroñero. */
    private void soltarMunicion(Location l) {
        ItemStack caja = new ItemStack(Material.CLAY_BALL);
        ItemMeta meta = caja.getItemMeta();
        meta.setItemModel(new NamespacedKey("tresmodos", "municion"));
        caja.setItemMeta(meta);
        Item it = l.getWorld().dropItem(l.clone().add(0, 0.3, 0), caja, i -> {
            i.setPickupDelay(10);
            i.setCanMobPickup(false);
            i.getPersistentDataContainer().set(Claves.MUNICION, PersistentDataType.BYTE, (byte) 1);
        });
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (it.isValid()) it.remove();
        }, 400);
    }

    @EventHandler(ignoreCancelled = true)
    public void alJuntarMunicion(EntityPickupItemEvent e) {
        if (!e.getItem().getPersistentDataContainer().has(Claves.MUNICION)) return;
        e.setCancelled(true);
        if (!(e.getEntity() instanceof Player p) || !clases.tiene(p, Ventaja.CARRONERO)) return;
        e.getItem().remove();
        plugin.armas().sumarCargador(p);
        p.playSound(p, Sound.ITEM_ARMOR_EQUIP_CHAIN, 0.8f, 1.5f);
        Util.barra(p, "<green>+munición (Carroñero)");
    }

    @EventHandler
    public void alHambre(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && enShooter(p)) {
            e.setCancelled(true);
            p.setFoodLevel(20);
        }
    }

    @EventHandler
    public void alTirar(PlayerDropItemEvent e) {
        if (enShooter(e.getPlayer()) && Armas.tipo(e.getItemDrop().getItemStack()) == null) e.setCancelled(true);
    }

    @EventHandler
    public void alClicInventario(InventoryClickEvent e) {
        // El minimapa no se saca de la mano izquierda.
        if (e.getWhoClicked() instanceof Player p && enShooter(p) && e.getSlot() == 40) e.setCancelled(true);
    }

    /** Como espectador libre no se sale del mapa ni se teletransporta a otros mundos. */
    @EventHandler(ignoreCancelled = true)
    public void alMoverEspectador(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SPECTATOR || !enShooter(p) || p.getSpectatorTarget() != null) return;
        Location to = e.getTo();
        double x = Math.max(-62, Math.min(62, to.getX())), z = Math.max(-52, Math.min(52, to.getZ()));
        double y = Math.max(PuebloAtomico.SUELO - 4, Math.min(PuebloAtomico.SUELO + 40, to.getY()));
        if (x != to.getX() || y != to.getY() || z != to.getZ()) {
            Location l = to.clone();
            l.set(x, y, z);
            e.setTo(l);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void alTeletransportar(PlayerTeleportEvent e) {
        if (e.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE && enShooter(e.getPlayer())
                && e.getTo().getWorld() != mundo()) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ ciclo

    private void tick() {
        int ahora = Bukkit.getCurrentTick();
        World w = mundo();
        if (w == null) return;
        List<Player> js = w.getPlayers();
        if (js.isEmpty()) {
            if (estado != Estado.ESPERANDO) {
                if (vacioDesde < 0) vacioDesde = ahora;
                else if (ahora - vacioDesde > 200) reiniciarVacio();
            }
            return;
        }
        // Regeneración: arranca 4 s después del último daño y llena la vida en 3 s.
        for (Player p : js) {
            if (p.isDead() || p.getGameMode() != GameMode.ADVENTURE) continue;
            double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
            int ult = ultimoDanio.getOrDefault(p.getUniqueId(), -1000);
            if (ahora - ult > 80 && p.getHealth() < max) p.setHealth(Math.min(max, p.getHealth() + max / 12));
        }
        // Espectadores: cuenta regresiva y reaparición.
        for (UUID id : new ArrayList<>(espectando.keySet())) {
            Player p = Bukkit.getPlayer(id);
            int fin = espectando.get(id);
            if (p == null || !enShooter(p)) {
                espectando.remove(id);
                continue;
            }
            if (ahora >= fin) reaparecerEnMapa(p);
            else Util.barra(p, "<gray>Reaparecés en <white>" + ((fin - ahora) / 20 + 1) + "<gray> s");
        }
        if (estado == Estado.EN_CURSO && !bombaEnCurso && ahora - inicio >= DURACION) {
            List<Player> orden = ranking();
            Player primero = orden.isEmpty() ? null : orden.get(0);
            if (primero == null || stats(primero).bajas == 0) terminar(null, "se terminó el tiempo");
            else terminar(primero, "tuvo más bajas a los 15 minutos");
        }
        if (ahora % 10 == 0) rastrear(js);
    }

    /** Rastreador: huellas rojas de los enemigos, visibles solo para quien tiene la ventaja. */
    private void rastrear(List<Player> js) {
        for (Player p : js) {
            if (p.isDead() || !clases.tiene(p, Ventaja.RASTREADOR)) continue;
            for (Player o : js) {
                if (o == p || o.isDead() || o.getGameMode() != GameMode.ADVENTURE) continue;
                if (o.getLocation().distanceSquared(p.getLocation()) > 40 * 40) continue;
                if (o.getVelocity().setY(0).lengthSquared() < 0.002 && !o.isSprinting()) continue;
                p.spawnParticle(Particle.DUST, o.getLocation().add(0, 0.05, 0), 2, 0.15, 0, 0.15, 0,
                        new Particle.DustOptions(Color.fromRGB(200, 30, 30), 0.9f));
            }
        }
    }

    private List<Player> ranking() {
        List<Player> orden = new ArrayList<>(jugadores());
        orden.sort(Comparator.comparingInt((Player o) -> stats(o).bajas).reversed()
                .thenComparingInt(o -> stats(o).muertes));
        return orden;
    }

    // ------------------------------------------------------------------ partida

    private void nuevaPartida() {
        restaurarMapa();
        equipamiento.limpiar();
        rachas.limpiar();
        stats.replaceAll((u, s) -> new Stats());
        espectando.clear();
        asesinoDe.clear();
        bajasPartida = 0;
        bombaEnCurso = false;
        estado = Estado.EN_CURSO;
        inicio = Bukkit.getCurrentTick();
        ponerManiquies();
        actualizarContador();
        for (Player o : jugadores()) {
            reaparecerEnMapa(o);
            proteger(o, PROTECCION + 20);
            Util.titulo(o, "<red><bold>NUEVA PARTIDA", "<gray>Primero a " + meta() + " bajas · 15 minutos", 200, 1800, 400);
            o.playSound(o, Sound.EVENT_RAID_HORN, 0.7f, 1.2f);
        }
    }

    private void reiniciarVacio() {
        estado = Estado.ESPERANDO;
        vacioDesde = -1;
        stats.clear();
        equipamiento.limpiar();
        rachas.limpiar();
        restaurarMapa();
    }

    /** Fin de la partida: podio con los 3 primeros, fuegos artificiales y nueva partida a los 15 s. */
    private void terminar(Player ganador, String motivo) {
        if (estado != Estado.EN_CURSO) return;
        estado = Estado.PODIO;
        bombaEnCurso = false;
        equipamiento.limpiar();
        rachas.limpiar();
        List<Player> orden = ranking();
        if (ganador != null) {
            orden.remove(ganador);
            orden.addFirst(ganador);
            DatosJugador d = plugin.almacen().de(ganador);
            d.shooterVictorias++;
        }
        double[][] podio = PuebloAtomico.podio();
        World w = mundo();
        for (int i = 0; i < orden.size(); i++) {
            Player o = orden.get(i);
            espectando.remove(o.getUniqueId());
            plugin.armas().dejarDeApuntar(o);
            plugin.movilidad().olvidar(o);
            if (o.isDead()) continue;
            if (o.getGameMode() == GameMode.SPECTATOR) o.setSpectatorTarget(null);
            o.setGameMode(GameMode.ADVENTURE);
            o.getInventory().clear();
            o.setHealth(o.getAttribute(Attribute.MAX_HEALTH).getValue());
            if (i < 3 && (ganador != null || stats(o).bajas > 0)) {
                o.teleport(new Location(w, podio[i][0], podio[i][1], podio[i][2], 90, 0));
            } else {
                o.teleport(lugarTribuna());
            }
        }
        String titulo = ganador == null ? "<gray><bold>EMPATE" : "<gold><bold>" + ganador.getName();
        String sub = ganador == null ? "<gray>Nadie llegó a la meta" : "<yellow>" + motivo;
        StringBuilder tabla = new StringBuilder("<gold><bold>Podio</bold>");
        for (int i = 0; i < Math.min(3, orden.size()); i++) {
            Stats s = stats(orden.get(i));
            tabla.append("\n<yellow>").append(i + 1).append(". <white>").append(orden.get(i).getName())
                    .append(" <green>").append(s.bajas).append(" <gray>/ <red>").append(s.muertes);
        }
        for (Player o : jugadores()) {
            Util.titulo(o, titulo, sub, 300, 5000, 800);
            Util.msg(o, tabla.toString());
            o.playSound(o, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        for (int i = 0; i < 6; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> fuegoArtificial(new Location(w, 24.5, PuebloAtomico.SUELO + 3, 0.5)), 10L + i * 25L);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (estado == Estado.PODIO) {
                if (jugadores().isEmpty()) reiniciarVacio();
                else nuevaPartida();
            }
        }, PODIO);
    }

    private void fuegoArtificial(Location l) {
        Firework f = l.getWorld().spawn(l, Firework.class);
        FireworkMeta fm = f.getFireworkMeta();
        fm.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE)
                .withColor(Color.YELLOW, Color.RED, Color.ORANGE).withFade(Color.WHITE).trail(true).build());
        fm.setPower(1);
        f.setFireworkMeta(fm);
    }

    // ------------------------------------------------------------------ bomba atómica

    /** La racha de 25: cuenta regresiva de 10 s, destello, onda expansiva y gana la partida. */
    boolean bombaAtomica(Player p) {
        if (estado != Estado.EN_CURSO || bombaEnCurso) return false;
        bombaEnCurso = true;
        UUID id = p.getUniqueId();
        plugin.almacen().de(p).shooterBombas++;
        for (Player o : jugadores()) {
            Util.titulo(o, "<red><bold>☢ BOMBA ATÓMICA ☢", "<yellow>" + p.getName() + " la lanzó · 10 s", 0, 3000, 500);
        }
        for (int s = 0; s < 10; s++) {
            final int falta = 10 - s;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player o : jugadores()) {
                    o.playSound(o, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, falta % 2 == 0 ? 0.6f : 0.5f);
                    o.playSound(o, Sound.ENTITY_WARDEN_SONIC_CHARGE, 0.4f, 0.5f + 0.05f * (10 - falta));
                    Util.barra(o, "<red><bold>☢ " + falta);
                }
            }, s * 20L);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> detonar(id), 200);
        return true;
    }

    private void detonar(UUID autorId) {
        if (estado != Estado.EN_CURSO) {
            bombaEnCurso = false;
            return;
        }
        World w = mundo();
        Player autor = Bukkit.getPlayer(autorId);
        Location centro = new Location(w, 0, PuebloAtomico.SUELO + 4, 0);
        for (Player o : jugadores()) {
            Hud.destello(o);
            o.playSound(o, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.3f);
            o.playSound(o, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.4f);
            o.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 120, 0, false, false, false));
        }
        for (int i = 0; i < 6; i++) {
            final int r = 6 + i * 8;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (int a = 0; a < 48; a++) {
                    double ang = a * Math.PI * 2 / 48;
                    Location l = centro.clone().add(Math.cos(ang) * r, -2, Math.sin(ang) * r);
                    w.spawnParticle(Particle.EXPLOSION_EMITTER, l, 1, 0, 0, 0, 0, null, true);
                    w.spawnParticle(Particle.LARGE_SMOKE, l, 6, 1, 1, 1, 0.05, null, true);
                }
            }, i * 4L);
        }
        w.spawnParticle(Particle.FLASH, centro, 5, 4, 4, 4, 0, Color.WHITE, true);
        quemarMapa();
        for (Player o : jugadores()) {
            if (o == autor || o.isDead() || o.getGameMode() != GameMode.ADVENTURE) continue;
            proteccion.remove(o.getUniqueId());
            Armas.danioDirecto(o, 10000, autor, "Bomba atómica", false);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player a = Bukkit.getPlayer(autorId);
            bombaEnCurso = false;
            if (a != null && enShooter(a)) terminar(a, "detonó la bomba atómica");
            else terminar(null, "la bomba arrasó el pueblo");
        }, 40);
    }

    /** Ennegrece el pueblo (se restaura desde el plano en la próxima partida). */
    private void quemarMapa() {
        World w = mundo();
        Plano plano = PuebloAtomico.plano();
        BlockData ceniza = Material.BLACK_CONCRETE_POWDER.createBlockData();
        BlockData hollin = Material.COAL_BLOCK.createBlockData();
        BlockData tierra = Material.COARSE_DIRT.createBlockData();
        plano.paraCada((x, y, z, t) -> {
            if (!PuebloAtomico.enZonaJugable(x, z) || y < PuebloAtomico.SUELO) return;
            String tipo = t.contains("[") ? t.substring(0, t.indexOf('[')) : t;
            if (!PuebloAtomico.quemable(tipo)) return;
            long h = Util.hash(x * 7 + y, z, 1945L) % 100;
            if (h > 55) return;
            Block b = w.getBlockAt(x, y, z);
            if (tipo.equals("grass_block")) b.setBlockData(h < 35 ? tierra : ceniza, false);
            else if (tipo.endsWith("leaves") || tipo.endsWith("fence")) b.setBlockData(Material.AIR.createBlockData(), false);
            else b.setBlockData(h < 25 ? hollin : ceniza, false);
            quemados.add(new int[]{x, y, z});
        });
    }

    private void restaurarMapa() {
        World w = mundo();
        if (w == null || quemados.isEmpty()) return;
        Plano plano = PuebloAtomico.plano();
        for (int[] c : quemados) plano.restaurar(w, c[0], c[1], c[2]);
        quemados.clear();
    }

    // ------------------------------------------------------------------ cartel, maniquíes y decorado

    private void prepararMundo() {
        World w = mundo();
        if (w == null) return;
        for (int cx = -3; cx <= 2; cx++)
            for (int cz = -2; cz <= 1; cz++) w.getChunkAt(cx, cz).load(true);
        for (Entity e : w.getEntities()) {
            if (e.getPersistentDataContainer().has(Claves.DECORADO) || e instanceof Mannequin) e.remove();
        }
        for (PuebloAtomico.Cartel c : PuebloAtomico.carteles()) {
            cartel(new Location(w, c.x(), c.y(), c.z(), c.yaw(), 0), c.texto(), false);
        }
        double[] cp = PuebloAtomico.contadorPoblacion();
        contador = cartel(new Location(w, cp[0], cp[1], cp[2], (float) cp[3], 0), "", true).getUniqueId();
        actualizarContador();
    }

    private TextDisplay cartel(Location l, String texto, boolean grande) {
        return l.getWorld().spawn(l, TextDisplay.class, t -> {
            t.text(Util.mm(texto));
            t.setBillboard(Display.Billboard.FIXED);
            t.setShadowed(false);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setLineWidth(200);
            if (grande) t.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(),
                    new org.joml.AxisAngle4f(), new org.joml.Vector3f(1.6f), new org.joml.AxisAngle4f()));
            t.getPersistentDataContainer().set(Claves.DECORADO, PersistentDataType.BYTE, (byte) 1);
        });
    }

    private void actualizarContador() {
        Entity e = contador == null ? null : Bukkit.getEntity(contador);
        if (e instanceof TextDisplay t) {
            t.text(Util.mm("<black>POBLACIÓN\n<dark_red><bold>" + bajasPartida + "</bold>"));
        }
    }

    private void ponerManiquies() {
        World w = mundo();
        for (UUID id : maniquies) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        maniquies.clear();
        for (PuebloAtomico.Maniqui m : PuebloAtomico.maniquies()) {
            Location l = new Location(w, m.x(), m.y(), m.z(), m.yaw(), 0);
            Mannequin q = w.spawn(l, Mannequin.class, x -> {
                x.setImmovable(true);
                x.setGravity(false);
                x.setPersistent(false);
                x.setDescription(Util.mm("<gray>Familia nuclear"));
                x.getEquipment().setChestplate(tenida(Material.LEATHER_CHESTPLATE, m.camisa()));
                x.getEquipment().setLeggings(tenida(Material.LEATHER_LEGGINGS, m.pantalon()));
                x.getEquipment().setBoots(tenida(Material.LEATHER_BOOTS, 0x2B2B2B));
                x.getPersistentDataContainer().set(Claves.MANIQUI, PersistentDataType.BYTE, (byte) 1);
            });
            maniquies.add(q.getUniqueId());
        }
        for (Player o : jugadores()) stats(o).maniquies = 0;
    }

    private static ItemStack tenida(Material m, int rgb) {
        ItemStack it = new ItemStack(m);
        LeatherArmorMeta meta = (LeatherArmorMeta) it.getItemMeta();
        meta.setColor(Color.fromRGB(rgb));
        it.setItemMeta(meta);
        return it;
    }

    /** Un maniquí recibió un tiro: si fue a la cabeza, se rompe. Con todos rotos, suena la canción. */
    public void maniquiAlcanzado(Player tirador, LivingEntity m, boolean cabeza) {
        if (!cabeza || !maniquies.remove(m.getUniqueId())) return;
        Location l = m.getLocation();
        m.getWorld().spawnParticle(Particle.BLOCK, l.clone().add(0, 1.6, 0), 20, 0.2, 0.2, 0.2, 0,
                Material.WHITE_CONCRETE.createBlockData());
        m.getWorld().playSound(l, Sound.ENTITY_ARMOR_STAND_BREAK, 1f, 1.2f);
        m.remove();
        stats(tirador).maniquies++;
        if (!maniquies.isEmpty()) {
            Util.barra(tirador, "<gray>Maniquíes: <white>" + (PuebloAtomico.maniquies().size() - maniquies.size())
                    + "/" + PuebloAtomico.maniquies().size());
            return;
        }
        anunciar("<light_purple>♪ " + tirador.getName() + " rompió el último maniquí. ¡Suena la radio del pueblo!");
        cancion();
        Player mejor = null;
        for (Player o : jugadores()) if (mejor == null || stats(o).maniquies > stats(mejor).maniquies) mejor = o;
        if (mejor != null) rachas.dar(mejor, Rachas.Racha.UAV);
    }

    /** Una melodía de los 50 con los bloques musicales (tónica, IV y V). */
    private void cancion() {
        float[] notas = {0.749f, 0.943f, 1.122f, 1.498f, 1.122f, 0.943f, 0.840f, 1.059f, 1.260f, 1.682f, 1.260f, 1.059f,
                1.0f, 1.260f, 1.498f, 2.0f, 1.498f, 1.260f, 0.749f, 0.749f};
        for (int i = 0; i < notas.length; i++) {
            final float n = notas[i];
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player o : jugadores()) {
                    o.playSound(o, Sound.BLOCK_NOTE_BLOCK_BELL, 0.9f, n);
                    o.playSound(o, Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, n / 2);
                }
            }, i * 5L);
        }
    }

    // ------------------------------------------------------------------ sidebar

    @Override
    public String tituloSidebar(Player p) {
        return "<red><bold>SHOOTER</bold> <gray>Pueblo Atómico";
    }

    @Override
    public List<String> lineasSidebar(Player p) {
        Stats s = stats(p);
        int restante = Math.max(0, DURACION - (Bukkit.getCurrentTick() - inicio)) / 20;
        List<String> l = new ArrayList<>();
        l.add(switch (estado) {
            case PODIO -> "<gold>Partida terminada";
            case ESPERANDO -> "<gray>Esperando jugadores";
            default -> "<gray>Tiempo: <white>" + Util.tiempo(restante);
        });
        l.add("<gray>Meta: <white>" + meta() + " bajas");
        l.add("");
        l.add("<white>Bajas: <green>" + s.bajas + "  <white>Muertes: <red>" + s.muertes);
        l.add("<white>Racha: <yellow>" + s.racha);
        Rachas.Racha prox = null;
        for (Rachas.Racha r : Rachas.Racha.values()) {
            if (rachas.costo(p, r) > s.racha) {
                prox = r;
                break;
            }
        }
        if (prox != null) l.add("<gray>Próxima: <gold>" + prox.nombre + " <gray>(" + rachas.costo(p, prox) + ")");
        l.add("<white>Clase: <aqua>" + ClasesShooter.NOMBRES[plugin.almacen().de(p).shooterClase]);
        l.add("");
        l.add("<gold>Tabla");
        List<Player> orden = ranking();
        for (int i = 0; i < Math.min(4, orden.size()); i++) {
            Player o = orden.get(i);
            l.add("<gray>" + (i + 1) + ". <white>" + o.getName() + " <green>" + stats(o).bajas);
        }
        return l;
    }
}
