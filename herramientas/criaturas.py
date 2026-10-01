"""Aspecto de los enemigos y jefes del RPG sin perder las animaciones de vanilla.

- Cascos: van en la ranura de la cabeza del mob (contexto HEAD, como una calabaza). La cabeza
  ocupa el cubo 1,6..14,4 del modelo y el frente mira a -Z.
- Piezas: ItemDisplay que el plugin mueve con el jefe (contexto NONE). Se modelan con el frente a
  -Z y se exportan girados 180° (variante "mundo"), así quedan mirando a +Z como la entidad. El
  centro del modelo (8, 8, 8) es el pivote: las alas tienen el hombro ahí para poder batirlas.
"""
import json
from pathlib import Path

from modelado import Caja, Material, cilindro, exportar, punteado, rayas

HIERRO_OSC = (58, 58, 64)
TELA = (86, 70, 54)
HUESO = (214, 204, 176)
CARNE = (124, 52, 50)
PALIDO = (222, 218, 206)


def C(x0, y0, z0, x1, y1, z1, mat, nombre=""):
    return Caja((x0, y0, z0), (x1, y1, z1), mat, nombre)


# ---------------------------------------------------------------- cascos (cabeza: 1,6..14,4)

def capucha(color=TELA):
    """Capucha de arpillera rota con la cara en sombra."""
    tela = Material(color, desgaste=0.05, sombra=0.14, patrones=[punteado(0.35, -0.10)])
    sombra = Material((24, 20, 18), ruido=0.0, desgaste=0.0, sombra=0.0)
    return [
        C(1.0, 4.0, 1.0, 1.6, 15.2, 15.0, tela, "lado_izq"),
        C(14.4, 4.0, 1.0, 15.0, 15.2, 15.0, tela, "lado_der"),
        C(1.6, 4.0, 14.4, 14.4, 15.2, 15.0, tela, "nuca"),
        C(1.6, 14.4, 1.0, 14.4, 15.2, 14.4, tela, "copa"),
        C(5.0, 15.2, 5.0, 11.0, 16.0, 12.0, tela, "punta"),
        C(1.6, 11.0, 1.0, 14.4, 14.4, 1.6, tela, "frente"),
        C(1.6, 4.0, 1.4, 14.4, 11.0, 1.6, sombra, "cara"),
        C(2.0, 0.0, 14.4, 14.0, 4.0, 15.4, tela, "faldon"),
    ]


def yelmo_lancero():
    """Capacete de ala ancha (kettle hat) oxidado."""
    hierro = Material((96, 84, 72), desgaste=0.22, sombra=0.08, patrones=[punteado(0.25, -0.08)])
    return [
        *cilindro("y", (8, 8), 7.0, 11.0, 14.8, hierro, "copa"),
        *cilindro("y", (8, 8), 4.5, 14.8, 16.2, hierro, "cima"),
        *cilindro("y", (8, 8), 9.6, 10.4, 11.0, hierro, "ala"),
    ]


def yelmo_caido():
    """Yelmo de cubo con una ranura para los ojos y un penacho rojo gastado."""
    hierro = Material(HIERRO_OSC, desgaste=0.25, sombra=0.08, patrones=[punteado(0.12, -0.06)])
    ranura = Material((10, 10, 12), ruido=0.0, desgaste=0.0, sombra=0.0)
    penacho = Material((120, 30, 30), desgaste=0.05, patrones=[rayas(periodo=2, ancho=1, delta=-0.15, direccion="arriba")])
    return [
        C(1.0, 2.0, 1.0, 1.6, 15.0, 15.0, hierro, "lado_izq"),
        C(14.4, 2.0, 1.0, 15.0, 15.0, 15.0, hierro, "lado_der"),
        C(1.6, 2.0, 14.4, 14.4, 15.0, 15.0, hierro, "nuca"),
        C(1.6, 14.4, 1.0, 14.4, 15.0, 14.4, hierro, "techo"),
        C(1.6, 10.6, 0.6, 14.4, 14.4, 1.6, hierro, "frente_alto"),
        C(1.6, 2.0, 0.6, 14.4, 9.4, 1.6, hierro, "frente_bajo"),
        C(1.6, 9.4, 1.2, 14.4, 10.6, 1.6, ranura, "ranura"),
        C(7.6, 2.0, 0.2, 8.4, 14.6, 0.6, hierro, "cresta"),
        C(7.2, 15.0, 4.0, 8.8, 18.0, 14.0, penacho, "penacho"),
        C(7.2, 12.0, 14.0, 8.8, 18.0, 16.0, penacho, "penacho_cola"),
    ]


