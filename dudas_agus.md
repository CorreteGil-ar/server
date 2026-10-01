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
- **Mundo RPG (R3)**: sigue siendo el terreno vanilla de `tm_rpg`. El plugin le suma lo siguiente:
  - **Zonas** ubicadas respecto del Santuario (el spawn), con su hora y bioma pintado:
    - Aldea Hueca, al oeste, nivel 1–10;
    - Bosque Podrido, al norte, 10–25;
    - Ciudadela, al este, 25–40;
    - Costa Hundida, al sudeste, 40–55;
    - Templo, debajo de la Costa, 55+.
  - **Biomas**: con setBiome, al cargar cada chunk. Así cambian el color de la niebla, el cielo, el
    pasto y las partículas. Para conseguir esos colores sin datapack usé biomas del Nether y uno nuevo:
    - Jardín Pálido para la Aldea;
    - Valle de Almas para el Bosque (niebla verdosa);
    - Deltas de Basalto para la Ciudadela (ceniza);
    - Pantano para la Costa (llueve);
    - Bosque Distorsionado para el Templo.

    **[revisar]** Lo raro es que en esas zonas suena la música del Nether. Si molesta, la
    alternativa es un datapack con biomas propios (`tresmodos:aldea_hueca`, etc.), que pide
    reiniciar el server una vez.
  - **Hogueras**: el plugin construye 3 por zona (entrada, medio y antesala del jefe): una ruina
    con la espada clavada y el nombre encima. Solo sirven de hoguera las registradas; las fogatas
    de las aldeas vanilla no. Desde cualquier hoguera se puede **viajar** a las que ya encendiste.
  - **Encuentros**: 45 puntos fijos por zona, con 13 tipos de enemigo hechos con mobs vanilla
    renombrados y equipados (Hueco, Perro de la plaga, Acechador, Caballero caído, Pálido con el
    Creaking, Vástago con el Evocador, etc.). El 10 % son Campeones. Los spawns naturales de
    monstruos quedaron apagados.
  - **Noche Roja**: cada 3 días del reloj del mundo (60 minutos reales), durante 10 minutos.
- **Modo aventura** en el RPG, como pide el diseño. Saqué las antorchas del kit porque ya no se
  pueden poner.
- **Siempre de noche en el server**: el mundo RPG queda fijo a medianoche, así las arañas atacan y
  los no-muertos no se queman. Cada jugador ve la hora de su zona (atardecer o noche).
- **Borde del mundo**: bajó de 8000 a 1700 bloques. Si alguien había quedado afuera, al entrar
  lo mando a su hoguera.
- **Jefes (R4)**: cada Señor tiene una arena construida a 24 bloques de la hoguera "antesala", con
  muro de niebla en la entrada. El Durmiente tiene la suya en una caverna bajo la Costa; se llega por
  la **Puerta del Abismo**, un arco de obsidiana con una piedra imán que necesita las 4 almas.
  **[revisar]** El diseño pide una escalera ciclópea; por ahora la puerta te teletransporta.
  Los cuerpos de los jefes, en vanilla:
  - Glotón: Devastador grande.
  - Tejedora: araña ×2,8.
  - Caballero: Wither esqueleto.
  - Vigía: Enderman ×1,7.
  - Durmiente: Warden ×1,6.

  Todos los ataques tienen un aviso previo y se esquivan rodando o se paran con parry. Los modelos
  propios con item displays quedan para más adelante.
- **Jefes repetibles**: en un mundo compartido no tiene sentido que un jefe muera para todos.
  Cualquiera puede volver a pelearlo; el alma y las brasas se dan una sola vez por jugador.
- **Bug que arreglé de paso**: las habilidades, los estados y los cuchillos pegaban con
  `damage(x, jugador)`, que Paper trata como un golpe cuerpo a cuerpo. Por eso les volvía a aplicar
  el multiplicador del arma y gastaba aguante. Ahora usan `CombateRpg.herir()`.
