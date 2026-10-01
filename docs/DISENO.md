# TresModos: diseño de los modos

Documento de diseño del server. Describe qué tiene cada modo, cómo se juega y cómo se ve. Es la especificación de la que sale el código; los números son **valores iniciales** para ajustar jugando.

| | Shooter | Guerra | RPG / Souls |
|---|---|---|---|
| Referencia | COD Modern Warfare / Black Ops | War Thunder / World of Tanks / Battlefield | Elden Ring / Dark Souls |
| Formato | Todos contra todos | Captura la bandera, 2 equipos | Cooperativo, mundo abierto |
| Jugadores | 4 (diseñado para 6) | 2 vs 2 (diseñado para 3 vs 3) | 4 (diseñado para 6) |
| Mapa | Pueblo Atómico (inspirado en Nuketown) | Valle de Hierro: pueblo + campo, 512×512 | Las Tierras Cenicientas: 5 zonas |
| Objetivo | Primero a N bajas o más bajas a los 10 min | 3 capturas o más capturas a los 15 min | Derrotar a los 4 Señores y al Durmiente |
| Duración | 10 min | 15 min | Persistente (con Ciclo+) |

El modo GTA actual se reemplaza por Guerra. Su generador de ciudad se reaprovecha para el pueblo del mapa de guerra.

---

## 0. Base común

### Leyenda de factibilidad

Minecraft sin mods impone límites. Cada mecánica lleva una marca:

- ✅ **Directo**: se hace con el plugin y el resource pack sin trucos raros.
- ⚠️ **Con trabajo o con trucos**: se puede, pero cuesta más o tiene alguna limitación visible.
- ❌ **No se puede sin mods**: se descarta o se reemplaza por una aproximación.

### Cliente y resource pack

- Minecraft Java sin mods. El server exige el resource pack al entrar; quien lo rechaza no puede jugar ✅.
- El pack se aloja en GitHub Releases y el server lo manda con su hash ✅. Objetivo de peso: menos de 50 MB, para que la descarga sea rápida.
- Los shaders son opcionales y los pone cada jugador. El server se tiene que ver bien sin ellos.

### Modelos y texturas

- **Propios, tomando como referencia el estilo de cada juego**: silueta, proporciones, paleta, desgaste e interfaz. No se copian archivos de esos juegos. Las armas y los vehículos llevan sus nombres reales (M4A1, T-72, etc.), que son diseños reales y no de los estudios.
- **Formato**: modelos de cubos (JSON del resource pack) con texturas de 32 a 128 px según el tamaño del objeto ✅. Es el techo de realismo sin mods; ver la sección 4.
- **Objetos grandes** (vehículos, jefes, criaturas): varias piezas montadas con *item displays* que el plugin mueve y anima ✅ ⚠️ (las animaciones son trabajo manual).
- **Armaduras propias** visibles sobre el jugador ✅.
- **Sonidos propios** (disparos, motores, golpes, rugidos), generados o de bibliotecas libres. No se sacan de los juegos.
- Lo modelo y texturizo yo, y queda todo en el repo listo para abrir y retocar en Blockbench.

### HUD

- Se reemplazan los corazones, la comida y la barra de experiencia por un HUD propio dibujado con fuentes del resource pack: barras, munición, iconos y marcadores de impacto ✅.
- Barra de jefe con textura propia ✅.
- Marcador lateral (sidebar) por modo, como ahora ✅.

### Lobby

- Plaza central con tres portales temáticos: un contenedor militar (Shooter), un hangar (Guerra) y un arco de piedra con fuego (RPG) ✅.
- Carteles con estadísticas de cada modo: bajas, capturas y jefes derrotados ✅.
- Selector de modo (la estrella) y `/modo`, como ahora ✅.
- Cada modo guarda su progreso por separado ✅. Ya existe en `Almacen`.

### Escalado por cantidad de jugadores

| Modo | 4 jugadores | 6 jugadores |
|---|---|---|
| Shooter | Meta de 20 bajas | Meta de 30 bajas |
| Guerra | 2 vs 2, vehículos con un 25 % más de tiempo de reaparición | 3 vs 3 |
| RPG | Jefes con vida base ×1,9 | Jefes con vida base ×2,5 (+30 % por jugador extra en la arena) |

---

## 1. Shooter: Pueblo Atómico

### 1.1 Concepto

Todos contra todos rápido, estilo Modern Warfare: muertes en menos de medio segundo, mucha movilidad, rachas y clases personalizadas. El mapa es un barrio de casas de los años 50 en medio de un campo de pruebas nucleares, inspirado en Nuketown de Black Ops.

### 1.2 Reglas de la partida

- **Victoria**: el primero en llegar a la meta (20 bajas con 4 jugadores, 30 con 6) o el que más tenga a los 10 minutos.
- **Vida**: 100 puntos. Empieza a regenerarse 4 s después del último daño y se llena en 3 s ✅.
- **Reaparición**: a los 3 s, en el punto más alejado de los enemigos vivos ✅ (ya existe `spawnSeguro`). Protección de 2 s, que se corta al disparar.
- **Sin daño por caída** ✅.
- **Al terminar**: podio con los 3 primeros, la explosión nuclear (ver 1.10) y una nueva partida a los 15 s ✅.
- **Bajas**: el killfeed va en el chat con arma y headshot ✅. Kill cam ❌.

### 1.3 Movilidad

