package ar.tresmodos.guerra;

import ar.tresmodos.Armas;
import ar.tresmodos.DatosJugador;
import ar.tresmodos.Menu;
import ar.tresmodos.Modo;
import ar.tresmodos.ModoJuego;
import ar.tresmodos.TresModos;
import ar.tresmodos.Util;
import ar.tresmodos.mundo.GeneradorValle;
import ar.tresmodos.mundo.ValleDeHierro;
import ar.tresmodos.shooter.ClasesShooter;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Guerra en el Valle de Hierro: captura la bandera, Azul contra Rojo. Gana el primero a 3 capturas o
 * el que tenga más a los 15 min (empate: muerte súbita de 3 min). Para capturar, tu bandera tiene que
 * estar en casa. Al caer quedás 10 s en el piso: un Fusilero te puede levantar.
 */
public class ModoGuerra implements ModoJuego, Listener {
    public static final int DURACION = 20 * 60 * 15;
    public static final int MUERTE_SUBITA = 20 * 60 * 3;
    public static final int REAPARICION = 120;
    public static final int CAIDO = 200;
    public static final int CAPTURAS = 3;
    private static final NamespacedKey LENTO = new NamespacedKey("tresmodos", "guerra_caido");

    public enum Equipo {
        AZUL("Azul", "<blue>", -1, Color.fromRGB(40, 70, 200), Material.BLUE_BANNER, BossBar.Color.BLUE),
        ROJO("Rojo", "<red>", 1, Color.fromRGB(190, 30, 30), Material.RED_BANNER, BossBar.Color.RED);

        public final String nombre, color;
        public final int lado;
        public final Color tinte;
        public final Material bandera;
        public final BossBar.Color barra;

        Equipo(String nombre, String color, int lado, Color tinte, Material bandera, BossBar.Color barra) {
            this.nombre = nombre;
            this.color = color;
            this.lado = lado;
            this.tinte = tinte;
            this.bandera = bandera;
            this.barra = barra;
        }

        public Equipo otro() {
            return this == AZUL ? ROJO : AZUL;
        }

        public int baseX() {
            return lado * ValleDeHierro.BASE_X;
        }
    }

    public enum Clase {
        FUSILERO("Fusilero", "Infantería: revive y cura. Fusil, granadas, granada antitanque y botiquín.", Material.IRON_SWORD,
                List.of(Armas.Tipo.M4A1, Armas.Tipo.AK47, Armas.Tipo.M249)),
        ANTITANQUE("Antitanque", "Caza tanques: carabina, RPG-7, AT4 y minas antitanque.", Material.FIRE_CHARGE,
                List.of(Armas.Tipo.MP5, Armas.Tipo.P90)),
        INGENIERO("Ingeniero", "Repara, demuele y derriba aviones: subfusil, llave, C4 y Stinger.", Material.ANVIL,
                List.of(Armas.Tipo.MP5, Armas.Tipo.VECTOR, Armas.Tipo.P90, Armas.Tipo.REMINGTON, Armas.Tipo.ESCOPETA)),
        TIRADOR("Tirador", "Reconocimiento: fusil de precisión, binoculares y claymore.", Material.SPYGLASS,
                List.of(Armas.Tipo.FRANCOTIRADOR, Armas.Tipo.M24));

        public final String nombre, desc;
        public final Material icono;
        /** Principales que puede llevar (la primera es la de inicio). */
        public final List<Armas.Tipo> armas;

        Clase(String nombre, String desc, Material icono, List<Armas.Tipo> armas) {
            this.nombre = nombre;
            this.desc = desc;
            this.icono = icono;
            this.armas = armas;
        }
    }

    enum Estado { EN_CURSO, MUERTE_SUBITA, FIN }

    enum EstadoBandera { EN_BASE, LLEVADA, CAIDA }

    final class Bandera {
        final Equipo equipo;
        EstadoBandera estado = EstadoBandera.EN_BASE;
        UUID portador;
        Location caida;
        int vuelve;
        ItemDisplay visual;

        Bandera(Equipo equipo) {
            this.equipo = equipo;
        }

        Location mastil() {
            int x = equipo.baseX();
            return new Location(mundo(), x + 0.5, ValleDeHierro.altura(x, 0) + 1, 0.5);
        }

        Location dondeEsta() {
            return switch (estado) {
                case EN_BASE -> mastil();
                case CAIDA -> caida;
                case LLEVADA -> {
                    Player p = Bukkit.getPlayer(portador);
                    yield p == null ? mastil() : p.getLocation();
                }
            };
        }
    }

    private record Caido(int hasta, UUID autor, String arma) {}

    private final TresModos plugin;
    private final Random rnd = new Random();
    private final Map<UUID, Equipo> equipos = new HashMap<>();
    private final Map<UUID, Integer> esperando = new HashMap<>();
    private final Set<UUID> quierePuesto = new HashSet<>();
    private final Map<UUID, Caido> caidos = new HashMap<>();
    private final Map<UUID, Integer> curaLista = new HashMap<>();
    private final Map<UUID, Integer> cajaLista = new HashMap<>();
    private final Map<Equipo, Integer> capturas = new EnumMap<>(Equipo.class);
    private final Map<Equipo, Bandera> banderas = new EnumMap<>(Equipo.class);
    private final Set<Long> rotos = new HashSet<>();
    private final List<Entity> decorado = new ArrayList<>();
    private final ArsenalGuerra arsenal;
    private Estado estado = Estado.EN_CURSO;
    private int inicio, finSubita, reinicio;
    private boolean enMarcha;
    // Puesto avanzado en la plaza.
    private Equipo puesto;
    private Equipo tomando;
    private int tomandoDesde;
    private BossBar barraPuesto;

