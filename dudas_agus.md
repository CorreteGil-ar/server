# Dudas y decisiones de la sesión autónoma

Preguntas que dejé anotadas para no frenar el trabajo, y decisiones que tomé por mi cuenta.
Cada una dice qué hice mientras tanto. Las marcadas como **[revisar]** también tienen un
`// TODO: Agus - Revisar esto` en el código.

## Alcance

- **"Completar el proyecto"**: seguí el orden de construcción de `docs/DISENO.md` (sección 6):
  primero Shooter, después RPG y por último Guerra. El documento pide mucho más de lo que entra
  en una sola noche (24 armas con modelo, 7 vehículos, 5 jefes animados, un mundo de 1500 × 1500).
  Prioricé las **mecánicas jugables** y el **mapa**, con modelos donde más se notan; lo que quedó
  afuera está listado al final de este archivo, en "Pendiente".

## Shooter

- **Regla de los 8 bloques al reaparecer** (la había propuesto yo y estaba pendiente de tu OK):
  la dejé activa y se puede apagar en `config.yml` (`shooter.reaparicion-segura`).
- **Mundo nuevo**: el mapa Pueblo Atómico se genera en un mundo nuevo (`tm_shooter`). El mundo viejo
  de la arena de contenedores (`tm_cod`) queda en disco sin usar; se puede borrar a mano.
- **Para probar en el juego** (programado según la API, sin verificar con el cliente):
  - *Misil Predator*: la cámara se engancha al misil con el modo espectador sobre un `ItemDisplay`
    y se maneja con WASD. Si la cámara no gira con el misil, la alternativa es dejar al jugador en
    su cuerpo y que el misil siga hacia donde mira.
  - *Cuerpo a tierra*: usa la técnica del bloque fantasma (una barrera que solo ve ese jugador) más
    la pose forzada en el server (`setPose(SWIMMING, true)`), para que la hitbox también baje.
  - *Destello de la bomba*: un glifo blanco de 160 de alto como título; si no tapa toda la
    pantalla en algún tamaño de interfaz, se agranda en `herramientas/hud.py`.
  - *Flecha del minimapa*: la dirección del cursor sale de la fórmula de vanilla; si apunta al revés,
    hay que sumar 8 en `Minimapa.cursor`.
- **Clases**: dejé 5 clases por defecto (Asalto, Subfusil, Escopetero, Francotirador y Libre) y todas
  se editan. Por ahora las armas disponibles son las 5 que tienen modelo; el resto del arsenal del
  diseño (AK-47, SCAR-H, M249, etc.) necesita modelos nuevos.
- **Daño**: pasé los daños a la escala del diseño (100 de vida = 20 corazones): M4A1 26, MP5 22,
  M9 30, M1014 14 por perdigón y Barrett 120. Cabeza ×1,5 (×2 la Barrett) y piernas ×0,8.
- **Cuchillo**: ahora va siempre con F (cuchillazo rápido), así que saqué el ítem de cuchillo del
  slot 3. El minimapa ocupa la mano izquierda.
