"""Genera los modelos, texturas y glifos del HUD dentro de paquete-recursos/.

Uso: python3 herramientas/generar.py
Requiere Pillow y numpy (pip install -r herramientas/requirements.txt).
"""
from pathlib import Path

import hud
from armas import ARMAS
from modelado import exportar, definicion_item

PACK = Path(__file__).resolve().parent.parent / "paquete-recursos"

# Posición de cada arma en cada contexto. "desplazamiento" mueve la geometría (en unidades
# de modelo) para que la empuñadura quede en la mano (tp) o el arma centrada (gui).
CONFIG = {
    "m4a1": {
        "fp": {"translation": [-5.5, 3.6, 2.0], "scale": 0.9},
        "tp": {"desplazamiento": (0, 0.8, 3.0), "translation": [0, 0, 0.5], "scale": 0.5},
        "gui": {"desplazamiento": (-6, 1, 0), "scale": 0.47},
    },
}


def variantes(cfg):
    fp, tp, gui = cfg["fp"], cfg["tp"], cfg["gui"]
    mano_fp = {"translation": fp["translation"], "scale": [fp["scale"]] * 3}
    mano_tp = {"translation": tp["translation"], "scale": [tp["scale"]] * 3}
    s = gui["scale"]
    return {
        "fp": {"orientacion": "fp",
               "display": {"firstperson_righthand": mano_fp, "firstperson_lefthand": mano_fp}},
        "tp": {"orientacion": "tp", "desplazamiento": tp["desplazamiento"],
               "display": {"thirdperson_righthand": mano_tp, "thirdperson_lefthand": mano_tp}},
        "gui": {"orientacion": "gui", "desplazamiento": gui["desplazamiento"], "display": {
            "gui": {"scale": [s, s, s]},
            "ground": {"translation": [0, 2, 0], "scale": [s * 0.6] * 3},
            "fixed": {"scale": [s * 1.1] * 3},
            "head": {"translation": [0, 10, 0], "scale": [s] * 3},
        }},
    }


def main():
    hud.main()
    for nombre, crear in ARMAS.items():
        rutas = exportar(nombre, crear(), variantes(CONFIG[nombre]), PACK, densidad=5)
        definicion_item(nombre, rutas, PACK)
        print(f"{nombre}: {', '.join(rutas.values())}")


if __name__ == "__main__":
    main()
