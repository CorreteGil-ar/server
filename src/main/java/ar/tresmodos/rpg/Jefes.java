package ar.tresmodos.rpg;

import ar.tresmodos.Claves;
import ar.tresmodos.DatosJugador;
import ar.tresmodos.Modo;
import ar.tresmodos.TresModos;
import ar.tresmodos.Util;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Los cinco jefes y sus arenas. Cada arena tiene un muro de niebla en la entrada: cuando alguien
 * entra empieza la pelea y los demás tienen 15 s para sumarse; después nadie entra ni sale hasta que
 * gane el grupo o mueran todos. La vida del jefe crece un 30 % por jugador extra.
 */
public class Jefes implements Listener {
    private static final int VERSION_ARENAS = 1;
    public static final List<String> SEÑORES = List.of("gloton", "tejedora", "abismo", "vigia");

    public enum EstadoArena { LIBRE, PELEA }

    public final class Arena {
        final String id;
        final Jefe jefe;
        final Location centro;
        final double radio;
        final Vector entrada;
        EstadoArena estado = EstadoArena.LIBRE;
        final Set<UUID> participantes = new LinkedHashSet<>();
        int graciaHasta, libreDesde, sinNadieDesde = -1;

        Arena(String id, Jefe jefe, Location centro, double radio, Vector entrada) {
            this.id = id;
            this.jefe = jefe;
            this.centro = centro;
            this.radio = radio;
            this.entrada = entrada;
        }

        public List<Player> participantes() {
            List<Player> l = new ArrayList<>();
            for (UUID u : participantes) {
                Player p = Bukkit.getPlayer(u);
                if (p != null && !p.isDead() && p.getWorld() == centro.getWorld()) l.add(p);
            }
            return l;
        }

        public boolean esParticipante(Player p) {
            return participantes.contains(p.getUniqueId());
        }

        boolean dentro(Location l, double margen) {
            if (l.getWorld() != centro.getWorld() || Math.abs(l.getY() - centro.getY()) > 16) return false;
            return Math.hypot(l.getX() - centro.getX(), l.getZ() - centro.getZ()) < radio - margen;
        }

        Location puerta() {
            return centro.clone().add(entrada.clone().multiply(radio + 1));
        }
    }

    /** Hilos, tentáculos: algo que se rompe a golpes y avisa cuando cae. */
    public static final class Rompible {
        final Interaction caja;
        final ItemDisplay visual;
        int golpes;
        final Consumer<Rompible> alRomper;

        Rompible(Interaction caja, ItemDisplay visual, int golpes, Consumer<Rompible> alRomper) {
            this.caja = caja;
            this.visual = visual;
            this.golpes = golpes;
            this.alRomper = alRomper;
        }

        public boolean vivo() {
            return caja.isValid();
        }

        public Location lugar() {
            return caja.getLocation();
        }

        public void quitar() {
            caja.remove();
            if (visual != null) visual.remove();
        }
    }

    private final TresModos plugin;
    private final ModoRpg modo;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();
    private final Map<UUID, Rompible> rompibles = new HashMap<>();
    private Location puertaArriba, puertaAbajo;

