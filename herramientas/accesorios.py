"""Accesorios de armas. Cada uno se define alrededor del origen de su montaje.

Marco: el cañón apunta a -Z y +Y se aleja del riel donde se monta ("arriba"). Para montar
en un riel lateral o inferior se rota alrededor del eje del cañón (ver `montar`).
"""
import numpy as np

from modelado import Caja, Material, cilindro, picatinny, punteado, rayas, reticula

NEGRO = (34, 34, 36)
FDE = (168, 142, 104)

negro = Material(NEGRO, desgaste=0.14, sombra=0.06)
negro_mate = Material((28, 28, 30), desgaste=0.08, sombra=0.06, patrones=[punteado(0.25, -0.05)])
fde = Material(FDE, desgaste=0.10, sombra=0.08)
vidrio = Material((70, 110, 120), ruido=0.0, desgaste=0, sombra=0, volumen=0, alfa=0.28)
vidrio_oscuro = Material((30, 50, 60), ruido=0.0, desgaste=0, sombra=0, volumen=0, alfa=0.55)


def lente(color_luz=None, ruido=0.0, alfa=0.3):
    """Vidrio con retícula; color_luz None = vidrio liso."""
    patrones = [reticula(color_luz, anillo=True)] if color_luz else []
    return Material((80, 120, 130), ruido=ruido, desgaste=0, sombra=0, volumen=0, alfa=alfa, patrones=patrones)


def frente_iluminado(color):
    """Cara frontal que emite luz (linterna, láser)."""

    def patron(reg, info):
        if float(np.dot(info["normal"], info["adelante"])) > 0.5:
            reg[..., :3] = np.array(color) / 255.0
            reg[..., 3] = 1

    return patron


def C(x0, y0, z0, x1, y1, z1, mat, nombre=""):
    return Caja((x0, y0, z0), (x1, y1, z1), mat, nombre)


# ---------------------------------------------------------------- ópticas (montaje superior)

def punto_rojo():
    """Mira de punto rojo compacta sobre elevador (tipo Aimpoint T-2)."""
    return [
        C(-0.45, 0.00, -0.95, 0.45, 0.55, 0.95, negro, "elevador"),
        C(-0.62, 0.55, -1.00, -0.44, 1.92, 1.00, negro, "pared_izq"),
        C(0.44, 0.55, -1.00, 0.62, 1.92, 1.00, negro, "pared_der"),
        C(-0.44, 1.74, -1.00, 0.44, 1.92, 1.00, negro, "techo"),
        C(-0.44, 0.55, -1.00, 0.44, 0.72, 1.00, negro, "piso"),
        C(-0.44, 0.72, 0.80, 0.44, 1.74, 0.84, lente((255, 30, 25)), "lente"),
        *cilindro("y", (0.0, 0.0), 0.22, 1.92, 2.18, negro, "torreta_sup"),
        *cilindro("x", (1.25, 0.0), 0.22, 0.62, 0.86, negro, "torreta_lat"),
    ]


def holografica():
    """Mira holográfica (tipo EOTech EXPS3): ventana rectangular con retícula de anillo y punto."""
    return [
        C(-0.70, 0.00, -1.90, 0.70, 0.50, 1.50, negro, "base"),
        C(-0.86, 0.10, -1.30, -0.70, 0.42, -0.55, negro, "palanca_qd"),
        C(-0.75, 0.50, -1.90, 0.75, 1.10, -0.20, negro_mate, "carcasa_baterias"),
        C(-0.75, 0.50, -0.20, 0.75, 1.10, 1.40, negro, "carcasa_trasera"),
        C(-0.20, 0.50, 1.40, 0.20, 0.80, 1.55, negro, "botones"),
        C(-0.75, 1.10, -1.70, -0.55, 2.40, 1.30, negro, "poste_izq"),
        C(0.55, 1.10, -1.70, 0.75, 2.40, 1.30, negro, "poste_der"),
        C(-0.75, 2.40, -1.90, 0.75, 2.62, 1.40, negro, "capucha"),
        C(-0.55, 1.10, -1.20, 0.55, 2.40, -1.16, vidrio, "vidrio_frente"),
        C(-0.55, 1.10, 0.40, 0.55, 2.40, 0.44, lente((255, 36, 28), alfa=0.22), "vidrio_reticula"),
    ]


def acog():
    """Mira de aumento 4x (tipo Trijicon TA31) con fibra óptica arriba y retícula ámbar."""
    fibra = Material((120, 190, 60), ruido=0.01, desgaste=0, sombra=0, volumen=0.03)
    return [
        C(-0.55, 0.00, -1.60, 0.55, 0.50, 1.60, negro, "montaje"),
        *cilindro("z", (0.0, 1.40), 0.86, -2.70, -1.20, negro, "objetivo"),
        C(-0.62, 0.50, -1.20, 0.62, 2.05, 1.40, negro, "cuerpo"),
        *cilindro("z", (0.0, 1.40), 0.64, 1.40, 2.70, negro, "ocular"),
        C(-0.14, 2.05, -1.00, 0.14, 2.22, 1.20, fibra, "fibra_optica"),
        C(-0.62, 0.62, -2.72, 0.62, 2.18, -2.68, vidrio_oscuro, "lente_objetivo"),
        C(-0.45, 0.96, 2.70, 0.45, 1.84, 2.73, lente((255, 150, 30), alfa=0.45), "lente_ocular"),
    ]


