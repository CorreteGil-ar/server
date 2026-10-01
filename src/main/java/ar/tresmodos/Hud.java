package ar.tresmodos;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.ShadowColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.title.Title;
import net.kyori.adventure.title.TitlePart;
import org.bukkit.entity.Player;

import java.text.Normalizer;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Elementos del HUD dibujados con la fuente tresmodos:hud del paquete de recursos. */
public final class Hud {
    public static final Key FUENTE = Key.key("tresmodos", "hud");
    private static final String MARCADOR = "";
    private static final String MARCADOR_BAJA = "";
    /** Cuadro blanco que tapa toda la pantalla (destello de la bomba atómica). */
    private static final String DESTELLO = "\uE020";
    /** Ícono de bala para usar dentro de textos MiniMessage. */
    public static final String ICONO_BALA = "<font:tresmodos:hud></font>";

    private static final Title.Times TIEMPOS_MARCADOR =
            Title.Times.times(Duration.ZERO, Duration.ofMillis(150), Duration.ofMillis(120));

    private Hud() {}

    /**
     * Marcador de impacto sobre la mira: blanco al pegar, rojo al matar.
     * Solo cambia la parte del título, así no borra subtítulos como los de las rachas.
     */
    /** Pantalla en blanco que se desvanece en 3 s. */
    public static void destello(Player p) {
        p.sendTitlePart(TitlePart.TIMES, Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(2600)));
        p.sendTitlePart(TitlePart.SUBTITLE, Component.empty());
        p.sendTitlePart(TitlePart.TITLE, Component.text(DESTELLO).font(FUENTE));
    }

    public static void marcador(Player p, boolean baja) {
        p.sendTitlePart(TitlePart.TIMES, TIEMPOS_MARCADOR);
        p.sendTitlePart(TitlePart.TITLE, Component.text(baja ? MARCADOR_BAJA : MARCADOR).font(FUENTE));
    }

    // ------------------------------------------------------------------ paneles

    /*
     * Los paneles (barras del RPG, tablero de los vehículos) se dibujan en el título de una barra
     * de jefe blanca, que el paquete deja transparente. Tres renglones (fuentes fila0, fila1 y
     * fila2, cada una 7 px más abajo) con letra chica de 3×5 y barras armadas con piezas de 2^i px.
     * Ver herramientas/hud.py.
     */

    /** Colores de las piezas de barra (mismo orden que COLORES_BARRA en hud.py). */
    public enum ColorBarra { ROJO, VERDE, AZUL, AMARILLO, NARANJA, BLANCO, VACIO, BORDE, GRIS }

    private static final Key[] FILAS = {Key.key("tresmodos", "fila0"), Key.key("tresmodos", "fila1"),
            Key.key("tresmodos", "fila2")};
    private static final char ATRAS_1 = '\uE200';
    private static final String LETRAS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ/%.:!-+()?* ";

    /** Texto armado de un panel, con su avance en píxeles (para alinear renglones). */
    public record Pieza(Component texto, int ancho) {
        public Pieza mas(Pieza otra) {
            return new Pieza(texto.append(otra.texto), ancho + otra.ancho);
        }
    }

    private static String piezas(ColorBarra c, int largo) {
        StringBuilder sb = new StringBuilder();
        for (int i = 7; i >= 0; i--) {
            int s = 1 << i;
            while (largo >= s) {
                sb.append((char) (0xE100 + 16 * c.ordinal() + i)).append(ATRAS_1);
                largo -= s;
            }
        }
        return sb.toString();
    }

    /** Caracteres de espacio que avanzan (o retroceden, si es negativo) {@code px} píxeles. */
    private static String espacios(int px) {
        StringBuilder sb = new StringBuilder();
        int base = px < 0 ? 0xE200 : 0xE210;
        int n = Math.abs(px);
        for (int i = 9; i >= 0; i--) {
            while (n >= (1 << i)) {
                sb.append((char) (base + i));
                n -= 1 << i;
            }
        }
        return sb.toString();
    }

    public static Pieza espacio(int px) {
        return new Pieza(Component.text(espacios(px)).font(FILAS[0]), px);
    }

    /**
     * Barra de {@code largo} px: lleno del color, el daño reciente en blanco y el resto vacío.
     * Mide largo + 2 (los bordes).
     */
    public static Pieza barra(int fila, ColorBarra color, double frac, double reciente, int largo) {
        int lleno = (int) Math.round(Math.max(0, Math.min(1, frac)) * largo);
        int blanco = (int) Math.round(Math.max(0, Math.min(1 - (double) lleno / largo, reciente)) * largo);
        String borde = piezas(ColorBarra.BORDE, 1);
        String t = borde + piezas(color, lleno) + piezas(ColorBarra.BLANCO, blanco)
                + piezas(ColorBarra.VACIO, largo - lleno - blanco) + borde;
        return new Pieza(Component.text(t).font(FILAS[fila]).shadowColor(ShadowColor.none()), largo + 2);
    }

    /** Letra chica (mayúsculas sin tildes; lo que no está se vuelve espacio). */
    public static Pieza texto(int fila, String s, TextColor color) {
        String t = Normalizer.normalize(s.toUpperCase(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        StringBuilder sb = new StringBuilder();
        int ancho = 0;
        for (char c : t.toCharArray()) {
            if (c == '·') c = '.';
            if (LETRAS.indexOf(c) < 0) c = ' ';
            sb.append(c);
            ancho += switch (c) {
                case 'M', 'W' -> 6;
                case 'N' -> 5;
                case '.', ':', '!' -> 2;
                case '(', ')', ' ' -> 3;
                default -> 4;
            };
        }
        return new Pieza(Component.text(sb.toString()).font(FILAS[fila]).color(color), ancho);
    }

    public static Pieza texto(int fila, String s) {
        return texto(fila, s, NamedTextColor.WHITE);
    }

    /**
     * Junta renglones alineados a la izquierda: cada uno vuelve al principio con un espacio
     * negativo y al final se avanza el más largo (así la barra de jefe lo centra bien).
     */
    public static Component renglones(Pieza... filas) {
        TextComponent.Builder b = Component.text();
        int max = 0;
        for (Pieza f : filas) {
            if (f == null) continue;
            b.append(f.texto).append(espacio(-f.ancho).texto);
            max = Math.max(max, f.ancho);
        }
        return b.append(espacio(max).texto).build();
    }

    /** Panel por jugador: una barra de jefe blanca (invisible) cuyo título es el HUD. */
    public static final class Panel {
        private final Map<UUID, BossBar> barras = new HashMap<>();

        public void mostrar(Player p, Component contenido) {
            BossBar b = barras.get(p.getUniqueId());
            if (b == null) {
                b = BossBar.bossBar(contenido, 0f, BossBar.Color.WHITE, BossBar.Overlay.PROGRESS);
                barras.put(p.getUniqueId(), b);
                p.showBossBar(b);
            } else {
                b.name(contenido);
            }
        }

        public boolean visible(Player p) {
            return barras.containsKey(p.getUniqueId());
        }

        public void ocultar(Player p) {
            BossBar b = barras.remove(p.getUniqueId());
            if (b != null) p.hideBossBar(b);
        }

        /** Para quien se desconectó (el cliente ya no tiene la barra). */
        public void olvidar(UUID id) {
            barras.remove(id);
        }

        public void ocultarTodos() {
            for (Map.Entry<UUID, BossBar> en : barras.entrySet()) {
                Player p = org.bukkit.Bukkit.getPlayer(en.getKey());
                if (p != null) p.hideBossBar(en.getValue());
            }
            barras.clear();
        }

        public Iterable<UUID> jugadores() {
            return new java.util.ArrayList<>(barras.keySet());
        }
    }
}
