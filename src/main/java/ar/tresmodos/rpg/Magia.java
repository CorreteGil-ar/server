package ar.tresmodos.rpg;

import ar.tresmodos.Claves;
import ar.tresmodos.DatosJugador;
import ar.tresmodos.Modo;
import ar.tresmodos.TresModos;
import ar.tresmodos.Util;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Hechicería (Inteligencia, con bastón) y milagros (Fe, con talismán). Clic derecho con el
 * catalizador lanza el hechizo elegido; Shift + clic derecho cambia de hechizo. Se aprenden leyendo
 * pergaminos (los vende el mercader; la Red Pútrida sale del alma de la Tejedora).
 */
public class Magia implements Listener {
    private static final NamespacedKey CLAVE = new NamespacedKey("tresmodos", "rpg_hechizo");

    public enum Escuela { HECHICERIA, MILAGRO, AMBAS }

    public enum Hechizo {
        SAETA_VACIO("Saeta del Vacío", "Proyectil rápido.", Escuela.HECHICERIA, 4, 8, 12),
        LANZA_VACIO("Lanza del Vacío", "Se carga 1 s y atraviesa todo.", Escuela.HECHICERIA, 10, 20, 40),
        NIEBLA_CORROSIVA("Niebla corrosiva", "Nube de veneno por 8 s.", Escuela.HECHICERIA, 8, 18, 40),
        ESCUDO_ARCANO("Escudo arcano", "−50 % de daño por 10 s.", Escuela.HECHICERIA, 6, 15, 200),
        CURACION("Curación", "Cura al grupo en 6 bloques.", Escuela.MILAGRO, 4, 20, 60),
        LANZA_SAGRADA("Lanza de fuego sagrado", "Se arroja y quema.", Escuela.MILAGRO, 8, 16, 24),
        BENDICION("Bendición", "Regeneración al grupo por 20 s.", Escuela.MILAGRO, 6, 18, 300),
        LLAMA_PURIFICADORA("Llama purificadora", "Limpia los estados del grupo.", Escuela.MILAGRO, 5, 12, 100),
        RED_PUTRIDA("Red Pútrida", "Red que frena y envenena (alma de la Tejedora).", Escuela.AMBAS, 8, 22, 60);

        public final String nombre, desc;
        public final Escuela escuela;
        public final int requisito, eter, espera;

        Hechizo(String nombre, String desc, Escuela escuela, int requisito, int eter, int espera) {
            this.nombre = nombre;
            this.desc = desc;
            this.escuela = escuela;
            this.requisito = requisito;
            this.eter = eter;
            this.espera = espera;
        }

        public Atributo atributo() {
            return escuela == Escuela.MILAGRO ? Atributo.FE : Atributo.INTELIGENCIA;
        }

        public ItemStack pergamino() {
            String color = escuela == Escuela.MILAGRO ? "<gold>" : escuela == Escuela.HECHICERIA ? "<light_purple>" : "<green>";
            ItemStack it = Util.item(Material.PAPER, color + "<bold>Pergamino: " + nombre, "<gray>" + desc,
                    "<gray>Requiere " + (escuela == Escuela.AMBAS ? "INT o FE" : atributo().abreviatura()) + " " + requisito
                            + " · " + eter + " de éter",
                    "<gray>" + (escuela == Escuela.MILAGRO ? "Se lanza con talismán" : escuela == Escuela.HECHICERIA
                            ? "Se lanza con bastón" : "Se lanza con bastón o talismán"),
                    "", "<yellow>Clic derecho para aprender");
            ItemMeta m = it.getItemMeta();
            m.getPersistentDataContainer().set(CLAVE, PersistentDataType.STRING, name());
            it.setItemMeta(m);
            return it;
        }

