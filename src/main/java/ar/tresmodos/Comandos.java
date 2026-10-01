package ar.tresmodos;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class Comandos implements CommandExecutor, TabCompleter {
    private final TresModos plugin;

    public Comandos(TresModos plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        String nombre = cmd.getName().toLowerCase();
        if (nombre.equals("tm")) return admin(s, args);
        if (!(s instanceof Player p)) {
            s.sendMessage("Solo jugadores.");
            return true;
        }
        switch (nombre) {
            case "modo" -> {
                if (args.length == 0) {
                    plugin.lobby().abrirMenu(p);
                } else {
                    Modo m = Modo.parse(args[0]);
                    if (m == null) Util.msg(p, "<red>Modos: gta, cod, rpg, lobby");
                    else plugin.cambio().cambiar(p, m, false);
                }
            }
            case "lobby" -> plugin.cambio().cambiar(p, Modo.LOBBY, false);
            case "clase" -> {
                if (Modo.de(p.getWorld()) == Modo.COD) plugin.cod().abrirMenuClases(p);
                else Util.msg(p, "<red>Las clases son del modo COD.");
            }
            case "celular" -> {
                if (Modo.de(p.getWorld()) == Modo.GTA) plugin.gta().abrirCelular(p);
                else Util.msg(p, "<red>El celular es del modo GTA.");
            }
            case "armero" -> {
                Modo m = Modo.de(p.getWorld());
                if (m == Modo.GTA || m == Modo.COD) plugin.armero().abrir(p);
                else Util.msg(p, "<red>El armero se usa en los modos con armas (GTA y COD).");
            }
            default -> { return false; }
        }
        return true;
    }

    private boolean admin(CommandSender s, String[] args) {
        if (args.length == 0) {
            s.sendMessage(Util.mm("<gold>/tm dinero <jugador> <monto> · /tm almas <jugador> <monto> · "
                    + "/tm buscado <jugador> <0-5> · /tm jefe · /tm guardar · /tm paquete · /tm info"));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "dinero", "almas" -> {
                if (args.length < 3) return uso(s, "/tm " + args[0] + " <jugador> <monto>");
                Player obj = Bukkit.getPlayerExact(args[1]);
                if (obj == null) return uso(s, "Jugador no conectado.");
                long monto;
                try {
                    monto = Long.parseLong(args[2]);
                } catch (NumberFormatException e) {
                    return uso(s, "Monto inválido.");
                }
                DatosJugador d = plugin.almacen().de(obj);
                if (args[0].equalsIgnoreCase("dinero")) d.dinero = Math.max(0, d.dinero + monto);
                else d.almas = Math.max(0, d.almas + monto);
                s.sendMessage(Util.mm("<green>Listo. " + obj.getName() + ": " + Util.plata(d.dinero) + " · " + d.almas + " almas"));
            }
            case "buscado" -> {
                if (args.length < 3) return uso(s, "/tm buscado <jugador> <0-5>");
                Player obj = Bukkit.getPlayerExact(args[1]);
                if (obj == null) return uso(s, "Jugador no conectado.");
                try {
                    plugin.gta().setBuscado(obj, Integer.parseInt(args[2]));
                } catch (NumberFormatException e) {
                    return uso(s, "Número inválido.");
                }
                s.sendMessage(Util.mm("<green>Nivel de búsqueda actualizado."));
            }
            case "jefe" -> {
                if (!(s instanceof Player p) || Modo.de(p.getWorld()) != Modo.RPG) return uso(s, "Usalo dentro del RPG.");
                plugin.rpg().invocarJefe(p);
            }
            case "guardar" -> {
                plugin.almacen().guardarTodo();
                s.sendMessage(Util.mm("<green>Datos guardados."));
            }
            case "paquete" -> {
                plugin.reloadConfig();
                plugin.paquete().cargar();
                s.sendMessage(Util.mm("<green>Bajando el paquete de recursos; se lo reenvío a todos cuando termine."));
            }
            case "info" -> {
                for (Modo m : Modo.values()) {
                    s.sendMessage(Util.mm(m.titulo + " <gray>mundo " + m.mundo + " · "
                            + plugin.mundos().de(m).getPlayerCount() + " jugadores"));
                }
            }
            default -> {
                return uso(s, "Subcomando desconocido.");
            }
        }
        return true;
    }

    private boolean uso(CommandSender s, String texto) {
        s.sendMessage(Util.mm("<red>" + texto));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] args) {
        List<String> l = new ArrayList<>();
        String nombre = cmd.getName().toLowerCase();
        if (nombre.equals("modo") && args.length == 1) l.addAll(List.of("gta", "cod", "rpg", "lobby"));
        if (nombre.equals("tm")) {
            if (args.length == 1) l.addAll(List.of("dinero", "almas", "buscado", "jefe", "guardar", "paquete", "info"));
            else if (args.length == 2) for (Player p : Bukkit.getOnlinePlayers()) l.add(p.getName());
        }
        String pref = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
        l.removeIf(x -> !x.toLowerCase().startsWith(pref));
        return l;
    }
}