- **Magia**: los catalizadores son el **bastón de hueso** (hechicería) y el **talismán** (milagros).
  La maza del Clérigo no, porque el clic derecho con escudo en la otra mano ya es bloquear o hacer
  parry. Con el catalizador en la mano:
  - Clic derecho: lanza el hechizo elegido.
  - Shift + clic derecho: cambia de hechizo.

  Hay 9 hechizos; el Hechicero y el Clérigo arrancan con 2 cada uno. El resto se aprende con
  pergaminos del mercader. La rueda del mouse no se puede interceptar sin cambiar de slot.
- **Santuario**: tiene 3 NPC (aldeanos sin IA) y el altar:
  - Herrero: mejora de +1 a +6 con fragmentos y de +7 a +10 con escamas.
  - Mercader: consumibles, pergaminos y catalizadores.
  - Guardiana: niveles y Ciclo+.
  - Altar: canjea las almas de jefe.

  El **Ciclo+** conserva todo (nivel, equipo y árbol); suben los enemigos y las almas.

## Guerra (reemplaza a GTA)

- **GTA eliminado**: el diseño dice que Guerra reemplaza al GTA, así que borré `ModoGta`, la
  ciudad, el celular y los comandos `/tm dinero` y `/tm buscado`. La plata de los jugadores queda
  guardada en sus datos por si algún día vuelve. El mundo viejo `tm_gta` queda en disco sin usar.
  `/modo gta` ahora lleva a Guerra.
- **Pueblo**: el diseño decía reaprovechar el generador de la ciudad del GTA, pero eran rascacielos
  modernos. Hice un pueblo europeo nuevo (casas de 2 y 3 pisos, iglesia, fábrica y estación).
- **Mapa como función pura** (`ValleDeHierro.bloque(x, y, z)`): cuando termina la partida, todo lo
  que rompieron las explosiones vuelve a su bloque original sin tener que guardar copias.
  También se puede volcar a imágenes fuera del juego (así lo revisé).
- **Explosiones que rompen**: el HE, los cohetes, el C4, las bombas, las minas y las granadas
  antitanque rompen bloques en todo el mapa menos en las bases. Así se pueden volar los puentes
  y abrir paredes del pueblo.
- **Infantería**: usa las 5 armas que hoy tienen modelo. La carabina G36K del Antitanque es una MP5
  y el Fusilero lleva M4A1. **[revisar]** si querés que el arsenal nuevo (AK-47, SCAR-H, SVD, etc.)
  llegue primero a Guerra.
- **Vehículos**:
  - Controles:
    - **Clic derecho mantenido**: el arma automática (ametralladora o cañón).
    - **Clic izquierdo**: el arma pesada (cañón del tanque, TOW, misiles, cohetes y bombas).
    - **F**: cambia la munición AP/HE o el arma secundaria del avión.
    - **Q**: extintor.
    - **1-4**: cambia de asiento.
    - **Shift 1 s**: bajar. En el avión, Shift eyecta al toque.
  - El avión es uno solo, con cañón, 2 misiles aire-aire y 2 bombas. El diseño pedía elegir caza o
    ataque en el hangar; lo junté para no sumar un menú.
  - **No se puede subir a vehículos enemigos.**
- **Para probar en el juego** (sin cliente no pude verificarlo):
  - Que el jugador quede bien sentado sobre los asientos (son ItemDisplay vacíos que se mueven cada
    tick) y que la cámara no tiemble. Si tiembla, la alternativa es subir el
    `setTeleportDuration` de 2 a 3.
  - La orientación de los modelos de los vehículos dentro del ItemDisplay: si aparecen mirando para
    atrás, hay que girar 180° la exportación en `herramientas/vehiculos.py`.
  - El manejo del helicóptero y del avión con teclado. Los números (aceleración, giro, pérdida a
    15 b/s, despegue a 18 b/s) son una primera pasada.
  - El clic izquierdo estando montado: el plugin lo toma del balanceo del brazo
    (`LEFT_CLICK_AIR`).
