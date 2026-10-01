"""Vista previa de modelos del resource pack sin abrir Minecraft.

Rasteriza los cubos de un modelo con su textura y aproxima las cámaras del juego:
- "fp": primera persona (mano derecha, FOV 70, cámara mirando a -Z).
- "gui": ícono del inventario (proyección ortográfica).
- "libre": vista 3/4 para revisar la geometría.

Uso: python3 herramientas/vista_previa.py tresmodos:item/m4a1_fp fp salida.png
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

from modelado import CARAS

RAIZ_PACK = Path(__file__).resolve().parent.parent / "paquete-recursos"


def _ruta(recurso, carpeta, ext):
    ns, ruta = recurso.split(":", 1) if ":" in recurso else ("minecraft", recurso)
    return RAIZ_PACK / "assets" / ns / carpeta / f"{ruta}{ext}"


def cargar(recurso):
    modelo = json.loads(_ruta(recurso, "models", ".json").read_text())
    texturas = {}
    for k, v in modelo.get("textures", {}).items():
        texturas["#" + k] = np.asarray(Image.open(_ruta(v, "textures", ".png")).convert("RGBA"),
                                       dtype=float) / 255.0
    return modelo, texturas


def _rot(eje, grados):
    a = math.radians(grados)
    c, s = math.cos(a), math.sin(a)
    if eje == "x":
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if eje == "y":
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def _display(modelo, contexto):
    d = modelo.get("display", {}).get(contexto, {})
    t = np.array(d.get("translation", [0, 0, 0]), dtype=float) / 16.0
    r = d.get("rotation", [0, 0, 0])
    s = np.array(d.get("scale", [1, 1, 1]), dtype=float)
    rot = _rot("x", r[0]) @ _rot("y", r[1]) @ _rot("z", r[2])
    return lambda p: t + rot @ (s * p)


def quads(modelo):
    """Genera (4 esquinas 3D en unidades de bloque centradas, 4 uv, textura, normal)."""
    for el in modelo["elements"]:
        f, t = np.array(el["from"], float), np.array(el["to"], float)
        for cara, datos in el["faces"].items():
            normal, u, v = (np.array(x, float) for x in CARAS[cara])
            uv = datos["uv"]
            esquinas, uvs = [], []
            for a, b in ((0, 0), (1, 0), (1, 1), (0, 1)):
                p = np.where(normal > 0, t, np.where(normal < 0, f, 0.0))
                for eje_dir, frac in ((u, a), (v, b)):
                    k = int(np.argmax(np.abs(eje_dir)))
                    p[k] = f[k] + frac * (t[k] - f[k]) if eje_dir[k] > 0 else t[k] - frac * (t[k] - f[k])
                esquinas.append(p / 16.0 - 0.5)
                uvs.append((uv[0] + a * (uv[2] - uv[0]), uv[1] + b * (uv[3] - uv[1])))
            yield np.array(esquinas), np.array(uvs), datos["texture"], normal


def renderizar(recursos, modo, ancho=960, alto=540, fondo=None):
    """Dibuja uno o varios modelos superpuestos (arma + accesorios), como el modelo compuesto."""
    if isinstance(recursos, str):
        recursos = [recursos]
    cargados = [cargar(r) for r in recursos]
    modelo = cargados[0][0]
    img = np.zeros((alto, ancho, 3))
    if modo == "fp":
        # Cielo arriba, piso abajo, para juzgar la posición del arma.
        grad = np.linspace(0, 1, alto)[:, None, None]
        img[:] = (1 - grad) * np.array([0.55, 0.70, 0.95]) + grad * np.array([0.42, 0.40, 0.36])
    else:
        img[:] = fondo if fondo is not None else (0.55, 0.55, 0.58)
    zbuf = np.full((alto, ancho), np.inf)

    if modo == "fp":
        disp = _display(modelo, "firstperson_righthand")
        mano = np.array([0.56, -0.52, -0.72])
        a_vista = lambda p: mano + disp(p)
        fov = math.radians(70)
        foco = 1 / math.tan(fov / 2)
        aspecto = ancho / alto

        def proyectar(p):
            z = -p[..., 2]
            x = foco / aspecto * p[..., 0] / z
            y = foco * p[..., 1] / z
            return np.stack([(x + 1) / 2 * ancho, (1 - y) / 2 * alto, z], -1)
    elif modo == "gui":
        disp = _display(modelo, "gui")
        a_vista = disp
        lado = min(ancho, alto)

        def proyectar(p):
            return np.stack([(p[..., 0] + 0.5) * lado + (ancho - lado) / 2,
                             (0.5 - p[..., 1]) * lado + (alto - lado) / 2, -p[..., 2]], -1)
    else:  # libre: 3/4 desde arriba a la derecha, ortográfica
        rot = _rot("x", 25) @ _rot("y", -35)
        a_vista = lambda p: rot @ p
        lado = min(ancho, alto)

        def proyectar(p):
            return np.stack([(p[..., 0] * 0.75 + 0.5) * lado + (ancho - lado) / 2,
                             (0.5 - p[..., 1] * 0.75) * lado + (alto - lado) / 2, -p[..., 2]], -1)

    luz = np.array([0.35, 0.85, 0.45])
    luz /= np.linalg.norm(luz)
    todos = [(q, tx) for m, tx in cargados for q in quads(m)]
    for (esquinas, uvs, tex_id, normal), texturas in todos:
        tex = texturas[tex_id]
        th, tw = tex.shape[:2]
        vista = np.array([a_vista(p) for p in esquinas])
        n = a_vista(normal) - a_vista(np.zeros(3))
        n /= np.linalg.norm(n) + 1e-9
        brillo = 0.55 + 0.45 * max(0.0, float(np.dot(n, luz)))
        pant = proyectar(vista)
        if modo == "fp" and np.any(pant[:, 2] <= 0.05):
            continue
        for tri in ((0, 1, 2), (0, 2, 3)):
            P = pant[list(tri)]
            UV = uvs[list(tri)]
            x0, x1 = int(max(0, np.floor(P[:, 0].min()))), int(min(ancho - 1, np.ceil(P[:, 0].max())))
            y0, y1 = int(max(0, np.floor(P[:, 1].min()))), int(min(alto - 1, np.ceil(P[:, 1].max())))
            if x1 < x0 or y1 < y0:
                continue
            xs, ys = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            (ax, ay, _), (bx, by, _), (cx, cy, _) = P
            den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
            if abs(den) < 1e-9:
                continue
            l1 = ((by - cy) * (xs - cx) + (cx - bx) * (ys - cy)) / den
            l2 = ((cy - ay) * (xs - cx) + (ax - cx) * (ys - cy)) / den
            l3 = 1 - l1 - l2
            dentro = (l1 >= -1e-6) & (l2 >= -1e-6) & (l3 >= -1e-6)
            if not dentro.any():
                continue
            if modo == "fp":  # interpolación con corrección de perspectiva
                iz = l1 / P[0, 2] + l2 / P[1, 2] + l3 / P[2, 2]
                w1, w2, w3 = l1 / P[0, 2] / iz, l2 / P[1, 2] / iz, l3 / P[2, 2] / iz
                z = 1 / iz
            else:
                w1, w2, w3 = l1, l2, l3
                z = l1 * P[0, 2] + l2 * P[1, 2] + l3 * P[2, 2]
            u = w1 * UV[0, 0] + w2 * UV[1, 0] + w3 * UV[2, 0]
            v = w1 * UV[0, 1] + w2 * UV[1, 1] + w3 * UV[2, 1]
            px = np.clip((u / 16 * tw).astype(int), 0, tw - 1)
            py = np.clip((v / 16 * th).astype(int), 0, th - 1)
            color = tex[py, px]
            sub = zbuf[y0:y1 + 1, x0:x1 + 1]
            visible = dentro & (color[..., 3] > 0.1) & (z < sub)
            sub[visible] = z[visible]
            img[y0:y1 + 1, x0:x1 + 1][visible] = color[visible][:, :3] * brillo
    if modo == "fp":  # mira en el centro
        cx, cy = ancho // 2, alto // 2
        img[cy, cx - 6:cx + 7] = 1
        img[cy - 6:cy + 7, cx] = 1
    return Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))


if __name__ == "__main__":
    recurso, modo, salida = sys.argv[1], sys.argv[2], sys.argv[3]
    renderizar(recurso, modo).save(salida)
    print("ok", salida)
