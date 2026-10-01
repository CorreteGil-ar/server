package ar.tresmodos.rpg;

import ar.tresmodos.Claves;
import ar.tresmodos.Modo;
import ar.tresmodos.TresModos;
import ar.tresmodos.Util;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Vex;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/**
 * Encuentros diseñados: cada zona tiene puntos fijos (elegidos con la semilla del mundo) donde aparece
 * siempre el mismo enemigo o la misma jauría, con nivel según el lugar. Un 10 % son Campeones (élites).
 * Los muertos vuelven cuando alguien descansa en una hoguera; las élites derrotadas no.
 */
public class Enemigos implements Listener {
    private static final int PUNTOS_POR_ZONA = 45;
    private static final NamespacedKey MULT = new NamespacedKey("tresmodos", "rpg_mult_danio");

    private static final class Punto {
        final String id;
        final Zona zona;
        final int x, z;
        final TipoEnemigo tipo;
        final boolean elite;
        Integer y;
        boolean invalido;
        final List<UUID> vivos = new ArrayList<>();
        int muertoTick = -1;

        Punto(String id, Zona zona, int x, int z, TipoEnemigo tipo, boolean elite) {
            this.id = id;
            this.zona = zona;
            this.x = x;
            this.z = z;
            this.tipo = tipo;
            this.elite = elite;
        }
    }

    private final TresModos plugin;
    private final ModoRpg modo;
    private final List<Punto> puntos = new ArrayList<>();
    private final Map<String, Punto> porId = new HashMap<>();
    private final Map<UUID, Punto> deEntidad = new HashMap<>();
    private final Set<String> elitesMuertas = new HashSet<>();
    private final Map<UUID, Integer> proximoEspecial = new HashMap<>();
    private int ultimoDescanso;

