package ar.tresmodos.rpg;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Estructuras grandes de cada zona del RPG: la Aldea Hueca (empalizada, casas quemadas, iglesia,
 * cementerio, horcas y campos), el Bosque Podrido (árboles muertos gigantes, hongos, pantanos
 * venenosos y la choza de la bruja), la Ciudadela Desmoronada (murallas, torres, catedral y torre
 * del homenaje) y la Costa Hundida (arena negra, dársena con casas hundidas, muelles y barcos).
 *
 * Solo depende de {@link Lienzo}: así se puede volcar fuera del juego para revisar cómo queda.
 * Los bloques van como texto de BlockData ("dark_oak_stairs[facing=north]").
 */
public final class ConstructorZonas {

    /** Donde se construye (el mundo, o una grilla en memoria para las pruebas). */
    public interface Lienzo {
        /** Altura del suelo firme (sin contar árboles ni plantas). */
        int suelo(int x, int z);

        /** Altura del agua si la hay encima del suelo; si no, el suelo. */
        int agua(int x, int z);

        void poner(int x, int y, int z, String datos);

        /** Nombre del bloque ("air", "water", "stone"...). */
        String bloque(int x, int y, int z);

        void cofre(int x, int y, int z, String mira, String botin, boolean secreto);

        void atril(int x, int y, int z, String mira, String nota);

        /** Puerta que se abre desde un solo lado: el lado hacia (nx, nz) desde la reja. */
        void atajo(String id, String nombre, List<int[]> reja, int[] palanca, int nx, int nz);

        /** Pared que desaparece al golpearla. */
        void ilusoria(String id, List<int[]> bloques);
    }

    /** Lugares que no hay que tapar (hogueras, arenas): x, z y radio. */
    public record Libre(int x, int z, int radio) {}

    private final Lienzo l;
    private final List<Libre> libres;
    private final List<int[]> ocupados = new ArrayList<>();

    public ConstructorZonas(Lienzo l, List<Libre> libres) {
        this.l = l;
        this.libres = libres;
    }

    // ------------------------------------------------------------------ utilidades

    /** Mientras se construye algo hundido, el aire por debajo de este nivel es agua. */
    private int aguaHasta = Integer.MIN_VALUE;

    private void p(int x, int y, int z, String s) {
        l.poner(x, y, z, y <= aguaHasta && s.equals("air") ? "water" : s);
    }

    private static String mira(int ax, int az) {
        if (ax > 0) return "east";
        if (ax < 0) return "west";
        return az > 0 ? "south" : "north";
    }

    private static String al(Random r, String... ops) {
        return ops[r.nextInt(ops.length)];
    }

    private static boolean blando(String b) {
        return b.equals("air") || b.equals("cave_air") || b.equals("water") || b.equals("lava") || b.contains("grass")
                || b.contains("fern") || b.contains("flower") || b.contains("bush") || b.contains("leaves")
                || b.contains("snow") || b.contains("vine") || b.contains("sapling") || b.contains("mushroom")
                && !b.contains("block") && !b.contains("stem") || b.equals("seagrass") || b.equals("tall_seagrass")
                || b.equals("kelp") || b.equals("kelp_plant") || b.contains("dandelion") || b.contains("poppy")
                || b.contains("tulip") || b.contains("orchid") || b.contains("allium") || b.contains("bluet")
                || b.contains("daisy") || b.contains("cornflower") || b.contains("lily") || b.contains("petals")
                || b.contains("moss_carpet") || b.contains("hanging_moss") || b.contains("eyeblossom");
    }

    /** Rellena hacia abajo hasta tocar suelo firme (máximo 24). */
    private void cimiento(int x, int y, int z, String s) {
        for (int dy = 1; dy <= 24; dy++) {
            if (!blando(l.bloque(x, y - dy, z))) return;
            p(x, y - dy, z, s);
        }
    }

    private void aire(int x, int y0, int z, int alto) {
        for (int y = y0; y < y0 + alto; y++) {
            String b = l.bloque(x, y, z);
            if (!b.equals("air") && !(y <= aguaHasta && b.equals("water"))) p(x, y, z, "air");
        }
    }

    /** Base de la estructura: el agua cuenta como suelo (se construye sobre pilotes). */
    private int base(int x, int z) {
        return Math.max(l.suelo(x, z), l.agua(x, z));
    }

    private boolean libre(int x, int z, int radio) {
        for (Libre f : libres) if (Math.hypot(x - f.x, z - f.z) < f.radio + radio) return false;
        for (int[] o : ocupados) if (Math.hypot(x - o[0], z - o[1]) < o[2] + radio) return false;
        return true;
    }

    private void ocupar(int x, int z, int radio) {
        ocupados.add(new int[]{x, z, radio});
    }

    /** Altura de la parte más baja de un rectángulo (para asentar pisos sin dejarlos en el aire). */
    private int pisoDe(int x0, int z0, int x1, int z1) {
        int[] ys = {base(x0, z0), base(x1, z0), base(x0, z1), base(x1, z1), base((x0 + x1) / 2, (z0 + z1) / 2)};
        java.util.Arrays.sort(ys);
        return ys[2];
    }

    // ------------------------------------------------------------------ piezas comunes

    /** Materiales de una casa. */
    record Paleta(String[] base, String[] pared, String poste, String techo, String[] piso, String ventana) {}

    static final Paleta ALDEA = new Paleta(new String[]{"cobblestone", "mossy_cobblestone", "cobblestone"},
            new String[]{"dark_oak_planks", "dark_oak_planks", "stripped_dark_oak_log", "blackstone", "spruce_planks"},
            "dark_oak_log", "dark_oak", new String[]{"spruce_planks", "dark_oak_planks", "coarse_dirt", "cobblestone"}, "air");
    static final Paleta COSTA = new Paleta(new String[]{"deepslate_bricks", "cobbled_deepslate", "tuff"},
            new String[]{"spruce_planks", "spruce_planks", "stripped_spruce_log", "deepslate_tiles", "dark_oak_planks"},
            "spruce_log", "deepslate_tile", new String[]{"spruce_planks", "deepslate_tiles", "gravel"}, "black_stained_glass_pane");

