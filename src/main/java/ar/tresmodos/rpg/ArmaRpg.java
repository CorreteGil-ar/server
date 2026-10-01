package ar.tresmodos.rpg;

import ar.tresmodos.Claves;
import ar.tresmodos.DatosJugador;
import ar.tresmodos.Util;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Armas del RPG. Cada una escala con uno o dos atributos (letra A–E) y pide un mínimo: si no lo
 * alcanzás pega la mitad. Las mejoras (+0 a +10) suman un 8 % de daño cada una.
 */
public enum ArmaRpg {
    // Armas de clase
    ESPADA_BASTARDA("Espada bastarda", Material.IRON_SWORD, 7, 1.5, 6, 22, null, 0, false, false,
            esc(Atributo.FUERZA, 'C', Atributo.DESTREZA, 'D'), req(Atributo.FUERZA, 3)),
    GRAN_HACHA("Gran hacha del verdugo", Material.IRON_AXE, 12, 0.85, 12, 48, Estado.SANGRADO, 10, false, true,
            esc(Atributo.FUERZA, 'B'), req(Atributo.FUERZA, 5)),
    KATANA("Katana del ronin", Material.IRON_SWORD, 6, 1.9, 5, 12, Estado.SANGRADO, 26, true, false,
            esc(Atributo.DESTREZA, 'A', Atributo.FUERZA, 'D'), req(Atributo.DESTREZA, 4)),
    DAGAS_GEMELAS("Dagas gemelas", Material.IRON_SWORD, 4, 2.7, 2, 6, Estado.SANGRADO, 12, false, false,
            esc(Atributo.DESTREZA, 'B'), req(Atributo.DESTREZA, 2)),
    ARCO_LARGO("Arco largo", Material.BOW, 7, 1.0, 4, 8, null, 0, false, true,
            esc(Atributo.DESTREZA, 'B'), req(Atributo.DESTREZA, 3)),
    BASTON_HUESO("Bastón de hueso", Material.BLAZE_ROD, 3, 1.2, 3, 6, null, 0, false, false,
            esc(Atributo.INTELIGENCIA, 'A'), req(Atributo.INTELIGENCIA, 4)),
    DAGA("Daga", Material.IRON_SWORD, 4, 2.4, 1, 5, Estado.SANGRADO, 8, true, false,
            esc(Atributo.DESTREZA, 'C'), req()),
    MAZA("Maza del clérigo", Material.MACE, 8, 1.2, 7, 30, null, 0, false, false,
            esc(Atributo.FUERZA, 'C', Atributo.FE, 'C'), req(Atributo.FUERZA, 3, Atributo.FE, 3)),
    TALISMAN("Talismán de la llama", Material.HEART_OF_THE_SEA, 1, 1.0, 1, 0, null, 0, false, false,
            esc(Atributo.FE, 'A'), req(Atributo.FE, 4)),
    // Armas comunes (botín del mundo)
    ESPADA_CORTA("Espada corta", Material.STONE_SWORD, 6, 1.7, 3, 14, null, 0, false, false,
            esc(Atributo.FUERZA, 'D', Atributo.DESTREZA, 'C'), req()),
    LANZA("Lanza", Material.IRON_SPEAR, 7, 1.3, 5, 16, null, 0, false, false,
            esc(Atributo.DESTREZA, 'C', Atributo.FUERZA, 'D'), req(Atributo.DESTREZA, 2)),
    ESTOQUE("Estoque", Material.IRON_SWORD, 5, 2.2, 3, 8, Estado.SANGRADO, 18, true, false,
            esc(Atributo.DESTREZA, 'B'), req(Atributo.DESTREZA, 4)),
    ALABARDA("Alabarda", Material.NETHERITE_AXE, 10, 0.95, 10, 36, null, 0, false, true,
            esc(Atributo.FUERZA, 'C', Atributo.DESTREZA, 'C'), req(Atributo.FUERZA, 4, Atributo.DESTREZA, 3)),
    GUADANIA("Guadaña", Material.NETHERITE_HOE, 9, 1.1, 8, 20, Estado.SANGRADO, 30, false, true,
            esc(Atributo.DESTREZA, 'B', Atributo.FE, 'D'), req(Atributo.DESTREZA, 5)),
    MARTILLO("Martillo de guerra", Material.IRON_AXE, 11, 0.9, 11, 55, null, 0, false, false,
            esc(Atributo.FUERZA, 'B'), req(Atributo.FUERZA, 6)),
    BALLESTA("Ballesta pesada", Material.CROSSBOW, 9, 0.8, 6, 10, null, 0, false, true,
            esc(), req(Atributo.FUERZA, 3)),
    // Armas de jefe (se canjean por el alma en el Santuario)
    GRAN_MAZA_GLOTON("Gran maza del Glotón", Material.MACE, 15, 0.8, 16, 70, null, 0, false, true,
            esc(Atributo.FUERZA, 'A'), req(Atributo.FUERZA, 8)),
    ESPADA_ABISMO("Espada del Abismo", Material.NETHERITE_SWORD, 11, 1.4, 8, 28, Estado.LOCURA, 15, true, false,
            esc(Atributo.FUERZA, 'C', Atributo.DESTREZA, 'C', Atributo.INTELIGENCIA, 'D'), req(Atributo.FUERZA, 5, Atributo.DESTREZA, 5)),
    LANZA_FARO("Lanza del Faro", Material.NETHERITE_SPEAR, 12, 1.2, 9, 24, Estado.FRIO, 30, false, false,
            esc(Atributo.DESTREZA, 'B', Atributo.FE, 'C'), req(Atributo.DESTREZA, 6));

