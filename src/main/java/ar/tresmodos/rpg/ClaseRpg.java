package ar.tresmodos.rpg;

import ar.tresmodos.Util;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Las seis clases del RPG: atributos iniciales, arma única, armadura y objetos de arranque. */
public enum ClaseRpg {
    CABALLERO("Caballero Ceniciento", "Equilibrado: bloqueo y parry.", Material.SHIELD,
            "Tajo ascendente", "Lanza al enemigo hacia arriba.", List.of(TipoDanio.FUEGO, TipoDanio.RAYO),
            attrs(Atributo.VIGOR, 3, Atributo.FUERZA, 3, Atributo.AGUANTE, 2, Atributo.DESTREZA, 1)),
    VERDUGO("Verdugo", "Golpes lentos que rompen la postura.", Material.IRON_AXE,
            "Grito de guerra", "+20 % de daño propio y de aliados cercanos por 15 s.", List.of(TipoDanio.SANGRADO, TipoDanio.APLASTANTE),
            attrs(Atributo.FUERZA, 5, Atributo.VIGOR, 2, Atributo.AGUANTE, 1)),
    RONIN("Ronin", "Parry y velocidad; la katana hace sangrar.", Material.IRON_SWORD,
            "Desenvaine", "Tajo instantáneo a 4 bloques.", List.of(TipoDanio.SANGRADO, TipoDanio.RAYO),
            attrs(Atributo.DESTREZA, 5, Atributo.AGUANTE, 2, Atributo.VIGOR, 1)),
    CAZADOR("Cazador de Bestias", "Distancia y movilidad.", Material.BOW,
            "Lluvia de flechas", "Flechas en un área de 5 bloques.", List.of(TipoDanio.ROBO_VIDA, TipoDanio.VENENO),
            attrs(Atributo.DESTREZA, 4, Atributo.AGUANTE, 2, Atributo.VIGOR, 2)),
    HECHICERO("Hechicero del Vacío", "Hechizos a distancia.", Material.BLAZE_ROD,
            "Orbe gravitatorio", "Atrae y aplasta a los enemigos.", List.of(TipoDanio.VACIO, TipoDanio.HIELO),
            attrs(Atributo.INTELIGENCIA, 5, Atributo.MENTE, 3, Atributo.VIGOR, 1)),
    CLERIGO("Clérigo de la Llama", "Curación y apoyo.", Material.MACE,
            "Llamarada sagrada", "Quema a los enemigos alrededor.", List.of(TipoDanio.FUEGO_SAGRADO, TipoDanio.RAYO),
            attrs(Atributo.FE, 5, Atributo.VIGOR, 2, Atributo.MENTE, 1));

    /** Tipos de daño de la rama Daño (cada clase elige uno de dos). */
    public enum TipoDanio {
        FUEGO("Fuego"), RAYO("Rayo"), SANGRADO("Sangrado"), APLASTANTE("Aplastante"), ROBO_VIDA("Robo de vida"),
        VENENO("Veneno"), VACIO("Vacío"), HIELO("Hielo"), FUEGO_SAGRADO("Fuego sagrado");

        public final String nombre;

        TipoDanio(String nombre) {
            this.nombre = nombre;
        }
    }

    public final String nombre, estilo, qBase, qDesc;
    public final Material icono;
    public final List<TipoDanio> tipos;
    public final Map<Atributo, Integer> atributos;

    ClaseRpg(String nombre, String estilo, Material icono, String qBase, String qDesc, List<TipoDanio> tipos,
             Map<Atributo, Integer> atributos) {
        this.nombre = nombre;
        this.estilo = estilo;
        this.icono = icono;
        this.qBase = qBase;
        this.qDesc = qDesc;
        this.tipos = tipos;
        this.atributos = atributos;
    }

    private static Map<Atributo, Integer> attrs(Object... pares) {
        Map<Atributo, Integer> m = new EnumMap<>(Atributo.class);
        for (Atributo a : Atributo.values()) m.put(a, 0);
        for (int i = 0; i < pares.length; i += 2) m.put((Atributo) pares[i], (Integer) pares[i + 1]);
        return m;
    }

