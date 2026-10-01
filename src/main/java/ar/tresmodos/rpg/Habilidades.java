package ar.tresmodos.rpg;

import ar.tresmodos.Claves;
import ar.tresmodos.DatosJugador;
import ar.tresmodos.Modo;
import ar.tresmodos.TresModos;
import ar.tresmodos.Util;
import ar.tresmodos.rpg.ClaseRpg.TipoDanio;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Habilidades de clase: Q (habilidad del arma, cambia con la rama elegida en el árbol) y Shift+Q
 * (definitiva de la rama a nivel 5). Gastan éter; la Q tiene 6 s de espera y la definitiva 90 s.
 */
public class Habilidades implements Listener {

    /** Variante de habilidad: la base o la de una rama (la de Daño depende del tipo elegido). */
    public enum Variante { BASE, DANIO_A, DANIO_B, VIDA, BENDICION }

    public record Def(String nombre, String desc, int eter) {}

    private static final Map<String, Def> Q = new HashMap<>();
    private static final Map<String, Def> ULT = new HashMap<>();

    private static void q(ClaseRpg c, Variante v, String nombre, String desc, int eter) {
        Q.put(c.name() + v.name(), new Def(nombre, desc, eter));
    }

    private static void u(ClaseRpg c, Variante v, String nombre, String desc) {
        ULT.put(c.name() + v.name(), new Def(nombre, desc, 40));
    }

    static {
        ClaseRpg c = ClaseRpg.CABALLERO;
        q(c, Variante.BASE, "Tajo ascendente", "Lanza al enemigo hacia arriba.", 12);
        q(c, Variante.DANIO_A, "Tajo llameante", "Deja una estela de fuego que quema 4 s.", 20);
        q(c, Variante.DANIO_B, "Tajo de tormenta", "Un rayo cae sobre el enemigo y salta a otro.", 20);
        q(c, Variante.VIDA, "Bastión", "3 s de bloqueo total al frente; devuelve el 30 %.", 15);
        q(c, Variante.BENDICION, "Estandarte", "+15 % de daño y regeneración a los aliados por 12 s.", 25);
        u(c, Variante.DANIO_A, "Juicio", "Círculo de llamas de 6 bloques.");
        u(c, Variante.DANIO_B, "Cólera del cielo", "5 rayos sobre los enemigos más cercanos.");
        u(c, Variante.VIDA, "Muralla", "8 s con −60 % de daño, sin perder postura; te atacan a vos.");
        u(c, Variante.BENDICION, "Última luz", "5 s en los que ningún aliado cercano puede morir.");
        c = ClaseRpg.VERDUGO;
        q(c, Variante.BASE, "Grito de guerra", "+20 % de daño propio y de aliados por 15 s.", 15);
        q(c, Variante.DANIO_A, "Desgarro", "Hachazo giratorio que llena el sangrado alrededor.", 22);
        q(c, Variante.DANIO_B, "Terremoto", "Golpe al piso que derriba y rompe postura.", 22);
        q(c, Variante.VIDA, "Piel de hierro", "8 s sin interrupciones y −30 % de daño.", 18);
        q(c, Variante.BENDICION, "Grito de la horda", "El grito además cura un 15 % a los aliados.", 25);
        u(c, Variante.DANIO_A, "Ejecución", "Salto y hachazo que remata a los heridos.");
        u(c, Variante.DANIO_B, "Cataclismo", "Tres golpes al piso que avanzan en línea.");
        u(c, Variante.VIDA, "Inmortal", "6 s sin poder morir; al final recuperás el 30 %.");
        u(c, Variante.BENDICION, "Canto de guerra", "10 s de aguante infinito para el grupo.");
        c = ClaseRpg.RONIN;
        q(c, Variante.BASE, "Desenvaine", "Tajo instantáneo a 4 bloques.", 12);
        q(c, Variante.DANIO_A, "Corte carmesí", "Llena medio sangrado y deja una herida.", 20);
        q(c, Variante.DANIO_B, "Relámpago", "Atravesás 8 bloques dañando todo a tu paso.", 22);
        q(c, Variante.VIDA, "Postura del agua", "El próximo golpe se devuelve como parry perfecto.", 15);
        q(c, Variante.BENDICION, "Meditación", "Te arrodillás y curás vida y aguante cerca.", 20);
        u(c, Variante.DANIO_A, "Mil cortes", "8 tajos saltando entre los enemigos.");
        u(c, Variante.DANIO_B, "Tormenta de acero", "6 s en los que cada golpe suelta un rayo.");
        u(c, Variante.VIDA, "Espíritu inquebrantable", "8 s con la ventana de parry triplicada.");
        u(c, Variante.BENDICION, "Camino del guerrero", "Parry doble y +20 % de daño al grupo.");
        c = ClaseRpg.CAZADOR;
        q(c, Variante.BASE, "Lluvia de flechas", "Flechas en un área de 5 bloques.", 14);
        q(c, Variante.DANIO_A, "Jeringa sangrienta", "Le sacás sangre 4 s y te curás la mitad.", 22);
        q(c, Variante.DANIO_B, "Flecha de la plaga", "Nube de veneno de 4 bloques por 6 s.", 20);
        q(c, Variante.VIDA, "Trampa de hierro", "Cepo que inmoviliza 3 s al primero que lo pisa.", 15);
        q(c, Variante.BENDICION, "Compañero lobo", "Un lobo pelea a tu lado y aúlla para curar.", 25);
        u(c, Variante.DANIO_A, "Cacería", "Marcás a un enemigo: +40 % de daño y cura al que le pega.");
        u(c, Variante.DANIO_B, "Pestilencia", "10 s en los que el veneno se contagia.");
        u(c, Variante.VIDA, "Instinto", "6 s de volteretas con doble invulnerabilidad.");
        u(c, Variante.BENDICION, "Manada", "Tres lobos durante 15 s.");
        c = ClaseRpg.HECHICERO;
        q(c, Variante.BASE, "Orbe gravitatorio", "Atrae y aplasta a los enemigos.", 15);
        q(c, Variante.DANIO_A, "Colapso", "El orbe atrae y explota al final.", 25);
        q(c, Variante.DANIO_B, "Prisión de escarcha", "Congela a los enemigos en 4 bloques por 2 s.", 22);
        q(c, Variante.VIDA, "Barrera de cristal", "Escudo que absorbe el 40 % de tu vida máxima.", 20);
        q(c, Variante.BENDICION, "Glifo", "Círculo: +25 % de daño mágico y éter a los aliados.", 25);
        u(c, Variante.DANIO_A, "Lluvia de estrellas", "10 meteoros del vacío en 10 bloques.");
        u(c, Variante.DANIO_B, "Invierno eterno", "Tormenta de hielo de 8 s que frena y congela.");
        u(c, Variante.VIDA, "Fase", "3 s intangible y un salto de 10 bloques.");
        u(c, Variante.BENDICION, "Tiempo detenido", "Los enemigos en 10 bloques quedan quietos 5 s.");
        c = ClaseRpg.CLERIGO;
        q(c, Variante.BASE, "Llamarada sagrada", "Quema a los enemigos alrededor.", 14);
        q(c, Variante.DANIO_A, "Martillo solar", "Golpe de maza que explota en fuego.", 22);
        q(c, Variante.DANIO_B, "Lanza del cielo", "Lanza de rayo a distancia que aturde.", 22);
        q(c, Variante.VIDA, "Égida", "Escudo sobre un aliado por el 30 % de su vida.", 20);
        q(c, Variante.BENDICION, "Plegaria", "Cura el 35 % a todo el grupo cercano.", 28);
        u(c, Variante.DANIO_A, "Sol negro", "Área de 8 bloques que quema 6 s.");
        u(c, Variante.DANIO_B, "Juicio celestial", "Rayo continuo de 4 s sobre el enemigo apuntado.");
        u(c, Variante.VIDA, "Santuario", "Cúpula de 8 s: los aliados reciben −50 % de daño.");
        u(c, Variante.BENDICION, "Resurrección", "Revive al último aliado caído donde cayó.");
    }

