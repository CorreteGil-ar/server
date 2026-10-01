package ar.tresmodos.rpg;

import org.bukkit.Material;

/** Las tres ramas del árbol de habilidades de cada clase. */
public enum Rama {
    DANIO("Daño", "<red>", "Más daño y el tipo de daño de tu clase.", Material.IRON_SWORD),
    VIDA("Vida y armadura", "<gold>", "Vida máxima, defensa y bloqueo más barato.", Material.SHIELD),
    BENDICION("Bendiciones y sanación", "<light_purple>", "Curar, potenciar aliados, aguante y éter.", Material.GLOW_BERRIES);

    public final String nombre, color, desc;
    public final Material icono;

    Rama(String nombre, String color, String desc, Material icono) {
        this.nombre = nombre;
        this.color = color;
        this.desc = desc;
        this.icono = icono;
    }

    /** Brasas que cuesta cada nivel de la rama (1 a 5). */
    public static final int[] COSTO = {1, 2, 3, 3, 4};

    public static int costoAcumulado(int nivel) {
        int c = 0;
        for (int i = 0; i < nivel; i++) c += COSTO[i];
        return c;
    }
}
