"""Vehículos de Guerra: piezas (casco, torreta, cañón, rotor) para ItemDisplay.

Cada pieza se dibuja en bloques reales, relativa a su pivote (el punto que el plugin ubica y
alrededor del cual gira), en el marco del vehículo: +Z adelante, +Y arriba, +X a la izquierda.
Se exporta con la escala que usa el plugin (TipoVehiculo.java): 1 bloque real = 16/escala
unidades de modelo, centrado en (8, 8, 8). Hay una versión por equipo: verde OTAN de tres
tonos (Azul) y arena y marrón (Rojo, sufijo "_rojo").
"""
import json
from pathlib import Path

import numpy as np

from modelado import Caja, Material, cilindro, exportar, rayas

LIMITE = 1.48  # bloques desde el pivote que entran en -16..32 a escala 1

PALETAS = {
    "": ((78, 88, 52), (58, 64, 40), (40, 38, 30)),       # verde OTAN: oliva, verde oscuro, negro
    "_rojo": ((176, 152, 104), (132, 104, 66), (92, 72, 48)),  # arena, marrón, tierra
}
FRANJA = {"": (40, 70, 200), "_rojo": (190, 30, 30)}
NEGRO = (30, 30, 32)
GOMA = (24, 24, 26)
ACERO = (70, 72, 76)
VIDRIO = (60, 80, 95)


def camuflaje(colores, celda=5):
    """Manchas de dos colores sobre el base (ruido de celdas grandes, con el sombreado conservado)."""
    c1, c2 = (np.array(c, float) / 255 for c in colores)

    def patron(reg, info):
        h, w = reg.shape[:2]
        rng = np.random.default_rng(info["semilla"] ^ 0x5EED)
        for color, umbral in ((c1, 0.62), (c2, 0.80)):
            gh, gw = h // celda + 2, w // celda + 2
            ruido = rng.random((gh, gw))
            # Suavizado simple para que las manchas sean redondeadas.
            ruido = (ruido + np.roll(ruido, 1, 0) + np.roll(ruido, 1, 1) + np.roll(np.roll(ruido, 1, 0), 1, 1)) / 4
            mascara = np.kron(ruido > umbral * 0.9, np.ones((celda, celda)))[:h, :w].astype(bool)
            base = reg[..., :3].mean(axis=-1, keepdims=True)
            luz = np.clip(base / max(1e-3, float(np.mean(base)) + 1e-3), 0.6, 1.4)
            reg[..., :3] = np.where(mascara[..., None], color * luz, reg[..., :3])
    return patron


class Pieza:
    """Arma las cajas de una pieza en bloques reales y las pasa a unidades de modelo."""

    def __init__(self, escala):
        self.escala = escala
        self.cajas = []

    def _u(self, v):
        return 8 + np.asarray(v, float) * 16 / self.escala

    def caja(self, desde, hasta, mat, nombre=""):
        for p in (*desde, *hasta):
            if abs(p) > LIMITE * self.escala + 1e-6:
                raise ValueError(f"{nombre}: {p} sale del límite ({LIMITE * self.escala:.2f})")
        self.cajas.append(Caja(self._u(desde), self._u(hasta), mat, nombre))

    def cil(self, eje, centro, radio, desde, hasta, mat, nombre=""):
        """Cilindro en bloques reales (centro en los otros dos ejes, en orden x, y, z)."""
        f = 16 / self.escala
        c = [8 + v * f for v in centro]
        self.cajas.extend(cilindro(eje, c, radio * f, 8 + desde * f, 8 + hasta * f, mat, nombre))


def materiales(sufijo):
    base, mancha, oscuro = PALETAS[sufijo]
    pintura = Material(base, ruido=0.02, desgaste=0.12, sombra=0.14, volumen=0.08,
                       patrones=[camuflaje((mancha, oscuro))])
    lisa = Material(base, ruido=0.02, desgaste=0.12, sombra=0.14, volumen=0.08)
    franja = Material(FRANJA[sufijo], desgaste=0.05, sombra=0.05)
    goma = Material(GOMA, ruido=0.03, desgaste=0.02, sombra=0.05, patrones=[rayas(periodo=3, ancho=1, delta=-0.15)])
    acero = Material(ACERO, desgaste=0.16, sombra=0.08)
    negro = Material(NEGRO, desgaste=0.06)
    # Translúcido: si no, el parabrisas y la cabina tapan la vista del que maneja.
    vidrio = Material(VIDRIO, ruido=0.01, desgaste=0.02, sombra=0.02, volumen=0.12, alfa=0.38)
    oruga = Material((48, 46, 42), ruido=0.04, desgaste=0.05, sombra=0.08,
                     patrones=[rayas(periodo=4, ancho=2, delta=-0.18)])
    return dict(pintura=pintura, lisa=lisa, franja=franja, goma=goma, acero=acero, negro=negro, vidrio=vidrio,
                oruga=oruga)


