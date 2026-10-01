package ar.tresmodos.rpg;

import ar.tresmodos.TresModos;
import ar.tresmodos.Util;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * El Vigía Pálido (Costa Hundida). Aparece detrás de los jugadores, grita (locura) y en la F2 crea
 * copias falsas: solo el real deja huellas.
 */
public class JefeVigia extends Jefe {
    private final Set<UUID> copias = new HashSet<>();
    private int proximaCopia;

    public JefeVigia(TresModos plugin, ModoRpg modo) {
        super(plugin, modo);
    }

    @Override public String id() { return "vigia"; }
    @Override public String nombre() { return "El Vigía Pálido"; }
    @Override public String alma() { return "Alma del Vigía"; }
    @Override protected double vidaBase() { return 1200; }
    @Override public long almas() { return 30000; }
    @Override protected BossBar.Color colorBarra() { return BossBar.Color.YELLOW; }

    @Override
    protected LivingEntity crearCuerpo(Location c) {
        return c.getWorld().spawn(c, Enderman.class, CreatureSpawnEvent.SpawnReason.CUSTOM, e -> {
            TipoEnemigo.base(e, Attribute.SCALE, 1.7);
            TipoEnemigo.base(e, Attribute.ATTACK_DAMAGE, 12);
            TipoEnemigo.base(e, Attribute.MOVEMENT_SPEED, 0.33);
            e.setScreaming(true);
        });
    }

    @Override
    protected void pensar(int ahora) {
        // Las huellas del real.
        cuerpo.getWorld().spawnParticle(Particle.DUST, cuerpo.getLocation().add(0, 0.05, 0), 2, 0.2, 0, 0.2, 0,
                new Particle.DustOptions(Color.fromRGB(230, 230, 230), 1.4f));
        if (fase == 1 && vidaFrac() < 0.5) {
            cambiarFase(2, "<white>La niebla se llena de ojos");
            proximaCopia = ahora;
        }
        if (fase == 2 && ahora >= proximaCopia) {
            proximaCopia = ahora + 500;
            for (int i = 0; i < 2; i++) copia();
        }
        if (ahora < proximo) return;
        double r = azar.nextDouble();
        if (r < 0.35) aparecerDetras(ahora);
        else if (r < 0.6) grito(ahora);
        else proximo = ahora + 40;
    }

    private void aparecerDetras(int ahora) {
        proximo = ahora + 90;
        Player t = alAzar();
        if (t == null) return;
        Location destino = t.getLocation().add(t.getLocation().getDirection().setY(0).normalize().multiply(-2.2));
        destino.setY(arena.centro.getY());
        if (!arena.dentro(destino, 1)) destino = arena.centro.clone();
        Location fin = destino;
        fin.getWorld().spawnParticle(Particle.PORTAL, fin.clone().add(0, 1, 0), 60, 0.4, 1, 0.4, 0.4);
        fin.getWorld().playSound(fin, Sound.ENTITY_ENDERMAN_STARE, 1f, 0.7f);
        despues(15, () -> {
            fin.setDirection(t.getLocation().toVector().subtract(fin.toVector()));
            cuerpo.teleport(fin);
            cuerpo.getWorld().playSound(fin, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
        });
        despues(25, () -> {
            cuerpo.swingMainHand();
            golpeCono(3.6, 0.2, 10);
        });
    }

    private void grito(int ahora) {
        proximo = ahora + 110;
        cuerpo.getWorld().playSound(cuerpo.getLocation(), Sound.ENTITY_ENDERMAN_SCREAM, 2f, 0.5f);
        for (Player p : arena.participantes()) p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false));
        despues(20, () -> {
            cuerpo.getWorld().playSound(cuerpo.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 0.6f);
            for (Player p : enRadio(16)) {
                modo.estados().acumular(p, Estado.LOCURA, 40, null);
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, false));
            }
        });
    }

    private void copia() {
        double a = azar.nextDouble() * Math.PI * 2;
        Location l = arena.centro.clone().add(Math.cos(a) * 8, 0, Math.sin(a) * 8);
        l.getWorld().spawnParticle(Particle.LARGE_SMOKE, l.clone().add(0, 1, 0), 30, 0.3, 1, 0.3, 0.02);
        Enderman e = l.getWorld().spawn(l, Enderman.class, CreatureSpawnEvent.SpawnReason.CUSTOM, x -> {
            x.customName(Util.mm("<dark_red><bold>" + nombre()));
            x.setCustomNameVisible(false);
            TipoEnemigo.base(x, Attribute.SCALE, 1.7);
            TipoEnemigo.base(x, Attribute.MAX_HEALTH, 1);
            x.setHealth(1);
            TipoEnemigo.base(x, Attribute.ATTACK_DAMAGE, 4);
            x.setScreaming(true);
            x.setPersistent(false);
            Player t = alAzar();
            if (t != null) x.setTarget(t);
        });
        copias.add(e.getUniqueId());
    }

    @Override
    public boolean esParte(Entity e) {
        return copias.contains(e.getUniqueId());
    }

    @Override
    public void alRecibir(EntityDamageByEntityEvent e, Player atacante) {
        if (!copias.remove(e.getEntity().getUniqueId())) return;
        e.setCancelled(true);
        Location l = e.getEntity().getLocation();
        l.getWorld().spawnParticle(Particle.LARGE_SMOKE, l.clone().add(0, 1.5, 0), 40, 0.4, 1, 0.4, 0.02);
        l.getWorld().playSound(l, Sound.ENTITY_ENDERMAN_DEATH, 1f, 1.5f);
        e.getEntity().remove();
        Util.barra(atacante, "<gray>Era una copia. <white>El real deja huellas.");
    }

    @Override
    public void alGolpear(Player v, EntityDamageByEntityEvent e) {
        modo.estados().acumular(v, Estado.LOCURA, 10, null);
    }

    @Override
    protected void limpiarExtra() {
        for (UUID u : copias) {
            Entity e = plugin.getServer().getEntity(u);
            if (e != null) e.remove();
        }
        copias.clear();
    }
}
