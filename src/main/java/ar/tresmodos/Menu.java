package ar.tresmodos;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Menú de inventario con acciones por casilla. Los clics nunca mueven ítems. */
public class Menu implements InventoryHolder {
    private final Inventory inv;
    private final Map<Integer, Consumer<Player>> acciones = new HashMap<>();

    public Menu(int filas, String titulo) {
        this.inv = Bukkit.createInventory(this, filas * 9, Util.mm(titulo));
    }

    public Menu poner(int slot, ItemStack item, Consumer<Player> accion) {
        inv.setItem(slot, item);
        if (accion != null) acciones.put(slot, accion);
        return this;
    }

    public void abrir(Player p) {
        p.openInventory(inv);
    }

    @Override
    public Inventory getInventory() {
        return inv;
    }

    public static class Escucha implements Listener {
        @EventHandler
        public void alClic(InventoryClickEvent e) {
            if (!(e.getView().getTopInventory().getHolder(false) instanceof Menu menu)) return;
            e.setCancelled(true);
            if (e.getClickedInventory() != e.getView().getTopInventory()) return;
            Consumer<Player> accion = menu.acciones.get(e.getRawSlot());
            if (accion != null && e.getWhoClicked() instanceof Player p) accion.accept(p);
        }

        @EventHandler
        public void alArrastrar(InventoryDragEvent e) {
            if (e.getView().getTopInventory().getHolder(false) instanceof Menu) e.setCancelled(true);
        }
    }

    public static Component vacio() {
        return Component.empty();
    }
}
