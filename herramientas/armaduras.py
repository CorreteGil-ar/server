"""Armaduras del RPG: texturas propias (assets de equipo) para cada set.

Cada set es un asset `tresmodos:<set>` con dos capas de 64 × 32, como las de vanilla:
- humanoid: yelmo (cabeza y su capa exterior), peto con brazos y botas (la parte baja de la pierna);
- humanoid_leggings: cintura y piernas.
El plugin pone el componente `equippable` con ese asset en cada pieza (Armaduras.java).

Estilos: placas de metal (remaches y líneas), cuero (costuras y correas), tela (pliegues y ribete)
y laminado (filas de láminas atadas, para el ronin).
"""
import json
from pathlib import Path

import numpy as np
from PIL import Image

# Caras de cada caja en la textura: (u, v, ancho, alto)
def _caras(u, v, w, h, d):
    return {"arriba": (u + d, v, w, d), "abajo": (u + d + w, v, w, d), "der": (u, v + d, d, h),
            "frente": (u + d, v + d, w, h), "izq": (u + d + w, v + d, d, h), "atras": (u + d + w + d, v + d, w, h)}


CABEZA = _caras(0, 0, 8, 8, 8)
CAPA = _caras(32, 0, 8, 8, 8)
TORSO = _caras(16, 16, 8, 12, 4)
BRAZO = _caras(40, 16, 4, 12, 4)
PIERNA = _caras(0, 16, 4, 12, 4)

SETS = {
    # nombre: (estilo, color base, color de acento, color oscuro)
    "caballero": ("placas", (150, 152, 158), (150, 40, 36), (70, 72, 78)),
    "verdugo": ("placas", (84, 80, 78), (110, 70, 40), (40, 36, 34)),
    "ronin": ("laminado", (40, 44, 70), (180, 140, 60), (20, 22, 36)),
    "cazador": ("cuero", (96, 66, 40), (70, 92, 50), (56, 38, 24)),
    "hechicero": ("tela", (52, 34, 74), (170, 130, 60), (30, 20, 44)),
    "clerigo": ("tela", (214, 204, 176), (190, 150, 60), (150, 140, 116)),
    "seda": ("tela", (236, 234, 228), (180, 40, 40), (190, 188, 182)),
    "abismo": ("placas", (40, 36, 48), (150, 70, 210), (20, 18, 26)),
    "carmesi": ("cuero", (34, 26, 28), (140, 16, 22), (18, 14, 16)),
}


class Lienzo:
    def __init__(self, semilla):
        self.px = np.zeros((32, 64, 4))
        self.rng = np.random.default_rng(semilla)

    def rect(self, cara, color, ruido=0.05, desde=0, hasta=None):
        """Pinta la cara (filas desde..hasta) con el color, ruido y luz de arriba a abajo."""
        u, v, w, h = cara
        hasta = h if hasta is None else hasta
        c = np.array(color, float) / 255
        for y in range(v + desde, v + hasta):
            luz = 1.12 - 0.24 * (y - v) / max(1, h - 1)
            for x in range(u, u + w):
                n = 1 + self.rng.normal(0, ruido)
                self.px[y, x, :3] = np.clip(c * luz * n, 0, 1)
                self.px[y, x, 3] = 1

    def punto(self, x, y, color, a=1.0):
        if 0 <= x < 64 and 0 <= y < 32:
            self.px[y, x, :3] = np.array(color, float) / 255
            self.px[y, x, 3] = a

    def fila(self, cara, fila, color, paso=1, desde=0):
        u, v, w, h = cara
        if not 0 <= fila < h:
            return
        for x in range(u + desde, u + w, paso):
            self.punto(x, v + fila, color)

    def columna(self, cara, col, color, desde=0, hasta=None, paso=1):
        u, v, w, h = cara
        hasta = h if hasta is None else hasta
        if not 0 <= col < w:
            return
        for y in range(v + desde, v + hasta, paso):
            self.punto(u + col, y, color)

    def borde(self, cara, color, filas=(0,), cols=()):
        for f in filas:
            self.fila(cara, f if f >= 0 else cara[3] + f, color)
        for c in cols:
            self.columna(cara, c if c >= 0 else cara[2] + c, color)

    def guardar(self, ruta):
        ruta.parent.mkdir(parents=True, exist_ok=True)
        Image.fromarray((self.px * 255).round().astype(np.uint8), "RGBA").save(ruta, optimize=True)


def _aclarar(c, k):
    return tuple(int(min(255, max(0, v * k))) for v in c)


