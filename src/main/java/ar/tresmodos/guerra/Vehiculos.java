package ar.tresmodos.guerra;

import ar.tresmodos.Armas;
import ar.tresmodos.Modo;
import ar.tresmodos.TresModos;
import ar.tresmodos.Util;
import ar.tresmodos.guerra.TipoVehiculo.Movimiento;
import ar.tresmodos.guerra.TipoVehiculo.Puesto;
import ar.tresmodos.mundo.ValleDeHierro;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
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
 * Los vehículos de Guerra: aparición en las bases, subir y bajar, física por tipo (ruedas, orugas,
 * avión y helicóptero), torretas que siguen la mirada, armas de cada asiento, daño por zonas con
 * módulos (orugas, motor, cañón, rotor de cola), incendio y reparación.
 */
public class Vehiculos implements Listener {
    public record Golpe(Vehiculo vehiculo, double distancia) {}

    private record Aparicion(TipoVehiculo tipo, ModoGuerra.Equipo equipo, Location lugar) {}

    private final TresModos plugin;
    private final ModoGuerra modo;
    private final Proyectiles proyectiles;
    private final List<Vehiculo> vehiculos = new ArrayList<>();
    private final Map<UUID, Vehiculo> deJugador = new HashMap<>();
    private final Map<Aparicion, Integer> esperan = new HashMap<>();
    private final Map<Aparicion, Vehiculo> deAparicion = new HashMap<>();
    private final Map<UUID, Integer> shiftDesde = new HashMap<>();
    private final Map<UUID, Integer> gatillo = new HashMap<>();
    private final Set<UUID> bajando = new HashSet<>();
    private final Set<UUID> paracaidas = new HashSet<>();
    private final Map<UUID, Integer> fijando = new HashMap<>();
    private final Map<UUID, Vehiculo> fijado = new HashMap<>();
    private final List<Aparicion> apariciones = new ArrayList<>();
    private int proximoId;

