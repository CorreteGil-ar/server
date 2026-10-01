package ar.tresmodos;

import java.util.ArrayList;
import java.util.List;

/**
 * Accesorios montados en un arma. Se guardan en el ítem como texto ({@link #codigo()}) y se le
 * pasan al paquete de recursos como strings de custom_model_data ({@link #cadenasModelo()}),
 * en el orden que espera herramientas/generar.py: mira, boca, bajo, láser, linterna, cargador y
 * camuflaje.
 */
public record Accesorios(Mira mira, boolean silenciador, boolean empunadura, boolean laser,
                         boolean linterna, boolean cargadorAmpliado, Camuflaje camo) {

    /** Ópticas. zoom = nivel de lentitud al apuntar (cada nivel cierra el campo visual). */
    public enum Mira {
        HIERRO("", "Miras de hierro", "Alza y guion. Zoom mínimo.", 0),
        PUNTO_ROJO("punto_rojo", "Punto rojo", "Punto compacto, campo visual amplio.", 0),
        HOLOGRAFICA("holografica", "Holográfica", "Anillo con punto, rápida de adquirir.", 1),
        ACOG("acog", "ACOG 4x", "Aumento 4x para media distancia.", 3),
        TELESCOPICA("telescopica", "Telescópica 8x", "Aumento 8x para larga distancia.", 6);

        public final String clave, nombre, descripcion;
        public final int zoom;

        Mira(String clave, String nombre, String descripcion, int zoom) {
            this.clave = clave;
            this.nombre = nombre;
            this.descripcion = descripcion;
            this.zoom = zoom;
        }

        static Mira de(String clave) {
            for (Mira m : values()) if (m.clave.equals(clave)) return m;
            return HIERRO;
        }
    }

    public static final Accesorios NINGUNO = new Accesorios(Mira.HIERRO, false, false, false, false, false, Camuflaje.NINGUNO);

    public Accesorios conMira(Mira m) {
        return new Accesorios(m, silenciador, empunadura, laser, linterna, cargadorAmpliado, camo);
    }

    public Accesorios conSilenciador(boolean v) {
        return new Accesorios(mira, v, empunadura, laser, linterna, cargadorAmpliado, camo);
    }

    public Accesorios conEmpunadura(boolean v) {
        return new Accesorios(mira, silenciador, v, laser, linterna, cargadorAmpliado, camo);
    }

    public Accesorios conLaser(boolean v) {
        return new Accesorios(mira, silenciador, empunadura, v, linterna, cargadorAmpliado, camo);
    }

    public Accesorios conLinterna(boolean v) {
        return new Accesorios(mira, silenciador, empunadura, laser, v, cargadorAmpliado, camo);
    }

    public Accesorios conCargadorAmpliado(boolean v) {
        return new Accesorios(mira, silenciador, empunadura, laser, linterna, v, camo);
    }

    public Accesorios conCamo(Camuflaje c) {
        return new Accesorios(mira, silenciador, empunadura, laser, linterna, cargadorAmpliado, c == null ? Camuflaje.NINGUNO : c);
    }

    /** Quita lo que el arma no admite y fuerza la óptica fija (por ejemplo, la de la Barrett). */
    public Accesorios validar(Armas.Tipo t) {
        Mira m = t.miras.contains(mira) ? mira : t.miras.getFirst();
        return new Accesorios(m, silenciador && t.admiteSilenciador, empunadura && t.admiteEmpunadura,
                laser && t.admiteLaser, linterna && t.admiteLinterna, cargadorAmpliado && t.cargadorAmpliado > 0,
                camo == null ? Camuflaje.NINGUNO : camo);
    }

    public List<String> cadenasModelo() {
        return List.of(mira.clave, silenciador ? "silenciador" : "", empunadura ? "empunadura" : "",
                laser ? "laser" : "", linterna ? "linterna" : "", cargadorAmpliado ? "ampliado" : "",
                camo == null ? "" : camo.clave);
    }

    /** Texto compacto para guardar en el ítem y en los datos del jugador: "acog,silenciador,laser". */
    public String codigo() {
        List<String> l = new ArrayList<>();
        if (mira != Mira.HIERRO) l.add(mira.clave);
        if (silenciador) l.add("silenciador");
        if (empunadura) l.add("empunadura");
        if (laser) l.add("laser");
        if (linterna) l.add("linterna");
        if (cargadorAmpliado) l.add("ampliado");
        if (camo != null && camo != Camuflaje.NINGUNO) l.add("camo:" + camo.clave);
        return String.join(",", l);
    }

    public static Accesorios de(String codigo) {
        if (codigo == null || codigo.isBlank()) return NINGUNO;
        Accesorios a = NINGUNO;
        for (String parte : codigo.split(",")) {
            switch (parte.trim()) {
                case "silenciador" -> a = a.conSilenciador(true);
                case "empunadura" -> a = a.conEmpunadura(true);
                case "laser" -> a = a.conLaser(true);
                case "linterna" -> a = a.conLinterna(true);
                case "ampliado" -> a = a.conCargadorAmpliado(true);
                default -> {
                    String x = parte.trim();
                    if (x.startsWith("camo:")) a = a.conCamo(Camuflaje.de(x.substring(5)));
                    else a = a.conMira(Mira.de(x));
                }
            }
        }
        return a;
    }

    /** Líneas de descripción para el lore del arma. */
    public List<String> resumen() {
        List<String> l = new ArrayList<>();
        l.add("Mira: " + mira.nombre);
        if (silenciador) l.add("Silenciador");
        if (empunadura) l.add("Empuñadura vertical");
        if (laser) l.add("Láser");
        if (linterna) l.add("Linterna");
        if (cargadorAmpliado) l.add("Cargador ampliado");
        if (camo != null && camo != Camuflaje.NINGUNO) l.add("Camuflaje " + camo.nombre);
        return l;
    }
}
