package ar.tresmodos.rpg;

import ar.tresmodos.Claves;
import ar.tresmodos.TresModos;
import ar.tresmodos.Util;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.CaveSpider;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Spider;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * La Tejedora (Bosque Podrido). F1: telarañas que frenan y saltos. F2 (50 % y 20 %): se cuelga en el
 * aire con tres hilos y suelta crías; hay que cortar los hilos para que caiga aturdida.
 */
public class JefeTejedora extends Jefe {
    private boolean colgada;
    private int vecesColgada;
    private Location colgadaEn;
    private final List<Jefes.Rompible> hilos = new ArrayList<>();
    private final Set<UUID> crias = new HashSet<>();

    public JefeTejedora(TresModos plugin, ModoRpg modo) {
        super(plugin, modo);
    }

    @Override public String id() { return "tejedora"; }
    @Override public String nombre() { return "La Tejedora"; }
    @Override public String alma() { return "Alma de la Tejedora"; }
    @Override protected double vidaBase() { return 650; }
    @Override public long almas() { return 9000; }

    @Override
    protected LivingEntity crearCuerpo(Location c) {
        colgada = false;
        vecesColgada = 0;
        return c.getWorld().spawn(c, Spider.class, CreatureSpawnEvent.SpawnReason.CUSTOM, s -> {
            TipoEnemigo.base(s, Attribute.SCALE, 2.8);
            TipoEnemigo.base(s, Attribute.ATTACK_DAMAGE, 9);
            TipoEnemigo.base(s, Attribute.MOVEMENT_SPEED, 0.32);
        });
    }

    @Override
    protected void vestir() {
        adorno("caparazon_tejedora", 0, 1.45f, -0.5f, 2.6f);
    }

    @Override
    protected void pensar(int ahora) {
        if (colgada) {
            cuerpo.teleport(colgadaEn);
            cuerpo.setVelocity(new Vector());
            for (Jefes.Rompible h : hilos) {
                if (!h.vivo()) continue;
                Vector d = colgadaEn.toVector().subtract(h.lugar().toVector());
                double largo = d.length();
                d.normalize();
                for (double s = 0; s < largo; s += 0.7) {
                    cuerpo.getWorld().spawnParticle(Particle.DUST, h.lugar().clone().add(0, 1.5, 0).add(d.clone().multiply(s)),
                            1, 0, 0, 0, 0, new Particle.DustOptions(Color.fromRGB(235, 235, 235), 0.8f));
                }
            }
            if ((ahora / 2) % 20 == 0) {
                Player t = alAzar();
                if (t != null) telarania(t);
            }
            if ((ahora / 2) % 80 == 0 && crias.size() < 6) {
                for (int i = 0; i < 2; i++) cria();
            }
            return;
        }
        if ((vecesColgada == 0 && vidaFrac() < 0.5) || (vecesColgada == 1 && vidaFrac() < 0.2)) {
            colgarse();
            return;
        }
        if (ahora < proximo) return;
        Player t = objetivo();
        if (t == null) return;
        double d = t.getLocation().distance(cuerpo.getLocation());
        if (d > 5 && azar.nextBoolean()) salto(t, ahora);
        else {
            proximo = ahora + 60;
            for (Player p : arena.participantes()) if (azar.nextDouble() < 0.7) telarania(p);
        }
    }

    private void telarania(Player t) {
        Location ojo = cuerpo.getEyeLocation();
        Vector dir = t.getEyeLocation().toVector().subtract(ojo.toVector()).normalize();
        Snowball s = cuerpo.launchProjectile(Snowball.class, dir.multiply(1.3));
        s.setItem(new ItemStack(Material.COBWEB));
        s.getPersistentDataContainer().set(Claves.EQUIPO, PersistentDataType.STRING, "telarania");
        cuerpo.getWorld().playSound(ojo, Sound.ENTITY_SPIDER_AMBIENT, 1f, 0.6f);
    }

