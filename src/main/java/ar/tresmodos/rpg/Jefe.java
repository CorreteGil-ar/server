package ar.tresmodos.rpg;

import ar.tresmodos.Claves;
import ar.tresmodos.TresModos;
import ar.tresmodos.Util;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Base de los jefes: cuerpo, barra, fases y ataques anunciados. Cada ataque tiene un gesto previo
 * (partículas y sonido) que deja tiempo para rodar o hacer parry: el daño se aplica con
 * {@code p.damage(x, cuerpo)}, así el parry, la voltereta y los escudos del combate funcionan solos.
 */
public abstract class Jefe {
    protected final TresModos plugin;
    protected final ModoRpg modo;
    protected Jefes.Arena arena;
    protected LivingEntity cuerpo;
    protected BossBar barra;
    protected int fase = 1;
    protected int proximo;
    protected double multDanio = 1;
    protected final Random azar = new Random();

    protected Jefe(TresModos plugin, ModoRpg modo) {
        this.plugin = plugin;
        this.modo = modo;
    }

    public abstract String id();

    public abstract String nombre();

    /** Nombre del alma que deja (y del objeto que se canjea en el altar). */
    public abstract String alma();

    protected abstract double vidaBase();

    public abstract long almas();

    protected abstract LivingEntity crearCuerpo(Location centro);

    /** Inteligencia del jefe, cada 2 ticks. */
    protected abstract void pensar(int ahora);

    protected BossBar.Color colorBarra() {
        return BossBar.Color.RED;
    }

    // ------------------------------------------------------------------ ciclo de vida

    public void aparecer(Jefes.Arena a, int jugadores, int ciclo) {
        this.arena = a;
        fase = 1;
        multDanio = Math.pow(1.5, ciclo);
        proximo = Bukkit.getCurrentTick() + 60;
        cuerpo = crearCuerpo(a.centro.clone());
        double vida = vidaBase() * (1 + 0.3 * Math.max(0, jugadores - 1)) * Math.pow(1.5, ciclo);
        TipoEnemigo.base(cuerpo, Attribute.MAX_HEALTH, vida);
        cuerpo.setHealth(vida);
        TipoEnemigo.base(cuerpo, Attribute.KNOCKBACK_RESISTANCE, 1.0);
        TipoEnemigo.base(cuerpo, Attribute.FOLLOW_RANGE, 48);
        cuerpo.customName(Util.mm("<dark_red><bold>" + nombre()));
        cuerpo.setCustomNameVisible(false);
        cuerpo.setRemoveWhenFarAway(false);
        cuerpo.setPersistent(false);
        cuerpo.getPersistentDataContainer().set(Claves.JEFE, PersistentDataType.STRING, id());
        if (barra == null) barra = BossBar.bossBar(Util.mm("<dark_red>" + nombre()), 1f, colorBarra(), BossBar.Overlay.NOTCHED_10);
        barra.name(Util.mm("<dark_red>" + nombre()));
        barra.color(colorBarra());
        barra.progress(1f);
    }

    /** Suma vida si entra otro jugador durante el muro de niebla (mantiene el porcentaje). */
    public void escalar(int jugadores, int ciclo) {
        if (!vivo()) return;
        double max = cuerpo.getAttribute(Attribute.MAX_HEALTH).getValue();
        double nuevo = vidaBase() * (1 + 0.3 * Math.max(0, jugadores - 1)) * Math.pow(1.5, ciclo);
        if (nuevo <= max) return;
        double frac = cuerpo.getHealth() / max;
        TipoEnemigo.base(cuerpo, Attribute.MAX_HEALTH, nuevo);
        cuerpo.setHealth(nuevo * frac);
    }

    public void quitar() {
        if (cuerpo != null && cuerpo.isValid()) cuerpo.remove();
        cuerpo = null;
        limpiarExtra();
        if (barra != null) for (Player p : Bukkit.getOnlinePlayers()) p.hideBossBar(barra);
    }

    /** Invocaciones, hilos, tentáculos: lo que cada jefe tenga que sacar al terminar. */
    protected void limpiarExtra() {}

    public boolean vivo() {
        return cuerpo != null && cuerpo.isValid() && !cuerpo.isDead();
    }

    public boolean esCuerpo(Entity e) {
        return cuerpo != null && cuerpo.getUniqueId().equals(e.getUniqueId());
    }

