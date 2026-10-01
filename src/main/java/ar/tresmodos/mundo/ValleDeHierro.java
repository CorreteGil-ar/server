package ar.tresmodos.mundo;

/**
 * Mapa de Guerra "Valle de Hierro" (512 × 512). Es una función pura: para cada columna (x, z)
 * devuelve los bloques como texto, sin depender del servidor. La usa el generador del mundo y la
 * restauración al final de cada partida (lo que rompen las explosiones vuelve a su bloque original),
 * y se puede volcar fuera del juego para revisarlo.
 *
 * Ejes: x crece al este, z al sur. Base Azul al oeste (x ≈ −215), base Roja al este (x ≈ +215),
 * pueblo en el centro (|x|, |z| ≤ 68), campo al norte (trigo, setos, loma con trincheras) y bosque
 * con un río en U al sur (dos puentes en x = ±60 y un vado en x = 0).
 */
public final class ValleDeHierro {
    public static final int SUELO = 64;
    public static final int AGUA = 62;
    public static final int YMIN = 40;
    public static final int YMAX = 136;
    public static final int MITAD = 256;
    public static final int BASE_X = 215;
    public static final int PUEBLO = 68;
    /** Las calles del pueblo (centro de cada calle en x y en z). */
    public static final int[] CALLES = {-66, -34, 0, 34, 66};
    public static final int RIEL_Z = -76;
    private static final long SEMILLA = 0x5A11_ED31_E770L;

    private ValleDeHierro() {}

    // ------------------------------------------------------------------ bloques

    private static final String AIRE = "air", PIEDRA = "stone", TIERRA = "dirt", PASTO = "grass_block",
            AGUA_B = "water", ARENA = "sand", GRAVA = "gravel", BARRO = "coarse_dirt";

    /** Llena out[y - YMIN] con el bloque de cada altura de la columna (null = aire). */
    public static void columna(int x, int z, String[] out) {
        java.util.Arrays.fill(out, null);
        int h = altura(x, z);
        for (int y = YMIN; y <= h - 4; y++) set(out, y, PIEDRA);
        for (int y = Math.max(YMIN, h - 3); y < h; y++) set(out, y, TIERRA);
        set(out, h, superficie(x, z, h));
        for (int y = h + 1; y <= AGUA; y++) set(out, y, AGUA_B);
        if (Math.abs(x) >= 172) base(x, z, h, out);
        else if (Math.abs(x) <= PUEBLO && Math.abs(z) <= PUEBLO) pueblo(x, z, h, out);
        else campo(x, z, h, out);
    }

    /** Un solo bloque (para restaurar). */
    public static String bloque(int x, int y, int z) {
        if (y < YMIN) return y <= -63 ? "bedrock" : PIEDRA;
        if (y > YMAX) return AIRE;
        String[] o = new String[YMAX - YMIN + 1];
        columna(x, z, o);
        String b = o[y - YMIN];
        return b == null ? AIRE : b;
    }

    private static void set(String[] o, int y, String b) {
        if (y >= YMIN && y <= YMAX) o[y - YMIN] = b;
    }

    private static String get(String[] o, int y) {
        return y >= YMIN && y <= YMAX ? o[y - YMIN] : null;
    }

    // ------------------------------------------------------------------ terreno

    /** Ruido de valores suave en [0, 1]. */
    private static double ruido(double x, double z, double periodo, long sal) {
        double fx = x / periodo, fz = z / periodo;
        long ix = (long) Math.floor(fx), iz = (long) Math.floor(fz);
        double tx = fx - ix, tz = fz - iz;
        tx = tx * tx * (3 - 2 * tx);
        tz = tz * tz * (3 - 2 * tz);
        double a = azar(ix, iz, sal), b = azar(ix + 1, iz, sal), c = azar(ix, iz + 1, sal), d = azar(ix + 1, iz + 1, sal);
        return (a * (1 - tx) + b * tx) * (1 - tz) + (c * (1 - tx) + d * tx) * tz;
    }

