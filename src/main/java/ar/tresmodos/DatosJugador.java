package ar.tresmodos;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import ar.tresmodos.rpg.Atributo;
import ar.tresmodos.rpg.Rama;

import java.util.Base64;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/** Todo lo que se guarda de un jugador entre sesiones. */
public class DatosJugador {

    /** Foto del jugador dentro de un modo: inventario, vida, hambre y posición. */
    public static class Estado {
        public byte[] inventario;
        public double vida = 20;
        public int comida = 20;
        public float saturacion = 5;
        public Location ubicacion;
    }

    public final UUID id;
    public final Map<Modo, Estado> estados = new EnumMap<>(Modo.class);
    /** false mientras el jugador está conectado: si el server se cae, al volver se rescata su inventario. */
    public boolean salidaLimpia = true;

    // GTA
    public long dinero = 500;
    // Shooter (histórico y clases personalizadas)
    public int codBajas, codMuertes, shooterVictorias, shooterBombas;
    /** Las 5 clases personalizadas (ClasesShooter.Clase.codigo()); vacío = la clase por defecto. */
    public final String[] shooterClases = new String[5];
    public int shooterClase = 0;
    // RPG
    public long almas = 0;
    public int vigor = 0, aguante = 0, fuerza = 0, mente = 0, destreza = 0, inteligencia = 0, fe = 0;
    /** Niveles comprados en las hogueras (el nivel es 1 + esto). */
    public int nivelesComprados = 0;
    /** Cargas totales de frascos y cuántas son de Éter (el resto son de Estus). */
    public int estusMax = 3, eterCargas = 1;
    public String clase;
    public final Map<Rama, Integer> arbol = new EnumMap<>(Rama.class);
    /** Tipo de daño elegido al entrar a la rama Daño (ClaseRpg.TipoDanio). */
    public String tipoDanio;
    /** Rama cuya Q y definitiva se usan (cuando hay más de una con nivel 3 o 5). */
    public Rama ramaQ, ramaDefinitiva;
    public final Set<String> jefes = new LinkedHashSet<>();
    public final Set<String> almasJefe = new LinkedHashSet<>();
    public final Set<String> hogueras = new LinkedHashSet<>();
    public int ciclo = 0;
    public int lagrimas = 0;
    /** Hechizos aprendidos (rpg.Magia.Hechizo) y el elegido. */
    public final Set<String> hechizos = new LinkedHashSet<>();
    public String hechizo;
    public Location hoguera;
    public Location mancha;
    public long almasMancha = 0;
    // Armas: accesorios elegidos en el armero, por tipo de arma (Armas.Tipo -> Accesorios.codigo()).
    public final Map<String, String> accesorios = new HashMap<>();

    public DatosJugador(UUID id) {
        this.id = id;
    }

    public int nivelRpg() {
        return 1 + nivelesComprados;
    }

    public int atributo(Atributo a) {
        return switch (a) {
            case VIGOR -> vigor;
            case MENTE -> mente;
            case AGUANTE -> aguante;
            case FUERZA -> fuerza;
            case DESTREZA -> destreza;
            case INTELIGENCIA -> inteligencia;
            case FE -> fe;
        };
    }

    public void setAtributo(Atributo a, int v) {
        switch (a) {
            case VIGOR -> vigor = v;
            case MENTE -> mente = v;
            case AGUANTE -> aguante = v;
            case FUERZA -> fuerza = v;
            case DESTREZA -> destreza = v;
            case INTELIGENCIA -> inteligencia = v;
            case FE -> fe = v;
        }
    }

    public int rama(Rama r) {
        return arbol.getOrDefault(r, 0);
    }

    /** Accesorios guardados para un tipo de arma, ya validados para ese arma. */
    public Accesorios accesorios(Armas.Tipo t) {
        return Accesorios.de(accesorios.get(t.name())).validar(t);
    }

    // ---------- persistencia ----------