| Acción | Tecla | Qué hace | |
|---|---|---|---|
| Correr | Ctrl o doble W | Velocidad normal de sprint. No se puede disparar ni apuntar | ✅ |
| Sprint táctico | Doble toque de W mientras corrés | +30 % de velocidad durante 3 s, arma baja. Recarga de 8 s | ✅ |
| Deslizarse | Shift mientras corrés | Impulso de unos 5 bloques, cuerpo agachado (cuesta más pegarte) y disparo desde la cadera. Enfriamiento de 1 s | ✅ El cuerpo se ve agachado; no hay animación de deslizarse |
| Salto de delfín | Shift en el aire mientras corrés | Estirada hacia adelante y caés cuerpo a tierra (guiño a Black Ops) | ⚠️ |
| Agacharse | Shift | Menos dispersión, menos ruido | ✅ |
| Cuerpo a tierra | Doble toque de Shift | Te tirás al piso: altura de 0,6 bloques, mucha precisión, movimiento lento | ⚠️ Usa un bloque fantasma que solo ve ese jugador (técnica conocida) |
| Trepar | Saltar frente a un borde de hasta 2 bloques | Te sube por encima del muro o la ventana | ✅ |
| Cambiar de arma | 1 / 2 o rueda | Principal y secundaria | ✅ |

### 1.4 Disparo y daño

| Acción | Tecla | |
|---|---|---|
| Disparar | Clic derecho; mantener = automático | ✅ El cliente repite el clic cada 4 ticks y el plugin completa la cadencia |
| Apuntar (ADS) | Clic izquierdo (alterna) | ✅ Zoom con el efecto de FOV y retícula propia. Las miras de francotirador ponen un overlay a pantalla completa ✅ |
| Recargar | Q | ✅ Ya existe |
| Cuchillazo rápido | F | ✅ |
| Letal / táctico | Slots 3 y 4 + clic derecho (mantener para cocinar la granada) | ✅ |

- **Precisión**: dispersión desde la cadera mayor que apuntando; penalización al correr, saltar y en el aire. Bonificación agachado y cuerpo a tierra ✅ (ya existe en parte).
- **Retroceso**: patrón vertical por arma que empuja la mira hacia arriba ⚠️. Se hace rotando la cámara desde el server; con lag se nota a saltos.
- **Daño por distancia**: cada arma pierde daño pasado su alcance efectivo ✅.
- **Multiplicadores**: cabeza ×1,5; francotiradores ×2; piernas ×0,8 ✅.
- **Penetración**: madera, vidrio, lana y paneles dejan pasar la bala con daño reducido ✅.
- **Marcador de impacto**: una cruz en el centro de la pantalla; roja si mataste ✅.

### 1.5 Arsenal

Valores iniciales con 100 de vida. "Disp/s" son disparos por segundo; el alcance se mide en bloques (1 bloque = 1 m).

| Arma | Tipo | Daño cuerpo / cabeza | Disp/s | Cargador | Recarga | Alcance | Rol |
|---|---|---|---|---|---|---|---|
| M4A1 | Fusil de asalto | 26 / 39 | 13 | 30 | 2,0 s | 45 | Todo terreno, poco retroceso |
| AK-47 | Fusil de asalto | 34 / 51 | 10 | 30 | 2,3 s | 45 | 3 tiros, pero patea |
| FN SCAR-H | Fusil de asalto | 38 / 57 | 9 | 20 | 2,4 s | 55 | Media distancia |
| HK G36K | Fusil de asalto | 28 / 42 | 12 | 30 | 2,0 s | 40 | Estable, para apuntar en movimiento |
| MP5 | Subfusil | 22 / 33 | 13 | 30 | 1,8 s | 25 | Clásico, preciso |
| MP7 | Subfusil | 20 / 30 | 15 | 40 | 1,9 s | 22 | Cargador grande |
| KRISS Vector | Subfusil | 18 / 27 | 19 | 25 | 1,9 s | 18 | Cadencia altísima, corto alcance |
| P90 | Subfusil | 19 / 28 | 15 | 50 | 2,6 s | 22 | Mucho cargador, recarga lenta |
| M249 SAW | Ametralladora ligera | 28 / 42 | 13 | 100 | 5,5 s | 50 | Supresión, apunta lento |
| PKM | Ametralladora ligera | 36 / 54 | 10 | 100 | 6,0 s | 55 | Pegada fuerte |
| Barrett M82A1 | Francotirador | 120 / 240 | 0,7 | 10 | 3,5 s | 150 | Mata de un tiro al cuerpo |
| M24 | Francotirador | 100 / 200 | 0,8 | 5 | 3,0 s | 130 | Mata al torso; a las piernas, no |
| SVD Dragunov | Tirador designado | 60 / 120 | 4 (semi) | 10 | 2,8 s | 90 | Dos tiros |
| Mk 14 EBR | Tirador designado | 50 / 100 | 5 (semi) | 20 | 2,6 s | 80 | Agresiva |
| Benelli M4 | Escopeta semi | 8 × 14 | 3 | 7 | 0,5 s por cartucho | 10 | Rápida |
| Remington 870 | Escopeta de bombeo | 8 × 18 | 1,2 | 6 | 0,6 s por cartucho | 12 | Un tiro de cerca |
| AA-12 | Escopeta automática | 8 × 10 | 5 | 8 | 3,0 s | 8 | Caótica |
| Beretta M9 | Pistola | 30 / 45 | 6 (semi) | 15 | 1,5 s | 20 | Secundaria de inicio |
| Desert Eagle | Pistola | 55 / 90 | 2,5 (semi) | 7 | 1,8 s | 25 | Dos tiros |
| Colt M1911 | Pistola | 38 / 57 | 5 (semi) | 8 | 1,5 s | 20 | Equilibrada |
| Revólver .44 (S&W 329PD) | Pistola | 60 / 100 | 2 | 6 | 2,8 s | 25 | Headshot de un tiro |
| M79 | Lanzagranadas | 100 en el centro, radio 3 | 1 tiro | 1 | 2,5 s | 40 | Secundaria explosiva |
| RPG-7 | Lanzacohetes | 150 en el centro, radio 3 | 1 tiro | 1 | 3,5 s | 60 | Secundaria explosiva |
| Cuchillo de combate | Cuerpo a cuerpo | 55; por la espalda mata | — | — | — | 2,5 | Siempre equipado (F) |

### 1.6 Accesorios y camuflajes

