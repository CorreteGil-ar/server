"""Equipamiento y rachas del Shooter: letales, tácticos, íconos de rachas y objetos del mundo.

Mismo marco que las armas: el frente apunta a -Z, arriba es +Y. Cada objeto se exporta con
variantes de primera persona, tercera persona, inventario y, si se muestra con ItemDisplay
(misil, caja del paquete, claymore), una variante "mundo" con el frente hacia +Z.
"""
import json
from pathlib import Path

import numpy as np

from modelado import Caja, Material, ORIENTACIONES, cilindro, exportar, punteado, ranuras, rayas, rectangulo

VERDE_OLIVA = (86, 92, 52)
ACERO = (70, 72, 76)
NEGRO = (32, 32, 34)


def C(x0, y0, z0, x1, y1, z1, mat, nombre=""):
    return Caja((x0, y0, z0), (x1, y1, z1), mat, nombre)


# ---------------------------------------------------------------- letales y tácticos

def granada():
    """M67 de fragmentación: cuerpo esférico verde oliva, espoleta, palanca y anilla."""
    cuerpo = Material(VERDE_OLIVA, desgaste=0.10, sombra=0.10)
    metal = Material(ACERO, desgaste=0.14)
    return [
        *cilindro("y", (8, 8), 1.7, 5.4, 9.4, cuerpo, "cuerpo"),
        *cilindro("y", (8, 8), 1.25, 4.9, 5.4, cuerpo, "base"),
        *cilindro("y", (8, 8), 0.7, 9.4, 10.6, metal, "espoleta"),
        C(8.5, 7.0, 7.6, 9.0, 10.4, 8.4, metal, "palanca"),
        C(6.7, 10.0, 7.85, 7.3, 10.9, 8.15, metal, "anilla"),
    ]


def semtex():
    """Carga plástica con temporizador y luz roja."""
    pasta = Material((196, 176, 132), desgaste=0.06, sombra=0.10, patrones=[punteado(0.2, -0.04)])
    cinta = Material((200, 170, 40), desgaste=0.05)
    return [
        C(6.4, 6.6, 6.6, 9.6, 8.6, 9.6, pasta, "carga"),
        C(6.35, 6.55, 7.7, 9.65, 8.65, 8.5, cinta, "cinta"),
        C(7.2, 8.6, 7.2, 8.8, 9.2, 8.6, Material(NEGRO), "temporizador"),
        C(7.4, 9.2, 7.4, 7.9, 9.4, 7.9, Material((230, 30, 30), ruido=0, volumen=0), "luz"),
    ]


def hacha():
    """Hacha táctica arrojadiza (tipo tomahawk): mango de polímero y cabeza de acero."""
    mango = Material((40, 40, 42), desgaste=0.05, patrones=[punteado(0.3, -0.06)])
    cabeza = Material((130, 132, 136), desgaste=0.18, sombra=0.06)
    return [
        *cilindro("y", (8, 8), 0.45, 2.0, 12.0, mango, "mango"),
        C(7.6, 10.4, 5.0, 8.4, 12.6, 7.6, cabeza, "hoja"),
        C(7.7, 10.6, 8.4, 8.3, 11.6, 10.4, cabeza, "pico"),
        C(7.5, 1.6, 7.5, 8.5, 2.2, 8.5, mango, "pomo"),
    ]


def claymore():
    """M18A1 Claymore: cuerpo curvo (aproximado) verde, patas y frente marcado."""
    cuerpo = Material(VERDE_OLIVA, desgaste=0.08, sombra=0.08)
    frente = cuerpo.con(color=(110, 116, 70), patrones=[rectangulo((0.5, 0.55), (0.7, 0.3), -0.10, solo_normal="adelante")])
    patas = Material(NEGRO)
    return [
        C(5.0, 5.0, 7.5, 11.0, 8.4, 8.5, frente, "cuerpo"),
        C(4.4, 5.0, 8.0, 5.0, 8.4, 9.0, cuerpo, "ala_izq"),
        C(11.0, 5.0, 8.0, 11.6, 8.4, 9.0, cuerpo, "ala_der"),
        C(7.6, 8.4, 7.8, 8.4, 9.2, 8.3, patas, "espoleta"),
        C(5.4, 3.0, 7.8, 5.8, 5.0, 8.2, patas, "pata_1"),
        C(10.2, 3.0, 7.8, 10.6, 5.0, 8.2, patas, "pata_2"),
        C(5.4, 3.0, 8.6, 5.8, 5.0, 9.0, patas, "pata_3"),
        C(10.2, 3.0, 8.6, 10.6, 5.0, 9.0, patas, "pata_4"),
    ]


