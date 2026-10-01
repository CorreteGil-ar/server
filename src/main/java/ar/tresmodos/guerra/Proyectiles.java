package ar.tresmodos.guerra;

import ar.tresmodos.Armas;
import ar.tresmodos.Util;
import ar.tresmodos.TresModos;
import ar.tresmodos.mundo.ValleDeHierro;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Proyectiles pesados de Guerra (obuses, cohetes, misiles, bombas) simulados por el plugin: avanzan
 * por ticks con caída, chocan contra bloques, vehículos (caja orientada) e infantería, y explotan.
 * Las explosiones rompen bloques (se restauran al terminar la partida) salvo en las bases.
 */
public class Proyectiles {

    /** Datos de cada munición: daño a vehículos, a infantería, radio de la explosión y si rompe bloques. */
    public enum Municion {
        AP("Obús AP", 120, 30, 0, false, 6.0, 0.012, Particle.CRIT),
        HE("Obús HE", 60, 30, 4, true, 4.0, 0.02, Particle.SMOKE),
        CANON_25("Cañón de 25 mm", 12, 6, 1.5, false, 4.5, 0.01, Particle.CRIT),
        FLAK("Antiaéreo de 35 mm", 8, 4, 2, false, 5.0, 0.008, Particle.SMOKE),
        RPG("RPG-7", 100, 18, 3, true, 2.2, 0.012, Particle.CAMPFIRE_COSY_SMOKE),
        AT4("AT4", 130, 18, 2.5, true, 2.6, 0.008, Particle.CAMPFIRE_COSY_SMOKE),
        JAVELIN("Javelin", 160, 18, 3, true, 2.0, 0, Particle.CAMPFIRE_COSY_SMOKE),
        STINGER("Stinger", 100, 10, 3, false, 3.0, 0, Particle.CAMPFIRE_COSY_SMOKE),
        TOW("TOW", 120, 20, 2.5, true, 2.0, 0, Particle.CAMPFIRE_COSY_SMOKE),
        COHETE("Cohetes", 40, 14, 3, true, 3.2, 0.01, Particle.CAMPFIRE_COSY_SMOKE),
        MISIL_HELI("Misil guiado", 100, 18, 3, true, 2.6, 0, Particle.CAMPFIRE_COSY_SMOKE),
        MISIL_AIRE("Misil aire-aire", 100, 10, 3, false, 4.0, 0, Particle.CAMPFIRE_COSY_SMOKE),
        BOMBA("Bomba", 150, 40, 7, true, 0, 0.05, Particle.SMOKE);

        public final String nombre;
        public final double vehiculo, infanteria, radio, velocidad, gravedad;
        public final boolean rompe;
        public final Particle estela;

        Municion(String nombre, double vehiculo, double infanteria, double radio, boolean rompe, double velocidad,
                 double gravedad, Particle estela) {
            this.nombre = nombre;
            this.vehiculo = vehiculo;
            this.infanteria = infanteria;
            this.radio = radio;
            this.rompe = rompe;
            this.velocidad = velocidad;
            this.gravedad = gravedad;
            this.estela = estela;
        }

        public boolean guiado() {
            return this == JAVELIN || this == STINGER || this == TOW || this == MISIL_HELI || this == MISIL_AIRE;
        }
    }

    final class Bala {
        final Municion m;
        final Player autor;
        final ModoGuerra.Equipo equipo;
        final Vehiculo propio;
        Vector pos, vel;
        Vehiculo objetivo;
        int vida = 200, fase;
        double alturaCrucero;

        Bala(Municion m, Player autor, ModoGuerra.Equipo equipo, Vehiculo propio, Vector pos, Vector vel) {
            this.m = m;
            this.autor = autor;
            this.equipo = equipo;
            this.propio = propio;
            this.pos = pos;
            this.vel = vel;
        }
    }

    private final TresModos plugin;
    private final ModoGuerra modo;
    private final List<Bala> balas = new ArrayList<>();
    private final Random rnd = new Random();