    public static Def defQ(ClaseRpg c, Variante v) {
        return Q.get(c.name() + v.name());
    }

    public static Def defUlt(ClaseRpg c, Variante v) {
        return ULT.get(c.name() + v.name());
    }

    /** Variante de la rama Daño según el tipo elegido. */
    public static Variante varianteDanio(ClaseRpg c, String tipo) {
        if (tipo == null) return Variante.DANIO_A;
        return c.tipos.indexOf(TipoDanio.valueOf(tipo)) == 1 ? Variante.DANIO_B : Variante.DANIO_A;
    }

    public static Variante variante(ClaseRpg c, Rama r, String tipo) {
        return switch (r) {
            case DANIO -> varianteDanio(c, tipo);
            case VIDA -> Variante.VIDA;
            case BENDICION -> Variante.BENDICION;
        };
    }

    // ------------------------------------------------------------------ uso

    private final TresModos plugin;
    private final ModoRpg modo;
    private final Map<UUID, Integer> esperaQ = new HashMap<>();
    private final Map<UUID, Integer> esperaUlt = new HashMap<>();
    private final List<UUID> invocaciones = new ArrayList<>();

    public Habilidades(TresModos plugin, ModoRpg modo) {
        this.plugin = plugin;
        this.modo = modo;
    }

    /** Q con el arma en la mano (y Shift+Q para la definitiva). El arma no se tira. */
    @EventHandler(priority = EventPriority.HIGH)
    public void alTirar(PlayerDropItemEvent e) {
        Player p = e.getPlayer();
        if (Modo.de(p.getWorld()) != Modo.RPG) return;
        ItemStack it = e.getItemDrop().getItemStack();
        if (ArmaRpg.de(it) == null) return;
        e.setCancelled(true);
        if (p.isSneaking()) usarDefinitiva(p);
        else usarQ(p);
    }

    private void usarQ(Player p) {
        DatosJugador d = plugin.almacen().de(p);
        ClaseRpg c = ClaseRpg.de(d.clase);
        if (c == null) return;
        Variante v = Variante.BASE;
        if (d.ramaQ != null && d.rama(d.ramaQ) >= 3) v = variante(c, d.ramaQ, d.tipoDanio);
        Def def = defQ(c, v);
        int ahora = Bukkit.getCurrentTick();
        int espera = esperaQ.getOrDefault(p.getUniqueId(), 0) - ahora;
        if (espera > 0) {
            Util.barra(p, "<gray>" + def.nombre() + " lista en " + (espera / 20 + 1) + " s");
            return;
        }
        if (!modo.gastarEter(p, def.eter())) return;
        esperaQ.put(p.getUniqueId(), ahora + 120);
        Util.barra(p, "<aqua>" + def.nombre());
        lanzar(p, c, v, false);
    }

    private void usarDefinitiva(Player p) {
        DatosJugador d = plugin.almacen().de(p);
        ClaseRpg c = ClaseRpg.de(d.clase);
        if (c == null) return;
        Rama r = d.ramaDefinitiva;
        if (r == null || d.rama(r) < 5) {
            Util.barra(p, "<gray>La definitiva se desbloquea con el nivel 5 de una rama del árbol.");
            return;
        }
        Variante v = variante(c, r, d.tipoDanio);
        Def def = defUlt(c, v);
        int ahora = Bukkit.getCurrentTick();
        int espera = esperaUlt.getOrDefault(p.getUniqueId(), 0) - ahora;
        if (espera > 0) {
            Util.barra(p, "<gray>" + def.nombre() + " lista en " + (espera / 20 + 1) + " s");
            return;
        }
        if (!modo.gastarEter(p, def.eter())) return;
        esperaUlt.put(p.getUniqueId(), ahora + 1800);
        Util.titulo(p, "", "<gold><bold>" + def.nombre(), 0, 1200, 300);
        p.playSound(p, Sound.ITEM_TOTEM_USE, 0.5f, 1.4f);
        lanzar(p, c, v, true);
    }