# ---------------------------------------------------------------- piezas

def ruedas(p, m, xs, zs, y, r, ancho):
    for x in xs:
        for z in zs:
            p.cil("x", (y, z), r, x - ancho / 2, x + ancho / 2, m["goma"], "rueda")


def cuatri(m):
    p = Pieza(1.0)  # pivote a 0,55 del piso
    p.caja((-0.55, -0.15, -0.9), (0.55, 0.25, 0.95), m["pintura"], "chasis")
    p.caja((-0.25, 0.26, -0.75), (0.25, 0.45, 0.15), m["negro"], "asiento")
    p.caja((-0.5, 0.25, 0.5), (0.5, 0.35, 1.0), m["lisa"], "guardabarros_d")
    p.caja((-0.5, 0.25, -1.0), (0.5, 0.35, -0.55), m["lisa"], "guardabarros_t")
    p.caja((-0.06, 0.25, 0.35), (0.06, 0.65, 0.47), m["acero"], "columna")
    p.caja((-0.45, 0.62, 0.38), (0.45, 0.7, 0.46), m["negro"], "manubrio")
    p.caja((-0.35, 0.36, -0.95), (0.35, 0.42, -0.6), m["acero"], "parrilla")
    ruedas(p, m, (-0.62, 0.62), (-0.68, 0.7), -0.25, 0.3, 0.26)
    return p


def jeep_casco(m):
    p = Pieza(1.5)  # pivote a 0,9 del piso
    p.caja((-1.15, -0.4, -2.1), (1.15, 0.25, 2.1), m["pintura"], "carroceria")
    p.caja((-1.1, 0.25, 0.9), (1.1, 0.45, 2.15), m["pintura"], "capot")
    p.caja((-1.12, 0.25, -2.1), (1.12, 1.05, 0.85), m["pintura"], "cabina")
    p.caja((-1.0, 1.05, -1.6), (1.0, 1.12, 0.75), m["lisa"], "techo")
    p.caja((-1.0, 0.5, 0.86), (1.0, 0.98, 0.9), m["vidrio"], "parabrisas")
    p.caja((-1.13, 0.5, -0.4), (-1.12, 0.95, 0.6), m["vidrio"], "ventana_i")
    p.caja((1.12, 0.5, -0.4), (1.13, 0.95, 0.6), m["vidrio"], "ventana_d")
    p.caja((-0.9, -0.2, 2.1), (0.9, 0.2, 2.2), m["negro"], "parrilla")
    p.caja((-1.16, 0.1, -0.3), (-1.15, 0.2, 0.7), m["franja"], "franja_i")
    p.caja((1.15, 0.1, -0.3), (1.16, 0.2, 0.7), m["franja"], "franja_d")
    p.caja((-0.45, 1.12, -1.15), (0.45, 1.2, -0.25), m["acero"], "anillo")
    ruedas(p, m, (-1.05, 1.05), (-1.35, 1.4), -0.45, 0.45, 0.35)
    return p


def jeep_ametralladora(m):
    p = Pieza(1.0)  # pivote en el anillo de la torreta
    p.caja((-0.35, -0.05, -0.3), (0.35, 0.45, -0.22), m["pintura"], "escudo")
    p.caja((-0.12, 0.05, -0.28), (0.12, 0.3, 0.25), m["negro"], "cajon")
    p.cil("z", (0.0, 0.18), 0.05, 0.25, 1.15, m["acero"], "canon")
    p.caja((-0.08, -0.25, -0.05), (0.08, 0.05, 0.05), m["acero"], "soporte")
    p.caja((0.12, 0.08, -0.1), (0.3, 0.26, 0.15), m["lisa"], "caja_balas")
    return p


