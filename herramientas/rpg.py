"""Armas del RPG (ArmaRpg.java): espadas, hachas, mazas, bastón y talismán con modelo propio.

Se modelan verticales (la hoja hacia +Y, el filo hacia ±X y el plano hacia ±Z) con el puño en
y ≈ 1,6, que es donde la mano agarra el sprite de una espada de vanilla. Las transformaciones de
mano son las de `item/handheld` con 45° más de giro en Z: así la hoja queda en la diagonal del
sprite y se agarra igual que una espada común. Arco, ballesta y lanzas quedan con el modelo de
vanilla (tienen animaciones de carga que dependen de él).
"""
import json
import math
from pathlib import Path

import numpy as np

from modelado import Caja, Material, cilindro, exportar, punteado, rayas

ACERO = (150, 152, 158)
ACERO_OSC = (92, 94, 100)
CUERO = (78, 52, 34)
MADERA = (102, 72, 44)
HUESO = (214, 204, 176)
ORO = (196, 156, 60)


def C(x0, y0, z0, x1, y1, z1, mat, nombre=""):
    return Caja((x0, y0, z0), (x1, y1, z1), mat, nombre)


def hoja(y0, y1, ancho, espesor, mat, punta=3.0, x=8.0, nombre="hoja"):
    """Hoja recta que se afina en los últimos `punta` de largo."""
    e = espesor / 2
    cuerpo = y1 - punta
    out = [C(x - ancho / 2, y0, 8 - e, x + ancho / 2, cuerpo, 8 + e, mat, nombre)]
    pasos = 3
    for i in range(pasos):
        a = ancho * (1 - (i + 1) / (pasos + 1))
        ya, yb = cuerpo + punta * i / pasos, cuerpo + punta * (i + 1) / pasos
        out.append(C(x - a / 2, ya, 8 - e * 0.9, x + a / 2, yb, 8 + e * 0.9, mat, f"{nombre}_punta{i}"))
    return out


def empunadura(y0, y1, mat, grosor=0.9, pomo=None, pomo_tam=1.4):
    g = grosor / 2
    out = [C(8 - g, y0, 8 - g, 8 + g, y1, 8 + g, mat, "puno")]
    if pomo is not None:
        p = pomo_tam / 2
        out.append(C(8 - p, y0 - pomo_tam, 8 - p, 8 + p, y0, 8 + p, pomo, "pomo"))
    return out


def mango(y0, y1, mat, grosor=0.9, nombre="mango"):
    g = grosor / 2
    return [C(8 - g, y0, 8 - g, 8 + g, y1, 8 + g, mat, nombre)]


# ---------------------------------------------------------------- espadas

def espada_bastarda():
    acero = Material(ACERO, desgaste=0.18, sombra=0.08, patrones=[rayas(periodo=6, ancho=1, delta=-0.05)])
    vaceo = Material(ACERO_OSC, desgaste=0.05)
    cuero = Material(CUERO, desgaste=0.05, patrones=[rayas(periodo=2, ancho=1, delta=-0.12)])
    hierro = Material((70, 70, 74), desgaste=0.2)
    return [
        *empunadura(-2.6, 4.0, cuero, pomo=hierro, pomo_tam=1.5),
        C(3.6, 4.0, 7.3, 12.4, 5.0, 8.7, hierro, "guarda"),
        *hoja(5.0, 27.0, 2.2, 0.6, acero, punta=4.0),
        C(7.75, 5.4, 7.62, 8.25, 20.0, 8.38, vaceo, "vaceo"),
    ]


def espada_corta():
    acero = Material(ACERO, desgaste=0.15, sombra=0.08)
    cuero = Material((96, 64, 40), desgaste=0.05, patrones=[rayas(periodo=2, ancho=1, delta=-0.12)])
    hierro = Material((84, 84, 88), desgaste=0.2)
    return [
        *empunadura(-1.6, 3.8, cuero, pomo=hierro, pomo_tam=1.2),
        C(5.0, 3.8, 7.4, 11.0, 4.6, 8.6, hierro, "guarda"),
        *hoja(4.6, 19.5, 2.0, 0.55, acero, punta=3.0),
    ]