def yelmo_ahogado():
    """Yelmo de buzo de bronce comido por percebes y algas."""
    bronce = Material((70, 98, 86), desgaste=0.20, sombra=0.10, patrones=[punteado(0.30, -0.10)])
    vidrio = Material((40, 70, 70), ruido=0.0, desgaste=0.05, alfa=0.9)
    percebe = Material((196, 190, 168), desgaste=0.1)
    alga = Material((40, 96, 50), desgaste=0.05)
    return [
        C(1.0, 1.0, 1.0, 15.0, 15.2, 15.0, bronce, "casco"),
        C(4.0, 6.0, 0.4, 12.0, 12.0, 1.0, vidrio, "visor"),
        C(3.4, 5.4, 0.2, 4.0, 12.6, 1.0, bronce, "marco_izq"),
        C(12.0, 5.4, 0.2, 12.6, 12.6, 1.0, bronce, "marco_der"),
        C(2.0, 13.0, 3.0, 4.0, 14.6, 5.0, percebe, "percebe1"),
        C(11.0, 14.6, 6.0, 13.0, 16.0, 8.0, percebe, "percebe2"),
        C(14.6, 6.0, 9.0, 15.6, 8.0, 11.0, percebe, "percebe3"),
        C(13.0, -2.0, 7.0, 14.0, 1.0, 8.0, alga, "alga1"),
        C(3.0, -3.0, 12.0, 4.0, 1.0, 13.0, alga, "alga2"),
    ]


def mascara_vastago():
    """Máscara de hueso con ojos verdes y tentáculos que cuelgan."""
    hueso = Material(HUESO, desgaste=0.1, sombra=0.12)
    ojo = Material((80, 255, 170), ruido=0.0, desgaste=0.0, sombra=0.0, volumen=0.0)
    tentaculo = Material((56, 70, 74), desgaste=0.05, sombra=0.12)
    out = [
        C(2.0, 4.0, 0.4, 14.0, 15.0, 1.6, hueso, "mascara"),
        C(4.0, 9.0, 0.0, 6.6, 11.0, 0.4, ojo, "ojo_izq"),
        C(9.4, 9.0, 0.0, 12.0, 11.0, 0.4, ojo, "ojo_der"),
        C(3.0, 15.0, 1.0, 5.0, 19.0, 3.0, hueso, "cuerno_izq"),
        C(11.0, 15.0, 1.0, 13.0, 19.0, 3.0, hueso, "cuerno_der"),
    ]
    for i, x in enumerate((3.0, 5.6, 8.2, 10.8)):
        largo = 5 + (i % 2) * 3
        out.append(C(x, 4.0 - largo, 0.8, x + 1.4, 4.0, 2.2, tentaculo, f"tentaculo{i}"))
    return out