    public Enemigos(TresModos plugin, ModoRpg modo) {
        this.plugin = plugin;
        this.modo = modo;
        Bukkit.getScheduler().runTaskLater(plugin, this::crearPuntos, 5);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickPuntos, 40, 20);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickConducta, 45, 10);
    }

    private World mundo() {
        return plugin.mundos().de(Modo.RPG);
    }

    private void crearPuntos() {
        MundoRpg m = modo.mundoRpg();
        elitesMuertas.addAll(m.datosExtra().getStringList("elites-muertas"));
        long semilla = mundo().getSeed();
        for (Zona z : new Zona[]{Zona.ALDEA, Zona.BOSQUE, Zona.CIUDADELA, Zona.COSTA}) {
            Random r = new Random(semilla ^ (z.ordinal() * 0x9E3779B97F4A7C15L));
            TipoEnemigo[] tabla = TipoEnemigo.de(z);
            int creados = 0, intentos = 0;
            while (creados < PUNTOS_POR_ZONA && intentos++ < PUNTOS_POR_ZONA * 10) {
                double ang = r.nextDouble() * Math.PI * 2, rad = Math.sqrt(r.nextDouble()) * z.radio * 0.95;
                int x = m.origenX() + z.dx + (int) (Math.cos(ang) * rad);
                int zz = m.origenZ() + z.dz + (int) (Math.sin(ang) * rad);
                boolean cercaHoguera = false;
                for (MundoRpg.Hoguera h : m.hogueras()) {
                    if (Math.hypot(h.x() - x, h.z() - zz) < 22) cercaHoguera = true;
                }
                TipoEnemigo t = tabla[r.nextInt(tabla.length)];
                boolean elite = r.nextDouble() < 0.10;
                if (cercaHoguera) continue;
                Punto p = new Punto(z.name().toLowerCase() + "_" + creados, z, x, zz, t, elite);
                puntos.add(p);
                porId.put(p.id, p);
                creados++;
            }
        }
        plugin.getLogger().info("RPG: " + puntos.size() + " encuentros en el mundo.");
    }

    /** Alguien descansó: los enemigos comunes muertos vuelven a aparecer. */
    public void descanso() {
        ultimoDescanso = Bukkit.getCurrentTick();
    }

    /** La Noche Roja: todos vuelven, más fuertes. Los vivos se reemplazan. */
    public void reiniciarTodos() {
        ultimoDescanso = Bukkit.getCurrentTick();
        for (Punto p : puntos) quitarVivos(p);
    }

    private void quitarVivos(Punto p) {
        for (UUID id : p.vivos) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
            deEntidad.remove(id);
        }
        p.vivos.clear();
    }

    // ------------------------------------------------------------------ aparición

    private void tickPuntos() {
        World w = mundo();
        List<Player> jugadores = new ArrayList<>();
        for (Player p : w.getPlayers()) if (!p.isDead()) jugadores.add(p);
        int ahora = Bukkit.getCurrentTick();
        for (Punto pt : puntos) {
            pt.vivos.removeIf(id -> {
                Entity e = Bukkit.getEntity(id);
                boolean fuera = e == null || !e.isValid();
                if (fuera) deEntidad.remove(id);
                return fuera;
            });
            double cerca = Double.MAX_VALUE;
            for (Player p : jugadores) {
                double d = Math.hypot(p.getX() - pt.x, p.getZ() - pt.z);
                if (d < cerca) cerca = d;
            }
            if (!pt.vivos.isEmpty()) {
                // Nadie cerca: se guarda (no cuenta como muerto).
                if (cerca > 80) quitarVivos(pt);
                continue;
            }
            if (cerca < 14 || cerca > 52 || pt.invalido) continue;
            if (pt.elite && elitesMuertas.contains(pt.id)) continue;
            if (pt.muertoTick >= 0 && pt.muertoTick > ultimoDescanso && ahora - pt.muertoTick < 20 * 60 * 15) continue;
            if (!w.isChunkLoaded(pt.x >> 4, pt.z >> 4)) continue;
            aparecer(pt);
        }
    }

    private void aparecer(Punto pt) {
        World w = mundo();
        if (pt.y == null) {
            int y = w.getHighestBlockYAt(pt.x, pt.z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Block suelo = w.getBlockAt(pt.x, y, pt.z);
            boolean agua = suelo.getType() == Material.WATER;
            if ((agua && pt.tipo != TipoEnemigo.AHOGADO) || suelo.getType() == Material.LAVA) {
                pt.invalido = true;
                return;
            }
            pt.y = y + 1;
        }
        pt.muertoTick = -1;
        Location l = new Location(w, pt.x + 0.5, pt.y, pt.z + 0.5);
        MundoRpg m = modo.mundoRpg();
        int nivel = m.nivel(l) + (m.nocheRoja() ? 5 : 0);
        int ciclo = 0;
        for (Player p : w.getPlayers()) ciclo = Math.max(ciclo, plugin.almacen().de(p).ciclo);
        int cantidad = pt.elite ? 1 : pt.tipo.grupo;
        for (int i = 0; i < cantidad; i++) {
            Location li = l.clone().add(i == 0 ? 0 : Math.random() * 3 - 1.5, 0, i == 0 ? 0 : Math.random() * 3 - 1.5);
            LivingEntity e = crear(pt, li, nivel, ciclo, m.nocheRoja());
            pt.vivos.add(e.getUniqueId());
            deEntidad.put(e.getUniqueId(), pt);
        }
    }

    private LivingEntity crear(Punto pt, Location l, int nivel, int ciclo, boolean roja) {
        TipoEnemigo t = pt.tipo;
        double fCiclo = Math.pow(1.5, ciclo);
        double vida = t.vida * (1 + 0.08 * nivel) * fCiclo * (pt.elite ? 2 : 1) * (roja ? 1.25 : 1);
        double danio = (1 + 0.05 * nivel) * fCiclo * (pt.elite ? 1.3 : 1);
        return l.getWorld().spawn(l, t.clase, CreatureSpawnEvent.SpawnReason.CUSTOM, e -> {
            t.preparar.accept(e);
            TipoEnemigo.base(e, Attribute.MAX_HEALTH, vida);
            e.setHealth(vida);
            TipoEnemigo.base(e, Attribute.ATTACK_DAMAGE, t.danio * danio);
            TipoEnemigo.base(e, Attribute.FOLLOW_RANGE, 28);
            if (pt.elite) TipoEnemigo.base(e, Attribute.KNOCKBACK_RESISTANCE, 0.6);
            e.customName(Util.mm((pt.elite ? "<gold>Nv. " + nivel + " <bold>Campeón " : (roja ? "<red>" : "<gray>") + "Nv. " + nivel + " <white>")
                    + t.nombre));
            e.setCustomNameVisible(false);
            e.setRemoveWhenFarAway(false);
            e.setPersistent(false);
            e.setCanPickupItems(false);
            PersistentDataContainer pdc = e.getPersistentDataContainer();
            pdc.set(Claves.ENEMIGO, PersistentDataType.STRING, pt.id);
            pdc.set(Claves.NIVEL, PersistentDataType.INTEGER, nivel);
            pdc.set(MULT, PersistentDataType.DOUBLE, danio);
            if (pt.elite) pdc.set(Claves.ELITE, PersistentDataType.BYTE, (byte) 1);
        });
    }

    // ------------------------------------------------------------------ conducta

    private void tickConducta() {
        int ahora = Bukkit.getCurrentTick();
        for (Map.Entry<UUID, Punto> en : new ArrayList<>(deEntidad.entrySet())) {
            Entity ent = Bukkit.getEntity(en.getKey());
            if (!(ent instanceof Mob m) || !m.isValid()) continue;
            Punto pt = en.getValue();
            if (modo.combate().aturdido(m)) continue;
            Player obj = m.getTarget() instanceof Player tp ? tp : null;
            if (obj == null) obj = cercano(m.getLocation(), pt.tipo == TipoEnemigo.PERRO ? 18 : 0);
            if (pt.tipo == TipoEnemigo.PERRO && obj != null && m instanceof Wolf w) {
                if (!w.isAngry()) w.setAngry(true);
                if (w.getTarget() == null) w.setTarget(obj);
            }
            if (pt.elite) {
                m.getWorld().spawnParticle(Particle.DUST, m.getLocation().add(0, m.getHeight() * 0.5, 0), 4,
                        0.35, m.getHeight() * 0.3, 0.35, 0, new Particle.DustOptions(Color.fromRGB(255, 190, 40), 1.2f));
            }
            if (obj == null || ahora < proximoEspecial.getOrDefault(m.getUniqueId(), 0)) continue;
            double d = m.getLocation().distance(obj.getLocation());
            if (pt.elite && d < 5) {
                proximoEspecial.put(m.getUniqueId(), ahora + 120);
                golpeAlPiso(m);
            } else if (pt.tipo == TipoEnemigo.TEJEDORA && d < 12 && m.hasLineOfSight(obj)) {
                proximoEspecial.put(m.getUniqueId(), ahora + 80);
                telarania(m, obj);
            } else if (pt.tipo == TipoEnemigo.BESTIA && d > 3 && d < 14) {
                proximoEspecial.put(m.getUniqueId(), ahora + 140);
                embestida(m, obj);
            }
        }
    }

    private static Player cercano(Location l, double r) {
        if (r <= 0) return null;
        Player mejor = null;
        double md = r * r;
        for (Player p : l.getWorld().getPlayers()) {
            if (p.isDead() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            double d = p.getLocation().distanceSquared(l);
            if (d < md) {
                md = d;
                mejor = p;
            }
        }
        return mejor;
    }

    /** Ataque especial de Campeón: anuncia con un círculo y golpea alrededor (se esquiva rodando). */
    private void golpeAlPiso(Mob m) {
        World w = m.getWorld();
        Location c = m.getLocation();
        w.playSound(c, Sound.ENTITY_RAVAGER_ROAR, 1f, 1.2f);
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI / 12;
            w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * 3.5, 0.2, Math.sin(a) * 3.5), 1, 0, 0, 0, 0,
                    new Particle.DustOptions(Color.fromRGB(255, 120, 20), 1.5f));
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!m.isValid() || modo.combate().aturdido(m)) return;
            Location l = m.getLocation();
            w.spawnParticle(Particle.EXPLOSION, l, 2, 1, 0.2, 1, 0);
            w.spawnParticle(Particle.BLOCK, l, 40, 2, 0.1, 2, 0, l.clone().subtract(0, 1, 0).getBlock().getBlockData());
            w.playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.8f);
            double danio = m.getAttribute(Attribute.ATTACK_DAMAGE).getValue() * 1.5;
            for (Player p : w.getPlayers()) {
                if (p.getLocation().distanceSquared(l) > 3.5 * 3.5) continue;
                p.damage(danio, m);
                Vector v = p.getLocation().toVector().subtract(l.toVector()).setY(0);
                if (v.lengthSquared() > 0.01) p.setVelocity(v.normalize().multiply(0.7).setY(0.4));
            }
        }, 16);
    }

    private void telarania(Mob m, Player obj) {
        Vector dir = obj.getEyeLocation().toVector().subtract(m.getEyeLocation().toVector()).normalize();
        Snowball s = m.launchProjectile(Snowball.class, dir.multiply(1.2));
        s.setItem(new ItemStack(Material.COBWEB));
        s.getPersistentDataContainer().set(Claves.EQUIPO, PersistentDataType.STRING, "telarania");
        m.getWorld().playSound(m.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 1f, 1.6f);
    }

    private void embestida(Mob m, Player obj) {
        m.getWorld().playSound(m.getLocation(), Sound.ENTITY_HOGLIN_ANGRY, 1f, 0.6f);
        m.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, m.getEyeLocation(), 3, 0.3, 0.2, 0.3, 0);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!m.isValid() || modo.combate().aturdido(m) || !obj.isOnline()) return;
            Vector v = obj.getLocation().toVector().subtract(m.getLocation().toVector()).setY(0);
            if (v.lengthSquared() < 0.01) return;
            m.setVelocity(v.normalize().multiply(1.5).setY(0.2));
        }, 15);
    }

    @EventHandler
    public void alImpactar(ProjectileHitEvent e) {
        Projectile pr = e.getEntity();
        if (!"telarania".equals(pr.getPersistentDataContainer().get(Claves.EQUIPO, PersistentDataType.STRING))) return;
        pr.remove();
        if (e.getHitEntity() instanceof Player p) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2, false, true));
            p.getWorld().spawnParticle(Particle.BLOCK, p.getLocation().add(0, 1, 0), 30, 0.4, 0.5, 0.4, 0,
                    Material.COBWEB.createBlockData());
            Util.barra(p, "<gray>Te atraparon las telarañas");
        }
    }

    // ------------------------------------------------------------------ daño y reglas

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void alGolpe(EntityDamageByEntityEvent e) {
        if (Modo.de(e.getEntity().getWorld()) != Modo.RPG) return;
        Entity fuente = e.getDamager();
        if (fuente instanceof Projectile pr && pr.getShooter() instanceof Entity s) fuente = s;
        if (fuente instanceof EvokerFangs f && f.getOwner() != null) fuente = f.getOwner();
        boolean fuenteEnemiga = fuente.getPersistentDataContainer().has(Claves.ENEMIGO);
        // Sin peleas entre enemigos.
        if (fuenteEnemiga && e.getEntity().getPersistentDataContainer().has(Claves.ENEMIGO)) {
            e.setCancelled(true);
            return;
        }
        if (fuenteEnemiga && fuente != e.getDamager()) {
            Double mult = fuente.getPersistentDataContainer().get(MULT, PersistentDataType.DOUBLE);
            if (mult != null) e.setDamage(e.getDamage() * mult);
        }
        // Estados de cada criatura.
        if (fuenteEnemiga && e.getEntity() instanceof Player p) {
            Punto pt = porId.get(fuente.getPersistentDataContainer().get(Claves.ENEMIGO, PersistentDataType.STRING));
            if (pt != null) {
                switch (pt.tipo) {
                    case PALIDO -> modo.estados().acumular(p, Estado.LOCURA, 25, null);
                    case VASTAGO -> modo.estados().acumular(p, Estado.LOCURA, 30, null);
                    case PERRO, ACECHADOR -> modo.estados().acumular(p, Estado.SANGRADO, 12, null);
                    case TEJEDORA -> modo.estados().acumular(p, Estado.VENENO, 20, null);
                    case AHOGADO -> modo.estados().acumular(p, Estado.FRIO, 15, null);
                    default -> { }
                }
                if (pt.zona == Zona.TEMPLO) modo.estados().acumular(p, Estado.LOCURA, 8, null);
            }
        }
        // El Caballero caído bloquea de frente con el escudo.
        if (e.getEntity() instanceof Mob m && e.getEntity().getPersistentDataContainer().has(Claves.ENEMIGO)
                && fuente instanceof Player atacante) {
            Punto pt = deEntidad.get(m.getUniqueId());
            if (pt != null && pt.tipo == TipoEnemigo.CABALLERO && !modo.combate().aturdido(m)
                    && !(e.getDamager() instanceof Projectile) && Math.random() < 0.35 && deFrente(m, atacante)) {
                e.setCancelled(true);
                m.getWorld().playSound(m.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 0.9f);
                modo.gastar(atacante, 10);
                Util.barra(atacante, "<gray>Bloqueó. <white>Rompé su postura o atacalo por la espalda.");
            }
        }
    }

    private static boolean deFrente(LivingEntity v, LivingEntity a) {
        Vector mira = v.getLocation().getDirection().setY(0);
        Vector hacia = a.getLocation().toVector().subtract(v.getLocation().toVector()).setY(0);
        return mira.lengthSquared() > 0.01 && hacia.lengthSquared() > 0.01 && mira.normalize().dot(hacia.normalize()) > 0.3;
    }

    @EventHandler(ignoreCancelled = true)
    public void alApuntar(EntityTargetLivingEntityEvent e) {
        if (e.getTarget() != null && e.getEntity().getPersistentDataContainer().has(Claves.ENEMIGO)
                && e.getTarget().getPersistentDataContainer().has(Claves.ENEMIGO)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void alQuemarse(EntityCombustEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(Claves.ENEMIGO)
                && !(e instanceof org.bukkit.event.entity.EntityCombustByEntityEvent)
                && !(e instanceof org.bukkit.event.entity.EntityCombustByBlockEvent)) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void alAparecer(CreatureSpawnEvent e) {
        // Los Vástagos (evocadores) no invocan vexes: sus colmillos son los tentáculos.
        if (Modo.de(e.getEntity().getWorld()) == Modo.RPG && e.getEntity() instanceof Vex
                && e.getSpawnReason() != CreatureSpawnEvent.SpawnReason.CUSTOM) e.setCancelled(true);
    }

    // ------------------------------------------------------------------ muerte, almas y botín

    @EventHandler
    public void alMorir(EntityDeathEvent e) {
        LivingEntity v = e.getEntity();
        String id = v.getPersistentDataContainer().get(Claves.ENEMIGO, PersistentDataType.STRING);
        if (id == null) return;
        Punto pt = porId.get(id);
        deEntidad.remove(v.getUniqueId());
        proximoEspecial.remove(v.getUniqueId());
        e.getDrops().clear();
        e.setDroppedExp(0);
        if (pt == null) return;
        pt.vivos.remove(v.getUniqueId());
        if (pt.vivos.isEmpty()) pt.muertoTick = Bukkit.getCurrentTick();
        if (pt.elite) {
            elitesMuertas.add(pt.id);
            modo.mundoRpg().guardarExtra("elites-muertas", new ArrayList<>(elitesMuertas));
        }
        Player asesino = v.getKiller();
        if (asesino == null) return;
        int nivel = v.getPersistentDataContainer().getOrDefault(Claves.NIVEL, PersistentDataType.INTEGER, 1);
        boolean roja = modo.mundoRpg().nocheRoja();
        // Cooperativo: todos los que están cerca reciben sus almas.
        for (Player p : v.getWorld().getPlayers()) {
            if (p != asesino && p.getLocation().distanceSquared(v.getLocation()) > 20 * 20) continue;
            int ciclo = plugin.almacen().de(p).ciclo;
            long almas = Math.round(pt.tipo.almas * (1 + 0.12 * nivel) * (pt.elite ? 3 : 1) * (roja ? 2 : 1) * (1 + 0.5 * ciclo));
            plugin.almacen().de(p).almas += almas;
            modo.sumarEter(p, 3);
            Util.barra(p, "<gold>+" + Util.num(almas) + " almas" + (roja ? " <red>(Noche Roja)" : ""));
        }
        if (pt.elite) {
            Util.titulo(asesino, "", "<gold>Campeón derrotado", 200, 1500, 500);
            asesino.playSound(asesino, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.6f, 1.3f);
        }
        botin(e.getDrops(), pt, nivel);
    }

    private static void botin(List<ItemStack> drops, Punto pt, int nivel) {
        Random r = new Random();
        boolean alta = pt.zona.ordinal() >= Zona.CIUDADELA.ordinal();
        if (r.nextDouble() < (pt.elite ? 1.0 : 0.35)) drops.add(ObjetosRpg.fragmento(pt.elite ? 3 + r.nextInt(3) : 1 + r.nextInt(2)));
        if (alta && r.nextDouble() < (pt.elite ? 0.4 : 0.03)) drops.add(ObjetosRpg.escama(1));
        if (r.nextDouble() < (pt.elite ? 0.5 : 0.08)) {
            drops.add(switch (r.nextInt(6)) {
                case 0 -> ObjetosRpg.bombaFuego(1 + r.nextInt(2));
                case 1 -> ObjetosRpg.cuchillos(3 + r.nextInt(3));
                case 2 -> ObjetosRpg.resina(r.nextBoolean() ? ObjetosRpg.Resina.FUEGO : ObjetosRpg.Resina.RAYO, 1);
                case 3 -> ObjetosRpg.hierba(1);
                case 4 -> ObjetosRpg.ceniza(1);
                default -> new ItemStack(Material.ARROW, 8 + r.nextInt(8));
            });
        }
        if (r.nextDouble() < (pt.elite ? 0.2 : 0.015)) {
            ArmaRpg[] comunes = {ArmaRpg.ESPADA_CORTA, ArmaRpg.LANZA, ArmaRpg.ESTOQUE, ArmaRpg.ALABARDA, ArmaRpg.GUADANIA,
                    ArmaRpg.MARTILLO, ArmaRpg.BALLESTA};
            drops.add(comunes[r.nextInt(comunes.length)].crear(Math.min(3, nivel / 12)));
        }
        if (pt.elite && r.nextDouble() < 0.05) drops.add(ObjetosRpg.lagrima(1));
    }

    public void apagar() {
        for (Punto p : puntos) quitarVivos(p);
    }
}
