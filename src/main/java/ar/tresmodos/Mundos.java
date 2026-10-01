package ar.tresmodos;

import ar.tresmodos.mundo.GeneradorBase;
import ar.tresmodos.mundo.GeneradorValle;
import ar.tresmodos.mundo.GeneradorPueblo;
import ar.tresmodos.mundo.PuebloAtomico;
import ar.tresmodos.mundo.ValleDeHierro;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

/** Crea y configura los cuatro mundos, construye el lobby y la hoguera inicial del RPG. */
public class Mundos {
    private final TresModos plugin;
    private final Map<Modo, World> mundos = new EnumMap<>(Modo.class);

    public Mundos(TresModos plugin) {
        this.plugin = plugin;
    }

    public World de(Modo m) {
        return mundos.get(m);
    }

    public void crear() {
        World lobby = new WorldCreator(Modo.LOBBY.mundo).generator(new GeneradorBase.Vacio())
                .generateStructures(false).createWorld();
        World guerra = new WorldCreator(Modo.GUERRA.mundo).generator(new GeneradorValle())
                .generateStructures(false).createWorld();
        World shooter = new WorldCreator(Modo.SHOOTER.mundo).generator(new GeneradorPueblo(PuebloAtomico.plano()))
                .generateStructures(false).createWorld();
        World rpg = new WorldCreator(Modo.RPG.mundo).type(WorldType.NORMAL).createWorld();
        mundos.put(Modo.LOBBY, lobby);
        mundos.put(Modo.GUERRA, guerra);
        mundos.put(Modo.SHOOTER, shooter);
        mundos.put(Modo.RPG, rpg);

        // ---- Lobby ----
        comunes(lobby, false);
        lobby.setDifficulty(Difficulty.PEACEFUL);
        lobby.setGameRule(GameRules.ADVANCE_TIME, false);
        lobby.setTime(6000);
        lobby.setSpawnLocation(0, 65, 0);
        construirLobby(lobby);

        // ---- Guerra: Valle de Hierro, 512 × 512, sin mobs; el clima lo elige cada partida ----
        comunes(guerra, false);
        guerra.setDifficulty(Difficulty.NORMAL);
        guerra.setGameRule(GameRules.ADVANCE_TIME, false);
        guerra.setGameRule(GameRules.KEEP_INVENTORY, false);
        guerra.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        guerra.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, true);
        guerra.setGameRule(GameRules.FALL_DAMAGE, true);
        guerra.setTime(6000);
        guerra.setSpawnLocation(-ValleDeHierro.BASE_X - 26, ValleDeHierro.SUELO + 1, 0);
        guerra.getWorldBorder().setCenter(0, 0);
        guerra.getWorldBorder().setSize(ValleDeHierro.MITAD * 2);

