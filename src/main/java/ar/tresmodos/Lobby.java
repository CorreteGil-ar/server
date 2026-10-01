package ar.tresmodos;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** El lobby: plataforma flotante, invulnerable, con selector de modos y placas. */
public class Lobby implements ModoJuego, Listener {
    private final TresModos plugin;

    public Lobby(TresModos plugin) {
        this.plugin = plugin;
    }

    @Override public Modo modo() { return Modo.LOBBY; }
    @Override public boolean guardaEstado() { return false; }
    @Override public GameMode modoJuego() { return GameMode.ADVENTURE; }

    @Override
    public Location ubicacionEntrada(Player p) {
        Location l = plugin.mundos().spawn(Modo.LOBBY);
        l.setYaw(180);
        return l;
    }

    @Override
    public Location respawn(Player p) {
        return ubicacionEntrada(p);
    }

    public static ItemStack selector() {
        return Util.marcar(Util.item(Material.NETHER_STAR, "<white><bold>Elegir modo</bold> <gray>(clic derecho)",
                "Abrí el menú de modos desde cualquier lado", "con /modo"), Claves.SELECTOR, "1");
    }

    @Override
    public void kitInicial(Player p) {
        p.getInventory().setItem(4, selector());
        p.getInventory().setHeldItemSlot(4);
    }