- **Accesorios**: hasta 3 por arma, y el modelo del arma cambia según lo que tenga puesto ✅.
  - Miras: punto rojo, holográfica, ACOG 4× y mira de francotirador 8×.
  - Silenciador: no aparecés en el minimapa al disparar y perdés un 15 % de alcance.
  - Cargador ampliado, empuñadura vertical (−25 % de retroceso), láser (mejor disparo desde la cadera) y culata liviana (apuntás más rápido).
- **Camuflajes**: Bosque, Desierto, Urbano, Tigre y Digital; los de desafío son Oro, Diamante y Atómico (verde brillante) ✅. Se ganan con bajas y headshots de esa arma.

### 1.7 Equipamiento

| Tipo | Ítem | Efecto | |
|---|---|---|---|
| Letal | Granada de fragmentación | Se cocina; explota a los 3 s | ✅ |
| Letal | Semtex | Se pega al primer blanco | ✅ |
| Letal | Hacha arrojadiza | Mata de un impacto y se recupera | ✅ |
| Letal | Claymore | Mina direccional | ✅ |
| Táctico | Aturdidora | Ceguera y lentitud 2 s | ✅ |
| Táctico | Humo | Nube de 6 s que tapa la visión | ✅ |
| Táctico | Sensor de latidos | Muestra a los enemigos cercanos en el HUD | ✅ |

### 1.8 Clases personalizadas y ventajas

- 5 clases editables: principal, secundaria, letal, táctico y una ventaja por cada uno de los 3 slots. Se cambian con el libro o con `/clase` y se aplican al reaparecer ✅. Ya existe una versión simple.
- **Ventajas**:

| Slot 1 | Slot 2 | Slot 3 |
|---|---|---|
| Prestidigitación: recarga un 40 % más rápido | Fantasma: invisible al UAV | Rastreador: ves las huellas de los enemigos como partículas |
| Carroñero: recuperás munición de los muertos | Endurecido: más penetración | Comando: más alcance del cuchillo |
| Ligero: +7 % de velocidad | Intransigente: las rachas cuestan una baja menos | Mano firme: menos dispersión desde la cadera |

### 1.9 Rachas (bajas sin morir)

| Bajas | Racha | Efecto | |
|---|---|---|---|
| 3 | UAV | 30 s viendo a los enemigos en el minimapa | ✅ / ⚠️ (con minimapa) |
| 4 | Paquete de ayuda | Cae donde tirás la bengala: munición, otra racha o un arma especial | ✅ |
| 5 | Misil Predator | Manejás un misil desde el cielo con tu cámara | ⚠️ La cámara se engancha al misil |
| 7 | Bombardeo de precisión | Línea de explosiones donde apuntás | ✅ Ya existe |
| 11 | Perros de ataque | 4 perros que cazan enemigos durante 30 s | ✅ |
| 25 | Bomba atómica | Gana la partida en el acto y detona el pueblo | ✅ |

### 1.10 Mapa: Pueblo Atómico

**Tamaño**: unos 70 × 50 bloques jugables, rodeados de desierto que funciona como decorado.

**Distribución**:
- Dos casas de dos pisos, una **amarilla** y una **verde**, enfrentadas a ambos lados de una calle sin salida con rotonda.
- Cada casa tiene:
  - garaje con un auto de época;
  - living, cocina y escalera;
  - dormitorios arriba, con ventanas que dan a la calle (posiciones para francotirador);
  - patio trasero con cerca blanca, cobertizo y pileta vacía.
- **Centro** (cobertura y flanqueo): un colectivo escolar abandonado, un camión de mudanza con la caja abierta, una casa rodante y un pickup.
- **Laterales**: patios conectados por huecos en las cercas, para rodear sin pasar por la calle.
- **Cartel "Población"** a la entrada, con un contador que muestra las bajas de la partida ✅.
- **Maniquíes** de la familia nuclear en las casas y la calle. Si matás a todos de un headshot, suena una canción y ganás un bonus de XP (easter egg) ✅.

**Puntos de aparición**: 14 entre las casas, los patios y los extremos de la calle ✅.

**Final de la partida**: destello blanco, onda expansiva, humo y temblor ✅. El mapa se restaura para la siguiente partida.

**Ambiente**: mediodía soleado y fijo, con el cielo despejado. Afuera del límite, torres de prueba, un búnker y carteles de radiación como decorado.

### 1.11 Dirección de arte

- **Armas**: estilo Modern Warfare con metal oscuro gastado, polímero negro o color arena y madera en la AK y el Dragunov. Bordes con desgaste, tornillería visible y miras con lente reflectante.
- **Pueblo**: paleta de los años 50, con revestimiento de madera pintado (amarillo manteca y verde menta), tejas oscuras, cercas blancas, muebles retro y heladeras redondeadas.
- **Desierto**: arena clara, matas secas y postes de luz.
- **HUD**:
  - abajo a la derecha, munición (cargador y reserva) y el nombre del arma;
  - abajo a la izquierda, las rachas disponibles;
  - arriba a la izquierda, el minimapa ⚠️ (un mapa en la mano izquierda que dibuja el plugin);
  - en el centro, la retícula dinámica.
- **Sonido**: disparo distinto por arma, con cola de eco en exteriores, y voces del locutor ("UAV en línea").

---

## 2. Guerra: Valle de Hierro

### 2.1 Concepto

Dos equipos, **Azul** y **Rojo**, en un conflicto moderno ("WW3"). Infantería y vehículos combinados, estilo Battlefield, con daño de vehículos inspirado en War Thunder y World of Tanks: blindaje por zonas, módulos y munición AP o HE. El objetivo es robar la bandera enemiga.

### 2.2 Captura la bandera

- Cada base tiene su bandera en un mástil ✅.
- **Capturar**: tocás la bandera enemiga, la llevás a tu base y la entregás en tu mástil. Para que cuente, tu bandera tiene que estar en casa ✅.
- **Victoria**: 3 capturas, o más capturas a los 15 min. Si hay empate, muerte súbita de 3 min (la primera captura gana).
- **Portador**:
  - brilla para todos y se ve en el HUD ✅;
  - puede ir en jeep o en cuatriciclo, como conductor o pasajero;
  - no puede usar tanque, avión ni antiaéreo, para que no la lleve un tanque blindado.
