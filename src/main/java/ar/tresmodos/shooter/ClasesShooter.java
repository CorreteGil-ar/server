package ar.tresmodos.shooter;

import ar.tresmodos.Armas;
import ar.tresmodos.Claves;
import ar.tresmodos.DatosJugador;
import ar.tresmodos.Menu;
import ar.tresmodos.Modo;
import ar.tresmodos.TresModos;
import ar.tresmodos.Util;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

/**
 * Clases personalizadas del Shooter: 5 por jugador, cada una con principal, secundaria, letal,
 * táctico y una ventaja por cada uno de los 3 slots. Se editan en el menú y se aplican al reaparecer.
 */
public class ClasesShooter {

    public enum Letal {
        FRAG("Granada de fragmentación", "Mantené clic derecho para cocinarla; explota a los 3 s.", "granada"),
        SEMTEX("Semtex", "Se pega a lo primero que toca y explota.", "semtex"),
        HACHA("Hacha arrojadiza", "Mata de un impacto. Se recupera del piso.", "hacha"),
        CLAYMORE("Claymore", "Mina direccional: explota si alguien pasa por delante.", "claymore");

        public final String nombre, desc, modelo;

        Letal(String nombre, String desc, String modelo) {
            this.nombre = nombre;
            this.desc = desc;
            this.modelo = modelo;
        }
    }

    public enum Tactico {
        ATURDIDORA("Aturdidora", "Lentitud y mareo durante 2 s.", "aturdidora"),
        HUMO("Humo", "Nube que tapa la visión durante 6 s.", "humo"),
        SENSOR("Sensor de latidos", "En la mano, muestra a los enemigos cercanos.", "sensor");

        public final String nombre, desc, modelo;

        Tactico(String nombre, String desc, String modelo) {
            this.nombre = nombre;
            this.desc = desc;
            this.modelo = modelo;
        }
    }

    public enum Ventaja {
        PRESTIDIGITACION(1, "Prestidigitación", "Recargás un 40 % más rápido.", Material.FEATHER),
        CARRONERO(1, "Carroñero", "Juntás munición de los caídos.", Material.BONE),
        LIGERO(1, "Ligero", "+7 % de velocidad.", Material.SUGAR),
        FANTASMA(2, "Fantasma", "Invisible al UAV y al sensor.", Material.PHANTOM_MEMBRANE),
        ENDURECIDO(2, "Endurecido", "Las balas atraviesan más y pierden menos daño.", Material.IRON_INGOT),
        INTRANSIGENTE(2, "Intransigente", "Las rachas cuestan una baja menos (no la bomba).", Material.REDSTONE),
        RASTREADOR(3, "Rastreador", "Ves las huellas de los enemigos.", Material.LEATHER_BOOTS),
        COMANDO(3, "Comando", "Más alcance del cuchillo (F).", Material.IRON_SWORD),
        MANO_FIRME(3, "Mano firme", "Menos dispersión desde la cadera.", Material.TARGET);

        public final int slot;
        public final String nombre, desc;
        public final Material icono;

        Ventaja(int slot, String nombre, String desc, Material icono) {
            this.slot = slot;
            this.nombre = nombre;
            this.desc = desc;
            this.icono = icono;
        }

        static List<Ventaja> delSlot(int slot) {
            return Arrays.stream(values()).filter(v -> v.slot == slot).toList();
        }
    }

    public static final List<Armas.Tipo> PRINCIPALES = Arrays.stream(Armas.Tipo.values()).filter(t -> !t.secundaria).toList();
    public static final List<Armas.Tipo> SECUNDARIAS = Arrays.stream(Armas.Tipo.values()).filter(t -> t.secundaria).toList();

    public record Clase(Armas.Tipo principal, Armas.Tipo secundaria, Letal letal, Tactico tactico,
                        Ventaja v1, Ventaja v2, Ventaja v3) {

        public String codigo() {
            return String.join(",", principal.name(), secundaria.name(), letal.name(), tactico.name(),
                    v1.name(), v2.name(), v3.name());
        }

        static Clase de(String codigo, Clase defecto) {
            if (codigo == null) return defecto;
            try {
                String[] p = codigo.split(",");
                Clase c = new Clase(Armas.Tipo.valueOf(p[0]), Armas.Tipo.valueOf(p[1]), Letal.valueOf(p[2]),
                        Tactico.valueOf(p[3]), Ventaja.valueOf(p[4]), Ventaja.valueOf(p[5]), Ventaja.valueOf(p[6]));
                return c.valida() ? c : defecto;
            } catch (RuntimeException e) {
                return defecto;
            }
        }

        boolean valida() {
            return !principal.secundaria && secundaria.secundaria && v1.slot == 1 && v2.slot == 2 && v3.slot == 3;
        }

        public boolean tiene(Ventaja v) {
            return v1 == v || v2 == v || v3 == v;
        }

        Clase con(int campo, Object valor) {
            return new Clase(
                    campo == 0 ? (Armas.Tipo) valor : principal, campo == 1 ? (Armas.Tipo) valor : secundaria,
                    campo == 2 ? (Letal) valor : letal, campo == 3 ? (Tactico) valor : tactico,
                    campo == 4 ? (Ventaja) valor : v1, campo == 5 ? (Ventaja) valor : v2, campo == 6 ? (Ventaja) valor : v3);
        }
    }

