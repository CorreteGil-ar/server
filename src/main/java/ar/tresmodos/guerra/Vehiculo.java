package ar.tresmodos.guerra;

import ar.tresmodos.guerra.TipoVehiculo.Movimiento;
import ar.tresmodos.guerra.TipoVehiculo.Pieza;
import ar.tresmodos.guerra.TipoVehiculo.Rol;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Un vehículo en el mundo: posición, rumbo, velocidad, vida, módulos y sus piezas visibles. La
 * física la mueve {@link Vehiculos}; acá están el estado, la geometría (caja orientada) y el dibujo.
 *
 * Marco local: z hacia adelante, y hacia arriba, x hacia la izquierda. El rumbo usa la convención de
 * Minecraft (yaw 0 = sur, −90 = este) y el cabeceo es positivo con la trompa arriba.
 */
public class Vehiculo {
    public static final NamespacedKey CLAVE = new NamespacedKey("tresmodos", "vehiculo");

    public final int id;
    public final TipoVehiculo tipo;
    public final ModoGuerra.Equipo equipo;
    public final Location origen;
    public final float rumboOrigen;

    // Estado físico.
    public double x, y, z;
    public float rumbo, cabeceo, alabeo;
    /** Velocidad hacia adelante (b/s) para los de tierra y el avión. */
    public double vel;
    /** Velocidad vertical (b/s) y lateral del helicóptero. */
    public double velY, velLado;
    public double potencia;
    public boolean enTierra = true;
    // Torreta y cañón (relativos al casco).
    public float torreta, canon;
    public float rotor;

    // Vida y módulos.
    public double vida;
    public int orugasHasta, canonHasta, recargaHasta, recargaSecHasta, ultimoDisparoAuto;
    public boolean motorDanado, incendio, extintorUsado, rotorCola;
    public boolean municionHE;
    public boolean secundarioBombas;
    public int misiles, bombas, cohetes;
    public int sinUsoDesde;
    public UUID ultimoAtacante;
    public String ultimaArma = "";

    public final UUID[] ocupantes;
    private final List<ItemDisplay> piezas = new ArrayList<>();
    private final List<ItemDisplay> asientos = new ArrayList<>();
    private boolean vivo = true;

    Vehiculo(int id, TipoVehiculo tipo, ModoGuerra.Equipo equipo, Location origen) {
        this.id = id;
        this.tipo = tipo;
        this.equipo = equipo;
        this.origen = origen.clone();
        this.rumboOrigen = origen.getYaw();
        this.ocupantes = new UUID[tipo.asientos.size()];
        this.x = origen.getX();
        this.y = origen.getY();
        this.z = origen.getZ();
        this.rumbo = origen.getYaw();
        this.vida = tipo.vida;
        rearmar();
    }

    public void rearmar() {
        misiles = tipo == TipoVehiculo.HELICOPTERO ? 4 : tipo == TipoVehiculo.AVION ? 2 : tipo == TipoVehiculo.VCI ? 2 : 0;
        bombas = tipo == TipoVehiculo.AVION ? 2 : 0;
        cohetes = tipo == TipoVehiculo.HELICOPTERO ? 16 : 0;
    }

    public boolean vivo() {
        return vivo;
    }

    public World mundo() {
        return origen.getWorld();
    }

    public Location ubicacion() {
        return new Location(mundo(), x, y, z, rumbo, -cabeceo);
    }

    // ------------------------------------------------------------------ geometría

    /** Rotación del casco (rumbo, cabeceo y alabeo). */
    public Quaternionf rotacion() {
        return new Quaternionf().rotateY((float) Math.toRadians(-rumbo)).rotateX((float) Math.toRadians(-cabeceo))
                .rotateZ((float) Math.toRadians(alabeo));
    }

    /** Punto del marco local en el mundo. */
    public Vector mundo(double lx, double ly, double lz) {
        Vector3f v = rotacion().transform(new Vector3f((float) lx, (float) ly, (float) lz));
        return new Vector(x + v.x, y + v.y, z + v.z);
    }

    /** Vector del mundo al marco local (relativo al centro de la base). */
    public Vector3f local(Vector p) {
        Quaternionf inv = rotacion().conjugate();
        return inv.transform(new Vector3f((float) (p.getX() - x), (float) (p.getY() - y), (float) (p.getZ() - z)));
    }

    public Vector adelante() {
        Vector3f f = rotacion().transform(new Vector3f(0, 0, 1));
        return new Vector(f.x, f.y, f.z);
    }

    /** Dirección del cañón en el mundo (torreta + elevación). */
    public Vector direccionCanon() {
        Quaternionf q = rotacion().rotateY((float) Math.toRadians(-torreta)).rotateX((float) Math.toRadians(-canon));
        Vector3f f = q.transform(new Vector3f(0, 0, 1));
        return new Vector(f.x, f.y, f.z);
    }