    public final String nombre;
    public final Material material;
    public final double danio, velocidad, peso, postura;
    public final Estado estado;
    public final double acumula;
    /** Se puede hacer parry con clic derecho (armas con guardia, como la katana). */
    public final boolean parry;
    public final boolean dosManos;
    public final Map<Atributo, Character> escalado;
    public final Map<Atributo, Integer> requisitos;

    ArmaRpg(String nombre, Material material, double danio, double velocidad, double peso, double postura,
            Estado estado, double acumula, boolean parry, boolean dosManos,
            Map<Atributo, Character> escalado, Map<Atributo, Integer> requisitos) {
        this.nombre = nombre;
        this.material = material;
        this.danio = danio;
        this.velocidad = velocidad;
        this.peso = peso;
        this.postura = postura;
        this.estado = estado;
        this.acumula = acumula;
        this.parry = parry;
        this.dosManos = dosManos;
        this.escalado = escalado;
        this.requisitos = requisitos;
    }

    private static Map<Atributo, Character> esc(Object... pares) {
        Map<Atributo, Character> m = new EnumMap<>(Atributo.class);
        for (int i = 0; i < pares.length; i += 2) m.put((Atributo) pares[i], (Character) pares[i + 1]);
        return m;
    }

    private static Map<Atributo, Integer> req(Object... pares) {
        Map<Atributo, Integer> m = new EnumMap<>(Atributo.class);
        for (int i = 0; i < pares.length; i += 2) m.put((Atributo) pares[i], (Integer) pares[i + 1]);
        return m;
    }

    static double coeficiente(char letra) {
        return switch (letra) {
            case 'S' -> 1.25;
            case 'A' -> 1.0;
            case 'B' -> 0.75;
            case 'C' -> 0.5;
            case 'D' -> 0.25;
            default -> 0.1;
        };
    }

    /** Multiplicador por atributos y requisitos del portador (el daño base lo pone el ítem). */
    public double multiplicador(DatosJugador d) {
        for (Map.Entry<Atributo, Integer> r : requisitos.entrySet()) {
            if (d.atributo(r.getKey()) < r.getValue()) return 0.5;
        }
        double m = 1;
        for (Map.Entry<Atributo, Character> e : escalado.entrySet()) {
            m += coeficiente(e.getValue()) * d.atributo(e.getKey()) * 0.05;
        }
        return m;
    }

    public boolean cumpleRequisitos(DatosJugador d) {
        for (Map.Entry<Atributo, Integer> r : requisitos.entrySet()) {
            if (d.atributo(r.getKey()) < r.getValue()) return false;
        }
        return true;
    }

    // ------------------------------------------------------------------ ítems

    public static final NamespacedKey CLAVE_DANIO = new NamespacedKey("tresmodos", "rpg_danio");
    public static final NamespacedKey CLAVE_VELOCIDAD = new NamespacedKey("tresmodos", "rpg_velocidad");

    public ItemStack crear(int mejora) {
        ItemStack it = new ItemStack(material);
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(Claves.ARMA_RPG, PersistentDataType.STRING, name());
        meta.getPersistentDataContainer().set(Claves.MEJORA, PersistentDataType.INTEGER, mejora);
        meta.setUnbreakable(true);
        double d = danio * (1 + 0.08 * mejora);
        if (material != Material.BOW && material != Material.CROSSBOW) {
            meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);
            meta.removeAttributeModifier(Attribute.ATTACK_SPEED);
            meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(CLAVE_DANIO, d - 1,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
            meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(CLAVE_VELOCIDAD, velocidad - 4,
                    AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
        meta.displayName(Util.mmItem("<white><bold>" + nombre + (mejora > 0 ? " +" + mejora : "")));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(Util.mmItem("<gray>Daño <white>" + Math.round(d * 10) / 10.0 + " <gray>· Peso <white>" + peso
                + " <gray>· Postura <white>" + Math.round(postura)));
        if (!escalado.isEmpty()) {
            StringBuilder sb = new StringBuilder("<gray>Escala:");
            for (Map.Entry<Atributo, Character> e : escalado.entrySet()) {
                sb.append(" ").append(e.getKey().color).append(e.getKey().abreviatura()).append(" ").append(e.getValue());
            }
            lore.add(Util.mmItem(sb.toString()));
        }
        if (!requisitos.isEmpty()) {
            StringBuilder sb = new StringBuilder("<gray>Requiere:");
            for (Map.Entry<Atributo, Integer> e : requisitos.entrySet()) {
                sb.append(" ").append(e.getKey().abreviatura()).append(" ").append(e.getValue());
            }
            lore.add(Util.mmItem(sb.toString()));
        }
        if (estado != null) lore.add(Util.mmItem("<dark_red>Acumula " + estado.nombre.toLowerCase() + " (" + Math.round(acumula) + ")"));
        if (parry) lore.add(Util.mmItem("<aqua>Clic derecho: parry"));
        if (dosManos) lore.add(Util.mmItem("<gray>A dos manos"));
        meta.lore(lore);
        it.setItemMeta(meta);
        return it;
    }

    public static ArmaRpg de(ItemStack it) {
        String s = Util.marca(it, Claves.ARMA_RPG);
        if (s == null) return null;
        try {
            return valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static int mejora(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return 0;
        Integer m = it.getItemMeta().getPersistentDataContainer().get(Claves.MEJORA, PersistentDataType.INTEGER);
        return m == null ? 0 : m;
    }
}