- **Si muere el portador**: la bandera cae. Vuelve a su base si la toca alguien de su equipo o a los 30 s ✅.
- **Reaparición**:
  - a los 6 s, en tu base;
  - o en el **puesto avanzado** del pueblo si tu equipo lo controla. Lo controla quien se queda 10 s en la zona sin enemigos dentro (opcional).

### 2.3 Infantería

Usa el mismo sistema de armas y movilidad del Shooter (deslizarse, cuerpo a tierra, trepar), con estos agregados:
- Si saltás de un avión, se abre el **paracaídas** ✅.
- **Botiquín**: cura a un compañero.
- **Revivir**: al morir quedás caído 10 s; si un Fusilero llega en ese tiempo, te levanta ✅.

| Clase | Principal | Secundaria y equipo | Rol |
|---|---|---|---|
| Fusilero | M4A1 / AK-47 / SCAR-H | Pistola, granadas, humo, botiquín | Combate de infantería, revive |
| Antitanque | Carabina (G36K) | RPG-7 (sin guía), Javelin (fija un blanco terrestre en 2 s y ataca desde arriba), minas antitanque | Cazar tanques |
| Ingeniero | Subfusil | Llave de reparación, C4, Stinger (fija un avión en 2 s) | Reparar, emboscar, antiaéreo |
| Tirador | M24 / SVD | Pistola, binoculares (marca blancos para el equipo por 10 s), claymore | Reconocimiento, cubrir la bandera |

### 2.4 Vehículos

Velocidades en bloques por segundo (b/s); 1 b/s = 3,6 km/h. La escala está comprimida para que el mapa alcance.

| Vehículo | Inspirado en | Asientos | Armamento | Vida | Blindaje | Vel. máx. | Por equipo | Reaparece |
|---|---|---|---|---|---|---|---|---|
| Cuatriciclo militar | ATV militar | Conductor + 1 | — | 30 | Ninguno | 22 | 2 | 20 s |
| Jeep | Humvee | Conductor + artillero + 2 | Ametralladora .50 en torreta | 80 | Ligero | 18 | 2 | 30 s |
| Vehículo de combate de infantería | Bradley / BMP | Conductor + artillero + 2 | Cañón automático 25 mm + 2 misiles TOW | 200 | Medio | 13 (anfibio) | 1 | 60 s |
| Tanque | M1 Abrams / T-72 | Conductor-artillero + ametralladora de techo | Cañón 120 mm (AP / HE) + ametralladora coaxial | 350 | Pesado | 10 | 1 | 90 s |
| Antiaéreo | Gepard / Tunguska | Conductor-artillero + 1 | 2 cañones de 35 mm + radar que marca aviones | 180 | Medio | 12 | 1 | 60 s |
| Avión | Caza (F-16 / MiG-29) o ataque (A-10 / Su-25), se elige en el hangar | Piloto | Cañón + 2 misiles aire-aire, o cañón + cohetes + 2 bombas | 120 | Ligero | 25–35 | 1 | 120 s |

El helicóptero (Apache / Mi-24) queda para más adelante ⚠️: es el más difícil de manejar con teclado sin mods.

**Cómo se arma un vehículo**:
- Una entidad invisible hace de asiento y lleva al jugador.
- Encima van varias piezas con modelo propio (casco, torreta y cañón) que el plugin mueve en cada tick con interpolación suave ✅.
- El plugin lee W, A, S, D, espacio y Shift ✅. Es la misma API que ya usa el esquive del RPG.

### 2.5 Manejo de cada vehículo

**Con ruedas (cuatriciclo, jeep)**:
- W acelera, S frena y da marcha atrás, A/D doblan (doblan menos a más velocidad) ✅.
- Espacio = freno de mano: derrapa ⚠️ (aproximado).
- Suben escalones de 1 bloque.
- Chocar a más de 12 b/s daña al vehículo y a los ocupantes ✅.

**Con orugas (tanque, VCI, antiaéreo)**:
- W/S aceleran despacio y A/D giran el casco sobre su eje ✅.
- **La torreta sigue tu mirada** a velocidad limitada (el tanque gira a unos 40°/s). El cañón sube entre −8° y +20° ✅.
- La retícula marca dónde pegaría el cañón, no dónde mirás ✅ (un marcador que solo ve el artillero).
- Atraviesan cercas, vidrios, hojas y cultivos y los rompen ✅.
- El VCI flota y cruza el río ✅.

**Avión**:
- W/S suben y bajan la potencia; el avión sigue tu mirada con inercia (cabeceo y guiñada) ✅ ⚠️.
- A/D alabean (inclinan): giro más cerrado y efecto visual.
- **Despegue**: desde la pista de tu base, superando los 18 b/s.
- **Pérdida**: por debajo de 15 b/s en vuelo, el avión cae hasta recuperar velocidad ✅.
- **Rearme y reparación**: aterrizando en tu pista ✅.
- Chocar contra el terreno = avión destruido.
- Shift = eyección con paracaídas ✅.

**Cámaras**:
- Primera persona desde el asiento ✅.
- F5 para tercera persona (lo maneja el cliente) ✅.
- El artillero tiene vista de mira con overlay propio ✅.

**Subir y bajar**:
- Clic derecho sobre el vehículo para subir.
- Mantener Shift 1 s para bajar (evita bajarse sin querer).
- Con las teclas 1 a 4 cambiás de asiento ✅. F queda para la munición (ver 2.6).

### 2.6 Daño, blindaje y munición

- **Zonas de blindaje** según desde dónde viene el impacto, calculadas respecto al casco ✅:
  - frente ×0,4;
  - lateral ×1;
  - trasera ×1,6;
  - techo ×2 (Javelin y bombas).
