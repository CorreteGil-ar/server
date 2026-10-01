package ar.tresmodos.rpg;

import ar.tresmodos.Armas;
import ar.tresmodos.Claves;
import ar.tresmodos.DatosJugador;
import ar.tresmodos.Modo;
import ar.tresmodos.TresModos;
import ar.tresmodos.Util;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Combate del RPG: ataque pesado (Shift), en carrera y en caída; parry con escudo o arma con
 * guardia; postura que al romperse aturde; críticos al aturdido o por la espalda; escalado por
 * atributos, resinas, estados y los efectos de las habilidades.
 */
public class CombateRpg implements Listener {
    private static final int VENTANA_PARRY = 5;

    private final TresModos plugin;
    private final ModoRpg modo;
    private final Map<UUID, Integer> ultimoParry = new HashMap<>();
    private final Map<UUID, Integer> aturdidoHasta = new HashMap<>();
    private final Map<UUID, Double> postura = new HashMap<>();
    private final Map<UUID, Integer> ultimoGolpePostura = new HashMap<>();

    public CombateRpg(TresModos plugin, ModoRpg modo) {
        this.plugin = plugin;
        this.modo = modo;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5, 5);
    }

    private static boolean enRpg(Entity e) {
        return Modo.de(e.getWorld()) == Modo.RPG;
    }

    private static boolean esJefe(Entity e) {
        return e.getPersistentDataContainer().has(Claves.JEFE);
    }

    // ------------------------------------------------------------------ parry

    @EventHandler
    public void alLevantarGuardia(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        if (!enRpg(p) || e.getHand() != EquipmentSlot.HAND) return;
        ItemStack mano = p.getInventory().getItemInMainHand();
        ArmaRpg a = ArmaRpg.de(mano);
        boolean escudo = p.getInventory().getItemInOffHand().getType() == Material.SHIELD || mano.getType() == Material.SHIELD;
        if (escudo || (a != null && a.parry)) ultimoParry.put(p.getUniqueId(), Bukkit.getCurrentTick());
    }

    private boolean enVentanaParry(Player p) {
        Integer t = ultimoParry.get(p.getUniqueId());
        if (t == null) return false;
        double mult = modo.buffs().valor(p, Buffs.Tipo.PARRY, 1.0);
        return Bukkit.getCurrentTick() - t <= Math.round(VENTANA_PARRY * mult);
    }

    // ------------------------------------------------------------------ aturdir y postura

    public void aturdir(LivingEntity e, int ticks) {
        aturdidoHasta.put(e.getUniqueId(), Bukkit.getCurrentTick() + ticks);
        if (e instanceof Mob m) m.setAware(false);
        if (e instanceof Player p) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 3, false, false));
            Util.titulo(p, "", "<yellow>Aturdido", 0, ticks * 50, 200);
        }
        e.getWorld().playSound(e.getLocation(), Sound.ITEM_SHIELD_BREAK, 1f, 0.6f);
    }

    public boolean aturdido(LivingEntity e) {
        Integer t = aturdidoHasta.get(e.getUniqueId());
        return t != null && t > Bukkit.getCurrentTick();
    }

    private void desaturdir(UUID id) {
        aturdidoHasta.remove(id);
        Entity e = Bukkit.getEntity(id);
        if (e instanceof Mob m && m.isValid()) m.setAware(true);
    }

    private double posturaMax(LivingEntity e) {
        double base = 25 + e.getAttribute(Attribute.MAX_HEALTH).getValue() * 0.8;
        return esJefe(e) ? base * 1.8 : base;
    }

    /** Suma daño de postura; si se llena, aturde y queda expuesto a un crítico. */
    public void danioPostura(LivingEntity v, double cant, Player autor) {
        if (cant <= 0 || aturdido(v) || modo.buffs().tiene(v, Buffs.Tipo.FIRME)) return;
        double total = postura.getOrDefault(v.getUniqueId(), 0.0) + cant;
        ultimoGolpePostura.put(v.getUniqueId(), Bukkit.getCurrentTick());
        if (total >= posturaMax(v)) {
            postura.remove(v.getUniqueId());
            aturdir(v, esJefe(v) ? 50 : 40);
            v.getWorld().spawnParticle(Particle.CRIT, v.getLocation().add(0, v.getHeight() + 0.3, 0), 20, 0.3, 0.2, 0.3, 0.1);
            if (autor != null) Util.barra(autor, "<yellow><bold>¡Postura rota! <gray>Pegale para un crítico");
        } else {
            postura.put(v.getUniqueId(), total);
        }
    }

    // ------------------------------------------------------------------ golpes

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alGolpe(EntityDamageByEntityEvent e) {
        if (!enRpg(e.getEntity()) || !(e.getEntity() instanceof LivingEntity victima)) return;
        Player atacante = TresModos.jugadorAtacante(e.getDamager());
        // Cooperativo: nada de PvP.
        if (atacante != null && victima instanceof Player && victima != atacante) {
            e.setCancelled(true);
            return;
        }
        // Un aturdido no puede pegar.
        if (e.getDamager() instanceof LivingEntity dm && aturdido(dm)) {
            e.setCancelled(true);
            return;
        }
        if (victima instanceof Player pv) {
            recibirGolpe(e, pv);
            return;
        }
        if (atacante == null || Armas.aplicandoBala) return;
        if (e.getDamager() == atacante && (e.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK
                || e.getCause() == EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK)) {
            golpeMelee(e, atacante, victima);
        } else if (e.getDamager() instanceof Projectile) {
            e.setDamage(e.getDamage() * multiplicadorGeneral(atacante, victima));
        }
    }

    private void recibirGolpe(EntityDamageByEntityEvent e, Player p) {
        Buffs b = modo.buffs();
        if (b.tiene(p, Buffs.Tipo.FASE)) {
            e.setCancelled(true);
            return;
        }
        boolean melee = !(e.getDamager() instanceof Projectile) && e.getDamager() instanceof LivingEntity;
        LivingEntity agresor = e.getDamager() instanceof LivingEntity le ? le
                : e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof LivingEntity le2 ? le2 : null;
        // Parry: justo antes del golpe (o con Postura del agua activa).
        boolean contra = b.tiene(p, Buffs.Tipo.CONTRAATAQUE);
        if (melee && agresor != null && (enVentanaParry(p) || contra)) {
            e.setCancelled(true);
            ultimoParry.remove(p.getUniqueId());
            aturdir(agresor, 30);
            p.playSound(p, Sound.BLOCK_ANVIL_LAND, 0.7f, 1.8f);
            p.getWorld().spawnParticle(Particle.FLASH, p.getEyeLocation().add(p.getLocation().getDirection()), 1, 0, 0, 0, 0,
                    org.bukkit.Color.WHITE);
            Util.barra(p, "<aqua><bold>¡PARRY! <gray>Pegale para un crítico");
            if (contra) {
                b.quitar(p, Buffs.Tipo.CONTRAATAQUE);
                double danio = danioArmaEnMano(p) * 3;
                agresor.setNoDamageTicks(0);
                agresor.damage(danio, p);
            }
            return;
        }
        // Bastión: bloqueo total al frente y devuelve parte del daño.
        double devolver = b.valor(p, Buffs.Tipo.BASTION, 0);
        if (devolver > 0 && agresor != null && deFrente(p, agresor)) {
            e.setCancelled(true);
            agresor.damage(e.getDamage() * devolver, p);
            p.playSound(p, Sound.ITEM_SHIELD_BLOCK, 1f, 0.8f);
            return;
        }
        // Bloqueo con escudo: cuesta aguante (menos con Vida y armadura); sin aguante se rompe la guardia.
        if (p.isBlocking()) {
            int vida = plugin.almacen().de(p).rama(Rama.VIDA);
            modo.gastar(p, 12 * (1 - 0.1 * vida));
            if (modo.aguanteDe(p) <= 0) {
                p.setCooldown(Material.SHIELD, 60);
                Util.barra(p, "<red><bold>¡Guardia rota!");
                p.playSound(p, Sound.ITEM_SHIELD_BREAK, 1f, 0.8f);
            }
        }
        e.setDamage(ajustarDanioRecibido(p, e.getDamage()));
    }

    /** Defensa del árbol y de los efectos; escudos; inmortalidad. */
    public double ajustarDanioRecibido(Player p, double danio) {
        Buffs b = modo.buffs();
        DatosJugador d = plugin.almacen().de(p);
        danio *= 1 - 0.04 * d.rama(Rama.VIDA);
        danio *= b.valor(p, Buffs.Tipo.DEFENSA, 1.0);
        danio = b.absorber(p, danio);
        if (b.tiene(p, Buffs.Tipo.INMORTAL) && danio >= p.getHealth()) danio = Math.max(0, p.getHealth() - 1);
        return danio;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alDanioCualquiera(EntityDamageEvent e) {
        // Daño sin atacante (caída, fuego, estados): también respeta la inmortalidad y los escudos.
        if (e instanceof EntityDamageByEntityEvent || !(e.getEntity() instanceof Player p) || !enRpg(p)) return;
        if (modo.buffs().tiene(p, Buffs.Tipo.FASE) && e.getCause() != EntityDamageEvent.DamageCause.VOID) {
            e.setCancelled(true);
            return;
        }
        Buffs b = modo.buffs();
        double danio = b.absorber(p, e.getDamage());
        if (b.tiene(p, Buffs.Tipo.INMORTAL) && danio >= p.getHealth()) danio = Math.max(0, p.getHealth() - 1);
        e.setDamage(danio);
    }

    private static boolean deFrente(Player p, LivingEntity agresor) {
        Vector mira = p.getLocation().getDirection().setY(0);
        Vector hacia = agresor.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
        return mira.lengthSquared() > 0.01 && hacia.lengthSquared() > 0.01 && mira.normalize().dot(hacia.normalize()) > 0.3;
    }

    private void golpeMelee(EntityDamageByEntityEvent e, Player p, LivingEntity v) {
        DatosJugador d = plugin.almacen().de(p);
        ItemStack mano = p.getInventory().getItemInMainHand();
        ArmaRpg arma = ArmaRpg.de(mano);
        double danio = e.getDamage();
        double posturaDanio = arma != null ? arma.postura : 8;
        double costo = arma != null ? 10 + arma.peso : 12;
        String tipoGolpe = null;

        danio *= arma != null ? arma.multiplicador(d) : 1 + 0.03 * d.fuerza;
        if (p.isSneaking()) {
            danio *= 1.6;
            posturaDanio *= 2.2;
            costo *= 1.8;
            tipoGolpe = "pesado";
        } else if (p.isSprinting()) {
            danio *= 1.25;
            posturaDanio *= 1.4;
            costo *= 1.3;
            Vector dir = p.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() > 0.01) p.setVelocity(p.getVelocity().add(dir.normalize().multiply(0.35)));
            tipoGolpe = "en carrera";
        }
        if (p.getFallDistance() > 2.2) {
            danio *= 1.5;
            posturaDanio *= 1.5;
            tipoGolpe = "en caída";
        }
        // Sin aguante, el golpe sale débil.
        if (modo.aguanteDe(p) < costo * 0.5) {
            danio *= 0.3;
            posturaDanio *= 0.3;
            Util.barra(p, "<red>Sin aguante: golpe débil");
        }
        modo.gastar(p, costo);

        // Crítico: al aturdido o por la espalda (no a los jefes).
        boolean critico = false;
        if (aturdido(v)) {
            danio *= 3;
            critico = true;
            desaturdir(v.getUniqueId());
        } else if (!esJefe(v) && v instanceof Mob && deEspaldas(p, v)) {
            danio *= 2.5;
            critico = true;
        }
        if (critico) {
            v.getWorld().spawnParticle(Particle.ENCHANTED_HIT, v.getLocation().add(0, v.getHeight() * 0.6, 0), 30, 0.3, 0.4, 0.3, 0.3);
            p.playSound(p, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.6f);
            p.playSound(p, Sound.ITEM_TRIDENT_HIT, 1f, 0.6f);
            Util.barra(p, "<gold><bold>¡CRÍTICO!");
        } else if (tipoGolpe != null) {
            Util.barra(p, "<gray>Ataque " + tipoGolpe);
        }

        danio *= multiplicadorGeneral(p, v);
        e.setDamage(danio);

        // Estados: los del arma, los de la rama Daño y los de la resina.
        if (arma != null && arma.estado != null) {
            modo.estados().acumular(v, arma.estado, arma.acumula * (tipoGolpe != null && tipoGolpe.equals("pesado") ? 1.5 : 1), p);
        }
        Estado propio = estadoDelTipo(d);
        int nivelDanio = d.rama(Rama.DANIO);
        if (propio != null && nivelDanio > 0) modo.estados().acumular(v, propio, 6 * nivelDanio, p);
        ObjetosRpg.Resina r = modo.objetos().resina(p);
        if (r == ObjetosRpg.Resina.FUEGO) v.setFireTicks(Math.max(v.getFireTicks(), 80));
        if (r == ObjetosRpg.Resina.RAYO || modo.buffs().tiene(p, Buffs.Tipo.TORMENTA)) {
            v.getWorld().strikeLightningEffect(v.getLocation());
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (v.isValid() && !v.isDead()) {
                    v.setNoDamageTicks(0);
                    v.damage(danioArmaEnMano(p) * 0.35, p);
                }
            });
        }
        // Marca de Cacería: el que le pega se cura.
        if (modo.buffs().tiene(v, Buffs.Tipo.MARCA)) {
            double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
            p.setHealth(Math.min(max, p.getHealth() + danio * 0.15));
        }
        danioPostura(v, posturaDanio * (d.tipoDanio != null && d.tipoDanio.equals("APLASTANTE") ? 1.3 : 1), p);
    }

    /** Daño extra de la rama Daño, de los efectos propios y de la marca del enemigo. */
    public double multiplicadorGeneral(Player p, LivingEntity v) {
        DatosJugador d = plugin.almacen().de(p);
        double m = 1 + 0.08 * d.rama(Rama.DANIO);
        m *= modo.buffs().valor(p, Buffs.Tipo.DANIO, 1.0);
        m *= modo.buffs().valor(v, Buffs.Tipo.MARCA, 1.0);
        return m;
    }

    /** El estado que acumula la rama Daño según el tipo elegido (null si es fuego, rayo, etc.). */
    public static Estado estadoDelTipo(DatosJugador d) {
        if (d.tipoDanio == null) return null;
        return switch (d.tipoDanio) {
            case "SANGRADO", "ROBO_VIDA" -> Estado.SANGRADO;
            case "VENENO" -> Estado.VENENO;
            case "HIELO" -> Estado.FRIO;
            default -> null;
        };
    }

    private static boolean deEspaldas(Player p, LivingEntity v) {
        Vector mira = v.getLocation().getDirection().setY(0);
        Vector hacia = v.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0);
        return mira.lengthSquared() > 0.01 && hacia.lengthSquared() > 0.01 && mira.normalize().dot(hacia.normalize()) > 0.6;
    }

    /** Daño base del arma de la mano con atributos (para habilidades y contraataques). */
    public double danioArmaEnMano(Player p) {
        ItemStack mano = p.getInventory().getItemInMainHand();
        ArmaRpg a = ArmaRpg.de(mano);
        DatosJugador d = plugin.almacen().de(p);
        if (a == null) return 4 + 0.3 * d.fuerza;
        return a.danio * (1 + 0.08 * ArmaRpg.mejora(mano)) * a.multiplicador(d);
    }

    @EventHandler(ignoreCancelled = true)
    public void alDisparar(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player p) || !enRpg(p)) return;
        modo.gastar(p, 10);
        if (!(e.getProjectile() instanceof AbstractArrow flecha)) return;
        ArmaRpg arco = ArmaRpg.de(e.getBow());
        DatosJugador d = plugin.almacen().de(p);
        double mult = arco != null ? arco.multiplicador(d) * (1 + 0.08 * ArmaRpg.mejora(e.getBow())) : 1 + 0.03 * d.destreza;
        flecha.setDamage(flecha.getDamage() * mult);
    }

    // ------------------------------------------------------------------ ciclo

    private void tick() {
        int ahora = Bukkit.getCurrentTick();
        Iterator<Map.Entry<UUID, Integer>> itr = aturdidoHasta.entrySet().iterator();
        while (itr.hasNext()) {
            Map.Entry<UUID, Integer> en = itr.next();
            Entity e = Bukkit.getEntity(en.getKey());
            if (e == null || !e.isValid()) {
                itr.remove();
                continue;
            }
            if (en.getValue() <= ahora) {
                itr.remove();
                if (e instanceof Mob m) m.setAware(true);
                continue;
            }
            e.getWorld().spawnParticle(Particle.CRIT, e.getLocation().add(0, e.getHeight() + 0.25, 0), 3, 0.25, 0.05, 0.25, 0);
        }
        // La postura se recupera si no te pegan durante 3 s.
        Iterator<Map.Entry<UUID, Double>> ip = postura.entrySet().iterator();
        while (ip.hasNext()) {
            Map.Entry<UUID, Double> en = ip.next();
            if (ahora - ultimoGolpePostura.getOrDefault(en.getKey(), 0) < 60) continue;
            double v = en.getValue() - 4;
            if (v <= 0) {
                ip.remove();
                ultimoGolpePostura.remove(en.getKey());
            } else {
                en.setValue(v);
            }
        }
    }

    public void olvidar(UUID id) {
        ultimoParry.remove(id);
        aturdidoHasta.remove(id);
        postura.remove(id);
        ultimoGolpePostura.remove(id);
    }
}
