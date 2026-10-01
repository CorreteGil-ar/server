"""Armas del resource pack, con proporciones reales.

Marco canónico: el cañón apunta a -Z, arriba es +Y, el lado derecho del arma es +X y el
eje del ánima pasa por x = 8. Cada arma se separa en partes que cambian con los
accesorios (cargador, mira de hierro, dispositivo de boca) y declara dónde se montan
los accesorios (ver accesorios.py).
"""
from dataclasses import dataclass, field

from modelado import (Caja, Material, cilindro, picatinny, punteado, puntos, ranuras, rayas,
                      rectangulo)

# ---------------------------------------------------------------- paleta

FDE = (168, 142, 104)          # cerakote flat dark earth
NEGRO = (36, 36, 38)           # anodizado / fosfatado
ACERO = (52, 53, 56)           # cañón
ALUMINIO = (60, 62, 65)        # cargador STANAG
GOMA = (26, 26, 27)
MADERA = (104, 66, 38)
POLIMERO = (30, 31, 33)


def C(x0, y0, z0, x1, y1, z1, mat, nombre=""):
    return Caja((x0, y0, z0), (x1, y1, z1), mat, nombre)


def lados(*patrones_por_lado):
    """Aplica el mismo patrón en los dos costados del arma."""
    return [p for p in patrones_por_lado]


@dataclass
class Arma:
    nombre: str
    cuerpo: list
    cargador: dict                 # "normal" / "ampliado" -> cajas
    mira_hierro: list              # alza levantada (sin óptica)
    mira_plegada: list             # alza plegada (con óptica)
    boca: list                     # apagallamas o freno de boca (se saca con silenciador)
    montajes: dict                 # ranura -> {"pos": (x, y, z), "lado": "arriba"|"derecha"|"izquierda"|"abajo"}
    opciones: dict                 # ranura -> lista de valores permitidos
    silenciador: str = "rifle"     # tamaño del silenciador
    mira_defecto: str = ""         # óptica fija si el arma no tiene miras de hierro
    # Línea de las miras de hierro: altura de la punta del guion (y) y cara trasera del alza (z).
    linea_hierro: dict = None
    # fp / tp / gui: transformaciones; ads: distancia aparente del ojo a la mira trasera, en
    # bloques a escala 1 ("hierro" y "optica"), ver generar.display_ads.
    config: dict = field(default_factory=dict)


# ---------------------------------------------------------------- M4A1

def m4a1():
    """Colt M4A1 SOPMOD: 838 mm, cañón de 14,5", guardamanos de 4 rieles. 1 unidad = 2,5 cm."""
    fde = Material(FDE, desgaste=0.10, sombra=0.08)
    riel_sup = fde.con(patrones=[picatinny("arriba")])
    riel_inf = fde.con(patrones=[picatinny("arriba", signo=-1)])
    riel_der = fde.con(patrones=[picatinny("derecha")])
    riel_izq = fde.con(patrones=[picatinny("derecha", signo=-1)])
    receptor = fde.con(patrones=[
        rectangulo((0.47, 0.52), (0.35, 0.46), 0, color=(22, 22, 23)),               # ventana de expulsión
        rectangulo((0.47, 0.25), (0.42, 0.05), -0.10),                              # bisagra de la tapa
        puntos([(0.93, 0.25), (0.07, 0.25)], solo_normal="derecha"),
        puntos([(0.93, 0.25), (0.07, 0.25)], solo_normal="derecha", signo_normal=-1),
    ])
    inferior = fde.con(patrones=[
        puntos([(0.95, 0.6), (0.06, 0.6)], solo_normal="derecha"),                  # pernos de desarme
        puntos([(0.95, 0.6), (0.06, 0.6)], solo_normal="derecha", signo_normal=-1),
        rectangulo((0.30, 0.65), (0.10, 0.18), 0.08, solo_normal="derecha", signo_normal=-1),  # selector
    ])
    negro = Material(NEGRO, desgaste=0.14, sombra=0.06)
    canon = Material(ACERO, desgaste=0.06, sombra=0.06)
    bocacha = Material((40, 40, 41), desgaste=0.08, patrones=[
        ranuras(largo=2, separacion=2, margen=1, delta=-0.30, solo_lados=False)])
    empunadura = Material(NEGRO, desgaste=0.05, sombra=0.10, patrones=[punteado(0.40, -0.08)])
    cargador = Material(ALUMINIO, desgaste=0.12, sombra=0.08, patrones=[
        rayas(periodo=5, ancho=1, delta=-0.08, direccion="adelante", solo_normal="derecha"),
        rayas(periodo=5, ancho=1, delta=-0.08, direccion="adelante", solo_normal="derecha", signo_normal=-1)])
    base_cargador = Material((44, 45, 47), desgaste=0.10)
    goma = Material(GOMA, desgaste=0.04, sombra=0.04, patrones=[rayas(periodo=2, delta=-0.05, direccion="arriba")])

    cuerpo = [
        # cañón con perfil M4 y base de mira delantera
        *cilindro("z", (8, 10), 0.30, -13.7, -8.6, canon, "canon"),
        *cilindro("z", (8, 10), 0.36, -7.4, -7.0, canon, "canon_tuerca"),
        C(7.55, 9.35, -8.6, 8.45, 10.65, -7.4, negro, "base_mira"),
        C(7.68, 10.65, -8.4, 8.32, 11.35, -7.6, negro, "torre_mira"),
        C(7.68, 11.35, -8.3, 7.82, 12.05, -7.7, negro, "oreja_izq"),
        C(8.18, 11.35, -8.3, 8.32, 12.05, -7.7, negro, "oreja_der"),
        C(7.95, 11.35, -8.1, 8.05, 11.95, -7.9, negro, "poste"),
        C(7.85, 8.95, -8.5, 8.15, 9.35, -7.9, negro, "tetón_bayoneta"),
        # guardamanos de cuatro rieles (KAC RAS)
        C(7.10, 9.25, -7.0, 8.90, 10.75, 0.6, fde, "guardamanos_a"),
        C(7.25, 9.10, -6.985, 8.75, 10.90, 0.585, fde, "guardamanos_b"),
        C(7.58, 10.90, -7.0, 8.42, 11.15, 0.6, riel_sup, "riel_sup"),
        C(7.58, 8.85, -7.0, 8.42, 9.10, 0.6, riel_inf, "riel_inf"),
        C(8.90, 9.58, -7.0, 9.15, 10.42, 0.6, riel_der, "riel_der"),
        C(6.85, 9.58, -7.0, 7.10, 10.42, 0.6, riel_izq, "riel_izq"),
        *cilindro("z", (8, 10), 0.95, 0.6, 1.2, negro, "anillo_delta"),
        # receptor superior con riel plano
        C(7.45, 9.20, 1.2, 8.55, 10.80, 8.6, receptor, "receptor_sup"),
        C(7.58, 10.80, 1.2, 8.42, 11.10, 8.4, riel_sup, "riel_receptor"),
        C(8.55, 9.85, 6.4, 8.75, 10.55, 6.9, fde, "deflector"),
        C(8.55, 10.05, 6.9, 8.85, 10.55, 7.8, negro, "asistente"),
        C(7.62, 10.50, 8.6, 8.38, 10.92, 9.35, negro, "manija_carga"),
        C(7.30, 10.52, 8.75, 7.62, 10.88, 9.35, negro, "pestillo_manija"),
        # receptor inferior, brocal y gatillo
        C(7.50, 8.20, 2.0, 8.50, 9.20, 9.0, inferior, "receptor_inf"),
        C(7.42, 7.20, 2.4, 8.58, 8.20, 5.0, inferior, "brocal"),
        C(7.38, 7.00, 2.35, 8.62, 7.20, 5.05, inferior, "brocal_labio"),
        C(8.50, 8.40, 4.95, 8.66, 8.80, 5.35, negro, "retén_cargador"),
        C(7.34, 8.50, 5.30, 7.50, 9.00, 5.90, negro, "retén_cerrojo"),
        C(7.34, 8.62, 7.00, 7.50, 8.88, 7.65, negro, "selector"),
        C(7.75, 6.98, 5.5, 8.25, 7.14, 7.9, negro, "guardamonte"),
        C(7.75, 7.14, 5.5, 8.25, 8.20, 5.72, negro, "guardamonte_frente"),
        C(7.92, 7.55, 6.50, 8.08, 8.20, 6.70, negro, "cola_disparador"),
        C(7.92, 7.32, 6.62, 8.08, 7.55, 6.84, negro, "cola_disparador_2"),
        # empuñadura A2 inclinada (cuatro tramos)
        C(7.50, 7.00, 7.55, 8.50, 8.20, 9.05, empunadura, "empunadura_1"),
        C(7.50, 5.90, 7.95, 8.50, 7.00, 9.45, empunadura, "empunadura_2"),
        C(7.50, 4.80, 8.35, 8.50, 5.90, 9.85, empunadura, "empunadura_3"),
        C(7.46, 4.30, 8.65, 8.54, 4.80, 10.15, empunadura, "empunadura_4"),
        # tubo del amortiguador, tuerca castillo y culata SOPMOD
        *cilindro("z", (8, 9.95), 0.70, 9.0, 9.45, negro, "tuerca_castillo"),
        *cilindro("z", (8, 9.95), 0.58, 9.45, 12.6, negro, "tubo"),
        C(7.30, 8.60, 12.5, 8.70, 10.85, 17.6, fde, "culata"),
        *cilindro("z", (7.12, 9.45), 0.42, 13.1, 17.5, fde, "tubo_bateria_izq"),
        *cilindro("z", (8.88, 9.45), 0.42, 13.1, 17.5, fde, "tubo_bateria_der"),
        C(7.40, 7.60, 15.0, 8.60, 8.60, 17.6, fde, "culata_bajo"),
        C(7.40, 6.80, 16.1, 8.60, 7.60, 17.6, fde, "culata_talon"),
        C(7.85, 8.30, 13.0, 8.15, 8.60, 14.1, negro, "palanca_culata"),
        C(7.25, 6.60, 17.6, 8.75, 11.00, 18.0, goma, "cantonera"),
    ]
    boca = [*cilindro("z", (8, 10), 0.42, -15.5, -13.7, bocacha, "apagallamas")]
    mira_hierro = [
        C(7.65, 11.10, 7.2, 8.35, 11.60, 8.2, negro, "alza_base"),
        C(7.65, 11.60, 7.5, 7.84, 12.25, 7.9, negro, "alza_ala_izq"),
        C(8.16, 11.60, 7.5, 8.35, 12.25, 7.9, negro, "alza_ala_der"),
        C(7.65, 12.25, 7.5, 8.35, 12.42, 7.9, negro, "alza_anillo"),
    ]
    mira_plegada = [C(7.65, 11.10, 7.0, 8.35, 11.42, 8.4, negro, "alza_plegada")]
    normal = [
        C(7.55, 5.20, 2.50, 8.45, 7.00, 4.95, cargador, "cargador_1"),
        C(7.55, 3.40, 2.15, 8.45, 5.20, 4.65, cargador, "cargador_2"),
        C(7.55, 2.00, 1.80, 8.45, 3.40, 4.35, cargador, "cargador_3"),
        C(7.48, 1.60, 1.60, 8.52, 2.00, 4.40, base_cargador, "cargador_base"),
    ]
    ampliado = [
        C(7.55, 5.20, 2.50, 8.45, 7.00, 4.95, cargador, "cargador_1"),
        C(7.55, 3.40, 2.15, 8.45, 5.20, 4.65, cargador, "cargador_2"),
        C(7.55, 1.70, 1.75, 8.45, 3.40, 4.30, cargador, "cargador_3"),
        C(7.55, 0.20, 1.30, 8.45, 1.70, 3.90, cargador, "cargador_4"),
        C(7.48, -0.20, 1.10, 8.52, 0.20, 3.95, base_cargador, "cargador_base"),
    ]
    return Arma(
        nombre="m4a1", cuerpo=cuerpo, cargador={"normal": normal, "ampliado": ampliado},
        mira_hierro=mira_hierro, mira_plegada=mira_plegada, boca=boca,
        montajes={
            "optica": {"pos": (8, 11.10, 4.6), "lado": "arriba"},
            "boca": {"pos": (8, 10, -13.7), "lado": "arriba"},
            "inferior": {"pos": (8, 8.85, -4.6), "lado": "abajo"},
            "laser": {"pos": (8, 11.15, -5.2), "lado": "arriba"},
            "linterna": {"pos": (6.85, 10.0, -5.4), "lado": "izquierda"},
        },
        opciones={
            "mira": ["", "punto_rojo", "holografica", "acog"],
            "boca": ["", "silenciador"],
            "bajo": ["", "empunadura"],
            "laser": ["", "laser"],
            "linterna": ["", "linterna"],
            "cargador": ["", "ampliado"],
        },
        linea_hierro={"y": 11.95, "z": 8.2},
        config={
            "fp": {"translation": [-5.5, 3.4, 2.0], "scale": 0.99},
            "tp": {"agarre": (8, 6.5, 8.6), "translation": [0, 0, 0.5], "scale": 0.58},
            "gui": {"scale": 0.45},
            "ads": {"hierro": 0.15, "optica": 0.24},
        },
    )


# ---------------------------------------------------------------- Beretta M9

