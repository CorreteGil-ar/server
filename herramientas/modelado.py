"""Generador de modelos de cubos para el resource pack.

Un modelo se define como una lista de cajas en el "marco canónico" de un arma:
el cañón apunta a -Z, arriba es +Y y el lado derecho del arma es +X. A partir de
esa definición se exportan variantes rotadas (primera persona, tercera persona e
inventario), así cada contexto usa su propio modelo sin rotaciones en "display".

La textura se pinta por código: cada cara de cada caja recibe una región del
atlas y un material la pinta (color, ruido, volumen, desgaste de bordes y patrones).
Las piezas redondas (cañones, tubos, miras) se arman con `cilindro`, que superpone
dos cajas en cruz y las sombrea como un cilindro.
"""
import itertools
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
EJES = {"x": np.array([1, 0, 0]), "y": np.array([0, 1, 0]), "z": np.array([0, 0, 1])}


def _v(t):
    return np.array(t, dtype=float)


class Caja:
    """Caja alineada a los ejes, en coordenadas de modelo (1 unidad = 1/16 de bloque).

    eje: si la caja es parte de un cilindro, el eje del cilindro ("x", "y" o "z");
    el material la sombrea redondeada alrededor de ese eje.
    """

    def __init__(self, desde, hasta, material, nombre="", eje=None, densidad=None, luz=0, caras=None):
        a, b = _v(desde), _v(hasta)
        self.desde = np.minimum(a, b)
        self.hasta = np.maximum(a, b)
        self.material = material
        self.nombre = nombre
        self.eje = EJES[eje] if isinstance(eje, str) else eje
        self.densidad = densidad  # píxeles por unidad de esta caja (None = la del modelo)
        self.luz = luz            # light_emission: retículas que se ven de noche
        self.caras = caras        # caras a exportar (None = todas), en el marco de la variante

    def copia(self, desde, hasta, eje=None):
        """Misma caja (material, nombre, densidad, luz, caras) con otras esquinas y eje."""
        return Caja(desde, hasta, self.material, self.nombre, eje, self.densidad, self.luz, self.caras)

    def mover(self, d):
        return self.copia(self.desde + _v(d), self.hasta + _v(d), self.eje)


def engrosar(cajas, k, centro_x=8.0):
    """Ensancha las piezas a lo ancho (eje X) sin cambiar el largo ni la altura.

    Los cilindros siguen redondos: su radio crece k veces en los dos ejes de la sección,
    alrededor de su propio centro. Las posiciones a lo ancho se escalan desde centro_x,
    así lo montado a los costados sigue apoyado sobre la pieza ensanchada.
    """
    salida = []
    for c in cajas:
        a, b = c.desde.copy(), c.hasta.copy()
        a[0], b[0] = centro_x + (a[0] - centro_x) * k, centro_x + (b[0] - centro_x) * k
        if c.eje is not None:
            for i in range(1, 3):
                if abs(c.eje[i]) < 0.5:
                    m = (a[i] + b[i]) / 2
                    a[i], b[i] = m + (a[i] - m) * k, m + (b[i] - m) * k
        salida.append(c.copia(a, b, c.eje))
    return salida


def cilindro(eje, centro, radio, desde, hasta, material, nombre=""):
    """Cilindro aproximado con dos cajas en cruz (sección casi octogonal).

    eje: "x", "y" o "z". centro: coordenadas de los otros dos ejes, en orden (x, y, z)
    salteando el eje. desde/hasta: extremos sobre el eje.
    """
    otros = [k for k in "xyz" if k != eje]
    ancho, angosto = radio, radio * 0.62
    cajas = []
    # La segunda caja es apenas más corta para que sus tapas no coincidan con las de la primera.
    for (r1, r2), inset in (((ancho, angosto), 0.0), ((angosto, ancho), 0.015)):
        lo, hi = {}, {}
        lo[eje], hi[eje] = desde + inset, hasta - inset
        lo[otros[0]], hi[otros[0]] = centro[0] - r1, centro[0] + r1
        lo[otros[1]], hi[otros[1]] = centro[1] - r2, centro[1] + r2
        cajas.append(Caja([lo[k] for k in "xyz"], [hi[k] for k in "xyz"], material, nombre, eje))
    return cajas