def vci_casco(m):
    p = Pieza(2.2)  # pivote a 0,95 del piso
    p.caja((-1.25, -0.35, -3.05), (1.25, 0.65, 3.0), m["pintura"], "casco")
    p.caja((-1.2, 0.3, 2.2), (1.2, 0.62, 3.1), m["pintura"], "trompa")
    p.caja((-1.6, -0.95, -2.95), (-1.2, -0.25, 2.95), m["oruga"], "oruga_i")
    p.caja((1.2, -0.95, -2.95), (1.6, -0.25, 2.95), m["oruga"], "oruga_d")
    p.caja((-1.62, -0.25, -3.08), (1.62, -0.15, 3.08), m["lisa"], "faldon")
    p.caja((-1.26, 0.2, -1.0), (-1.25, 0.35, 1.0), m["franja"], "franja_i")
    p.caja((1.25, 0.2, -1.0), (1.26, 0.35, 1.0), m["franja"], "franja_d")
    p.caja((-0.7, 0.0, -3.1), (0.7, 0.55, -3.05), m["negro"], "rampa")
    p.caja((-0.9, 0.65, 1.2), (-0.4, 0.75, 1.8), m["negro"], "escotilla")
    return p


def vci_torreta(m):
    p = Pieza(1.4)
    p.caja((-0.9, -0.35, -1.0), (0.9, 0.35, 1.0), m["pintura"], "torreta")
    p.caja((-0.7, 0.35, -0.7), (-0.2, 0.45, -0.2), m["negro"], "escotilla")
    p.caja((0.95, -0.2, -0.6), (1.25, 0.25, 0.4), m["lisa"], "lanzador_tow")
    p.caja((0.3, 0.35, 0.3), (0.6, 0.6, 0.6), m["acero"], "periscopio")
    return p


def vci_canon(m):
    p = Pieza(1.2)
    p.caja((-0.25, -0.2, -0.3), (0.25, 0.2, 0.25), m["pintura"], "mantelete")
    p.cil("z", (0.0, 0.0), 0.07, 0.25, 1.75, m["acero"], "canon")
    p.cil("z", (0.0, 0.0), 0.1, 1.55, 1.77, m["negro"], "freno")
    return p


def tanque_casco(m):
    p = Pieza(2.5)  # pivote a 0,8 del piso
    p.caja((-1.35, -0.35, -3.5), (1.35, 0.5, 3.4), m["pintura"], "casco")
    p.caja((-1.3, 0.2, 2.6), (1.3, 0.45, 3.55), m["pintura"], "glacis")
    p.caja((-1.8, -0.8, -3.55), (-1.35, -0.1, 3.5), m["oruga"], "oruga_i")
    p.caja((1.35, -0.8, -3.55), (1.8, -0.1, 3.5), m["oruga"], "oruga_d")
    p.caja((-1.82, -0.1, -3.6), (1.82, 0.0, 3.6), m["lisa"], "guardabarros")
    p.caja((-1.36, 0.15, -2.4), (-1.35, 0.3, 0.6), m["franja"], "franja_i")
    p.caja((1.35, 0.15, -2.4), (1.36, 0.3, 0.6), m["franja"], "franja_d")
    p.caja((-1.2, 0.5, -3.4), (1.2, 0.6, -2.4), m["negro"], "rejilla_motor")
    p.caja((-1.0, 0.0, -3.62), (-0.5, 0.25, -3.55), m["negro"], "caja_herr_i")
    p.caja((0.5, 0.0, -3.62), (1.0, 0.25, -3.55), m["negro"], "caja_herr_d")
    return p


def tanque_torreta(m):
    p = Pieza(2.0)  # pivote en el anillo de la torreta
    p.caja((-1.3, -0.3, -1.4), (1.3, 0.45, 1.25), m["pintura"], "torreta")
    p.caja((-1.1, -0.25, -2.2), (1.1, 0.4, -1.4), m["pintura"], "polizon")
    p.caja((-1.31, 0.0, -1.0), (-1.3, 0.15, 0.8), m["franja"], "franja_i")
    p.caja((1.3, 0.0, -1.0), (1.31, 0.15, 0.8), m["franja"], "franja_d")
    p.caja((0.3, 0.45, -0.6), (0.9, 0.55, 0.0), m["negro"], "escotilla")
    p.caja((-0.8, 0.45, 0.4), (-0.5, 0.7, 0.7), m["acero"], "periscopio")
    p.caja((-0.12, 0.55, -1.8), (-0.06, 1.4, -1.74), m["negro"], "antena")
    p.caja((0.5, 0.55, -0.35), (0.6, 0.85, 0.1), m["acero"], "ametralladora_techo")
    return p


