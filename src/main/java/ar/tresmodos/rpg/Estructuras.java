package ar.tresmodos.rpg;

import ar.tresmodos.Modo;
import ar.tresmodos.TresModos;
import ar.tresmodos.Util;
import io.papermc.paper.event.player.PlayerArmSwingEvent;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.Lectern;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Switch;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Estructuras de las zonas (las arma {@link ConstructorZonas} una sola vez), y lo que vive en ellas:
 * cofres con botín, atriles con las notas del lore, atajos (portones que se abren desde un solo
 * lado) y paredes ilusorias (desaparecen al golpearlas). El estado queda en rpg-estructuras.yml.
 */
public class Estructuras implements Listener {
    /** Subir este número vuelve a construir (sobre lo que haya). */
    private static final int VERSION = 1;
    /** La misma marca que usa Invasores: un cofre revisado ya no puede ser mímico. */
    private static final NamespacedKey REVISADO = new NamespacedKey("tresmodos", "rpg_cofre_revisado");

    private static final class Atajo {
        final String id, nombre;
        final List<int[]> reja;
        final int[] palanca;
        final int nx, nz;
        boolean abierto;

        Atajo(String id, String nombre, List<int[]> reja, int[] palanca, int nx, int nz, boolean abierto) {
            this.id = id;
            this.nombre = nombre;
            this.reja = reja;
            this.palanca = palanca;
            this.nx = nx;
            this.nz = nz;
            this.abierto = abierto;
        }
    }

    private final TresModos plugin;
    private final ModoRpg modo;
    private final File archivo;
    private final Map<String, Atajo> atajos = new LinkedHashMap<>();
    /** Posición de cada reja y palanca -> atajo. */
    private final Map<Long, Atajo> deBloque = new HashMap<>();
    private final Map<String, List<int[]>> ilusorias = new LinkedHashMap<>();
    private final Set<String> reveladas = new HashSet<>();
    private final Map<Long, String> ilusorio = new HashMap<>();
    private final Set<UUID> avisadosPantano = new HashSet<>();