    public ModoGuerra(TresModos plugin) {
        this.plugin = plugin;
        for (Equipo e : Equipo.values()) {
            banderas.put(e, new Bandera(e));
            capturas.put(e, 0);
        }
        this.arsenal = new ArsenalGuerra(plugin, this);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 5);
        Bukkit.getScheduler().runTaskLater(plugin, this::prepararMundo, 2);
    }

    public World mundo() {
        return plugin.mundos().de(Modo.GUERRA);
    }

    public ArsenalGuerra arsenal() {
        return arsenal;
    }

    private boolean enGuerra(Entity e) {
        return Modo.de(e.getWorld()) == Modo.GUERRA;
    }

    public List<Player> jugadores() {
        return new ArrayList<>(mundo().getPlayers());
    }

    public Equipo equipo(Player p) {
        return equipos.get(p.getUniqueId());
    }

    /** Mismo equipo (sin fuego amigo). Fuera de Guerra siempre es false. */
    public boolean aliados(Player a, Player b) {
        if (a == null || b == null || a == b || !enGuerra(a) || !enGuerra(b)) return false;
        Equipo ea = equipos.get(a.getUniqueId()), eb = equipos.get(b.getUniqueId());
        return ea != null && ea == eb;
    }

    public boolean caido(Player p) {
        return caidos.containsKey(p.getUniqueId());
    }

    /** No se dispara caído, como espectador, en la espera ni con la partida terminada. */
    public boolean bloqueaDisparo(Player p) {
        if (!enGuerra(p)) return false;
        return estado == Estado.FIN || caido(p) || p.getGameMode() != GameMode.ADVENTURE || arsenal.ocupado(p);
    }

    public boolean esPortador(Player p) {
        for (Bandera b : banderas.values()) if (p.getUniqueId().equals(b.portador)) return true;
        return false;
    }

    public Clase clase(Player p) {
        String c = plugin.almacen().de(p).guerraClase;
        if (c != null) {
            try {
                return Clase.valueOf(c);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Clase.FUSILERO;
    }

    // ------------------------------------------------------------------ ModoJuego

    @Override public Modo modo() { return Modo.GUERRA; }
    @Override public boolean guardaEstado() { return false; }
    @Override public GameMode modoJuego() { return GameMode.ADVENTURE; }

    @Override
    public Location ubicacionEntrada(Player p) {
        return aparicion(asignar(p));
    }

    @Override
    public Location respawn(Player p) {
        Equipo e = equipo(p);
        return e == null ? mundo().getSpawnLocation() : bunker(e).add(0, 6, 0);
    }

    @Override
    public void kitInicial(Player p) {
        darEquipo(p);
    }

    @Override
    public void alEntrar(Player p, boolean primeraVez) {
        Equipo e = asignar(p);
        if (!enMarcha) nuevaPartida();
        Util.titulo(p, "<dark_green><bold>VALLE DE HIERRO", "<gray>Sos del equipo " + e.color + e.nombre
                + "<gray> · robá la bandera enemiga", 300, 3000, 700);
        Util.msg(p, "<dark_green>Guerra: <gray>robá la bandera " + e.otro().color + e.otro().nombre
                + "<gray> y llevala a tu mástil (la tuya tiene que estar en casa). Clase: <white>" + clase(p).nombre
                + "<gray> (libro o <white>/clase<gray>). Al caer, un Fusilero te puede levantar.");
        for (Player o : jugadores()) if (o != p) Util.msg(o, e.color + p.getName() + " <gray>se sumó al equipo " + e.color + e.nombre);
        if (barraPuesto != null) p.showBossBar(barraPuesto);
    }

    @Override
    public void alSalir(Player p) {
        UUID id = p.getUniqueId();
        soltarBandera(p, false);
        terminarCaido(p, false);
        equipos.remove(id);
        esperando.remove(id);
        quierePuesto.remove(id);
        curaLista.remove(id);
        arsenal.olvidar(p);
        plugin.armas().dejarDeApuntar(p);
        plugin.movilidad().olvidar(p);
        if (barraPuesto != null) p.hideBossBar(barraPuesto);
        p.removePotionEffect(PotionEffectType.GLOWING);
        if (p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(null);
    }

    @Override
    public void alReaparecer(Player p) {
        if (estado == Estado.FIN) {
            aparecerEnBase(p);
            return;
        }
        p.getInventory().clear();
        p.setGameMode(GameMode.SPECTATOR);
        esperando.put(p.getUniqueId(), Bukkit.getCurrentTick() + REAPARICION);
        boolean puede = puesto != null && puesto == equipo(p);
        Util.titulo(p, "<red><bold>CAÍSTE", "<gray>Reaparecés en 6 s" + (puede ? " · <white>Shift<gray>: puesto avanzado" : ""), 0, 2000, 300);
    }

    // ------------------------------------------------------------------ equipos y aparición

    private Equipo asignar(Player p) {
        Equipo e = equipos.get(p.getUniqueId());
        if (e != null) return e;
        int azul = 0, rojo = 0;
        for (Equipo x : equipos.values()) if (x == Equipo.AZUL) azul++; else rojo++;
        e = azul < rojo ? Equipo.AZUL : rojo < azul ? Equipo.ROJO : (rnd.nextBoolean() ? Equipo.AZUL : Equipo.ROJO);
        equipos.put(p.getUniqueId(), e);
        return e;
    }

    /** Centro del búnker de aparición del equipo. */
    public Location bunker(Equipo e) {
        int x = e.baseX() + e.lado * 26;
        Location l = new Location(mundo(), x + 0.5, ValleDeHierro.altura(x, 0) + 1, 0.5);
        l.setYaw(e.lado < 0 ? -90 : 90);
        return l;
    }

    private Location aparicion(Equipo e) {
        Location b = bunker(e);
        return b.add(rnd.nextInt(7) - 3, 0, rnd.nextInt(7) - 3);
    }

    private Location puestoAvanzado() {
        return new Location(mundo(), 0.5, ValleDeHierro.altura(6, 6) + 1, 6.5);
    }

    private void aparecerEnBase(Player p) {
        esperando.remove(p.getUniqueId());
        Equipo e = asignar(p);
        if (p.getGameMode() == GameMode.SPECTATOR) p.setSpectatorTarget(null);
        p.setGameMode(GameMode.ADVENTURE);
        boolean enPuesto = quierePuesto.remove(p.getUniqueId()) && puesto == e;
        Location l = enPuesto ? puestoAvanzado().add(rnd.nextInt(5) - 2, 0, rnd.nextInt(5) - 2) : aparicion(e);
        p.teleport(l);
        p.setFireTicks(0);
        p.setFallDistance(0);
        for (PotionEffect ef : p.getActivePotionEffects()) p.removePotionEffect(ef.getType());
        AttributeInstance vida = p.getAttribute(Attribute.MAX_HEALTH);
        if (vida != null) p.setHealth(vida.getValue());
        darEquipo(p);
        plugin.sidebar().actualizar(p);
    }

    /** Uniforme del equipo y el equipo de la clase. */
    public void darEquipo(Player p) {
        Equipo e = asignar(p);
        Clase c = clase(p);
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setHelmet(uniforme(Material.LEATHER_HELMET, e));
        inv.setChestplate(uniforme(Material.LEATHER_CHESTPLATE, e));
        inv.setLeggings(uniforme(Material.LEATHER_LEGGINGS, e));
        inv.setBoots(uniforme(Material.LEATHER_BOOTS, e));
        var eq = plugin.shooter().equipamiento();
        switch (c) {
            case FUSILERO -> {
                inv.setItem(0, arma(principal(p, c), p));
                inv.setItem(1, arma(secundaria(p), p));
                inv.setItem(2, eq.letal(ClasesShooter.Letal.FRAG, 2));
                inv.setItem(3, eq.tactico(ClasesShooter.Tactico.HUMO, 1));
                inv.setItem(4, ArmaGuerra.GRANADA_AT.crear(1));
                inv.setItem(5, ArmaGuerra.BOTIQUIN.crear(1));
            }
            case ANTITANQUE -> {
                inv.setItem(0, arma(principal(p, c), p));
                inv.setItem(1, ArmaGuerra.RPG7.crear(3));
                inv.setItem(2, ArmaGuerra.AT4.crear(1));
                inv.setItem(3, ArmaGuerra.JAVELIN.crear(2));
                inv.setItem(4, ArmaGuerra.MINA_AT.crear(2));
            }
            case INGENIERO -> {
                inv.setItem(0, arma(principal(p, c), p));
                inv.setItem(1, ArmaGuerra.LLAVE.crear(1));
                inv.setItem(2, ArmaGuerra.C4.crear(3));
                inv.setItem(3, ArmaGuerra.DETONADOR.crear(1));
                inv.setItem(4, ArmaGuerra.STINGER.crear(2));
            }
            case TIRADOR -> {
                inv.setItem(0, arma(principal(p, c), p));
                inv.setItem(1, arma(secundaria(p), p));
                inv.setItem(2, ArmaGuerra.BINOCULARES.crear(1));
                inv.setItem(3, eq.letal(ClasesShooter.Letal.CLAYMORE, 1));
            }
        }
        inv.setItem(8, libro());
        plugin.armas().rellenarReservas(p);
    }

    /** Principal elegida para la clase (o la de inicio). */
    public Armas.Tipo principal(Player p, Clase c) {
        String s = plugin.almacen().de(p).guerraArmas.get(c.name());
        for (Armas.Tipo t : c.armas) if (t.name().equals(s)) return t;
        return c.armas.getFirst();
    }

    public Armas.Tipo secundaria(Player p) {
        String s = plugin.almacen().de(p).guerraArmas.get("SECUNDARIA");
        for (Armas.Tipo t : ClasesShooter.SECUNDARIAS) if (t.name().equals(s)) return t;
        return Armas.Tipo.PISTOLA;
    }

    private ItemStack arma(Armas.Tipo t, Player p) {
        ItemStack it = Armas.crear(t, plugin.almacen().de(p).accesorios(t));
        Armas.setReservaItem(it, Armas.capacidad(t, Armas.accesorios(it)) * 3);
        return it;
    }

    private static ItemStack uniforme(Material m, Equipo e) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta instanceof LeatherArmorMeta lm) lm.setColor(m == Material.LEATHER_CHESTPLATE || m == Material.LEATHER_HELMET
                ? e.tinte : Color.fromRGB(70, 75, 50));
        meta.setUnbreakable(true);
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack libro() {
        return Util.item(Material.BOOK, "<gold><bold>Clase", "<gray>Clic derecho para cambiar de clase.",
                "<gray>Se aplica al reaparecer (o al toque si estás en tu base).");
    }

    public void abrirClases(Player p) {
        Menu m = new Menu(4, "<dark_gray>Clase de infantería");
        Clase actual = clase(p);
        int slot = 10;
        for (Clase c : Clase.values()) {
            m.poner(slot, Util.item(c.icono, (c == actual ? "<green>" : "<white>") + "<bold>" + c.nombre, "<gray>" + c.desc,
                    "", c == actual ? "<green>Elegida" : "<yellow>Clic para elegir"), pl -> {
                plugin.almacen().de(pl).guerraClase = c.name();
                pl.closeInventory();
                reequipar(pl, "<green>Ahora sos " + c.nombre, "<gray>Vas a ser " + c.nombre + " al reaparecer.");
            });
            Armas.Tipo t = principal(p, c);
            m.poner(slot + 9, Util.item(t.material, "<aqua><bold>Principal", "<white>" + t.nombre,
                    "<gray>" + (c.armas.size() > 1 ? "Clic: cambiar (" + c.armas.size() + " opciones)" : "Única opción")), pl -> {
                if (c.armas.size() < 2) return;
                Armas.Tipo sig = c.armas.get((c.armas.indexOf(principal(pl, c)) + 1) % c.armas.size());
                plugin.almacen().de(pl).guerraArmas.put(c.name(), sig.name());
                if (c == clase(pl)) reequipar(pl, "<green>Principal: " + sig.nombre, "<gray>Vas a llevar " + sig.nombre + " al reaparecer.");
                abrirClases(pl);
            });
            slot += 2;
        }
        Armas.Tipo sec = secundaria(p);
        m.poner(31, Util.item(sec.material, "<aqua><bold>Pistola", "<white>" + sec.nombre,
                "<gray>Fusilero y Tirador. Clic: cambiar"), pl -> {
            var lista = ClasesShooter.SECUNDARIAS;
            Armas.Tipo sig = lista.get((lista.indexOf(secundaria(pl)) + 1) % lista.size());
            plugin.almacen().de(pl).guerraArmas.put("SECUNDARIA", sig.name());
            reequipar(pl, "<green>Pistola: " + sig.nombre, "<gray>Vas a llevar " + sig.nombre + " al reaparecer.");
            abrirClases(pl);
        });
        m.abrir(p);
    }

    /** En la base (y de pie, sin la bandera) cambia el equipo al instante; si no, al reaparecer. */
    private void reequipar(Player pl, String ahora, String despues) {
        Equipo e = equipo(pl);
        boolean enBase = e != null && pl.getLocation().distanceSquared(bunker(e)) < 30 * 30;
        if (enBase && pl.getGameMode() == GameMode.ADVENTURE && !caido(pl) && !esPortador(pl)) {
            darEquipo(pl);
            Util.barra(pl, ahora);
        } else {
            Util.barra(pl, despues);
        }
    }

    // ------------------------------------------------------------------ partida

    private void prepararMundo() {
        World w = mundo();
        for (Entity e : w.getEntities()) {
            if (e.getPersistentDataContainer().has(ar.tresmodos.Claves.DECORADO)) e.remove();
        }
        for (Equipo e : Equipo.values()) {
            Location m = banderas.get(e).mastil();
            w.getChunkAt(m);
            TextDisplay t = w.spawn(m.clone().add(0, 8.4, 0), TextDisplay.class, d -> {
                d.text(Util.mm(e.color + "<bold>BANDERA " + e.nombre.toUpperCase()));
                d.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                d.getPersistentDataContainer().set(ar.tresmodos.Claves.DECORADO, PersistentDataType.BYTE, (byte) 1);
            });
            decorado.add(t);
        }
        Location pa = puestoAvanzado();
        w.getChunkAt(pa);
        TextDisplay t = w.spawn(pa.clone().add(0, 3, -6), TextDisplay.class, d -> {
            d.text(Util.mm("<gold><bold>PUESTO AVANZADO\n<gray>Quedate 10 s sin enemigos para tomarlo"));
            d.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            d.getPersistentDataContainer().set(ar.tresmodos.Claves.DECORADO, PersistentDataType.BYTE, (byte) 1);
        });
        decorado.add(t);
    }

    private void nuevaPartida() {
        enMarcha = true;
        estado = Estado.EN_CURSO;
        inicio = Bukkit.getCurrentTick();
        for (Equipo e : Equipo.values()) capturas.put(e, 0);
        for (Bandera b : banderas.values()) volverABase(b, false);
        puesto = null;
        tomando = null;
        restaurarMapa();
        arsenal.reiniciar();
        // Clima al azar: día, atardecer o lluvia.
        World w = mundo();
        double c = rnd.nextDouble();
        w.setTime(c < 0.25 ? 12300 : 6000);
        w.setStorm(c > 0.75);
        // Equipos parejos de nuevo.
        List<Player> js = jugadores();
        java.util.Collections.shuffle(js, rnd);
        equipos.clear();
        for (Player p : js) asignar(p);
        for (Player p : js) {
            caidos.remove(p.getUniqueId());
            plugin.movilidad().levantar(p);
            aparecerEnBase(p);
            Equipo e = equipo(p);
            Util.titulo(p, "<dark_green><bold>¡A LA CARGA!", "<gray>Equipo " + e.color + e.nombre + "<gray> · 3 capturas o 15 min",
                    300, 2500, 700);
            p.playSound(p, Sound.EVENT_RAID_HORN, 0.7f, 1f);
        }
        actualizarBarraPuesto();
    }

    private void terminar(Equipo ganador, String motivo) {
        estado = Estado.FIN;
        reinicio = Bukkit.getCurrentTick() + 300;
        for (Player p : jugadores()) {
            Equipo e = equipo(p);
            if (ganador == null) Util.titulo(p, "<yellow><bold>EMPATE", "<gray>" + motivo, 300, 5000, 1000);
            else if (e == ganador) {
                Util.titulo(p, "<gold><bold>¡VICTORIA!", "<gray>" + motivo, 300, 5000, 1000);
                plugin.almacen().de(p).guerraVictorias++;
            } else Util.titulo(p, "<red><bold>DERROTA", "<gray>" + motivo, 300, 5000, 1000);
            p.playSound(p, e == ganador ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_WITHER_DEATH, 0.8f, 1f);
        }
        if (ganador != null) {
            Location m = banderas.get(ganador).mastil();
            for (int i = 0; i < 5; i++) {
                int k = i;
                Bukkit.getScheduler().runTaskLater(plugin, () -> fuegoArtificial(m.clone().add(rnd.nextInt(9) - 4, 2, rnd.nextInt(9) - 4), ganador.tinte), k * 10L);
            }
        }
        anunciar(ganador == null ? "<yellow>Empate: " + motivo : ganador.color + "<bold>Ganó el equipo " + ganador.nombre + "</bold> <gray>(" + motivo + ")");
    }

    private void fuegoArtificial(Location l, Color c) {
        Firework f = l.getWorld().spawn(l, Firework.class);
        FireworkMeta m = f.getFireworkMeta();
        m.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).withColor(c).withFade(Color.WHITE).build());
        m.setPower(1);
        f.setFireworkMeta(m);
    }

    private void anunciar(String s) {
        for (Player p : jugadores()) Util.msg(p, s);
    }

    // ------------------------------------------------------------------ banderas

    private void volverABase(Bandera b, boolean anunciar) {
        if (b.portador != null) {
            Player p = Bukkit.getPlayer(b.portador);
            if (p != null) p.removePotionEffect(PotionEffectType.GLOWING);
        }
        b.estado = EstadoBandera.EN_BASE;
        b.portador = null;
        b.caida = null;
        ponerVisual(b);
        if (anunciar) {
            anunciar(b.equipo.color + "La bandera " + b.equipo.nombre + " volvió a su base.");
            for (Player p : jugadores()) p.playSound(p, Sound.BLOCK_NOTE_BLOCK_CHIME, 0.7f, equipo(p) == b.equipo ? 1.4f : 0.7f);
        }
    }

    private void ponerVisual(Bandera b) {
        if (b.visual != null && b.visual.isValid()) b.visual.remove();
        Location l = switch (b.estado) {
            case EN_BASE -> b.mastil().add(0, 6.6, 0);
            case CAIDA -> b.caida.clone().add(0, 0.9, 0);
            case LLEVADA -> null;
        };
        World w = mundo();
        Location donde = l != null ? l : b.dondeEsta().clone().add(0, 2.2, 0);
        w.getChunkAt(donde);
        b.visual = w.spawn(donde, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(b.equipo.bandera));
            d.setBillboard(org.bukkit.entity.Display.Billboard.VERTICAL);
            d.setPersistent(false);
            d.setGlowing(true);
            d.setGlowColorOverride(b.equipo.tinte);
            d.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.AxisAngle4f(),
                    new org.joml.Vector3f(1.4f, 1.4f, 1.4f), new org.joml.AxisAngle4f()));
        });
        if (b.estado == EstadoBandera.LLEVADA) {
            Player p = Bukkit.getPlayer(b.portador);
            if (p != null) p.addPassenger(b.visual);
        }
    }

    private void agarrar(Bandera b, Player p) {
        b.estado = EstadoBandera.LLEVADA;
        b.portador = p.getUniqueId();
        b.caida = null;
        ponerVisual(b);
        p.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, PotionEffect.INFINITE_DURATION, 0, false, false));
        plugin.armas().dejarDeApuntar(p);
        Equipo e = equipo(p);
        anunciar(e.color + p.getName() + " <white>robó la bandera " + b.equipo.color + b.equipo.nombre + "<white>!");
        for (Player o : jugadores()) {
            boolean propia = equipo(o) == b.equipo;
            Util.titulo(o, "", propia ? "<red>¡Robaron nuestra bandera!" : "<green>¡Tenemos su bandera!", 100, 1800, 400);
            o.playSound(o, propia ? Sound.ENTITY_ELDER_GUARDIAN_CURSE : Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f);
        }
    }

    /** El portador la suelta donde está (muerto, caído o desconectado). */
    private void soltarBandera(Player p, boolean anunciar) {
        for (Bandera b : banderas.values()) {
            if (!p.getUniqueId().equals(b.portador)) continue;
            p.removePotionEffect(PotionEffectType.GLOWING);
            if (b.visual != null) p.removePassenger(b.visual);
            b.estado = EstadoBandera.CAIDA;
            b.portador = null;
            Location l = p.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
            // Si cayó al agua o al vacío, vuelve sola.
            if (l.getY() < ValleDeHierro.AGUA - 4) {
                volverABase(b, true);
                continue;
            }
            b.caida = l;
            b.vuelve = Bukkit.getCurrentTick() + 600;
            ponerVisual(b);
            if (anunciar) anunciar(b.equipo.color + "La bandera " + b.equipo.nombre + " quedó en el piso.");
        }
    }

    private void capturar(Player p, Bandera robada) {
        Equipo e = equipo(p);
        p.removePotionEffect(PotionEffectType.GLOWING);
        if (robada.visual != null) p.removePassenger(robada.visual);
        int n = capturas.merge(e, 1, Integer::sum);
        plugin.almacen().de(p).guerraCapturas++;
        volverABase(robada, false);
        anunciar(e.color + "<bold>¡" + p.getName() + " capturó la bandera " + robada.equipo.nombre + "!</bold> <gray>("
                + capturas.get(Equipo.AZUL) + " – " + capturas.get(Equipo.ROJO) + ")");
        for (Player o : jugadores()) {
            Util.titulo(o, e.color + "<bold>¡CAPTURA!", "<gray>Azul " + capturas.get(Equipo.AZUL) + " – " + capturas.get(Equipo.ROJO) + " Rojo",
                    200, 2500, 600);
            o.playSound(o, equipo(o) == e ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_BLAZE_DEATH, 0.8f, 1f);
        }
        fuegoArtificial(banderas.get(e).mastil().add(0, 3, 0), e.tinte);
        if (estado == Estado.MUERTE_SUBITA) terminar(e, "capturó en la muerte súbita");
        else if (n >= CAPTURAS) terminar(e, "llegó a " + CAPTURAS + " capturas");
    }

    private void tickBanderas() {
        int ahora = Bukkit.getCurrentTick();
        for (Bandera b : banderas.values()) {
            switch (b.estado) {
                case CAIDA -> {
                    if (ahora >= b.vuelve) {
                        volverABase(b, true);
                        continue;
                    }
                    for (Player p : jugadores()) {
                        if (!vivo(p) || p.getLocation().distanceSquared(b.caida) > 2.2 * 2.2) continue;
                        if (equipo(p) == b.equipo) {
                            volverABase(b, false);
                            anunciar(b.equipo.color + p.getName() + " devolvió su bandera.");
                        } else if (!arsenal.enVehiculoProhibido(p)) {
                            agarrar(b, p);
                        }
                        break;
                    }
                }
                case EN_BASE -> {
                    Location m = b.mastil();
                    for (Player p : jugadores()) {
                        if (!vivo(p) || equipo(p) == b.equipo || esPortador(p) || arsenal.enVehiculoProhibido(p)) continue;
                        Location l = p.getLocation();
                        if (Math.hypot(l.getX() - m.getX(), l.getZ() - m.getZ()) < 2.2 && Math.abs(l.getY() - m.getY()) < 4) {
                            agarrar(b, p);
                            break;
                        }
                    }
                }
                case LLEVADA -> {
                    Player p = Bukkit.getPlayer(b.portador);
                    if (p == null || !enGuerra(p) || p.isDead()) {
                        volverABase(b, true);
                        continue;
                    }
                    if (b.visual == null || !b.visual.isValid() || !p.getPassengers().contains(b.visual)) ponerVisual(b);
                    p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 2.2, 0), 3, 0.2, 0.2, 0.2, 0,
                            new Particle.DustOptions(b.equipo.tinte, 1.4f));
                    // Captura: en tu mástil, con tu bandera en casa.
                    Equipo propio = equipo(p);
                    Bandera mia = banderas.get(propio);
                    Location m = mia.mastil();
                    Location l = p.getLocation();
                    if (Math.hypot(l.getX() - m.getX(), l.getZ() - m.getZ()) < 3 && Math.abs(l.getY() - m.getY()) < 4) {
                        if (mia.estado == EstadoBandera.EN_BASE) capturar(p, b);
                        else if (ahora % 40 < 5) Util.barra(p, "<red>Tu bandera no está en casa: recuperala para capturar.");
                    }
                }
            }
        }
    }

    private boolean vivo(Player p) {
        return !p.isDead() && p.getGameMode() == GameMode.ADVENTURE && !caido(p);
    }

    // ------------------------------------------------------------------ puesto avanzado

    private void tickPuesto() {
        Location c = puestoAvanzado();
        Set<Equipo> adentro = new HashSet<>();
        for (Player p : jugadores()) {
            if (!vivo(p)) continue;
            Location l = p.getLocation();
            if (Math.hypot(l.getX() - c.getX(), l.getZ() - c.getZ()) < 6 && Math.abs(l.getY() - c.getY()) < 4) adentro.add(equipo(p));
        }
        int ahora = Bukkit.getCurrentTick();
        Equipo solo = adentro.size() == 1 ? adentro.iterator().next() : null;
        if (solo == null || solo == puesto) {
            if (tomando != null && (solo == null || solo == puesto)) {
                tomando = null;
                actualizarBarraPuesto();
            }
        } else if (tomando != solo) {
            tomando = solo;
            tomandoDesde = ahora;
        } else if (ahora - tomandoDesde >= 200) {
            puesto = solo;
            tomando = null;
            anunciar(solo.color + "El equipo " + solo.nombre + " tomó el puesto avanzado de la plaza.");
            for (Player p : jugadores()) p.playSound(p, Sound.BLOCK_BEACON_POWER_SELECT, 0.6f, equipo(p) == solo ? 1.4f : 0.6f);
        }
        actualizarBarraPuesto();
        // Partículas del círculo con el color del dueño.
        Color col = puesto == null ? Color.WHITE : puesto.tinte;
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI / 12;
            mundo().spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * 6, 0.2, Math.sin(a) * 6), 1, 0, 0, 0, 0,
                    new Particle.DustOptions(col, 1.2f));
        }
    }

    private void actualizarBarraPuesto() {
        if (barraPuesto == null) barraPuesto = BossBar.bossBar(Util.mm(""), 0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
        String dueño = puesto == null ? "<gray>nadie" : puesto.color + puesto.nombre;
        if (tomando != null) {
            float prog = Math.min(1f, (Bukkit.getCurrentTick() - tomandoDesde) / 200f);
            barraPuesto.name(Util.mm("<gold>Puesto avanzado: " + tomando.color + "tomando… <gray>(de " + dueño + "<gray>)"));
            barraPuesto.color(tomando.barra);
            barraPuesto.progress(prog);
        } else {
            barraPuesto.name(Util.mm("<gold>Puesto avanzado: " + dueño));
            barraPuesto.color(puesto == null ? BossBar.Color.WHITE : puesto.barra);
            barraPuesto.progress(puesto == null ? 0f : 1f);
        }
        for (Player p : jugadores()) p.showBossBar(barraPuesto);
    }

    // ------------------------------------------------------------------ caído y revivir

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void alDanio(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !enGuerra(p)) return;
        if (estado == Estado.FIN || p.getGameMode() != GameMode.ADVENTURE) {
            e.setCancelled(true);
            return;
        }
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && p.getFallDistance() < 6) e.setDamage(e.getDamage() * 0.5);
        if (caido(p)) {
            // Rematar a un caído: cualquier golpe lo mata.
            if (e instanceof EntityDamageByEntityEvent) e.setDamage(1000);
            else e.setCancelled(true);
            return;
        }
        if (e.getFinalDamage() < p.getHealth() || e.getCause() == EntityDamageEvent.DamageCause.VOID) return;
        if (arsenal.enVehiculo(p) || e.getFinalDamage() > 60) return; // dentro de un vehículo destruido o volado: muerte directa
        e.setCancelled(true);
        Armas.Impacto imp = plugin.armas().ultimoImpacto(p.getUniqueId());
        caer(p, imp == null ? null : imp.autor(), imp == null ? "?" : imp.arma());
    }

    private void caer(Player p, UUID autor, String arma) {
        caidos.put(p.getUniqueId(), new Caido(Bukkit.getCurrentTick() + CAIDO, autor, arma));
        soltarBandera(p, true);
        plugin.armas().dejarDeApuntar(p);
        p.setHealth(Math.min(p.getHealth(), 6));
        plugin.movilidad().tirar(p);
        AttributeInstance vel = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (vel != null && vel.getModifier(LENTO) == null) {
            vel.addModifier(new AttributeModifier(LENTO, -0.8, AttributeModifier.Operation.ADD_SCALAR, EquipmentSlotGroup.ANY));
        }
        Util.titulo(p, "<red><bold>CAÍDO", "<gray>Un Fusilero te puede levantar · 10 s", 0, 2500, 300);
        for (Player o : jugadores()) {
            if (aliados(o, p)) Util.barra(o, "<yellow>" + p.getName() + " está caído <gray>(clic derecho para levantarlo si sos Fusilero)");
        }
        Player a = autor == null ? null : Bukkit.getPlayer(autor);
        if (a != null) Util.barra(a, "<gray>Derribaste a <white>" + p.getName());
    }

    /** Sale del estado caído: revivido o muerto. */
    private void terminarCaido(Player p, boolean revivido) {
        if (caidos.remove(p.getUniqueId()) == null) return;
        plugin.movilidad().levantar(p);
        AttributeInstance vel = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (vel != null) vel.removeModifier(LENTO);
        if (revivido) {
            double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
            p.setHealth(max * 0.4);
            Util.titulo(p, "", "<green>Te levantaron", 0, 1200, 300);
        }
    }

    private void tickCaidos() {
        int ahora = Bukkit.getCurrentTick();
        for (Map.Entry<UUID, Caido> en : new ArrayList<>(caidos.entrySet())) {
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null) {
                caidos.remove(en.getKey());
                continue;
            }
            int resta = en.getValue().hasta() - ahora;
            if (resta <= 0) {
                Caido c = en.getValue();
                Player a = c.autor() == null ? null : Bukkit.getPlayer(c.autor());
                if (a != null) plugin.armas().registrarImpacto(p, a, c.arma(), false);
                terminarCaido(p, false);
                p.setHealth(0);
                continue;
            }
            Util.barra(p, "<red>Caído <white>" + (resta / 20 + 1) + " s");
            p.getWorld().spawnParticle(Particle.DUST, p.getLocation().add(0, 0.5, 0), 3, 0.3, 0.1, 0.3, 0,
                    new Particle.DustOptions(Color.fromRGB(160, 0, 0), 1.2f));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void alTocarCompañero(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !(e.getRightClicked() instanceof Player o)) return;
        Player p = e.getPlayer();
        if (!enGuerra(p) || !aliados(p, o) || !vivo(p)) return;
        if (caido(o)) {
            e.setCancelled(true);
            if (clase(p) != Clase.FUSILERO) {
                Util.barra(p, "<gray>Solo un Fusilero puede levantarlo.");
                return;
            }
            terminarCaido(o, true);
            plugin.almacen().de(p).guerraRevividos++;
            Util.barra(p, "<green>Levantaste a " + o.getName());
            p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.6f);
            return;
        }
        if (ArmaGuerra.de(p.getInventory().getItemInMainHand()) == ArmaGuerra.BOTIQUIN) {
            e.setCancelled(true);
            curar(p, o, 0.45);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void alUsar(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (!enGuerra(p)) return;
        if (e.getAction() == Action.PHYSICAL && e.getClickedBlock() != null && e.getClickedBlock().getType() == Material.FARMLAND) {
            e.setCancelled(true);
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack it = e.getItem();
        if (it != null && it.getType() == Material.BOOK) {
            e.setCancelled(true);
            abrirClases(p);
            return;
        }
        if (ArmaGuerra.de(it) == ArmaGuerra.BOTIQUIN && p.isSneaking()) {
            e.setCancelled(true);
            curar(p, p, 0.3);
            return;
        }
        // Cajas de munición (barriles): recargan todo, cada 20 s.
        Block b = e.getClickedBlock();
        if (b != null && b.getType() == Material.BARREL) {
            e.setCancelled(true);
            int ahora = Bukkit.getCurrentTick();
            if (ahora < cajaLista.getOrDefault(p.getUniqueId(), 0)) {
                Util.barra(p, "<gray>Caja vacía por ahora (" + (cajaLista.get(p.getUniqueId()) - ahora) / 20 + " s)");
                return;
            }
            cajaLista.put(p.getUniqueId(), ahora + 400);
            reabastecer(p);
        }
    }

    private void curar(Player medico, Player o, double fraccion) {
        int ahora = Bukkit.getCurrentTick();
        if (ahora < curaLista.getOrDefault(medico.getUniqueId(), 0)) {
            Util.barra(medico, "<gray>Botiquín listo en " + (curaLista.get(medico.getUniqueId()) - ahora) / 20 + " s");
            return;
        }
        double max = o.getAttribute(Attribute.MAX_HEALTH).getValue();
        if (o.getHealth() >= max - 0.5) return;
        curaLista.put(medico.getUniqueId(), ahora + (medico == o ? 400 : 200));
        o.setHealth(Math.min(max, o.getHealth() + max * fraccion));
        o.getWorld().spawnParticle(Particle.HEART, o.getLocation().add(0, 2, 0), 4, 0.3, 0.2, 0.3, 0);
        o.playSound(o, Sound.ITEM_ARMOR_EQUIP_LEATHER, 1f, 1.4f);
        if (medico != o) Util.barra(medico, "<green>Curaste a " + o.getName());
    }

    /** Recarga munición, granadas y el equipo de la clase. */
    public void reabastecer(Player p) {
        plugin.armas().rellenarReservas(p);
        arsenal.reabastecer(p, clase(p));
        p.playSound(p, Sound.ITEM_ARMOR_EQUIP_IRON, 1f, 1.2f);
        Util.barra(p, "<green>Reabastecido");
    }

    // ------------------------------------------------------------------ muertes

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alGolpe(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player v) || !enGuerra(v)) return;
        Player a = TresModos.jugadorAtacante(e.getDamager());
        if (a != null && aliados(a, v)) {
            e.setCancelled(true);
            return;
        }
        // A mano limpia casi no se lastima.
        if (a != null && e.getDamager() == a && !Armas.aplicandoBala && e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            e.setDamage(2);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void alMorir(PlayerDeathEvent e) {
        Player v = e.getPlayer();
        if (!enGuerra(v)) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        e.deathMessage(null);
        e.setKeepInventory(false);
        soltarBandera(v, true);
        Caido c = caidos.get(v.getUniqueId());
        terminarCaido(v, false);
        if (estado == Estado.FIN) return;
        plugin.almacen().de(v).guerraMuertes++;
        Armas.Impacto imp = plugin.armas().ultimoImpacto(v.getUniqueId());
        UUID autor = imp != null ? imp.autor() : c != null ? c.autor() : null;
        String arma = imp != null ? imp.arma() : c != null ? c.arma() : "?";
        Player a = autor == null ? null : Bukkit.getPlayer(autor);
        Equipo ev = equipo(v);
        if (a == null || a == v || !enGuerra(a)) {
            anunciar("<gray>☠ " + (ev == null ? "" : ev.color) + v.getName() + " <gray>murió");
            return;
        }
        Equipo ea = equipo(a);
        plugin.almacen().de(a).guerraBajas++;
        plugin.armas().contarBaja(a, arma, imp != null && imp.cabeza());
        anunciar((ea == null ? "" : ea.color) + a.getName() + " <gray>[" + arma + "] " + (ev == null ? "" : ev.color) + v.getName());
        a.playSound(a, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.6f);
    }

    @EventHandler(ignoreCancelled = true)
    public void alAgacharEspectador(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (!enGuerra(p) || !e.isSneaking() || !esperando.containsKey(p.getUniqueId())) return;
        if (puesto != null && puesto == equipo(p) && quierePuesto.add(p.getUniqueId())) {
            Util.barra(p, "<gold>Vas a aparecer en el puesto avanzado");
        }
    }

    /** El espectador que espera reaparecer no se aleja del mapa. */
    @EventHandler(ignoreCancelled = true)
    public void alMoverEspectador(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SPECTATOR || !enGuerra(p) || p.getSpectatorTarget() != null) return;
        Location to = e.getTo();
        double lim = ValleDeHierro.MITAD - 2;
        double x = Math.max(-lim, Math.min(lim, to.getX())), z = Math.max(-lim, Math.min(lim, to.getZ()));
        double y = Math.max(50, Math.min(150, to.getY()));
        if (x != to.getX() || y != to.getY() || z != to.getZ()) {
            Location l = to.clone();
            l.set(x, y, z);
            e.setTo(l);
        }
    }

    @EventHandler
    public void alHambre(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && enGuerra(p)) e.setCancelled(true);
    }

    @EventHandler
    public void alTirar(PlayerDropItemEvent e) {
        if (enGuerra(e.getPlayer()) && Armas.tipo(e.getItemDrop().getItemStack()) == null) e.setCancelled(true);
    }

    @EventHandler
    public void alClicInventario(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && enGuerra(p) && e.getSlotType() == org.bukkit.event.inventory.InventoryType.SlotType.ARMOR) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ destrucción y restauración

    /** Anota un bloque que se va a romper para restaurarlo al terminar. */
    public void registrarRotura(Block b) {
        rotos.add(clave(b.getX(), b.getY(), b.getZ()));
    }

    private static long clave(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    @EventHandler(ignoreCancelled = true)
    public void alExplotarEntidad(EntityExplodeEvent e) {
        if (Modo.de(e.getLocation().getWorld()) != Modo.GUERRA) return;
        for (Block b : e.blockList()) registrarRotura(b);
    }

    @EventHandler(ignoreCancelled = true)
    public void alExplotarBloque(BlockExplodeEvent e) {
        if (Modo.de(e.getBlock().getWorld()) != Modo.GUERRA) return;
        for (Block b : e.blockList()) registrarRotura(b);
    }

    private void restaurarMapa() {
        World w = mundo();
        int n = 0;
        for (long k : rotos) {
            int x = (int) (k >> 38), z = (int) ((k >> 12) & 0x3FFFFFF), y = (int) (k & 0xFFF);
            if (x >= 1 << 25) x -= 1 << 26;
            if (z >= 1 << 25) z -= 1 << 26;
            if (y >= 1 << 11) y -= 1 << 12;
            w.getBlockAt(x, y, z).setBlockData(GeneradorValle.datos(ValleDeHierro.bloque(x, y, z)), false);
            n++;
        }
        rotos.clear();
        if (n > 0) plugin.getLogger().info("Guerra: " + n + " bloques restaurados.");
    }

    // ------------------------------------------------------------------ ciclo

    private void tick() {
        if (!enMarcha) return;
        int ahora = Bukkit.getCurrentTick();
        List<Player> js = jugadores();
        if (js.isEmpty()) {
            enMarcha = false;
            restaurarMapa();
            arsenal.reiniciar();
            for (Bandera b : banderas.values()) volverABase(b, false);
            equipos.clear();
            return;
        }
        for (Map.Entry<UUID, Integer> en : new ArrayList<>(esperando.entrySet())) {
            if (ahora < en.getValue()) continue;
            Player p = Bukkit.getPlayer(en.getKey());
            if (p != null && enGuerra(p)) aparecerEnBase(p);
            else esperando.remove(en.getKey());
        }
        if (estado == Estado.FIN) {
            if (ahora >= reinicio) nuevaPartida();
            return;
        }
        tickBanderas();
        tickCaidos();
        if (ahora % 10 < 5) tickPuesto();
        int jugado = ahora - inicio;
        if (estado == Estado.EN_CURSO && jugado >= DURACION) {
            int a = capturas.get(Equipo.AZUL), r = capturas.get(Equipo.ROJO);
            if (a != r) terminar(a > r ? Equipo.AZUL : Equipo.ROJO, "más capturas a los 15 min");
            else {
                estado = Estado.MUERTE_SUBITA;
                finSubita = ahora + MUERTE_SUBITA;
                for (Player p : js) {
                    Util.titulo(p, "<gold><bold>MUERTE SÚBITA", "<gray>La primera captura gana · 3 min", 300, 2500, 700);
                    p.playSound(p, Sound.EVENT_RAID_HORN, 0.8f, 0.8f);
                }
            }
        } else if (estado == Estado.MUERTE_SUBITA && ahora >= finSubita) {
            terminar(null, "nadie capturó en la muerte súbita");
        }
    }

    public void apagar() {
        for (Bandera b : banderas.values()) if (b.visual != null) b.visual.remove();
        for (Entity e : decorado) e.remove();
        arsenal.apagar();
        restaurarMapa();
        if (barraPuesto != null) for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(barraPuesto);
    }

    // ------------------------------------------------------------------ sidebar

    @Override
    public String tituloSidebar(Player p) {
        return "<dark_green><bold>GUERRA</bold> <gray>Valle de Hierro";
    }

    private String estadoBandera(Bandera b) {
        return switch (b.estado) {
            case EN_BASE -> "<green>en base";
            case CAIDA -> "<yellow>en el piso " + Math.max(0, (b.vuelve - Bukkit.getCurrentTick()) / 20) + " s";
            case LLEVADA -> {
                Player p = Bukkit.getPlayer(b.portador);
                yield "<red>robada" + (p == null ? "" : " <gray>(" + p.getName() + ")");
            }
        };
    }

    @Override
    public List<String> lineasSidebar(Player p) {
        List<String> l = new ArrayList<>();
        Equipo e = equipo(p);
        int resta;
        if (estado == Estado.MUERTE_SUBITA) resta = Math.max(0, finSubita - Bukkit.getCurrentTick());
        else resta = Math.max(0, DURACION - (Bukkit.getCurrentTick() - inicio));
        l.add((estado == Estado.MUERTE_SUBITA ? "<gold>Muerte súbita " : "<white>Tiempo ") + "<yellow>" + Util.tiempo(resta / 20));
        l.add("");
        l.add("<blue>Azul <white>" + capturas.get(Equipo.AZUL) + " <gray>– <white>" + capturas.get(Equipo.ROJO) + " <red>Rojo");
        l.add("<blue>⚑ " + estadoBandera(banderas.get(Equipo.AZUL)));
        l.add("<red>⚑ " + estadoBandera(banderas.get(Equipo.ROJO)));
        l.add("<gold>Puesto: " + (puesto == null ? "<gray>nadie" : puesto.color + puesto.nombre));
        l.add("");
        if (e != null) l.add("<gray>Equipo " + e.color + e.nombre + " <gray>· " + clase(p).nombre);
        DatosJugador d = plugin.almacen().de(p);
        l.add("<gray>Bajas <white>" + d.guerraBajas + " <gray>Capturas <white>" + d.guerraCapturas);
        return l;
    }

    // ------------------------------------------------------------------ utilidades para el arsenal

    /** Jugadores enemigos de p dentro de un radio. */
    public List<Player> enemigosCerca(Player p, Location l, double r) {
        List<Player> out = new ArrayList<>();
        for (Player o : jugadores()) {
            if (o == p || aliados(p, o) || !vivo(o)) continue;
            if (o.getLocation().distanceSquared(l) <= r * r) out.add(o);
        }
        return out;
    }

    public Vector haciaEnemigo(Equipo e) {
        return new Vector(e.lado * -1, 0, 0);
    }
}
