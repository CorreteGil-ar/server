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

## HUD (todos los modos)

- **Paneles arriba al centro**: las barras estilo Souls del RPG (vida, aguante y éter, con el golpe
  recibido en blanco unos instantes) y el tablero de los vehículos (blindaje, velocidad, recarga del
  cañón, munición y averías) se dibujan como título de una barra de jefe **blanca**, que el paquete
  deja transparente (`minecraft:gui/sprites/boss_bar/white_*`). Por eso ningún jefe puede usar el
  color blanco (el Vigía pasó a amarillo). Usan letra chica de 3×5 propia.
  - No se puede anclar arriba a la izquierda como en Dark Souls: sin mods, el HUD solo se puede
    ubicar respecto del centro de la pantalla. Lo dejé centrado arriba, donde no tapa la mira.
  - **Para probar en el juego**: que las barras no se pisen con la barra de un jefe (van debajo) y
    que se lea bien en escala de interfaz 2 y 3.
  - Los corazones y la barra de experiencia (aguante) de vanilla siguen visibles: esconderlos
    cambiaría también los otros modos.
- La barra de acción de los vehículos ahora queda libre para los avisos ("Cañón dañado", etc.).

## Lobby

- **Portales**: hangar en arco (Guerra), contenedor militar (Shooter) y arco de piedra con dos fuegos
  (RPG) alrededor de cada placa. Se rehacen en cada arranque, así que si los tocás a mano en el juego
  vuelven a su forma.
- **Estadísticas**: junto a cada portal hay un cartel con tus números de ese modo (bajas, capturas,
  B/M, nivel, Señores vencidos, almas). Cada jugador ve solo los suyos; se actualizan al volver al
  lobby. **[revisar]** si además querés un ranking general.

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
  se editan. Hay 12 armas con modelo: a las 5 de antes se sumaron AK-47, P90, Vector, M249, M24,
  Remington 870 y Desert Eagle. Faltan SCAR-H, G36K, MP7, PKM, SVD, Mk 14, AA-12, M1911 y el revólver.
- **Arsenal nuevo** (valores de la tabla del diseño ÷ 5, como el resto): AK-47 6,8 (patea un 30 % más
  que la M4), P90 3,8 con 50 balas sin cargador ampliado, Vector 3,6 a 1140 disp/min (patea menos),
  M249 5,6 con caja de 100 (5,5 s de recarga; sin silenciador, láser ni linterna), M24 20 de cerrojo
  (5 balas, un tiro al torso con 100 de vida), Remington 870 8 × 3,6 de bombeo y Desert Eagle 11
  (patea el doble que la M9). La recarga de las escopetas es de golpe, no cartucho por cartucho.
  **[revisar]** si las querés bloqueadas por nivel (hoy está todo libre, como los accesorios).
- **Tamaño del paquete**: con 12 armas, 8 camuflajes cada una y los vehículos, el zip pesa ~18 MB
  (antes ~10). El cliente lo baja una vez y lo guarda; si molesta, se pueden sacar camuflajes del
  apuntado (son la mitad de los modelos).
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

- **Modelos de las armas del RPG** (`herramientas/rpg.py`): 15 armas con modelo 3D propio. Se modelan
  verticales y usan las transformaciones de mano de una espada de vanilla con 45° más de giro, así se
  agarran igual. Arco largo, ballesta y lanzas siguen con el modelo de vanilla porque sus animaciones
  (tensar, cargar, embestir) dependen de él. Las armas que ya tenías en el inventario toman el modelo
  nuevo al entrar al RPG. **Para probar en el juego**: que la mano agarre el puño (en las armas de
  asta larga, como la alabarda y la guadaña, el agarre queda en la mitad del asta).
- **Estructuras de las zonas** (`ConstructorZonas`): se construyen una sola vez, la primera vez que
  arranca el server con esta versión (una zona por segundo; en el log queda cuánto tardó cada una).
  Quedan registradas en `plugins/TresModos/rpg-estructuras.yml`; si se borra ese archivo, se vuelven a
  construir encima de lo que haya.
  - Aldea Hueca: empalizada con portón, brecha y salida trasera; plaza con pozo y horcas; iglesia con
    torre y cripta; cementerio; 14 casas quemadas; campos de trigo podrido.
  - Bosque Podrido: 22 árboles muertos gigantes, hongos gigantes que brillan, 3 pantanos (el agua
    envenena) y la choza de la herbolaria.
  - Ciudadela Desmoronada: muralla con 4 torres, portón con 2 torres, brecha al costado, torre del
    homenaje de 60 bloques (se ve de lejos) y catedral en ruinas.
  - Costa Hundida: arena negra, dársena con 3 casas hundidas, muelle y barco a medio hundir, barco
    encallado en la playa y 9 casas de pescadores.
  - **Atajos**: los portones de la Aldea y de la Ciudadela se abren solo desde adentro (palanca o
    clic a la reja). Se entra por la brecha. Una vez abiertos quedan abiertos para todos, también en
    el Ciclo+. **[revisar]** si querés que se cierren al empezar un ciclo nuevo.
  - **Paredes ilusorias**: tras el altar de la iglesia, la corteza del árbol hueco, la entrada de la
    torre del homenaje y la proa del barco encallado. Desaparecen con un golpe y esconden un cofre con
    un arma mejorada, materiales y un pergamino. Las notas de cada zona dan la pista.
  - **Notas**: 5 libros en atriles (Santuario, iglesia, choza, catedral y casa del capitán del puerto).
  - Lo revisé volcando las estructuras sobre un terreno de prueba fuera del juego; **para probar en
    el juego** cómo quedan sobre el relieve real (por ejemplo, una muralla que cruza un barranco).

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
- **Infantería**: cada clase elige su principal en `/clase` (fila de abajo): Fusilero M4A1, AK-47
  o M249; Antitanque MP5 o P90 (la G36K del diseño todavía no tiene modelo); Ingeniero MP5, Vector,
  P90, Remington 870 o M1014; Tirador Barrett o M24. La pistola (M9 o Desert Eagle) también se
  elige ahí. **[revisar]** si preferís que cada equipo tenga su arsenal (Rojo con AK-47, etc.).
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
