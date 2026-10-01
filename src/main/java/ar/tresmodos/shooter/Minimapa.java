package ar.tresmodos.shooter;

import ar.tresmodos.TresModos;
import ar.tresmodos.mundo.Plano;
import ar.tresmodos.mundo.PuebloAtomico;
import ar.tresmodos.shooter.ClasesShooter.Ventaja;
import org.bukkit.Bukkit;
import java.awt.Color;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursor;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Minimapa del Shooter: un mapa en la mano izquierda con el pueblo visto desde arriba. Muestra tu
 * posición y, en rojo, a los enemigos que dispararon sin silenciador o a todos si tenés un UAV.
 */
public class Minimapa extends MapRenderer {
    /** Bloques por píxel: el mapa (128 px) cubre 85 × 85 bloques centrados en el pueblo. */
    private static final double ESCALA = 85.0 / 128;

    private final ModoShooter modo;
    private final Color[][] fondo = new Color[128][128];
    private final Set<UUID> dibujado = new HashSet<>();
    private final Map<UUID, Integer> disparos = new HashMap<>();
    private MapView vista;

    Minimapa(TresModos plugin, ModoShooter modo) {
        super(true);
        this.modo = modo;
        Plano plano = PuebloAtomico.plano();
        for (int px = 0; px < 128; px++) {
            for (int pz = 0; pz < 128; pz++) {
                int x = (int) Math.floor((px - 64) * ESCALA), z = (int) Math.floor((pz - 64) * ESCALA);
                fondo[px][pz] = colorColumna(plano, x, z);
            }
        }
    }

    private static Color colorColumna(Plano p, int x, int z) {
        if (!PuebloAtomico.enZonaJugable(x, z)) return new Color(196, 178, 128);
        for (int y = PuebloAtomico.SUELO + 18; y >= PuebloAtomico.SUELO - 6; y--) {
            String t = p.tipo(x, y, z);
            if (t.equals("air") || t.equals("barrier") || t.equals("light")) continue;
            int alto = y - PuebloAtomico.SUELO;
            Color c = colorBloque(t);
            double k = alto > 0 ? 0.75 : 1.0;
            return new Color((int) (c.getRed() * k), (int) (c.getGreen() * k), (int) (c.getBlue() * k));
        }
        return new Color(196, 178, 128);
    }

    private static Color colorBloque(String t) {
        if (t.contains("terracotta") && t.startsWith("yellow")) return new Color(214, 170, 60);
        if (t.contains("terracotta") && t.startsWith("lime")) return new Color(130, 165, 80);
        if (t.startsWith("deepslate")) return new Color(70, 70, 76);
        if (t.equals("gray_concrete") || t.equals("cyan_terracotta") || t.equals("andesite")) return new Color(78, 80, 86);
        if (t.equals("yellow_concrete")) return new Color(235, 190, 40);
        if (t.contains("grass") || t.contains("leaves")) return new Color(96, 138, 62);
        if (t.contains("dirt")) return new Color(120, 92, 64);
        if (t.contains("smooth_stone") || t.contains("quartz")) return new Color(190, 190, 186);
        if (t.contains("fence")) return new Color(225, 220, 200);
        if (t.contains("white")) return new Color(230, 230, 230);
        if (t.contains("red")) return new Color(170, 45, 40);
        return new Color(140, 135, 125);
    }

    /** Un mapa nuevo (un solo MapView compartido por todos; cada uno ve sus propios marcadores). */
    public ItemStack item() {
        World w = modo.mundo();
        if (vista == null) {
            vista = Bukkit.createMap(w);
            vista.getRenderers().forEach(vista::removeRenderer);
            vista.setTrackingPosition(false);
            vista.setUnlimitedTracking(false);
            vista.setLocked(true);
            vista.addRenderer(this);
        }
        ItemStack it = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) it.getItemMeta();
        meta.setMapView(vista);
        meta.displayName(ar.tresmodos.Util.mmItem("<gray>Minimapa"));
        it.setItemMeta(meta);
        return it;
    }

    /** Un disparo sin silenciador te marca en el minimapa de los demás durante 3 s. */
    public void marcarDisparo(Player p) {
        disparos.put(p.getUniqueId(), Bukkit.getCurrentTick() + 60);
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player p) {
        if (dibujado.add(p.getUniqueId())) {
            for (int x = 0; x < 128; x++)
                for (int z = 0; z < 128; z++) canvas.setPixelColor(x, z, fondo[x][z]);
        }
        MapCursorCollection cursores = new MapCursorCollection();
        if (p.getWorld() == modo.mundo()) {
            cursores.addCursor(cursor(p.getLocation().getX(), p.getLocation().getZ(), p.getLocation().getYaw(), MapCursor.Type.PLAYER));
            boolean uav = modo.rachas().uavActivo(p);
            int ahora = Bukkit.getCurrentTick();
            for (Player o : p.getWorld().getPlayers()) {
                if (o == p || o.isDead() || o.getGameMode() != GameMode.ADVENTURE) continue;
                boolean visible = disparos.getOrDefault(o.getUniqueId(), 0) > ahora
                        || (uav && !modo.clases().tiene(o, Ventaja.FANTASMA));
                if (visible) {
                    cursores.addCursor(cursor(o.getLocation().getX(), o.getLocation().getZ(), o.getLocation().getYaw(), MapCursor.Type.RED_MARKER));
                }
            }
        }
        canvas.setCursors(cursores);
    }

    private static MapCursor cursor(double x, double z, float yaw, MapCursor.Type tipo) {
        int cx = (int) Math.max(-127, Math.min(127, x / ESCALA * 2));
        int cz = (int) Math.max(-127, Math.min(127, z / ESCALA * 2));
        byte dir = (byte) Math.floorMod(Math.round(yaw * 16f / 360f), 16);
        return new MapCursor((byte) cx, (byte) cz, dir, tipo, true);
    }

    void olvidar(UUID id) {
        dibujado.remove(id);
        disparos.remove(id);
    }
}
