package ar.tresmodos.shooter;

import ar.tresmodos.Armas;
import ar.tresmodos.Claves;
import ar.tresmodos.Modo;
import ar.tresmodos.TresModos;
import ar.tresmodos.Util;
import ar.tresmodos.shooter.ClasesShooter.Ventaja;
import com.destroystokyo.paper.event.player.PlayerStopSpectatingEntityEvent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Rachas por bajas seguidas sin morir: UAV, paquete de ayuda, Predator, bombardeo, perros y la bomba. */
public class Rachas implements Listener {

    public enum Racha {
        UAV(3, "UAV", "30 s viendo a los enemigos en el minimapa.", "uav"),
        PAQUETE(4, "Paquete de ayuda", "Tirá la bengala: cae munición, otra racha o un arma.", "bengala"),
        PREDATOR(5, "Misil Predator", "Manejás un misil desde el cielo (WASD, espacio acelera).", "predator"),
        BOMBARDEO(7, "Bombardeo de precisión", "Línea de explosiones donde apuntás.", "radio"),
        PERROS(11, "Perros de ataque", "4 perros cazan enemigos durante 30 s.", "silbato"),
        BOMBA(25, "Bomba atómica", "Gana la partida en el acto y detona el pueblo.", "bomba");

        public final int bajas;
        public final String nombre, desc, modelo;

        Racha(int bajas, String nombre, String desc, String modelo) {
            this.bajas = bajas;
            this.nombre = nombre;
            this.desc = desc;
            this.modelo = modelo;
        }
    }

    private static final class Paquete {
        final UUID duenio;
        final ItemDisplay caja;
        final Location suelo;
        boolean aterrizado;
        final Map<UUID, Integer> progreso = new HashMap<>();

        Paquete(UUID duenio, ItemDisplay caja, Location suelo) {
            this.duenio = duenio;
            this.caja = caja;
            this.suelo = suelo;
        }
    }

    private static final class Misil {
        final UUID duenio;
        final ItemDisplay cuerpo;
        final Location volver;
        float yaw, pitch;
        int fin;

        Misil(UUID duenio, ItemDisplay cuerpo, Location volver) {
            this.duenio = duenio;
            this.cuerpo = cuerpo;
            this.volver = volver;
        }
    }

    private final TresModos plugin;
    private final ModoShooter modo;
    private final Random rnd = new Random();
    private final Map<UUID, Integer> uavHasta = new HashMap<>();
    private final List<Paquete> paquetes = new ArrayList<>();
    private final Map<UUID, Misil> misiles = new HashMap<>();
    private final Map<UUID, Input> entradas = new HashMap<>();
    private final Map<UUID, UUID> perros = new HashMap<>();   // perro -> dueño
    private final Map<UUID, Integer> perrosHasta = new HashMap<>();

    Rachas(TresModos plugin, ModoShooter modo) {
        this.plugin = plugin;
        this.modo = modo;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    // ------------------------------------------------------------------ obtener

    public ItemStack item(Racha r) {
        ItemStack it = Util.item(Material.CLAY_BALL, "<gold><bold>" + r.nombre, r.desc, "<yellow>Clic derecho para usar");
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("tresmodos", r.modelo));
        meta.getPersistentDataContainer().set(Claves.RACHA, PersistentDataType.STRING, r.name());
        it.setItemMeta(meta);
        return it;
    }

    /** Bajas que cuesta una racha para este jugador (Intransigente descuenta una, salvo la bomba). */
    int costo(Player p, Racha r) {
        if (r == Racha.BOMBA) return r.bajas;
        return r.bajas - (modo.clases().tiene(p, Ventaja.INTRANSIGENTE) ? 1 : 0);
    }

    /** Llamado al sumar una baja: entrega las rachas que se completaron. */
    void alSumarBaja(Player p, int racha) {
        for (Racha r : Racha.values()) {
            if (racha != costo(p, r)) continue;
            dar(p, r);
            modo.anunciar("<gold>" + p.getName() + " <gray>consiguió <gold>" + r.nombre + " <gray>(" + racha + " seguidas)");
        }
    }