def _caja(l, caras, estilo, base, acento, oscuro, filas=None):
    """Pinta todas las caras de una caja con el estilo; filas=(desde, hasta) limita en alto (botas)."""
    for nombre, cara in caras.items():
        u, v, w, h = cara
        desde, hasta = (0, h) if filas is None or nombre in ("arriba", "abajo") else filas
        l.rect(cara, base, desde=desde, hasta=hasta)
        if nombre in ("arriba", "abajo"):
            continue
        if estilo == "placas":
            for f in range(desde + 3, hasta, 4):
                l.fila(cara, f, _aclarar(oscuro, 0.9))
            for f in range(desde + 2, hasta, 4):
                l.fila(cara, f, _aclarar(base, 1.25), paso=2)
            l.punto(u, v + desde, _aclarar(base, 1.5))
            l.punto(u + w - 1, v + desde, _aclarar(base, 1.5))
        elif estilo == "cuero":
            l.columna(cara, 0, _aclarar(oscuro, 1.1), desde, hasta, paso=2)
            l.columna(cara, w - 1, _aclarar(oscuro, 1.1), desde, hasta, paso=2)
            if hasta - desde > 6:
                l.fila(cara, desde + (hasta - desde) // 2, oscuro)
                l.fila(cara, desde + (hasta - desde) // 2 + 1, _aclarar(acento, 0.9))
        elif estilo == "tela":
            for c in range(1, w, 3):
                l.columna(cara, c, _aclarar(base, 0.82), desde, hasta)
            l.fila(cara, hasta - 1, acento)
        elif estilo == "laminado":
            for f in range(desde, hasta):
                if (f - desde) % 2 == 0:
                    l.fila(cara, f, _aclarar(base, 1.18), paso=2, desde=(f // 2) % 2)
                else:
                    l.fila(cara, f, acento, paso=3)


def humanoid(nombre, estilo, base, acento, oscuro, semilla):
    l = Lienzo(semilla)
    # Yelmo: cabeza con una ranura (o capucha para la tela) y la capa exterior con la cimera.
    _caja(l, CABEZA, "placas" if estilo in ("placas", "laminado") else estilo, base, acento, oscuro)
    u, v, w, h = CABEZA["frente"]
    if estilo == "tela":
        # Capucha: la cara queda en sombra.
        for y in range(v + 2, v + 8):
            for x in range(u + 1, u + 7):
                l.punto(x, y, (18, 16, 20))
        l.fila(CABEZA["frente"], 1, acento)
    elif estilo == "cuero":
        for y in range(v + 4, v + 8):
            for x in range(u + 1, u + 7):
                l.punto(x, y, _aclarar(oscuro, 0.7))
        l.fila(CABEZA["frente"], 3, (20, 18, 18))
    else:
        l.fila(CABEZA["frente"], 3, (12, 12, 14))
        l.fila(CABEZA["frente"], 4, (12, 12, 14), paso=2)
        l.columna(CABEZA["frente"], 3, _aclarar(base, 1.3), 5, 8)
        l.columna(CABEZA["frente"], 4, _aclarar(base, 1.3), 5, 8)
    # Capa exterior: solo una cresta (o el ala del sombrero del ronin) para no tapar todo.
    cu, cv, cw, ch = CAPA["arriba"]
    if estilo in ("placas", "laminado"):
        for y in range(cv, cv + ch):
            l.punto(cu + 3, y, acento)
            l.punto(cu + 4, y, _aclarar(acento, 0.8))
    # Peto y brazos.
    _caja(l, TORSO, estilo, base, acento, oscuro)
    _caja(l, BRAZO, estilo, base, acento, oscuro)
    u, v, w, h = TORSO["frente"]
    if estilo == "placas":
        # Emblema en el pecho.
        for y in range(v + 2, v + 6):
            l.punto(u + 3, y, acento)
            l.punto(u + 4, y, acento)
        l.fila(TORSO["frente"], 3, acento, desde=2)
    elif estilo == "tela":
        l.columna(TORSO["frente"], 3, acento, 0, 12)
        l.columna(TORSO["frente"], 4, acento, 0, 12)
    elif estilo == "cuero":
        for i in range(8):
            l.punto(u + i, v + min(11, i + 1), oscuro)
    l.fila(TORSO["frente"], 9, oscuro)
    l.fila(TORSO["atras"], 9, oscuro)
    # Hombreras.
    l.fila(BRAZO["der"], 0, _aclarar(acento, 0.9))
    l.fila(BRAZO["der"], 1, _aclarar(base, 1.2))
    # Botas: solo los 5 de abajo de la pierna.
    _caja(l, PIERNA, "placas" if estilo == "placas" else "cuero", _aclarar(base, 0.85), acento, oscuro, filas=(7, 12))
    return l


def leggings(nombre, estilo, base, acento, oscuro, semilla):
    l = Lienzo(semilla + 1)
    # Cintura: la parte baja del torso (cinturón).
    for cara in TORSO.values():
        u, v, w, h = cara
        if cara in (TORSO["arriba"], TORSO["abajo"]):
            continue
        l.rect(cara, _aclarar(base, 0.9), desde=8)
        l.fila(cara, 8, oscuro)
        l.fila(cara, 9, _aclarar(acento, 0.9) if estilo != "tela" else oscuro, paso=1)
    _caja(l, PIERNA, estilo, _aclarar(base, 0.92), acento, oscuro)
    l.fila(PIERNA["frente"], 5, _aclarar(base, 1.3) if estilo == "placas" else oscuro)
    return l


def main(pack):
    raiz = Path(pack) / "assets" / "tresmodos"
    for i, (nombre, (estilo, base, acento, oscuro)) in enumerate(SETS.items()):
        humanoid(nombre, estilo, base, acento, oscuro, 50 + i).guardar(
            raiz / "textures" / "entity" / "equipment" / "humanoid" / f"{nombre}.png")
        leggings(nombre, estilo, base, acento, oscuro, 90 + i).guardar(
            raiz / "textures" / "entity" / "equipment" / "humanoid_leggings" / f"{nombre}.png")
        equipo = {"layers": {"humanoid": [{"texture": f"tresmodos:{nombre}"}],
                             "humanoid_leggings": [{"texture": f"tresmodos:{nombre}"}]}}
        ruta = raiz / "equipment" / f"{nombre}.json"
        ruta.parent.mkdir(parents=True, exist_ok=True)
        ruta.write_text(json.dumps(equipo, indent=1) + "\n", encoding="utf-8")
    print(f"armaduras: {len(SETS)} sets")


if __name__ == "__main__":
    main(Path(__file__).resolve().parent.parent / "paquete-recursos")