def m9():
    """Beretta M9A3: 217 mm, corredera abierta, riel bajo el armazón. 1 unidad = 1,6 cm."""
    corredera = Material(NEGRO, desgaste=0.16, sombra=0.06)
    estrias = corredera.con(patrones=[
        rayas(periodo=2, ancho=1, delta=-0.12, direccion="adelante", solo_normal="derecha"),
        rayas(periodo=2, ancho=1, delta=-0.12, direccion="adelante", solo_normal="derecha", signo_normal=-1),
    ])
    armazon = Material((150, 128, 96), desgaste=0.10, sombra=0.08)   # M9A3 en color tierra
    negro = Material(NEGRO, desgaste=0.14, sombra=0.06)
    canon = Material((70, 70, 72), desgaste=0.10)
    cachas = Material((52, 46, 40), desgaste=0.05, sombra=0.10, patrones=[punteado(0.45, -0.10)])
    riel = armazon.con(patrones=[picatinny("arriba", signo=-1)])

    # Ánima en y = 10. La corredera va de z -5.2 (boca) a z 8.4 (extremo trasero).
    cuerpo = [
        C(7.20, 9.30, -5.0, 8.80, 11.20, -1.2, corredera, "corredera_frente"),
        C(7.20, 9.30, 2.4, 8.80, 11.20, 5.6, corredera, "corredera_media"),
        C(7.20, 9.30, 5.6, 8.80, 11.20, 8.4, estrias, "corredera_estrias"),
        C(7.20, 10.70, -1.2, 8.80, 11.20, 2.4, negro, "corredera_puente"),
        C(7.20, 9.30, -1.2, 7.55, 10.70, 2.4, negro, "corredera_lado_izq"),
        C(8.45, 9.30, -1.2, 8.80, 10.70, 2.4, negro, "corredera_lado_der"),
        *cilindro("z", (8, 10.05), 0.40, -1.2, 2.4, canon, "canon_visible"),
        C(7.87, 11.20, -4.6, 8.13, 11.75, -4.0, negro, "guion"),
        C(7.50, 11.20, 6.9, 7.80, 11.75, 7.6, negro, "alza_izq"),
        C(8.20, 11.20, 6.9, 8.50, 11.75, 7.6, negro, "alza_der"),
        C(7.10, 10.20, 5.8, 7.20, 10.80, 7.2, negro, "seguro_izq"),
        C(8.80, 10.20, 5.8, 8.90, 10.80, 7.2, negro, "seguro_der"),
        C(7.80, 9.80, 8.4, 8.20, 10.90, 8.9, negro, "martillo"),
        # armazón con riel, guardamonte y empuñadura
        C(7.30, 8.30, -3.6, 8.70, 9.30, 6.8, armazon, "armazon"),
        C(7.40, 8.05, -3.6, 8.60, 8.30, -0.4, riel, "riel_armazon"),
        C(7.40, 6.30, 0.6, 8.60, 6.60, 3.6, armazon, "guardamonte"),
        C(7.40, 6.60, 0.6, 8.60, 8.30, 0.95, armazon, "guardamonte_frente"),
        C(7.88, 6.90, 2.3, 8.12, 8.30, 2.65, negro, "disparador"),
        C(7.25, 3.20, 3.6, 8.75, 8.30, 7.2, armazon, "empunadura"),
        C(7.18, 3.80, 3.9, 7.25, 7.90, 6.9, cachas, "cacha_izq"),
        C(8.75, 3.80, 3.9, 8.82, 7.90, 6.9, cachas, "cacha_der"),
        C(7.25, 2.60, 4.1, 8.75, 3.20, 7.6, armazon, "empunadura_bajo"),
        C(7.90, 6.80, 7.2, 8.10, 8.30, 7.6, armazon, "lomo"),
    ]
    boca = []   # la M9 no lleva dispositivo de boca
    normal = [C(7.35, 2.20, 4.3, 8.65, 2.60, 7.2, Material((30, 30, 31)), "base_cargador")]
    ampliado = [
        C(7.35, 1.00, 4.4, 8.65, 2.60, 7.1, Material((70, 72, 75), desgaste=0.12), "cargador_extendido"),
        C(7.30, 0.70, 4.3, 8.70, 1.00, 7.3, Material((30, 30, 31)), "base_cargador"),
    ]
    return Arma(
        nombre="m9", cuerpo=cuerpo, cargador={"normal": normal, "ampliado": ampliado},
        mira_hierro=[], mira_plegada=[], boca=boca,
        montajes={
            "boca": {"pos": (8, 10.05, -5.0), "lado": "arriba"},
            "laser": {"pos": (8, 8.05, -2.5), "lado": "abajo"},
            "linterna": {"pos": (8, 8.05, -2.5), "lado": "abajo"},
        },
        opciones={"mira": [""], "boca": ["", "silenciador"], "bajo": [""],
                  "laser": ["", "laser"], "linterna": ["", "linterna"], "cargador": ["", "ampliado"]},
        silenciador="pistola",
        linea_hierro={"y": 11.75, "z": 7.6},
        config={
            "fp": {"translation": [-4.5, 2.8, 1.0], "scale": 0.98},
            "tp": {"agarre": (8, 5.5, 5.4), "translation": [0, 0, 0.5], "scale": 0.42},
            "gui": {"scale": 0.85},
            "ads": {"hierro": 0.18},
        },
    )


# ---------------------------------------------------------------- MP5

def mp5():
    """H&K MP5A3: 700 mm con culata extendida, cargador curvo de 30. 1 unidad = 2,2 cm."""
    negro = Material(NEGRO, desgaste=0.14, sombra=0.07)
    receptor = Material(POLIMERO, desgaste=0.13, sombra=0.08, patrones=[
        rectangulo((0.40, 0.55), (0.18, 0.35), 0, color=(18, 18, 19)),            # ventana de expulsión
        rayas(periodo=8, ancho=1, delta=-0.06, direccion="adelante", solo_normal="derecha"),
    ])
    guardamanos = Material((28, 29, 31), desgaste=0.08, sombra=0.10, patrones=[punteado(0.30, -0.06)])
    canon = Material(ACERO, desgaste=0.06)
    cargador = Material((50, 52, 55), desgaste=0.12, sombra=0.08)
    empunadura = Material(POLIMERO, desgaste=0.05, sombra=0.10, patrones=[punteado(0.40, -0.08)])
    riel = negro.con(patrones=[picatinny("arriba")])

    # Ánima en y = 10. Boca en z -12.2, culata hasta z 18.
    cuerpo = [
        *cilindro("z", (8, 10), 0.30, -12.2, -9.6, canon, "canon"),
        # tubo del cerrojo de carga sobre el cañón y mira delantera con capuchón
        *cilindro("z", (8, 10.95), 0.42, -9.8, -1.0, negro, "tubo_carga"),
        C(7.55, 10.30, -10.4, 8.45, 11.55, -9.6, negro, "mira_del_base"),
        C(7.55, 11.55, -10.3, 7.72, 12.40, -9.7, negro, "capuchon_izq"),
        C(8.28, 11.55, -10.3, 8.45, 12.40, -9.7, negro, "capuchon_der"),
        C(7.72, 12.25, -10.3, 8.28, 12.40, -9.7, negro, "capuchon_sup"),
        C(7.95, 11.55, -10.1, 8.05, 12.10, -9.9, negro, "poste"),
        C(8.42, 10.75, -3.4, 8.90, 11.15, -2.6, negro, "manija_carga"),
        # guardamanos delgado
        C(7.25, 8.90, -9.4, 8.75, 10.55, -2.6, guardamanos, "guardamanos_a"),
        C(7.40, 8.70, -9.385, 8.60, 10.70, -2.615, guardamanos, "guardamanos_b"),
        # receptor estampado con riel
        C(7.35, 9.20, -2.6, 8.65, 11.40, 7.4, receptor, "receptor"),
        C(7.58, 11.40, -1.0, 8.42, 11.70, 5.4, riel, "riel_receptor"),
        C(7.45, 8.30, 0.0, 8.55, 9.20, 3.1, receptor, "brocal"),
        # grupo de disparo y empuñadura
        C(7.40, 8.10, 3.4, 8.60, 9.20, 7.6, negro, "caja_gatillo"),
        C(7.38, 8.55, 6.2, 7.50, 8.85, 7.0, negro, "selector"),
        C(7.75, 7.20, 3.6, 8.25, 7.40, 5.9, negro, "guardamonte"),
        C(7.75, 7.40, 3.6, 8.25, 8.10, 3.85, negro, "guardamonte_frente"),
        C(7.92, 7.60, 4.5, 8.08, 8.10, 4.7, negro, "disparador"),
        C(7.45, 6.70, 5.9, 8.55, 8.10, 7.6, empunadura, "empunadura_1"),
        C(7.45, 5.10, 6.3, 8.55, 6.70, 8.0, empunadura, "empunadura_2"),
        C(7.45, 4.20, 6.6, 8.55, 5.10, 8.3, empunadura, "empunadura_3"),
        # culata retráctil de dos varillas
        C(7.40, 8.90, 7.6, 8.60, 10.60, 8.4, negro, "placa_culata"),
        *cilindro("z", (7.45, 10.40), 0.20, 8.4, 16.8, negro, "varilla_izq"),
        *cilindro("z", (8.55, 10.40), 0.20, 8.4, 16.8, negro, "varilla_der"),
        C(7.25, 7.40, 16.8, 8.75, 11.00, 17.6, negro, "cantonera"),
    ]
    normal = [
        C(7.55, 6.60, 0.25, 8.45, 8.30, 2.85, cargador, "cargador_1"),
        C(7.55, 5.00, -0.35, 8.45, 6.60, 2.30, cargador, "cargador_2"),
        C(7.55, 3.50, -1.05, 8.45, 5.00, 1.65, cargador, "cargador_3"),
        C(7.50, 3.10, -1.35, 8.50, 3.50, 1.45, cargador, "cargador_base"),
    ]
    ampliado = normal[:3] + [
        C(7.55, 2.10, -1.85, 8.45, 3.50, 1.05, cargador, "cargador_4"),
        C(7.50, 1.70, -2.15, 8.50, 2.10, 0.85, cargador, "cargador_base"),
    ]
    # alza de tambor con orificio (se saca al montar una óptica)
    mira_hierro = [
        C(7.40, 11.40, 6.0, 8.60, 11.78, 7.0, negro, "tambor_base"),
        C(7.40, 11.78, 6.0, 7.80, 12.42, 7.0, negro, "tambor_izq"),
        C(8.20, 11.78, 6.0, 8.60, 12.42, 7.0, negro, "tambor_der"),
        C(7.40, 12.42, 6.0, 8.60, 12.72, 7.0, negro, "tambor_sup"),
    ]
    return Arma(
        nombre="mp5", cuerpo=cuerpo, cargador={"normal": normal, "ampliado": ampliado},
        mira_hierro=mira_hierro, mira_plegada=[], boca=[],
        montajes={
            "optica": {"pos": (8, 11.70, 2.2), "lado": "arriba"},
            "boca": {"pos": (8, 10, -12.2), "lado": "arriba"},
            "inferior": {"pos": (8, 8.70, -6.2), "lado": "abajo"},
            "laser": {"pos": (8.75, 9.75, -6.0), "lado": "derecha"},
            "linterna": {"pos": (7.25, 9.75, -6.0), "lado": "izquierda"},
        },
        opciones={"mira": ["", "punto_rojo", "holografica", "acog"], "boca": ["", "silenciador"],
                  "bajo": ["", "empunadura"], "laser": ["", "laser"], "linterna": ["", "linterna"],
                  "cargador": ["", "ampliado"]},
        silenciador="subfusil",
        linea_hierro={"y": 12.10, "z": 7.0},
        config={
            "fp": {"translation": [-5.0, 3.2, -3.0], "scale": 0.94},
            "tp": {"agarre": (8, 6.0, 7.0), "translation": [0, 0, 0.5], "scale": 0.52},
            "gui": {"scale": 0.50},
            "ads": {"hierro": 0.15, "optica": 0.24},
        },
    )


# ---------------------------------------------------------------- Benelli M4 (M1014)

