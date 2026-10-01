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
        config={
            "fp": {"translation": [-5.5, 3.4, 2.0], "scale": 0.86},
            "tp": {"agarre": (8, 6.5, 8.6), "translation": [0, 0, 0.5], "scale": 0.5},
            "gui": {"scale": 0.45},
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
        C(7.85, 11.20, -4.6, 8.15, 11.65, -4.0, negro, "guion"),
        C(7.55, 11.20, 6.9, 7.80, 11.70, 7.6, negro, "alza_izq"),
        C(8.20, 11.20, 6.9, 8.45, 11.70, 7.6, negro, "alza_der"),
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
        config={
            "fp": {"translation": [-4.5, 2.8, 1.0], "scale": 0.85},
            "tp": {"agarre": (8, 5.5, 5.4), "translation": [0, 0, 0.5], "scale": 0.36},
            "gui": {"scale": 0.85},
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
        C(7.40, 11.40, 6.0, 8.60, 12.40, 7.0, negro, "alza_tambor"),
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
    return Arma(
        nombre="mp5", cuerpo=cuerpo, cargador={"normal": normal, "ampliado": ampliado},
        mira_hierro=[], mira_plegada=[], boca=[],
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
        config={
            "fp": {"translation": [-5.0, 3.0, 0.0], "scale": 0.82},
            "tp": {"agarre": (8, 6.0, 7.0), "translation": [0, 0, 0.5], "scale": 0.45},
            "gui": {"scale": 0.50},
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
        *cilindro("z", (8, 9.35), 0.38, -12.6, -3.0, negro, "tubo_cargador"),
        C(7.62, 9.10, -13.2, 8.38, 10.70, -12.6, negro, "abrazadera"),
        C(7.80, 10.66, -15.4, 8.20, 11.10, -14.9, negro, "guion"),
        # guardamanos de polímero
        C(7.15, 8.70, -8.0, 8.85, 10.70, -1.0, polimero, "guardamanos_a"),
        C(7.30, 8.55, -7.985, 8.70, 10.85, -1.015, polimero, "guardamanos_b"),
        # receptor con riel y alza de anillo
        C(7.30, 8.40, -1.0, 8.70, 11.00, 7.0, receptor, "receptor"),
        C(7.58, 11.00, -0.6, 8.42, 11.30, 6.4, riel, "riel_receptor"),
        C(7.50, 11.30, 5.4, 7.70, 12.20, 6.2, negro, "alza_izq"),
        C(8.30, 11.30, 5.4, 8.50, 12.20, 6.2, negro, "alza_der"),
        C(7.70, 12.00, 5.4, 8.30, 12.20, 6.2, negro, "alza_sup"),
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
    return Arma(
        nombre="m1014", cuerpo=cuerpo, cargador={"normal": normal, "ampliado": ampliado},
        mira_hierro=[], mira_plegada=[], boca=[],
        montajes={
            "optica": {"pos": (8, 11.30, 2.4), "lado": "arriba"},
            "laser": {"pos": (8.85, 9.70, -5.0), "lado": "derecha"},
            "linterna": {"pos": (7.15, 9.70, -5.0), "lado": "izquierda"},
        },
        opciones={"mira": ["", "punto_rojo", "holografica"], "boca": [""], "bajo": [""],
                  "laser": ["", "laser"], "linterna": ["", "linterna"], "cargador": ["", "ampliado"]},
        config={
            "fp": {"translation": [-5.5, 3.0, 2.0], "scale": 0.80},
            "tp": {"agarre": (8, 6.0, 6.8), "translation": [0, 0, 0.5], "scale": 0.52},
            "gui": {"scale": 0.44},
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
            "fp": {"translation": [-5.5, 2.6, 0.0], "scale": 0.80},
            "tp": {"agarre": (8, 5.5, 10.8), "translation": [0, 0, 0.5], "scale": 0.62},
            "gui": {"scale": 0.33},
        },
    )


ARMAS = {"m4a1": m4a1, "m9": m9, "mp5": mp5, "m1014": m1014, "barrett": barrett}
