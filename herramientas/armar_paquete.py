"""Valida paquete-recursos/ y lo empaqueta en target/tresmodos-pack.zip.

Solo usa la biblioteca estándar, así corre en CI sin instalar nada.
Uso: python3 herramientas/armar_paquete.py
"""
import hashlib
import json
import struct
import sys
import zipfile
from pathlib import Path

RAIZ = Path(__file__).resolve().parent.parent
PACK = RAIZ / "paquete-recursos"
SALIDA = RAIZ / "target" / "tresmodos-pack.zip"

errores = []


def error(msg):
    errores.append(msg)


def ruta_recurso(recurso, carpeta, ext):
    ns, ruta = recurso.split(":", 1) if ":" in recurso else ("minecraft", recurso)
    return PACK / "assets" / ns / carpeta / f"{ruta}{ext}", ns


def png_tam(ruta):
    with open(ruta, "rb") as f:
        cab = f.read(24)
    if cab[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError("no es un PNG")
    return struct.unpack(">II", cab[16:24])


def cargar_json(ruta):
    try:
        return json.loads(ruta.read_text(encoding="utf-8"))
    except (ValueError, UnicodeDecodeError) as e:
        error(f"{ruta.relative_to(RAIZ)}: JSON inválido ({e})")
        return None


TIPOS_MODELO = {"model", "composite", "select", "condition", "empty", "range_dispatch", "special", "bundle/selected_item"}


def validar_nodo(nodo, rel):
    """Estructura de la definición de ítem: cada tipo con los campos que exige."""
    if not isinstance(nodo, dict):
        error(f"{rel}: modelo de ítem que no es un objeto")
        return
    tipo = nodo.get("type", "").removeprefix("minecraft:")
    if tipo not in TIPOS_MODELO:
        error(f"{rel}: tipo de modelo desconocido {nodo.get('type')!r}")
        return
    hijos = []
    if tipo == "model" and "model" not in nodo:
        error(f"{rel}: modelo sin 'model'")
    elif tipo == "composite":
        hijos = nodo.get("models", [])
    elif tipo == "select":
        if "property" not in nodo or not isinstance(nodo.get("cases"), list):
            error(f"{rel}: select sin 'property' o 'cases'")
        for caso in nodo.get("cases", []):
            if "when" not in caso or "model" not in caso:
                error(f"{rel}: caso de select sin 'when' o 'model'")
            elif caso["when"] == "" or caso["when"] == []:
                error(f"{rel}: caso de select con 'when' vacío")
            hijos.append(caso.get("model"))
        if "fallback" in nodo:
            hijos.append(nodo["fallback"])
    elif tipo == "condition":
        if "property" not in nodo or "on_true" not in nodo or "on_false" not in nodo:
            error(f"{rel}: condition sin 'property', 'on_true' u 'on_false'")
        hijos = [nodo.get("on_true"), nodo.get("on_false")]
    for h in hijos:
        if h is not None:
            validar_nodo(h, rel)


def modelos_de_item(nodo, acc):
    if isinstance(nodo, dict):
        if nodo.get("type") in ("minecraft:model", "model") and "model" in nodo:
            acc.add(nodo["model"])
        for v in nodo.values():
            modelos_de_item(v, acc)
    elif isinstance(nodo, list):
        for v in nodo:
            modelos_de_item(v, acc)


def validar_modelo(recurso, vistos):
    if recurso in vistos:
        return
    vistos.add(recurso)
    ruta, ns = ruta_recurso(recurso, "models", ".json")
    if not ruta.exists():
        if ns != "minecraft":
            error(f"falta el modelo {recurso}")
        return
    m = cargar_json(ruta)
    if m is None:
        return
    rel = ruta.relative_to(RAIZ)
    texturas = m.get("textures", {})
    for k, v in texturas.items():
        if v.startswith("#"):
            continue
        tex, tns = ruta_recurso(v, "textures", ".png")
        if not tex.exists() and tns != "minecraft":
            error(f"{rel}: falta la textura {v}")
    for el in m.get("elements", []):
        for p in el["from"] + el["to"]:
            if not -16 <= p <= 32:
                error(f"{rel}: coordenada {p} fuera de -16..32 en {el.get('name', '?')}")
        luz = el.get("light_emission", 0)
        if not (isinstance(luz, int) and 0 <= luz <= 15):
            error(f"{rel}: light_emission {luz!r} fuera de 0..15 en {el.get('name', '?')}")
        rot = el.get("rotation")
        if rot and rot.get("angle") not in (-45, -22.5, 0, 22.5, 45):
            error(f"{rel}: ángulo {rot.get('angle')} no permitido en {el.get('name', '?')}")
        for cara, datos in el.get("faces", {}).items():
            if any(not 0 <= u <= 16 for u in datos.get("uv", [])):
                error(f"{rel}: uv fuera de 0..16 en {el.get('name', '?')}/{cara}")
            t = datos.get("texture", "")
            if t.startswith("#") and t[1:] not in texturas:
                error(f"{rel}: la cara usa {t} pero no está en textures")
    for contexto, d in m.get("display", {}).items():
        if any(abs(x) > 80 for x in d.get("translation", [])):
            error(f"{rel}: translation de {contexto} fuera de ±80")
        if any(x > 4 for x in d.get("scale", [])):
            error(f"{rel}: scale de {contexto} mayor a 4")
    if "parent" in m:
        validar_modelo(m["parent"], vistos)


def validar():
    meta = cargar_json(PACK / "pack.mcmeta")
    if meta is not None:
        p = meta.get("pack", {})
        if "min_format" not in p or "max_format" not in p:
            error("pack.mcmeta: faltan min_format y max_format")
    for ruta in PACK.rglob("*.json"):
        cargar_json(ruta)
    for ruta in PACK.rglob("*.png"):
        try:
            png_tam(ruta)
        except (OSError, ValueError) as e:
            error(f"{ruta.relative_to(RAIZ)}: {e}")

    vistos = set()
    for ruta in PACK.glob("assets/*/items/*.json"):
        d = cargar_json(ruta)
        if d is None:
            continue
        rel = ruta.relative_to(RAIZ)
        for clave, valor in d.items():
            if clave == "model":
                validar_nodo(valor, rel)
            elif clave in ("hand_animation_on_swap", "oversized_in_gui"):
                if not isinstance(valor, bool):
                    error(f"{rel}: {clave} tiene que ser true o false")
            elif clave == "swap_animation_scale":
                if not isinstance(valor, (int, float)):
                    error(f"{rel}: swap_animation_scale tiene que ser un número")
            else:
                error(f"{rel}: campo desconocido {clave!r}")
        refs = set()
        modelos_de_item(d.get("model"), refs)
        if not refs:
            error(f"{ruta.relative_to(RAIZ)}: no referencia ningún modelo")
        for r in refs:
            validar_modelo(r, vistos)

    for ruta in PACK.glob("assets/*/equipment/*.json"):
        d = cargar_json(ruta)
        if d is None:
            continue
        for capa, lista in d.get("layers", {}).items():
            for l in lista:
                ns, path = l["texture"].split(":", 1) if ":" in l["texture"] else ("minecraft", l["texture"])
                png = PACK / "assets" / ns / "textures" / "entity" / "equipment" / capa / f"{path}.png"
                if not png.exists():
                    error(f"{ruta.relative_to(RAIZ)}: falta {png.relative_to(RAIZ)}")
                elif png_tam(png) != (64, 32):
                    error(f"{png.relative_to(RAIZ)}: tiene que medir 64 × 32")

    for ruta in PACK.glob("assets/*/sounds.json"):
        d = cargar_json(ruta)
        if d is None:
            continue
        for evento, datos in d.items():
            for s in datos.get("sounds", []):
                nombre = s if isinstance(s, str) else s.get("name", "")
                archivo, _ = ruta_recurso(nombre, "sounds", ".ogg")
                if not archivo.exists():
                    error(f"{ruta.relative_to(RAIZ)}: {evento} pide {nombre} y no existe")
                elif archivo.read_bytes()[:4] != b"OggS":
                    error(f"{archivo.relative_to(RAIZ)}: no es un OGG")

    for ruta in PACK.glob("assets/*/font/*.json"):
        d = cargar_json(ruta)
        if d is None:
            continue
        for prov in d.get("providers", []):
            if prov.get("type") != "bitmap":
                continue
            archivo, _ = ruta_recurso(prov["file"], "textures", "")
            if not archivo.exists():
                error(f"{ruta.relative_to(RAIZ)}: falta {prov['file']}")
                continue
            if prov.get("ascent", 0) > prov.get("height", 8):
                error(f"{ruta.relative_to(RAIZ)}: ascent mayor que height en {prov['file']}")
            w, h = png_tam(archivo)
            filas = prov["chars"]
            cols = len(filas[0])
            if w % cols or h % len(filas) or any(len(f) != cols for f in filas):
                error(f"{ruta.relative_to(RAIZ)}: la grilla de {prov['file']} no coincide con chars")


def empaquetar():
    SALIDA.parent.mkdir(parents=True, exist_ok=True)
    archivos = sorted(p for p in PACK.rglob("*") if p.is_file())
    with zipfile.ZipFile(SALIDA, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for p in archivos:
            info = zipfile.ZipInfo(p.relative_to(PACK).as_posix(), date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, p.read_bytes())
    datos = SALIDA.read_bytes()
    return hashlib.sha1(datos).hexdigest(), len(datos), len(archivos)


def main():
    validar()
    if errores:
        print("El paquete de recursos tiene errores:")
        for e in errores:
            print("  -", e)
        sys.exit(1)
    sha1, tam, n = empaquetar()
    print(f"{SALIDA.relative_to(RAIZ)}: {n} archivos, {tam / 1024:.1f} KB, sha1 {sha1}")


if __name__ == "__main__":
    main()
