package ar.tresmodos;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Claves de PersistentDataContainer usadas para marcar ítems y entidades del plugin. */
public final class Claves {
    public static NamespacedKey ARMA, ARMA_ID, ACCESORIOS, BALAS, GRANADA, CUCHILLO, BOMBARDEO, CLASE_MENU;
    public static NamespacedKey SELECTOR, CELULAR, LLAVE, PAQUETE, AUTO_DUENO, POLICIA;
    public static NamespacedKey ESTUS, CAMPANA, JEFE, SIERVO, DISPLAY_LOBBY, BOTIN;
    public static NamespacedKey RESERVA, LETAL, TACTICO, EQUIPO, RACHA, DUENIO, MUNICION, MANIQUI, DECORADO;
    public static NamespacedKey ARMA_RPG, MEJORA, ETER, CONSUMIBLE, ENEMIGO, NIVEL, ELITE, INVOCACION;

    private Claves() {}

    static void init(Plugin p) {
        ARMA = new NamespacedKey(p, "arma");
        ARMA_ID = new NamespacedKey(p, "arma_id");
        ACCESORIOS = new NamespacedKey(p, "accesorios");
        BALAS = new NamespacedKey(p, "balas");
        GRANADA = new NamespacedKey(p, "granada");
        CUCHILLO = new NamespacedKey(p, "cuchillo");
        BOMBARDEO = new NamespacedKey(p, "bombardeo");
        CLASE_MENU = new NamespacedKey(p, "clase_menu");
        SELECTOR = new NamespacedKey(p, "selector");
        CELULAR = new NamespacedKey(p, "celular");
        LLAVE = new NamespacedKey(p, "llave");
        PAQUETE = new NamespacedKey(p, "paquete");
        AUTO_DUENO = new NamespacedKey(p, "auto_dueno");
        POLICIA = new NamespacedKey(p, "policia");
        ESTUS = new NamespacedKey(p, "estus");
        CAMPANA = new NamespacedKey(p, "campana");
        JEFE = new NamespacedKey(p, "jefe");
        SIERVO = new NamespacedKey(p, "siervo");
        DISPLAY_LOBBY = new NamespacedKey(p, "display_lobby");
        BOTIN = new NamespacedKey(p, "botin");
        RESERVA = new NamespacedKey(p, "reserva");
        LETAL = new NamespacedKey(p, "letal");
        TACTICO = new NamespacedKey(p, "tactico");
        EQUIPO = new NamespacedKey(p, "equipo");
        RACHA = new NamespacedKey(p, "racha");
        DUENIO = new NamespacedKey(p, "duenio");
        MUNICION = new NamespacedKey(p, "municion");
        MANIQUI = new NamespacedKey(p, "maniqui");
        DECORADO = new NamespacedKey(p, "decorado");
        ARMA_RPG = new NamespacedKey(p, "arma_rpg");
        MEJORA = new NamespacedKey(p, "mejora");
        ETER = new NamespacedKey(p, "eter");
        CONSUMIBLE = new NamespacedKey(p, "consumible");
        ENEMIGO = new NamespacedKey(p, "enemigo");
        NIVEL = new NamespacedKey(p, "nivel");
        ELITE = new NamespacedKey(p, "elite");
        INVOCACION = new NamespacedKey(p, "invocacion");
    }
}