def m1014():
    """Benelli M4 / M1014: 1010 mm, tubo de cargador bajo el cañón. 1 unidad = 2,8 cm."""
    negro = Material(NEGRO, desgaste=0.14, sombra=0.07)
    receptor = Material((42, 43, 45), desgaste=0.13, sombra=0.08, patrones=[
        rectangulo((0.55, 0.55), (0.30, 0.40), 0, color=(20, 20, 21)),
        rectangulo((0.30, 0.35), (0.10, 0.18), 0.10)])
    riel = negro.con(patrones=[picatinny("arriba")])
    canon = Material(ACERO, desgaste=0.08)
    polimero = Material(POLIMERO, desgaste=0.08, sombra=0.10)
    agarre = polimero.con(patrones=[punteado(0.35, -0.07)])
    goma = Material(GOMA, desgaste=0.04, patrones=[rayas(periodo=2, delta=-0.05, direccion="arriba")])

    cuerpo = [
        *cilindro("z", (8, 10.3), 0.36, -16.0, -3.0, canon, "canon"),
        *cilindro("z", (8, 9.35), 0.38, -12.6, -3.05, negro, "tubo_cargador"),
        C(7.62, 8.80, -13.2, 8.38, 10.85, -12.6, negro, "abrazadera"),
        C(7.78, 10.70, -15.5, 8.22, 11.02, -14.8, negro, "guion_base"),
        C(7.94, 11.02, -15.35, 8.06, 11.62, -14.95, negro, "guion"),
        # guardamanos de polímero
        C(7.15, 8.70, -8.0, 8.85, 10.70, -1.0, polimero, "guardamanos_a"),
        C(7.30, 8.55, -7.985, 8.70, 10.85, -1.015, polimero, "guardamanos_b"),
        # receptor con riel y alza de anillo
        C(7.30, 8.40, -1.0, 8.70, 11.00, 7.0, receptor, "receptor"),
        C(7.58, 11.00, -0.6, 8.42, 11.30, 6.4, riel, "riel_receptor"),
        C(8.70, 9.80, 2.0, 9.15, 10.20, 2.8, negro, "manija_carga"),
        C(7.40, 7.60, 0.4, 8.60, 8.40, 3.2, negro, "ventana_carga"),
        C(7.75, 6.70, 3.4, 8.25, 6.90, 5.6, negro, "guardamonte"),
        C(7.75, 6.90, 3.4, 8.25, 8.40, 3.65, negro, "guardamonte_frente"),
        C(7.92, 7.20, 4.3, 8.08, 8.40, 4.55, negro, "disparador"),
        # empuñadura y culata telescópica
        C(7.45, 6.70, 5.6, 8.55, 8.40, 7.4, agarre, "empunadura_1"),
        C(7.45, 5.00, 6.0, 8.55, 6.70, 7.8, agarre, "empunadura_2"),
        C(7.45, 4.10, 6.3, 8.55, 5.00, 8.1, agarre, "empunadura_3"),
        *cilindro("z", (8, 9.9), 0.50, 7.0, 13.0, negro, "tubo_culata"),
        C(7.35, 8.20, 11.0, 8.65, 10.70, 16.8, polimero, "culata"),
        C(7.45, 6.80, 14.4, 8.55, 8.20, 16.8, polimero, "culata_talon"),
        C(7.25, 6.60, 16.8, 8.75, 10.90, 17.4, goma, "cantonera"),
    ]
    normal = [*cilindro("z", (8, 9.35), 0.40, -13.6, -12.6, negro, "tapa_tubo")]
    ampliado = [*cilindro("z", (8, 9.35), 0.38, -15.4, -13.2, negro, "extension_tubo"),
                *cilindro("z", (8, 9.35), 0.42, -15.8, -15.4, negro, "tapa_tubo")]
    # alza de anillo fantasma (se saca al montar una óptica)
    mira_hierro = [
        C(7.50, 11.30, 5.4, 7.70, 12.20, 6.2, negro, "alza_izq"),
        C(8.30, 11.30, 5.4, 8.50, 12.20, 6.2, negro, "alza_der"),
        C(7.70, 12.00, 5.4, 8.30, 12.20, 6.2, negro, "alza_sup"),
    ]
    return Arma(
        nombre="m1014", cuerpo=cuerpo, cargador={"normal": normal, "ampliado": ampliado},
        mira_hierro=mira_hierro, mira_plegada=[], boca=[],
        montajes={
            "optica": {"pos": (8, 11.30, 2.4), "lado": "arriba"},
            "laser": {"pos": (8.85, 9.70, -5.0), "lado": "derecha"},
            "linterna": {"pos": (7.15, 9.70, -5.0), "lado": "izquierda"},
        },
        opciones={"mira": ["", "punto_rojo", "holografica"], "boca": [""], "bajo": [""],
                  "laser": ["", "laser"], "linterna": ["", "linterna"], "cargador": ["", "ampliado"]},
        linea_hierro={"y": 11.62, "z": 6.2},
        config={
            "fp": {"translation": [-5.5, 3.0, 2.0], "scale": 0.92},
            "tp": {"agarre": (8, 6.0, 6.8), "translation": [0, 0, 0.5], "scale": 0.60},
            "gui": {"scale": 0.44},
            "ads": {"hierro": 0.15, "optica": 0.24},
        },
    )


# ---------------------------------------------------------------- Barrett M82A1

def barrett():
    """Barrett M82A1: 1448 mm, freno de boca doble, bípode plegado. 1 unidad = 3,2 cm."""
    negro = Material(NEGRO, desgaste=0.14, sombra=0.07)
    receptor = Material((48, 50, 47), desgaste=0.13, sombra=0.08, patrones=[
        rectangulo((0.55, 0.55), (0.18, 0.35), 0, color=(20, 20, 21)),
        puntos([(0.2, 0.3), (0.5, 0.3), (0.8, 0.3)], solo_normal="derecha"),
        puntos([(0.2, 0.3), (0.5, 0.3), (0.8, 0.3)], solo_normal="derecha", signo_normal=-1),
        rayas(periodo=6, ancho=1, delta=-0.05, direccion="adelante", solo_normal="derecha")])
    riel = negro.con(patrones=[picatinny("arriba")])
    canon = Material(ACERO, desgaste=0.06, patrones=[rayas(periodo=3, ancho=1, delta=-0.07)])
    freno = Material((40, 41, 43), desgaste=0.10, patrones=[
        rectangulo((0.30, 0.5), (0.25, 0.6), 0, color=(14, 14, 15), solo_normal="derecha"),
        rectangulo((0.30, 0.5), (0.25, 0.6), 0, color=(14, 14, 15), solo_normal="derecha", signo_normal=-1),
        rectangulo((0.72, 0.5), (0.25, 0.6), 0, color=(14, 14, 15), solo_normal="derecha"),
        rectangulo((0.72, 0.5), (0.25, 0.6), 0, color=(14, 14, 15), solo_normal="derecha", signo_normal=-1)])
    goma = Material(GOMA, desgaste=0.04, patrones=[rayas(periodo=2, delta=-0.05, direccion="arriba")])
    empunadura = Material(POLIMERO, desgaste=0.05, sombra=0.10, patrones=[punteado(0.40, -0.08)])
    cargador = Material((44, 46, 44), desgaste=0.12, sombra=0.08)

    cuerpo = [
        *cilindro("z", (8, 10), 0.42, -12.8, 0.0, canon, "canon"),
        # receptor superior grande con riel y manija de transporte
        C(7.15, 9.20, -1.0, 8.85, 11.10, 12.0, receptor, "receptor_sup"),
        C(7.25, 8.60, -6.5, 8.75, 11.00, -1.0, receptor, "camisa"),
        C(7.58, 11.10, -1.0, 8.42, 11.40, 11.4, riel, "riel"),
        C(7.40, 11.40, 4.6, 7.60, 12.60, 5.0, negro, "manija_pata_izq"),
        C(8.40, 11.40, 4.6, 8.60, 12.60, 5.0, negro, "manija_pata_der"),
        C(7.40, 12.60, 2.6, 8.60, 12.90, 5.0, negro, "manija"),
        # receptor inferior, brocal, gatillo y empuñadura
        C(7.20, 7.80, 1.5, 8.80, 9.20, 15.0, receptor, "receptor_inf"),
        C(7.25, 6.80, 3.0, 8.75, 7.80, 6.4, receptor, "brocal"),
        C(7.75, 6.40, 7.2, 8.25, 6.60, 9.6, negro, "guardamonte"),
        C(7.75, 6.60, 7.2, 8.25, 7.80, 7.45, negro, "guardamonte_frente"),
        C(7.92, 6.90, 8.1, 8.08, 7.80, 8.35, negro, "disparador"),
        C(7.45, 6.40, 9.6, 8.55, 7.80, 11.2, empunadura, "empunadura_1"),
        C(7.45, 4.80, 10.0, 8.55, 6.40, 11.6, empunadura, "empunadura_2"),
        C(7.45, 3.90, 10.3, 8.55, 4.80, 11.9, empunadura, "empunadura_3"),
        # culata con apoyo de mejilla y monopie
        C(7.30, 8.00, 15.0, 8.70, 10.90, 20.2, receptor, "culata"),
        C(7.40, 6.40, 17.6, 8.60, 8.00, 20.2, receptor, "culata_bajo"),
        C(7.20, 6.20, 20.2, 8.80, 11.10, 21.0, goma, "cantonera"),
        C(7.85, 5.40, 17.8, 8.15, 6.40, 18.2, negro, "monopie"),
        # bípode plegado bajo la camisa
        *cilindro("z", (7.55, 8.35), 0.18, -6.2, 1.5, negro, "bipode_izq"),
        *cilindro("z", (8.45, 8.35), 0.18, -6.2, 1.5, negro, "bipode_der"),
        C(7.40, 8.20, -6.6, 8.60, 8.60, -6.0, negro, "bipode_base"),
    ]
    boca = [
        *cilindro("z", (8, 10), 0.48, -13.2, -12.8, freno, "freno_cuello"),
        C(7.05, 9.40, -15.6, 8.95, 10.60, -13.2, freno, "freno_boca"),
    ]
    normal = [
        C(7.35, 4.40, 3.20, 8.65, 6.80, 6.20, cargador, "cargador"),
        C(7.30, 4.10, 3.10, 8.70, 4.40, 6.30, negro, "cargador_base"),
    ]
    return Arma(
        nombre="barrett", cuerpo=cuerpo, cargador={"normal": normal, "ampliado": normal},
        mira_hierro=[], mira_plegada=[], boca=boca,
        montajes={
            "optica": {"pos": (8, 11.40, 6.8), "lado": "arriba"},
            "boca": {"pos": (8, 10, -12.8), "lado": "arriba"},
        },
        opciones={"mira": ["telescopica"], "boca": ["", "silenciador"], "bajo": [""],
                  "laser": [""], "linterna": [""], "cargador": [""]},
        silenciador="francotirador", mira_defecto="telescopica",
        config={
            "fp": {"translation": [-5.5, 2.6, 0.0], "scale": 0.92},
            "tp": {"agarre": (8, 5.5, 10.8), "translation": [0, 0, 0.5], "scale": 0.70},
            "gui": {"scale": 0.33},
        },
    )


# ---------------------------------------------------------------- AK-47

def ak47():
    """AK-47 (AKM): 880 mm, madera, cargador curvo de 30. 1 unidad = 2,5 cm."""
    acero = Material((44, 45, 47), desgaste=0.16, sombra=0.08, patrones=[
        rectangulo((0.45, 0.55), (0.30, 0.30), 0, color=(20, 20, 21)),
        puntos([(0.85, 0.35), (0.15, 0.35)], solo_normal="derecha"),
        puntos([(0.85, 0.35), (0.15, 0.35)], solo_normal="derecha", signo_normal=-1)])
    tapa = Material((40, 41, 43), desgaste=0.18, sombra=0.06, patrones=[rayas(periodo=4, ancho=1, delta=-0.05)])
    madera = Material(MADERA, desgaste=0.12, sombra=0.12, patrones=[rayas(periodo=3, ancho=1, delta=-0.05, direccion="adelante")])
    madera_osc = madera.con(color=(86, 54, 30))
    canon = Material(ACERO, desgaste=0.08)
    negro = Material(NEGRO, desgaste=0.14, sombra=0.06)
    cargador = Material((150, 72, 34), desgaste=0.10, sombra=0.10, patrones=[
        rayas(periodo=4, ancho=1, delta=-0.08, direccion="adelante", solo_normal="derecha"),
        rayas(periodo=4, ancho=1, delta=-0.08, direccion="adelante", solo_normal="derecha", signo_normal=-1)])
    cuerpo = [
        *cilindro("z", (8, 10), 0.32, -15.0, -6.0, canon, "canon"),
        C(7.50, 9.40, -13.8, 8.50, 10.60, -12.8, negro, "base_guion"),
        C(7.70, 10.60, -13.6, 7.85, 11.95, -13.0, negro, "oreja_izq"),
        C(8.15, 10.60, -13.6, 8.30, 11.95, -13.0, negro, "oreja_der"),
        C(7.93, 10.60, -13.4, 8.07, 11.95, -13.2, negro, "guion"),
        C(7.55, 10.30, -9.6, 8.45, 11.30, -8.6, negro, "bloque_gases"),
        *cilindro("z", (8, 10.95), 0.36, -8.6, -6.2, negro, "tubo_gases"),
        C(7.25, 8.80, -6.4, 8.75, 10.40, -0.6, madera, "guardamanos_inf"),
        C(7.45, 10.40, -6.2, 8.55, 11.55, -1.0, madera_osc, "guardamanos_sup"),
        C(7.35, 8.60, -0.6, 8.65, 10.90, 9.0, acero, "receptor"),
        C(7.45, 10.90, 0.6, 8.55, 11.30, 9.0, tapa, "tapa_cierre"),
        C(8.65, 10.20, 1.6, 9.20, 10.55, 2.4, negro, "manija_carga"),
        C(8.65, 9.20, 4.6, 8.82, 9.90, 6.8, negro, "selector"),
        C(7.75, 6.90, 4.0, 8.25, 7.10, 6.6, negro, "guardamonte"),
        C(7.75, 7.10, 4.0, 8.25, 8.60, 4.25, negro, "guardamonte_frente"),
        C(7.92, 7.40, 5.0, 8.08, 8.60, 5.2, negro, "disparador"),
        C(7.45, 7.10, 6.8, 8.55, 8.60, 8.2, madera_osc, "empunadura_1"),
        C(7.45, 5.70, 7.2, 8.55, 7.10, 8.6, madera_osc, "empunadura_2"),
        C(7.45, 4.40, 7.6, 8.55, 5.70, 9.0, madera_osc, "empunadura_3"),
        C(7.30, 8.00, 9.0, 8.70, 10.60, 13.0, madera, "culata_1"),
        C(7.25, 7.20, 13.0, 8.75, 10.40, 17.0, madera, "culata_2"),
        C(7.20, 6.80, 17.0, 8.80, 10.30, 18.2, madera_osc, "culata_talon"),
    ]
    boca = [*cilindro("z", (8, 10), 0.38, -16.4, -15.0, negro, "compensador")]
    mira_hierro = [
        C(7.55, 10.90, -1.0, 8.45, 11.60, 0.4, negro, "alza_base"),
        C(7.55, 11.60, -0.6, 7.86, 12.15, 0.4, negro, "alza_izq"),
        C(8.14, 11.60, -0.6, 8.45, 12.15, 0.4, negro, "alza_der"),
    ]
    mira_plegada = [C(7.55, 10.90, -1.0, 8.45, 11.30, 0.4, negro, "alza_baja")]
    normal = [
        C(7.50, 6.60, 1.3, 8.50, 8.60, 3.9, cargador, "cargador_1"),
        C(7.50, 4.80, 0.8, 8.50, 6.60, 3.5, cargador, "cargador_2"),
        C(7.50, 3.20, -0.2, 8.50, 4.80, 2.7, cargador, "cargador_3"),
        C(7.45, 2.80, -0.5, 8.55, 3.20, 2.4, negro, "cargador_base"),
    ]
    ampliado = normal[:3] + [
        C(7.50, 1.80, -1.2, 8.50, 3.20, 1.9, cargador, "cargador_4"),
        C(7.45, 1.40, -1.6, 8.55, 1.80, 1.6, negro, "cargador_base"),
    ]
    return Arma(
        nombre="ak47", cuerpo=cuerpo, cargador={"normal": normal, "ampliado": ampliado},
        mira_hierro=mira_hierro, mira_plegada=mira_plegada, boca=boca,
        montajes={
            "optica": {"pos": (8, 11.30, 4.0), "lado": "arriba"},
            "boca": {"pos": (8, 10, -15.0), "lado": "arriba"},
            "inferior": {"pos": (8, 8.80, -3.6), "lado": "abajo"},
            "laser": {"pos": (8, 11.55, -3.8), "lado": "arriba"},
            "linterna": {"pos": (7.25, 9.6, -3.8), "lado": "izquierda"},
        },
        opciones={"mira": ["", "punto_rojo", "holografica", "acog"], "boca": ["", "silenciador"],
                  "bajo": ["", "empunadura"], "laser": ["", "laser"], "linterna": ["", "linterna"],
                  "cargador": ["", "ampliado"]},
        linea_hierro={"y": 11.95, "z": 0.4},
        config={
            "fp": {"translation": [-5.5, 3.4, 2.0], "scale": 0.99},
            "tp": {"agarre": (8, 6.5, 7.6), "translation": [0, 0, 0.5], "scale": 0.58},
            "gui": {"scale": 0.45},
            "ads": {"hierro": 0.15, "optica": 0.24},
        },
    )


