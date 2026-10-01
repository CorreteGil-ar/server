package ar.tresmodos;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;

/** Contrato común de los cuatro modos (lobby incluido). */
public interface ModoJuego {
    Modo modo();

    /** true si el modo guarda inventario/vida/posición al salir (GTA y RPG). */
    boolean guardaEstado();

    GameMode modoJuego();

    /** Dónde aparece el jugador si no tiene una posición guardada. */
    Location ubicacionEntrada(Player p);

    /** Ajustes de atributos antes de restaurar la vida (p. ej. vida máxima por Vigor). */
    default void prepararAtributos(Player p) {}

    /** Ítems iniciales: la primera vez en GTA/RPG, siempre en Shooter y lobby. */
    void kitInicial(Player p);

    /** Después de teletransportar y restaurar el estado. */
    void alEntrar(Player p, boolean primeraVez);

    /** Antes de guardar el estado y limpiar al jugador. */
    void alSalir(Player p);

    /** Lugar de reaparición al morir dentro del modo. */
    Location respawn(Player p);

    /** Un tick después de reaparecer. */
    default void alReaparecer(Player p) {}

    String tituloSidebar(Player p);

    List<String> lineasSidebar(Player p);
}