- **Munición del tanque**: se cambia con F estando en el asiento del artillero ✅.
  - **AP**: 120 de daño, proyectil rápido (120 b/s) con caída.
  - **HE**: 60 + 40 en un radio de 4; mata infantería y daña vehículos ligeros.
- **Módulos** (estilo War Thunder) ✅:
  - Orugas o ruedas rotas: inmovilizado 8 s o hasta reparar.
  - Motor dañado: velocidad a la mitad.
  - Cañón dañado: no dispara durante 6 s.
- **Incendio**: un impacto trasero puede prender el motor, que pierde vida hasta que lo apagás con el extintor (tecla Q, una vez por vida del vehículo) ✅.
- **Reparación**: con la llave del Ingeniero o en la zona de reparación de la base ✅.
- **Armas de infantería contra vehículos**:

| Arma | Contra tanque | Contra jeep | Contra avión |
|---|---|---|---|
| Fusiles y subfusiles | 0 | Poco | Poco |
| RPG-7 | 100 | 100 | — |
| Javelin | 160 (techo) | 160 | — |
| Stinger | — | — | 100 |
| C4 | 200 | 200 | — |
| Mina AT | 150 + orugas | Destruye | — |

- **Vehículo destruido**: explosión y carcasa humeante. Los ocupantes salen eyectados con daño ✅.
- Sin fuego amigo ✅.

### 2.7 Mapa: Valle de Hierro

**Tamaño**: 512 × 512 bloques con borde de mundo. Las bases están en los extremos oeste (Azul) y este (Rojo).

**Cada base** tiene:
- mástil con la bandera dentro de un recinto de bolsas de arena;
- garaje con los puntos de aparición de los vehículos;
- hangar y pista de 120 bloques;
- zona de reparación y búnker de aparición protegido.

**Centro: el pueblo** (unos 130 × 130), para combate cerrado:
- iglesia con campanario (nido de francotirador);
- plaza con fuente y mercado;
- casas de 2 y 3 pisos con interiores recorribles: escaleras, sótanos y ventanas;
- calles angostas donde los tanques quedan expuestos a emboscadas;
- una fábrica con galpón y grúa;
- estación de tren con vagones como cobertura;
- el **puesto avanzado** opcional está en la plaza.

**Norte: campo abierto**:
- campos de trigo y girasoles con setos altos (como el bocage normando, buenos para emboscar tanques);
- granjas aisladas, molino y fardos de pasto;
- una loma con trincheras y búnkeres, desde donde los tanques pueden asomar solo la torreta.

**Sur: bosque y río**:
- bosque denso, malo para vehículos y bueno para infantería;
- río con **2 puentes** y un vado poco profundo: son los cuellos de botella;
- los puentes se pueden volar con C4 y se restauran al terminar.

**Elementos de cobertura**: cráteres, vehículos destruidos, erizos antitanque y alambre de púas.

**Destrucción**: paredes y techos del pueblo se rompen con explosiones (HE, bombas, C4, RPG) ✅. El mapa entero se restaura al terminar cada partida ✅.

**Clima por partida** (al azar): día despejado, atardecer o lluvia ✅. La niebla real no existe sin mods ❌; se aproxima con partículas.

### 2.8 Dirección de arte

- **Vehículos**:
  - camuflaje militar moderno: verde OTAN de tres tonos en Azul y arena-marrón en Rojo;
  - metal gastado, barro en orugas y ruedas;
  - escotillas, antenas y cajas de herramientas;
  - insignia del equipo: franja azul o roja y número de torreta.
- **Infantería**: uniforme y casco con el color del equipo, como armadura propia ✅.
- **Pueblo**: estilo europeo, con ladrillo, revoque descascarado, tejas rojas, persianas de madera y daño de guerra (agujeros, hollín, escombros).
- **Campo**: trigo, tierra arada, caminos de barro y postes de madera.
- **HUD**:
  - arriba, el marcador de capturas y el estado de las dos banderas (en base / robada / caída) ✅;
  - en un vehículo: velocidad, vida, munición elegida, recarga y módulos dañados como iconos rojos ✅.

---

## 3. RPG / Souls: Las Tierras Cenicientas

### 3.1 Concepto y lore

El reino de Valdren cayó cuando **el Durmiente**, una entidad antigua bajo el mar, empezó a despertar. El sol quedó velado, llueve ceniza y la gente se volvió Hueca. Los jugadores son **Portadores de Ceniza**, los últimos que conservan una chispa de llama.

**Objetivo**: derrotar a los 4 Señores de las zonas, abrir la Puerta del Abismo y vencer al Durmiente. Después empieza el **Ciclo+**: el mismo mundo, con enemigos más fuertes y mejores recompensas.

**Tono**: medieval, oscuro y apocalíptico, con horror cósmico (lovecraftiano) a medida que avanzás.

### 3.2 Combate y movilidad

| Acción | Tecla | Detalle | |
|---|---|---|---|
| Ataque ligero | Clic izquierdo | Rápido, gasta poco aguante | ✅ |
| Ataque pesado | Shift + clic izquierdo | Lento, más daño y más daño de postura | ✅ |
| Ataque en carrera | Clic izquierdo corriendo | Embestida | ✅ |
| Ataque en caída | Clic izquierdo cayendo desde más de 2 bloques | ×1,5 de daño | ✅ |
| Bloquear | Clic derecho con escudo | Gasta aguante por golpe; si se vacía, se rompe la guardia | ✅ Ya existe |
| **Parry** | Clic derecho con escudo, o con un arma con guardia como la katana, justo antes del golpe (ventana de 0,25 s) | Aturde al enemigo 1,5 s | ✅ |
| **Golpe crítico** | Clic izquierdo a un aturdido o por la espalda | ×3 de daño, sonido y efecto propios | ✅ Sin animación especial del jugador |
| **Voltereta** | F + dirección | Invulnerable 8 ticks; la distancia depende de la carga de equipo | ✅ / ⚠️ El cuerpo baja al piso durante la voltereta (bloque fantasma); no hay animación de rodar |
| Paso atrás | F sin dirección | Corto y rápido | ✅ Ya existe |
| Correr | Ctrl | Gasta aguante | ✅ Ya existe |
| Habilidad del arma | Q con el arma en la mano | Movimiento especial; gasta éter | ✅ |
| Hechizos | Clic derecho con catalizador; rueda para elegir | Ver 3.6 | ✅ |
| Estus y objetos | Seleccionar el slot y clic derecho | Te deja expuesto mientras tomás, como en Souls | ✅ Ya existe |