# ---------------------------------------------------------------- FN P90

def p90():
    """FN P90: 500 mm bullpup, cargador translúcido de 50 encima. 1 unidad = 2,0 cm."""
    cuerpo_m = Material((44, 46, 44), desgaste=0.10, sombra=0.10, patrones=[punteado(0.15, -0.04)])
    negro = Material(NEGRO, desgaste=0.12, sombra=0.06)
    riel = negro.con(patrones=[picatinny("arriba")])
    canon = Material(ACERO, desgaste=0.06)
    cargador = Material((150, 120, 70), ruido=0.01, desgaste=0.04, sombra=0.04, alfa=0.85,
                        patrones=[rayas(periodo=2, ancho=1, delta=-0.10, direccion="adelante", solo_normal="arriba")])
    cuerpo = [
        *cilindro("z", (8, 9.4), 0.26, -12.0, -9.0, canon, "canon"),
        C(7.30, 8.40, -9.4, 8.70, 10.40, -5.6, cuerpo_m, "frente"),
        C(7.20, 6.20, -6.4, 8.80, 8.40, -2.2, cuerpo_m, "agarre_frontal"),
        C(7.35, 6.20, -2.2, 8.65, 6.80, 1.8, cuerpo_m, "puente_pulgar"),
        C(7.20, 6.20, 1.8, 8.80, 9.80, 11.6, cuerpo_m, "culata"),
        C(7.10, 8.40, -5.6, 8.90, 9.80, 1.8, cuerpo_m, "cuerpo"),
        C(7.40, 9.80, -3.0, 8.60, 10.60, 4.6, negro, "receptor"),
        C(7.58, 10.60, -2.4, 8.42, 10.90, 4.2, riel, "riel"),
        C(7.25, 5.80, 11.6, 8.75, 9.80, 12.8, negro, "cantonera"),
        C(7.92, 7.40, -1.0, 8.08, 8.40, -0.8, negro, "disparador"),
    ]
    normal = [C(7.25, 9.80, 4.6, 8.75, 10.80, 12.2, cargador, "cargador")]
    mira_hierro = [
        C(7.50, 10.90, 2.6, 8.50, 11.30, 3.8, negro, "mira_base"),
        C(7.50, 11.30, 2.6, 7.75, 12.00, 3.8, negro, "anillo_izq"),
        C(8.25, 11.30, 2.6, 8.50, 12.00, 3.8, negro, "anillo_der"),
        C(7.75, 11.85, 2.6, 8.25, 12.00, 3.8, negro, "anillo_sup"),
        C(7.95, 11.30, 2.9, 8.05, 11.65, 3.1, Material((220, 40, 30), ruido=0, volumen=0), "punto"),
    ]
    return Arma(
        nombre="p90", cuerpo=cuerpo, cargador={"normal": normal, "ampliado": normal},
        mira_hierro=mira_hierro, mira_plegada=[], boca=[],
        montajes={
            "optica": {"pos": (8, 10.90, 0.6), "lado": "arriba"},
            "boca": {"pos": (8, 9.4, -12.0), "lado": "arriba"},
            "laser": {"pos": (8.80, 7.6, -4.4), "lado": "derecha"},
            "linterna": {"pos": (7.20, 7.6, -4.4), "lado": "izquierda"},
        },
        opciones={"mira": ["", "punto_rojo", "holografica"], "boca": ["", "silenciador"], "bajo": [""],
                  "laser": ["", "laser"], "linterna": ["", "linterna"], "cargador": [""]},
        silenciador="subfusil",
        linea_hierro={"y": 11.50, "z": 3.8},
        config={
            "fp": {"translation": [-5.0, 3.0, -3.0], "scale": 0.94},
            "tp": {"agarre": (8, 7.0, 3.0), "translation": [0, 0, 0.5], "scale": 0.52},
            "gui": {"scale": 0.55},
            "ads": {"hierro": 0.16, "optica": 0.24},
        },
    )


# ---------------------------------------------------------------- KRISS Vector

def vector():
    """KRISS Vector: 620 mm, mentón angular delante del gatillo y culata plegable. 1 unidad = 2,0 cm."""
    negro = Material(NEGRO, desgaste=0.12, sombra=0.08)
    cuerpo_m = Material((40, 42, 44), desgaste=0.12, sombra=0.10, patrones=[
        rectangulo((0.5, 0.5), (0.5, 0.3), -0.06, solo_normal="derecha"),
        rectangulo((0.5, 0.5), (0.5, 0.3), -0.06, solo_normal="derecha", signo_normal=-1)])
    riel = negro.con(patrones=[picatinny("arriba")])
    canon = Material(ACERO, desgaste=0.06)
    cargador = Material((36, 36, 38), desgaste=0.10, sombra=0.08)
    empunadura = Material(POLIMERO, desgaste=0.05, sombra=0.10, patrones=[punteado(0.40, -0.08)])
    cuerpo = [
        *cilindro("z", (8, 10), 0.30, -12.6, -7.0, canon, "canon"),
        C(7.30, 9.20, -7.4, 8.70, 10.90, 6.0, cuerpo_m, "receptor"),
        C(7.58, 10.90, -7.0, 8.42, 11.20, 5.6, riel, "riel"),
        C(7.20, 6.40, -6.8, 8.80, 9.20, -1.4, cuerpo_m, "menton"),
        C(7.25, 5.60, -6.2, 8.75, 6.40, -3.6, cuerpo_m, "menton_bajo"),
        C(7.75, 7.00, -1.4, 8.25, 7.20, 1.8, negro, "guardamonte"),
        C(7.92, 7.40, -0.6, 8.08, 9.20, -0.4, negro, "disparador"),
        C(7.40, 7.20, 1.6, 8.60, 9.20, 3.0, empunadura, "empunadura_1"),
        C(7.40, 5.60, 2.0, 8.60, 7.20, 3.4, empunadura, "empunadura_2"),
        C(7.40, 4.40, 2.4, 8.60, 5.60, 3.8, empunadura, "empunadura_3"),
        C(7.40, 9.40, 6.0, 8.60, 10.40, 14.0, negro, "culata_brazo"),
        C(7.30, 7.60, 13.2, 8.70, 10.60, 14.6, negro, "culata_placa"),
        C(8.70, 9.80, -5.4, 9.10, 10.30, -4.2, negro, "manija_carga"),
    ]
    normal = [
        C(7.50, 3.20, -5.8, 8.50, 5.60, -4.0, cargador, "cargador"),
        C(7.45, 2.90, -5.9, 8.55, 3.20, -3.9, negro, "cargador_base"),
    ]
    ampliado = [
        C(7.50, 1.60, -5.8, 8.50, 5.60, -4.0, cargador, "cargador"),
        C(7.45, 1.30, -5.9, 8.55, 1.60, -3.9, negro, "cargador_base"),
    ]
    mira_hierro = [
        C(7.65, 11.20, -6.4, 8.35, 11.95, -5.8, negro, "guion"),
        C(7.60, 11.20, 4.6, 8.40, 11.70, 5.4, negro, "alza_base"),
        C(7.60, 11.70, 4.8, 7.82, 12.10, 5.2, negro, "alza_izq"),
        C(8.18, 11.70, 4.8, 8.40, 12.10, 5.2, negro, "alza_der"),
    ]
    return Arma(
        nombre="vector", cuerpo=cuerpo, cargador={"normal": normal, "ampliado": ampliado},
        mira_hierro=mira_hierro, mira_plegada=[], boca=[],
        montajes={
            "optica": {"pos": (8, 11.20, 1.0), "lado": "arriba"},
            "boca": {"pos": (8, 10, -12.6), "lado": "arriba"},
            "inferior": {"pos": (8, 9.20, -9.2), "lado": "abajo"},
            "laser": {"pos": (8.70, 10.0, -4.0), "lado": "derecha"},
            "linterna": {"pos": (7.30, 10.0, -4.0), "lado": "izquierda"},
        },
        opciones={"mira": ["", "punto_rojo", "holografica"], "boca": ["", "silenciador"],
                  "bajo": ["", "empunadura"], "laser": ["", "laser"], "linterna": ["", "linterna"],
                  "cargador": ["", "ampliado"]},
        silenciador="subfusil",
        linea_hierro={"y": 11.95, "z": 5.4},
        config={
            "fp": {"translation": [-5.0, 3.2, -3.0], "scale": 0.94},
            "tp": {"agarre": (8, 6.0, 2.6), "translation": [0, 0, 0.5], "scale": 0.52},
            "gui": {"scale": 0.55},
            "ads": {"hierro": 0.15, "optica": 0.24},
        },
    )


# ---------------------------------------------------------------- M249 SAW

def m249():
    """FN M249 SAW: 1040 mm, caja de cinta de 100 a la izquierda y bípode. 1 unidad = 3,0 cm."""
    negro = Material(NEGRO, desgaste=0.14, sombra=0.08)
    receptor = Material((42, 44, 42), desgaste=0.14, sombra=0.08, patrones=[
        puntos([(0.2, 0.3), (0.5, 0.3), (0.8, 0.3)], solo_normal="derecha"),
        puntos([(0.2, 0.3), (0.5, 0.3), (0.8, 0.3)], solo_normal="derecha", signo_normal=-1)])
    riel = negro.con(patrones=[picatinny("arriba")])
    canon = Material(ACERO, desgaste=0.08, patrones=[rayas(periodo=3, ancho=1, delta=-0.05)])
    caja = Material((78, 82, 56), desgaste=0.10, sombra=0.10, patrones=[rayas(periodo=6, ancho=1, delta=-0.06)])
    empunadura = Material(POLIMERO, desgaste=0.05, sombra=0.10, patrones=[punteado(0.40, -0.08)])
    cuerpo = [
        *cilindro("z", (8, 10), 0.36, -15.0, -4.0, canon, "canon"),
        *cilindro("z", (8, 8.95), 0.30, -10.0, -3.0, negro, "tubo_gases"),
        C(7.55, 10.30, -14.0, 8.45, 11.40, -13.2, negro, "base_guion"),
        C(7.92, 11.40, -13.8, 8.08, 12.30, -13.4, negro, "guion"),
        C(7.80, 10.40, -7.6, 8.20, 11.60, -7.0, negro, "manija_pie"),
        C(7.70, 11.60, -9.4, 8.30, 11.90, -5.2, negro, "manija"),
        C(7.20, 8.30, -4.0, 8.80, 10.20, -0.6, negro, "guardamanos"),
        C(7.10, 8.20, -0.6, 8.90, 10.60, 9.0, receptor, "receptor"),
        C(7.20, 10.60, 0.2, 8.80, 11.40, 7.2, receptor, "tapa_alimentador"),
        C(7.58, 11.40, 0.8, 8.42, 11.70, 6.8, riel, "riel"),
        C(6.40, 9.10, 0.6, 7.10, 10.00, 2.4, negro, "boca_alimentacion"),
        C(7.75, 6.90, 4.4, 8.25, 7.10, 6.8, negro, "guardamonte"),
        C(7.92, 7.30, 5.2, 8.08, 8.20, 5.4, negro, "disparador"),
        C(7.40, 7.10, 6.6, 8.60, 8.20, 8.0, empunadura, "empunadura_1"),
        C(7.40, 5.60, 7.0, 8.60, 7.10, 8.4, empunadura, "empunadura_2"),
        C(7.40, 4.40, 7.4, 8.60, 5.60, 8.8, empunadura, "empunadura_3"),
        C(7.30, 7.60, 9.0, 8.70, 10.40, 16.0, negro, "culata"),
        C(7.20, 7.20, 16.0, 8.80, 10.60, 16.8, Material(GOMA), "cantonera"),
        *cilindro("z", (7.55, 8.50), 0.16, -11.5, -4.6, negro, "bipode_izq"),
        *cilindro("z", (8.45, 8.50), 0.16, -11.5, -4.6, negro, "bipode_der"),
    ]
    normal = [
        C(4.60, 5.20, 0.4, 7.10, 8.80, 4.6, caja, "caja_cinta"),
        C(4.60, 8.80, 1.2, 7.10, 9.10, 3.8, negro, "tapa_caja"),
    ]
    mira_hierro = [
        C(7.55, 11.70, 5.6, 8.45, 12.05, 6.8, negro, "alza_base"),
        C(7.55, 12.05, 6.2, 7.78, 12.60, 6.8, negro, "alza_izq"),
        C(8.22, 12.05, 6.2, 8.45, 12.60, 6.8, negro, "alza_der"),
    ]
    return Arma(
        nombre="m249", cuerpo=cuerpo, cargador={"normal": normal, "ampliado": normal},
        mira_hierro=mira_hierro, mira_plegada=[], boca=[],
        montajes={
            "optica": {"pos": (8, 11.70, 3.2), "lado": "arriba"},
            "boca": {"pos": (8, 10, -15.0), "lado": "arriba"},
            "inferior": {"pos": (8, 8.30, -2.4), "lado": "abajo"},
        },
        opciones={"mira": ["", "punto_rojo", "holografica", "acog"], "boca": [""], "bajo": ["", "empunadura"],
                  "laser": [""], "linterna": [""], "cargador": [""]},
        linea_hierro={"y": 12.30, "z": 6.8},
        config={
            "fp": {"translation": [-5.5, 3.0, 1.0], "scale": 0.95},
            "tp": {"agarre": (8, 6.0, 7.8), "translation": [0, 0, 0.5], "scale": 0.62},
            "gui": {"scale": 0.40},
            "ads": {"hierro": 0.15, "optica": 0.24},
        },
    )