    private void lanzar(Player p, ClaseRpg c, Variante v, boolean ult) {
        switch (c) {
            case CABALLERO -> caballero(p, v, ult);
            case VERDUGO -> verdugo(p, v, ult);
            case RONIN -> ronin(p, v, ult);
            case CAZADOR -> cazador(p, v, ult);
            case HECHICERO -> hechicero(p, v, ult);
            case CLERIGO -> clerigo(p, v, ult);
        }
    }

    // ------------------------------------------------------------------ ayudantes

    private World mundo() {
        return plugin.mundos().de(Modo.RPG);
    }

    /** Enemigos (no jugadores ni invocaciones aliadas) a menos de r bloques. */
    private List<LivingEntity> enemigos(Location l, double r) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : l.getWorld().getNearbyEntities(l, r, r, r)) {
            if (!(e instanceof LivingEntity le) || le instanceof Player || le.isDead()) continue;
            if (e.getPersistentDataContainer().has(Claves.INVOCACION)) continue;
            if (e.getType() == org.bukkit.entity.EntityType.ARMOR_STAND || e instanceof org.bukkit.entity.Villager) continue;
            if (le.getLocation().distanceSquared(l) <= r * r) out.add(le);
        }
        return out;
    }

    private List<Player> aliados(Location l, double r) {
        List<Player> out = new ArrayList<>();
        for (Player o : l.getWorld().getPlayers()) {
            if (!o.isDead() && o.getLocation().distanceSquared(l) <= r * r) out.add(o);
        }
        return out;
    }

    /** Primer enemigo en la mira hasta `alcance` bloques. */
    private LivingEntity apuntado(Player p, double alcance) {
        RayTraceResult r = p.getWorld().rayTrace(p.getEyeLocation(), p.getEyeLocation().getDirection(), alcance,
                FluidCollisionMode.NEVER, true, 0.6,
                e -> e instanceof LivingEntity le && !(le instanceof Player) && !le.isDead()
                        && !e.getPersistentDataContainer().has(Claves.INVOCACION));
        return r != null && r.getHitEntity() instanceof LivingEntity le ? le : null;
    }

    /** Punto del piso al que apunta, hasta `alcance` bloques. */
    private Location puntoApuntado(Player p, double alcance) {
        RayTraceResult r = p.getWorld().rayTraceBlocks(p.getEyeLocation(), p.getEyeLocation().getDirection(), alcance,
                FluidCollisionMode.NEVER, true);
        if (r != null) return r.getHitPosition().toLocation(p.getWorld());
        return p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(alcance));
    }

    /** Enemigos en un cono al frente. */
    private List<LivingEntity> enCono(Player p, double alcance, double apertura) {
        List<LivingEntity> out = new ArrayList<>();
        Vector mira = p.getLocation().getDirection().setY(0).normalize();
        for (LivingEntity e : enemigos(p.getLocation(), alcance)) {
            Vector hacia = e.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
            if (hacia.lengthSquared() < 0.5 || hacia.normalize().dot(mira) >= apertura) out.add(e);
        }
        return out;
    }

    private double poder(Player p, Atributo a) {
        return 1 + 0.05 * plugin.almacen().de(p).atributo(a);
    }

    /** Daño de habilidad: escala con el atributo de la clase y con los multiplicadores del combate. */
    private void danio(Player p, LivingEntity v, double base, Atributo a, boolean magico) {
        double m = poder(p, a) * modo.combate().multiplicadorGeneral(p, v);
        if (magico) m *= modo.buffs().valor(p, Buffs.Tipo.DANIO_MAGICO, 1.0);
        CombateRpg.herir(v, base * m, p);
    }

    /** Golpe de habilidad basado en el arma: suma los multiplicadores de rama y efectos. */
    private void golpe(LivingEntity v, double danio, Player p) {
        CombateRpg.herir(v, danio * modo.combate().multiplicadorGeneral(p, v), p);
    }

    private double danioArma(Player p) {
        return modo.combate().danioArmaEnMano(p);
    }

    private static void curar(Player o, double fraccion) {
        double max = o.getAttribute(Attribute.MAX_HEALTH).getValue();
        o.setHealth(Math.min(max, o.getHealth() + max * fraccion));
        o.getWorld().spawnParticle(Particle.HEART, o.getLocation().add(0, 2, 0), 3, 0.3, 0.2, 0.3, 0);
    }

    private static void circulo(Location c, double r, Particle part, int puntos) {
        for (int i = 0; i < puntos; i++) {
            double a = i * Math.PI * 2 / puntos;
            c.getWorld().spawnParticle(part, c.clone().add(Math.cos(a) * r, 0.2, Math.sin(a) * r), 1, 0, 0, 0, 0);
        }
    }

    private static void circuloPolvo(Location c, double r, Color color, int puntos) {
        Particle.DustOptions op = new Particle.DustOptions(color, 1.6f);
        for (int i = 0; i < puntos; i++) {
            double a = i * Math.PI * 2 / puntos;
            c.getWorld().spawnParticle(Particle.DUST, c.clone().add(Math.cos(a) * r, 0.2, Math.sin(a) * r), 1, 0, 0, 0, 0, op);
        }
    }

    /** Repite una acción cada `cada` ticks, `veces` veces. */
    private void repetir(int veces, int cada, Consumer<Integer> accion) {
        for (int i = 0; i < veces; i++) {
            final int k = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> accion.accept(k), (long) i * cada);
        }
    }

    private void despues(int ticks, Runnable r) {
        Bukkit.getScheduler().runTaskLater(plugin, r, ticks);
    }

    // ------------------------------------------------------------------ Caballero Ceniciento

    private void caballero(Player p, Variante v, boolean ult) {
        World w = p.getWorld();
        if (!ult) switch (v) {
            case BASE -> {
                for (LivingEntity e : enCono(p, 3.5, 0.5)) {
                    e.setNoDamageTicks(0);
                    golpe(e, danioArma(p) * 1.4, p);
                    e.setVelocity(new Vector(0, 1.1, 0));
                    modo.combate().danioPostura(e, 30, p);
                }
                w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.7f);
                w.spawnParticle(Particle.SWEEP_ATTACK, p.getEyeLocation().add(p.getLocation().getDirection().multiply(1.5)), 3);
            }
            case DANIO_A -> {
                for (LivingEntity e : enCono(p, 4, 0.4)) {
                    e.setNoDamageTicks(0);
                    golpe(e, danioArma(p) * 1.3, p);
                    e.setFireTicks(80);
                }
                List<Location> estela = new ArrayList<>();
                Vector dir = p.getLocation().getDirection().setY(0).normalize();
                for (int i = 1; i <= 5; i++) estela.add(p.getLocation().add(dir.clone().multiply(i)));
                repetir(8, 10, k -> {
                    for (Location l : estela) {
                        w.spawnParticle(Particle.FLAME, l.clone().add(0, 0.3, 0), 6, 0.3, 0.1, 0.3, 0.02);
                        for (LivingEntity e : enemigos(l, 1.3)) {
                            e.setFireTicks(Math.max(e.getFireTicks(), 60));
                            danio(p, e, 1.5, Atributo.FUERZA, false);
                        }
                    }
                });
                w.playSound(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 0.8f);
            }
            case DANIO_B -> {
                LivingEntity t = apuntado(p, 8);
                if (t == null) t = enCono(p, 5, 0.3).stream().findFirst().orElse(null);
                if (t == null) return;
                w.strikeLightningEffect(t.getLocation());
                t.setNoDamageTicks(0);
                golpe(t, danioArma(p) * 1.5, p);
                final LivingEntity primero = t;
                enemigos(t.getLocation(), 6).stream().filter(e -> e != primero)
                        .min(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(primero.getLocation())))
                        .ifPresent(otro -> despues(6, () -> {
                            w.strikeLightningEffect(otro.getLocation());
                            otro.setNoDamageTicks(0);
                            golpe(otro, danioArma(p) * 0.8, p);
                        }));
            }
            case VIDA -> {
                modo.buffs().dar(p, Buffs.Tipo.BASTION, 0.3, 60);
                w.playSound(p.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1f, 0.6f);
                circuloPolvo(p.getLocation(), 1.2, Color.fromRGB(200, 200, 220), 20);
            }
            case BENDICION -> {
                Location l = p.getLocation();
                ItemDisplay estandarte = w.spawn(l.clone().add(0, 1.5, 0), ItemDisplay.class, d -> {
                    d.setItemStack(new ItemStack(Material.RED_BANNER));
                    d.setPersistent(false);
                });
                repetir(12, 20, k -> {
                    for (Player o : aliados(l, 6)) {
                        modo.buffs().dar(o, Buffs.Tipo.DANIO, 1.15, 25);
                        double max = o.getAttribute(Attribute.MAX_HEALTH).getValue();
                        o.setHealth(Math.min(max, o.getHealth() + 1));
                    }
                    circuloPolvo(l, 6, Color.fromRGB(220, 60, 40), 36);
                    if (k == 11) estandarte.remove();
                });
                w.playSound(l, Sound.BLOCK_WOOD_PLACE, 1f, 0.8f);
            }
        }
        else switch (v) {
            case DANIO_A -> {
                Location c = p.getLocation();
                repetir(12, 10, k -> {
                    circulo(c, 6, Particle.FLAME, 48);
                    circulo(c, 3, Particle.FLAME, 24);
                    for (LivingEntity e : enemigos(c, 6)) {
                        e.setFireTicks(80);
                        danio(p, e, 3, Atributo.FUERZA, false);
                    }
                });
                w.playSound(c, Sound.ENTITY_BLAZE_SHOOT, 1f, 0.5f);
            }
            case DANIO_B -> {
                List<LivingEntity> objetivos = enemigos(p.getLocation(), 15);
                objetivos.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(p.getLocation())));
                for (int i = 0; i < Math.min(5, objetivos.size()); i++) {
                    LivingEntity e = objetivos.get(i);
                    despues(i * 5, () -> {
                        if (!e.isValid()) return;
                        w.strikeLightningEffect(e.getLocation());
                        e.setNoDamageTicks(0);
                        golpe(e, danioArma(p) * 2, p);
                    });
                }
            }
            case VIDA -> {
                modo.buffs().dar(p, Buffs.Tipo.DEFENSA, 0.4, 160);
                modo.buffs().dar(p, Buffs.Tipo.FIRME, 1, 160);
                for (LivingEntity e : enemigos(p.getLocation(), 10)) if (e instanceof Mob m) m.setTarget(p);
                w.playSound(p.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1f, 0.5f);
            }
            case BENDICION -> {
                for (Player o : aliados(p.getLocation(), 12)) {
                    modo.buffs().dar(o, Buffs.Tipo.INMORTAL, 1, 100);
                    o.getWorld().spawnParticle(Particle.END_ROD, o.getLocation().add(0, 1, 0), 30, 0.4, 0.8, 0.4, 0.05);
                    Util.barra(o, "<gold>Última luz: no podés morir por 5 s");
                }
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ Verdugo

    private void grito(Player p, boolean cura) {
        for (Player o : aliados(p.getLocation(), 8)) {
            modo.buffs().dar(o, Buffs.Tipo.DANIO, 1.2, 300);
            if (cura) curar(o, 0.15);
        }
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 1f, 0.8f);
        circuloPolvo(p.getLocation(), 8, Color.fromRGB(180, 40, 30), 48);
    }

    private void verdugo(Player p, Variante v, boolean ult) {
        World w = p.getWorld();
        if (!ult) switch (v) {
            case BASE -> grito(p, false);
            case BENDICION -> grito(p, true);
            case DANIO_A -> {
                for (LivingEntity e : enemigos(p.getLocation(), 3.5)) {
                    e.setNoDamageTicks(0);
                    golpe(e, danioArma(p) * 1.2, p);
                    modo.estados().acumular(e, Estado.SANGRADO, 60, p);
                }
                circulo(p.getLocation().add(0, 1, 0), 2.5, Particle.SWEEP_ATTACK, 8);
                w.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.5f);
            }
            case DANIO_B -> terremoto(p, p.getLocation(), 5, 1.0, 60);
            case VIDA -> {
                modo.buffs().dar(p, Buffs.Tipo.FIRME, 1, 160);
                modo.buffs().dar(p, Buffs.Tipo.DEFENSA, 0.7, 160);
                w.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 0.6f);
            }
        }
        else switch (v) {
            case DANIO_A -> {
                Vector dir = p.getLocation().getDirection().setY(0).normalize();
                p.setVelocity(dir.multiply(1.1).setY(0.9));
                despues(14, () -> {
                    for (LivingEntity e : enemigos(p.getLocation(), 3.5)) {
                        boolean jefe = e.getPersistentDataContainer().has(Claves.JEFE);
                        double max = e.getAttribute(Attribute.MAX_HEALTH).getValue();
                        e.setNoDamageTicks(0);
                        if (!jefe && e.getHealth() < max * 0.3) CombateRpg.herir(e, e.getHealth() + 100, p);
                        else golpe(e, danioArma(p) * 3, p);
                    }
                    w.spawnParticle(Particle.EXPLOSION, p.getLocation(), 3, 1, 0.2, 1, 0);
                    w.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
                });
            }
            case DANIO_B -> {
                Vector dir = p.getLocation().getDirection().setY(0).normalize();
                Location base = p.getLocation();
                repetir(3, 10, k -> terremoto(p, base.clone().add(dir.clone().multiply(3 * (k + 1))), 2.8, 1.5, 80));
            }
            case VIDA -> {
                modo.buffs().dar(p, Buffs.Tipo.INMORTAL, 1, 120);
                Util.barra(p, "<gold>Inmortal: 6 s");
                despues(120, () -> {
                    if (p.isOnline() && !p.isDead()) curar(p, 0.3);
                });
            }
            case BENDICION -> {
                for (Player o : aliados(p.getLocation(), 12)) {
                    modo.buffs().dar(o, Buffs.Tipo.AGUANTE_INFINITO, 1, 200);
                    Util.barra(o, "<green>Canto de guerra: aguante infinito 10 s");
                }
                w.playSound(p.getLocation(), Sound.EVENT_RAID_HORN, 1f, 1.2f);
            }
            default -> { }
        }
    }

    private void terremoto(Player p, Location c, double radio, double mult, double postura) {
        World w = c.getWorld();
        for (LivingEntity e : enemigos(c, radio)) {
            e.setNoDamageTicks(0);
            golpe(e, danioArma(p) * mult, p);
            e.setVelocity(new Vector(0, 0.5, 0));
            modo.combate().danioPostura(e, postura, p);
        }
        w.spawnParticle(Particle.BLOCK, c, 60, radio / 2, 0.1, radio / 2, 0, c.clone().subtract(0, 1, 0).getBlock().getBlockData());
        w.spawnParticle(Particle.EXPLOSION, c, 2, 0.5, 0, 0.5, 0);
        w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 0.5f);
    }

    // ------------------------------------------------------------------ Ronin

    private void ronin(Player p, Variante v, boolean ult) {
        World w = p.getWorld();
        if (!ult) switch (v) {
            case BASE, DANIO_A -> {
                for (LivingEntity e : enCono(p, 4.2, 0.75)) {
                    e.setNoDamageTicks(0);
                    golpe(e, danioArma(p) * 1.5, p);
                    if (v == Variante.DANIO_A) {
                        modo.estados().acumular(e, Estado.SANGRADO, 50, p);
                        repetir(10, 10, k -> {
                            if (e.isValid() && !e.isDead()) danio(p, e, 1, Atributo.DESTREZA, false);
                        });
                    }
                }
                w.spawnParticle(Particle.SWEEP_ATTACK, p.getEyeLocation().add(p.getLocation().getDirection().multiply(2)), 4, 0.6, 0.1, 0.6, 0);
                w.playSound(p.getLocation(), Sound.ITEM_TRIDENT_RIPTIDE_1, 1f, 1.6f);
            }
            case DANIO_B -> {
                Vector dir = p.getLocation().getDirection().setY(0).normalize();
                Location desde = p.getLocation();
                RayTraceResult r = w.rayTraceBlocks(desde.clone().add(0, 0.5, 0), dir, 8, FluidCollisionMode.NEVER, true);
                double dist = r == null ? 8 : Math.max(0, r.getHitPosition().distance(desde.toVector()) - 0.8);
                Location hasta = desde.clone().add(dir.clone().multiply(dist));
                for (double s = 0; s <= dist; s += 0.8) {
                    Location l = desde.clone().add(dir.clone().multiply(s)).add(0, 1, 0);
                    w.spawnParticle(Particle.ELECTRIC_SPARK, l, 4, 0.1, 0.2, 0.1, 0.05);
                    for (LivingEntity e : enemigos(l, 1.4)) {
                        e.setNoDamageTicks(0);
                        golpe(e, danioArma(p) * 1.4, p);
                    }
                }
                modo.buffs().dar(p, Buffs.Tipo.FASE, 1, 6);
                p.teleport(hasta);
                w.playSound(hasta, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.8f);
            }
            case VIDA -> {
                modo.buffs().dar(p, Buffs.Tipo.CONTRAATAQUE, 1, 40);
                w.playSound(p.getLocation(), Sound.BLOCK_WATER_AMBIENT, 1f, 1.6f);
                w.spawnParticle(Particle.SPLASH, p.getLocation().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0);
            }
            case BENDICION -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 5, false, false));
                w.playSound(p.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 1f, 1.2f);
                despues(40, () -> {
                    for (Player o : aliados(p.getLocation(), 6)) {
                        curar(o, 0.25);
                        modo.llenarAguante(o);
                    }
                });
            }
        }
        else switch (v) {
            case DANIO_A -> {
                List<LivingEntity> obj = enemigos(p.getLocation(), 10);
                if (obj.isEmpty()) return;
                modo.buffs().dar(p, Buffs.Tipo.FASE, 1, 36);
                Location volver = p.getLocation();
                repetir(8, 4, k -> {
                    LivingEntity e = obj.get(k % obj.size());
                    if (!e.isValid() || e.isDead()) return;
                    Location l = e.getLocation().add(e.getLocation().getDirection().multiply(-1.2));
                    l.setDirection(e.getLocation().toVector().subtract(l.toVector()));
                    p.teleport(l);
                    e.setNoDamageTicks(0);
                    golpe(e, danioArma(p) * 0.9, p);
                    modo.estados().acumular(e, Estado.SANGRADO, 25, p);
                    w.spawnParticle(Particle.SWEEP_ATTACK, e.getLocation().add(0, 1, 0), 2);
                    w.playSound(l, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 1.5f);
                    if (k == 7) p.teleport(volver);
                });
            }
            case DANIO_B -> modo.buffs().dar(p, Buffs.Tipo.TORMENTA, 1, 120);
            case VIDA -> modo.buffs().dar(p, Buffs.Tipo.PARRY, 3.0, 160);
            case BENDICION -> {
                for (Player o : aliados(p.getLocation(), 12)) {
                    modo.buffs().dar(o, Buffs.Tipo.PARRY, 2.0, 200);
                    modo.buffs().dar(o, Buffs.Tipo.DANIO, 1.2, 200);
                }
                w.playSound(p.getLocation(), Sound.BLOCK_BELL_RESONATE, 1f, 1.2f);
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ Cazador de Bestias

    private void cazador(Player p, Variante v, boolean ult) {
        World w = p.getWorld();
        if (!ult) switch (v) {
            case BASE -> {
                Location c = puntoApuntado(p, 30);
                double fuerza = poder(p, Atributo.DESTREZA);
                repetir(20, 1, k -> {
                    Location l = c.clone().add(Math.random() * 10 - 5, 14, Math.random() * 10 - 5);
                    Arrow a = w.spawnArrow(l, new Vector(0, -1, 0), 2.2f, 0);
                    a.setShooter(p);
                    a.setDamage(4 * fuerza);
                    a.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
                });
                w.playSound(p.getLocation(), Sound.ENTITY_ARROW_SHOOT, 1f, 0.6f);
            }
            case DANIO_A -> {
                LivingEntity t = apuntado(p, 12);
                if (t == null) {
                    Util.barra(p, "<gray>No hay nadie en la mira.");
                    return;
                }
                repetir(8, 10, k -> {
                    if (!t.isValid() || t.isDead() || !p.isOnline()) return;
                    double antes = t.getHealth();
                    danio(p, t, 1.5, Atributo.DESTREZA, false);
                    double sacado = Math.max(0, antes - t.getHealth());
                    double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
                    p.setHealth(Math.min(max, p.getHealth() + sacado * 0.5));
                    modo.estados().acumular(t, Estado.SANGRADO, 8, p);
                    Vector d = t.getEyeLocation().toVector().subtract(p.getEyeLocation().toVector());
                    for (double s = 0; s < d.length(); s += 0.5) {
                        w.spawnParticle(Particle.DUST, p.getEyeLocation().add(d.clone().normalize().multiply(s)), 1, 0, 0, 0, 0,
                                new Particle.DustOptions(Color.fromRGB(140, 0, 0), 0.8f));
                    }
                });
                w.playSound(p.getLocation(), Sound.ITEM_CROSSBOW_HIT, 1f, 1.4f);
            }
            case DANIO_B -> {
                Location c = puntoApuntado(p, 25);
                repetir(12, 10, k -> {
                    w.spawnParticle(Particle.DUST, c, 30, 2, 0.6, 2, 0, new Particle.DustOptions(Color.fromRGB(80, 150, 40), 2f));
                    for (LivingEntity e : enemigos(c, 4)) modo.estados().acumular(e, Estado.VENENO, 10, p);
                });
                w.playSound(c, Sound.ENTITY_SPLASH_POTION_BREAK, 1f, 0.6f);
            }
            case VIDA -> {
                Location l = p.getLocation().getBlock().getLocation().add(0.5, 0.02, 0.5);
                ItemDisplay cepo = w.spawn(l, ItemDisplay.class, d -> {
                    d.setItemStack(new ItemStack(Material.IRON_TRAPDOOR));
                    d.setPersistent(false);
                });
                repetir(300, 2, k -> {
                    if (!cepo.isValid()) return;
                    for (LivingEntity e : enemigos(l, 1.3)) {
                        modo.combate().aturdir(e, 60);
                        danio(p, e, 6, Atributo.DESTREZA, false);
                        w.playSound(l, Sound.BLOCK_IRON_TRAPDOOR_CLOSE, 1f, 0.6f);
                        cepo.remove();
                        return;
                    }
                    if (k == 299) cepo.remove();
                });
            }
            case BENDICION -> invocarLobos(p, 1, 1200);
        }
        else switch (v) {
            case DANIO_A -> {
                LivingEntity t = apuntado(p, 20);
                if (t == null) {
                    Util.barra(p, "<gray>No hay nadie en la mira.");
                    return;
                }
                modo.buffs().dar(t, Buffs.Tipo.MARCA, 1.4, 200);
                t.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0, false, false));
                w.playSound(t.getLocation(), Sound.ENTITY_WOLF_GROWL, 1f, 0.6f);
            }
            case DANIO_B -> {
                modo.buffs().dar(p, Buffs.Tipo.PESTILENCIA, 1, 200);
                repetir(10, 20, k -> {
                    for (LivingEntity e : enemigos(p.getLocation(), 20)) {
                        if (!modo.estados().envenenado(e)) continue;
                        for (LivingEntity otro : enemigos(e.getLocation(), 3)) {
                            if (otro != e) modo.estados().acumular(otro, Estado.VENENO, 30, p);
                        }
                    }
                });
            }
            case VIDA -> modo.buffs().dar(p, Buffs.Tipo.INSTINTO, 1, 120);
            case BENDICION -> invocarLobos(p, 3, 300);
            default -> { }
        }
    }

    private void invocarLobos(Player p, int cantidad, int duracion) {
        World w = p.getWorld();
        for (int i = 0; i < cantidad; i++) {
            Wolf lobo = w.spawn(p.getLocation().add(Math.random() - 0.5, 0, Math.random() - 0.5), Wolf.class,
                    CreatureSpawnEvent.SpawnReason.CUSTOM, x -> {
                        x.setOwner(p);
                        x.setPersistent(false);
                        x.customName(Util.mm("<gray>Lobo de " + p.getName()));
                        var f = x.getAttribute(Attribute.ATTACK_DAMAGE);
                        if (f != null) f.setBaseValue(6 * poder(p, Atributo.DESTREZA));
                        var vida = x.getAttribute(Attribute.MAX_HEALTH);
                        if (vida != null) vida.setBaseValue(40);
                        x.setHealth(40);
                        x.getPersistentDataContainer().set(Claves.INVOCACION, PersistentDataType.BYTE, (byte) 1);
                    });
            invocaciones.add(lobo.getUniqueId());
            repetir(duracion / 100, 100, k -> {
                if (!lobo.isValid()) return;
                w.playSound(lobo.getLocation(), Sound.ENTITY_WOLF_AMBIENT, 1f, 0.8f);
                for (Player o : aliados(lobo.getLocation(), 6)) {
                    double max = o.getAttribute(Attribute.MAX_HEALTH).getValue();
                    o.setHealth(Math.min(max, o.getHealth() + 2));
                }
            });
            despues(duracion, () -> {
                invocaciones.remove(lobo.getUniqueId());
                if (lobo.isValid()) lobo.remove();
            });
        }
        w.playSound(p.getLocation(), Sound.ENTITY_WOLF_GROWL, 1f, 0.8f);
    }

    // ------------------------------------------------------------------ Hechicero del Vacío

    private void hechicero(Player p, Variante v, boolean ult) {
        World w = p.getWorld();
        if (!ult) switch (v) {
            case BASE, DANIO_A -> orbe(p, v == Variante.DANIO_A);
            case DANIO_B -> {
                for (LivingEntity e : enemigos(p.getLocation(), 4)) {
                    modo.combate().aturdir(e, 40);
                    modo.estados().acumular(e, Estado.FRIO, 60, p);
                    danio(p, e, 3, Atributo.INTELIGENCIA, true);
                }
                w.spawnParticle(Particle.SNOWFLAKE, p.getLocation().add(0, 1, 0), 120, 3, 1, 3, 0.02);
                w.playSound(p.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.6f);
            }
            case VIDA -> {
                double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
                modo.buffs().dar(p, Buffs.Tipo.ESCUDO, max * 0.4, 400);
                w.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
                w.spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 40, 0.6, 0.9, 0.6, 0.01);
            }
            case BENDICION -> {
                Location c = p.getLocation();
                repetir(10, 20, k -> {
                    circuloPolvo(c, 4, Color.fromRGB(120, 60, 220), 40);
                    for (Player o : aliados(c, 4)) {
                        modo.buffs().dar(o, Buffs.Tipo.DANIO_MAGICO, 1.25, 25);
                        modo.sumarEter(o, 2);
                    }
                });
            }
        }
        else switch (v) {
            case DANIO_A -> {
                Location c = puntoApuntado(p, 30);
                repetir(10, 5, k -> {
                    Location l = c.clone().add(Math.random() * 20 - 10, 0, Math.random() * 20 - 10);
                    w.spawnParticle(Particle.DRAGON_BREATH, l.clone().add(0, 8, 0), 30, 0.3, 4, 0.3, 0.02, 1f);
                    despues(6, () -> {
                        w.spawnParticle(Particle.EXPLOSION, l, 2, 0.5, 0.2, 0.5, 0);
                        w.playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 0.6f, 1.2f);
                        for (LivingEntity e : enemigos(l, 3)) danio(p, e, 7, Atributo.INTELIGENCIA, true);
                    });
                });
            }
            case DANIO_B -> {
                Location c = p.getLocation();
                repetir(16, 10, k -> {
                    w.spawnParticle(Particle.SNOWFLAKE, c.clone().add(0, 2, 0), 150, 8, 2, 8, 0.05);
                    for (LivingEntity e : enemigos(c, 8)) {
                        e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 2, false, false));
                        modo.estados().acumular(e, Estado.FRIO, 8, p);
                        if (k % 2 == 0) danio(p, e, 1, Atributo.INTELIGENCIA, true);
                    }
                });
            }
            case VIDA -> {
                modo.buffs().dar(p, Buffs.Tipo.FASE, 1, 60);
                Vector dir = p.getLocation().getDirection().setY(0).normalize();
                RayTraceResult r = w.rayTraceBlocks(p.getLocation().add(0, 0.5, 0), dir, 10, FluidCollisionMode.NEVER, true);
                double dist = r == null ? 10 : Math.max(0, r.getHitPosition().distance(p.getLocation().toVector()) - 1);
                Location hasta = p.getLocation().add(dir.multiply(dist));
                w.spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.5);
                p.teleport(hasta);
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 60, 0, false, false));
                w.playSound(hasta, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.8f);
            }
            case BENDICION -> {
                for (LivingEntity e : enemigos(p.getLocation(), 10)) {
                    modo.combate().aturdir(e, 100);
                    e.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 100, 6, false, false));
                }
                w.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.5f);
                circuloPolvo(p.getLocation(), 10, Color.fromRGB(200, 200, 255), 80);
            }
            default -> { }
        }
    }

    /** Orbe gravitatorio: avanza, atrae y aplasta; con Colapso explota al final. */
    private void orbe(Player p, boolean colapso) {
        World w = p.getWorld();
        Location inicio = p.getEyeLocation();
        Vector dir = inicio.getDirection().normalize();
        repetir(20, 2, k -> {
            Location l = inicio.clone().add(dir.clone().multiply(Math.min(12, k * 0.9)));
            w.spawnParticle(Particle.DRAGON_BREATH, l, 12, 0.3, 0.3, 0.3, 0.01, 1f);
            w.spawnParticle(Particle.REVERSE_PORTAL, l, 8, 0.4, 0.4, 0.4, 0.05);
            for (LivingEntity e : enemigos(l, 3.5)) {
                Vector hacia = l.toVector().subtract(e.getLocation().toVector());
                if (hacia.lengthSquared() > 0.25) e.setVelocity(hacia.normalize().multiply(0.35));
                if (k % 4 == 0) danio(p, e, 1.5, Atributo.INTELIGENCIA, true);
            }
            if (k == 19 && colapso) {
                w.spawnParticle(Particle.EXPLOSION_EMITTER, l, 1);
                w.playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.8f);
                for (LivingEntity e : enemigos(l, 4)) danio(p, e, 9, Atributo.INTELIGENCIA, true);
            }
        });
        w.playSound(inicio, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 0.6f);
    }

    // ------------------------------------------------------------------ Clérigo de la Llama

    private void clerigo(Player p, Variante v, boolean ult) {
        World w = p.getWorld();
        if (!ult) switch (v) {
            case BASE -> {
                for (LivingEntity e : enemigos(p.getLocation(), 4)) {
                    danio(p, e, 5, Atributo.FE, true);
                    e.setFireTicks(60);
                }
                circulo(p.getLocation(), 2, Particle.SOUL_FIRE_FLAME, 24);
                circulo(p.getLocation(), 4, Particle.FLAME, 40);
                w.playSound(p.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1f, 1.2f);
            }
            case DANIO_A -> {
                LivingEntity t = enCono(p, 3.5, 0.5).stream().findFirst().orElse(null);
                Location c = t != null ? t.getLocation() : p.getLocation().add(p.getLocation().getDirection().multiply(2));
                if (t != null) {
                    t.setNoDamageTicks(0);
                    golpe(t, danioArma(p) * 2, p);
                }
                w.spawnParticle(Particle.FLAME, c, 60, 1.2, 0.5, 1.2, 0.1);
                w.spawnParticle(Particle.EXPLOSION, c, 1);
                w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.3f);
                for (LivingEntity e : enemigos(c, 3)) {
                    danio(p, e, 4, Atributo.FE, true);
                    e.setFireTicks(80);
                }
            }
            case DANIO_B -> {
                LivingEntity t = apuntado(p, 25);
                if (t == null) {
                    Util.barra(p, "<gray>No hay nadie en la mira.");
                    return;
                }
                w.strikeLightningEffect(t.getLocation());
                danio(p, t, 7, Atributo.FE, true);
                modo.combate().aturdir(t, 25);
            }
            case VIDA -> {
                Player obj = aliadoApuntado(p, 15);
                double max = obj.getAttribute(Attribute.MAX_HEALTH).getValue();
                modo.buffs().dar(obj, Buffs.Tipo.ESCUDO, max * 0.3, 400);
                obj.getWorld().spawnParticle(Particle.END_ROD, obj.getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.01);
                Util.barra(obj, "<gold>Égida de " + p.getName());
            }
            case BENDICION -> {
                for (Player o : aliados(p.getLocation(), 10)) curar(o, 0.35);
                w.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.4f);
                circuloPolvo(p.getLocation(), 10, Color.fromRGB(255, 220, 120), 60);
            }
        }
        else switch (v) {
            case DANIO_A -> {
                Location c = puntoApuntado(p, 20);
                repetir(12, 10, k -> {
                    w.spawnParticle(Particle.FLAME, c.clone().add(0, 0.5, 0), 60, 4, 0.6, 4, 0.02);
                    w.spawnParticle(Particle.LARGE_SMOKE, c.clone().add(0, 1, 0), 20, 4, 1, 4, 0.01);
                    for (LivingEntity e : enemigos(c, 8)) {
                        danio(p, e, 2, Atributo.FE, true);
                        e.setFireTicks(60);
                    }
                });
            }
            case DANIO_B -> {
                LivingEntity t = apuntado(p, 25);
                if (t == null) {
                    Util.barra(p, "<gray>No hay nadie en la mira.");
                    return;
                }
                repetir(16, 5, k -> {
                    if (!t.isValid() || t.isDead() || !p.isOnline()) return;
                    Vector d = t.getEyeLocation().toVector().subtract(p.getEyeLocation().toVector());
                    for (double s = 0; s < d.length(); s += 0.6) {
                        w.spawnParticle(Particle.END_ROD, p.getEyeLocation().add(d.clone().normalize().multiply(s)), 1, 0, 0, 0, 0);
                    }
                    danio(p, t, 1.5, Atributo.FE, true);
                    if (k % 4 == 0) w.strikeLightningEffect(t.getLocation());
                });
            }
            case VIDA -> {
                Location c = p.getLocation();
                repetir(16, 10, k -> {
                    circuloPolvo(c, 6, Color.fromRGB(255, 240, 180), 48);
                    for (Player o : aliados(c, 6)) modo.buffs().dar(o, Buffs.Tipo.DEFENSA, 0.5, 15);
                });
                w.playSound(c, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.2f);
            }
            case BENDICION -> {
                if (!modo.resucitar(p)) Util.barra(p, "<gray>No hay ningún aliado caído en los últimos 30 s.");
            }
            default -> { }
        }
    }

    private Player aliadoApuntado(Player p, double alcance) {
        RayTraceResult r = p.getWorld().rayTrace(p.getEyeLocation(), p.getEyeLocation().getDirection(), alcance,
                FluidCollisionMode.NEVER, true, 0.8, e -> e instanceof Player o && o != p);
        return r != null && r.getHitEntity() instanceof Player o ? o : p;
    }

    public void olvidar(UUID id) {
        esperaQ.remove(id);
        esperaUlt.remove(id);
    }

    /** Saca las invocaciones (lobos) del mundo. */
    public void apagar() {
        for (UUID id : invocaciones) {
            Entity e = Bukkit.getEntity(id);
            if (e != null) e.remove();
        }
        invocaciones.clear();
    }
}
