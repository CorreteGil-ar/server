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

## RPG

- **Clase al entrar**: la primera vez (o si tu personaje es de antes de las clases) se abre el menú
  de las 6 clases. Se elige una sola vez. Si ya tenías atributos subidos, te quedás con el mayor
  entre lo tuyo y la base de la clase. Al elegirla, el inventario se reemplaza por el kit de la clase,
  así que se pierden la espada y el arco viejos. **[revisar]** si querés que se pueda cambiar de clase
  (por ejemplo, en el altar del Santuario y pagando almas).
- **Controles**:
  - **F**: voltereta.
  - **Q con un arma del RPG en la mano**: habilidad de clase (el arma no se tira).
  - **Shift+Q**: definitiva.
  - **Clic derecho**: con escudo o arma con guardia, parry; con un consumible, usarlo.
  - **Shift + clic izquierdo**: ataque pesado.
- **Éter**: se muestra en una barra de jefe propia, que también enseña la acumulación de estados
  (sangrado, veneno, frío, locura). El máximo es 50 + 8 por Mente. Se regenera solo,
  medio punto por segundo (más con Bendiciones), y suma 3 por cada enemigo que matás. Lo puse así
  porque con la Q costando 12–28 sin regeneración solo se podía usar 2 o 3 veces por hoguera.
- **Frascos**: hay un total (empieza en 3 y llega hasta 10) que se reparte entre Estus y Éter en la
  hoguera. Siempre queda al menos uno de Estus.
- **Carga**: la capacidad es 40 + 3 por Aguante. Pesan las armas de la barra, el escudo y la
  armadura. Las cuatro cargas:
  - Ligera (menos del 30 %): rodás 1,25× y tenés 10 ticks de invulnerabilidad.
  - Media: 1× y 8 ticks.
  - Pesada: 0,7× y 6 ticks.
  - Sobrecarga: no rodás y caminás un 35 % más lento.
- **Brasas del árbol**: una cada 3 niveles y 2 por cada alma de jefe. Llevar una rama a 5 cuesta 13,
  así que maxear las tres pide 39 (nivel ~90 sin jefes). La idea es que no se pueda tener todo.
- **Q y definitivas**: hay 54 habilidades programadas (6 clases × [1 Q base + 4 Q de rama + 4
  definitivas]). Los números (daño, duración, éter) son una primera pasada y hay que balancearlos
  jugando. Esperas: la Q tarda 6 s y la definitiva 90 s.
- **Jefe del Abismo**: lo saqué a su propia clase (`JefeAbismo`). Sigue invocándose con la campana
  hasta que estén los 5 jefes con arena (etapa R4). Ahora da el *Alma del Abismo* (+2 brasas) y a
  veces suelta la Espada del Abismo.
- **Para probar en el juego**:
  - Que la Q no tire el arma al soltarla desde el inventario abierto: el evento es el mismo, así
    que también dispara la habilidad.
  - Que el `setAware(false)` del aturdimiento no deje mobs congelados si el server se reinicia
    en medio.