# ---------------------------------------------------------------- Remington M24

def m24():
    """Remington M24 SWS: 1092 mm, cerrojo, culata verde de fibra. 1 unidad = 3,2 cm."""
    verde = Material((72, 80, 56), desgaste=0.10, sombra=0.10, patrones=[punteado(0.30, -0.05)])
    negro = Material(NEGRO, desgaste=0.16, sombra=0.06)
    canon = Material(ACERO, desgaste=0.08)
    cuerpo = [
        *cilindro("z", (8, 10.2), 0.34, -15.0, -1.0, canon, "canon"),
        C(7.25, 8.40, -9.0, 8.75, 9.95, 1.0, verde, "guardamanos"),
        C(7.30, 7.90, 1.0, 8.70, 9.95, 7.4, verde, "accion_culata"),
        *cilindro("z", (8, 10.25), 0.62, 0.0, 6.6, negro, "receptor"),
        C(8.55, 10.00, 5.4, 9.30, 10.30, 5.8, negro, "palanca_cerrojo"),
        *cilindro("x", (10.15, 5.6), 0.26, 9.30, 9.90, negro, "bola_cerrojo"),
        C(7.75, 6.90, 3.6, 8.25, 7.10, 6.2, negro, "guardamonte"),
        C(7.92, 7.30, 4.6, 8.08, 7.90, 4.8, negro, "disparador"),
        C(7.35, 6.40, 7.0, 8.65, 8.80, 9.6, verde, "garganta"),
        C(7.30, 7.40, 9.6, 8.70, 10.10, 16.4, verde, "culata"),
        C(7.40, 10.10, 10.4, 8.60, 10.60, 14.0, verde, "carrillera"),
        C(7.25, 6.80, 16.4, 8.75, 10.40, 17.2, Material(GOMA), "cantonera"),
    ]
    normal = [C(7.50, 7.60, 2.2, 8.50, 7.90, 4.4, negro, "chapa_cargador")]
    return Arma(
        nombre="m24", cuerpo=cuerpo, cargador={"normal": normal, "ampliado": normal},
        mira_hierro=[], mira_plegada=[], boca=[],
        montajes={
            "optica": {"pos": (8, 10.87, 3.4), "lado": "arriba"},
            "boca": {"pos": (8, 10.2, -15.0), "lado": "arriba"},
        },
        opciones={"mira": ["telescopica"], "boca": ["", "silenciador"], "bajo": [""],
                  "laser": [""], "linterna": [""], "cargador": [""]},
        silenciador="francotirador", mira_defecto="telescopica",
        config={
            "fp": {"translation": [-5.5, 2.6, 0.0], "scale": 0.92},
            "tp": {"agarre": (8, 6.5, 8.2), "translation": [0, 0, 0.5], "scale": 0.68},
            "gui": {"scale": 0.36},
        },
    )


# ---------------------------------------------------------------- Remington 870

def r870():
    """Remington 870 de bombeo: 1000 mm, guardamanos de madera y tubo de cargador. 1 unidad = 2,8 cm."""
    madera = Material(MADERA, desgaste=0.12, sombra=0.12, patrones=[rayas(periodo=3, ancho=1, delta=-0.05, direccion="adelante")])
    bombeo = madera.con(patrones=[rayas(periodo=2, ancho=1, delta=-0.15)])
    negro = Material(NEGRO, desgaste=0.18, sombra=0.06)
    canon = Material(ACERO, desgaste=0.10)
    cuerpo = [
        *cilindro("z", (8, 10.4), 0.36, -15.0, -1.0, canon, "canon"),
        *cilindro("z", (8, 9.35), 0.40, -12.6, -1.0, negro, "tubo_cargador"),
        C(7.20, 8.50, -9.6, 8.80, 9.95, -4.0, bombeo, "bombeo"),
        C(7.35, 8.40, -1.0, 8.65, 10.95, 6.0, negro, "receptor"),
        C(7.80, 10.76, -14.9, 8.20, 11.15, -14.5, Material((222, 200, 120)), "punto_mira"),
        C(7.75, 6.90, 3.0, 8.25, 7.10, 5.6, negro, "guardamonte"),
        C(7.75, 7.10, 3.0, 8.25, 8.40, 3.25, negro, "guardamonte_frente"),
        C(7.92, 7.40, 4.0, 8.08, 8.40, 4.2, negro, "disparador"),
        C(7.35, 7.40, 6.0, 8.65, 9.80, 8.6, madera, "garganta"),
        C(7.35, 6.20, 7.4, 8.65, 7.40, 9.0, madera, "puño"),
        C(7.30, 7.00, 8.6, 8.70, 10.20, 17.0, madera, "culata"),
        C(7.25, 6.60, 17.0, 8.75, 10.40, 17.8, Material(GOMA), "cantonera"),
    ]
    normal = [*cilindro("z", (8, 9.35), 0.44, -13.2, -12.6, negro, "tapa_cargador")]
    return Arma(
        nombre="r870", cuerpo=cuerpo, cargador={"normal": normal, "ampliado": normal},
        mira_hierro=[], mira_plegada=[], boca=[],
        montajes={
            "optica": {"pos": (8, 10.95, 2.4), "lado": "arriba"},
            "boca": {"pos": (8, 10.4, -15.0), "lado": "arriba"},
            "laser": {"pos": (8, 8.50, -6.8), "lado": "abajo"},
            "linterna": {"pos": (8.80, 9.2, -6.8), "lado": "derecha"},
        },
        opciones={"mira": ["", "punto_rojo"], "boca": [""], "bajo": [""],
                  "laser": ["", "laser"], "linterna": ["", "linterna"], "cargador": [""]},
        linea_hierro={"y": 11.15, "z": 6.0},
        config={
            "fp": {"translation": [-5.5, 3.2, 1.0], "scale": 0.96},
            "tp": {"agarre": (8, 6.5, 7.8), "translation": [0, 0, 0.5], "scale": 0.60},
            "gui": {"scale": 0.42},
            "ads": {"hierro": 0.15, "optica": 0.24},
        },
    )


# ---------------------------------------------------------------- Desert Eagle

def deagle():
    """IMI Desert Eagle .50 AE: 270 mm, cañón triangular con riel. 1 unidad = 1,6 cm."""
    plata = Material((168, 170, 172), desgaste=0.18, sombra=0.08)
    estrias = plata.con(patrones=[
        rayas(periodo=2, ancho=1, delta=-0.12, direccion="adelante", solo_normal="derecha"),
        rayas(periodo=2, ancho=1, delta=-0.12, direccion="adelante", solo_normal="derecha", signo_normal=-1)])
    negro = Material(NEGRO, desgaste=0.14, sombra=0.06)
    goma = Material(GOMA, desgaste=0.04, sombra=0.10, patrones=[punteado(0.45, -0.10)])
    riel = plata.con(patrones=[picatinny("arriba")])
    cuerpo = [
        C(7.10, 9.20, -7.0, 8.90, 11.30, 2.0, plata, "canon_cuerpo"),
        C(7.40, 11.30, -7.0, 8.60, 11.55, 1.6, riel, "riel"),
        C(7.20, 9.30, 2.0, 8.80, 11.20, 6.0, plata, "corredera"),
        C(7.20, 9.30, 6.0, 8.80, 11.20, 8.6, estrias, "corredera_estrias"),
        C(7.86, 11.55, -6.6, 8.14, 12.05, -6.0, negro, "guion"),
        C(7.50, 11.20, 7.6, 7.80, 11.80, 8.4, negro, "alza_izq"),
        C(8.20, 11.20, 7.6, 8.50, 11.80, 8.4, negro, "alza_der"),
        C(7.25, 8.20, -3.6, 8.75, 9.30, 7.0, plata, "armazon"),
        C(7.40, 6.30, 0.6, 8.60, 6.60, 3.8, plata, "guardamonte"),
        C(7.40, 6.60, 0.6, 8.60, 8.20, 0.95, plata, "guardamonte_frente"),
        C(7.88, 6.90, 2.3, 8.12, 8.20, 2.65, negro, "disparador"),
        C(7.15, 3.00, 3.8, 8.85, 8.20, 7.8, goma, "empunadura"),
        C(7.80, 9.80, 8.6, 8.20, 11.00, 9.1, negro, "martillo"),
    ]
    normal = [C(7.30, 2.50, 4.0, 8.70, 3.00, 7.6, negro, "base_cargador")]
    ampliado = [
        C(7.35, 1.30, 4.1, 8.65, 3.00, 7.5, Material((70, 72, 75), desgaste=0.12), "cargador_extendido"),
        C(7.30, 1.00, 4.0, 8.70, 1.30, 7.6, negro, "base_cargador"),
    ]
    return Arma(
        nombre="deagle", cuerpo=cuerpo, cargador={"normal": normal, "ampliado": ampliado},
        mira_hierro=[], mira_plegada=[], boca=[],
        montajes={
            "boca": {"pos": (8, 10.3, -7.0), "lado": "arriba"},
            "laser": {"pos": (8, 8.20, -2.2), "lado": "abajo"},
            "linterna": {"pos": (8, 8.20, -2.2), "lado": "abajo"},
        },
        opciones={"mira": [""], "boca": [""], "bajo": [""], "laser": ["", "laser"], "linterna": ["", "linterna"],
                  "cargador": ["", "ampliado"]},
        silenciador="pistola",
        linea_hierro={"y": 12.05, "z": 8.4},
        config={
            "fp": {"translation": [-4.5, 2.8, 1.0], "scale": 0.98},
            "tp": {"agarre": (8, 5.5, 5.8), "translation": [0, 0, 0.5], "scale": 0.44},
            "gui": {"scale": 0.75},
            "ads": {"hierro": 0.18},
        },
    )


# ---------------------------------------------------------------- FN SCAR-H

