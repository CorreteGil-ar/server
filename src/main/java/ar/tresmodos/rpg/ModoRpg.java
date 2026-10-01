package ar.tresmodos.rpg;

import ar.tresmodos.Claves;
import ar.tresmodos.DatosJugador;
import ar.tresmodos.Menu;
import ar.tresmodos.Modo;
import ar.tresmodos.ModoJuego;
import ar.tresmodos.TresModos;
import ar.tresmodos.Util;
import com.destroystokyo.paper.ParticleBuilder;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Campfire;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerExpChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Modo RPG/Souls «Las Tierras Cenicientas». Orquesta las piezas: clases y atributos, aguante y
 * éter, voltereta según la carga, hogueras, frascos, almas con mancha de sangre, el combate, los
 * estados, el árbol de habilidades y las habilidades de clase.
 */
public class ModoRpg implements ModoJuego, Listener, ObjetosRpg.ModoRpgHook {
    private static final double COSTO_ESQUIVE = 22;
    private static final NamespacedKey MOD_VIDA = new NamespacedKey("tresmodos", "rpg_vida_arbol");
    private static final NamespacedKey MOD_CARGA = new NamespacedKey("tresmodos", "rpg_sobrecarga");

    /** Carga del equipo respecto de la capacidad: define la voltereta. */
    public enum Carga {
        LIGERA("Ligera", "<green>", 1.25, 10), MEDIA("Media", "<yellow>", 1.0, 8),
        PESADA("Pesada", "<gold>", 0.7, 6), SOBRECARGA("Sobrecarga", "<red>", 0, 0);

        public final String nombre, color;
        public final double impulso;
        public final int iframes;

        Carga(String nombre, String color, double impulso, int iframes) {
            this.nombre = nombre;
            this.color = color;
            this.impulso = impulso;
            this.iframes = iframes;
        }
    }

    private record Caida(Location lugar, int tick, long almas) {}

    private final TresModos plugin;
    private final Buffs buffs = new Buffs();
    private final Estados estados;
    private final ObjetosRpg objetos;
    private final CombateRpg combate;
    private final ArbolHabilidades arbol;
    private final Habilidades habilidades;
    private final JefeAbismo jefe;

    private final Map<UUID, Double> aguante = new HashMap<>();
    private final Map<UUID, Double> eter = new HashMap<>();
    private final Map<UUID, Integer> ultimoGasto = new HashMap<>();
    private final Map<UUID, Integer> invulnerableHasta = new HashMap<>();
    private final Map<UUID, Integer> ultimoEsquive = new HashMap<>();
    private final Set<UUID> sinAliento = new HashSet<>();
    private final Map<UUID, BossBar> barrasEter = new HashMap<>();
    private final Map<UUID, Caida> caidos = new HashMap<>();