    private void salto(Player t, int ahora) {
        proximo = ahora + 70;
        Location destino = t.getLocation();
        aviso(destino, 3, Color.fromRGB(120, 200, 60));
        cuerpo.getWorld().playSound(cuerpo.getLocation(), Sound.ENTITY_SPIDER_HURT, 1.2f, 0.5f);
        despues(12, () -> {
            Vector v = destino.toVector().subtract(cuerpo.getLocation().toVector());
            double dist = v.clone().setY(0).length();
            cuerpo.setVelocity(v.setY(0).normalize().multiply(Math.min(1.8, 0.4 + dist * 0.11)).setY(0.9));
        });
        despues(32, () -> {
            Location l = cuerpo.getLocation();
            l.getWorld().spawnParticle(Particle.BLOCK, l, 40, 2, 0.1, 2, 0, l.clone().subtract(0, 1, 0).getBlock().getBlockData());
            l.getWorld().playSound(l, Sound.ENTITY_GENERIC_SMALL_FALL, 1.5f, 0.5f);
            golpeArea(l, 3.2, 10, 0.7);
        });
    }

    private void colgarse() {
        colgada = true;
        vecesColgada++;
        colgadaEn = arena.centro.clone().add(0, 7, 0);
        cuerpo.setGravity(false);
        cuerpo.teleport(colgadaEn);
        if (cuerpo instanceof org.bukkit.entity.Mob m) m.setAware(false);
        cambiarFase(2, "<green>La Tejedora se cuelga: <white>cortá los hilos");
        cuerpo.getWorld().playSound(colgadaEn, Sound.ENTITY_SPIDER_STEP, 2f, 0.4f);
        Jefes jefes = modo.jefes();
        for (int i = 0; i < 3; i++) {
            double a = i * Math.PI * 2 / 3 + azar.nextDouble();
            Location l = arena.centro.clone().add(Math.cos(a) * 8, 0, Math.sin(a) * 8);
            hilos.add(jefes.rompible(l, new ItemStack(Material.COBWEB), 0.8f, 1.2f, 2.6f, 4 + arena.participantes().size(), r -> {
                if (hilos.stream().noneMatch(Jefes.Rompible::vivo)) caer();
            }));
        }
    }

    private void caer() {
        colgada = false;
        hilos.clear();
        cuerpo.setGravity(true);
        if (cuerpo instanceof org.bukkit.entity.Mob m) m.setAware(true);
        cuerpo.damage(cuerpo.getAttribute(Attribute.MAX_HEALTH).getValue() * 0.06);
        modo.combate().aturdir(cuerpo, 100);
        for (Player p : arena.participantes()) Util.titulo(p, "", "<yellow>¡Cayó! <gray>Es el momento", 0, 1500, 300);
    }

    private void cria() {
        double a = azar.nextDouble() * Math.PI * 2;
        Location l = arena.centro.clone().add(Math.cos(a) * 6, 0.2, Math.sin(a) * 6);
        CaveSpider c = l.getWorld().spawn(l, CaveSpider.class, CreatureSpawnEvent.SpawnReason.CUSTOM, s -> {
            s.customName(Util.mm("<gray>Cría"));
            TipoEnemigo.base(s, Attribute.MAX_HEALTH, 14);
            s.setHealth(14);
            s.setPersistent(false);
            Player t = alAzar();
            if (t != null) s.setTarget(t);
        });
        crias.add(c.getUniqueId());
    }

    @Override
    public boolean esParte(Entity e) {
        return crias.contains(e.getUniqueId());
    }

    @Override
    public void alRecibir(EntityDamageByEntityEvent e, Player atacante) {
        if (colgada && esCuerpo(e.getEntity()) && !(e.getDamager() instanceof org.bukkit.entity.Projectile)) e.setCancelled(true);
    }

    @Override
    public void alGolpear(Player v, EntityDamageByEntityEvent e) {
        modo.estados().acumular(v, Estado.VENENO, esCuerpo(e.getDamager()) ? 25 : 10, null);
    }

    @Override
    protected void limpiarExtra() {
        for (Jefes.Rompible h : hilos) modo.jefes().olvidarRompible(h);
        hilos.clear();
        for (UUID u : crias) {
            Entity e = plugin.getServer().getEntity(u);
            if (e != null) e.remove();
        }
        crias.clear();
        colgada = false;
    }
}