    public static ClaseRpg de(String s) {
        if (s == null) return null;
        try {
            return valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Arma, armadura y objetos con los que arranca la clase (no incluye los frascos). */
    public void darKit(Player p) {
        PlayerInventory inv = p.getInventory();
        switch (this) {
            case CABALLERO -> {
                inv.setItem(0, ArmaRpg.ESPADA_BASTARDA.crear(0));
                inv.setItemInOffHand(escudo("Escudo de cometa", Color.fromRGB(120, 30, 30)));
                armadura(inv, Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS,
                        null, TrimPattern.SENTRY, TrimMaterial.REDSTONE);
                inv.addItem(ObjetosRpg.bombaFuego(2), ObjetosRpg.hierba(1));
            }
            case VERDUGO -> {
                inv.setItem(0, ArmaRpg.GRAN_HACHA.crear(0));
                armadura(inv, Material.CHAINMAIL_HELMET, Material.IRON_CHESTPLATE, Material.CHAINMAIL_LEGGINGS,
                        Material.CHAINMAIL_BOOTS, null, TrimPattern.RIB, TrimMaterial.NETHERITE);
                inv.addItem(ObjetosRpg.cuchillos(5), ObjetosRpg.resina(ObjetosRpg.Resina.FUEGO, 1));
            }
            case RONIN -> {
                inv.setItem(0, ArmaRpg.KATANA.crear(0));
                armadura(inv, Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS,
                        Material.LEATHER_BOOTS, Color.fromRGB(40, 44, 70), TrimPattern.WAYFINDER, TrimMaterial.GOLD);
                inv.addItem(ObjetosRpg.resina(ObjetosRpg.Resina.RAYO, 2), ObjetosRpg.cuchillos(5));
            }
            case CAZADOR -> {
                inv.setItem(0, ArmaRpg.DAGAS_GEMELAS.crear(0));
                inv.setItem(1, ArmaRpg.ARCO_LARGO.crear(0));
                armadura(inv, Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS,
                        Material.LEATHER_BOOTS, Color.fromRGB(90, 62, 38), TrimPattern.WILD, TrimMaterial.EMERALD);
                inv.addItem(new ItemStack(Material.ARROW, 48), ObjetosRpg.bombaFuego(2));
            }
            case HECHICERO -> {
                inv.setItem(0, ArmaRpg.BASTON_HUESO.crear(0));
                inv.setItem(1, ArmaRpg.DAGA.crear(0));
                armadura(inv, Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS,
                        Material.LEATHER_BOOTS, Color.fromRGB(46, 30, 66), TrimPattern.VEX, TrimMaterial.AMETHYST);
                inv.addItem(ObjetosRpg.ceniza(1), ObjetosRpg.hierba(1));
            }
            case CLERIGO -> {
                inv.setItem(0, ArmaRpg.MAZA.crear(0));
                inv.setItem(1, ArmaRpg.TALISMAN.crear(0));
                inv.setItemInOffHand(escudo("Escudo pequeño", Color.fromRGB(200, 170, 80)));
                armadura(inv, Material.CHAINMAIL_HELMET, Material.LEATHER_CHESTPLATE, Material.CHAINMAIL_LEGGINGS,
                        Material.LEATHER_BOOTS, Color.fromRGB(220, 210, 180), TrimPattern.SPIRE, TrimMaterial.GOLD);
                inv.addItem(ObjetosRpg.hierba(2));
            }
        }
    }

    private static ItemStack escudo(String nombre, Color color) {
        ItemStack it = Util.item(Material.SHIELD, "<white>" + nombre, "Clic derecho justo antes del golpe: parry.");
        ItemMeta meta = it.getItemMeta();
        meta.setUnbreakable(true);
        it.setItemMeta(meta);
        return it;
    }

    private static void armadura(PlayerInventory inv, Material casco, Material pecho, Material piernas, Material botas,
                                 Color cuero, TrimPattern patron, TrimMaterial material) {
        inv.setHelmet(pieza(casco, cuero, patron, material));
        inv.setChestplate(pieza(pecho, cuero, patron, material));
        inv.setLeggings(pieza(piernas, cuero, patron, material));
        inv.setBoots(pieza(botas, cuero, patron, material));
    }

    private static ItemStack pieza(Material m, Color cuero, TrimPattern patron, TrimMaterial material) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setUnbreakable(true);
        if (meta instanceof LeatherArmorMeta lm && cuero != null) lm.setColor(cuero);
        if (meta instanceof ArmorMeta am) am.setTrim(new ArmorTrim(material, patron));
        it.setItemMeta(meta);
        Armaduras.actualizar(it);
        return it;
    }
}