    static final String[] NOMBRES = {"Asalto", "Subfusil", "Escopetero", "Francotirador", "Libre"};
    static final Clase[] DEFECTO = {
            new Clase(Armas.Tipo.M4A1, Armas.Tipo.PISTOLA, Letal.FRAG, Tactico.ATURDIDORA,
                    Ventaja.LIGERO, Ventaja.INTRANSIGENTE, Ventaja.MANO_FIRME),
            new Clase(Armas.Tipo.MP5, Armas.Tipo.PISTOLA, Letal.SEMTEX, Tactico.HUMO,
                    Ventaja.LIGERO, Ventaja.FANTASMA, Ventaja.COMANDO),
            new Clase(Armas.Tipo.ESCOPETA, Armas.Tipo.PISTOLA, Letal.HACHA, Tactico.ATURDIDORA,
                    Ventaja.CARRONERO, Ventaja.ENDURECIDO, Ventaja.COMANDO),
            new Clase(Armas.Tipo.FRANCOTIRADOR, Armas.Tipo.PISTOLA, Letal.CLAYMORE, Tactico.SENSOR,
                    Ventaja.PRESTIDIGITACION, Ventaja.FANTASMA, Ventaja.RASTREADOR),
            new Clase(Armas.Tipo.M4A1, Armas.Tipo.PISTOLA, Letal.FRAG, Tactico.HUMO,
                    Ventaja.PRESTIDIGITACION, Ventaja.ENDURECIDO, Ventaja.RASTREADOR),
    };

    private static final NamespacedKey LIGERO_KEY = new NamespacedKey("tresmodos", "ventaja_ligero");

    private final TresModos plugin;
    private final ModoShooter modo;

    ClasesShooter(TresModos plugin, ModoShooter modo) {
        this.plugin = plugin;
        this.modo = modo;
    }

    public Clase clase(Player p, int i) {
        return Clase.de(plugin.almacen().de(p).shooterClases[i], DEFECTO[i]);
    }

    public Clase actual(Player p) {
        return clase(p, plugin.almacen().de(p).shooterClase);
    }

    public boolean tiene(Player p, Ventaja v) {
        return Modo.de(p.getWorld()) == Modo.SHOOTER && actual(p).tiene(v);
    }

    // ------------------------------------------------------------------ equipo

    /** Inventario de una vida: armas con munición de reserva, letal, táctico, minimapa y libro. */
    public void darEquipo(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        Clase c = actual(p);
        DatosJugador d = plugin.almacen().de(p);
        ItemStack principal = Armas.crear(c.principal(), d.accesorios(c.principal()));
        Armas.setReservaItem(principal, Armas.capacidad(c.principal(), d.accesorios(c.principal())) * 3);
        ItemStack secundaria = Armas.crear(c.secundaria(), d.accesorios(c.secundaria()));
        Armas.setReservaItem(secundaria, Armas.capacidad(c.secundaria(), d.accesorios(c.secundaria())) * 3);
        inv.setItem(0, principal);
        inv.setItem(1, secundaria);
        inv.setItem(2, modo.equipamiento().letal(c.letal(), c.letal() == Letal.FRAG ? 2 : 1));
        inv.setItem(3, modo.equipamiento().tactico(c.tactico(), c.tactico() == Tactico.SENSOR ? 1 : 2));
        inv.setItem(8, Util.marcar(Util.item(Material.BOOK, "<yellow><bold>Clases y opciones",
                "Elegir y editar clases, armero", "o volver al lobby."), Claves.CLASE_MENU, "1"));
        inv.setItemInOffHand(modo.minimapa().item());
        inv.setHeldItemSlot(0);

        AttributeInstance vel = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (vel != null) {
            vel.removeModifier(LIGERO_KEY);
            if (c.tiene(Ventaja.LIGERO)) {
                vel.addModifier(new AttributeModifier(LIGERO_KEY, 0.07, AttributeModifier.Operation.ADD_SCALAR,
                        EquipmentSlotGroup.ANY));
            }
        }
        p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
        p.setFoodLevel(20);
    }

