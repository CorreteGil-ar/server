package ar.tresmodos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.text.NumberFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Util {
    private Util() {}

    public static final MiniMessage MM = MiniMessage.miniMessage();
    private static final NumberFormat NUM = NumberFormat.getIntegerInstance(Locale.of("es", "AR"));

    public static Component mm(String s) {
        return MM.deserialize(s);
    }

    /** Texto para ítems: sin la cursiva que Minecraft agrega a los nombres custom. */
    public static Component mmItem(String s) {
        return MM.deserialize(s).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static void msg(Player p, String s) {
        p.sendMessage(mm(s));
    }

    public static void barra(Player p, String s) {
        p.sendActionBar(mm(s));
    }

    public static void titulo(Player p, String titulo, String sub, int entradaMs, int quedarseMs, int salidaMs) {
        p.showTitle(Title.title(mm(titulo), mm(sub), Title.Times.times(
                Duration.ofMillis(entradaMs), Duration.ofMillis(quedarseMs), Duration.ofMillis(salidaMs))));
    }

    public static String plata(long n) {
        return "$" + NUM.format(n);
    }

    public static String num(long n) {
        return NUM.format(n);
    }

    public static ItemStack item(Material mat, String nombre, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(mmItem(nombre));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) l.add(mmItem("<gray>" + s));
            meta.lore(l);
        }
        it.setItemMeta(meta);
        return it;
    }

    public static ItemStack marcar(ItemStack it, NamespacedKey key, String valor) {
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, valor);
        it.setItemMeta(meta);
        return it;
    }

    public static String marca(ItemStack it, NamespacedKey key) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public static String estrellas(int n, int max) {
        StringBuilder sb = new StringBuilder("<yellow>");
        for (int i = 0; i < max; i++) {
            if (i == n) sb.append("<dark_gray>");
            sb.append('★');
        }
        return sb.toString();
    }

    public static String tiempo(long segundos) {
        if (segundos < 0) segundos = 0;
        return String.format("%d:%02d", segundos / 60, segundos % 60);
    }

    public static long hash(long a, long b, long semilla) {
        long h = semilla ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL);
        h ^= (h >>> 31);
        h *= 0xBF58476D1CE4E5B9L;
        h ^= (h >>> 27);
        h *= 0x94D049BB133111EBL;
        h ^= (h >>> 33);
        return h & Long.MAX_VALUE;
    }
}
