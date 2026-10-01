package ar.tresmodos.rpg;

import org.bukkit.Material;

/** Los siete atributos que se suben en las hogueras gastando almas. */
public enum Atributo {
    VIGOR("Vigor", "<red>", "+2 de vida máxima por nivel.", Material.GOLDEN_APPLE),
    MENTE("Mente", "<aqua>", "+8 de éter máximo por nivel.", Material.LAPIS_LAZULI),
    AGUANTE("Aguante", "<green>", "+10 de aguante y +3 de carga máxima.", Material.FEATHER),
    FUERZA("Fuerza", "<gold>", "Daño de armas pesadas.", Material.IRON_INGOT),
    DESTREZA("Destreza", "<yellow>", "Daño de armas rápidas y arcos.", Material.ARROW),
    INTELIGENCIA("Inteligencia", "<blue>", "Daño de hechicería.", Material.AMETHYST_SHARD),
    FE("Fe", "<light_purple>", "Daño y curación de milagros.", Material.GLOWSTONE_DUST);

    public final String nombre, color, desc;
    public final Material icono;

    Atributo(String nombre, String color, String desc, Material icono) {
        this.nombre = nombre;
        this.color = color;
        this.desc = desc;
        this.icono = icono;
    }

    public String abreviatura() {
        return nombre.substring(0, Math.min(3, nombre.length())).toUpperCase();
    }
}
