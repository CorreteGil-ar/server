package ar.tresmodos;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Pasa a un jugador de un modo a otro guardando y restaurando lo que corresponda. */
public class CambioModo implements Listener {
    private static final long COMBATE_MS = 6000;

    private final TresModos plugin;
    private final Map<UUID, Long> ultimoGolpe = new HashMap<>();

    public CambioModo(TresModos plugin) {
        this.plugin = plugin;
    }

    public void cambiar(Player p, Modo destino, boolean forzar) {
        Modo actual = Modo.de(p.getWorld());
        if (actual == destino) {
            Util.msg(p, "<gray>Ya estás en " + destino.titulo + "<gray>.");
            return;
        }
        if (!forzar && enCombate(p)) {
            Util.msg(p, "<red>Estás en combate. Esperá unos segundos para cambiar de modo.");
            return;
        }
        if (actual != null) salir(p, actual);
        limpiar(p);
        entrar(p, destino);
    }

    public boolean enCombate(Player p) {
        Modo m = Modo.de(p.getWorld());
        if (m == null || m == Modo.LOBBY || m == Modo.SHOOTER) return false;
        Long t = ultimoGolpe.get(p.getUniqueId());
        return t != null && System.currentTimeMillis() - t < COMBATE_MS;
    }

    void salir(Player p, Modo m) {
        ModoJuego juego = plugin.juego(m);
        juego.alSalir(p);
        if (juego.guardaEstado()) guardarEstado(p, m);
    }

    public void guardarEstado(Player p, Modo m) {
        plugin.armas().sincronizar(p);
        DatosJugador d = plugin.almacen().de(p);
        DatosJugador.Estado e = new DatosJugador.Estado();
        ItemStack[] contenido = p.getInventory().getContents();
        for (int i = 0; i < contenido.length; i++) if (contenido[i] == null) contenido[i] = ItemStack.empty();
        e.inventario = ItemStack.serializeItemsAsBytes(contenido);
        e.vida = p.isDead() ? 20 : p.getHealth();
        e.comida = p.getFoodLevel();
        e.saturacion = p.getSaturation();
        e.ubicacion = p.getLocation();
        d.estados.put(m, e);
    }

    public void limpiar(Player p) {
        plugin.armas().olvidar(p.getUniqueId());
        p.closeInventory();
        p.setItemOnCursor(null);
        p.getInventory().clear();
        for (PotionEffect ef : p.getActivePotionEffects()) p.removePotionEffect(ef.getType());
        AttributeInstance vida = p.getAttribute(Attribute.MAX_HEALTH);
        if (vida != null) vida.setBaseValue(20);
        AttributeInstance vel = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (vel != null) vel.setBaseValue(0.1);
        if (!p.isDead()) p.setHealth(20);
        p.setFoodLevel(20);
        p.setSaturation(5);
        p.setExp(0);
        p.setLevel(0);
        p.setFireTicks(0);
        p.setFallDistance(0);
        p.setGlowing(false);
        p.setInvulnerable(false);
        if (p.isInsideVehicle()) p.leaveVehicle();
    }

    void entrar(Player p, Modo m) {
        ModoJuego juego = plugin.juego(m);
        DatosJugador d = plugin.almacen().de(p);
        DatosJugador.Estado e = juego.guardaEstado() ? d.estados.get(m) : null;
        boolean primera = juego.guardaEstado() && e == null;

        Location destino = juego.ubicacionEntrada(p);
        if (e != null && e.ubicacion != null && e.ubicacion.getWorld() == plugin.mundos().de(m)) destino = e.ubicacion;
        p.teleport(destino);
        p.setGameMode(juego.modoJuego());
        juego.prepararAtributos(p);

        if (e != null && e.inventario != null) {
            try {
                p.getInventory().setContents(ItemStack.deserializeItemsFromBytes(e.inventario));
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("No pude restaurar el inventario de " + p.getName() + ": " + ex.getMessage());
                juego.kitInicial(p);
            }
            double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
            p.setHealth(Math.max(1, Math.min(max, e.vida)));
            p.setFoodLevel(e.comida);
            p.setSaturation(e.saturacion);
        } else {
            juego.kitInicial(p);
        }
        juego.alEntrar(p, primera);
        plugin.sidebar().actualizar(p);
    }

    // ------------------------------------------------------------------ eventos

    @EventHandler
    public void alEntrarServer(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        DatosJugador d = plugin.almacen().de(p);
        Modo m = Modo.de(p.getWorld());
        // Si el server se cayó con el jugador adentro, su inventario real es el de ese modo: lo rescatamos.
        if (!d.salidaLimpia && m != null && m != Modo.LOBBY && plugin.juego(m).guardaEstado()) {
            guardarEstado(p, m);
            plugin.getLogger().info("Rescaté el inventario de " + p.getName() + " en " + m);
        }
        d.salidaLimpia = false;
        limpiar(p);
        entrar(p, Modo.LOBBY);
        Util.titulo(p, "<white><bold>TRES MODOS</bold>", "<gray>GTA · Shooter · RPG", 300, 2500, 700);
    }

    @EventHandler
    public void alSalirServer(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        sacarDelServer(p);
        plugin.almacen().guardarYSoltar(p.getUniqueId());
        plugin.sidebar().olvidar(p);
        ultimoGolpe.remove(p.getUniqueId());
    }

    /** Guarda el estado del modo actual y deja al jugador vacío (lo usa la salida y el apagado). */
    public void sacarDelServer(Player p) {
        Modo m = Modo.de(p.getWorld());
        if (m != null) salir(p, m);
        limpiar(p);
        if (p.getGameMode() != GameMode.ADVENTURE) p.setGameMode(GameMode.ADVENTURE);
        plugin.almacen().de(p).salidaLimpia = true;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void alReaparecer(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Modo m = Modo.de(p.getWorld());
        if (m == null) m = Modo.LOBBY;
        ModoJuego juego = plugin.juego(m);
        e.setRespawnLocation(juego.respawn(p));
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) {
                juego.alReaparecer(p);
                plugin.sidebar().actualizar(p);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void alGolpear(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player p) ultimoGolpe.put(p.getUniqueId(), System.currentTimeMillis());
        Player atacante = TresModos.jugadorAtacante(e.getDamager());
        if (atacante != null) ultimoGolpe.put(atacante.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void alUsarPortal(PlayerPortalEvent e) {
        if (Modo.de(e.getFrom().getWorld()) != null) {
            e.setCancelled(true);
            Util.barra(e.getPlayer(), "<dark_purple>Los portales de este mundo están sellados.");
        }
    }
}
