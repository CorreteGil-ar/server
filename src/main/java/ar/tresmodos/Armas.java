package ar.tresmodos;

import com.destroystokyo.paper.event.player.PlayerLaunchProjectileEvent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.AbstractHorse;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Armas de disparo instantáneo compartidas por GTA y COD: cargador, recarga, headshots y granadas. */
public class Armas implements Listener {

    public enum Tipo {
        //            nombre           material             daño cad alc  carg rec perd disp   modelo
        PISTOLA("Pistola M9", Material.IRON_HOE, 4.5, 5, 45, 12, 30, 1, 0.025, null),
        MP5("MP5", Material.STONE_HOE, 3.5, 2, 35, 30, 40, 1, 0.045, null),
        M4A1("M4A1", Material.DIAMOND_HOE, 5.0, 3, 70, 30, 45, 1, 0.020, "m4a1"),
        ESCOPETA("M1014", Material.GOLDEN_HOE, 3.0, 14, 18, 8, 60, 8, 0.120, null),
        FRANCOTIRADOR("Barrett .50", Material.NETHERITE_HOE, 30.0, 30, 150, 5, 70, 1, 0.000, null);

        public final String nombre;
        public final Material material;
        public final double danio;
        public final int cadencia, alcance, cargador, recarga, perdigones;
        public final double dispersion;
        /** Modelo del paquete de recursos ("tresmodos:" + modelo), o null si todavía se ve como la azada. */
        public final String modelo;

        Tipo(String nombre, Material material, double danio, int cadencia, int alcance, int cargador,
             int recarga, int perdigones, double dispersion, String modelo) {
            this.nombre = nombre;
            this.material = material;
            this.danio = danio;
            this.cadencia = cadencia;
            this.alcance = alcance;
            this.cargador = cargador;
            this.recarga = recarga;
            this.perdigones = perdigones;
            this.dispersion = dispersion;
            this.modelo = modelo;
        }
    }

    /** Último golpe recibido por una entidad, para el killfeed y para acreditar la baja. */
    public record Impacto(UUID autor, String arma, boolean cabeza, int tick) {}

    private record Recarga(int slot, Tipo tipo, int inicio, int fin) {}

    private final TresModos plugin;
    private final Random rnd = new Random();
    private final Map<UUID, Integer> ultimoDisparo = new HashMap<>();
    private final Map<UUID, Recarga> recargando = new HashMap<>();
    private final Set<UUID> conZoom = new HashSet<>();
    private final Map<UUID, Impacto> impactos = new HashMap<>();
    /** true mientras se aplica daño de bala: los listeners de melee lo usan para ignorarlo. */
    public static boolean aplicandoBala = false;