    Proyectiles(TresModos plugin, ModoGuerra modo) {
        this.plugin = plugin;
        this.modo = modo;
    }

    private Vehiculos vehiculos() {
        return modo.arsenal().vehiculos();
    }

    /** Lanza un proyectil desde un punto en una dirección (normalizada adentro). */
    public Bala lanzar(Municion m, Player autor, Vehiculo propio, Vector desde, Vector dir, Vehiculo objetivo) {
        ModoGuerra.Equipo eq = propio != null ? propio.equipo : autor != null ? modo.equipo(autor) : null;
        Vector v = dir.clone().normalize().multiply(m.velocidad);
        Bala b = new Bala(m, autor, eq, propio, desde.clone(), v);
        b.objetivo = objetivo;
        b.alturaCrucero = desde.getY() + 30;
        balas.add(b);
        World w = modo.mundo();
        Location l = desde.toLocation(w);
        switch (m) {
            case AP, HE -> {
                Util.sonido(l, "vehiculo.canon", 6f, 1.0f);
                w.spawnParticle(Particle.EXPLOSION, l, 1);
                w.spawnParticle(Particle.LARGE_SMOKE, l, 12, 0.3, 0.3, 0.3, 0.05);
            }
            case CANON_25, FLAK -> Util.sonido(l, "vehiculo.ametralladora", 4f, 0.7f);
            case BOMBA -> w.playSound(l, Sound.BLOCK_PISTON_EXTEND, 1f, 0.5f);
            default -> Util.sonido(l, "arma.lanzacohetes", 3.5f, 0.9f);
        }
        return b;
    }

    public Bala lanzarConVelocidad(Municion m, Player autor, Vehiculo propio, Vector desde, Vector velocidad) {
        Bala b = lanzar(m, autor, propio, desde, velocidad.lengthSquared() > 1e-6 ? velocidad : new Vector(0, -1, 0), null);
        b.vel = velocidad.clone();
        return b;
    }

    void tick() {
        World w = modo.mundo();
        Iterator<Bala> it = balas.iterator();
        while (it.hasNext()) {
            Bala b = it.next();
            if (--b.vida <= 0 || b.pos.getY() < ValleDeHierro.YMIN) {
                it.remove();
                continue;
            }
            guiar(b);
            b.vel.setY(b.vel.getY() - b.m.gravedad);
            double largo = b.vel.length();
            if (largo < 1e-6) continue;
            Vector dir = b.vel.clone().normalize();
            // Espoleta de proximidad (antiaéreo y misiles contra aviones).
            if (b.m == Municion.FLAK || b.m == Municion.STINGER || b.m == Municion.MISIL_AIRE) {
                Vehiculo cerca = vehiculos().aereoCerca(b.pos, b.m == Municion.FLAK ? 3.0 : 4.0, b.equipo);
                if (cerca != null) {
                    impactar(b, b.pos.clone(), cerca, null, dir);
                    it.remove();
                    continue;
                }
            }
            double mejor = largo;
            Vehiculo hv = null;
            Player hp = null;
            Vector punto = null;
            RayTraceResult rb = w.rayTraceBlocks(b.pos.toLocation(w), dir, largo, FluidCollisionMode.NEVER, true);
            if (rb != null) {
                mejor = rb.getHitPosition().distance(b.pos);
                punto = rb.getHitPosition();
            }
            Vehiculos.Golpe g = vehiculos().rayo(b.pos, dir, mejor, b.propio);
            if (g != null && g.distancia() < mejor) {
                mejor = g.distancia();
                hv = g.vehiculo();
                punto = b.pos.clone().add(dir.clone().multiply(mejor));
            }
            RayTraceResult re = w.rayTraceEntities(b.pos.toLocation(w), dir, mejor, 0.35,
                    e -> e instanceof Player o && o != b.autor && o.getGameMode() == GameMode.ADVENTURE && !vehiculos().protegido(o)
                            && (b.equipo == null || modo.equipo(o) != b.equipo));
            if (re != null && re.getHitEntity() instanceof Player o) {
                hp = o;
                hv = null;
                punto = re.getHitPosition();
            }
            // Estela.
            for (double s = 0; s < Math.min(largo, mejor); s += 0.8) {
                Location l = b.pos.clone().add(dir.clone().multiply(s)).toLocation(w);
                w.spawnParticle(b.m.estela, l, 1, 0, 0, 0, 0.005);
                if (b.m.guiado() || b.m == Municion.RPG || b.m == Municion.AT4 || b.m == Municion.COHETE) {
                    w.spawnParticle(Particle.FLAME, l, 1, 0, 0, 0, 0.002);
                }
            }
            if (punto != null) {
                impactar(b, punto, hv, hp, dir);
                it.remove();
                continue;
            }
            b.pos.add(b.vel);
        }
    }