def telescopica():
    """Mira telescópica larga con torretas (francotirador)."""
    return [
        C(-0.66, 0.00, -2.70, 0.66, 2.02, -2.10, negro, "anillo_del"),
        C(-0.66, 0.00, 1.70, 0.66, 2.02, 2.30, negro, "anillo_tras"),
        *cilindro("z", (0.0, 1.40), 0.55, -4.20, 3.80, negro, "tubo"),
        *cilindro("z", (0.0, 1.40), 0.72, -4.80, -4.20, negro, "cono"),
        *cilindro("z", (0.0, 1.40), 0.90, -6.60, -4.80, negro, "objetivo"),
        *cilindro("z", (0.0, 1.40), 0.74, 3.80, 6.00, negro, "ocular"),
        *cilindro("z", (0.0, 1.40), 0.80, 6.00, 6.40, negro_mate, "aro_ocular"),
        *cilindro("y", (0.0, 0.10), 0.38, 1.95, 2.65, negro_mate, "torreta_elevacion"),
        *cilindro("x", (1.40, 0.10), 0.38, 0.55, 1.25, negro_mate, "torreta_deriva"),
        C(-0.80, 0.70, -6.64, 0.80, 2.10, -6.60, vidrio_oscuro, "lente_objetivo"),
        C(-0.55, 0.85, 6.40, 0.55, 1.95, 6.43, Material((30, 42, 48), ruido=0, desgaste=0, sombra=0, volumen=0, alfa=0.7,
                                                        patrones=[reticula((10, 10, 10), anillo=False)]), "lente_ocular"),
    ]


# ---------------------------------------------------------------- boca

SILENCIADORES = {
    # radio, desde (sobre el cañón), hasta (hacia adelante)
    "rifle": (0.55, 0.9, -5.0),
    "subfusil": (0.52, 0.6, -4.6),
    "pistola": (0.42, 0.4, -3.6),
    "francotirador": (0.78, 0.6, -6.8),
}


def silenciador(tam):
    radio, desde, hasta = SILENCIADORES[tam]
    cuerpo = Material((44, 44, 46), desgaste=0.12, sombra=0.06,
                      patrones=[rayas(periodo=7, ancho=1, delta=-0.05)])
    tapa = Material((30, 30, 31), desgaste=0.10)
    return [
        *cilindro("z", (0.0, 0.0), radio, hasta + 0.35, desde, cuerpo, "silenciador"),
        *cilindro("z", (0.0, 0.0), radio * 0.92, hasta, hasta + 0.35, tapa, "tapa"),
    ]


# ---------------------------------------------------------------- rieles

def empunadura():
    """Empuñadura vertical con abrazadera de riel."""
    agarre = Material((30, 30, 32), desgaste=0.06, sombra=0.08, patrones=[punteado(0.35, -0.07)])
    return [
        C(-0.45, 0.00, -0.70, 0.45, 0.45, 0.70, negro, "abrazadera"),
        *cilindro("y", (0.0, 0.0), 0.44, 0.45, 2.95, agarre, "agarre"),
        *cilindro("y", (0.0, 0.0), 0.50, 2.95, 3.20, negro, "tapa"),
    ]


def laser():
    """Módulo láser/IR (tipo AN/PEQ-15) color tierra."""
    carcasa = fde.con(patrones=[frente_iluminado((60, 64, 66))])
    return [
        C(-0.50, 0.00, -1.10, 0.50, 0.32, 0.80, negro, "base"),
        C(-0.62, 0.32, -1.25, 0.62, 1.20, 0.95, carcasa, "carcasa"),
        C(-0.50, 0.50, -1.29, -0.10, 0.90, -1.25, Material((200, 20, 16), ruido=0, volumen=0), "emisor_visible"),
        C(0.10, 0.50, -1.29, 0.50, 0.90, -1.25, Material((20, 24, 26), ruido=0, volumen=0), "emisor_ir"),
        C(-0.30, 1.20, -0.20, 0.30, 1.32, 0.40, negro, "botones"),
    ]


def linterna():
    """Linterna táctica (tipo SureFire M600) con su montaje."""
    cuerpo = Material((32, 32, 34), desgaste=0.14, sombra=0.06)
    cabeza = cuerpo.con(patrones=[frente_iluminado((255, 246, 214))])
    return [
        C(-0.40, 0.00, -0.60, 0.40, 0.40, 0.60, negro, "montaje"),
        *cilindro("z", (0.0, 0.85), 0.42, -1.20, 1.40, cuerpo, "cuerpo"),
        *cilindro("z", (0.0, 0.85), 0.56, -2.00, -1.20, cabeza, "cabeza"),
        *cilindro("z", (0.0, 0.85), 0.36, 1.40, 1.75, negro, "interruptor"),
    ]


ACCESORIOS = {
    "punto_rojo": punto_rojo, "holografica": holografica, "acog": acog, "telescopica": telescopica,
    "empunadura": empunadura, "laser": laser, "linterna": linterna,
}


# ---------------------------------------------------------------- montaje

_ROTACION_LADO = {
    # Lleva +Y (lejos del riel) a la dirección del lado, girando alrededor del eje del cañón.
    "arriba": np.array([[1, 0, 0], [0, 1, 0], [0, 0, 1]]),
    "abajo": np.array([[-1, 0, 0], [0, -1, 0], [0, 0, 1]]),
    "derecha": np.array([[0, 1, 0], [-1, 0, 0], [0, 0, 1]]),
    "izquierda": np.array([[0, -1, 0], [1, 0, 0], [0, 0, 1]]),
}


def montar(cajas, pos, lado="arriba"):
    """Rota un accesorio al lado del riel y lo lleva a la posición del montaje."""
    m = _ROTACION_LADO[lado]
    pos = np.array(pos, dtype=float)
    salida = []
    for c in cajas:
        a, b = m @ c.desde + pos, m @ c.hasta + pos
        eje = None if c.eje is None else m @ c.eje
        salida.append(Caja(a, b, c.material, c.nombre, eje))
    return salida
