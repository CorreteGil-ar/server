package ar.tresmodos.guerra;

import java.util.List;

/**
 * Los siete vehículos de Guerra. Medidas en bloques (ancho en x, largo en z, alto), velocidad en
 * bloques por segundo y reaparición en segundos. Cada uno se arma con piezas (item displays con
 * modelo del paquete de recursos) y asientos.
 */
public enum TipoVehiculo {
    CUATRI("Cuatriciclo", Movimiento.RUEDAS, Blindaje.NINGUNO, 30, 22, 1.6, 2.4, 1.3, 2, 20, false, false,
            List.of(new Pieza("cuatri", 0, 0.55, 0, 1.0, Rol.CASCO)),
            List.of(new Asiento(0, 0.75, -0.05, Puesto.CONDUCTOR), new Asiento(0, 0.85, -0.75, Puesto.PASAJERO))),
    JEEP("Jeep", Movimiento.RUEDAS, Blindaje.LIGERO, 80, 18, 2.4, 4.4, 2.0, 2, 30, false, false,
            List.of(new Pieza("jeep_casco", 0, 0.9, 0, 1.5, Rol.CASCO),
                    new Pieza("jeep_ametralladora", 0, 2.15, -0.7, 1.0, Rol.TORRETA)),
            List.of(new Asiento(-0.5, 0.75, 0.55, Puesto.CONDUCTOR), new Asiento(0, 1.35, -0.7, Puesto.ARTILLERO),
                    new Asiento(0.5, 0.75, 0.55, Puesto.PASAJERO), new Asiento(0.5, 0.75, -1.5, Puesto.PASAJERO))),
    VCI("Vehículo de combate de infantería", Movimiento.ORUGAS, Blindaje.MEDIO, 200, 13, 3.2, 6.2, 2.6, 1, 60, true, true,
            List.of(new Pieza("vci_casco", 0, 0.95, 0, 2.2, Rol.CASCO),
                    new Pieza("vci_torreta", 0, 2.2, 0.3, 1.4, Rol.TORRETA),
                    new Pieza("vci_canon", 0, 2.3, 1.6, 1.2, Rol.CANON)),
            List.of(new Asiento(-0.7, 1.3, 1.8, Puesto.CONDUCTOR), new Asiento(0, 1.6, 0.3, Puesto.ARTILLERO),
                    new Asiento(-0.6, 1.2, -1.8, Puesto.PASAJERO), new Asiento(0.6, 1.2, -1.8, Puesto.PASAJERO))),
    TANQUE("Tanque", Movimiento.ORUGAS, Blindaje.PESADO, 350, 10, 3.6, 7.2, 2.6, 1, 90, false, true,
            List.of(new Pieza("tanque_casco", 0, 0.8, 0, 2.5, Rol.CASCO),
                    new Pieza("tanque_torreta", 0, 1.95, -0.3, 2.0, Rol.TORRETA),
                    new Pieza("tanque_canon", 0, 2.05, 1.1, 3.2, Rol.CANON)),
            List.of(new Asiento(0, 1.4, -0.3, Puesto.CONDUCTOR), new Asiento(0.6, 2.55, -0.8, Puesto.ARTILLERO))),
    ANTIAEREO("Antiaéreo", Movimiento.ORUGAS, Blindaje.MEDIO, 180, 12, 3.4, 6.8, 3.0, 1, 60, false, true,
            List.of(new Pieza("aa_casco", 0, 0.8, 0, 2.3, Rol.CASCO),
                    new Pieza("aa_torreta", 0, 2.05, -0.5, 1.8, Rol.TORRETA),
                    new Pieza("aa_canones", 0, 2.6, 0.4, 1.8, Rol.CANON)),
            List.of(new Asiento(0, 1.4, -0.5, Puesto.CONDUCTOR), new Asiento(-0.8, 1.3, 1.6, Puesto.PASAJERO))),
    AVION("Avión", Movimiento.AVION, Blindaje.LIGERO, 120, 35, 9.0, 11.0, 3.0, 1, 120, false, true,
            List.of(new Pieza("avion", 0, 1.3, 0, 3.8, Rol.CASCO)),
            List.of(new Asiento(0, 1.2, 1.6, Puesto.CONDUCTOR))),
    HELICOPTERO("Helicóptero de ataque", Movimiento.HELI, Blindaje.LIGERO, 160, 20, 3.0, 12.0, 3.6, 1, 120, false, true,
            List.of(new Pieza("heli_casco", 0, 1.5, 0, 4.2, Rol.CASCO),
                    new Pieza("heli_rotor", 0, 3.35, 0.4, 4.2, Rol.ROTOR),
                    new Pieza("heli_cola", 0, 2.0, -5.4, 1.2, Rol.ROTOR_COLA)),
            List.of(new Asiento(0, 1.6, 0.4, Puesto.CONDUCTOR), new Asiento(0, 1.3, 1.9, Puesto.ARTILLERO)));

    public enum Movimiento { RUEDAS, ORUGAS, AVION, HELI }

    public enum Blindaje { NINGUNO, LIGERO, MEDIO, PESADO }

    public enum Rol { CASCO, TORRETA, CANON, ROTOR, ROTOR_COLA }

    public enum Puesto { CONDUCTOR, ARTILLERO, PASAJERO }

    /**
     * Pieza del modelo: posición del pivote respecto del centro de la base del casco (x a la izquierda,
     * z adelante) y escala del ItemDisplay. Tiene que coincidir con herramientas/vehiculos.py.
     */
    public record Pieza(String modelo, double x, double y, double z, double escala, Rol rol) {}

    public record Asiento(double x, double y, double z, Puesto puesto) {}

    public final String nombre;
    public final Movimiento movimiento;
    public final Blindaje blindaje;
    public final double vida, velMax, ancho, largo, alto;
    public final int porEquipo, reaparicion;
    public final boolean anfibio;
    /** Cerrado: los de adentro no reciben balas (todo lo absorbe el vehículo). */
    public final boolean cerrado;
    public final List<Pieza> piezas;
    public final List<Asiento> asientos;

    TipoVehiculo(String nombre, Movimiento movimiento, Blindaje blindaje, double vida, double velMax, double ancho,
                 double largo, double alto, int porEquipo, int reaparicion, boolean anfibio, boolean cerrado,
                 List<Pieza> piezas, List<Asiento> asientos) {
        this.nombre = nombre;
        this.movimiento = movimiento;
        this.blindaje = blindaje;
        this.vida = vida;
        this.velMax = velMax;
        this.ancho = ancho;
        this.largo = largo;
        this.alto = alto;
        this.porEquipo = porEquipo;
        this.reaparicion = reaparicion;
        this.anfibio = anfibio;
        this.cerrado = cerrado;
        this.piezas = piezas;
        this.asientos = asientos;
    }

    public boolean aereo() {
        return movimiento == Movimiento.AVION || movimiento == Movimiento.HELI;
    }

    /** El portador de la bandera no puede subir a estos. */
    public boolean prohibidoAlPortador() {
        return this == TANQUE || this == AVION || this == ANTIAEREO;
    }
}
