# server

Server de Minecraft con modos: **TresModos**, un plugin para Paper con un lobby y tres modos de juego.

| Modo | Mundo | Qué es |
|---|---|---|
| Lobby | `tm_lobby` | Plataforma con placas para entrar a cada modo y selector (estrella) |
| GTA | `tm_gta` | Ciudad generada, dinero, armería, autos (caballos), policía con 5 estrellas, misiones de entrega |
| COD | `tm_cod` | Arena todos contra todos, 4 clases, granadas, rachas (UAV, bombardeo), primero a 30 bajas |
| RPG / Souls | `tm_rpg` | Aguante, esquive con F, hogueras, Estus, almas que se pierden al morir, jefe con dos fases |

Versión de destino: **Paper 26.3** (build 140) y **Java 25**. El instalador agrega ViaVersion 5.12.0.

## Estructura

```
src/main/java/ar/tresmodos/   código del plugin
  modos/                      ModoGta, ModoCod, ModoRpg
  mundo/                      generadores de la ciudad y la arena
src/main/resources/           plugin.yml (comandos y permisos) y config.yml
paquete-recursos/             paquete de recursos: modelos, texturas y fuente del HUD
herramientas/                 generador de modelos y texturas, vista previa y empaquetado del paquete
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

Los modelos y texturas se generan por código con las herramientas de `herramientas/` (Python 3 con Pillow y numpy):

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

## Armas

Cinco armas con modelo propio: Beretta M9, H&K MP5, Colt M4A1, Benelli M1014 y Barrett M82A1.

| Control | Acción |
|---|---|
| Clic derecho (mantener) | Disparar; M4A1 y MP5 en automático a 800 disp/min |
| Clic izquierdo | Apuntar / dejar de apuntar: el arma se centra con la mira de hierro o la óptica alineada con la mira de la pantalla; con ACOG o telescópica se ve el ocular. El zoom depende de la óptica |
| Q | Recargar |

Para apuntar estable al caminar conviene desactivar **Movimiento de la visión** (Opciones → Gráficos): ese balanceo mueve el arma en la mano. El zoom usa **Efectos de FOV** (Opciones → Accesibilidad), que tiene que estar en más de 0 %.

Accesorios (todos desbloqueados, se eligen en `/armero` y quedan guardados por arma): punto rojo, holográfica, ACOG 4x, mira telescópica (fija en la Barrett), silenciador, empuñadura vertical, láser, linterna y cargador ampliado. Cada arma admite los que tiene en la realidad.

## Comandos en el juego

| Comando | Para qué |
|---|---|
| `/modo [gta\|cod\|rpg\|lobby]` | Abre el selector o cambia de modo directo |
| `/lobby` (`/hub`, `/l`) | Volver al lobby |
| `/clase` | Elegir clase en COD |
| `/celular` (`/cel`, `/tel`) | Celular de GTA: armería, armero, concesionaria, misiones, soborno |
| `/armero` (`/accesorios`) | Poner y sacar accesorios al arma (todos desbloqueados) |
| `/tm <dinero\|almas\|buscado\|jefe\|guardar\|paquete\|info>` | Administración (solo op). `paquete` recarga el paquete de recursos |
