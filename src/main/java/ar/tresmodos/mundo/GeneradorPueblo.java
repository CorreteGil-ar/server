package ar.tresmodos.mundo;

import ar.tresmodos.Util;
import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Random;

/**
 * Mundo del Shooter: desierto con dunas alrededor de Pueblo Atómico. El pueblo y el decorado salen
 * del plano (PuebloAtomico), que se vuelca encima del terreno.
 */
public class GeneradorPueblo extends GeneradorBase {
    private static final int RADIO = 176;
    private final Plano plano;

    public GeneradorPueblo(Plano plano) {
        this.plano = plano;
    }

    @Override
    public void generateNoise(WorldInfo info, Random random, int cx, int cz, ChunkData d) {
        int suelo = PuebloAtomico.SUELO;
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = cx * 16 + lx, z = cz * 16 + lz;
                if (Math.abs(x) > RADIO || Math.abs(z) > RADIO) continue;
                int alto = suelo + duna(x, z);
                for (int y = suelo - 12; y < suelo - 3; y++) d.setBlock(lx, y, lz, Material.SANDSTONE);
                for (int y = suelo - 3; y <= alto; y++) d.setBlock(lx, y, lz, arena(x, y, z));
                long h = Util.hash(x, z, 404L) % 1000;
                if (alto > suelo || !cercaDelPueblo(x, z)) {
                    if (h < 6) d.setBlock(lx, alto + 1, lz, Material.DEAD_BUSH);
                    else if (h < 14) d.setBlock(lx, alto + 1, lz, Material.SHORT_DRY_GRASS);
                    else if (h == 20) {
                        for (int y = 1; y <= 2 + h % 2; y++) d.setBlock(lx, alto + y, lz, Material.CACTUS);
                    }
                }
            }
        }
        plano.volcar(cx, cz, d);
    }

    private static boolean cercaDelPueblo(int x, int z) {
        return Math.abs(x) <= PuebloAtomico.X_MAX + 3 && Math.abs(z) <= PuebloAtomico.Z_MAX + 3;
    }

    /** Altura de las dunas: nada cerca del pueblo ni sobre la ruta, y suaves más lejos. */
    private static int duna(int x, int z) {
        double borde = Math.max(Math.abs(x) - (PuebloAtomico.X_MAX + 6), Math.abs(z) - (PuebloAtomico.Z_MAX + 6));
        if (borde <= 0 || (x < 0 && Math.abs(z) <= 10)) return 0;
        double rampa = Math.min(1, borde / 25.0);
        double h = 2.2 * Math.sin(x * 0.061 + Math.cos(z * 0.043) * 1.7) + 1.6 * Math.cos(z * 0.078 + x * 0.02) + 1.2;
        return (int) Math.max(0, Math.round(h * rampa * 1.6));
    }

    private static Material arena(int x, int y, int z) {
        long h = Util.hash(x * 3 + y, z, 88L) % 100;
        if (h < 70) return Material.SAND;
        if (h < 85) return Material.SMOOTH_SANDSTONE;
        if (h < 95) return Material.SANDSTONE;
        return Material.TERRACOTTA;
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new BiomeProvider() {
            @Override
            public Biome getBiome(WorldInfo info, int x, int y, int z) {
                return Biome.DESERT;
            }

            @Override
            public List<Biome> getBiomes(WorldInfo info) {
                return List.of(Biome.DESERT);
            }
        };
    }
}