    static DatosJugador cargar(File archivo, UUID id, java.util.logging.Logger log) {
        DatosJugador d = new DatosJugador(id);
        if (!archivo.exists()) return d;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(archivo);
        d.salidaLimpia = y.getBoolean("salidaLimpia", true);
        d.dinero = y.getLong("gta.dinero", 500);
        d.codBajas = y.getInt("shooter.bajas", y.getInt("cod.bajas"));
        d.codMuertes = y.getInt("shooter.muertes", y.getInt("cod.muertes"));
        d.shooterVictorias = y.getInt("shooter.victorias");
        d.shooterBombas = y.getInt("shooter.bombas");
        d.shooterClase = Math.max(0, Math.min(4, y.getInt("shooter.clase")));
        for (int i = 0; i < 5; i++) d.shooterClases[i] = y.getString("shooter.clases." + i);
        d.almas = y.getLong("rpg.almas");
        d.vigor = y.getInt("rpg.vigor");
        d.aguante = y.getInt("rpg.aguante");
        d.fuerza = y.getInt("rpg.fuerza");
        d.mente = y.getInt("rpg.mente");
        d.destreza = y.getInt("rpg.destreza");
        d.inteligencia = y.getInt("rpg.inteligencia");
        d.fe = y.getInt("rpg.fe");
        // Datos viejos (antes de las clases): cada punto de atributo era un nivel comprado.
        d.nivelesComprados = y.getInt("rpg.niveles", d.vigor + d.aguante + d.fuerza);
        d.estusMax = y.getInt("rpg.estusMax", 3);
        d.eterCargas = y.getInt("rpg.eterCargas", 1);
        d.clase = y.getString("rpg.clase");
        d.tipoDanio = y.getString("rpg.tipoDanio");
        for (Rama r : Rama.values()) d.arbol.put(r, y.getInt("rpg.arbol." + r.name().toLowerCase()));
        d.ramaQ = rama(y.getString("rpg.ramaQ"));
        d.ramaDefinitiva = rama(y.getString("rpg.ramaDefinitiva"));
        d.jefes.addAll(y.getStringList("rpg.jefes"));
        d.almasJefe.addAll(y.getStringList("rpg.almasJefe"));
        d.hogueras.addAll(y.getStringList("rpg.hogueras"));
        d.ciclo = y.getInt("rpg.ciclo");
        d.lagrimas = y.getInt("rpg.lagrimas");
        d.hechizos.addAll(y.getStringList("rpg.hechizos"));
        d.hechizo = y.getString("rpg.hechizo");
        d.hoguera = leerLoc(y, "rpg.hoguera");
        d.mancha = leerLoc(y, "rpg.mancha");
        d.almasMancha = y.getLong("rpg.almasMancha");
        ConfigurationSection acc = y.getConfigurationSection("armas.accesorios");
        if (acc != null) for (String k : acc.getKeys(false)) d.accesorios.put(k, acc.getString(k, ""));
        ConfigurationSection est = y.getConfigurationSection("estados");
        if (est != null) {
            for (String k : est.getKeys(false)) {
                Modo m = Modo.parse(k);
                if (m == null) continue;
                ConfigurationSection s = est.getConfigurationSection(k);
                if (s == null) continue;
                Estado e = new Estado();
                try {
                    String inv = s.getString("inventario");
                    if (inv != null) e.inventario = Base64.getDecoder().decode(inv);
                } catch (IllegalArgumentException ex) {
                    log.log(Level.WARNING, "Inventario corrupto de " + id + " en " + k, ex);
                }
                e.vida = s.getDouble("vida", 20);
                e.comida = s.getInt("comida", 20);
                e.saturacion = (float) s.getDouble("saturacion", 5);
                e.ubicacion = leerLoc(s, "ubicacion");
                d.estados.put(m, e);
            }
        }
        return d;
    }

