package ar.tresmodos.modos;

import ar.tresmodos.Armas;
import ar.tresmodos.Claves;
import ar.tresmodos.DatosJugador;
import ar.tresmodos.Menu;
import ar.tresmodos.Modo;
import ar.tresmodos.ModoJuego;
import ar.tresmodos.TresModos;
import ar.tresmodos.Util;
import com.destroystokyo.paper.ParticleBuilder;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Stray;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Modo RPG/Souls: aguante, esquive con F, hogueras, Estus, almas con mancha de sangre, niveles y un jefe. */
public class ModoRpg implements ModoJuego, Listener {
    private static final double COSTO_ATAQUE = 16, COSTO_ESQUIVE = 22, COSTO_BLOQUEO = 12, COSTO_ARCO = 10;
    private static final int IFRAMES = 8;

    private final TresModos plugin;
    private final Map<UUID, Double> aguante = new HashMap<>();
    private final Map<UUID, Integer> ultimoGasto = new HashMap<>();
    private final Map<UUID, Integer> invulnerableHasta = new HashMap<>();
    private final Map<UUID, Integer> ultimoEsquive = new HashMap<>();
    private final Set<UUID> sinAliento = new HashSet<>();

    // jefe
    private UUID jefeId;
    private BossBar barra;
    private int fase;
    private int proximoAtaque;
    private int ultimoConJugadores;
    private final Set<UUID> participantes = new HashSet<>();
    private final Set<UUID> siervos = new HashSet<>();

