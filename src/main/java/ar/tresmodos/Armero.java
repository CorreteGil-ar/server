package ar.tresmodos;

import ar.tresmodos.Accesorios.Mira;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.function.UnaryOperator;

/**
 * Menú para montar y sacar accesorios. Todos están desbloqueados: se elige libremente y la
 * elección queda guardada por arma, así el arma aparece igual al reaparecer o al comprarla.
 */
public class Armero {
    private final TresModos plugin;

    public Armero(TresModos plugin) {
        this.plugin = plugin;
    }

    /** Abre el armero con el arma de la mano o, si no hay, la primera de la barra rápida. */
    public void abrir(Player p) {
        int slot = p.getInventory().getHeldItemSlot();
        if (Armas.tipo(p.getInventory().getItem(slot)) == null) {
            slot = -1;
            for (int i = 0; i < 9 && slot < 0; i++) {
                if (Armas.tipo(p.getInventory().getItem(i)) != null) slot = i;
            }
        }
        if (slot < 0) {
            Util.msg(p, "<red>No tenés ningún arma en la barra rápida.");
            return;
        }
        abrir(p, slot);
    }

    public void abrir(Player p, int slot) {
        ItemStack arma = p.getInventory().getItem(slot);
        Armas.Tipo t = Armas.tipo(arma);
        if (t == null) {
            p.closeInventory();
            return;
        }
        Accesorios a = Armas.accesorios(arma);
        Menu m = new Menu(3, "<dark_gray>Armero · " + t.nombre);
        m.poner(4, arma.clone(), null);

        m.poner(10, icono(a.mira() == Mira.HIERRO ? null : a.mira().clave, Material.IRON_NUGGET,
                "<aqua><bold>Mira", a.mira().nombre, t.miras.size() > 1 ? "Clic: elegir óptica" : "Óptica fija"),
                t.miras.size() > 1 ? pl -> abrirMiras(pl, slot) : null);
        ranura(m, 11, slot, t.admiteSilenciador, a.silenciador(), "silenciador", "Silenciador",
                "Menos ruido y sin fogonazo; −15 % de alcance.", x -> x.conSilenciador(!x.silenciador()));
        ranura(m, 12, slot, t.admiteEmpunadura, a.empunadura(), "empunadura", "Empuñadura vertical",
                "−25 % de dispersión.", x -> x.conEmpunadura(!x.empunadura()));
        ranura(m, 13, slot, t.admiteLaser, a.laser(), "laser", "Láser",
                "−30 % de dispersión sin apuntar. Marca un punto rojo visible.", x -> x.conLaser(!x.laser()));
        ranura(m, 14, slot, t.admiteLinterna, a.linterna(), "linterna", "Linterna",
                "Visión nocturna mientras tenés el arma en la mano.", x -> x.conLinterna(!x.linterna()));
        ranura(m, 15, slot, t.cargadorAmpliado > 0, a.cargadorAmpliado(), "cargador", "Cargador ampliado",
                t.cargador + " → " + t.cargadorAmpliado + " balas.", x -> x.conCargadorAmpliado(!x.cargadorAmpliado()));

        Camuflaje camo = a.camo() == null ? Camuflaje.NINGUNO : a.camo();
        m.poner(16, Util.item(camo.icono, "<gold><bold>Camuflaje", camo.nombre, "<yellow>Clic: elegir"), pl -> abrirCamos(pl, slot));

        m.poner(22, Util.item(Material.LIME_DYE, "<green><bold>Listo"), Player::closeInventory);
        m.abrir(p);
    }