    void guardar(File archivo, java.util.logging.Logger log) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("salidaLimpia", salidaLimpia);
        y.set("gta.dinero", dinero);
        y.set("shooter.bajas", codBajas);
        y.set("shooter.muertes", codMuertes);
        y.set("shooter.victorias", shooterVictorias);
        y.set("shooter.bombas", shooterBombas);
        y.set("shooter.clase", shooterClase);
        for (int i = 0; i < 5; i++) if (shooterClases[i] != null) y.set("shooter.clases." + i, shooterClases[i]);
        y.set("rpg.almas", almas);
        y.set("rpg.vigor", vigor);
        y.set("rpg.aguante", aguante);
        y.set("rpg.fuerza", fuerza);
        y.set("rpg.estusMax", estusMax);
        y.set("rpg.mente", mente);
        y.set("rpg.destreza", destreza);
        y.set("rpg.inteligencia", inteligencia);
        y.set("rpg.fe", fe);
        y.set("rpg.niveles", nivelesComprados);
        y.set("rpg.eterCargas", eterCargas);
        y.set("rpg.clase", clase);
        y.set("rpg.tipoDanio", tipoDanio);
        for (Rama r : Rama.values()) y.set("rpg.arbol." + r.name().toLowerCase(), rama(r));
        y.set("rpg.ramaQ", ramaQ == null ? null : ramaQ.name());
        y.set("rpg.ramaDefinitiva", ramaDefinitiva == null ? null : ramaDefinitiva.name());
        y.set("rpg.jefes", new java.util.ArrayList<>(jefes));
        y.set("rpg.almasJefe", new java.util.ArrayList<>(almasJefe));
        y.set("rpg.hogueras", new java.util.ArrayList<>(hogueras));
        y.set("rpg.ciclo", ciclo);
        y.set("rpg.lagrimas", lagrimas);
        y.set("rpg.hechizos", new java.util.ArrayList<>(hechizos));
        y.set("rpg.hechizo", hechizo);
        escribirLoc(y, "rpg.hoguera", hoguera);
        escribirLoc(y, "rpg.mancha", mancha);
        y.set("rpg.almasMancha", almasMancha);
        for (Map.Entry<String, String> en : accesorios.entrySet()) y.set("armas.accesorios." + en.getKey(), en.getValue());
        for (Map.Entry<Modo, Estado> en : estados.entrySet()) {
            String base = "estados." + en.getKey().name().toLowerCase();
            Estado e = en.getValue();
            if (e.inventario != null) y.set(base + ".inventario", Base64.getEncoder().encodeToString(e.inventario));
            y.set(base + ".vida", e.vida);
            y.set(base + ".comida", e.comida);
            y.set(base + ".saturacion", e.saturacion);
            escribirLoc(y, base + ".ubicacion", e.ubicacion);
        }
        try {
            File tmp = new File(archivo.getParentFile(), archivo.getName() + ".tmp");
            y.save(tmp);
            if (!tmp.renameTo(archivo)) {
                java.nio.file.Files.move(tmp.toPath(), archivo.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            log.log(Level.SEVERE, "No pude guardar los datos de " + id, ex);
        }
    }

    private static Rama rama(String s) {
        if (s == null) return null;
        try {
            return Rama.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // Guardamos ubicaciones como texto para no depender de que el mundo esté cargado al leer.
    private static void escribirLoc(ConfigurationSection y, String ruta, Location l) {
        if (l == null || l.getWorld() == null) {
            y.set(ruta, null);
            return;
        }
        y.set(ruta, l.getWorld().getName() + ";" + l.getX() + ";" + l.getY() + ";" + l.getZ() + ";" + l.getYaw() + ";" + l.getPitch());
    }

    private static Location leerLoc(ConfigurationSection y, String ruta) {
        String s = y.getString(ruta);
        if (s == null) return null;
        String[] p = s.split(";");
        if (p.length < 6) return null;
        org.bukkit.World w = org.bukkit.Bukkit.getWorld(p[0]);
        if (w == null) return null;
        try {
            return new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3]),
                    Float.parseFloat(p[4]), Float.parseFloat(p[5]));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
