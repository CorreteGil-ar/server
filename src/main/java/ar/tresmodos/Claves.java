package ar.tresmodos;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/** Claves de PersistentDataContainer usadas para marcar ítems y entidades del plugin. */
public final class Claves {
    public static NamespacedKey ARMA, BALAS, GRANADA, CUCHILLO, BOMBARDEO, CLASE_MENU;
    public static NamespacedKey SELECTOR, CELULAR, LLAVE, PAQUETE, AUTO_DUENO, POLICIA;
    public static NamespacedKey ESTUS, CAMPANA, JEFE, SIERVO, DISPLAY_LOBBY, BOTIN;

    private Claves() {}

    static void init(Plugin p) {
        ARMA = new NamespacedKey(p, "arma");
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
    }
}