def aturdidora():
    """M84 (aturdidora): cilindro con perforaciones."""
    cuerpo = Material((60, 62, 60), desgaste=0.12,
                      patrones=[rayas(periodo=3, ancho=1, delta=-0.18, direccion="arriba")])
    metal = Material(ACERO, desgaste=0.14)
    return [
        *cilindro("y", (8, 8), 1.2, 4.0, 9.6, cuerpo, "cuerpo"),
        *cilindro("y", (8, 8), 0.8, 9.6, 10.6, metal, "espoleta"),
        C(8.4, 6.4, 7.7, 8.9, 10.4, 8.3, metal, "palanca"),
    ]


def humo():
    """M18 de humo: cilindro verde con franja blanca."""
    cuerpo = Material((70, 84, 52), desgaste=0.10)
    franja = Material((220, 220, 210), desgaste=0.05)
    metal = Material(ACERO, desgaste=0.14)
    return [
        *cilindro("y", (8, 8), 1.3, 4.0, 7.2, cuerpo, "cuerpo_bajo"),
        *cilindro("y", (8, 8), 1.32, 7.2, 7.8, franja, "franja"),
        *cilindro("y", (8, 8), 1.3, 7.8, 9.8, cuerpo, "cuerpo_alto"),
        *cilindro("y", (8, 8), 0.8, 9.8, 10.8, metal, "espoleta"),
    ]


def sensor():
    """Sensor de latidos: equipo de mano con pantalla verde y antena."""
    carcasa = Material((48, 50, 46), desgaste=0.12, patrones=[punteado(0.25, -0.05)])
    pantalla = Material((40, 140, 70), ruido=0.01, desgaste=0, sombra=0, volumen=0,
                        patrones=[rayas(periodo=3, ancho=1, delta=0.12, direccion="arriba")])
    return [
        C(6.0, 4.0, 7.4, 10.0, 11.0, 8.6, carcasa, "carcasa"),
        C(6.6, 7.6, 8.6, 9.4, 10.4, 8.7, pantalla, "pantalla"),
        C(9.0, 11.0, 7.8, 9.4, 13.6, 8.2, Material(NEGRO), "antena"),
        C(6.4, 4.6, 8.6, 9.6, 6.6, 8.8, Material((30, 30, 30)), "botones"),
    ]


# ---------------------------------------------------------------- íconos de rachas (en la mano)

def tablet(color_pantalla):
    """Tablet resistente (UAV)."""
    carcasa = Material((60, 64, 52), desgaste=0.12)
    pantalla = Material(color_pantalla, ruido=0.01, desgaste=0, sombra=0, volumen=0,
                        patrones=[rayas(periodo=4, ancho=1, delta=0.10, direccion="arriba")])
    return [
        C(4.5, 5.0, 7.5, 11.5, 10.0, 8.5, carcasa, "carcasa"),
        C(5.2, 5.6, 8.5, 10.8, 9.4, 8.6, pantalla, "pantalla"),
    ]


def bengala():
    """Bengala roja de señales para el paquete de ayuda."""
    tubo = Material((190, 40, 36), desgaste=0.10, patrones=[rayas(periodo=6, ancho=1, delta=-0.08, direccion="arriba")])
    return [
        *cilindro("y", (8, 8), 0.6, 2.0, 11.0, tubo, "tubo"),
        *cilindro("y", (8, 8), 0.7, 11.0, 12.0, Material((40, 40, 40)), "tapa"),
    ]