    public Jefes(TresModos plugin, ModoRpg modo) {
        this.plugin = plugin;
        this.modo = modo;
        Bukkit.getScheduler().runTaskLater(plugin, this::preparar, 3);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 2);
    }

    private World mundo() {
        return plugin.mundos().de(Modo.RPG);
    }

    public Arena arena(String id) {
        return arenas.get(id);
    }

    // ------------------------------------------------------------------ arenas

    private void preparar() {
        MundoRpg m = modo.mundoRpg();
        boolean construir = m.datosExtra().getInt("version-arenas", 0) < VERSION_ARENAS;
        long t0 = System.currentTimeMillis();
        Jefe[] jefes = {new JefeGloton(plugin, modo), new JefeTejedora(plugin, modo), new JefeCaballero(plugin, modo),
                new JefeVigia(plugin, modo)};
        Zona[] zonas = {Zona.ALDEA, Zona.BOSQUE, Zona.CIUDADELA, Zona.COSTA};
        for (int i = 0; i < jefes.length; i++) {
            Zona z = zonas[i];
            MundoRpg.Hoguera h = m.hoguera(z.name().toLowerCase() + "_jefe");
            if (h == null) continue;
            double[] hs = z.haciaSantuario();
            Vector afuera = new Vector(-hs[0], 0, -hs[1]);
            int cx = (int) Math.round(h.x() + afuera.getX() * 24), cz = (int) Math.round(h.z() + afuera.getZ() * 24);
            World w = mundo();
            w.getChunkAt(cx >> 4, cz >> 4);
            int y = construir ? piso(w, cx, cz) : m.datosExtra().getInt("arenas." + jefes[i].id() + ".y", piso(w, cx, cz));
            Location centro = new Location(w, cx + 0.5, y + 1, cz + 0.5);
            Arena a = new Arena(jefes[i].id(), jefes[i], centro, 15, afuera.clone().multiply(-1));
            arenas.put(a.id, a);
            if (construir) {
                construirArena(a, z);
                m.guardarExtra("arenas." + a.id + ".y", y);
            }
        }
        // El Templo del Abismo, bajo la Costa.
        Location c = new Location(mundo(), m.origenX() + Zona.TEMPLO.dx + 0.5, -36, m.origenZ() + Zona.TEMPLO.dz + 0.5);
        Arena templo = new Arena("durmiente", new JefeDurmiente(plugin, modo), c, 14, new Vector(0, 0, 1));
        arenas.put(templo.id, templo);
        Location costa = modo.mundoRpg().centro(Zona.COSTA);
        puertaArriba = costa.clone().add(6, 0, 0).getBlock().getLocation();
        puertaArriba.setY(m.datosExtra().getInt("puerta.y", (int) costa.getY()));
        puertaAbajo = c.clone().add(0, 0, 17).getBlock().getLocation();
        if (construir) {
            construirTemplo(templo);
            puertaArriba = construirPuerta(costa);
            m.guardarExtra("puerta.y", puertaArriba.getBlockY());
            m.guardarExtra("version-arenas", VERSION_ARENAS);
            plugin.getLogger().info("RPG: arenas de jefe construidas en " + (System.currentTimeMillis() - t0) + " ms.");
        }
    }

    private static int piso(World w, int x, int z) {
        return Math.max(w.getHighestBlockYAt(x, z, HeightMap.OCEAN_FLOOR), w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES));
    }

    private void construirArena(Arena a, Zona z) {
        World w = mundo();
        int cx = a.centro.getBlockX(), cy = a.centro.getBlockY() - 1, cz = a.centro.getBlockZ();
        int r = (int) a.radio;
        Random rnd = new Random(cx * 7L + cz);
        Material[] pal = z.paleta;
        Material piso = switch (a.id) {
            case "abismo" -> Material.POLISHED_BLACKSTONE_BRICKS;
            case "vigia" -> Material.DEEPSLATE_TILES;
            default -> pal[0];
        };
        for (int dx = -r - 2; dx <= r + 2; dx++) {
            for (int dz = -r - 2; dz <= r + 2; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > r + 1.6) continue;
                Block b = w.getBlockAt(cx + dx, cy, cz + dz);
                b.setType(rnd.nextInt(4) == 0 ? pal[rnd.nextInt(pal.length)] : piso, false);
                for (int dy = 1; dy <= 20; dy++) {
                    Block abajo = w.getBlockAt(cx + dx, cy - dy, cz + dz);
                    if (abajo.getType().isSolid()) break;
                    abajo.setType(pal[0] == Material.GRAVEL ? Material.COBBLESTONE : pal[0], false);
                }
                for (int dy = 1; dy <= 16; dy++) w.getBlockAt(cx + dx, cy + dy, cz + dz).setType(Material.AIR, false);
                // Muro en ruinas con un hueco en la entrada.
                if (d > r + 0.4) {
                    Vector hacia = new Vector(dx, 0, dz).normalize();
                    if (hacia.dot(a.entrada) > 0.97) continue;
                    int alto = 3 + rnd.nextInt(4);
                    for (int dy = 1; dy <= alto; dy++) {
                        w.getBlockAt(cx + dx, cy + dy, cz + dz).setType(pal[rnd.nextInt(pal.length)] == Material.GRAVEL
                                ? Material.COBBLESTONE : pal[rnd.nextInt(pal.length)], false);
                    }
                }
            }
        }
        // Detalles según el jefe.
        Vector fondo = a.entrada.clone().multiply(-1);
        int bx = cx + (int) Math.round(fondo.getX() * (r - 3)), bz = cz + (int) Math.round(fondo.getZ() * (r - 3));
        switch (a.id) {
            case "gloton" -> {
                // El molino quemado: torre de madera oscura con aspas rotas.
                for (int dy = 1; dy <= 9; dy++)
                    for (int ox = -1; ox <= 1; ox++)
                        for (int oz = -1; oz <= 1; oz++)
                            if (Math.abs(ox) + Math.abs(oz) > 0 && rnd.nextInt(6) > 0)
                                w.getBlockAt(bx + ox, cy + dy, bz + oz).setType(dy % 3 == 0 ? Material.STRIPPED_DARK_OAK_LOG : Material.DARK_OAK_PLANKS, false);
                for (int i = -4; i <= 4; i++) {
                    w.getBlockAt(bx + i, cy + 8 + i / 2, bz + 2).setType(Material.DARK_OAK_FENCE, false);
                    w.getBlockAt(bx + i / 2, cy + 8 - i, bz + 2).setType(Material.DARK_OAK_FENCE, false);
                }
                for (int i = 0; i < 6; i++) {
                    int px = cx + rnd.nextInt(2 * r - 4) - r + 2, pz = cz + rnd.nextInt(2 * r - 4) - r + 2;
                    if (Math.hypot(px - cx, pz - cz) < 5) continue;
                    w.getBlockAt(px, cy + 1, pz).setType(Material.HAY_BLOCK, false);
                }
            }
            case "tejedora" -> {
                for (int i = 0; i < 40; i++) {
                    double ang = rnd.nextDouble() * Math.PI * 2, rr = r - 1 - rnd.nextDouble() * 2;
                    Block b = w.getBlockAt(cx + (int) (Math.cos(ang) * rr), cy + 1 + rnd.nextInt(3), cz + (int) (Math.sin(ang) * rr));
                    if (b.getType() == Material.AIR) b.setType(Material.COBWEB, false);
                }
                for (int i = 0; i < 4; i++) {
                    double ang = i * Math.PI / 2 + 0.6;
                    int px = cx + (int) (Math.cos(ang) * 9), pz = cz + (int) (Math.sin(ang) * 9);
                    for (int dy = 1; dy <= 10; dy++) w.getBlockAt(px, cy + dy, pz).setType(Material.MUSHROOM_STEM, false);
                }
            }
            case "abismo" -> {
                for (int i = 0; i < 6; i++) {
                    double ang = i * Math.PI / 3;
                    int px = cx + (int) (Math.cos(ang) * 10), pz = cz + (int) (Math.sin(ang) * 10);
                    for (int dy = 1; dy <= 7; dy++) w.getBlockAt(px, cy + dy, pz).setType(Material.POLISHED_BLACKSTONE_BRICKS, false);
                    w.getBlockAt(px, cy + 8, pz).setType(Material.SOUL_LANTERN, false);
                }
                w.getBlockAt(bx, cy + 1, bz).setType(Material.GILDED_BLACKSTONE, false);
                w.getBlockAt(bx, cy + 2, bz).setType(Material.POLISHED_BLACKSTONE_STAIRS, false);
            }
            case "vigia" -> {
                // El faro, detrás del muro.
                int fx = cx + (int) Math.round(fondo.getX() * (r + 5)), fz = cz + (int) Math.round(fondo.getZ() * (r + 5));
                for (int dy = -6; dy <= 16; dy++)
                    for (int ox = -2; ox <= 2; ox++)
                        for (int oz = -2; oz <= 2; oz++) {
                            if (Math.abs(ox) == 2 && Math.abs(oz) == 2) continue;
                            boolean borde = Math.abs(ox) == 2 || Math.abs(oz) == 2;
                            if (!borde && dy > 0 && dy < 16) continue;
                            w.getBlockAt(fx + ox, cy + dy, fz + oz).setType(dy == 16 ? Material.SEA_LANTERN
                                    : (dy / 3) % 2 == 0 ? Material.WHITE_CONCRETE : Material.RED_CONCRETE, false);
                        }
            }
            default -> { }
        }
        nombre(a.puerta().clone().add(0, 3, 0), "<gray>Niebla de " + a.jefe.nombre());
    }

    private void construirTemplo(Arena a) {
        World w = mundo();
        int cx = a.centro.getBlockX(), cy = a.centro.getBlockY() - 1, cz = a.centro.getBlockZ();
        Random rnd = new Random(13);
        for (int dx = -24; dx <= 24; dx++) {
            for (int dz = -24; dz <= 24; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > 24) continue;
                for (int dy = -6; dy <= 22; dy++) {
                    Block b = w.getBlockAt(cx + dx, cy + dy, cz + dz);
                    double cupula = 22 - (d * d) / 40.0;
                    if (dy > cupula) {
                        if (dy <= cupula + 2) b.setType(rnd.nextInt(5) == 0 ? Material.SCULK : Material.DEEPSLATE_BRICKS, false);
                        continue;
                    }
                    if (d > 23) b.setType(Material.DEEPSLATE_BRICKS, false);
                    else if (dy < 0) b.setType(d < a.radio + 0.5 ? Material.DARK_PRISMARINE : dy < -4 ? Material.DEEPSLATE : Material.WATER, false);
                    else if (dy == 0) b.setType(d < a.radio + 0.5 ? (rnd.nextInt(6) == 0 ? Material.SCULK : Material.DARK_PRISMARINE)
                            : Material.WATER, false);
                    else b.setType(Material.AIR, false);
                }
                if (d > a.radio + 1 && d < 23 && rnd.nextInt(30) == 0) {
                    for (int dy = 1; dy <= 3 + rnd.nextInt(5); dy++) w.getBlockAt(cx + dx, cy + dy, cz + dz).setType(Material.PRISMARINE_WALL, false);
                }
            }
        }
        // Monolitos para esconderse de la mirada.
        for (int i = 0; i < 5; i++) {
            double ang = i * Math.PI * 2 / 5 + 0.3;
            int px = cx + (int) (Math.cos(ang) * 8), pz = cz + (int) (Math.sin(ang) * 8);
            for (int dy = 1; dy <= 7; dy++)
                for (int ox = 0; ox <= 1; ox++)
                    for (int oz = 0; oz <= 1; oz++)
                        w.getBlockAt(px + ox, cy + dy, pz + oz).setType(dy == 7 ? Material.SEA_LANTERN : Material.POLISHED_DEEPSLATE, false);
        }
        // Puente a la roca y el portal de vuelta.
        for (int dz = (int) a.radio; dz <= 18; dz++)
            for (int dx = -1; dx <= 1; dx++) w.getBlockAt(cx + dx, cy, cz + dz).setType(Material.DARK_PRISMARINE, false);
        w.getBlockAt(cx, cy + 1, cz + 17).setType(Material.LODESTONE, false);
        nombre(new Location(w, cx + 0.5, cy + 3, cz + 17.5), "<dark_purple>Ascenso");
        for (int i = 0; i < 40; i++) {
            Block b = w.getBlockAt(cx + rnd.nextInt(40) - 20, cy + 14 + rnd.nextInt(6), cz + rnd.nextInt(40) - 20);
            if (b.getType() == Material.AIR) b.setType(Material.SHROOMLIGHT, false);
        }
    }

    /** Puerta del Abismo en la Costa: arco de obsidiana llorosa con una piedra imán. */
    private Location construirPuerta(Location costa) {
        World w = mundo();
        int x = costa.getBlockX() + 6, z = costa.getBlockZ();
        w.getChunkAt(x >> 4, z >> 4);
        int y = piso(w, x, z);
        for (int dx = -3; dx <= 3; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                w.getBlockAt(x + dx, y, z + dz).setType(Material.DEEPSLATE_TILES, false);
                for (int dy = 1; dy <= 6; dy++) w.getBlockAt(x + dx, y + dy, z + dz).setType(Material.AIR, false);
            }
        for (int dy = 1; dy <= 5; dy++) {
            w.getBlockAt(x - 2, y + dy, z).setType(Material.CRYING_OBSIDIAN, false);
            w.getBlockAt(x + 2, y + dy, z).setType(Material.CRYING_OBSIDIAN, false);
        }
        for (int dx = -2; dx <= 2; dx++) w.getBlockAt(x + dx, y + 6, z).setType(Material.CRYING_OBSIDIAN, false);
        w.getBlockAt(x, y + 1, z).setType(Material.LODESTONE, false);
        nombre(new Location(w, x + 0.5, y + 3.2, z + 0.5), "<dark_purple><bold>Puerta del Abismo");
        return new Location(w, x, y + 1, z);
    }

    private static void nombre(Location l, String texto) {
        l.getWorld().spawn(l, TextDisplay.class, t -> {
            t.text(Util.mm(texto));
            t.setBillboard(Display.Billboard.CENTER);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.setShadowed(true);
            t.getPersistentDataContainer().set(Claves.DECORADO, PersistentDataType.BYTE, (byte) 1);
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void alTocarPuerta(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) return;
        Block b = e.getClickedBlock();
        if (b.getType() != Material.LODESTONE || Modo.de(b.getWorld()) != Modo.RPG) return;
        Player p = e.getPlayer();
        if (puertaArriba != null && b.getLocation().equals(puertaArriba)) {
            e.setCancelled(true);
            DatosJugador d = plugin.almacen().de(p);
            List<String> faltan = new ArrayList<>();
            for (String s : SEÑORES) if (!d.jefes.contains(s)) faltan.add(arenas.get(s) == null ? s : arenas.get(s).jefe.nombre());
            if (!faltan.isEmpty()) {
                Util.msg(p, "<dark_purple>La puerta no se abre. <gray>Faltan las almas de: <white>" + String.join(", ", faltan));
                p.playSound(p, Sound.BLOCK_IRON_DOOR_CLOSE, 1f, 0.5f);
                return;
            }
            Arena t = arenas.get("durmiente");
            Location destino = t.centro.clone().add(0, 0, 15);
            destino.setDirection(new Vector(0, 0, -1));
            p.teleport(destino);
            p.playSound(p, Sound.BLOCK_END_PORTAL_SPAWN, 0.6f, 0.6f);
            Util.titulo(p, "<dark_purple><bold>TEMPLO DEL ABISMO", "<gray>El Durmiente sueña", 800, 3000, 1000);
        } else if (puertaAbajo != null && b.getLocation().equals(puertaAbajo)) {
            e.setCancelled(true);
            Location arriba = puertaArriba.clone().add(0.5, 0, 2.5);
            p.teleport(arriba);
            p.playSound(p, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1f, 0.6f);
        }
    }

    /** Lleva a un admin a la entrada de una arena. */
    public boolean irA(Player p, String id) {
        Arena a = arenas.get(id);
        if (a == null) return false;
        Location l = a.puerta().clone().add(a.entrada.clone().multiply(3));
        l.setY(a.centro.getY());
        l.setDirection(a.entrada.clone().multiply(-1));
        p.teleport(l);
        return true;
    }

    public List<String> ids() {
        return new ArrayList<>(arenas.keySet());
    }

    // ------------------------------------------------------------------ pelea

    private void tick() {
        int ahora = Bukkit.getCurrentTick();
        World w = mundo();
        for (Arena a : arenas.values()) {
            if (!a.centro.isChunkLoaded()) continue;
            niebla(a, a.estado == EstadoArena.PELEA);
            if (a.estado == EstadoArena.LIBRE) {
                if (ahora < a.libreDesde) continue;
                List<Player> adentro = new ArrayList<>();
                for (Player p : w.getPlayers()) {
                    if (!p.isDead() && p.getGameMode() != org.bukkit.GameMode.SPECTATOR && a.dentro(p.getLocation(), 1.5)) adentro.add(p);
                }
                if (!adentro.isEmpty()) empezar(a, adentro);
                continue;
            }
            // Pelea: durante la gracia se suman los que entran; después, nadie entra ni sale.
            for (Player p : w.getPlayers()) {
                if (p.isDead()) continue;
                boolean participa = a.esParticipante(p);
                boolean adentro = a.dentro(p.getLocation(), 0.8);
                if (!participa && adentro) {
                    if (ahora < a.graciaHasta) {
                        a.participantes.add(p.getUniqueId());
                        a.jefe.escalar(a.participantes.size(), cicloDe(a));
                        Util.titulo(p, "", "<gray>Entraste a la niebla", 100, 1200, 300);
                    } else {
                        empujar(p, a, false);
                        Util.barra(p, "<gray>La niebla no te deja pasar.");
                    }
                } else if (participa && !a.dentro(p.getLocation(), 0.5) && Math.abs(p.getY() - a.centro.getY()) < 16) {
                    empujar(p, a, true);
                }
            }
            if (a.participantes().isEmpty()) {
                if (a.sinNadieDesde < 0) a.sinNadieDesde = ahora;
                if (ahora - a.sinNadieDesde > 60) terminar(a, false);
                continue;
            }
            a.sinNadieDesde = -1;
            if (!a.jefe.vivo()) {
                terminar(a, false);
                continue;
            }
            a.jefe.tick(ahora);
        }
    }

    private int cicloDe(Arena a) {
        int c = 0;
        for (Player p : a.participantes()) c = Math.max(c, plugin.almacen().de(p).ciclo);
        return c;
    }

    private void empezar(Arena a, List<Player> adentro) {
        a.estado = EstadoArena.PELEA;
        a.participantes.clear();
        for (Player p : adentro) a.participantes.add(p.getUniqueId());
        a.graciaHasta = Bukkit.getCurrentTick() + 300;
        a.sinNadieDesde = -1;
        a.jefe.aparecer(a, adentro.size(), cicloDe(a));
        World w = a.centro.getWorld();
        w.playSound(a.centro, Sound.BLOCK_END_PORTAL_SPAWN, 1.2f, 0.5f);
        for (Player p : w.getPlayers()) {
            if (p.getLocation().distanceSquared(a.centro) > 80 * 80) continue;
            Util.titulo(p, "<dark_red><bold>" + a.jefe.nombre().toUpperCase(),
                    a.esParticipante(p) ? "<gray>Los demás tienen 15 s para entrar" : "<gray>Tenés 15 s para entrar a la niebla", 500, 2500, 800);
        }
    }

    private void terminar(Arena a, boolean victoria) {
        a.jefe.quitar();
        for (UUID u : a.participantes) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && a.jefe.barra() != null) p.hideBossBar(a.jefe.barra());
        }
        a.participantes.clear();
        a.estado = EstadoArena.LIBRE;
        a.libreDesde = Bukkit.getCurrentTick() + (victoria ? 600 : 100);
    }

    private static void empujar(Player p, Arena a, boolean haciaAdentro) {
        Vector v = p.getLocation().toVector().subtract(a.centro.toVector()).setY(0);
        if (v.lengthSquared() < 0.01) v = a.entrada.clone();
        v.normalize();
        if (haciaAdentro) v.multiply(-1);
        p.setVelocity(v.multiply(0.8).setY(0.25));
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.01);
    }

    private static void niebla(Arena a, boolean densa) {
        Location c = a.puerta();
        Vector lado = new Vector(-a.entrada.getZ(), 0, a.entrada.getX());
        World w = c.getWorld();
        int n = densa ? 14 : 6;
        for (int i = 0; i < n; i++) {
            double s = Math.random() * 5 - 2.5, h = Math.random() * 4;
            w.spawnParticle(densa ? Particle.CLOUD : Particle.WHITE_SMOKE, c.clone().add(lado.clone().multiply(s)).add(0, h, 0), 1, 0.05, 0.05, 0.05, 0);
        }
    }

    // ------------------------------------------------------------------ eventos

    private Arena arenaDe(Entity e) {
        for (Arena a : arenas.values()) if (a.estado == EstadoArena.PELEA && (a.jefe.esCuerpo(e) || a.jefe.esParte(e))) return a;
        return null;
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void alGolpe(EntityDamageByEntityEvent e) {
        if (Modo.de(e.getEntity().getWorld()) != Modo.RPG) return;
        Arena a = arenaDe(e.getEntity());
        if (a != null) {
            Player atacante = TresModos.jugadorAtacante(e.getDamager());
            if (atacante != null) {
                if (!a.esParticipante(atacante)) {
                    e.setCancelled(true);
                    return;
                }
                a.jefe.alRecibir(e, atacante);
            }
            return;
        }
        Entity fuente = e.getDamager();
        if (fuente instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Entity s) fuente = s;
        if (fuente instanceof org.bukkit.entity.EvokerFangs f && f.getOwner() != null) fuente = f.getOwner();
        Arena b = arenaDe(fuente);
        if (b != null && e.getEntity() instanceof Player v) b.jefe.alGolpear(v, e);
    }

    /** El Vigía no se escapa teletransportándose fuera de su arena. */
    @EventHandler(ignoreCancelled = true)
    public void alEscaparEnderman(com.destroystokyo.paper.event.entity.EndermanEscapeEvent e) {
        if (arenaDe(e.getEntity()) != null) e.setCancelled(true);
    }

    @EventHandler
    public void alMorir(EntityDeathEvent e) {
        Arena a = arenaDe(e.getEntity());
        if (a == null) return;
        e.getDrops().clear();
        e.setDroppedExp(0);
        if (!a.jefe.esCuerpo(e.getEntity())) return;
        victoria(a);
    }

    private void victoria(Arena a) {
        Jefe j = a.jefe;
        boolean durmiente = j.id().equals("durmiente");
        for (Player p : a.participantes()) {
            DatosJugador d = plugin.almacen().de(p);
            long premio = Math.round(j.almas() * (1 + 0.5 * d.ciclo));
            d.almas += premio;
            boolean primera = d.jefes.add(j.id());
            if (durmiente) d.jefes.add("durmiente@" + d.ciclo);
            if (primera) d.almasJefe.add(j.id());
            p.hideBossBar(j.barra());
            Util.titulo(p, durmiente ? "<dark_purple><bold>EL DURMIENTE CAE" : "<gold><bold>ENEMIGO FORMIDABLE DERROTADO",
                    "<gold>+" + Util.num(premio) + " almas", 500, 3500, 1200);
            p.playSound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
            if (primera) {
                Util.msg(p, "<gold>Conseguiste el <bold>" + j.alma() + "</bold><gold>: +2 brasas para el árbol. "
                        + "Canjealo en el altar del Santuario.");
                if (durmiente) Util.msg(p, "<dark_purple>El ciclo terminó. <gray>La Guardiana del Fuego del Santuario "
                        + "puede empezar el <dark_red>Ciclo+" + (d.ciclo + 1) + "<gray>.");
            }
            modo.estados().limpiar(p);
        }
        terminar(a, true);
    }

    @EventHandler
    public void alMorirJugador(PlayerDeathEvent e) {
        for (Arena a : arenas.values()) {
            if (a.participantes.remove(e.getPlayer().getUniqueId()) && a.jefe.barra() != null) {
                e.getPlayer().hideBossBar(a.jefe.barra());
            }
        }
    }

    // ------------------------------------------------------------------ rompibles

    public Rompible rompible(Location l, ItemStack visual, float escala, float ancho, float alto, int golpes, Consumer<Rompible> alRomper) {
        World w = l.getWorld();
        Interaction caja = w.spawn(l, Interaction.class, i -> {
            i.setInteractionWidth(ancho);
            i.setInteractionHeight(alto);
            i.setResponsive(true);
            i.setPersistent(false);
        });
        ItemDisplay vis = visual == null ? null : w.spawn(l.clone().add(0, alto / 2, 0), ItemDisplay.class, d -> {
            d.setItemStack(visual);
            d.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(),
                    new Vector3f(escala, escala * alto, escala), new AxisAngle4f()));
            d.setPersistent(false);
        });
        Rompible r = new Rompible(caja, vis, golpes, alRomper);
        rompibles.put(caja.getUniqueId(), r);
        return r;
    }

    @EventHandler(ignoreCancelled = true)
    public void alPegarRompible(PrePlayerAttackEntityEvent e) {
        Rompible r = rompibles.get(e.getAttacked().getUniqueId());
        if (r == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        Location l = r.lugar();
        l.getWorld().spawnParticle(Particle.CRIT, l.clone().add(0, 1, 0), 10, 0.3, 0.5, 0.3, 0.1);
        l.getWorld().playSound(l, Sound.ENTITY_SLIME_ATTACK, 1f, 0.6f);
        modo.gastar(p, 8);
        if (--r.golpes <= 0) {
            rompibles.remove(r.caja.getUniqueId());
            r.quitar();
            l.getWorld().playSound(l, Sound.ENTITY_SLIME_DEATH, 1f, 0.5f);
            r.alRomper.accept(r);
        }
    }

    public void olvidarRompible(Rompible r) {
        rompibles.remove(r.caja.getUniqueId());
        r.quitar();
    }

    public void apagar() {
        for (Arena a : arenas.values()) a.jefe.quitar();
        for (Rompible r : rompibles.values()) r.quitar();
        rompibles.clear();
    }

    public static BlockData datos(Material m) {
        return m.createBlockData();
    }

    public static LivingEntity vivo(Entity e) {
        return e instanceof LivingEntity le && le.isValid() ? le : null;
    }
}
