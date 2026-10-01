package ar.tresmodos.rpg;

import ar.tresmodos.Claves;
import ar.tresmodos.DatosJugador;
import ar.tresmodos.Menu;
import ar.tresmodos.Modo;
import ar.tresmodos.TresModos;
import ar.tresmodos.Util;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.WeatherType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * El mundo de Las Tierras Cenicientas: zonas con su bioma pintado, hora y clima por jugador,
 * hogueras construidas por el plugin (registradas para viajar entre ellas) y la Noche Roja.
 */
public class MundoRpg implements Listener {
    /** Subir este número vuelve a pintar los biomas de los chunks ya pintados. */
    private static final int VERSION_BIOMAS = 1;
    private static final int VERSION_HOGUERAS = 1;
    private static final NamespacedKey CLAVE_BIOMA = new NamespacedKey("tresmodos", "rpg_bioma");

    public record Hoguera(String id, String nombre, Zona zona, int x, int y, int z) {
        public boolean es(Block b) {
            return b.getX() == x && b.getY() == y && b.getZ() == z;
        }
    }

    private final TresModos plugin;
    private final ModoRpg modo;
    private final File archivo;
    private final Map<String, Hoguera> hogueras = new LinkedHashMap<>();
    private final Map<UUID, Zona> zonaActual = new HashMap<>();
    private final Map<UUID, Boolean> enPaso = new HashMap<>();
    private int sx, sz;
    private boolean nocheRoja;

