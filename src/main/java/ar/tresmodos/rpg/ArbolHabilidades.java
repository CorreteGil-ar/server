package ar.tresmodos.rpg;

import ar.tresmodos.DatosJugador;
import ar.tresmodos.Menu;
import ar.tresmodos.TresModos;
import ar.tresmodos.Util;
import ar.tresmodos.rpg.ClaseRpg.TipoDanio;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Árbol de habilidades: tres ramas de 5 niveles que se compran con brasas (una cada 3 niveles de
 * personaje y 2 por cada alma de jefe). El nivel 3 de una rama transforma la Q y el 5 da la
 * definitiva; si tenés varias, elegís cuál usar. La Lágrima del Olvido lo reinicia.
 */
public class ArbolHabilidades implements Listener {
    private final TresModos plugin;
    private final ModoRpg modo;

    public ArbolHabilidades(TresModos plugin, ModoRpg modo) {
        this.plugin = plugin;
        this.modo = modo;
    }

    public static int brasasTotales(DatosJugador d) {
        int jefes = 0;
        for (String j : List.of("gloton", "tejedora", "abismo", "vigia", "durmiente")) if (d.jefes.contains(j)) jefes++;
        return d.nivelRpg() / 3 + 2 * jefes;
    }

    public static int brasasGastadas(DatosJugador d) {
        int g = 0;
        for (Rama r : Rama.values()) g += Rama.costoAcumulado(d.rama(r));
        return g;
    }

    public int brasasLibres(DatosJugador d) {
        return brasasTotales(d) - brasasGastadas(d);
    }

    /** Qué da cada nivel de cada rama (texto del nodo). */
    private static String efecto(Rama r, int nivel) {
        return switch (r) {
            case DANIO -> nivel == 1 ? "+8 % de daño y elegís tu tipo de daño"
                    : nivel == 3 ? "+8 % de daño · la Q se transforma"
                    : nivel == 5 ? "+8 % de daño · definitiva (Shift+Q)"
                    : "+8 % de daño y más acumulación del tipo";
            case VIDA -> nivel == 3 ? "+8 % de vida, −4 % de daño · la Q se transforma"
                    : nivel == 5 ? "+8 % de vida, −4 % de daño · definitiva"
                    : "+8 % de vida, −4 % de daño, bloqueo más barato";
            case BENDICION -> nivel == 3 ? "+20 % de éter, Estus +5 % · la Q se transforma"
                    : nivel == 5 ? "+20 % de éter, Estus +5 % · definitiva"
                    : "+20 % de regeneración de éter, +6 % de aguante, Estus +5 %";
        };
    }

    public void abrir(Player p) {
        DatosJugador d = plugin.almacen().de(p);
        ClaseRpg c = ClaseRpg.de(d.clase);
        if (c == null) {
            modo.abrirClases(p);
            return;
        }
        int libres = brasasLibres(d);
        Menu m = new Menu(5, "<dark_gray>Árbol · " + c.nombre + " · " + libres + " brasas");
        m.poner(4, Util.item(Material.BLAZE_POWDER, "<gold><bold>Brasas: " + libres,
                "<gray>Ganás una cada 3 niveles y 2 por cada",
                "<gray>alma de jefe. Totales: <white>" + brasasTotales(d),
                "", "<gray>La <white>Lágrima del Olvido<gray> reinicia el árbol."), pl -> { });
        Rama[] ramas = Rama.values();
        for (int i = 0; i < ramas.length; i++) {
            Rama r = ramas[i];
            int fila = 9 * (i + 1);
            int nivel = d.rama(r);
            m.poner(fila, Util.item(r.icono, r.color + "<bold>" + r.nombre + " <white>" + nivel + "/5", r.desc), pl -> { });
            for (int n = 1; n <= 5; n++) {
                final int niv = n;
                boolean tiene = nivel >= n;
                boolean siguiente = nivel == n - 1;
                Material mat = tiene ? Material.ORANGE_STAINED_GLASS_PANE
                        : siguiente ? Material.YELLOW_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE;
                List<String> lore = new ArrayList<>();
                lore.add("<white>" + efecto(r, n));
                Habilidades.Variante v = Habilidades.variante(c, r, d.tipoDanio);
                if (n == 3) {
                    if (r == Rama.DANIO && d.tipoDanio == null) {
                        lore.add("<aqua>Q: <gray>según el tipo que elijas");
                    } else {
                        Habilidades.Def q = Habilidades.defQ(c, v);
                        lore.add("<aqua>Q: <white>" + q.nombre() + " <gray>— " + q.desc());
                    }
                }
                if (n == 5) {
                    if (r == Rama.DANIO && d.tipoDanio == null) {
                        lore.add("<gold>Definitiva: <gray>según el tipo que elijas");
                    } else {
                        Habilidades.Def u = Habilidades.defUlt(c, v);
                        lore.add("<gold>Definitiva: <white>" + u.nombre() + " <gray>— " + u.desc());
                    }
                }
                lore.add("");
                lore.add(tiene ? "<green>Comprado" : siguiente ? "<yellow>Clic: comprar (" + Rama.COSTO[n - 1] + " brasas)"
                        : "<dark_gray>Necesitás el nivel anterior");
                m.poner(fila + 1 + n, Util.item(mat, r.color + r.nombre + " " + romano(n), lore.toArray(new String[0])),
                        pl -> comprar(pl, r, niv));
            }
            // Elegir cuál Q y cuál definitiva usar.
            boolean qActiva = r == d.ramaQ;
            m.poner(fila + 7, Util.item(nivel >= 3 ? (qActiva ? Material.LIME_DYE : Material.GRAY_DYE) : Material.BARRIER,
                    "<aqua>Usar esta Q", nivel < 3 ? "<dark_gray>Requiere nivel 3" : qActiva ? "<green>En uso" : "<yellow>Clic para usarla"),
                    pl -> elegirQ(pl, r));
            boolean uActiva = r == d.ramaDefinitiva;
            m.poner(fila + 8, Util.item(nivel >= 5 ? (uActiva ? Material.NETHER_STAR : Material.GRAY_DYE) : Material.BARRIER,
                    "<gold>Usar esta definitiva", nivel < 5 ? "<dark_gray>Requiere nivel 5" : uActiva ? "<green>En uso" : "<yellow>Clic para usarla"),
                    pl -> elegirDefinitiva(pl, r));
        }
        if (d.tipoDanio != null) {
            m.poner(40, Util.item(Material.FIRE_CHARGE, "<red>Tipo de daño: <white>" + TipoDanio.valueOf(d.tipoDanio).nombre,
                    "<gray>Se cambia reiniciando el árbol."), pl -> { });
        }
        m.poner(36, Util.item(Material.ARROW, "<white>Volver a la hoguera"), modo::abrirHoguera);
        m.abrir(p);
    }

