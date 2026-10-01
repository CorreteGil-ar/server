package ar.tresmodos;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;
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
    // COD (histórico)
    public int codBajas, codMuertes;
    // RPG
    public long almas = 0;
    public int vigor = 0, aguante = 0, fuerza = 0;
    public int estusMax = 3;
    public Location hoguera;
    public Location mancha;
    public long almasMancha = 0;

    public DatosJugador(UUID id) {
        this.id = id;
    }

    public int nivelRpg() {
        return 1 + vigor + aguante + fuerza;
    }

    // ---------- persistencia ----------

    static DatosJugador cargar(File archivo, UUID id, java.util.logging.Logger log) {
        DatosJugador d = new DatosJugador(id);
        if (!archivo.exists()) return d;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(archivo);
        d.salidaLimpia = y.getBoolean("salidaLimpia", true);
        d.dinero = y.getLong("gta.dinero", 500);
        d.codBajas = y.getInt("cod.bajas");
        d.codMuertes = y.getInt("cod.muertes");
        d.almas = y.getLong("rpg.almas");
        d.vigor = y.getInt("rpg.vigor");
        d.aguante = y.getInt("rpg.aguante");
        d.fuerza = y.getInt("rpg.fuerza");
        d.estusMax = y.getInt("rpg.estusMax", 3);
        d.hoguera = leerLoc(y, "rpg.hoguera");
        d.mancha = leerLoc(y, "rpg.mancha");
        d.almasMancha = y.getLong("rpg.almasMancha");
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
        y.set("cod.bajas", codBajas);
        y.set("cod.muertes", codMuertes);
        y.set("rpg.almas", almas);
        y.set("rpg.vigor", vigor);
        y.set("rpg.aguante", aguante);
        y.set("rpg.fuerza", fuerza);
        y.set("rpg.estusMax", estusMax);
        escribirLoc(y, "rpg.hoguera", hoguera);
        escribirLoc(y, "rpg.mancha", mancha);
        y.set("rpg.almasMancha", almasMancha);
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
