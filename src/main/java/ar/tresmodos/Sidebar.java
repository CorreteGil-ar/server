package ar.tresmodos;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Barra lateral por jugador, con el contenido que define cada modo. */
public class Sidebar {
    private static final int MAX = 14;
    private final TresModos plugin;
    private final Map<UUID, Scoreboard> tableros = new HashMap<>();

    public Sidebar(TresModos plugin) {
        this.plugin = plugin;
    }

    public void actualizarTodos() {
        for (Player p : Bukkit.getOnlinePlayers()) actualizar(p);
    }

    public void actualizar(Player p) {
        Modo m = Modo.de(p.getWorld());
        if (m == null) return;
        ModoJuego juego = plugin.juego(m);
        Scoreboard sb = tableros.computeIfAbsent(p.getUniqueId(), u -> Bukkit.getScoreboardManager().getNewScoreboard());
        if (p.getScoreboard() != sb) p.setScoreboard(sb);

        Objective obj = sb.getObjective("tm");
        if (obj == null) {
            obj = sb.registerNewObjective("tm", Criteria.DUMMY, Util.mm(juego.tituloSidebar(p)));
            obj.numberFormat(io.papermc.paper.scoreboard.numbers.NumberFormat.blank());
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            obj.displayName(Util.mm(juego.tituloSidebar(p)));
        }

        List<String> lineas = juego.lineasSidebar(p);
        int n = Math.min(MAX, lineas.size());
        for (int i = 0; i < MAX; i++) {
            String entrada = "§" + Integer.toHexString(i) + "§r";
            if (i >= n) {
                sb.resetScores(entrada);
                continue;
            }
            Score s = obj.getScore(entrada);
            s.setScore(n - i);
            s.customName(Util.mm(lineas.get(i).isEmpty() ? " " : lineas.get(i)));
        }
    }

    public void olvidar(Player p) {
        tableros.remove(p.getUniqueId());
    }
}
