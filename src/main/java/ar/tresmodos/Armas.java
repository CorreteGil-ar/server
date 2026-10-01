package ar.tresmodos;

import ar.tresmodos.Accesorios.Mira;
import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.SwingAnimation;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSprintEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Armas de disparo instantáneo compartidas por Guerra y el Shooter: fuego automático por cadencia real,
 * apuntado con zoom según la óptica, accesorios, cargador, recarga, headshots y granadas.
 */
public class Armas implements Listener {

    public enum Disparo { AUTOMATICO, SEMI }

    /**
     * Armas disponibles. Los accesorios admitidos tienen que coincidir con herramientas/armas.py
     * (el paquete de recursos solo trae modelos para esas combinaciones).
     */
    public enum Tipo {
        PISTOLA("Beretta M9", Material.IRON_HOE, 6.0, 360, Disparo.SEMI, 45, 15, 20, 30, 1, 0.025, "m9",
                List.of(Mira.HIERRO), true, false, true, true),
        MP5("H&K MP5", Material.STONE_HOE, 4.4, 800, Disparo.AUTOMATICO, 35, 30, 40, 40, 1, 0.045, "mp5",
                List.of(Mira.HIERRO, Mira.PUNTO_ROJO, Mira.HOLOGRAFICA, Mira.ACOG), true, true, true, true),
        M4A1("Colt M4A1", Material.DIAMOND_HOE, 5.2, 800, Disparo.AUTOMATICO, 70, 30, 40, 45, 1, 0.020, "m4a1",
                List.of(Mira.HIERRO, Mira.PUNTO_ROJO, Mira.HOLOGRAFICA, Mira.ACOG), true, true, true, true),
        ESCOPETA("Benelli M1014", Material.GOLDEN_HOE, 2.8, 180, Disparo.SEMI, 18, 7, 9, 60, 8, 0.120, "m1014",
                List.of(Mira.HIERRO, Mira.PUNTO_ROJO, Mira.HOLOGRAFICA), false, false, true, true),
        FRANCOTIRADOR("Barrett M82A1", Material.NETHERITE_HOE, 24.0, 60, Disparo.SEMI, 150, 10, 0, 70, 1, 0.000, "barrett",
                List.of(Mira.TELESCOPICA), true, false, false, false);

        public final String nombre;
        public final Material material;
        public final double danio;
        /** Disparos por minuto (en semiautomáticas, el máximo con clics seguidos). */
        public final int rpm;
        public final Disparo disparo;
        public final int alcance, cargador, cargadorAmpliado, recarga, perdigones;
        public final double dispersion;
        /** Modelo del paquete de recursos ("tresmodos:" + modelo). */
        public final String modelo;
        public final List<Mira> miras;
        public final boolean admiteSilenciador, admiteEmpunadura, admiteLaser, admiteLinterna;
        /** Va en el slot de la secundaria (pistolas). */
        public final boolean secundaria;

        Tipo(String nombre, Material material, double danio, int rpm, Disparo disparo, int alcance, int cargador,
             int cargadorAmpliado, int recarga, int perdigones, double dispersion, String modelo, List<Mira> miras,
             boolean admiteSilenciador, boolean admiteEmpunadura, boolean admiteLaser, boolean admiteLinterna) {
            this.nombre = nombre;
            this.material = material;
            this.danio = danio;
            this.rpm = rpm;
            this.disparo = disparo;
            this.alcance = alcance;
            this.cargador = cargador;
            this.cargadorAmpliado = cargadorAmpliado;
            this.recarga = recarga;
            this.perdigones = perdigones;
            this.dispersion = dispersion;
            this.modelo = modelo;
            this.miras = miras;
            this.admiteSilenciador = admiteSilenciador;
            this.admiteEmpunadura = admiteEmpunadura;
            this.admiteLaser = admiteLaser;
            this.admiteLinterna = admiteLinterna;
            this.secundaria = material == Material.IRON_HOE;
        }
    }

    /** Último golpe recibido por una entidad, para el killfeed y para acreditar la baja. */
    public record Impacto(UUID autor, String arma, boolean cabeza, int tick) {}

    private record Recarga(int slot, Tipo tipo, int inicio, int fin) {}

    private final TresModos plugin;
    private final Random rnd = new Random();
    private final Map<UUID, Integer> ultimoDisparo = new HashMap<>();
    /** Tick hasta el que se considera apretado el gatillo (el cliente repite el clic cada 4 ticks). */
    private final Map<UUID, Integer> gatilloHasta = new HashMap<>();
    private final Map<UUID, Double> acumulado = new HashMap<>();
    private final Map<UUID, Recarga> recargando = new HashMap<>();
    /** Jugador que está apuntando -> id del arma con la que apunta. */
    private final Map<UUID, String> apuntando = new HashMap<>();
    private final Set<UUID> conLinterna = new HashSet<>();
    private final Map<UUID, Integer> ultimoTirar = new HashMap<>();
    private final Map<UUID, Impacto> impactos = new HashMap<>();
    /**
     * Balas de cada arma (por id del ítem). Se llevan en memoria para no tocar el ítem en cada
     * disparo: cambiar el ítem que está en la mano hace que el cliente repita la animación de
     * agarrarlo. Se escriben en el ítem al recargar, al cambiar de arma y al guardar.
     */
    private final Map<String, Integer> municion = new HashMap<>();
    /** Reserva de cada arma por id, en memoria igual que las balas del cargador. */
    private final Map<String, Integer> reservas = new HashMap<>();
    /** true mientras se aplica daño de bala: los listeners de melee lo usan para ignorarlo. */
    public static boolean aplicandoBala = false;