    // ------------------------------------------------------------------ menús

    public void abrirMenu(Player p) {
        DatosJugador d = plugin.almacen().de(p);
        Menu m = new Menu(4, "<dark_gray>Clases · se aplican al reaparecer");
        for (int i = 0; i < 5; i++) {
            final int idx = i;
            Clase c = clase(p, i);
            boolean actual = d.shooterClase == i;
            ItemStack icono = Util.item(c.principal().material, (actual ? "<green><bold>" : "<aqua><bold>")
                            + NOMBRES[i] + (actual ? " <green>(en uso)" : ""),
                    "Principal: " + c.principal().nombre, "Secundaria: " + c.secundaria().nombre,
                    "Letal: " + c.letal().nombre, "Táctico: " + c.tactico().nombre,
                    "Ventajas: " + c.v1().nombre + ", " + c.v2().nombre + ", " + c.v3().nombre, "",
                    "<yellow>Clic: usar esta clase", "<gold>El yunque de abajo la edita");
            ItemMeta meta = icono.getItemMeta();
            meta.setItemModel(new org.bukkit.NamespacedKey("tresmodos", c.principal().modelo));
            icono.setItemMeta(meta);
            m.poner(9 + i * 2, icono, pl -> elegir(pl, idx));
            m.poner(18 + i * 2, Util.item(Material.ANVIL, "<gold>Editar " + NOMBRES[i]), pl -> editar(pl, idx));
        }
        m.poner(30, Util.item(Material.SMITHING_TABLE, "<gold><bold>Armero",
                "Accesorios del arma de la mano o", "de la primera arma de la barra."), pl -> plugin.armero().abrir(pl, 0));
        m.poner(32, Util.item(Material.OAK_DOOR, "<white>Volver al lobby"), pl -> {
            pl.closeInventory();
            plugin.cambio().cambiar(pl, Modo.LOBBY, false);
        });
        m.abrir(p);
    }

    private void elegir(Player p, int i) {
        plugin.almacen().de(p).shooterClase = i;
        p.playSound(p, Sound.ITEM_ARMOR_EQUIP_IRON, 0.8f, 1.2f);
        if (modo.puedeCambiarYa(p)) {
            darEquipo(p);
            Util.msg(p, "<green>Clase " + NOMBRES[i] + " equipada.");
            p.closeInventory();
        } else {
            Util.msg(p, "<green>Clase " + NOMBRES[i] + " elegida. <gray>Se aplica al reaparecer.");
            abrirMenu(p);
        }
    }

    private void editar(Player p, int i) {
        Clase c = clase(p, i);
        Menu m = new Menu(3, "<dark_gray>Editar " + NOMBRES[i]);
        m.poner(10, Util.item(c.principal().material, "<aqua><bold>Principal", c.principal().nombre, "<yellow>Clic: cambiar"),
                pl -> ciclo(pl, i, 0, PRINCIPALES, c.principal()));
        m.poner(11, Util.item(c.secundaria().material, "<aqua><bold>Secundaria", c.secundaria().nombre, "<yellow>Clic: cambiar"),
                pl -> ciclo(pl, i, 1, SECUNDARIAS, c.secundaria()));
        m.poner(12, Util.item(Material.TNT, "<red><bold>Letal", c.letal().nombre, c.letal().desc, "<yellow>Clic: cambiar"),
                pl -> ciclo(pl, i, 2, List.of(Letal.values()), c.letal()));
        m.poner(13, Util.item(Material.GUNPOWDER, "<blue><bold>Táctico", c.tactico().nombre, c.tactico().desc, "<yellow>Clic: cambiar"),
                pl -> ciclo(pl, i, 3, List.of(Tactico.values()), c.tactico()));
        Ventaja[] vs = {c.v1(), c.v2(), c.v3()};
        for (int s = 1; s <= 3; s++) {
            final int slot = s;
            Ventaja v = vs[s - 1];
            m.poner(14 + s, Util.item(v.icono, "<light_purple><bold>Ventaja " + s, v.nombre, v.desc, "<yellow>Clic: cambiar"),
                    pl -> ciclo(pl, i, 3 + slot, Ventaja.delSlot(slot), v));
        }
        m.poner(22, Util.item(Material.ARROW, "<white>Volver"), this::abrirMenu);
        m.abrir(p);
    }

    private <T> void ciclo(Player p, int i, int campo, List<T> opciones, T actual) {
        T sig = opciones.get((opciones.indexOf(actual) + 1) % opciones.size());
        Clase nueva = clase(p, i).con(campo, sig);
        plugin.almacen().de(p).shooterClases[i] = nueva.codigo();
        p.playSound(p, Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
        editar(p, i);
    }
}