    private static long hash(long a, long b, long sal) {
        long h = SEMILLA ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL) ^ (sal * 0x165667B19E3779F9L);
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h & Long.MAX_VALUE;
    }

    private static double azar(long a, long b, long sal) {
        return (hash(a, b, sal) % 100000) / 100000.0;
    }

    /** Centro del río en U para un x dado. */
    public static double rioZ(double x) {
        return 132 + 0.0045 * x * x;
    }

    public static boolean enRio(int x, int z) {
        return Math.abs(x) < 178 && Math.abs(z - rioZ(x)) < 6;
    }

    /** Distancia al centro de la loma norte. */
    private static double dLoma(int x, int z) {
        return Math.hypot(x, z + 178);
    }

    private static boolean camino(int x, int z) {
        int ax = Math.abs(x);
        // Ruta oeste-este de base a base.
        if (Math.abs(z) <= 2 && ax > PUEBLO && ax < 190) return true;
        // Al norte, hacia la loma; al sur, hacia el vado.
        if (Math.abs(x) <= 2 && ((z < -PUEBLO && z > -140) || (z > PUEBLO && z < rioZ(0) - 6))) return true;
        // Hacia los dos puentes.
        if (Math.abs(ax - 60) <= 2 && z > PUEBLO && z < rioZ(60) - 8) return true;
        return false;
    }

    public static int altura(int x, int z) {
        double base = SUELO + (ruido(x, z, 48, 1) - 0.5) * 5 + (ruido(x, z, 17, 2) - 0.5) * 1.6;
        int ax = Math.abs(x), az = Math.abs(z);
        double plano = 0;
        plano = Math.max(plano, rampa(ax, 160, 175));                       // bases
        plano = Math.max(plano, Math.min(rampa(-ax, -88, -72), rampa(-az, -88, -72))); // pueblo
        if (camino(x, z)) plano = 1;
        double h = base * (1 - plano) + SUELO * plano;
        double dl = dLoma(x, z);
        if (dl < 50) h += 13 * suave(1 - dl / 50);
        int hi = (int) Math.round(h);
        if (ax < 178) {
            double dr = Math.abs(z - rioZ(x));
            if (dr < 12) {
                int lecho = Math.abs(x) <= 5 ? 61 : 58;
                if (dr < 6) hi = lecho;
                else hi = Math.min(hi, (int) Math.round(lecho + (dr - 6) * 0.9));
            }
        }
        return hi - crater(x, z);
    }

    /** Profundidad de cráter en la tierra de nadie (0 si no hay). */
    private static int crater(int x, int z) {
        int ax = Math.abs(x);
        if (ax <= PUEBLO + 6 || ax >= 160 || camino(x, z) || (z < -84 && ax < 165) || (z > 74 && ax < 168)) return 0;
        long cx = Math.floorDiv(x, 26), cz = Math.floorDiv(z, 26);
        if (azar(cx, cz, 30) >= 0.35) return 0;
        double ox = cx * 26 + 6 + azar(cx, cz, 31) * 14, oz = cz * 26 + 6 + azar(cx, cz, 32) * 14;
        double r = 3 + azar(cx, cz, 33) * 2.5, d = Math.hypot(x - ox, z - oz);
        return d < r ? (int) Math.round(2.2 * (1 - d / r)) : 0;
    }

    private static double rampa(double v, double a, double b) {
        if (v <= a) return 0;
        if (v >= b) return 1;
        return (v - a) / (b - a);
    }

    private static double suave(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    private static String superficie(int x, int z, int h) {
        if (h <= AGUA) return Math.abs(z - rioZ(x)) < 6 ? GRAVA : ARENA;
        if (camino(x, z)) return azar(x, z, 5) < 0.25 ? GRAVA : "dirt_path";
        if (crater(x, z) > 0) return BARRO;
        if (Math.abs(x) < 178 && Math.abs(z - rioZ(x)) < 9) return ARENA;
        return PASTO;
    }

    // ------------------------------------------------------------------ bases

    /** Lado del equipo: -1 Azul (oeste), +1 Rojo (este). */
    public static int lado(int x) {
        return x < 0 ? -1 : 1;
    }

    private static void base(int x, int z, int h, String[] o) {
        int s = lado(x);
        int bx = s * BASE_X;
        int rx = (x - bx) * s; // positivo = hacia afuera del mapa
        String color = s < 0 ? "blue" : "red";
        // Pista: 11 de ancho, de z = −185 a −60.
        if (Math.abs(x - bx) <= 5 && z >= -185 && z <= -60) {
            String b = "black_concrete";
            if (x == bx && Math.floorMod(z, 8) < 4) b = "white_concrete";
            if (Math.abs(x - bx) == 5) b = Math.floorMod(z, 10) == 0 ? "sea_lantern" : "gray_concrete";
            set(o, h, b);
            return;
        }
        // Hangar al costado de la pista, abierto hacia ella.
        int hx0 = bx + s * 8, hx1 = bx + s * 22;
        if (dentro(x, Math.min(hx0, hx1), Math.max(hx0, hx1)) && z >= -100 && z <= -78) {
            set(o, h, "smooth_stone");
            boolean pared = x == hx1 || z == -100 || z == -78;
            boolean puerta = x == hx0;
            if (pared && !puerta) for (int y = h + 1; y <= h + 8; y++) set(o, y, y == h + 4 ? color + "_concrete" : "light_gray_concrete");
            set(o, h + 9, "gray_concrete");
            return;
        }
        // Recinto de la bandera: anillo de bolsas de arena con hueco hacia el centro.
        double df = Math.hypot(x - bx, z);
        if (df <= 7.5) {
            set(o, h, df <= 2.6 ? color + "_concrete" : "packed_mud");
            if (df >= 6.4) {
                boolean hueco = rx < 0 && Math.abs(z) <= 1;
                if (!hueco) {
                    set(o, h + 1, "packed_mud");
                    set(o, h + 2, "mud_brick_slab[type=bottom]");
                }
            }
            if (x == bx && z == 0) for (int y = h + 1; y <= h + 7; y++) set(o, y, "iron_bars");
            return;
        }
        // Búnker de aparición: más afuera, con puerta hacia el centro.
        int ux = bx + s * 26;
        if (Math.abs(x - ux) <= 6 && Math.abs(z) <= 5) {
            set(o, h, "smooth_stone");
            boolean borde = Math.abs(x - ux) == 6 || Math.abs(z) == 5;
            boolean puerta = (x - ux) * s == -6 && Math.abs(z) <= 1;
            if (borde && !puerta) for (int y = h + 1; y <= h + 4; y++) set(o, y, y == h + 2 ? color + "_concrete" : "gray_concrete");
            else if (borde) set(o, h + 4, "gray_concrete");
            set(o, h + 5, "gray_concrete");
            if (!borde && Math.abs(x - ux) == 3 && Math.abs(z) == 3) set(o, h + 4, "sea_lantern");
            return;
        }
        // Garaje techado: z de −42 a −20.
        if (Math.abs(x - bx) <= 15 && z >= -42 && z <= -20) {
            set(o, h, "gray_concrete");
            boolean pilar = Math.floorMod(x - bx, 7) == 1 && (z == -42 || z == -20 || z == -31);
            if (pilar) for (int y = h + 1; y <= h + 6; y++) set(o, y, "polished_andesite");
            set(o, h + 7, Math.floorMod(x + z, 6) == 0 ? "glass" : "smooth_stone_slab[type=bottom]");
            return;
        }
        // Zona de reparación: borde amarillo y negro.
        if (Math.abs(x - bx) <= 4 && Math.abs(z - 22) <= 4) {
            boolean borde = Math.abs(x - bx) == 4 || Math.abs(z - 22) == 4;
            set(o, h, borde ? (Math.floorMod(x + z, 2) == 0 ? "yellow_concrete" : "black_concrete") : "light_gray_concrete");
            return;
        }
        // Helipuerto con la H.
        if (Math.abs(x - bx) <= 4 && Math.abs(z - 42) <= 4) {
            int lx = x - bx, lz = z - 42;
            boolean hache = (Math.abs(lx) == 2 && Math.abs(lz) <= 2) || (lz == 0 && Math.abs(lx) <= 2);
            set(o, h, hache ? "yellow_concrete" : "light_gray_concrete");
            return;
        }
        // Patio de concreto alrededor del centro de la base.
        if (Math.abs(x - bx) <= 34 && Math.abs(z) <= 50) {
            set(o, h, azar(x, z, 7) < 0.8 ? "light_gray_concrete" : "andesite");
            // Cerco perimetral con huecos.
            boolean cerco = (Math.abs(x - bx) == 34 || Math.abs(z) == 50) && !(Math.abs(z) <= 4) && !(Math.abs(x - bx) <= 4);
            if (cerco) {
                set(o, h + 1, "iron_bars");
                set(o, h + 2, "iron_bars");
            }
        }
    }

    private static boolean dentro(int v, int a, int b) {
        return v >= a && v <= b;
    }

    // ------------------------------------------------------------------ pueblo

    private static int calle(int v) {
        for (int c : CALLES) if (Math.abs(v - c) <= 2) return c;
        return Integer.MIN_VALUE;
    }

    private static void pueblo(int x, int z, int h, String[] o) {
        // Plaza central con fuente y puestos de mercado (ocupa los cuatro lotes del centro).
        if (Math.abs(x) <= 17 && Math.abs(z) <= 17) {
            plaza(x, z, h, o);
            return;
        }
        boolean cx = calle(x) != Integer.MIN_VALUE, cz = calle(z) != Integer.MIN_VALUE;
        if (cx || cz) {
            set(o, h, azar(x, z, 9) < 0.15 ? "andesite" : azar(x, z, 10) < 0.1 ? GRAVA : "cobblestone");
            // Faroles en las esquinas.
            if (cx && cz && Math.abs(x - calle(x)) == 2 && Math.abs(z - calle(z)) == 2) {
                for (int y = h + 1; y <= h + 3; y++) set(o, y, "dark_oak_fence");
                set(o, h + 4, "lantern[hanging=false]");
            }
            return;
        }
        // Manzanas: x en [3, 31] o [37, 63] (y espejos); cada una con 2 × 2 lotes.
        int[] mx = manzana(x), mz = manzana(z);
        if (mx == null || mz == null) {
            set(o, h, PASTO);
            return;
        }
        // Edificios especiales.
        if (mx[0] == -31 && mz[0] == -63) {
            iglesia(x, z, h, o, mx, mz);
            return;
        }
        if (mx[0] == 37 && mz[0] == 37) {
            fabrica(x, z, h, o, mx, mz);
            return;
        }
        int midX = (mx[0] + mx[1]) / 2, midZ = (mz[0] + mz[1]) / 2;
        int lx0 = x <= midX ? mx[0] : midX + 1, lx1 = x <= midX ? midX : mx[1];
        int lz0 = z <= midZ ? mz[0] : midZ + 1, lz1 = z <= midZ ? midZ : mz[1];
        casa(x, z, h, o, lx0, lx1, lz0, lz1);
    }

    /** Manzana que contiene a v (sus límites) o null si v es calle o queda fuera. */
    private static int[] manzana(int v) {
        int[][] m = {{-63, -37}, {-31, -3}, {3, 31}, {37, 63}};
        for (int[] r : m) if (v >= r[0] && v <= r[1]) return r;
        return null;
    }

    private static void plaza(int x, int z, int h, String[] o) {
        double d = Math.hypot(x, z);
        set(o, h, (Math.floorMod(x, 4) == 0 || Math.floorMod(z, 4) == 0) ? "stone_bricks" : "polished_andesite");
        if (d <= 3.6) {
            if (d >= 2.6) {
                set(o, h + 1, "stone_brick_wall");
            } else {
                set(o, h, "water");
                set(o, h - 1, "stone_bricks");
                if (d < 0.8) {
                    set(o, h, "stone_bricks");
                    set(o, h + 1, "stone_brick_wall");
                    set(o, h + 2, "stone_brick_wall");
                    set(o, h + 3, "chiseled_stone_bricks");
                }
            }
            return;
        }
        // Puestos de mercado: cuatro toldos de lana.
        int[][] puestos = {{-9, -9}, {9, -9}, {-9, 9}, {9, 9}};
        String[] lanas = {"red_wool", "yellow_wool", "white_wool", "green_wool"};
        for (int i = 0; i < 4; i++) {
            int px = puestos[i][0], pz = puestos[i][1];
            if (Math.abs(x - px) <= 2 && Math.abs(z - pz) <= 1) {
                boolean poste = Math.abs(x - px) == 2 && Math.abs(z - pz) == 1;
                if (poste) for (int y = h + 1; y <= h + 2; y++) set(o, y, "spruce_fence");
                if (Math.abs(z - pz) == 0 && !poste && Math.abs(x - px) <= 1) set(o, h + 1, "barrel[facing=up]");
                set(o, h + 3, lanas[i]);
                return;
            }
        }
        // Bancos y árboles en los bordes de la plaza.
        if ((Math.abs(x) == 16 || Math.abs(z) == 16) && Math.floorMod(x + z, 8) == 0) {
            for (int y = h + 1; y <= h + 4; y++) set(o, y, "oak_log");
            for (int y = h + 5; y <= h + 6; y++) set(o, y, "oak_leaves[persistent=true]");
        }
    }

    private static final String[] PAREDES = {"bricks", "white_terracotta", "smooth_sandstone", "light_gray_terracotta",
            "mud_bricks", "stone_bricks", "terracotta"};

    /**
     * Casa de 2 o 3 pisos en un lote: paredes de color, ventanas por piso, puerta hacia la calle,
     * pisos de madera con hueco y escalera de mano, techo de tejas y a veces sótano. Algunas tienen
     * daño de guerra (agujeros en paredes).
     */
    private static void casa(int x, int z, int h, String[] o, int x0, int x1, int z0, int z1) {
        long semilla = hash(x0, z0, 77);
        int pisos = 2 + (int) (semilla % 2);
        String pared = PAREDES[(int) ((semilla >>> 3) % PAREDES.length)];
        boolean sotano = (semilla >>> 7) % 10 < 3;
        boolean dañada = (semilla >>> 11) % 10 < 4;
        // Casa: un bloque de retiro respecto del lote.
        int a0 = x0 + 1, a1 = x1 - 1, b0 = z0 + 1, b1 = z1 - 1;
        if (x < a0 || x > a1 || z < b0 || z > b1) {
            // Patio: pasto, cercos bajos y algún arbusto.
            set(o, h, azar(x, z, 12) < 0.2 ? "coarse_dirt" : PASTO);
            if ((x == x0 || x == x1 || z == z0 || z == z1) && azar(x, z, 13) < 0.5) set(o, h + 1, "oak_leaves[persistent=true]");
            return;
        }
        int alto = pisos * 4;
        boolean bx = x == a0 || x == a1, bz = z == b0 || z == b1;
        boolean borde = bx || bz;
        boolean esquina = bx && bz;
        // Puerta: en el lado que da a la calle más cercana.
        int lx = x - a0, lz = z - b0, ancho = a1 - a0, largo = b1 - b0;
        int ladoPuerta = (int) ((semilla >>> 15) % 4);
        boolean puerta = switch (ladoPuerta) {
            case 0 -> z == b0 && lx == ancho / 2;
            case 1 -> z == b1 && lx == ancho / 2;
            case 2 -> x == a0 && lz == largo / 2;
            default -> x == a1 && lz == largo / 2;
        };
        // Sótano.
        if (sotano) {
            for (int y = h - 4; y < h; y++) set(o, y, borde ? "stone_bricks" : AIRE);
            set(o, h - 5, "stone_bricks");
        }
        set(o, h, borde ? "stone_bricks" : "spruce_planks");
        for (int y = h + 1; y <= h + alto; y++) {
            int dy = (y - h - 1) % 4; // 0..3 dentro de cada piso
            boolean entrepiso = dy == 3 && y < h + alto;
            if (borde) {
                String b = esquina ? "stone_bricks" : pared;
                // Ventanas: dos de alto, cada 3 bloques de pared.
                int pos = bz ? lx : lz;
                boolean ventana = !esquina && (dy == 1 || dy == 2) && pos % 3 == 2 && pos > 0;
                if (ventana) b = bz ? "glass_pane[east=true,west=true]" : "glass_pane[north=true,south=true]";
                if (puerta && y <= h + 2) b = AIRE;
                if (dañada && !esquina && azar(x * 7 + y, z, 14) < 0.08) b = AIRE;
                if (entrepiso && !esquina) b = "spruce_planks";
                set(o, y, b);
            } else if (entrepiso) {
                // Hueco de la escalera en la esquina noroeste del interior.
                boolean hueco = lx == 1 && lz == 1;
                set(o, y, hueco ? AIRE : "spruce_planks");
            } else {
                set(o, y, AIRE);
            }
        }
        // Escalera de mano pegada a la pared oeste, desde el sótano hasta el último piso.
        if (lx == 1 && lz == 1) {
            int desde = sotano ? h - 4 : h + 1;
            for (int y = desde; y < h + alto; y++) {
                if (y == h) continue;
                set(o, y, "ladder[facing=east]");
            }
            if (sotano) set(o, h, "ladder[facing=east]");
        }
        // Algún mueble.
        if (!borde && lx == ancho - 1 && lz == largo - 1) set(o, h + 1, "barrel[facing=up]");
        if (!borde && lx == ancho - 1 && lz == 1) set(o, h + 1, "crafting_table");
        // Techo: tejas rojas a cuatro aguas por escalones.
        int y0 = h + alto + 1;
        int dist = Math.min(Math.min(lx, ancho - lx), Math.min(lz, largo - lz));
        for (int k = 0; k <= dist && k < 4; k++) set(o, y0 + k, k == dist || k == 3 ? "red_terracotta" : "red_terracotta");
        if (dist == 0) set(o, y0, "brick_slab[type=bottom]");
    }

    /** Iglesia con campanario (nido de francotirador) en la manzana noroeste del centro. */
    private static void iglesia(int x, int z, int h, String[] o, int[] mx, int[] mz) {
        int x0 = mx[0] + 4, x1 = mx[1] - 4, z0 = mz[0] + 2, z1 = mz[1] - 1;
        if (x < x0 || x > x1 || z < z0 || z > z1) {
            set(o, h, azar(x, z, 15) < 0.3 ? "coarse_dirt" : PASTO);
            // Lápidas del cementerio.
            if (x < x0 - 1 && Math.floorMod(z, 3) == 0 && Math.floorMod(x, 2) == 0) set(o, h + 1, "cobblestone_wall");
            return;
        }
        // Torre al sur (frente a la calle z = −34), nave hacia el norte.
        int tx0 = (x0 + x1) / 2 - 2, tx1 = tx0 + 4, tz1 = z1, tz0 = z1 - 4;
        boolean torre = x >= tx0 && x <= tx1 && z >= tz0 && z <= tz1;
        set(o, h, "polished_andesite");
        if (torre) {
            boolean b = x == tx0 || x == tx1 || z == tz0 || z == tz1;
            int lx = x - tx0, lz = z - tz0;
            for (int y = h + 1; y <= h + 24; y++) {
                String bl = AIRE;
                if (b) {
                    bl = "stone_bricks";
                    boolean campanario = y >= h + 18 && y <= h + 21 && (lx == 2 || lz == 2);
                    if (campanario) bl = AIRE;
                    if (z == tz1 && lx == 2 && y <= h + 3) bl = AIRE; // puerta
                    if (y % 6 == 0 && (lx == 2 || lz == 2) && y < h + 17) bl = "glass_pane[north=true,south=true,east=true,west=true]";
                }
                if (!b && y == h + 17) bl = lx == 1 && lz == 1 ? AIRE : "spruce_planks";
                if (lx == 1 && lz == 1 && y <= h + 17) bl = "ladder[facing=east]";
                if (y == h + 22) bl = "stone_brick_slab[type=top]";
                if (y == h + 23 && !b) bl = AIRE;
                if (y == h + 23 && b) bl = "stone_brick_wall";
                if (y == h + 24 && lx == 2 && lz == 2) bl = "lightning_rod";
                if (!b && y == h + 21 && lx == 2 && lz == 2) bl = "bell[attachment=ceiling,facing=north]";
                set(o, y, bl);
            }
            return;
        }
        boolean borde = x == x0 || x == x1 || z == z0 || z == tz0;
        if (z > tz0) {
            set(o, h, PASTO);
            return;
        }
        for (int y = h + 1; y <= h + 10; y++) {
            String bl = AIRE;
            if (borde) {
                bl = "stone_bricks";
                boolean vitral = (x == x0 || x == x1) && Math.floorMod(z, 4) == 0 && y >= h + 3 && y <= h + 7;
                if (vitral) bl = "purple_stained_glass_pane[north=true,south=true]";
                if (z == tz0 && Math.abs(x - (x0 + x1) / 2) <= 1 && y <= h + 3) bl = AIRE;
            } else if (y == h + 1 && Math.floorMod(z, 3) == 0 && Math.abs(x - (x0 + x1) / 2) > 1) {
                bl = "spruce_stairs[facing=south]";
            }
            set(o, y, bl);
        }
        // Techo a dos aguas sobre la nave.
        int mid = (x0 + x1) / 2, d = Math.abs(x - mid), anchoMedio = (x1 - x0) / 2;
        set(o, h + 11 + (anchoMedio - d), "dark_oak_planks");
        for (int y = h + 11; y < h + 11 + (anchoMedio - d); y++) if (d == anchoMedio) set(o, y, "dark_oak_planks");
    }

    /** Fábrica con galpón alto y grúa, en la manzana sudeste. */
    private static void fabrica(int x, int z, int h, String[] o, int[] mx, int[] mz) {
        int x0 = mx[0] + 1, x1 = mx[1] - 1, z0 = mz[0] + 1, z1 = mz[1] - 8;
        set(o, h, "smooth_stone");
        if (x >= x0 && x <= x1 && z >= z0 && z <= z1) {
            boolean borde = x == x0 || x == x1 || z == z0 || z == z1;
            boolean porton = (z == z0 && Math.abs(x - (x0 + x1) / 2) <= 2) || (x == x0 && Math.abs(z - (z0 + z1) / 2) <= 2);
            for (int y = h + 1; y <= h + 11; y++) {
                String bl = AIRE;
                if (borde) {
                    bl = y >= h + 8 && y <= h + 9 ? "iron_bars" : (Math.floorMod(x + z, 6) == 0 ? "stone_bricks" : "light_gray_concrete");
                    if (porton && y <= h + 5) bl = AIRE;
                }
                if (y == h + 11) bl = Math.floorMod(x, 5) == 0 ? "glass" : "gray_concrete";
                set(o, y, bl);
            }
            // Máquinas y cajas adentro.
            if (!(x == x0 || x == x1 || z == z0 || z == z1)) {
                long r = hash(x / 4, z / 4, 16);
                if (Math.floorMod(x, 4) == 1 && Math.floorMod(z, 4) == 1 && r % 3 == 0) {
                    set(o, h + 1, "blast_furnace[facing=north]");
                    set(o, h + 2, "iron_block");
                } else if (Math.floorMod(x, 4) == 2 && Math.floorMod(z, 5) == 2 && r % 3 == 1) {
                    set(o, h + 1, "barrel[facing=up]");
                    set(o, h + 2, "barrel[facing=up]");
                }
            }
            return;
        }
        // Grúa en el patio de carga.
        int gx = x1 - 2, gz = z1 + 4;
        if (x == gx && z == gz) for (int y = h + 1; y <= h + 22; y++) set(o, y, "yellow_concrete");
        if (z == gz && x <= gx && x >= gx - 14) set(o, h + 22, "yellow_concrete");
        if (z == gz && x == gx - 12) {
            for (int y = h + 14; y <= h + 21; y++) set(o, y, "iron_chain[axis=y]");
            set(o, h + 13, "iron_block");
        }
        // Contenedores.
        if (z >= z1 + 2 && z <= z1 + 4 && x >= x0 + 1 && x <= x0 + 7) {
            for (int y = h + 1; y <= h + 3; y++) set(o, y, "orange_terracotta");
        }
    }

    // ------------------------------------------------------------------ campo, bosque y río

    private static void campo(int x, int z, int h, String[] o) {
        int ax = Math.abs(x);
        // Vías del tren, al norte del pueblo, de lado a lado (hasta las bases).
        if (Math.abs(z - RIEL_Z) <= 1 && ax < 172) {
            set(o, h, GRAVA);
            if (z == RIEL_Z) set(o, h + 1, "rail[shape=east_west]");
            // Vagones como cobertura.
            boolean vagon = (x >= -30 && x <= -16) || (x >= 10 && x <= 24) || (x >= -110 && x <= -98) || (x >= 96 && x <= 108);
            if (vagon) for (int y = h + 1; y <= h + 3; y++) set(o, y, y == h + 3 ? "oxidized_cut_copper" : "weathered_copper");
            return;
        }
        // Andén de la estación.
        if (z > RIEL_Z + 1 && z <= RIEL_Z + 5 && ax <= 26) {
            set(o, h, "stone_bricks");
            set(o, h + 1, "smooth_stone_slab[type=bottom]");
            if (z == RIEL_Z + 5 && Math.floorMod(x, 8) == 0) {
                for (int y = h + 2; y <= h + 4; y++) set(o, y, "dark_oak_fence");
                set(o, h + 5, "dark_oak_slab[type=bottom]");
            }
            return;
        }
        if (camino(x, z)) return;
        // Puentes sobre el río (se pueden volar con C4).
        if (ax < 178) {
            double dr = z - rioZ(x);
            if (Math.abs(ax - 60) <= 2 && Math.abs(dr) <= 11) {
                // Tablero a la altura del suelo, pilares cada 6 bloques y baranda a los costados.
                boolean pilar = Math.floorMod(z, 6) == 0;
                for (int y = h + 1; y < SUELO; y++) if (pilar) set(o, y, "stone_bricks");
                set(o, SUELO, "stone_bricks");
                for (int y = SUELO + 1; y <= SUELO + 4; y++) set(o, y, null);
                if (Math.abs(ax - 60) == 2) set(o, SUELO + 1, "stone_brick_wall");
                return;
            }
            if (Math.abs(dr) < 12) return; // orillas y agua
        }
        // Loma con trincheras y búnkeres.
        double dl = dLoma(x, z);
        if (dl < 52) {
            loma(x, z, h, o);
            return;
        }
        if (z < -84 && ax < 165) {
            campos(x, z, h, o);
            return;
        }
        if (z > 74 && ax < 168) {
            bosque(x, z, h, o);
            return;
        }
        tierraDeNadie(x, z, h, o);
    }

    private static void loma(int x, int z, int h, String[] o) {
        // Trinchera en zigzag frente a la loma (mira al pueblo).
        double zt = -150 + 4 * Math.sin(x / 5.0);
        if (Math.abs(x) <= 42 && Math.abs(z - zt) < 1.3) {
            set(o, h, AIRE);
            set(o, h - 1, AIRE);
            set(o, h - 2, "coarse_dirt");
            return;
        }
        if (Math.abs(x) <= 42 && Math.abs(z - zt) < 2.3) {
            set(o, h, "spruce_planks");
            set(o, h - 1, "spruce_planks");
            if (azar(x, z, 17) < 0.3) set(o, h + 1, "sandstone_wall");
            return;
        }
        // Búnkeres de concreto con troneras.
        int[][] bunker = {{-24, -160}, {24, -160}, {0, -186}};
        for (int[] b : bunker) {
            if (Math.abs(x - b[0]) <= 3 && Math.abs(z - b[1]) <= 3) {
                boolean borde = Math.abs(x - b[0]) == 3 || Math.abs(z - b[1]) == 3;
                set(o, h, "gray_concrete");
                for (int y = h + 1; y <= h + 3; y++) {
                    String bl = borde ? "gray_concrete" : AIRE;
                    if (borde && y == h + 2 && z == b[1] + 3 && Math.abs(x - b[0]) <= 1) bl = AIRE; // tronera al sur
                    if (borde && y <= h + 2 && z == b[1] - 3 && x == b[0]) bl = AIRE;               // puerta al norte
                    set(o, y, bl);
                }
                set(o, h + 4, "gray_concrete");
                return;
            }
        }
        if (azar(x, z, 18) < 0.06) set(o, h + 1, "short_grass");
    }

    /** Campos de trigo y girasoles en parcelas con setos altos (como el bocage). */
    private static void campos(int x, int z, int h, String[] o) {
        int u = Math.floorMod(x, 24), v = Math.floorMod(z, 24);
        long cx = Math.floorDiv(x, 24), cz = Math.floorDiv(z, 24);
        // Setos en los bordes de la parcela, con huecos.
        if (u == 0 || v == 0) {
            boolean hueco = (u == 0 ? azar(cx, Math.floorDiv(z, 6), 19) : azar(Math.floorDiv(x, 6), cz, 20)) < 0.25;
            if (!hueco) for (int y = h + 1; y <= h + 3; y++) set(o, y, "oak_leaves[persistent=true]");
            return;
        }
        // Molino en una parcela fija.
        if (cx == -4 && cz == -5) {
            double d = Math.hypot(u - 12, v - 12);
            if (d <= 3.4) {
                for (int y = h + 1; y <= h + 14; y++) set(o, y, d >= 2.4 || y == h + 14 ? "white_terracotta" : (y % 5 == 0 ? "spruce_planks" : AIRE));
                if (d < 1) for (int y = h + 1; y <= h + 13; y++) set(o, y, "ladder[facing=north]");
                return;
            }
            if (v == 8 && Math.abs(u - 12) <= 7) set(o, h + 12 + (u - 12) / 2, "spruce_fence");
            if (v == 8 && u == 12) for (int k = -7; k <= 7; k++) set(o, h + 12 + k / 2, "spruce_fence");
            set(o, h, PASTO);
            return;
        }
        double tipo = azar(cx, cz, 21);
        if (tipo < 0.08 && u >= 7 && u <= 15 && v >= 7 && v <= 15) {
            // Granja: casa chica de un piso.
            casaGranja(u, v, h, o);
            return;
        }
        if (tipo < 0.45) {
            set(o, h, "farmland[moisture=7]");
            set(o, h + 1, "wheat[age=7]");
        } else if (tipo < 0.65) {
            set(o, h, PASTO);
            if ((u + v) % 2 == 0) {
                set(o, h + 1, "sunflower[half=lower]");
                set(o, h + 2, "sunflower[half=upper]");
            } else {
                set(o, h + 1, "short_grass");
            }
        } else if (tipo < 0.8) {
            set(o, h, "farmland[moisture=7]");
            if (azar(x, z, 22) < 0.02) set(o, h + 1, "hay_block[axis=y]");
        } else {
            set(o, h, PASTO);
            if (azar(x, z, 23) < 0.03) set(o, h + 1, "hay_block[axis=x]");
            else if (azar(x, z, 24) < 0.2) set(o, h + 1, "short_grass");
        }
    }

    private static void casaGranja(int u, int v, int h, String[] o) {
        boolean borde = u == 7 || u == 15 || v == 7 || v == 15;
        set(o, h, "spruce_planks");
        for (int y = h + 1; y <= h + 4; y++) {
            String b = borde ? "stripped_spruce_log[axis=y]" : AIRE;
            if ((u == 7 || u == 15) && y == h + 2 && v % 3 == 0) b = "glass_pane[north=true,south=true]";
            if (v == 15 && u == 11 && y <= h + 2) b = AIRE;
            set(o, y, b);
        }
        int dist = Math.min(Math.min(u - 7, 15 - u), Math.min(v - 7, 15 - v));
        set(o, h + 5 + Math.min(dist, 3), "dark_oak_planks");
    }

    /** Bosque denso: árboles por celdas de 6 × 6 (las copas cruzan celdas y chunks sin cortes). */
    private static void bosque(int x, int z, int h, String[] o) {
        double cerca = Math.abs(x) <= 5 || Math.abs(Math.abs(x) - 60) <= 5 ? 0 : 1;
        if (azar(x, z, 25) < 0.15) set(o, h + 1, azar(x, z, 26) < 0.5 ? "fern" : "short_grass");
        if (cerca == 0) return;
        long cx0 = Math.floorDiv(x, 6), cz0 = Math.floorDiv(z, 6);
        for (long cx = cx0 - 1; cx <= cx0 + 1; cx++) {
            for (long cz = cz0 - 1; cz <= cz0 + 1; cz++) {
                if (azar(cx, cz, 27) > 0.75) continue;
                int tx = (int) (cx * 6 + 1 + (long) (azar(cx, cz, 28) * 4)), tz = (int) (cz * 6 + 1 + (long) (azar(cx, cz, 29) * 4));
                if (Math.abs(tx) <= 5 || Math.abs(Math.abs(tx) - 60) <= 5) continue;
                if (Math.abs(tz - rioZ(tx)) < 10) continue;
                int th = altura(tx, tz);
                int tipo = (int) (hash(cx, cz, 34) % 3);
                int alto = 6 + (int) (hash(cx, cz, 35) % 4);
                String tronco = tipo == 0 ? "oak_log[axis=y]" : tipo == 1 ? "spruce_log[axis=y]" : "dark_oak_log[axis=y]";
                String hojas = (tipo == 0 ? "oak_leaves" : tipo == 1 ? "spruce_leaves" : "dark_oak_leaves") + "[persistent=true]";
                if (tx == x && tz == z) for (int y = th + 1; y <= th + alto; y++) set(o, y, tronco);
                int r = tipo == 1 ? 2 : 3;
                for (int y = th + alto - 3; y <= th + alto + 1; y++) {
                    int ry = tipo == 1 ? Math.max(1, (th + alto + 1 - y)) : r;
                    double d = Math.hypot(x - tx, z - tz) + Math.abs(y - (th + alto - 1)) * 0.6;
                    if (d <= ry && get(o, y) == null) set(o, y, hojas);
                }
            }
        }
    }

    /** Tierra de nadie entre el pueblo y las bases: restos de vehículos y erizos antitanque. */
    private static void tierraDeNadie(int x, int z, int h, String[] o) {
        long cx = Math.floorDiv(x, 26), cz = Math.floorDiv(z, 26);
        int u = Math.floorMod(x, 26), v = Math.floorMod(z, 26);
        double r = azar(cx, cz, 36);
        if (r < 0.12 && u >= 10 && u <= 15 && v >= 11 && v <= 13) {
            // Auto quemado.
            set(o, h + 1, "blackstone");
            if (u >= 11 && u <= 13) set(o, h + 2, v == 12 ? "coal_block" : "polished_blackstone");
            return;
        }
        if (r > 0.8) {
            // Erizos antitanque en fila.
            if (v == 13 && u % 5 == 2) {
                set(o, h + 1, "iron_bars");
                set(o, h + 2, "iron_bars");
            }
        }
        if (azar(x, z, 37) < 0.05) set(o, h + 1, "short_grass");
    }
}
