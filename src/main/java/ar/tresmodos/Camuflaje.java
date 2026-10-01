package ar.tresmodos;

import org.bukkit.Material;

/**
 * Camuflajes de las armas. Se ganan con bajas y tiros a la cabeza de esa arma (Shooter y Guerra).
 * La clave va como séptimo string de custom_model_data (ver herramientas/camuflajes.py).
 */
public enum Camuflaje {
    NINGUNO("", "Sin camuflaje", Material.GRAY_DYE, 0, 0),
    BOSQUE("bosque", "Bosque", Material.GREEN_DYE, 10, 0),
    DESIERTO("desierto", "Desierto", Material.SAND, 25, 0),
    URBANO("urbano", "Urbano", Material.LIGHT_GRAY_DYE, 50, 0),
    TIGRE("tigre", "Tigre", Material.ORANGE_DYE, 50, 15),
    DIGITAL("digital", "Digital", Material.CYAN_DYE, 100, 30),
    ORO("oro", "Oro", Material.GOLD_INGOT, 150, 50),
    DIAMANTE("diamante", "Diamante", Material.DIAMOND, 300, 100),
    ATOMICO("atomico", "Atómico", Material.LIME_DYE, 500, 150);

    public final String clave, nombre;
    public final Material icono;
    public final int bajas, cabezas;

    Camuflaje(String clave, String nombre, Material icono, int bajas, int cabezas) {
        this.clave = clave;
        this.nombre = nombre;
        this.icono = icono;
        this.bajas = bajas;
        this.cabezas = cabezas;
    }

    public static Camuflaje de(String clave) {
        for (Camuflaje c : values()) if (c.clave.equals(clave)) return c;
        return NINGUNO;
    }

    /** El Atómico, además, pide haber tirado una bomba atómica en el Shooter. */
    public boolean desbloqueado(DatosJugador d, Armas.Tipo t) {
        if (this == NINGUNO) return true;
        int b = d.armaBajas.getOrDefault(t.name(), 0), c = d.armaCabezas.getOrDefault(t.name(), 0);
        return b >= bajas && c >= cabezas && (this != ATOMICO || d.shooterBombas > 0);
    }

    public String requisito() {
        if (this == NINGUNO) return "";
        String s = bajas + " bajas";
        if (cabezas > 0) s += " y " + cabezas + " tiros a la cabeza";
        if (this == ATOMICO) s += " y una bomba atómica";
        return s;
    }
}