def laptop():
    """Laptop resistente abierta (Predator)."""
    carcasa = Material((50, 52, 48), desgaste=0.12)
    pantalla = Material((30, 60, 40), ruido=0.01, desgaste=0, sombra=0, volumen=0,
                        patrones=[rayas(periodo=3, ancho=1, delta=0.15, direccion="arriba")])
    teclado = Material((30, 30, 32), patrones=[punteado(0.5, 0.08)])
    return [
        C(4.0, 5.0, 6.0, 12.0, 5.6, 11.0, carcasa, "base"),
        C(4.6, 5.6, 7.0, 11.4, 5.7, 10.4, teclado, "teclado"),
        C(4.0, 5.6, 5.4, 12.0, 11.0, 6.0, carcasa, "tapa"),
        C(4.6, 6.2, 6.0, 11.4, 10.4, 6.1, pantalla, "pantalla"),
    ]


def radio():
    """Radio de campaña con antena (bombardeo)."""
    caja = Material(VERDE_OLIVA, desgaste=0.12, patrones=[ranuras(2, 2, 1, -0.15)])
    return [
        C(6.0, 3.0, 7.0, 10.0, 11.0, 9.0, caja, "caja"),
        C(9.0, 11.0, 7.6, 9.4, 15.0, 8.0, Material(NEGRO), "antena"),
        C(6.4, 9.0, 9.0, 8.0, 10.4, 9.4, Material((30, 30, 30)), "perillas"),
    ]


def silbato():
    """Silbato para perros con cordón."""
    metal = Material((180, 182, 186), desgaste=0.2, sombra=0.05)
    return [
        *cilindro("z", (8, 8), 0.6, 5.0, 10.0, metal, "silbato"),
        C(7.8, 8.6, 9.6, 8.2, 11.0, 10.0, Material((40, 40, 40)), "cordon"),
    ]


def maletin():
    """Maletín nuclear con botón rojo (bomba atómica)."""
    cuero = Material((26, 26, 28), desgaste=0.06, sombra=0.08, patrones=[punteado(0.2, -0.04)])
    metal = Material((170, 170, 172), desgaste=0.15)
    boton = Material((220, 30, 26), ruido=0, volumen=0)
    return [
        C(3.5, 4.0, 7.0, 12.5, 10.5, 9.4, cuero, "maletin"),
        C(6.5, 10.5, 7.9, 9.5, 11.6, 8.5, metal, "manija"),
        C(3.4, 7.0, 6.9, 12.6, 7.4, 9.5, metal, "cierre"),
        C(7.4, 8.4, 9.4, 8.6, 9.6, 9.8, boton, "boton"),
    ]


def municion():
    """Caja de munición verde con traba (la que suelta cada caído)."""
    caja = Material(VERDE_OLIVA, desgaste=0.12, sombra=0.10, patrones=[
        rectangulo((0.5, 0.5), (0.5, 0.4), 0, solo_normal="derecha", color=(200, 190, 140))])
    return [
        C(5.0, 4.0, 6.0, 11.0, 9.0, 10.0, caja, "caja"),
        C(7.0, 9.0, 7.5, 9.0, 9.6, 8.5, Material(NEGRO), "manija"),
    ]


# ---------------------------------------------------------------- objetos del mundo

def paquete():
    """Caja del paquete de ayuda: madera con flejes y el símbolo del equipo."""
    madera = Material((128, 104, 62), desgaste=0.12, sombra=0.12,
                      patrones=[rayas(periodo=4, ancho=1, delta=-0.06, direccion="arriba")])
    fleje = Material((50, 52, 50), desgaste=0.15)
    return [
        C(1.0, 0.0, 2.0, 15.0, 10.0, 14.0, madera, "caja"),
        C(0.9, -0.05, 4.5, 15.1, 10.1, 5.5, fleje, "fleje_1"),
        C(0.9, -0.05, 10.5, 15.1, 10.1, 11.5, fleje, "fleje_2"),
        C(6.0, 10.0, 1.9, 10.0, 10.2, 14.1, fleje, "fleje_3"),
    ]


def misil():
    """Misil Predator (AGM-114): cuerpo blanco, punta oscura y aletas."""
    cuerpo = Material((215, 215, 210), desgaste=0.08, sombra=0.08,
                      patrones=[rayas(periodo=10, ancho=1, delta=-0.06)])
    punta = Material((50, 54, 58), desgaste=0.10)
    aleta = Material((180, 180, 176), desgaste=0.1)
    return [
        *cilindro("z", (8, 8), 1.5, -4.0, 10.0, cuerpo, "cuerpo"),
        *cilindro("z", (8, 8), 1.1, -6.0, -4.0, punta, "punta"),
        C(4.4, 7.7, 7.0, 11.6, 8.3, 9.94, aleta, "aletas_h"),
        C(7.7, 4.4, 7.03, 8.3, 11.6, 9.97, aleta, "aletas_v"),
        C(7.4, 9.4, -2.0, 8.6, 9.8, 1.0, Material((40, 40, 40)), "sensor"),
    ]