def katana():
    acero = Material((186, 188, 196), desgaste=0.25, sombra=0.04)
    filo = Material((222, 224, 230), desgaste=0.1)
    ito = Material((40, 36, 52), desgaste=0.05, patrones=[rayas(periodo=2, ancho=1, delta=-0.2)])
    tsuba = Material((60, 52, 40), desgaste=0.2)
    out = [
        *empunadura(-3.4, 4.0, ito, pomo=Material((120, 100, 60)), pomo_tam=1.0),
        C(6.6, 4.0, 6.6, 9.4, 4.6, 9.4, tsuba, "tsuba"),
        C(7.4, 4.6, 7.6, 8.6, 5.4, 8.4, Material(ORO), "habaki"),
    ]
    # Hoja curva: tramos corridos hacia el lomo (+X) a medida que sube.
    y, x = 5.4, 8.0
    for i in range(6):
        largo = 3.8
        out.append(C(x - 0.7, y, 7.75, x + 0.55, y + largo, 8.25, acero, f"hoja{i}"))
        out.append(C(x - 0.85, y, 7.82, x - 0.7, y + largo, 8.18, filo, f"filo{i}"))
        y += largo
        x += 0.18 + i * 0.05
    out.append(C(x - 0.6, y, 7.8, x + 0.3, y + 1.6, 8.2, acero, "kissaki"))
    return out


def estoque():
    acero = Material((176, 178, 184), desgaste=0.2)
    oro = Material(ORO, desgaste=0.2, sombra=0.1)
    cuero = Material((60, 40, 30), patrones=[rayas(periodo=2, ancho=1, delta=-0.15)])
    return [
        *empunadura(-2.4, 3.8, cuero, pomo=oro, pomo_tam=1.3),
        C(5.2, 3.8, 7.5, 10.8, 4.4, 8.5, oro, "gavilanes"),
        C(9.6, 0.4, 7.6, 10.2, 3.8, 8.4, oro, "guardamano"),
        C(8.0, 0.0, 7.6, 9.6, 0.6, 8.4, oro, "guardamano_bajo"),
        C(6.6, 4.4, 7.2, 9.4, 4.9, 8.8, oro, "cazoleta"),
        *hoja(4.9, 28.0, 0.8, 0.5, acero, punta=2.5),
    ]


def daga():
    acero = Material(ACERO, desgaste=0.18)
    cuero = Material(CUERO, patrones=[rayas(periodo=2, ancho=1, delta=-0.12)])
    hierro = Material((70, 70, 74))
    return [
        *empunadura(-1.2, 3.2, cuero, pomo=hierro, pomo_tam=1.1),
        C(6.2, 3.2, 7.5, 9.8, 3.8, 8.5, hierro, "guarda"),
        *hoja(3.8, 13.0, 1.5, 0.5, acero, punta=2.6),
    ]


def dagas_gemelas():
    """Daga curva de hoja ancha, con anilla en el pomo."""
    acero = Material((168, 170, 178), desgaste=0.2)
    cuero = Material((110, 40, 36), patrones=[rayas(periodo=2, ancho=1, delta=-0.15)])
    hierro = Material((60, 60, 64))
    out = [
        *empunadura(-1.0, 3.2, cuero),
        C(7.3, -2.2, 7.7, 8.7, -1.0, 8.3, hierro, "anilla"),
        C(6.0, 3.2, 7.5, 10.0, 3.8, 8.5, hierro, "guarda"),
    ]
    y, x = 3.8, 8.0
    for i, (a, l) in enumerate(((1.8, 3.0), (1.6, 3.0), (1.3, 2.4), (0.8, 1.8))):
        out.append(C(x - a / 2, y, 7.75, x + a / 2, y + l, 8.25, acero, f"hoja{i}"))
        y += l
        x -= 0.35
    return out


def espada_abismo():
    """Mandoble negro con un vaceo violeta que brilla y guarda dentada."""
    negro = Material((40, 36, 46), desgaste=0.12, sombra=0.05, patrones=[punteado(0.12, -0.05)])
    brillo = Material((170, 80, 230), ruido=0.0, desgaste=0.0, sombra=0.0, volumen=0.0)
    guarda = Material((54, 48, 60), desgaste=0.2)
    cuero = Material((30, 24, 34), patrones=[rayas(periodo=2, ancho=1, delta=-0.15)])
    return [
        *empunadura(-3.6, 4.0, cuero, pomo=guarda, pomo_tam=1.6),
        C(3.0, 4.0, 7.2, 13.0, 5.2, 8.8, guarda, "guarda"),
        C(2.2, 5.2, 7.4, 3.6, 6.4, 8.6, guarda, "diente_izq"),
        C(12.4, 5.2, 7.4, 13.8, 6.4, 8.6, guarda, "diente_der"),
        *hoja(5.2, 29.0, 3.0, 0.7, negro, punta=4.5),
        C(7.6, 5.6, 7.6, 8.4, 22.0, 8.4, brillo, "vaceo"),
    ]