    /** Boca del cañón (para disparar). */
    public Vector bocaCanon() {
        Pieza p = pieza(Rol.CANON);
        Pieza t = pieza(Rol.TORRETA);
        if (p == null || t == null) return mundo(0, tipo.alto * 0.7, tipo.largo / 2 + 0.5);
        Vector3f rel = new Vector3f((float) (p.x() - t.x()), (float) (p.y() - t.y()), (float) (p.z() - t.z()));
        new Quaternionf().rotateY((float) Math.toRadians(-torreta)).transform(rel);
        Vector base = mundo(t.x() + rel.x, t.y() + rel.y, t.z() + rel.z);
        double largo = switch (tipo) {
            case TANQUE -> 4.6;
            case ANTIAEREO -> 2.6;
            case VCI -> 1.8;
            default -> 1.5;
        };
        return base.add(direccionCanon().multiply(largo));
    }

    Pieza pieza(Rol r) {
        for (Pieza p : tipo.piezas) if (p.rol() == r) return p;
        return null;
    }

    /**
     * Intersección de un rayo con la caja del vehículo. Devuelve la distancia o −1. La caja va de
     * −ancho/2 a +ancho/2, de 0 a alto y de −largo/2 a +largo/2 en el marco local.
     */
    public double rayo(Vector origenRayo, Vector dir, double max) {
        Vector3f o = local(origenRayo);
        Vector3f d = rotacion().conjugate().transform(new Vector3f((float) dir.getX(), (float) dir.getY(), (float) dir.getZ()));
        double[] mn = {-tipo.ancho / 2, 0, -tipo.largo / 2}, mx = {tipo.ancho / 2, tipo.alto, tipo.largo / 2};
        double[] or = {o.x, o.y, o.z}, dr = {d.x, d.y, d.z};
        double t0 = 0, t1 = max;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(dr[i]) < 1e-9) {
                if (or[i] < mn[i] || or[i] > mx[i]) return -1;
                continue;
            }
            double a = (mn[i] - or[i]) / dr[i], b = (mx[i] - or[i]) / dr[i];
            if (a > b) {
                double tmp = a;
                a = b;
                b = tmp;
            }
            t0 = Math.max(t0, a);
            t1 = Math.min(t1, b);
            if (t0 > t1) return -1;
        }
        return t0;
    }

    /** true si el punto está dentro de la caja (con margen). */
    public boolean contiene(Vector p, double margen) {
        Vector3f l = local(p);
        return Math.abs(l.x) <= tipo.ancho / 2 + margen && l.y >= -margen && l.y <= tipo.alto + margen
                && Math.abs(l.z) <= tipo.largo / 2 + margen;
    }

    /**
     * Multiplicador por zona de blindaje según desde dónde viene el golpe: frente ×0,4, costado ×1,
     * trasera ×1,6 y techo ×2 (ataque desde arriba). Solo los blindados lo aplican.
     */
    public double zona(Vector direccionGolpe, boolean desdeArriba) {
        if (tipo.blindaje == TipoVehiculo.Blindaje.NINGUNO || tipo.blindaje == TipoVehiculo.Blindaje.LIGERO) return 1;
        if (desdeArriba || direccionGolpe.getY() < -0.65) return 2.0;
        Vector f = adelante().setY(0);
        Vector d = direccionGolpe.clone().setY(0);
        if (f.lengthSquared() < 1e-6 || d.lengthSquared() < 1e-6) return 1;
        double dot = -d.normalize().dot(f.normalize()); // > 0: viene de adelante
        if (dot > 0.55) return 0.4;
        if (dot < -0.55) return 1.6;
        return 1;
    }

    /** "frente", "costado", "trasera" o "techo" (para módulos y avisos). */
    public String zonaNombre(Vector direccionGolpe, boolean desdeArriba) {
        double m = zona(direccionGolpe, desdeArriba);
        if (tipo.blindaje == TipoVehiculo.Blindaje.NINGUNO || tipo.blindaje == TipoVehiculo.Blindaje.LIGERO) {
            Vector f = adelante().setY(0);
            Vector d = direccionGolpe.clone().setY(0);
            if (f.lengthSquared() < 1e-6 || d.lengthSquared() < 1e-6) return "costado";
            double dot = -d.normalize().dot(f.normalize());
            return dot > 0.55 ? "frente" : dot < -0.55 ? "trasera" : "costado";
        }
        return m == 2.0 ? "techo" : m == 0.4 ? "frente" : m == 1.6 ? "trasera" : "costado";
    }

    // ------------------------------------------------------------------ ocupantes

    public int asientoDe(Player p) {
        for (int i = 0; i < ocupantes.length; i++) if (p.getUniqueId().equals(ocupantes[i])) return i;
        return -1;
    }

    public boolean vacio() {
        for (UUID u : ocupantes) if (u != null) return false;
        return true;
    }

    public ItemDisplay asientoEntidad(int i) {
        return i < asientos.size() ? asientos.get(i) : null;
    }

    public boolean esEntidad(UUID u) {
        for (ItemDisplay d : asientos) if (d.getUniqueId().equals(u)) return true;
        for (ItemDisplay d : piezas) if (d.getUniqueId().equals(u)) return true;
        return false;
    }

    // ------------------------------------------------------------------ dibujo

    void crearEntidades() {
        World w = mundo();
        Location l = new Location(w, x, y, z);
        for (Pieza p : tipo.piezas) {
            ItemDisplay d = w.spawn(l, ItemDisplay.class, e -> {
                e.setItemStack(modelo(p.modelo(), equipo));
                e.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.NONE);
                e.setPersistent(false);
                e.setTeleportDuration(2);
                e.setInterpolationDuration(2);
                e.setViewRange(4f);
                e.setShadowRadius(0);
                e.setBrightness(null);
                e.getPersistentDataContainer().set(CLAVE, org.bukkit.persistence.PersistentDataType.INTEGER, id);
            });
            piezas.add(d);
        }
        for (int i = 0; i < tipo.asientos.size(); i++) {
            ItemDisplay a = w.spawn(l, ItemDisplay.class, e -> {
                e.setPersistent(false);
                e.setTeleportDuration(2);
                e.getPersistentDataContainer().set(CLAVE, org.bukkit.persistence.PersistentDataType.INTEGER, id);
            });
            asientos.add(a);
        }
        actualizar();
    }

    /** Ítem con el modelo de la pieza ("tresmodos:vehiculo/<modelo>"), con el color del equipo. */
    static ItemStack modelo(String nombre, ModoGuerra.Equipo equipo) {
        ItemStack it = new ItemStack(Material.CLAY_BALL);
        ItemMeta m = it.getItemMeta();
        m.setItemModel(new NamespacedKey("tresmodos", "vehiculo/" + nombre + (equipo == ModoGuerra.Equipo.ROJO ? "_rojo" : "")));
        it.setItemMeta(m);
        return it;
    }

    /** Mueve las piezas y los asientos a la posición actual (con interpolación del cliente). */
    void actualizar() {
        if (!vivo) return;
        World w = mundo();
        Location base = new Location(w, x, y, z);
        Quaternionf q = rotacion();
        Pieza torre = pieza(Rol.TORRETA);
        for (int i = 0; i < piezas.size(); i++) {
            ItemDisplay d = piezas.get(i);
            Pieza p = tipo.piezas.get(i);
            Vector3f pos = new Vector3f((float) p.x(), (float) p.y(), (float) p.z());
            Quaternionf rot = new Quaternionf(q);
            switch (p.rol()) {
                case TORRETA -> rot.rotateY((float) Math.toRadians(-torreta));
                case CANON -> {
                    if (torre != null) {
                        Vector3f rel = new Vector3f((float) (p.x() - torre.x()), (float) (p.y() - torre.y()), (float) (p.z() - torre.z()));
                        new Quaternionf().rotateY((float) Math.toRadians(-torreta)).transform(rel);
                        pos = new Vector3f((float) torre.x(), (float) torre.y(), (float) torre.z()).add(rel);
                    }
                    rot.rotateY((float) Math.toRadians(-torreta)).rotateX((float) Math.toRadians(-canon));
                }
                case ROTOR -> rot.rotateY((float) Math.toRadians(rotor));
                case ROTOR_COLA -> rot.rotateX((float) Math.toRadians(rotor * 1.7f));
                default -> { }
            }
            // El ItemDisplay dibuja el modelo girado 180° en Y (probado con el cliente 26.3): sin esto
            // todos los vehículos quedaban con la trompa hacia atrás y andaban marcha atrás.
            rot.rotateY((float) Math.PI);
            q.transform(pos);
            float s = (float) p.escala();
            Transformation t = new Transformation(pos, rot, new Vector3f(s, s, s), new Quaternionf());
            if (!t.equals(d.getTransformation())) {
                d.setInterpolationDelay(0);
                d.setTransformation(t);
            }
            if (d.getLocation().distanceSquared(base) > 1e-4) d.teleport(base);
        }
        for (int i = 0; i < asientos.size(); i++) {
            TipoVehiculo.Asiento a = tipo.asientos.get(i);
            Vector v = mundo(a.x(), a.y(), a.z());
            Location l = new Location(w, v.getX(), v.getY(), v.getZ(), rumbo, 0);
            ItemDisplay e = asientos.get(i);
            if (e.getLocation().distanceSquared(l) > 1e-4 || Math.abs(e.getLocation().getYaw() - rumbo) > 0.5) {
                e.teleport(l);
            }
        }
    }

    /** Saca las entidades del mundo (los ocupantes se bajan). */
    void quitar() {
        vivo = false;
        for (ItemDisplay a : asientos) {
            for (var pas : a.getPassengers()) a.removePassenger(pas);
            a.remove();
        }
        for (ItemDisplay d : piezas) d.remove();
        piezas.clear();
        asientos.clear();
        java.util.Arrays.fill(ocupantes, null);
    }

    public List<Display> entidades() {
        List<Display> l = new ArrayList<>(piezas);
        l.addAll(asientos);
        return l;
    }

    public boolean movil(int ahora) {
        return orugasHasta <= ahora;
    }

    public boolean esAereo() {
        return tipo.movimiento == Movimiento.AVION || tipo.movimiento == Movimiento.HELI;
    }
}