    public MundoRpg(TresModos plugin, ModoRpg modo) {
        this.plugin = plugin;
        this.modo = modo;
        this.archivo = new File(plugin.getDataFolder(), "rpg-mundo.yml");
        Location s = mundo().getSpawnLocation();
        sx = s.getBlockX();
        sz = s.getBlockZ();
        cargar();
        Bukkit.getScheduler().runTask(plugin, this::generarSiFalta);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 20);
    }

    private World mundo() {
        return plugin.mundos().de(Modo.RPG);
    }

    public int origenX() { return sx; }
    public int origenZ() { return sz; }

    public Zona zona(Location l) {
        return Zona.de(l.getX() - sx, l.getY(), l.getZ() - sz);
    }

    public Location centro(Zona z) {
        int x = sx + z.dx, zz = sz + z.dz;
        return new Location(mundo(), x + 0.5, mundo().getHighestBlockYAt(x, zz, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1, zz + 0.5);
    }

    /** Nivel del lugar: dentro de una zona sube con la distancia a su entrada; afuera, con la distancia al Santuario. */
    public int nivel(Location l) {
        Zona z = zona(l);
        if (z == null) {
            double d = Math.hypot(l.getX() - sx, l.getZ() - sz);
            return Math.max(1, (int) (d / 45));
        }
        if (z == Zona.SANTUARIO) return 1;
        double[] h = z.haciaSantuario();
        double ex = sx + z.dx + h[0] * z.radio * 0.8, ez = sz + z.dz + h[1] * z.radio * 0.8;
        double d = Math.hypot(l.getX() - ex, l.getZ() - ez);
        double t = Math.max(0, Math.min(1, d / (z.radio * 1.7)));
        return (int) Math.round(z.nivelMin + (z.nivelMax - z.nivelMin) * t);
    }

    public boolean nocheRoja() {
        return nocheRoja;
    }

    // ------------------------------------------------------------------ registro

    private void cargar() {
        if (!archivo.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(archivo);
        ConfigurationSection sec = y.getConfigurationSection("hogueras");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection h = sec.getConfigurationSection(id);
            Zona z;
            try {
                z = Zona.valueOf(h.getString("zona", "SANTUARIO"));
            } catch (IllegalArgumentException e) {
                z = Zona.SANTUARIO;
            }
            hogueras.put(id, new Hoguera(id, h.getString("nombre", id), z, h.getInt("x"), h.getInt("y"), h.getInt("z")));
        }
    }

    private void guardar(int version) {
        YamlConfiguration y = archivo.exists() ? YamlConfiguration.loadConfiguration(archivo) : new YamlConfiguration();
        y.set("version-hogueras", version);
        y.set("hogueras", null);
        for (Hoguera h : hogueras.values()) {
            String b = "hogueras." + h.id() + ".";
            y.set(b + "nombre", h.nombre());
            y.set(b + "zona", h.zona().name());
            y.set(b + "x", h.x());
            y.set(b + "y", h.y());
            y.set(b + "z", h.z());
        }
        try {
            y.save(archivo);
        } catch (IOException e) {
            plugin.getLogger().warning("No pude guardar rpg-mundo.yml: " + e.getMessage());
        }
    }

    /** Lo guardado aparte del registro de hogueras (élites derrotadas, etc.). */
    public YamlConfiguration datosExtra() {
        return archivo.exists() ? YamlConfiguration.loadConfiguration(archivo) : new YamlConfiguration();
    }

    public void guardarExtra(String clave, Object valor) {
        YamlConfiguration y = datosExtra();
        y.set(clave, valor);
        try {
            y.save(archivo);
        } catch (IOException e) {
            plugin.getLogger().warning("No pude guardar rpg-mundo.yml: " + e.getMessage());
        }
    }

    public Hoguera hoguera(Block b) {
        for (Hoguera h : hogueras.values()) if (h.es(b)) return h;
        return null;
    }

    public Hoguera hoguera(String id) {
        return hogueras.get(id);
    }

    public List<Hoguera> hogueras() {
        return new ArrayList<>(hogueras.values());
    }

    // ------------------------------------------------------------------ generación

    private void generarSiFalta() {
        int version = datosExtra().getInt("version-hogueras", 0);
        if (version >= VERSION_HOGUERAS && !hogueras.isEmpty()) return;
        long t0 = System.currentTimeMillis();
        World w = mundo();
        // La hoguera del Santuario ya existe (Mundos.hogueraInicial): la registramos.
        Block santuario = buscarFogata(w, sx, sz, 5);
        if (santuario == null) {
            santuario = construirHoguera(w, sx - 2, sz, Zona.SANTUARIO, "Hoguera del Santuario", false);
        }
        registrar("santuario", "Hoguera del Santuario", Zona.SANTUARIO, santuario);
        construirSantuario(w, santuario);
        String[][] nombres = {
                {"Puerta de la Aldea", "Plaza de las Horcas", "Molino Quemado"},
                {"Linde del Bosque", "Pantano de las Esporas", "Árbol Hueco"},
                {"Puente Levadizo", "Catedral en Ruinas", "Salón del Trono"},
                {"Muelle Podrido", "Barco Encallado", "Faro del Vigía"}};
        Zona[] zonas = {Zona.ALDEA, Zona.BOSQUE, Zona.CIUDADELA, Zona.COSTA};
        for (int i = 0; i < zonas.length; i++) {
            Zona z = zonas[i];
            double[] h = z.haciaSantuario();
            int cx = sx + z.dx, cz = sz + z.dz;
            int[][] pos = {
                    {(int) (cx + h[0] * z.radio * 0.8), (int) (cz + h[1] * z.radio * 0.8)},
                    {cx + (int) (h[1] * 30), cz - (int) (h[0] * 30)},
                    {(int) (cx - h[0] * z.radio * 0.55), (int) (cz - h[1] * z.radio * 0.55)}};
            String[] ids = {"entrada", "medio", "jefe"};
            for (int k = 0; k < 3; k++) {
                Block f = construirHoguera(w, pos[k][0], pos[k][1], z, nombres[i][k], true);
                registrar(z.name().toLowerCase() + "_" + ids[k], nombres[i][k], z, f);
            }
        }
        guardar(VERSION_HOGUERAS);
        plugin.getLogger().info("RPG: " + hogueras.size() + " hogueras construidas en " + (System.currentTimeMillis() - t0) + " ms.");
    }

    private void registrar(String id, String nombre, Zona z, Block fuego) {
        hogueras.put(id, new Hoguera(id, nombre, z, fuego.getX(), fuego.getY(), fuego.getZ()));
    }

    private static Block buscarFogata(World w, int x, int z, int r) {
        int y0 = w.getHighestBlockYAt(x, z);
        for (int dx = -r; dx <= r; dx++)
            for (int dz = -r; dz <= r; dz++)
                for (int dy = -4; dy <= 4; dy++) {
                    Block b = w.getBlockAt(x + dx, y0 + dy, z + dz);
                    if (b.getType() == Material.CAMPFIRE) return b;
                }
        return null;
    }

    /**
     * Ruina de hoguera: piso de 7×7 con la paleta de la zona, cimientos hasta el suelo, columnas
     * rotas en las esquinas, la fogata con una espada clavada y el nombre encima. Devuelve la fogata.
     */
    private Block construirHoguera(World w, int x, int z, Zona zona, String nombre, boolean ruina) {
        w.getChunkAt(x >> 4, z >> 4);
        int y = w.getHighestBlockYAt(x, z, HeightMap.OCEAN_FLOOR);
        int agua = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        int piso = Math.max(y, agua);
        Random r = new Random(x * 31L + z);
        Material[] pal = zona.paleta;
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (Math.abs(dx) == 3 && Math.abs(dz) == 3) continue;
                Block b = w.getBlockAt(x + dx, piso, z + dz);
                b.setType(pal[r.nextInt(pal.length)], false);
                // Cimientos: hasta tocar algo sólido.
                for (int dy = 1; dy <= 14; dy++) {
                    Block abajo = w.getBlockAt(x + dx, piso - dy, z + dz);
                    if (abajo.getType().isSolid()) break;
                    abajo.setType(pal[0], false);
                }
                for (int dy = 1; dy <= 5; dy++) w.getBlockAt(x + dx, piso + dy, z + dz).setType(Material.AIR, false);
            }
        }
        if (ruina) {
            int[][] esquinas = {{-3, -2}, {3, 2}, {-2, 3}, {2, -3}};
            for (int[] e : esquinas) {
                int alto = 1 + r.nextInt(3);
                for (int dy = 1; dy <= alto; dy++) {
                    w.getBlockAt(x + e[0], piso + dy, z + e[1]).setType(dy == alto && r.nextBoolean()
                            ? Material.STONE_BRICK_SLAB : pal[0] == Material.GRAVEL ? Material.COBBLESTONE : pal[0], false);
                }
            }
        }
        Block fuego = w.getBlockAt(x, piso + 1, z);
        fuego.setType(Material.CAMPFIRE, false);
        if (fuego.getBlockData() instanceof Campfire c) {
            c.setLit(true);
            c.setSignalFire(false);
            fuego.setBlockData(c, false);
        }
        Location centro = fuego.getLocation().add(0.5, 0, 0.5);
        // La espada enroscada clavada en la hoguera.
        w.spawn(centro.clone().add(0, 0.95, 0), ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.IRON_SWORD));
            d.setTransformation(new Transformation(new Vector3f(),
                    new AxisAngle4f((float) Math.toRadians(-135), 0, 0, 1), new Vector3f(1.1f, 1.1f, 1.1f), new AxisAngle4f()));
            d.getPersistentDataContainer().set(Claves.DECORADO, PersistentDataType.BYTE, (byte) 1);
        });
        w.spawn(centro.clone().add(0, 2.0, 0), TextDisplay.class, t -> {
            t.text(Util.mm(zona.color + nombre));
            t.setBillboard(Display.Billboard.CENTER);
            t.setShadowed(true);
            t.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            t.getPersistentDataContainer().set(Claves.DECORADO, PersistentDataType.BYTE, (byte) 1);
        });
        return fuego;
    }

    /** Plaza del Santuario alrededor de la hoguera inicial: piso de piedra, faroles y lugares para los NPC. */
    private void construirSantuario(World w, Block fuego) {
        int x = fuego.getX(), y = fuego.getY() - 1, z = fuego.getZ();
        Random r = new Random(7);
        Material[] pal = Zona.SANTUARIO.paleta;
        for (int dx = -9; dx <= 9; dx++) {
            for (int dz = -9; dz <= 9; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > 9.3) continue;
                Block b = w.getBlockAt(x + dx, y, z + dz);
                if (b.equals(fuego)) continue;
                b.setType(d < 2.5 ? Material.CHISELED_STONE_BRICKS : pal[r.nextInt(pal.length)], false);
                for (int dy = 1; dy <= 10; dy++) {
                    Block abajo = w.getBlockAt(x + dx, y - dy, z + dz);
                    if (abajo.getType().isSolid()) break;
                    abajo.setType(Material.STONE_BRICKS, false);
                }
                for (int dy = 1; dy <= 6; dy++) {
                    Block arriba = w.getBlockAt(x + dx, y + dy, z + dz);
                    if (arriba.equals(fuego) || arriba.getType() == Material.CAMPFIRE) continue;
                    if (arriba.getType() != Material.AIR) arriba.setType(Material.AIR, false);
                }
            }
        }
        // Faroles en cuatro columnas.
        for (int[] c : new int[][]{{-7, -7}, {7, -7}, {-7, 7}, {7, 7}}) {
            for (int dy = 1; dy <= 3; dy++) w.getBlockAt(x + c[0], y + dy, z + c[1]).setType(Material.STONE_BRICK_WALL, false);
            w.getBlockAt(x + c[0], y + 4, z + c[1]).setType(Material.LANTERN, false);
        }
        // Pedestales de los NPC (herrero al este, guardiana al norte, mercader al oeste, altar al sur).
        for (int[] c : new int[][]{{6, 0}, {0, -6}, {-6, 0}, {0, 6}}) {
            w.getBlockAt(x + c[0], y, z + c[1]).setType(Material.POLISHED_DEEPSLATE, false);
        }
        w.getBlockAt(x + 7, y + 1, z).setType(Material.ANVIL, false);
        w.getBlockAt(x + 7, y + 1, z + 1).setType(Material.BLAST_FURNACE, false);
        w.getBlockAt(x, y + 1, z + 7).setType(Material.LODESTONE, false);
        w.getBlockAt(x - 7, y + 1, z).setType(Material.BARREL, false);
    }

    /** Lugar del Santuario para un NPC (desplazamiento desde la hoguera). */
    public Location lugarSantuario(int dx, int dz) {
        Hoguera h = hogueras.get("santuario");
        if (h == null) return mundo().getSpawnLocation();
        Location l = new Location(mundo(), h.x() + dx + 0.5, h.y(), h.z() + dz + 0.5);
        l.setDirection(new org.bukkit.util.Vector(-dx, 0, -dz));
        return l;
    }

    // ------------------------------------------------------------------ biomas

    @EventHandler
    public void alCargarChunk(ChunkLoadEvent e) {
        if (Modo.de(e.getWorld()) != Modo.RPG) return;
        Chunk c = e.getChunk();
        Integer v = c.getPersistentDataContainer().get(CLAVE_BIOMA, PersistentDataType.INTEGER);
        if (v != null && v >= VERSION_BIOMAS) return;
        pintar(c);
        c.getPersistentDataContainer().set(CLAVE_BIOMA, PersistentDataType.INTEGER, VERSION_BIOMAS);
    }

    private void pintar(Chunk c) {
        World w = c.getWorld();
        int bx = c.getX() << 4, bz = c.getZ() << 4;
        // Atajo: chunks lejos de toda zona.
        boolean cerca = false;
        for (Zona z : Zona.values()) {
            if (z.bioma == null) continue;
            double d = Math.hypot(bx + 8 - (sx + z.dx), bz + 8 - (sz + z.dz));
            if (d < z.radio + 24) cerca = true;
        }
        if (!cerca) return;
        int min = w.getMinHeight(), max = w.getMaxHeight();
        for (int cx = 0; cx < 16; cx += 4) {
            for (int cz = 0; cz < 16; cz += 4) {
                int x = bx + cx + 2, z = bz + cz + 2;
                Zona arriba = Zona.de(x - sx, 64, z - sz);
                Zona abajo = Zona.de(x - sx, Zona.Y_TEMPLO - 1, z - sz);
                for (int y = min; y < max; y += 4) {
                    Zona zn = y < Zona.Y_TEMPLO ? abajo : arriba;
                    if (zn == null || zn.bioma == null) continue;
                    w.setBiome(x, y, z, zn.bioma);
                }
            }
        }
    }

    // ------------------------------------------------------------------ ciclo

    private void tick() {
        World w = mundo();
        // Noche Roja: cada 3 días del reloj del mundo, durante la segunda mitad (10 minutos).
        long t = w.getGameTime();
        boolean roja = (t / 24000) % 3 == 2 && t % 24000 >= 12000;
        if (roja != nocheRoja) {
            nocheRoja = roja;
            for (Player p : w.getPlayers()) {
                if (roja) {
                    Util.titulo(p, "<dark_red><bold>LA NOCHE ROJA", "<red>Los muertos vuelven más fuertes", 800, 3500, 1200);
                    p.playSound(p, Sound.ENTITY_WITHER_SPAWN, 0.6f, 0.5f);
                } else {
                    Util.titulo(p, "", "<gray>La Noche Roja terminó", 400, 2000, 800);
                }
            }
            if (roja) modo.enemigos().reiniciarTodos();
        }
        for (Player p : w.getPlayers()) {
            Zona z = zona(p.getLocation());
            Zona antes = zonaActual.get(p.getUniqueId());
            boolean paso = z == null;
            if (z != antes || (paso != enPaso.getOrDefault(p.getUniqueId(), !paso))) {
                zonaActual.put(p.getUniqueId(), z);
                enPaso.put(p.getUniqueId(), paso);
                if (z != null) {
                    String sub = z == Zona.SANTUARIO ? "<gray>Aquí la llama todavía arde"
                            : "<gray>Nivel " + z.nivelMin + "–" + z.nivelMax;
                    Util.titulo(p, z.color + "<bold>" + z.nombre, sub, 600, 2500, 900);
                    p.playSound(p, Sound.BLOCK_BELL_RESONATE, 0.5f, 0.6f);
                }
            }
            long hora = nocheRoja ? 18000 : z != null ? z.hora : 12600;
            if (p.getPlayerTime() % 24000 != hora || p.isPlayerTimeRelative()) p.setPlayerTime(hora, false);
            WeatherType clima = z != null && z.lluvia && !nocheRoja ? WeatherType.DOWNFALL : WeatherType.CLEAR;
            if (p.getPlayerWeather() != clima) p.setPlayerWeather(clima);
            Location l = p.getLocation();
            if (nocheRoja) {
                p.spawnParticle(Particle.DUST, l.clone().add(0, 3, 0), 25, 10, 5, 10, 0,
                        new Particle.DustOptions(Color.fromRGB(170, 10, 10), 1.6f));
                p.spawnParticle(Particle.CRIMSON_SPORE, l.clone().add(0, 2, 0), 20, 8, 4, 8, 0);
            } else if (z != null && z.ambiente != null) {
                p.spawnParticle(z.ambiente, l.clone().add(0, 4, 0), 30, 10, 5, 10, 0);
            }
        }
    }

    public String nombreZona(Player p) {
        Zona z = zonaActual.get(p.getUniqueId());
        return z == null ? "<gray>Tierras de paso" : z.color + z.nombre;
    }

    public void olvidar(Player p) {
        zonaActual.remove(p.getUniqueId());
        enPaso.remove(p.getUniqueId());
        p.resetPlayerTime();
        p.resetPlayerWeather();
    }

    // ------------------------------------------------------------------ viaje

    public void abrirViaje(Player p) {
        DatosJugador d = plugin.almacen().de(p);
        Menu m = new Menu(4, "<dark_gray>Viajar a una hoguera");
        int slot = 0;
        for (Hoguera h : hogueras.values()) {
            boolean conocida = d.hogueras.contains(h.id()) || h.id().equals("santuario");
            if (!conocida) continue;
            Material icono = h.zona() == Zona.SANTUARIO ? Material.CAMPFIRE : Material.SOUL_CAMPFIRE;
            m.poner(slot++, Util.item(icono, h.zona().color + "<bold>" + h.nombre(), "<gray>" + h.zona().nombre,
                    "", "<yellow>Clic para viajar"), pl -> viajar(pl, h));
            if (slot >= 35) break;
        }
        m.poner(35, Util.item(Material.ARROW, "<white>Volver a la hoguera"), modo::abrirHoguera);
        m.abrir(p);
    }

    private void viajar(Player p, Hoguera h) {
        Block b = mundo().getBlockAt(h.x(), h.y(), h.z());
        p.closeInventory();
        p.getWorld().spawnParticle(Particle.ASH, p.getLocation().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.02);
        Location destino = ModoRpg.lugarJunto(b);
        p.teleport(destino);
        p.playSound(p, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.6f);
        plugin.almacen().de(p).hoguera = destino;
        p.sendActionBar(Component.text(h.nombre()));
    }
}