# ---------------------------------------------------------------- Guerra

def rpg7():
    """RPG-7: tubo con empuñaduras y la ojiva del cohete asomando adelante."""
    tubo = Material((88, 92, 70), desgaste=0.14, sombra=0.08)
    madera = Material((120, 78, 42), desgaste=0.10, patrones=[rayas(periodo=3, ancho=1, delta=-0.05)])
    ojiva = Material((70, 80, 60), desgaste=0.12)
    return [
        *cilindro("z", (8, 9), 0.9, -2.0, 18.0, tubo, "tubo"),
        *cilindro("z", (8, 9), 1.2, 15.0, 18.6, tubo, "tobera"),
        *cilindro("z", (8, 9), 1.7, -6.5, -2.0, ojiva, "ojiva"),
        *cilindro("z", (8, 9), 0.7, -8.0, -6.5, ojiva, "punta"),
        C(7.4, 9.6, 2.0, 8.6, 10.2, 8.0, madera, "protector"),
        C(7.5, 5.6, 6.0, 8.5, 8.2, 7.0, madera, "empunadura"),
        C(7.5, 5.6, 10.0, 8.5, 8.2, 11.0, madera, "empunadura_2"),
        C(8.9, 9.6, 6.0, 9.6, 10.6, 7.6, Material(NEGRO), "mira"),
    ]


def at4():
    """AT4: tubo descartable verde con miras abatibles."""
    tubo = Material((98, 104, 64), desgaste=0.10, sombra=0.08, patrones=[rayas(periodo=8, ancho=1, delta=-0.05)])
    return [
        *cilindro("z", (8, 9), 1.3, -6.0, 16.0, tubo, "tubo"),
        C(7.5, 5.8, 3.0, 8.5, 7.8, 4.0, Material(NEGRO), "gatillo"),
        C(7.7, 10.2, -4.0, 8.3, 11.0, -3.4, Material(NEGRO), "mira_frontal"),
        C(7.6, 10.2, 6.0, 8.4, 11.2, 6.6, Material(NEGRO), "mira_trasera"),
        C(7.3, 9.6, 12.0, 8.7, 10.6, 15.0, Material((60, 60, 50)), "hombrera"),
    ]


def javelin():
    """Javelin: tubo grueso con la unidad de mando (CLU) a un costado."""
    tubo = Material((96, 100, 70), desgaste=0.10, sombra=0.08)
    clu = Material((70, 74, 56), desgaste=0.12)
    return [
        *cilindro("z", (8, 9), 1.7, -6.0, 15.0, tubo, "tubo"),
        C(9.4, 6.5, 2.0, 12.6, 10.5, 7.0, clu, "clu"),
        C(12.6, 8.0, 3.0, 13.2, 9.5, 5.0, Material(VIDRIO_OSC), "visor"),
        C(9.6, 5.0, 4.0, 10.6, 6.5, 5.0, Material(NEGRO), "empunadura"),
    ]


def stinger():
    """Stinger: tubo largo y fino con antena IFF y empuñadura."""
    tubo = Material((96, 100, 70), desgaste=0.10, sombra=0.08)
    return [
        *cilindro("z", (8, 9), 1.1, -7.0, 15.0, tubo, "tubo"),
        C(9.0, 7.0, 0.0, 9.6, 10.0, 0.6, Material(NEGRO), "antena_base"),
        C(8.8, 10.0, -2.0, 11.6, 10.4, 1.0, Material(NEGRO), "antena"),
        C(7.5, 5.0, 3.0, 8.5, 7.9, 4.2, Material(NEGRO), "empunadura"),
        C(6.6, 9.8, 2.0, 7.4, 11.0, 5.0, Material(ACERO), "mira"),
    ]


