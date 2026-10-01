package ar.tresmodos.modos;

import ar.tresmodos.Armas;
import ar.tresmodos.Claves;
import ar.tresmodos.DatosJugador;
import ar.tresmodos.Menu;
import ar.tresmodos.Modo;
import ar.tresmodos.ModoJuego;
import ar.tresmodos.TresModos;
import ar.tresmodos.Util;
import ar.tresmodos.mundo.GeneradorArena;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Modo COD: todos contra todos en la arena de contenedores. */
public class ModoCod implements ModoJuego, Listener {
    public static final int META = 30;
    private static final int DURACION_TICKS = 20 * 60 * 10;

    public enum Clase {
        ASALTO("Asalto", Armas.Tipo.M4A1, 2, "Fusil de asalto, buen alcance."),
        SUBFUSIL("Subfusil", Armas.Tipo.MP5, 2, "Rápido y ágil (Velocidad I)."),
        ESCOPETERO("Escopetero", Armas.Tipo.ESCOPETA, 1, "Letal de cerca."),
        FRANCOTIRADOR("Francotirador", Armas.Tipo.FRANCOTIRADOR, 1, "Un tiro. Shift para apuntar.");

        final String nombre;
        final Armas.Tipo principal;
        final int granadas;
        final String desc;

        Clase(String nombre, Armas.Tipo principal, int granadas, String desc) {
            this.nombre = nombre;
            this.principal = principal;
            this.granadas = granadas;
            this.desc = desc;
        }
    }

    private static final class Stats {
        int bajas, muertes, racha;
    }

    private final TresModos plugin;
    private final Random rnd = new Random();
    private final List<int[]> puntos = GeneradorArena.puntosLibres();
    private final Map<UUID, Clase> clases = new HashMap<>();
    private final Map<UUID, Stats> stats = new HashMap<>();
    private final Map<UUID, Integer> proteccion = new HashMap<>();
    private final Map<UUID, Integer> ultimoDanio = new HashMap<>();
    private boolean terminada = false;
    private int inicioPartida;

