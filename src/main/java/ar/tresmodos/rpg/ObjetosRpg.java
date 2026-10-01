package ar.tresmodos.rpg;

import ar.tresmodos.Claves;
import ar.tresmodos.Modo;
import ar.tresmodos.TresModos;
import ar.tresmodos.Util;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Frascos y consumibles del RPG: bombas de fuego, cuchillos, resinas, hierbas, Ceniza de Retorno y Lágrima. */
public class ObjetosRpg implements Listener {

    public enum Resina {
        FUEGO("Resina de fuego", "<gold>", Color.ORANGE), RAYO("Resina de rayo", "<yellow>", Color.YELLOW);

        public final String nombre, color;
        public final Color tinte;

        Resina(String nombre, String color, Color tinte) {
            this.nombre = nombre;
            this.color = color;
            this.tinte = tinte;
        }
    }

    private final TresModos plugin;
    private final ModoRpgHook modo;
    /** Resina activa por jugador y hasta qué tick dura. */
    private final Map<UUID, Resina> resinas = new HashMap<>();
    private final Map<UUID, Integer> resinaHasta = new HashMap<>();

    /** Lo que el RPG le presta a los objetos (estados, hogueras). */
    public interface ModoRpgHook {
        Estados estados();

        Location hogueraDe(Player p);
    }

    public ObjetosRpg(TresModos plugin, ModoRpgHook modo) {
        this.plugin = plugin;
        this.modo = modo;
    }

    // ------------------------------------------------------------------ frascos

    public static ItemStack estus(int cargas, int max) {
        return frasco("Frasco de Estus", cargas, max, Color.ORANGE, Claves.ESTUS, "Cura el 45 % de tu vida.");
    }

    public static ItemStack eter(int cargas, int max) {
        return frasco("Frasco de Éter", cargas, max, Color.fromRGB(70, 120, 255), Claves.ETER, "Recupera la mitad del éter.");
    }