    public ModoRpg(TresModos plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickAguante, 1, 1);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickManchas, 10, 10);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickJefe, 10, 10);
    }

    private World mundo() {
        return plugin.mundos().de(Modo.RPG);
    }

    private boolean enRpg(Entity e) {
        return Modo.de(e.getWorld()) == Modo.RPG;
    }

    @Override public Modo modo() { return Modo.RPG; }
    @Override public boolean guardaEstado() { return true; }
    @Override public GameMode modoJuego() { return GameMode.SURVIVAL; }

    @Override
    public Location ubicacionEntrada(Player p) {
        return mundo().getSpawnLocation().toCenterLocation();
    }

    @Override
    public Location respawn(Player p) {
        DatosJugador d = plugin.almacen().de(p);
        if (d.hoguera != null && d.hoguera.getWorld() == mundo()) return d.hoguera;
        return ubicacionEntrada(p);
    }

    private double aguanteMax(Player p) {
        return 100 + 10 * plugin.almacen().de(p).aguante;
    }

    @Override
    public void prepararAtributos(Player p) {
        AttributeInstance vida = p.getAttribute(Attribute.MAX_HEALTH);
        if (vida != null) vida.setBaseValue(20 + 2 * plugin.almacen().de(p).vigor);
    }

    @Override
    public void kitInicial(Player p) {
        var inv = p.getInventory();
        inv.setItem(0, Util.item(Material.IRON_SWORD, "<white>Espada larga"));
        inv.setItem(1, Util.item(Material.BOW, "<white>Arco corto"));
        inv.setItem(2, estus(plugin.almacen().de(p).estusMax, plugin.almacen().de(p).estusMax));
        inv.setItem(8, new ItemStack(Material.TORCH, 16));
        inv.setItem(9, new ItemStack(Material.ARROW, 32));
        inv.setItemInOffHand(Util.item(Material.SHIELD, "<white>Escudo de madera"));
        inv.setChestplate(new ItemStack(Material.CHAINMAIL_CHESTPLATE));
        inv.setLeggings(new ItemStack(Material.CHAINMAIL_LEGGINGS));
        inv.setBoots(new ItemStack(Material.LEATHER_BOOTS));
    }

    @Override
    public void alEntrar(Player p, boolean primeraVez) {
        aguante.put(p.getUniqueId(), aguanteMax(p));
        if (primeraVez) {
            Util.titulo(p, "<dark_purple><bold>TIERRAS PERDIDAS", "<gray>Morir cuesta caro", 800, 3500, 1000);
            Util.msg(p, "<light_purple>RPG: <gray>la barra de experiencia es tu <green>aguante<gray>. "
                    + "<white>F<gray> esquiva (con invulnerabilidad breve). Clic derecho a una <gold>fogata encendida<gray> "
                    + "para descansar, llenar el <gold>Estus<gray> y subir de nivel. No hay regeneración natural. "
                    + "Si morís, tus almas quedan donde caíste: volvé a buscarlas.");
        } else {
            Util.titulo(p, "<dark_purple><bold>RPG", "<gray>" + Util.num(plugin.almacen().de(p).almas) + " almas", 200, 1500, 500);
        }
    }

    @Override
    public void alSalir(Player p) {
        UUID id = p.getUniqueId();
        aguante.remove(id);
        sinAliento.remove(id);
        invulnerableHasta.remove(id);
        if (barra != null) p.hideBossBar(barra);
    }

    @Override
    public void alReaparecer(Player p) {
        prepararAtributos(p);
        p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
        rellenarEstus(p);
        aguante.put(p.getUniqueId(), aguanteMax(p));
        DatosJugador d = plugin.almacen().de(p);
        Util.titulo(p, "<dark_red>HAS MUERTO", d.mancha != null ? "<gray>Tus almas te esperan donde caíste" : "",
                600, 2000, 1200);
    }

    public void apagar() {
        LivingEntity j = jefe();
        if (j != null) j.remove();
        for (UUID s : siervos) {
            Entity e = Bukkit.getEntity(s);
            if (e != null) e.remove();
        }
        if (barra != null) for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(barra);
    }

    // ------------------------------------------------------------------ aguante

    private void gastar(Player p, double cant) {
        aguante.compute(p.getUniqueId(), (u, v) -> Math.max(0, (v == null ? aguanteMax(p) : v) - cant));
        ultimoGasto.put(p.getUniqueId(), Bukkit.getCurrentTick());
    }

    private double aguanteDe(Player p) {
        return aguante.computeIfAbsent(p.getUniqueId(), u -> aguanteMax(p));
    }

    private void tickAguante() {
        int ahora = Bukkit.getCurrentTick();
        for (Player p : mundo().getPlayers()) {
            if (p.isDead() || p.getGameMode() != GameMode.SURVIVAL) continue;
            UUID id = p.getUniqueId();
            double max = aguanteMax(p);
            double s = aguanteDe(p);
            if (p.isSprinting()) {
                s -= 0.7;
                ultimoGasto.put(id, ahora);
            } else if (ahora - ultimoGasto.getOrDefault(id, 0) > 12) {
                s += p.isBlocking() ? 0.5 : 1.5;
            }
            s = Math.max(0, Math.min(max, s));
            aguante.put(id, s);
            if (s <= 1 && sinAliento.add(id)) {
                p.setFoodLevel(6); // con 6 de hambre no se puede correr
                p.setSprinting(false);
            } else if (s >= max * 0.3 && sinAliento.remove(id)) {
                p.setFoodLevel(20);
            }
            float exp = (float) (s / max);
            if (Math.abs(p.getExp() - exp) > 0.004f) p.setExp(Math.min(0.999f, exp));
            int nivel = plugin.almacen().de(p).nivelRpg();
            if (p.getLevel() != nivel) p.setLevel(nivel);
        }
    }

    @EventHandler
    public void alHambre(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && enRpg(p)) e.setCancelled(true);
    }

    @EventHandler
    public void alGanarExp(PlayerExpChangeEvent e) {
        if (enRpg(e.getPlayer())) e.setAmount(0);
    }

    @EventHandler
    public void alAbrirMesa(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p) || !enRpg(p)) return;
        InventoryType t = e.getInventory().getType();
        if (t == InventoryType.ENCHANTING || t == InventoryType.ANVIL) {
            e.setCancelled(true);
            Util.barra(p, "<dark_purple>Esa magia no funciona en estas tierras. Subí de nivel en las hogueras.");
        }
    }

    // ------------------------------------------------------------------ esquive

    @EventHandler
    public void alEsquivar(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!enRpg(p)) return;
        e.setCancelled(true);
        int ahora = Bukkit.getCurrentTick();
        if (ahora - ultimoEsquive.getOrDefault(p.getUniqueId(), -100) < 12) return;
        if (aguanteDe(p) < COSTO_ESQUIVE) {
            Util.barra(p, "<red>Sin aguante para esquivar");
            return;
        }
        ultimoEsquive.put(p.getUniqueId(), ahora);
        gastar(p, COSTO_ESQUIVE);

        double yaw = Math.toRadians(p.getLocation().getYaw());
        Vector frente = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        Vector derecha = new Vector(-frente.getZ(), 0, frente.getX());
        Input in = p.getCurrentInput();
        Vector dir = new Vector();
        if (in.isForward()) dir.add(frente);
        if (in.isBackward()) dir.subtract(frente);
        if (in.isRight()) dir.add(derecha);
        if (in.isLeft()) dir.subtract(derecha);
        if (dir.lengthSquared() < 0.01) dir = frente.clone().multiply(-1);
        p.setVelocity(dir.normalize().multiply(1.05).setY(0.22));
        invulnerableHasta.put(p.getUniqueId(), ahora + IFRAMES);
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.2, 0), 8, 0.3, 0.05, 0.3, 0.02);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5f, 1.7f);
    }

    // ------------------------------------------------------------------ combate

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alRecibirDanio(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !enRpg(p)) return;
        Integer hasta = invulnerableHasta.get(p.getUniqueId());
        if (hasta != null && Bukkit.getCurrentTick() <= hasta && e.getCause() != EntityDamageEvent.DamageCause.VOID) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alGolpe(EntityDamageByEntityEvent e) {
        if (!enRpg(e.getEntity())) return;
        Player atacante = TresModos.jugadorAtacante(e.getDamager());
        // Cooperativo: nada de PvP en el RPG
        if (atacante != null && e.getEntity() instanceof Player victima && victima != atacante) {
            e.setCancelled(true);
            return;
        }
        // Bloqueo con escudo: cuesta aguante; sin aguante se rompe la guardia
        if (e.getEntity() instanceof Player v && v.isBlocking()) {
            gastar(v, COSTO_BLOQUEO);
            if (aguanteDe(v) <= 0) {
                v.setCooldown(Material.SHIELD, 60);
                Util.barra(v, "<red><bold>¡Guardia rota!");
                v.playSound(v, Sound.ITEM_SHIELD_BREAK, 1f, 0.8f);
            }
        }
        if (e.getDamager() instanceof Player p && !Armas.aplicandoBala
                && e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            DatosJugador d = plugin.almacen().de(p);
            double danio = e.getDamage() * (1 + 0.06 * d.fuerza);
            if (aguanteDe(p) < COSTO_ATAQUE) {
                danio *= 0.3;
                Util.barra(p, "<red>Sin aguante: golpe débil");
            }
            gastar(p, COSTO_ATAQUE);
            e.setDamage(danio);
        }
        if (atacante != null && isJefe(e.getEntity())) participantes.add(atacante.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void alDisparar(EntityShootBowEvent e) {
        if (e.getEntity() instanceof Player p && enRpg(p)) gastar(p, COSTO_ARCO);
    }

    @EventHandler(ignoreCancelled = true)
    public void alAparecerMob(CreatureSpawnEvent e) {
        if (!enRpg(e.getEntity()) || !(e.getEntity() instanceof Enemy)) return;
        if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return;
        LivingEntity m = e.getEntity();
        AttributeInstance vida = m.getAttribute(Attribute.MAX_HEALTH);
        if (vida != null) {
            vida.setBaseValue(vida.getBaseValue() * 1.5);
            m.setHealth(vida.getValue());
        }
        AttributeInstance fuerza = m.getAttribute(Attribute.ATTACK_DAMAGE);
        if (fuerza != null) fuerza.setBaseValue(fuerza.getBaseValue() * 1.3);
    }

    @EventHandler
    public void alMorirEntidad(EntityDeathEvent e) {
        LivingEntity v = e.getEntity();
        if (!enRpg(v) || v instanceof Player) return;
        e.setDroppedExp(0);
        if (isJefe(v)) {
            jefeDerrotado(e);
            return;
        }
        Player asesino = v.getKiller();
        if (asesino == null) return;
        long almas;
        if (siervos.remove(v.getUniqueId())) almas = 150;
        else {
            AttributeInstance vida = v.getAttribute(Attribute.MAX_HEALTH);
            almas = Math.round((vida == null ? 10 : vida.getValue()) * 2.5);
        }
        DatosJugador d = plugin.almacen().de(asesino);
        d.almas += almas;
        Util.barra(asesino, "<gold>+" + almas + " almas");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void alMorir(PlayerDeathEvent e) {
        Player p = e.getPlayer();
        if (!enRpg(p)) return;
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
        DatosJugador d = plugin.almacen().de(p);
        if (d.mancha != null && d.almasMancha > 0) {
            Util.msg(p, "<dark_red>Perdiste " + Util.num(d.almasMancha) + " almas para siempre.");
        }
        if (d.almas > 0) {
            d.mancha = p.getLocation();
            d.almasMancha = d.almas;
            d.almas = 0;
        } else {
            d.mancha = null;
            d.almasMancha = 0;
        }
        net.kyori.adventure.text.Component msg = e.deathMessage();
        e.deathMessage(null);
        if (msg != null) for (Player o : mundo().getPlayers()) o.sendMessage(msg.color(net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY));
    }

    // ------------------------------------------------------------------ manchas de sangre

    private void tickManchas() {
        for (Player p : mundo().getPlayers()) {
            if (p.isDead()) continue;
            DatosJugador d = plugin.almacen().de(p);
            if (d.mancha == null || d.mancha.getWorld() != mundo()) continue;
            if (!d.mancha.isChunkLoaded()) continue;
            new ParticleBuilder(Particle.DUST).location(d.mancha.clone().add(0, 0.3, 0)).count(10).offset(0.25, 0.2, 0.25)
                    .data(new Particle.DustOptions(Color.fromRGB(150, 0, 0), 1.3f)).receivers(p).force(true).spawn();
            new ParticleBuilder(Particle.SOUL).location(d.mancha.clone().add(0, 0.6, 0)).count(2).offset(0.2, 0.3, 0.2)
                    .extra(0.01).receivers(p).force(true).spawn();
            if (p.getLocation().distanceSquared(d.mancha) < 1.8 * 1.8) {
                d.almas += d.almasMancha;
                Util.titulo(p, "<gold><bold>ALMAS RECUPERADAS", "<gold>+" + Util.num(d.almasMancha), 200, 1800, 500);
                p.playSound(p, Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.8f);
                p.playSound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.6f);
                d.mancha = null;
                d.almasMancha = 0;
            }
        }
    }

    // ------------------------------------------------------------------ hogueras

    @EventHandler(priority = EventPriority.HIGH)
    public void alInteractuar(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!enRpg(p) || e.getHand() != EquipmentSlot.HAND) return;
        ItemStack mano = e.getItem();
        if ((e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)
                && Util.marca(mano, Claves.CAMPANA) != null) {
            e.setCancelled(true);
            if (invocarJefe(p)) mano.setAmount(mano.getAmount() - 1);
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Block b = e.getClickedBlock();
        if (b.getType() != Material.CAMPFIRE && b.getType() != Material.SOUL_CAMPFIRE) return;
        if (mano != null && (mano.getType().isEdible() || mano.getType() == Material.FLINT_AND_STEEL
                || mano.getType() == Material.FIRE_CHARGE)) return;
        e.setCancelled(true);
        if (!(b.getBlockData() instanceof Campfire c) || !c.isLit()) {
            Util.barra(p, "<gray>La hoguera está apagada. Encendela con un encendedor.");
            return;
        }
        descansar(p, b);
        abrirHoguera(p);
    }

    private void descansar(Player p, Block fuego) {
        DatosJugador d = plugin.almacen().de(p);
        Location lugar = lugarJunto(fuego);
        boolean nueva = d.hoguera == null || d.hoguera.getWorld() != lugar.getWorld()
                || d.hoguera.distanceSquared(lugar) > 4;
        d.hoguera = lugar;
        prepararAtributos(p);
        p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
        p.setFireTicks(0);
        for (PotionEffect ef : p.getActivePotionEffects()) {
            if (ef.getType() == PotionEffectType.POISON || ef.getType() == PotionEffectType.WITHER) p.removePotionEffect(ef.getType());
        }
        rellenarEstus(p);
        aguante.put(p.getUniqueId(), aguanteMax(p));
        p.getWorld().spawnParticle(Particle.FLAME, fuego.getLocation().add(0.5, 0.8, 0.5), 20, 0.25, 0.4, 0.25, 0.02);
        p.playSound(p, Sound.ITEM_FIRECHARGE_USE, 0.7f, 0.7f);
        if (nueva) {
            Util.titulo(p, "<gold><bold>HOGUERA ENCENDIDA", "", 400, 1800, 800);
            p.playSound(p, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 0.6f);
        }
    }

    /** Un lugar libre al lado de la fogata para reaparecer (no encima: quema). */
    private Location lugarJunto(Block fuego) {
        for (BlockFace f : new BlockFace[]{BlockFace.SOUTH, BlockFace.EAST, BlockFace.NORTH, BlockFace.WEST}) {
            Block b = fuego.getRelative(f);
            if (b.isPassable() && b.getRelative(BlockFace.UP).isPassable() && b.getRelative(BlockFace.DOWN).isSolid()) {
                Location l = b.getLocation().add(0.5, 0, 0.5);
                l.setDirection(fuego.getLocation().add(0.5, 0, 0.5).toVector().subtract(l.toVector()));
                return l;
            }
        }
        return fuego.getLocation().add(0.5, 1, 0.5);
    }

    private long costoNivel(DatosJugador d) {
        long n = d.nivelRpg();
        return 60 + 25 * n * n;
    }

    public void abrirHoguera(Player p) {
        DatosJugador d = plugin.almacen().de(p);
        long costo = costoNivel(d);
        Menu m = new Menu(3, "<dark_gray>Hoguera · " + Util.num(d.almas) + " almas");
        m.poner(10, Util.item(Material.GOLDEN_APPLE, "<red><bold>Vigor <white>" + d.vigor,
                "+2 de vida máxima por nivel.", "<gold>Costo: " + Util.num(costo) + " almas"), pl -> subir(pl, 0));
        m.poner(12, Util.item(Material.FEATHER, "<green><bold>Aguante <white>" + d.aguante,
                "+10 de aguante máximo por nivel.", "<gold>Costo: " + Util.num(costo) + " almas"), pl -> subir(pl, 1));
        m.poner(14, Util.item(Material.IRON_SWORD, "<gold><bold>Fuerza <white>" + d.fuerza,
                "+6% de daño cuerpo a cuerpo.", "<gold>Costo: " + Util.num(costo) + " almas"), pl -> subir(pl, 2));
        long costoEstus = 1500L * (d.estusMax - 2);
        ItemStack iconoEstus = estus(d.estusMax, d.estusMax);
        iconoEstus.lore(List.of(
                Util.mmItem(d.estusMax >= 8 ? "<gray>Máximo alcanzado" : "<gray>Clic: +1 carga"),
                Util.mmItem(d.estusMax >= 8 ? "" : "<gold>Costo: " + Util.num(costoEstus) + " almas")));
        m.poner(16, iconoEstus, this::mejorarEstus);
        m.poner(20, Util.item(Material.BELL, "<dark_red><bold>Campana del Jefe", "Despierta al Caballero del Abismo.",
                "Mejor ir acompañado.", "<gold>Costo: 1.000 almas"), pl -> {
            if (gastarAlmas(pl, 1000)) {
                pl.getInventory().addItem(campana());
                pl.playSound(pl, Sound.BLOCK_BELL_USE, 1f, 0.6f);
            }
            abrirHoguera(pl);
        });
        m.poner(24, Util.item(Material.OAK_DOOR, "<white>Volver al lobby"), pl -> {
            pl.closeInventory();
            plugin.cambio().cambiar(pl, Modo.LOBBY, false);
        });
        m.abrir(p);
    }

    private boolean gastarAlmas(Player p, long cant) {
        DatosJugador d = plugin.almacen().de(p);
        if (d.almas < cant) {
            Util.msg(p, "<red>Te faltan " + Util.num(cant - d.almas) + " almas.");
            p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
            return false;
        }
        d.almas -= cant;
        return true;
    }

    private void subir(Player p, int stat) {
        DatosJugador d = plugin.almacen().de(p);
        if (!gastarAlmas(p, costoNivel(d))) return;
        switch (stat) {
            case 0 -> d.vigor++;
            case 1 -> d.aguante++;
            default -> d.fuerza++;
        }
        prepararAtributos(p);
        p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
        p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
        abrirHoguera(p);
    }

    private void mejorarEstus(Player p) {
        DatosJugador d = plugin.almacen().de(p);
        if (d.estusMax >= 8) return;
        if (!gastarAlmas(p, 1500L * (d.estusMax - 2))) return;
        d.estusMax++;
        rellenarEstus(p);
        p.playSound(p, Sound.BLOCK_BREWING_STAND_BREW, 1f, 1f);
        abrirHoguera(p);
    }

    // ------------------------------------------------------------------ Estus

    private ItemStack estus(int cargas, int max) {
        ItemStack it = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) it.getItemMeta();
        meta.setColor(cargas > 0 ? Color.ORANGE : Color.GRAY);
        meta.displayName(Util.mmItem((cargas > 0 ? "<gold>" : "<gray>") + "<bold>Frasco de Estus</bold> <white>(" + cargas + "/" + max + ")"));
        meta.lore(List.of(Util.mmItem("<gray>Cura casi la mitad de tu vida."), Util.mmItem("<gray>Se rellena en las hogueras.")));
        meta.getPersistentDataContainer().set(Claves.ESTUS, PersistentDataType.INTEGER, cargas);
        it.setItemMeta(meta);
        return it;
    }

    private void rellenarEstus(Player p) {
        int max = plugin.almacen().de(p).estusMax;
        var inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it != null && it.hasItemMeta() && it.getItemMeta().getPersistentDataContainer().has(Claves.ESTUS)) {
                inv.setItem(i, estus(max, max));
            }
        }
    }

    private int cargasEstus(Player p) {
        for (ItemStack it : p.getInventory()) {
            if (it == null || !it.hasItemMeta()) continue;
            Integer c = it.getItemMeta().getPersistentDataContainer().get(Claves.ESTUS, PersistentDataType.INTEGER);
            if (c != null) return c;
        }
        return -1;
    }

    @EventHandler
    public void alBeber(PlayerItemConsumeEvent e) {
        ItemStack it = e.getItem();
        if (!it.hasItemMeta()) return;
        Integer cargas = it.getItemMeta().getPersistentDataContainer().get(Claves.ESTUS, PersistentDataType.INTEGER);
        if (cargas == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        int max = plugin.almacen().de(p).estusMax;
        if (cargas <= 0) {
            Util.barra(p, "<gray>El frasco está vacío. Descansá en una hoguera.");
        } else {
            double vidaMax = p.getAttribute(Attribute.MAX_HEALTH).getValue();
            p.setHealth(Math.min(vidaMax, p.getHealth() + vidaMax * 0.45));
            p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.01);
            p.playSound(p, Sound.ITEM_HONEY_BOTTLE_DRINK, 1f, 0.8f);
            p.getInventory().setItem(e.getHand(), estus(cargas - 1, max));
        }
        Bukkit.getScheduler().runTask(plugin, p::updateInventory);
    }

    // ------------------------------------------------------------------ jefe

    private ItemStack campana() {
        return Util.marcar(Util.item(Material.BELL, "<dark_red><bold>Campana del Jefe",
                "Clic derecho en un lugar abierto.", "Despierta al Caballero del Abismo."), Claves.CAMPANA, "1");
    }

    private boolean isJefe(Entity e) {
        return e.getPersistentDataContainer().has(Claves.JEFE);
    }

    private LivingEntity jefe() {
        if (jefeId == null) return null;
        Entity e = Bukkit.getEntity(jefeId);
        return e instanceof LivingEntity le && le.isValid() ? le : null;
    }

    public boolean invocarJefe(Player p) {
        if (jefe() != null) {
            Util.msg(p, "<red>El Caballero del Abismo ya está despierto en algún lugar.");
            return false;
        }
        World w = p.getWorld();
        Vector frente = p.getLocation().getDirection().setY(0);
        if (frente.lengthSquared() < 0.01) frente = new Vector(1, 0, 0);
        Location l = p.getLocation().add(frente.normalize().multiply(8));
        l.setY(w.getHighestBlockYAt(l) + 1);
        WitherSkeleton j = w.spawn(l, WitherSkeleton.class, CreatureSpawnEvent.SpawnReason.CUSTOM, s -> {
            s.customName(Util.mm("<dark_red><bold>Caballero del Abismo"));
            s.setCustomNameVisible(true);
            base(s, Attribute.SCALE, 1.6);
            base(s, Attribute.MAX_HEALTH, 400);
            s.setHealth(400);
            base(s, Attribute.ATTACK_DAMAGE, 11);
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
            s.getPersistentDataContainer().set(Claves.JEFE, PersistentDataType.BYTE, (byte) 1);
            s.setTarget(p);
        });
        jefeId = j.getUniqueId();
        fase = 1;
        participantes.clear();
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

    private void tickJefe() {
        LivingEntity j = jefe();
        if (j == null) {
            if (jefeId != null) terminarJefe();
            return;
        }
        int ahora = Bukkit.getCurrentTick();
        double max = j.getAttribute(Attribute.MAX_HEALTH).getValue();
        float prog = (float) Math.max(0, Math.min(1, j.getHealth() / max));
        barra.progress(prog);

        List<Player> cerca = new ArrayList<>();
        for (Player p : mundo().getPlayers()) {
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
            terminarJefe();
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

        if (ahora >= proximoAtaque && !cerca.isEmpty()) {
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

    private void jefeDerrotado(EntityDeathEvent e) {
        e.getDrops().clear();
        Location l = e.getEntity().getLocation();
        for (UUID id : participantes) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || !enRpg(p)) continue;
            plugin.almacen().de(p).almas += 3000;
            Util.titulo(p, "<gold><bold>ENEMIGO FORMIDABLE DERROTADO", "<gold>+3.000 almas", 500, 3000, 1000);
            p.playSound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
        }
        if (Math.random() < 0.5) {
            ItemStack espada = Util.item(Material.NETHERITE_SWORD, "<dark_red><bold>Espada del Abismo",
                    "Arrancada al Caballero caído.");
            espada.addUnsafeEnchantment(Enchantment.SHARPNESS, 5);
            espada.addUnsafeEnchantment(Enchantment.FIRE_ASPECT, 2);
            ItemMeta meta = espada.getItemMeta();
            meta.getPersistentDataContainer().set(Claves.BOTIN, PersistentDataType.BYTE, (byte) 1);
            espada.setItemMeta(meta);
            l.getWorld().dropItemNaturally(l, espada);
        }
        terminarJefe();
    }

    private void terminarJefe() {
        jefeId = null;
        if (barra != null) for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(barra);
        for (UUID s : siervos) {
            Entity e = Bukkit.getEntity(s);
            if (e != null) e.remove();
        }
        siervos.clear();
        participantes.clear();
    }

    // ------------------------------------------------------------------ sidebar

    @Override
    public String tituloSidebar(Player p) {
        return "<dark_purple><bold>RPG</bold> <gray>Tierras Perdidas";
    }

    @Override
    public List<String> lineasSidebar(Player p) {
        DatosJugador d = plugin.almacen().de(p);
        List<String> l = new ArrayList<>();
        l.add("<white>Almas: <gold>" + Util.num(d.almas));
        l.add("<white>Nivel: <light_purple>" + d.nivelRpg() + " <dark_gray>(próx. " + Util.num(costoNivel(d)) + ")");
        l.add("");
        l.add("<red>Vigor <white>" + d.vigor + "  <green>Aguante <white>" + d.aguante);
        l.add("<gold>Fuerza <white>" + d.fuerza);
        int c = cargasEstus(p);
        l.add("<white>Estus: <gold>" + (c < 0 ? "-" : c + "/" + d.estusMax));
        if (d.mancha != null && d.mancha.getWorld() == p.getWorld()) {
            l.add("");
            l.add("<dark_red>Mancha: <white>" + Util.num(d.almasMancha) + " almas");
            l.add("<dark_red>A <white>" + (int) d.mancha.distance(p.getLocation()) + " m");
        }
        l.add("");
        l.add("<gray>F: esquivar · Fogata: hoguera");
        return l;
    }
}
