package ar.tresmodos;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Movilidad de los modos modernos (Shooter y, más adelante, Guerra): deslizarse, sprint táctico,
 * salto de delfín, cuerpo a tierra y trepar.
 */
public class Movilidad implements Listener {
    private static final NamespacedKey TACTICO = new NamespacedKey("tresmodos", "sprint_tactico");
    private static final NamespacedKey TENDIDO = new NamespacedKey("tresmodos", "cuerpo_a_tierra");

    private final TresModos plugin;
    private final Map<UUID, Integer> ultimoSprint = new HashMap<>();
    private final Map<UUID, Integer> deslizando = new HashMap<>();
    private final Map<UUID, Integer> ultimoDeslizar = new HashMap<>();
    private final Map<UUID, Integer> ultimoShift = new HashMap<>();
    private final Map<UUID, Location> tendido = new HashMap<>();
    private final Map<UUID, Integer> delfin = new HashMap<>();
    private final Map<UUID, Integer> tacticoHasta = new HashMap<>();
    private final Map<UUID, Integer> tacticoListo = new HashMap<>();
    private final Map<UUID, Boolean> adelanteAntes = new HashMap<>();
    private final Map<UUID, Integer> soltoAdelante = new HashMap<>();
    /** Tendidos a la fuerza (caídos en Guerra): no se paran con salto ni con Shift. */
    private final java.util.Set<UUID> forzados = new java.util.HashSet<>();

