package ar.tresmodos.rpg;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Biome;

/**
 * Zonas de Las Tierras Cenicientas, ubicadas respecto del Santuario (el spawn del mundo RPG).
 * Cada una pinta su bioma (que da el color de la niebla, el cielo, el pasto y las partículas), fija la
 * hora que ve el jugador y define el rango de nivel de sus enemigos.
 */
public enum Zona {
    SANTUARIO("Santuario del Último Fuego", "<gold>", 0, 0, 70, 1, 1, null, 12600, false, null,
            new Material[]{Material.STONE_BRICKS, Material.CHISELED_STONE_BRICKS, Material.POLISHED_ANDESITE}),
    ALDEA("Aldea Hueca", "<gray>", -430, 10, 250, 1, 10, Biome.PALE_GARDEN, 12600, false, Particle.WHITE_ASH,
            new Material[]{Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.GRAVEL, Material.COARSE_DIRT}),
    BOSQUE("Bosque Podrido", "<dark_green>", 10, -430, 250, 10, 25, Biome.SOUL_SAND_VALLEY, 12900, false, Particle.SPORE_BLOSSOM_AIR,
            new Material[]{Material.MOSSY_STONE_BRICKS, Material.MUD_BRICKS, Material.MOSS_BLOCK, Material.PACKED_MUD}),
    CIUDADELA("Ciudadela Desmoronada", "<gold>", 430, -60, 250, 25, 40, Biome.BASALT_DELTAS, 12300, false, Particle.ASH,
            new Material[]{Material.STONE_BRICKS, Material.CRACKED_STONE_BRICKS, Material.POLISHED_BLACKSTONE_BRICKS, Material.TUFF_BRICKS}),
    COSTA("Costa Hundida", "<dark_aqua>", 330, 410, 220, 40, 55, Biome.SWAMP, 18000, true, Particle.UNDERWATER,
            new Material[]{Material.DEEPSLATE_TILES, Material.BLACKSTONE, Material.TUFF, Material.PRISMARINE_BRICKS}),
    /** Bajo la Costa: se abre con las 4 almas de Señor. */
    TEMPLO("Templo del Abismo", "<dark_purple>", 330, 410, 90, 55, 70, Biome.WARPED_FOREST, 18000, false, null,
            new Material[]{Material.DARK_PRISMARINE, Material.PRISMARINE_BRICKS, Material.SCULK, Material.DEEPSLATE_BRICKS});

    /** Debajo de esta altura, cerca del centro de la Costa, empieza el Templo. */
    public static final int Y_TEMPLO = 0;

    public final String nombre, color;
    /** Centro relativo al Santuario. */
    public final int dx, dz, radio;
    public final int nivelMin, nivelMax;
    public final Biome bioma;
    /** Hora que ve el jugador (12600 atardecer, 18000 noche). */
    public final long hora;
    public final boolean lluvia;
    public final Particle ambiente;
    public final Material[] paleta;

    Zona(String nombre, String color, int dx, int dz, int radio, int nivelMin, int nivelMax, Biome bioma, long hora,
         boolean lluvia, Particle ambiente, Material[] paleta) {
        this.nombre = nombre;
        this.color = color;
        this.dx = dx;
        this.dz = dz;
        this.radio = radio;
        this.nivelMin = nivelMin;
        this.nivelMax = nivelMax;
        this.bioma = bioma;
        this.hora = hora;
        this.lluvia = lluvia;
        this.ambiente = ambiente;
        this.paleta = paleta;
    }

    /** Zona de un punto relativo al Santuario (null = tierras de paso). */
    public static Zona de(double rx, double y, double rz) {
        if (y < Y_TEMPLO && dist2(rx, rz, TEMPLO) < TEMPLO.radio * TEMPLO.radio) return TEMPLO;
        if (dist2(rx, rz, SANTUARIO) < SANTUARIO.radio * SANTUARIO.radio) return SANTUARIO;
        Zona mejor = null;
        double mejorD = Double.MAX_VALUE;
        for (Zona z : new Zona[]{ALDEA, BOSQUE, CIUDADELA, COSTA}) {
            double d = dist2(rx, rz, z);
            if (d < z.radio * z.radio && d < mejorD) {
                mejor = z;
                mejorD = d;
            }
        }
        return mejor;
    }

    private static double dist2(double rx, double rz, Zona z) {
        double a = rx - z.dx, b = rz - z.dz;
        return a * a + b * b;
    }

    /** Dirección (normalizada) desde el centro de la zona hacia el Santuario. */
    public double[] haciaSantuario() {
        double l = Math.sqrt(dx * dx + dz * dz);
        return l < 1 ? new double[]{0, 1} : new double[]{-dx / l, -dz / l};
    }
}
