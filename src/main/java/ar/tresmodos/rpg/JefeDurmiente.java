package ar.tresmodos.rpg;

import ar.tresmodos.TresModos;
import ar.tresmodos.Util;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * El Durmiente (Templo del Abismo). F1: tentáculos que lo protegen (romperlos abre su guardia) y
 * golpean desde abajo. F2 (60 %): ráfaga de alas que empuja al agua. F3 (30 %): levanta vuelo y su
 * mirada da locura a quien lo vea; hay que esconderse detrás de los monolitos.
 */
public class JefeDurmiente extends Jefe {
    private final List<Jefes.Rompible> tentaculos = new ArrayList<>();
    private int proximosTentaculos, proximaRafaga, cambioVuelo;
    private boolean volando;

    public JefeDurmiente(TresModos plugin, ModoRpg modo) {
        super(plugin, modo);
    }

    @Override public String id() { return "durmiente"; }
    @Override public String nombre() { return "El Durmiente"; }
    @Override public String alma() { return "Alma del Durmiente"; }
    @Override protected double vidaBase() { return 2500; }
    @Override public long almas() { return 60000; }
    @Override protected BossBar.Color colorBarra() { return BossBar.Color.GREEN; }

    @Override
    protected LivingEntity crearCuerpo(Location c) {
        volando = false;
        proximosTentaculos = 0;
        return c.getWorld().spawn(c, Warden.class, CreatureSpawnEvent.SpawnReason.CUSTOM, w -> {
            TipoEnemigo.base(w, Attribute.SCALE, 1.6);
            TipoEnemigo.base(w, Attribute.ATTACK_DAMAGE, 16);
            TipoEnemigo.base(w, Attribute.MOVEMENT_SPEED, 0.28);
        });
    }

    @Override
    protected void pensar(int ahora) {
        if (cuerpo instanceof Warden w) for (Player p : arena.participantes()) w.setAnger(p, 150);
        if (fase == 1 && vidaFrac() < 0.6) {
            cambiarFase(2, "<dark_aqua>Las alas se despliegan");
            proximaRafaga = ahora + 40;
        }
        if (fase == 2 && vidaFrac() < 0.3) {
            cambiarFase(3, "<dark_purple>No lo mires");
            cambioVuelo = ahora;
        }
        if (ahora >= proximosTentaculos && tentaculos.stream().noneMatch(Jefes.Rompible::vivo)) {
            proximosTentaculos = ahora + 600;
            brotar();
        }
        if (fase >= 2 && ahora >= proximaRafaga && !volando) {
            proximaRafaga = ahora + 220;
            rafaga();
        }
        if (fase == 3 && ahora >= cambioVuelo) {
            cambioVuelo = ahora + 300;
            if (volando) aterrizar();
            else despegar();
        }
        if (volando) {
            cuerpo.teleport(arena.centro.clone().add(0, 9, 0));
            cuerpo.setVelocity(new Vector());
            if ((ahora / 2) % 10 == 0) mirada();
            return;
        }
        if (ahora < proximo) return;
        proximo = ahora + 100;
        // Golpes desde abajo: colmillos bajo cada jugador, uno por tentáculo vivo (mínimo uno).
        long vivos = Math.max(1, tentaculos.stream().filter(Jefes.Rompible::vivo).count());
        for (Player p : arena.participantes()) {
            if (azar.nextDouble() > 0.4 + 0.2 * vivos) continue;
            Location l = p.getLocation();
            aviso(l, 1.4, Color.fromRGB(40, 220, 160));
            despues(20, () -> l.getWorld().spawn(l, EvokerFangs.class, f -> f.setOwner(cuerpo)));
        }
    }

