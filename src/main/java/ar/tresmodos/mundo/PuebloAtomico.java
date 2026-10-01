package ar.tresmodos.mundo;

import ar.tresmodos.Util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Pueblo Atómico, el mapa del Shooter (inspirado en Nuketown): dos casas de los años 50 enfrentadas
 * sobre una calle sin salida con rotonda, en medio de un campo de pruebas nucleares en el desierto.
 *
 * Ejes: x hacia el este, z hacia el sur. Zona jugable x -38..38, z -30..30, suelo en y = 64.
 * La casa amarilla (norte) se arma a mano y la verde (sur) es la misma parcela girada 180°.
 */
public final class PuebloAtomico {
    public static final int SUELO = 64;
    public static final int X_MIN = -38, X_MAX = 38, Z_MIN = -30, Z_MAX = 30;
    /** Altura máxima de los pies para aparecer (planta alta de las casas). */
    public static final int Y_MAX_APARICION = 70;

    private static final String REVESTIMIENTO = "yellow_terracotta";
    private static final String MOLDURA = "smooth_quartz";
    private static final String TEJA = "deepslate_tile_stairs";

    /** Maniquí: posición, rumbo y colores de ropa (camisa, pantalón) en RGB. */
    public record Maniqui(double x, double y, double z, float yaw, int camisa, int pantalon) {}

    /** Cartel con texto que pone el plugin: posición, rumbo y texto MiniMessage. */
    public record Cartel(double x, double y, double z, float yaw, String texto) {}

    private static Plano plano;

    private PuebloAtomico() {}

    /** El plano completo y cerrado; se arma la primera vez (hay que llamarlo desde el hilo principal). */
    public static synchronized Plano plano() {
        if (plano == null) {
            plano = construir();
            plano.cerrar();
        }
        return plano;
    }

    public static boolean enZonaJugable(double x, double z) {
        return x >= X_MIN && x < X_MAX + 1 && z >= Z_MIN && z < Z_MAX + 1;
    }

    // ------------------------------------------------------------------ construcción

    /** Arma el plano sin cerrarlo (no necesita servidor: lo usan las herramientas de vista previa). */
    public static Plano construir() {
        Plano p = new Plano();
        suelo(p);
        parcelaAmarilla(p);
        p.copiarGirado180(-38, -30, 13, -7, Map.of(
                "yellow_terracotta", "lime_terracotta",
                "cyan_concrete", "red_concrete",
                "red_carpet", "blue_carpet",
                "red_bed", "light_blue_bed"));
        loteNoreste(p);
        loteSuroeste(p);
        calle(p);
        vehiculosCentro(p);
        podio(p);
        limites(p);
        desierto(p);
        return p;
    }

    /** Pasto seco en toda la zona jugable, marcado como lugar de aparición. */
    private static void suelo(Plano p) {
        for (int x = X_MIN; x <= X_MAX; x++) {
            for (int z = Z_MIN; z <= Z_MAX; z++) {
                long h = Util.hash(x, z, 1955L) % 100;
                p.set(x, SUELO, z, h < 82 ? "grass_block" : h < 94 ? "coarse_dirt" : "rooted_dirt");
            }
        }
        p.marcarPiso(X_MIN, Z_MIN, X_MAX, Z_MAX, SUELO);
    }

    // ------------------------------------------------------------------ casa amarilla y su parcela

    private static void parcelaAmarilla(Plano p) {
        casa(p);
        garaje(p);
        // Entrada de autos y camino a la puerta.
        p.caja(-1, SUELO, -10, 4, SUELO, -7, "light_gray_concrete");
        p.caja(-11, SUELO, -9, -10, SUELO, -7, "smooth_stone");
        // Ligustros bajos al frente: cobertura agachado, se saltan.
        p.caja(-17, SUELO + 1, -8, -14, SUELO + 1, -8, "oak_leaves[persistent=true]");
        p.caja(-8, SUELO + 1, -8, -5, SUELO + 1, -8, "oak_leaves[persistent=true]");
        p.caja(-30, SUELO + 1, -8, -26, SUELO + 1, -8, "oak_leaves[persistent=true]");
        p.set(-12, SUELO + 1, -7, "spruce_fence");
        p.set(-12, SUELO + 2, -7, "barrel[facing=east]"); // buzón
        flores(p, -17, -9, -14, -9);
        flores(p, -8, -9, -5, -9);

        // Patio lateral oeste: árbol, cucha y una reposera.
        arbol(p, -28, -16);
        arbol(p, -27, -28);
        p.caja(-24, SUELO + 1, -19, -23, SUELO + 1, -18, "spruce_planks");
        p.caja(-24, SUELO + 2, -19, -23, SUELO + 2, -18, "spruce_slab[type=bottom]");
        p.set(-22, SUELO + 1, -12, "white_concrete");
        p.set(-22, SUELO + 1, -13, "quartz_stairs[facing=north]");
        auto(p, -35, -16, "cyan_concrete");

        // Cercas blancas con huecos para flanquear por los patios.
        cercaX(p, -38, -32, -23, "birch_fence");
        cercaX(p, -28, -19, -23, "birch_fence");
        cercaX(p, 6, 8, -22, "birch_fence");
        cercaX(p, 11, 13, -22, "birch_fence");
        cercaX(p, -38, 13, Z_MIN, "birch_fence");
        cercaZ(p, X_MIN, -29, -10, "birch_fence");

        // Patio trasero: pileta vacía, quincho, hamaca y un galpón.
        pileta(p, -12, -29, -4, -25);
        galpon(p, -36, -29, -32, -25);
        hamaca(p, -24, -27);
        p.set(-17, SUELO + 1, -26, "blast_furnace[facing=south]"); // parrilla
        p.set(-16, SUELO + 1, -26, "smooth_stone_slab[type=bottom]");
        p.set(-15, SUELO + 1, -26, "smooth_stone_slab[type=bottom]");
        // Huerta y cucha detrás del garaje.
        for (int x = -1; x <= 5; x++)
            for (int z = -28; z <= -25; z++) {
                p.set(x, SUELO, z, "coarse_dirt");
                if ((x + z) % 2 == 0) p.set(x, SUELO + 1, z, "short_dry_grass");
            }
        p.caja(8, SUELO + 1, -28, 9, SUELO + 1, -27, "spruce_planks");
        p.caja(8, SUELO + 2, -28, 9, SUELO + 2, -27, "spruce_slab[type=bottom]");
        p.set(8, SUELO + 1, -27, "air");
    }

