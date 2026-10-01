package ar.tresmodos.rpg;

import org.bukkit.Color;

/** Estados alterados: se acumulan golpe a golpe y al llenarse estallan. */
public enum Estado {
    SANGRADO("Sangrado", "<dark_red>", Color.fromRGB(150, 0, 0), 100),
    VENENO("Veneno", "<dark_green>", Color.fromRGB(70, 140, 40), 100),
    FRIO("Frío", "<aqua>", Color.fromRGB(170, 220, 255), 100),
    LOCURA("Locura", "<gold>", Color.fromRGB(230, 180, 40), 100);

    public final String nombre, color;
    public final Color particula;
    public final double umbral;

    Estado(String nombre, String color, Color particula, double umbral) {
        this.nombre = nombre;
        this.color = color;
        this.particula = particula;
        this.umbral = umbral;
    }
}
