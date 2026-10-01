package ar.tresmodos.rpg;

import ar.tresmodos.Claves;
import ar.tresmodos.TresModos;
import ar.tresmodos.Util;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Estados alterados: cada golpe acumula y al llegar al umbral estallan.
 * Sangrado: golpe del 15 % de la vida. Veneno: daño lento. Frío: daño, lentitud y escarcha.
 * Locura: daño fuerte, mareo y oscuridad.
 */
public class Estados {
    private record Dano(UUID autor, double porSegundo, int hasta) {}

    private final TresModos plugin;
    private final Map<UUID, EnumMap<Estado, Double>> acumulado = new HashMap<>();
    private final Map<UUID, Integer> ultimoAcumulo = new HashMap<>();
    private final Map<UUID, Dano> veneno = new HashMap<>();

    public Estados(TresModos plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 20);
    }

    private static boolean esJefe(Entity e) {
        return e.getPersistentDataContainer().has(Claves.JEFE);
    }

    /** Suma acumulación de un estado; si se llena, estalla. Los jefes resisten más. */
    public void acumular(LivingEntity v, Estado e, double cantidad, Player autor) {
        if (v.isDead() || cantidad <= 0) return;
        double resistencia = esJefe(v) ? 0.45 : 1.0;
        if (v instanceof Player pj) resistencia *= resistenciaJugador(pj, e);
        EnumMap<Estado, Double> m = acumulado.computeIfAbsent(v.getUniqueId(), u -> new EnumMap<>(Estado.class));
        double nuevo = m.getOrDefault(e, 0.0) + cantidad * resistencia;
        ultimoAcumulo.put(v.getUniqueId(), Bukkit.getCurrentTick());
        v.getWorld().spawnParticle(Particle.DUST, v.getLocation().add(0, v.getHeight() * 0.7, 0), 4, 0.25, 0.3, 0.25, 0,
                new Particle.DustOptions(e.particula, 1.1f));
        if (nuevo >= e.umbral) {
            m.remove(e);
            estallar(v, e, autor);
        } else {
            m.put(e, nuevo);
        }
    }

    /** Las armaduras pesadas resisten algo más el sangrado; la Vida y armadura del árbol, todo. */
    private double resistenciaJugador(Player p, Estado e) {
        int vida = plugin.almacen().de(p).rama(Rama.VIDA);
        return Math.max(0.4, 1 - 0.08 * vida);
    }

    private void estallar(LivingEntity v, Estado e, Player autor) {
        double max = v.getAttribute(Attribute.MAX_HEALTH).getValue();
        boolean jefe = esJefe(v);
        switch (e) {
            case SANGRADO -> {
                golpe(v, max * (jefe ? 0.07 : 0.15), autor);
                v.getWorld().spawnParticle(Particle.BLOCK, v.getLocation().add(0, 1, 0), 40, 0.3, 0.5, 0.3, 0,
                        Material.REDSTONE_BLOCK.createBlockData());
                v.getWorld().playSound(v.getLocation(), Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 1f, 0.6f);
                if (autor != null) Util.barra(autor, "<dark_red><bold>¡Hemorragia!");
            }
            case VENENO -> {
                veneno.put(v.getUniqueId(), new Dano(autor == null ? null : autor.getUniqueId(),
                        Math.max(1, max * (jefe ? 0.008 : 0.025)), Bukkit.getCurrentTick() + 240));
                v.getWorld().playSound(v.getLocation(), Sound.ENTITY_SPIDER_HURT, 0.8f, 0.6f);
            }
            case FRIO -> {
                golpe(v, max * (jefe ? 0.05 : 0.10), autor);
                v.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 120, 1, false, true));
                v.setFreezeTicks(Math.max(v.getFreezeTicks(), 200));
                v.getWorld().spawnParticle(Particle.SNOWFLAKE, v.getLocation().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.02);
                v.getWorld().playSound(v.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 1.4f);
            }
            case LOCURA -> {
                golpe(v, max * (jefe ? 0.04 : (v instanceof Player ? 0.20 : 0.25)), autor);
                v.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 160, 0, false, false));
                v.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 80, 0, false, false));
                v.getWorld().playSound(v.getLocation(), Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 0.6f);
                if (v instanceof Player p) Util.titulo(p, "", "<gold><bold>LOCURA", 0, 1200, 400);
            }
        }
    }

    private static void golpe(LivingEntity v, double danio, Player autor) {
        v.setNoDamageTicks(0);
        if (autor != null && autor.isOnline()) v.damage(danio, autor);
        else v.damage(danio);
    }

    private void tick() {
        int ahora = Bukkit.getCurrentTick();
        // La acumulación baja sola si no te siguen pegando.
        Iterator<Map.Entry<UUID, EnumMap<Estado, Double>>> itr = acumulado.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry<UUID, EnumMap<Estado, Double>> en = itr.next();
            if (ahora - ultimoAcumulo.getOrDefault(en.getKey(), 0) < 60) continue;
            en.getValue().replaceAll((k, v) -> v - 6);
            en.getValue().values().removeIf(v -> v <= 0);
            if (en.getValue().isEmpty()) {
                itr.remove();
                ultimoAcumulo.remove(en.getKey());
            }
        }
        Iterator<Map.Entry<UUID, Dano>> iv = veneno.entrySet().iterator();
        while (iv.hasNext()) {
            Map.Entry<UUID, Dano> en = iv.next();
            Entity e = Bukkit.getEntity(en.getKey());
            Dano d = en.getValue();
            if (!(e instanceof LivingEntity v) || v.isDead() || d.hasta() < ahora) {
                iv.remove();
                continue;
            }
            Player autor = d.autor() == null ? null : Bukkit.getPlayer(d.autor());
            golpe(v, d.porSegundo(), autor);
            v.getWorld().spawnParticle(Particle.DUST, v.getLocation().add(0, 1, 0), 6, 0.3, 0.4, 0.3, 0,
                    new Particle.DustOptions(Estado.VENENO.particula, 1.2f));
        }
    }

    /** Texto con las barras de acumulación del jugador (para la barra de éter). */
    public String resumen(Player p) {
        EnumMap<Estado, Double> m = acumulado.get(p.getUniqueId());
        StringBuilder sb = new StringBuilder();
        if (m != null) {
            for (Map.Entry<Estado, Double> en : m.entrySet()) {
                int lleno = (int) Math.min(5, Math.round(en.getValue() / en.getKey().umbral * 5));
                sb.append("  ").append(en.getKey().color).append(en.getKey().nombre).append(" ")
                        .append("▰".repeat(lleno)).append("<dark_gray>").append("▱".repeat(5 - lleno));
            }
        }
        if (veneno.containsKey(p.getUniqueId())) sb.append("  <dark_green>☠ envenenado");
        return sb.toString();
    }

    public boolean envenenado(LivingEntity v) {
        return veneno.containsKey(v.getUniqueId());
    }

    public void limpiar(LivingEntity v) {
        acumulado.remove(v.getUniqueId());
        veneno.remove(v.getUniqueId());
        v.setFreezeTicks(0);
        v.removePotionEffect(PotionEffectType.POISON);
        v.removePotionEffect(PotionEffectType.NAUSEA);
        v.removePotionEffect(PotionEffectType.DARKNESS);
        v.removePotionEffect(PotionEffectType.WITHER);
    }

    public void olvidar(UUID id) {
        acumulado.remove(id);
        ultimoAcumulo.remove(id);
        veneno.remove(id);
    }
}