    private static String romano(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> "V";
        };
    }

    private void comprar(Player p, Rama r, int nivel) {
        DatosJugador d = plugin.almacen().de(p);
        if (d.rama(r) != nivel - 1) return;
        int costo = Rama.COSTO[nivel - 1];
        if (brasasLibres(d) < costo) {
            Util.msg(p, "<red>Te faltan brasas. <gray>Subí de nivel o derrotá jefes.");
            p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
            return;
        }
        if (r == Rama.DANIO && nivel == 1 && d.tipoDanio == null) {
            elegirTipo(p);
            return;
        }
        d.arbol.put(r, nivel);
        if (nivel == 3 && d.ramaQ == null) d.ramaQ = r;
        if (nivel == 5 && d.ramaDefinitiva == null) d.ramaDefinitiva = r;
        if (r == Rama.VIDA) {
            modo.prepararAtributos(p);
        }
        p.playSound(p, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.4f);
        abrir(p);
    }

    private void elegirTipo(Player p) {
        DatosJugador d = plugin.almacen().de(p);
        ClaseRpg c = ClaseRpg.de(d.clase);
        Menu m = new Menu(3, "<dark_gray>Elegí tu tipo de daño");
        for (int i = 0; i < 2; i++) {
            TipoDanio t = c.tipos.get(i);
            Habilidades.Variante v = i == 0 ? Habilidades.Variante.DANIO_A : Habilidades.Variante.DANIO_B;
            Habilidades.Def q = Habilidades.defQ(c, v), u = Habilidades.defUlt(c, v);
            ItemStack it = Util.item(i == 0 ? Material.BLAZE_POWDER : Material.PRISMARINE_CRYSTALS, "<red><bold>" + t.nombre,
                    "<gray>Tus golpes acumulan " + t.nombre.toLowerCase() + ".",
                    "<aqua>Q (nivel 3): <white>" + q.nombre(), "<gray>" + q.desc(),
                    "<gold>Definitiva (nivel 5): <white>" + u.nombre(), "<gray>" + u.desc(),
                    "", "<yellow>Clic para elegir (se cambia con la Lágrima)");
            m.poner(i == 0 ? 11 : 15, it, pl -> {
                DatosJugador dd = plugin.almacen().de(pl);
                if (dd.tipoDanio != null || brasasLibres(dd) < Rama.COSTO[0]) return;
                dd.tipoDanio = t.name();
                dd.arbol.put(Rama.DANIO, 1);
                pl.playSound(pl, Sound.ITEM_FIRECHARGE_USE, 1f, 0.8f);
                abrir(pl);
            });
        }
        m.abrir(p);
    }

    private void elegirQ(Player p, Rama r) {
        DatosJugador d = plugin.almacen().de(p);
        if (d.rama(r) < 3) return;
        d.ramaQ = r;
        p.playSound(p, Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        abrir(p);
    }

    private void elegirDefinitiva(Player p, Rama r) {
        DatosJugador d = plugin.almacen().de(p);
        if (d.rama(r) < 5) return;
        d.ramaDefinitiva = r;
        p.playSound(p, Sound.UI_BUTTON_CLICK, 1f, 1.2f);
        abrir(p);
    }

    /** Lágrima del Olvido: devuelve todas las brasas. */
    public void reiniciar(Player p) {
        DatosJugador d = plugin.almacen().de(p);
        for (Rama r : Rama.values()) d.arbol.put(r, 0);
        d.tipoDanio = null;
        d.ramaQ = null;
        d.ramaDefinitiva = null;
        d.lagrimas++;
        modo.prepararAtributos(p);
        double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
        if (p.getHealth() > max) p.setHealth(max);
        p.playSound(p, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 0.6f);
        Util.titulo(p, "<aqua><bold>OLVIDO", "<gray>Tus brasas vuelven a estar libres", 200, 2000, 600);
    }
}