    /** Casa de dos plantas: x -18..-3, z -22..-10. Planta baja y 65-68, alta y 70-73, techo a dos aguas. */
    private static void casa(Plano p) {
        int x1 = -18, x2 = -3, z1 = -22, z2 = -10;
        // Pisos
        p.caja(x1, SUELO, z1, x2, SUELO, z2, "spruce_planks");
        for (int x = x1 + 1; x <= x2 - 1; x++)
            for (int z = z1 + 1; z <= -17; z++) p.set(x, SUELO, z, ((x + z) & 1) == 0 ? "white_concrete" : "black_concrete");
        // Paredes de las dos plantas y franja blanca entre pisos
        p.paredes(x1, SUELO + 1, z1, x2, SUELO + 4, z2, REVESTIMIENTO);
        p.paredes(x1, SUELO + 5, z1, x2, SUELO + 5, z2, MOLDURA);
        p.paredes(x1, SUELO + 6, z1, x2, SUELO + 10, z2, REVESTIMIENTO);
        for (int[] e : new int[][]{{x1, z1}, {x1, z2}, {x2, z1}, {x2, z2}}) {
            p.caja(e[0], SUELO + 1, e[1], e[0], SUELO + 10, e[1], "quartz_pillar");
        }
        // Entrepiso y cielorraso
        p.caja(x1 + 1, SUELO + 5, z1 + 1, x2 - 1, SUELO + 5, z2 - 1, "spruce_planks");
        p.caja(x1 + 1, SUELO + 10, z1 + 1, x2 - 1, SUELO + 10, z2 - 1, "spruce_planks");
        p.marcarPiso(x1 + 1, z1 + 1, x2 - 1, z2 - 1, SUELO + 5);
        techoDosAguas(p, x1, x2, z1, z2);
        p.caja(-6, SUELO + 11, -20, -6, SUELO + 17, -19, "bricks"); // chimenea

        // Puertas (huecos) y ventanas
        p.aire(-11, SUELO + 1, z2, -10, SUELO + 3, z2);           // puerta principal
        p.caja(-11, SUELO + 4, z2 + 1, -10, SUELO + 4, z2 + 1, "smooth_quartz_slab[type=top]"); // alero
        p.aire(-7, SUELO + 1, z1, -6, SUELO + 3, z1);             // puerta trasera
        p.aire(x2, SUELO + 1, -15, x2, SUELO + 3, -14);           // al garaje
        filaVentana(p, -16, -13, SUELO + 2, SUELO + 3, z2);
        filaVentana(p, -7, -5, SUELO + 2, SUELO + 3, z2);
        filaVentana(p, -16, -14, SUELO + 7, SUELO + 8, z2);       // ventanas de francotirador
        filaVentana(p, -8, -6, SUELO + 7, SUELO + 8, z2);
        filaVentana(p, -14, -12, SUELO + 2, SUELO + 3, z1);
        filaVentana(p, -11, -9, SUELO + 7, SUELO + 8, z1);
        colVentana(p, x1, SUELO + 2, SUELO + 3, -14, -13);
        colVentana(p, x1, SUELO + 7, SUELO + 8, -19, -18);
        colVentana(p, x1, SUELO + 7, SUELO + 8, -14, -12);

        // Escalera junto a la pared oeste: sube hacia el norte de z -11 a z -15.
        for (int i = 0; i <= 4; i++) {
            p.caja(-17, SUELO + 1 + i, -11 - i, -16, SUELO + 1 + i, -11 - i, "spruce_stairs[facing=north]");
            if (SUELO + 2 + i <= SUELO + 4) p.aire(-17, SUELO + 2 + i, -11 - i, -16, SUELO + 4, -11 - i);
        }
        p.aire(-17, SUELO + 5, -14, -16, SUELO + 5, -11);          // hueco del entrepiso
        for (int z = -14; z <= -11; z++) p.set(-15, SUELO + 6, z, "spruce_fence[north=" + (z > -14) + ",south=" + (z < -11) + "]");

        // Planta baja: tabique entre living (frente) y cocina (fondo)
        p.caja(-15, SUELO + 1, -16, x2 - 1, SUELO + 4, -16, "white_terracotta");
        p.aire(-11, SUELO + 1, -16, -10, SUELO + 3, -16);
        p.aire(-15, SUELO + 1, -16, -14, SUELO + 3, -16);
        // Living: sillón, mesa ratona, televisor de los 50, alfombra y lámpara
        p.caja(-13, SUELO + 1, -15, -11, SUELO + 1, -15, "dark_oak_stairs[facing=north]");
        p.caja(-13, SUELO + 1, -13, -11, SUELO + 1, -13, "red_carpet");
        p.caja(-13, SUELO + 1, -12, -11, SUELO + 1, -12, "red_carpet");
        p.set(-12, SUELO + 1, -13, "spruce_trapdoor[half=top,facing=north]");
        p.set(-12, SUELO + 1, -11, "jukebox");
        p.set(-14, SUELO + 1, -11, "potted_fern");
        p.set(-4, SUELO + 1, -11, "spruce_fence");
        p.set(-4, SUELO + 2, -11, "lantern");
        p.set(-4, SUELO + 1, -13, "bookshelf");
        p.set(-7, SUELO + 1, -13, "dark_oak_stairs[facing=west]");
        // Cocina: mesada, heladera redondeada, cocina, pileta y mesa con sillas
        p.caja(-17, SUELO + 1, -21, -17, SUELO + 2, -21, "quartz_pillar");
        p.caja(-16, SUELO + 1, -21, -13, SUELO + 1, -21, "smooth_quartz");
        p.set(-15, SUELO + 1, -21, "smoker[facing=south]");
        p.set(-14, SUELO + 1, -21, "cauldron");
        p.set(-16, SUELO + 3, -21, "spruce_trapdoor[half=top,facing=south,open=true]");
        p.set(-8, SUELO + 1, -19, "spruce_fence");
        p.set(-8, SUELO + 2, -19, "white_carpet");
        p.set(-9, SUELO + 1, -19, "spruce_stairs[facing=west]");
        p.set(-7, SUELO + 1, -19, "spruce_stairs[facing=east]");
        p.set(-4, SUELO + 1, -21, "barrel[facing=up]");

        // Planta alta: dormitorio grande al frente (con el hueco de la escalera) y pasillo/baño atrás
        p.caja(-15, SUELO + 6, -16, x2 - 1, SUELO + 9, -16, "white_terracotta");
        p.aire(-11, SUELO + 6, -16, -10, SUELO + 8, -16);
        p.caja(-17, SUELO + 6, -16, -16, SUELO + 9, -16, "white_terracotta");
        p.aire(-17, SUELO + 6, -16, -16, SUELO + 8, -16);          // llegada de la escalera
        p.caja(-9, SUELO + 6, z1 + 1, -9, SUELO + 9, -17, "white_terracotta");
        p.aire(-9, SUELO + 6, -19, -9, SUELO + 8, -19);
        p.set(-13, SUELO + 6, -15, "red_bed[facing=north,part=head]");
        p.set(-13, SUELO + 6, -14, "red_bed[facing=north,part=foot]");
        p.set(-6, SUELO + 6, -15, "blue_bed[facing=north,part=head]");
        p.set(-6, SUELO + 6, -14, "blue_bed[facing=north,part=foot]");
        p.caja(-4, SUELO + 6, -15, -4, SUELO + 7, -15, "spruce_planks");
        p.caja(-8, SUELO + 6, -11, -9, SUELO + 6, -11, "spruce_slab[type=bottom]");
        p.set(-5, SUELO + 6, -21, "cauldron");                      // bañera
        p.set(-7, SUELO + 6, -21, "quartz_stairs[facing=south]");   // inodoro
        p.set(-15, SUELO + 6, -21, "bookshelf");

        // Luz interior
        for (int[] l : new int[][]{{-13, -13}, {-6, -13}, {-13, -19}, {-6, -19}}) {
            p.set(l[0], SUELO + 4, l[1], "light[level=13]");
            p.set(l[0], SUELO + 9, l[1], "light[level=13]");
        }
    }