    /**
     * Casa de 1 o 2 pisos, quemada (huecos en paredes y techo), con techo a dos aguas sobre el eje
     * largo. La puerta mira hacia (px, pz). Devuelve el piso (y).
     */
    private int casa(int x0, int z0, int ancho, int largo, int pisos, int px, int pz, Paleta pal, Random r,
                     String botin, double quemado, int pisoForzado) {
        int x1 = x0 + ancho - 1, z1 = z0 + largo - 1;
        int y = pisoForzado != Integer.MIN_VALUE ? pisoForzado : pisoDe(x0, z0, x1, z1);
        int alto = pisos == 2 ? 7 : 4;
        boolean ejeX = ancho >= largo; // el caballete corre a lo largo del lado mayor
        // Despejar, piso y cimientos.
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                aire(x, y + 1, z, alto + Math.max(ancho, largo) / 2 + 3);
                if (x < x0 || x > x1 || z < z0 || z > z1) continue;
                p(x, y, z, al(r, pal.piso));
                cimiento(x, y, z, pal.base[0]);
            }
        }
        // Paredes.
        int cxp = (x0 + x1) / 2, czp = (z0 + z1) / 2;
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                boolean bx = x == x0 || x == x1, bz = z == z0 || z == z1;
                if (!bx && !bz) continue;
                boolean esquina = bx && bz;
                for (int dy = 1; dy <= alto; dy++) {
                    String m;
                    if (esquina) m = pal.poste;
                    else if (dy == 1) m = al(r, pal.base);
                    else if (pisos == 2 && dy == 4) m = pal.poste + "[axis=" + (bx ? "z" : "x") + "]";
                    else m = al(r, pal.pared);
                    // Ventanas: cada 3 bloques en los pisos.
                    boolean enVentana = !esquina && (dy == 2 || dy == 3 || pisos == 2 && (dy == 5 || dy == 6))
                            && ((bx ? z - z0 : x - x0) % 3 == 2) && (bx ? z1 - z : x1 - x) >= 2;
                    if (enVentana) m = dy == 3 || dy == 6 ? "air" : pal.ventana;
                    // Quemado: faltan bloques arriba.
                    if (!esquina && dy >= 3 && r.nextDouble() < quemado * (dy / (double) alto)) m = "air";
                    p(x, y + dy, z, m);
                }
            }
        }
        // Puerta.
        int dx = px != 0 ? (px > 0 ? x1 : x0) : cxp, dz = pz != 0 ? (pz > 0 ? z1 : z0) : czp;
        p(dx, y + 1, dz, "air");
        p(dx, y + 2, dz, "air");
        // Entrepiso.
        if (pisos == 2) {
            for (int x = x0 + 1; x < x1; x++)
                for (int z = z0 + 1; z < z1; z++)
                    if (r.nextDouble() > quemado * 0.8 && !(x == x0 + 1 && z == z0 + 1)) p(x, y + 4, z, "spruce_planks");
        }
        // Techo a dos aguas: escalones que suben desde los dos aleros hasta el caballete.
        int b0 = (ejeX ? z0 : x0) - 1, b1 = (ejeX ? z1 : x1) + 1;
        for (int k = 0; b0 + k <= b1 - k; k++) {
            int ty = y + alto + 1 + k, bi = b0 + k, bd = b1 - k;
            for (int a = (ejeX ? x0 - 1 : z0 - 1); a <= (ejeX ? x1 + 1 : z1 + 1); a++) {
                if (r.nextDouble() >= quemado * 0.9) {
                    if (bi == bd) {
                        p(ejeX ? a : bi, ty, ejeX ? bi : a, pal.techo + "_slab[type=bottom]");
                    } else {
                        p(ejeX ? a : bi, ty, ejeX ? bi : a, pal.techo + "_stairs[facing=" + (ejeX ? "south" : "east") + "]");
                        p(ejeX ? a : bd, ty, ejeX ? bd : a, pal.techo + "_stairs[facing=" + (ejeX ? "north" : "west") + "]");
                    }
                }
                // Hastiales: la pared sigue bajo el techo en los dos extremos.
                if (a == (ejeX ? x0 : z0) || a == (ejeX ? x1 : z1)) {
                    for (int b = bi + 1; b <= bd - 1; b++) {
                        if (r.nextDouble() > quemado * 0.7) p(ejeX ? a : b, ty, ejeX ? b : a, al(r, pal.pared));
                    }
                }
            }
        }
        // Adentro: telarañas, barriles y a veces un cofre.
        int ix = x0 + 1 + r.nextInt(Math.max(1, ancho - 2)), iz = z0 + 1 + r.nextInt(Math.max(1, largo - 2));
        if (!(ix == dx && iz == dz)) p(ix, y + 1, iz, al(r, "barrel[facing=up]", "cobweb", "cauldron", "composter"));
        p(x0 + 1, y + alto - 1, z1 - 1, "cobweb");
        if (botin != null && r.nextDouble() < 0.6) {
            int cx = px != 0 ? (px > 0 ? x0 + 1 : x1 - 1) : (r.nextBoolean() ? x0 + 1 : x1 - 1);
            int cz = pz != 0 ? (pz > 0 ? z0 + 1 : z1 - 1) : (r.nextBoolean() ? z0 + 1 : z1 - 1);
            l.cofre(cx, y + 1, cz, mira(px, pz), botin, false);
        }
        return y;
    }

    /** Rastro de escombros alrededor de un punto. */
    private void escombros(int cx, int cz, int radio, int n, Random r, String... mats) {
        for (int i = 0; i < n; i++) {
            int x = cx + r.nextInt(2 * radio + 1) - radio, z = cz + r.nextInt(2 * radio + 1) - radio;
            int y = l.suelo(x, z);
            if (!l.bloque(x, y + 1, z).equals("air") || l.agua(x, z) > y) continue;
            p(x, y + 1, z, al(r, mats));
        }
    }

    /**
     * Anillo de muro alrededor de (cx, cz) que sigue el terreno, con puertas en las direcciones
     * pedidas. Devuelve las celdas de las aberturas (para no tapar el camino).
     */
    private void anillo(int cx, int cz, int radio, int altura, Random r, String[] mats, String tope,
                        int[][] aberturas, int anchoAbertura) {
        for (int dx = -radio - 1; dx <= radio + 1; dx++) {
            for (int dz = -radio - 1; dz <= radio + 1; dz++) {
                if (Math.round(Math.hypot(dx, dz)) != radio) continue;
                boolean hueco = false;
                for (int[] a : aberturas) {
                    double al = dx * a[0] + dz * a[1];
                    double lat = Math.abs(dx * a[1] - dz * a[0]);
                    if (al > radio - 2 && lat <= anchoAbertura / 2.0) hueco = true;
                }
                if (hueco) continue;
                int x = cx + dx, z = cz + dz;
                int y = base(x, z);
                cimiento(x, y + 1, z, mats[0]);
                int h = altura + (r.nextInt(3) == 0 ? 1 : 0);
                for (int k = 1; k <= h; k++) p(x, y + k, z, al(r, mats));
                if (tope != null) p(x, y + h + 1, z, tope);
            }
        }
    }

    /**
     * Portón con reja que se abre desde adentro (atajo). (ax, az) apunta hacia afuera del recinto;
     * el portón queda en (gx, gz) y abarca 3 bloques de ancho.
     */
    private void porton(String id, String nombre, int gx, int gz, int ax, int az, int alto, String marco, String dintel) {
        int lx = -az, lz = ax; // lateral
        int y = Math.max(base(gx, gz), Math.max(base(gx + lx, gz + lz), base(gx - lx, gz - lz)));
        List<int[]> reja = new ArrayList<>();
        for (int s = -1; s <= 1; s++) {
            int x = gx + lx * s, z = gz + lz * s;
            p(x, y, z, "cobblestone");
            cimiento(x, y, z, "cobblestone");
            for (int k = 1; k <= alto; k++) {
                p(x, y + k, z, "iron_bars");
                reja.add(new int[]{x, y + k, z});
            }
            p(x, y + alto + 1, z, dintel);
            // Despejar el paso a los dos lados.
            for (int d = 1; d <= 3; d++) {
                aire(x + ax * d, y + 1, z + az * d, alto);
                aire(x - ax * d, y + 1, z - az * d, alto);
            }
        }
        for (int s : new int[]{-2, 2}) {
            int x = gx + lx * s, z = gz + lz * s;
            cimiento(x, y + 1, z, marco);
            for (int k = 0; k <= alto + 2; k++) p(x, y + k, z, marco);
        }
        // La palanca, del lado de adentro, sobre el poste.
        int px = gx + lx * 2 - ax, pz = gz + lz * 2 - az;
        aire(px, y + 1, pz, 3);
        p(px, y + 2, pz, "lever[face=wall,facing=" + mira(-ax, -az) + ",powered=false]");
        l.atajo(id, nombre, reja, new int[]{px, y + 2, pz}, -ax, -az);
    }

    private void atril(int x, int y, int z, String mira, String nota) {
        l.atril(x, y, z, mira, nota);
    }

    // ------------------------------------------------------------------ Aldea Hueca

    /**
     * (ax, az): eje hacia el Santuario. La empalizada rodea el pueblo: el portón principal (de ese
     * lado) se abre desde adentro; se entra por una brecha del costado y se sale por atrás.
     */
    public void aldea(int cx, int cz, int ax, int az, long semilla) {
        Random r = new Random(semilla);
        int lx = -az, lz = ax;
        int radio = 44;
        // Plaza: grava y empedrado, el pozo y las horcas.
        for (int dx = -8; dx <= 8; dx++)
            for (int dz = -8; dz <= 8; dz++) {
                if (Math.hypot(dx, dz) > 8.4) continue;
                int x = cx + dx, z = cz + dz, y = l.suelo(x, z);
                if (l.agua(x, z) > y) continue;
                p(x, y, z, al(r, "gravel", "cobblestone", "coarse_dirt", "mossy_cobblestone", "gravel"));
                aire(x, y + 1, z, 4);
            }
        ocupar(cx, cz, 9);
        int y0 = l.suelo(cx, cz);
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    p(cx, y0, cz, "water");
                    p(cx, y0 - 1, cz, "water");
                    p(cx, y0 - 2, cz, "mud");
                    continue;
                }
                p(cx + dx, y0 + 1, cz + dz, "cobblestone_wall");
            }
        p(cx - 1, y0 + 2, cz - 1, "oak_fence");
        p(cx + 1, y0 + 2, cz + 1, "oak_fence");
        for (int s : new int[]{1, -1}) horca(cx + lx * 5 * s + ax * 3 * s, cz + lz * 5 * s + az * 3 * s, lx, lz, r);

        // Empalizada con portón (atajo), salida trasera y brecha al costado.
        anillo(cx, cz, radio, 4, r, new String[]{"dark_oak_log", "dark_oak_log", "stripped_dark_oak_log", "spruce_log"},
                null, new int[][]{{ax, az}, {-ax, -az}, {lx, lz}}, 5);
        porton("aldea_porton", "Portón de la Aldea", cx + ax * radio, cz + az * radio, ax, az, 4, "dark_oak_log", "dark_oak_planks");
        // Salida trasera: arco sin reja.
        int bx = cx - ax * radio, bz = cz - az * radio;
        int yb = base(bx, bz);
        for (int s : new int[]{-2, 2}) for (int k = 1; k <= 6; k++) p(bx + lx * s, yb + k, bz + lz * s, "dark_oak_log");
        for (int s = -2; s <= 2; s++) p(bx + lx * s, yb + 6, bz + lz * s, "dark_oak_planks");
        // Brecha: troncos caídos y escombros.
        int qx = cx + lx * radio, qz = cz + lz * radio;
        escombros(qx, qz, 4, 14, r, "dark_oak_log[axis=x]", "dark_oak_log[axis=z]", "cobblestone", "coal_block");

        // Iglesia hacia el fondo, mirando a la plaza, con el cementerio al lado.
        int ix = cx - ax * 20, iz = cz - az * 20;
        iglesia(ix, iz, ax, az, r);
        cementerio(ix + lx * 15, iz + lz * 15, r);

        // Casas.
        int puestas = 0;
        for (int intento = 0; intento < 300 && puestas < 14; intento++) {
            double ang = r.nextDouble() * Math.PI * 2, d = 13 + r.nextDouble() * 26;
            int hx = cx + (int) Math.round(Math.cos(ang) * d), hz = cz + (int) Math.round(Math.sin(ang) * d);
            if (!libre(hx, hz, 7)) continue;
            int ancho = 5 + r.nextInt(3), largo = 7 + r.nextInt(3);
            if (r.nextBoolean()) {
                int t = ancho;
                ancho = largo;
                largo = t;
            }
            int vx = cx - hx, vz = cz - hz;
            int px = Math.abs(vx) >= Math.abs(vz) ? Integer.signum(vx) : 0, pz = px == 0 ? Integer.signum(vz) : 0;
            casa(hx - ancho / 2, hz - largo / 2, ancho, largo, r.nextInt(3) == 0 ? 2 : 1, px, pz, ALDEA, r,
                    "casa", 0.35, Integer.MIN_VALUE);
            ocupar(hx, hz, 6);
            puestas++;
        }

        // Campos de trigo podrido afuera, a los costados.
        for (int lado : new int[]{1, -1}) {
            int fx = cx + lx * lado * (radio + 14) + ax * 6, fz = cz + lz * lado * (radio + 14) + az * 6;
            if (libre(fx, fz, 9)) campo(fx, fz, r);
        }
        escombros(cx, cz, 38, 60, r, "dead_bush", "cobweb", "hay_block", "coarse_dirt", "skeleton_skull[rotation=3]");
    }

    private void horca(int x, int z, int lx, int lz, Random r) {
        int y = l.suelo(x, z);
        for (int k = 1; k <= 4; k++) {
            p(x - lx, y + k, z - lz, "dark_oak_fence");
            p(x + lx, y + k, z + lz, "dark_oak_fence");
        }
        p(x - lx, y + 5, z - lz, "dark_oak_planks");
        p(x, y + 5, z, "dark_oak_planks");
        p(x + lx, y + 5, z + lz, "dark_oak_planks");
        p(x, y + 4, z, "iron_chain[axis=y]");
        p(x, y + 3, z, "iron_chain[axis=y]");
        p(x - lx, y + 1, z - lz, "dark_oak_planks");
    }

    private void campo(int cx, int cz, Random r) {
        for (int dx = -8; dx <= 8; dx++)
            for (int dz = -6; dz <= 6; dz++) {
                int x = cx + dx, z = cz + dz, y = l.suelo(x, z);
                if (l.agua(x, z) > y) continue;
                p(x, y, z, al(r, "coarse_dirt", "rooted_dirt", "coarse_dirt", "podzol"));
                aire(x, y + 1, z, 3);
                if (r.nextInt(3) > 0) p(x, y + 1, z, al(r, "dead_bush", "dead_bush", "short_grass"));
            }
        ocupar(cx, cz, 9);
        int y = l.suelo(cx, cz);
        p(cx, y + 1, cz, "dark_oak_fence");
        p(cx, y + 2, cz, "dark_oak_fence");
        p(cx, y + 3, cz, "carved_pumpkin[facing=south]");
        p(cx - 1, y + 2, cz, "dark_oak_fence");
        p(cx + 1, y + 2, cz, "dark_oak_fence");
        for (int i = 0; i < 4; i++) {
            int x = cx + r.nextInt(15) - 7, z = cz + r.nextInt(11) - 5;
            p(x, l.suelo(x, z) + 1, z, "hay_block");
        }
    }

    /** Iglesia de piedra con torre al fondo. La pared tras el altar es ilusoria: da a la cripta. */
    private void iglesia(int cx, int cz, int ax, int az, Random r) {
        int lx = -az, lz = ax;
        int media = 4, largo = 17, alto = 7;
        // Coordenadas locales: u lateral (-media..media), v hacia la puerta (0..largo-1 desde el fondo).
        int x0 = cx - ax * (largo / 2), z0 = cz - az * (largo / 2); // fondo
        int y = pisoDe(cx - lx * media - ax * 8, cz - lz * media - az * 8, cx + lx * media + ax * 8, cz + lz * media + az * 8);
        String[] piedra = {"stone_bricks", "cracked_stone_bricks", "mossy_stone_bricks", "stone_bricks", "cobblestone"};
        ocupar(cx, cz, 12);
        for (int v = -6; v < largo; v++) {
            for (int u = -media - 1; u <= media + 1; u++) {
                int x = x0 + ax * v + lx * u, z = z0 + az * v + lz * u;
                boolean torre = v < 0;
                if (torre && Math.abs(u) > 2) continue;
                aire(x, y + 1, z, torre ? 24 : alto + media + 3);
                if (Math.abs(u) > media) continue;
                p(x, y, z, torre ? "stone_bricks" : al(r, "polished_andesite", "stone_bricks", "andesite"));
                cimiento(x, y, z, "cobblestone");
                boolean pared = Math.abs(u) == media || v == largo - 1 || v == 0 && !torre;
                if (torre) pared = Math.abs(u) == 2 || v == -6 || v == -1;
                if (!pared) continue;
                int h = torre ? 20 : alto;
                for (int k = 1; k <= h; k++) {
                    String m = al(r, piedra);
                    // Ventanas altas en los costados, rosetón en la fachada, campanario arriba.
                    if (!torre && Math.abs(u) == media && v % 3 == 1 && k >= 3 && k <= 5) m = k == 5 ? "air" : "gray_stained_glass_pane";
                    if (!torre && v == largo - 1 && Math.abs(u) <= 1 && k >= 5 && k <= 6) m = "gray_stained_glass_pane";
                    if (torre && k >= 15 && k <= 17 && (Math.abs(u) == 2 && v == -3 || v == -6 && u == 0 || v == -1 && u == 0)) m = "air";
                    if (!torre && k > 4 && r.nextInt(8) == 0) m = "air";
                    p(x, y + k, z, m);
                }
            }
        }
        // Puerta.
        for (int u = -1; u <= 1; u++)
            for (int k = 1; k <= 3; k++) p(x0 + ax * (largo - 1) + lx * u, y + k, z0 + az * (largo - 1) + lz * u, "air");
        // Techo de la nave (roto) y de la torre.
        for (int k = 0; k <= media + 1; k++) {
            for (int v = 0; v < largo; v++) {
                if (r.nextInt(5) == 0) continue;
                for (int s : new int[]{-1, 1}) {
                    int u = s * (media + 1 - k);
                    int x = x0 + ax * v + lx * u, z = z0 + az * v + lz * u;
                    p(x, y + alto + 1 + k, z, u == 0 ? "deepslate_tile_slab[type=bottom]"
                            : "deepslate_tile_stairs[facing=" + mira(-lx * s, -lz * s) + "]");
                }
            }
        }
        for (int k = 0; k <= 3; k++)
            for (int v = -6 + k; v <= -1 - k; v++)
                for (int u = -2 + k; u <= 2 - k; u++) {
                    boolean borde = Math.abs(u) == 2 - k || v == -6 + k || v == -1 - k;
                    if (borde) p(x0 + ax * v + lx * u, y + 21 + k, z0 + az * v + lz * u, "deepslate_tiles");
                }
        p(x0 - ax * 3, y + 24, z0 - az * 3, "lightning_rod");
        for (int v = -5; v <= -2; v++)
            for (int u = -1; u <= 1; u++) p(x0 + ax * v + lx * u, y + 14, z0 + az * v + lz * u, "spruce_planks");
        p(x0 - ax * 3, y + 15, z0 - az * 3, "bell[attachment=floor,facing=" + mira(lx, lz) + "]");
        // Bancos y altar.
        for (int v = 5; v <= largo - 3; v += 2)
            for (int u = -3; u <= 3; u++) {
                if (u == 0 || r.nextInt(5) == 0) continue;
                p(x0 + ax * v + lx * u, y + 1, z0 + az * v + lz * u, "spruce_stairs[facing=" + mira(ax, az) + "]");
            }
        for (int u = -1; u <= 1; u++) p(x0 + ax * 2 + lx * u, y + 1, z0 + az * 2 + lz * u, "polished_andesite");
        p(x0 + ax * 2 + lx * 2, y + 1, z0 + az * 2 + lz * 2, "candle[candles=3,lit=true]");
        p(x0 + ax * 2 - lx * 2, y + 1, z0 + az * 2 - lz * 2, "candle[candles=2,lit=true]");
        atril(x0 + ax * 3, y + 1, z0 + az * 3, mira(ax, az), "aldea");
        // Pared del fondo ilusoria (3 × 3 en el medio) y la cripta en la base de la torre.
        List<int[]> ilusion = new ArrayList<>();
        for (int u = -1; u <= 1; u++)
            for (int k = 1; k <= 3; k++) {
                int x = x0 + lx * u, z = z0 + lz * u;
                p(x, y + k, z, "stone_bricks");
                ilusion.add(new int[]{x, y + k, z});
            }
        // La pared trasera de la nave (v = 0) coincide con el frente de la torre (v = -1): se abre ahí.
        for (int u = -1; u <= 1; u++)
            for (int k = 1; k <= 3; k++) {
                int x = x0 - ax + lx * u, z = z0 - az + lz * u;
                p(x, y + k, z, "air");
            }
        l.ilusoria("aldea_cripta", ilusion);
        l.cofre(x0 - ax * 4, y + 1, z0 - az * 4, mira(ax, az), "secreto_aldea", true);
        p(x0 - ax * 3 + lx, y + 1, z0 - az * 3 + lz, "skeleton_skull[rotation=0]");
        p(x0 - ax * 5 - lx, y + 1, z0 - az * 5 - lz, "cobweb");
    }

    private void cementerio(int cx, int cz, Random r) {
        if (!libre(cx, cz, 7)) return;
        ocupar(cx, cz, 8);
        for (int dx = -6; dx <= 6; dx++)
            for (int dz = -6; dz <= 6; dz++) {
                int x = cx + dx, z = cz + dz, y = l.suelo(x, z);
                if (l.agua(x, z) > y) continue;
                aire(x, y + 1, z, 4);
                p(x, y, z, al(r, "coarse_dirt", "podzol", "rooted_dirt", "coarse_dirt"));
                boolean borde = Math.abs(dx) == 6 || Math.abs(dz) == 6;
                if (borde && !(dz == 6 && Math.abs(dx) <= 1)) {
                    p(x, y + 1, z, r.nextInt(6) == 0 ? "air" : "dark_oak_fence");
                } else if (!borde && dx % 3 == 0 && dz % 3 == -1 + 0 && Math.abs(dx) <= 4) {
                    // Tumba: lápida y montículo.
                    p(x, y + 1, z, al(r, "cobblestone_wall", "stone_brick_wall", "mossy_cobblestone_wall", "andesite_wall"));
                    if (r.nextBoolean()) p(x, y + 2, z, "stone_button[face=floor,facing=north]");
                    p(x, y, z + 1, "podzol");
                    p(x, y, z + 2, "podzol");
                    if (r.nextInt(3) == 0) p(x, y + 1, z + 1, "dead_bush");
                } else if (!borde && r.nextInt(14) == 0) {
                    p(x, y + 1, z, al(r, "dead_bush", "cobweb", "skeleton_skull[rotation=5]", "candle[candles=1,lit=false]"));
                }
            }
    }

    // ------------------------------------------------------------------ Bosque Podrido

    public void bosque(int cx, int cz, int ax, int az, long semilla) {
        Random r = new Random(semilla);
        int lx = -az, lz = ax;
        // El árbol hueco con la pared ilusoria va primero (lo nombra la nota de la herbolaria).
        for (int[] c : new int[][]{{18, 0}, {24, 8}, {18, -14}, {-18, -10}, {30, 0}, {-26, 6}, {12, 22}, {-12, -24},
                {40, 10}, {-40, -10}, {10, 40}, {-10, -40}}) {
            int hx = cx + lx * c[0] + ax * c[1], hz = cz + lz * c[0] + az * c[1];
            if (!libre(hx, hz, 6) || !arbolGigante(hx, hz, 5, 26, r, true)) continue;
            ocupar(hx, hz, 8);
            break;
        }
        // Pantanos venenosos.
        int pantanos = 0;
        for (int intento = 0; intento < 40 && pantanos < 3; intento++) {
            double ang = r.nextDouble() * Math.PI * 2, d = 25 + r.nextDouble() * 70;
            int px = cx + (int) (Math.cos(ang) * d), pz = cz + (int) (Math.sin(ang) * d);
            if (!libre(px, pz, 14)) continue;
            int ra = 9 + r.nextInt(5);
            if (pantano(px, pz, ra, 7 + r.nextInt(4), r)) {
                if (pantanos == 0) choza(px + ra + 6, pz, r);
                ocupar(px, pz, 14);
                pantanos++;
            }
        }
        // Árboles muertos gigantes.
        int arboles = 0;
        for (int intento = 0; intento < 400 && arboles < 22; intento++) {
            double ang = r.nextDouble() * Math.PI * 2, d = 10 + r.nextDouble() * 115;
            int x = cx + (int) (Math.cos(ang) * d), z = cz + (int) (Math.sin(ang) * d);
            if (!libre(x, z, 5) || !arbolGigante(x, z, r.nextInt(3) == 0 ? 3 : 2, 15 + r.nextInt(14), r, false)) continue;
            ocupar(x, z, 6);
            arboles++;
        }
        // Hongos que brillan.
        int hongos = 0;
        for (int intento = 0; intento < 200 && hongos < 12; intento++) {
            double ang = r.nextDouble() * Math.PI * 2, d = 10 + r.nextDouble() * 110;
            int x = cx + (int) (Math.cos(ang) * d), z = cz + (int) (Math.sin(ang) * d);
            if (!libre(x, z, 4)) continue;
            hongo(x, z, 4 + r.nextInt(5), r);
            ocupar(x, z, 4);
            hongos++;
        }
        escombros(cx, cz, 90, 260, r, "cobweb", "cobweb", "brown_mushroom", "red_mushroom", "dead_bush", "bone_block[axis=y]");
    }

    private boolean arbolGigante(int x, int z, int grosor, int alto, Random r, boolean hueco) {
        int y = l.suelo(x, z);
        if (l.agua(x, z) > y) return false;
        int o = grosor / 2;
        String[] tronco = {"dark_oak_log[axis=y]", "dark_oak_log[axis=y]", "stripped_dark_oak_log[axis=y]", "pale_oak_log[axis=y]"};
        for (int dx = -o; dx < grosor - o; dx++)
            for (int dz = -o; dz < grosor - o; dz++) {
                cimiento(x + dx, y + 1, z + dz, "dark_oak_log[axis=y]");
                int h = alto - r.nextInt(3) - (Math.abs(dx) + Math.abs(dz)) * 2;
                for (int k = 1; k <= h; k++) {
                    boolean dentro = hueco && Math.abs(dx) < o && Math.abs(dz) < o && k <= 5;
                    p(x + dx, y + k, z + dz, dentro ? "air" : k > 3 && r.nextInt(40) == 0 ? "shroomlight" : al(r, tronco));
                }
            }
        // Raíces.
        for (int[] d : new int[][]{{1, 1}, {-1, 1}, {1, -1}, {-1, -1}, {1, 0}, {0, -1}}) {
            int largo = 2 + r.nextInt(3);
            for (int i = 1; i <= largo; i++) {
                int rx = x + d[0] * (o + i), rz = z + d[1] * (o + i);
                int ry = l.suelo(rx, rz) + (i < largo ? 1 : 0);
                p(rx, ry, rz, "dark_oak_wood");
            }
        }
        // Ramas: suben en diagonal y terminan en palitos.
        int ramas = 3 + r.nextInt(3);
        for (int i = 0; i < ramas; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            int ky = alto / 2 + r.nextInt(Math.max(1, alto / 2 - 2));
            double bx = x, bz = z, by = y + ky;
            int largo = 4 + r.nextInt(5);
            for (int k = 0; k < largo; k++) {
                bx += Math.cos(ang);
                bz += Math.sin(ang);
                if (k % 2 == 1) by += 1;
                String eje = Math.abs(Math.cos(ang)) > Math.abs(Math.sin(ang)) ? "x" : "z";
                p((int) Math.round(bx), (int) by, (int) Math.round(bz), k < largo - 1 ? "dark_oak_log[axis=" + eje + "]" : "dark_oak_fence");
                if (r.nextInt(5) == 0) p((int) Math.round(bx), (int) by - 1, (int) Math.round(bz), "cobweb");
            }
        }
        if (hueco) {
            // Una cara de la corteza es ilusoria: 1 × 2 al ras del suelo.
            List<int[]> ilusion = new ArrayList<>();
            for (int k = 1; k <= 2; k++) {
                p(x + o, y + k, z, "dark_oak_log[axis=y]");
                ilusion.add(new int[]{x + o, y + k, z});
            }
            l.ilusoria("bosque_arbol", ilusion);
            l.cofre(x - o + 1, y + 1, z, "east", "secreto_bosque", true);
            p(x, y + 1, z - o + 1, "cobweb");
            p(x, y + 1, z + o - 1, "red_mushroom");
        }
        return true;
    }

    private void hongo(int x, int z, int alto, Random r) {
        int y = l.suelo(x, z);
        if (l.agua(x, z) > y) return;
        String cap = r.nextBoolean() ? "red_mushroom_block" : "brown_mushroom_block";
        for (int k = 1; k <= alto; k++) p(x, y + k, z, "mushroom_stem");
        int rad = 2 + (alto > 6 ? 1 : 0);
        for (int dx = -rad; dx <= rad; dx++)
            for (int dz = -rad; dz <= rad; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > rad + 0.4) continue;
                p(x + dx, y + alto + 1, z + dz, cap);
                if (d > rad - 0.6) p(x + dx, y + alto, z + dz, cap);
                else if (r.nextInt(3) == 0) p(x + dx, y + alto, z + dz, "shroomlight");
            }
        p(x, y + alto + 2, z, cap);
    }

    /** Charco de pantano a nivel parejo (el agua no se escapa). Devuelve false si el terreno es muy desparejo. */
    private boolean pantano(int cx, int cz, int ra, int rb, Random r) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int dx = -ra - 1; dx <= ra + 1; dx++)
            for (int dz = -rb - 1; dz <= rb + 1; dz++) {
                double e = (dx * dx) / (double) ((ra + 1) * (ra + 1)) + (dz * dz) / (double) ((rb + 1) * (rb + 1));
                if (e > 1.05) continue;
                int y = l.suelo(cx + dx, cz + dz);
                min = Math.min(min, y);
                max = Math.max(max, y);
            }
        if (max - min > 6) return false;
        int nivel = min;
        for (int dx = -ra - 1; dx <= ra + 1; dx++)
            for (int dz = -rb - 1; dz <= rb + 1; dz++) {
                double e = (dx * dx) / (double) (ra * ra) + (dz * dz) / (double) (rb * rb);
                double eb = (dx * dx) / (double) ((ra + 1) * (ra + 1)) + (dz * dz) / (double) ((rb + 1) * (rb + 1));
                int x = cx + dx, z = cz + dz, y = l.suelo(x, z);
                if (e <= 1) {
                    aire(x, nivel + 1, z, Math.max(0, y - nivel) + 3);
                    p(x, nivel, z, "water");
                    p(x, nivel - 1, z, e < 0.4 ? "water" : "mud");
                    if (e < 0.4) p(x, nivel - 2, z, "mud");
                    if (r.nextInt(9) == 0) p(x, nivel + 1, z, "lily_pad");
                } else if (eb <= 1.05) {
                    p(x, nivel, z, "mud");
                    if (y > nivel) aire(x, nivel + 1, z, y - nivel);
                    if (r.nextInt(4) == 0) p(x, nivel + 1, z, al(r, "dead_bush", "brown_mushroom", "short_grass"));
                }
            }
        return true;
    }

    /** Choza de la bruja sobre pilotes, con el atril de la nota del Bosque. */
    private void choza(int x, int z, Random r) {
        if (!libre(x, z, 4)) return;
        ocupar(x, z, 5);
        int y = Math.max(l.suelo(x, z), l.agua(x, z)) + 2;
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                aire(x + dx, y + 1, z + dz, 6);
                p(x + dx, y, z + dz, "spruce_planks");
                if (Math.abs(dx) == 2 && Math.abs(dz) == 2) {
                    for (int k = y - 1; k > l.suelo(x + dx, z + dz); k--) p(x + dx, k, z + dz, "spruce_log");
                    for (int k = 1; k <= 3; k++) p(x + dx, y + k, z + dz, "spruce_log");
                } else if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                    for (int k = 1; k <= 3; k++) {
                        boolean vano = dz == 2 && dx == 0 && k <= 2 || k == 2 && (dx == 0 || dz == 0) && r.nextBoolean();
                        p(x + dx, y + k, z + dz, vano ? "air" : al(r, "spruce_planks", "mangrove_planks", "spruce_planks"));
                    }
                }
                p(x + dx, y + 4, z + dz, "mangrove_planks");
            }
        for (int k = 1; k <= 2; k++) p(x, y + 4 + k, z, "mangrove_roots");
        p(x - 1, y + 1, z - 1, "cauldron");
        p(x + 1, y + 1, z - 1, "brewing_stand");
        atril(x, y + 1, z, "south", "bosque");
        // Escalera de troncos hasta el piso.
        int ys = l.suelo(x, z + 3);
        for (int k = ys + 1; k <= y; k++) p(x, k, z + 3, "ladder[facing=south]");
        p(x, y, z + 3, "spruce_planks");
        p(x, y + 1, z + 3, "air");
        for (int k = ys + 1; k <= y; k++) p(x, k, z + 2, k == y ? "spruce_planks" : "spruce_planks");
    }

    // ------------------------------------------------------------------ Ciudadela Desmoronada

    public void ciudadela(int cx, int cz, int ax, int az, long semilla) {
        Random r = new Random(semilla);
        int lx = -az, lz = ax;
        int m = 36; // media muralla
        String[] piedra = {"stone_bricks", "stone_bricks", "cracked_stone_bricks", "mossy_stone_bricks", "tuff_bricks",
                "cracked_stone_bricks"};
        // Murallas (cuadradas, 2 de espesor, almenas afuera) con portón, salida trasera y brecha.
        for (int a = -m; a <= m; a++) {
            for (int espesor = 0; espesor < 2; espesor++) {
                int[][] lados = {{a, m - espesor}, {a, -m + espesor}, {m - espesor, a}, {-m + espesor, a}};
                for (int[] d : lados) {
                    int ux = d[0], vz = d[1];
                    // Aberturas: portón (+eje), trasera (-eje) y brecha (lateral).
                    int al = ux * ax + vz * az, lat = ux * lx + vz * lz;
                    boolean frente = al >= m - 2 && Math.abs(lat) <= 2;
                    boolean fondo = al <= -m + 2 && Math.abs(lat) <= 2;
                    boolean brecha = lat >= m - 2 && Math.abs(al - 8) <= 3;
                    if (frente || fondo) continue;
                    int x = cx + ux, z = cz + vz;
                    int y = base(x, z);
                    cimiento(x, y + 1, z, "cobblestone");
                    int h = brecha ? r.nextInt(2) : 9 - (r.nextInt(7) == 0 ? 3 : 0);
                    for (int k = 1; k <= h; k++) p(x, y + k, z, al(r, piedra));
                    aire(x, y + h + 1, z, 4);
                    if (espesor == 0 && h >= 8 && Math.floorMod(a, 2) == 0) p(x, y + h + 1, z, al(r, piedra));
                }
            }
        }
        escombros(cx + lx * m + ax * 8, cz + lz * m + az * 8, 5, 30, r, "cobblestone", "cracked_stone_bricks", "stone_brick_slab[type=bottom]", "gravel");
        for (int[] e : new int[][]{{1, 1}, {1, -1}, {-1, 1}, {-1, -1}}) torre(cx + e[0] * m, cz + e[1] * m, 3, 17, piedra, r);
        porton("ciudadela_porton", "Puerta de la Ciudadela", cx + ax * m, cz + az * m, ax, az, 5, "polished_blackstone_bricks", "chiseled_stone_bricks");
        torre(cx + ax * m + lx * 5, cz + az * m + lz * 5, 2, 14, piedra, r);
        torre(cx + ax * m - lx * 5, cz + az * m - lz * 5, 2, 14, piedra, r);
        // Camino empedrado del portón al centro.
        for (int d = 0; d <= m + 6; d++)
            for (int s = -1; s <= 1; s++) {
                int x = cx + ax * d + lx * s, z = cz + az * d + lz * s, y = l.suelo(x, z);
                if (l.agua(x, z) > y) continue;
                p(x, y, z, al(r, "cobblestone", "stone_bricks", "gravel", "andesite"));
            }
        ocupar(cx, cz, 4);
        // Torre del homenaje: se ve desde todo el mapa. La base guarda el tesoro detrás de una ilusión.
        homenaje(cx - ax * 10, cz - az * 10, ax, az, piedra, r);
        // Catedral en ruinas, del lado contrario a la hoguera del medio.
        catedral(cx + lx * 18 + ax * 4, cz + lz * 18 + az * 4, ax, az, piedra, r);
        escombros(cx, cz, 30, 70, r, "cobblestone", "cracked_stone_bricks", "stone_brick_slab[type=bottom]", "cobweb",
                "skeleton_skull[rotation=2]", "iron_chain[axis=y]");
    }

    private void torre(int x, int z, int rad, int alto, String[] piedra, Random r) {
        int y = l.suelo(x, z);
        for (int dx = -rad; dx <= rad; dx++)
            for (int dz = -rad; dz <= rad; dz++) {
                if (Math.abs(dx) == rad && Math.abs(dz) == rad) continue;
                boolean borde = Math.abs(dx) == rad || Math.abs(dz) == rad || Math.abs(dx) + Math.abs(dz) >= 2 * rad - 1;
                int yb = base(x + dx, z + dz);
                cimiento(x + dx, yb + 1, z + dz, "cobblestone");
                int h = alto - (r.nextInt(5) == 0 ? r.nextInt(4) : 0);
                for (int k = yb + 1 - y; k <= h; k++) {
                    String mat = borde ? (k % 4 == 2 && (dx == 0 || dz == 0) ? "air" : al(r, piedra)) : (k == 0 ? "cobblestone" : "air");
                    p(x + dx, y + k, z + dz, mat);
                }
                if (borde && (dx + dz) % 2 == 0) p(x + dx, y + h + 1, z + dz, al(r, piedra));
            }
        ocupar(x, z, rad + 1);
    }

    private void homenaje(int cx, int cz, int ax, int az, String[] piedra, Random r) {
        int lx = -az, lz = ax;
        int y = pisoDe(cx - 5, cz - 5, cx + 5, cz + 5);
        ocupar(cx, cz, 8);
        for (int dx = -5; dx <= 5; dx++)
            for (int dz = -5; dz <= 5; dz++) {
                int x = cx + dx, z = cz + dz;
                cimiento(x, y + 1, z, "cobblestone");
                boolean borde = Math.abs(dx) == 5 || Math.abs(dz) == 5;
                boolean esquina = Math.abs(dx) == 5 && Math.abs(dz) == 5;
                p(x, y, z, "polished_blackstone_bricks");
                for (int k = 1; k <= 40; k++) {
                    if (!borde) {
                        p(x, y + k, z, k % 10 == 0 && k < 40 ? "stone_bricks" : "air");
                        continue;
                    }
                    String m = esquina ? "polished_blackstone_bricks" : al(r, piedra);
                    if (k % 6 == 3 && (dx == 0 || dz == 0)) m = "air";
                    if (k % 10 == 0) m = "chiseled_stone_bricks";
                    p(x, y + k, z, m);
                }
            }
        // Aguja: 7 × 7 que se afina hasta 60 con el remate dorado.
        for (int k = 41; k <= 58; k++) {
            int rad = k < 50 ? 3 : k < 55 ? 2 : 1;
            for (int dx = -rad; dx <= rad; dx++)
                for (int dz = -rad; dz <= rad; dz++) {
                    boolean borde = Math.abs(dx) == rad || Math.abs(dz) == rad;
                    if (!borde && k < 57) continue;
                    p(cx + dx, y + k, cz + dz, k == 49 || k == 54 ? "gold_block" : al(r, "polished_blackstone_bricks", "deepslate_tiles"));
                }
        }
        p(cx, y + 59, cz, "gilded_blackstone");
        p(cx, y + 60, cz, "soul_lantern");
        // Almenas del cuerpo principal.
        for (int dx = -5; dx <= 5; dx++)
            for (int dz = -5; dz <= 5; dz++)
                if ((Math.abs(dx) == 5 || Math.abs(dz) == 5) && (dx + dz) % 2 == 0) p(cx + dx, y + 41, cz + dz, al(r, piedra));
        // La entrada (mirando al portón) es una pared ilusoria; adentro, el tesoro.
        List<int[]> ilusion = new ArrayList<>();
        for (int s = -1; s <= 1; s++)
            for (int k = 1; k <= 3; k++) {
                int x = cx + ax * 5 + lx * s, z = cz + az * 5 + lz * s;
                p(x, y + k, z, al(r, piedra));
                ilusion.add(new int[]{x, y + k, z});
            }
        l.ilusoria("ciudadela_homenaje", ilusion);
        l.cofre(cx - ax * 3, y + 1, cz - az * 3, mira(ax, az), "secreto_ciudadela", true);
        p(cx - ax * 3 + lx * 2, y + 1, cz - az * 3 + lz * 2, "gold_block");
        p(cx - ax * 3 - lx * 2, y + 1, cz - az * 3 - lz * 2, "gold_block");
        p(cx - ax * 3 + lx * 2, y + 2, cz - az * 3 + lz * 2, "candle[candles=4,lit=true]");
        p(cx - ax * 3 - lx * 2, y + 2, cz - az * 3 - lz * 2, "candle[candles=4,lit=true]");
    }

    /** Catedral gótica en ruinas: nave alta con pilares, contrafuertes, dos torres al frente y rosetón. */
    private void catedral(int cx, int cz, int ax, int az, String[] piedra, Random r) {
        int lx = -az, lz = ax;
        int media = 6, largo = 27, alto = 13;
        int x0 = cx - ax * (largo / 2), z0 = cz - az * (largo / 2); // ábside
        int y = pisoDe(cx - lx * media - ax * 13, cz - lz * media - az * 13, cx + lx * media + ax * 13, cz + lz * media + az * 13);
        ocupar(cx, cz, 16);
        for (int v = 0; v < largo + 1; v++) {
            for (int u = -media - 2; u <= media + 2; u++) {
                int x = x0 + ax * v + lx * u, z = z0 + az * v + lz * u;
                boolean torreFrente = v >= largo - 5 && Math.abs(u) >= media - 4 && v < largo;
                aire(x, y + 1, z, alto + 12);
                if (Math.abs(u) > media) {
                    // Contrafuertes cada 4.
                    if (v % 4 == 1 && v < largo - 5 && Math.abs(u) == media + 1) {
                        cimiento(x, y + 1, z, "cobblestone");
                        for (int k = 1; k <= alto - 3; k++) p(x, y + k, z, al(r, piedra));
                    }
                    continue;
                }
                if (v == largo) continue;
                p(x, y, z, (u + v) % 2 == 0 ? "polished_blackstone_bricks" : "polished_andesite");
                cimiento(x, y, z, "cobblestone");
                boolean pared = Math.abs(u) == media || v == 0 || v == largo - 1;
                boolean pilar = !pared && Math.abs(u) == media - 3 && v % 4 == 1 && v < largo - 5;
                int h = torreFrente && (Math.abs(u) == media || Math.abs(u) == media - 4 || v == largo - 5 || v == largo - 1)
                        ? alto + 10 : pared ? alto : pilar ? alto - 1 : 0;
                for (int k = 1; k <= h; k++) {
                    String m = al(r, piedra);
                    if (Math.abs(u) == media && v % 4 == 3 && k >= 4 && k <= 10 && !torreFrente)
                        m = k == 10 ? "air" : r.nextInt(3) == 0 ? "air" : al(r, "gray_stained_glass_pane", "yellow_stained_glass_pane", "black_stained_glass_pane");
                    if (!torreFrente && k > alto - 4 && r.nextInt(4) == 0) m = "air";
                    if (torreFrente && k > alto + 7 && r.nextInt(3) == 0) m = "air";
                    p(x, y + k, z, m);
                }
            }
        }
        // Rosetón en la fachada (círculo de vidrio) y portal.
        int fx = x0 + ax * (largo - 1), fz = z0 + az * (largo - 1);
        for (int u = -2; u <= 2; u++)
            for (int k = 7; k <= 11; k++) {
                double d = Math.hypot(u, k - 9);
                if (d <= 2.3) p(fx + lx * u, y + k, fz + lz * u, d < 1 ? "yellow_stained_glass_pane" : "gray_stained_glass_pane");
            }
        for (int u = -1; u <= 1; u++)
            for (int k = 1; k <= 4; k++) p(fx + lx * u, y + k, fz + lz * u, k == 4 && u != 0 ? "stone_brick_stairs[facing="
                    + mira(lx * -u, lz * -u) + ",half=top]" : "air");
        // Techo de la nave: la mitad cayó.
        for (int k = 0; k <= media; k++)
            for (int v = 1; v < largo - 5; v++) {
                if (v > largo / 2 && r.nextInt(3) > 0 || r.nextInt(6) == 0) continue;
                for (int s : new int[]{-1, 1}) {
                    int u = s * (media - k);
                    p(x0 + ax * v + lx * u, y + alto + 1 + k, z0 + az * v + lz * u,
                            u == 0 ? "deepslate_tile_slab[type=bottom]" : "deepslate_tile_stairs[facing=" + mira(-lx * s, -lz * s) + "]");
                }
            }
        // Bancos caídos, altar con velas y el atril.
        for (int v = 6; v <= largo - 7; v += 2)
            for (int u = -2; u <= 2; u++) {
                if (u == 0 || r.nextInt(4) == 0) continue;
                p(x0 + ax * v + lx * u, y + 1, z0 + az * v + lz * u, r.nextInt(5) == 0 ? "spruce_slab[type=bottom]"
                        : "spruce_stairs[facing=" + mira(ax, az) + "]");
            }
        for (int u = -2; u <= 2; u++) p(x0 + ax * 2 + lx * u, y + 1, z0 + az * 2 + lz * u, "polished_blackstone_bricks");
        for (int u = -2; u <= 2; u += 2) p(x0 + ax * 2 + lx * u, y + 2, z0 + az * 2 + lz * u, "candle[candles=4,lit=true]");
        atril(x0 + ax * 4, y + 1, z0 + az * 4, mira(ax, az), "ciudadela");
        escombros(cx, cz, 9, 26, r, "cobblestone", "cracked_stone_bricks", "stone_brick_slab[type=bottom]", "cobweb", "candle[candles=1,lit=false]");
    }

    // ------------------------------------------------------------------ Costa Hundida

    public void costa(int cx, int cz, int ax, int az, long semilla) {
        Random r = new Random(semilla);
        int lx = -az, lz = ax;
        // Arena negra.
        for (int dx = -70; dx <= 70; dx++)
            for (int dz = -70; dz <= 70; dz++) {
                double d = Math.hypot(dx, dz);
                if (d > 70 || d > 55 && r.nextDouble() < (d - 55) / 15) continue;
                int x = cx + dx, z = cz + dz, y = l.suelo(x, z);
                if (l.agua(x, z) > y) continue;
                String b = l.bloque(x, y, z);
                if (!(b.contains("grass") || b.contains("dirt") || b.equals("sand") || b.equals("podzol")
                        || b.equals("mud") || b.equals("gravel") || b.contains("moss") || b.equals("clay"))) continue;
                p(x, y, z, al(r, "black_concrete_powder", "black_concrete_powder", "gray_concrete_powder", "tuff", "gravel",
                        "black_concrete_powder"));
                String arriba = l.bloque(x, y + 1, z);
                if (blando(arriba) && !arriba.equals("air") && !arriba.equals("water")) p(x, y + 1, z, "air");
            }
        // Dársena con casas hundidas, muelle y un barco a medio hundir (hacia el Santuario, lejos de la
        // Puerta del Abismo y de la hoguera del medio).
        int dx0 = cx + ax * 30, dz0 = cz + az * 30;
        int nivel = darsena(dx0, dz0, 16, 22, lx, lz, r);
        if (nivel != Integer.MIN_VALUE) {
            ocupar(dx0, dz0, 24);
            aguaHasta = nivel;
            for (int i = 0; i < 3; i++) {
                int hx = dx0 + lx * (-8 + i * 7) + ax * (i % 2 == 0 ? -6 : 7), hz = dz0 + lz * (-8 + i * 7) + az * (i % 2 == 0 ? -6 : 7);
                casa(hx - 3, hz - 3, 6, 7, i == 1 ? 2 : 1, ax, az, COSTA, r, i == 0 ? "casa" : null, 0.45, nivel - 3);
            }
            // Muelle sobre pilotes a lo largo de un borde.
            for (int t = -14; t <= 10; t++)
                for (int s = 0; s <= 2; s++) {
                    int x = dx0 + lx * t + ax * (13 + s), z = dz0 + lz * t + az * (13 + s);
                    if (r.nextInt(9) == 0) continue;
                    p(x, nivel + 1, z, al(r, "spruce_planks", "spruce_planks", "dark_oak_planks"));
                    if (Math.floorMod(t, 4) == 0 && s != 1) {
                        for (int k = nivel; k > nivel - 6; k--) {
                            if (!blando(l.bloque(x, k, z))) break;
                            p(x, k, z, "spruce_log");
                        }
                        p(x, nivel + 2, z, "spruce_fence");
                    }
                }
            barco(dx0 - ax * 2, dz0 - az * 2, lx, lz, nivel - 2, r, false);
            aguaHasta = Integer.MIN_VALUE;
        }
        // Barco encallado en la arena: la bodega tiene una pared ilusoria.
        for (int[] c : new int[][]{{24, -12}, {30, 4}, {-30, -6}, {20, 20}, {-20, 24}, {40, -20}}) {
            int bx = cx + lx * c[0] + ax * c[1], bz = cz + lz * c[0] + az * c[1];
            if (!libre(bx, bz, 10)) continue;
            barco(bx, bz, ax, az, l.suelo(bx, bz) - 1, r, true);
            ocupar(bx, bz, 10);
            break;
        }
        // Casas de pescadores y la del capitán del puerto (con la nota).
        int puestas = 0;
        for (int intento = 0; intento < 300 && puestas < 9; intento++) {
            double ang = r.nextDouble() * Math.PI * 2, d = 12 + r.nextDouble() * 40;
            int hx = cx + (int) Math.round(Math.cos(ang) * d), hz = cz + (int) Math.round(Math.sin(ang) * d);
            if (!libre(hx, hz, 7)) continue;
            int vx = cx - hx, vz = cz - hz;
            int px = Math.abs(vx) >= Math.abs(vz) ? Integer.signum(vx) : 0, pz = px == 0 ? Integer.signum(vz) : 0;
            int y = casa(hx - 3, hz - 3, 6 + r.nextInt(2), 7, r.nextInt(3) == 0 ? 2 : 1, px, pz, COSTA, r, "casa", 0.3, Integer.MIN_VALUE);
            if (puestas == 0) atril(hx - 1, y + 1, hz, mira(px, pz), "costa");
            ocupar(hx, hz, 6);
            puestas++;
        }
        escombros(cx, cz, 55, 90, r, "barrel[facing=up]", "cobweb", "iron_chain[axis=y]", "spruce_slab[type=bottom]", "dead_bush",
                "skeleton_skull[rotation=7]");
    }

    /** Pozo de agua profunda a nivel parejo. Devuelve el nivel del agua o MIN_VALUE. */
    private int darsena(int cx, int cz, int ra, int rb, int lx, int lz, Random r) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int dx = -ra - 2; dx <= ra + 2; dx++)
            for (int dz = -ra - 2; dz <= ra + 2; dz++) {
                int y = l.suelo(cx + dx, cz + dz);
                min = Math.min(min, y);
                max = Math.max(max, y);
            }
        if (max - min > 10) return Integer.MIN_VALUE;
        int nivel = min;
        for (int dx = -ra - 2; dx <= ra + 2; dx++)
            for (int dz = -ra - 2; dz <= ra + 2; dz++) {
                // Elipse alargada sobre el eje lateral.
                double t = dx * lx + dz * lz, s = dx * lz - dz * lx;
                double e = (t * t) / (rb * rb) + (s * s) / (ra * ra);
                int x = cx + dx, z = cz + dz, y = l.suelo(x, z);
                if (e <= 1) {
                    aire(x, nivel + 1, z, Math.max(0, y - nivel) + 4);
                    int prof = e < 0.6 ? 5 : e < 0.85 ? 3 : 1;
                    for (int k = 0; k < prof; k++) p(x, nivel - k, z, "water");
                    p(x, nivel - prof, z, al(r, "gravel", "mud", "black_concrete_powder"));
                    if (r.nextInt(12) == 0 && prof > 2) p(x, nivel - prof + 1, z, "seagrass");
                } else if (e <= 1.35) {
                    p(x, nivel, z, al(r, "deepslate_bricks", "cobbled_deepslate", "tuff"));
                    if (y > nivel) aire(x, nivel + 1, z, y - nivel);
                    cimiento(x, nivel, z, "cobbled_deepslate");
                }
            }
        return nivel;
    }

    /** Barco de madera de 15 de largo. Si está encallado, la bodega guarda un cofre tras una tabla falsa. */
    private void barco(int cx, int cz, int ax, int az, int y, Random r, boolean encallado) {
        int lx = -az, lz = ax;
        for (int v = -7; v <= 7; v++) {
            int ancho = v > 4 ? 7 - v : v < -5 ? 1 : 2;
            if (ancho < 0) continue;
            for (int u = -ancho; u <= ancho; u++) {
                int x = cx + ax * v + lx * u, z = cz + az * v + lz * u;
                p(x, y, z, "dark_oak_planks");
                boolean borde = Math.abs(u) == ancho || Math.abs(v) == 7;
                for (int k = 1; k <= 3; k++) {
                    if (borde) {
                        if (!(r.nextInt(6) == 0 && k == 3)) p(x, y + k, z, al(r, "spruce_planks", "dark_oak_planks", "stripped_spruce_log"));
                    } else {
                        p(x, y + k, z, k == 3 ? (r.nextInt(4) == 0 ? "air" : "spruce_planks") : "air");
                    }
                }
            }
        }
        // Mástil roto con jirones.
        for (int k = 1; k <= 9; k++) p(cx, y + 3 + k, cz, "spruce_log");
        for (int u = -3; u <= 3; u++) if (r.nextBoolean()) p(cx + lx * u, y + 10, cz + lz * u, al(r, "white_wool", "light_gray_wool", "cobweb"));
        for (int u = -3; u <= 3; u++) p(cx + lx * u, y + 11, cz + lz * u, "spruce_fence");
        if (encallado) {
            // Tabla falsa en la proa: adentro, el cofre.
            List<int[]> ilusion = new ArrayList<>();
            int x = cx + ax * 7, z = cz + az * 7;
            for (int k = 1; k <= 2; k++) {
                p(x, y + k, z, "spruce_planks");
                ilusion.add(new int[]{x, y + k, z});
            }
            for (int k = 1; k <= 2; k++) p(cx + ax * 6, y + k, cz + az * 6, "air");
            l.ilusoria("costa_barco", ilusion);
            l.cofre(cx + ax * 5, y + 1, cz + az * 5, mira(-ax, -az), "secreto_costa", true);
        }
    }
}