def scarh():
    """FN SCAR-H (Mk 17): 997 mm, receptor superior color tierra, cargador de acero de 20. 1 u = 2,8 cm."""
    fde = Material((176, 150, 108), desgaste=0.10, sombra=0.08)
    riel = fde.con(patrones=[picatinny("arriba")])
    riel_inf = fde.con(patrones=[picatinny("arriba", signo=-1)])
    riel_der = fde.con(patrones=[picatinny("derecha")])
    riel_izq = fde.con(patrones=[picatinny("derecha", signo=-1)])
    negro = Material(NEGRO, desgaste=0.14, sombra=0.06)
    canon = Material(ACERO, desgaste=0.06)
    empunadura = Material(NEGRO, desgaste=0.05, sombra=0.10, patrones=[punteado(0.40, -0.08)])
    acero = Material((48, 49, 52), desgaste=0.12, patrones=[
        rayas(periodo=4, ancho=1, delta=-0.08, direccion="adelante", solo_normal="derecha"),
        rayas(periodo=4, ancho=1, delta=-0.08, direccion="adelante", solo_normal="derecha", signo_normal=-1)])
    cuerpo = [
        *cilindro("z", (8, 10), 0.32, -15.0, -7.0, canon, "canon"),
        C(7.55, 9.45, -8.4, 8.45, 10.55, -7.4, negro, "bloque_gases"),
        C(7.30, 9.40, -7.0, 8.70, 11.00, 7.0, fde, "receptor_sup"),
        C(7.58, 11.00, -7.0, 8.42, 11.30, 6.8, riel, "riel_sup"),
        C(7.58, 9.10, -7.0, 8.42, 9.40, -1.6, riel_inf, "riel_inf"),
        C(8.70, 9.80, -6.8, 8.95, 10.60, -3.0, riel_der, "riel_der"),
        C(7.05, 9.80, -6.8, 7.30, 10.60, -3.0, riel_izq, "riel_izq"),
        C(7.00, 10.20, -3.0, 7.30, 10.60, -2.0, negro, "manija_carga"),
        C(7.45, 8.30, 0.5, 8.55, 9.40, 7.0, negro, "receptor_inf"),
        C(7.45, 7.20, 1.0, 8.55, 8.30, 3.6, negro, "brocal"),
        C(7.75, 6.98, 3.9, 8.25, 7.14, 6.0, negro, "guardamonte"),
        C(7.75, 7.14, 3.9, 8.25, 8.30, 4.12, negro, "guardamonte_frente"),
        C(7.92, 7.50, 4.8, 8.08, 8.30, 5.0, negro, "disparador"),
        C(7.50, 7.00, 5.6, 8.50, 8.30, 7.0, empunadura, "empunadura_1"),
        C(7.50, 5.80, 6.0, 8.50, 7.00, 7.4, empunadura, "empunadura_2"),
        C(7.50, 4.60, 6.4, 8.50, 5.80, 7.8, empunadura, "empunadura_3"),
        C(7.40, 8.80, 7.0, 8.60, 10.80, 7.8, negro, "bisagra"),
        C(7.35, 8.60, 7.8, 8.65, 10.60, 14.0, fde, "culata"),
        C(7.45, 10.60, 9.4, 8.55, 11.00, 12.6, fde, "carrillera"),
        C(7.40, 7.60, 11.6, 8.60, 8.60, 14.0, fde, "culata_bajo"),
        C(7.30, 7.40, 14.0, 8.70, 10.80, 14.6, Material(GOMA), "cantonera"),
    ]
    boca = [*cilindro("z", (8, 10), 0.42, -16.4, -15.0, negro, "apagallamas")]
    mira_hierro = [
        C(7.70, 11.30, -6.6, 8.30, 11.60, -6.0, negro, "guion_base"),
        C(7.93, 11.60, -6.4, 8.07, 12.00, -6.2, negro, "guion"),
        C(7.65, 11.30, 5.6, 8.35, 11.60, 6.6, negro, "alza_base"),
        C(7.65, 11.60, 6.0, 7.84, 12.25, 6.4, negro, "alza_ala_izq"),
        C(8.16, 11.60, 6.0, 8.35, 12.25, 6.4, negro, "alza_ala_der"),
        C(7.65, 12.25, 6.0, 8.35, 12.42, 6.4, negro, "alza_anillo"),
    ]
    mira_plegada = [
        C(7.70, 11.30, -6.6, 8.30, 11.55, -5.6, negro, "guion_plegado"),
        C(7.65, 11.30, 5.4, 8.35, 11.55, 6.6, negro, "alza_plegada"),
    ]
    normal = [
        C(7.50, 5.10, 1.30, 8.50, 7.20, 3.40, acero, "cargador_1"),
        C(7.50, 3.70, 1.00, 8.50, 5.10, 3.10, acero, "cargador_2"),
        C(7.45, 3.30, 0.90, 8.55, 3.70, 3.20, negro, "cargador_base"),
    ]
    ampliado = normal[:2] + [
        C(7.50, 2.50, 0.75, 8.50, 3.70, 2.85, acero, "cargador_3"),
        C(7.45, 2.10, 0.65, 8.55, 2.50, 2.95, negro, "cargador_base"),
    ]
    return Arma(
        nombre="scarh", cuerpo=cuerpo, cargador={"normal": normal, "ampliado": ampliado},
        mira_hierro=mira_hierro, mira_plegada=mira_plegada, boca=boca,
        montajes={
            "optica": {"pos": (8, 11.30, 2.0), "lado": "arriba"},
            "boca": {"pos": (8, 10, -15.0), "lado": "arriba"},
            "inferior": {"pos": (8, 9.10, -4.4), "lado": "abajo"},
            "laser": {"pos": (8.95, 10.2, -5.0), "lado": "derecha"},
            "linterna": {"pos": (7.05, 10.2, -5.0), "lado": "izquierda"},
        },
        opciones={"mira": ["", "punto_rojo", "holografica", "acog"], "boca": ["", "silenciador"],
                  "bajo": ["", "empunadura"], "laser": ["", "laser"], "linterna": ["", "linterna"],
                  "cargador": ["", "ampliado"]},
        linea_hierro={"y": 12.0, "z": 6.6},
        config={
            "fp": {"translation": [-5.5, 3.3, 2.0], "scale": 0.99},
            "tp": {"agarre": (8, 6.3, 6.4), "translation": [0, 0, 0.5], "scale": 0.60},
            "gui": {"scale": 0.44},
            "ads": {"hierro": 0.15, "optica": 0.24},
        },
    )


# ---------------------------------------------------------------- HK G36K

def g36k():
    """HK G36K: 860 mm, polímero negro, cargador translúcido y culata esqueleto. 1 u = 2,4 cm."""
    poli = Material(POLIMERO, desgaste=0.10, sombra=0.08, patrones=[punteado(0.12, -0.04)])
    ventilado = poli.con(patrones=[ranuras(largo=3, separacion=2, margen=1, delta=-0.25)])
    riel = Material(NEGRO, desgaste=0.12, patrones=[picatinny("arriba")])
    negro = Material(NEGRO, desgaste=0.14, sombra=0.06)
    canon = Material(ACERO, desgaste=0.06)
    cargador = Material((80, 84, 92), ruido=0.01, desgaste=0.05, sombra=0.05, alfa=0.82,
                        patrones=[rayas(periodo=3, ancho=1, delta=-0.10, direccion="adelante", solo_normal="derecha"),
                                  rayas(periodo=3, ancho=1, delta=-0.10, direccion="adelante", solo_normal="derecha", signo_normal=-1)])
    cuerpo = [
        *cilindro("z", (8, 10), 0.30, -13.5, -8.0, canon, "canon"),
        C(7.25, 8.90, -8.0, 8.75, 10.80, -1.0, ventilado, "guardamanos"),
        C(7.35, 8.40, -1.0, 8.65, 10.90, 7.4, poli, "receptor"),
        C(7.58, 10.90, -1.0, 8.42, 11.20, 7.0, riel, "riel"),
        C(7.45, 7.40, 1.4, 8.55, 8.40, 3.8, poli, "brocal"),
        C(7.60, 6.70, 4.0, 8.40, 6.95, 6.4, poli, "guardamonte"),
        C(7.60, 6.95, 4.0, 8.40, 8.40, 4.25, poli, "guardamonte_frente"),
        C(7.92, 7.40, 4.9, 8.08, 8.40, 5.1, negro, "disparador"),
        C(7.50, 6.95, 6.0, 8.50, 8.40, 7.4, poli, "empunadura_1"),
        C(7.50, 5.60, 6.4, 8.50, 6.95, 7.8, poli, "empunadura_2"),
        C(7.50, 4.50, 6.8, 8.50, 5.60, 8.2, poli, "empunadura_3"),
        C(7.45, 9.60, 7.4, 8.55, 10.40, 13.6, poli, "culata_sup"),
        C(7.45, 7.60, 10.6, 8.55, 8.30, 13.6, poli, "culata_inf"),
        C(7.45, 8.30, 9.6, 8.55, 9.60, 10.6, poli, "culata_diagonal"),
        C(7.35, 7.40, 13.6, 8.65, 10.60, 14.2, Material(GOMA), "cantonera"),
    ]
    boca = [*cilindro("z", (8, 10), 0.40, -15.0, -13.5, negro, "apagallamas")]
    mira_hierro = [
        C(7.65, 11.20, -0.8, 8.35, 11.50, 0.0, negro, "guion_base"),
        C(7.93, 11.50, -0.6, 8.07, 11.95, -0.3, negro, "guion"),
        C(7.65, 11.20, 6.0, 8.35, 11.50, 7.0, negro, "alza_base"),
        C(7.65, 11.50, 6.6, 7.84, 12.20, 7.0, negro, "alza_ala_izq"),
        C(8.16, 11.50, 6.6, 8.35, 12.20, 7.0, negro, "alza_ala_der"),
        C(7.65, 12.20, 6.6, 8.35, 12.36, 7.0, negro, "alza_anillo"),
    ]
    mira_plegada = [C(7.65, 11.20, 5.8, 8.35, 11.45, 7.0, negro, "alza_plegada")]
    normal = [
        C(7.50, 5.40, 1.60, 8.50, 7.40, 3.60, cargador, "cargador_1"),
        C(7.50, 3.80, 1.20, 8.50, 5.40, 3.30, cargador, "cargador_2"),
        C(7.45, 3.50, 1.10, 8.55, 3.80, 3.35, negro, "cargador_base"),
    ]
    ampliado = normal[:2] + [
        C(7.50, 2.40, 0.80, 8.50, 3.80, 3.00, cargador, "cargador_3"),
        C(7.45, 2.10, 0.70, 8.55, 2.40, 3.05, negro, "cargador_base"),
    ]
    return Arma(
        nombre="g36k", cuerpo=cuerpo, cargador={"normal": normal, "ampliado": ampliado},
        mira_hierro=mira_hierro, mira_plegada=mira_plegada, boca=boca,
        montajes={
            "optica": {"pos": (8, 11.20, 3.4), "lado": "arriba"},
            "boca": {"pos": (8, 10, -13.5), "lado": "arriba"},
            "inferior": {"pos": (8, 8.90, -4.6), "lado": "abajo"},
            "laser": {"pos": (8.75, 9.9, -5.0), "lado": "derecha"},
            "linterna": {"pos": (7.25, 9.9, -5.0), "lado": "izquierda"},
        },
        opciones={"mira": ["", "punto_rojo", "holografica", "acog"], "boca": ["", "silenciador"],
                  "bajo": ["", "empunadura"], "laser": ["", "laser"], "linterna": ["", "linterna"],
                  "cargador": ["", "ampliado"]},
        linea_hierro={"y": 11.95, "z": 7.0},
        config={
            "fp": {"translation": [-5.5, 3.4, 2.0], "scale": 0.99},
            "tp": {"agarre": (8, 6.4, 6.9), "translation": [0, 0, 0.5], "scale": 0.58},
            "gui": {"scale": 0.47},
            "ads": {"hierro": 0.15, "optica": 0.24},
        },
    )


# ---------------------------------------------------------------- HK MP7

def mp7():
    """HK MP7A1: 638 mm con culata extendida, cargador de 40 dentro de la empuñadura. 1 u = 2,0 cm."""
    poli = Material(POLIMERO, desgaste=0.10, sombra=0.08, patrones=[punteado(0.12, -0.04)])
    riel = Material(NEGRO, desgaste=0.12, patrones=[picatinny("arriba")])
    riel_der = Material(NEGRO, desgaste=0.12, patrones=[picatinny("derecha")])
    riel_izq = Material(NEGRO, desgaste=0.12, patrones=[picatinny("derecha", signo=-1)])
    negro = Material(NEGRO, desgaste=0.14, sombra=0.06)
    canon = Material(ACERO, desgaste=0.06)
    empunadura = Material(POLIMERO, desgaste=0.05, sombra=0.10, patrones=[punteado(0.40, -0.08)])
    cuerpo = [
        *cilindro("z", (8, 10), 0.28, -8.4, -5.5, canon, "canon"),
        C(7.35, 9.30, -5.5, 8.65, 10.90, 5.0, poli, "receptor"),
        C(7.58, 10.90, -5.5, 8.42, 11.20, 4.6, riel, "riel"),
        C(8.65, 9.70, -5.3, 8.90, 10.50, -2.4, riel_der, "riel_der"),
        C(7.10, 9.70, -5.3, 7.35, 10.50, -2.4, riel_izq, "riel_izq"),
        C(7.45, 8.40, -2.4, 8.55, 9.30, 5.0, poli, "armazon"),
        C(7.75, 6.98, -0.6, 8.25, 7.14, 1.8, negro, "guardamonte"),
        C(7.75, 7.14, -0.6, 8.25, 8.40, -0.38, negro, "guardamonte_frente"),
        C(7.92, 7.50, 0.2, 8.08, 8.40, 0.4, negro, "disparador"),
        C(7.40, 7.00, 1.8, 8.60, 8.40, 3.4, empunadura, "empunadura_1"),
        C(7.40, 5.60, 2.1, 8.60, 7.00, 3.7, empunadura, "empunadura_2"),
        C(7.40, 4.40, 2.4, 8.60, 5.60, 4.0, empunadura, "empunadura_3"),
        *cilindro("z", (7.45, 9.10), 0.22, 5.0, 9.0, negro, "varilla_izq"),
        *cilindro("z", (8.55, 9.10), 0.22, 5.0, 9.0, negro, "varilla_der"),
        C(7.30, 7.80, 9.0, 8.70, 10.40, 9.6, Material(GOMA), "cantonera"),
        C(7.00, 10.20, 3.6, 7.35, 10.60, 4.6, negro, "manija_carga"),
    ]
    mira_hierro = [
        C(7.70, 11.20, -5.3, 8.30, 11.50, -4.7, negro, "guion_base"),
        C(7.93, 11.50, -5.1, 8.07, 11.85, -4.9, negro, "guion"),
        C(7.65, 11.20, 3.6, 8.35, 11.50, 4.4, negro, "alza_base"),
        C(7.65, 11.50, 4.0, 7.84, 12.10, 4.4, negro, "alza_ala_izq"),
        C(8.16, 11.50, 4.0, 8.35, 12.10, 4.4, negro, "alza_ala_der"),
        C(7.65, 12.10, 4.0, 8.35, 12.26, 4.4, negro, "alza_anillo"),
    ]
    mira_plegada = [C(7.65, 11.20, 3.4, 8.35, 11.45, 4.4, negro, "alza_plegada")]
    normal = [C(7.50, 3.90, 2.5, 8.50, 4.40, 3.9, negro, "base_cargador")]
    return Arma(
        nombre="mp7", cuerpo=cuerpo, cargador={"normal": normal, "ampliado": normal},
        mira_hierro=mira_hierro, mira_plegada=mira_plegada, boca=[],
        montajes={
            "optica": {"pos": (8, 11.20, 0.6), "lado": "arriba"},
            "boca": {"pos": (8, 10, -8.4), "lado": "arriba"},
            "inferior": {"pos": (8, 9.30, -4.2), "lado": "abajo"},
            "laser": {"pos": (8.90, 10.1, -4.0), "lado": "derecha"},
            "linterna": {"pos": (7.10, 10.1, -4.0), "lado": "izquierda"},
        },
        opciones={"mira": ["", "punto_rojo", "holografica"], "boca": ["", "silenciador"],
                  "bajo": ["", "empunadura"], "laser": ["", "laser"], "linterna": ["", "linterna"], "cargador": [""]},
        silenciador="subfusil",
        linea_hierro={"y": 11.85, "z": 4.4},
        config={
            "fp": {"translation": [-5.0, 3.2, -3.0], "scale": 0.94},
            "tp": {"agarre": (8, 6.0, 2.9), "translation": [0, 0, 0.5], "scale": 0.50},
            "gui": {"scale": 0.62},
            "ads": {"hierro": 0.15, "optica": 0.24},
        },
    )