        public static Hechizo de(String s) {
            if (s == null) return null;
            try {
                return valueOf(s);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
    }

    private final TresModos plugin;
    private final ModoRpg modo;
    private final Map<UUID, Integer> espera = new HashMap<>();

    public Magia(TresModos plugin, ModoRpg modo) {
        this.plugin = plugin;
        this.modo = modo;
    }

    /** Los hechizos con los que arranca cada clase lanzadora. */
    public static List<Hechizo> iniciales(ClaseRpg c) {
        if (c == ClaseRpg.HECHICERO) return List.of(Hechizo.SAETA_VACIO, Hechizo.ESCUDO_ARCANO);
        if (c == ClaseRpg.CLERIGO) return List.of(Hechizo.CURACION, Hechizo.LANZA_SAGRADA);
        return List.of();
    }

    private static Escuela catalizador(ItemStack it) {
        ArmaRpg a = ArmaRpg.de(it);
        if (a == ArmaRpg.BASTON_HUESO) return Escuela.HECHICERIA;
        if (a == ArmaRpg.TALISMAN) return Escuela.MILAGRO;
        return null;
    }

    private static boolean sirve(Hechizo h, Escuela cat) {
        return h.escuela == Escuela.AMBAS || h.escuela == cat;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void alUsar(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (Modo.de(p.getWorld()) != Modo.RPG) return;
        ItemStack it = e.getItem();
        // Pergamino: aprender.
        String perg = it == null || !it.hasItemMeta() ? null : it.getItemMeta().getPersistentDataContainer().get(CLAVE, PersistentDataType.STRING);
        if (perg != null) {
            e.setCancelled(true);
            Hechizo h = Hechizo.de(perg);
            DatosJugador d = plugin.almacen().de(p);
            if (h == null) return;
            if (!d.hechizos.add(h.name())) {
                Util.barra(p, "<gray>Ya conocés " + h.nombre + ".");
                return;
            }
            if (d.hechizo == null) d.hechizo = h.name();
            it.setAmount(it.getAmount() - 1);
            p.playSound(p, Sound.ITEM_BOOK_PAGE_TURN, 1f, 0.8f);
            p.playSound(p, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.2f);
            Util.titulo(p, "", "<light_purple>Aprendiste " + h.nombre, 100, 1800, 400);
            return;
        }
        Escuela cat = catalizador(it);
        if (cat == null) return;
        e.setCancelled(true);
        DatosJugador d = plugin.almacen().de(p);
        ClaseRpg clase = ClaseRpg.de(d.clase);
        if (d.hechizos.isEmpty() && clase != null) {
            for (Hechizo h : iniciales(clase)) d.hechizos.add(h.name());
        }
        List<Hechizo> disponibles = new ArrayList<>();
        for (String s : d.hechizos) {
            Hechizo h = Hechizo.de(s);
            if (h != null && sirve(h, cat)) disponibles.add(h);
        }
        if (disponibles.isEmpty()) {
            Util.barra(p, "<gray>No conocés hechizos para este catalizador. <white>El mercader vende pergaminos.");
            return;
        }
        Hechizo actual = Hechizo.de(d.hechizo);
        if (actual == null || !disponibles.contains(actual)) {
            actual = disponibles.get(0);
            d.hechizo = actual.name();
        }
        if (p.isSneaking()) {
            Hechizo sig = disponibles.get((disponibles.indexOf(actual) + 1) % disponibles.size());
            d.hechizo = sig.name();
            Util.barra(p, "<light_purple>Hechizo: <white>" + sig.nombre + " <gray>(" + sig.eter + " éter)");
            p.playSound(p, Sound.UI_BUTTON_CLICK, 0.6f, 1.6f);
            return;
        }
        lanzar(p, actual, it);
    }

    private void lanzar(Player p, Hechizo h, ItemStack cat) {
        DatosJugador d = plugin.almacen().de(p);
        int nivelAtr = h.escuela == Escuela.AMBAS ? Math.max(d.inteligencia, d.fe) : d.atributo(h.atributo());
        if (nivelAtr < h.requisito) {
            Util.barra(p, "<red>Te falta " + (h.escuela == Escuela.AMBAS ? "INT o FE" : h.atributo().nombre) + " (" + h.requisito + ")");
            return;
        }
        int ahora = Bukkit.getCurrentTick();
        if (ahora < espera.getOrDefault(p.getUniqueId(), 0)) return;
        if (!modo.gastarEter(p, h.eter)) return;
        espera.put(p.getUniqueId(), ahora + Math.max(12, h.espera / 2));
        ArmaRpg a = ArmaRpg.de(cat);
        double poder = (a == null ? 1 : a.multiplicador(d)) * (1 + 0.08 * ArmaRpg.mejora(cat))
                * modo.buffs().valor(p, Buffs.Tipo.DANIO_MAGICO, 1.0);
        Util.barra(p, "<light_purple>" + h.nombre);
        switch (h) {
            case SAETA_VACIO -> proyectil(p, 1.6, 30, Color.fromRGB(150, 70, 255), Particle.REVERSE_PORTAL, false, false,
                    (v, l) -> danio(p, v, 8 * poder));
            case LANZA_VACIO -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 3, false, false));
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.6f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (!p.isOnline() || p.isDead()) return;
                    rayo(p, 30, Color.fromRGB(110, 40, 220), v -> danio(p, v, 18 * poder));
                    p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 0.6f, 1.6f);
                }, 20);
            }
            case NIEBLA_CORROSIVA -> {
                Location c = punto(p, 20);
                for (int i = 0; i < 16; i++) {
                    final int k = i;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        c.getWorld().spawnParticle(Particle.DUST, c.clone().add(0, 0.6, 0), 40, 2.2, 0.6, 2.2, 0,
                                new Particle.DustOptions(Color.fromRGB(90, 160, 40), 2f));
                        for (LivingEntity v : enemigos(c, 3)) {
                            modo.estados().acumular(v, Estado.VENENO, 12, p);
                            if (k % 2 == 0) danio(p, v, 1 * poder);
                        }
                    }, i * 10L);
                }
                c.getWorld().playSound(c, Sound.ENTITY_SPLASH_POTION_BREAK, 1f, 0.6f);
            }
            case ESCUDO_ARCANO -> {
                modo.buffs().dar(p, Buffs.Tipo.DEFENSA, 0.5, 200);
                p.getWorld().spawnParticle(Particle.ENCHANT, p.getLocation().add(0, 1, 0), 60, 0.5, 0.8, 0.5, 0.5);
                p.playSound(p, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.6f);
            }
            case CURACION -> {
                for (Player o : aliados(p.getLocation(), 6)) {
                    double max = o.getAttribute(Attribute.MAX_HEALTH).getValue();
                    o.setHealth(Math.min(max, o.getHealth() + max * (0.3 + 0.01 * d.fe)));
                    o.getWorld().spawnParticle(Particle.HEART, o.getLocation().add(0, 2, 0), 4, 0.3, 0.2, 0.3, 0);
                }
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.4f);
                circulo(p.getLocation(), 6, Color.fromRGB(255, 220, 120));
            }
            case LANZA_SAGRADA -> proyectil(p, 1.3, 30, Color.fromRGB(255, 180, 60), Particle.FLAME, true, false, (v, l) -> {
                danio(p, v, 14 * poder);
                v.setFireTicks(80);
            });
            case BENDICION -> {
                for (Player o : aliados(p.getLocation(), 6)) {
                    o.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 400, 0, false, true));
                }
                circulo(p.getLocation(), 6, Color.fromRGB(255, 240, 180));
                p.getWorld().playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.4f);
            }
            case LLAMA_PURIFICADORA -> {
                for (Player o : aliados(p.getLocation(), 6)) {
                    modo.estados().limpiar(o);
                    o.setFireTicks(0);
                    o.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, o.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.02);
                }
                p.getWorld().playSound(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 1.6f);
            }
            case RED_PUTRIDA -> proyectil(p, 1.1, 25, Color.fromRGB(230, 230, 230), Particle.WHITE_ASH, false, true, (v, l) -> {
                l.getWorld().spawnParticle(Particle.BLOCK, l, 60, 2, 0.5, 2, 0, Material.COBWEB.createBlockData());
                for (LivingEntity o : enemigos(l, 4)) {
                    o.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 3, false, true));
                    modo.estados().acumular(o, Estado.VENENO, 40, p);
                    danio(p, o, 4 * poder);
                }
            });
        }
    }

    // ------------------------------------------------------------------ piezas

    private interface AlImpacto {
        void en(LivingEntity v, Location l);
    }

    /** Proyectil de partículas que avanza por ticks y pega al primero que toca. */
    private void proyectil(Player p, double paso, double alcance, Color color, Particle extra, boolean cae, boolean enBloque,
                           AlImpacto alImpacto) {
        Location pos = p.getEyeLocation();
        Vector dir = pos.getDirection().normalize();
        World w = p.getWorld();
        w.playSound(pos, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 1.4f);
        Particle.DustOptions op = new Particle.DustOptions(color, 1.3f);
        final double[] recorrido = {0};
        final Vector vel = dir.multiply(paso);
        Bukkit.getScheduler().runTaskTimer(plugin, tarea -> {
            for (int sub = 0; sub < 2; sub++) {
                if (cae) vel.setY(vel.getY() - 0.01);
                RayTraceResult r = w.rayTrace(pos, vel, vel.length(), FluidCollisionMode.NEVER, true, 0.4,
                        e -> e instanceof LivingEntity le && le != p && !(le instanceof Player) && !le.isDead()
                                && !e.getPersistentDataContainer().has(Claves.INVOCACION));
                if (r != null) {
                    Location l = r.getHitPosition().toLocation(w);
                    w.spawnParticle(Particle.DUST, l, 20, 0.3, 0.3, 0.3, 0, op);
                    if (r.getHitEntity() instanceof LivingEntity v) alImpacto.en(v, l);
                    else if (enBloque) alImpacto.en(null, l);
                    tarea.cancel();
                    return;
                }
                pos.add(vel);
                recorrido[0] += vel.length();
                w.spawnParticle(Particle.DUST, pos, 3, 0.05, 0.05, 0.05, 0, op);
                w.spawnParticle(extra, pos, 2, 0.05, 0.05, 0.05, 0.01);
                if (recorrido[0] > alcance) {
                    tarea.cancel();
                    return;
                }
            }
        }, 0, 1);
    }

    private void rayo(Player p, double alcance, Color color, java.util.function.Consumer<LivingEntity> alPegar) {
        Location o = p.getEyeLocation();
        Vector dir = o.getDirection().normalize();
        RayTraceResult muro = p.getWorld().rayTraceBlocks(o, dir, alcance, FluidCollisionMode.NEVER, true);
        double largo = muro == null ? alcance : muro.getHitPosition().distance(o.toVector());
        Set<UUID> golpeados = new HashSet<>();
        Particle.DustOptions op = new Particle.DustOptions(color, 1.8f);
        for (double s = 0; s < largo; s += 0.5) {
            Location l = o.clone().add(dir.clone().multiply(s));
            p.getWorld().spawnParticle(Particle.DUST, l, 1, 0, 0, 0, 0, op);
            for (LivingEntity v : enemigos(l, 1.2)) if (golpeados.add(v.getUniqueId())) alPegar.accept(v);
        }
    }

    private void danio(Player p, LivingEntity v, double d) {
        if (v == p) return;
        CombateRpg.herir(v, d * modo.combate().multiplicadorGeneral(p, v), p);
    }

    private static Location punto(Player p, double alcance) {
        RayTraceResult r = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), alcance,
                FluidCollisionMode.NEVER, true);
        if (r != null) return r.getHitPosition().toLocation(p.getWorld());
        return p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(alcance));
    }

    private static List<LivingEntity> enemigos(Location l, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : l.getWorld().getNearbyEntities(l, r, r, r)) {
            if (e instanceof LivingEntity le && !(le instanceof Player) && !le.isDead()
                    && !e.getPersistentDataContainer().has(Claves.INVOCACION) && !e.getPersistentDataContainer().has(Claves.NPC)
                    && e.getType() != org.bukkit.entity.EntityType.ARMOR_STAND) out.add(le);
        }
        return out;
    }

    private static List<Player> aliados(Location l, double r) {
        List<Player> out = new ArrayList<>();
        for (Player o : l.getWorld().getPlayers()) if (!o.isDead() && o.getLocation().distanceSquared(l) <= r * r) out.add(o);
        return out;
    }

    private static void circulo(Location c, double r, Color color) {
        Particle.DustOptions op = new Particle.DustOptions(color, 1.5f);
        for (int i = 0; i < 48; i++) {
            double a = i * Math.PI / 24;
            c.getWorld().spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * r, 0.2, Math.sin(a) * r), 1, 0, 0, 0, 0, op);
        }
    }

    public void olvidar(UUID id) {
        espera.remove(id);
    }
}
