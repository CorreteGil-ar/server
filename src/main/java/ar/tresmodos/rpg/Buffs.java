package ar.tresmodos.rpg;

import org.bukkit.Bukkit;
import org.bukkit.entity.LivingEntity;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Efectos temporales de las habilidades sobre jugadores y enemigos (daño extra, defensa, inmortal,
 * ventana de parry, aguante infinito, marcas). El combate y el aguante los consultan.
 */
public class Buffs {

    public enum Tipo {
        /** Multiplica el daño que hace (valor = multiplicador). */
        DANIO,
        /** Multiplica el daño mágico que hace. */
        DANIO_MAGICO,
        /** Multiplica el daño que recibe (valor < 1 = defensa). */
        DEFENSA,
        /** Los golpes no lo interrumpen ni le rompen la postura. */
        FIRME,
        /** No puede bajar de 1 de vida. */
        INMORTAL,
        /** Multiplica la ventana de parry. */
        PARRY,
        /** El próximo golpe recibido se devuelve como parry perfecto. */
        CONTRAATAQUE,
        /** No gasta aguante. */
        AGUANTE_INFINITO,
        /** Bloqueo total al frente; devuelve parte del daño (valor = fracción devuelta). */
        BASTION,
        /** Escudo que absorbe daño (valor = vida del escudo). */
        ESCUDO,
        /** Cada golpe suelta un rayo. */
        TORMENTA,
        /** Enemigo marcado: recibe más daño y cura a quien le pega (valor = multiplicador). */
        MARCA,
        /** Invulnerable y atravesable. */
        FASE,
        /** Volteretas con el doble de invulnerabilidad y señuelo. */
        INSTINTO,
        /** Envenenados contagian a los de al lado. */
        PESTILENCIA,
        /** Casi quieto (Tiempo detenido). */
        DETENIDO
    }

    private static final class Efecto {
        double valor;
        int hasta;

        Efecto(double valor, int hasta) {
            this.valor = valor;
            this.hasta = hasta;
        }
    }

    private final Map<UUID, EnumMap<Tipo, Efecto>> efectos = new HashMap<>();

    public void dar(LivingEntity e, Tipo t, double valor, int ticks) {
        int hasta = Bukkit.getCurrentTick() + ticks;
        efectos.computeIfAbsent(e.getUniqueId(), u -> new EnumMap<>(Tipo.class)).put(t, new Efecto(valor, hasta));
    }

    public boolean tiene(LivingEntity e, Tipo t) {
        return !Double.isNaN(valor(e, t, Double.NaN));
    }

    /** Valor del efecto activo, o el valor por defecto si no lo tiene. */
    public double valor(LivingEntity e, Tipo t, double defecto) {
        EnumMap<Tipo, Efecto> m = efectos.get(e.getUniqueId());
        if (m == null) return defecto;
        Efecto ef = m.get(t);
        if (ef == null) return defecto;
        if (ef.hasta < Bukkit.getCurrentTick()) {
            m.remove(t);
            return defecto;
        }
        return ef.valor;
    }

    /** Resta vida al escudo; devuelve el daño que lo atraviesa. */
    public double absorber(LivingEntity e, double danio) {
        EnumMap<Tipo, Efecto> m = efectos.get(e.getUniqueId());
        if (m == null) return danio;
        Efecto ef = m.get(Tipo.ESCUDO);
        if (ef == null || ef.hasta < Bukkit.getCurrentTick()) return danio;
        double absorbido = Math.min(ef.valor, danio);
        ef.valor -= absorbido;
        if (ef.valor <= 0.01) m.remove(Tipo.ESCUDO);
        return danio - absorbido;
    }

    public void quitar(LivingEntity e, Tipo t) {
        EnumMap<Tipo, Efecto> m = efectos.get(e.getUniqueId());
        if (m != null) m.remove(t);
    }

    /** Entidades con un efecto activo (para recorrerlas en los ciclos). */
    public List<UUID> con(Tipo t) {
        List<UUID> l = new ArrayList<>();
        int ahora = Bukkit.getCurrentTick();
        for (Map.Entry<UUID, EnumMap<Tipo, Efecto>> en : efectos.entrySet()) {
            Efecto ef = en.getValue().get(t);
            if (ef != null && ef.hasta >= ahora) l.add(en.getKey());
        }
        return l;
    }

    public void olvidar(UUID id) {
        efectos.remove(id);
    }
}
