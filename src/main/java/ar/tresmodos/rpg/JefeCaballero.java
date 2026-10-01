package ar.tresmodos.rpg;

import ar.tresmodos.TresModos;
import ar.tresmodos.Util;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Stray;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * El Caballero del Abismo (Ciudadela). Duelo técnico: todos sus golpes se pueden parar con parry.
 * Combos de tajos, salto con onda y, en la F2, fuego oscuro en línea y siervos.
 */
public class JefeCaballero extends Jefe {
    private final Set<UUID> siervos = new HashSet<>();

    public JefeCaballero(TresModos plugin, ModoRpg modo) {
        super(plugin, modo);
    }

    @Override public String id() { return "abismo"; }
    @Override public String nombre() { return "Caballero del Abismo"; }
    @Override public String alma() { return "Alma del Abismo"; }
    @Override protected double vidaBase() { return 900; }
    @Override public long almas() { return 18000; }
    @Override protected BossBar.Color colorBarra() { return BossBar.Color.PURPLE; }

    @Override
    protected LivingEntity crearCuerpo(Location c) {
        return c.getWorld().spawn(c, WitherSkeleton.class, CreatureSpawnEvent.SpawnReason.CUSTOM, s -> {
            TipoEnemigo.base(s, Attribute.SCALE, 1.6);
            TipoEnemigo.base(s, Attribute.ATTACK_DAMAGE, 11);
            TipoEnemigo.base(s, Attribute.ARMOR, 12);
            TipoEnemigo.base(s, Attribute.MOVEMENT_SPEED, 0.27);
            var eq = s.getEquipment();
            eq.setItemInMainHand(TipoEnemigo.modelo(Material.NETHERITE_SWORD, "rpg_espada_abismo"));
            eq.setHelmet(TipoEnemigo.modelo(Material.PAPER, "yelmo_abismo"));
            eq.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
            eq.setItemInMainHandDropChance(0f);
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
        });
    }

    @Override
    protected void pensar(int ahora) {
        if (fase == 1 && vidaFrac() <= 0.5) enfurecer();
        if (ahora < proximo) return;
        Player t = objetivo();
        if (t == null) return;
        double d = t.getLocation().distance(cuerpo.getLocation());
        if (fase == 2 && azar.nextDouble() < 0.35) fuegoOscuro(t, ahora);
        else if (d < 4.5) combo(t, ahora);
        else if (d > 6) salto(t, ahora);
        else proximo = ahora + 20;
    }

