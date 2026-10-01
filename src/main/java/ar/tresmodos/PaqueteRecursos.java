package ar.tresmodos;

import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Paquete de recursos obligatorio (modelos, texturas y HUD).
 *
 * <p>Al iniciar lo descarga desde la URL de config.yml (el release "pack" que publica GitHub Actions),
 * calcula su SHA-1 y se lo manda a cada jugador que entra. Si se publica una versión nueva con el
 * server prendido, {@code /tm paquete} la vuelve a bajar y se la reenvía a todos.
 */
public class PaqueteRecursos implements Listener {
    private static final UUID ID = UUID.nameUUIDFromBytes("tresmodos:paquete".getBytes(StandardCharsets.UTF_8));
    private static final long MAX_BYTES = 100L * 1024 * 1024;

    private final TresModos plugin;
    private volatile ResourcePackInfo info;

    public PaqueteRecursos(TresModos plugin) {
        this.plugin = plugin;
    }

    private boolean activo() {
        return plugin.getConfig().getBoolean("paquete-recursos.activo", true);
    }

    /** Descarga el paquete en segundo plano y, cuando termina, se lo manda a los que ya están conectados. */
    public void cargar() {
        if (!activo()) {
            plugin.getLogger().info("Paquete de recursos desactivado en config.yml.");
            return;
        }
        String url = plugin.getConfig().getString("paquete-recursos.url", "");
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                byte[] datos = descargar(url);
                String sha1 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(datos));
                info = ResourcePackInfo.resourcePackInfo(ID, URI.create(url), sha1);
                plugin.getLogger().info("Paquete de recursos listo: " + datos.length / 1024 + " KB, sha1 " + sha1);
                Bukkit.getScheduler().runTask(plugin, () -> Bukkit.getOnlinePlayers().forEach(this::enviar));
            } catch (IOException | NoSuchAlgorithmException | IllegalArgumentException e) {
                plugin.getLogger().warning("No pude bajar el paquete de recursos de " + url + ": " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private static byte[] descargar(String url) throws IOException, InterruptedException {
        HttpClient cliente = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        HttpRequest pedido = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(2)).GET().build();
        HttpResponse<byte[]> resp = cliente.send(pedido, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() != 200) throw new IOException("respuesta HTTP " + resp.statusCode());
        if (resp.body().length > MAX_BYTES) throw new IOException("pesa más de 100 MB");
        return resp.body();
    }

    public void enviar(Player p) {
        ResourcePackInfo actual = info;
        if (actual == null) return;
        p.sendResourcePacks(ResourcePackRequest.resourcePackRequest()
                .packs(actual)
                .required(true)
                .replace(true)
                .prompt(Util.mm(plugin.getConfig().getString("paquete-recursos.mensaje",
                        "<gold>TresModos</gold> <gray>usa modelos y texturas propios.")))
                .build());
    }

    @EventHandler
    public void alEntrar(PlayerJoinEvent e) {
        enviar(e.getPlayer());
    }

    @EventHandler
    public void alResponder(PlayerResourcePackStatusEvent e) {
        Player p = e.getPlayer();
        switch (e.getStatus()) {
            case DECLINED -> p.kick(Util.mm("<red>Para jugar en TresModos hay que aceptar el paquete de recursos.\n"
                    + "<gray>Si lo rechazaste sin querer: en la lista de servidores, Editar → "
                    + "Paquetes de recursos: Activados."));
            case FAILED_DOWNLOAD, INVALID_URL, FAILED_RELOAD -> {
                plugin.getLogger().warning(p.getName() + " no pudo cargar el paquete de recursos: " + e.getStatus());
                Util.msg(p, "<red>No se pudo cargar el paquete de recursos (" + e.getStatus()
                        + "). <gray>Vas a ver las armas sin modelo. Avisale al admin.");
            }
            default -> { }
        }
    }
}