def c4():
    """C4: dos bloques de explosivo con cinta, detonador y cables."""
    pasta = Material((205, 196, 160), desgaste=0.05, sombra=0.08, patrones=[punteado(0.15, -0.04)])
    cinta = Material((60, 64, 50), desgaste=0.05)
    return [
        C(5.0, 6.0, 5.5, 11.0, 7.4, 10.5, pasta, "bloque_1"),
        C(5.2, 7.4, 5.7, 10.8, 8.8, 10.3, pasta, "bloque_2"),
        C(4.95, 5.95, 7.4, 11.05, 8.85, 8.4, cinta, "cinta"),
        C(7.2, 8.8, 6.8, 8.8, 9.4, 8.2, Material(NEGRO), "detonador"),
        C(7.6, 9.4, 7.2, 7.9, 9.6, 7.5, Material((230, 30, 30), ruido=0, volumen=0), "luz"),
    ]


def detonador():
    """Detonador de mano (clacker) con antena."""
    cuerpo = Material((70, 74, 56), desgaste=0.12)
    return [
        C(6.4, 4.0, 6.6, 9.6, 10.0, 9.0, cuerpo, "cuerpo"),
        C(6.9, 10.0, 7.2, 9.1, 10.6, 8.4, Material((200, 40, 30)), "boton"),
        C(9.0, 10.05, 8.6, 9.4, 14.0, 9.0, Material(NEGRO), "antena"),
    ]


def mina():
    """Mina antitanque (tipo TM-62): disco chato verde con espoleta."""
    cuerpo = Material((80, 88, 56), desgaste=0.10, sombra=0.10)
    return [
        *cilindro("y", (8, 8), 5.0, 6.0, 8.2, cuerpo, "disco"),
        *cilindro("y", (8, 8), 1.3, 8.2, 9.0, Material(ACERO), "espoleta"),
        C(4.0, 7.2, 7.6, 12.0, 7.6, 8.4, cuerpo.con(color=(64, 70, 46)), "asa"),
    ]


def granada_at():
    """Granada antitanque (tipo RKG-3): cabeza cilíndrica y mango con estabilizador."""
    cabeza = Material((86, 92, 52), desgaste=0.10, sombra=0.10)
    mango = Material((110, 80, 46), desgaste=0.08)
    return [
        *cilindro("y", (8, 8), 1.6, 8.0, 11.5, cabeza, "cabeza"),
        *cilindro("y", (8, 8), 0.8, 2.5, 8.0, mango, "mango"),
        *cilindro("y", (8, 8), 1.0, 2.0, 2.6, Material(ACERO), "tapa"),
    ]


def llave():
    """Llave de reparación grande."""
    acero = Material((150, 152, 156), desgaste=0.18, sombra=0.06)
    goma = Material((180, 40, 30), desgaste=0.04)
    return [
        C(7.4, 1.0, 7.4, 8.6, 9.0, 8.6, goma, "mango"),
        C(7.5, 9.0, 7.5, 8.5, 11.0, 8.5, acero, "cuello"),
        C(5.6, 11.0, 7.4, 10.4, 12.4, 8.6, acero, "cabeza"),
        C(5.6, 12.4, 7.4, 6.8, 14.4, 8.6, acero, "boca_i"),
        C(9.2, 12.4, 7.4, 10.4, 14.4, 8.6, acero, "boca_d"),
    ]


def binoculares():
    """Binoculares: dos tubos unidos."""
    cuerpo = Material((50, 54, 46), desgaste=0.10)
    return [
        *cilindro("z", (6.3, 8), 1.4, 4.0, 11.0, cuerpo, "tubo_i"),
        *cilindro("z", (9.7, 8), 1.4, 4.0, 11.0, cuerpo, "tubo_d"),
        C(6.6, 7.4, 6.0, 9.4, 8.6, 9.0, cuerpo, "puente"),
        *cilindro("z", (6.3, 8), 1.0, 3.6, 4.0, Material(VIDRIO_OSC), "lente_i"),
        *cilindro("z", (9.7, 8), 1.0, 3.6, 4.0, Material(VIDRIO_OSC), "lente_d"),
    ]