# ---------------------------------------------------------------- PKM

def pkm():
    """PKM: 1173 mm, cañón estriado con manija, caja de cinta de 100 a la derecha y culata hueca. 1 u = 3,2 cm."""
    negro = Material(NEGRO, desgaste=0.16, sombra=0.08, patrones=[
        puntos([(0.15, 0.3), (0.85, 0.3)], solo_normal="derecha"),
        puntos([(0.15, 0.3), (0.85, 0.3)], solo_normal="derecha", signo_normal=-1)])
    canon = Material(ACERO, desgaste=0.08, patrones=[rayas(periodo=2, ancho=1, delta=-0.10)])
    madera = Material((92, 52, 30), desgaste=0.10, sombra=0.12, patrones=[rayas(periodo=3, ancho=1, delta=-0.05, direccion="adelante")])
    caja = Material((84, 90, 58), desgaste=0.10, sombra=0.10, patrones=[rayas(periodo=6, ancho=1, delta=-0.06)])
    cuerpo = [
        *cilindro("z", (8, 10), 0.36, -17.0, -6.0, canon, "canon"),
        *cilindro("z", (8, 8.95), 0.30, -12.0, -6.0, Material(NEGRO), "tubo_gases"),
        C(7.55, 10.30, -16.6, 8.45, 11.10, -15.8, Material(NEGRO), "base_guion"),
        C(7.93, 11.10, -16.4, 8.07, 11.90, -16.0, Material(NEGRO), "guion"),
        C(7.80, 10.40, -9.4, 8.20, 11.60, -8.8, Material(NEGRO), "manija_pie"),
        C(7.70, 11.60, -11.0, 8.30, 11.90, -7.4, madera, "manija"),
        C(7.25, 8.60, -6.0, 8.75, 10.80, 6.0, negro, "receptor"),
        C(7.30, 10.80, -3.0, 8.70, 11.40, 4.6, negro, "tapa_alimentador"),
        C(8.75, 9.00, -2.4, 9.40, 10.20, 1.0, Material(NEGRO), "boca_alimentacion"),
        C(7.75, 6.98, 2.0, 8.25, 7.14, 4.2, Material(NEGRO), "guardamonte"),
        C(7.92, 7.40, 2.8, 8.08, 8.60, 3.0, Material(NEGRO), "disparador"),
        C(7.40, 7.10, 4.0, 8.60, 8.60, 5.4, madera, "empunadura_1"),
        C(7.40, 5.60, 4.4, 8.60, 7.10, 5.8, madera, "empunadura_2"),
        C(7.40, 4.40, 4.8, 8.60, 5.60, 6.2, madera, "empunadura_3"),
        C(7.40, 8.20, 6.0, 8.60, 10.00, 14.0, madera, "culata_sup"),
        C(7.40, 6.40, 9.0, 8.60, 7.40, 14.0, madera, "culata_inf"),
        C(7.40, 7.40, 6.6, 8.60, 8.20, 8.0, madera, "culata_union"),
        C(7.30, 6.20, 14.0, 8.70, 10.20, 14.8, Material(NEGRO), "cantonera"),
        *cilindro("z", (7.55, 8.60), 0.16, -14.0, -7.0, Material(NEGRO), "bipode_izq"),
        *cilindro("z", (8.45, 8.60), 0.16, -14.0, -7.0, Material(NEGRO), "bipode_der"),
    ]
    boca = [*cilindro("z", (8, 10), 0.42, -18.2, -17.0, Material(NEGRO), "apagallamas")]
    mira_hierro = [
        C(7.55, 11.40, -5.6, 8.45, 11.70, -4.2, Material(NEGRO), "alza_base"),
        C(7.55, 11.70, -4.8, 7.82, 12.10, -4.2, Material(NEGRO), "alza_izq"),
        C(8.18, 11.70, -4.8, 8.45, 12.10, -4.2, Material(NEGRO), "alza_der"),
    ]
    normal = [
        C(9.40, 5.60, -2.8, 11.40, 9.00, 1.4, caja, "caja_cinta"),
        C(9.40, 9.00, -2.4, 11.40, 9.30, 1.0, Material(NEGRO), "tapa_caja"),
    ]
    return Arma(
        nombre="pkm", cuerpo=cuerpo, cargador={"normal": normal, "ampliado": normal},
        mira_hierro=mira_hierro, mira_plegada=[], boca=boca,
        montajes={
            "optica": {"pos": (8, 11.40, 1.0), "lado": "arriba"},
            "boca": {"pos": (8, 10, -17.0), "lado": "arriba"},
        },
        opciones={"mira": ["", "punto_rojo", "holografica", "acog"], "boca": [""], "bajo": [""],
                  "laser": [""], "linterna": [""], "cargador": [""]},
        linea_hierro={"y": 11.90, "z": -4.2},
        config={
            "fp": {"translation": [-5.5, 3.0, 1.0], "scale": 0.95},
            "tp": {"agarre": (8, 6.0, 5.1), "translation": [0, 0, 0.5], "scale": 0.64},
            "gui": {"scale": 0.38},
            "ads": {"hierro": 0.15, "optica": 0.24},
        },
    )


# ---------------------------------------------------------------- SVD Dragunov

def svd():
    """SVD Dragunov: 1225 mm, madera con culata de pulgar pasante y mira PSO-1. 1 u = 3,3 cm."""
    madera = Material((134, 72, 40), desgaste=0.12, sombra=0.12, patrones=[rayas(periodo=3, ancho=1, delta=-0.05, direccion="adelante")])
    ventilado = madera.con(patrones=[ranuras(largo=2, separacion=2, margen=1, delta=-0.25)])
    negro = Material(NEGRO, desgaste=0.16, sombra=0.08)
    canon = Material(ACERO, desgaste=0.08)
    cuerpo = [
        *cilindro("z", (8, 10), 0.30, -19.0, -6.0, canon, "canon"),
        C(7.55, 10.30, -18.8, 8.45, 11.30, -18.2, negro, "capuchon_guion"),
        C(7.25, 8.90, -6.0, 8.75, 10.90, 0.0, ventilado, "guardamanos"),
        C(7.35, 8.60, 0.0, 8.65, 10.70, 8.0, negro, "receptor"),
        C(8.65, 10.00, 3.6, 9.10, 10.40, 4.4, negro, "palanca"),
        C(7.75, 6.98, 4.6, 8.25, 7.14, 6.8, negro, "guardamonte"),
        C(7.75, 7.14, 4.6, 8.25, 8.60, 4.82, negro, "guardamonte_frente"),
        C(7.92, 7.50, 5.4, 8.08, 8.60, 5.6, negro, "disparador"),
        C(7.35, 6.60, 8.0, 8.65, 8.40, 9.6, madera, "empunadura"),
        C(7.35, 8.40, 8.0, 8.65, 10.60, 16.0, madera, "culata_sup"),
        C(7.35, 6.00, 11.0, 8.65, 7.00, 16.0, madera, "culata_inf"),
        C(7.35, 6.00, 9.6, 8.65, 6.60, 11.0, madera, "culata_union"),
        C(7.45, 10.60, 10.0, 8.55, 11.00, 14.0, madera, "carrillera"),
        C(7.30, 5.80, 16.0, 8.70, 10.80, 16.6, negro, "cantonera"),
    ]
    boca = [*cilindro("z", (8, 10), 0.38, -20.4, -19.0, negro, "apagallamas")]
    normal = [
        C(7.50, 6.40, 2.0, 8.50, 8.60, 4.2, negro, "cargador_1"),
        C(7.50, 5.20, 1.6, 8.50, 6.40, 3.8, negro, "cargador_2"),
        C(7.45, 4.90, 1.5, 8.55, 5.20, 3.9, negro, "cargador_base"),
    ]
    return Arma(
        nombre="svd", cuerpo=cuerpo, cargador={"normal": normal, "ampliado": normal},
        mira_hierro=[], mira_plegada=[], boca=boca,
        montajes={
            "optica": {"pos": (8, 10.70, 4.0), "lado": "arriba"},
            "boca": {"pos": (8, 10, -19.0), "lado": "arriba"},
        },
        opciones={"mira": ["telescopica"], "boca": ["", "silenciador"], "bajo": [""],
                  "laser": [""], "linterna": [""], "cargador": [""]},
        silenciador="francotirador", mira_defecto="telescopica",
        config={
            "fp": {"translation": [-5.5, 2.6, 0.0], "scale": 0.92},
            "tp": {"agarre": (8, 7.4, 8.8), "translation": [0, 0, 0.5], "scale": 0.66},
            "gui": {"scale": 0.33},
        },
    )


# ---------------------------------------------------------------- Mk 14 EBR

def mk14():
    """Mk 14 EBR: 889 mm, chasis de aluminio con rieles y culata regulable, cargador de 20. 1 u = 2,8 cm."""
    chasis = Material((150, 136, 104), desgaste=0.10, sombra=0.08)
    riel = chasis.con(patrones=[picatinny("arriba")])
    riel_inf = chasis.con(patrones=[picatinny("arriba", signo=-1)])
    riel_der = chasis.con(patrones=[picatinny("derecha")])
    riel_izq = chasis.con(patrones=[picatinny("derecha", signo=-1)])
    negro = Material(NEGRO, desgaste=0.14, sombra=0.06)
    canon = Material(ACERO, desgaste=0.06)
    empunadura = Material(NEGRO, desgaste=0.05, sombra=0.10, patrones=[punteado(0.40, -0.08)])
    cuerpo = [
        *cilindro("z", (8, 10), 0.32, -16.0, -8.0, canon, "canon"),
        C(7.15, 8.80, -8.0, 8.85, 11.00, 2.0, chasis, "chasis"),
        C(7.58, 11.00, -8.0, 8.42, 11.30, 8.0, riel, "riel_sup"),
        C(7.58, 8.50, -8.0, 8.42, 8.80, -2.0, riel_inf, "riel_inf"),
        C(8.85, 9.50, -7.8, 9.10, 10.30, -2.4, riel_der, "riel_der"),
        C(6.90, 9.50, -7.8, 7.15, 10.30, -2.4, riel_izq, "riel_izq"),
        C(7.35, 8.80, 2.0, 8.65, 10.90, 8.0, chasis, "receptor"),
        C(8.65, 9.90, 2.6, 9.20, 10.30, 3.4, negro, "manija_carga"),
        C(7.75, 6.98, 4.8, 8.25, 7.14, 6.6, negro, "guardamonte"),
        C(7.75, 7.14, 4.8, 8.25, 8.80, 5.02, negro, "guardamonte_frente"),
        C(7.92, 7.50, 5.5, 8.08, 8.80, 5.7, negro, "disparador"),
        C(7.50, 7.00, 6.2, 8.50, 8.80, 7.6, empunadura, "empunadura_1"),
        C(7.50, 5.80, 6.6, 8.50, 7.00, 8.0, empunadura, "empunadura_2"),
        C(7.50, 4.60, 7.0, 8.50, 5.80, 8.4, empunadura, "empunadura_3"),
        *cilindro("z", (8, 9.80), 0.55, 8.0, 11.2, negro, "tubo"),
        C(7.35, 8.00, 11.2, 8.65, 10.80, 15.4, negro, "culata"),
        C(7.45, 10.80, 11.8, 8.55, 11.30, 14.6, negro, "carrillera"),
        C(7.30, 7.60, 15.4, 8.70, 11.00, 16.0, Material(GOMA), "cantonera"),
    ]
    boca = [*cilindro("z", (8, 10), 0.42, -17.2, -16.0, negro, "freno")]
    mira_hierro = [
        C(7.70, 11.30, -7.6, 8.30, 11.60, -7.0, negro, "guion_base"),
        C(7.93, 11.60, -7.4, 8.07, 12.00, -7.2, negro, "guion"),
        C(7.65, 11.30, 7.0, 8.35, 11.60, 8.0, negro, "alza_base"),
        C(7.65, 11.60, 7.4, 7.84, 12.25, 7.8, negro, "alza_ala_izq"),
        C(8.16, 11.60, 7.4, 8.35, 12.25, 7.8, negro, "alza_ala_der"),
        C(7.65, 12.25, 7.4, 8.35, 12.42, 7.8, negro, "alza_anillo"),
    ]
    mira_plegada = [
        C(7.70, 11.30, -7.6, 8.30, 11.55, -6.6, negro, "guion_plegado"),
        C(7.65, 11.30, 6.8, 8.35, 11.55, 8.0, negro, "alza_plegada"),
    ]
    normal = [
        C(7.50, 6.40, 2.6, 8.50, 8.80, 4.8, negro, "cargador_1"),
        C(7.50, 4.80, 2.2, 8.50, 6.40, 4.4, negro, "cargador_2"),
        C(7.45, 4.50, 2.1, 8.55, 4.80, 4.5, negro, "cargador_base"),
    ]
    return Arma(
        nombre="mk14", cuerpo=cuerpo, cargador={"normal": normal, "ampliado": normal},
        mira_hierro=mira_hierro, mira_plegada=mira_plegada, boca=boca,
        montajes={
            "optica": {"pos": (8, 11.30, 3.0), "lado": "arriba"},
            "boca": {"pos": (8, 10, -16.0), "lado": "arriba"},
            "inferior": {"pos": (8, 8.50, -5.0), "lado": "abajo"},
            "laser": {"pos": (9.10, 9.9, -5.0), "lado": "derecha"},
            "linterna": {"pos": (6.90, 9.9, -5.0), "lado": "izquierda"},
        },
        opciones={"mira": ["", "punto_rojo", "holografica", "acog"], "boca": ["", "silenciador"],
                  "bajo": ["", "empunadura"], "laser": ["", "laser"], "linterna": ["", "linterna"], "cargador": [""]},
        linea_hierro={"y": 12.0, "z": 7.8},
        config={
            "fp": {"translation": [-5.5, 3.1, 1.0], "scale": 0.96},
            "tp": {"agarre": (8, 6.4, 7.0), "translation": [0, 0, 0.5], "scale": 0.62},
            "gui": {"scale": 0.38},
            "ads": {"hierro": 0.15, "optica": 0.24},
        },
    )