    private void abrirCamos(Player p, int slot) {
        ItemStack arma = p.getInventory().getItem(slot);
        Armas.Tipo t = Armas.tipo(arma);
        if (t == null) {
            p.closeInventory();
            return;
        }
        DatosJugador d = plugin.almacen().de(p);
        Camuflaje actual = Armas.accesorios(arma).camo();
        int bajas = d.armaBajas.getOrDefault(t.name(), 0), cabezas = d.armaCabezas.getOrDefault(t.name(), 0);
        Menu m = new Menu(3, "<dark_gray>Camuflaje · " + t.nombre);
        m.poner(4, Util.item(Material.PAPER, "<white><bold>" + t.nombre, "Bajas: " + bajas, "Tiros a la cabeza: " + cabezas), null);
        int pos = 9;
        for (Camuflaje c : Camuflaje.values()) {
            boolean libre = c.desbloqueado(d, t);
            String marca = c == actual ? " <green>(puesto)" : "";
            ItemStack ic = Util.item(libre ? c.icono : Material.GRAY_STAINED_GLASS_PANE,
                    (libre ? "<gold><bold>" : "<dark_gray><bold>") + c.nombre + marca,
                    libre ? "<yellow>Clic para ponerlo" : "<gray>Se gana con " + c.requisito());
            m.poner(pos++, ic, libre ? pl -> {
                cambiar(pl, slot, x -> x.conCamo(c));
                abrir(pl, slot);
            } : null);
        }
        m.poner(22, Util.item(Material.ARROW, "<white>Volver"), pl -> abrir(pl, slot));
        m.abrir(p);
    }

    private void abrirMiras(Player p, int slot) {
        ItemStack arma = p.getInventory().getItem(slot);
        Armas.Tipo t = Armas.tipo(arma);
        if (t == null) {
            p.closeInventory();
            return;
        }
        Mira actual = Armas.accesorios(arma).mira();
        Menu m = new Menu(3, "<dark_gray>Mira · " + t.nombre);
        int pos = 10;
        for (Mira mira : t.miras) {
            String marca = mira == actual ? " <green>(puesta)" : "";
            m.poner(pos, icono(mira == Mira.HIERRO ? null : mira.clave, Material.IRON_NUGGET,
                    "<aqua><bold>" + mira.nombre + marca, mira.descripcion, "Zoom al apuntar: " + (mira.zoom + 1)),
                    pl -> {
                        cambiar(pl, slot, x -> x.conMira(mira));
                        abrir(pl, slot);
                    });
            pos += 2;
        }
        m.poner(22, Util.item(Material.ARROW, "<white>Volver"), pl -> abrir(pl, slot));
        m.abrir(p);
    }

    private void ranura(Menu m, int pos, int slot, boolean admite, boolean puesto, String icono, String nombre,
                        String efecto, UnaryOperator<Accesorios> alternar) {
        if (!admite) {
            m.poner(pos, Util.item(Material.GRAY_STAINED_GLASS_PANE, "<dark_gray>" + nombre, "Esta arma no lo admite."), null);
            return;
        }
        m.poner(pos, icono(puesto ? icono : null, Material.GRAY_DYE,
                (puesto ? "<green><bold>" : "<gray><bold>") + nombre, efecto,
                puesto ? "<green>Puesto · clic para sacarlo" : "<yellow>Clic para ponerlo"), pl -> {
            cambiar(pl, slot, alternar);
            abrir(pl, slot);
        });
    }

    private void cambiar(Player p, int slot, UnaryOperator<Accesorios> cambio) {
        ItemStack arma = p.getInventory().getItem(slot);
        Armas.Tipo t = Armas.tipo(arma);
        if (t == null) return;
        Accesorios nuevos = cambio.apply(Armas.accesorios(arma)).validar(t);
        plugin.armas().cambiarAccesorios(p, slot, nuevos);
        plugin.almacen().de(p).accesorios.put(t.name(), nuevos.codigo());
        p.playSound(p, Sound.ITEM_ARMOR_EQUIP_IRON, 0.8f, 1.3f);
    }

    /** Ícono con el modelo 3D del accesorio (tresmodos:icono_*) o un ítem común si no hay accesorio. */
    private static ItemStack icono(String clave, Material sinAccesorio, String nombre, String... lore) {
        ItemStack it = Util.item(clave == null ? sinAccesorio : Material.PAPER, nombre, lore);
        if (clave != null) {
            ItemMeta meta = it.getItemMeta();
            meta.setItemModel(new NamespacedKey("tresmodos", "icono_" + clave));
            it.setItemMeta(meta);
        }
        return it;
    }
}
