package ar.tresmodos.modos;

import ar.tresmodos.Armas;
import ar.tresmodos.Claves;
import ar.tresmodos.DatosJugador;
import ar.tresmodos.Menu;
import ar.tresmodos.Modo;
import ar.tresmodos.ModoJuego;
import ar.tresmodos.TresModos;
import ar.tresmodos.Util;
import ar.tresmodos.mundo.GeneradorCiudad;
import com.destroystokyo.paper.ParticleBuilder;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Horse;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Player;
import org.bukkit.entity.Ravager;
import org.bukkit.entity.Vindicator;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Modo GTA: ciudad procedural, plata, tienda, nivel de búsqueda con policía, autos y entregas. */
public class ModoGta implements ModoJuego, Listener {
    private static final int MAX_ESTRELLAS = 5;
    private static final int DECAE_TICKS = 20 * 30;

    public enum Auto {
        COMUN("Auto común", 1000, 0.30, 0.55, Horse.Color.WHITE),
        DEPORTIVO("Deportivo", 4000, 0.40, 0.75, Horse.Color.BLACK),
        SUPER("Superdeportivo", 9000, 0.48, 0.95, Horse.Color.CHESTNUT);

        final String nombre;
        final long precio;
        final double velocidad, salto;
        final Horse.Color color;

        Auto(String nombre, long precio, double velocidad, double salto, Horse.Color color) {
            this.nombre = nombre;
            this.precio = precio;
            this.velocidad = velocidad;
            this.salto = salto;
            this.color = color;
        }
    }

    private record Mision(Location destino, int fin, long premio) {}

    private final TresModos plugin;
    private final Random rnd = new Random();
    private final Map<UUID, Integer> buscado = new HashMap<>();
    private final Map<UUID, Integer> ultimoCrimen = new HashMap<>();
    private final Map<UUID, Set<UUID>> policias = new HashMap<>();
    private final Map<UUID, UUID> autos = new HashMap<>();
    private final Map<UUID, Mision> misiones = new HashMap<>();