        // ---- Shooter: Pueblo Atómico a mediodía, sin daño por caída, regeneración propia ----
        comunes(shooter, false);
        shooter.setDifficulty(Difficulty.EASY);
        shooter.setGameRule(GameRules.ADVANCE_TIME, false);
        shooter.setTime(6000);
        shooter.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        shooter.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, false);
        shooter.setGameRule(GameRules.FALL_DAMAGE, false);
        shooter.setGameRule(GameRules.KEEP_INVENTORY, false);
        shooter.setSpawnLocation(-30, PuebloAtomico.SUELO + 1, 0);
        shooter.getWorldBorder().setCenter(0, 0);
        shooter.getWorldBorder().setSize(352);

        // ---- RPG: mundo vanilla en difícil, sin regeneración natural ----
        // Los enemigos salen de encuentros diseñados (rpg.Enemigos), no de spawns naturales. En el
        // server siempre es de noche (los no-muertos no se queman y las arañas atacan); cada jugador
        // ve la hora de su zona (atardecer o noche) con setPlayerTime.
        rpg.setDifficulty(Difficulty.HARD);
        rpg.setGameRule(GameRules.KEEP_INVENTORY, true);
        rpg.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, false);
        rpg.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        rpg.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        rpg.setGameRule(GameRules.SPAWN_MONSTERS, false);
        rpg.setGameRule(GameRules.SPAWN_PATROLS, false);
        rpg.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        rpg.setGameRule(GameRules.RAIDS, false);
        rpg.setGameRule(GameRules.MOB_GRIEFING, false);
        rpg.setGameRule(GameRules.ADVANCE_TIME, false);
        rpg.setGameRule(GameRules.ADVANCE_WEATHER, false);
        rpg.setTime(18000);
        rpg.setStorm(false);
        rpg.setThundering(false);
        hogueraInicial(rpg);
        rpg.getWorldBorder().setCenter(rpg.getSpawnLocation());
        rpg.getWorldBorder().setSize(1700);
    }

    private void comunes(World w, boolean mobs) {
        w.setGameRule(GameRules.SPAWN_MOBS, mobs);
        w.setGameRule(GameRules.SPAWN_MONSTERS, mobs);
        w.setGameRule(GameRules.SPAWN_PATROLS, false);
        w.setGameRule(GameRules.SPAWN_WANDERING_TRADERS, false);
        w.setGameRule(GameRules.SPAWN_WARDENS, false);
        w.setGameRule(GameRules.RAIDS, false);                       // antes DISABLE_RAIDS = true
        w.setGameRule(GameRules.ADVANCE_WEATHER, false);
        w.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0); // antes DO_FIRE_TICK = false
        w.setGameRule(GameRules.MOB_GRIEFING, false);
        w.setGameRule(GameRules.SHOW_ADVANCEMENT_MESSAGES, false);
        w.setGameRule(GameRules.SPAWN_PHANTOMS, false);
        w.setStorm(false);
        w.setThundering(false);
        w.setClearWeatherDuration(Integer.MAX_VALUE / 2);
    }

    public Location spawn(Modo m) {
        World w = de(m);
        Location s = w.getSpawnLocation().toCenterLocation();
        s.setY(Math.floor(s.getY()));
        return s;
    }

    // ------------------------------------------------------------------ lobby

    /** Pads del lobby: el jugador pisa la placa y entra al modo. */
    public static final int[][] PADS = {{-8, 0}, {0, -8}, {8, 0}}; // Guerra, Shooter, RPG
    public static final Modo[] PAD_MODO = {Modo.GUERRA, Modo.SHOOTER, Modo.RPG};

    private void construirLobby(World w) {
        w.getChunkAt(0, 0).load(true);
        w.getChunkAt(-1, 0).load(true);
        w.getChunkAt(0, -1).load(true);
        w.getChunkAt(-1, -1).load(true);

        if (w.getBlockAt(0, 64, 0).getType() == Material.AIR) {
            int r = 12;
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    boolean borde = Math.abs(x) == r || Math.abs(z) == r;
                    Material m;
                    if (borde) m = Material.QUARTZ_BRICKS;
                    else if ((Math.abs(x) + Math.abs(z)) % 6 == 0) m = Material.SEA_LANTERN;
                    else m = ((x + z) & 1) == 0 ? Material.POLISHED_DEEPSLATE : Material.DEEPSLATE_TILES;
                    w.getBlockAt(x, 64, z).setType(m, false);
                    if (borde) w.getBlockAt(x, 65, z).setType(Material.QUARTZ_SLAB, false);
                }
            }
            for (int i = 0; i < PADS.length; i++) {
                w.getBlockAt(PADS[i][0], 65, PADS[i][1]).setType(Material.LIGHT_WEIGHTED_PRESSURE_PLATE, false);
            }
            w.getBlockAt(0, 64, 0).setType(Material.BEACON, false);
            w.getBlockAt(0, 65, 0).setType(Material.AIR, false);
            for (int x = -1; x <= 1; x++)
                for (int z = -1; z <= 1; z++)
                    w.getBlockAt(x, 63, z).setType(Material.IRON_BLOCK, false);
        }

        // Color de cada placa (se repinta siempre: el primer modo pasó de GTA a Guerra).
        Material[] color = {Material.EMERALD_BLOCK, Material.REDSTONE_BLOCK, Material.AMETHYST_BLOCK};
        for (int i = 0; i < PADS.length; i++) {
            int px = PADS[i][0], pz = PADS[i][1];
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    w.getBlockAt(px + dx, 64, pz + dz).setType(color[i], false);
        }

        portales(w);

        // Carteles flotantes (se regeneran en cada arranque)
        for (Entity e : w.getEntities()) {
            if (e.getPersistentDataContainer().has(Claves.DISPLAY_LOBBY)) e.remove();
        }
        String[] textos = {
                "<dark_green><bold>GUERRA</bold></dark_green>\n<gray>Valle de Hierro · captura la bandera\n<gray>Azul contra Rojo, tanques y aviones",
                "<red><bold>SHOOTER</bold></red>\n<gray>Pueblo Atómico · todos contra todos\n<gray>clases, rachas y bomba atómica",
                "<light_purple><bold>RPG / SOULS</bold></light_purple>\n<gray>Stamina, esquive, hogueras\n<gray>almas y jefes"
        };
        double[] alturas = {71.0, 70.8, 72.6};
        for (int i = 0; i < PADS.length; i++) {
            cartel(w, new Location(w, PADS[i][0] + 0.5, alturas[i], PADS[i][1] + 0.5), textos[i]);
        }
        cartel(w, new Location(w, 0.5, 68.2, 0.5),
                "<white><bold>TRES MODOS</bold></white>\n<gray>Pisá una placa o usá la estrella");
    }

    /** Lugar del cartel de estadísticas personales de cada portal (Guerra, Shooter, RPG). */
    public static final double[][] CARTEL_STATS = {{-3.6, 66.6, 6.2}, {4.4, 66.6, -3.6}, {5.8, 66.6, -4.6}};

    private static void poner(World w, int x, int y, int z, String datos) {
        w.getBlockAt(x, y, z).setBlockData(Bukkit.createBlockData("minecraft:" + datos), false);
    }

    /**
     * Portales temáticos alrededor de cada placa: el hangar de Guerra, el contenedor militar del
     * Shooter y el arco de piedra con fuego del RPG. Se rehacen en cada arranque (son pocos bloques).
     */
    private void portales(World w) {
        // Shooter (0, -8): contenedor de 7 × 5 × 7 abierto hacia el centro.
        for (int x = -3; x <= 3; x++)
            for (int z = -11; z <= -5; z++)
                for (int y = 65; y <= 69; y++) {
                    boolean lado = Math.abs(x) == 3, fondo = z == -11, techo = y == 69;
                    boolean marco = (Math.abs(x) == 3 && (z == -11 || z == -5)) || techo && (Math.abs(x) == 3 || z == -11 || z == -5);
                    if (!lado && !fondo && !techo) continue;
                    String m = marco ? "gray_concrete" : ((lado ? z : x) & 1) == 0 ? "red_terracotta" : "red_concrete";
                    poner(w, x, y, z, m);
                }
        poner(w, 0, 69, -8, "redstone_lamp[lit=true]");
        poner(w, 0, 65, -10, "target");
        poner(w, -2, 65, -10, "barrel[facing=up]");
        poner(w, 2, 65, -10, "barrel[facing=up]");
        poner(w, 2, 66, -10, "barrel[facing=up]");
        for (int x : new int[]{-3, -2, 2, 3}) {
            poner(w, x, 65, -3, "packed_mud");
            poner(w, x, 66, -3, "mud_brick_slab[type=bottom]");
        }
        poner(w, -4, 65, -6, "iron_bars");
        poner(w, 4, 65, -6, "iron_bars");

        // Guerra (-8, 0): hangar en arco, abierto hacia el centro, con las banderas de los equipos.
        int[][] perfil = {{3, 67}, {2, 68}, {1, 69}, {0, 69}};
        for (int x = -11; x <= -5; x++)
            for (int[] pz : perfil)
                for (int s : new int[]{-1, 1}) {
                    int z = pz[0] * s;
                    String m = x % 2 == 0 ? "iron_block" : "light_gray_concrete";
                    if (pz[0] == 3) {
                        for (int y = 65; y <= pz[1]; y++) poner(w, x, y, z, m);
                    } else {
                        poner(w, x, pz[1], z, m);
                    }
                }
        for (int z = -3; z <= 3; z++) {
            int alto = Math.abs(z) == 3 ? 67 : Math.abs(z) == 2 ? 68 : 69;
            for (int y = 65; y <= alto; y++) poner(w, -11, y, z, z == 0 && y <= 66 ? "iron_door[facing=east,half="
                    + (y == 65 ? "lower" : "upper") + "]" : "gray_concrete");
        }
        poner(w, -8, 69, 0, "sea_lantern");
        poner(w, -10, 65, 2, "spruce_planks");
        poner(w, -10, 66, 2, "barrel[facing=up]");
        poner(w, -10, 65, -2, "spruce_planks");
        for (int[] b : new int[][]{{-4, 4, 0}, {-4, -4, 1}}) {
            for (int y = 65; y <= 70; y++) poner(w, b[0], y, b[1], "iron_bars");
            poner(w, b[0], 71, b[1], (b[2] == 0 ? "blue" : "red") + "_banner[rotation=4]");
        }

        // RPG (8, 0): arco de piedra en ruinas con dos fuegos y calaveras.
        String[] piedra = {"stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks"};
        java.util.Random r = new java.util.Random(3);
        for (int x = 7; x <= 9; x++) {
            for (int s : new int[]{-1, 1}) {
                for (int y = 65; y <= 69; y++) poner(w, x, y, 3 * s, piedra[r.nextInt(3)]);
                poner(w, x, 69, 2 * s, "stone_brick_stairs[facing=" + (s > 0 ? "south" : "north") + ",half=top]");
            }
            for (int z = -3; z <= 3; z++) poner(w, x, 70, z, z == 0 ? "chiseled_stone_bricks" : piedra[r.nextInt(3)]);
        }
        poner(w, 8, 71, 3, "campfire[lit=true]");
        poner(w, 8, 71, -3, "campfire[lit=true]");
        poner(w, 8, 69, 0, "iron_chain[axis=y]");
        poner(w, 8, 68, 0, "soul_lantern[hanging=true]");
        poner(w, 10, 65, 3, "skeleton_skull[rotation=12]");
        poner(w, 10, 65, -2, "cobblestone");
        poner(w, 11, 65, 2, "mossy_cobblestone");
        poner(w, 10, 65, -4, "candle[candles=3,lit=true]");
    }

    private void cartel(World w, Location l, String texto) {
        w.spawn(l, TextDisplay.class, t -> {
            t.text(Util.mm(texto));
            t.setBillboard(Display.Billboard.CENTER);
            t.setShadowed(true);
            t.setBackgroundColor(org.bukkit.Color.fromARGB(90, 0, 0, 0));
            t.getPersistentDataContainer().set(Claves.DISPLAY_LOBBY, PersistentDataType.BYTE, (byte) 1);
        });
    }

    // ------------------------------------------------------------------ RPG

    private void hogueraInicial(World rpg) {
        File marca = new File(plugin.getDataFolder(), "hoguera-inicial.txt");
        if (marca.exists()) return;
        Location s = rpg.getSpawnLocation();
        Block suelo = rpg.getHighestBlockAt(s.getBlockX(), s.getBlockZ());
        Block fuego = suelo.getRelative(0, 1, 0);
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                if (Math.abs(dx) + Math.abs(dz) <= 3) suelo.getRelative(dx, 0, dz).setType(Material.COBBLESTONE, false);
        fuego.setType(Material.CAMPFIRE, false);
        rpg.setSpawnLocation(fuego.getX() + 2, fuego.getY(), fuego.getZ());
        rpg.getWorldBorder().setCenter(rpg.getSpawnLocation());
        rpg.spawn(fuego.getLocation().add(0.5, 1.6, 0.5), TextDisplay.class, t -> {
            t.text(Component.text("Hoguera del Santuario").color(net.kyori.adventure.text.format.NamedTextColor.GOLD));
            t.setBillboard(Display.Billboard.CENTER);
        });
        try {
            plugin.getDataFolder().mkdirs();
            if (!marca.createNewFile()) plugin.getLogger().warning("No pude crear " + marca);
        } catch (IOException e) {
            plugin.getLogger().warning("No pude marcar la hoguera inicial: " + e.getMessage());
        }
        Bukkit.getLogger().info("[TresModos] Hoguera inicial del RPG en " + fuego.getLocation());
    }
}