    /** Dos o tres tajos con su gesto: cada uno se puede parar con parry. */
    private void combo(Player t, int ahora) {
        int golpes = fase == 1 ? 2 : 3;
        proximo = ahora + 30 + golpes * 16;
        for (int i = 0; i < golpes; i++) {
            int base = i * 16;
            despues(base, () -> {
                mirarA(t);
                cuerpo.getWorld().playSound(cuerpo.getLocation(), Sound.ENTITY_WITHER_SKELETON_AMBIENT, 1f, 0.5f);
                Location frente = cuerpo.getEyeLocation().add(cuerpo.getLocation().getDirection().multiply(1.5));
                cuerpo.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, frente, 8, 0.4, 0.3, 0.4, 0.01);
            });
            despues(base + 12, () -> {
                cuerpo.swingMainHand();
                cuerpo.getWorld().playSound(cuerpo.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.6f);
                cuerpo.getWorld().spawnParticle(Particle.SWEEP_ATTACK, cuerpo.getEyeLocation()
                        .add(cuerpo.getLocation().getDirection().multiply(2)), 3, 0.6, 0.1, 0.6, 0);
                golpeCono(4.8, 0.3, 11);
            });
        }
    }

    private void salto(Player t, int ahora) {
        proximo = ahora + (fase == 1 ? 120 : 90);
        Vector hacia = t.getLocation().toVector().subtract(cuerpo.getLocation().toVector()).setY(0);
        double dist = hacia.length();
        if (dist > 0.1) hacia.normalize().multiply(Math.min(1.6, 0.35 + dist * 0.09));
        aviso(t.getLocation(), 4, Color.fromRGB(120, 40, 200));
        cuerpo.setVelocity(hacia.setY(0.95));
        cuerpo.getWorld().playSound(cuerpo.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1f, 0.6f);
        despues(18, () -> {
            Location c = cuerpo.getLocation();
            c.getWorld().spawnParticle(Particle.EXPLOSION, c, 3, 1.2, 0.2, 1.2, 0);
            c.getWorld().spawnParticle(Particle.SOUL, c, 30, 2, 0.2, 2, 0.02);
            c.getWorld().playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
            golpeArea(c, 4, fase == 1 ? 10 : 13, 0.9);
        });
    }

    /** Línea de fuego oscuro hacia el jugador: avisa, después quema. */
    private void fuegoOscuro(Player t, int ahora) {
        proximo = ahora + 70;
        Location desde = cuerpo.getLocation();
        Vector dir = t.getLocation().toVector().subtract(desde.toVector()).setY(0);
        if (dir.lengthSquared() < 0.01) return;
        dir.normalize();
        mirarA(t);
        for (double s = 1; s < 14; s += 1) {
            cuerpo.getWorld().spawnParticle(Particle.SOUL, desde.clone().add(dir.clone().multiply(s)).add(0, 0.2, 0), 2, 0.2, 0, 0.2, 0);
        }
        cuerpo.getWorld().playSound(desde, Sound.ITEM_FIRECHARGE_USE, 1f, 0.5f);
        despues(18, () -> {
            for (double s = 1; s < 14; s += 1) {
                Location l = desde.clone().add(dir.clone().multiply(s));
                l.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, l.clone().add(0, 0.5, 0), 6, 0.3, 0.5, 0.3, 0.02);
                for (Player p : arena.participantes()) {
                    if (p.getLocation().distanceSquared(l) < 1.6 * 1.6) {
                        p.damage(9 * multDanio, cuerpo);
                        p.setFireTicks(60);
                    }
                }
            }
        });
    }

    private void enfurecer() {
        cambiarFase(2, "<light_purple>El Caballero se enfurece");
        TipoEnemigo.base(cuerpo, Attribute.MOVEMENT_SPEED, 0.33);
        cuerpo.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, 0, false, true));
        ItemStack espada = new ItemStack(Material.NETHERITE_SWORD);
        espada.addUnsafeEnchantment(Enchantment.FIRE_ASPECT, 2);
        cuerpo.getEquipment().setItemInMainHand(espada);
        barra.name(Util.mm("<dark_red>Caballero del Abismo <gray>· <light_purple>enfurecido"));
        for (int i = 0; i < 3; i++) {
            double ang = i * Math.PI * 2 / 3;
            Location l = cuerpo.getLocation().add(Math.cos(ang) * 3, 0.2, Math.sin(ang) * 3);
            l.getWorld().strikeLightningEffect(l);
            Stray s = l.getWorld().spawn(l, Stray.class, CreatureSpawnEvent.SpawnReason.CUSTOM, st -> {
                st.customName(Util.mm("<dark_aqua>Siervo del Abismo"));
                st.setPersistent(false);
                st.setShouldBurnInDay(false);
                Player t = alAzar();
                if (t != null) st.setTarget(t);
            });
            siervos.add(s.getUniqueId());
        }
        for (Player p : arena.participantes()) p.playSound(p, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 0.6f);
    }

    @Override
    public boolean esParte(Entity e) {
        return siervos.contains(e.getUniqueId());
    }

    @Override
    protected void limpiarExtra() {
        for (UUID u : siervos) {
            Entity e = plugin.getServer().getEntity(u);
            if (e != null) e.remove();
        }
        siervos.clear();
    }
}