def yelmo_abismo():
    """Yelmo negro del Caballero del Abismo: corona dentada, cuernos y gemas violetas."""
    negro = Material((34, 30, 40), desgaste=0.22, sombra=0.06, patrones=[punteado(0.10, -0.05)])
    oro = Material((150, 120, 60), desgaste=0.25)
    gema = Material((170, 80, 230), ruido=0.0, desgaste=0.0, sombra=0.0, volumen=0.0)
    ranura = Material((10, 8, 14), ruido=0.0, desgaste=0.0, sombra=0.0)
    out = [
        C(1.0, 2.0, 1.0, 1.6, 15.0, 15.0, negro, "lado_izq"),
        C(14.4, 2.0, 1.0, 15.0, 15.0, 15.0, negro, "lado_der"),
        C(1.6, 2.0, 14.4, 14.4, 15.0, 15.0, negro, "nuca"),
        C(1.6, 14.4, 1.0, 14.4, 15.0, 14.4, negro, "techo"),
        C(1.6, 10.6, 0.6, 14.4, 14.4, 1.6, negro, "frente_alto"),
        C(1.6, 2.0, 0.6, 14.4, 9.4, 1.6, negro, "frente_bajo"),
        C(1.6, 9.4, 1.2, 14.4, 10.6, 1.6, ranura, "ranura"),
        C(1.0, 15.0, 1.0, 15.0, 15.8, 15.0, oro, "aro"),
        C(-3.0, 12.0, 6.0, 1.0, 13.6, 8.0, negro, "cuerno_izq_1"),
        C(-4.0, 13.6, 6.0, -2.4, 18.0, 8.0, negro, "cuerno_izq_2"),
        C(15.0, 12.0, 6.0, 19.0, 13.6, 8.0, negro, "cuerno_der_1"),
        C(18.4, 13.6, 6.0, 20.0, 18.0, 8.0, negro, "cuerno_der_2"),
        C(7.0, 12.0, 0.2, 9.0, 14.0, 0.6, gema, "gema"),
    ]
    for i, x in enumerate((2.0, 5.2, 8.4, 11.6)):
        alto = 3.0 if i in (1, 2) else 2.0
        out.append(C(x, 15.8, 1.8, x + 2.2, 15.8 + alto, 3.0, oro, f"punta{i}"))
    return out


# ---------------------------------------------------------------- piezas de jefe (ItemDisplay)

def fauces_gloton():
    """La sonrisa del Glotón: mandíbulas de carne con hileras de dientes, abierta."""
    carne = Material(CARNE, desgaste=0.05, sombra=0.15, patrones=[punteado(0.35, -0.10)])
    encia = Material((170, 70, 76), desgaste=0.05)
    diente = Material((236, 228, 200), desgaste=0.05)
    out = [
        C(-2.0, 10.0, -4.0, 18.0, 13.0, 8.0, carne, "mandibula_sup"),
        C(-1.0, 1.0, -3.0, 17.0, 4.0, 8.0, carne, "mandibula_inf"),
        C(-1.6, 9.2, -3.6, 17.6, 10.0, 7.0, encia, "encia_sup"),
        C(-0.6, 4.0, -2.6, 16.6, 4.8, 7.0, encia, "encia_inf"),
        C(-2.0, 4.8, 7.0, 18.0, 9.2, 8.0, carne, "garganta"),
    ]
    for i in range(9):
        x = -1.0 + i * 2.1
        out.append(C(x, 7.0, -3.4, x + 1.2, 9.2, -2.2, diente, f"diente_sup{i}"))
        out.append(C(x + 0.8, 4.8, -2.4, x + 1.8, 6.8, -1.4, diente, f"diente_inf{i}"))
    return out


def caparazon_tejedora():
    """Abdomen rojo con franjas negras y púas, para encima de la araña."""
    rojo = Material((150, 30, 28), desgaste=0.12, sombra=0.15, patrones=[rayas(periodo=4, ancho=1, delta=-0.35)])
    negro = Material((24, 20, 22), desgaste=0.1)
    out = [
        C(2.0, 3.0, 6.0, 14.0, 13.0, 20.0, rojo, "abdomen"),
        C(3.0, 13.0, 8.0, 13.0, 15.0, 18.0, rojo, "abdomen_lomo"),
        C(3.5, 2.0, 20.0, 12.5, 12.0, 23.0, rojo, "abdomen_cola"),
        C(4.0, 4.0, -2.0, 12.0, 10.0, 6.0, negro, "cintura"),
    ]
    for i, (x, z) in enumerate(((4.0, 9.0), (10.6, 9.0), (4.0, 15.0), (10.6, 15.0), (7.3, 12.0))):
        out.append(C(x, 15.0, z, x + 1.4, 19.0 + (i == 4) * 2, z + 1.4, negro, f"pua{i}"))
    return out


