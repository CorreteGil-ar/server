package ar.tresmodos;

import ar.tresmodos.modos.ModoCod;
import ar.tresmodos.modos.ModoGta;
import ar.tresmodos.modos.ModoRpg;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumMap;
import java.util.Map;

public final class TresModos extends JavaPlugin {
    private Almacen almacen;
    private Mundos mundos;
    private Sidebar sidebar;
    private CambioModo cambio;
    private Armas armas;
    private Lobby lobby;
    private ModoGta gta;
    private ModoCod cod;
    private ModoRpg rpg;
    private final Map<Modo, ModoJuego> juegos = new EnumMap<>(Modo.class);

    @Override
    public void onEnable() {
        Claves.init(this);
        getDataFolder().mkdirs();
        almacen = new Almacen(this);
        mundos = new Mundos(this);
        mundos.crear();
        sidebar = new Sidebar(this);
        cambio = new CambioModo(this);
        armas = new Armas(this);
        lobby = new Lobby(this);
        gta = new ModoGta(this);
        cod = new ModoCod(this);
        rpg = new ModoRpg(this);
        juegos.put(Modo.LOBBY, lobby);
        juegos.put(Modo.GTA, gta);
        juegos.put(Modo.COD, cod);
        juegos.put(Modo.RPG, rpg);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new Menu.Escucha(), this);
        pm.registerEvents(cambio, this);
        pm.registerEvents(armas, this);
        pm.registerEvents(lobby, this);
        pm.registerEvents(gta, this);
        pm.registerEvents(cod, this);
        pm.registerEvents(rpg, this);

        Comandos comandos = new Comandos(this);
        for (String c : new String[]{"modo", "lobby", "clase", "celular", "tm"}) {
            var cmd = getCommand(c);
            if (cmd != null) {
                cmd.setExecutor(comandos);
                cmd.setTabCompleter(comandos);
            }
        }

        Bukkit.getScheduler().runTaskTimer(this, sidebar::actualizarTodos, 20, 20);
        // Guardado periódico: si el server se cae, se pierden como mucho 5 minutos.
        Bukkit.getScheduler().runTaskTimer(this, this::guardadoPeriodico, 20 * 300, 20 * 300);

        // Si se recarga el plugin con gente adentro, los mandamos al lobby con su estado a salvo.
        for (Player p : Bukkit.getOnlinePlayers()) {
            cambio.limpiar(p);
            cambio.entrar(p, Modo.LOBBY);
        }
        getLogger().info("TresModos listo: lobby, GTA, COD y RPG.");
    }

    @Override
    public void onDisable() {
        if (cambio == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                cambio.sacarDelServer(p);
                p.teleport(mundos.spawn(Modo.LOBBY));
            } catch (RuntimeException ex) {
                getLogger().warning("Error guardando a " + p.getName() + ": " + ex.getMessage());
            }
        }
        if (gta != null) gta.apagar();
        if (rpg != null) rpg.apagar();
        almacen.guardarTodo();
    }

    private void guardadoPeriodico() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Modo m = Modo.de(p.getWorld());
            if (m != null && juego(m).guardaEstado() && !p.isDead()) cambio.guardarEstado(p, m);
        }
        almacen.guardarTodo();
    }

    /** El jugador responsable de un golpe: directo, por proyectil o por TNT. */
    public static Player jugadorAtacante(Entity damager) {
        if (damager instanceof Player p) return p;
        if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) return p;
        if (damager instanceof TNTPrimed t && t.getSource() instanceof Player p) return p;
        return null;
    }

    public ModoJuego juego(Modo m) { return juegos.get(m); }
    public Almacen almacen() { return almacen; }
    public Mundos mundos() { return mundos; }
    public Sidebar sidebar() { return sidebar; }
    public CambioModo cambio() { return cambio; }
    public Armas armas() { return armas; }
    public Lobby lobby() { return lobby; }
    public ModoGta gta() { return gta; }
    public ModoCod cod() { return cod; }
    public ModoRpg rpg() { return rpg; }
}
