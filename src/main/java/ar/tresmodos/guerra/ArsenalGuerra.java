package ar.tresmodos.guerra;

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
import org.bukkit.block.Block;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
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
import java.util.UUID;

/**
 * El equipo de infantería contra vehículos y de apoyo: RPG-7, AT4, Javelin y Stinger (con fijación),
 * C4 con detonador, minas antitanque, granada antitanque que se pega, llave de reparación y
 * binoculares que marcan blancos para el equipo. También es la puerta de entrada a los vehículos.
 */
public class ArsenalGuerra implements Listener {
    private static final NamespacedKey GRANADA_AT = new NamespacedKey("tresmodos", "granada_at");

    private static final class Carga {
        final UUID duenio;
        final ItemDisplay visual;
        final Vehiculo vehiculo;
        final Vector3f local;

        Carga(UUID duenio, ItemDisplay visual, Vehiculo vehiculo, Vector3f local) {
            this.duenio = duenio;
            this.visual = visual;
            this.vehiculo = vehiculo;
            this.local = local;
        }
    }

    private static final class Mina {
        final UUID duenio;
        final ModoGuerra.Equipo equipo;
        final ItemDisplay visual;
        final int armada;

        Mina(UUID duenio, ModoGuerra.Equipo equipo, ItemDisplay visual, int armada) {
            this.duenio = duenio;
            this.equipo = equipo;
            this.visual = visual;
            this.armada = armada;
        }
    }

    private record Marca(UUID blanco, Vehiculo vehiculo, ModoGuerra.Equipo equipo, int hasta) {}

    private final TresModos plugin;
    private final ModoGuerra modo;
    private final Vehiculos vehiculos;
    private final List<Carga> cargas = new ArrayList<>();
    private final List<Mina> minas = new ArrayList<>();
    private final List<Marca> marcas = new ArrayList<>();
    private final Map<UUID, Integer> esperaLanzador = new HashMap<>();
    private final Map<Snowball, UUID> granadas = new HashMap<>();