def botiquin():
    """Botiquín: bolso verde con cruz blanca."""
    bolso = Material((70, 84, 52), desgaste=0.08, sombra=0.10)
    cruz = Material((235, 235, 235), ruido=0.005, desgaste=0, sombra=0)
    return [
        C(4.5, 5.0, 6.0, 11.5, 10.0, 10.0, bolso, "bolso"),
        C(7.4, 6.0, 5.95, 8.6, 9.0, 6.0, cruz, "cruz_v"),
        C(6.5, 6.9, 5.94, 9.5, 8.1, 5.99, cruz, "cruz_h"),
        C(6.0, 10.0, 7.6, 10.0, 10.6, 8.4, Material(NEGRO), "manija"),
    ]


VIDRIO_OSC = (40, 52, 60)

# nombre -> (función, en el mundo con ItemDisplay)
OBJETOS = {
    "granada": (granada, False), "semtex": (semtex, False), "hacha": (hacha, False),
    "claymore": (claymore, True), "aturdidora": (aturdidora, False), "humo": (humo, False),
    "sensor": (sensor, False), "uav": (lambda: tablet((40, 90, 150)), False), "bengala": (bengala, False),
    "predator": (laptop, False), "radio": (radio, False), "silbato": (silbato, False),
    "bomba": (maletin, False), "municion": (municion, False), "paquete": (paquete, True), "misil": (misil, True),
    # Guerra
    "rpg7": (rpg7, False), "at4": (at4, False), "javelin": (javelin, False), "stinger": (stinger, False),
    "c4": (c4, True), "detonador": (detonador, False), "mina": (mina, True), "granada_at": (granada_at, False),
    "llave": (llave, False), "binoculares": (binoculares, False), "botiquin": (botiquin, False),
}


def _limites(cajas):
    lo = np.min([c.desde for c in cajas], axis=0)
    hi = np.max([c.hasta for c in cajas], axis=0)
    return lo, hi


def variantes(cajas, mundo):
    lo, hi = _limites(cajas)
    centro = (lo + hi) / 2
    tam = float(np.max(hi - lo))
    out = {}
    for v in ("fp", "tp", "gui") + (("mundo",) if mundo else ()):
        m = ORIENTACIONES[v]
        d = 8 - (m @ (centro - 8) + 8)
        if v == "fp":
            s = round(min(0.85, 8.0 / tam), 3)
            mano = {"translation": [-2.2, 2.4, -1.6], "scale": [s] * 3}
            display = {"firstperson_righthand": mano, "firstperson_lefthand": mano}
        elif v == "tp":
            s = round(min(0.6, 6.0 / tam), 3)
            mano = {"translation": [0, 2.0, 1.2], "scale": [s] * 3}
            display = {"thirdperson_righthand": mano, "thirdperson_lefthand": mano}
        elif v == "gui":
            s = round(min(1.0, 13.0 / tam), 3)
            display = {"gui": {"rotation": [25, -35, 0], "scale": [s] * 3},
                       "ground": {"scale": [round(s * 0.55, 3)] * 3},
                       "fixed": {"scale": [s] * 3}, "head": {"scale": [s] * 3}}
        else:
            display = {}
        out[v] = {"orientacion": v, "desplazamiento": tuple(d), "display": display}
    return out


def definicion(nombre, rutas, pack):
    casos = [{"when": ["firstperson_righthand", "firstperson_lefthand"], "model": {"type": "minecraft:model", "model": rutas["fp"]}},
             {"when": ["thirdperson_righthand", "thirdperson_lefthand"], "model": {"type": "minecraft:model", "model": rutas["tp"]}}]
    if "mundo" in rutas:
        casos.append({"when": "none", "model": {"type": "minecraft:model", "model": rutas["mundo"]}})
    d = {"model": {"type": "minecraft:select", "property": "minecraft:display_context", "cases": casos,
                   "fallback": {"type": "minecraft:model", "model": rutas["gui"]}}}
    ruta = Path(pack) / "assets" / "tresmodos" / "items" / f"{nombre}.json"
    ruta.parent.mkdir(parents=True, exist_ok=True)
    ruta.write_text(json.dumps(d, indent=1) + "\n", encoding="utf-8")


def main(pack):
    for nombre, (crear, mundo) in OBJETOS.items():
        cajas = crear()
        densidad = {"paquete": 6, "misil": 8}.get(nombre, 12)
        rutas = exportar(nombre, cajas, variantes(cajas, mundo), pack, densidad=densidad)
        definicion(nombre, rutas, pack)
    print(f"equipo: {len(OBJETOS)} objetos")
