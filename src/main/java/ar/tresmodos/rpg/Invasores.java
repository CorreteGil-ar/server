package ar.tresmodos.rpg;

import ar.tresmodos.Claves;
import ar.tresmodos.Modo;
import ar.tresmodos.TresModos;
import ar.tresmodos.Util;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Husk;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Amenazas que no son encuentros fijos: el Cazador Carmesí, un invasor que persigue a los jugadores
 * en las zonas 2 a 4 y pelea como uno (rueda, bloquea y toma estus), y los mímicos, cofres que muerden.
 */
public class Invasores implements Listener {
    private static final NamespacedKey REVISADO = new NamespacedKey("tresmodos", "rpg_cofre_revisado");
    private static final NamespacedKey CAZADOR = new NamespacedKey("tresmodos", "rpg_cazador");
    private static final NamespacedKey MIMICO = new NamespacedKey("tresmodos", "rpg_mimico");

    private final TresModos plugin;
    private final ModoRpg modo;
    private final Random azar = new Random();
    private UUID cazador;
    private UUID presa;
    private int cazadorHasta, proximoEsquive, proximoCuchillo, estus;
    private boolean tomando;
    private final List<UUID> mimicos = new ArrayList<>();
    private final java.util.Map<UUID, ItemStack[]> tragado = new java.util.HashMap<>();