    public ModoCod(TresModos plugin) {
        this.plugin = plugin;
        this.inicioPartida = Bukkit.getCurrentTick();
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10, 10);
    }

    private World mundo() {
        return plugin.mundos().de(Modo.COD);
    }

    private boolean enCod(Player p) {
        return Modo.de(p.getWorld()) == Modo.COD;
    }

    private List<Player> jugadores() {
        return mundo().getPlayers();
    }

    @Override public Modo modo() { return Modo.COD; }
    @Override public boolean guardaEstado() { return false; }
    @Override public GameMode modoJuego() { return GameMode.ADVENTURE; }

    @Override
    public Location ubicacionEntrada(Player p) {
        return spawnSeguro(p);
    }

    @Override
    public Location respawn(Player p) {
        return spawnSeguro(p);
    }

    /** Entre 10 puntos libres al azar, elige el más lejano a los demás jugadores vivos. */
    private Location spawnSeguro(Player p) {
        World w = mundo();
        Location mejor = null;
        double mejorDist = -1;
        for (int i = 0; i < 10; i++) {
            int[] pt = puntos.get(rnd.nextInt(puntos.size()));
            Location l = new Location(w, pt[0] + 0.5, GeneradorArena.SUELO + 1, pt[1] + 0.5);
            double min = Double.MAX_VALUE;
            for (Player o : jugadores()) {
                if (o == p || o.isDead()) continue;
                min = Math.min(min, o.getLocation().distanceSquared(l));
            }
            if (min > mejorDist) {
                mejorDist = min;
                mejor = l;
            }
        }
        Location centro = new Location(w, 0, mejor.getY(), 0);
        mejor.setDirection(centro.toVector().subtract(mejor.toVector()));
        return mejor;
    }

    @Override
    public void kitInicial(Player p) {
        darEquipo(p);
    }

    private void darEquipo(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        Clase c = clases.getOrDefault(p.getUniqueId(), Clase.ASALTO);
        inv.setItem(0, Armas.crear(c.principal));
        inv.setItem(1, Armas.crear(Armas.Tipo.PISTOLA));
        inv.setItem(2, Util.marcar(Util.item(Material.IRON_SWORD, "<white><bold>Cuchillo",
                "Dos golpes. Por la espalda, uno."), Claves.CUCHILLO, "1"));
        if (c.granadas > 0) inv.setItem(3, Armas.granadas(c.granadas));
        inv.setItem(8, Util.marcar(Util.item(Material.BOOK, "<yellow><bold>Clases y opciones",
                "Cambiar de clase o volver al lobby."), Claves.CLASE_MENU, "1"));
        inv.setHeldItemSlot(0);
        p.removePotionEffect(PotionEffectType.SPEED);
        if (c == Clase.SUBFUSIL) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, false, true));
        }
        p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
        p.setFoodLevel(20);
    }

    @Override
    public void alEntrar(Player p, boolean primeraVez) {
        stats.computeIfAbsent(p.getUniqueId(), u -> new Stats());
        proteger(p, 60);
        Util.titulo(p, "<red><bold>COD", "<gray>Todos contra todos · primero a " + META + " bajas", 200, 2000, 500);
        Util.msg(p, "<gray>Clase actual: <aqua>" + clases.getOrDefault(p.getUniqueId(), Clase.ASALTO).nombre
                + "<gray>. Cambiala con el libro o con <white>/clase<gray>. Q recarga.");
        for (Player o : jugadores()) if (o != p) Util.msg(o, "<gray>" + p.getName() + " entró a la arena.");
    }

    @Override
    public void alSalir(Player p) {
        plugin.armas().quitarZoom(p);
        proteccion.remove(p.getUniqueId());
    }

    @Override
    public void alReaparecer(Player p) {
        darEquipo(p);
        proteger(p, 40);
    }

    private void proteger(Player p, int ticks) {
        proteccion.put(p.getUniqueId(), Bukkit.getCurrentTick() + ticks);
    }

    private boolean protegido(Player p) {
        Integer t = proteccion.get(p.getUniqueId());
        return t != null && Bukkit.getCurrentTick() < t;
    }

    /** Las armas no disparan entre partidas. Disparar quita la protección de aparición. */
    public boolean bloqueaDisparo(Player p) {
        if (!enCod(p)) return false;
        if (terminada) return true;
        proteccion.remove(p.getUniqueId());
        return false;
    }

    // ------------------------------------------------------------------ clases

    public void abrirMenuClases(Player p) {
        Menu m = new Menu(3, "<dark_gray>Clases");
        Material[] icono = {Material.DIAMOND_HOE, Material.STONE_HOE, Material.GOLDEN_HOE, Material.NETHERITE_HOE};
        Clase actual = clases.getOrDefault(p.getUniqueId(), Clase.ASALTO);
        int slot = 10;
        for (Clase c : Clase.values()) {
            String marca = c == actual ? " <green>(actual)" : "";
            m.poner(slot, Util.item(icono[c.ordinal()], "<aqua><bold>" + c.nombre + marca, c.desc,
                    "Principal: " + c.principal.nombre, "Granadas: " + c.granadas), pl -> elegirClase(pl, c));
            slot += 2;
        }
        m.poner(22, Util.item(Material.OAK_DOOR, "<white>Volver al lobby"), pl -> {
            pl.closeInventory();
            plugin.cambio().cambiar(pl, Modo.LOBBY, false);
        });
        m.abrir(p);
    }

    private void elegirClase(Player p, Clase c) {
        clases.put(p.getUniqueId(), c);
        p.closeInventory();
        if (protegido(p)) {
            darEquipo(p);
            Util.msg(p, "<green>Clase " + c.nombre + " equipada.");
        } else {
            Util.msg(p, "<green>Clase " + c.nombre + " elegida. <gray>Se aplica al reaparecer.");
        }
    }

    // ------------------------------------------------------------------ eventos

    @EventHandler(priority = EventPriority.HIGH)
    public void alInteractuar(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!enCod(p) || e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (Util.marca(it, Claves.CLASE_MENU) != null) {
            e.setCancelled(true);
            abrirMenuClases(p);
        } else if (Util.marca(it, Claves.BOMBARDEO) != null) {
            e.setCancelled(true);
            usarBombardeo(p, it);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alDanio(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !enCod(p)) return;
        if (terminada || protegido(p)) {
            e.setCancelled(true);
            return;
        }
        ultimoDanio.put(p.getUniqueId(), Bukkit.getCurrentTick());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alGolpe(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victima) || !enCod(victima)) return;
        Player atacante = TresModos.jugadorAtacante(e.getDamager());
        if (atacante == null) return;
        proteccion.remove(atacante.getUniqueId());
        if (Armas.aplicandoBala || e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (Util.marca(atacante.getInventory().getItemInMainHand(), Claves.CUCHILLO) == null) return;
        // Puñalada por la espalda: el atacante mira en la misma dirección que la víctima
        Vector mira = victima.getLocation().getDirection().setY(0).normalize();
        Vector hacia = victima.getLocation().toVector().subtract(atacante.getLocation().toVector()).setY(0).normalize();
        boolean espalda = mira.dot(hacia) > 0.5;
        e.setDamage(espalda ? 40 : 11);
        if (espalda) atacante.playSound(atacante, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.7f);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void alMorir(PlayerDeathEvent e) {
        Player victima = e.getPlayer();
        if (!enCod(victima)) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.deathMessage(null);
        e.setKeepInventory(false);

        Stats sv = stats.computeIfAbsent(victima.getUniqueId(), u -> new Stats());
        sv.muertes++;
        sv.racha = 0;
        plugin.almacen().de(victima).codMuertes++;

        Armas.Impacto imp = plugin.armas().ultimoImpacto(victima.getUniqueId());
        Player asesino = null;
        String arma;
        boolean cabeza = false;
        if (imp != null) {
            asesino = Bukkit.getPlayer(imp.autor());
            arma = imp.arma();
            cabeza = imp.cabeza();
        } else {
            asesino = victima.getKiller();
            arma = asesino == null ? null : armaEnMano(asesino);
        }

        if (asesino == null || asesino == victima || !enCod(asesino)) {
            feed("<gray>☠ <white>" + victima.getName() + " <gray>se murió solo");
            return;
        }
        Stats sa = stats.computeIfAbsent(asesino.getUniqueId(), u -> new Stats());
        sa.bajas++;
        sa.racha++;
        DatosJugador da = plugin.almacen().de(asesino);
        da.codBajas++;
        feed("<red>" + asesino.getName() + " <gray>[" + arma + "] <white>" + victima.getName()
                + (cabeza ? " <gold>✦ headshot" : ""));
        asesino.playSound(asesino, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.6f);
        Util.barra(asesino, "<green>+1 baja  <gray>racha <yellow>" + sa.racha);
        rachas(asesino, sa.racha);
        if (sa.bajas >= META && !terminada) terminarPartida(asesino.getName());
    }

    private String armaEnMano(Player p) {
        ItemStack it = p.getInventory().getItemInMainHand();
        Armas.Tipo t = Armas.tipo(it);
        if (t != null) return t.nombre;
        if (Util.marca(it, Claves.CUCHILLO) != null) return "Cuchillo";
        return "Puños";
    }

    private void feed(String s) {
        for (Player o : jugadores()) Util.msg(o, s);
    }

    @EventHandler
    public void alHambre(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && enCod(p)) {
            e.setCancelled(true);
            p.setFoodLevel(20);
        }
    }

    @EventHandler
    public void alTirar(PlayerDropItemEvent e) {
        if (enCod(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void alCambiarMano(PlayerSwapHandItemsEvent e) {
        if (enCod(e.getPlayer())) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ rachas

    private void rachas(Player p, int racha) {
        if (racha == 3) {
            feed("<aqua>✈ " + p.getName() + " lanzó un <bold>UAV</bold>: todos marcados 10 s");
            for (Player o : jugadores()) {
                if (o != p) o.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0, false, false, false));
                o.playSound(o, Sound.ENTITY_PHANTOM_FLAP, 1f, 0.6f);
            }
        } else if (racha == 5) {
            p.getInventory().addItem(Util.marcar(Util.item(Material.BLAZE_ROD, "<gold><bold>Bombardeo",
                    "Clic derecho apuntando al suelo."), Claves.BOMBARDEO, "1"));
            Util.titulo(p, "", "<gold>Racha de 5: <bold>Bombardeo</bold> disponible", 100, 1500, 300);
            p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        }
    }

    private void usarBombardeo(Player p, ItemStack it) {
        RayTraceResult r = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 120,
                FluidCollisionMode.NEVER, true);
        if (r == null || r.getHitBlock() == null) {
            Util.barra(p, "<red>Apuntá a un punto del mapa.");
            return;
        }
        it.setAmount(it.getAmount() - 1);
        Block b = r.getHitBlock();
        Location objetivo = b.getLocation().add(0.5, 1, 0.5);
        feed("<gold>✈ ¡Bombardeo entrante de " + p.getName() + "!");
        for (Player o : jugadores()) o.playSound(o, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.6f);
        Vector linea = new Vector(rnd.nextDouble() - 0.5, 0, rnd.nextDouble() - 0.5).normalize();
        for (int i = -3; i <= 3; i++) {
            final int k = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location l = objetivo.clone().add(linea.clone().multiply(k * 3.0));
                l.getWorld().spawnParticle(Particle.LARGE_SMOKE, l.clone().add(0, 6, 0), 10, 0.3, 2, 0.3, 0.02);
                plugin.armas().explotar(l, 3.2f, p.isOnline() ? p : null, "Bombardeo");
            }, 40 + (i + 3) * 4L);
        }
        objetivo.getWorld().spawnParticle(Particle.DUST, objetivo, 40, 1.5, 0.1, 1.5, 0,
                new Particle.DustOptions(org.bukkit.Color.RED, 1.5f));
    }

    // ------------------------------------------------------------------ partida

    private void tick() {
        int ahora = Bukkit.getCurrentTick();
        for (Player p : jugadores()) {
            if (p.isDead()) continue;
            Integer ult = ultimoDanio.get(p.getUniqueId());
            double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
            if ((ult == null || ahora - ult > 100) && p.getHealth() < max) p.setHealth(Math.min(max, p.getHealth() + 2));
        }
        if (!terminada && ahora - inicioPartida >= DURACION_TICKS) {
            Map.Entry<UUID, Stats> top = stats.entrySet().stream()
                    .filter(en -> Bukkit.getPlayer(en.getKey()) != null && enCod(Bukkit.getPlayer(en.getKey())))
                    .max(Comparator.comparingInt(en -> en.getValue().bajas)).orElse(null);
            if (top != null && top.getValue().bajas > 0) terminarPartida(Bukkit.getPlayer(top.getKey()).getName());
            else inicioPartida = ahora;
        }
    }

    private void terminarPartida(String ganador) {
        terminada = true;
        for (Player o : jugadores()) {
            Util.titulo(o, "<gold><bold>" + ganador, "<yellow>ganó la partida", 200, 5000, 800);
            o.playSound(o, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            stats.replaceAll((u, s) -> new Stats());
            terminada = false;
            inicioPartida = Bukkit.getCurrentTick();
            for (Player o : jugadores()) {
                o.teleport(spawnSeguro(o));
                darEquipo(o);
                proteger(o, 60);
                Util.titulo(o, "<red><bold>NUEVA PARTIDA", "<gray>Primero a " + META + " bajas", 200, 1500, 400);
            }
        }, 160);
    }

    // ------------------------------------------------------------------ sidebar

    @Override
    public String tituloSidebar(Player p) {
        return "<red><bold>COD</bold> <gray>Contenedores";
    }

    @Override
    public List<String> lineasSidebar(Player p) {
        Stats s = stats.computeIfAbsent(p.getUniqueId(), u -> new Stats());
        int restante = Math.max(0, DURACION_TICKS - (Bukkit.getCurrentTick() - inicioPartida)) / 20;
        List<String> l = new ArrayList<>();
        l.add(terminada ? "<gold>Partida terminada" : "<gray>Tiempo: <white>" + Util.tiempo(restante));
        l.add("");
        l.add("<white>Bajas: <green>" + s.bajas);
        l.add("<white>Muertes: <red>" + s.muertes);
        l.add("<white>Racha: <yellow>" + s.racha);
        l.add("<white>Clase: <aqua>" + clases.getOrDefault(p.getUniqueId(), Clase.ASALTO).nombre);
        l.add("");
        l.add("<gold>Tabla");
        List<Player> orden = new ArrayList<>(jugadores());
        orden.sort(Comparator.comparingInt((Player o) -> stats.computeIfAbsent(o.getUniqueId(), u -> new Stats()).bajas).reversed());
        for (int i = 0; i < Math.min(3, orden.size()); i++) {
            Player o = orden.get(i);
            l.add("<gray>" + (i + 1) + ". <white>" + o.getName() + " <green>" + stats.get(o.getUniqueId()).bajas);
        }
        l.add("");
        l.add("<gray>Meta: <white>" + META + " bajas");
        return l;
    }
}
