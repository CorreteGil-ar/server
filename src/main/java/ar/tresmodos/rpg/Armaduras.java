package ar.tresmodos.rpg;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.Equippable;
import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.trim.TrimPattern;

import java.util.Map;

/**
 * Aspecto propio de las armaduras del RPG (texturas de herramientas/armaduras.py). Cada set se
 * reconoce por el patrón de adorno con el que se crea, así también se actualizan las piezas que los
 * jugadores ya tenían.
 */
public final class Armaduras {
    private static final Map<TrimPattern, String> POR_PATRON = Map.of(
            TrimPattern.SENTRY, "caballero", TrimPattern.RIB, "verdugo", TrimPattern.WAYFINDER, "ronin",
            TrimPattern.WILD, "cazador", TrimPattern.VEX, "hechicero", TrimPattern.SPIRE, "clerigo",
            TrimPattern.SILENCE, "seda", TrimPattern.EYE, "abismo", TrimPattern.DUNE, "carmesi");

    private Armaduras() {}

    /** Si la pieza es de un set del RPG, le pone el asset de equipo del set (y le saca el adorno). */
    public static void actualizar(ItemStack it) {
        if (it == null || !(it.getItemMeta() instanceof ArmorMeta am) || !am.hasTrim()) return;
        String set = POR_PATRON.get(am.getTrim().getPattern());
        EquipmentSlot ranura = ranura(it.getType());
        if (set == null || ranura == null) return;
        am.setTrim(null);
        it.setItemMeta((ItemMeta) am);
        String sonido = it.getType().name().startsWith("LEATHER_") ? "item.armor.equip_leather"
                : it.getType().name().startsWith("CHAINMAIL_") ? "item.armor.equip_chain"
                : it.getType().name().startsWith("NETHERITE_") ? "item.armor.equip_netherite" : "item.armor.equip_iron";
        it.setData(DataComponentTypes.EQUIPPABLE, Equippable.equippable(ranura)
                .assetId(Key.key("tresmodos", set))
                .equipSound(Key.key("minecraft", sonido))
                .build());
    }

    private static EquipmentSlot ranura(Material m) {
        String n = m.name();
        if (n.endsWith("_HELMET")) return EquipmentSlot.HEAD;
        if (n.endsWith("_CHESTPLATE")) return EquipmentSlot.CHEST;
        if (n.endsWith("_LEGGINGS")) return EquipmentSlot.LEGS;
        if (n.endsWith("_BOOTS")) return EquipmentSlot.FEET;
        return null;
    }
}