    private static Armas instancia;

    public Armas(TresModos plugin) {
        this.plugin = plugin;
        instancia = this;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickGatillos, 1, 1);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickRecargas, 2, 2);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickAccesorios, 2, 2);
    }

    // ------------------------------------------------------------------ ítems

    public static ItemStack crear(Tipo t) {
        return crear(t, Accesorios.NINGUNO);
    }

    public static ItemStack crear(Tipo t, Accesorios a) {
        ItemStack it = new ItemStack(t.material);
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(Claves.ARMA, PersistentDataType.STRING, t.name());
        meta.getPersistentDataContainer().set(Claves.ARMA_ID, PersistentDataType.STRING, UUID.randomUUID().toString());
        meta.setUnbreakable(true);
        meta.setItemModel(new NamespacedKey("tresmodos", t.modelo));
        it.setItemMeta(meta);
        Accesorios validos = a.validar(t);
        aplicarAccesorios(it, t, validos);
        setBalasItem(it, capacidad(t, validos));
        return it;
    }

    private static final NamespacedKey SIN_DEMORA = new NamespacedKey("tresmodos", "arma_sin_demora");

    /** Monta los accesorios en el ítem: datos, modelo y descripción. */
    static void aplicarAccesorios(ItemStack it, Tipo t, Accesorios a) {
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(Claves.ACCESORIOS, PersistentDataType.STRING, a.codigo());
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setStrings(a.cadenasModelo());
        meta.setCustomModelDataComponent(cmd);
        meta.displayName(Util.mmItem("<white><bold>" + t.nombre));
        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
        lore.add(Util.mmItem("<gray>Daño " + t.danio + (t.perdigones > 1 ? " x" + t.perdigones : "")
                + " · " + t.rpm + " disp/min · Cargador " + capacidad(t, a)));
        lore.add(Util.mmItem("<gray>" + (t.disparo == Disparo.AUTOMATICO ? "Automática" : "Semiautomática")
                + " · Clic der: disparar · Clic izq: apuntar"));
        lore.add(Util.mmItem("<gray>Q: recargar · /armero: accesorios"));
        for (String linea : a.resumen()) lore.add(Util.mmItem("<dark_aqua>• " + linea));
        meta.lore(lore);
        // Sin demora de ataque: si no, el cliente baja el arma y la vuelve a subir después de
        // cada clic izquierdo (apuntar), como tras un golpe.
        meta.removeAttributeModifier(Attribute.ATTACK_SPEED);
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(SIN_DEMORA, 1000,
                AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(meta);
        // Y sin el golpe del brazo al hacer clic izquierdo: la mira no se mueve al apuntar.
        it.setData(DataComponentTypes.ATTACK_ANIMATION,
                SwingAnimation.swingAnimation().type(SwingAnimation.Animation.NONE).build());
    }

    public static ItemStack granadas(int cantidad) {
        ItemStack it = Util.item(Material.SNOWBALL, "<green><bold>Granada", "Explota al impactar.", "No rompe bloques.");
        it.setAmount(cantidad);
        return Util.marcar(it, Claves.GRANADA, "1");
    }

    public static Tipo tipo(ItemStack it) {
        String s = Util.marca(it, Claves.ARMA);
        if (s == null) return null;
        try {
            return Tipo.valueOf(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static Accesorios accesorios(ItemStack it) {
        return Accesorios.de(Util.marca(it, Claves.ACCESORIOS));
    }

    public static int capacidad(Tipo t, Accesorios a) {
        return a.cargadorAmpliado() && t.cargadorAmpliado > 0 ? t.cargadorAmpliado : t.cargador;
    }

    private static int balasItem(ItemStack it) {
        Integer b = it.getItemMeta().getPersistentDataContainer().get(Claves.BALAS, PersistentDataType.INTEGER);
        return b == null ? 0 : b;
    }

    private static void setBalasItem(ItemStack it, int n) {
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(Claves.BALAS, PersistentDataType.INTEGER, n);
        it.setItemMeta(meta);
    }

    // ------------------------------------------------------------------ reserva

    /** Balas de reserva del arma (-1 = infinitas). */
    private int reserva(ItemStack it) {
        String id = Util.marca(it, Claves.ARMA_ID);
        if (id != null && reservas.containsKey(id)) return reservas.get(id);
        Integer r = it.getItemMeta().getPersistentDataContainer().get(Claves.RESERVA, PersistentDataType.INTEGER);
        return r == null ? -1 : r;
    }

    private void setReserva(ItemStack it, int n) {
        String id = Util.marca(it, Claves.ARMA_ID);
        if (id != null) reservas.put(id, n);
        else setReservaItem(it, n);
    }

    /** Fija la reserva en el ítem (al crearlo para una clase). */
    public static void setReservaItem(ItemStack it, int n) {
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(Claves.RESERVA, PersistentDataType.INTEGER, n);
        it.setItemMeta(meta);
    }

    /** Llena la reserva de todas las armas del jugador (paquete de ayuda): tres cargadores. */
    public void rellenarReservas(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            Tipo t = tipo(it);
            if (t != null && reserva(it) >= 0) setReserva(it, capacidad(t, accesorios(it)) * 3);
        }
    }

    /** Suma un cargador a la reserva de cada arma (Carroñero). */
    public void sumarCargador(Player p) {
        for (ItemStack it : p.getInventory().getContents()) {
            Tipo t = tipo(it);
            if (t != null && reserva(it) >= 0) setReserva(it, Math.min(reserva(it) + capacidad(t, accesorios(it)),
                    capacidad(t, accesorios(it)) * 4));
        }
    }

    private int balas(ItemStack it) {
        String id = Util.marca(it, Claves.ARMA_ID);
        if (id == null) return balasItem(it);
        return municion.computeIfAbsent(id, k -> balasItem(it));
    }

    private void setBalas(ItemStack it, int n) {
        String id = Util.marca(it, Claves.ARMA_ID);
        if (id == null) setBalasItem(it, n);
        else municion.put(id, n);
    }

    /** Escribe en el ítem las balas que se llevan en memoria. */
    private void persistir(ItemStack it) {
        String id = Util.marca(it, Claves.ARMA_ID);
        Integer n = id == null ? null : municion.get(id);
        if (n != null && n != balasItem(it)) setBalasItem(it, n);
        Integer r = id == null ? null : reservas.get(id);
        if (r != null) setReservaItem(it, r);
    }

    /** Pasa al ítem las balas de todas las armas del inventario (antes de guardarlo). */
    public void sincronizar(Player p) {
        dejarDeApuntar(p);
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && tipo(it) != null) persistir(it);
        }
    }

    /** Cambia los accesorios de un arma del inventario y la deja con las balas que entren. */
    public void cambiarAccesorios(Player p, int slot, Accesorios nuevos) {
        ItemStack it = p.getInventory().getItem(slot);
        Tipo t = tipo(it);
        if (t == null) return;
        Accesorios a = nuevos.validar(t);
        int balas = Math.min(balas(it), capacidad(t, a));
        aplicarAccesorios(it, t, a);
        setBalas(it, balas);
        persistir(it);
        p.getInventory().setItem(slot, it);
        if (slot == p.getInventory().getHeldItemSlot()) {
            recargando.remove(p.getUniqueId());
            dejarDeApuntar(p);
        }
    }

    private boolean mundoConArmas(Player p) {
        Modo m = Modo.de(p.getWorld());
        return m == Modo.GUERRA || m == Modo.SHOOTER;
    }

    // ------------------------------------------------------------------ gatillo y disparo

    @EventHandler(priority = EventPriority.HIGH)
    public void alInteractuar(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        ItemStack it = p.getInventory().getItemInMainHand();
        Tipo t = tipo(it);
        if (t == null) return;
        Action a = e.getAction();
        if (a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK) {
            e.setCancelled(true); // evita arar la tierra con la azada
            if (!mundoConArmas(p) || plugin.shooter().bloqueaDisparo(p) || plugin.guerra().bloqueaDisparo(p)) return;
            if (p.isSprinting()) {
                // Como en los shooters: corriendo no se dispara; el clic corta el sprint y el próximo dispara.
                p.setSprinting(false);
                return;
            }
            apretarGatillo(p, it, t);
        } else if (a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK) {
            if (!mundoConArmas(p)) return;
            e.setCancelled(true);
            alternarApuntado(p, it);
        }
    }

    private void apretarGatillo(Player p, ItemStack it, Tipo t) {
        UUID id = p.getUniqueId();
        int ahora = Bukkit.getCurrentTick();
        if (t.disparo == Disparo.AUTOMATICO) {
            boolean yaApretado = gatilloHasta.getOrDefault(id, -1) >= ahora;
            gatilloHasta.put(id, ahora + 5);
            if (!yaApretado) {
                acumulado.put(id, 0.0);
                disparar(p, it, t);
            }
        } else {
            Integer ult = ultimoDisparo.get(id);
            if (ult != null && ahora - ult < 1200.0 / t.rpm) return;
            disparar(p, it, t);
        }
    }

    /** Fuego automático: mientras el gatillo siga apretado, dispara según la cadencia del arma. */
    private void tickGatillos() {
        int ahora = Bukkit.getCurrentTick();
        Iterator<Map.Entry<UUID, Integer>> itr = gatilloHasta.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry<UUID, Integer> en = itr.next();
            Player p = Bukkit.getPlayer(en.getKey());
            ItemStack it = p == null ? null : p.getInventory().getItemInMainHand();
            Tipo t = it == null ? null : tipo(it);
            if (p == null || en.getValue() < ahora || t == null || t.disparo != Disparo.AUTOMATICO || !mundoConArmas(p)) {
                acumulado.remove(en.getKey());
                itr.remove();
                continue;
            }
            double acc = acumulado.getOrDefault(en.getKey(), 0.0) + t.rpm / 1200.0;
            while (acc >= 1) {
                acc -= 1;
                if (!disparar(p, it, t)) {
                    acc = 0;
                    break;
                }
            }
            acumulado.put(en.getKey(), acc);
        }
    }

    /** Dispara una vez. Devuelve false si no pudo (sin balas o recargando). */
    private boolean disparar(Player p, ItemStack it, Tipo t) {
        UUID id = p.getUniqueId();
        if (recargando.containsKey(id)) return false;
        int balas = balas(it);
        if (balas <= 0) {
            p.playSound(p, Sound.BLOCK_DISPENSER_FAIL, 0.6f, 1.6f);
            recargar(p, it, t);
            return false;
        }
        ultimoDisparo.put(id, Bukkit.getCurrentTick());
        setBalas(it, balas - 1);

        Accesorios acc = accesorios(it);
        boolean apunta = apuntando.containsKey(id);
        double disp = t.dispersion;
        if (t == Tipo.FRANCOTIRADOR) {
            disp = apunta ? 0.0 : 0.09;
        } else if (apunta) {
            disp *= 0.25;
        } else {
            if (acc.laser()) disp *= 0.7;
            if (p.isSneaking()) disp *= 0.6;
            if (plugin.shooter().clases().tiene(p, ar.tresmodos.shooter.ClasesShooter.Ventaja.MANO_FIRME)) disp *= 0.65;
        }
        disp *= plugin.movilidad().factorDispersion(p);
        if (acc.empunadura()) disp *= 0.75;
        if (p.isSprinting()) disp *= 1.8;
        if (!((Entity) p).isOnGround()) disp *= 1.5;
        double alcance = t.alcance * (acc.silenciador() ? 0.85 : 1.0);

        Location ojo = p.getEyeLocation();
        Vector dir = ojo.getDirection();
        World w = p.getWorld();
        boolean endurecido = plugin.shooter().clases().tiene(p, ar.tresmodos.shooter.ClasesShooter.Ventaja.ENDURECIDO);
        for (int i = 0; i < t.perdigones; i++) {
            Vector d = dir.clone().add(new Vector(rnd.nextGaussian() * disp, rnd.nextGaussian() * disp,
                    rnd.nextGaussian() * disp)).normalize();
            double distancia = trazar(p, t, ojo, d, alcance, endurecido);
            trazador(ojo, d, distancia, t);
        }
        sonidoDisparo(p, t, acc.silenciador());
        if (!acc.silenciador() && Modo.de(w) == Modo.SHOOTER) plugin.shooter().minimapa().marcarDisparo(p);
        retroceso(p, t, apunta, acc);
        if (!acc.silenciador()) {
            w.spawnParticle(Particle.SMOKE, ojo.clone().add(dir.clone().multiply(0.9)), 2, 0.02, 0.02, 0.02, 0.01);
        }
        mostrarBalas(p, it, t);
        if (balas - 1 <= 0) recargar(p, it, t);
        return true;
    }

    /**
     * Sigue la bala: atraviesa madera, vidrio, lana y paredes livianas perdiendo daño (Endurecido
     * atraviesa una más y pierde menos). Devuelve la distancia recorrida.
     */
    private double trazar(Player p, Tipo t, Location ojo, Vector d, double alcance, boolean endurecido) {
        World w = ojo.getWorld();
        Location desde = ojo.clone();
        double recorrido = 0, factor = 1;
        int atraviesa = 0, maximo = endurecido ? 2 : 1;
        while (recorrido < alcance) {
            RayTraceResult r = w.rayTrace(desde, d, alcance - recorrido, FluidCollisionMode.NEVER, true, 0.15,
                    ent -> blancoValido(p, ent));
            // En Guerra, la bala puede pegar antes en un vehículo.
            if (Modo.de(w) == Modo.GUERRA) {
                var vs = plugin.guerra().arsenal().vehiculos();
                double hasta = r == null ? alcance - recorrido : r.getHitPosition().distance(desde.toVector());
                var g = vs.rayo(desde.toVector(), d, hasta, vs.de(p));
                if (g != null) {
                    vs.impactoBala(g.vehiculo(), p, t, d);
                    return recorrido + g.distancia();
                }
            }
            if (r == null) return alcance;
            double tramo = r.getHitPosition().distance(desde.toVector());
            recorrido += tramo;
            Location punto = r.getHitPosition().toLocation(w);
            if (r.getHitEntity() instanceof LivingEntity le) {
                impactar(p, le, t, r.getHitPosition(), recorrido, alcance, factor);
                return recorrido;
            }
            if (r.getHitBlock() == null) return recorrido;
            w.spawnParticle(Particle.BLOCK, punto, 5, 0.05, 0.05, 0.05, 0, r.getHitBlock().getBlockData());
            if (atraviesa >= maximo || !penetrable(r.getHitBlock().getType())) return recorrido;
            atraviesa++;
            factor *= endurecido ? 0.75 : 0.55;
            // Avanza hasta salir del bloque atravesado.
            Location salida = punto.clone();
            int pasos = 0;
            while (salida.getBlock().equals(r.getHitBlock()) && pasos++ < 12) salida.add(d.clone().multiply(0.15));
            recorrido += salida.distance(punto);
            desde = salida;
        }
        return alcance;
    }

    private static boolean penetrable(Material m) {
        String n = m.name();
        return n.contains("GLASS") || n.endsWith("_WOOL") || n.endsWith("_PLANKS") || n.endsWith("_FENCE")
                || n.endsWith("_TRAPDOOR") || n.endsWith("_DOOR") || n.endsWith("_LEAVES") || n.endsWith("TERRACOTTA")
                || m == Material.BARREL || m == Material.HAY_BLOCK || m == Material.BOOKSHELF || n.endsWith("_SLAB");
    }

    /** true si el tirador puede pegarle a esa entidad (lo usan el cuchillo y lo arrojadizo). */
    public boolean blanco(Player tirador, LivingEntity ent) {
        return blancoValido(tirador, ent);
    }

    /**
     * Retroceso: la mira sube un poco con cada disparo y se va de costado al azar. El cliente suma
     * el giro (rotación relativa), así no pelea con el mouse del jugador.
     */
    private void retroceso(Player p, Tipo t, boolean apunta, Accesorios acc) {
        double vertical = switch (t) {
            case PISTOLA -> 1.6;
            case MP5 -> 0.55;
            case M4A1 -> 0.7;
            case ESCOPETA -> 3.6;
            case FRANCOTIRADOR -> 6.5;
        };
        if (apunta) vertical *= 0.65;
        if (p.isSneaking()) vertical *= 0.8;
        if (acc.empunadura()) vertical *= 0.7;
        vertical *= plugin.movilidad().tendido(p) ? 0.5 : 1;
        double lateral = (rnd.nextDouble() - 0.5) * vertical * 0.6;
        p.setRotation(io.papermc.paper.math.Angle.relative((float) lateral), io.papermc.paper.math.Angle.relative((float) -vertical));
    }

    private boolean blancoValido(Player tirador, Entity ent) {
        if (ent == tirador || !(ent instanceof LivingEntity le) || le.isDead()) return false;
        if (ent instanceof ArmorStand) return false;
        if (ent instanceof Player op && (op.getGameMode() == GameMode.SPECTATOR)) return false;
        if (ent instanceof Player op && (plugin.guerra().aliados(tirador, op)
                || plugin.guerra().arsenal().vehiculos().protegido(op))) return false;
        return true;
    }

    private void impactar(Player tirador, LivingEntity blanco, Tipo t, Vector punto, double distancia, double alcance,
                          double factor) {
        boolean cabeza = punto.getY() >= blanco.getEyeLocation().getY() - 0.3;
        if (blanco.getPersistentDataContainer().has(Claves.MANIQUI)) {
            plugin.shooter().maniquiAlcanzado(tirador, blanco, cabeza);
            return;
        }
        double danio = t.danio * factor;
        // Pierde daño pasado el alcance efectivo (el 60 % del máximo).
        if (t != Tipo.FRANCOTIRADOR && distancia > alcance * 0.6) danio *= 0.7;
        boolean piernas = punto.getY() < blanco.getLocation().getY() + blanco.getHeight() * 0.42;
        if (cabeza) danio *= t == Tipo.FRANCOTIRADOR ? 2.0 : 1.5;
        else if (piernas) danio *= 0.8;
        danioDirecto(blanco, danio, tirador, t.nombre, cabeza);
        Hud.marcador(tirador, blanco.isDead());
        blanco.getWorld().spawnParticle(Particle.BLOCK, punto.toLocation(blanco.getWorld()), 6, 0.1, 0.1, 0.1, 0,
                Material.REDSTONE_BLOCK.createBlockData());
        tirador.playSound(tirador, cabeza ? Sound.ENTITY_ARROW_HIT_PLAYER : Sound.BLOCK_NOTE_BLOCK_HAT,
                cabeza ? 0.8f : 1f, cabeza ? 1.8f : 1.4f);
    }

    private void trazador(Location ojo, Vector d, double distancia, Tipo t) {
        World w = ojo.getWorld();
        Particle.DustOptions polvo = new Particle.DustOptions(
                t == Tipo.FRANCOTIRADOR ? Color.WHITE : Color.fromRGB(200, 190, 150), t == Tipo.FRANCOTIRADOR ? 0.8f : 0.45f);
        double paso = Math.max(0.6, distancia / 40.0);
        for (double s = 1.0; s < distancia; s += paso) {
            w.spawnParticle(Particle.DUST, ojo.clone().add(d.clone().multiply(s)), 1, 0, 0, 0, 0, polvo);
        }
    }

    private void sonidoDisparo(Player p, Tipo t, boolean silenciado) {
        Location l = p.getLocation();
        World w = p.getWorld();
        if (silenciado) {
            // Con silenciador: chasquido corto que se oye poco más allá del tirador.
            w.playSound(l, Sound.ENTITY_ARROW_SHOOT, 0.45f, 1.7f);
            w.playSound(l, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 0.20f, 2.0f);
            return;
        }
        switch (t) {
            case PISTOLA -> w.playSound(l, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.2f, 1.6f);
            case MP5 -> w.playSound(l, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1.0f, 1.95f);
            case M4A1 -> w.playSound(l, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1.3f, 1.45f);
            case ESCOPETA -> w.playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 1.8f);
            case FRANCOTIRADOR -> {
                w.playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 1.4f, 0.9f);
                w.playSound(l, Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1.6f, 0.6f);
            }
        }
    }

    public void mostrarBalas(Player p, ItemStack it, Tipo t) {
        int b = balas(it);
        int cap = capacidad(t, accesorios(it));
        String color = b == 0 ? "<red>" : b <= cap / 4 ? "<yellow>" : "<white>";
        int res = reserva(it);
        Util.barra(p, "<gray>" + t.nombre + "  " + Hud.ICONO_BALA + " " + color + "<bold>" + b + "</bold><gray> / "
                + (res < 0 ? String.valueOf(cap) : "<white>" + res));
    }

    // ------------------------------------------------------------------ apuntado (ADS)

    private void alternarApuntado(Player p, ItemStack it) {
        UUID id = p.getUniqueId();
        // Al tirar el arma con Q el cliente también agita el brazo: no lo tomamos como clic.
        if (Bukkit.getCurrentTick() - ultimoTirar.getOrDefault(id, -100) <= 3) return;
        if (apuntando.containsKey(id)) {
            dejarDeApuntar(p);
            return;
        }
        if (recargando.containsKey(id) || p.isSprinting()) return;
        Mira mira = accesorios(it).mira();
        apuntando.put(id, String.valueOf(Util.marca(it, Claves.ARMA_ID)));
        // El paquete de recursos centra el arma con la mira alineada (o muestra el visor).
        marcarApuntado(it, true);
        p.getInventory().setItemInMainHand(it);
        // La lentitud cierra el campo visual: es el zoom de la óptica (y te frena al apuntar).
        p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, mira.zoom,
                false, false, false));
        p.playSound(p, Sound.ITEM_ARMOR_EQUIP_GENERIC, 0.5f, 1.6f);
    }

    public void dejarDeApuntar(Player p) {
        if (apuntando.remove(p.getUniqueId()) != null) p.removePotionEffect(PotionEffectType.SLOWNESS);
        limpiarMarcas(p);
    }

    /** Saca la marca de apuntado de todas las armas del inventario (el ítem decide el modelo). */
    private static void limpiarMarcas(Player p) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (apuntado(it)) {
                marcarApuntado(it, false);
                inv.setItem(i, it);
            }
        }
    }

    /** Flag 0 de custom_model_data: el paquete de recursos dibuja el arma apuntando. */
    static boolean apuntado(ItemStack it) {
        if (tipo(it) == null) return false;
        List<Boolean> flags = it.getItemMeta().getCustomModelDataComponent().getFlags();
        return !flags.isEmpty() && flags.getFirst();
    }

    static void marcarApuntado(ItemStack it, boolean valor) {
        ItemMeta meta = it.getItemMeta();
        CustomModelDataComponent cmd = meta.getCustomModelDataComponent();
        cmd.setFlags(valor ? List.of(true) : List.of());
        meta.setCustomModelDataComponent(cmd);
        it.setItemMeta(meta);
    }

    @EventHandler
    public void alCorrer(PlayerToggleSprintEvent e) {
        if (e.isSprinting()) dejarDeApuntar(e.getPlayer());
    }

    // ------------------------------------------------------------------ láser y linterna

    private void tickAccesorios() {
        int ahora = Bukkit.getCurrentTick();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            ItemStack it = p.getInventory().getItemInMainHand();
            Tipo t = tipo(it);
            Accesorios a = t != null && mundoConArmas(p) ? accesorios(it) : null;

            // Si el arma con la que apuntaba ya no está en la mano (la movió en el inventario), deja de apuntar.
            String apuntada = apuntando.get(id);
            if (apuntada != null && !apuntada.equals(Util.marca(it, Claves.ARMA_ID))) dejarDeApuntar(p);

            // Linterna: visión nocturna mientras el arma esté en la mano.
            if (a != null && a.linterna()) {
                if (conLinterna.add(id) || ahora % 100 == 0) {
                    p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 400, 0, false, false, false));
                }
            } else if (conLinterna.remove(id)) {
                p.removePotionEffect(PotionEffectType.NIGHT_VISION);
            }

            // Láser: punto rojo donde apunta el arma, visible para todos.
            if (a != null && a.laser() && !p.isSprinting() && !recargando.containsKey(id)) {
                Location ojo = p.getEyeLocation();
                RayTraceResult r = p.getWorld().rayTrace(ojo, ojo.getDirection(), 60, FluidCollisionMode.NEVER, true,
                        0.1, ent -> blancoValido(p, ent));
                if (r != null) {
                    Location punto = r.getHitPosition().toLocation(p.getWorld())
                            .subtract(ojo.getDirection().multiply(0.05));
                    p.getWorld().spawnParticle(Particle.DUST, punto, 1, 0, 0, 0, 0,
                            new Particle.DustOptions(Color.fromRGB(255, 20, 20), 0.6f));
                }
            }
        }
    }

    // ------------------------------------------------------------------ recarga

    public void recargar(Player p, ItemStack it, Tipo t) {
        UUID id = p.getUniqueId();
        if (recargando.containsKey(id) || balas(it) >= capacidad(t, accesorios(it))) return;
        if (reserva(it) == 0) {
            Util.barra(p, "<red>Sin munición de reserva");
            p.playSound(p, Sound.BLOCK_DISPENSER_FAIL, 0.6f, 1.2f);
            return;
        }
        int ahora = Bukkit.getCurrentTick();
        int duracion = t.recarga;
        if (plugin.shooter().clases().tiene(p, ar.tresmodos.shooter.ClasesShooter.Ventaja.PRESTIDIGITACION)) {
            duracion = (int) Math.round(duracion * 0.6);
        }
        recargando.put(id, new Recarga(p.getInventory().getHeldItemSlot(), t, ahora, ahora + duracion));
        gatilloHasta.remove(id);
        p.playSound(p, Sound.ITEM_CROSSBOW_LOADING_START, 0.8f, 1.2f);
        dejarDeApuntar(p);
    }

    private void tickRecargas() {
        int ahora = Bukkit.getCurrentTick();
        Iterator<Map.Entry<UUID, Recarga>> itr = recargando.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry<UUID, Recarga> en = itr.next();
            Player p = Bukkit.getPlayer(en.getKey());
            Recarga r = en.getValue();
            if (p == null || p.getInventory().getHeldItemSlot() != r.slot()) {
                itr.remove();
                continue;
            }
            ItemStack it = p.getInventory().getItemInMainHand();
            if (tipo(it) != r.tipo()) {
                itr.remove();
                continue;
            }
            if (ahora >= r.fin()) {
                int cap = capacidad(r.tipo(), accesorios(it));
                int res = reserva(it);
                if (res < 0) {
                    setBalas(it, cap);
                } else {
                    int pone = Math.min(cap - balas(it), res);
                    setBalas(it, balas(it) + pone);
                    setReserva(it, res - pone);
                }
                persistir(it);
                p.playSound(p, Sound.ITEM_CROSSBOW_LOADING_END, 0.8f, 1.3f);
                mostrarBalas(p, it, r.tipo());
                itr.remove();
            } else {
                double prog = (ahora - r.inicio()) / (double) (r.fin() - r.inicio());
                int llenos = (int) Math.round(prog * 12);
                Util.barra(p, "<gray>Recargando <yellow>" + "▰".repeat(llenos) + "<dark_gray>" + "▱".repeat(12 - llenos));
            }
        }
    }

    @EventHandler
    public void alTirar(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        ItemStack it = e.getItemDrop().getItemStack();
        Tipo t = tipo(it);
        if (t == null || !mundoConArmas(p)) return;
        e.setCancelled(true);
        ultimoTirar.put(p.getUniqueId(), Bukkit.getCurrentTick());
        // Después de cancelar, el ítem vuelve a la mano: recargamos el que está en la mano.
        Bukkit.getScheduler().runTask(plugin, () -> {
            ItemStack mano = p.getInventory().getItemInMainHand();
            Tipo tm = tipo(mano);
            if (tm != null) recargar(p, mano, tm);
        });
    }

    @EventHandler
    public void alCambiarSlot(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        dejarDeApuntar(p);
        recargando.remove(p.getUniqueId());
        gatilloHasta.remove(p.getUniqueId());
        ItemStack anterior = p.getInventory().getItem(e.getPreviousSlot());
        if (anterior != null && tipo(anterior) != null) persistir(anterior);
        ItemStack it = p.getInventory().getItem(e.getNewSlot());
        Tipo t = tipo(it);
        if (t != null && mundoConArmas(p)) mostrarBalas(p, it, t);
    }

    @EventHandler
    public void alSalirServer(PlayerQuitEvent e) {
        olvidar(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void alMorir(PlayerDeathEvent e) {
        // Al morir se pierden los efectos (zoom, visión nocturna): se reinicia el estado del arma.
        UUID id = e.getPlayer().getUniqueId();
        apuntando.remove(id);
        limpiarMarcas(e.getPlayer());
        for (ItemStack it : e.getDrops()) if (apuntado(it)) marcarApuntado(it, false);
        conLinterna.remove(id);
        recargando.remove(id);
        gatilloHasta.remove(id);
    }

    // ------------------------------------------------------------------ granadas y explosiones

    @EventHandler
    public void alLanzar(PlayerLaunchProjectileEvent e) {
        if (Util.marca(e.getItemStack(), Claves.GRANADA) == null) return;
        if (!mundoConArmas(e.getPlayer())) {
            e.setCancelled(true);
            return;
        }
        e.getProjectile().getPersistentDataContainer().set(Claves.GRANADA, PersistentDataType.BYTE, (byte) 1);
    }

    @EventHandler
    public void alImpactarProyectil(ProjectileHitEvent e) {
        Projectile pr = e.getEntity();
        if (!pr.getPersistentDataContainer().has(Claves.GRANADA)) return;
        Player autor = pr.getShooter() instanceof Player p ? p : null;
        explotar(pr.getLocation(), 2.8f, autor, "Granada");
        pr.remove();
    }

    /** Explosión que no rompe bloques y acredita las bajas al autor. */
    public void explotar(Location l, float potencia, Player autor, String arma) {
        if (autor != null) {
            int tick = Bukkit.getCurrentTick();
            for (Entity ent : l.getWorld().getNearbyEntities(l, potencia * 2, potencia * 2, potencia * 2)) {
                if (ent instanceof LivingEntity le) impactos.put(le.getUniqueId(), new Impacto(autor.getUniqueId(), arma, false, tick));
            }
        }
        l.getWorld().createExplosion(l, potencia, false, false, autor);
    }

    // ------------------------------------------------------------------ daño de otras fuentes

    /** Registra quién le pegó a la entidad (para acreditar la baja y el killfeed). */
    public void registrarImpacto(LivingEntity blanco, Player autor, String arma, boolean cabeza) {
        impactos.put(blanco.getUniqueId(), new Impacto(autor.getUniqueId(), arma, cabeza, Bukkit.getCurrentTick()));
    }

    /** Daño de arma (bala, cuchillo, hacha, bomba) acreditado al autor, sin invulnerabilidad entre golpes. */
    public static void danioDirecto(LivingEntity blanco, double danio, Player autor, String arma, boolean cabeza) {
        if (autor != null) instancia.registrarImpacto(blanco, autor, arma, cabeza);
        blanco.setNoDamageTicks(0);
        aplicandoBala = true;
        try {
            if (autor != null) blanco.damage(danio, autor);
            else blanco.damage(danio);
        } finally {
            aplicandoBala = false;
        }
    }

    // ------------------------------------------------------------------ consultas

    /** Impacto reciente (últimos 5 s) sobre una entidad, o null. */
    public Impacto ultimoImpacto(UUID victima) {
        Impacto i = impactos.get(victima);
        if (i == null || Bukkit.getCurrentTick() - i.tick() > 100) return null;
        return i;
    }

    public void olvidar(UUID id) {
        impactos.remove(id);
        recargando.remove(id);
        ultimoDisparo.remove(id);
        gatilloHasta.remove(id);
        acumulado.remove(id);
        apuntando.remove(id);
        conLinterna.remove(id);
        ultimoTirar.remove(id);
    }

    /** Tipo por su nombre visible (el que queda en el registro de impactos), o null. */
    public static Tipo porNombre(String nombre) {
        for (Tipo t : Tipo.values()) if (t.nombre.equals(nombre)) return t;
        return null;
    }

    /**
     * Suma una baja (y tiro a la cabeza) al arma para los camuflajes y avisa si se desbloqueó uno.
     */
    public void contarBaja(Player p, String arma, boolean cabeza) {
        Tipo t = porNombre(arma);
        if (t == null) return;
        DatosJugador d = plugin.almacen().de(p);
        java.util.Set<Camuflaje> antes = java.util.EnumSet.noneOf(Camuflaje.class);
        for (Camuflaje c : Camuflaje.values()) if (c.desbloqueado(d, t)) antes.add(c);
        d.armaBajas.merge(t.name(), 1, Integer::sum);
        if (cabeza) d.armaCabezas.merge(t.name(), 1, Integer::sum);
        for (Camuflaje c : Camuflaje.values()) {
            if (!antes.contains(c) && c.desbloqueado(d, t)) {
                Util.msg(p, "<gold>Desbloqueaste el camuflaje <bold>" + c.nombre + "</bold> para la " + t.nombre
                        + "<gold>. Ponelo en <white>/armero<gold>.");
                p.playSound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.4f);
            }
        }
    }

    public static List<Tipo> todos() {
        return List.of(Tipo.values());
    }
}