    /** Guía de los misiles. El Javelin sube y cae sobre el techo; el TOW sigue la mira del artillero. */
    private void guiar(Bala b) {
        if (!b.m.guiado()) return;
        Vector destino = null;
        if (b.m == Municion.TOW) {
            if (b.autor != null && b.autor.isOnline()) {
                Location ojo = b.autor.getEyeLocation();
                RayTraceResult r = ojo.getWorld().rayTraceBlocks(ojo, ojo.getDirection(), 200, FluidCollisionMode.NEVER, true);
                destino = r != null ? r.getHitPosition() : ojo.toVector().add(ojo.getDirection().multiply(200));
            }
        } else if (b.objetivo != null && b.objetivo.vivo()) {
            destino = new Vector(b.objetivo.x, b.objetivo.y + b.objetivo.tipo.alto * 0.5, b.objetivo.z);
            if (b.m == Municion.JAVELIN) {
                double horiz = Math.hypot(destino.getX() - b.pos.getX(), destino.getZ() - b.pos.getZ());
                if (b.fase == 0 && horiz > 12) destino = new Vector(destino.getX(), Math.max(b.alturaCrucero, destino.getY() + 25), destino.getZ());
                else b.fase = 1;
            }
        }
        if (destino == null) return;
        Vector quiero = destino.subtract(b.pos);
        if (quiero.lengthSquared() < 1e-6) return;
        quiero.normalize();
        Vector actual = b.vel.clone().normalize();
        double giro = b.m == Municion.TOW ? 0.18 : b.m == Municion.JAVELIN ? 0.25 : 0.2;
        Vector nuevo = actual.multiply(1 - giro).add(quiero.multiply(giro)).normalize();
        b.vel = nuevo.multiply(b.m.velocidad);
    }

    private void impactar(Bala b, Vector punto, Vehiculo hv, Player hp, Vector dir) {
        String arma = b.m.nombre;
        boolean arriba = b.m == Municion.JAVELIN && b.fase == 1 || b.m == Municion.BOMBA;
        if (hv != null) {
            double danio = b.m.vehiculo;
            // Antiaéreo y misiles aire-aire casi no le hacen a los de tierra; las armas antitanque, poco a los aviones.
            if ((b.m == Municion.FLAK) && !hv.esAereo()) danio = 3;
            if ((b.m == Municion.STINGER || b.m == Municion.MISIL_AIRE) && !hv.esAereo()) danio *= 0.3;
            vehiculos().daniar(hv, danio, b.autor, arma, dir, arriba, true);
        }
        if (b.m.radio > 0) {
            explotar(punto.toLocation(modo.mundo()), b.m.radio, b.m.infanteria,
                    hv == null ? b.m.vehiculo * 0.35 : 0, b.autor, arma, b.m.rompe, hv);
        } else {
            if (hp != null) Armas.danioDirecto(hp, b.m.infanteria, b.autor, arma, false);
            modo.mundo().spawnParticle(Particle.EXPLOSION, punto.toLocation(modo.mundo()), 1);
            Util.sonido(punto.toLocation(modo.mundo()), "explosion.chica", 2f, 1.3f);
        }
    }

