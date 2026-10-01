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
src/main/resources/plugin.yml comandos y permisos
pom.xml                       build de Maven
instalar.sh                   instala Paper + plugin como servicio systemd (Ubuntu 24.04)
mc                            manda comandos a la consola por RCON
apagado-inactivo.sh           desasigna la VM de Azure tras 30 min sin jugadores
referencias/                  imágenes y video de referencia para los próximos cambios
dist/tresmodos-0.1.0.zip      paquete 0.1.0 ya compilado (jar + instalador viejo)
```

## Compilar

GitHub Actions compila el plugin en cada push (`.github/workflows/compilar.yml`). En la pestaña **Actions**, la última corrida deja un artefacto `tresmodos` con `TresModos.jar` y los tres scripts.

En tu máquina necesitás JDK 25 y Maven:

```bash
mvn package          # genera target/TresModos.jar
```

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

## Comandos en el juego

| Comando | Para qué |
|---|---|
| `/modo [gta\|cod\|rpg\|lobby]` | Abre el selector o cambia de modo directo |
| `/lobby` (`/hub`, `/l`) | Volver al lobby |
| `/clase` | Elegir clase en COD |
| `/celular` (`/cel`, `/tel`) | Celular de GTA: armería, concesionaria, misiones, soborno |
| `/tm <dinero\|almas\|buscado\|jefe\|guardar\|info>` | Administración (solo op) |