def tanque_canon(m):
    p = Pieza(3.2)  # pivote en los muñones del cañón
    p.caja((-0.45, -0.3, -0.35), (0.45, 0.3, 0.35), m["pintura"], "mantelete")
    p.cil("z", (0.0, 0.0), 0.11, 0.35, 4.6, m["lisa"], "canon")
    p.cil("z", (0.0, 0.0), 0.16, 2.2, 2.9, m["acero"], "evacuador")
    return p


def aa_casco(m):
    p = Pieza(2.3)
    p.caja((-1.3, -0.35, -3.3), (1.3, 0.55, 3.2), m["pintura"], "casco")
    p.caja((-1.25, 0.2, 2.5), (1.25, 0.5, 3.35), m["pintura"], "trompa")
    p.caja((-1.7, -0.8, -3.3), (-1.3, -0.1, 3.3), m["oruga"], "oruga_i")
    p.caja((1.3, -0.8, -3.3), (1.7, -0.1, 3.3), m["oruga"], "oruga_d")
    p.caja((-1.72, -0.1, -3.35), (1.72, 0.0, 3.35), m["lisa"], "guardabarros")
    p.caja((-1.31, 0.15, -2.0), (-1.3, 0.3, 0.5), m["franja"], "franja_i")
    p.caja((1.3, 0.15, -2.0), (1.31, 0.3, 0.5), m["franja"], "franja_d")
    return p


def aa_torreta(m):
    p = Pieza(1.8)
    p.caja((-1.0, -0.3, -1.2), (1.0, 0.6, 1.1), m["pintura"], "torreta")
    p.caja((-0.45, 0.6, -1.0), (0.45, 0.75, -0.3), m["acero"], "base_radar")
    p.caja((-0.85, 0.75, -0.7), (0.85, 1.35, -0.6), m["negro"], "radar")
    p.caja((-1.01, 0.1, -0.8), (-1.0, 0.25, 0.6), m["franja"], "franja")
    return p


def aa_canones(m):
    p = Pieza(1.8)  # pivote en el eje de elevación
    p.caja((-1.25, -0.3, -0.4), (-0.85, 0.3, 0.4), m["pintura"], "cuna_i")
    p.caja((0.85, -0.3, -0.4), (1.25, 0.3, 0.4), m["pintura"], "cuna_d")
    p.caja((-0.85, -0.1, -0.15), (0.85, 0.1, 0.15), m["acero"], "eje")
    p.cil("z", (-1.05, 0.0), 0.06, 0.4, 2.6, m["acero"], "canon_i")
    p.cil("z", (1.05, 0.0), 0.06, 0.4, 2.6, m["acero"], "canon_d")
    return p


def avion(m):
    p = Pieza(3.8)  # pivote a 1,3 del piso, en el centro del avión
    p.cil("z", (0.0, 0.0), 0.55, -4.6, 3.2, m["lisa"], "fuselaje")
    p.cil("z", (0.0, 0.0), 0.32, 3.2, 5.4, m["lisa"], "trompa")
    p.caja((-0.3, 0.35, 1.6), (0.3, 0.75, 3.4), m["vidrio"], "cabina")
    p.caja((-4.5, -0.1, -1.6), (4.5, 0.05, 0.9), m["pintura"], "alas")
    p.caja((-1.9, -0.05, -4.55), (1.9, 0.08, -3.4), m["pintura"], "estabilizador")
    p.caja((-0.06, 0.08, -4.7), (0.06, 1.7, -3.3), m["pintura"], "deriva")
    p.caja((-0.07, 0.6, -4.2), (0.07, 1.4, -3.6), m["franja"], "insignia")
    p.caja((-0.5, -0.45, -0.6), (0.5, -0.05, 0.6), m["negro"], "tomas")
    p.caja((-2.6, -0.35, -0.6), (-2.3, -0.1, 0.8), m["acero"], "misil_i")
    p.caja((2.3, -0.35, -0.6), (2.6, -0.1, 0.8), m["acero"], "misil_d")
    p.caja((-0.05, -1.3, 2.0), (0.05, -0.5, 2.1), m["acero"], "tren_d")
    p.caja((-0.9, -1.3, -0.6), (-0.8, -0.5, -0.5), m["acero"], "tren_i")
    p.caja((0.8, -1.3, -0.6), (0.9, -0.5, -0.5), m["acero"], "tren_dd")
    return p


