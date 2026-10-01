package ar.tresmodos;

import org.bukkit.entity.Player;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Caché en memoria + archivos YAML por jugador (plugins/TresModos/jugadores/<uuid>.yml). */
public class Almacen {
    private final TresModos plugin;
    private final File carpeta;
    private final Map<UUID, DatosJugador> cache = new HashMap<>();

    public Almacen(TresModos plugin) {
        this.plugin = plugin;
        this.carpeta = new File(plugin.getDataFolder(), "jugadores");
        if (!carpeta.exists() && !carpeta.mkdirs()) {
            plugin.getLogger().warning("No pude crear la carpeta " + carpeta);
        }
    }

    public DatosJugador de(Player p) {
        return de(p.getUniqueId());
    }

    public DatosJugador de(UUID id) {
        return cache.computeIfAbsent(id, u -> DatosJugador.cargar(archivo(u), u, plugin.getLogger()));
    }

    public void guardar(UUID id) {
        DatosJugador d = cache.get(id);
        if (d != null) d.guardar(archivo(id), plugin.getLogger());
    }

    public void guardarYSoltar(UUID id) {
        guardar(id);
        cache.remove(id);
    }

    public void guardarTodo() {
        for (UUID id : cache.keySet()) guardar(id);
    }

    private File archivo(UUID id) {
        return new File(carpeta, id + ".yml");
    }
}
