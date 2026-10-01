package ar.tresmodos.shooter;

import ar.tresmodos.Armas;
import ar.tresmodos.Claves;
import ar.tresmodos.Modo;
import ar.tresmodos.TresModos;
import ar.tresmodos.Util;
import ar.tresmodos.shooter.ClasesShooter.Letal;
import ar.tresmodos.shooter.ClasesShooter.Tactico;
import ar.tresmodos.shooter.ClasesShooter.Ventaja;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Letales y tácticos: granadas, semtex, hacha, claymore, aturdidora, humo y sensor de latidos. */
public class Equipamiento implements Listener {
    private static final int MECHA_FRAG = 60;
    private static final String[] FLECHAS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private record Claymore(UUID duenio, Location lugar, Vector frente, UUID display) {}

    private record Humo(Location lugar, int fin) {}

    private final TresModos plugin;
    private final ModoShooter modo;
    private final Map<UUID, Integer> cocinando = new HashMap<>();
    private final Map<UUID, Integer> ultimoClic = new HashMap<>();
    private final Map<UUID, Claymore> claymores = new HashMap<>();
    private final List<Humo> humos = new ArrayList<>();
    /** Entidades del equipo en el mundo (granadas, semtex pegadas, hachas): para limpiarlas al reiniciar. */
    private final List<UUID> sueltas = new ArrayList<>();

    Equipamiento(TresModos plugin, ModoShooter modo) {
        this.plugin = plugin;
        this.modo = modo;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    private static boolean activo(Player p) {
        return Modo.de(p.getWorld()) == Modo.SHOOTER;
    }

    // ------------------------------------------------------------------ ítems

    public ItemStack letal(Letal l, int cantidad) {
        return item(l.nombre, l.desc, l.modelo, Claves.LETAL, l.name(), cantidad);
    }

    public ItemStack tactico(Tactico t, int cantidad) {
        return item(t.nombre, t.desc, t.modelo, Claves.TACTICO, t.name(), cantidad);
    }

    private static ItemStack item(String nombre, String desc, String modelo, NamespacedKey clave, String valor, int cant) {
        ItemStack it = Util.item(Material.CLAY_BALL, "<white><bold>" + nombre, desc);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("tresmodos", modelo));
        meta.getPersistentDataContainer().set(clave, PersistentDataType.STRING, valor);
        meta.setMaxStackSize(4);
        it.setItemMeta(meta);
        it.setAmount(cant);
        return it;
    }