# ---------------------------------------------------------------- hachas, mazas y astas

def gran_hacha():
    madera = Material(MADERA, desgaste=0.08, patrones=[rayas(periodo=3, ancho=1, delta=-0.06, direccion="arriba")])
    acero = Material((126, 128, 134), desgaste=0.25, sombra=0.08)
    oscuro = Material((60, 60, 66), desgaste=0.2)
    cuero = Material(CUERO, patrones=[rayas(periodo=2, ancho=1, delta=-0.12)])
    return [
        *mango(-9.0, 24.0, madera, 1.0),
        *mango(-1.0, 4.0, cuero, 1.2, "agarre"),
        C(8.5, 15.0, 7.6, 10.0, 25.0, 8.4, oscuro, "ojo"),
        C(10.0, 14.0, 7.7, 12.5, 26.0, 8.3, acero, "hoja1"),
        C(12.5, 12.5, 7.75, 14.5, 27.0, 8.25, acero, "hoja2"),
        C(14.5, 13.5, 7.8, 15.5, 26.0, 8.2, acero, "filo"),
        C(5.0, 19.0, 7.7, 7.5, 21.0, 8.3, oscuro, "contrapeso"),
        C(7.4, 24.0, 7.4, 8.6, 25.4, 8.6, oscuro, "remate"),
    ]


def alabarda():
    madera = Material((80, 58, 38), desgaste=0.08, patrones=[rayas(periodo=3, ancho=1, delta=-0.06, direccion="arriba")])
    acero = Material((130, 132, 140), desgaste=0.25)
    return [
        *mango(-12.0, 26.0, madera, 0.9),
        C(7.5, -12.8, 7.5, 8.5, -12.0, 8.5, acero, "regaton"),
        C(8.45, 19.0, 7.7, 12.5, 24.0, 8.3, acero, "hacha"),
        C(12.5, 18.0, 7.75, 13.5, 25.0, 8.25, acero, "filo"),
        C(4.5, 21.0, 7.75, 7.55, 22.4, 8.25, acero, "pico"),
        C(3.6, 20.4, 7.8, 4.5, 21.6, 8.2, acero, "pico_punta"),
        *hoja(26.0, 31.5, 1.2, 0.5, acero, punta=2.5, nombre="punta"),
    ]


def guadania():
    madera = Material((70, 56, 44), desgaste=0.1, patrones=[rayas(periodo=3, ancho=1, delta=-0.06, direccion="arriba")])
    acero = Material((120, 124, 130), desgaste=0.3, sombra=0.04)
    filo = Material((196, 200, 206), desgaste=0.1)
    out = [
        *mango(-12.0, 24.0, madera, 0.9),
        C(7.4, 5.0, 7.4, 8.6, 5.8, 8.6, madera, "manija_base"),
        C(8.6, 5.0, 7.6, 10.4, 5.8, 8.4, madera, "manija"),
        C(7.3, 22.0, 7.3, 8.7, 24.6, 8.7, Material((60, 60, 66)), "abrazadera"),
    ]
    # Hoja: sale del asta hacia -X y se curva hacia abajo en la punta.
    tramos = [(1.6, 3.0), (1.4, 3.0), (1.2, 3.0), (1.0, 2.6), (0.8, 2.0)]
    x, y = 7.3, 23.0
    for i, (alto, largo) in enumerate(tramos):
        out.append(C(x - largo, y - alto / 2, 7.8, x, y + alto / 2, 8.2, acero, f"hoja{i}"))
        out.append(C(x - largo, y - alto / 2 - 0.3, 7.85, x, y - alto / 2, 8.15, filo, f"filo{i}"))
        x -= largo
        y -= 0.5 + i * 0.35
    return out