    /** Techo de tejas oscuras a dos aguas, cumbrera en el centro y aleros de un bloque. */
    private static void techoDosAguas(Plano p, int x1, int x2, int z1, int z2) {
        int base = SUELO + 11;
        int medio = (z1 + z2) / 2;
        for (int x = x1 - 1; x <= x2 + 1; x++) {
            for (int z = z1 - 1; z <= z2 + 1; z++) {
                int h = base - 1 + Math.min(z - (z1 - 1), (z2 + 1) - z);
                if (z == medio) p.set(x, h, z, "deepslate_tiles");
                else p.set(x, h, z, TEJA + "[facing=" + (z > medio ? "north" : "south") + "]");
                // Tímpanos de los extremos
                if ((x == x1 || x == x2) && z > z1 - 1 && z < z2 + 1) {
                    for (int y = base - 1; y < h; y++) p.set(x, y, z, REVESTIMIENTO);
                }
            }
        }
        p.set(x1, base + 2, (z1 + z2) / 2, "glass_pane[north=true,south=true]"); // ojo de buey en el tímpano
    }

    /** Garaje de techo plano con un auto de época adentro: x -2..5, z -21..-11. */
    private static void garaje(Plano p) {
        int x1 = -2, x2 = 5, z1 = -21, z2 = -11;
        p.caja(x1, SUELO, z1, x2, SUELO, z2, "smooth_stone");
        p.paredes(x1, SUELO + 1, z1, x2, SUELO + 4, z2, REVESTIMIENTO);
        p.caja(x1, SUELO + 5, z1, x2, SUELO + 5, z2, "smooth_stone");
        p.paredes(x1, SUELO + 6, z1, x2, SUELO + 6, z2, MOLDURA);
        for (int[] e : new int[][]{{x1, z1}, {x1, z2}, {x2, z1}, {x2, z2}}) {
            p.caja(e[0], SUELO + 1, e[1], e[0], SUELO + 4, e[1], "quartz_pillar");
        }
        p.aire(x1 + 1, SUELO + 1, z2, x2 - 1, SUELO + 3, z2);      // portón abierto
        p.aire(x1, SUELO + 1, -15, x1, SUELO + 3, -14);            // a la casa
        colVentana(p, x2, SUELO + 2, SUELO + 3, -17, -15);
        filaVentana(p, 1, 2, SUELO + 2, SUELO + 3, z1);
        auto(p, 0, -19, "cyan_concrete");
        p.caja(4, SUELO + 1, -20, 4, SUELO + 2, -20, "barrel[facing=up]");
        p.set(-1, SUELO + 1, -20, "hay_block");
        p.set(1, SUELO + 4, -16, "light[level=12]");
    }

