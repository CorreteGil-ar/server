package ar.tresmodos.guerra;

import ar.tresmodos.Util;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/** El equipo propio de Guerra: antitanque, demolición, apoyo y reconocimiento. */
public enum ArmaGuerra {
    RPG7("RPG-7", "Cohete sin guía: 100 contra cualquier vehículo. Clic derecho para disparar.", "rpg7", 3),
    AT4("AT4", "Descartable: un solo tiro de 130.", "at4", 1),
    JAVELIN("Javelin", "Apuntá 2 s a un vehículo de tierra: ataca desde arriba (160).", "javelin", 2),
    STINGER("Stinger", "Apuntá 2 s a un avión o helicóptero: lo persigue (100).", "stinger", 2),
    C4("C4", "Clic derecho: pegar (en un vehículo o en el piso). Hasta 3.", "c4", 3),
    DETONADOR("Detonador", "Clic derecho: vuela todo tu C4.", "detonador", 1),
    MINA_AT("Mina antitanque", "Se pone en el piso: la pisa un vehículo y vuela (150).", "mina", 2),
    GRANADA_AT("Granada antitanque", "Se pega al vehículo: 60.", "granada_at", 1),
    LLAVE("Llave", "Mantené clic derecho sobre un vehículo aliado para repararlo.", "llave", 1),
    BINOCULARES("Binoculares", "Clic derecho: marca al enemigo que mirás por 10 s.", "binoculares", 1),
    BOTIQUIN("Botiquín", "Clic derecho a un compañero: lo cura. Shift + clic: a vos.", "botiquin", 1);

    public static final NamespacedKey CLAVE = new NamespacedKey("tresmodos", "arma_guerra");

    public final String nombre, desc, modelo;
    public final int cantidad;

    ArmaGuerra(String nombre, String desc, String modelo, int cantidad) {
        this.nombre = nombre;
        this.desc = desc;
        this.modelo = modelo;
        this.cantidad = cantidad;
    }

    public ItemStack crear(int n) {
        ItemStack it = Util.item(Material.CLAY_BALL, "<white><bold>" + nombre, desc);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("tresmodos", modelo));
        meta.getPersistentDataContainer().set(CLAVE, PersistentDataType.STRING, name());
        meta.setMaxStackSize(Math.max(1, cantidad));
        it.setItemMeta(meta);
        it.setAmount(Math.max(1, n));
        return it;
    }

    public static ArmaGuerra de(ItemStack it) {
        String s = Util.marca(it, CLAVE);
        if (s == null) return null;
        try {
            return valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