    /** Partes del jefe que no son el cuerpo (copias, crías): no dan almas al morir. */
    public boolean esParte(Entity e) {
        return false;
    }

    public void tick(int ahora) {
        if (!vivo()) return;
        double max = cuerpo.getAttribute(Attribute.MAX_HEALTH).getValue();
        float prog = (float) Math.max(0, Math.min(1, cuerpo.getHealth() / max));
        barra.progress(prog);
        for (Player p : arena.participantes()) p.showBossBar(barra);
        if (cuerpo instanceof Mob m && !(m.getTarget() instanceof Player t && arena.esParticipante(t))) {
            Player obj = objetivo();
            if (obj != null) m.setTarget(obj);
        }
        if (modo.combate().aturdido(cuerpo)) return;
        pensar(ahora);
    }

    /** Recibe un golpe de un jugador (para hilos, copias y agarres). */
    public void alRecibir(EntityDamageByEntityEvent e, Player atacante) {}

    /** Golpea a un jugador (para aplicar estados propios). */
    public void alGolpear(Player victima, EntityDamageByEntityEvent e) {}

    public BossBar barra() {
        return barra;
    }

    // ------------------------------------------------------------------ ayudantes

    protected double vidaFrac() {
        return cuerpo.getHealth() / cuerpo.getAttribute(Attribute.MAX_HEALTH).getValue();
    }

    protected Player objetivo() {
        Player mejor = null;
        double md = Double.MAX_VALUE;
        for (Player p : arena.participantes()) {
            double d = p.getLocation().distanceSquared(cuerpo.getLocation());
            if (d < md) {
                md = d;
                mejor = p;
            }
        }
        return mejor;
    }

    protected Player alAzar() {
        List<Player> l = arena.participantes();
        return l.isEmpty() ? null : l.get(azar.nextInt(l.size()));
    }

    protected void despues(int ticks, Runnable r) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (vivo()) r.run();
        }, ticks);
    }

    /** Círculo de aviso en el piso. */
    protected static void aviso(Location c, double r, Color color) {
        World w = c.getWorld();
        int puntos = (int) Math.max(16, r * 8);
        Particle.DustOptions op = new Particle.DustOptions(color, 1.5f);
        for (int i = 0; i < puntos; i++) {
            double a = i * Math.PI * 2 / puntos;
            w.spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * r, 0.15, Math.sin(a) * r), 1, 0, 0, 0, 0, op);
        }
    }

    /** Daña a los participantes dentro del radio (con empuje opcional). */
    protected void golpeArea(Location c, double r, double danio, double empuje) {
        for (Player p : arena.participantes()) {
            if (p.getLocation().distanceSquared(c) > r * r) continue;
            p.damage(danio * multDanio, cuerpo);
            if (empuje > 0) {
                Vector v = p.getLocation().toVector().subtract(c.toVector()).setY(0);
                if (v.lengthSquared() > 0.01) p.setVelocity(v.normalize().multiply(empuje).setY(0.45));
            }
        }
    }

    /** Golpe en un cono al frente del jefe. */
    protected void golpeCono(double alcance, double apertura, double danio) {
        Vector mira = cuerpo.getLocation().getDirection().setY(0).normalize();
        for (Player p : arena.participantes()) {
            Vector hacia = p.getLocation().toVector().subtract(cuerpo.getLocation().toVector()).setY(0);
            if (hacia.length() > alcance) continue;
            if (hacia.lengthSquared() > 0.25 && hacia.normalize().dot(mira) < apertura) continue;
            p.damage(danio * multDanio, cuerpo);
        }
    }

    protected void mirarA(Player p) {
        Location l = cuerpo.getLocation();
        l.setDirection(p.getLocation().toVector().subtract(l.toVector()));
        cuerpo.setRotation(l.getYaw(), 0);
    }

    protected List<Player> enRadio(double r) {
        List<Player> out = new ArrayList<>();
        for (Player p : arena.participantes()) if (p.getLocation().distanceSquared(cuerpo.getLocation()) <= r * r) out.add(p);
        return out;
    }

    protected void cambiarFase(int nueva, String texto) {
        fase = nueva;
        for (Player p : arena.participantes()) Util.titulo(p, "", texto, 200, 1600, 400);
    }
}
