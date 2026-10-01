# server

Server de Minecraft con modos: **TresModos**, un plugin para Paper con un lobby y tres modos de juego.

| Modo | Mundo | Qué es |
|---|---|---|
| Lobby | `tm_lobby` | Plaza con tres portales (hangar de Guerra, contenedor del Shooter y arco con fuego del RPG), carteles con tus estadísticas de cada modo y selector (estrella) |
| Guerra | `tm_guerra` | Valle de Hierro (512 × 512): captura la bandera Azul contra Rojo, 4 clases de infantería, caído y revivir, 7 vehículos (cuatriciclo, jeep, VCI, tanque, antiaéreo, avión y helicóptero) y armas antitanque |
| Shooter | `tm_shooter` | Pueblo Atómico (inspirado en Nuketown): todos contra todos estilo Modern Warfare, clases editables, ventajas, equipamiento, rachas hasta la bomba atómica |
| RPG / Souls | `tm_rpg` | Las Tierras Cenicientas: 6 clases, 7 atributos, árbol de habilidades, combate con parry y postura, 5 zonas con encuentros, 5 jefes con arena, magia, Santuario y Ciclo+ |

El diseño completo de cada modo está en [`docs/DISENO.md`](docs/DISENO.md). Las decisiones que tomé por mi cuenta y lo que falta probar en el juego están en [`dudas_agus.md`](dudas_agus.md).

Versión de destino: **Paper 26.3** (build 140) y **Java 25**. El instalador agrega ViaVersion 5.12.0.

## Estructura

```
src/main/java/ar/tresmodos/   código del plugin (lobby, armas, movilidad, cambio de modo, datos)
  guerra/                     ModoGuerra, vehículos, proyectiles y arsenal antitanque
  shooter/                    ModoShooter, clases, equipamiento, rachas y minimapa
  rpg/                        ModoRpg, combate, clases, árbol, habilidades, magia, zonas, enemigos, jefes
  mundo/                      mapas (Pueblo Atómico y Valle de Hierro) y sus generadores
src/main/resources/           plugin.yml (comandos y permisos) y config.yml
paquete-recursos/             paquete de recursos: modelos, texturas y fuente del HUD
herramientas/                 generador de modelos, texturas y sonidos (armas, equipo, vehículos, RPG, HUD), vista previa y empaquetado
docs/DISENO.md                diseño de los modos
pom.xml                       build de Maven
instalar.sh                   instala Paper + plugin como servicio systemd (Ubuntu 24.04)
mc                            manda comandos a la consola por RCON
apagado-inactivo.sh           desasigna la VM de Azure tras 30 min sin jugadores
referencias/                  imágenes y video de referencia para los próximos cambios
dist/tresmodos-0.1.0.zip      paquete 0.1.0 ya compilado (jar + instalador viejo)
```

## Compilar

GitHub Actions compila el plugin y valida el paquete de recursos en cada push (`.github/workflows/compilar.yml`). En la pestaña **Actions**, la última corrida deja un artefacto `tresmodos` con `TresModos.jar`, `tresmodos-pack.zip` y los tres scripts.

En tu máquina necesitás JDK 25 y Maven:

```bash
mvn package          # genera target/TresModos.jar
```

## Paquete de recursos