    public ModoRpg(TresModos plugin) {
        this.plugin = plugin;
        this.estados = new Estados(plugin);
        this.objetos = new ObjetosRpg(plugin, this);
        this.combate = new CombateRpg(plugin, this);
        this.arbol = new ArbolHabilidades(plugin, this);
        this.habilidades = new Habilidades(plugin, this);
        this.jefe = new JefeAbismo(plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickAguante, 1, 1);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickEter, 10, 10);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tickManchas, 10, 10);
    }

    /** Las piezas que también escuchan eventos (las registra TresModos). */
    public List<Listener> escuchas() {
        return List.of(this, objetos, combate, arbol, habilidades, jefe);
    }

    public Buffs buffs() { return buffs; }
    @Override public Estados estados() { return estados; }
    public ObjetosRpg objetos() { return objetos; }
    public CombateRpg combate() { return combate; }
    public ArbolHabilidades arbol() { return arbol; }
    public Habilidades habilidades() { return habilidades; }
    public JefeAbismo jefe() { return jefe; }

    private World mundo() {
        return plugin.mundos().de(Modo.RPG);
    }

    private static boolean enRpg(Entity e) {
        return Modo.de(e.getWorld()) == Modo.RPG;
    }

    private DatosJugador datos(Player p) {
        return plugin.almacen().de(p);
    }

    @Override public Modo modo() { return Modo.RPG; }
    @Override public boolean guardaEstado() { return true; }
    @Override public GameMode modoJuego() { return GameMode.SURVIVAL; }

    @Override
    public Location ubicacionEntrada(Player p) {
        return mundo().getSpawnLocation().toCenterLocation();
    }

    @Override
    public Location respawn(Player p) {
        Location h = hogueraDe(p);
        return h != null ? h : ubicacionEntrada(p);
    }

    @Override
    public Location hogueraDe(Player p) {
        DatosJugador d = datos(p);
        return d.hoguera != null && d.hoguera.getWorld() == mundo() ? d.hoguera : null;
    }

    // ------------------------------------------------------------------ atributos derivados

    public double aguanteMax(Player p) {
        return 100 + 10 * datos(p).aguante;
    }

    public double eterMax(Player p) {
        return 50 + 8 * datos(p).mente;
    }

    /** Capacidad de carga: 40 + 3 por punto de Aguante. */
    public double capacidad(Player p) {
        return 40 + 3 * datos(p).aguante;
    }

    /** Peso del equipo: armas de la barra, mano secundaria y armadura. */
    public double peso(Player p) {
        PlayerInventory inv = p.getInventory();
        double total = 0;
        for (int i = 0; i < 9; i++) {
            ArmaRpg a = ArmaRpg.de(inv.getItem(i));
            if (a != null) total += a.peso;
        }
        ItemStack mano2 = inv.getItemInOffHand();
        if (mano2.getType() == Material.SHIELD) total += 4;
        else if (ArmaRpg.de(mano2) != null) total += ArmaRpg.de(mano2).peso;
        for (ItemStack pieza : inv.getArmorContents()) total += pesoArmadura(pieza);
        return total;
    }

    private static double pesoArmadura(ItemStack it) {
        if (it == null) return 0;
        String n = it.getType().name();
        if (n.startsWith("LEATHER_")) return 1.5;
        if (n.startsWith("CHAINMAIL_") || n.startsWith("GOLDEN_")) return 3;
        if (n.startsWith("IRON_")) return 4.5;
        if (n.startsWith("DIAMOND_")) return 5.5;
        if (n.startsWith("NETHERITE_")) return 7;
        if (n.startsWith("TURTLE_")) return 2;
        return 0;
    }

    public Carga carga(Player p) {
        double r = peso(p) / capacidad(p);
        if (r < 0.3) return Carga.LIGERA;
        if (r < 0.7) return Carga.MEDIA;
        if (r <= 1.0) return Carga.PESADA;
        return Carga.SOBRECARGA;
    }

    @Override
    public void prepararAtributos(Player p) {
        DatosJugador d = datos(p);
        AttributeInstance vida = p.getAttribute(Attribute.MAX_HEALTH);
        if (vida != null) {
            vida.setBaseValue(20 + 2 * d.vigor);
            vida.removeModifier(MOD_VIDA);
            int nv = d.rama(Rama.VIDA);
            if (nv > 0) vida.addModifier(new AttributeModifier(MOD_VIDA, 0.08 * nv,
                    AttributeModifier.Operation.ADD_SCALAR, EquipmentSlotGroup.ANY));
        }
        actualizarCarga(p);
    }

    /** Con sobrecarga se camina más lento. */
    private void actualizarCarga(Player p) {
        AttributeInstance vel = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (vel == null) return;
        boolean sobre = carga(p) == Carga.SOBRECARGA;
        boolean tiene = vel.getModifier(MOD_CARGA) != null;
        if (sobre && !tiene) {
            vel.addModifier(new AttributeModifier(MOD_CARGA, -0.35, AttributeModifier.Operation.ADD_SCALAR, EquipmentSlotGroup.ANY));
        } else if (!sobre && tiene) {
            vel.removeModifier(MOD_CARGA);
        }
    }

    // ------------------------------------------------------------------ entrada y salida

    @Override
    public void kitInicial(Player p) {
        // El kit de verdad lo da la clase al elegirla; acá sólo lo común.
        DatosJugador d = datos(p);
        if (d.clase != null) {
            ClaseRpg c = ClaseRpg.de(d.clase);
            if (c != null) c.darKit(p);
        }
        darFrascos(p);
        p.getInventory().setItem(8, new ItemStack(Material.TORCH, 16));
    }

    private void darFrascos(Player p) {
        DatosJugador d = datos(p);
        int eterC = Math.min(d.eterCargas, d.estusMax - 1);
        PlayerInventory inv = p.getInventory();
        inv.setItem(6, ObjetosRpg.estus(d.estusMax - eterC, d.estusMax - eterC));
        inv.setItem(7, ObjetosRpg.eter(eterC, eterC));
    }

    @Override
    public void alEntrar(Player p, boolean primeraVez) {
        aguante.put(p.getUniqueId(), aguanteMax(p));
        eter.put(p.getUniqueId(), eterMax(p));
        mostrarBarra(p);
        DatosJugador d = datos(p);
        if (primeraVez || d.clase == null) {
            Util.titulo(p, "<gold><bold>LAS TIERRAS CENICIENTAS", "<gray>Morir cuesta caro", 800, 3500, 1000);
            Util.msg(p, "<gold>RPG: <gray>la barra de experiencia es tu <green>aguante<gray> y la barra de arriba, tu "
                    + "<aqua>éter<gray>. <white>F<gray> rueda (según el peso que cargues), <white>Q<gray> con el arma usa la "
                    + "habilidad de tu clase y <white>Shift+Q<gray> la definitiva. Clic derecho a una <gold>hoguera<gray> para "
                    + "descansar y subir de nivel. Si morís, tus almas quedan donde caíste.");
        } else {
            Util.titulo(p, "<gold><bold>RPG", "<gray>" + Util.num(d.almas) + " almas", 200, 1500, 500);
        }
        if (d.clase == null) Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && enRpg(p)) abrirClases(p);
        }, 40);
    }

    @Override
    public void alSalir(Player p) {
        UUID id = p.getUniqueId();
        aguante.remove(id);
        eter.remove(id);
        sinAliento.remove(id);
        invulnerableHasta.remove(id);
        ultimoEsquive.remove(id);
        BossBar b = barrasEter.remove(id);
        if (b != null) p.hideBossBar(b);
        jefe.ocultar(p);
        estados.olvidar(id);
        buffs.olvidar(id);
        objetos.olvidar(id);
        combate.olvidar(id);
        habilidades.olvidar(id);
        AttributeInstance vel = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (vel != null) vel.removeModifier(MOD_CARGA);
        AttributeInstance vida = p.getAttribute(Attribute.MAX_HEALTH);
        if (vida != null) vida.removeModifier(MOD_VIDA);
    }

    @Override
    public void alReaparecer(Player p) {
        prepararAtributos(p);
        p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
        rellenarFrascos(p);
        estados.limpiar(p);
        aguante.put(p.getUniqueId(), aguanteMax(p));
        eter.put(p.getUniqueId(), eterMax(p));
        DatosJugador d = datos(p);
        Util.titulo(p, "<dark_red>HAS MUERTO", d.mancha != null ? "<gray>Tus almas te esperan donde caíste" : "",
                600, 2000, 1200);
    }

    public void apagar() {
        jefe.apagar();
        habilidades.apagar();
        for (Map.Entry<UUID, BossBar> en : barrasEter.entrySet()) {
            Player p = Bukkit.getPlayer(en.getKey());
            if (p != null) p.hideBossBar(en.getValue());
        }
    }

    // ------------------------------------------------------------------ clases

    public void abrirClases(Player p) {
        DatosJugador d = datos(p);
        if (d.clase != null) {
            Util.msg(p, "<gray>Ya sos " + ClaseRpg.de(d.clase).nombre + ".");
            return;
        }
        Menu m = new Menu(3, "<dark_gray>Elegí tu clase");
        int[] slots = {10, 11, 12, 14, 15, 16};
        ClaseRpg[] cs = ClaseRpg.values();
        for (int i = 0; i < cs.length; i++) {
            ClaseRpg c = cs[i];
            List<String> lore = new ArrayList<>();
            lore.add(c.estilo);
            lore.add("");
            StringBuilder at = new StringBuilder();
            for (Atributo a : Atributo.values()) {
                int v = c.atributos.get(a);
                if (v > 0) at.append(a.color).append(a.abreviatura()).append(" ").append(v).append("  ");
            }
            lore.add(at.toString().trim());
            lore.add("<aqua>Q: <white>" + c.qBase + " <gray>— " + c.qDesc);
            lore.add("<red>Daño: <white>" + c.tipos.get(0).nombre + " <gray>o <white>" + c.tipos.get(1).nombre);
            lore.add("");
            lore.add("<yellow>Clic para elegir (no se cambia)");
            m.poner(slots[i], Util.item(c.icono, "<gold><bold>" + c.nombre, lore.toArray(new String[0])), pl -> elegirClase(pl, c));
        }
        m.abrir(p);
    }

    private void elegirClase(Player p, ClaseRpg c) {
        DatosJugador d = datos(p);
        if (d.clase != null) return;
        d.clase = c.name();
        // Los que ya habían subido atributos los conservan si eran más altos.
        for (Atributo a : Atributo.values()) d.setAtributo(a, Math.max(d.atributo(a), c.atributos.get(a)));
        PlayerInventory inv = p.getInventory();
        inv.clear();
        c.darKit(p);
        darFrascos(p);
        inv.setItem(8, new ItemStack(Material.TORCH, 16));
        prepararAtributos(p);
        p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
        aguante.put(p.getUniqueId(), aguanteMax(p));
        eter.put(p.getUniqueId(), eterMax(p));
        p.closeInventory();
        Util.titulo(p, "<gold><bold>" + c.nombre.toUpperCase(), "<gray>" + c.estilo, 300, 2500, 700);
        p.playSound(p, Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1f, 0.8f);
        Util.msg(p, "<gray>Tu habilidad: <aqua>" + c.qBase + "<gray> (Q con el arma en la mano). En las hogueras se gastan "
                + "almas en atributos y brasas en el <gold>árbol de habilidades<gray>.");
    }

    // ------------------------------------------------------------------ aguante

    public void gastar(Player p, double cant) {
        if (buffs.tiene(p, Buffs.Tipo.AGUANTE_INFINITO)) return;
        aguante.compute(p.getUniqueId(), (u, v) -> Math.max(0, (v == null ? aguanteMax(p) : v) - cant));
        ultimoGasto.put(p.getUniqueId(), Bukkit.getCurrentTick());
    }

    public double aguanteDe(Player p) {
        return aguante.computeIfAbsent(p.getUniqueId(), u -> aguanteMax(p));
    }

    public void llenarAguante(Player p) {
        aguante.put(p.getUniqueId(), aguanteMax(p));
        sinAliento.remove(p.getUniqueId());
        p.setFoodLevel(20);
    }

    private void tickAguante() {
        int ahora = Bukkit.getCurrentTick();
        for (Player p : mundo().getPlayers()) {
            if (p.isDead() || p.getGameMode() != GameMode.SURVIVAL) continue;
            UUID id = p.getUniqueId();
            double max = aguanteMax(p);
            double s = aguanteDe(p);
            double regen = 1.5 * (1 + 0.06 * datos(p).rama(Rama.BENDICION));
            if (p.isSprinting() && !buffs.tiene(p, Buffs.Tipo.AGUANTE_INFINITO)) {
                s -= 0.7;
                ultimoGasto.put(id, ahora);
            } else if (ahora - ultimoGasto.getOrDefault(id, 0) > 12) {
                s += p.isBlocking() ? regen / 3 : regen;
            }
            s = Math.max(0, Math.min(max, s));
            aguante.put(id, s);
            if (s <= 1 && sinAliento.add(id)) {
                p.setFoodLevel(6); // con 6 de hambre no se puede correr
                p.setSprinting(false);
            } else if (s >= max * 0.3 && sinAliento.remove(id)) {
                p.setFoodLevel(20);
            }
            float exp = (float) (s / max);
            if (Math.abs(p.getExp() - exp) > 0.004f) p.setExp(Math.min(0.999f, exp));
            int nivel = datos(p).nivelRpg();
            if (p.getLevel() != nivel) p.setLevel(nivel);
        }
    }

    @EventHandler
    public void alHambre(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && enRpg(p)) e.setCancelled(true);
    }

    @EventHandler
    public void alGanarExp(PlayerExpChangeEvent e) {
        if (enRpg(e.getPlayer())) e.setAmount(0);
    }

    @EventHandler
    public void alAbrirMesa(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player p) || !enRpg(p)) return;
        InventoryType t = e.getInventory().getType();
        if (t == InventoryType.ENCHANTING || t == InventoryType.ANVIL) {
            e.setCancelled(true);
            Util.barra(p, "<dark_purple>Esa magia no funciona en estas tierras. El herrero del Santuario mejora armas.");
        }
    }

    // ------------------------------------------------------------------ éter

    public double eterDe(Player p) {
        return eter.computeIfAbsent(p.getUniqueId(), u -> eterMax(p));
    }

    /** Descuenta éter; si no alcanza avisa y devuelve false. */
    public boolean gastarEter(Player p, double cant) {
        double e = eterDe(p);
        if (e < cant) {
            Util.barra(p, "<blue>Te falta éter <gray>(" + (int) e + "/" + (int) cant + ")");
            p.playSound(p, Sound.BLOCK_FIRE_EXTINGUISH, 0.5f, 1.6f);
            return false;
        }
        eter.put(p.getUniqueId(), e - cant);
        return true;
    }

    public void sumarEter(Player p, double cant) {
        eter.put(p.getUniqueId(), Math.min(eterMax(p), eterDe(p) + cant));
    }

    private void mostrarBarra(Player p) {
        BossBar b = barrasEter.computeIfAbsent(p.getUniqueId(),
                u -> BossBar.bossBar(Util.mm("<aqua>Éter"), 1f, BossBar.Color.BLUE, BossBar.Overlay.NOTCHED_10));
        p.showBossBar(b);
    }

    private void tickEter() {
        for (Player p : mundo().getPlayers()) {
            if (p.isDead()) continue;
            double max = eterMax(p);
            // Medio punto por segundo; más con la rama de Bendiciones.
            double e = Math.min(max, eterDe(p) + 0.25 * (1 + 0.2 * datos(p).rama(Rama.BENDICION)));
            eter.put(p.getUniqueId(), e);
            BossBar b = barrasEter.get(p.getUniqueId());
            if (b == null) {
                mostrarBarra(p);
                b = barrasEter.get(p.getUniqueId());
            }
            b.progress((float) Math.max(0, Math.min(1, e / max)));
            b.name(Util.mm("<aqua>Éter <white>" + (int) e + "<gray>/" + (int) max + estados.resumen(p)));
            actualizarCarga(p);
        }
    }

    // ------------------------------------------------------------------ voltereta

    @EventHandler
    public void alEsquivar(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (!enRpg(p)) return;
        e.setCancelled(true);
        int ahora = Bukkit.getCurrentTick();
        if (ahora - ultimoEsquive.getOrDefault(p.getUniqueId(), -100) < 12) return;
        if (combate.aturdido(p)) return;
        Carga c = carga(p);
        if (c == Carga.SOBRECARGA) {
            Util.barra(p, "<red>Sobrecarga: no podés rodar. Sacate peso (" + (int) peso(p) + "/" + (int) capacidad(p) + ")");
            return;
        }
        if (aguanteDe(p) < COSTO_ESQUIVE && !buffs.tiene(p, Buffs.Tipo.AGUANTE_INFINITO)) {
            Util.barra(p, "<red>Sin aguante para rodar");
            return;
        }
        ultimoEsquive.put(p.getUniqueId(), ahora);
        gastar(p, COSTO_ESQUIVE);

        double yaw = Math.toRadians(p.getLocation().getYaw());
        Vector frente = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
        Vector derecha = new Vector(-frente.getZ(), 0, frente.getX());
        Input in = p.getCurrentInput();
        Vector dir = new Vector();
        if (in.isForward()) dir.add(frente);
        if (in.isBackward()) dir.subtract(frente);
        if (in.isRight()) dir.add(derecha);
        if (in.isLeft()) dir.subtract(derecha);
        if (dir.lengthSquared() < 0.01) dir = frente.clone().multiply(-1);
        p.setVelocity(dir.normalize().multiply(1.05 * c.impulso).setY(0.22));
        int iframes = c.iframes * (buffs.tiene(p, Buffs.Tipo.INSTINTO) ? 2 : 1);
        invulnerableHasta.put(p.getUniqueId(), ahora + iframes);
        p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation().add(0, 0.2, 0), 8, 0.3, 0.05, 0.3, 0.02);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.5f, c == Carga.PESADA ? 1.1f : 1.7f);
        if (buffs.tiene(p, Buffs.Tipo.INSTINTO)) {
            // Señuelo: los enemigos que te seguían pierden el rastro un momento.
            for (Entity en : p.getNearbyEntities(8, 4, 8)) {
                if (en instanceof org.bukkit.entity.Mob m && p.equals(m.getTarget())) m.setTarget(null);
            }
            p.getWorld().spawnParticle(Particle.LARGE_SMOKE, p.getLocation().add(0, 1, 0), 20, 0.3, 0.6, 0.3, 0.01);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void alRecibirDanio(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !enRpg(p)) return;
        Integer hasta = invulnerableHasta.get(p.getUniqueId());
        if (hasta != null && Bukkit.getCurrentTick() <= hasta && e.getCause() != EntityDamageEvent.DamageCause.VOID) {
            e.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ enemigos y almas

    @EventHandler(ignoreCancelled = true)
    public void alAparecerMob(CreatureSpawnEvent e) {
        if (!enRpg(e.getEntity()) || !(e.getEntity() instanceof Enemy)) return;
        if (e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return;
        LivingEntity m = e.getEntity();
        AttributeInstance vida = m.getAttribute(Attribute.MAX_HEALTH);
        if (vida != null) {
            vida.setBaseValue(vida.getBaseValue() * 1.5);
            m.setHealth(vida.getValue());
        }
        AttributeInstance fuerza = m.getAttribute(Attribute.ATTACK_DAMAGE);
        if (fuerza != null) fuerza.setBaseValue(fuerza.getBaseValue() * 1.3);
    }

    @EventHandler
    public void alMorirEntidad(EntityDeathEvent e) {
        LivingEntity v = e.getEntity();
        if (!enRpg(v) || v instanceof Player) return;
        e.setDroppedExp(0);
        estados.olvidar(v.getUniqueId());
        buffs.olvidar(v.getUniqueId());
        combate.olvidar(v.getUniqueId());
        if (v.getPersistentDataContainer().has(Claves.JEFE) || v.getPersistentDataContainer().has(Claves.SIERVO)
                || v.getPersistentDataContainer().has(Claves.INVOCACION)) return;
        Player asesino = v.getKiller();
        if (asesino == null) return;
        AttributeInstance vida = v.getAttribute(Attribute.MAX_HEALTH);
        long almas = Math.round((vida == null ? 10 : vida.getValue()) * 2.5 * (1 + 0.5 * datos(asesino).ciclo));
        datos(asesino).almas += almas;
        sumarEter(asesino, 3);
        Util.barra(asesino, "<gold>+" + almas + " almas");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void alMorir(PlayerDeathEvent e) {
        Player p = e.getPlayer();
        if (!enRpg(p)) return;
        e.setKeepInventory(true);
        e.getDrops().clear();
        e.setKeepLevel(true);
        e.setDroppedExp(0);
        DatosJugador d = datos(p);
        caidos.put(p.getUniqueId(), new Caida(p.getLocation(), Bukkit.getCurrentTick(), d.almas));
        if (d.mancha != null && d.almasMancha > 0) {
            Util.msg(p, "<dark_red>Perdiste " + Util.num(d.almasMancha) + " almas para siempre.");
        }
        if (d.almas > 0) {
            d.mancha = p.getLocation();
            d.almasMancha = d.almas;
            d.almas = 0;
        } else {
            d.mancha = null;
            d.almasMancha = 0;
        }
        buffs.olvidar(p.getUniqueId());
        net.kyori.adventure.text.Component msg = e.deathMessage();
        e.deathMessage(null);
        if (msg != null) for (Player o : mundo().getPlayers()) o.sendMessage(msg.color(net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY));
    }

    /** Resurrección del Clérigo: el último aliado caído (hace menos de 30 s) vuelve donde cayó, con sus almas. */
    public boolean resucitar(Player clerigo) {
        int ahora = Bukkit.getCurrentTick();
        UUID elegido = null;
        int mejor = -1;
        for (Map.Entry<UUID, Caida> en : caidos.entrySet()) {
            if (en.getKey().equals(clerigo.getUniqueId())) continue;
            Caida c = en.getValue();
            if (ahora - c.tick() > 600 || c.tick() <= mejor) continue;
            Player o = Bukkit.getPlayer(en.getKey());
            if (o == null || !enRpg(o) || o.isDead()) continue;
            elegido = en.getKey();
            mejor = c.tick();
        }
        if (elegido == null) return false;
        Player o = Bukkit.getPlayer(elegido);
        Caida c = caidos.remove(elegido);
        DatosJugador d = datos(o);
        if (d.mancha != null && d.almasMancha == c.almas()) {
            d.almas += d.almasMancha;
            d.mancha = null;
            d.almasMancha = 0;
        }
        o.teleport(c.lugar());
        double max = o.getAttribute(Attribute.MAX_HEALTH).getValue();
        o.setHealth(Math.max(1, max * 0.5));
        o.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, o.getLocation().add(0, 1, 0), 60, 0.4, 0.8, 0.4, 0.3);
        o.playSound(o, Sound.ITEM_TOTEM_USE, 1f, 1f);
        Util.titulo(o, "<gold><bold>RESUCITADO", "<gray>por " + clerigo.getName(), 200, 2000, 600);
        return true;
    }

    // ------------------------------------------------------------------ manchas de sangre

    private void tickManchas() {
        for (Player p : mundo().getPlayers()) {
            if (p.isDead()) continue;
            DatosJugador d = datos(p);
            if (d.mancha == null || d.mancha.getWorld() != mundo()) continue;
            if (!d.mancha.isChunkLoaded()) continue;
            new ParticleBuilder(Particle.DUST).location(d.mancha.clone().add(0, 0.3, 0)).count(10).offset(0.25, 0.2, 0.25)
                    .data(new Particle.DustOptions(Color.fromRGB(150, 0, 0), 1.3f)).receivers(p).force(true).spawn();
            new ParticleBuilder(Particle.SOUL).location(d.mancha.clone().add(0, 0.6, 0)).count(2).offset(0.2, 0.3, 0.2)
                    .speed(0.01).receivers(p).force(true).spawn();
            if (p.getLocation().distanceSquared(d.mancha) < 1.8 * 1.8) {
                d.almas += d.almasMancha;
                Util.titulo(p, "<gold><bold>ALMAS RECUPERADAS", "<gold>+" + Util.num(d.almasMancha), 200, 1800, 500);
                p.playSound(p, Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.8f);
                p.playSound(p, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.6f);
                d.mancha = null;
                d.almasMancha = 0;
            }
        }
    }

    // ------------------------------------------------------------------ hogueras

    @EventHandler(priority = EventPriority.HIGH)
    public void alInteractuar(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (!enRpg(p) || e.getHand() != EquipmentSlot.HAND) return;
        ItemStack mano = e.getItem();
        if ((e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)
                && Util.marca(mano, Claves.CAMPANA) != null) {
            e.setCancelled(true);
            if (jefe.invocar(p)) mano.setAmount(mano.getAmount() - 1);
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        Block b = e.getClickedBlock();
        if (b.getType() != Material.CAMPFIRE && b.getType() != Material.SOUL_CAMPFIRE) return;
        if (mano != null && (mano.getType().isEdible() || mano.getType() == Material.FLINT_AND_STEEL
                || mano.getType() == Material.FIRE_CHARGE)) return;
        e.setCancelled(true);
        if (!(b.getBlockData() instanceof Campfire c) || !c.isLit()) {
            Util.barra(p, "<gray>La hoguera está apagada. Encendela con un encendedor.");
            return;
        }
        if (datos(p).clase == null) {
            abrirClases(p);
            return;
        }
        descansar(p, b);
        abrirHoguera(p);
    }

    private void descansar(Player p, Block fuego) {
        DatosJugador d = datos(p);
        Location lugar = lugarJunto(fuego);
        boolean nueva = d.hoguera == null || d.hoguera.getWorld() != lugar.getWorld()
                || d.hoguera.distanceSquared(lugar) > 4;
        d.hoguera = lugar;
        d.hogueras.add(fuego.getX() + "," + fuego.getY() + "," + fuego.getZ());
        prepararAtributos(p);
        p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
        p.setFireTicks(0);
        estados.limpiar(p);
        rellenarFrascos(p);
        llenarAguante(p);
        eter.put(p.getUniqueId(), eterMax(p));
        p.getWorld().spawnParticle(Particle.FLAME, fuego.getLocation().add(0.5, 0.8, 0.5), 20, 0.25, 0.4, 0.25, 0.02);
        p.playSound(p, Sound.ITEM_FIRECHARGE_USE, 0.7f, 0.7f);
        if (nueva) {
            Util.titulo(p, "<gold><bold>HOGUERA ENCENDIDA", "", 400, 1800, 800);
            p.playSound(p, Sound.BLOCK_BEACON_POWER_SELECT, 0.8f, 0.6f);
        }
    }

    /** Un lugar libre al lado de la fogata para reaparecer (no encima: quema). */
    private static Location lugarJunto(Block fuego) {
        for (BlockFace f : new BlockFace[]{BlockFace.SOUTH, BlockFace.EAST, BlockFace.NORTH, BlockFace.WEST}) {
            Block b = fuego.getRelative(f);
            if (b.isPassable() && b.getRelative(BlockFace.UP).isPassable() && b.getRelative(BlockFace.DOWN).isSolid()) {
                Location l = b.getLocation().add(0.5, 0, 0.5);
                l.setDirection(fuego.getLocation().add(0.5, 0, 0.5).toVector().subtract(l.toVector()));
                return l;
            }
        }
        return fuego.getLocation().add(0.5, 1, 0.5);
    }

    public long costoNivel(DatosJugador d) {
        long n = d.nivelRpg();
        return 60 + 25 * n * n;
    }

    public void abrirHoguera(Player p) {
        DatosJugador d = datos(p);
        long costo = costoNivel(d);
        Menu m = new Menu(4, "<dark_gray>Hoguera · Nivel " + d.nivelRpg() + " · " + Util.num(d.almas) + " almas");
        int slot = 10;
        for (Atributo a : Atributo.values()) {
            m.poner(slot++, Util.item(a.icono, a.color + "<bold>" + a.nombre + " <white>" + d.atributo(a), a.desc,
                    "", "<gold>Costo: " + Util.num(costo) + " almas", "<yellow>Clic: +1"), pl -> subir(pl, a));
        }
        int total = d.estusMax;
        int eterC = Math.min(d.eterCargas, total - 1);
        long costoFrasco = 1500L * (total - 2);
        ItemStack iconoEstus = ObjetosRpg.estus(total - eterC, total - eterC);
        iconoEstus.lore(List.of(
                Util.mmItem("<gray>Frascos totales: <white>" + total + "<gray>/10"),
                Util.mmItem(total >= 10 ? "<gray>Máximo alcanzado" : "<yellow>Clic: +1 frasco <gold>(" + Util.num(costoFrasco) + " almas)"),
                Util.mmItem("<yellow>Shift+clic: pasar uno de Éter a Estus")));
        m.poner(28, iconoEstus, pl -> {
            if (pl.isSneaking()) repartir(pl, -1);
            else mejorarFrascos(pl);
        });
        ItemStack iconoEter = ObjetosRpg.eter(eterC, eterC);
        iconoEter.lore(List.of(Util.mmItem("<gray>Éter: <white>" + (int) eterMax(p) + " <gray>máximo"),
                Util.mmItem("<yellow>Clic: pasar uno de Estus a Éter")));
        m.poner(29, iconoEter, pl -> repartir(pl, 1));
        ClaseRpg c = ClaseRpg.de(d.clase);
        int brasas = arbol.brasasLibres(d);
        m.poner(31, Util.item(Material.BLAZE_POWDER, "<gold><bold>Árbol de habilidades",
                "<gray>Clase: <white>" + (c == null ? "-" : c.nombre),
                "<gray>Brasas libres: <gold>" + brasas,
                "", "<yellow>Clic para abrir"), arbol::abrir);
        Carga cg = carga(p);
        m.poner(33, Util.item(Material.IRON_CHESTPLATE, "<white><bold>Equipo",
                "<gray>Peso: <white>" + Math.round(peso(p) * 10) / 10.0 + "<gray>/" + (int) capacidad(p),
                "<gray>Carga: " + cg.color + cg.nombre,
                "<gray>Ligera: rodás lejos. Pesada: rodás corto.",
                "<gray>Sobrecarga: no podés rodar."), pl -> { });
        m.poner(35, Util.item(Material.BELL, "<dark_red><bold>Campana del Jefe", "Despierta al Caballero del Abismo.",
                "Mejor ir acompañado.", "<gold>Costo: 1.000 almas"), pl -> {
            if (gastarAlmas(pl, 1000)) {
                pl.getInventory().addItem(JefeAbismo.campana());
                pl.playSound(pl, Sound.BLOCK_BELL_USE, 1f, 0.6f);
            }
            abrirHoguera(pl);
        });
        m.poner(27, Util.item(Material.OAK_DOOR, "<white>Volver al lobby"), pl -> {
            pl.closeInventory();
            plugin.cambio().cambiar(pl, Modo.LOBBY, false);
        });
        m.abrir(p);
    }

    public boolean gastarAlmas(Player p, long cant) {
        DatosJugador d = datos(p);
        if (d.almas < cant) {
            Util.msg(p, "<red>Te faltan " + Util.num(cant - d.almas) + " almas.");
            p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
            return false;
        }
        d.almas -= cant;
        return true;
    }

    private void subir(Player p, Atributo a) {
        DatosJugador d = datos(p);
        if (d.atributo(a) >= 60) {
            Util.msg(p, "<gray>" + a.nombre + " ya está al máximo.");
            return;
        }
        if (!gastarAlmas(p, costoNivel(d))) return;
        d.setAtributo(a, d.atributo(a) + 1);
        d.nivelesComprados++;
        prepararAtributos(p);
        p.setHealth(p.getAttribute(Attribute.MAX_HEALTH).getValue());
        if (d.nivelRpg() % 3 == 0) Util.msg(p, "<gold>Nivel " + d.nivelRpg() + ": <yellow>+1 brasa <gray>para el árbol.");
        p.playSound(p, Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
        abrirHoguera(p);
    }

    private void mejorarFrascos(Player p) {
        DatosJugador d = datos(p);
        if (d.estusMax >= 10) return;
        if (!gastarAlmas(p, 1500L * (d.estusMax - 2))) return;
        d.estusMax++;
        rellenarFrascos(p);
        p.playSound(p, Sound.BLOCK_BREWING_STAND_BREW, 1f, 1f);
        abrirHoguera(p);
    }

    /** Pasa un frasco de Estus a Éter (+1) o al revés (-1). Siempre queda al menos uno de Estus. */
    private void repartir(Player p, int haciaEter) {
        DatosJugador d = datos(p);
        int nuevo = d.eterCargas + haciaEter;
        if (nuevo < 0 || nuevo > d.estusMax - 1) return;
        d.eterCargas = nuevo;
        rellenarFrascos(p);
        p.playSound(p, Sound.ITEM_BOTTLE_FILL, 1f, 1f);
        abrirHoguera(p);
    }

    // ------------------------------------------------------------------ frascos

    private void rellenarFrascos(Player p) {
        DatosJugador d = datos(p);
        int eterC = Math.min(d.eterCargas, d.estusMax - 1);
        int estusC = d.estusMax - eterC;
        PlayerInventory inv = p.getInventory();
        boolean hayEstus = false, hayEter = false;
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || !it.hasItemMeta()) continue;
            var pdc = it.getItemMeta().getPersistentDataContainer();
            if (pdc.has(Claves.ESTUS)) {
                inv.setItem(i, ObjetosRpg.estus(estusC, estusC));
                hayEstus = true;
            } else if (pdc.has(Claves.ETER)) {
                inv.setItem(i, ObjetosRpg.eter(eterC, eterC));
                hayEter = true;
            }
        }
        if (!hayEstus) inv.addItem(ObjetosRpg.estus(estusC, estusC));
        if (!hayEter) inv.addItem(ObjetosRpg.eter(eterC, eterC));
    }

    private static int cargas(Player p, NamespacedKey clave) {
        for (ItemStack it : p.getInventory()) {
            if (it == null || !it.hasItemMeta()) continue;
            Integer c = it.getItemMeta().getPersistentDataContainer().get(clave, PersistentDataType.INTEGER);
            if (c != null) return c;
        }
        return -1;
    }

    @EventHandler
    public void alBeber(PlayerItemConsumeEvent e) {
        ItemStack it = e.getItem();
        if (!it.hasItemMeta()) return;
        var pdc = it.getItemMeta().getPersistentDataContainer();
        boolean esEstus = pdc.has(Claves.ESTUS);
        if (!esEstus && !pdc.has(Claves.ETER)) return;
        Integer cargas = pdc.get(esEstus ? Claves.ESTUS : Claves.ETER, PersistentDataType.INTEGER);
        if (cargas == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        DatosJugador d = datos(p);
        int eterC = Math.min(d.eterCargas, d.estusMax - 1);
        int max = esEstus ? d.estusMax - eterC : eterC;
        if (cargas <= 0) {
            Util.barra(p, "<gray>El frasco está vacío. Descansá en una hoguera.");
        } else if (esEstus) {
            double vidaMax = p.getAttribute(Attribute.MAX_HEALTH).getValue();
            double cura = 0.45 + 0.05 * d.rama(Rama.BENDICION);
            p.setHealth(Math.min(vidaMax, p.getHealth() + vidaMax * cura));
            p.getWorld().spawnParticle(Particle.FLAME, p.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.01);
            p.playSound(p, Sound.ITEM_HONEY_BOTTLE_DRINK, 1f, 0.8f);
            p.getInventory().setItem(e.getHand(), ObjetosRpg.estus(cargas - 1, max));
        } else {
            sumarEter(p, eterMax(p) * 0.5);
            p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, p.getLocation().add(0, 1, 0), 15, 0.3, 0.5, 0.3, 0.01);
            p.playSound(p, Sound.ITEM_HONEY_BOTTLE_DRINK, 1f, 1.3f);
            p.getInventory().setItem(e.getHand(), ObjetosRpg.eter(cargas - 1, max));
        }
        Bukkit.getScheduler().runTask(plugin, p::updateInventory);
    }

    // ------------------------------------------------------------------ sidebar

    @Override
    public String tituloSidebar(Player p) {
        return "<gold><bold>RPG</bold> <gray>Tierras Cenicientas";
    }

    @Override
    public List<String> lineasSidebar(Player p) {
        DatosJugador d = datos(p);
        ClaseRpg c = ClaseRpg.de(d.clase);
        List<String> l = new ArrayList<>();
        l.add("<white>" + (c == null ? "Sin clase" : c.nombre) + (d.ciclo > 0 ? " <dark_red>Ciclo+" + d.ciclo : ""));
        l.add("<white>Almas: <gold>" + Util.num(d.almas));
        l.add("<white>Nivel: <light_purple>" + d.nivelRpg() + " <dark_gray>(próx. " + Util.num(costoNivel(d)) + ")");
        l.add("");
        l.add("<red>VIG " + d.vigor + " <aqua>MEN " + d.mente + " <green>AGU " + d.aguante);
        l.add("<gold>FUE " + d.fuerza + " <yellow>DES " + d.destreza);
        l.add("<blue>INT " + d.inteligencia + " <light_purple>FE " + d.fe);
        int ce = cargas(p, Claves.ESTUS), cx = cargas(p, Claves.ETER);
        l.add("<gold>Estus " + (ce < 0 ? "-" : ce) + " <blue>Éter " + (cx < 0 ? "-" : cx));
        Carga cg = carga(p);
        l.add("<gray>Carga: " + cg.color + cg.nombre);
        if (d.mancha != null && d.mancha.getWorld() == p.getWorld()) {
            l.add("");
            l.add("<dark_red>Mancha: <white>" + Util.num(d.almasMancha) + " <gray>a " + (int) d.mancha.distance(p.getLocation()) + " m");
        }
        return l;
    }
}