- **Aguante**: lo gasta todo (atacar, bloquear, rodar y correr) y no se regenera mientras lo usás ✅ (ya existe).
- **Postura**: cada enemigo y jugador acumula daño de postura. Si se llena, queda aturdido y expuesto a un crítico. Los pesados y los parries llenan mucho ✅.
- **Carga de equipo** (peso del equipo sobre la capacidad) ✅:
  - liviana (menos del 30 %): voltereta larga y rápida;
  - media (hasta el 70 %): normal;
  - pesada (hasta el 100 %): voltereta corta y lenta;
  - sobrecarga: no rodás.
- **Fijar objetivo (lock-on)** ⚠️: el server puede girar la cámara, pero se siente brusco. Queda afuera salvo que lo probemos y funcione bien.
- **Sin construir ni romper bloques** (modo aventura), para que nadie haga trampa contra los jefes ✅.

### 3.3 Atributos y nivel

- Se sube de nivel en las hogueras gastando almas ✅ (ya existe, con 3 atributos; pasa a 7):

| Atributo | Sube |
|---|---|
| Vigor | Vida máxima |
| Mente | Éter máximo (para habilidades y hechizos) |
| Aguante | Aguante máximo y capacidad de carga |
| Fuerza | Daño de armas pesadas |
| Destreza | Daño de armas rápidas y arcos |
| Inteligencia | Daño de hechicería |
| Fe | Daño y curación de milagros |

- Cada arma escala con uno o dos atributos (por ejemplo, una katana: Destreza A, Fuerza D) ✅.
- **Requisitos**: si no llegás al mínimo de Fuerza o Destreza de un arma, pega la mitad ✅.

### 3.4 Clases

Seis, una por jugador.

| Clase | Arma única | Armadura | Habilidad del arma (Q) | Estilo |
|---|---|---|---|---|
| **Caballero Ceniciento** | Espada bastarda + escudo de cometa | Placas pesada | *Tajo ascendente*: lanza al enemigo hacia arriba | Equilibrado, bloqueo y parry. Fuerza / Vigor |
| **Verdugo** | Gran hacha a dos manos | Media con cadenas | *Grito de guerra*: +20 % de daño propio y de los aliados cercanos por 15 s | Golpes lentos que rompen postura. Fuerza |
| **Ronin** | Katana (acumula sangrado) | Ligera de tela y cuero | *Desenvaine*: tajo instantáneo a 4 bloques | Parry y velocidad. Destreza |
| **Cazador de Bestias** | Arco largo + dagas gemelas | Cuero, capa con capucha | *Lluvia de flechas*: área de 5 bloques | Distancia y movilidad. Destreza |
| **Hechicero del Vacío** | Bastón de hueso + daga | Túnica | *Orbe gravitatorio*: atrae y aplasta | Hechizos a distancia. Inteligencia / Mente |
| **Clérigo de la Llama** | Maza + escudo pequeño | Media con hábito | *Llamarada sagrada*: quema alrededor | Curación y apoyo. Fe |

Cada clase empieza con su arma, su set de armadura y 2 objetos propios.

### 3.5 Armas, mejoras y armaduras

- **Mejoras**: +0 a +10 con el herrero del Santuario, usando **Fragmentos de Hierro Estelar** (comunes, los sueltan los enemigos) y **Escamas del Abismo** (raras, para +7 en adelante) ✅.
- **Armas de jefe**: cada Señor deja un **alma**. En el Santuario se cambia por un arma o un hechizo único de ese jefe, con habilidad propia ✅.
- **Armas comunes** que se encuentran en el mundo: espada corta, lanza, estoque, alabarda, guadaña, ballesta y martillo de guerra (unas 10).
- **Armaduras**:
  - un set por clase más 3 sets de jefe;
  - cada pieza tiene defensa física y mágica, resistencia (veneno, sangrado, locura y frío), peso y postura ✅;
  - se ven sobre el jugador con modelo propio ✅.
- **Estados alterados** ✅:
  - Veneno: daño lento.
  - Sangrado: se acumula y al llenarse estalla con un golpe del 15 % de la vida.
  - Frío: congela la pantalla y te frena (efecto de nieve polvo de Minecraft).
  - **Locura**: aparece en las zonas del Durmiente; te hace daño y te distorsiona la vista.

### 3.6 Magia

- **Hechicería** (Inteligencia, con bastón):
  - Saeta del Vacío: proyectil rápido.
  - Lanza del Vacío: proyectil cargado que atraviesa.
  - Niebla corrosiva: área de veneno.
  - Escudo arcano: −50 % de daño por 10 s.
- **Milagros** (Fe, con talismán o maza):
  - Curación: grupal en un radio de 6.
  - Lanza de fuego sagrado.
  - Bendición: regeneración por 20 s.
  - Llama purificadora: limpia los estados alterados.
- Se gastan con **éter** (barra azul), que se recupera con el **Frasco de Éter** o en las hogueras ✅.

### 3.7 Objetos

- **Frasco de Estus**: cura el 45 %. Se rellena en las hogueras y se mejora de 3 a 8 cargas ✅ (ya existe).
- **Frasco de Éter**: recupera éter. Las cargas se reparten con el Estus en el herrero.
- **Consumibles**:
  - bombas de fuego y cuchillos arrojadizos;
  - resinas (rayo o fuego en el arma por 30 s);
  - hierbas contra el veneno y el sangrado;
  - **Ceniza de Retorno**: te lleva a tu última hoguera.
- **Llaves y objetos de misión**: abren atajos y zonas.