    public Invasores(TresModos plugin, ModoRpg modo) {
        this.plugin = plugin;
        this.modo = modo;
        Bukkit.getScheduler().runTaskTimer(plugin, this::quizasInvadir, 20 * 120, 20 * 90);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40, 5);
    }

    // ------------------------------------------------------------------ Cazador Carmesí

    private LivingEntity cazador() {
        if (cazador == null) return null;
        Entity e = Bukkit.getEntity(cazador);
        return e instanceof LivingEntity le && le.isValid() ? le : null;
    }

    private void quizasInvadir() {
        if (cazador() != null) return;
        World w = plugin.mundos().de(Modo.RPG);
        List<Player> candidatos = new ArrayList<>();
        for (Player p : w.getPlayers()) {
            if (p.isDead()) continue;
            Zona z = modo.mundoRpg().zona(p.getLocation());
            if (z == Zona.BOSQUE || z == Zona.CIUDADELA || z == Zona.COSTA) candidatos.add(p);
        }
        if (candidatos.isEmpty() || azar.nextDouble() > 0.3) return;
        invadir(candidatos.get(azar.nextInt(candidatos.size())));
    }

    /** Aparece detrás del jugador, a unos 14 bloques. */
    public void invadir(Player p) {
        Vector atras = p.getLocation().getDirection().setY(0);
        if (atras.lengthSquared() < 0.01) atras = new Vector(1, 0, 0);
        Location l = p.getLocation().add(atras.normalize().multiply(-14));
        l.setY(l.getWorld().getHighestBlockYAt(l) + 1);
        int nivel = modo.mundoRpg().nivel(p.getLocation());
        int ciclo = plugin.almacen().de(p).ciclo;
        double vida = 140 * (1 + 0.08 * nivel) * Math.pow(1.5, ciclo);
        Skeleton s = l.getWorld().spawn(l, Skeleton.class, CreatureSpawnEvent.SpawnReason.CUSTOM, e -> {
            e.customName(Util.mm("<dark_red><bold>Cazador Carmesí"));
            e.setCustomNameVisible(true);
            e.setShouldBurnInDay(false);
            e.setPersistent(false);
            e.setRemoveWhenFarAway(false);
            var eq = e.getEquipment();
            List<ItemStack> set = setCarmesi();
            eq.setHelmet(set.get(0));
            eq.setChestplate(set.get(1));
            eq.setLeggings(set.get(2));
            eq.setBoots(set.get(3));
            eq.setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
            eq.setItemInOffHand(new ItemStack(Material.SHIELD));
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setLeggingsDropChance(0f);
            eq.setBootsDropChance(0f);
            eq.setItemInMainHandDropChance(0f);
            eq.setItemInOffHandDropChance(0f);
            TipoEnemigo.base(e, Attribute.MAX_HEALTH, vida);
            e.setHealth(vida);
            TipoEnemigo.base(e, Attribute.ATTACK_DAMAGE, 8 * (1 + 0.05 * nivel) * Math.pow(1.5, ciclo));
            TipoEnemigo.base(e, Attribute.MOVEMENT_SPEED, 0.31);
            TipoEnemigo.base(e, Attribute.FOLLOW_RANGE, 48);
            e.getPersistentDataContainer().set(CAZADOR, PersistentDataType.INTEGER, nivel);
            e.setTarget(p);
        });
        cazador = s.getUniqueId();
        presa = p.getUniqueId();
        cazadorHasta = Bukkit.getCurrentTick() + 20 * 240;
        estus = 3;
        tomando = false;
        for (Player o : l.getWorld().getPlayers()) {
            if (o.getLocation().distanceSquared(l) > 60 * 60) continue;
            Util.titulo(o, "<dark_red><bold>CAZADOR CARMESÍ", o == p ? "<red>Te está cazando" : "<red>Está cazando a " + p.getName(),
                    300, 2500, 800);
            o.playSound(o, Sound.EVENT_RAID_HORN, 0.7f, 0.6f);
        }
    }

    private void tick() {
        LivingEntity c = cazador();
        if (c == null) {
            cazador = null;
        } else {
            Player p = Bukkit.getPlayer(presa);
            int ahora = Bukkit.getCurrentTick();
            if (p == null || p.isDead() || p.getWorld() != c.getWorld() || ahora > cazadorHasta
                    || p.getLocation().distanceSquared(c.getLocation()) > 80 * 80) {
                c.getWorld().spawnParticle(Particle.LARGE_SMOKE, c.getLocation().add(0, 1, 0), 30, 0.3, 0.8, 0.3, 0.02);
                c.remove();
                cazador = null;
                if (p != null && p.isOnline() && !p.isDead()) Util.barra(p, "<gray>El Cazador Carmesí se retiró.");
            } else {
                if (c instanceof org.bukkit.entity.Mob m && m.getTarget() != p && !tomando) m.setTarget(p);
                double max = c.getAttribute(Attribute.MAX_HEALTH).getValue();
                // Toma estus: se queda quieto un segundo (es el momento de castigarlo).
                if (!tomando && estus > 0 && c.getHealth() < max * 0.4) {
                    tomando = true;
                    estus--;
                    if (c instanceof org.bukkit.entity.Mob m) m.setAware(false);
                    c.getWorld().playSound(c.getLocation(), Sound.ITEM_HONEY_BOTTLE_DRINK, 1.2f, 0.8f);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        tomando = false;
                        if (!c.isValid()) return;
                        if (c instanceof org.bukkit.entity.Mob m) m.setAware(true);
                        c.setHealth(Math.min(max, c.getHealth() + max * 0.4));
                        c.getWorld().spawnParticle(Particle.FLAME, c.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.01);
                    }, 24);
                }
                // Cuchillos a distancia.
                double d = p.getLocation().distance(c.getLocation());
                if (!tomando && d > 6 && d < 16 && ahora > proximoCuchillo && c.hasLineOfSight(p)) {
                    proximoCuchillo = ahora + 60;
                    var bola = c.launchProjectile(org.bukkit.entity.Snowball.class,
                            p.getEyeLocation().toVector().subtract(c.getEyeLocation().toVector()).normalize().multiply(1.8));
                    bola.setItem(new ItemStack(Material.IRON_NUGGET));
                    bola.getPersistentDataContainer().set(Claves.EQUIPO, PersistentDataType.STRING, "cuchillo_cazador");
                }
            }
        }
        // Mímicos: el cofre que llevan encima los sigue.
        mimicos.removeIf(id -> {
            Entity e = Bukkit.getEntity(id);
            boolean fuera = e == null || !e.isValid();
            if (fuera) tragado.remove(id);
            return fuera;
        });
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void alGolpe(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof org.bukkit.entity.Snowball s
                && "cuchillo_cazador".equals(s.getPersistentDataContainer().get(Claves.EQUIPO, PersistentDataType.STRING))
                && e.getEntity() instanceof Player p) {
            e.setDamage(5);
            modo.estados().acumular(p, Estado.SANGRADO, 20, null);
            return;
        }
        LivingEntity c = cazador();
        if (c == null || !e.getEntity().getUniqueId().equals(c.getUniqueId())) return;
        Player atacante = TresModos.jugadorAtacante(e.getDamager());
        if (atacante == null || tomando) return;
        int ahora = Bukkit.getCurrentTick();
        // Rueda para esquivar o bloquea de frente, como un jugador.
        if (ahora > proximoEsquive && azar.nextDouble() < 0.3) {
            proximoEsquive = ahora + 50;
            e.setCancelled(true);
            Vector lado = c.getLocation().getDirection().setY(0).crossProduct(new Vector(0, 1, 0));
            if (lado.lengthSquared() < 0.01) lado = new Vector(1, 0, 0);
            c.setVelocity(lado.normalize().multiply(azar.nextBoolean() ? 1 : -1).multiply(1.0).setY(0.2));
            c.getWorld().spawnParticle(Particle.CLOUD, c.getLocation().add(0, 0.2, 0), 8, 0.3, 0.05, 0.3, 0.02);
            c.getWorld().playSound(c.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1.7f);
        } else if (!(e.getDamager() instanceof org.bukkit.entity.Projectile) && azar.nextDouble() < 0.25) {
            Vector mira = c.getLocation().getDirection().setY(0);
            Vector hacia = atacante.getLocation().toVector().subtract(c.getLocation().toVector()).setY(0);
            if (mira.lengthSquared() > 0.01 && hacia.lengthSquared() > 0.01 && mira.normalize().dot(hacia.normalize()) > 0.3) {
                e.setCancelled(true);
                c.getWorld().playSound(c.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 0.9f);
                modo.gastar(atacante, 12);
            }
        }
    }

    @EventHandler
    public void alMorir(EntityDeathEvent e) {
        LivingEntity v = e.getEntity();
        Integer nivel = v.getPersistentDataContainer().get(CAZADOR, PersistentDataType.INTEGER);
        if (nivel != null) {
            e.getDrops().clear();
            e.setDroppedExp(0);
            cazador = null;
            List<ItemStack> set = setCarmesi();
            e.getDrops().add(set.get(azar.nextInt(set.size())));
            e.getDrops().add(ObjetosRpg.fragmento(3));
            for (Player p : v.getWorld().getPlayers()) {
                if (p.getLocation().distanceSquared(v.getLocation()) > 24 * 24) continue;
                long almas = Math.round(1500 * (1 + 0.12 * nivel) * (1 + 0.5 * plugin.almacen().de(p).ciclo));
                plugin.almacen().de(p).almas += almas;
                Util.titulo(p, "", "<dark_red>Cazador Carmesí derrotado <gold>+" + Util.num(almas), 200, 2000, 500);
            }
            return;
        }
        if (v.getPersistentDataContainer().has(MIMICO)) {
            e.getDrops().clear();
            e.setDroppedExp(0);
            for (Entity pas : v.getPassengers()) pas.remove();
            ItemStack[] dentro = tragado.remove(v.getUniqueId());
            if (dentro != null) for (ItemStack it : dentro) if (it != null) e.getDrops().add(it);
            e.getDrops().add(ObjetosRpg.fragmento(2 + azar.nextInt(3)));
            if (azar.nextDouble() < 0.5) e.getDrops().add(ObjetosRpg.escama(1));
            if (azar.nextDouble() < 0.3) e.getDrops().add(ObjetosRpg.lagrima(1));
            ArmaRpg[] comunes = {ArmaRpg.ESTOQUE, ArmaRpg.ALABARDA, ArmaRpg.GUADANIA, ArmaRpg.MARTILLO, ArmaRpg.BALLESTA};
            e.getDrops().add(comunes[azar.nextInt(comunes.length)].crear(1 + azar.nextInt(3)));
            Player k = v.getKiller();
            if (k != null) {
                long almas = Math.round(800 * (1 + 0.5 * plugin.almacen().de(k).ciclo));
                plugin.almacen().de(k).almas += almas;
                Util.barra(k, "<gold>+" + Util.num(almas) + " almas");
            }
        }
    }

    public static List<ItemStack> setCarmesi() {
        List<ItemStack> l = new ArrayList<>();
        Material[] piezas = {Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS};
        String[] nombres = {"Yelmo", "Peto", "Grebas", "Botas"};
        for (int i = 0; i < 4; i++) {
            ItemStack it = Util.item(piezas[i], "<dark_red><bold>" + nombres[i] + " del Cazador Carmesí", "<gray>Cuero negro teñido de sangre.");
            ItemMeta m = it.getItemMeta();
            m.setUnbreakable(true);
            if (m instanceof LeatherArmorMeta lm) lm.setColor(i % 2 == 0 ? Color.fromRGB(25, 20, 22) : Color.fromRGB(120, 12, 18));
            if (m instanceof ArmorMeta am) am.setTrim(new ArmorTrim(TrimMaterial.REDSTONE, TrimPattern.DUNE));
            it.setItemMeta(m);
            Armaduras.actualizar(it);
            l.add(it);
        }
        return l;
    }

    // ------------------------------------------------------------------ mímicos

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alAbrirCofre(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Block b = e.getClickedBlock();
        if (b.getType() != Material.CHEST || Modo.de(b.getWorld()) != Modo.RPG) return;
        if (!(b.getState() instanceof Chest cofre)) return;
        if (cofre.getPersistentDataContainer().has(REVISADO)) return;
        cofre.getPersistentDataContainer().set(REVISADO, PersistentDataType.BYTE, (byte) 1);
        cofre.update();
        if (modo.mundoRpg().zona(b.getLocation()) == Zona.SANTUARIO || azar.nextDouble() > 0.15) return;
        e.setCancelled(true);
        mimico(b, e.getPlayer());
    }

    private void mimico(Block b, Player p) {
        Location l = b.getLocation().add(0.5, 0, 0.5);
        ItemStack[] contenido = b.getState() instanceof Chest c ? c.getBlockInventory().getContents().clone() : new ItemStack[0];
        if (b.getState() instanceof Chest c) c.getBlockInventory().clear();
        b.setType(Material.AIR);
        int nivel = modo.mundoRpg().nivel(l);
        double vida = 60 * (1 + 0.08 * nivel) * Math.pow(1.5, plugin.almacen().de(p).ciclo);
        Husk h = l.getWorld().spawn(l, Husk.class, CreatureSpawnEvent.SpawnReason.CUSTOM, x -> {
            x.setAdult();
            x.setSilent(true);
            x.customName(Util.mm("<gold>Mímico"));
            x.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false));
            TipoEnemigo.base(x, Attribute.MAX_HEALTH, vida);
            x.setHealth(vida);
            TipoEnemigo.base(x, Attribute.ATTACK_DAMAGE, 9 * (1 + 0.05 * nivel));
            TipoEnemigo.base(x, Attribute.MOVEMENT_SPEED, 0.3);
            TipoEnemigo.base(x, Attribute.SCALE, 0.7);
            x.getEquipment().clear();
            x.setPersistent(false);
            x.getPersistentDataContainer().set(MIMICO, PersistentDataType.BYTE, (byte) 1);
            x.setTarget(p);
        });
        ItemDisplay tapa = l.getWorld().spawn(l, ItemDisplay.class, d -> {
            d.setItemStack(new ItemStack(Material.CHEST));
            d.setPersistent(false);
        });
        h.addPassenger(tapa);
        mimicos.add(h.getUniqueId());
        tragado.put(h.getUniqueId(), contenido);
        l.getWorld().playSound(l, Sound.BLOCK_CHEST_CLOSE, 1.5f, 0.5f);
        l.getWorld().playSound(l, Sound.ENTITY_EVOKER_FANGS_ATTACK, 1.2f, 0.6f);
        Util.titulo(p, "", "<gold>¡Era un mímico!", 0, 1500, 300);
        p.damage(6, h);
    }

    public void apagar() {
        LivingEntity c = cazador();
        if (c != null) c.remove();
        for (UUID id : mimicos) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) {
                for (Entity pas : e.getPassengers()) pas.remove();
                e.remove();
            }
        }
    }
}