    public Armas(TresModos plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickRecargas, 2, 2);
    }

    // ------------------------------------------------------------------ ítems

    public static ItemStack crear(Tipo t) {
        ItemStack it = Util.item(t.material, "<white><bold>" + t.nombre,
                "Daño " + t.danio + (t.perdigones > 1 ? " x" + t.perdigones : "") + " · Cargador " + t.cargador,
                "Clic derecho: disparar", "Q: recargar" + (t == Tipo.FRANCOTIRADOR ? " · Shift: mira" : ""));
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(Claves.ARMA, PersistentDataType.STRING, t.name());
        meta.getPersistentDataContainer().set(Claves.BALAS, PersistentDataType.INTEGER, t.cargador);
        meta.setUnbreakable(true);
        if (t.modelo != null) meta.setItemModel(new NamespacedKey("tresmodos", t.modelo));
        it.setItemMeta(meta);
        return it;
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

    private static int balas(ItemStack it) {
        Integer b = it.getItemMeta().getPersistentDataContainer().get(Claves.BALAS, PersistentDataType.INTEGER);
        return b == null ? 0 : b;
    }

    private static void setBalas(ItemStack it, int n) {
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(Claves.BALAS, PersistentDataType.INTEGER, n);
        it.setItemMeta(meta);
    }

    private boolean mundoConArmas(Player p) {
        Modo m = Modo.de(p.getWorld());
        return m == Modo.GTA || m == Modo.COD;
    }

    // ------------------------------------------------------------------ disparo

    @EventHandler(priority = EventPriority.HIGH)
    public void alInteractuar(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        ItemStack it = p.getInventory().getItemInMainHand();
        Tipo t = tipo(it);
        if (t == null) return;
        e.setCancelled(true); // evita arar la tierra con la azada
        if (!mundoConArmas(p)) return;
        if (plugin.cod().bloqueaDisparo(p)) return;
        disparar(p, it, t);
    }

    private void disparar(Player p, ItemStack it, Tipo t) {
        UUID id = p.getUniqueId();
        int ahora = Bukkit.getCurrentTick();
        Integer ult = ultimoDisparo.get(id);
        if (ult != null && ahora - ult < t.cadencia) return;
        if (recargando.containsKey(id)) return;
        int balas = balas(it);
        if (balas <= 0) {
            p.playSound(p, Sound.BLOCK_DISPENSER_FAIL, 0.6f, 1.6f);
            recargar(p, it, t);
            return;
        }
        ultimoDisparo.put(id, ahora);
        setBalas(it, balas - 1);

        Location ojo = p.getEyeLocation();
        Vector dir = ojo.getDirection();
        double disp = t.dispersion;
        if (t == Tipo.FRANCOTIRADOR) disp = conZoom.contains(id) ? 0.0 : 0.09;
        else if (p.isSneaking()) disp *= 0.5;
        if (p.isSprinting()) disp *= 1.8;
        if (!((Entity) p).isOnGround()) disp *= 1.5;

        World w = p.getWorld();
        for (int i = 0; i < t.perdigones; i++) {
            Vector d = dir.clone().add(new Vector(rnd.nextGaussian() * disp, rnd.nextGaussian() * disp,
                    rnd.nextGaussian() * disp)).normalize();
            RayTraceResult r = w.rayTrace(ojo, d, t.alcance, FluidCollisionMode.NEVER, true, 0.15,
                    ent -> blancoValido(p, ent));
            double distancia = t.alcance;
            if (r != null) {
                distancia = r.getHitPosition().distance(ojo.toVector());
                Location punto = r.getHitPosition().toLocation(w);
                if (r.getHitEntity() instanceof LivingEntity le) {
                    impactar(p, le, t, r.getHitPosition(), distancia);
                } else if (r.getHitBlock() != null) {
                    w.spawnParticle(Particle.BLOCK, punto, 5, 0.05, 0.05, 0.05, 0, r.getHitBlock().getBlockData());
                }
            }
            trazador(ojo, d, distancia, t);
        }
        sonidoDisparo(p, t);
        w.spawnParticle(Particle.SMOKE, ojo.clone().add(dir.clone().multiply(0.9)), 2, 0.02, 0.02, 0.02, 0.01);
        mostrarBalas(p, it, t);
        if (balas - 1 <= 0) recargar(p, it, t);
    }

    private boolean blancoValido(Player tirador, Entity ent) {
        if (ent == tirador || !(ent instanceof LivingEntity le) || le.isDead()) return false;
        if (ent instanceof ArmorStand) return false;
        if (ent instanceof Player op && (op.getGameMode() == org.bukkit.GameMode.SPECTATOR)) return false;
        if (ent instanceof AbstractHorse && ent.getPersistentDataContainer().has(Claves.AUTO_DUENO)) return false;
        return true;
    }

    private void impactar(Player tirador, LivingEntity blanco, Tipo t, Vector punto, double distancia) {
        double danio = t.danio;
        if (t != Tipo.FRANCOTIRADOR && distancia > t.alcance * 0.6) danio *= 0.7;
        boolean cabeza = punto.getY() >= blanco.getEyeLocation().getY() - 0.3;
        if (cabeza) danio *= (t == Tipo.FRANCOTIRADOR ? 1.3 : 1.8);
        impactos.put(blanco.getUniqueId(), new Impacto(tirador.getUniqueId(), t.nombre, cabeza, Bukkit.getCurrentTick()));
        blanco.setNoDamageTicks(0);
        aplicandoBala = true;
        try {
            blanco.damage(danio, tirador);
        } finally {
            aplicandoBala = false;
        }
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

    private void sonidoDisparo(Player p, Tipo t) {
        Location l = p.getLocation();
        World w = p.getWorld();
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
        String color = b == 0 ? "<red>" : b <= t.cargador / 4 ? "<yellow>" : "<white>";
        Util.barra(p, "<gray>" + t.nombre + "  " + Hud.ICONO_BALA + " " + color + "<bold>" + b + "</bold><gray> / " + t.cargador);
    }

    // ------------------------------------------------------------------ recarga

    public void recargar(Player p, ItemStack it, Tipo t) {
        UUID id = p.getUniqueId();
        if (recargando.containsKey(id) || balas(it) >= t.cargador) return;
        int ahora = Bukkit.getCurrentTick();
        recargando.put(id, new Recarga(p.getInventory().getHeldItemSlot(), t, ahora, ahora + t.recarga));
        p.playSound(p, Sound.ITEM_CROSSBOW_LOADING_START, 0.8f, 1.2f);
        quitarZoom(p);
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
                setBalas(it, r.tipo().cargador);
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
        quitarZoom(p);
        recargando.remove(p.getUniqueId());
        ItemStack it = p.getInventory().getItem(e.getNewSlot());
        Tipo t = tipo(it);
        if (t != null && mundoConArmas(p)) mostrarBalas(p, it, t);
    }

    // ------------------------------------------------------------------ mira del francotirador

    @EventHandler
    public void alAgacharse(PlayerToggleSneakEvent e) {
        Player p = e.getPlayer();
        if (!mundoConArmas(p)) return;
        if (e.isSneaking() && tipo(p.getInventory().getItemInMainHand()) == Tipo.FRANCOTIRADOR
                && !recargando.containsKey(p.getUniqueId())) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, PotionEffect.INFINITE_DURATION, 6, false, false, false));
            conZoom.add(p.getUniqueId());
        } else {
            quitarZoom(p);
        }
    }

    public void quitarZoom(Player p) {
        if (conZoom.remove(p.getUniqueId())) p.removePotionEffect(PotionEffectType.SLOWNESS);
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
        conZoom.remove(id);
    }

    public static List<Tipo> todos() {
        return List.of(Tipo.values());
    }
}
