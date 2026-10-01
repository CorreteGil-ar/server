package ar.tresmodos;

import org.bukkit.World;

/** Los cuatro espacios del server (Guerra reemplazó al viejo GTA). El modo de un jugador se deduce del mundo en el que está. */
public enum Modo {
    LOBBY("tm_lobby", "<white><bold>LOBBY</bold>"),
    GUERRA("tm_guerra", "<dark_green><bold>GUERRA</bold>"),
    SHOOTER("tm_shooter", "<red><bold>SHOOTER</bold>"),
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
            case "guerra", "war", "ctf", "g", "gta" -> GUERRA;
            case "shooter", "cod", "mw", "s" -> SHOOTER;
            case "rpg", "souls", "dark" -> RPG;
            default -> null;
        };
    }
}