def martillo():
    madera = Material(MADERA, desgaste=0.08, patrones=[rayas(periodo=3, ancho=1, delta=-0.06, direccion="arriba")])
    hierro = Material((88, 88, 94), desgaste=0.25, sombra=0.1)
    cuero = Material(CUERO, patrones=[rayas(periodo=2, ancho=1, delta=-0.12)])
    return [
        *mango(-5.0, 20.0, madera, 1.0),
        *mango(-1.0, 4.0, cuero, 1.2, "agarre"),
        C(5.0, 18.0, 6.2, 11.6, 23.0, 9.8, hierro, "cabeza"),
        C(11.6, 18.6, 6.6, 12.4, 22.4, 9.4, hierro, "cara"),
        C(2.0, 19.6, 7.4, 5.0, 21.4, 8.6, hierro, "pico"),
        C(7.4, 23.0, 7.4, 8.6, 24.6, 8.6, hierro, "punta"),
    ]


def maza():
    madera = Material(MADERA, desgaste=0.08)
    hierro = Material((110, 110, 116), desgaste=0.25, sombra=0.08)
    oro = Material(ORO, desgaste=0.2)
    out = [
        *mango(-3.0, 13.0, madera, 1.0),
        C(7.3, -3.8, 7.3, 8.7, -3.0, 8.7, oro, "pomo"),
        *cilindro("y", (8, 8), 1.4, 13.0, 18.0, hierro, "nucleo"),
        C(7.5, 18.0, 7.5, 8.5, 19.4, 8.5, hierro, "punta"),
    ]
    # Pestañas en cruz.
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        x0 = 8 + dx * 1.2 if dx else 7.75
        x1 = 8 + dx * 2.6 if dx else 8.25
        z0 = 8 + dz * 1.2 if dz else 7.75
        z1 = 8 + dz * 2.6 if dz else 8.25
        out.append(C(min(x0, x1), 13.4, min(z0, z1), max(x0, x1), 17.6, max(z0, z1), hierro, f"pestana_{dx}_{dz}"))
    return out


def gran_maza_gloton():
    """Garrote enorme de carne seca y hueso, con dientes clavados."""
    hueso = Material(HUESO, desgaste=0.1, sombra=0.12)
    carne = Material((110, 50, 46), desgaste=0.05, sombra=0.15, patrones=[punteado(0.35, -0.12)])
    diente = Material((236, 228, 200), desgaste=0.05)
    out = [
        *mango(-5.0, 12.6, hueso, 1.4),
        C(7.0, -6.0, 7.0, 9.0, -5.0, 9.0, hueso, "rotula"),
        C(5.0, 14.0, 5.0, 11.0, 24.0, 11.0, carne, "masa"),
        C(5.6, 24.0, 5.6, 10.4, 26.0, 10.4, carne, "masa_tope"),
        C(5.8, 12.6, 5.8, 10.2, 14.0, 10.2, carne, "masa_base"),
    ]
    for i, (x, y, z, dx, dz) in enumerate(((11, 16, 8, 1, 0), (11, 21, 7, 1, 0), (5, 18, 8, -1, 0), (5, 22.5, 9, -1, 0),
                                            (8, 15.5, 11, 0, 1), (7, 20, 11, 0, 1), (9, 17.5, 5, 0, -1), (8, 22, 5, 0, -1))):
        x0, x1 = (x, x + 1.2) if dx > 0 else (x - 1.2, x) if dx < 0 else (x - 0.4, x + 0.4)
        z0, z1 = (z, z + 1.2) if dz > 0 else (z - 1.2, z) if dz < 0 else (z - 0.4, z + 0.4)
        out.append(C(x0, y, z0, x1, y + 1.0, z1, diente, f"diente{i}"))
    return out


# ---------------------------------------------------------------- catalizadores

def baston_hueso():
    hueso = Material(HUESO, desgaste=0.1, sombra=0.12)
    nudo = Material((180, 168, 140), desgaste=0.1)
    brillo = Material((120, 230, 255), ruido=0.0, desgaste=0.0, sombra=0.0, volumen=0.0)
    out = [*mango(-12.0, 19.2, hueso, 0.9)]
    for y in (-6.0, 2.0, 10.0, 17.0):
        out.append(C(7.3, y, 7.3, 8.7, y + 1.0, 8.7, nudo, f"nudo{y:.0f}"))
    # Calavera chica en la punta con los ojos encendidos.
    out += [
        C(6.6, 20.0, 6.6, 9.4, 23.0, 9.4, hueso, "craneo"),
        C(7.0, 19.2, 7.0, 9.0, 20.0, 8.6, nudo, "mandibula"),
        C(7.0, 21.2, 6.5, 7.8, 22.0, 6.6, brillo, "ojo_izq"),
        C(8.2, 21.2, 6.5, 9.0, 22.0, 6.6, brillo, "ojo_der"),
        C(6.2, 22.4, 7.6, 6.6, 24.4, 8.4, nudo, "cuerno_izq"),
        C(9.4, 22.4, 7.6, 9.8, 24.4, 8.4, nudo, "cuerno_der"),
    ]
    return out


