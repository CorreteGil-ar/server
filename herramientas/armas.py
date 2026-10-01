"""Definición de las armas del resource pack (marco canónico: cañón hacia -Z, arriba +Y, derecha +X)."""
from modelado import Caja, Material, rayas, ranuras, punteado, rectangulo

# Paleta estilo Modern Warfare: metal negro anodizado, acero fosfatado y polímero color tierra.
NEGRO_ANODIZADO = (40, 41, 44)
GRAFITO = (52, 54, 57)
ACERO = (62, 63, 66)
FOSFATO = (46, 46, 47)
FDE = (158, 132, 94)       # flat dark earth
FDE_OSCURO = (128, 105, 74)
GOMA = (28, 28, 29)


def m4a1():
    metal = Material(NEGRO_ANODIZADO, ruido=0.016, desgaste=0.13, sombra=0.08)
    riel = Material(NEGRO_ANODIZADO, ruido=0.016, desgaste=0.10, sombra=0.05,
                    patrones=[rayas(periodo=2, ancho=1, delta=-0.10)])
    guardamanos = Material(GRAFITO, ruido=0.016, desgaste=0.12, sombra=0.10,
                           patrones=[ranuras(largo=5, separacion=3, delta=-0.20)])
    receptor = Material(NEGRO_ANODIZADO, ruido=0.016, desgaste=0.13, sombra=0.10, patrones=[
        # Ventana de expulsión (lado derecho) y su tapa.
        rectangulo((0.55, 0.45), (0.30, 0.35), -0.16),
    ])
    receptor_inf = Material(NEGRO_ANODIZADO, ruido=0.016, desgaste=0.12, sombra=0.10, patrones=[
        # Selector de tiro (lado derecho, sobre la empuñadura).
        rectangulo((0.25, 0.55), (0.10, 0.25), 0.10),
    ])
    canon = Material(ACERO, ruido=0.014, desgaste=0.08, sombra=0.08)
    bocacha = Material(FOSFATO, ruido=0.016, desgaste=0.06, sombra=0.06,
                       patrones=[ranuras(largo=2, separacion=1, margen=1, delta=-0.25, solo_lados=False)])
    mira = Material((30, 30, 32), ruido=0.012, desgaste=0.10, sombra=0.04)
    polimero = Material(FDE, ruido=0.016, desgaste=0.08, sombra=0.12)
    agarre = Material(FDE, ruido=0.016, desgaste=0.05, sombra=0.12, patrones=[punteado(0.35, -0.09)])
    cargador = Material(FDE, ruido=0.016, desgaste=0.07, sombra=0.10, patrones=[
        rayas(periodo=3, ancho=1, delta=-0.07, direccion="arriba"),
    ])
    base_cargador = Material(FDE_OSCURO, ruido=0.016, desgaste=0.05, sombra=0.08)
    goma = Material(GOMA, ruido=0.02, desgaste=0.04, sombra=0.04, patrones=[rayas(periodo=2, delta=-0.05, direccion="arriba")])

    return [
        # --- frente
        Caja((7.6, 9.6, -14.0), (8.4, 10.4, -12.4), bocacha, "bocacha"),
        Caja((7.7, 9.7, -12.4), (8.3, 10.3, -7.0), canon, "canon"),
        Caja((7.6, 11.4, -6.8), (8.4, 12.5, -6.0), mira, "mira_delantera"),
        # --- guardamanos con riel superior
        Caja((7.0, 8.6, -7.2), (9.0, 10.9, 1.6), guardamanos, "guardamanos"),
        Caja((7.4, 10.9, -7.2), (8.6, 11.4, 1.6), riel, "riel_guardamanos"),
        # --- receptor superior
        Caja((7.05, 9.0, 1.6), (8.95, 11.2, 9.0), receptor, "receptor_superior"),
        Caja((7.4, 11.2, 1.6), (8.6, 11.6, 8.6), riel, "riel_receptor"),
        Caja((7.5, 11.6, 7.2), (8.5, 12.6, 8.2), mira, "mira_trasera"),
        Caja((7.3, 10.6, 9.0), (8.7, 11.1, 9.8), metal, "manija_de_carga"),
        Caja((8.95, 9.9, 6.4), (9.4, 10.6, 7.3), metal, "asistente"),
        # --- receptor inferior, brocal y gatillo
        Caja((7.1, 7.6, 2.0), (8.9, 9.0, 9.4), receptor_inf, "receptor_inferior"),
        Caja((7.15, 6.6, 2.4), (8.85, 7.6, 5.0), receptor_inf, "brocal"),
        Caja((7.6, 6.5, 5.3), (8.4, 6.8, 7.9), metal, "guardamonte"),
        Caja((7.6, 6.8, 5.3), (8.4, 7.6, 5.6), metal, "guardamonte_frente"),
        Caja((7.85, 6.8, 6.3), (8.15, 7.6, 6.6), metal, "gatillo"),
        # --- cargador curvo (tres tramos que avanzan hacia adelante al bajar)
        Caja((7.3, 4.2, 2.6), (8.7, 6.6, 4.8), cargador, "cargador_1"),
        Caja((7.3, 2.0, 2.2), (8.7, 4.2, 4.5), cargador, "cargador_2"),
        Caja((7.2, 1.4, 1.9), (8.8, 2.0, 4.4), base_cargador, "cargador_base"),
        # --- empuñadura inclinada hacia atrás
        Caja((7.3, 5.4, 7.6), (8.7, 7.6, 9.4), agarre, "empunadura_1"),
        Caja((7.3, 3.6, 8.1), (8.7, 5.4, 9.9), agarre, "empunadura_2"),
        Caja((7.3, 2.6, 8.5), (8.7, 3.6, 10.3), agarre, "empunadura_3"),
        # --- tubo y culata
        Caja((7.5, 8.6, 9.4), (8.5, 9.6, 14.6), metal, "tubo"),
        Caja((7.2, 7.2, 12.0), (8.8, 10.0, 17.4), polimero, "culata"),
        Caja((7.2, 5.2, 15.4), (8.8, 7.2, 17.4), polimero, "culata_talon"),
        Caja((7.1, 5.0, 17.4), (8.9, 10.2, 18.0), goma, "cantonera"),
    ]


ARMAS = {
    "m4a1": m4a1,
}
