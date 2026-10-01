package ar.tresmodos.mundo;

import ar.tresmodos.Util;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.WorldInfo;

import java.util.Random;

/**
 * Ciudad procedural para el modo GTA. Todo se calcula por columna a partir de coordenadas globales,
 * así los edificios que cruzan chunks salen idénticos de los dos lados.
 *
 * Cuadrícula de período P = 64: calle de 10 bloques, vereda de 2 y un lote de 50x50 dividido en
 * cuatro sublotes de 25x25 (edificio, plaza o estacionamiento).
 */
public class GeneradorCiudad extends GeneradorBase {
    public static final int P = 64;
    public static final int CALLE = 10;
    public static final int SUELO = 64; // bloque del piso de la calle
    private static final long SEMILLA = 0x6A7A_2026L;

    private static final Material[] FACHADAS = {
            Material.WHITE_CONCRETE, Material.LIGHT_GRAY_CONCRETE, Material.GRAY_CONCRETE, Material.BRICKS,
            Material.SMOOTH_SANDSTONE, Material.TERRACOTTA, Material.CYAN_TERRACOTTA, Material.QUARTZ_BLOCK,
            Material.POLISHED_ANDESITE, Material.DEEPSLATE_TILES, Material.LIGHT_BLUE_TERRACOTTA, Material.BLACK_CONCRETE
    };
    private static final Material[] VIDRIOS = {
            Material.LIGHT_BLUE_STAINED_GLASS, Material.GLASS, Material.GRAY_STAINED_GLASS, Material.CYAN_STAINED_GLASS
    };

    private final BlockData escalera = Bukkit.createBlockData("minecraft:ladder[facing=east]");
    private final BlockData hojas = Bukkit.createBlockData("minecraft:oak_leaves[persistent=true]");

