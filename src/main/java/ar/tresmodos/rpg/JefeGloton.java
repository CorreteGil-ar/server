package ar.tresmodos.rpg;

import ar.tresmodos.TresModos;
import ar.tresmodos.Util;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Ravager;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * El Glotón de las Fauces (Aldea Hueca). F1: embestidas y golpes al piso. F2 (50 %): se acelera y
 * agarra: el jugador tragado pierde vida hasta que los demás le peguen lo suficiente; si no, lo escupe.
 */
public class JefeGloton extends Jefe {
    private Player tragado;
    private int tragadoHasta;
    private double paraSoltar;

    public JefeGloton(TresModos plugin, ModoRpg modo) {
        super(plugin, modo);
    }

    @Override public String id() { return "gloton"; }
    @Override public String nombre() { return "El Glotón de las Fauces"; }
    @Override public String alma() { return "Alma del Glotón"; }
    @Override protected double vidaBase() { return 500; }
    @Override public long almas() { return 4000; }

    @Override
    protected LivingEntity crearCuerpo(Location c) {
        return c.getWorld().spawn(c, Ravager.class, CreatureSpawnEvent.SpawnReason.CUSTOM, r -> {
            TipoEnemigo.base(r, Attribute.SCALE, 1.4);
            TipoEnemigo.base(r, Attribute.ATTACK_DAMAGE, 10);
            TipoEnemigo.base(r, Attribute.MOVEMENT_SPEED, 0.3);
        });
    }

    @Override
    protected void pensar(int ahora) {
        if (tragado != null) {
            if (!tragado.isOnline() || tragado.isDead()) {
                tragado = null;
            } else {
                if (!cuerpo.getPassengers().contains(tragado)) cuerpo.addPassenger(tragado);
                if ((ahora / 2) % 5 == 0) tragado.damage(2 * multDanio, cuerpo);
                if (ahora >= tragadoHasta) escupir();
                return;
            }
        }
        if (fase == 1 && vidaFrac() < 0.5) {
            cambiarFase(2, "<red>El Glotón tiene hambre");
            TipoEnemigo.base(cuerpo, Attribute.MOVEMENT_SPEED, 0.38);
            cuerpo.getWorld().playSound(cuerpo.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.5f, 0.5f);
        }
        if (ahora < proximo) return;
        Player t = objetivo();
        if (t == null) return;
        double d = t.getLocation().distance(cuerpo.getLocation());
        if (fase == 2 && d < 4 && azar.nextDouble() < 0.4) agarre(t, ahora);
        else if (d > 6) embestida(t, ahora);
        else golpePiso(ahora);
    }

    private void embestida(Player t, int ahora) {
        proximo = ahora + (fase == 1 ? 80 : 55);
        mirarA(t);
        cuerpo.getWorld().playSound(cuerpo.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1.2f, 0.8f);
        cuerpo.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, cuerpo.getLocation().add(0, 1, 0), 10, 0.5, 0.3, 0.5, 0.01);
        Location objetivo = t.getLocation();
        despues(20, () -> {
            Vector v = objetivo.toVector().subtract(cuerpo.getLocation().toVector()).setY(0);
            if (v.lengthSquared() < 0.01) return;
            cuerpo.setVelocity(v.normalize().multiply(1.9).setY(0.15));
            Set<UUID> golpeados = new HashSet<>();
            for (int i = 1; i <= 12; i++) {
                despues(i, () -> {
                    for (Player p : enRadio(2.6)) {
                        if (!golpeados.add(p.getUniqueId())) continue;
                        p.damage(11 * multDanio, cuerpo);
                        p.setVelocity(cuerpo.getVelocity().clone().setY(0.5));
                    }
                });
            }
        });
    }

    private void golpePiso(int ahora) {
        proximo = ahora + (fase == 1 ? 70 : 50);
        Location c = cuerpo.getLocation();
        aviso(c, 5, Color.fromRGB(255, 120, 30));
        cuerpo.getWorld().playSound(c, Sound.ENTITY_RAVAGER_ATTACK, 1f, 0.5f);
        despues(12, () -> aviso(c, 5, Color.fromRGB(255, 60, 20)));
        despues(24, () -> {
            Location l = cuerpo.getLocation();
            l.getWorld().spawnParticle(Particle.EXPLOSION, l, 3, 1.5, 0.2, 1.5, 0);
            l.getWorld().spawnParticle(Particle.BLOCK, l, 60, 2.5, 0.1, 2.5, 0, l.clone().subtract(0, 1, 0).getBlock().getBlockData());
            l.getWorld().playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
            golpeArea(l, 5, 12, 0.9);
        });
    }

    private void agarre(Player t, int ahora) {
        proximo = ahora + 110;
        mirarA(t);
        cuerpo.getWorld().playSound(cuerpo.getLocation(), Sound.ENTITY_RAVAGER_STUNNED, 1.2f, 0.5f);
        aviso(t.getLocation(), 1.5, Color.fromRGB(160, 0, 0));
        despues(14, () -> {
            if (tragado != null || !t.isOnline() || t.isDead() || t.getLocation().distance(cuerpo.getLocation()) > 3.8) return;
            tragado = t;
            tragadoHasta = plugin.getServer().getCurrentTick() + 90;
            paraSoltar = cuerpo.getAttribute(Attribute.MAX_HEALTH).getValue() * 0.06;
            cuerpo.addPassenger(t);
            Util.titulo(t, "<dark_red><bold>¡TE TRAGÓ!", "<gray>Tus aliados tienen que pegarle", 0, 2500, 300);
            for (Player p : arena.participantes()) {
                if (p != t) Util.barra(p, "<red>¡Pegale al Glotón para que suelte a " + t.getName() + "!");
            }
        });
    }

    @Override
    public void alRecibir(EntityDamageByEntityEvent e, Player atacante) {
        if (tragado == null || atacante == tragado || !esCuerpo(e.getEntity())) return;
        paraSoltar -= e.getDamage();
        if (paraSoltar <= 0) soltar();
    }

    private void soltar() {
        Player t = tragado;
        tragado = null;
        cuerpo.removePassenger(t);
        modo.combate().aturdir(cuerpo, 60);
        Util.titulo(t, "", "<green>Te soltó", 0, 1200, 300);
        cuerpo.getWorld().playSound(cuerpo.getLocation(), Sound.ENTITY_RAVAGER_HURT, 1.2f, 0.5f);
    }

    private void escupir() {
        Player t = tragado;
        tragado = null;
        cuerpo.removePassenger(t);
        Vector v = cuerpo.getLocation().getDirection().setY(0);
        if (v.lengthSquared() < 0.01) v = new Vector(1, 0, 0);
        t.setVelocity(v.normalize().multiply(1.6).setY(0.7));
        t.damage(18 * multDanio, cuerpo);
        cuerpo.getWorld().playSound(cuerpo.getLocation(), Sound.ENTITY_PLAYER_BURP, 1.5f, 0.5f);
    }

    @Override
    protected void limpiarExtra() {
        if (tragado != null && cuerpo != null) cuerpo.removePassenger(tragado);
        tragado = null;
    }
}