    public ArsenalGuerra(TresModos plugin, ModoGuerra modo) {
        this.plugin = plugin;
        this.modo = modo;
        this.vehiculos = new Vehiculos(plugin, modo);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40, 2);
    }

    public Vehiculos vehiculos() {
        return vehiculos;
    }

    /** Maneja un arma del vehículo (no usa las propias). */
    public boolean ocupado(Player p) {
        return vehiculos.ocupado(p);
    }

    public boolean enVehiculo(Player p) {
        return vehiculos.de(p) != null;
    }

    /** El portador no puede ir en tanque, avión ni antiaéreo. */
    public boolean enVehiculoProhibido(Player p) {
        Vehiculo v = vehiculos.de(p);
        return v != null && v.tipo.prohibidoAlPortador();
    }

    private boolean activo(Player p) {
        return Modo.de(p.getWorld()) == Modo.GUERRA && !modo.bloqueaDisparo(p);
    }

    // ------------------------------------------------------------------ uso

    @EventHandler(priority = EventPriority.HIGH)
    public void alUsar(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (Modo.de(p.getWorld()) != Modo.GUERRA) return;
        ItemStack it = e.getItem();
        ArmaGuerra a = ArmaGuerra.de(it);
        if (a == null || a == ArmaGuerra.BOTIQUIN) return;
        e.setCancelled(true);
        if (!activo(p)) return;
        int ahora = Bukkit.getCurrentTick();
        switch (a) {
            case RPG7, AT4 -> {
                if (ahora < esperaLanzador.getOrDefault(p.getUniqueId(), 0)) return;
                esperaLanzador.put(p.getUniqueId(), ahora + (a == ArmaGuerra.RPG7 ? 50 : 20));
                Location ojo = p.getEyeLocation();
                vehiculos.proyectiles().lanzar(a == ArmaGuerra.RPG7 ? Proyectiles.Municion.RPG : Proyectiles.Municion.AT4, p,
                        vehiculos.de(p), ojo.toVector().add(ojo.getDirection().multiply(0.8)), ojo.getDirection(), null);
                p.setVelocity(p.getVelocity().add(ojo.getDirection().multiply(-0.25)));
                gastar(p, it);
            }
            case JAVELIN, STINGER -> {
                boolean aereo = a == ArmaGuerra.STINGER;
                Vehiculo blanco = vehiculos.fijar(p, vehiculos.de(p), aereo, 40, aereo ? 220 : 180);
                if (blanco == null) return;
                if (ahora < esperaLanzador.getOrDefault(p.getUniqueId(), 0)) return;
                esperaLanzador.put(p.getUniqueId(), ahora + 40);
                Location ojo = p.getEyeLocation();
                Vector dir = aereo ? ojo.getDirection() : ojo.getDirection().clone().setY(Math.max(0.5, ojo.getDirection().getY() + 0.6));
                vehiculos.proyectiles().lanzar(aereo ? Proyectiles.Municion.STINGER : Proyectiles.Municion.JAVELIN, p,
                        vehiculos.de(p), ojo.toVector().add(ojo.getDirection().multiply(0.8)), dir, blanco);
                vehiculos.soltarFijacion(p);
                gastar(p, it);
                for (UUID u : blanco.ocupantes) {
                    Player o = u == null ? null : Bukkit.getPlayer(u);
                    if (o != null) Util.titulo(o, "", "<red><bold>¡MISIL!", 0, 1500, 200);
                }
            }
            case C4 -> ponerC4(p, it);
            case DETONADOR -> detonar(p);
            case MINA_AT -> ponerMina(p, it, e.getClickedBlock());
            case GRANADA_AT -> {
                Snowball s = p.launchProjectile(Snowball.class, p.getEyeLocation().getDirection().multiply(1.1));
                ItemStack vis = new ItemStack(Material.CLAY_BALL);
                ItemMeta m = vis.getItemMeta();
                m.setItemModel(new NamespacedKey("tresmodos", ArmaGuerra.GRANADA_AT.modelo));
                vis.setItemMeta(m);
                s.setItem(vis);
                s.getPersistentDataContainer().set(GRANADA_AT, PersistentDataType.BYTE, (byte) 1);
                granadas.put(s, p.getUniqueId());
                gastar(p, it);
            }
            case LLAVE -> reparar(p);
            case BINOCULARES -> marcar(p);
            default -> { }
        }
    }

    private static void gastar(Player p, ItemStack it) {
        it.setAmount(it.getAmount() - 1);
        if (it.getAmount() <= 0) p.getInventory().setItemInMainHand(null);
    }

    // ------------------------------------------------------------------ C4

    private void ponerC4(Player p, ItemStack it) {
        long propias = cargas.stream().filter(c -> c.duenio.equals(p.getUniqueId())).count();
        if (propias >= 3) {
            Util.barra(p, "<gray>Ya tenés 3 cargas puestas.");
            return;
        }
        Vehiculos.Golpe g = vehiculos.mirando(p, 4);
        World w = p.getWorld();
        ItemStack vis = visual(ArmaGuerra.C4.modelo);
        if (g != null) {
            Vehiculo v = g.vehiculo();
            Vector punto = p.getEyeLocation().toVector().add(p.getEyeLocation().getDirection().multiply(g.distancia()));
            Vector3f local = v.local(punto);
            ItemDisplay d = w.spawn(punto.toLocation(w), ItemDisplay.class, x -> {
                x.setItemStack(vis);
                x.setPersistent(false);
                x.setTeleportDuration(2);
                x.setTransformation(escala(0.45f));
            });
            cargas.add(new Carga(p.getUniqueId(), d, v, local));
            Util.barra(p, "<yellow>C4 pegado al " + v.tipo.nombre.toLowerCase());
        } else {
            RayTraceResult r = w.rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), 4, FluidCollisionMode.NEVER, true);
            if (r == null || r.getHitBlock() == null) {
                Util.barra(p, "<gray>Apuntá a un bloque o a un vehículo cerca.");
                return;
            }
            Location l = r.getHitPosition().toLocation(w);
            ItemDisplay d = w.spawn(l, ItemDisplay.class, x -> {
                x.setItemStack(vis);
                x.setPersistent(false);
                x.setTransformation(escala(0.45f));
            });
            cargas.add(new Carga(p.getUniqueId(), d, null, null));
            Util.barra(p, "<yellow>C4 puesto · usá el detonador");
        }
        w.playSound(p.getLocation(), Sound.BLOCK_SLIME_BLOCK_PLACE, 1f, 0.8f);
        gastar(p, it);
    }

    private void detonar(Player p) {
        boolean alguna = false;
        Iterator<Carga> it = cargas.iterator();
        while (it.hasNext()) {
            Carga c = it.next();
            if (!c.duenio.equals(p.getUniqueId())) continue;
            it.remove();
            alguna = true;
            Location l = c.visual.getLocation();
            c.visual.remove();
            if (c.vehiculo != null && c.vehiculo.vivo()) {
                vehiculos.daniar(c.vehiculo, 200, p, "C4", new Vector(0, -1, 0), false, true);
            }
            vehiculos.proyectiles().explotar(l, 5, 30, c.vehiculo == null ? 200 : 0, p, "C4", true, c.vehiculo);
        }
        p.playSound(p, Sound.BLOCK_NOTE_BLOCK_BIT, 1f, alguna ? 2f : 0.5f);
        if (!alguna) Util.barra(p, "<gray>No tenés C4 puesto.");
    }

    // ------------------------------------------------------------------ minas

    private void ponerMina(Player p, ItemStack it, Block clic) {
        if (clic == null) {
            Util.barra(p, "<gray>Clic derecho al piso para enterrarla.");
            return;
        }
        Location l = clic.getLocation().add(0.5, 1.02, 0.5);
        if (!l.getBlock().isPassable()) return;
        World w = p.getWorld();
        ItemDisplay d = w.spawn(l, ItemDisplay.class, x -> {
            x.setItemStack(visual(ArmaGuerra.MINA_AT.modelo));
            x.setPersistent(false);
            x.setTransformation(escala(0.7f));
        });
        minas.add(new Mina(p.getUniqueId(), modo.equipo(p), d, Bukkit.getCurrentTick() + 40));
        w.playSound(l, Sound.BLOCK_GRAVEL_PLACE, 1f, 0.7f);
        Util.barra(p, "<yellow>Mina antitanque armada en 2 s");
        gastar(p, it);
    }

    // ------------------------------------------------------------------ llave y binoculares

    private void reparar(Player p) {
        Vehiculos.Golpe g = vehiculos.mirando(p, 5);
        if (g == null) {
            Util.barra(p, "<gray>Apuntá a un vehículo aliado cerca.");
            return;
        }
        Vehiculo v = g.vehiculo();
        if (v.equipo != modo.equipo(p)) return;
        v.vida = Math.min(v.tipo.vida, v.vida + 3);
        if (Math.random() < 0.12) {
            if (v.incendio) v.incendio = false;
            else if (v.orugasHasta > Bukkit.getCurrentTick()) v.orugasHasta = 0;
            else if (v.motorDanado) v.motorDanado = false;
            else if (v.canonHasta > Bukkit.getCurrentTick()) v.canonHasta = 0;
            else if (v.rotorCola) v.rotorCola = false;
        }
        Vector punto = p.getEyeLocation().toVector().add(p.getEyeLocation().getDirection().multiply(g.distancia()));
        p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, punto.toLocation(p.getWorld()), 6, 0.2, 0.2, 0.2, 0.05);
        p.playSound(p, Sound.BLOCK_ANVIL_USE, 0.3f, 1.8f);
        Util.barra(p, "<green>Reparando " + v.tipo.nombre.toLowerCase() + " " + (int) v.vida + "/" + (int) v.tipo.vida);
    }

    private void marcar(Player p) {
        Location ojo = p.getEyeLocation();
        World w = p.getWorld();
        RayTraceResult rb = w.rayTraceBlocks(ojo, ojo.getDirection(), 250, FluidCollisionMode.NEVER, true);
        double max = rb == null ? 250 : rb.getHitPosition().distance(ojo.toVector());
        Vehiculos.Golpe g = vehiculos.rayo(ojo.toVector(), ojo.getDirection(), max, vehiculos.de(p));
        RayTraceResult re = w.rayTraceEntities(ojo, ojo.getDirection(), g != null ? g.distancia() : max, 0.6,
                e -> e instanceof Player o && o != p && !modo.aliados(p, o));
        ModoGuerra.Equipo eq = modo.equipo(p);
        if (re != null && re.getHitEntity() instanceof Player o) {
            marcas.add(new Marca(o.getUniqueId(), null, eq, Bukkit.getCurrentTick() + 200));
            avisarEquipo(p, "<gold>" + p.getName() + " marcó a " + o.getName());
        } else if (g != null && g.vehiculo().equipo != eq) {
            marcas.add(new Marca(null, g.vehiculo(), eq, Bukkit.getCurrentTick() + 200));
            avisarEquipo(p, "<gold>" + p.getName() + " marcó un " + g.vehiculo().tipo.nombre.toLowerCase());
        } else {
            Util.barra(p, "<gray>No hay ningún enemigo en la mira.");
            return;
        }
        p.playSound(p, Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f);
    }

    private void avisarEquipo(Player p, String s) {
        for (Player o : modo.jugadores()) if (o == p || modo.aliados(p, o)) Util.barra(o, s);
    }

    // ------------------------------------------------------------------ granada antitanque

    @EventHandler
    public void alImpactar(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Snowball s) || !s.getPersistentDataContainer().has(GRANADA_AT)) return;
        UUID u = granadas.remove(s);
        Player autor = u == null ? null : Bukkit.getPlayer(u);
        explotarGranada(s.getLocation(), autor, null);
        s.remove();
    }

    private void explotarGranada(Location l, Player autor, Vehiculo pegada) {
        if (pegada != null) {
            double d = pegada.tipo.blindaje == TipoVehiculo.Blindaje.PESADO || pegada.tipo.blindaje == TipoVehiculo.Blindaje.MEDIO ? 60 : 80;
            vehiculos.daniar(pegada, d, autor, "Granada antitanque", pegada.adelante().multiply(-1), false, true);
        }
        vehiculos.proyectiles().explotar(l, 2.5, 10, pegada == null ? 30 : 0, autor, "Granada antitanque", true, pegada);
    }

    // ------------------------------------------------------------------ ciclo

    private void tick() {
        int ahora = Bukkit.getCurrentTick();
        // Granadas antitanque en vuelo: si tocan un vehículo, se pegan y explotan.
        for (Map.Entry<Snowball, UUID> en : new ArrayList<>(granadas.entrySet())) {
            Snowball s = en.getKey();
            if (!s.isValid()) {
                granadas.remove(s);
                continue;
            }
            for (Vehiculo v : vehiculos.todos()) {
                if (!v.vivo() || !v.contiene(s.getLocation().toVector(), 0.4)) continue;
                Player autor = Bukkit.getPlayer(en.getValue());
                if (autor != null && modo.equipo(autor) == v.equipo) continue;
                granadas.remove(s);
                Location l = s.getLocation();
                s.remove();
                l.getWorld().playSound(l, Sound.BLOCK_SLIME_BLOCK_PLACE, 1f, 1.2f);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (v.vivo()) explotarGranada(new Location(l.getWorld(), v.x, v.y + v.tipo.alto * 0.6, v.z), autor, v);
                }, 20);
                break;
            }
        }
        // C4 pegado: sigue al vehículo.
        Iterator<Carga> ic = cargas.iterator();
        while (ic.hasNext()) {
            Carga c = ic.next();
            if (!c.visual.isValid()) {
                ic.remove();
                continue;
            }
            if (c.vehiculo != null) {
                if (!c.vehiculo.vivo()) {
                    c.visual.remove();
                    ic.remove();
                    continue;
                }
                Vector p = c.vehiculo.mundo(c.local.x, c.local.y, c.local.z);
                c.visual.teleport(p.toLocation(c.visual.getWorld()));
            }
        }
        // Minas: las pisa un vehículo enemigo.
        Iterator<Mina> im = minas.iterator();
        while (im.hasNext()) {
            Mina m = im.next();
            if (!m.visual.isValid()) {
                im.remove();
                continue;
            }
            if (ahora < m.armada) continue;
            Location l = m.visual.getLocation();
            for (Vehiculo v : vehiculos.todos()) {
                if (!v.vivo() || v.equipo == m.equipo || v.esAereo() && !v.enTierra) continue;
                if (!v.contiene(l.toVector().add(new Vector(0, 0.3, 0)), 0.5)) continue;
                Player autor = Bukkit.getPlayer(m.duenio);
                vehiculos.daniar(v, 150, autor, "Mina antitanque", new Vector(0, 1, 0), false, false);
                if (v.vivo()) v.orugasHasta = ahora + 160;
                vehiculos.proyectiles().explotar(l, 3, 24, 0, autor, "Mina antitanque", true, v);
                m.visual.remove();
                im.remove();
                break;
            }
        }
        // Marcas de binoculares: solo las ve el equipo que marcó.
        marcas.removeIf(m -> m.hasta() < ahora);
        for (Marca m : marcas) {
            Location l;
            if (m.vehiculo() != null) {
                if (!m.vehiculo().vivo()) continue;
                l = new Location(modo.mundo(), m.vehiculo().x, m.vehiculo().y + m.vehiculo().tipo.alto + 1.5, m.vehiculo().z);
            } else {
                Player b = Bukkit.getPlayer(m.blanco());
                if (b == null || b.isDead() || Modo.de(b.getWorld()) != Modo.GUERRA) continue;
                l = b.getLocation().add(0, 2.6, 0);
            }
            for (Player o : modo.jugadores()) {
                if (modo.equipo(o) != m.equipo()) continue;
                o.spawnParticle(Particle.DUST, l, 4, 0.15, 0.15, 0.15, 0, new Particle.DustOptions(Color.fromRGB(255, 140, 0), 2f));
            }
        }
    }

    // ------------------------------------------------------------------ utilidades

    private static ItemStack visual(String modelo) {
        ItemStack it = new ItemStack(Material.CLAY_BALL);
        ItemMeta m = it.getItemMeta();
        m.setItemModel(new NamespacedKey("tresmodos", modelo));
        it.setItemMeta(m);
        return it;
    }

    private static Transformation escala(float s) {
        return new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(s, s, s), new AxisAngle4f());
    }

    /** Repone el equipo de la clase en la caja de munición. */
    public void reabastecer(Player p, ModoGuerra.Clase c) {
        PlayerInventory inv = p.getInventory();
        switch (c) {
            case FUSILERO -> completar(inv, ArmaGuerra.GRANADA_AT, 1);
            case ANTITANQUE -> {
                completar(inv, ArmaGuerra.RPG7, 3);
                completar(inv, ArmaGuerra.AT4, 1);
                completar(inv, ArmaGuerra.JAVELIN, 2);
                completar(inv, ArmaGuerra.MINA_AT, 2);
            }
            case INGENIERO -> {
                completar(inv, ArmaGuerra.C4, 3);
                completar(inv, ArmaGuerra.STINGER, 2);
            }
            case TIRADOR -> { }
        }
    }

    /** Lleva la cantidad del ítem al máximo (o lo agrega si no está). */
    static void completar(PlayerInventory inv, ArmaGuerra a, int max) {
        for (ItemStack it : inv.getContents()) {
            if (ArmaGuerra.de(it) == a) {
                it.setAmount(Math.max(it.getAmount(), max));
                return;
            }
        }
        inv.addItem(a.crear(max));
    }

    public void olvidar(Player p) {
        vehiculos.olvidar(p);
        esperaLanzador.remove(p.getUniqueId());
        cargas.removeIf(c -> {
            if (!c.duenio.equals(p.getUniqueId())) return false;
            c.visual.remove();
            return true;
        });
    }

    /** Nueva partida: vehículos en su lugar y sin cargas ni minas. */
    public void reiniciar() {
        vehiculos.reiniciar();
        for (Carga c : cargas) c.visual.remove();
        cargas.clear();
        for (Mina m : minas) m.visual.remove();
        minas.clear();
        marcas.clear();
        for (Snowball s : granadas.keySet()) s.remove();
        granadas.clear();
    }

    public void apagar() {
        vehiculos.apagar();
        for (Carga c : cargas) c.visual.remove();
        for (Mina m : minas) m.visual.remove();
        cargas.clear();
        minas.clear();
    }
}