    void dar(Player p, Racha r) {
        var sobrante = p.getInventory().addItem(item(r));
        if (!sobrante.isEmpty()) p.getInventory().setItem(7, item(r));
        Util.titulo(p, "", "<gold>Racha: <bold>" + r.nombre + "</bold> <gray>(clic derecho)", 100, 1800, 300);
        p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void alInteractuar(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        String s = Util.marca(e.getItem(), Claves.RACHA);
        if (s == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (Modo.de(p.getWorld()) != Modo.SHOOTER || modo.bloqueaDisparo(p)) return;
        Racha r = Racha.valueOf(s);
        boolean usada = switch (r) {
            case UAV -> uav(p);
            case PAQUETE -> paquete(p);
            case PREDATOR -> predator(p);
            case BOMBARDEO -> bombardeo(p);
            case PERROS -> perros(p);
            case BOMBA -> modo.bombaAtomica(p);
        };
        if (usada) {
            ItemStack it = e.getItem();
            it.setAmount(it.getAmount() - 1);
        }
    }

    // ------------------------------------------------------------------ UAV

    private boolean uav(Player p) {
        uavHasta.put(p.getUniqueId(), Bukkit.getCurrentTick() + 600);
        for (Player o : modo.jugadores()) {
            o.playSound(o, Sound.ENTITY_PHANTOM_FLAP, 1f, 0.6f);
            Util.barra(o, o == p ? "<aqua>✈ UAV en línea: los enemigos aparecen en tu minimapa"
                    : "<red>✈ UAV enemigo en línea");
        }
        return true;
    }

    /** true si este jugador tiene un UAV activo. */
    public boolean uavActivo(Player p) {
        return uavHasta.getOrDefault(p.getUniqueId(), 0) > Bukkit.getCurrentTick();
    }

    // ------------------------------------------------------------------ paquete de ayuda

    private boolean paquete(Player p) {
        RayTraceResult r = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 40,
                FluidCollisionMode.NEVER, true);
        Block b = r == null ? null : r.getHitBlock();
        if (b == null || b.getY() > 70) {
            Util.barra(p, "<red>Tirá la bengala a un lugar abierto del piso.");
            return false;
        }
        Location suelo = b.getLocation().add(0.5, 1, 0.5);
        if (p.getWorld().getHighestBlockYAt(suelo) > suelo.getBlockY() + 1) {
            Util.barra(p, "<red>Ahí no llega el helicóptero: buscá un lugar sin techo.");
            return false;
        }
        World w = p.getWorld();
        UUID id = p.getUniqueId();
        for (int i = 0; i < 4; i++) {
            final int k = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> w.spawnParticle(Particle.DUST, suelo.clone().add(0, 0.5 + k * 0.4, 0),
                    25, 0.25, 0.6, 0.25, 0, new Particle.DustOptions(Color.RED, 2.2f), true), i * 10L);
        }
        w.playSound(suelo, Sound.ENTITY_BLAZE_SHOOT, 1f, 0.5f);
        modo.anunciar("<gold>📦 " + p.getName() + " pidió un paquete de ayuda");
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            ItemDisplay caja = w.spawn(suelo.clone().add(0, 35, 0), ItemDisplay.class, d -> {
                d.setItemStack(modelo("paquete", Material.BARREL));
                d.setTransformation(new Transformation(new Vector3f(0, 0.5f, 0), new AxisAngle4f(), new Vector3f(1.1f), new AxisAngle4f()));
                d.setTeleportDuration(2);
                d.setPersistent(false);
                d.setGlowing(true);
            });
            paquetes.add(new Paquete(id, caja, suelo));
            w.playSound(suelo, Sound.ENTITY_WITHER_SHOOT, 0.6f, 0.4f);
        }, 50);
        return true;
    }

    private void tickPaquetes() {
        Iterator<Paquete> itr = paquetes.iterator();
        while (itr.hasNext()) {
            Paquete pq = itr.next();
            if (!pq.caja.isValid()) {
                itr.remove();
                continue;
            }
            if (!pq.aterrizado) {
                Location l = pq.caja.getLocation();
                double y = Math.max(pq.suelo.getY(), l.getY() - 0.35);
                pq.caja.teleport(new Location(l.getWorld(), pq.suelo.getX(), y, pq.suelo.getZ()));
                pq.caja.getWorld().spawnParticle(Particle.CLOUD, pq.caja.getLocation().add(0, 2.2, 0), 2, 0.4, 0.1, 0.4, 0);
                if (y <= pq.suelo.getY()) {
                    pq.aterrizado = true;
                    pq.caja.getWorld().playSound(pq.suelo, Sound.BLOCK_ANVIL_LAND, 0.8f, 0.6f);
                }
                continue;
            }
            // Captura: agachado al lado de la caja (el dueño tarda menos).
            for (Player o : pq.suelo.getWorld().getPlayers()) {
                if (o.isDead() || o.getGameMode() != GameMode.ADVENTURE) continue;
                if (!o.isSneaking() || o.getLocation().distanceSquared(pq.suelo) > 2.4 * 2.4) {
                    pq.progreso.remove(o.getUniqueId());
                    continue;
                }
                int necesario = o.getUniqueId().equals(pq.duenio) ? 20 : 60;
                int v = pq.progreso.merge(o.getUniqueId(), 1, Integer::sum);
                int lleno = Math.min(10, v * 10 / necesario);
                Util.barra(o, "<gold>Abriendo paquete <yellow>" + "▰".repeat(lleno) + "<dark_gray>" + "▱".repeat(10 - lleno));
                if (v >= necesario) {
                    abrir(o, pq);
                    pq.caja.remove();
                    itr.remove();
                    break;
                }
            }
        }
    }

    private void abrir(Player p, Paquete pq) {
        p.playSound(p, Sound.BLOCK_BARREL_OPEN, 1f, 1f);
        int dado = rnd.nextInt(100);
        if (dado < 35) {
            plugin.armas().rellenarReservas(p);
            Util.titulo(p, "", "<green>Paquete: munición completa", 100, 1500, 300);
        } else if (dado < 80) {
            Racha[] posibles = {Racha.UAV, Racha.UAV, Racha.PREDATOR, Racha.BOMBARDEO, Racha.PERROS};
            Racha r = posibles[rnd.nextInt(posibles.length)];
            dar(p, r);
        } else {
            ItemStack arma = Armas.crear(Armas.Tipo.FRANCOTIRADOR);
            Armas.setReservaItem(arma, 20);
            p.getInventory().addItem(arma);
            Util.titulo(p, "", "<gold>Paquete: Barrett M82A1", 100, 1500, 300);
        }
    }

    // ------------------------------------------------------------------ Predator

    private boolean predator(Player p) {
        World w = p.getWorld();
        Location inicio = new Location(w, rnd.nextInt(30) - 15, 125, rnd.nextInt(24) - 12, 0, 75);
        ItemDisplay cuerpo = w.spawn(inicio, ItemDisplay.class, d -> {
            d.setItemStack(modelo("misil", Material.FIREWORK_ROCKET));
            d.setTeleportDuration(1);
            d.setPersistent(false);
            d.setGlowing(true);
        });
        Misil m = new Misil(p.getUniqueId(), cuerpo, p.getLocation());
        m.yaw = 0;
        m.pitch = 75;
        m.fin = Bukkit.getCurrentTick() + 160;
        misiles.put(p.getUniqueId(), m);
        modo.entrarEnRacha(p);
        p.setGameMode(GameMode.SPECTATOR);
        p.setSpectatorTarget(cuerpo);
        for (Player o : modo.jugadores()) {
            o.playSound(o, Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 0.5f);
            if (o != p) Util.barra(o, "<red>🚀 ¡Misil Predator entrante!");
        }
        Util.titulo(p, "", "<gray>W/S cabecear · A/D girar · espacio acelera", 100, 2500, 300);
        return true;
    }

    @EventHandler
    public void alEntrada(PlayerInputEvent e) {
        entradas.put(e.getPlayer().getUniqueId(), e.getInput());
    }

    @EventHandler
    public void alDejarDeMirar(PlayerStopSpectatingEntityEvent e) {
        if (misiles.containsKey(e.getPlayer().getUniqueId())) e.setCancelled(true);
    }

    private void tickMisiles() {
        int ahora = Bukkit.getCurrentTick();
        for (Misil m : new ArrayList<>(misiles.values())) {
            Player p = Bukkit.getPlayer(m.duenio);
            if (p == null || !m.cuerpo.isValid()) {
                terminarMisil(m, false);
                continue;
            }
            Input in = entradas.get(m.duenio);
            if (in != null) {
                if (in.isLeft()) m.yaw -= 3.5f;
                if (in.isRight()) m.yaw += 3.5f;
                if (in.isForward()) m.pitch = Math.min(89, m.pitch + 2.5f);
                if (in.isBackward()) m.pitch = Math.max(35, m.pitch - 2.5f);
            }
            double vel = in != null && in.isJump() ? 1.9 : 1.1;
            Location l = m.cuerpo.getLocation();
            double ry = Math.toRadians(m.yaw), rp = Math.toRadians(m.pitch);
            Vector dir = new Vector(-Math.sin(ry) * Math.cos(rp), -Math.sin(rp), Math.cos(ry) * Math.cos(rp));
            Location sig = l.clone().add(dir.clone().multiply(vel));
            sig.setYaw(m.yaw);
            sig.setPitch(m.pitch);
            World w = l.getWorld();
            RayTraceResult choque = w.rayTrace(l, dir, vel + 0.5, FluidCollisionMode.NEVER, true, 0.6,
                    e -> e instanceof LivingEntity && !e.getUniqueId().equals(m.duenio) && e != m.cuerpo);
            if (choque != null || ahora >= m.fin || sig.getY() < 60) {
                Location boom = choque != null ? choque.getHitPosition().toLocation(w) : sig;
                terminarMisil(m, true);
                plugin.armas().explotar(boom, 4.5f, p, "Misil Predator");
                w.spawnParticle(Particle.EXPLOSION_EMITTER, boom, 1);
                continue;
            }
            m.cuerpo.teleport(sig);
            w.spawnParticle(Particle.FLAME, l, 3, 0.1, 0.1, 0.1, 0.01, null, true);
            w.spawnParticle(Particle.LARGE_SMOKE, l, 2, 0.1, 0.1, 0.1, 0.01, null, true);
            int seg = Math.max(0, (m.fin - ahora) / 20);
            Util.barra(p, "<red>🚀 PREDATOR <gray>· altura " + (int) (sig.getY() - 64) + " m · " + seg + " s");
        }
    }

    private void terminarMisil(Misil m, boolean volver) {
        misiles.remove(m.duenio);
        if (m.cuerpo.isValid()) m.cuerpo.remove();
        Player p = Bukkit.getPlayer(m.duenio);
        if (p == null) return;
        p.setSpectatorTarget(null);
        if (volver || Modo.de(p.getWorld()) == Modo.SHOOTER) {
            modo.salirDeRacha(p, m.volver);
        }
    }

    public boolean enMisil(Player p) {
        return misiles.containsKey(p.getUniqueId());
    }

    // ------------------------------------------------------------------ bombardeo

    private boolean bombardeo(Player p) {
        RayTraceResult r = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 120,
                FluidCollisionMode.NEVER, true);
        if (r == null || r.getHitBlock() == null) {
            Util.barra(p, "<red>Apuntá a un punto del mapa.");
            return false;
        }
        Location objetivo = r.getHitBlock().getLocation().add(0.5, 1, 0.5);
        modo.anunciar("<gold>✈ ¡Bombardeo entrante de " + p.getName() + "!");
        for (Player o : modo.jugadores()) o.playSound(o, Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.6f);
        Vector linea = p.getLocation().getDirection().setY(0);
        if (linea.lengthSquared() < 0.01) linea = new Vector(1, 0, 0);
        linea.normalize();
        UUID id = p.getUniqueId();
        for (int i = -3; i <= 3; i++) {
            final int k = i;
            final Vector lin = linea;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                Location l = objetivo.clone().add(lin.clone().multiply(k * 3.0));
                l.getWorld().spawnParticle(Particle.LARGE_SMOKE, l.clone().add(0, 6, 0), 10, 0.3, 2, 0.3, 0.02);
                plugin.armas().explotar(l, 3.4f, Bukkit.getPlayer(id), "Bombardeo");
            }, 40 + (i + 3) * 4L);
        }
        objetivo.getWorld().spawnParticle(Particle.DUST, objetivo, 40, 1.5, 0.1, 1.5, 0, new Particle.DustOptions(Color.RED, 1.5f));
        return true;
    }

    // ------------------------------------------------------------------ perros

    private boolean perros(Player p) {
        World w = p.getWorld();
        int fin = Bukkit.getCurrentTick() + 600;
        for (int i = 0; i < 4; i++) {
            Location l = p.getLocation().add(rnd.nextDouble() * 2 - 1, 0, rnd.nextDouble() * 2 - 1);
            Wolf lobo = w.spawn(l, Wolf.class, CreatureSpawnEvent.SpawnReason.CUSTOM, x -> {
                x.customName(Util.mm("<gray>Perro de " + p.getName()));
                x.setAngry(true);
                x.setPersistent(false);
                x.setRemoveWhenFarAway(false);
                var vida = x.getAttribute(Attribute.MAX_HEALTH);
                if (vida != null) vida.setBaseValue(24);
                x.setHealth(24);
                var fuerza = x.getAttribute(Attribute.ATTACK_DAMAGE);
                if (fuerza != null) fuerza.setBaseValue(7);
                var vel = x.getAttribute(Attribute.MOVEMENT_SPEED);
                if (vel != null) vel.setBaseValue(0.42);
                x.getPersistentDataContainer().set(Claves.DUENIO, PersistentDataType.STRING, p.getUniqueId().toString());
            });
            perros.put(lobo.getUniqueId(), p.getUniqueId());
            perrosHasta.put(lobo.getUniqueId(), fin);
        }
        for (Player o : modo.jugadores()) o.playSound(o, Sound.ENTITY_WOLF_ANGRY_GROWL, 1f, 0.8f);
        modo.anunciar("<red>🐕 " + p.getName() + " soltó los perros");
        return true;
    }

    private void tickPerros() {
        int ahora = Bukkit.getCurrentTick();
        Iterator<Map.Entry<UUID, UUID>> itr = perros.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry<UUID, UUID> en = itr.next();
            Entity e = Bukkit.getEntity(en.getKey());
            if (!(e instanceof Wolf lobo) || !lobo.isValid() || perrosHasta.getOrDefault(en.getKey(), 0) < ahora) {
                if (e != null) e.remove();
                perrosHasta.remove(en.getKey());
                itr.remove();
                continue;
            }
            if (lobo.getTarget() instanceof Player t && !t.isDead() && t.getGameMode() == GameMode.ADVENTURE) continue;
            Player mejor = null;
            double dMin = 40 * 40;
            for (Player o : lobo.getWorld().getPlayers()) {
                if (o.getUniqueId().equals(en.getValue()) || o.isDead() || o.getGameMode() != GameMode.ADVENTURE) continue;
                double d = o.getLocation().distanceSquared(lobo.getLocation());
                if (d < dMin) {
                    dMin = d;
                    mejor = o;
                }
            }
            lobo.setTarget(mejor);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void alApuntarPerro(EntityTargetEvent e) {
        UUID duenio = perros.get(e.getEntity().getUniqueId());
        if (duenio != null && e.getTarget() != null && e.getTarget().getUniqueId().equals(duenio)) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alMorderPerro(EntityDamageByEntityEvent e) {
        UUID duenio = perros.get(e.getDamager().getUniqueId());
        if (duenio == null || !(e.getEntity() instanceof LivingEntity v)) return;
        if (v.getUniqueId().equals(duenio) || perros.containsKey(v.getUniqueId())) {
            e.setCancelled(true);
            return;
        }
        Player d = Bukkit.getPlayer(duenio);
        if (d != null) plugin.armas().registrarImpacto(v, d, "Perros de ataque", false);
    }

    // ------------------------------------------------------------------ general

    private ItemStack modelo(String modelo, Material base) {
        ItemStack it = new ItemStack(base);
        ItemMeta meta = it.getItemMeta();
        meta.setItemModel(new NamespacedKey("tresmodos", modelo));
        it.setItemMeta(meta);
        return it;
    }

    private void tick() {
        tickMisiles();
        int ahora = Bukkit.getCurrentTick();
        if (ahora % 2 == 0) tickPaquetes();
        if (ahora % 20 == 0) tickPerros();
    }

    /** Saca todo lo de las rachas (nueva partida o fin). */
    void limpiar() {
        for (Misil m : new ArrayList<>(misiles.values())) terminarMisil(m, true);
        for (Paquete pq : paquetes) if (pq.caja.isValid()) pq.caja.remove();
        paquetes.clear();
        for (UUID id : perros.keySet()) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        perros.clear();
        perrosHasta.clear();
        uavHasta.clear();
    }

    void olvidar(UUID id) {
        Misil m = misiles.get(id);
        if (m != null) terminarMisil(m, false);
        uavHasta.remove(id);
        entradas.remove(id);
    }
}
