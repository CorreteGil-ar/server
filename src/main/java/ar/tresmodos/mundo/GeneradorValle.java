package ar.tresmodos.mundo;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

/** Mundo de Guerra: vuelca el mapa Valle de Hierro columna por columna. */
public class GeneradorValle extends GeneradorBase {
    private static final Map<String, BlockData> CACHE = new ConcurrentHashMap<>();

    /** Texto de bloque → BlockData (con caché). */
    public static BlockData datos(String b) {
        return CACHE.computeIfAbsent(b, k -> Bukkit.createBlockData("minecraft:" + k));
    }

    @Override
    public void generateNoise(WorldInfo info, Random random, int cx, int cz, ChunkData d) {
        int min = info.getMinHeight();
        d.setRegion(0, min, 0, 16, min + 1, 16, Material.BEDROCK);
        d.setRegion(0, min + 1, 0, 16, ValleDeHierro.YMIN, 16, Material.STONE);
        String[] o = new String[ValleDeHierro.YMAX - ValleDeHierro.YMIN + 1];
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                ValleDeHierro.columna(cx * 16 + lx, cz * 16 + lz, o);
                for (int i = 0; i < o.length; i++) {
                    String b = o[i];
                    if (b == null || b.equals("air")) continue;
                    d.setBlock(lx, ValleDeHierro.YMIN + i, lz, datos(b));
                }
            }
        }
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo info, int x, int y, int z) {
                if (z > 74 && Math.abs(x) < 168) return Biome.FOREST;
                return Biome.PLAINS;
            }

            @Override
            public List<Biome> getBiomes(WorldInfo info) {
                return List.of(Biome.PLAINS, Biome.FOREST);
            }
        };
    }
}
