package ar.tresmodos.rpg;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.CaveSpider;
import org.bukkit.entity.Creaking;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.Evoker;
import org.bukkit.entity.Hoglin;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Spider;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.entity.Wolf;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;

import java.util.function.Consumer;

/** Los enemigos de Las Tierras Cenicientas, hechos con mobs vanilla, equipo y atributos propios. */
public enum TipoEnemigo {
    HUECO("Hueco", Zombie.class, 30, 4, 40, 1, e -> {
        Zombie z = (Zombie) e;
        z.setAdult();
        z.setShouldBurnInDay(false);
        oxidado(z.getEquipment(), modelo(Material.STONE_SWORD, "rpg_espada_corta"), "capucha_hueca");
    }),
    HUECO_LANCERO("Hueco lancero", Zombie.class, 32, 5, 45, 1, e -> {
        Zombie z = (Zombie) e;
        z.setAdult();
        z.setShouldBurnInDay(false);
        oxidado(z.getEquipment(), new ItemStack(Material.IRON_SPEAR), "yelmo_lancero");
    }),
    HUECO_ARQUERO("Hueco arquero", Skeleton.class, 24, 3, 45, 1, e -> {
        Skeleton s = (Skeleton) e;
        s.setShouldBurnInDay(false);
        oxidado(s.getEquipment(), new ItemStack(Material.BOW), "capucha_arquero");
    }),
    PERRO("Perro de la plaga", Wolf.class, 16, 3, 25, 3, e -> {
        Wolf w = (Wolf) e;
        w.setAdult();
        w.setVariant(Wolf.Variant.BLACK);
        w.setAngry(true);
        escala(e, 0.9);
    }),
    CUERVO("Cuervo carroñero", Phantom.class, 12, 3, 30, 2, e -> {
        ((Phantom) e).setShouldBurnInDay(false);
        escala(e, 0.55);
    }),
    ACECHADOR("Acechador", Spider.class, 34, 5, 70, 1, e -> {
        escala(e, 1.4);
        base(e, Attribute.MOVEMENT_SPEED, 0.38);
    }),
    TEJEDORA("Araña tejedora", CaveSpider.class, 30, 4, 80, 1, e -> escala(e, 1.6)),
    BESTIA("Bestia de carne", Hoglin.class, 70, 8, 160, 1, e -> {
        Hoglin h = (Hoglin) e;
        h.setAdult();
        h.setImmuneToZombification(true);
        h.setIsAbleToBeHunted(false);
        escala(e, 1.15);
    }),
    CABALLERO("Caballero caído", WitherSkeleton.class, 60, 7, 200, 1, e -> {
        EntityEquipment eq = e.getEquipment();
        eq.setHelmet(modelo(Material.PAPER, "yelmo_caido"));
        eq.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
        eq.setLeggings(new ItemStack(Material.CHAINMAIL_LEGGINGS));
        eq.setItemInMainHand(modelo(Material.IRON_SWORD, "rpg_espada_bastarda"));
        eq.setItemInOffHand(new ItemStack(Material.SHIELD));
        sinBotin(eq);
        escala(e, 0.85);
    }),
    GARGOLA("Gárgola", Phantom.class, 40, 6, 180, 1, e -> {
        ((Phantom) e).setShouldBurnInDay(false);
        escala(e, 1.3);
    }),
    PALIDO("Pálido", Creaking.class, 60, 8, 300, 1, e -> escala(e, 1.1)),
    AHOGADO("Ahogado", Drowned.class, 45, 7, 220, 2, e -> {
        Drowned d = (Drowned) e;
        d.setAdult();
        d.setShouldBurnInDay(false);
        d.getEquipment().setItemInMainHand(new ItemStack(Math.random() < 0.5 ? Material.TRIDENT : Material.IRON_SPEAR));
        d.getEquipment().setHelmet(modelo(Material.PAPER, "yelmo_ahogado"));
        sinBotin(d.getEquipment());
    }),
    VASTAGO("Vástago del Durmiente", Evoker.class, 90, 6, 600, 1, e -> {
        escala(e, 1.2);
        e.getEquipment().setHelmet(modelo(Material.PAPER, "mascara_vastago"));
        sinBotin(e.getEquipment());
    });

    public final String nombre;
    public final Class<? extends LivingEntity> clase;
    public final double vida, danio;
    public final long almas;
    public final int grupo;
    public final Consumer<LivingEntity> preparar;

    TipoEnemigo(String nombre, Class<? extends LivingEntity> clase, double vida, double danio, long almas, int grupo,
                Consumer<LivingEntity> preparar) {
        this.nombre = nombre;
        this.clase = clase;
        this.vida = vida;
        this.danio = danio;
        this.almas = almas;
        this.grupo = grupo;
        this.preparar = preparar;
    }

    /** Qué enemigos aparecen en cada zona y con qué peso. */
    public static TipoEnemigo[] de(Zona z) {
        return switch (z) {
            case ALDEA -> new TipoEnemigo[]{HUECO, HUECO, HUECO, HUECO_LANCERO, HUECO_LANCERO, HUECO_ARQUERO, HUECO_ARQUERO,
                    PERRO, PERRO, CUERVO};
            case BOSQUE -> new TipoEnemigo[]{ACECHADOR, ACECHADOR, ACECHADOR, TEJEDORA, TEJEDORA, BESTIA, CUERVO, CUERVO,
                    HUECO, PERRO};
            case CIUDADELA -> new TipoEnemigo[]{CABALLERO, CABALLERO, CABALLERO, CABALLERO, GARGOLA, GARGOLA, BESTIA,
                    BESTIA, HUECO_ARQUERO, HUECO_LANCERO};
            case COSTA -> new TipoEnemigo[]{PALIDO, PALIDO, PALIDO, AHOGADO, AHOGADO, AHOGADO, AHOGADO, CUERVO,
                    CABALLERO, GARGOLA};
            case TEMPLO -> new TipoEnemigo[]{VASTAGO, VASTAGO, VASTAGO, PALIDO, AHOGADO};
            default -> new TipoEnemigo[0];
        };
    }

    static void base(LivingEntity e, Attribute a, double v) {
        AttributeInstance ai = e.getAttribute(a);
        if (ai != null) ai.setBaseValue(v);
    }

    private static void escala(LivingEntity e, double s) {
        base(e, Attribute.SCALE, s);
    }

    /** Ítem con modelo propio del paquete (cascos de herramientas/criaturas.py, armas de rpg.py). */
    static ItemStack modelo(Material m, String modelo) {
        ItemStack it = new ItemStack(m);
        org.bukkit.inventory.meta.ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new org.bukkit.NamespacedKey("tresmodos", modelo));
        it.setItemMeta(meta);
        return it;
    }

    private static void oxidado(EntityEquipment eq, ItemStack arma, String casco) {
        eq.setHelmet(modelo(Material.PAPER, casco));
        ItemStack pecho = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta m = (LeatherArmorMeta) pecho.getItemMeta();
        m.setColor(Color.fromRGB(92, 64, 44));
        pecho.setItemMeta(m);
        eq.setChestplate(pecho);
        eq.setItemInMainHand(arma);
        sinBotin(eq);
    }

    private static void sinBotin(EntityEquipment eq) {
        eq.setHelmetDropChance(0f);
        eq.setChestplateDropChance(0f);
        eq.setLeggingsDropChance(0f);
        eq.setBootsDropChance(0f);
        eq.setItemInMainHandDropChance(0f);
        eq.setItemInOffHandDropChance(0f);
    }
}
