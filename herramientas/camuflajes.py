"""Camuflajes de las armas (Camuflaje.java): repintan el cuerpo del arma con otro material.

El atlas del camuflaje sale de exportar las mismas cajas con materiales distintos: mismas caras y
mismos tamaños, así que el empaquetado y las uv coinciden con el del cuerpo original. Cada modelo
del cuerpo (primera y tercera persona, inventario y apuntado) tiene un hijo que solo cambia la
textura.
"""
import numpy as np

from modelado import Caja, Material

ORDEN = ["bosque", "desierto", "urbano", "tigre", "digital", "oro", "diamante", "atomico"]


def _luz(reg):
    """Sombreado del material original (para conservar volumen y desgaste)."""
    base = reg[..., :3].mean(axis=-1, keepdims=True)
    return np.clip(base / max(1e-3, float(np.mean(base))), 0.55, 1.45)


def _suave(g):
    return (g + np.roll(g, 1, 0) + np.roll(g, 1, 1) + np.roll(np.roll(g, 1, 0), 1, 1)) / 4


def manchas(colores, celda=3, umbrales=(0.55, 0.68, 0.80)):
    cols = [np.array(c, float) / 255 for c in colores]

    def patron(reg, info):
        h, w = reg.shape[:2]
        rng = np.random.default_rng(info["semilla"] ^ 0xCA0)
        luz = _luz(reg)
        for col, u in zip(cols, umbrales):
            g = _suave(rng.random((h // celda + 2, w // celda + 2)))
            m = np.kron(g > u * 0.92, np.ones((celda, celda)))[:h, :w].astype(bool)
            reg[..., :3] = np.where(m[..., None], col * luz, reg[..., :3])
    return patron


def rayas_tigre(color, periodo=5.0):
    col = np.array(color, float) / 255

    def patron(reg, info):
        h, w = reg.shape[:2]
        rng = np.random.default_rng(info["semilla"] ^ 0x716E)
        fase = rng.random() * 10
        y, x = np.mgrid[0:h, 0:w]
        ruido = _suave(rng.random((h, w))) * 2.2
        v = np.sin((x * 0.9 + y * 0.45 + ruido + fase) * 2 * np.pi / periodo)
        m = v > 0.45
        reg[..., :3] = np.where(m[..., None], col * _luz(reg), reg[..., :3])
    return patron


def pixeles(colores, celda=1, probs=(0.3, 0.25, 0.2)):
    cols = [np.array(c, float) / 255 for c in colores]

    def patron(reg, info):
        h, w = reg.shape[:2]
        rng = np.random.default_rng(info["semilla"] ^ 0xD161)
        luz = _luz(reg)
        r = rng.random((h // celda + 1, w // celda + 1))
        r = np.kron(r, np.ones((celda, celda)))[:h, :w]
        acum = 0
        for col, pr in zip(cols, probs):
            m = (r >= acum) & (r < acum + pr)
            reg[..., :3] = np.where(m[..., None], col * luz, reg[..., :3])
            acum += pr
    return patron


def brillo(color_brillo, densidad=0.06):
    """Reflejos en diagonal y chispas (oro y diamante)."""
    col = np.array(color_brillo, float) / 255

    def patron(reg, info):
        h, w = reg.shape[:2]
        rng = np.random.default_rng(info["semilla"] ^ 0xB1)
        y, x = np.mgrid[0:h, 0:w]
        diag = ((x + y) % 7 == 0)
        reg[..., :3] = np.where(diag[..., None], reg[..., :3] * 0.6 + col * 0.4, reg[..., :3])
        chispas = rng.random((h, w)) < densidad
        reg[..., :3] = np.where(chispas[..., None], col, reg[..., :3])
    return patron


def vetas(color):
    col = np.array(color, float) / 255

    def patron(reg, info):
        h, w = reg.shape[:2]
        rng = np.random.default_rng(info["semilla"] ^ 0xA70)
        g = _suave(_suave(rng.random((h + 2, w + 2))))[:h, :w]
        m = np.abs(g - 0.5) < 0.035
        reg[..., :3] = np.where(m[..., None], col, reg[..., :3])
    return patron


CAMOS = {
    "bosque": ((92, 104, 62), [manchas([(58, 70, 38), (106, 84, 54), (32, 32, 28)])]),
    "desierto": ((196, 170, 122), [manchas([(158, 126, 86), (216, 198, 154), (118, 94, 62)])]),
    "urbano": ((132, 134, 138), [manchas([(86, 88, 94), (186, 188, 192), (40, 42, 46)])]),
    "tigre": ((112, 122, 72), [rayas_tigre((36, 40, 28))]),
    "digital": ((92, 100, 74), [pixeles([(122, 130, 98), (60, 66, 48), (150, 140, 112)])]),
    "oro": ((214, 168, 58), [brillo((255, 236, 150))]),
    "diamante": ((112, 222, 232), [brillo((240, 255, 255), 0.08)]),
    "atomico": ((96, 255, 70), [vetas((20, 110, 20)), brillo((210, 255, 200), 0.04)]),
}


def repintar(cajas, camo):
    """Mismas cajas con el material del camuflaje (conserva los patrones del original: rieles, estrías)."""
    color, patrones = CAMOS[camo]
    metal = camo in ("oro", "diamante")
    salida = []
    for c in cajas:
        m = c.material
        nuevo = m.con(color=color, patrones=list(m.patrones) + patrones,
                      desgaste=0.2 if metal else m.desgaste, ruido=0.01 if metal else m.ruido)
        salida.append(Caja(c.desde, c.hasta, nuevo, c.nombre, c.eje, c.densidad, c.luz, c.caras))
    return salida