# ---------------------------------------------------------------- AA-12

def aa12():
    """AA-12: 966 mm, escopeta automática con manija y cargador de 8 (tambor de 20). 1 u = 2,7 cm."""
    negro = Material(NEGRO, desgaste=0.14, sombra=0.08, patrones=[punteado(0.10, -0.04)])
    riel = Material(NEGRO, desgaste=0.12, patrones=[picatinny("arriba")])
    riel_inf = Material(NEGRO, desgaste=0.12, patrones=[picatinny("arriba", signo=-1)])
    canon = Material(ACERO, desgaste=0.08)
    empunadura = Material(POLIMERO, desgaste=0.05, sombra=0.10, patrones=[punteado(0.40, -0.08)])
    cuerpo = [
        *cilindro("z", (8, 10), 0.42, -13.0, -6.0, canon, "canon"),
        C(7.15, 8.60, -6.0, 8.85, 10.80, 0.0, negro, "guardamanos"),
        C(7.58, 8.30, -5.6, 8.42, 8.60, -1.0, riel_inf, "riel_inf"),
        C(7.20, 8.40, 0.0, 8.80, 11.00, 12.0, negro, "receptor"),
        C(7.60, 11.00, -1.0, 8.40, 11.90, -0.2, negro, "manija_frente"),
        C(7.60, 11.00, 5.4, 8.40, 11.90, 6.2, negro, "manija_atras"),
        C(7.60, 11.90, -1.0, 8.40, 12.20, 6.2, negro, "manija"),
        C(7.58, 12.20, -1.0, 8.42, 12.50, 4.6, riel, "riel"),
        C(7.75, 6.98, 3.0, 8.25, 7.14, 5.0, negro, "guardamonte"),
        C(7.75, 7.14, 3.0, 8.25, 8.40, 3.22, negro, "guardamonte_frente"),
        C(7.92, 7.50, 3.8, 8.08, 8.40, 4.0, negro, "disparador"),
        C(7.50, 7.00, 4.6, 8.50, 8.40, 6.0, empunadura, "empunadura_1"),
        C(7.50, 5.80, 5.0, 8.50, 7.00, 6.4, empunadura, "empunadura_2"),
        C(7.50, 4.60, 5.4, 8.50, 5.80, 6.8, empunadura, "empunadura_3"),
        C(7.15, 8.00, 12.0, 8.85, 11.20, 12.6, Material(GOMA), "cantonera"),
        C(7.93, 10.80, -12.4, 8.07, 12.90, -12.0, Material(NEGRO), "guion"),
    ]
    normal = [
        C(7.40, 5.60, 0.6, 8.60, 8.40, 2.8, negro, "cargador"),
        C(7.35, 5.30, 0.5, 8.65, 5.60, 2.9, Material(NEGRO), "cargador_base"),
    ]
    ampliado = [*cilindro("x", (5.8, 1.6), 2.4, 7.2, 8.8, negro, "tambor"),
                C(7.40, 7.60, 0.6, 8.60, 8.40, 2.8, negro, "cuello_tambor")]
    mira_hierro = [
        C(7.60, 12.20, 4.6, 8.40, 12.70, 5.2, Material(NEGRO), "alza_base"),
        C(7.60, 12.70, 4.6, 7.86, 13.20, 5.2, Material(NEGRO), "alza_izq"),
        C(8.14, 12.70, 4.6, 8.40, 13.20, 5.2, Material(NEGRO), "alza_der"),
    ]
    return Arma(
        nombre="aa12", cuerpo=cuerpo, cargador={"normal": normal, "ampliado": ampliado},
        mira_hierro=mira_hierro, mira_plegada=[], boca=[],
        montajes={
            "optica": {"pos": (8, 12.50, 1.8), "lado": "arriba"},
            "boca": {"pos": (8, 10, -13.0), "lado": "arriba"},
            "inferior": {"pos": (8, 8.30, -3.4), "lado": "abajo"},
            "laser": {"pos": (8.85, 9.7, -3.4), "lado": "derecha"},
            "linterna": {"pos": (7.15, 9.7, -3.4), "lado": "izquierda"},
        },
        opciones={"mira": ["", "punto_rojo", "holografica"], "boca": [""], "bajo": ["", "empunadura"],
                  "laser": ["", "laser"], "linterna": ["", "linterna"], "cargador": ["", "ampliado"]},
        linea_hierro={"y": 12.90, "z": 5.2},
        config={
            "fp": {"translation": [-5.5, 2.9, 0.0], "scale": 0.95},
            "tp": {"agarre": (8, 6.2, 5.7), "translation": [0, 0, 0.5], "scale": 0.58},
            "gui": {"scale": 0.42},
            "ads": {"hierro": 0.15, "optica": 0.24},
        },
    )


# ---------------------------------------------------------------- Colt M1911

def m1911():
    """Colt M1911A1: 216 mm, pavonado con cachas de madera. 1 u = 1,5 cm."""
    pavon = Material((44, 46, 54), desgaste=0.20, sombra=0.06)
    estrias = pavon.con(patrones=[
        rayas(periodo=2, ancho=1, delta=-0.14, direccion="adelante", solo_normal="derecha"),
        rayas(periodo=2, ancho=1, delta=-0.14, direccion="adelante", solo_normal="derecha", signo_normal=-1)])
    cachas = Material((112, 64, 36), desgaste=0.06, sombra=0.10, patrones=[punteado(0.40, -0.12)])
    negro = Material(NEGRO, desgaste=0.14)
    cuerpo = [
        C(7.30, 9.40, -4.4, 8.70, 10.90, 2.8, pavon, "corredera"),
        C(7.30, 9.40, 2.8, 8.70, 10.90, 4.8, estrias, "corredera_estrias"),
        *cilindro("z", (8, 10.05), 0.45, -4.8, -4.4, pavon, "casquillo"),
        C(7.90, 10.90, -4.0, 8.10, 11.30, -3.5, negro, "guion"),
        C(7.50, 10.90, 4.0, 7.85, 11.35, 4.6, negro, "alza_izq"),
        C(8.15, 10.90, 4.0, 8.50, 11.35, 4.6, negro, "alza_der"),
        C(7.40, 8.40, -2.6, 8.60, 9.40, 4.4, pavon, "armazon"),
        C(7.40, 6.40, -0.4, 8.60, 6.70, 2.2, pavon, "guardamonte"),
        C(7.40, 6.70, -0.4, 8.60, 8.40, -0.05, pavon, "guardamonte_frente"),
        C(7.88, 6.90, 1.0, 8.12, 8.40, 1.4, negro, "disparador"),
        C(7.40, 3.80, 2.2, 8.60, 8.40, 5.0, pavon, "empunadura"),
        C(7.25, 4.20, 2.5, 7.40, 8.00, 4.7, cachas, "cacha_izq"),
        C(8.60, 4.20, 2.5, 8.75, 8.00, 4.7, cachas, "cacha_der"),
        C(7.50, 8.40, 4.4, 8.50, 9.20, 5.6, pavon, "cola_castor"),
        C(7.80, 9.40, 4.8, 8.20, 10.60, 5.3, negro, "martillo"),
    ]
    normal = [C(7.35, 3.40, 2.3, 8.65, 3.80, 4.9, negro, "base_cargador")]
    ampliado = [
        C(7.40, 2.30, 2.4, 8.60, 3.80, 4.8, Material((70, 72, 75), desgaste=0.12), "cargador_extendido"),
        C(7.35, 2.00, 2.3, 8.65, 2.30, 4.9, negro, "base_cargador"),
    ]
    return Arma(
        nombre="m1911", cuerpo=cuerpo, cargador={"normal": normal, "ampliado": ampliado},
        mira_hierro=[], mira_plegada=[], boca=[],
        montajes={
            "boca": {"pos": (8, 10.05, -4.8), "lado": "arriba"},
            "laser": {"pos": (8, 8.40, -1.6), "lado": "abajo"},
            "linterna": {"pos": (8, 8.40, -1.6), "lado": "abajo"},
        },
        opciones={"mira": [""], "boca": ["", "silenciador"], "bajo": [""], "laser": ["", "laser"],
                  "linterna": ["", "linterna"], "cargador": ["", "ampliado"]},
        silenciador="pistola",
        linea_hierro={"y": 11.30, "z": 4.6},
        config={
            "fp": {"translation": [-4.5, 2.8, 1.0], "scale": 0.98},
            "tp": {"agarre": (8, 5.8, 3.6), "translation": [0, 0, 0.5], "scale": 0.44},
            "gui": {"scale": 0.85},
            "ads": {"hierro": 0.18},
        },
    )


# ---------------------------------------------------------------- Revólver S&W 329PD

def revolver():
    """S&W 329PD .44 Magnum: 240 mm, armazón de escandio negro, tambor de 6 y cachas de madera. 1 u = 1,6 cm."""
    negro = Material((38, 38, 42), desgaste=0.18, sombra=0.06)
    tambor = negro.con(patrones=[rayas(periodo=3, ancho=1, delta=-0.18, direccion="adelante")])
    madera = Material((120, 70, 40), desgaste=0.06, sombra=0.10, patrones=[punteado(0.30, -0.10)])
    cuerpo = [
        C(7.55, 9.60, -6.0, 8.45, 10.50, -1.0, negro, "canon"),
        C(7.60, 8.90, -6.0, 8.40, 9.60, -1.6, negro, "funda_extractor"),
        C(7.85, 10.50, -5.8, 8.15, 11.10, -5.0, Material((220, 120, 40)), "guion"),
        *cilindro("z", (8, 9.45), 1.10, -1.0, 1.8, tambor, "tambor"),
        C(7.55, 10.55, -1.0, 8.45, 10.80, 1.8, negro, "puente"),
        C(7.45, 8.30, 1.8, 8.55, 10.80, 3.2, negro, "armazon"),
        C(7.50, 8.10, -1.2, 8.50, 8.35, 1.8, negro, "armazon_bajo"),
        C(7.50, 10.80, 2.0, 7.85, 11.20, 2.6, negro, "alza_izq"),
        C(8.15, 10.80, 2.0, 8.50, 11.20, 2.6, negro, "alza_der"),
        C(7.85, 10.20, 3.2, 8.15, 11.00, 3.8, negro, "martillo"),
        C(7.60, 6.50, 0.2, 8.40, 6.75, 2.2, negro, "guardamonte"),
        C(7.60, 6.75, 0.2, 8.40, 8.10, 0.42, negro, "guardamonte_frente"),
        C(7.88, 6.90, 1.0, 8.12, 8.10, 1.3, negro, "disparador"),
        C(7.25, 6.40, 2.4, 8.75, 8.30, 4.4, madera, "cacha_1"),
        C(7.25, 4.40, 2.9, 8.75, 6.40, 5.0, madera, "cacha_2"),
    ]
    normal = [C(7.85, 9.00, -1.6, 8.15, 9.30, -1.0, Material(ACERO), "varilla_extractor")]
    return Arma(
        nombre="revolver", cuerpo=cuerpo, cargador={"normal": normal, "ampliado": normal},
        mira_hierro=[], mira_plegada=[], boca=[],
        montajes={"boca": {"pos": (8, 10.05, -6.0), "lado": "arriba"}},
        opciones={"mira": [""], "boca": [""], "bajo": [""], "laser": [""], "linterna": [""], "cargador": [""]},
        silenciador="pistola",
        linea_hierro={"y": 11.10, "z": 2.6},
        config={
            "fp": {"translation": [-4.5, 2.8, 1.0], "scale": 0.98},
            "tp": {"agarre": (8, 6.2, 3.4), "translation": [0, 0, 0.5], "scale": 0.44},
            "gui": {"scale": 0.8},
            "ads": {"hierro": 0.18},
        },
    )


ARMAS = {"m4a1": m4a1, "m9": m9, "mp5": mp5, "m1014": m1014, "barrett": barrett,
         "ak47": ak47, "p90": p90, "vector": vector, "m249": m249, "m24": m24, "r870": r870, "deagle": deagle,
         "scarh": scarh, "g36k": g36k, "mp7": mp7, "pkm": pkm, "svd": svd, "mk14": mk14, "aa12": aa12,
         "m1911": m1911, "revolver": revolver}