def heli_casco(m):
    p = Pieza(4.2)  # pivote a 1,5 del piso
    p.caja((-0.6, -0.7, -1.4), (0.6, 0.6, 1.8), m["pintura"], "cuerpo")
    p.caja((-0.5, -0.4, 1.8), (0.5, 0.45, 2.9), m["pintura"], "trompa")
    p.caja((-0.45, 0.05, 1.0), (0.45, 0.65, 2.7), m["vidrio"], "cabina")
    p.caja((-0.25, -0.1, -6.0), (0.25, 0.35, -1.4), m["pintura"], "botalon")
    p.caja((-0.05, 0.35, -6.0), (0.05, 1.3, -5.2), m["pintura"], "deriva")
    p.caja((-1.4, -0.2, -0.5), (1.4, -0.05, 0.5), m["lisa"], "alas")
    p.caja((-1.5, -0.45, -0.3), (-1.2, -0.2, 0.6), m["negro"], "cohetes_i")
    p.caja((1.2, -0.45, -0.3), (1.5, -0.2, 0.6), m["negro"], "cohetes_d")
    p.caja((-0.15, -0.95, 2.2), (0.15, -0.65, 2.6), m["acero"], "canon_30")
    p.caja((-0.61, 0.0, -0.8), (-0.6, 0.15, 0.6), m["franja"], "franja_i")
    p.caja((0.6, 0.0, -0.8), (0.61, 0.15, 0.6), m["franja"], "franja_d")
    p.caja((-0.75, -1.5, -0.8), (-0.65, -0.7, 1.0), m["acero"], "patin_i")
    p.caja((0.65, -1.5, -0.8), (0.75, -0.7, 1.0), m["acero"], "patin_d")
    p.caja((-0.3, 0.6, -0.4), (0.3, 1.05, 0.6), m["lisa"], "motor")
    return p


def heli_rotor(m):
    p = Pieza(4.2)
    p.caja((-6.0, -0.03, -0.18), (6.0, 0.03, 0.18), m["negro"], "pala_1")
    p.caja((-0.18, -0.02, -6.0), (0.18, 0.02, 6.0), m["negro"], "pala_2")
    p.cil("y", (0.0, 0.0), 0.25, -0.15, 0.15, m["acero"], "cubo")
    return p


def heli_cola(m):
    p = Pieza(1.2)
    p.caja((0.2, -1.1, -0.08), (0.24, 1.1, 0.08), m["negro"], "pala_1")
    p.caja((0.18, -0.08, -1.1), (0.26, 0.08, 1.1), m["negro"], "pala_2")
    return p


PIEZAS = {
    "cuatri": cuatri, "jeep_casco": jeep_casco, "jeep_ametralladora": jeep_ametralladora,
    "vci_casco": vci_casco, "vci_torreta": vci_torreta, "vci_canon": vci_canon,
    "tanque_casco": tanque_casco, "tanque_torreta": tanque_torreta, "tanque_canon": tanque_canon,
    "aa_casco": aa_casco, "aa_torreta": aa_torreta, "aa_canones": aa_canones,
    "avion": avion, "heli_casco": heli_casco, "heli_rotor": heli_rotor, "heli_cola": heli_cola,
}


def main(pack):
    pack = Path(pack)
    escalas = {}
    for nombre, crear in PIEZAS.items():
        for sufijo in PALETAS:
            pieza = crear(materiales(sufijo))
            escalas[nombre] = pieza.escala
            # Densidad en píxeles por unidad de modelo: ~10 px por bloque real.
            densidad = max(0.5, round(10 * pieza.escala / 16, 2))
            arch = f"vehiculo/{nombre}{sufijo}"
            rutas = exportar(arch, pieza.cajas, {"": {"orientacion": "fp", "desplazamiento": (0, 0, 0), "display": {}}},
                             pack, densidad=densidad)
            ruta = pack / "assets" / "tresmodos" / "items" / f"{arch}.json"
            ruta.parent.mkdir(parents=True, exist_ok=True)
            ruta.write_text(json.dumps({"model": {"type": "minecraft:model", "model": rutas[""]}}, indent=1) + "\n",
                            encoding="utf-8")
    print(f"vehículos: {len(PIEZAS)} piezas × {len(PALETAS)} equipos")
    return escalas


if __name__ == "__main__":
    main(Path(__file__).resolve().parent.parent / "paquete-recursos")