Los modelos, texturas y el HUD viven en `paquete-recursos/`. En cada push a `main`, GitHub Actions lo valida, lo empaqueta y lo publica en el release [`pack`](https://github.com/CorreteGil-ar/server/releases/tag/pack). El plugin lo baja al iniciar, calcula su SHA-1 y se lo manda obligatorio a cada jugador que entra.

- La URL está en `plugins/TresModos/config.yml` (`paquete-recursos.url`); con `activo: false` se desactiva.
- Si se publica una versión nueva con el server prendido, `/tm paquete` la vuelve a bajar y se la reenvía a todos.
- Quien rechace el paquete queda afuera con un mensaje que explica cómo activarlo.

Los modelos, texturas y sonidos se generan por código con las herramientas de `herramientas/` (Python 3 con Pillow y numpy; los sonidos además usan ffmpeg con libvorbis):

```bash
pip install -r herramientas/requirements.txt
python3 herramientas/generar.py          # regenera modelos, texturas y glifos del HUD
python3 herramientas/armar_paquete.py    # valida y arma target/tresmodos-pack.zip
python3 herramientas/vista_previa.py tresmodos:item/m4a1_fp fp vista.png   # cómo se ve en primera persona
```

Los archivos que salen de `generar.py` se versionan en el repo; si retocás un modelo en Blockbench, guardalo en `paquete-recursos/` y no vuelvas a correr el generador para esa arma.

## Instalar en una VM de Azure

`instalar.sh` acepta el plugin ya compilado (`TresModos.jar` al lado) o el código fuente (`pom.xml` + `src/`). En el segundo caso instala JDK 25 y Maven y lo compila ahí mismo. Lo más simple es clonar el repo en la VM:

```bash
git clone https://github.com/CorreteGil-ar/server.git
cd server
sudo bash instalar.sh TuUsuario Amigo1 Amigo2
```

- El primer usuario queda como operador y todos entran a la lista blanca. Se pueden agregar después con `mc "whitelist add Nombre"`.
- Abrí solo el puerto **25565/TCP** en el NSG. El RCON (25575) es local: no lo abras.
- Para que el apagado automático funcione, la VM necesita una **identidad administrada** con permiso para desasignarse, por ejemplo el rol *Virtual Machine Contributor* sobre la propia VM. Si no lo tiene, el script lo deja anotado en el log y vuelve a levantar el server.

Para administrarlo:

```bash
mc "list"
journalctl -u minecraft -f
journalctl -t tresmodos        # avisos del apagado automático
```

## Controles por modo

**Shooter y Guerra (infantería)**: clic derecho dispara (mantener = automático), clic izquierdo apunta, Q recarga, F cuchillazo (Shooter), Shift corriendo se desliza, doble Shift cuerpo a tierra, doble W sprint táctico, saltar frente a un muro de 2 trepa.

**Guerra (vehículos)**: clic derecho sobre un vehículo aliado para subir · W/A/S/D manejan · clic derecho mantenido = ametralladora o cañón automático · clic izquierdo = cañón principal, misiles, cohetes o bombas · F cambia munición (AP/HE) o arma secundaria · Q extintor · 1-4 cambia de asiento · Shift 1 s baja (en el avión, eyecta). Avión: W/S potencia y sigue la mirada. Helicóptero: Espacio sube, Ctrl baja.

**RPG**: F voltereta (según la carga), Shift + clic ataque pesado, clic derecho con escudo o katana justo antes del golpe = parry, Q con el arma = habilidad de clase, Shift + Q = definitiva, clic derecho con bastón o talismán = hechizo (Shift + clic cambia). Las hogueras (con la espada clavada) curan, rellenan frascos, suben atributos, abren el árbol y permiten viajar.

## Armas

Veintitrés armas con modelo propio:

| Tipo | Armas |
|---|---|
| Pistolas (secundaria) | Beretta M9, Colt M1911, Desert Eagle, revólver .44 |
| Subfusiles | H&K MP5, HK MP7, FN P90, KRISS Vector |
| Fusiles de asalto | Colt M4A1, AK-47, FN SCAR-H, HK G36K |
| Ametralladoras | M249 SAW, PKM (cajas de 100) |
| Escopetas | Benelli M1014 (semi), Remington 870 (bombeo), AA-12 (automática) |
| Tirador designado | SVD Dragunov, Mk 14 EBR |
| Francotirador | Barrett M82A1, Remington M24 (cerrojo) |
| Secundarias explosivas (Shooter) | M79 (granada de 40 mm, se arma a los 5 bloques), RPG-7 |

En el Shooter se eligen en el editor de clases (`/clase`); en Guerra, cada clase de infantería tiene sus opciones (el Fusilero puede llevar M4A1, AK-47, SCAR-H, M249 o PKM; el Tirador, Barrett, M24, SVD o Mk 14, etc.).

| Control | Acción |
|---|---|
| Clic derecho (mantener) | Disparar; las automáticas a su cadencia real (de 600 disp/min la AK-47 a 1140 la Vector) |
| Clic izquierdo | Apuntar / dejar de apuntar: el arma se centra con la mira de hierro o la óptica alineada con la mira de la pantalla; con ACOG o telescópica se ve el ocular. El zoom depende de la óptica |
| Q | Recargar |

Para apuntar estable al caminar conviene desactivar **Movimiento de la visión** (Opciones → Gráficos): ese balanceo mueve el arma en la mano. El zoom usa **Efectos de FOV** (Opciones → Accesibilidad), que tiene que estar en más de 0 %.

Accesorios (todos desbloqueados, se eligen en `/armero` y quedan guardados por arma): punto rojo, holográfica, ACOG 4x, mira telescópica (fija en la Barrett, la M24 y la SVD), silenciador, empuñadura vertical, láser, linterna y cargador ampliado. Cada arma admite los que tiene en la realidad.

Camuflajes (también en `/armero`): Bosque, Desierto, Urbano, Tigre y Digital se ganan con bajas y tiros a la cabeza de esa arma; Oro, Diamante y Atómico son de desafío (el Atómico pide además haber tirado una bomba atómica).

## Comandos en el juego

| Comando | Para qué |
|---|---|
| `/modo [guerra\|shooter\|rpg\|lobby]` | Abre el selector o cambia de modo directo |
| `/lobby` (`/hub`, `/l`) | Volver al lobby |
| `/clase` | Elegir clase (Guerra, Shooter o RPG) |
| `/armero` (`/accesorios`) | Poner y sacar accesorios al arma (Guerra y Shooter) |
| `/tm <almas\|jefe\|cazador\|guardar\|paquete\|info>` | Administración (solo op). `jefe <id>` lleva a la arena de un jefe del RPG, `cazador` fuerza una invasión, `paquete` recarga el paquete de recursos |