    /**
     * Explosión: daño a la infantería con caída por distancia, daño a los vehículos cercanos
     * (salvo el que ya recibió el golpe directo) y rotura de bloques fuera de las bases.
     */
    public void explotar(Location l, double radio, double danioInf, double danioVeh, Player autor, String arma,
                         boolean rompe, Vehiculo excluir) {
        World w = l.getWorld();
        w.spawnParticle(radio >= 4 ? Particle.EXPLOSION_EMITTER : Particle.EXPLOSION, l, radio >= 4 ? 1 : 3, radio * 0.2, 0.2, radio * 0.2, 0);
        w.spawnParticle(Particle.LARGE_SMOKE, l, (int) (10 * radio), radio * 0.4, radio * 0.3, radio * 0.4, 0.04);
        w.spawnParticle(Particle.FLAME, l, (int) (6 * radio), radio * 0.3, radio * 0.2, radio * 0.3, 0.05);
        Util.sonido(l, radio >= 4 ? "explosion.grande" : "explosion.chica", (float) Math.min(8, 2 + radio * 0.8), 0.9f + (float) Math.random() * 0.2f);
        ModoGuerra.Equipo eq = autor != null ? modo.equipo(autor) : null;
        for (Player p : w.getPlayers()) {
            if (p.getGameMode() != GameMode.ADVENTURE || vehiculos().protegido(p)) continue;
            double d = p.getLocation().add(0, 0.9, 0).distance(l);
            if (d > radio) continue;
            if (p != autor && eq != null && modo.equipo(p) == eq) continue;
            double f = 1 - 0.7 * (d / radio);
            Armas.danioDirecto(p, danioInf * f, autor, arma, false);
            Vector emp = p.getLocation().toVector().subtract(l.toVector());
            if (emp.lengthSquared() > 0.01) p.setVelocity(p.getVelocity().add(emp.normalize().multiply(0.6 * f).setY(0.35 * f)));
        }
        if (danioVeh > 0) {
            for (Vehiculo v : vehiculos().todos()) {
                if (v == excluir || !v.vivo()) continue;
                double d = new Vector(v.x, v.y + v.tipo.alto / 2, v.z).distance(l.toVector()) - Math.max(v.tipo.ancho, v.tipo.largo) / 2;
                if (d > radio) continue;
                double f = d <= 0 ? 1 : 1 - d / radio;
                vehiculos().daniar(v, danioVeh * f, autor, arma, new Vector(v.x, v.y, v.z).subtract(l.toVector()), false, false);
            }
        }
        if (rompe) romper(l, radio * 0.75);
    }

    private void romper(Location c, double r) {
        if (Math.abs(c.getX()) > 168) return; // las bases no se rompen
        World w = c.getWorld();
        int ri = (int) Math.ceil(r);
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dy = -ri; dy <= ri; dy++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    if (d > r) continue;
                    Block b = w.getBlockAt(c.getBlockX() + dx, c.getBlockY() + dy, c.getBlockZ() + dz);
                    Material m = b.getType();
                    if (m.isAir() || m == Material.BEDROCK || m == Material.WATER || m == Material.LAVA || m == Material.BARRIER) continue;
                    if (b.getY() < ValleDeHierro.SUELO - 4) continue;
                    if (m.getBlastResistance() > 20) continue;
                    if (rnd.nextDouble() > Math.pow(1 - d / (r + 0.01), 0.6)) continue;
                    modo.registrarRotura(b);
                    b.setType(Material.AIR, false);
                }
            }
        }
        w.spawnParticle(Particle.BLOCK, c, 40, r * 0.4, r * 0.3, r * 0.4, 0, Material.COBBLESTONE.createBlockData());
    }

    void limpiar() {
        balas.clear();
    }

    static Color colorEquipo(ModoGuerra.Equipo e) {
        return e == null ? Color.WHITE : e.tinte;
    }

    int cantidad() {
        return balas.size();
    }

    static int ahora() {
        return Bukkit.getCurrentTick();
    }
}
