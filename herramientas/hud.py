"""Glifos del HUD (fuente tresmodos:hud) e ícono del paquete.

Los glifos se dibujan con Pillow y se referencian desde assets/tresmodos/font/hud.json.
Caracteres usados por el plugin (ver Hud.java):
  U+E000 marcador de impacto · U+E001 marcador de baja · U+E010 ícono de bala
"""
import json
from pathlib import Path

from PIL import Image, ImageDraw

PACK = Path(__file__).resolve().parent.parent / "paquete-recursos"
FUENTES = PACK / "assets" / "tresmodos" / "textures" / "font"


def marcador(color):
    """Cruz en X con hueco en el centro, como el marcador de impacto de COD.

    Se dibuja píxel por píxel: trazo diagonal de 1 px y contorno oscuro de 1 px.
    """
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = im.load()
    trazo = set()
    for sx in (1, -1):
        for sy in (1, -1):
            for k in range(2, 7):
                x = 8 + k if sx > 0 else 7 - k
                y = 8 + k if sy > 0 else 7 - k
                trazo.add((x, y))
    for x, y in trazo:
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                q = (x + dx, y + dy)
                if q not in trazo and 0 <= q[0] < 16 and 0 <= q[1] < 16:
                    px[q] = (0, 0, 0, 150)
    for q in trazo:
        px[q] = color
    return im


def bala():
    """Cartucho de fusil vertical: punta de cobre y vaina de latón."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([5, 6, 10, 15], fill=(0, 0, 0, 150))
    d.polygon([(5, 6), (7, 1), (8, 1), (10, 6)], fill=(0, 0, 0, 150))
    d.rectangle([6, 7, 9, 14], fill=(201, 160, 72, 255))       # vaina
    d.line([(6, 7), (6, 14)], fill=(236, 205, 120, 255))         # brillo
    d.rectangle([6, 12, 9, 12], fill=(150, 112, 44, 255))        # ranura del culote
    d.polygon([(6, 6), (7, 2), (8, 2), (9, 6)], fill=(184, 98, 58, 255))  # punta
    return im


def icono_pack():
    """Ícono del paquete: tres franjas (Shooter, Guerra, RPG) sobre fondo oscuro."""
    im = Image.new("RGBA", (128, 128), (24, 24, 28, 255))
    d = ImageDraw.Draw(im)
    for i, color in enumerate(((186, 52, 46), (104, 112, 64), (112, 66, 150))):
        x0 = 18 + i * 34
        d.rectangle([x0, 22, x0 + 22, 106], fill=color)
        d.rectangle([x0, 22, x0 + 22, 26], fill=tuple(min(255, c + 50) for c in color))
    d.rectangle([0, 0, 127, 127], outline=(70, 70, 76, 255), width=3)
    return im


def main():
    FUENTES.mkdir(parents=True, exist_ok=True)
    marcador((255, 255, 255, 255)).save(FUENTES / "marcador.png")
    marcador((232, 52, 44, 255)).save(FUENTES / "marcador_baja.png")
    bala().save(FUENTES / "bala.png")
    icono_pack().save(PACK / "pack.png")

    # El título se dibuja escalado x4 con su borde superior 10 unidades arriba del centro.
    # Con alto 4 y ascent -1 el glifo queda centrado sobre la mira.
    fuente = {"providers": [
        {"type": "bitmap", "file": "tresmodos:font/marcador.png", "ascent": -1, "height": 4,
         "chars": [""]},
        {"type": "bitmap", "file": "tresmodos:font/marcador_baja.png", "ascent": -1, "height": 4,
         "chars": [""]},
        {"type": "bitmap", "file": "tresmodos:font/bala.png", "ascent": 7, "height": 8,
         "chars": [""]},
    ]}
    ruta = PACK / "assets" / "tresmodos" / "font" / "hud.json"
    ruta.parent.mkdir(parents=True, exist_ok=True)
    ruta.write_text(json.dumps(fuente, indent=2) + "\n", encoding="utf-8")
    print("hud: marcador, marcador_baja, bala, pack.png")


if __name__ == "__main__":
    main()
