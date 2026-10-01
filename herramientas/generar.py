"""Genera los modelos, texturas y glifos del HUD dentro de paquete-recursos/.

Uso: python3 herramientas/generar.py
Requiere Pillow y numpy (pip install -r herramientas/requirements.txt).

Cada arma se exporta en partes (cuerpo, cargadores, dispositivo de boca, miras de hierro
y un modelo por accesorio montado) y la definición del ítem las combina según los
strings de custom_model_data que pone el plugin (ver Accesorios.java):
  0 mira · 1 boca · 2 bajo · 3 láser · 4 linterna · 5 cargador
"""
import json
from pathlib import Path

import numpy as np

import hud
from accesorios import ACCESORIOS, montar, silenciador
from armas import ARMAS
from modelado import ORIENTACIONES, definicion_arma, definicion_simple, exportar

PACK = Path(__file__).resolve().parent.parent / "paquete-recursos"
RANURAS = ["mira", "boca", "bajo", "laser", "linterna", "cargador"]


def _caja_limites(cajas):
    lo = np.min([c.desde for c in cajas], axis=0)
    hi = np.max([c.hasta for c in cajas], axis=0)
    return lo, hi


def _en_variante(m, p):
    return m @ (np.asarray(p, float) - 8) + 8


def planificar(arma, base, todas):
    """Variantes de exportación con desplazamientos que hacen entrar todo en -16..32.

    La traslación de "display" compensa el desplazamiento: el arma queda donde dice
    arma.config sin importar cuánto se haya movido la geometría.
    """
    cfg = arma.config
    lo_b, hi_b = _caja_limites(base)
    lo_t, hi_t = _caja_limites(todas)
    centro_base = (lo_b + hi_b) / 2
    centro_todo = (lo_t + hi_t) / 2
    variantes = {}
    for v in ("fp", "tp", "gui"):
        m = ORIENTACIONES[v]
        d = 8 - _en_variante(m, centro_todo)       # centra el conjunto completo
        if v == "fp":
            s = cfg["fp"]["scale"]
            t = np.array(cfg["fp"]["translation"], float) - s * d
            mano = {"translation": [round(x, 3) for x in t], "scale": [s] * 3}
            display = {"firstperson_righthand": mano, "firstperson_lefthand": mano}
        elif v == "tp":
            s = cfg["tp"]["scale"]
            agarre = _en_variante(m, cfg["tp"]["agarre"]) + d
            t = np.array(cfg["tp"]["translation"], float) - s * (agarre - 8)
            mano = {"translation": [round(x, 3) for x in t], "scale": [s] * 3}
            display = {"thirdperson_righthand": mano, "thirdperson_lefthand": mano}
        else:
            s = cfg["gui"]["scale"]
            c = _en_variante(m, centro_base) + d
            display = {}
            for contexto, escala, extra in (("gui", s, (0, 0, 0)), ("ground", s * 0.6, (0, 2, 0)),
                                            ("fixed", s * 1.1, (0, 0, 0)), ("head", s, (0, 10, 0))):
                t = np.array(extra, float) - escala * (c - 8)
                display[contexto] = {"translation": [round(x, 3) for x in t], "scale": [round(escala, 4)] * 3}
        variantes[v] = {"orientacion": v, "desplazamiento": tuple(d), "display": display}
    return variantes


def accesorio_montado(arma, clave):
    """Cajas del accesorio `clave` ya montado en el arma, y la textura que comparte."""
    if clave == "silenciador":
        mont = arma.montajes["boca"]
        return montar(silenciador(arma.silenciador), mont["pos"], mont["lado"]), f"silenciador_{arma.silenciador}"
    ranura = {"punto_rojo": "optica", "holografica": "optica", "acog": "optica", "telescopica": "optica",
              "empunadura": "inferior", "laser": "laser", "linterna": "linterna"}[clave]
    mont = arma.montajes[ranura]
    cajas = montar(ACCESORIOS[clave](), mont["pos"], mont["lado"])
    if ranura == "optica":
        cajas += arma.mira_plegada  # el alza se pliega bajo la óptica
        return cajas, f"{arma.nombre}_mira_{clave}"
    return cajas, f"acc_{clave}_{mont['lado']}"