    @Override
    public void alEntrar(Player p, boolean primeraVez) {
        p.playSound(p, Sound.BLOCK_BEACON_ACTIVATE, 0.6f, 1.4f);
        // Un tick después: el jugador ya está en el mundo del lobby.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && enLobby(p)) mostrarEstadisticas(p);
        });
    }

    @Override
    public void alSalir(Player p) {
        quitarEstadisticas(p.getUniqueId());
    }

    // ------------------------------------------------------------------ estadísticas

    /** Carteles personales junto a cada portal: solo los ve su dueño. */
    private final java.util.Map<java.util.UUID, List<org.bukkit.entity.TextDisplay>> carteles = new java.util.HashMap<>();

    private List<String> estadisticas(Player p) {
        DatosJugador d = plugin.almacen().de(p);
        String guerra = "<dark_green><bold>Tu Guerra</bold>\n<gray>Bajas <white>" + d.guerraBajas + "  <gray>Muertes <white>" + d.guerraMuertes
                + "\n<gray>Capturas <white>" + d.guerraCapturas + "  <gray>Revividos <white>" + d.guerraRevividos
                + "\n<gray>Victorias <white>" + d.guerraVictorias;
        double kd = d.codMuertes == 0 ? d.codBajas : d.codBajas / (double) d.codMuertes;
        String shooter = "<red><bold>Tu Shooter</bold>\n<gray>Bajas <white>" + d.codBajas + "  <gray>Muertes <white>" + d.codMuertes
                + "\n<gray>B/M <white>" + String.format(java.util.Locale.ROOT, "%.2f", kd) + "  <gray>Victorias <white>" + d.shooterVictorias
                + "\n<gray>Bombas atómicas <white>" + d.shooterBombas;
        long senores = d.jefes.stream().filter(j -> !j.contains("@") && !j.equals("durmiente")).count();
        boolean durmiente = d.jefes.stream().anyMatch(j -> j.startsWith("durmiente"));
        String rpg = "<light_purple><bold>Tu RPG</bold>\n<gray>Nivel <white>" + d.nivelRpg() + "  <gray>Ciclo <white>" + (d.ciclo + 1)
                + "\n<gray>Señores <white>" + senores + "/4" + (durmiente ? "  <dark_purple>Durmiente vencido" : "")
                + "\n<gray>Almas <white>" + Util.num(d.almas) + "  <gray>Hogueras <white>" + d.hogueras.size();
        return List.of(guerra, shooter, rpg);
    }

    private void mostrarEstadisticas(Player p) {
        quitarEstadisticas(p.getUniqueId());
        List<String> textos = estadisticas(p);
        List<org.bukkit.entity.TextDisplay> propios = new ArrayList<>();
        for (int i = 0; i < textos.size(); i++) {
            double[] c = Mundos.CARTEL_STATS[i];
            String texto = textos.get(i);
            org.bukkit.entity.TextDisplay t = p.getWorld().spawn(new Location(p.getWorld(), c[0], c[1], c[2]),
                    org.bukkit.entity.TextDisplay.class, d -> {
                        d.setVisibleByDefault(false);
                        d.setPersistent(false);
                        d.text(Util.mm(texto));
                        d.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
                        d.setShadowed(true);
                        d.setBackgroundColor(org.bukkit.Color.fromARGB(110, 0, 0, 0));
                        d.getPersistentDataContainer().set(Claves.DISPLAY_LOBBY, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
                    });
            p.showEntity(plugin, t);
            propios.add(t);
        }
        carteles.put(p.getUniqueId(), propios);
    }

    private void quitarEstadisticas(java.util.UUID id) {
        List<org.bukkit.entity.TextDisplay> l = carteles.remove(id);
        if (l != null) for (org.bukkit.entity.TextDisplay t : l) t.remove();
    }

    @EventHandler
    public void alIrse(org.bukkit.event.player.PlayerQuitEvent e) {
        quitarEstadisticas(e.getPlayer().getUniqueId());
    }

    @Override
    public String tituloSidebar(Player p) {
        return "<white><bold>TRES MODOS";
    }

    @Override
    public List<String> lineasSidebar(Player p) {
        List<String> l = new ArrayList<>();
        l.add("<dark_gray>" + java.time.LocalDate.now());
        l.add("");
        l.add("<dark_green>Guerra <white>" + contar(Modo.GUERRA) + " <gray>jugando");
        l.add("<red>Shooter <white>" + contar(Modo.SHOOTER) + " <gray>jugando");
        l.add("<light_purple>RPG <white>" + contar(Modo.RPG) + " <gray>jugando");
        l.add("");
        l.add("<gray>Pisá una placa o usá");
        l.add("<gray>la estrella para entrar.");
        return l;
    }

    public int contar(Modo m) {
        return plugin.mundos().de(m).getPlayerCount();
    }

    /** Menú de modos: se abre desde el lobby o con /modo desde cualquier lado. */
    public void abrirMenu(Player p) {
        Menu menu = new Menu(3, "<dark_gray>Elegí un modo");
        menu.poner(11, Util.item(Material.GREEN_BANNER, "<dark_green><bold>GUERRA · Valle de Hierro",
                "Captura la bandera, Azul contra Rojo.", "Infantería, tanques, aviones y helicópteros.",
                "3 capturas o 15 min.", "",
                "<yellow>" + contar(Modo.GUERRA) + " jugando"), pl -> ir(pl, Modo.GUERRA));
        menu.poner(13, Util.item(Material.DIAMOND_HOE, "<red><bold>SHOOTER · Pueblo Atómico",
                "Todos contra todos estilo Modern Warfare.", "Clases, rachas y bomba atómica.",
                "Primero a 20 bajas (30 con 5+) o 15 min.", "",
                "<yellow>" + contar(Modo.SHOOTER) + " jugando"), pl -> ir(pl, Modo.SHOOTER));
        menu.poner(15, Util.item(Material.NETHERITE_SWORD, "<light_purple><bold>RPG / SOULS",
                "Stamina, esquive con F, hogueras,", "almas y jefes. Morir cuesta caro.", "",
                "<yellow>" + contar(Modo.RPG) + " jugando"), pl -> ir(pl, Modo.RPG));
        if (Modo.de(p.getWorld()) != Modo.LOBBY) {
            menu.poner(22, Util.item(Material.OAK_DOOR, "<white>Volver al lobby"), pl -> ir(pl, Modo.LOBBY));
        }
        menu.abrir(p);
    }

    private void ir(Player p, Modo m) {
        p.closeInventory();
        plugin.cambio().cambiar(p, m, false);
    }

    // ------------------------------------------------------------------ eventos

    private boolean enLobby(Player p) {
        return Modo.de(p.getWorld()) == Modo.LOBBY;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void alInteractuar(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        // El selector funciona en cualquier modo que lo tenga
        if (e.getHand() == EquipmentSlot.HAND && Util.marca(e.getItem(), Claves.SELECTOR) != null
                && (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            e.setCancelled(true);
            abrirMenu(p);
            return;
        }
        if (!enLobby(p)) return;
        if (e.getAction() == Action.PHYSICAL) {
            Block b = e.getClickedBlock();
            if (b == null || b.getType() != Material.LIGHT_WEIGHTED_PRESSURE_PLATE) return;
            for (int i = 0; i < Mundos.PADS.length; i++) {
                if (b.getX() == Mundos.PADS[i][0] && b.getZ() == Mundos.PADS[i][1]) {
                    Modo destino = Mundos.PAD_MODO[i];
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (p.isOnline() && enLobby(p)) plugin.cambio().cambiar(p, destino, false);
                    });
                    return;
                }
            }
        }
    }

    @EventHandler
    public void alDanio(EntityDamageEvent e) {
        if (e.getEntity() instanceof Player p && enLobby(p)) {
            e.setCancelled(true);
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID) p.teleport(ubicacionEntrada(p));
        }
    }

    @EventHandler
    public void alMover(PlayerMoveEvent e) {
        if (e.getTo().getY() < 40 && enLobby(e.getPlayer())) e.getPlayer().teleport(ubicacionEntrada(e.getPlayer()));
    }

    @EventHandler
    public void alHambre(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && enLobby(p)) {
            e.setCancelled(true);
            p.setFoodLevel(20);
        }
    }

    @EventHandler
    public void alTirar(PlayerDropItemEvent e) {
        if (enLobby(e.getPlayer()) || Util.marca(e.getItemDrop().getItemStack(), Claves.SELECTOR) != null) e.setCancelled(true);
    }

    @EventHandler
    public void alCambiarMano(PlayerSwapHandItemsEvent e) {
        if (enLobby(e.getPlayer())) e.setCancelled(true);
    }

    @EventHandler
    public void alClicInventario(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && enLobby(p) && p.getGameMode() != GameMode.CREATIVE) e.setCancelled(true);
    }
}
