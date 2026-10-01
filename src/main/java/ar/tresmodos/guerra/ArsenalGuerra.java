package ar.tresmodos.guerra;

import ar.tresmodos.TresModos;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Equipo antitanque, demolición y vehículos de Guerra (el comportamiento llega con los vehículos). */
public class ArsenalGuerra implements Listener {
    private final TresModos plugin;
    private final ModoGuerra modo;

    public ArsenalGuerra(TresModos plugin, ModoGuerra modo) {
        this.plugin = plugin;
        this.modo = modo;
    }

    /** Ocupado con algo que no deja disparar (apuntando un Javelin, manejando). */
    public boolean ocupado(Player p) {
        return false;
    }

    public boolean enVehiculo(Player p) {
        return false;
    }

    /** El portador no puede ir en tanque, avión ni antiaéreo. */
    public boolean enVehiculoProhibido(Player p) {
        return false;
    }

    /** Repone el equipo de la clase en la caja de munición. */
    public void reabastecer(Player p, ModoGuerra.Clase c) {
        PlayerInventory inv = p.getInventory();
        switch (c) {
            case FUSILERO -> completar(inv, ArmaGuerra.GRANADA_AT, 1);
            case ANTITANQUE -> {
                completar(inv, ArmaGuerra.RPG7, 3);
                completar(inv, ArmaGuerra.AT4, 1);
                completar(inv, ArmaGuerra.JAVELIN, 2);
                completar(inv, ArmaGuerra.MINA_AT, 2);
            }
            case INGENIERO -> {
                completar(inv, ArmaGuerra.C4, 3);
                completar(inv, ArmaGuerra.STINGER, 2);
            }
            case TIRADOR -> { }
        }
    }

    /** Lleva la cantidad del ítem al máximo (o lo agrega si no está). */
    static void completar(PlayerInventory inv, ArmaGuerra a, int max) {
        for (ItemStack it : inv.getContents()) {
            if (ArmaGuerra.de(it) == a) {
                it.setAmount(Math.max(it.getAmount(), max));
                return;
            }
        }
        inv.addItem(a.crear(max));
    }

    public void olvidar(Player p) {}

    public void reiniciar() {}
}