    /** Sedán de los 50 de 3 × 7, con la trompa hacia el sur. x0, z0 = esquina noroeste. */
    private static void auto(Plano p, int x0, int z0, String color) {
        int x1 = x0, x2 = x0 + 2, za = z0, zb = z0 + 6;
        p.caja(x1, SUELO + 1, za, x2, SUELO + 1, zb, color);
        for (int[] r : new int[][]{{x1, za + 1}, {x2, za + 1}, {x1, zb - 1}, {x2, zb - 1}}) {
            p.set(r[0], SUELO + 1, r[1], "black_concrete");
        }
        p.caja(x1, SUELO + 2, za, x2, SUELO + 2, za + 1, color);           // baúl
        p.caja(x1, SUELO + 2, za + 2, x2, SUELO + 2, za + 4, "glass");      // cabina
        p.caja(x1, SUELO + 3, za + 2, x2, SUELO + 3, za + 4, color);        // techo
        p.caja(x1, SUELO + 2, zb - 1, x2, SUELO + 2, zb - 1, color);        // capó
        p.set(x1, SUELO + 1, zb, "white_concrete");                          // faros y cromados
        p.set(x2, SUELO + 1, zb, "white_concrete");
    }

    /** Pileta vacía de 2 de profundidad con escalera de mano. */
    private static void pileta(Plano p, int x1, int z1, int x2, int z2) {
        p.paredes(x1, SUELO, z1, x2, SUELO, z2, "smooth_quartz");
        for (int x = x1 + 1; x <= x2 - 1; x++) {
            for (int z = z1 + 1; z <= z2 - 1; z++) {
                p.set(x, SUELO - 2, z, ((x + z) & 1) == 0 ? "light_blue_terracotta" : "light_blue_concrete");
                p.set(x, SUELO - 1, z, "air");
                p.set(x, SUELO, z, "air");
            }
        }
        p.paredes(x1, SUELO - 1, z1, x2, SUELO - 1, z2, "light_blue_concrete");
        p.set(x1 + 1, SUELO - 1, z1 + 2, "ladder[facing=east]");
        p.set(x1 + 1, SUELO, z1 + 2, "ladder[facing=east]");
        p.set(x2 - 1, SUELO - 1, z1 + 1, "dead_bush");
    }

    private static void galpon(Plano p, int x1, int z1, int x2, int z2) {
        p.caja(x1, SUELO, z1, x2, SUELO, z2, "spruce_planks");
        p.paredes(x1, SUELO + 1, z1, x2, SUELO + 3, z2, "spruce_planks");
        p.caja(x1, SUELO + 4, z1, x2, SUELO + 4, z2, "spruce_slab[type=bottom]");
        p.aire(x2, SUELO + 1, (z1 + z2) / 2, x2, SUELO + 2, (z1 + z2) / 2);
        p.set(x1 + 1, SUELO + 1, z1 + 1, "barrel[facing=up]");
        p.set(x1 + 1, SUELO + 2, z1 + 1, "barrel[facing=up]");
        p.set(x1 + 2, SUELO + 1, z1 + 1, "hay_block");
        p.marcarPiso(x1 + 1, z1 + 1, x2 - 1, z2 - 1, SUELO);
    }

    private static void hamaca(Plano p, int x, int z) {
        p.caja(x, SUELO + 1, z, x, SUELO + 3, z, "spruce_fence");
        p.caja(x + 4, SUELO + 1, z, x + 4, SUELO + 3, z, "spruce_fence");
        cercaX(p, x, x + 4, z, "spruce_fence", SUELO + 4);
        for (int dx : new int[]{1, 3}) {
            p.caja(x + dx, SUELO + 2, z, x + dx, SUELO + 3, z, "iron_chain[axis=y]");
            p.set(x + dx, SUELO + 1, z, "spruce_slab[type=top]");
        }
    }