def cola_vigia():
    """Cola pálida y segmentada que arrastra el Vigía."""
    piel = Material(PALIDO, desgaste=0.05, sombra=0.14, patrones=[punteado(0.20, -0.06)])
    out = []
    x, y, z, a = 8.0, 8.0, 8.0, 3.4
    for i in range(6):
        largo = 3.4
        out.append(C(x - a / 2, y - a / 2, z, x + a / 2, y + a / 2, z + largo, piel, f"segmento{i}"))
        z += largo
        y -= 0.9
        a *= 0.82
    out.append(C(x - 0.4, y - 0.4, z, x + 0.4, y + 0.4, z + 2.0, Material((60, 56, 60)), "punta"))
    return out


def ala(lado):
    """Ala de murciélago: el hombro en (8, 8, 8) y la membrana hacia afuera (lado +1 derecha, -1 izquierda)."""
    hueso = Material((60, 72, 74), desgaste=0.1, sombra=0.1)
    membrana = Material((30, 52, 56), ruido=0.03, desgaste=0.0, sombra=0.1, alfa=0.92)
    def X(a, b):
        return (8 + lado * a, 8 + lado * b) if lado > 0 else (8 + lado * b, 8 + lado * a)
    out = []
    x0, x1 = X(0, 22)
    out.append(C(x0, 26.0, 7.4, x1, 27.4, 8.6, hueso, "brazo"))
    x0, x1 = X(0, 1.4)
    out.append(C(x0, 8.0, 7.4, x1, 26.0, 8.6, hueso, "hombro"))
    for i, (d, abajo) in enumerate(((7, 10), (14, 6), (21, 2))):
        x0, x1 = X(d, d + 1.2)
        out.append(C(x0, abajo, 7.6, x1, 26.0, 8.4, hueso, f"dedo{i}"))
    for i, (a, b, abajo) in enumerate(((1.4, 7, 12), (8.2, 14, 8), (15.2, 21, 4))):
        x0, x1 = X(a, b)
        out.append(C(x0, abajo, 7.85, x1, 26.0, 8.15, membrana, f"membrana{i}"))
    return out


def tentaculos_durmiente():
    """Barba de tentáculos verdosos que cuelga de la cara del Durmiente."""
    piel = Material((44, 70, 66), desgaste=0.05, sombra=0.15, patrones=[punteado(0.30, -0.08)])
    brillo = Material((90, 255, 200), ruido=0.0, desgaste=0.0, sombra=0.0, volumen=0.0)
    out = []
    for i, (x, largo, z) in enumerate(((3.0, 12, 4.0), (5.4, 16, 2.6), (7.6, 20, 2.0), (9.8, 16, 2.6), (12.2, 12, 4.0),
                                       (4.2, 9, 6.0), (11.0, 9, 6.0))):
        out.append(C(x, 10.0 - largo, z, x + 1.6, 10.0, z + 1.6, piel, f"tentaculo{i}"))
        out.append(C(x + 0.4, 10.0 - largo - 0.8, z + 0.4, x + 1.2, 10.0 - largo, z + 1.2, brillo, f"punta{i}"))
    return out


