"""Generador de modelos de cubos para el resource pack.

Un modelo se define como una lista de cajas en el "marco canónico" de un arma:
el cañón apunta a -Z, arriba es +Y y el lado derecho del arma es +X. A partir de
esa definición se exportan variantes rotadas (primera persona, tercera persona e
inventario), así cada contexto usa su propio modelo sin rotaciones en "display".

La textura se pinta por código: cada cara de cada caja recibe una región del
atlas y un material la pinta (color, ruido, desgaste de bordes y patrones).
"""
import json
import math
import zlib
from pathlib import Path

import numpy as np
from PIL import Image

# Ejes de UV que usa Minecraft para cada cara: (normal, dirección de +u, dirección de +v).
CARAS = {
    "north": ((0, 0, -1), (-1, 0, 0), (0, -1, 0)),
    "south": ((0, 0, 1), (1, 0, 0), (0, -1, 0)),
    "east": ((1, 0, 0), (0, 0, -1), (0, -1, 0)),
    "west": ((-1, 0, 0), (0, 0, 1), (0, -1, 0)),
    "up": ((0, 1, 0), (1, 0, 0), (0, 0, 1)),
    "down": ((0, -1, 0), (1, 0, 0), (0, 0, -1)),
}

# Orientaciones. Cada una lleva los vectores canónicos (adelante, arriba, derecha)
# a su marco: matriz 3x3 aplicada a coordenadas centradas en (8, 8, 8).
ORIENTACIONES = {
    # Primera persona: el marco del ítem coincide con la vista (la cámara mira a -Z).
    "fp": np.array([[1, 0, 0], [0, 1, 0], [0, 0, 1]]),
    # Tercera persona: en la mano, +Y del ítem apunta hacia adelante y +Z hacia arriba.
    "tp": np.array([[1, 0, 0], [0, 0, -1], [0, 1, 0]]),
    # Inventario: perfil derecho visto desde +Z, cañón hacia la derecha (+X).
    "gui": np.array([[0, 0, -1], [0, 1, 0], [1, 0, 0]]),
}

ADELANTE = np.array([0, 0, -1])
ARRIBA = np.array([0, 1, 0])
DERECHA = np.array([1, 0, 0])


def _v(t):
    return np.array(t, dtype=float)


class Caja:
    """Caja alineada a los ejes, en coordenadas de modelo (1 unidad = 1/16 de bloque)."""

    def __init__(self, desde, hasta, material, nombre=""):
        a, b = _v(desde), _v(hasta)
        self.desde = np.minimum(a, b)
        self.hasta = np.maximum(a, b)
        self.material = material
        self.nombre = nombre