### 3.8 Muerte y almas

- Al morir perdés tus almas, que quedan donde caíste. Si llegás a buscarlas, las recuperás; si morís antes, se pierden ✅ (ya existe).
- Al descansar en una hoguera **reaparecen todos los enemigos** comunes (no los jefes ni las élites derrotadas) ✅.

### 3.9 Enemigos y dificultad progresiva

**Nivel de los enemigos**: cada zona tiene un rango de nivel, y dentro de la zona sube con la distancia a su hoguera de entrada ✅.

| Escala | Efecto |
|---|---|
| Por nivel | Vida ×(1 + 0,08·nivel), daño ×(1 + 0,05·nivel), más postura |
| Por Ciclo+ | Vida y daño ×1,5 acumulativo; más almas y mejores objetos |

- **Encuentros diseñados**: los enemigos aparecen en puntos fijos de cada zona (emboscadas, patrullas, guardias), no al azar como en Minecraft ✅.
- **Élites**: el 10 % de los enemigos aparece como versión Campeón ✅. Tienen aura, el doble de vida, un ataque especial y sueltan mejor botín.
- **Mímicos**: algunos cofres muerden ✅.
- **Noche Roja**: cada 3 noches del juego, cielo rojo durante una noche. Los enemigos reaparecen más fuertes y dan el doble de almas ✅.
- **Cazador Carmesí**: un invasor NPC (caballero con armadura negra y roja) que persigue a los jugadores en las zonas 2 a 4 de vez en cuando. Pelea como un jugador: rueda, bloquea y toma estus. Si lo matás, suelta su set por partes ✅ ⚠️ (es una IA propia).

**Tipos de enemigo**, inspirados en tus referencias de criaturas:

| Enemigo | Zona | Descripción |
|---|---|---|
| Hueco | 1 | Soldado no-muerto con armadura oxidada; espada, lanza o ballesta |
| Perro de la plaga | 1 | Rápido, en jauría |
| Cuervo carroñero | 1–2 | Vuela y picotea |
| Acechador | 2 | Criatura flaca de patas larguísimas que corre en cuatro patas |
| Araña tejedora | 2 | Tira telarañas que frenan |
| Bestia de carne | 2–3 | Mole sin ojos con fauces enormes; embiste |
| Caballero caído | 3 | Escudo y espada; bloquea y hace parry |
| Gárgola | 3 | Planea desde los techos |
| Pálido | 4 | Humanoide blanco y alargado, aparece entre la niebla |
| Ahogado | 4 | Criatura anfibia con tentáculos en la cara; sale del agua |
| Vástago del Durmiente | 5 | Masa de tentáculos que escupe locura |

### 3.10 Jefes

Cada jefe tiene arena propia con **muro de niebla** (una vez adentro, no salís hasta ganar o morir), barra de jefe y ataques anunciados: un gesto previo que deja tiempo para rodar o hacer parry ✅. Los modelos y las animaciones son el trabajo más grande ⚠️.

| Jefe | Zona | Inspiración (tus referencias) | Fases y mecánicas | Recompensa |
|---|---|---|---|---|
| **El Glotón de las Fauces** | 1, Aldea Hueca (molino quemado) | Mole oscura con sonrisa gigante | F1: embestidas y golpes al piso. F2 (50 %): agarre que te traga, y si no te sueltan con daño te escupe con mucho daño; se acelera. | Alma → Gran maza del Glotón |
| **La Tejedora** | 2, Bosque Podrido (árbol hueco) | Criatura roja de patas largas | F1: telarañas (lentitud) y saltos entre ramas. F2: invoca crías y cuelga del techo; hay que cortar los hilos que la sostienen. | Alma → Hechizo Red Pútrida + set de seda |
| **Caballero del Abismo** | 3, Ciudadela (salón del trono) | El jefe actual, mejorado | Duelo técnico: todos sus ataques se pueden parar con parry. F2: fuego oscuro y siervos (ya existe una versión). | Alma → Espada del Abismo + set del Abismo |
| **El Vigía Pálido** | 4, Costa Hundida (faro) | Criatura pálida, alargada y con cola | Se teletransporta en la niebla. Su grito da locura. F2: duplicados falsos; solo el real deja huellas. | Alma → Lanza del Faro |
| **El Durmiente** | 5, Templo del Abismo (roca en el mar) | Portada de *La Llamada de Cthulhu*: gigante alado con tentáculos | F1: tentáculos que salen del agua (romperlos abre su guardia). F2: ráfaga de alas que empuja hacia el borde. F3: levanta vuelo y la mirada aplica locura; hay que esconderse tras los monolitos. | Final del Ciclo; alma → arma a elección |

Además, cada zona tiene 1 o 2 **minijefes** (Campeón Hueco, Bestia Alfa, Gárgola Gemela, etc.) que custodian atajos u objetos clave.

### 3.11 Mundo: Las Tierras Cenicientas

**Tamaño**: unos 1500 × 1500 bloques. El borde actual de 8000 es demasiado para 6 jugadores.

**Santuario del Último Fuego**: el hub, en un acantilado al sur del centro. Tiene la hoguera principal, el herrero, el mercader, la guardiana del fuego (subir de nivel) y el altar donde se canjean las almas de jefe. Desde ahí sale un camino a cada zona.

| Zona | Nivel | Entorno |
|---|---|---|
| 1. **Aldea Hueca** (oeste) | 1–10 | Aldea medieval quemada, cementerio, molino, campos de trigo podrido y horcas. Atardecer naranja con ceniza |
| 2. **Bosque Podrido** (norte) | 10–25 | Árboles muertos gigantes, pantano venenoso, hongos que brillan y telarañas. Niebla verdosa |
| 3. **Ciudadela Desmoronada** (este, en la montaña) | 25–40 | Castillo y catedral gótica en ruinas, murallas, puentes colgantes y torres. Se ve desde todo el mapa |
| 4. **Costa Hundida** (sudeste) | 40–55 | Ciudad portuaria medio sumergida, faro, barcos encallados y arena negra. Niebla densa y llovizna |
| 5. **Templo del Abismo** (bajo la costa) | 55+ | Se abre con las 4 almas de Señor. Descenso por una escalera ciclópea a una roca en el mar, con arquitectura no euclidiana y bioluminiscencia verde |