    @Override
    public void generateNoise(WorldInfo info, Random random, int cx, int cz, ChunkData data) {
        int min = info.getMinHeight();
        data.setRegion(0, min, 0, 16, min + 1, 16, Material.BEDROCK);
        data.setRegion(0, min + 1, 0, 16, SUELO - 3, 16, Material.STONE);
        data.setRegion(0, SUELO - 3, 0, 16, SUELO, 16, Material.DIRT);
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                columna(cx * 16 + lx, cz * 16 + lz, lx, lz, data);
            }
        }
    }

    private void columna(int x, int z, int lx, int lz, ChunkData d) {
        int mx = Math.floorMod(x, P), mz = Math.floorMod(z, P);
        boolean calleNS = mx < CALLE, calleEO = mz < CALLE;

        if (calleNS || calleEO) {
            Material m = Material.BLACK_CONCRETE;
            boolean cruce = calleNS && calleEO;
            if (!cruce) {
                if (calleNS && (mx == 4 || mx == 5) && Math.floorMod(z, 6) < 3) m = Material.YELLOW_CONCRETE;
                if (calleEO && (mz == 4 || mz == 5) && Math.floorMod(x, 6) < 3) m = Material.YELLOW_CONCRETE;
                // senda peatonal justo después de cada cruce
                if (calleNS && (mz == CALLE || mz == CALLE + 1 || mz == P - 1 || mz == P - 2) && x % 2 == 0) m = Material.WHITE_CONCRETE;
                if (calleEO && (mx == CALLE || mx == CALLE + 1 || mx == P - 1 || mx == P - 2) && z % 2 == 0) m = Material.WHITE_CONCRETE;
            }
            d.setBlock(lx, SUELO, lz, m);
            return;
        }

        boolean vereda = mx < CALLE + 2 || mx >= P - 2 || mz < CALLE + 2 || mz >= P - 2;
        if (vereda) {
            d.setBlock(lx, SUELO, lz, Material.SMOOTH_STONE);
            boolean esquinaX = mx == CALLE + 1 || mx == P - 2;
            boolean esquinaZ = mz == CALLE + 1 || mz == P - 2;
            if (esquinaX && esquinaZ) { // farola
                for (int y = SUELO + 1; y <= SUELO + 4; y++) d.setBlock(lx, y, lz, Material.IRON_BARS);
                d.setBlock(lx, SUELO + 5, lz, Material.SEA_LANTERN);
            }
            return;
        }

        // Lote interior 50x50 -> 4 sublotes de 25x25
        int ix = mx - (CALLE + 2), iz = mz - (CALLE + 2);
        int subX = ix / 25, subZ = iz / 25;
        int px = ix % 25, pz = iz % 25;
        long id = Util.hash(Math.floorDiv(x, P) * 2L + subX, Math.floorDiv(z, P) * 2L + subZ, SEMILLA);
        int tipo = (int) (id % 100);

        if (tipo < 68) edificio(x, z, lx, lz, px, pz, id, d);
        else if (tipo < 86) plaza(lx, lz, px, pz, d);
        else estacionamiento(lx, lz, px, pz, d);
    }

    /** Altura de un edificio: más alto cerca del centro de la ciudad, en múltiplos de 5 (pisos). */
    public static int alturaEdificio(long id, int x, int z) {
        double dist = Math.sqrt((double) x * x + (double) z * z);
        double factor = Math.max(0.25, 1.0 - dist / 900.0);
        int pisos = 2 + (int) ((id >>> 8) % 11); // 2..12
        pisos = Math.max(2, (int) Math.round(pisos * factor * 1.2));
        return pisos * 5;
    }

    private void edificio(int x, int z, int lx, int lz, int px, int pz, long id, ChunkData d) {
        // Entorno del edificio: franja de pasto de 2 bloques
        if (px < 2 || px > 22 || pz < 2 || pz > 22) {
            d.setBlock(lx, SUELO, lz, (px + pz) % 7 == 0 ? Material.GRASS_BLOCK : Material.STONE_BRICKS);
            return;
        }
        int h = alturaEdificio(id, x, z);
        Material fachada = FACHADAS[(int) ((id >>> 16) % FACHADAS.length)];
        Material vidrio = VIDRIOS[(int) ((id >>> 24) % VIDRIOS.length)];
        boolean borde = px == 2 || px == 22 || pz == 2 || pz == 22;
        boolean esquina = (px == 2 || px == 22) && (pz == 2 || pz == 22);
        int techo = SUELO + h;

        d.setBlock(lx, SUELO, lz, Material.POLISHED_ANDESITE);

        if (borde) {
            int w = (px == 2 || px == 22) ? pz : px; // coordenada a lo largo de la pared
            for (int y = SUELO + 1; y < techo; y++) {
                int ly = y - SUELO;
                int r = (ly - 1) % 5;
                boolean puerta = ly <= 3 && w >= 11 && w <= 13;
                if (puerta) continue;
                boolean ventana = !esquina && (r == 1 || r == 2) && w % 3 != 0;
                d.setBlock(lx, y, lz, ventana ? vidrio : fachada);
            }
            d.setBlock(lx, techo, lz, Material.GRAY_CONCRETE);
            d.setBlock(lx, techo + 1, lz, fachada); // parapeto
            return;
        }

        // Interior: losas cada 5 bloques, luz en el centro, escalera en la esquina (3,3)
        boolean hueco = px == 3 && pz == 3;
        for (int y = SUELO + 5; y <= techo; y += 5) {
            if (hueco) continue;
            Material m = (y == techo) ? Material.GRAY_CONCRETE : Material.SMOOTH_STONE;
            if (px == 12 && pz == 12 && y != techo) m = Material.SEA_LANTERN;
            d.setBlock(lx, y, lz, m);
        }
        if (px == 12 && pz == 12) d.setBlock(lx, techo - 1, lz, Material.SEA_LANTERN);
        if (hueco) {
            for (int y = SUELO + 1; y <= techo; y++) d.setBlock(lx, y, lz, escalera);
        }
    }

    private void plaza(int lx, int lz, int px, int pz, ChunkData d) {
        d.setBlock(lx, SUELO, lz, (px == 12 || pz == 12) ? Material.GRAVEL : Material.GRASS_BLOCK);
        if (px == 12 || pz == 12) {
            d.setBlock(lx, SUELO - 1, lz, Material.STONE);
            return;
        }
        // árboles en una grilla de 8, con copa calculada por columna
        int tx = Math.round((px - 4) / 8f) * 8 + 4, tz = Math.round((pz - 4) / 8f) * 8 + 4;
        if (tx == 12 || tz == 12 || tx < 2 || tz < 2 || tx > 22 || tz > 22) return;
        int dx = Math.abs(px - tx), dz = Math.abs(pz - tz);
        if (dx == 0 && dz == 0) {
            for (int y = SUELO + 1; y <= SUELO + 5; y++) d.setBlock(lx, y, lz, Material.OAK_LOG);
            d.setBlock(lx, SUELO + 6, lz, hojas);
        } else if (dx <= 2 && dz <= 2 && !(dx == 2 && dz == 2)) {
            for (int y = SUELO + 4; y <= SUELO + 5; y++) d.setBlock(lx, y, lz, hojas);
            if (dx <= 1 && dz <= 1) d.setBlock(lx, SUELO + 6, lz, hojas);
        }
    }

    private void estacionamiento(int lx, int lz, int px, int pz, ChunkData d) {
        Material m = Material.GRAY_CONCRETE;
        if (pz >= 3 && pz <= 21 && pz != 12 && px % 5 == 0) m = Material.WHITE_CONCRETE;
        d.setBlock(lx, SUELO, lz, m);
    }
}