    private static ItemStack frasco(String nombre, int cargas, int max, Color color, org.bukkit.NamespacedKey clave, String desc) {
        ItemStack it = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) it.getItemMeta();
        meta.setColor(cargas > 0 ? color : Color.GRAY);
        meta.displayName(Util.mmItem((cargas > 0 ? "<gold>" : "<gray>") + "<bold>" + nombre + "</bold> <white>(" + cargas + "/" + max + ")"));
        meta.lore(List.of(Util.mmItem("<gray>" + desc), Util.mmItem("<gray>Se rellena en las hogueras.")));
        meta.getPersistentDataContainer().set(clave, PersistentDataType.INTEGER, cargas);
        it.setItemMeta(meta);
        return it;
    }

    // ------------------------------------------------------------------ consumibles

    public static ItemStack bombaFuego(int n) {
        return consumible(Material.FIRE_CHARGE, "Bomba de fuego", "Se lanza y prende fuego en un área.", "bomba_fuego", n);
    }

    public static ItemStack cuchillos(int n) {
        return consumible(Material.IRON_NUGGET, "Cuchillo arrojadizo", "Rápido; hace sangrar.", "cuchillo", n);
    }

    public static ItemStack resina(Resina r, int n) {
        return consumible(r == Resina.FUEGO ? Material.MAGMA_CREAM : Material.GLOWSTONE_DUST, r.nombre,
                "Unta tu arma por 30 s.", "resina_" + r.name().toLowerCase(), n);
    }

    public static ItemStack hierba(int n) {
        return consumible(Material.SWEET_BERRIES, "Hierba purificadora", "Cura veneno, sangrado, frío y locura.", "hierba", n);
    }

    public static ItemStack ceniza(int n) {
        return consumible(Material.GUNPOWDER, "Ceniza de Retorno", "Te lleva a tu última hoguera.", "ceniza", n);
    }

    public static ItemStack lagrima(int n) {
        return consumible(Material.GHAST_TEAR, "Lágrima del Olvido", "Reinicia tu árbol de habilidades.", "lagrima", n);
    }

    // ------------------------------------------------------------------ materiales de mejora

    public static ItemStack fragmento(int n) {
        return material(Material.PRISMARINE_SHARD, "<aqua>Fragmento de Hierro Estelar", "fragmento",
                "Lo usa el herrero del Santuario", "para mejorar armas de +0 a +6.", n);
    }

    public static ItemStack escama(int n) {
        return material(Material.ECHO_SHARD, "<dark_purple>Escama del Abismo", "escama",
                "Rara. El herrero la pide", "para mejorar armas de +7 a +10.", n);
    }

    private static ItemStack material(Material m, String nombre, String id, String l1, String l2, int n) {
        ItemStack it = Util.item(m, "<bold>" + nombre, l1, l2);
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(Claves.MATERIAL, PersistentDataType.STRING, id);
        it.setItemMeta(meta);
        it.setAmount(n);
        return it;
    }

    /** Cuántos materiales de un tipo tiene el jugador. */
    public static int contar(Player p, String id) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (id.equals(Util.marca(it, Claves.MATERIAL))) n += it.getAmount();
        }
        return n;
    }

    /** Saca materiales del inventario (asume que alcanzan). */
    public static void quitar(Player p, String id, int cant) {
        ItemStack[] cont = p.getInventory().getStorageContents();
        for (ItemStack it : cont) {
            if (cant <= 0) break;
            if (!id.equals(Util.marca(it, Claves.MATERIAL))) continue;
            int saca = Math.min(cant, it.getAmount());
            it.setAmount(it.getAmount() - saca);
            cant -= saca;
        }
        p.getInventory().setStorageContents(cont);
    }

    private static ItemStack consumible(Material m, String nombre, String desc, String id, int n) {
        ItemStack it = Util.item(m, "<white><bold>" + nombre, desc, "<yellow>Clic derecho para usar");
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(Claves.CONSUMIBLE, PersistentDataType.STRING, id);
        it.setItemMeta(meta);
        it.setAmount(n);
        return it;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void alUsar(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        String id = Util.marca(e.getItem(), Claves.CONSUMIBLE);
        if (id == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (Modo.de(p.getWorld()) != Modo.RPG) return;
        boolean usado = switch (id) {
            case "bomba_fuego" -> lanzar(p, "bomba_fuego", 1.0, Material.FIRE_CHARGE);
            case "cuchillo" -> lanzar(p, "cuchillo", 2.0, Material.IRON_NUGGET);
            case "resina_fuego" -> untar(p, Resina.FUEGO);
            case "resina_rayo" -> untar(p, Resina.RAYO);
            case "hierba" -> {
                modo.estados().limpiar(p);
                p.playSound(p, Sound.ENTITY_GENERIC_EAT, 1f, 1.2f);
                Util.barra(p, "<green>Te sentís mejor.");
                yield true;
            }
            case "ceniza" -> volver(p);
            case "lagrima" -> {
                plugin.rpg().arbol().reiniciar(p);
                yield true;
            }
            default -> false;
        };
        if (usado) {
            ItemStack it = e.getItem();
            it.setAmount(it.getAmount() - 1);
        }
    }

    private boolean lanzar(Player p, String tipo, double fuerza, Material visual) {
        Snowball s = p.launchProjectile(Snowball.class, p.getEyeLocation().getDirection().multiply(fuerza));
        s.setItem(new ItemStack(visual));
        s.getPersistentDataContainer().set(Claves.EQUIPO, PersistentDataType.STRING, tipo);
        p.playSound(p, Sound.ENTITY_SNOWBALL_THROW, 0.8f, tipo.equals("cuchillo") ? 1.6f : 0.6f);
        return true;
    }

    @EventHandler
    public void alImpactar(ProjectileHitEvent e) {
        Projectile pr = e.getEntity();
        String tipo = pr.getPersistentDataContainer().get(Claves.EQUIPO, PersistentDataType.STRING);
        if (tipo == null || (!tipo.equals("bomba_fuego") && !tipo.equals("cuchillo"))) return;
        Player autor = pr.getShooter() instanceof Player p ? p : null;
        Location l = pr.getLocation();
        World w = l.getWorld();
        pr.remove();
        if (tipo.equals("cuchillo")) {
            if (e.getHitEntity() instanceof LivingEntity le && le != autor) {
                le.damage(4, autor);
                modo.estados().acumular(le, Estado.SANGRADO, 18, autor);
            }
            return;
        }
        w.spawnParticle(Particle.FLAME, l, 40, 1.2, 0.5, 1.2, 0.05);
        w.spawnParticle(Particle.LAVA, l, 6, 0.6, 0.2, 0.6, 0);
        w.playSound(l, Sound.ITEM_FIRECHARGE_USE, 1f, 0.7f);
        for (Entity en : w.getNearbyEntities(l, 2.8, 2.0, 2.8)) {
            if (!(en instanceof LivingEntity le) || le == autor || le instanceof Player) continue;
            le.damage(8, autor);
            le.setFireTicks(80);
        }
    }

    private boolean untar(Player p, Resina r) {
        resinas.put(p.getUniqueId(), r);
        resinaHasta.put(p.getUniqueId(), Bukkit.getCurrentTick() + 600);
        p.playSound(p, Sound.ITEM_HONEYCOMB_WAX_ON, 1f, 0.8f);
        Util.barra(p, r.color + r.nombre + " <gray>en tu arma por 30 s");
        return true;
    }

    /** Resina activa (o null). */
    public Resina resina(Player p) {
        Integer hasta = resinaHasta.get(p.getUniqueId());
        if (hasta == null || hasta < Bukkit.getCurrentTick()) {
            resinas.remove(p.getUniqueId());
            resinaHasta.remove(p.getUniqueId());
            return null;
        }
        return resinas.get(p.getUniqueId());
    }

    private boolean volver(Player p) {
        Location h = modo.hogueraDe(p);
        if (h == null) {
            Util.barra(p, "<gray>Todavía no descansaste en ninguna hoguera.");
            return false;
        }
        p.getWorld().spawnParticle(Particle.ASH, p.getLocation().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.02);
        p.playSound(p, Sound.BLOCK_PORTAL_TRIGGER, 0.6f, 1.6f);
        Location desde = p.getLocation().getBlock().getLocation();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline() || p.isDead() || !p.getLocation().getBlock().getLocation().equals(desde)) {
                if (p.isOnline()) Util.barra(p, "<red>Te moviste: la ceniza se dispersó.");
                return;
            }
            p.teleport(h);
            p.playSound(p, Sound.BLOCK_FIRE_EXTINGUISH, 1f, 0.6f);
        }, 50);
        return true;
    }

    public void olvidar(UUID id) {
        resinas.remove(id);
        resinaHasta.remove(id);
    }
}