class Material:
    """Pinta una región del atlas.

    color: RGB base. ruido: desvío del ruido por píxel (0-1). desgaste: aclarado de
    los bordes superiores. sombra: oscurecido de los bordes inferiores. volumen:
    degradé de luz de arriba hacia abajo. alfa: opacidad (vidrios). patrones:
    funciones extra que reciben (region, info).
    """

    def __init__(self, color, ruido=0.016, desgaste=0.10, sombra=0.10, patrones=(), volumen=0.07, alfa=1.0):
        self.color = np.array(color, dtype=float) / 255.0
        self.ruido = ruido
        self.desgaste = desgaste
        self.sombra = sombra
        self.patrones = patrones
        self.volumen = volumen
        self.alfa = alfa

    def con(self, **cambios):
        """Copia del material con algunos parámetros cambiados."""
        m = Material.__new__(Material)
        m.__dict__.update(self.__dict__)
        for k, v in cambios.items():
            setattr(m, k, np.array(v, dtype=float) / 255.0 if k == "color" else v)
        return m

    def pintar(self, reg, info):
        h, w = reg.shape[:2]
        rng = np.random.default_rng(info["semilla"])
        base = np.empty((h, w, 3))
        base[:] = self.color
        # Ruido fino más una variación suave de baja frecuencia (manchas del acabado).
        base += rng.normal(0, self.ruido, (h, w, 1))
        if h > 3 and w > 3:
            gruesa = rng.normal(0, self.ruido * 0.7, (h // 4 + 1, w // 4 + 1, 1))
            base += np.kron(gruesa, np.ones((4, 4, 1)))[:h, :w]
        reg[..., :3] = base
        reg[..., 3] = self.alfa
        if self.volumen:
            gradiente(reg, info, info["arriba"], self.volumen)
        if info.get("eje_cil") is not None:
            redondear(reg, info, info["eje_cil"])
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


def _coordenada(reg, info, direccion):
    """Matriz (h, w) con la posición de cada píxel a lo largo de `direccion`, de 0 a 1."""
    eje, signo = _eje_en_cara(info, direccion)
    if eje is None:
        return None
    h, w = reg.shape[:2]
    n = w if eje == "u" else h
    t = (np.arange(n) + 0.5) / n
    if signo < 0:
        t = 1 - t
    return np.tile(t, (h, 1)) if eje == "u" else np.tile(t[:, None], (1, w))


def gradiente(reg, info, direccion, intensidad):
    """Más claro hacia `direccion` y más oscuro del lado opuesto (luz cenital)."""
    t = _coordenada(reg, info, direccion)
    if t is not None:
        reg[..., :3] += ((t - 0.55) * 2 * intensidad)[..., None]


def redondear(reg, info, eje_cil):
    """Sombreado de cilindro: brillo en el centro de la cara y bordes más oscuros."""
    if abs(float(np.dot(info["normal"], eje_cil))) > 0.5:
        return  # tapa del cilindro
    otro = np.cross(info["normal"], eje_cil)
    t = _coordenada(reg, info, otro)
    if t is None:
        return
    reg[..., :3] += (0.10 * np.cos((t - 0.5) * math.pi) - 0.06)[..., None]
    # Brillo especular fino si la cara mira hacia arriba o al costado.
    if float(np.dot(info["normal"], info["arriba"])) > 0.5:
        reg[..., :3] += (0.08 * np.exp(-((t - 0.42) / 0.08) ** 2))[..., None]


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


def _cara_es(info, nombre_eje, signo=1):
    return float(np.dot(info["normal"], info[nombre_eje])) * signo > 0.5


def rayas(periodo=2, ancho=1, delta=-0.12, direccion=None, solo_normal=None, signo_normal=1):
    """Rayas perpendiculares a `direccion` (por defecto, el eje del cañón).

    solo_normal: si se indica ("arriba", "derecha"...), solo pinta en las caras cuya
    normal coincide (con signo_normal = -1, en la opuesta).
    """

    def patron(reg, info):
        if solo_normal is not None and not _cara_es(info, solo_normal, signo_normal):
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


def picatinny(superior="arriba", signo=1, paso=3):
    """Dientes de riel Picatinny: ranuras transversales con canto iluminado.

    superior: dirección semántica hacia la que mira la cara útil del riel.
    """

    def patron(reg, info):
        sup = info[superior] * signo
        eje, _ = _eje_en_cara(info, info["adelante"])
        if eje is None:
            return
        h, w = reg.shape[:2]
        n = w if eje == "u" else h
        mascara = None
        if float(np.dot(info["normal"], sup)) > 0.5:
            mascara = np.ones((h, w), bool)
        elif abs(float(np.dot(info["normal"], sup))) < 0.5:
            # Costado del riel: los dientes se ven en la mitad cercana a la cara útil.
            t = _coordenada(reg, info, sup)
            if t is None:
                return
            mascara = t > 0.45
        if mascara is None:
            return
        for i in range(n):
            fase = i % paso
            delta = -0.22 if fase == 0 else (0.07 if fase == 1 else 0.0)
            if eje == "u":
                reg[mascara[:, i], i, :3] += delta
            else:
                reg[i, mascara[i, :], :3] += delta

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
        paso = largo + separacion
        if eje == "u":
            if h < 3:
                return
            fila = slice(h // 2 - (1 if h >= 5 else 0), h // 2 + 1)
            x = margen
            while x + largo <= w - margen:
                reg[fila, x:x + largo, :3] += delta
                x += paso
        else:
            if w < 3:
                return
            col = slice(w // 2 - (1 if w >= 5 else 0), w // 2 + 1)
            y = margen
            while y + largo <= h - margen:
                reg[y:y + largo, col, :3] += delta
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


def _rect_en_cara(reg, info, centro, tam):
    """Píxeles de un rectángulo dado en fracciones (adelante, arriba) de la cara."""
    h, w = reg.shape[:2]
    ta = _coordenada(reg, info, info["adelante"])
    tb = _coordenada(reg, info, info["arriba"])
    if ta is None or tb is None:
        return None
    return ((np.abs(ta - centro[0]) <= tam[0] / 2) & (np.abs(tb - centro[1]) <= tam[1] / 2))


def rectangulo(centro, tam, delta, solo_normal="derecha", signo_normal=1, color=None):
    """Rectángulo plano pintado en las caras cuya normal coincide con `solo_normal`.

    centro y tam en fracciones (0-1) de la cara, en ejes (adelante, arriba): adelante 1 es
    el extremo de la boca. Con `color` reemplaza el color en vez de sumar `delta`.
    """

    def patron(reg, info):
        if not _cara_es(info, solo_normal, signo_normal):
            return
        m = _rect_en_cara(reg, info, centro, tam)
        if m is None:
            return
        if color is not None:
            reg[m, :3] = np.array(color) / 255.0
        else:
            reg[m, :3] += delta

    return patron


def puntos(posiciones, solo_normal="derecha", signo_normal=1, delta=-0.25):
    """Tornillos o pernos: punto oscuro con un brillo arriba. Posiciones en fracciones."""

    def patron(reg, info):
        if not _cara_es(info, solo_normal, signo_normal):
            return
        ta = _coordenada(reg, info, info["adelante"])
        tb = _coordenada(reg, info, info["arriba"])
        if ta is None or tb is None:
            return
        h, w = reg.shape[:2]
        for fa, fb in posiciones:
            d = (ta - fa) ** 2 * w * w + (tb - fb) ** 2 * h * h
            i = np.unravel_index(np.argmin(d), d.shape)
            reg[i[0], i[1], :3] += delta
            arriba = _eje_en_cara(info, info["arriba"])
            if arriba[0] == "v":
                j = i[0] + (1 if arriba[1] > 0 else -1)
                if 0 <= j < h:
                    reg[j, i[1], :3] += 0.12

    return patron


def reticula(color=(255, 40, 30), anillo=True, punto=0.06, radio_anillo=0.32):
    """Retícula de mira: punto central y, opcional, anillo (holográfica).

    punto y radio_anillo: radios en fracción del lado menor de la cara.
    """

    def patron(reg, info):
        if abs(float(np.dot(info["normal"], info["adelante"]))) < 0.5:
            return
        h, w = reg.shape[:2]
        yy, xx = np.mgrid[0:h, 0:w]
        cy, cx = (h - 1) / 2, (w - 1) / 2
        r = np.sqrt((yy - cy) ** 2 + (xx - cx) ** 2)
        c = np.array(color) / 255.0
        centro = r <= max(0.6, min(h, w) * punto)
        reg[centro, :3] = c
        reg[centro, 3] = 1
        if anillo:
            radio = min(h, w) * radio_anillo
            ar = np.abs(r - radio) <= 0.55
            reg[ar, :3] = c
            reg[ar, 3] = 0.95

    return patron


# ---------------------------------------------------------------- exportación

def _orientar(cajas, orient, desplazamiento):
    m = ORIENTACIONES[orient]
    centro = np.array([8.0, 8.0, 8.0])
    salida = []
    for c in cajas:
        esquinas = [m @ (_v(p) - centro) + centro + desplazamiento for p in (c.desde, c.hasta)]
        eje = None if c.eje is None else m @ c.eje
        salida.append(c.copia(esquinas[0], esquinas[1], eje))
    return salida, {"adelante": m @ ADELANTE, "arriba": m @ ARRIBA, "derecha": m @ DERECHA}


def _tam_cara(caja, cara, densidad):
    densidad = caja.densidad or densidad
    d = caja.hasta - caja.desde
    if cara in ("north", "south"):
        ancho, alto = d[0], d[1]
    elif cara in ("east", "west"):
        ancho, alto = d[2], d[1]
    else:
        ancho, alto = d[0], d[2]
    return max(1, int(round(ancho * densidad))), max(1, int(round(alto * densidad))), ancho, alto


def _empacar(rects, ancho):
    """Empaquetado por estantes con ancho fijo. Devuelve (posiciones, alto usado) o None."""
    orden = sorted(range(len(rects)), key=lambda i: (-rects[i][1], -rects[i][0]))
    pos = [None] * len(rects)
    x = y = alto_fila = 0
    for i in orden:
        w, h = rects[i]
        if w > ancho:
            return None
        if x + w > ancho:
            x, y = 0, y + alto_fila
            alto_fila = 0
        pos[i] = (x, y)
        x += w
        alto_fila = max(alto_fila, h)
    return pos, y + alto_fila


def superposiciones(cajas):
    """Pares de caras coplanares con la misma orientación que se pisan (parpadean en el juego)."""
    avisos = []
    for (i, a), (j, b) in itertools.combinations(enumerate(cajas), 2):
        for k in range(3):
            o = [x for x in range(3) if x != k]
            for lado in ("desde", "hasta"):
                if abs(getattr(a, lado)[k] - getattr(b, lado)[k]) > 1e-6:
                    continue
                solape = all(min(a.hasta[x], b.hasta[x]) - max(a.desde[x], b.desde[x]) > 1e-4 for x in o)
                if solape:
                    avisos.append(f"{a.nombre or i} y {b.nombre or j} ({'xyz'[k]} {lado})")
    return avisos


def exportar(nombre, cajas, variantes, carpeta_pack, densidad=8, namespace="tresmodos", textura=None):
    """Escribe la textura del atlas y un modelo por variante.

    variantes: dict nombre_variante -> {"orientacion": "fp"|"tp"|"gui",
    "desplazamiento": (x, y, z), "display": {...}, "gui_light": "front"|"side"}.
    textura: nombre del PNG (por defecto, `nombre`); los accesorios lo comparten entre armas.
    Devuelve dict variante -> recurso del modelo ("namespace:item/...").
    """
    for aviso in superposiciones(cajas):
        print(f"  aviso {nombre}: caras superpuestas en {aviso}")
    carpeta_pack = Path(carpeta_pack)
    textura = textura or nombre
    preparadas = {}
    rects, caras_info = [], []
    for vnombre, cfg in variantes.items():
        orientadas, ejes = _orientar(cajas, cfg["orientacion"], _v(cfg.get("desplazamiento", (0, 0, 0))))
        preparadas[vnombre] = (orientadas, ejes)
        for idx, c in enumerate(orientadas):
            for cara, (normal, _, _) in CARAS.items():
                if c.caras is not None and cara not in c.caras:
                    continue
                w, h, ancho, alto = _tam_cara(c, cara, densidad)
                if ancho <= 1e-6 or alto <= 1e-6:
                    continue
                n = _v(normal)
                semantica = next(nom for nom, vec, s in (
                    ("adelante", ejes["adelante"], 1), ("atras", ejes["adelante"], -1),
                    ("arriba", ejes["arriba"], 1), ("abajo", ejes["arriba"], -1),
                    ("derecha", ejes["derecha"], 1), ("izquierda", ejes["derecha"], -1))
                    if np.dot(n, vec) * s > 0.5)
                semilla = zlib.crc32(f"{textura}/{idx}/{semantica}".encode())
                rects.append((w, h))
                caras_info.append((vnombre, idx, cara, semilla))

    mejor = None
    for ancho in (16, 32, 64, 128, 256, 512, 1024):
        r = _empacar(rects, ancho)
        if r is None:
            continue
        pos, usado = r
        alto = 16
        while alto < usado:
            alto *= 2
        if alto > 1024:
            continue
        clave = (ancho * alto, abs(math.log2(ancho / alto)))
        if mejor is None or clave < mejor[0]:
            mejor = (clave, ancho, alto, pos)
    if mejor is None:
        raise ValueError(f"{nombre}: el atlas no entra en 1024 px")
    _, tex_w, tex_h, pos = mejor

    atlas = np.zeros((tex_h, tex_w, 4))
    uvs = {}
    for (w, h), (x, y), (vnombre, idx, cara, semilla) in zip(rects, pos, caras_info):
        orientadas, ejes = preparadas[vnombre]
        c = orientadas[idx]
        normal, u, v = (_v(t) for t in CARAS[cara])
        info = {"normal": normal, "u": u, "v": v, "semilla": semilla, "eje_cil": c.eje, **ejes}
        c.material.pintar(atlas[y:y + h, x:x + w], info)
        uvs[(vnombre, idx, cara)] = [round(x * 16 / tex_w, 4), round(y * 16 / tex_h, 4),
                                     round((x + w) * 16 / tex_w, 4), round((y + h) * 16 / tex_h, 4)]

    tex_rel = f"{namespace}:item/{textura}"
    tex_path = carpeta_pack / "assets" / namespace / "textures" / "item" / f"{textura}.png"
    tex_path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray((atlas * 255).round().astype(np.uint8), "RGBA").save(tex_path, optimize=True)

    rutas = {}
    for vnombre, cfg in variantes.items():
        orientadas, _ = preparadas[vnombre]
        elementos = []
        for idx, c in enumerate(orientadas):
            caras = {cara: {"uv": uvs[(vnombre, idx, cara)], "texture": "#0"}
                     for cara in CARAS if (vnombre, idx, cara) in uvs}
            for p in (*c.desde, *c.hasta):
                if p < -16 or p > 32:
                    raise ValueError(f"{nombre}/{vnombre}: la caja {c.nombre!r} sale del rango -16..32 ({p:.2f})")
            elemento = {
                "name": c.nombre or f"caja{idx}",
                "from": [round(float(t), 4) for t in c.desde],
                "to": [round(float(t), 4) for t in c.hasta],
                "faces": caras,
            }
            if c.luz:
                elemento["light_emission"] = c.luz
            elementos.append(elemento)
        modelo = {
            "textures": {"0": tex_rel, "particle": tex_rel},
            "elements": elementos,
            "display": cfg.get("display", {}),
            "gui_light": cfg.get("gui_light", "front"),
        }
        arch = f"{nombre}_{vnombre}" if vnombre else nombre
        ruta = carpeta_pack / "assets" / namespace / "models" / "item" / f"{arch}.json"
        ruta.parent.mkdir(parents=True, exist_ok=True)
        ruta.write_text(json.dumps(modelo, separators=(",", ":"), ensure_ascii=False) + "\n", encoding="utf-8")
        rutas[vnombre] = f"{namespace}:item/{arch}"
    return rutas


# ---------------------------------------------------------------- definiciones de ítems

CONTEXTOS = {
    "fp": ["firstperson_righthand", "firstperson_lefthand"],
    "tp": ["thirdperson_righthand", "thirdperson_lefthand"],
}


def _modelo(recurso):
    return {"type": "minecraft:model", "model": recurso}


def _escribir_item(nombre, definicion, carpeta_pack, namespace):
    ruta = Path(carpeta_pack) / "assets" / namespace / "items" / f"{nombre}.json"
    ruta.parent.mkdir(parents=True, exist_ok=True)
    ruta.write_text(json.dumps(definicion, indent=1) + "\n", encoding="utf-8")


def definicion_arma(nombre, base, ranuras, carpeta_pack, ads=None, namespace="tresmodos"):
    """Item con el arma base más una ranura por accesorio, según el contexto de dibujo.

    base: dict variante -> recurso. ranuras: lista ordenada (índice en los strings de
    custom_model_data) de dicts {"opciones": {valor: rutas}, "defecto": rutas | None}.
    La ranura 0 es la mira.

    ads: apuntado en primera persona, que el plugin activa con el flag 0 de
    custom_model_data. Dict {"opciones": {valor_mira: modo}, "defecto": modo}, donde modo es
    {"variante": "ads_x"} (el arma entera con la transformación de esa mira; las rutas deben
    tener esa variante) o {"visor": recurso} (solo la vista a través de la óptica).
    """

    def compuesto(v, mira=None):
        modelos = [_modelo(base[v])]
        for i, ranura in enumerate(ranuras):
            defecto = ranura.get("defecto")
            if i == 0 and mira is not None:
                rutas = ranura["opciones"].get(mira) or defecto
                modelos.append(_modelo(rutas[v]) if rutas else {"type": "minecraft:empty"})
                continue
            modelos.append({
                "type": "minecraft:select",
                "property": "minecraft:custom_model_data",
                "index": i,
                "cases": [{"when": valor, "model": _modelo(rutas[v])}
                          for valor, rutas in ranura["opciones"].items()],
                "fallback": _modelo(defecto[v]) if defecto else {"type": "minecraft:empty"},
            })
        return {"type": "minecraft:composite", "models": modelos}

    def apuntando(valor, modo):
        if "visor" in modo:
            return _modelo(modo["visor"])
        return compuesto(modo["variante"], mira=valor)

    primera = compuesto("fp")
    if ads:
        primera = {
            "type": "minecraft:condition",
            "property": "minecraft:custom_model_data",
            "index": 0,
            "on_true": {
                "type": "minecraft:select",
                "property": "minecraft:custom_model_data",
                "index": 0,
                "cases": [{"when": valor, "model": apuntando(valor, modo)}
                          for valor, modo in ads["opciones"].items() if valor],
                "fallback": apuntando("", ads["defecto"]),
            },
            "on_false": primera,
        }
    definicion = {"model": {
        "type": "minecraft:select",
        "property": "minecraft:display_context",
        "cases": [{"when": CONTEXTOS["fp"], "model": primera},
                  {"when": CONTEXTOS["tp"], "model": compuesto("tp")}],
        "fallback": compuesto("gui"),
    }}
    if ads:
        # Al apuntar cambia el ítem (el flag): sin esto el arma baja y sube cada vez.
        definicion["hand_animation_on_swap"] = False
    _escribir_item(nombre, definicion, carpeta_pack, namespace)


def modelo_hijo(nombre, padre, display, carpeta_pack, namespace="tresmodos"):
    """Modelo que hereda geometría y textura de `padre` y solo cambia la transformación."""
    ruta = Path(carpeta_pack) / "assets" / namespace / "models" / "item" / f"{nombre}.json"
    ruta.parent.mkdir(parents=True, exist_ok=True)
    ruta.write_text(json.dumps({"parent": padre, "display": display}, separators=(",", ":")) + "\n",
                    encoding="utf-8")
    return f"{namespace}:item/{nombre}"


def definicion_simple(nombre, recurso, carpeta_pack, namespace="tresmodos"):
    """Item que siempre usa el mismo modelo (íconos de menú)."""
    _escribir_item(nombre, {"model": _modelo(recurso)}, carpeta_pack, namespace)