    /** Ítem decorativo para mostrar en el mundo (granada lanzada, claymore). */
    private static ItemStack visual(String modelo) {
        ItemStack it = new ItemStack(Material.CLAY_BALL);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("tresmodos", modelo));
        it.setItemMeta(meta);
        return it;
    }

    private static Letal letalDe(ItemStack it) {
        String s = Util.marca(it, Claves.LETAL);
        return s == null ? null : Letal.valueOf(s);
    }

    private static Tactico tacticoDe(ItemStack it) {
        String s = Util.marca(it, Claves.TACTICO);
        return s == null ? null : Tactico.valueOf(s);
    }

    /** Gasta una unidad del ítem marcado con esa clave y valor (aunque ya no esté en la mano). */
    private static void gastar(Player p, NamespacedKey clave, String valor) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (valor.equals(Util.marca(it, clave))) {
                it.setAmount(it.getAmount() - 1);
                inv.setItem(i, it.getAmount() <= 0 ? null : it);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ uso

    @EventHandler(priority = EventPriority.HIGH)
    public void alInteractuar(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack it = e.getItem();
        Letal l = letalDe(it);
        Tactico t = tacticoDe(it);
        if (l == null && t == null) return;
        e.setCancelled(true);
        if (!activo(p) || modo.bloqueaDisparo(p)) return;
        if (l != null) usarLetal(p, l);
        else usarTactico(p, t);
    }

    private void usarLetal(Player p, Letal l) {
        UUID id = p.getUniqueId();
        int ahora = Bukkit.getCurrentTick();
        switch (l) {
            case FRAG -> {
                if (cocinando.putIfAbsent(id, ahora) == null) {
                    p.playSound(p, Sound.ITEM_FLINTANDSTEEL_USE, 0.8f, 1.6f);
                }
                ultimoClic.put(id, ahora);
            }
            case SEMTEX -> {
                lanzar(p, l.modelo, 1.25, "semtex");
                gastar(p, Claves.LETAL, l.name());
            }
            case HACHA -> {
                lanzar(p, l.modelo, 1.9, "hacha");
                gastar(p, Claves.LETAL, l.name());
                p.playSound(p, Sound.ITEM_TRIDENT_THROW, 0.8f, 1.3f);
            }
            case CLAYMORE -> {
                if (!((Entity) p).isOnGround()) {
                    Util.barra(p, "<red>La claymore se pone parado en el piso.");
                    return;
                }
                ponerClaymore(p);
                gastar(p, Claves.LETAL, l.name());
            }
        }
    }

    private void usarTactico(Player p, Tactico t) {
        switch (t) {
            case ATURDIDORA -> {
                lanzar(p, t.modelo, 1.3, "aturdidora");
                gastar(p, Claves.TACTICO, t.name());
            }
            case HUMO -> {
                lanzar(p, t.modelo, 1.2, "humo");
                gastar(p, Claves.TACTICO, t.name());
            }
            case SENSOR -> Util.barra(p, "<gray>El sensor funciona solo con tenerlo en la mano.");
        }
    }

    private void lanzar(Player p, String modelo, double fuerza, String tipo) {
        Snowball s = p.launchProjectile(Snowball.class, p.getEyeLocation().getDirection().multiply(fuerza));
        s.setItem(visual(modelo));
        s.getPersistentDataContainer().set(Claves.EQUIPO, PersistentDataType.STRING, tipo);
        p.playSound(p, Sound.ENTITY_SNOWBALL_THROW, 0.7f, 0.7f);
    }

    // ------------------------------------------------------------------ granada de fragmentación

    private void tirarFrag(Player p, int restante) {
        cocinando.remove(p.getUniqueId());
        ultimoClic.remove(p.getUniqueId());
        gastar(p, Claves.LETAL, Letal.FRAG.name());
        Location ojo = p.getEyeLocation();
        Item g = p.getWorld().dropItem(ojo.clone().add(ojo.getDirection().multiply(0.4)), visual(Letal.FRAG.modelo), i -> {
            i.setPickupDelay(Integer.MAX_VALUE);
            i.setCanPlayerPickup(false);
            i.setCanMobPickup(false);
            i.setUnlimitedLifetime(true);
            i.getPersistentDataContainer().set(Claves.EQUIPO, PersistentDataType.STRING, "frag");
        });
        g.setVelocity(ojo.getDirection().multiply(1.05).add(new Vector(0, 0.18, 0)));
        sueltas.add(g.getUniqueId());
        p.playSound(p, Sound.ENTITY_SNOWBALL_THROW, 0.8f, 0.5f);
        UUID autor = p.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            sueltas.remove(g.getUniqueId());
            if (!g.isValid()) return;
            Location l = g.getLocation();
            g.remove();
            plugin.armas().explotar(l, 3.3f, Bukkit.getPlayer(autor), "Granada");
        }, Math.max(1, restante));
    }

    // ------------------------------------------------------------------ claymore

    private void ponerClaymore(Player p) {
        Claymore vieja = claymores.remove(p.getUniqueId());
        if (vieja != null) quitar(vieja.display());
        Vector frente = p.getLocation().getDirection().setY(0);
        if (frente.lengthSquared() < 0.01) frente = new Vector(0, 0, 1);
        frente.normalize();
        Location l = p.getLocation().clone().add(frente.clone().multiply(0.6));
        l.setY(Math.floor(l.getY()) + 0.25);
        l.setYaw(p.getLocation().getYaw());
        l.setPitch(0);
        ItemDisplay d = p.getWorld().spawn(l, ItemDisplay.class, x -> {
            x.setItemStack(visual(Letal.CLAYMORE.modelo));
            x.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.6f), new AxisAngle4f()));
            x.setPersistent(false);
        });
        claymores.put(p.getUniqueId(), new Claymore(p.getUniqueId(), l, frente, d.getUniqueId()));
        p.playSound(l, Sound.BLOCK_TRIPWIRE_ATTACH, 1f, 0.8f);
        Util.barra(p, "<gray>Claymore puesta mirando al frente.");
    }

    private void tickClaymores() {
        Iterator<Map.Entry<UUID, Claymore>> itr = claymores.entrySet().iterator();
        while (itr.hasNext()) {
            Claymore c = itr.next().getValue();
            World w = c.lugar().getWorld();
            for (Player o : w.getPlayers()) {
                if (o.getUniqueId().equals(c.duenio()) || o.isDead() || o.getGameMode() != GameMode.ADVENTURE) continue;
                Vector hacia = o.getLocation().toVector().subtract(c.lugar().toVector());
                if (Math.abs(hacia.getY()) > 1.8) continue;
                hacia.setY(0);
                double dist = hacia.length();
                if (dist > 3.2 || dist < 0.05 || hacia.normalize().dot(c.frente()) < 0.35) continue;
                itr.remove();
                w.playSound(c.lugar(), Sound.BLOCK_NOTE_BLOCK_BIT, 1.5f, 2f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    quitar(c.display());
                    plugin.armas().explotar(c.lugar().clone().add(c.frente().clone().multiply(1.2)), 3.2f,
                            Bukkit.getPlayer(c.duenio()), "Claymore");
                }, 5);
                break;
            }
        }
    }

    // ------------------------------------------------------------------ impactos de lo lanzado

    @EventHandler
    public void alImpactar(ProjectileHitEvent e) {
        Projectile pr = e.getEntity();
        String tipo = pr.getPersistentDataContainer().get(Claves.EQUIPO, PersistentDataType.STRING);
        if (tipo == null || !List.of("semtex", "hacha", "aturdidora", "humo").contains(tipo)) return;
        Player autor = pr.getShooter() instanceof Player p ? p : null;
        Location l = pr.getLocation();
        Entity golpeado = e.getHitEntity();
        if (golpeado != null && golpeado == autor) {
            e.setCancelled(true);
            return;
        }
        pr.remove();
        switch (tipo) {
            case "semtex" -> pegarSemtex(autor, l, golpeado);
            case "hacha" -> hacha(autor, l, golpeado);
            case "aturdidora" -> aturdir(autor, l);
            case "humo" -> {
                humos.add(new Humo(l.clone().add(0, 0.5, 0), Bukkit.getCurrentTick() + 120));
                l.getWorld().playSound(l, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.6f);
            }
            default -> { }
        }
    }

    private void pegarSemtex(Player autor, Location l, Entity golpeado) {
        Item s = l.getWorld().dropItem(l, visual(Letal.SEMTEX.modelo), i -> {
            i.setPickupDelay(Integer.MAX_VALUE);
            i.setCanPlayerPickup(false);
            i.setCanMobPickup(false);
            i.setGravity(false);
            i.setVelocity(new Vector());
            i.getPersistentDataContainer().set(Claves.EQUIPO, PersistentDataType.STRING, "semtex");
        });
        sueltas.add(s.getUniqueId());
        if (golpeado instanceof LivingEntity le) {
            le.addPassenger(s);
            if (le instanceof Player v) Util.titulo(v, "", "<red><bold>¡Tenés un semtex pegado!", 0, 1200, 200);
            if (autor != null) Util.barra(autor, "<gold>¡Semtex pegado!");
        }
        l.getWorld().playSound(l, Sound.BLOCK_SLIME_BLOCK_PLACE, 1f, 1.4f);
        UUID id = autor == null ? null : autor.getUniqueId();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            sueltas.remove(s.getUniqueId());
            if (!s.isValid()) return;
            Location donde = s.getLocation();
            s.remove();
            plugin.armas().explotar(donde, 3.2f, id == null ? null : Bukkit.getPlayer(id), "Semtex");
        }, 40);
    }

    private void hacha(Player autor, Location l, Entity golpeado) {
        if (golpeado instanceof LivingEntity le && autor != null && plugin.armas().blanco(autor, le)) {
            Armas.danioDirecto(le, 1000, autor, "Hacha arrojadiza", false);
            l.getWorld().playSound(l, Sound.ITEM_TRIDENT_HIT, 1f, 0.8f);
        }
        if (autor == null) return;
        // Queda en el piso para que el dueño la junte.
        Item h = l.getWorld().dropItem(l, visual(Letal.HACHA.modelo), i -> {
            i.setPickupDelay(10);
            i.setCanMobPickup(false);
            i.getPersistentDataContainer().set(Claves.EQUIPO, PersistentDataType.STRING, "hacha:" + autor.getUniqueId());
        });
        sueltas.add(h.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            sueltas.remove(h.getUniqueId());
            if (h.isValid()) h.remove();
        }, 400);
    }

    private void aturdir(Player autor, Location l) {
        World w = l.getWorld();
        w.spawnParticle(Particle.FLASH, l, 1, 0, 0, 0, 0, org.bukkit.Color.WHITE);
        w.spawnParticle(Particle.EXPLOSION, l, 1);
        w.playSound(l, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.4f, 0.6f);
        for (Player o : w.getPlayers()) {
            if (o.isDead() || o.getGameMode() != GameMode.ADVENTURE) continue;
            double d = o.getEyeLocation().distance(l);
            if (d > 6.5) continue;
            Vector dir = o.getEyeLocation().toVector().subtract(l.toVector());
            if (w.rayTraceBlocks(l, dir.clone().normalize(), d, FluidCollisionMode.NEVER, true) != null) continue;
            int dur = d < 3 ? 50 : 35;
            o.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, dur, 2, false, false, false));
            o.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, dur + 40, 0, false, false, false));
            o.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, dur, 1, false, false, false));
            plugin.armas().dejarDeApuntar(o);
            if (autor != null && o != autor) plugin.armas().registrarImpacto(o, autor, "Aturdidora", false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void alJuntar(EntityPickupItemEvent e) {
        PersistentDataContainer pdc = e.getItem().getPersistentDataContainer();
        String tipo = pdc.get(Claves.EQUIPO, PersistentDataType.STRING);
        if (tipo == null) return;
        e.setCancelled(true);
        if (!tipo.startsWith("hacha:") || !(e.getEntity() instanceof Player p)) return;
        if (!tipo.substring(6).equals(p.getUniqueId().toString())) return;
        e.getItem().remove();
        sueltas.remove(e.getItem().getUniqueId());
        PlayerInventory inv = p.getInventory();
        ItemStack slot = inv.getItem(2);
        if (Letal.HACHA.name().equals(Util.marca(slot, Claves.LETAL))) slot.setAmount(slot.getAmount() + 1);
        else inv.addItem(letal(Letal.HACHA, 1));
        p.playSound(p, Sound.ITEM_ARMOR_EQUIP_CHAIN, 0.8f, 1.2f);
    }

    // ------------------------------------------------------------------ ciclo

    private void tick() {
        int ahora = Bukkit.getCurrentTick();
        // Granadas cocinándose: se lanzan al soltar el clic; si se pasa la mecha, explotan en la mano.
        for (UUID id : new ArrayList<>(cocinando.keySet())) {
            Player p = Bukkit.getPlayer(id);
            int inicio = cocinando.get(id);
            if (p == null || p.isDead() || !activo(p)) {
                cocinando.remove(id);
                ultimoClic.remove(id);
                continue;
            }
            int pasado = ahora - inicio;
            if (pasado >= MECHA_FRAG) {
                cocinando.remove(id);
                ultimoClic.remove(id);
                gastar(p, Claves.LETAL, Letal.FRAG.name());
                plugin.armas().explotar(p.getLocation().add(0, 1, 0), 3.3f, p, "Granada");
            } else if (ahora - ultimoClic.getOrDefault(id, inicio) > 5) {
                tirarFrag(p, MECHA_FRAG - pasado);
            } else if (pasado % 4 == 0) {
                int lleno = Math.round(pasado / (float) MECHA_FRAG * 10);
                Util.barra(p, "<red>Cocinando <yellow>" + "▰".repeat(lleno) + "<dark_gray>" + "▱".repeat(10 - lleno));
            }
        }
        if (ahora % 2 == 0) tickClaymores();
        if (ahora % 3 == 0) {
            humos.removeIf(h -> h.fin() < ahora);
            for (Humo h : humos) {
                World w = h.lugar().getWorld();
                w.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, h.lugar(), 10, 2.2, 1.2, 2.2, 0.005, null, true);
                w.spawnParticle(Particle.WHITE_SMOKE, h.lugar(), 25, 2.5, 1.5, 2.5, 0.01, null, true);
            }
        }
        if (ahora % 10 == 0) tickSensor(ahora);
    }

    /** Sensor de latidos en la mano: enemigos a menos de 30 bloques, con distancia y dirección. */
    private void tickSensor(int ahora) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!activo(p) || p.isDead() || tacticoDe(p.getInventory().getItemInMainHand()) != Tactico.SENSOR) continue;
            StringBuilder sb = new StringBuilder("<green>♥ Sensor:");
            int n = 0;
            for (Player o : p.getWorld().getPlayers()) {
                if (o == p || o.isDead() || o.getGameMode() != GameMode.ADVENTURE) continue;
                if (modo.clases().tiene(o, Ventaja.FANTASMA)) continue;
                double d = o.getLocation().distance(p.getLocation());
                if (d > 30) continue;
                Vector hacia = o.getLocation().toVector().subtract(p.getLocation().toVector());
                double ang = Math.toDegrees(Math.atan2(-hacia.getX(), hacia.getZ())) - p.getLocation().getYaw();
                int idx = Math.floorMod(Math.round((float) (ang / 45.0)), 8);
                sb.append(" <red>").append(FLECHAS[idx]).append(" ").append((int) d).append("m");
                if (++n >= 4) break;
            }
            if (n == 0) sb.append(" <gray>nadie cerca");
            Util.barra(p, sb.toString());
            if (n > 0 && ahora % 20 == 0) p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 0.5f, 1.6f);
        }
    }

    private static void quitar(UUID id) {
        Entity e = Bukkit.getEntity(id);
        if (e != null) e.remove();
    }

    /** Saca del mundo todo lo lanzado o puesto (nueva partida). */
    void limpiar() {
        for (Claymore c : claymores.values()) quitar(c.display());
        claymores.clear();
        for (UUID id : sueltas) quitar(id);
        sueltas.clear();
        humos.clear();
        cocinando.clear();
        ultimoClic.clear();
    }

    void olvidar(UUID id) {
        cocinando.remove(id);
        ultimoClic.remove(id);
    }
}
