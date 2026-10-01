package ar.tresmodos;

import org.bukkit.World;

/** Los cuatro espacios del server. El modo de un jugador se deduce del mundo en el que está. */
public enum Modo {
    LOBBY("tm_lobby", "<white><bold>LOBBY</bold>"),
    GTA("tm_gta", "<gold><bold>GTA</bold>"),
    COD("tm_cod", "<red><bold>COD</bold>"),
    RPG("tm_rpg", "<dark_purple><bold>RPG</bold>");

    public final String mundo;
    public final String titulo;

    Modo(String mundo, String titulo) {
        this.mundo = mundo;
        this.titulo = titulo;
    }

    /** Devuelve el modo al que pertenece un mundo, o null si es un mundo ajeno (p. ej. "world"). */
    public static Modo de(World w) {
        if (w == null) return null;
        String n = w.getName();
        for (Modo m : values()) if (m.mundo.equals(n)) return m;
        return null;
    }

    public static Modo parse(String s) {
        if (s == null) return null;
        return switch (s.toLowerCase()) {
            case "lobby", "hub", "l" -> LOBBY;
            case "gta", "ciudad" -> GTA;
            case "cod", "mw", "guerra" -> COD;
            case "rpg", "souls", "dark" -> RPG;
            default -> null;
        };
    }
}