    private static void arbol(Plano p, int x, int z) {
        p.caja(x, SUELO + 1, z, x, SUELO + 5, z, "oak_log");
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++)
                for (int dy = 4; dy <= 7; dy++) {
                    int r = Math.abs(dx) + Math.abs(dz) + Math.abs(dy - 5);
                    if ((dx != 0 || dz != 0 || dy > 5) && r <= 3 && Util.hash(x + dx * 7, z + dz * 13 + dy, 3L) % 10 < 8) {
                        p.set(x + dx, SUELO + dy, z + dz, "oak_leaves[persistent=true]");
                    }
                }
    }

    private static void flores(Plano p, int x1, int z1, int x2, int z2) {
        String[] f = {"red_tulip", "white_tulip", "orange_tulip", "oxeye_daisy"};
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                p.set(x, SUELO, z, "coarse_dirt");
                p.set(x, SUELO + 1, z, f[(int) (Util.hash(x, z, 9L) % f.length)]);
            }
    }

    // ------------------------------------------------------------------ lotes únicos

    /** Lote del noreste, junto a la rotonda: casa rodante estacionada y cajones. */
    private static void loteNoreste(Plano p) {
        for (int x = 14; x <= X_MAX; x++)
            for (int z = Z_MIN; z <= -13; z++) {
                long h = Util.hash(x, z, 77L) % 10;
                p.set(x, SUELO, z, h < 5 ? "coarse_dirt" : h < 8 ? "dirt_path" : "grass_block");
            }
        casaRodante(p, 18, -22);
        p.caja(31, SUELO + 1, -27, 33, SUELO + 1, -25, "barrel[facing=up]");
        p.caja(32, SUELO + 2, -27, 33, SUELO + 2, -26, "barrel[facing=up]");
        p.caja(16, SUELO + 1, -28, 17, SUELO + 2, -27, "stripped_oak_wood");
        p.caja(34, SUELO + 1, -18, 36, SUELO + 1, -17, "spruce_slab[type=top]"); // mesa de picnic
        p.caja(34, SUELO + 1, -19, 36, SUELO + 1, -19, "spruce_stairs[facing=south]");
        p.caja(34, SUELO + 1, -16, 36, SUELO + 1, -16, "spruce_stairs[facing=north]");
        p.caja(26, SUELO + 1, -28, 27, SUELO + 1, -27, "hay_block");
        cercaZ(p, 14, Z_MIN, -22, "birch_fence");
        cercaZ(p, 14, -18, -13, "birch_fence");
        cercaZ(p, X_MAX, Z_MIN, -13, "birch_fence");
        cercaX(p, 15, X_MAX, Z_MIN, "birch_fence");
    }

    /** Casa rodante hueca (se puede entrar): 11 × 4, puerta al sur. */
    private static void casaRodante(Plano p, int x0, int z0) {
        int x1 = x0, x2 = x0 + 10, z1 = z0, z2 = z0 + 3;
        p.caja(x1, SUELO + 1, z1, x2, SUELO + 1, z2, "white_concrete");
        for (int x : new int[]{x1 + 2, x2 - 2}) {
            p.set(x, SUELO + 1, z1, "black_concrete");
            p.set(x, SUELO + 1, z2, "black_concrete");
        }
        p.paredes(x1, SUELO + 2, z1, x2, SUELO + 2, z2, "light_blue_concrete");
        p.paredes(x1, SUELO + 3, z1, x2, SUELO + 3, z2, "white_concrete");
        p.caja(x1, SUELO + 4, z1, x2, SUELO + 4, z2, "white_concrete");
        filaVentana(p, x1 + 2, x1 + 4, SUELO + 3, SUELO + 3, z1);
        filaVentana(p, x2 - 4, x2 - 2, SUELO + 3, SUELO + 3, z1);
        filaVentana(p, x1 + 1, x1 + 3, SUELO + 3, SUELO + 3, z2);
        filaVentana(p, x2 - 3, x2 - 1, SUELO + 3, SUELO + 3, z2);
        p.aire(x1 + 6, SUELO + 2, z2, x1 + 6, SUELO + 3, z2);
        p.set(x1 + 6, SUELO + 1, z2 + 1, "smooth_stone_slab[type=bottom]");
        p.marcarPiso(x1 + 1, z1 + 1, x2 - 1, z2 - 1, SUELO + 1);
        p.set(x1 + 1, SUELO + 2, z1 + 1, "red_bed[facing=west,part=head]");
        p.set(x1 + 2, SUELO + 2, z1 + 1, "red_bed[facing=west,part=foot]");
        p.set(x2 - 1, SUELO + 2, z1 + 1, "smoker[facing=west]");
        p.set(x1 + 4, SUELO + 3, z1 + 2, "light[level=12]");
    }

    /** Lote del suroeste: refugio antiatómico subterráneo, arenero y tobogán. */
    private static void loteSuroeste(Plano p) {
        for (int x = X_MIN; x <= -14; x++)
            for (int z = 7; z <= Z_MAX; z++) if (Util.hash(x, z, 31L) % 10 < 2) p.set(x, SUELO, z, "coarse_dirt");
        refugio(p);
        // Cartel de entrada "Población" (el texto lo pone el plugin)
        p.caja(-36, SUELO + 1, 7, -36, SUELO + 5, 7, "spruce_log");
        p.caja(-36, SUELO + 1, 12, -36, SUELO + 5, 12, "spruce_log");
        p.caja(-36, SUELO + 3, 8, -36, SUELO + 5, 11, "white_concrete");
        // Arenero y tobogán
        p.paredes(-24, SUELO + 1, 24, -20, SUELO + 1, 28, "oak_slab[type=bottom]");
        p.caja(-23, SUELO, 25, -21, SUELO, 27, "sand");
        p.caja(-18, SUELO + 1, 25, -18, SUELO + 3, 25, "spruce_fence");
        p.caja(-16, SUELO + 1, 25, -16, SUELO + 3, 25, "spruce_fence");
        p.caja(-17, SUELO + 1, 25, -17, SUELO + 2, 25, "spruce_planks");
        p.set(-17, SUELO + 3, 25, "smooth_quartz_slab[type=bottom]");
        p.set(-17, SUELO + 2, 26, "quartz_stairs[facing=north]");
        p.set(-17, SUELO + 1, 27, "quartz_stairs[facing=north]");
        p.set(-17, SUELO + 1, 24, "ladder[facing=north]");
        p.set(-17, SUELO + 2, 24, "ladder[facing=north]");
        arbol(p, -20, 12);
        p.caja(-35, SUELO + 1, 27, -34, SUELO + 1, 28, "hay_block");
        p.set(-35, SUELO + 2, 27, "hay_block");
        cercaZ(p, X_MIN, 7, Z_MAX, "birch_fence");
        cercaX(p, -37, -14, Z_MAX, "birch_fence");
        cercaZ(p, -14, 13, 18, "birch_fence");
        cercaZ(p, -14, 22, 29, "birch_fence");
    }

    /** Refugio antiatómico: escalera que baja a un cuarto bajo el patio (y 60-62). */
    private static void refugio(Plano p) {
        // Escalera descubierta que baja hacia el oeste
        for (int i = 0; i < 4; i++) {
            int x = -26 - i, y = SUELO - 1 - i;
            p.caja(x, y, 19, x, y, 20, "stone_brick_stairs[facing=east]");
            p.aire(x, y + 1, 19, x, SUELO, 20);
            p.set(x, SUELO, 18, "stone_bricks");
            p.set(x, SUELO, 21, "stone_bricks");
        }
        cercaX(p, -29, -26, 18, "iron_bars", SUELO + 1);
        cercaX(p, -29, -26, 21, "iron_bars", SUELO + 1);
        // Cuarto
        int x1 = -37, x2 = -30, z1 = 16, z2 = 23, piso = SUELO - 5;
        p.caja(x1, piso, z1, x2, SUELO - 1, z2, "light_gray_concrete");
        p.aire(x1 + 1, piso + 1, z1 + 1, x2 - 1, piso + 3, z2 - 1);
        p.caja(x1 + 1, piso, z1 + 1, x2 - 1, piso, z2 - 1, "gray_concrete");
        p.aire(x2, piso + 1, 19, x2, piso + 2, 20);
        p.marcarPiso(x1 + 1, z1 + 1, x2 - 1, z2 - 1, piso);
        p.caja(x1 + 1, piso + 1, z1 + 1, x1 + 1, piso + 2, z1 + 3, "barrel[facing=east]");
        p.caja(x1 + 1, piso + 1, z2 - 3, x1 + 1, piso + 2, z2 - 1, "barrel[facing=east]");
        p.set(x1 + 3, piso + 1, z2 - 1, "red_bed[facing=east,part=foot]");
        p.set(x1 + 4, piso + 1, z2 - 1, "red_bed[facing=east,part=head]");
        p.set(x1 + 4, piso + 3, 19, "light[level=11]");
        p.set(x2 - 2, piso + 3, 20, "light[level=11]");
    }

    // ------------------------------------------------------------------ calle y centro

    private static void calle(Plano p) {
        int cx = 24;
        for (int x = X_MIN; x <= X_MAX; x++) {
            for (int z = -12; z <= 12; z++) {
                double d = Math.hypot(x - cx, z);
                boolean recta = x <= cx && Math.abs(z) <= 4;
                boolean veredaRecta = x <= cx && Math.abs(z) >= 5 && Math.abs(z) <= 6;
                if (d <= 3) {
                    p.set(x, SUELO, z, "grass_block");                                // isla
                } else if (d <= 10 || recta) {
                    long h = Util.hash(x, z, 5L) % 100;
                    p.set(x, SUELO, z, h < 88 ? "gray_concrete" : h < 96 ? "cyan_terracotta" : "andesite");
                    if (z == 0 && x < cx - 10 && Math.floorMod(x, 5) < 3) p.set(x, SUELO, z, "yellow_concrete");
                } else if (d <= 12 || veredaRecta) {
                    p.set(x, SUELO, z, "smooth_stone");
                }
                for (int y = SUELO + 1; y <= SUELO + 3; y++) {
                    if (d <= 12 || recta || veredaRecta) p.borrar(x, y, z);
                }
            }
        }
        // Faroles
        for (int[] f : new int[][]{{-30, -6}, {-12, 6}, {6, -6}, {13, 6}, {33, -6}}) {
            p.caja(f[0], SUELO + 1, f[1], f[0], SUELO + 4, f[1], "spruce_fence");
            p.set(f[0], SUELO + 5, f[1], "lantern");
        }
        // Barreras de hormigón en la rotonda (cobertura)
        p.caja(30, SUELO + 1, -4, 30, SUELO + 1, -2, "smooth_stone");
        p.caja(18, SUELO + 1, 8, 20, SUELO + 1, 8, "smooth_stone");
    }

    private static void vehiculosCentro(Plano p) {
        colectivo(p, -14, -4);
        camion(p, 4, 1);
        camioneta(p, 22, 5);
    }

    /** Colectivo escolar abandonado, hueco y con dos puertas: x -16..-4, z -4..-1. */
    private static void colectivo(Plano p, int x1, int z1) {
        int x2 = x1 + 10, z2 = z1 + 3;
        p.caja(x1, SUELO + 1, z1, x2, SUELO + 1, z2, "yellow_concrete");
        for (int x : new int[]{x1 + 1, x2 - 2}) {
            p.set(x, SUELO + 1, z1, "black_concrete");
            p.set(x, SUELO + 1, z2, "black_concrete");
        }
        p.paredes(x1, SUELO + 2, z1, x2, SUELO + 2, z2, "yellow_concrete");
        p.paredes(x1, SUELO + 3, z1, x2, SUELO + 3, z2, "yellow_concrete");
        p.caja(x1, SUELO + 4, z1, x2, SUELO + 4, z2, "yellow_concrete");
        filaVentana(p, x1 + 1, x2 - 1, SUELO + 3, SUELO + 3, z1);
        filaVentana(p, x1 + 3, x2 - 1, SUELO + 3, SUELO + 3, z2);
        colVentana(p, x1, SUELO + 3, SUELO + 3, z1 + 1, z2 - 1);
        p.aire(x1 + 1, SUELO + 2, z2, x1 + 2, SUELO + 3, z2);                 // puerta delantera
        p.aire(x2, SUELO + 2, z1 + 1, x2, SUELO + 3, z2 - 1);                 // puerta de emergencia
        p.set(x1 + 1, SUELO + 1, z2 + 1, "smooth_stone_slab[type=bottom]");
        p.set(x2 + 1, SUELO + 1, z1 + 1, "smooth_stone_slab[type=bottom]");
        p.caja(x1 - 2, SUELO + 1, z1, x1 - 1, SUELO + 2, z2, "yellow_concrete");    // trompa
        p.caja(x1 - 2, SUELO + 1, z1, x1 - 2, SUELO + 1, z2, "black_concrete");
        for (int x = x1 + 3; x <= x2 - 1; x += 2) p.set(x, SUELO + 2, z1 + 1, "spruce_stairs[facing=east]");
        p.marcarPiso(x1 + 1, z1 + 1, x2 - 1, z2 - 1, SUELO + 1);
    }

    /** Camión de mudanza con la caja abierta atrás (al oeste) y una rampa. */
    private static void camion(Plano p, int x1, int z1) {
        int x2 = x1 + 9, z2 = z1 + 3;
        p.caja(x1, SUELO + 1, z1, x2, SUELO + 1, z2, "gray_concrete");
        for (int x : new int[]{x1 + 1, x1 + 5, x2 - 1}) {
            p.set(x, SUELO + 1, z1, "black_concrete");
            p.set(x, SUELO + 1, z2, "black_concrete");
        }
        int caja2 = x2 - 3;
        p.paredes(x1, SUELO + 2, z1, caja2, SUELO + 4, z2, "white_concrete");
        p.caja(x1, SUELO + 5, z1, caja2, SUELO + 5, z2, "white_concrete");
        p.aire(x1, SUELO + 2, z1 + 1, x1, SUELO + 4, z2 - 1);                // caja abierta
        p.set(x1 - 1, SUELO + 1, z1 + 1, "smooth_stone_slab[type=bottom]");
        p.set(x1 - 1, SUELO + 1, z1 + 2, "smooth_stone_slab[type=bottom]");
        p.caja(caja2 + 1, SUELO + 2, z1, x2, SUELO + 3, z2, "red_concrete");  // cabina
        p.caja(x2, SUELO + 3, z1 + 1, x2, SUELO + 3, z2 - 1, "glass");
        p.set(x1 + 2, SUELO + 2, z1 + 1, "barrel[facing=up]");
        p.set(x1 + 3, SUELO + 2, z2 - 1, "spruce_planks");
        p.marcarPiso(x1 + 1, z1 + 1, caja2 - 1, z2 - 1, SUELO + 1);
    }

    private static void camioneta(Plano p, int x1, int z1) {
        int x2 = x1 + 4, z2 = z1 + 2;
        p.caja(x1, SUELO + 1, z1, x2, SUELO + 1, z2, "cyan_terracotta");
        p.set(x1 + 1, SUELO + 1, z1, "black_concrete");
        p.set(x1 + 1, SUELO + 1, z2, "black_concrete");
        p.set(x2 - 1, SUELO + 1, z1, "black_concrete");
        p.set(x2 - 1, SUELO + 1, z2, "black_concrete");
        p.caja(x1, SUELO + 2, z1, x1 + 2, SUELO + 2, z1, "cyan_terracotta");  // laterales de la caja
        p.caja(x1, SUELO + 2, z2, x1 + 2, SUELO + 2, z2, "cyan_terracotta");
        p.set(x1, SUELO + 2, z1 + 1, "cyan_terracotta");
        p.caja(x2 - 1, SUELO + 2, z1, x2, SUELO + 2, z2, "glass");
        p.caja(x2 - 1, SUELO + 3, z1, x2, SUELO + 3, z2, "cyan_terracotta");
    }

    /** Podio en la isla de la rotonda (1.°, 2.° y 3.° puesto). */
    private static void podio(Plano p) {
        p.caja(24, SUELO + 1, 0, 24, SUELO + 2, 0, "gold_block");
        p.set(24, SUELO + 1, -1, "iron_block");
        p.set(24, SUELO + 1, 1, "copper_block");
    }

    /** Posición de cada escalón del podio (pies), mirando al oeste. */
    public static double[][] podio() {
        return new double[][]{{24.5, SUELO + 3, 0.5}, {24.5, SUELO + 2, -0.5}, {24.5, SUELO + 2, 1.5}};
    }

    // ------------------------------------------------------------------ límites y desierto

    private static void limites(Plano p) {
        for (int y = SUELO + 1; y <= SUELO + 40; y++) {
            for (int x = X_MIN - 1; x <= X_MAX + 1; x++) {
                p.set(x, y, Z_MIN - 1, "barrier");
                p.set(x, y, Z_MAX + 1, "barrier");
            }
            for (int z = Z_MIN - 1; z <= Z_MAX + 1; z++) {
                p.set(X_MIN - 1, y, z, "barrier");
                p.set(X_MAX + 1, y, z, "barrier");
            }
        }
    }

    /** Decorado afuera del límite: la ruta que sigue, postes, torre de pruebas, búnker y carteles. */
    private static void desierto(Plano p) {
        for (int x = -150; x < X_MIN - 1; x++) {
            for (int z = -4; z <= 4; z++) {
                long h = Util.hash(x, z, 11L) % 100;
                if (h < 12) continue; // arena que tapa el asfalto
                p.set(x, SUELO, z, h < 85 ? "gray_concrete" : "cyan_terracotta");
            }
            if (Math.floorMod(x, 5) < 3) p.set(x, SUELO, 0, "yellow_concrete");
            if (Math.floorMod(x, 14) == 0) {
                p.caja(x, SUELO + 1, -9, x, SUELO + 8, -9, "spruce_log");
                cercaZ(p, x, -10, -8, "spruce_fence", SUELO + 7);
            }
        }
        torrePruebas(p, -70, 55);
        torrePruebas(p, 95, 70);
        bunker(p, 55, -60);
        for (int[] c : new int[][]{{-44, 18}, {44, -22}, {0, 36}, {12, -36}, {-20, -36}, {44, 18}}) {
            p.caja(c[0], SUELO + 1, c[1], c[0], SUELO + 2, c[1], "spruce_fence");
            p.set(c[0], SUELO + 3, c[1], "yellow_concrete");
        }
    }

    private static void torrePruebas(Plano p, int x0, int z0) {
        int alto = 26;
        for (int[] e : new int[][]{{0, 0}, {3, 0}, {0, 3}, {3, 3}}) {
            p.caja(x0 + e[0], SUELO + 1, z0 + e[1], x0 + e[0], SUELO + alto, z0 + e[1], "spruce_fence");
        }
        for (int y = SUELO + 8; y <= SUELO + alto; y += 9) p.caja(x0, y, z0, x0 + 3, y, z0 + 3, "smooth_stone_slab[type=bottom]");
        p.caja(x0, SUELO + alto + 1, z0, x0 + 3, SUELO + alto + 3, z0 + 3, "iron_block");
        p.set(x0 + 1, SUELO + alto + 4, z0 + 1, "lightning_rod");
    }

    private static void bunker(Plano p, int x0, int z0) {
        p.caja(x0, SUELO - 1, z0, x0 + 10, SUELO + 3, z0 + 8, "light_gray_concrete");
        p.caja(x0 + 1, SUELO + 2, z0 + 8, x0 + 9, SUELO + 2, z0 + 8, "black_concrete");
        p.caja(x0 - 1, SUELO + 1, z0 - 1, x0 + 11, SUELO + 1, z0 - 1, "sandstone_wall[east=low,west=low,up=true]");
    }

    // ------------------------------------------------------------------ piezas

    private static void filaVentana(Plano p, int x1, int x2, int y1, int y2, int z) {
        for (int x = x1; x <= x2; x++)
            for (int y = y1; y <= y2; y++) p.set(x, y, z, "glass_pane[east=true,west=true]");
    }

    private static void colVentana(Plano p, int x, int y1, int y2, int z1, int z2) {
        for (int z = z1; z <= z2; z++)
            for (int y = y1; y <= y2; y++) p.set(x, y, z, "glass_pane[north=true,south=true]");
    }

    private static void cercaX(Plano p, int x1, int x2, int z, String mat) {
        cercaX(p, x1, x2, z, mat, SUELO + 1);
    }

    private static void cercaX(Plano p, int x1, int x2, int z, String mat, int y) {
        for (int x = x1; x <= x2; x++) p.set(x, y, z, mat + "[east=" + (x < x2) + ",west=" + (x > x1) + "]");
    }

    private static void cercaZ(Plano p, int x, int z1, int z2, String mat) {
        cercaZ(p, x, z1, z2, mat, SUELO + 1);
    }

    private static void cercaZ(Plano p, int x, int z1, int z2, String mat, int y) {
        for (int z = z1; z <= z2; z++) p.set(x, y, z, mat + "[north=" + (z > z1) + ",south=" + (z < z2) + "]");
    }

    // ------------------------------------------------------------------ entidades y carteles

    /** La familia nuclear: maniquíes en las casas y en la calle (girados para la casa verde). */
    public static List<Maniqui> maniquies() {
        List<Maniqui> l = new ArrayList<>();
        Maniqui[] amarilla = {
                new Maniqui(-9.5, SUELO + 1, -12.5, 45, 0x2F5DA8, 0x3B3B3B),     // papá mirando la tele
                new Maniqui(-15.5, SUELO + 1, -19.5, 180, 0xD94F70, 0xE8E0C8),   // mamá en la cocina
                new Maniqui(-6.5, SUELO + 6, -13.5, 0, 0xE0A030, 0x2C4C88),      // hijo arriba, en la ventana
                new Maniqui(-22.5, SUELO + 1, -25.5, 270, 0x5FA85F, 0x6B4A2B),   // hija junto a la hamaca
        };
        for (Maniqui m : amarilla) {
            l.add(m);
            l.add(new Maniqui(-m.x(), m.y(), -m.z(), m.yaw() + 180, m.pantalon(), m.camisa()));
        }
        l.add(new Maniqui(-1.5, SUELO + 1, 2.5, 270, 0xFFFFFF, 0x1E2A4A));      // en la calle
        l.add(new Maniqui(19.5, SUELO + 1, -9.5, 45, 0xB03030, 0x3B3B3B));
        return Collections.unmodifiableList(l);
    }

    public static List<Cartel> carteles() {
        return List.of(
                new Cartel(-35.45, SUELO + 5.0, 10.0, 270, "<dark_red><bold>PUEBLO ATÓMICO</bold>"),
                new Cartel(-29.5, SUELO + 2.2, 19.5, 90, "<yellow>☢ <white>REFUGIO <yellow>☢"),
                new Cartel(-44.5, SUELO + 4.2, 18.5, 90, "<yellow>☢ <black>ZONA DE PRUEBAS"),
                new Cartel(44.5, SUELO + 4.2, -21.5, 270, "<yellow>☢ <black>RADIACIÓN"),
                new Cartel(0.5, SUELO + 4.2, 36.5, 180, "<yellow>☢ <black>NO PASAR"),
                new Cartel(12.5, SUELO + 4.2, -35.5, 0, "<yellow>☢ <black>NO PASAR"));
    }

    /** Dónde va el contador de población (bajas de la partida), junto al cartel de entrada. */
    public static double[] contadorPoblacion() {
        return new double[]{-35.45, SUELO + 3.6, 10.0, 270};
    }

    /** Bloques que se ennegrecen con la bomba atómica (después se restauran desde el plano). */
    public static boolean quemable(String tipo) {
        return switch (tipo) {
            case "grass_block", "yellow_terracotta", "lime_terracotta", "white_concrete", "smooth_quartz", "oak_leaves",
                 "yellow_concrete", "quartz_pillar", "spruce_planks", "birch_fence", "white_terracotta" -> true;
            default -> false;
        };
    }
}
