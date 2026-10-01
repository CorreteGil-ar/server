package ar.tresmodos.mundo;

import ar.tresmodos.Util;
import org.bukkit.Material;
import org.bukkit.generator.WorldInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Arena "Contenedores" para el modo COD: 81x81 bloques en el vacío, con contenedores de colores,
 * cajas y un muro perimetral con barreras invisibles arriba.
 */
public class GeneradorArena extends GeneradorBase {
    public static final int RADIO = 40; // la zona jugable va de -40 a 40
    public static final int SUELO = 64;

    /** Caja sólida entre (x1,y1,z1) y (x2,y2,z2), inclusive. */
    public record Caja(int x1, int z1, int x2, int z2, int y1, int y2, Material mat) {
        boolean cubre(int x, int z) {
            return x >= x1 && x <= x2 && z >= z1 && z <= z2;
        }
    }

    private static final Material[] COLORES = {
            Material.RED_CONCRETE, Material.BLUE_CONCRETE, Material.ORANGE_CONCRETE, Material.GREEN_CONCRETE,
            Material.CYAN_CONCRETE, Material.BROWN_CONCRETE, Material.YELLOW_TERRACOTTA, Material.WHITE_CONCRETE
    };

    private static final List<Caja> CAJAS = armarCajas();

    public static List<Caja> cajas() {
        return CAJAS;
    }

    private static List<Caja> armarCajas() {
        Random r = new Random(20260930L);
        List<Caja> l = new ArrayList<>();
        int[] filas = {-30, -18, -6, 6, 18, 30};
        for (int i = 0; i < filas.length; i++) {
            int cz = filas[i];
            boolean alLargoX = i % 2 == 0;
            int offset = (i % 2 == 0) ? 0 : 5;
            for (int cx = -30 + offset; cx <= 30; cx += 12) {
                if (Math.abs(cx) <= 3 && Math.abs(cz) <= 6) continue; // centro despejado
                if (r.nextInt(100) < 22) continue;
                Material m = COLORES[r.nextInt(COLORES.length)];
                int x1, x2, z1, z2;
                if (alLargoX) { x1 = cx - 3; x2 = cx + 3; z1 = cz - 1; z2 = cz + 1; }
                else { x1 = cx - 1; x2 = cx + 1; z1 = cz - 3; z2 = cz + 3; }
                l.add(new Caja(x1, z1, x2, z2, SUELO + 1, SUELO + 3, m));
                if (r.nextInt(100) < 20) { // contenedor apilado
                    l.add(new Caja(x1, z1, x2, z2, SUELO + 4, SUELO + 6, COLORES[r.nextInt(COLORES.length)]));
                }
            }
        }
        for (int i = 0; i < 16; i++) { // cajas sueltas de 2x2
            int x = r.nextInt(2 * RADIO - 6) - (RADIO - 3);
            int z = r.nextInt(2 * RADIO - 6) - (RADIO - 3);
            int alto = 1 + r.nextInt(2);
            Material m = r.nextBoolean() ? Material.BARREL : Material.HAY_BLOCK;
            l.add(new Caja(x, z, x + 1, z + 1, SUELO + 1, SUELO + alto, m));
        }
        return Collections.unmodifiableList(l);
    }

    /** Puntos libres para reaparecer, con un bloque de margen alrededor de cualquier obstáculo. */
    public static List<int[]> puntosLibres() {
        List<int[]> puntos = new ArrayList<>();
        for (int x = -RADIO + 2; x <= RADIO - 2; x += 3) {
            for (int z = -RADIO + 2; z <= RADIO - 2; z += 3) {
                boolean libre = true;
                for (Caja c : CAJAS) {
                    if (x >= c.x1() - 1 && x <= c.x2() + 1 && z >= c.z1() - 1 && z <= c.z2() + 1) {
                        libre = false;
                        break;
                    }
                }
                if (libre) puntos.add(new int[]{x, z});
            }
        }
        return puntos;
    }

    @Override
    public void generateNoise(WorldInfo info, Random random, int cx, int cz, ChunkData d) {
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = cx * 16 + lx, z = cz * 16 + lz;
                int ax = Math.abs(x), az = Math.abs(z);
                if (ax > RADIO + 1 || az > RADIO + 1) continue;
                for (int y = SUELO - 4; y < SUELO; y++) d.setBlock(lx, y, lz, Material.STONE);
                d.setBlock(lx, SUELO, lz, piso(x, z));
                if (ax == RADIO + 1 || az == RADIO + 1) {
                    for (int y = SUELO + 1; y <= SUELO + 4; y++) d.setBlock(lx, y, lz, Material.GRAY_CONCRETE);
                    for (int y = SUELO + 5; y <= SUELO + 30; y++) d.setBlock(lx, y, lz, Material.BARRIER);
                    continue;
                }
                for (Caja c : CAJAS) {
                    if (!c.cubre(x, z)) continue;
                    for (int y = c.y1(); y <= c.y2(); y++) d.setBlock(lx, y, lz, c.mat());
                }
            }
        }
    }

    private Material piso(int x, int z) {
        long h = Util.hash(Math.floorDiv(x, 3), Math.floorDiv(z, 3), 77L) % 100;
        if (h < 55) return Material.STONE_BRICKS;
        if (h < 72) return Material.ANDESITE;
        if (h < 86) return Material.CRACKED_STONE_BRICKS;
        if (h < 94) return Material.COARSE_DIRT;
        return Material.POLISHED_ANDESITE;
    }
}