    private void brotar() {
        Jefes jefes = modo.jefes();
        for (int i = 0; i < 3; i++) {
            double a = i * Math.PI * 2 / 3 + azar.nextDouble();
            Location l = arena.centro.clone().add(Math.cos(a) * 10, 0, Math.sin(a) * 10);
            tentaculos.add(jefes.rompible(l, new ItemStack(Material.TWISTING_VINES), 1.2f, 1.0f, 3.5f,
                    5 + arena.participantes().size(), r -> {
                        r.lugar().getWorld().spawnParticle(Particle.SQUID_INK, r.lugar().clone().add(0, 1.5, 0), 30, 0.3, 1, 0.3, 0.05);
                        if (tentaculos.stream().noneMatch(Jefes.Rompible::vivo)) {
                            tentaculos.clear();
                            modo.combate().aturdir(cuerpo, 140);
                            for (Player p : arena.participantes()) Util.titulo(p, "", "<yellow>Su guardia se abrió", 0, 1500, 300);
                        }
                    }));
        }
        for (Player p : arena.participantes()) {
            Util.barra(p, "<dark_aqua>Brotan tentáculos: <white>rompelos para abrir su guardia");
            p.playSound(p, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 0.6f, 0.6f);
        }
    }

    private void rafaga() {
        Location c = cuerpo.getLocation();
        c.getWorld().playSound(c, Sound.ENTITY_ENDER_DRAGON_FLAP, 2f, 0.4f);
        aviso(c, 6, Color.fromRGB(180, 230, 255));
        despues(30, () -> {
            Location l = cuerpo.getLocation();
            l.getWorld().playSound(l, Sound.ENTITY_ENDER_DRAGON_GROWL, 1.5f, 0.5f);
            l.getWorld().spawnParticle(Particle.GUST_EMITTER_LARGE, l.clone().add(0, 1, 0), 1);
            for (Player p : arena.participantes()) {
                Vector v = p.getLocation().toVector().subtract(l.toVector()).setY(0);
                if (v.lengthSquared() < 0.01) v = new Vector(1, 0, 0);
                double d = v.length();
                if (d > 18) continue;
                p.setVelocity(v.normalize().multiply(2.2 - d * 0.06).setY(0.5));
                p.damage(6 * multDanio, cuerpo);
            }
        });
    }

    private void despegar() {
        volando = true;
        cuerpo.setGravity(false);
        if (cuerpo instanceof Mob m) m.setAware(false);
        for (Player p : arena.participantes()) {
            Util.titulo(p, "", "<dark_purple>Escondete detrás de los monolitos", 0, 2000, 300);
            p.playSound(p, Sound.ENTITY_ENDER_DRAGON_FLAP, 1.5f, 0.3f);
        }
    }

    private void aterrizar() {
        volando = false;
        cuerpo.setGravity(true);
        if (cuerpo instanceof Mob m) m.setAware(true);
        cuerpo.teleport(arena.centro.clone());
        golpeArea(arena.centro, 5, 14, 1.0);
        arena.centro.getWorld().spawnParticle(Particle.EXPLOSION, arena.centro, 4, 2, 0.2, 2, 0);
    }

    /** Quien tiene línea de vista a sus ojos acumula locura. */
    private void mirada() {
        Location ojo = cuerpo.getEyeLocation();
        for (Player p : arena.participantes()) {
            Vector hacia = p.getEyeLocation().toVector().subtract(ojo.toVector());
            RayTraceResult r = ojo.getWorld().rayTraceBlocks(ojo, hacia.clone().normalize(), hacia.length(),
                    FluidCollisionMode.NEVER, true);
            if (r != null) continue;
            modo.estados().acumular(p, Estado.LOCURA, 22, null);
            for (double s = 0; s < hacia.length(); s += 1) {
                ojo.getWorld().spawnParticle(Particle.DUST, ojo.clone().add(hacia.clone().normalize().multiply(s)), 1, 0, 0, 0, 0,
                        new Particle.DustOptions(Color.fromRGB(60, 255, 170), 1f));
            }
        }
    }

    @Override
    public void alRecibir(EntityDamageByEntityEvent e, Player atacante) {
        // Con tentáculos vivos, su guardia aguanta la mitad.
        if (esCuerpo(e.getEntity()) && tentaculos.stream().anyMatch(Jefes.Rompible::vivo)) e.setDamage(e.getDamage() * 0.5);
    }

    @Override
    public void alGolpear(Player v, EntityDamageByEntityEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.SONIC_BOOM) e.setDamage(12 * multDanio);
        modo.estados().acumular(v, Estado.LOCURA, 15, null);
    }

    @Override
    protected void limpiarExtra() {
        for (Jefes.Rompible t : tentaculos) modo.jefes().olvidarRompible(t);
        tentaculos.clear();
        volando = false;
    }
}