**Diseño de niveles** ✅:
- hogueras intermedias en cada zona (2 o 3);
- **atajos** que se abren desde un solo lado (puertas, ascensores, escaleras que bajan) y conectan con hogueras anteriores;
- **paredes ilusorias** que desaparecen al golpearlas;
- secretos con equipo raro;
- notas y descripciones de ítems que cuentan el lore.

**Terreno**: relieve natural de Minecraft con **biomas propios** por zona ✅, que definen el color de la niebla, el cielo y el agua y las partículas ambientales (ceniza, esporas). Las estructuras (aldea, castillo, faro, templo) las construye el plugin desde plantillas guardadas en el repo ✅.

**Hora**: atardecer permanente en las zonas 1–3 y noche en la 4–5 ✅. La Noche Roja la pisa cada 3 noches.

### 3.12 Dirección de arte

- **Paleta**: grises de ceniza, marrón óxido, verde enfermo y naranja brasa. Acentos dorados en la Ciudadela y verde azulado bioluminiscente en las zonas del Durmiente.
- **Armas**: hierro forjado con mellas, cuero gastado y empuñaduras con tela. Las de jefe, con detalles orgánicos (dientes, hueso, tentáculos).
- **Armaduras**: placas con remaches y abolladuras, capas rotas y yelmos con visera cerrada. Sets de jefe con siluetas reconocibles.
- **Enemigos y jefes**: piel pálida o carbonizada, carne expuesta y proporciones alargadas (por tus referencias de criaturas).
- **Arquitectura**: piedra gótica, ventanales rotos, hierro negro y velas derretidas.
- **HUD** estilo Souls:
  - arriba a la izquierda, tres barras finas: vida (roja), éter (azul) y aguante (verde);
  - abajo a la derecha, el contador de almas;
  - abajo a la izquierda, los slots rápidos;
  - "HAS MUERTO" en rojo y "ENEMIGO FORMIDABLE DERROTADO" en dorado, centrados ✅ (ya existen como títulos).

### 3.13 Cooperativo

- Todos en el mismo mundo, sin fuego amigo ✅ (ya existe).
- **Muro de niebla compartido**: cuando uno entra, los demás tienen 15 s para entrar a la misma pelea ✅.
- La vida del jefe escala con los jugadores dentro de la arena (ver la sección 0) ✅.
- **Almas**: cada uno recibe las suyas por cada enemigo; al vencer a un jefe, todos los participantes reciben el total ✅ (ya existe).
- **Equipamiento de jefe**: cada participante recibe su propia alma, así nadie se queda sin el arma.

---

## 4. Lo que no se puede sin mods (y cómo se aproxima)

| Limitación | Aproximación |
|---|---|
| Animaciones del cuerpo del jugador (rodar, deslizarse, recargar, nuevos golpes) ❌ | Cambio de postura (agachado o cuerpo a tierra), modelo del arma que cambia al recargar o cargar, partículas y sonido |
| Kill cam ❌ | Mensaje con arma, distancia y headshot |
| Mallas realistas y física real ❌ | Modelos de cubos con texturas detalladas; física propia simple para vehículos |
| Lock-on de cámara ⚠️ | Descartado salvo que se sienta bien en prueba |
| Chat de voz ❌ | Discord |
| Luz dinámica (linternas, fogonazos) ❌ | Bloques de luz invisibles que siguen al jugador ⚠️ |
| Niebla real ❌ | Niebla por bioma en el RPG ✅; partículas en Guerra |
| Pasos silenciosos para los demás ❌ | La ventaja "Ninja" se reemplaza por "Rastreador" |
| Cielo o sol distinto por modo ❌ (el resource pack es uno solo) | Se cambia la hora y el clima por mundo |

---

## 5. Lista de assets

Es el volumen real de trabajo. Lo modelo y texturizo yo; conviene revisarlo y retocarlo en Blockbench.

| Modo | Modelos | Otros |
|---|---|---|
| Shooter | 24 armas, ~10 accesorios, 7 equipamientos, paquete de ayuda, misil, perros, ~20 objetos del mapa (autos, colectivo, maniquíes, muebles) | Camuflajes (8 por arma), HUD, ~30 sonidos |
| Guerra | 6 vehículos en piezas (casco, torreta, cañón, ruedas u orugas), 6 armas nuevas (Javelin, Stinger, C4, minas, llave, binoculares), banderas, ~25 objetos del mapa | Uniformes por equipo, HUD de vehículo, ~25 sonidos |
| RPG | 6 armas de clase, 5 de jefe, ~10 comunes, 2 catalizadores; 6 sets de armadura + 3 de jefe; 11 enemigos; 5 jefes animados; NPC y objetos | Texturas de biomas, HUD Souls, ~40 sonidos |

---

## 6. Orden de construcción

1. **Base común**: resource pack con hosting, HUD propio y un primer modelo de arma de punta a punta.
2. **Shooter**: es lo más cercano a lo que ya existe y prueba el sistema de modelos.
3. **RPG**: amplía lo que ya existe (aguante, esquive, hogueras, jefe).
4. **Guerra**: lo más grande (vehículos y mapa); va último, con el sistema de modelos probado.

Cada etapa se compila en GitHub Actions y se prueba en la VM.

## 7. Decisiones pendientes

1. **Época de Guerra**: moderna ("WW3", como tu captura) o Segunda Guerra Mundial (como World of Tanks clásico). Este documento asume la moderna.
2. **Bomba atómica a las 25 bajas**: ¿la dejamos? Con 4 a 6 jugadores es difícil de conseguir, pero define la partida.
3. **Clases del RPG**: ¿te sirven las 6 propuestas o querés cambiar alguna?