class Material:
    """Pinta una región del atlas.

    color: RGB base. ruido: desvío del ruido por píxel (0-1). desgaste: aclarado de
    los bordes superiores. sombra: oscurecido de los bordes inferiores. patrones:
    funciones extra que reciben (region, info).
    """

    def __init__(self, color, ruido=0.035, desgaste=0.10, sombra=0.10, patrones=()):
        self.color = np.array(color, dtype=float) / 255.0
        self.ruido = ruido
        self.desgaste = desgaste
        self.sombra = sombra
        self.patrones = patrones

    def pintar(self, reg, info):
        h, w = reg.shape[:2]
        rng = np.random.default_rng(info["semilla"])
        base = np.empty((h, w, 3))
        base[:] = self.color
        # Ruido fino más una variación suave de baja frecuencia (manchas).
        base += rng.normal(0, self.ruido, (h, w, 1))
        if h > 2 and w > 2:
            gruesa = rng.normal(0, self.ruido * 0.6, (max(1, h // 3), max(1, w // 3), 1))
            gruesa = np.kron(gruesa, np.ones((3, 3, 1)))[:h, :w]
            if gruesa.shape[:2] == (h, w):
                base += gruesa
        reg[..., :3] = base
        reg[..., 3] = 1.0
        if self.desgaste:
            borde(reg, info, info["arriba"], +self.desgaste)
        if self.sombra:
            borde(reg, info, -info["arriba"], -self.sombra)
        for p in self.patrones:
            p(reg, info)
        np.clip(reg, 0, 1, out=reg)


# ---------------------------------------------------------------- patrones

def _eje_en_cara(info, direccion):
    """Devuelve ('u'|'v', signo) si la dirección está en el plano de la cara."""
    du = float(np.dot(info["u"], direccion))
    dv = float(np.dot(info["v"], direccion))
    if abs(du) > 0.5:
        return "u", math.copysign(1, du)
    if abs(dv) > 0.5:
        return "v", math.copysign(1, dv)
    return None, 0


def borde(reg, info, direccion, delta, ancho=1):
    """Aclara (delta > 0) u oscurece el borde de la región que mira hacia `direccion`."""
    eje, signo = _eje_en_cara(info, direccion)
    if eje is None:
        return
    h, w = reg.shape[:2]
    if eje == "u":
        cols = slice(w - ancho, w) if signo > 0 else slice(0, ancho)
        reg[:, cols, :3] += delta
    else:
        filas = slice(h - ancho, h) if signo > 0 else slice(0, ancho)
        reg[filas, :, :3] += delta


def rayas(periodo=2, ancho=1, delta=-0.12, direccion=None, solo_normal=None):
    """Rayas perpendiculares a `direccion` (por defecto, el eje del cañón).

    solo_normal: si se indica, solo pinta en las caras cuya normal coincide.
    """

    def patron(reg, info):
        if solo_normal is not None and float(np.dot(info["normal"], info[solo_normal])) < 0.5:
            return
        d = info[direccion] if direccion else info["adelante"]
        eje, _ = _eje_en_cara(info, d)
        if eje is None:
            return
        h, w = reg.shape[:2]
        n = w if eje == "u" else h
        for i in range(n):
            if i % periodo < ancho:
                if eje == "u":
                    reg[:, i, :3] += delta
                else:
                    reg[i, :, :3] += delta

    return patron


def ranuras(largo=4, separacion=2, margen=1, delta=-0.22, solo_lados=True):
    """Ranuras alargadas (tipo M-LOK) a lo largo del eje del cañón, en las caras laterales."""

    def patron(reg, info):
        if solo_lados and abs(float(np.dot(info["normal"], info["derecha"]))) < 0.5:
            return
        eje, _ = _eje_en_cara(info, info["adelante"])
        if eje is None:
            return
        h, w = reg.shape[:2]
        largo_px = largo
        paso = largo_px + separacion
        if eje == "u":
            if h < 3:
                return
            fila = slice(h // 2 - (1 if h >= 5 else 0), h // 2 + 1)
            x = margen
            while x + largo_px <= w - margen:
                reg[fila, x:x + largo_px, :3] += delta
                x += paso
        else:
            if w < 3:
                return
            col = slice(w // 2 - (1 if w >= 5 else 0), w // 2 + 1)
            y = margen
            while y + largo_px <= h - margen:
                reg[y:y + largo_px, col, :3] += delta
                y += paso

    return patron


def punteado(densidad=0.35, delta=-0.10):
    """Textura de agarre (stippling)."""

    def patron(reg, info):
        rng = np.random.default_rng(info["semilla"] + 7)
        h, w = reg.shape[:2]
        m = rng.random((h, w)) < densidad
        reg[m, :3] += delta

    return patron


def rectangulo(centro, tam, delta, solo_normal="derecha"):
    """Rectángulo plano pintado en las caras cuya normal coincide con `solo_normal`.

    centro y tam en fracciones (0-1) de la cara, en ejes (adelante, arriba).
    """

    def patron(reg, info):
        if float(np.dot(info["normal"], info[solo_normal])) < 0.5:
            return
        h, w = reg.shape[:2]
        eje_a, signo_a = _eje_en_cara(info, info["adelante"])
        eje_b, signo_b = _eje_en_cara(info, info["arriba"])
        if eje_a is None or eje_b is None:
            return
        n_a = w if eje_a == "u" else h
        n_b = w if eje_b == "u" else h
        # En la dirección positiva del eje, la fracción 1 cae al final del rango.
        fa = centro[0] if signo_a > 0 else 1 - centro[0]
        fb = centro[1] if signo_b > 0 else 1 - centro[1]
        a0 = int(round((fa - tam[0] / 2) * n_a))
        a1 = int(round((fa + tam[0] / 2) * n_a))
        b0 = int(round((fb - tam[1] / 2) * n_b))
        b1 = int(round((fb + tam[1] / 2) * n_b))
        a0, b0 = max(a0, 0), max(b0, 0)
        if eje_a == "u":
            reg[b0:b1, a0:a1, :3] += delta
        else:
            reg[a0:a1, b0:b1, :3] += delta

    return patron


# ---------------------------------------------------------------- exportación

def _orientar(cajas, orient, desplazamiento):
    m = ORIENTACIONES[orient]
    centro = np.array([8.0, 8.0, 8.0])
    salida = []
    for c in cajas:
        esquinas = [m @ (_v(p) - centro) + centro + desplazamiento
                    for p in (c.desde, c.hasta)]
        nueva = Caja(esquinas[0], esquinas[1], c.material, c.nombre)
        salida.append(nueva)
    return salida, {
        "adelante": m @ ADELANTE,
        "arriba": m @ ARRIBA,
        "derecha": m @ DERECHA,
    }


def _tam_cara(caja, cara, densidad):
    d = caja.hasta - caja.desde
    if cara in ("north", "south"):
        ancho, alto = d[0], d[1]
    elif cara in ("east", "west"):
        ancho, alto = d[2], d[1]
    else:
        ancho, alto = d[0], d[2]
    return max(1, int(round(ancho * densidad))), max(1, int(round(alto * densidad))), ancho, alto


def _empacar(rects, lado):
    """Empaquetado por estantes. rects: lista de (w, h). Devuelve posiciones o None."""
    orden = sorted(range(len(rects)), key=lambda i: -rects[i][1])
    pos = [None] * len(rects)
    x = y = alto_fila = 0
    for i in orden:
        w, h = rects[i]
        if w > lado:
            return None
        if x + w > lado:
            x, y = 0, y + alto_fila
            alto_fila = 0
        if y + h > lado:
            return None
        pos[i] = (x, y)
        x += w
        alto_fila = max(alto_fila, h)
    return pos


def exportar(nombre, cajas, variantes, carpeta_pack, densidad=6, namespace="tresmodos"):
    """Escribe la textura del atlas y un modelo por variante.

    variantes: dict nombre_variante -> {"orientacion": "fp"|"tp"|"gui",
    "desplazamiento": (x, y, z), "display": {...}, "gui_light": "front"|"side"}.
    Devuelve dict variante -> ruta del modelo ("namespace:item/...").
    """
    carpeta_pack = Path(carpeta_pack)
    preparadas = {}
    rects = []
    caras_info = []
    for vnombre, cfg in variantes.items():
        orientadas, ejes = _orientar(cajas, cfg["orientacion"], _v(cfg.get("desplazamiento", (0, 0, 0))))
        preparadas[vnombre] = (orientadas, ejes)
        for idx, c in enumerate(orientadas):
            for cara, (normal, u, v) in CARAS.items():
                w, h, ancho, alto = _tam_cara(c, cara, densidad)
                if ancho <= 1e-6 or alto <= 1e-6:
                    continue
                # Semilla estable por caja y cara semántica, igual en todas las variantes.
                n = _v(normal)
                semantica = ("adelante" if np.dot(n, ejes["adelante"]) > 0.5 else
                             "atras" if np.dot(n, ejes["adelante"]) < -0.5 else
                             "arriba" if np.dot(n, ejes["arriba"]) > 0.5 else
                             "abajo" if np.dot(n, ejes["arriba"]) < -0.5 else
                             "derecha" if np.dot(n, ejes["derecha"]) > 0.5 else "izquierda")
                semilla = zlib.crc32(f"{nombre}/{idx}/{semantica}".encode())
                rects.append((w, h))
                caras_info.append((vnombre, idx, cara, semilla))

    lado = 16
    while True:
        pos = _empacar(rects, lado)
        if pos is not None:
            break
        lado *= 2
        if lado > 1024:
            raise ValueError("El atlas no entra en 1024 px")

    atlas = np.zeros((lado, lado, 4))
    uvs = {}
    for (w, h), (x, y), (vnombre, idx, cara, semilla) in zip(rects, pos, caras_info):
        orientadas, ejes = preparadas[vnombre]
        c = orientadas[idx]
        normal, u, v = (_v(t) for t in CARAS[cara])
        info = {"normal": normal, "u": u, "v": v, "semilla": semilla, **ejes}
        c.material.pintar(atlas[y:y + h, x:x + w], info)
        f = 16.0 / lado
        uvs[(vnombre, idx, cara)] = [round(x * f, 4), round(y * f, 4),
                                     round((x + w) * f, 4), round((y + h) * f, 4)]

    tex_rel = f"{namespace}:item/{nombre}"
    tex_path = carpeta_pack / "assets" / namespace / "textures" / "item" / f"{nombre}.png"
    tex_path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray((atlas * 255).round().astype(np.uint8), "RGBA").save(tex_path, optimize=True)

    rutas = {}
    for vnombre, cfg in variantes.items():
        orientadas, _ = preparadas[vnombre]
        elementos = []
        for idx, c in enumerate(orientadas):
            caras = {}
            for cara in CARAS:
                if (vnombre, idx, cara) in uvs:
                    caras[cara] = {"uv": uvs[(vnombre, idx, cara)], "texture": "#0"}
            for p in (*c.desde, *c.hasta):
                if p < -16 or p > 32:
                    raise ValueError(f"{nombre}/{vnombre}: la caja {c.nombre!r} sale del rango -16..32")
            elementos.append({
                "name": c.nombre or f"caja{idx}",
                "from": [round(float(t), 4) for t in c.desde],
                "to": [round(float(t), 4) for t in c.hasta],
                "faces": caras,
            })
        modelo = {
            "textures": {"0": tex_rel, "particle": tex_rel},
            "elements": elementos,
            "display": cfg.get("display", {}),
            "gui_light": cfg.get("gui_light", "front"),
        }
        arch = f"{nombre}_{vnombre}" if vnombre else nombre
        ruta = carpeta_pack / "assets" / namespace / "models" / "item" / f"{arch}.json"
        ruta.parent.mkdir(parents=True, exist_ok=True)
        ruta.write_text(json.dumps(modelo, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
        rutas[vnombre] = f"{namespace}:item/{arch}"
    return rutas


def definicion_item(nombre, rutas, carpeta_pack, namespace="tresmodos"):
    """Escribe assets/<ns>/items/<nombre>.json eligiendo el modelo según el contexto."""
    casos = []
    if "fp" in rutas:
        casos.append({"when": ["firstperson_righthand", "firstperson_lefthand"],
                      "model": {"type": "minecraft:model", "model": rutas["fp"]}})
    if "tp" in rutas:
        casos.append({"when": ["thirdperson_righthand", "thirdperson_lefthand"],
                      "model": {"type": "minecraft:model", "model": rutas["tp"]}})
    definicion = {
        "model": {
            "type": "minecraft:select",
            "property": "minecraft:display_context",
            "cases": casos,
            "fallback": {"type": "minecraft:model", "model": rutas["gui"]},
        }
    }
    ruta = Path(carpeta_pack) / "assets" / namespace / "items" / f"{nombre}.json"
    ruta.parent.mkdir(parents=True, exist_ok=True)
    ruta.write_text(json.dumps(definicion, indent=2) + "\n", encoding="utf-8")