def talisman():
    """Medallón con la llama, colgado de una cadena corta."""
    oro = Material(ORO, desgaste=0.25, sombra=0.1)
    llama = Material((255, 150, 40), ruido=0.0, desgaste=0.0, sombra=0.0, volumen=0.0)
    cadena = Material((150, 150, 156))
    return [
        C(7.7, 4.0, 7.85, 8.3, 9.5, 8.15, cadena, "cadena"),
        *cilindro("z", (8, 1.5), 2.6, 7.6, 8.4, oro, "medallon"),
        C(7.4, 0.2, 8.4, 8.6, 2.6, 8.6, llama, "llama"),
        C(7.7, 2.6, 8.4, 8.3, 3.4, 8.6, llama, "llama_punta"),
        C(7.4, 3.9, 7.4, 8.6, 4.3, 8.6, oro, "argolla"),
    ]


ARMAS = {
    "espada_bastarda": espada_bastarda, "gran_hacha": gran_hacha, "katana": katana, "dagas_gemelas": dagas_gemelas,
    "baston_hueso": baston_hueso, "daga": daga, "maza": maza, "talisman": talisman, "espada_corta": espada_corta,
    "estoque": estoque, "alabarda": alabarda, "guadania": guadania, "martillo": martillo,
    "gran_maza_gloton": gran_maza_gloton, "espada_abismo": espada_abismo,
}

# Mismas transformaciones que item/handheld, con la hoja girada 45° hacia la diagonal.
MANO = {
    "thirdperson_righthand": {"rotation": [0, -90, 10], "translation": [0, 4.0, 0.5], "scale": [0.85] * 3},
    "thirdperson_lefthand": {"rotation": [0, 90, -100], "translation": [0, 4.0, 0.5], "scale": [0.85] * 3},
    "firstperson_righthand": {"rotation": [0, -90, -20], "translation": [1.13, 3.2, 1.13], "scale": [0.68] * 3},
    "firstperson_lefthand": {"rotation": [0, 90, -70], "translation": [1.13, 3.2, 1.13], "scale": [0.68] * 3},
}


def _rz(grados, v):
    a = math.radians(grados)
    return np.array([v[0] * math.cos(a) - v[1] * math.sin(a), v[0] * math.sin(a) + v[1] * math.cos(a), v[2]])


def display(cajas):
    """Mano como un arma de vanilla; inventario, suelo y marco con la diagonal centrada."""
    lo = np.min([c.desde for c in cajas], axis=0)
    hi = np.max([c.hasta for c in cajas], axis=0)
    largo = float(np.max(hi - lo))
    centro = (lo + hi) / 2 - 8
    s = round(min(1.0, 19.0 / largo), 3)
    t = -s * _rz(-45, centro)
    d = dict(MANO)
    d["gui"] = {"rotation": [0, 0, -45], "translation": [round(float(t[0]), 2), round(float(t[1]), 2), 0], "scale": [s] * 3}
    d["fixed"] = {"rotation": [0, 180, -45], "translation": [round(float(-t[0]), 2), round(float(t[1]), 2), 0], "scale": [s] * 3}
    d["ground"] = {"rotation": [0, 0, -45], "translation": [round(float(t[0]) * 0.5, 2), 2 + round(float(t[1]) * 0.5, 2), 0],
                   "scale": [round(s * 0.5, 3)] * 3}
    d["head"] = {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]}
    return d


def main(pack):
    for nombre, crear in ARMAS.items():
        cajas = crear()
        variantes = {"mano": {"orientacion": "fp", "desplazamiento": (0, 0, 0), "display": display(cajas)}}
        rutas = exportar(f"rpg_{nombre}", cajas, variantes, pack, densidad=12)
        ruta = Path(pack) / "assets" / "tresmodos" / "items" / f"rpg_{nombre}.json"
        ruta.parent.mkdir(parents=True, exist_ok=True)
        ruta.write_text(json.dumps({"model": {"type": "minecraft:model", "model": rutas["mano"]}}, indent=1) + "\n",
                        encoding="utf-8")
    print(f"rpg: {len(ARMAS)} armas")