    Vehiculos(TresModos plugin, ModoGuerra modo) {
        this.plugin = plugin;
        this.modo = modo;
        this.proyectiles = new Proyectiles(plugin, modo);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40, 1);
    }

    public Proyectiles proyectiles() {
        return proyectiles;
    }

    public List<Vehiculo> todos() {
        return vehiculos;
    }

    public Vehiculo de(Player p) {
        Vehiculo v = deJugador.get(p.getUniqueId());
        return v != null && v.vivo() ? v : null;
    }

    /** En un vehículo cerrado: las balas y explosiones le pegan al vehículo, no a él. */
    public boolean protegido(Player p) {
        Vehiculo v = de(p);
        if (v == null || !v.tipo.cerrado) return false;
        int a = v.asientoDe(p);
        // En el tanque, el del techo va expuesto.
        return !(v.tipo == TipoVehiculo.TANQUE && a == 1);
    }

    /** Maneja o dispara un arma del vehículo (no usa sus propias armas). */
    public boolean ocupado(Player p) {
        Vehiculo v = de(p);
        if (v == null) return false;
        int a = v.asientoDe(p);
        return a >= 0 && v.tipo.asientos.get(a).puesto() != Puesto.PASAJERO;
    }

    private World mundo() {
        return modo.mundo();
    }

    // ------------------------------------------------------------------ aparición

    private void prepararApariciones() {
        apariciones.clear();
        for (ModoGuerra.Equipo e : ModoGuerra.Equipo.values()) {
            int bx = e.baseX(), s = e.lado;
            float haciaCentro = s < 0 ? -90 : 90;
            agregar(TipoVehiculo.CUATRI, e, bx - s * 11, -25, haciaCentro);
            agregar(TipoVehiculo.CUATRI, e, bx - s * 11, -37, haciaCentro);
            agregar(TipoVehiculo.JEEP, e, bx - s * 5, -25, haciaCentro);
            agregar(TipoVehiculo.JEEP, e, bx - s * 5, -37, haciaCentro);
            agregar(TipoVehiculo.VCI, e, bx + s * 3, -25, haciaCentro);
            agregar(TipoVehiculo.TANQUE, e, bx + s * 3, -37, haciaCentro);
            agregar(TipoVehiculo.ANTIAEREO, e, bx + s * 11, -31, haciaCentro);
            agregar(TipoVehiculo.AVION, e, bx, -70, 180);
            agregar(TipoVehiculo.HELICOPTERO, e, bx, 42, haciaCentro);
        }
    }

    private void agregar(TipoVehiculo t, ModoGuerra.Equipo e, int x, int z, float rumbo) {
        Location l = new Location(mundo(), x + 0.5, ValleDeHierro.altura(x, z) + 1, z + 0.5, rumbo, 0);
        apariciones.add(new Aparicion(t, e, l));
    }

    /** Pone todos los vehículos en su lugar (al empezar la partida). */
    public void reiniciar() {
        for (Vehiculo v : new ArrayList<>(vehiculos)) quitar(v);
        vehiculos.clear();
        deJugador.clear();
        esperan.clear();
        deAparicion.clear();
        proyectiles.limpiar();
        if (apariciones.isEmpty()) prepararApariciones();
        int ahora = Bukkit.getCurrentTick();
        for (Aparicion a : apariciones) esperan.put(a, ahora + 20);
    }

    private void aparecer(Aparicion a) {
        mundo().getChunkAt(a.lugar());
        Vehiculo v = new Vehiculo(proximoId++, a.tipo(), a.equipo(), a.lugar());
        v.sinUsoDesde = Bukkit.getCurrentTick();
        v.crearEntidades();
        vehiculos.add(v);
        deAparicion.put(a, v);
    }

    private void quitar(Vehiculo v) {
        for (UUID u : v.ocupantes) {
            if (u == null) continue;
            deJugador.remove(u);
            Player p = Bukkit.getPlayer(u);
            if (p != null) {
                bajando.add(u);
                p.removePotionEffect(PotionEffectType.INVISIBILITY);
            }
        }
        v.quitar();
        bajando.clear();
    }

    // ------------------------------------------------------------------ subir y bajar

    /** Clic derecho mirando un vehículo: subir (o reparar con la llave, que lo maneja el arsenal). */
    @EventHandler(priority = EventPriority.HIGH)
    public void alClic(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (Modo.de(p.getWorld()) != Modo.GUERRA) return;
        Vehiculo dentro = de(p);
        Action a = e.getAction();
        if (dentro != null) {
            int asiento = dentro.asientoDe(p);
            if (asiento < 0 || dentro.tipo.asientos.get(asiento).puesto() == Puesto.PASAJERO) return;
            e.setCancelled(true);
            if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) gatillo.put(p.getUniqueId(), Bukkit.getCurrentTick() + 5);
            else if (a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) disparoPesado(dentro, p, asiento);
            return;
        }
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;
        if (ArmaGuerra.de(p.getInventory().getItemInMainHand()) == ArmaGuerra.LLAVE) return;
        Golpe g = mirando(p, 5);
        if (g == null) return;
        e.setCancelled(true);
        subir(p, g.vehiculo());
    }

    /** Vehículo al que apunta la mirada del jugador. */
    public Golpe mirando(Player p, double alcance) {
        Location ojo = p.getEyeLocation();
        RayTraceResult r = p.getWorld().rayTraceBlocks(ojo, ojo.getDirection(), alcance, FluidCollisionMode.NEVER, true);
        double max = r == null ? alcance : r.getHitPosition().distance(ojo.toVector());
        return rayo(ojo.toVector(), ojo.getDirection(), max, de(p));
    }

    private void subir(Player p, Vehiculo v) {
        if (modo.equipo(p) != v.equipo) {
            Util.barra(p, "<red>Es un vehículo enemigo.");
            return;
        }
        if (modo.caido(p) || p.getGameMode() != GameMode.ADVENTURE) return;
        if (modo.esPortador(p) && v.tipo.prohibidoAlPortador()) {
            Util.barra(p, "<red>Con la bandera no podés usar " + v.tipo.nombre.toLowerCase() + ".");
            return;
        }
        for (int i = 0; i < v.ocupantes.length; i++) {
            if (v.ocupantes[i] == null) {
                sentar(p, v, i);
                return;
            }
        }
        Util.barra(p, "<gray>Está lleno.");
    }

    private void sentar(Player p, Vehiculo v, int i) {
        Vehiculo antes = de(p);
        if (antes != null) {
            int a = antes.asientoDe(p);
            if (a >= 0) antes.ocupantes[a] = null;
            ItemDisplay ae = a >= 0 ? antes.asientoEntidad(a) : null;
            bajando.add(p.getUniqueId());
            if (ae != null) ae.removePassenger(p);
            bajando.remove(p.getUniqueId());
        }
        ItemDisplay asiento = v.asientoEntidad(i);
        if (asiento == null) return;
        plugin.armas().dejarDeApuntar(p);
        plugin.movilidad().olvidar(p);
        v.ocupantes[i] = p.getUniqueId();
        deJugador.put(p.getUniqueId(), v);
        asiento.addPassenger(p);
        paracaidas.remove(p.getUniqueId());
        if (v.tipo.cerrado && !(v.tipo == TipoVehiculo.TANQUE && i == 1)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, PotionEffect.INFINITE_DURATION, 0, false, false));
        } else {
            p.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
        Puesto pu = v.tipo.asientos.get(i).puesto();
        p.playSound(p, Sound.BLOCK_IRON_DOOR_CLOSE, 0.7f, 1.3f);
        Util.titulo(p, "", "<gray>" + v.tipo.nombre + " · " + switch (pu) {
            case CONDUCTOR -> "conductor";
            case ARTILLERO -> "artillero";
            case PASAJERO -> "pasajero";
        } + " <dark_gray>(1-" + v.ocupantes.length + " cambia · Shift 1 s baja)", 0, 2200, 300);
        Util.msg(p, ayuda(v, i));
    }

    private static String ayuda(Vehiculo v, int asiento) {
        Puesto pu = v.tipo.asientos.get(asiento).puesto();
        String s = switch (v.tipo) {
            case CUATRI, JEEP -> pu == Puesto.CONDUCTOR ? "W/S acelera y frena · A/D dobla · Espacio freno de mano"
                    : pu == Puesto.ARTILLERO ? "Mantené clic derecho: ametralladora .50" : "";
            case VCI -> pu == Puesto.CONDUCTOR ? "W/S · A/D gira · flota en el río"
                    : pu == Puesto.ARTILLERO ? "Clic derecho: cañón de 25 mm · Clic izquierdo: misil TOW (sigue tu mira)" : "";
            case TANQUE -> pu == Puesto.CONDUCTOR ? "W/S · A/D gira el casco · la torreta sigue tu mirada · Clic izquierdo: cañón · "
                    + "F: AP/HE · Clic derecho: ametralladora · Q: extintor" : "Mantené clic derecho: ametralladora del techo";
            case ANTIAEREO -> pu == Puesto.CONDUCTOR ? "Mantené clic derecho: cañones de 35 mm · los aviones enemigos brillan en el radar" : "";
            case AVION -> "W/S potencia · el avión sigue tu mirada · A/D alabeo · clic derecho: cañón · clic izquierdo: "
                    + "misil o bomba (F cambia) · Shift: eyección · aterrizá en tu pista para reparar";
            case HELICOPTERO -> pu == Puesto.CONDUCTOR ? "Espacio sube · Ctrl baja · W/S/A/D se mueve · clic izquierdo: cohetes"
                    : "Mantené clic derecho: cañón de 30 mm · apuntá 1,5 s a un vehículo y clic izquierdo: misil";
        };
        return s.isEmpty() ? "<gray>Pasajero: podés usar tus armas." : "<gray>" + s;
    }

    /** Baja al jugador a un costado del vehículo. */
    public void bajar(Player p, boolean eyectar) {
        Vehiculo v = de(p);
        deJugador.remove(p.getUniqueId());
        p.removePotionEffect(PotionEffectType.INVISIBILITY);
        if (v == null) return;
        int a = v.asientoDe(p);
        if (a >= 0) v.ocupantes[a] = null;
        ItemDisplay ae = a >= 0 ? v.asientoEntidad(a) : null;
        bajando.add(p.getUniqueId());
        if (ae != null) ae.removePassenger(p);
        bajando.remove(p.getUniqueId());
        if (eyectar) {
            p.teleport(p.getLocation().add(0, 1.5, 0));
            p.setVelocity(new Vector(0, 1.2, 0));
            paracaidas.add(p.getUniqueId());
            Util.barra(p, "<white>Paracaídas abierto");
        } else {
            Vector lado = v.mundo(v.tipo.ancho / 2 + 1.0, 0, 0);
            Location l = new Location(mundo(), lado.getX(), lado.getY(), lado.getZ(), p.getLocation().getYaw(), p.getLocation().getPitch());
            Block b = l.getBlock();
            if (!b.isPassable() || !b.getRelative(0, 1, 0).isPassable()) l = new Location(mundo(), v.x, v.y + v.tipo.alto + 0.5, v.z);
            if (v.esAereo() && !v.enTierra) paracaidas.add(p.getUniqueId());
            p.teleport(l);
        }
        v.sinUsoDesde = Bukkit.getCurrentTick();
    }

    @EventHandler(ignoreCancelled = true)
    public void alBajar(EntityDismountEvent e) {
        if (!(e.getEntity() instanceof Player p) || bajando.contains(p.getUniqueId())) return;
        Vehiculo v = de(p);
        if (v == null) return;
        // Se baja manteniendo Shift 1 s (en el avión, Shift eyecta al toque).
        e.setCancelled(true);
    }

    @EventHandler
    public void alShift(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        Vehiculo v = de(p);
        if (v == null) return;
        if (!e.isSneaking()) {
            shiftDesde.remove(p.getUniqueId());
            return;
        }
        if (v.tipo == TipoVehiculo.AVION && !v.enTierra) {
            bajar(p, true);
            return;
        }
        shiftDesde.put(p.getUniqueId(), Bukkit.getCurrentTick());
    }

    /** Teclas 1 a 4: cambiar de asiento. */
    @EventHandler(ignoreCancelled = true)
    public void alCambiarSlot(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        Vehiculo v = de(p);
        if (v == null) return;
        int n = e.getNewSlot();
        if (n >= v.ocupantes.length) return;
        e.setCancelled(true);
        if (v.ocupantes[n] != null) {
            Util.barra(p, "<gray>Ese asiento está ocupado.");
            return;
        }
        if (modo.esPortador(p) && v.tipo.prohibidoAlPortador()) return;
        sentar(p, v, n);
    }

    /** F: cambiar munición (tanque) o arma secundaria (avión). */
    @EventHandler
    public void alF(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        Vehiculo v = de(p);
        if (v == null) return;
        e.setCancelled(true);
        if (v.tipo == TipoVehiculo.TANQUE && v.asientoDe(p) == 0) {
            v.municionHE = !v.municionHE;
            Util.barra(p, "<yellow>Munición: " + (v.municionHE ? "HE (explosiva)" : "AP (perforante)"));
            p.playSound(p, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1f, 1.2f);
        } else if (v.tipo == TipoVehiculo.AVION) {
            v.secundarioBombas = !v.secundarioBombas;
            Util.barra(p, "<yellow>Secundaria: " + (v.secundarioBombas ? "bombas (" + v.bombas + ")" : "misiles (" + v.misiles + ")"));
        }
    }

    /** Q: extintor (una vez por vida del vehículo). */
    @EventHandler
    public void alQ(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        Vehiculo v = de(p);
        if (v == null) return;
        e.setCancelled(true);
        if (!v.incendio) {
            Util.barra(p, "<gray>No hay incendio.");
            return;
        }
        if (v.extintorUsado) {
            Util.barra(p, "<red>Ya usaste el extintor: repará con la llave o en la base.");
            return;
        }
        v.extintorUsado = true;
        v.incendio = false;
        mundo().spawnParticle(Particle.CLOUD, v.x, v.y + 1, v.z, 40, 1, 0.5, 1, 0.05);
        mundo().playSound(new Location(mundo(), v.x, v.y, v.z), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.8f);
        Util.barra(p, "<green>Incendio apagado");
    }

    // ------------------------------------------------------------------ ciclo

    private void tick() {
        if (mundo() == null) return;
        int ahora = Bukkit.getCurrentTick();
        // Reapariciones.
        for (Map.Entry<Aparicion, Integer> en : new ArrayList<>(esperan.entrySet())) {
            if (ahora < en.getValue()) continue;
            esperan.remove(en.getKey());
            if (!mundo().getPlayers().isEmpty()) aparecer(en.getKey());
            else esperan.put(en.getKey(), ahora + 100);
        }
        proyectiles.tick();
        for (Vehiculo v : new ArrayList<>(vehiculos)) {
            if (!v.vivo()) continue;
            limpiarOcupantes(v);
            Player conductor = ocupante(v, 0);
            switch (v.tipo.movimiento) {
                case RUEDAS, ORUGAS -> moverTierra(v, conductor, ahora);
                case AVION -> moverAvion(v, conductor, ahora);
                case HELI -> moverHeli(v, conductor, ahora);
            }
            if (!v.vivo()) continue;
            apuntar(v, ahora);
            armasAutomaticas(v, ahora);
            estado(v, ahora);
            if (!v.vivo()) continue;
            v.actualizar();
            if (ahora % 5 == 0) hud(v, ahora);
        }
        // Bajar con Shift sostenido.
        for (Map.Entry<UUID, Integer> en : new ArrayList<>(shiftDesde.entrySet())) {
            if (ahora - en.getValue() < 20) continue;
            shiftDesde.remove(en.getKey());
            Player p = Bukkit.getPlayer(en.getKey());
            if (p != null && de(p) != null) {
                Vehiculo v = de(p);
                bajar(p, v.esAereo() && !v.enTierra);
            }
        }
        // Paracaídas.
        for (UUID u : new ArrayList<>(paracaidas)) {
            Player p = Bukkit.getPlayer(u);
            if (p == null || p.isDead() || Modo.de(p.getWorld()) != Modo.GUERRA) {
                paracaidas.remove(u);
                continue;
            }
            if (((Entity) p).isOnGround() || p.isInWater()) {
                paracaidas.remove(u);
                p.removePotionEffect(PotionEffectType.SLOW_FALLING);
                continue;
            }
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 40, 0, false, false));
            p.setFallDistance(0);
            if (ahora % 4 == 0) p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 3, 0), 3, 0.6, 0.1, 0.6, 0);
        }
    }

    private Player ocupante(Vehiculo v, int i) {
        UUID u = i < v.ocupantes.length ? v.ocupantes[i] : null;
        return u == null ? null : Bukkit.getPlayer(u);
    }

    private void limpiarOcupantes(Vehiculo v) {
        for (int i = 0; i < v.ocupantes.length; i++) {
            UUID u = v.ocupantes[i];
            if (u == null) continue;
            Player p = Bukkit.getPlayer(u);
            ItemDisplay a = v.asientoEntidad(i);
            if (p == null || p.isDead() || Modo.de(p.getWorld()) != Modo.GUERRA || a == null) {
                v.ocupantes[i] = null;
                deJugador.remove(u);
                continue;
            }
            if (!a.getPassengers().contains(p)) {
                if (p.getLocation().distanceSquared(a.getLocation()) < 64) a.addPassenger(p);
                else {
                    v.ocupantes[i] = null;
                    deJugador.remove(u);
                    p.removePotionEffect(PotionEffectType.INVISIBILITY);
                }
            }
        }
    }

    // ------------------------------------------------------------------ física de tierra

    private void moverTierra(Vehiculo v, Player c, int ahora) {
        double dt = 0.05;
        Input in = c != null ? c.getCurrentInput() : null;
        boolean adelante = in != null && in.isForward(), atras = in != null && in.isBackward();
        boolean izq = in != null && in.isLeft(), der = in != null && in.isRight(), freno = in != null && in.isJump();
        boolean orugas = v.tipo.movimiento == Movimiento.ORUGAS;
        double vmax = v.tipo.velMax * (v.motorDanado ? 0.5 : 1);
        boolean movil = v.movil(ahora);
        if (!movil) {
            adelante = atras = izq = der = false;
            v.vel *= 0.8;
        }
        double acel = orugas ? 3.0 : 7.0;
        if (adelante) v.vel = Math.min(vmax, v.vel + acel * dt);
        else if (atras) v.vel = v.vel > 0.5 ? v.vel - acel * 2 * dt : Math.max(-vmax * (orugas ? 0.4 : 0.3), v.vel - acel * dt);
        else v.vel *= 1 - (orugas ? 2.0 : 1.2) * dt;
        if (freno) v.vel *= 1 - 3.5 * dt;
        if (Math.abs(v.vel) < 0.05) v.vel = 0;
        int giro = (izq ? 1 : 0) - (der ? 1 : 0);
        if (giro != 0) {
            double tasa;
            if (orugas) tasa = 35;
            else tasa = 75 * Math.min(1, Math.abs(v.vel) / 4) * (1 - 0.45 * Math.abs(v.vel) / v.tipo.velMax) * (freno ? 1.6 : 1)
                    * Math.signum(v.vel == 0 ? 1 : v.vel);
            v.rumbo -= (float) (giro * tasa * dt);
        }
        // Agua: los de ruedas se frenan, el VCI flota y el tanque no entra hondo.
        World w = mundo();
        Block medio = w.getBlockAt((int) Math.floor(v.x), (int) Math.floor(v.y + 0.5), (int) Math.floor(v.z));
        boolean agua = medio.getType() == Material.WATER;
        if (agua && !v.tipo.anfibio) v.vel = Math.max(-2, Math.min(2.5, v.vel));
        Vector f = new Vector(-Math.sin(Math.toRadians(v.rumbo)), 0, Math.cos(Math.toRadians(v.rumbo)));
        Vector paso = f.clone().multiply(v.vel * dt);
        double nx = v.x + paso.getX(), nz = v.z + paso.getZ();
        // Choque contra paredes: tres puntos en la trompa (o la cola si va marcha atrás).
        double sentido = Math.signum(v.vel);
        if (sentido != 0) {
            Vector r = new Vector(Math.cos(Math.toRadians(v.rumbo)), 0, Math.sin(Math.toRadians(v.rumbo)));
            boolean choque = false;
            for (double k : new double[]{-0.45, 0, 0.45}) {
                Vector punta = new Vector(nx, v.y, nz).add(f.clone().multiply(sentido * v.tipo.largo / 2)).add(r.clone().multiply(k * v.tipo.ancho));
                for (double dy = 1.05; dy < Math.min(2.6, v.tipo.alto); dy += 0.8) {
                    Block b = w.getBlockAt(punta.getBlockX(), (int) Math.floor(v.y + dy), punta.getBlockZ());
                    if (b.isPassable() || b.getType() == Material.WATER) continue;
                    if (orugas && rompible(b.getType())) {
                        modo.registrarRotura(b);
                        w.spawnParticle(Particle.BLOCK, b.getLocation().add(0.5, 0.5, 0.5), 15, 0.3, 0.3, 0.3, 0, b.getBlockData());
                        b.setType(Material.AIR, false);
                        v.vel *= 0.9;
                        continue;
                    }
                    choque = true;
                }
            }
            if (choque) {
                if (Math.abs(v.vel) > 12) choque(v, Math.abs(v.vel));
                v.vel = -v.vel * 0.2;
                nx = v.x;
                nz = v.z;
            }
        }
        // Suelo bajo la nueva posición (sube escalones de 1).
        double suelo = suelo(w, nx, v.y + 1.2, nz);
        if (v.tipo.anfibio && agua) suelo = Math.max(suelo, ValleDeHierro.AGUA + 0.4);
        if (suelo > v.y + 1.1) {
            if (Math.abs(v.vel) > 12) choque(v, Math.abs(v.vel));
            v.vel = 0;
        } else {
            v.x = nx;
            v.z = nz;
            if (suelo < v.y - 0.05) {
                v.velY -= 25 * dt;
                v.y = Math.max(suelo, v.y + v.velY * dt);
                if (v.y <= suelo) {
                    if (v.velY < -14) daniar(v, (-v.velY - 12) * 3, null, "Caída", new Vector(0, -1, 0), false, false);
                    v.velY = 0;
                }
                v.enTierra = false;
            } else {
                v.y = suelo;
                v.velY = 0;
                v.enTierra = true;
            }
        }
        // Inclinación visual según el terreno.
        double adelanteY = suelo(w, v.x + f.getX() * v.tipo.largo * 0.4, v.y + 1.2, v.z + f.getZ() * v.tipo.largo * 0.4);
        double atrasY = suelo(w, v.x - f.getX() * v.tipo.largo * 0.4, v.y + 1.2, v.z - f.getZ() * v.tipo.largo * 0.4);
        float objetivo = (float) Math.toDegrees(Math.atan2(adelanteY - atrasY, v.tipo.largo * 0.8));
        v.cabeceo += (Math.max(-25, Math.min(25, objetivo)) - v.cabeceo) * 0.3f;
        v.alabeo = 0;
        // Atropellar infantería enemiga.
        if (Math.abs(v.vel) > 4) atropellar(v, c);
        if (c != null && Math.abs(v.vel) > 0.5 && ahora % 8 == 0) {
            w.playSound(new Location(w, v.x, v.y, v.z), orugas ? Sound.ENTITY_IRON_GOLEM_STEP : Sound.ENTITY_MINECART_RIDING,
                    orugas ? 0.8f : 0.4f, orugas ? 0.5f : 1.2f);
        }
        if (orugas && Math.abs(v.vel) > 1 && ahora % 3 == 0) {
            w.spawnParticle(Particle.BLOCK, v.x - f.getX() * v.tipo.largo / 2, v.y + 0.2, v.z - f.getZ() * v.tipo.largo / 2, 4,
                    v.tipo.ancho / 3, 0.1, v.tipo.ancho / 3, 0, w.getBlockAt((int) Math.floor(v.x), (int) Math.floor(v.y) - 1, (int) Math.floor(v.z)).getBlockData());
        }
    }

    /** Altura del suelo (tope del bloque sólido más alto debajo de desdeY). */
    private static double suelo(World w, double x, double desdeY, double z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        for (int y = (int) Math.floor(desdeY); y > desdeY - 8; y--) {
            Block b = w.getBlockAt(bx, y, bz);
            if (!b.isPassable() && b.getType() != Material.WATER) {
                double alto = b.getBoundingBox().getHeight();
                return y + (alto > 0 ? b.getBoundingBox().getMaxY() - y : 1);
            }
        }
        return desdeY - 8;
    }

    private static boolean rompible(Material m) {
        String n = m.name();
        return n.endsWith("_FENCE") || n.endsWith("_FENCE_GATE") || n.contains("GLASS") || n.endsWith("_LEAVES")
                || n.endsWith("_WALL") || m == Material.HAY_BLOCK || m == Material.IRON_BARS || m == Material.BARREL
                || n.endsWith("_WOOL") || m == Material.SUNFLOWER || m == Material.WHEAT || n.endsWith("_LOG");
    }

    private void choque(Vehiculo v, double vel) {
        double d = (vel - 10) * 4;
        daniar(v, d, null, "Choque", v.adelante().multiply(-1), false, false);
        for (int i = 0; i < v.ocupantes.length; i++) {
            Player p = ocupante(v, i);
            if (p != null) p.damage((vel - 10) * 0.6);
        }
        mundo().playSound(new Location(mundo(), v.x, v.y, v.z), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.6f);
    }

    private void atropellar(Vehiculo v, Player conductor) {
        for (Player p : mundo().getPlayers()) {
            if (de(p) != null || p.getGameMode() != GameMode.ADVENTURE) continue;
            if (conductor != null && modo.aliados(conductor, p)) continue;
            if (modo.equipo(p) == v.equipo) continue;
            if (!v.contiene(p.getLocation().toVector().add(new Vector(0, 0.5, 0)), 0.3)) continue;
            Armas.danioDirecto(p, Math.abs(v.vel) * 1.4, conductor, v.tipo.nombre, false);
            p.setVelocity(v.adelante().multiply(Math.signum(v.vel) * 0.9).setY(0.5));
        }
    }

    // ------------------------------------------------------------------ avión

    private void moverAvion(Vehiculo v, Player c, int ahora) {
        double dt = 0.05;
        Input in = c != null ? c.getCurrentInput() : null;
        if (in != null && in.isForward()) v.potencia = Math.min(1, v.potencia + 0.6 * dt);
        if (in != null && in.isBackward()) v.potencia = Math.max(0, v.potencia - 0.6 * dt);
        if (c == null) v.potencia = Math.max(0, v.potencia - 0.3 * dt);
        double objetivo = v.potencia * v.tipo.velMax;
        v.vel += (objetivo - v.vel) * (v.enTierra ? 0.35 : 0.5) * dt;
        v.vel -= Math.sin(Math.toRadians(v.cabeceo)) * 9 * dt; // picar acelera, trepar frena
        v.vel = Math.max(0, Math.min(v.tipo.velMax * 1.2, v.vel));
        // Hacia dónde quiere ir el piloto.
        float quiereRumbo = c != null ? c.getLocation().getYaw() : v.rumbo;
        float quiereCabeceo = c != null ? -c.getLocation().getPitch() : 0;
        double alabeoInput = in == null ? 0 : (in.isLeft() ? 1 : 0) - (in.isRight() ? 1 : 0);
        float dif = envolver(quiereRumbo - v.rumbo);
        double tasaGiro = 45 * (1 + 0.6 * Math.abs(alabeoInput)) * dt;
        if (!v.enTierra || v.vel < 6) v.rumbo += (float) Math.max(-tasaGiro, Math.min(tasaGiro, dif));
        v.alabeo += (float) ((-dif * 0.6 + alabeoInput * 35 - v.alabeo) * 0.1);
        v.alabeo = Math.max(-60, Math.min(60, v.alabeo));
        double tasaCab = 30 * dt;
        float quiere = Math.max(-60, Math.min(60, quiereCabeceo));
        if (v.enTierra) {
            if (v.vel < 18) quiere = 0;
            quiere = Math.max(0, quiere);
        }
        v.cabeceo += (float) Math.max(-tasaCab, Math.min(tasaCab, quiere - v.cabeceo));
        // Pérdida: muy lento en el aire, cae de trompa.
        double hundir = 0;
        if (!v.enTierra && v.vel < 15) {
            v.cabeceo = Math.max(-70, v.cabeceo - (float) (20 * dt));
            hundir = (15 - v.vel) * 0.6;
            if (c != null && ahora % 10 == 0) Util.barra(c, "<red><bold>¡PÉRDIDA! <gray>Bajá la trompa y dale potencia");
        }
        Vector dir = new Vector(-Math.sin(Math.toRadians(v.rumbo)) * Math.cos(Math.toRadians(v.cabeceo)),
                Math.sin(Math.toRadians(v.cabeceo)), Math.cos(Math.toRadians(v.rumbo)) * Math.cos(Math.toRadians(v.cabeceo)));
        Vector paso = dir.multiply(v.vel * dt).add(new Vector(0, -hundir * dt, 0));
        World w = mundo();
        double nx = v.x + paso.getX(), ny = v.y + paso.getY(), nz = v.z + paso.getZ();
        double suelo = suelo(w, nx, Math.max(ny, v.y) + 1.5, nz);
        if (v.enTierra) {
            ny = suelo;
            if (v.vel >= 18 && v.cabeceo > 3) {
                v.enTierra = false;
                ny = suelo + 0.3;
            }
            // En tierra no cruza paredes.
            Block frente = w.getBlockAt((int) Math.floor(nx + dir.getX() * 5), (int) Math.floor(ny + 1), (int) Math.floor(nz + dir.getZ() * 5));
            if (!frente.isPassable() && v.vel > 1) {
                if (v.vel > 12) destruir(v, null, "Choque");
                else v.vel = 0;
                return;
            }
        } else if (ny <= suelo + 0.1 || !w.getBlockAt((int) Math.floor(nx), (int) Math.floor(ny + 1), (int) Math.floor(nz)).isPassable()) {
            boolean aterriza = Math.abs(v.cabeceo) < 14 && v.vel < 24 && Math.abs(v.alabeo) < 25 && ny <= suelo + 0.6;
            if (aterriza) {
                v.enTierra = true;
                ny = suelo;
                v.cabeceo = 0;
                if (c != null) Util.barra(c, "<green>Aterrizaste");
            } else {
                destruir(v, null, "Se estrelló");
                return;
            }
        }
        v.x = nx;
        v.y = ny;
        v.z = nz;
        if (c != null && ahora % 6 == 0) w.playSound(new Location(w, v.x, v.y, v.z), Sound.ENTITY_MINECART_INSIDE, 0.7f,
                (float) (0.5 + v.vel / v.tipo.velMax));
        if (!v.enTierra && ahora % 2 == 0) {
            Vector cola = v.mundo(0, 1.2, -v.tipo.largo / 2);
            w.spawnParticle(Particle.CLOUD, cola.getX(), cola.getY(), cola.getZ(), 1, 0, 0, 0, 0);
        }
    }

    private static float envolver(float a) {
        a %= 360;
        if (a > 180) a -= 360;
        if (a < -180) a += 360;
        return a;
    }

    // ------------------------------------------------------------------ helicóptero

    private void moverHeli(Vehiculo v, Player c, int ahora) {
        double dt = 0.05;
        Input in = c != null ? c.getCurrentInput() : null;
        v.rotor += c != null || !v.enTierra ? 47 : 12;
        if (v.rotorCola) {
            // Sin rotor de cola: gira descontrolado y cae.
            v.rumbo += 25;
            v.velY = Math.max(-12, v.velY - 10 * dt);
        } else if (in != null) {
            if (in.isJump()) v.velY = Math.min(6, v.velY + 9 * dt);
            else if (in.isSprint()) v.velY = Math.max(-6, v.velY - 9 * dt);
            else v.velY *= 0.85;
            double objetivo = (in.isForward() ? 1 : 0) - (in.isBackward() ? 0.6 : 0);
            v.vel += (objetivo * v.tipo.velMax - v.vel) * 0.9 * dt;
            double lado = (in.isLeft() ? 1 : 0) - (in.isRight() ? 1 : 0);
            v.velLado += (lado * 9 - v.velLado) * 1.2 * dt;
            float dif = envolver(c.getLocation().getYaw() - v.rumbo);
            double tasa = 60 * dt;
            v.rumbo += (float) Math.max(-tasa, Math.min(tasa, dif));
        } else {
            // Sin piloto: baja despacio.
            v.velY = Math.max(-3, v.velY - 4 * dt);
            v.vel *= 0.95;
            v.velLado *= 0.9;
        }
        v.cabeceo = (float) (-v.vel / v.tipo.velMax * 14);
        v.alabeo = (float) (-v.velLado * 2.2);
        Vector f = new Vector(-Math.sin(Math.toRadians(v.rumbo)), 0, Math.cos(Math.toRadians(v.rumbo)));
        Vector izq = new Vector(Math.cos(Math.toRadians(v.rumbo)), 0, Math.sin(Math.toRadians(v.rumbo)));
        Vector paso = f.multiply(v.vel * dt).add(izq.multiply(v.velLado * dt)).add(new Vector(0, v.velY * dt, 0));
        World w = mundo();
        double nx = v.x + paso.getX(), ny = v.y + paso.getY(), nz = v.z + paso.getZ();
        double suelo = suelo(w, nx, Math.max(ny, v.y) + 2, nz);
        Block cuerpo = w.getBlockAt((int) Math.floor(nx), (int) Math.floor(ny + 1.5), (int) Math.floor(nz));
        if (!cuerpo.isPassable()) {
            double vel = Math.sqrt(v.vel * v.vel + v.velY * v.velY);
            if (vel > 9) {
                destruir(v, null, "Se estrelló");
                return;
            }
            v.vel = -v.vel * 0.3;
            v.velLado = 0;
            return;
        }
        if (ny <= suelo) {
            if (v.velY < -8) {
                destruir(v, null, "Se estrelló");
                return;
            }
            ny = suelo;
            v.velY = Math.max(0, v.velY);
            v.enTierra = true;
            v.vel *= 0.7;
            v.velLado *= 0.7;
        } else {
            v.enTierra = false;
        }
        v.x = nx;
        v.y = ny;
        v.z = nz;
        if ((c != null || !v.enTierra) && ahora % 4 == 0) {
            w.playSound(new Location(w, v.x, v.y + 3, v.z), Sound.ENTITY_PHANTOM_FLAP, 1.2f, 0.5f);
        }
        if (!v.enTierra && v.y - suelo < 6 && ahora % 2 == 0) {
            w.spawnParticle(Particle.CLOUD, v.x, suelo + 0.2, v.z, 6, 2.5, 0, 2.5, 0.05);
        }
    }

    // ------------------------------------------------------------------ torretas y armas

    /** La torreta sigue la mirada de quien la maneja, con velocidad limitada. */
    private void apuntar(Vehiculo v, int ahora) {
        int asientoTorreta = switch (v.tipo) {
            case TANQUE, ANTIAEREO -> 0;
            case VCI, JEEP -> 1;
            default -> -1;
        };
        if (asientoTorreta < 0) return;
        Player p = ocupante(v, asientoTorreta);
        if (p == null) return;
        float quiere = envolver(p.getLocation().getYaw() - v.rumbo);
        float tasa = switch (v.tipo) {
            case TANQUE -> 2.0f;
            case VCI, ANTIAEREO -> 3.0f;
            default -> 30f;
        };
        float dif = envolver(quiere - v.torreta);
        v.torreta = envolver(v.torreta + Math.max(-tasa, Math.min(tasa, dif)));
        float[] lim = switch (v.tipo) {
            case TANQUE -> new float[]{-8, 20};
            case ANTIAEREO -> new float[]{-5, 80};
            case VCI -> new float[]{-10, 45};
            default -> new float[]{-15, 40};
        };
        float quiereCanon = Math.max(lim[0], Math.min(lim[1], -p.getLocation().getPitch() - v.cabeceo));
        v.canon += Math.max(-tasa, Math.min(tasa, quiereCanon - v.canon));
        // Retícula: dónde pegaría el cañón (solo la ve el artillero).
        if (ahora % 2 == 0 && (v.tipo == TipoVehiculo.TANQUE || v.tipo == TipoVehiculo.VCI)) {
            Vector boca = v.bocaCanon();
            Vector d = v.direccionCanon();
            RayTraceResult r = mundo().rayTraceBlocks(boca.toLocation(mundo()), d, 200, FluidCollisionMode.NEVER, true);
            Vector punto = r != null ? r.getHitPosition() : boca.clone().add(d.clone().multiply(200));
            p.spawnParticle(Particle.DUST, punto.toLocation(mundo()), 2, 0.05, 0.05, 0.05, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 40, 40), 1.6f));
        }
    }

    /** Armas que se mantienen apretadas (clic derecho): ametralladoras y cañones automáticos. */
    private void armasAutomaticas(Vehiculo v, int ahora) {
        for (int i = 0; i < v.ocupantes.length; i++) {
            Player p = ocupante(v, i);
            if (p == null || gatillo.getOrDefault(p.getUniqueId(), 0) < ahora) continue;
            Puesto pu = v.tipo.asientos.get(i).puesto();
            if (pu == Puesto.PASAJERO) continue;
            int cadencia;
            switch (v.tipo) {
                case JEEP -> cadencia = 3;
                case TANQUE -> cadencia = i == 0 ? 3 : 3;
                case VCI -> cadencia = 5;
                case ANTIAEREO -> cadencia = 3;
                case AVION -> cadencia = 2;
                case HELICOPTERO -> cadencia = i == 1 ? 4 : 0;
                default -> cadencia = 0;
            }
            if (cadencia == 0 || ahora - v.ultimoDisparoAuto < cadencia) continue;
            if (v.canonHasta > ahora && (v.tipo == TipoVehiculo.VCI || v.tipo == TipoVehiculo.ANTIAEREO)) {
                if (ahora % 20 == 0) Util.barra(p, "<red>Cañón dañado");
                continue;
            }
            v.ultimoDisparoAuto = ahora;
            switch (v.tipo) {
                case VCI -> proyectiles.lanzar(Proyectiles.Municion.CANON_25, p, v, v.bocaCanon(), v.direccionCanon(), null);
                case ANTIAEREO -> {
                    Vector d = v.direccionCanon();
                    Vector boca = v.bocaCanon();
                    Vector lado = new Vector(-d.getZ(), 0, d.getX()).normalize().multiply(0.6);
                    proyectiles.lanzar(Proyectiles.Municion.FLAK, p, v, boca.clone().add(lado), d, null);
                    proyectiles.lanzar(Proyectiles.Municion.FLAK, p, v, boca.clone().subtract(lado), d, null);
                }
                case AVION -> metralla(v, p, v.mundo(0, 1.0, v.tipo.largo / 2 + 1), v.adelante(), 6, 5, 0.02, "Cañón del avión");
                case HELICOPTERO -> metralla(v, p, v.mundo(0, 0.6, 3.2), p.getEyeLocation().getDirection(), 7, 9, 0.025, "Cañón de 30 mm");
                default -> {
                    // Ametralladoras: la .50 del jeep y del techo del tanque, y la coaxial.
                    Vector desde = v.tipo == TipoVehiculo.TANQUE && i == 0 ? v.bocaCanon() : p.getEyeLocation().toVector()
                            .add(p.getEyeLocation().getDirection().multiply(1.2));
                    Vector d = v.tipo == TipoVehiculo.TANQUE && i == 0 ? v.direccionCanon() : p.getEyeLocation().getDirection();
                    metralla(v, p, desde, d, 4, 2, 0.03, "Ametralladora .50");
                }
            }
        }
    }

    /** Disparo instantáneo de ametralladora o cañón liviano. */
    private void metralla(Vehiculo propio, Player p, Vector desde, Vector dir, double danioInf, double danioVeh, double disp,
                          String arma) {
        World w = mundo();
        Vector d = dir.clone().normalize().add(new Vector((Math.random() - 0.5) * disp * 2, (Math.random() - 0.5) * disp * 2,
                (Math.random() - 0.5) * disp * 2)).normalize();
        double alcance = 160;
        RayTraceResult rb = w.rayTraceBlocks(desde.toLocation(w), d, alcance, FluidCollisionMode.NEVER, true);
        double max = rb == null ? alcance : rb.getHitPosition().distance(desde);
        Golpe g = rayo(desde, d, max, propio);
        RayTraceResult re = w.rayTraceEntities(desde.toLocation(w), d, g != null ? g.distancia() : max, 0.25,
                e -> e instanceof Player o && o != p && o.getGameMode() == GameMode.ADVENTURE && !protegido(o) && !modo.aliados(p, o));
        double dist;
        if (re != null && re.getHitEntity() instanceof LivingEntity le) {
            Armas.danioDirecto(le, danioInf, p, arma, false);
            dist = re.getHitPosition().distance(desde);
        } else if (g != null) {
            double dv = g.vehiculo().tipo.blindaje == TipoVehiculo.Blindaje.PESADO ? 0
                    : g.vehiculo().tipo.blindaje == TipoVehiculo.Blindaje.MEDIO ? danioVeh * 0.3 : danioVeh;
            if (dv > 0) daniar(g.vehiculo(), dv, p, arma, d, false, false);
            dist = g.distancia();
            Vector pt = desde.clone().add(d.clone().multiply(dist));
            w.spawnParticle(Particle.CRIT, pt.getX(), pt.getY(), pt.getZ(), 3, 0.1, 0.1, 0.1, 0.1);
        } else {
            dist = max;
            if (rb != null) w.spawnParticle(Particle.BLOCK, rb.getHitPosition().toLocation(w), 4, 0.05, 0.05, 0.05, 0,
                    rb.getHitBlock() != null ? rb.getHitBlock().getBlockData() : Material.STONE.createBlockData());
        }
        Particle.DustOptions polvo = new Particle.DustOptions(Color.fromRGB(255, 200, 90), 0.6f);
        for (double s = 1; s < dist; s += Math.max(1, dist / 30)) {
            Vector q = desde.clone().add(d.clone().multiply(s));
            w.spawnParticle(Particle.DUST, q.getX(), q.getY(), q.getZ(), 1, 0, 0, 0, 0, polvo);
        }
        w.playSound(desde.toLocation(w), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.4f, 1.3f);
    }

    /** Clic izquierdo: cañón del tanque, TOW, misiles, cohetes y bombas. */
    private void disparoPesado(Vehiculo v, Player p, int asiento) {
        int ahora = Bukkit.getCurrentTick();
        switch (v.tipo) {
            case TANQUE -> {
                if (asiento != 0) return;
                if (v.canonHasta > ahora) {
                    Util.barra(p, "<red>Cañón dañado");
                    return;
                }
                if (v.recargaHasta > ahora) {
                    Util.barra(p, "<gray>Recargando… " + String.format("%.1f", (v.recargaHasta - ahora) / 20.0) + " s");
                    return;
                }
                v.recargaHasta = ahora + 80;
                proyectiles.lanzar(v.municionHE ? Proyectiles.Municion.HE : Proyectiles.Municion.AP, p, v, v.bocaCanon(),
                        v.direccionCanon(), null);
            }
            case VCI -> {
                if (asiento != 1 || v.misiles <= 0) {
                    if (asiento == 1) Util.barra(p, "<gray>Sin misiles TOW (reponé en la base)");
                    return;
                }
                if (v.recargaSecHasta > ahora) return;
                v.recargaSecHasta = ahora + 100;
                v.misiles--;
                proyectiles.lanzar(Proyectiles.Municion.TOW, p, v, v.bocaCanon(), v.direccionCanon(), null);
            }
            case AVION -> {
                if (v.recargaSecHasta > ahora) return;
                if (v.secundarioBombas) {
                    if (v.bombas <= 0) {
                        Util.barra(p, "<gray>Sin bombas (aterrizá en tu pista)");
                        return;
                    }
                    v.bombas--;
                    v.recargaSecHasta = ahora + 20;
                    Vector vel = v.adelante().multiply(v.vel / 20.0);
                    proyectiles.lanzarConVelocidad(Proyectiles.Municion.BOMBA, p, v, v.mundo(0, -0.3, 0), vel);
                } else {
                    if (v.misiles <= 0) {
                        Util.barra(p, "<gray>Sin misiles (aterrizá en tu pista)");
                        return;
                    }
                    Vehiculo blanco = blancoEnCono(p, v, 160, 12, true);
                    if (blanco == null) {
                        Util.barra(p, "<gray>Sin blanco aéreo en la mira");
                        return;
                    }
                    v.misiles--;
                    v.recargaSecHasta = ahora + 30;
                    proyectiles.lanzar(Proyectiles.Municion.MISIL_AIRE, p, v, v.mundo(0, 0.5, v.tipo.largo / 2), v.adelante(), blanco);
                    avisarFijado(blanco);
                }
            }
            case HELICOPTERO -> {
                if (asiento == 0) {
                    if (v.cohetes <= 0) {
                        Util.barra(p, "<gray>Sin cohetes (volvé al helipuerto)");
                        return;
                    }
                    if (v.recargaHasta > ahora) return;
                    v.recargaHasta = ahora + 6;
                    for (int k = -1; k <= 1; k += 2) {
                        if (v.cohetes <= 0) break;
                        v.cohetes--;
                        Vector d = v.adelante().add(new Vector(0, -0.05, 0)).normalize();
                        proyectiles.lanzar(Proyectiles.Municion.COHETE, p, v, v.mundo(k * 1.4, 0.6, 1.0), d, null);
                    }
                } else {
                    Vehiculo blanco = fijado.get(p.getUniqueId());
                    if (blanco == null || !blanco.vivo()) {
                        Util.barra(p, "<gray>Apuntá 1,5 s a un vehículo para fijarlo");
                        return;
                    }
                    if (v.misiles <= 0 || v.recargaSecHasta > ahora) return;
                    v.misiles--;
                    v.recargaSecHasta = ahora + 30;
                    proyectiles.lanzar(Proyectiles.Municion.MISIL_HELI, p, v, v.mundo(0, 0.4, 2.5), p.getEyeLocation().getDirection(), blanco);
                    avisarFijado(blanco);
                }
            }
            default -> { }
        }
    }

    /** Vehículo enemigo dentro de un cono frente a la mira del jugador. */
    public Vehiculo blancoEnCono(Player p, Vehiculo propio, double alcance, double grados, boolean aereo) {
        Vector ojo = p.getEyeLocation().toVector();
        Vector mira = p.getEyeLocation().getDirection();
        Vehiculo mejor = null;
        double mejorAng = grados;
        ModoGuerra.Equipo eq = modo.equipo(p);
        for (Vehiculo v : vehiculos) {
            if (!v.vivo() || v == propio || v.equipo == eq || v.esAereo() != aereo) continue;
            Vector c = new Vector(v.x, v.y + v.tipo.alto / 2, v.z);
            Vector hacia = c.clone().subtract(ojo);
            double d = hacia.length();
            if (d > alcance || d < 3) continue;
            double ang = Math.toDegrees(mira.angle(hacia));
            if (ang < mejorAng) {
                RayTraceResult r = mundo().rayTraceBlocks(ojo.toLocation(mundo()), hacia.clone().normalize(), d - 2,
                        FluidCollisionMode.NEVER, true);
                if (r != null) continue;
                mejorAng = ang;
                mejor = v;
            }
        }
        return mejor;
    }

    /** Fijación por mirar sostenido (Javelin, Stinger, misiles del helicóptero). Devuelve el blanco si ya está fijado. */
    public Vehiculo fijar(Player p, Vehiculo propio, boolean aereo, int ticks, double alcance) {
        int ahora = Bukkit.getCurrentTick();
        Vehiculo b = blancoEnCono(p, propio, alcance, 5, aereo);
        Vehiculo antes = fijado.get(p.getUniqueId());
        if (b == null) {
            fijando.remove(p.getUniqueId());
            fijado.remove(p.getUniqueId());
            return null;
        }
        if (antes != b) {
            fijado.put(p.getUniqueId(), b);
            fijando.put(p.getUniqueId(), ahora);
        }
        int desde = fijando.getOrDefault(p.getUniqueId(), ahora);
        int pasado = ahora - desde;
        if (pasado >= ticks) {
            if (pasado - ticks < 5) p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 2f);
            return b;
        }
        if (ahora % 4 == 0) p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 0.6f, 1f + pasado / (float) ticks);
        Util.barra(p, "<yellow>Fijando " + b.tipo.nombre.toLowerCase() + " " + "▮".repeat(Math.max(0, pasado * 5 / ticks))
                + "<dark_gray>" + "▯".repeat(5 - Math.max(0, pasado * 5 / ticks)));
        return null;
    }

    public void soltarFijacion(Player p) {
        fijando.remove(p.getUniqueId());
        fijado.remove(p.getUniqueId());
    }

    private void avisarFijado(Vehiculo blanco) {
        for (int i = 0; i < blanco.ocupantes.length; i++) {
            Player o = ocupante(blanco, i);
            if (o == null) continue;
            Util.titulo(o, "", "<red><bold>¡MISIL!", 0, 1500, 200);
            o.playSound(o, Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);
        }
    }

    /** Antiaéreo y artillero del helicóptero: el radar y la fijación. */
    private void estado(Vehiculo v, int ahora) {
        // Fijación del artillero del helicóptero (mirando 1,5 s).
        if (v.tipo == TipoVehiculo.HELICOPTERO) {
            Player g = ocupante(v, 1);
            if (g != null && ahora % 2 == 0) fijar(g, v, false, 30, 180);
        }
        // Radar del antiaéreo: marca los aviones y helicópteros enemigos para su equipo.
        if (v.tipo == TipoVehiculo.ANTIAEREO && ocupante(v, 0) != null && ahora % 10 == 0) {
            for (Vehiculo a : vehiculos) {
                if (!a.vivo() || !a.esAereo() || a.equipo == v.equipo) continue;
                if (Math.hypot(a.x - v.x, a.z - v.z) > 250) continue;
                for (Player p : modo.jugadores()) {
                    if (modo.equipo(p) != v.equipo) continue;
                    p.spawnParticle(Particle.DUST, a.x, a.y + a.tipo.alto + 2, a.z, 6, 0.3, 0.3, 0.3, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 60, 60), 2.5f));
                }
            }
        }
        // Incendio: pierde vida hasta apagarlo.
        if (v.incendio) {
            if (ahora % 10 == 0) {
                daniar(v, 1, v.ultimoAtacante == null ? null : Bukkit.getPlayer(v.ultimoAtacante), v.ultimaArma, new Vector(0, 1, 0), false, false);
                if (!v.vivo()) return;
            }
            Vector m = v.mundo(0, v.tipo.alto * 0.8, -v.tipo.largo * 0.35);
            mundo().spawnParticle(Particle.FLAME, m.getX(), m.getY(), m.getZ(), 4, 0.4, 0.3, 0.4, 0.02);
            mundo().spawnParticle(Particle.LARGE_SMOKE, m.getX(), m.getY() + 0.5, m.getZ(), 2, 0.3, 0.3, 0.3, 0.02);
        } else if (v.vida < v.tipo.vida * 0.35 && ahora % 4 == 0) {
            mundo().spawnParticle(Particle.LARGE_SMOKE, v.x, v.y + v.tipo.alto, v.z, 1, 0.3, 0.2, 0.3, 0.02);
        }
        // Reparación y rearme en la base.
        if (ahora % 10 == 0 && enZonaReparacion(v)) {
            boolean algo = v.vida < v.tipo.vida || v.incendio || v.motorDanado;
            v.vida = Math.min(v.tipo.vida, v.vida + v.tipo.vida * 0.05);
            v.incendio = false;
            v.motorDanado = false;
            v.orugasHasta = 0;
            v.canonHasta = 0;
            v.rotorCola = false;
            v.rearmar();
            Player c = ocupante(v, 0);
            if (algo && c != null) Util.barra(c, "<green>Reparando " + (int) v.vida + "/" + (int) v.tipo.vida);
        }
        // Abandonado lejos de la base: vuelve solo a los 90 s.
        if (v.vacio()) {
            if (ahora - v.sinUsoDesde > 20 * 90 && Math.hypot(v.x - v.origen.getX(), v.z - v.origen.getZ()) > 20) {
                Aparicion a = aparicionDe(v);
                quitar(v);
                vehiculos.remove(v);
                if (a != null) {
                    deAparicion.remove(a);
                    esperan.put(a, ahora + 20);
                }
            }
        } else {
            v.sinUsoDesde = ahora;
        }
    }

    private boolean enZonaReparacion(Vehiculo v) {
        int bx = v.equipo.baseX();
        if (v.tipo == TipoVehiculo.AVION) {
            return v.enTierra && v.vel < 3 && Math.abs(v.x - bx) <= 6 && v.z >= -186 && v.z <= -58;
        }
        if (v.tipo == TipoVehiculo.HELICOPTERO) return v.enTierra && Math.abs(v.x - bx) <= 5 && Math.abs(v.z - 42) <= 5;
        return Math.abs(v.x - bx) <= 5 && Math.abs(v.z - 22) <= 5;
    }

    private Aparicion aparicionDe(Vehiculo v) {
        for (Map.Entry<Aparicion, Vehiculo> en : deAparicion.entrySet()) if (en.getValue() == v) return en.getKey();
        return null;
    }

    private void hud(Vehiculo v, int ahora) {
        for (int i = 0; i < v.ocupantes.length; i++) {
            Player p = ocupante(v, i);
            if (p == null || v.tipo.asientos.get(i).puesto() == Puesto.PASAJERO) continue;
            StringBuilder sb = new StringBuilder("<white>" + v.tipo.nombre + " ");
            double frac = v.vida / v.tipo.vida;
            sb.append(frac > 0.6 ? "<green>" : frac > 0.3 ? "<yellow>" : "<red>").append("❤ ").append((int) Math.ceil(v.vida))
                    .append("<gray>/").append((int) v.tipo.vida);
            double vel = v.tipo.movimiento == Movimiento.HELI ? Math.hypot(v.vel, v.velLado) : Math.abs(v.vel);
            sb.append(" <gray>· <white>").append((int) Math.round(vel)).append(" b/s");
            if (v.tipo == TipoVehiculo.AVION) sb.append(" <gray>· ").append((int) (v.potencia * 100)).append("% · alt ")
                    .append((int) (v.y - ValleDeHierro.SUELO));
            if (v.tipo == TipoVehiculo.TANQUE && i == 0) {
                sb.append(" <gray>· <yellow>").append(v.municionHE ? "HE" : "AP");
                int resta = v.recargaHasta - ahora;
                sb.append(resta > 0 ? " <red>" + "▮".repeat(Math.max(0, 4 - resta / 20)) + "<dark_gray>" + "▯".repeat(Math.min(4, resta / 20 + 0)) : " <green>LISTO");
            }
            if (v.tipo == TipoVehiculo.AVION) sb.append(" <gray>· ").append(v.secundarioBombas ? "bombas " + v.bombas : "misiles " + v.misiles);
            if (v.tipo == TipoVehiculo.HELICOPTERO) sb.append(i == 0 ? " <gray>· cohetes " + v.cohetes : " <gray>· misiles " + v.misiles);
            if (v.tipo == TipoVehiculo.VCI && i == 1) sb.append(" <gray>· TOW ").append(v.misiles);
            if (!v.movil(ahora)) sb.append(" <red>⚠ orugas");
            if (v.motorDanado) sb.append(" <red>⚠ motor");
            if (v.canonHasta > ahora) sb.append(" <red>⚠ cañón");
            if (v.rotorCola) sb.append(" <red>⚠ rotor de cola");
            if (v.incendio) sb.append(" <gold>🔥 Q: extintor");
            p.sendActionBar(Util.mm(sb.toString()));
        }
    }

    // ------------------------------------------------------------------ daño

    /** Rayo contra todos los vehículos (salvo uno): el más cercano. */
    public Golpe rayo(Vector origen, Vector dir, double max, Vehiculo excluir) {
        Golpe mejor = null;
        Vector d = dir.clone().normalize();
        for (Vehiculo v : vehiculos) {
            if (!v.vivo() || v == excluir) continue;
            double cx = v.x - origen.getX(), cz = v.z - origen.getZ();
            double r = Math.max(v.tipo.largo, v.tipo.ancho) + max;
            if (cx * cx + cz * cz > r * r) continue;
            double t = v.rayo(origen, d, max);
            if (t >= 0 && (mejor == null || t < mejor.distancia())) mejor = new Golpe(v, t);
        }
        return mejor;
    }

    public Vehiculo aereoCerca(Vector p, double r, ModoGuerra.Equipo propio) {
        for (Vehiculo v : vehiculos) {
            if (!v.vivo() || !v.esAereo() || v.equipo == propio || v.enTierra) continue;
            if (new Vector(v.x, v.y + v.tipo.alto / 2, v.z).distance(p) <= r + v.tipo.largo / 3) return v;
        }
        return null;
    }

    /** Bala de infantería contra un vehículo: nada al tanque, poco a los livianos. */
    public void impactoBala(Vehiculo v, Player tirador, Armas.Tipo t, Vector dir) {
        if (tirador != null && modo.equipo(tirador) == v.equipo) return;
        double d = switch (v.tipo.blindaje) {
            case PESADO, MEDIO -> 0;
            case LIGERO -> t == Armas.Tipo.FRANCOTIRADOR ? 25 : 0.6;
            case NINGUNO -> t == Armas.Tipo.FRANCOTIRADOR ? 25 : 1.5;
        };
        Vector p = new Vector(v.x, v.y + 1, v.z);
        mundo().spawnParticle(Particle.CRIT, p.getX(), p.getY(), p.getZ(), 2, 0.4, 0.4, 0.4, 0.1);
        if (d > 0) daniar(v, d, tirador, t.nombre, dir, false, t == Armas.Tipo.FRANCOTIRADOR);
        else mundo().playSound(p.toLocation(mundo()), Sound.BLOCK_ANVIL_LAND, 0.3f, 2f);
    }

    /**
     * Daño a un vehículo con su zona de blindaje. Con puedeModulos, los golpes fuertes pueden romper
     * orugas (inmoviliza 8 s), motor (mitad de velocidad), cañón (6 s sin disparar), rotor de cola o
     * prender fuego (trasera).
     */
    public void daniar(Vehiculo v, double danio, Player autor, String arma, Vector dir, boolean desdeArriba, boolean puedeModulos) {
        if (!v.vivo() || danio <= 0) return;
        if (autor != null && modo.equipo(autor) == v.equipo) return;
        double zona = v.zona(dir, desdeArriba);
        String nombreZona = v.zonaNombre(dir, desdeArriba);
        double total = danio * zona;
        v.vida -= total;
        if (autor != null) {
            v.ultimoAtacante = autor.getUniqueId();
            v.ultimaArma = arma;
            if (total >= 3) Util.barra(autor, "<gold>" + v.tipo.nombre + " <white>−" + (int) Math.round(total)
                    + (zona != 1 ? " <gray>(" + nombreZona + " ×" + String.format("%.1f", zona) + ")" : ""));
            ar.tresmodos.Hud.marcador(autor, v.vida <= 0);
        }
        int ahora = Bukkit.getCurrentTick();
        if (puedeModulos && danio >= 25) {
            double r = Math.random();
            if (v.esAereo()) {
                if (v.tipo == TipoVehiculo.HELICOPTERO && nombreZona.equals("trasera") && r < 0.35) {
                    v.rotorCola = true;
                    avisarOcupantes(v, "<red><bold>¡ROTOR DE COLA!");
                }
            } else {
                if (r < 0.22 && !nombreZona.equals("techo")) {
                    v.orugasHasta = ahora + 160;
                    avisarOcupantes(v, "<red>Orugas rotas: inmovilizado");
                } else if (r < 0.40 && nombreZona.equals("trasera")) {
                    v.motorDanado = true;
                    avisarOcupantes(v, "<red>Motor dañado");
                } else if (r < 0.52 && (nombreZona.equals("frente") || nombreZona.equals("techo"))) {
                    v.canonHasta = ahora + 120;
                    avisarOcupantes(v, "<red>Cañón dañado");
                }
                if (nombreZona.equals("trasera") && Math.random() < 0.25) {
                    v.incendio = true;
                    avisarOcupantes(v, "<gold>¡Incendio! Q: extintor");
                }
            }
        }
        if (v.vida <= 0) destruir(v, autor, arma);
    }

    private void avisarOcupantes(Vehiculo v, String s) {
        for (int i = 0; i < v.ocupantes.length; i++) {
            Player p = ocupante(v, i);
            if (p != null) Util.titulo(p, "", s, 0, 1200, 300);
        }
    }

    /** Explota, eyecta a los ocupantes con daño y deja una carcasa humeante; reaparece en su base. */
    public void destruir(Vehiculo v, Player autor, String arma) {
        if (!v.vivo()) return;
        if (autor == null && v.ultimoAtacante != null) {
            autor = Bukkit.getPlayer(v.ultimoAtacante);
            arma = v.ultimaArma;
        }
        World w = mundo();
        Location l = new Location(w, v.x, v.y + 1, v.z);
        List<Player> adentro = new ArrayList<>();
        for (int i = 0; i < v.ocupantes.length; i++) {
            Player p = ocupante(v, i);
            if (p != null) adentro.add(p);
        }
        Aparicion a = aparicionDe(v);
        quitar(v);
        vehiculos.remove(v);
        for (Player p : adentro) {
            p.removePotionEffect(PotionEffectType.INVISIBILITY);
            if (autor != null) plugin.armas().registrarImpacto(p, autor, arma, false);
            p.setVelocity(new Vector(Math.random() - 0.5, 1.0, Math.random() - 0.5));
            if (v.esAereo() && !v.enTierra) paracaidas.add(p.getUniqueId());
            double d = v.tipo.cerrado ? 1000 : 10;
            if (autor != null) {
                ar.tresmodos.Armas.danioDirecto(p, d, autor, arma, false);
            } else {
                p.damage(d);
            }
        }
        w.spawnParticle(Particle.EXPLOSION_EMITTER, l, 2, 1, 0.5, 1, 0);
        w.spawnParticle(Particle.FLAME, l, 80, 1.5, 1, 1.5, 0.1);
        w.spawnParticle(Particle.LARGE_SMOKE, l, 60, 1.5, 1.5, 1.5, 0.05);
        w.playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 3f, 0.5f);
        proyectiles.explotar(l, 4, 14, 0, autor, v.tipo.nombre + " destruido", false, null);
        // Carcasa humeante.
        for (int k = 0; k < 20; k++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                w.spawnParticle(Particle.CAMPFIRE_SIGNAL_SMOKE, l, 3, 0.8, 0.3, 0.8, 0.01);
                w.spawnParticle(Particle.FLAME, l, 2, 0.6, 0.2, 0.6, 0.01);
            }, k * 20L);
        }
        if (autor != null) {
            for (Player p : modo.jugadores()) Util.msg(p, (modo.equipo(autor) == null ? "" : modo.equipo(autor).color) + autor.getName()
                    + " <gray>destruyó un <white>" + v.tipo.nombre.toLowerCase() + " <gray>[" + arma + "]");
        }
        if (a != null) {
            deAparicion.remove(a);
            esperan.put(a, Bukkit.getCurrentTick() + v.tipo.reaparicion * 20);
        }
    }

    public void olvidar(Player p) {
        if (de(p) != null) bajar(p, false);
        deJugador.remove(p.getUniqueId());
        shiftDesde.remove(p.getUniqueId());
        gatillo.remove(p.getUniqueId());
        paracaidas.remove(p.getUniqueId());
        soltarFijacion(p);
    }

    public void apagar() {
        for (Vehiculo v : new ArrayList<>(vehiculos)) quitar(v);
        vehiculos.clear();
        proyectiles.limpiar();
    }
}
