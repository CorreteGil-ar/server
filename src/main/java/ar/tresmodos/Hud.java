package ar.tresmodos;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.entity.Player;

import java.time.Duration;

/** Elementos del HUD dibujados con la fuente tresmodos:hud del paquete de recursos. */
public final class Hud {
    public static final Key FUENTE = Key.key("tresmodos", "hud");
    private static final String MARCADOR = "";
    private static final String MARCADOR_BAJA = "";
    /** Ícono de bala para usar dentro de textos MiniMessage. */
    public static final String ICONO_BALA = "<font:tresmodos:hud></font>";

    private static final Title.Times TIEMPOS_MARCADOR =
            Title.Times.times(Duration.ZERO, Duration.ofMillis(150), Duration.ofMillis(120));

    private Hud() {}

    /**
     * Marcador de impacto sobre la mira: blanco al pegar, rojo al matar.
     * Solo cambia la parte del título, así no borra subtítulos como los de las rachas.
     */
    public static void marcador(Player p, boolean baja) {
        p.sendTitlePart(TitlePart.TIMES, TIEMPOS_MARCADOR);
        p.sendTitlePart(TitlePart.TITLE, Component.text(baja ? MARCADOR_BAJA : MARCADOR).font(FUENTE));
    }
}