    public Estructuras(TresModos plugin, ModoRpg modo) {
        this.plugin = plugin;
        this.modo = modo;
        this.archivo = new File(plugin.getDataFolder(), "rpg-estructuras.yml");
        cargar();
        // Después de las hogueras (tick 1) y las arenas (tick 3).
        Bukkit.getScheduler().runTaskLater(plugin, this::generarSiFalta, 40);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickPantanos, 40, 20);
    }

    private World mundo() {
        return plugin.mundos().de(Modo.RPG);
    }

    private static long clave(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    // ------------------------------------------------------------------ guardado

    private void cargar() {
        if (!archivo.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(archivo);
        ConfigurationSection a = y.getConfigurationSection("atajos");
        if (a != null) {
            for (String id : a.getKeys(false)) {
                ConfigurationSection s = a.getConfigurationSection(id);
                if (s == null) continue;
                registrarAtajo(new Atajo(id, s.getString("nombre", id), posiciones(s.getString("reja", "")),
                        posiciones(s.getString("palanca", "")).getFirst(), s.getInt("nx"), s.getInt("nz"), s.getBoolean("abierto")));
            }
        }
        ConfigurationSection i = y.getConfigurationSection("ilusorias");
        if (i != null) {
            for (String id : i.getKeys(false)) {
                registrarIlusoria(id, posiciones(i.getString(id + ".bloques", "")));
                if (i.getBoolean(id + ".revelada")) reveladas.add(id);
            }
        }
    }

    private void guardar(int version) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("version", version);
        for (Atajo t : atajos.values()) {
            String b = "atajos." + t.id + ".";
            y.set(b + "nombre", t.nombre);
            y.set(b + "reja", texto(t.reja));
            y.set(b + "palanca", texto(List.of(t.palanca)));
            y.set(b + "nx", t.nx);
            y.set(b + "nz", t.nz);
            y.set(b + "abierto", t.abierto);
        }
        for (Map.Entry<String, List<int[]>> en : ilusorias.entrySet()) {
            y.set("ilusorias." + en.getKey() + ".bloques", texto(en.getValue()));
            y.set("ilusorias." + en.getKey() + ".revelada", reveladas.contains(en.getKey()));
        }
        try {
            y.save(archivo);
        } catch (IOException e) {
            plugin.getLogger().warning("No pude guardar rpg-estructuras.yml: " + e.getMessage());
        }
    }

    private int versionGuardada() {
        return archivo.exists() ? YamlConfiguration.loadConfiguration(archivo).getInt("version", 0) : 0;
    }

    private static String texto(List<int[]> ps) {
        StringBuilder sb = new StringBuilder();
        for (int[] p : ps) {
            if (!sb.isEmpty()) sb.append(';');
            sb.append(p[0]).append(',').append(p[1]).append(',').append(p[2]);
        }
        return sb.toString();
    }

    private static List<int[]> posiciones(String s) {
        List<int[]> l = new ArrayList<>();
        for (String p : s.split(";")) {
            String[] c = p.split(",");
            if (c.length == 3) l.add(new int[]{Integer.parseInt(c[0].trim()), Integer.parseInt(c[1].trim()), Integer.parseInt(c[2].trim())});
        }
        if (l.isEmpty()) l.add(new int[]{0, -9999, 0});
        return l;
    }

    private void registrarAtajo(Atajo t) {
        atajos.put(t.id, t);
        for (int[] p : t.reja) deBloque.put(clave(p[0], p[1], p[2]), t);
        deBloque.put(clave(t.palanca[0], t.palanca[1], t.palanca[2]), t);
    }

    private void registrarIlusoria(String id, List<int[]> bloques) {
        ilusorias.put(id, bloques);
        for (int[] p : bloques) ilusorio.put(clave(p[0], p[1], p[2]), id);
    }

    // ------------------------------------------------------------------ construcción

    private void generarSiFalta() {
        if (versionGuardada() >= VERSION) return;
        MundoRpg m = modo.mundoRpg();
        if (m.hogueras().isEmpty()) {
            Bukkit.getScheduler().runTaskLater(plugin, this::generarSiFalta, 40);
            return;
        }
        List<ConstructorZonas.Libre> libres = new ArrayList<>();
        for (MundoRpg.Hoguera h : m.hogueras()) libres.add(new ConstructorZonas.Libre(h.x(), h.z(), h.id().equals("santuario") ? 14 : 8));
        for (String id : modo.jefes().ids()) {
            Jefes.Arena a = modo.jefes().arena(id);
            if (a != null && a.centro.getY() > 0) libres.add(new ConstructorZonas.Libre(a.centro.getBlockX(), a.centro.getBlockZ(), (int) a.radio + 8));
        }
        // La Puerta del Abismo, junto al centro de la Costa.
        libres.add(new ConstructorZonas.Libre(m.origenX() + Zona.COSTA.dx + 6, m.origenZ() + Zona.COSTA.dz, 9));
        Zona[] zonas = {Zona.ALDEA, Zona.BOSQUE, Zona.CIUDADELA, Zona.COSTA};
        // Una zona por segundo, para no congelar el server de un saque.
        for (int i = 0; i < zonas.length; i++) {
            Zona z = zonas[i];
            boolean ultima = i == zonas.length - 1;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                long t0 = System.currentTimeMillis();
                construir(z, libres);
                plugin.getLogger().info("RPG: estructuras de " + z.nombre + " en " + (System.currentTimeMillis() - t0) + " ms.");
                if (ultima) {
                    atrilSantuario();
                    guardar(VERSION);
                }
            }, 20L * (i + 1));
        }
    }

    private void construir(Zona z, List<ConstructorZonas.Libre> libres) {
        MundoRpg m = modo.mundoRpg();
        int cx = m.origenX() + z.dx, cz = m.origenZ() + z.dz;
        double[] h = z.haciaSantuario();
        int ax = Math.abs(h[0]) >= Math.abs(h[1]) ? (int) Math.signum(h[0]) : 0;
        int az = ax == 0 ? (int) Math.signum(h[1]) : 0;
        ConstructorZonas c = new ConstructorZonas(new LienzoMundo(mundo(), z), libres);
        long semilla = mundo().getSeed() ^ z.ordinal() * 0x9E3779B97F4A7C15L;
        switch (z) {
            case ALDEA -> c.aldea(cx, cz, ax, az, semilla);
            case BOSQUE -> c.bosque(cx, cz, ax, az, semilla);
            case CIUDADELA -> c.ciudadela(cx, cz, ax, az, semilla);
            case COSTA -> c.costa(cx, cz, ax, az, semilla);
            default -> { }
        }
    }

    /** La primera nota, en el Santuario, entre la guardiana y el mercader. */
    private void atrilSantuario() {
        MundoRpg.Hoguera h = modo.mundoRpg().hoguera("santuario");
        if (h == null) return;
        new LienzoMundo(mundo(), Zona.SANTUARIO).atril(h.x() - 4, h.y(), h.z() - 4, "south", "intro");
    }

    /** El mundo de verdad visto como {@link ConstructorZonas.Lienzo}. */
    private final class LienzoMundo implements ConstructorZonas.Lienzo {
        private final World w;
        private final Zona zona;
        private final Map<String, BlockData> cache = new HashMap<>();
        private final Random azar;

        LienzoMundo(World w, Zona zona) {
            this.w = w;
            this.zona = zona;
            this.azar = new Random(w.getSeed() + zona.ordinal());
        }

        private boolean ignorado(Material m) {
            return !m.isSolid() || Tag.LOGS.isTagged(m) || Tag.LEAVES.isTagged(m) || m == Material.MUSHROOM_STEM
                    || m == Material.BROWN_MUSHROOM_BLOCK || m == Material.RED_MUSHROOM_BLOCK || m == Material.BAMBOO
                    || m == Material.COBWEB || m == Material.CACTUS;
        }

        @Override
        public int suelo(int x, int z) {
            int y = w.getHighestBlockYAt(x, z, HeightMap.OCEAN_FLOOR);
            while (y > w.getMinHeight() + 1 && ignorado(w.getBlockAt(x, y, z).getType())) y--;
            return y;
        }

        private boolean esAgua(Material m) {
            return m == Material.WATER || m == Material.SEAGRASS || m == Material.TALL_SEAGRASS || m == Material.KELP
                    || m == Material.KELP_PLANT || m == Material.BUBBLE_COLUMN;
        }

        @Override
        public int agua(int x, int z) {
            int s = suelo(x, z), y = s + 1;
            while (y < w.getMaxHeight() && esAgua(w.getBlockAt(x, y, z).getType())) y++;
            return y - 1;
        }

        @Override
        public void poner(int x, int y, int z, String datos) {
            if (y <= w.getMinHeight() || y >= w.getMaxHeight()) return;
            BlockData d = cache.computeIfAbsent(datos, k -> {
                try {
                    return Bukkit.createBlockData("minecraft:" + k);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Estructuras: bloque inválido " + k);
                    return null;
                }
            });
            if (d != null) w.getBlockAt(x, y, z).setBlockData(d, false);
        }

        @Override
        public String bloque(int x, int y, int z) {
            return w.getBlockAt(x, y, z).getType().getKey().getKey();
        }

        @Override
        public void cofre(int x, int y, int z, String mira, String botin, boolean secreto) {
            poner(x, y, z, "chest[facing=" + mira + "]");
            if (!(w.getBlockAt(x, y, z).getState() instanceof Chest c)) return;
            int nivel = modo.mundoRpg().nivel(new Location(w, x, y, z));
            for (ItemStack it : botin(botin, nivel, azar)) c.getSnapshotInventory().addItem(it);
            if (secreto) c.getPersistentDataContainer().set(REVISADO, PersistentDataType.BYTE, (byte) 1);
            c.update(true, false);
        }

        @Override
        public void atril(int x, int y, int z, String mira, String nota) {
            poner(x, y, z, "lectern[facing=" + mira + ",has_book=true]");
            if (!(w.getBlockAt(x, y, z).getState() instanceof Lectern l)) return;
            l.getSnapshotInventory().setItem(0, libro(nota));
            l.update(true, false);
        }

        @Override
        public void atajo(String id, String nombre, List<int[]> reja, int[] palanca, int nx, int nz) {
            registrarAtajo(new Atajo(id, nombre, reja, palanca, nx, nz, false));
        }

        @Override
        public void ilusoria(String id, List<int[]> bloques) {
            registrarIlusoria(id, bloques);
            reveladas.remove(id);
        }
    }

    // ------------------------------------------------------------------ botín

    private static List<ItemStack> botin(String tipo, int nivel, Random r) {
        List<ItemStack> l = new ArrayList<>();
        switch (tipo) {
            case "secreto_aldea" -> {
                l.add(ArmaRpg.KATANA.crear(2));
                l.add(ObjetosRpg.fragmento(3));
                l.add(ObjetosRpg.lagrima(1));
            }
            case "secreto_bosque" -> {
                l.add(ArmaRpg.GUADANIA.crear(3));
                l.add(Magia.Hechizo.NIEBLA_CORROSIVA.pergamino());
                l.add(ObjetosRpg.hierba(3));
            }
            case "secreto_ciudadela" -> {
                l.add(ArmaRpg.ALABARDA.crear(5));
                l.add(ObjetosRpg.escama(2));
                l.add(Magia.Hechizo.LANZA_SAGRADA.pergamino());
            }
            case "secreto_costa" -> {
                l.add(ArmaRpg.ESTOQUE.crear(6));
                l.add(ObjetosRpg.escama(3));
                l.add(Magia.Hechizo.LANZA_VACIO.pergamino());
            }
            default -> {
                int n = 1 + r.nextInt(3);
                for (int i = 0; i < n; i++) {
                    l.add(switch (r.nextInt(7)) {
                        case 0 -> ObjetosRpg.fragmento(1 + r.nextInt(2));
                        case 1 -> ObjetosRpg.hierba(1 + r.nextInt(2));
                        case 2 -> ObjetosRpg.cuchillos(3 + r.nextInt(4));
                        case 3 -> ObjetosRpg.bombaFuego(1 + r.nextInt(2));
                        case 4 -> ObjetosRpg.resina(r.nextBoolean() ? ObjetosRpg.Resina.FUEGO : ObjetosRpg.Resina.RAYO, 1);
                        case 5 -> ObjetosRpg.ceniza(1);
                        default -> nivel >= 25 && r.nextBoolean() ? ObjetosRpg.escama(1) : ObjetosRpg.fragmento(1);
                    });
                }
            }
        }
        return l;
    }

    // ------------------------------------------------------------------ notas

    private static final Map<String, String[]> NOTAS = new LinkedHashMap<>();

    static {
        NOTAS.put("intro", new String[]{"Crónica del Último Fuego", "Hermano Ulric",
                "Valdren tuvo sol. Lo digo para que alguien lo recuerde.\n\nUna noche el mar respiró, y desde entonces llueve ceniza.",
                "La gente se apagó por dentro. Caminan, comen y gritan, pero ya no son nadie. Les decimos Huecos.",
                "Vos todavía tenés la chispa. Por eso la llama te trajo acá.\n\nSos un Portador de Ceniza.",
                "Cuatro Señores custodian lo que queda: el Glotón en la Aldea, la Tejedora en el Bosque, el Caballero en la Ciudadela y el Vigía en la Costa.",
                "Sus almas abren la Puerta del Abismo. Debajo duerme lo que nos hizo esto.\n\nNo dejes que despierte del todo."});
        NOTAS.put("aldea", new String[]{"Último sermón", "Padre Ansel",
                "Orsk, el molinero, dejó de moler hace tres inviernos. Dice que tiene hambre. Que el hambre le habla.",
                "Ayer faltaron dos niños. Hoy cerramos el portón y colgamos a los que se volvieron Huecos.",
                "El portón solo se abre desde adentro. Que nadie lo abra para los de afuera.",
                "A los fieles: lo que la iglesia guarda tras el altar no es para los ojos. Es para la mano que golpea la piedra."});
        NOTAS.put("bosque", new String[]{"Diario de la herbolaria", "Mirna",
                "No bebas del pantano. No te metas en el agua. Las esporas la pudrieron y el veneno entra por la piel.",
                "La hierba purificadora cura lo que el agua enferma. Llevá siempre.",
                "En el Árbol Hueco vive la Tejedora. Teje con los hilos de los que se perdieron.",
                "El árbol más viejo de este lado no está muerto: está vacío. Golpeá su corteza y vas a ver lo que escondí."});
        NOTAS.put("ciudadela", new String[]{"Registro del capellán", "Capellán Hirst",
                "Sir Aldric fue el campeón del rey. Bajó a la costa a ver la piedra del mar y volvió con los ojos negros.",
                "Ahora se sienta en el Salón del Trono y no deja pasar a nadie. Todo su arte se puede desviar, si tenés el pulso.",
                "La puerta grande quedó trabada. Se acciona desde adentro de la muralla; por afuera, ni un ariete.",
                "El tesoro del rey duerme en la torre del homenaje, tras una puerta que no existe."});
        NOTAS.put("costa", new String[]{"Bitácora del Albatros", "Capitán Morrow",
                "La niebla no se va más. En el faro vive algo pálido que grita, y el grito te vuelve loco.",
                "Lo vi copiarse a sí mismo. Solo el de verdad deja huellas en la arena.",
                "Encallamos el Albatros en la playa negra. En la proa, detrás de la tabla floja, guardé lo que quedaba.",
                "Hay una roca en el mar con una escalera que baja. La puerta pide cuatro almas. Que nadie se las dé."});
    }

    private static ItemStack libro(String nota) {
        String[] n = NOTAS.getOrDefault(nota, new String[]{"Nota", "Anónimo", "(ilegible)"});
        ItemStack it = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) it.getItemMeta();
        meta.title(Component.text(n[0]));
        meta.author(Component.text(n[1]));
        for (int i = 2; i < n.length; i++) meta.addPages(Component.text(n[i]));
        it.setItemMeta(meta);
        return it;
    }

    // ------------------------------------------------------------------ atajos

    @EventHandler(ignoreCancelled = false)
    public void alUsar(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) return;
        Block b = e.getClickedBlock();
        if (Modo.de(b.getWorld()) != Modo.RPG) return;
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            revelar(e.getPlayer(), b);
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Atajo t = deBloque.get(clave(b.getX(), b.getY(), b.getZ()));
        if (t == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (t.abierto) return;
        int[] c = t.reja.get(t.reja.size() / 2);
        double lado = (p.getX() - (c[0] + 0.5)) * t.nx + (p.getZ() - (c[2] + 0.5)) * t.nz;
        if (lado <= 0) {
            Util.barra(p, "<gray>No se abre de este lado.");
            p.playSound(b.getLocation(), Sound.BLOCK_IRON_DOOR_CLOSE, 0.7f, 0.6f);
            return;
        }
        abrir(t, p);
    }

    private void abrir(Atajo t, Player p) {
        t.abierto = true;
        guardar(versionGuardada());
        World w = mundo();
        Block palanca = w.getBlockAt(t.palanca[0], t.palanca[1], t.palanca[2]);
        if (palanca.getBlockData() instanceof Switch s) {
            s.setPowered(true);
            palanca.setBlockData(s, false);
        }
        // La reja sube de a una fila.
        int yMin = Integer.MAX_VALUE, yMax = Integer.MIN_VALUE;
        for (int[] r : t.reja) {
            yMin = Math.min(yMin, r[1]);
            yMax = Math.max(yMax, r[1]);
        }
        Location centro = new Location(w, t.reja.get(t.reja.size() / 2)[0] + 0.5, yMin + 1, t.reja.get(t.reja.size() / 2)[2] + 0.5);
        w.playSound(centro, Sound.BLOCK_IRON_DOOR_OPEN, 1.2f, 0.5f);
        for (int fila = yMin; fila <= yMax; fila++) {
            int f = fila;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (int[] r : t.reja) if (r[1] == f) w.getBlockAt(r[0], r[1], r[2]).setType(Material.AIR, false);
                w.playSound(centro, Sound.BLOCK_CHAIN_PLACE, 1f, 0.6f);
            }, 6L * (fila - yMin + 1));
        }
        Util.titulo(p, "", "<gray>Se abrió un atajo: <white>" + t.nombre, 200, 2000, 600);
    }

    // ------------------------------------------------------------------ paredes ilusorias

    @EventHandler
    public void alGolpear(PlayerArmSwingEvent e) {
        Player p = e.getPlayer();
        if (Modo.de(p.getWorld()) != Modo.RPG || ilusorio.isEmpty()) return;
        RayTraceResult r = p.rayTraceBlocks(4.5);
        if (r == null || r.getHitBlock() == null) return;
        revelar(p, r.getHitBlock());
    }

    private void revelar(Player p, Block b) {
        String id = ilusorio.get(clave(b.getX(), b.getY(), b.getZ()));
        if (id == null || reveladas.contains(id)) return;
        reveladas.add(id);
        guardar(versionGuardada());
        World w = b.getWorld();
        for (int[] q : ilusorias.get(id)) {
            Block x = w.getBlockAt(q[0], q[1], q[2]);
            w.spawnParticle(Particle.BLOCK, x.getLocation().add(0.5, 0.5, 0.5), 14, 0.3, 0.3, 0.3, 0, x.getBlockData());
            x.setType(Material.AIR, false);
        }
        w.playSound(b.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.2f, 0.8f);
        w.playSound(b.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.6f);
        Util.titulo(p, "", "<gray>La pared era una ilusión", 100, 1600, 500);
    }

    // ------------------------------------------------------------------ pantanos

    /** El agua del Bosque Podrido envenena. */
    private void tickPantanos() {
        World w = mundo();
        if (w == null) return;
        for (Player p : w.getPlayers()) {
            if (!p.isInWater() || modo.mundoRpg().zona(p.getLocation()) != Zona.BOSQUE) continue;
            modo.estados().acumular(p, Estado.VENENO, 14, null);
            if (avisadosPantano.add(p.getUniqueId())) Util.barra(p, "<dark_green>El agua del pantano está envenenada.");
        }
    }

    public void olvidar(UUID id) {
        avisadosPantano.remove(id);
    }
}
