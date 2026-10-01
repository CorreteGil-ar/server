"""Glifos del HUD (fuente tresmodos:hud) e ícono del paquete.

Los glifos se dibujan con Pillow y se referencian desde assets/tresmodos/font/hud.json.
Caracteres usados por el plugin (ver Hud.java):
  U+E000 marcador de impacto · U+E001 marcador de baja · U+E010 ícono de bala
  U+E020 destello (pantalla en blanco de la bomba atómica)
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


def destello():
    """Rectángulo blanco: dibujado como título (x4) tapa toda la pantalla."""
    return Image.new("RGBA", (64, 16), (255, 255, 255, 255))


# ------------------------------------------------------------------ paneles (barras y letra chica)
#
# Los paneles del HUD (barras estilo Souls del RPG, tablero de los vehículos) se escriben como
# título de una barra de jefe blanca cuyo dibujo es transparente. Tres fuentes iguales salvo la
# altura: tresmodos:fila0, fila1 y fila2 (cada una 7 px más abajo). Hud.java arma el texto.
#
#   letras: ASCII en mayúsculas, 3×5 (M, W y N más anchas), avance = ancho + 1
#   barras: U+E100 + 16·color + i, pieza de 2^i px de largo (i = 0..7), 5 px de alto con borde
#   espacios: U+E200 + i retrocede 2^i px; U+E210 + i avanza 2^i px (i = 0..9)

LETRAS = {
    "0": ["###", "#.#", "#.#", "#.#", "###"], "1": [".#.", "##.", ".#.", ".#.", "###"],
    "2": ["###", "..#", "###", "#..", "###"], "3": ["###", "..#", ".##", "..#", "###"],
    "4": ["#.#", "#.#", "###", "..#", "..#"], "5": ["###", "#..", "###", "..#", "###"],
    "6": ["###", "#..", "###", "#.#", "###"], "7": ["###", "..#", ".#.", ".#.", ".#."],
    "8": ["###", "#.#", "###", "#.#", "###"], "9": ["###", "#.#", "###", "..#", "###"],
    "A": [".#.", "#.#", "###", "#.#", "#.#"], "B": ["##.", "#.#", "##.", "#.#", "##."],
    "C": [".##", "#..", "#..", "#..", ".##"], "D": ["##.", "#.#", "#.#", "#.#", "##."],
    "E": ["###", "#..", "##.", "#..", "###"], "F": ["###", "#..", "##.", "#..", "#.."],
    "G": [".##", "#..", "#.#", "#.#", ".##"], "H": ["#.#", "#.#", "###", "#.#", "#.#"],
    "I": ["###", ".#.", ".#.", ".#.", "###"], "J": ["..#", "..#", "..#", "#.#", ".#."],
    "K": ["#.#", "#.#", "##.", "#.#", "#.#"], "L": ["#..", "#..", "#..", "#..", "###"],
    "M": ["#...#", "##.##", "#.#.#", "#...#", "#...#"], "N": ["#..#", "##.#", "#.##", "#..#", "#..#"],
    "O": [".#.", "#.#", "#.#", "#.#", ".#."], "P": ["##.", "#.#", "##.", "#..", "#.."],
    "Q": [".#.", "#.#", "#.#", "##.", ".##"], "R": ["##.", "#.#", "##.", "#.#", "#.#"],
    "S": [".##", "#..", ".#.", "..#", "##."], "T": ["###", ".#.", ".#.", ".#.", ".#."],
    "U": ["#.#", "#.#", "#.#", "#.#", "###"], "V": ["#.#", "#.#", "#.#", "#.#", ".#."],
    "W": ["#...#", "#...#", "#.#.#", "##.##", "#...#"], "X": ["#.#", "#.#", ".#.", "#.#", "#.#"],
    "Y": ["#.#", "#.#", ".#.", ".#.", ".#."], "Z": ["###", "..#", ".#.", "#..", "###"],
    "/": ["..#", "..#", ".#.", "#..", "#.."], "%": ["#.#", "..#", ".#.", "#..", "#.#"],
    ".": [".", ".", ".", ".", "#"], ":": [".", "#", ".", "#", "."], "!": ["#", "#", "#", ".", "#"],
    "-": ["...", "...", "###", "...", "..."], "+": ["...", ".#.", "###", ".#.", "..."],
    "(": [".#", "#.", "#.", "#.", ".#"], ")": ["#.", ".#", ".#", ".#", "#."],
    "?": ["##.", "..#", ".#.", "...", ".#."], "*": ["#.#", ".#.", "#.#", "...", "..."],
}

# Colores de las barras: (brillo, medio, sombra). El orden es el de Hud.ColorBarra.
COLORES_BARRA = [
    ((236, 84, 74), (190, 34, 34), (120, 16, 16)),     # ROJO (vida)
    ((120, 210, 96), (60, 160, 60), (28, 96, 34)),     # VERDE (aguante, blindaje sano)
    ((104, 156, 244), (52, 96, 206), (28, 54, 138)),   # AZUL (éter)
    ((252, 222, 104), (220, 178, 40), (150, 108, 20)),  # AMARILLO
    ((250, 164, 80), (226, 116, 30), (150, 70, 14)),   # NARANJA
    ((250, 246, 230), (230, 220, 196), (190, 176, 150)),  # BLANCO (daño reciente)
    ((54, 50, 50), (38, 34, 34), (30, 26, 26)),        # VACIO
    None,                                               # BORDE (columna oscura)
    ((170, 170, 176), (124, 124, 130), (84, 84, 90)),  # GRIS
]
BORDE = (14, 10, 10, 235)


def letras():
    """Grilla 16 × n de celdas de 6 × 8 (la letra arriba a la izquierda; el ancho sale solo)."""
    chars = list(LETRAS)
    filas = [chars[i:i + 16] for i in range(0, len(chars), 16)]
    im = Image.new("RGBA", (16 * 6, len(filas) * 8), (0, 0, 0, 0))
    px = im.load()
    for fy, fila in enumerate(filas):
        for fx, c in enumerate(fila):
            for y, linea in enumerate(LETRAS[c]):
                for x, v in enumerate(linea):
                    if v == "#":
                        px[fx * 6 + x, fy * 8 + y] = (255, 255, 255, 255)
    return im, ["".join(f).ljust(16, "\0") for f in filas]


def barras():
    """Grilla 8 × colores de celdas de 128 × 8: la pieza i mide 2^i px (5 de alto con borde)."""
    im = Image.new("RGBA", (8 * 128, len(COLORES_BARRA) * 8), (0, 0, 0, 0))
    px = im.load()
    for ci, col in enumerate(COLORES_BARRA):
        for i in range(8):
            largo = 1 << i
            for x in range(largo):
                for y in range(5):
                    if col is None or y in (0, 4):
                        c = BORDE
                    else:
                        c = col[y - 1] + ((200,) if ci == 6 else (255,))
                    px[i * 128 + x, ci * 8 + y] = c
    filas = ["".join(chr(0xE100 + 16 * ci + i) for i in range(8)) for ci in range(len(COLORES_BARRA))]
    return im, filas


def fuentes_paneles():
    im, filas_letras = letras()
    im.save(FUENTES / "letras.png")
    im, filas_barras = barras()
    im.save(FUENTES / "barras.png")
    espacios = {" ": 3}
    for i in range(10):
        espacios[chr(0xE200 + i)] = -(1 << i)
        espacios[chr(0xE210 + i)] = 1 << i
    for n, ascent in enumerate((7, 0, -7)):
        fuente = {"providers": [
            {"type": "space", "advances": espacios},
            {"type": "bitmap", "file": "tresmodos:font/letras.png", "ascent": ascent, "height": 8, "chars": filas_letras},
            {"type": "bitmap", "file": "tresmodos:font/barras.png", "ascent": ascent, "height": 8, "chars": filas_barras},
        ]}
        ruta = PACK / "assets" / "tresmodos" / "font" / f"fila{n}.json"
        ruta.write_text(json.dumps(fuente, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    # La barra de jefe blanca queda invisible: es el soporte de los paneles (ningún jefe la usa).
    sprites = PACK / "assets" / "minecraft" / "textures" / "gui" / "sprites" / "boss_bar"
    sprites.mkdir(parents=True, exist_ok=True)
    for nombre in ("white_background", "white_progress"):
        Image.new("RGBA", (182, 5), (0, 0, 0, 0)).save(sprites / f"{nombre}.png")


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
    destello().save(FUENTES / "destello.png")
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
        # Alto 160 y ascent 77: centrado sobre la mira como el marcador (centro = ascent - alto/2 = -3).
        {"type": "bitmap", "file": "tresmodos:font/destello.png", "ascent": 77, "height": 160,
         "chars": ["\ue020"]},
    ]}
    ruta = PACK / "assets" / "tresmodos" / "font" / "hud.json"
    ruta.parent.mkdir(parents=True, exist_ok=True)
    ruta.write_text(json.dumps(fuente, indent=2) + "\n", encoding="utf-8")
    fuentes_paneles()
    print("hud: marcador, marcador_baja, bala, destello, paneles (fila0-2), pack.png")


if __name__ == "__main__":
    main()