    public Movilidad(TresModos plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1, 1);
    }

    /** Modos con esta movilidad. */
    public static boolean activa(Player p) {
        Modo m = Modo.de(p.getWorld());
        return (m == Modo.SHOOTER || m == Modo.GUERRA) && p.getGameMode() == GameMode.ADVENTURE && !p.isInsideVehicle();
    }

    public boolean sprintTactico(Player p) {
        return tacticoHasta.getOrDefault(p.getUniqueId(), 0) > Bukkit.getCurrentTick();
    }

    public boolean tendido(Player p) {
        return tendido.containsKey(p.getUniqueId());
    }

    /** Cuerpo a tierra y deslizándose: mucha precisión; en el aire o en sprint táctico, nada. */
    public double factorDispersion(Player p) {
        if (tendido(p)) return 0.45;
        if (deslizando.containsKey(p.getUniqueId())) return 0.9;
        return 1;
    }

    // ------------------------------------------------------------------ eventos

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void alAgacharse(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (!activa(p) || !e.isSneaking() || forzados.contains(p.getUniqueId())) return;
        UUID id = p.getUniqueId();
        int ahora = Bukkit.getCurrentTick();
        boolean corria = p.isSprinting() || ahora - ultimoSprint.getOrDefault(id, -100) <= 3;
        if (tendido(p)) {
            levantarse(p);
            return;
        }
        if (corria && !((Entity) p).isOnGround()) {
            saltoDelfin(p);
            return;
        }
        if (corria && ahora - ultimoDeslizar.getOrDefault(id, -100) > 20) {
            deslizar(p);
            return;
        }
        // Doble toque de Shift quieto o caminando: cuerpo a tierra.
        Integer antes = ultimoShift.put(id, ahora);
        if (antes != null && ahora - antes <= 8 && ((Entity) p).isOnGround()) tenderse(p);
    }

    @EventHandler
    public void alEntrada(PlayerInputEvent e) {
        Player p = e.getPlayer();
        if (!activa(p)) return;
        UUID id = p.getUniqueId();
        Input in = e.getInput();
        int ahora = Bukkit.getCurrentTick();
        boolean antes = adelanteAntes.getOrDefault(id, false);
        adelanteAntes.put(id, in.isForward());
        if (antes && !in.isForward()) soltoAdelante.put(id, ahora);
        // Doble toque de W mientras corrés: sprint táctico.
        if (!antes && in.isForward() && ahora - soltoAdelante.getOrDefault(id, -100) <= 6
                && ahora - ultimoSprint.getOrDefault(id, -100) <= 8) {
            sprintTactico(p, ahora);
        }
        if (in.isJump() && tendido(p) && !forzados.contains(id)) levantarse(p);
    }

    @EventHandler(ignoreCancelled = true)
    public void alSaltar(PlayerJumpEvent e) {
        Player p = e.getPlayer();
        if (!activa(p)) return;
        if (tendido(p)) {
            e.setCancelled(true);
            if (!forzados.contains(p.getUniqueId())) levantarse(p);
            return;
        }
        trepar(p);
    }

    @EventHandler(ignoreCancelled = true)
    public void alMover(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        Location antes = tendido.get(p.getUniqueId());
        if (antes == null) return;
        Block nuevo = e.getTo().getBlock().getRelative(BlockFace.UP);
        if (nuevo.getLocation().equals(antes)) return;
        // El bloque fantasma (solo lo ve este jugador) sigue a la cabeza para que el cliente siga tendido.
        p.sendBlockChange(antes, antes.getBlock().getBlockData());
        if (nuevo.isPassable()) p.sendBlockChange(nuevo.getLocation(), Material.BARRIER.createBlockData());
        tendido.put(p.getUniqueId(), nuevo.getLocation());
    }

    // ------------------------------------------------------------------ acciones

    private void deslizar(Player p) {
        UUID id = p.getUniqueId();
        int ahora = Bukkit.getCurrentTick();
        ultimoDeslizar.put(id, ahora);
        deslizando.put(id, ahora + 14);
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 0.01) return;
        p.setVelocity(dir.normalize().multiply(sprintTactico(p) ? 1.25 : 1.05).setY(0.05));
        plugin.armas().dejarDeApuntar(p);
        p.getWorld().playSound(p.getLocation(), Sound.BLOCK_GRAVEL_BREAK, 0.7f, 1.4f);
        p.getWorld().spawnParticle(Particle.BLOCK, p.getLocation(), 12, 0.3, 0.05, 0.3, 0,
                p.getLocation().subtract(0, 0.5, 0).getBlock().getBlockData());
        terminarTactico(p);
    }

    private void saltoDelfin(Player p) {
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 0.01) return;
        p.setVelocity(dir.normalize().multiply(0.95).setY(0.18));
        delfin.put(p.getUniqueId(), Bukkit.getCurrentTick());
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 0.6f);
        terminarTactico(p);
    }

    private void tenderse(Player p) {
        UUID id = p.getUniqueId();
        if (tendido.containsKey(id)) return;
        plugin.armas().dejarDeApuntar(p);
        p.setPose(Pose.SWIMMING, true);
        Block cabeza = p.getLocation().getBlock().getRelative(BlockFace.UP);
        if (cabeza.isPassable()) p.sendBlockChange(cabeza.getLocation(), Material.BARRIER.createBlockData());
        tendido.put(id, cabeza.getLocation());
        AttributeInstance vel = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (vel != null && vel.getModifier(TENDIDO) == null) {
            vel.addModifier(new AttributeModifier(TENDIDO, -0.55, AttributeModifier.Operation.ADD_SCALAR, EquipmentSlotGroup.ANY));
        }
        p.getWorld().playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, 0.8f, 0.7f);
        Util.barra(p, "<gray>Cuerpo a tierra · saltá o doble Shift para pararte");
    }

    private void levantarse(Player p) {
        Location l = tendido.remove(p.getUniqueId());
        if (l != null) p.sendBlockChange(l, l.getBlock().getBlockData());
        p.setPose(Pose.STANDING, false);
        AttributeInstance vel = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (vel != null) vel.removeModifier(TENDIDO);
    }

    private void sprintTactico(Player p, int ahora) {
        UUID id = p.getUniqueId();
        if (ahora < tacticoListo.getOrDefault(id, 0) || sprintTactico(p)) return;
        tacticoHasta.put(id, ahora + 60);
        tacticoListo.put(id, ahora + 60 + 160);
        plugin.armas().dejarDeApuntar(p);
        AttributeInstance vel = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (vel != null && vel.getModifier(TACTICO) == null) {
            vel.addModifier(new AttributeModifier(TACTICO, 0.3, AttributeModifier.Operation.ADD_SCALAR, EquipmentSlotGroup.ANY));
        }
        p.playSound(p, Sound.ENTITY_HORSE_BREATHE, 0.6f, 1.6f);
        Util.barra(p, "<yellow>Sprint táctico");
    }

    private void terminarTactico(Player p) {
        tacticoHasta.remove(p.getUniqueId());
        AttributeInstance vel = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (vel != null) vel.removeModifier(TACTICO);
    }

    /** Saltando frente a un muro de 2 o una cerca, te impulsa por encima. */
    private void trepar(Player p) {
        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() < 0.01) return;
        dir.normalize();
        Location pies = p.getLocation();
        Block frente = pies.clone().add(dir.clone().multiply(0.8)).getBlock();
        Block arriba1 = frente.getRelative(BlockFace.UP);
        Block arriba2 = arriba1.getRelative(BlockFace.UP);
        Block arriba3 = arriba2.getRelative(BlockFace.UP);
        String n = frente.getType().name();
        boolean cerca = n.endsWith("_FENCE") || n.endsWith("_WALL") || n.endsWith("_PANE") || frente.getType() == Material.IRON_BARS;
        if (cerca && arriba1.isPassable()) {
            Block detras = frente.getRelative(dir.getX() > 0.5 ? BlockFace.EAST : dir.getX() < -0.5 ? BlockFace.WEST
                    : dir.getZ() > 0 ? BlockFace.SOUTH : BlockFace.NORTH);
            if (!detras.isPassable() || !detras.getRelative(BlockFace.UP).isPassable()) return;
            Bukkit.getScheduler().runTask(plugin, () -> p.setVelocity(dir.clone().multiply(0.42).setY(0.58)));
            return;
        }
        if (!frente.isSolid() || !arriba1.isSolid() || !arriba2.isPassable() || !arriba3.isPassable()) return;
        if (!pies.getBlock().getRelative(BlockFace.UP, 2).isPassable()) return;
        Bukkit.getScheduler().runTask(plugin, () -> p.setVelocity(new Vector(0, 0.66, 0)));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) p.setVelocity(p.getVelocity().add(dir.clone().multiply(0.32)));
        }, 7);
        p.getWorld().playSound(pies, Sound.BLOCK_LADDER_STEP, 0.7f, 0.9f);
    }

    // ------------------------------------------------------------------ ciclo

    private void tick() {
        int ahora = Bukkit.getCurrentTick();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            if (p.isSprinting()) ultimoSprint.put(id, ahora);
            Integer finDesliz = deslizando.get(id);
            if (finDesliz != null && ahora >= finDesliz) deslizando.remove(id);
            Integer fin = tacticoHasta.get(id);
            if (fin != null && (ahora >= fin || !p.isSprinting())) terminarTactico(p);
            Integer d = delfin.get(id);
            if (d != null && ahora - d > 2 && ((Entity) p).isOnGround()) {
                delfin.remove(id);
                if (activa(p)) tenderse(p);
            }
            if (tendido(p) && (!activa(p) || (p.isSprinting() && !forzados.contains(id)))) {
                forzados.remove(id);
                levantarse(p);
            }
        }
    }

    /** Tira al jugador al piso sin que se pueda parar (caído esperando que lo revivan). */
    public void tirar(Player p) {
        forzados.add(p.getUniqueId());
        tenderse(p);
    }

    /** Lo levanta (revivido o muerto). */
    public void levantar(Player p) {
        forzados.remove(p.getUniqueId());
        if (tendido(p)) levantarse(p);
    }

    public void olvidar(Player p) {
        UUID id = p.getUniqueId();
        forzados.remove(id);
        if (tendido(p)) levantarse(p);
        terminarTactico(p);
        deslizando.remove(id);
        delfin.remove(id);
        ultimoShift.remove(id);
        ultimoSprint.remove(id);
        tacticoListo.remove(id);
        adelanteAntes.remove(id);
        soltoAdelante.remove(id);
    }
}