    public ModoGta(TresModos plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickPolicia, 100, 100);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickMisiones, 10, 10);
        Bukkit.getScheduler().runTaskTimer(plugin, this::sueldo, 1200, 1200);
    }

    private World mundo() {
        return plugin.mundos().de(Modo.GTA);
    }

    private boolean enGta(Entity e) {
        return Modo.de(e.getWorld()) == Modo.GTA;
    }

    @Override public Modo modo() { return Modo.GTA; }
    @Override public boolean guardaEstado() { return true; }
    @Override public GameMode modoJuego() { return GameMode.ADVENTURE; }

    @Override
    public Location ubicacionEntrada(Player p) {
        return plugin.mundos().spawn(Modo.GTA);
    }

    @Override
    public Location respawn(Player p) {
        return plugin.mundos().spawn(Modo.GTA);
    }

    public static ItemStack celular() {
        return Util.marcar(Util.item(Material.COMPASS, "<aqua><bold>Celular <gray>(clic derecho)",
                "Tienda, concesionaria, misiones.", "La aguja apunta a tu entrega."), Claves.CELULAR, "1");
    }

    @Override
    public void kitInicial(Player p) {
        p.getInventory().setItem(0, Armas.crear(Armas.Tipo.PISTOLA, plugin.almacen().de(p).accesorios(Armas.Tipo.PISTOLA)));
        p.getInventory().setItem(7, new ItemStack(Material.COOKED_BEEF, 16));
        p.getInventory().setItem(8, celular());
    }

    @Override
    public void alEntrar(Player p, boolean primeraVez) {
        p.setCompassTarget(mundo().getSpawnLocation());
        if (primeraVez) {
            Util.titulo(p, "<gold><bold>BIENVENIDO A LA CIUDAD", "<gray>Tenés " + Util.plata(plugin.almacen().de(p).dinero)
                    + " y una pistola", 300, 3000, 700);
            Util.msg(p, "<gold>GTA: <gray>abrí el <aqua>celular<gray> (brújula) para comprar armas y autos o tomar misiones. "
                    + "Matar gente o policías sube tu búsqueda. <white>/lobby<gray> para salir.");
        } else {
            Util.titulo(p, "<gold><bold>GTA", "<gray>" + Util.plata(plugin.almacen().de(p).dinero), 200, 1500, 500);
        }
    }

    @Override
    public void alSalir(Player p) {
        setBuscado(p, 0);
        cancelarMision(p, null);
        quitarAuto(p);
    }

    @Override
    public void alReaparecer(Player p) {
        Util.titulo(p, "<gray><bold>WASTED", "", 100, 1800, 600);
        p.setCompassTarget(mundo().getSpawnLocation());
    }

    public void apagar() {
        for (UUID id : new ArrayList<>(policias.keySet())) quitarPolicia(id);
        for (UUID car : autos.values()) {
            Entity e = Bukkit.getEntity(car);
            if (e != null) e.remove();
        }
        autos.clear();
    }

    private void sueldo() {
        for (Player p : mundo().getPlayers()) {
            plugin.almacen().de(p).dinero += 20;
        }
    }

    // ------------------------------------------------------------------ celular y menús

    public void abrirCelular(Player p) {
        DatosJugador d = plugin.almacen().de(p);
        int estrellas = buscado.getOrDefault(p.getUniqueId(), 0);
        Menu m = new Menu(3, "<dark_gray>Celular · " + Util.plata(d.dinero));
        m.poner(10, Util.item(Material.CHEST, "<yellow><bold>Armería", "Armas, granadas, chaleco y botiquín."),
                this::abrirTienda);
        m.poner(12, Util.item(Material.SADDLE, "<yellow><bold>Concesionaria", "Comprá un auto y llamalo con la llave."),
                this::abrirConcesionaria);
        boolean activa = misiones.containsKey(p.getUniqueId());
        m.poner(14, Util.item(Material.FILLED_MAP, activa ? "<red><bold>Cancelar entrega" : "<yellow><bold>Misión de entrega",
                activa ? "Abandonás el paquete." : "Llevá un paquete a otra esquina", activa ? "" : "antes de que se acabe el tiempo."),
                pl -> {
                    pl.closeInventory();
                    if (misiones.containsKey(pl.getUniqueId())) cancelarMision(pl, "<red>Entrega cancelada.");
                    else iniciarMision(pl);
                });
        long soborno = 400L * estrellas;
        m.poner(16, Util.item(Material.EMERALD, "<yellow><bold>Sobornar a la policía",
                estrellas == 0 ? "No te busca nadie." : "Borra tu búsqueda por " + Util.plata(soborno) + "."),
                pl -> {
                    int n = buscado.getOrDefault(pl.getUniqueId(), 0);
                    if (n == 0) return;
                    if (cobrar(pl, 400L * n)) {
                        setBuscado(pl, 0);
                        Util.msg(pl, "<green>La policía mira para otro lado.");
                    }
                    pl.closeInventory();
                });
        m.poner(20, Util.item(Material.SMITHING_TABLE, "<gold><bold>Armero",
                "Accesorios para tus armas.", "Todos gratis y desbloqueados."), plugin.armero()::abrir);
        m.poner(22, Util.item(Material.OAK_DOOR, "<white>Volver al lobby"), pl -> {
            pl.closeInventory();
            plugin.cambio().cambiar(pl, Modo.LOBBY, false);
        });
        m.abrir(p);
    }

    private void abrirTienda(Player p) {
        Menu m = new Menu(4, "<dark_gray>Armería · " + Util.plata(plugin.almacen().de(p).dinero));
        long[] precios = {300, 1500, 3000, 2000, 5000};
        Armas.Tipo[] tipos = {Armas.Tipo.PISTOLA, Armas.Tipo.MP5, Armas.Tipo.M4A1, Armas.Tipo.ESCOPETA, Armas.Tipo.FRANCOTIRADOR};
        for (int i = 0; i < tipos.length; i++) {
            Armas.Tipo t = tipos[i];
            long precio = precios[i];
            ItemStack icono = Armas.crear(t);
            icono.lore(List.of(Util.mmItem("<green>" + Util.plata(precio)), Util.mmItem("<gray>Daño " + t.danio
                    + (t.perdigones > 1 ? " x" + t.perdigones : "") + " · cargador " + t.cargador)));
            m.poner(10 + i, icono, pl -> comprar(pl, precio, Armas.crear(t, plugin.almacen().de(pl).accesorios(t))));
        }
        m.poner(19, conPrecio(Armas.granadas(3), 600), pl -> comprar(pl, 600, Armas.granadas(3)));
        m.poner(20, conPrecio(new ItemStack(Material.IRON_CHESTPLATE), 800), pl -> comprar(pl, 800, new ItemStack(Material.IRON_CHESTPLATE)));
        m.poner(21, conPrecio(new ItemStack(Material.IRON_HELMET), 400), pl -> comprar(pl, 400, new ItemStack(Material.IRON_HELMET)));
        m.poner(22, conPrecio(Util.item(Material.GOLDEN_APPLE, "<gold>Botiquín"), 250),
                pl -> comprar(pl, 250, Util.item(Material.GOLDEN_APPLE, "<gold>Botiquín")));
        m.poner(23, conPrecio(new ItemStack(Material.COOKED_BEEF, 16), 60), pl -> comprar(pl, 60, new ItemStack(Material.COOKED_BEEF, 16)));
        m.poner(31, Util.item(Material.ARROW, "<white>Volver"), this::abrirCelular);
        m.abrir(p);
    }

    private ItemStack conPrecio(ItemStack it, long precio) {
        ItemStack c = it.clone();
        c.lore(List.of(Util.mmItem("<green>" + Util.plata(precio))));
        return c;
    }

    private void abrirConcesionaria(Player p) {
        Menu m = new Menu(3, "<dark_gray>Concesionaria · " + Util.plata(plugin.almacen().de(p).dinero));
        Material[] icono = {Material.IRON_HORSE_ARMOR, Material.GOLDEN_HORSE_ARMOR, Material.DIAMOND_HORSE_ARMOR};
        int slot = 11;
        for (Auto a : Auto.values()) {
            m.poner(slot, Util.item(icono[a.ordinal()], "<yellow><bold>" + a.nombre, "<green>" + Util.plata(a.precio),
                    "Velocidad " + (int) (a.velocidad * 100) + " · salto " + (int) (a.salto * 100),
                    "Te da una llave: clic derecho para llamarlo."), pl -> comprar(pl, a.precio, llave(a)));
            slot += 2;
        }
        m.poner(22, Util.item(Material.ARROW, "<white>Volver"), this::abrirCelular);
        m.abrir(p);
    }

    private ItemStack llave(Auto a) {
        return Util.marcar(Util.item(Material.TRIPWIRE_HOOK, "<yellow><bold>Llave: " + a.nombre,
                "Clic derecho: llamar y subir al auto."), Claves.LLAVE, a.name());
    }

    private boolean cobrar(Player p, long precio) {
        DatosJugador d = plugin.almacen().de(p);
        if (d.dinero < precio) {
            Util.msg(p, "<red>No te alcanza: te faltan " + Util.plata(precio - d.dinero) + ".");
            p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return false;
        }
        d.dinero -= precio;
        return true;
    }

    private void comprar(Player p, long precio, ItemStack item) {
        if (!cobrar(p, precio)) return;
        for (ItemStack sobra : p.getInventory().addItem(item).values()) p.getWorld().dropItem(p.getLocation(), sobra);
        p.playSound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        Util.barra(p, "<green>Compraste por " + Util.plata(precio) + " · te quedan " + Util.plata(plugin.almacen().de(p).dinero));
    }

    // ------------------------------------------------------------------ autos

    private void usarLlave(Player p, Auto a) {
        UUID actual = autos.get(p.getUniqueId());
        Entity viejo = actual == null ? null : Bukkit.getEntity(actual);
        if (viejo instanceof Horse h && h.isValid() && h.getWorld() == p.getWorld()
                && a.name().equals(h.getPersistentDataContainer().get(Claves.LLAVE, PersistentDataType.STRING))) {
            if (h.getPassengers().contains(p)) return;
            h.teleport(p.getLocation());
            h.addPassenger(p);
            return;
        }
        if (viejo != null) viejo.remove();
        Horse h = p.getWorld().spawn(p.getLocation(), Horse.class, c -> {
            c.setAdult();
            c.setTamed(true);
            c.setOwner(p);
            c.getInventory().setSaddle(new ItemStack(Material.SADDLE));
            c.setColor(a.color);
            c.setStyle(Horse.Style.NONE);
            c.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(a.velocidad);
            c.getAttribute(Attribute.JUMP_STRENGTH).setBaseValue(a.salto);
            c.getAttribute(Attribute.MAX_HEALTH).setBaseValue(40);
            c.setHealth(40);
            c.setInvulnerable(true);
            c.setPersistent(false);
            c.customName(Util.mm("<yellow>" + a.nombre + " <gray>de " + p.getName()));
            c.getPersistentDataContainer().set(Claves.AUTO_DUENO, PersistentDataType.STRING, p.getUniqueId().toString());
            c.getPersistentDataContainer().set(Claves.LLAVE, PersistentDataType.STRING, a.name());
        });
        autos.put(p.getUniqueId(), h.getUniqueId());
        h.addPassenger(p);
        p.playSound(p, Sound.ENTITY_HORSE_SADDLE, 1f, 0.8f);
    }

    private void quitarAuto(Player p) {
        UUID id = autos.remove(p.getUniqueId());
        if (id == null) return;
        Entity e = Bukkit.getEntity(id);
        if (e != null) e.remove();
    }

    @EventHandler
    public void alMontar(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof AbstractHorse h)) return;
        String dueno = h.getPersistentDataContainer().get(Claves.AUTO_DUENO, PersistentDataType.STRING);
        if (dueno != null && !dueno.equals(e.getPlayer().getUniqueId().toString())) {
            e.setCancelled(true);
            Util.barra(e.getPlayer(), "<red>Este auto no es tuyo.");
        }
    }

    @EventHandler
    public void alAbrirInventario(InventoryOpenEvent e) {
        if (e.getInventory().getHolder(false) instanceof AbstractHorse h
                && h.getPersistentDataContainer().has(Claves.AUTO_DUENO)) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ interacción

    @EventHandler(priority = EventPriority.HIGH)
    public void alInteractuar(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!enGta(p) || e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (Util.marca(it, Claves.CELULAR) != null) {
            e.setCancelled(true);
            abrirCelular(p);
            return;
        }
        String llave = Util.marca(it, Claves.LLAVE);
        if (llave != null) {
            e.setCancelled(true);
            try {
                usarLlave(p, Auto.valueOf(llave));
            } catch (IllegalArgumentException ignored) {
                // llave de una versión vieja
            }
        }
    }

    @EventHandler
    public void alTirar(PlayerDropItemEvent e) {
        if (!enGta(e.getPlayer())) return;
        ItemStack it = e.getItemDrop().getItemStack();
        if (Util.marca(it, Claves.CELULAR) != null || Util.marca(it, Claves.PAQUETE) != null) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ crímenes y policía

    public int getBuscado(Player p) {
        return buscado.getOrDefault(p.getUniqueId(), 0);
    }

    public void setBuscado(Player p, int n) {
        n = Math.max(0, Math.min(MAX_ESTRELLAS, n));
        int antes = getBuscado(p);
        if (n == 0) {
            buscado.remove(p.getUniqueId());
            quitarPolicia(p.getUniqueId());
            if (antes > 0) Util.barra(p, "<green>Perdiste a la policía.");
        } else {
            buscado.put(p.getUniqueId(), n);
            ultimoCrimen.put(p.getUniqueId(), Bukkit.getCurrentTick());
            if (n > antes) {
                Util.titulo(p, "", Util.estrellas(n, MAX_ESTRELLAS), 50, 1200, 300);
                p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BELL, 1f, 0.6f);
            }
        }
    }

    private void crimen(Player p, int estrellas) {
        if (!enGta(p)) return;
        setBuscado(p, getBuscado(p) + estrellas);
    }

    private boolean esPolicia(Entity e) {
        return e.getPersistentDataContainer().has(Claves.POLICIA);
    }

    @EventHandler(ignoreCancelled = true)
    public void alGolpe(EntityDamageByEntityEvent e) {
        if (!enGta(e.getEntity())) return;
        Player atacante = TresModos.jugadorAtacante(e.getDamager());
        if (atacante == null || atacante == e.getEntity()) return;
        if ((esPolicia(e.getEntity()) || e.getEntity() instanceof Player) && getBuscado(atacante) == 0) crimen(atacante, 1);
    }

    @EventHandler
    public void alMorirEntidad(EntityDeathEvent e) {
        LivingEntity v = e.getEntity();
        if (!enGta(v) || v instanceof Player) return;
        Player asesino = v.getKiller();
        if (asesino == null) {
            Armas.Impacto imp = plugin.armas().ultimoImpacto(v.getUniqueId());
            if (imp != null) asesino = Bukkit.getPlayer(imp.autor());
        }
        if (esPolicia(v)) {
            e.getDrops().clear();
            e.setDroppedExp(0);
            if (asesino != null) {
                plugin.almacen().de(asesino).dinero += 25;
                crimen(asesino, 1);
            }
        } else if ((v instanceof Villager || v instanceof IronGolem) && asesino != null) {
            crimen(asesino, 1);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void alMorir(PlayerDeathEvent e) {
        Player v = e.getPlayer();
        if (!enGta(v)) return;
        DatosJugador dv = plugin.almacen().de(v);
        long perdida = dv.dinero / 10;
        dv.dinero -= perdida;
        setBuscado(v, 0);
        cancelarMision(v, "<red>Entrega fallida.");
        quitarAuto(v);

        Player asesino = v.getKiller();
        Armas.Impacto imp = plugin.armas().ultimoImpacto(v.getUniqueId());
        if (imp != null) asesino = Bukkit.getPlayer(imp.autor());
        String texto;
        if (asesino != null && asesino != v && enGta(asesino)) {
            plugin.almacen().de(asesino).dinero += perdida;
            crimen(asesino, 2);
            Util.barra(asesino, "<green>+" + Util.plata(perdida) + " <gray>robados a " + v.getName());
            texto = "<red>" + asesino.getName() + " <gray>mató a <white>" + v.getName();
        } else {
            texto = "<gray>" + v.getName() + " murió en la ciudad";
        }
        e.deathMessage(null);
        for (Player o : mundo().getPlayers()) Util.msg(o, "<dark_gray>[GTA] " + texto);
        if (perdida > 0) Util.msg(v, "<red>Gastos de hospital: -" + Util.plata(perdida));
    }

    private void tickPolicia() {
        int ahora = Bukkit.getCurrentTick();
        for (Player p : mundo().getPlayers()) {
            int n = getBuscado(p);
            if (n == 0 || p.isDead()) continue;
            if (ahora - ultimoCrimen.getOrDefault(p.getUniqueId(), ahora) > DECAE_TICKS) {
                setBuscado(p, n - 1);
                ultimoCrimen.put(p.getUniqueId(), ahora);
                n = getBuscado(p);
                if (n == 0) continue;
            }
            Set<UUID> suyos = policias.computeIfAbsent(p.getUniqueId(), u -> new HashSet<>());
            int vivos = 0;
            boolean hayBlindado = false;
            for (Iterator<UUID> it = suyos.iterator(); it.hasNext(); ) {
                Entity e = Bukkit.getEntity(it.next());
                if (e == null || !e.isValid() || e.getWorld() != p.getWorld()
                        || e.getLocation().distanceSquared(p.getLocation()) > 90 * 90) {
                    if (e != null) e.remove();
                    it.remove();
                    continue;
                }
                vivos++;
                if (e instanceof Ravager) hayBlindado = true;
                if (e instanceof Mob mob && mob.getTarget() != p) mob.setTarget(p);
            }
            int deseados = Math.min(12, n * 2);
            for (int i = 0; i < 2 && vivos < deseados; i++, vivos++) {
                Entity nuevo = spawnPolicia(p, n, !hayBlindado && n >= 5);
                if (nuevo instanceof Ravager) hayBlindado = true;
                if (nuevo != null) suyos.add(nuevo.getUniqueId());
            }
        }
    }

    private Entity spawnPolicia(Player p, int nivel, boolean blindado) {
        Location base = p.getLocation();
        World w = base.getWorld();
        int y = GeneradorCiudad.SUELO + 1;
        for (int intento = 0; intento < 10; intento++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            double dist = 18 + rnd.nextDouble() * 10;
            int x = (int) Math.floor(base.getX() + Math.cos(ang) * dist);
            int z = (int) Math.floor(base.getZ() + Math.sin(ang) * dist);
            if (!w.getBlockAt(x, y, z).isEmpty() || !w.getBlockAt(x, y + 1, z).isEmpty()
                    || !w.getBlockAt(x, y - 1, z).isSolid()) continue;
            Location l = new Location(w, x + 0.5, y, z + 0.5);
            Class<? extends Mob> clase = blindado ? Ravager.class
                    : (nivel >= 3 && rnd.nextBoolean()) ? Pillager.class : Vindicator.class;
            String nombre = blindado ? "<dark_blue><bold>Blindado" : clase == Pillager.class ? "<blue><bold>SWAT" : "<blue>Policía";
            return w.spawn(l, clase, m -> {
                m.customName(Util.mm(nombre));
                m.setCustomNameVisible(true);
                m.setRemoveWhenFarAway(true);
                m.getPersistentDataContainer().set(Claves.POLICIA, PersistentDataType.STRING, p.getUniqueId().toString());
                if (m.getEquipment() != null) {
                    m.getEquipment().setItemInMainHandDropChance(0f);
                    m.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
                    m.getEquipment().setHelmetDropChance(0f);
                }
                if (m instanceof org.bukkit.entity.Raider r) {
                    r.setCanJoinRaid(false);
                    r.setPatrolLeader(false);
                }
                m.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, PotionEffect.INFINITE_DURATION, 0, false, false));
                m.setTarget(p);
            });
        }
        return null;
    }

    /** Los policías solo persiguen a su buscado (y a quien los ataque). */
    @EventHandler
    public void alApuntar(EntityTargetEvent e) {
        if (!esPolicia(e.getEntity()) || !(e.getTarget() instanceof Player t)) return;
        String objetivo = e.getEntity().getPersistentDataContainer().get(Claves.POLICIA, PersistentDataType.STRING);
        if (getBuscado(t) == 0 && !t.getUniqueId().toString().equals(objetivo)) e.setCancelled(true);
    }

    private void quitarPolicia(UUID jugador) {
        Set<UUID> suyos = policias.remove(jugador);
        if (suyos == null) return;
        for (UUID id : suyos) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                e.getWorld().spawnParticle(Particle.POOF, e.getLocation().add(0, 1, 0), 8, 0.3, 0.5, 0.3, 0.02);
                e.remove();
            }
        }
    }

    // ------------------------------------------------------------------ misiones

    private void iniciarMision(Player p) {
        Location pos = p.getLocation();
        int bx = Math.floorDiv(pos.getBlockX(), GeneradorCiudad.P), bz = Math.floorDiv(pos.getBlockZ(), GeneradorCiudad.P);
        Location destino = null;
        int dist = 0;
        for (int i = 0; i < 40 && destino == null; i++) {
            int ix = bx + rnd.nextInt(15) - 7, iz = bz + rnd.nextInt(15) - 7;
            int x = ix * GeneradorCiudad.P + 5, z = iz * GeneradorCiudad.P + 5;
            if (Math.abs(x) > 940 || Math.abs(z) > 940) continue;
            int d = Math.abs(x - pos.getBlockX()) + Math.abs(z - pos.getBlockZ());
            if (d < 180 || d > 420) continue;
            destino = new Location(p.getWorld(), x + 0.5, GeneradorCiudad.SUELO + 1, z + 0.5);
            dist = d;
        }
        if (destino == null) {
            Util.msg(p, "<red>No encontré un destino. Probá desde otro lugar.");
            return;
        }
        long premio = Math.round((150 + dist * 1.2) / 10.0) * 10;
        int segundos = (int) (dist / 4.2) + 25;
        misiones.put(p.getUniqueId(), new Mision(destino, Bukkit.getCurrentTick() + segundos * 20, premio));
        p.setCompassTarget(destino);
        p.getInventory().addItem(Util.marcar(Util.item(Material.CHEST, "<yellow><bold>Paquete",
                "Llevalo a la esquina que marca el celular."), Claves.PAQUETE, "1"));
        Util.titulo(p, "<yellow><bold>ENTREGA", "<gray>" + dist + " m · " + Util.tiempo(segundos) + " · " + Util.plata(premio), 200, 2000, 400);
        Util.msg(p, "<yellow>Seguí la aguja del celular hasta el haz de luz. <gray>Si morís, perdés el paquete.");
    }

    private void cancelarMision(Player p, String motivo) {
        Mision m = misiones.remove(p.getUniqueId());
        if (m == null) return;
        p.getInventory().forEach(it -> {
            if (Util.marca(it, Claves.PAQUETE) != null) it.setAmount(0);
        });
        p.setCompassTarget(mundo().getSpawnLocation());
        if (motivo != null) Util.msg(p, motivo);
    }

    private void tickMisiones() {
        int ahora = Bukkit.getCurrentTick();
        for (Player p : mundo().getPlayers()) {
            Mision m = misiones.get(p.getUniqueId());
            if (m == null) continue;
            if (ahora > m.fin()) {
                cancelarMision(p, "<red>Se acabó el tiempo. Entrega fallida.");
                p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
                continue;
            }
            Location l = p.getLocation();
            double dx = l.getX() - m.destino().getX(), dz = l.getZ() - m.destino().getZ();
            if (dx * dx + dz * dz < 16) {
                cancelarMision(p, null);
                plugin.almacen().de(p).dinero += m.premio();
                Util.titulo(p, "<green><bold>¡ENTREGADO!", "<green>+" + Util.plata(m.premio()), 100, 1800, 500);
                p.playSound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
                continue;
            }
            if (ahora % 20 < 10) {
                new ParticleBuilder(Particle.END_ROD).location(m.destino().clone().add(0, 4, 0)).count(25)
                        .offset(0.15, 4, 0.15).speed(0).receivers(p).force(true).spawn();
            }
        }
    }

    // ------------------------------------------------------------------ sidebar

    @Override
    public String tituloSidebar(Player p) {
        return "<gold><bold>GTA</bold> <gray>Ciudad";
    }

    @Override
    public List<String> lineasSidebar(Player p) {
        DatosJugador d = plugin.almacen().de(p);
        List<String> l = new ArrayList<>();
        l.add("<white>Dinero: <green>" + Util.plata(d.dinero));
        l.add("<white>Búsqueda: " + Util.estrellas(getBuscado(p), MAX_ESTRELLAS));
        l.add("");
        Mision m = misiones.get(p.getUniqueId());
        if (m != null) {
            Location pl = p.getLocation();
            int dist = (int) Math.round(Math.hypot(pl.getX() - m.destino().getX(), pl.getZ() - m.destino().getZ()));
            int seg = Math.max(0, (m.fin() - Bukkit.getCurrentTick()) / 20);
            l.add("<yellow>Entrega: <white>" + dist + " m");
            l.add("<yellow>Tiempo: <white>" + Util.tiempo(seg));
            l.add("<yellow>Pago: <green>" + Util.plata(m.premio()));
        } else {
            l.add("<gray>Sin misión activa");
        }
        l.add("");
        l.add("<gray>Celular: brújula");
        l.add("<gray>/lobby para salir");
        return l;
    }
}