def generar_arma(arma):
    partes = {"cuerpo": arma.cuerpo}
    for tipo, cajas in arma.cargador.items():
        partes[f"cargador_{tipo}"] = cajas
    if arma.boca:
        partes["boca"] = arma.boca
    if arma.mira_hierro:
        partes["mira_hierro"] = arma.mira_hierro
    accesorios = {}
    for ranura in RANURAS:
        for valor in arma.opciones.get(ranura, [""]):
            if valor and ranura != "cargador":
                accesorios[valor] = accesorio_montado(arma, valor)
    if arma.mira_defecto and arma.mira_defecto not in accesorios:
        accesorios[arma.mira_defecto] = accesorio_montado(arma, arma.mira_defecto)

    base = arma.cuerpo + arma.cargador["normal"] + arma.boca
    todas = [c for cajas in partes.values() for c in cajas] + [c for cajas, _ in accesorios.values() for c in cajas]
    variantes = planificar(arma, base, todas)

    rutas = {}
    for parte, cajas in partes.items():
        nombre = arma.nombre if parte == "cuerpo" else f"{arma.nombre}_{parte}"
        rutas[parte] = exportar(nombre, cajas, variantes, PACK)
    for valor, (cajas, textura) in accesorios.items():
        rutas[valor] = exportar(f"{arma.nombre}_{valor}", cajas, variantes, PACK, textura=textura)

    ranuras = []
    for ranura in RANURAS:
        opciones = {v: rutas[v] for v in arma.opciones.get(ranura, [""]) if v and ranura != "cargador"}
        if ranura == "mira":
            defecto = rutas.get(arma.mira_defecto) if arma.mira_defecto else rutas.get("mira_hierro")
        elif ranura == "boca":
            defecto = rutas.get("boca")
        elif ranura == "cargador":
            defecto = rutas["cargador_normal"]
            if "ampliado" in arma.opciones.get("cargador", []):
                opciones = {"ampliado": rutas["cargador_ampliado"]}
        else:
            defecto = None
        ranuras.append({"opciones": opciones, "defecto": defecto})
    definicion_arma(arma.nombre, rutas["cuerpo"], ranuras, PACK)
    return {r: arma.opciones.get(r, [""]) for r in RANURAS}


def generar_iconos():
    """Íconos de los accesorios para el menú del armero."""
    piezas = {clave: crear() for clave, crear in ACCESORIOS.items()}
    piezas["silenciador"] = silenciador("rifle")
    piezas["cargador"] = ARMAS["m4a1"]().cargador["ampliado"]
    for clave, cajas in piezas.items():
        lo, hi = _caja_limites(cajas)
        centro = (lo + hi) / 2
        d = 8 - _en_variante(ORIENTACIONES["gui"], centro)
        escala = round(min(1.0, 13.0 / float(np.max(hi - lo))), 3)
        variantes = {"": {"orientacion": "gui", "desplazamiento": tuple(d), "display": {
            "gui": {"rotation": [20, 0, 0], "scale": [escala] * 3},
            "ground": {"scale": [round(escala * 0.5, 3)] * 3}, "fixed": {"scale": [escala] * 3}}}}
        rutas = exportar(f"icono_{clave}", cajas, variantes, PACK)
        definicion_simple(f"icono_{clave}", rutas[""], PACK)


def main():
    hud.main()
    # Se regenera todo: se borran los modelos, texturas e ítems generados antes.
    for carpeta in ("models/item", "textures/item", "items"):
        for f in (PACK / "assets" / "tresmodos" / carpeta).glob("*"):
            f.unlink()
    resumen = {}
    for nombre, crear in ARMAS.items():
        resumen[nombre] = generar_arma(crear())
        print(f"{nombre}: listo")
    generar_iconos()
    print("íconos de accesorios: listo")
    (Path(__file__).resolve().parent / "accesorios_por_arma.json").write_text(
        json.dumps(resumen, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")


if __name__ == "__main__":
    main()