def cepo():
    """Cepo de dientes abierto, apoyado en el piso (el piso es y = 8)."""
    hierro = Material((70, 70, 74), desgaste=0.25, sombra=0.08, patrones=[punteado(0.20, -0.08)])
    diente = Material((150, 150, 156), desgaste=0.2)
    cadena = Material((96, 96, 100))
    out = [
        C(2.8, 8.1, 7.2, 13.2, 8.5, 8.8, hierro, "eje"),
        C(6.6, 8.5, 6.6, 9.4, 8.9, 9.4, hierro, "plato"),
        C(2.0, 8.0, 2.0, 2.8, 8.6, 14.0, hierro, "aro_izq"),
        C(13.2, 8.0, 2.0, 14.0, 8.6, 14.0, hierro, "aro_der"),
        C(2.8, 8.0, 2.0, 13.2, 8.6, 2.8, hierro, "aro_frente"),
        C(2.8, 8.0, 13.2, 13.2, 8.6, 14.0, hierro, "aro_atras"),
        C(14.0, 8.0, 7.6, 18.0, 8.4, 8.4, cadena, "cadena"),
    ]
    for i in range(5):
        x = 3.2 + i * 2.1
        out.append(C(x, 8.6, 2.2, x + 0.8, 10.6, 2.6, diente, f"diente_a{i}"))
        out.append(C(x + 0.9, 8.6, 13.4, x + 1.7, 10.6, 13.8, diente, f"diente_b{i}"))
    return out


def estandarte():
    """Estandarte de guerra: asta con remate y paño rojo con el emblema de la llama."""
    madera = Material((92, 64, 40), desgaste=0.1, patrones=[rayas(periodo=3, ancho=1, delta=-0.06, direccion="arriba")])
    oro = Material((196, 156, 60), desgaste=0.25)
    pano = Material((150, 30, 32), desgaste=0.05, sombra=0.12, patrones=[rayas(periodo=4, ancho=1, delta=-0.08)])
    llama = Material((250, 170, 50), ruido=0.0, desgaste=0.0, sombra=0.0, volumen=0.0)
    return [
        C(7.4, -12.0, 7.4, 8.6, 28.0, 8.6, madera, "asta"),
        C(7.0, 28.0, 7.0, 9.0, 30.0, 9.0, oro, "remate"),
        C(2.0, 26.0, 7.6, 14.0, 26.8, 8.4, madera, "travesano"),
        C(2.4, 8.0, 7.8, 13.6, 26.0, 8.2, pano, "pano"),
        C(7.0, 14.0, 7.6, 9.0, 20.0, 8.4, llama, "llama"),
        C(7.6, 20.0, 7.6, 8.4, 21.6, 8.4, llama, "llama_punta"),
    ]


CASCOS = {
    "capucha_hueca": capucha, "capucha_arquero": lambda: capucha((70, 76, 52)), "yelmo_lancero": yelmo_lancero,
    "yelmo_caido": yelmo_caido, "yelmo_ahogado": yelmo_ahogado, "mascara_vastago": mascara_vastago,
    "yelmo_abismo": yelmo_abismo,
}
PIEZAS = {
    "fauces_gloton": fauces_gloton, "caparazon_tejedora": caparazon_tejedora, "cola_vigia": cola_vigia,
    "ala_der": lambda: ala(1), "ala_izq": lambda: ala(-1), "tentaculos_durmiente": tentaculos_durmiente,
    "cepo": cepo, "estandarte": estandarte,
}


def _item(nombre, recurso, pack):
    ruta = Path(pack) / "assets" / "tresmodos" / "items" / f"{nombre}.json"
    ruta.parent.mkdir(parents=True, exist_ok=True)
    ruta.write_text(json.dumps({"model": {"type": "minecraft:model", "model": recurso}}, indent=1) + "\n", encoding="utf-8")


def main(pack):
    identidad = {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [1, 1, 1]}
    for nombre, crear in CASCOS.items():
        cajas = crear()
        display = {"head": identidad, "gui": {"rotation": [25, 145, 0], "scale": [0.7] * 3}}
        rutas = exportar(nombre, cajas, {"cabeza": {"orientacion": "fp", "display": display}}, pack, densidad=8)
        _item(nombre, rutas["cabeza"], pack)
    for nombre, crear in PIEZAS.items():
        cajas = crear()
        rutas = exportar(nombre, cajas, {"mundo": {"orientacion": "mundo", "display": {}}}, pack, densidad=6)
        _item(nombre, rutas["mundo"], pack)
    print(f"criaturas: {len(CASCOS)} cascos y {len(PIEZAS)} piezas")
