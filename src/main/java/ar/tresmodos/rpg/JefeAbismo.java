package ar.tresmodos.rpg;

import ar.tresmodos.Claves;
import ar.tresmodos.Modo;
import ar.tresmodos.TresModos;
import ar.tresmodos.Util;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Stray;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * El Caballero del Abismo: jefe que se invoca con la campana en cualquier lugar abierto. Salta
 * sobre el jugador más cercano (se esquiva rodando) y a mitad de vida se enfurece y llama siervos.
 */
public class JefeAbismo implements Listener {
    public static final String ID = "abismo";

    private final TresModos plugin;
    private UUID jefeId;
    private BossBar barra;
    private int fase;
    private int proximoAtaque;
    private int ultimoConJugadores;
    private final Set<UUID> participantes = new HashSet<>();
    private final Set<UUID> siervos = new HashSet<>();

    public JefeAbismo(TresModos plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 10, 10);
    }

    public static ItemStack campana() {
        return Util.marcar(Util.item(Material.BELL, "<dark_red><bold>Campana del Jefe",
                "Clic derecho en un lugar abierto.", "Despierta al Caballero del Abismo."), Claves.CAMPANA, "1");
    }

    private static boolean esJefe(Entity e) {
        return e.getPersistentDataContainer().has(Claves.JEFE);
    }

    private LivingEntity jefe() {
        if (jefeId == null) return null;
        Entity e = Bukkit.getEntity(jefeId);
        return e instanceof LivingEntity le && le.isValid() ? le : null;
    }

    public boolean invocar(Player p) {
        if (jefe() != null) {
            Util.msg(p, "<red>El Caballero del Abismo ya está despierto en algún lugar.");
            return false;
        }
        World w = p.getWorld();
        Vector frente = p.getLocation().getDirection().setY(0);
        if (frente.lengthSquared() < 0.01) frente = new Vector(1, 0, 0);
        Location l = p.getLocation().add(frente.normalize().multiply(8));
        l.setY(w.getHighestBlockYAt(l) + 1);
        int ciclo = plugin.almacen().de(p).ciclo;
        double vida = 400 * (1 + 0.5 * ciclo);
        WitherSkeleton j = w.spawn(l, WitherSkeleton.class, CreatureSpawnEvent.SpawnReason.CUSTOM, s -> {
            s.customName(Util.mm("<dark_red><bold>Caballero del Abismo"));
            s.setCustomNameVisible(true);
            base(s, Attribute.SCALE, 1.6);
            base(s, Attribute.MAX_HEALTH, vida);
            s.setHealth(vida);
            base(s, Attribute.ATTACK_DAMAGE, 11 * (1 + 0.3 * ciclo));
            base(s, Attribute.ARMOR, 12);
            base(s, Attribute.MOVEMENT_SPEED, 0.27);
            base(s, Attribute.KNOCKBACK_RESISTANCE, 0.8);
            base(s, Attribute.FOLLOW_RANGE, 48);
            var eq = s.getEquipment();
            eq.setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
            eq.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
            eq.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
            eq.setItemInMainHandDropChance(0f);
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            s.setRemoveWhenFarAway(false);
            s.setPersistent(false);
            s.getPersistentDataContainer().set(Claves.JEFE, PersistentDataType.STRING, ID);
            s.setTarget(p);
        });
        jefeId = j.getUniqueId();
        fase = 1;
        participantes.clear();
        participantes.add(p.getUniqueId());
        proximoAtaque = Bukkit.getCurrentTick() + 120;
        ultimoConJugadores = Bukkit.getCurrentTick();
        if (barra == null) {
            barra = BossBar.bossBar(Util.mm("<dark_red>Caballero del Abismo"), 1f, BossBar.Color.RED, BossBar.Overlay.NOTCHED_10);
        } else {
            barra.name(Util.mm("<dark_red>Caballero del Abismo"));
            barra.progress(1f);
            barra.color(BossBar.Color.RED);
        }
        w.strikeLightningEffect(l);
        for (Player o : w.getPlayers()) {
            if (o.getLocation().distanceSquared(l) < 60 * 60) {
                Util.titulo(o, "<dark_red><bold>CABALLERO DEL ABISMO", "<gray>Esquivá su salto con F", 500, 2500, 800);
                o.playSound(o, Sound.ENTITY_WITHER_SPAWN, 0.8f, 0.7f);
            }
        }
        return true;
    }

    private static void base(LivingEntity e, Attribute a, double v) {
        AttributeInstance ai = e.getAttribute(a);
        if (ai != null) ai.setBaseValue(v);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void alGolpe(EntityDamageByEntityEvent e) {
        if (e.getEntity().getUniqueId().equals(jefeId)) {
            Player a = TresModos.jugadorAtacante(e.getDamager());
            if (a != null) participantes.add(a.getUniqueId());
        }
    }

    @EventHandler
    public void alMorir(EntityDeathEvent e) {
        if (!e.getEntity().getUniqueId().equals(jefeId)) {
            if (siervos.remove(e.getEntity().getUniqueId()) && e.getEntity().getKiller() != null) {
                plugin.almacen().de(e.getEntity().getKiller()).almas += 150;
            }
            return;
        }
        e.getDrops().clear();
        e.setDroppedExp(0);
        Location l = e.getEntity().getLocation();
        for (UUID id : participantes) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || Modo.de(p.getWorld()) != Modo.RPG) continue;
            var d = plugin.almacen().de(p);
            long premio = 3000L * (1 + d.ciclo);
            d.almas += premio;
            boolean primera = d.jefes.add(ID);
            if (primera) d.almasJefe.add(ID);
            Util.titulo(p, "<gold><bold>ENEMIGO FORMIDABLE DERROTADO", "<gold>+" + Util.num(premio) + " almas", 500, 3000, 1000);
            if (primera) Util.msg(p, "<gold>Conseguiste el <bold>Alma del Abismo</bold><gold>: +2 brasas para el árbol. "
                    + "Canjeala en el Santuario por su arma.");
            p.playSound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
        }
        if (Math.random() < 0.25) l.getWorld().dropItemNaturally(l, ArmaRpg.ESPADA_ABISMO.crear(0));
        terminar();
    }

    private void tick() {
        LivingEntity j = jefe();
        if (j == null) {
            if (jefeId != null) terminar();
            return;
        }
        int ahora = Bukkit.getCurrentTick();
        double max = j.getAttribute(Attribute.MAX_HEALTH).getValue();
        float prog = (float) Math.max(0, Math.min(1, j.getHealth() / max));
        barra.progress(prog);

        List<Player> cerca = new ArrayList<>();
        for (Player p : j.getWorld().getPlayers()) {
            if (!p.isDead() && p.getLocation().distanceSquared(j.getLocation()) < 50 * 50) {
                cerca.add(p);
                p.showBossBar(barra);
            } else {
                p.hideBossBar(barra);
            }
        }
        if (!cerca.isEmpty()) ultimoConJugadores = ahora;
        else if (ahora - ultimoConJugadores > 20 * 60) {
            j.getWorld().spawnParticle(Particle.LARGE_SMOKE, j.getLocation().add(0, 1.5, 0), 30, 0.5, 1, 0.5, 0.02);
            j.remove();
            terminar();
            return;
        }

        if (fase == 1 && prog <= 0.5f) {
            fase = 2;
            base(j, Attribute.MOVEMENT_SPEED, 0.33);
            j.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, PotionEffect.INFINITE_DURATION, 0, false, true));
            ItemStack espada = new ItemStack(Material.NETHERITE_SWORD);
            espada.addUnsafeEnchantment(Enchantment.FIRE_ASPECT, 2);
            j.getEquipment().setItemInMainHand(espada);
            j.getEquipment().setItemInMainHandDropChance(0f);
            barra.color(BossBar.Color.PURPLE);
            barra.name(Util.mm("<dark_red>Caballero del Abismo <gray>· <light_purple>enfurecido"));
            World w = j.getWorld();
            for (int i = 0; i < 3; i++) {
                double ang = i * Math.PI * 2 / 3;
                Location l = j.getLocation().add(Math.cos(ang) * 3, 0, Math.sin(ang) * 3);
                l.setY(w.getHighestBlockYAt(l) + 1);
                w.strikeLightningEffect(l);
                Stray s = w.spawn(l, Stray.class, CreatureSpawnEvent.SpawnReason.CUSTOM, st -> {
                    st.customName(Util.mm("<dark_aqua>Siervo del Abismo"));
                    st.setPersistent(false);
                    st.getPersistentDataContainer().set(Claves.SIERVO, PersistentDataType.BYTE, (byte) 1);
                    if (!cerca.isEmpty()) st.setTarget(cerca.get(0));
                });
                siervos.add(s.getUniqueId());
            }
            for (Player p : cerca) {
                Util.titulo(p, "", "<light_purple>El Caballero se enfurece", 200, 1500, 400);
                p.playSound(p, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 0.6f);
            }
        }

        if (ahora >= proximoAtaque && !cerca.isEmpty() && !plugin.rpg().combate().aturdido(j)) {
            proximoAtaque = ahora + (fase == 1 ? 160 : 110);
            salto(j, cerca);
        }
    }

    /** Salta hacia el jugador más cercano y al caer golpea todo en 4 bloques (se esquiva rodando). */
    private void salto(LivingEntity j, List<Player> cerca) {
        Player objetivo = null;
        double mejor = Double.MAX_VALUE;
        for (Player p : cerca) {
            double d = p.getLocation().distanceSquared(j.getLocation());
            if (d < mejor && d < 22 * 22) {
                mejor = d;
                objetivo = p;
            }
        }
        if (objetivo == null) return;
        Vector hacia = objetivo.getLocation().toVector().subtract(j.getLocation().toVector()).setY(0);
        double dist = hacia.length();
        if (dist > 0.1) hacia.normalize().multiply(Math.min(1.6, 0.35 + dist * 0.09));
        j.setVelocity(hacia.setY(0.95));
        j.getWorld().playSound(j.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1f, 0.6f);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!j.isValid()) return;
            Location c = j.getLocation();
            World w = c.getWorld();
            w.spawnParticle(Particle.EXPLOSION, c, 3, 1.2, 0.2, 1.2, 0);
            w.spawnParticle(Particle.BLOCK, c, 60, 2.5, 0.1, 2.5, 0, c.clone().subtract(0, 1, 0).getBlock().getBlockData());
            w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
            for (Player p : w.getPlayers()) {
                if (p.getLocation().distanceSquared(c) > 4 * 4) continue;
                p.damage(fase == 1 ? 9 : 12, j);
                Vector empuje = p.getLocation().toVector().subtract(c.toVector()).setY(0);
                if (empuje.lengthSquared() > 0.01) p.setVelocity(empuje.normalize().multiply(0.9).setY(0.45));
            }
        }, 18);
    }

    private void terminar() {
        jefeId = null;
        if (barra != null) for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(barra);
        for (UUID s : siervos) {
            Entity e = Bukkit.getEntity(s);
            if (e != null) e.remove();
        }
        siervos.clear();
        participantes.clear();
    }

    public void ocultar(Player p) {
        if (barra != null) p.hideBossBar(barra);
    }

    public void apagar() {
        LivingEntity j = jefe();
        if (j != null) j.remove();
        terminar();
    }
}
