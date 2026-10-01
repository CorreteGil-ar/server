package ar.tresmodos.rpg;

import ar.tresmodos.Claves;
import ar.tresmodos.DatosJugador;
import ar.tresmodos.Menu;
import ar.tresmodos.Modo;
import ar.tresmodos.TresModos;
import ar.tresmodos.Util;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Los NPC del Santuario del Último Fuego: el herrero (mejoras +1 a +10), el mercader, la Guardiana
 * del Fuego (niveles y Ciclo+) y el altar donde se canjean las almas de jefe.
 */
public class Santuario implements Listener {
    private final TresModos plugin;
    private final ModoRpg modo;

    public Santuario(TresModos plugin, ModoRpg modo) {
        this.plugin = plugin;
        this.modo = modo;
        Bukkit.getScheduler().runTaskLater(plugin, this::ponerNpcs, 10);
    }

    private void ponerNpcs() {
        MundoRpg m = modo.mundoRpg();
        World w = plugin.mundos().de(Modo.RPG);
        Location base = m.lugarSantuario(0, 0);
        w.getChunkAt(base);
        for (Entity e : w.getNearbyEntities(base, 16, 8, 16)) {
            if (e.getPersistentDataContainer().has(Claves.NPC)) e.remove();
        }
        npc(m.lugarSantuario(6, 0), "herrero", "<gold>Andrés, el Herrero", Villager.Profession.WEAPONSMITH);
        npc(m.lugarSantuario(0, -6), "guardiana", "<light_purple>La Guardiana del Fuego", Villager.Profession.CLERIC);
        npc(m.lugarSantuario(-6, 0), "mercader", "<green>Ulrich, el Mercader", Villager.Profession.LIBRARIAN);
    }

    private void npc(Location l, String id, String nombre, Villager.Profession prof) {
        l.getWorld().spawn(l, Villager.class, v -> {
            v.setProfession(prof);
            v.setVillagerType(Villager.Type.TAIGA);
            v.setVillagerLevel(5);
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setCollidable(false);
            v.setPersistent(false);
            v.customName(Util.mm(nombre));
            v.setCustomNameVisible(true);
            v.getPersistentDataContainer().set(Claves.NPC, PersistentDataType.STRING, id);
        });
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void alHablar(PlayerInteractEntityEvent e) {
        String id = e.getRightClicked().getPersistentDataContainer().get(Claves.NPC, PersistentDataType.STRING);
        if (id == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (plugin.almacen().de(p).clase == null) {
            modo.abrirClases(p);
            return;
        }
        switch (id) {
            case "herrero" -> herrero(p);
            case "guardiana" -> guardiana(p);
            case "mercader" -> mercader(p);
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void alTocarAltar(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) return;
        Block b = e.getClickedBlock();
        if (b.getType() != Material.LODESTONE || Modo.de(b.getWorld()) != Modo.RPG) return;
        MundoRpg.Hoguera h = modo.mundoRpg().hoguera("santuario");
        if (h == null || b.getX() != h.x() || b.getY() != h.y() || b.getZ() != h.z() + 7) return;
        e.setCancelled(true);
        altar(e.getPlayer());
    }

    // ------------------------------------------------------------------ herrero

    /** Costo de pasar de +n a +n+1: almas, fragmentos y escamas. */
    public static int[] costoMejora(int n) {
        if (n < 6) return new int[]{400 * (n + 1), 2 * (n + 1), 0};
        return new int[]{1000 * (n + 1), 6, n - 5};
    }

    public void herrero(Player p) {
        ItemStack mano = p.getInventory().getItemInMainHand();
        ArmaRpg a = ArmaRpg.de(mano);
        Menu m = new Menu(3, "<dark_gray>Herrero");
        if (a == null) {
            m.poner(13, Util.item(Material.ANVIL, "<gold><bold>Mejorar arma", "<gray>Traé en la mano el arma que querés mejorar."), pl -> { });
        } else {
            int n = ArmaRpg.mejora(mano);
            ItemStack vista = a.crear(n);
            m.poner(11, vista, pl -> { });
            if (n >= 10) {
                m.poner(15, Util.item(Material.NETHER_STAR, "<gold><bold>+10", "<gray>No se puede mejorar más."), pl -> { });
            } else {
                int[] c = costoMejora(n);
                int frag = ObjetosRpg.contar(p, "fragmento"), esc = ObjetosRpg.contar(p, "escama");
                List<String> lore = new ArrayList<>();
                lore.add("<gray>" + a.nombre + " <white>+" + n + " <gray>→ <green>+" + (n + 1));
                lore.add("<gray>Daño ×" + String.format("%.2f", 1 + 0.08 * (n + 1)));
                lore.add("");
                lore.add((d(p).almas >= c[0] ? "<green>" : "<red>") + Util.num(c[0]) + " almas");
                lore.add((frag >= c[1] ? "<green>" : "<red>") + c[1] + " fragmentos de Hierro Estelar <gray>(tenés " + frag + ")");
                if (c[2] > 0) lore.add((esc >= c[2] ? "<green>" : "<red>") + c[2] + " escamas del Abismo <gray>(tenés " + esc + ")");
                lore.add("");
                lore.add("<yellow>Clic para mejorar");
                m.poner(15, Util.item(Material.SMITHING_TABLE, "<gold><bold>Mejorar", lore.toArray(new String[0])), this::mejorar);
            }
        }
        m.abrir(p);
    }

    private void mejorar(Player p) {
        ItemStack mano = p.getInventory().getItemInMainHand();
        ArmaRpg a = ArmaRpg.de(mano);
        if (a == null) return;
        int n = ArmaRpg.mejora(mano);
        if (n >= 10) return;
        int[] c = costoMejora(n);
        if (ObjetosRpg.contar(p, "fragmento") < c[1] || ObjetosRpg.contar(p, "escama") < c[2]) {
            Util.msg(p, "<red>Te faltan materiales. <gray>Los fragmentos los sueltan los enemigos; las escamas, los "
                    + "Campeones de la Ciudadela en adelante.");
            p.playSound(p, Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
            return;
        }
        if (!modo.gastarAlmas(p, c[0])) return;
        ObjetosRpg.quitar(p, "fragmento", c[1]);
        if (c[2] > 0) ObjetosRpg.quitar(p, "escama", c[2]);
        p.getInventory().setItemInMainHand(a.crear(n + 1));
        p.playSound(p, Sound.BLOCK_ANVIL_USE, 1f, 1f);
        p.playSound(p, Sound.BLOCK_SMITHING_TABLE_USE, 1f, 0.8f);
        Util.titulo(p, "", "<gold>" + a.nombre + " +" + (n + 1), 100, 1500, 400);
        herrero(p);
    }

    // ------------------------------------------------------------------ mercader

    private record Oferta(Supplier<ItemStack> item, long precio) {}

    public void mercader(Player p) {
        List<Oferta> ofertas = List.of(
                new Oferta(() -> ObjetosRpg.bombaFuego(1), 300),
                new Oferta(() -> ObjetosRpg.cuchillos(5), 250),
                new Oferta(() -> ObjetosRpg.resina(ObjetosRpg.Resina.FUEGO, 1), 400),
                new Oferta(() -> ObjetosRpg.resina(ObjetosRpg.Resina.RAYO, 1), 400),
                new Oferta(() -> ObjetosRpg.hierba(1), 350),
                new Oferta(() -> ObjetosRpg.ceniza(1), 600),
                new Oferta(() -> new ItemStack(Material.ARROW, 16), 120),
                new Oferta(() -> ObjetosRpg.fragmento(1), 900),
                new Oferta(() -> ObjetosRpg.lagrima(1), 6000),
                new Oferta(() -> ArmaRpg.BASTON_HUESO.crear(0), 3000),
                new Oferta(() -> ArmaRpg.TALISMAN.crear(0), 3000),
                new Oferta(Magia.Hechizo.SAETA_VACIO::pergamino, 2000),
                new Oferta(Magia.Hechizo.ESCUDO_ARCANO::pergamino, 3000),
                new Oferta(Magia.Hechizo.NIEBLA_CORROSIVA::pergamino, 4000),
                new Oferta(Magia.Hechizo.LANZA_VACIO::pergamino, 6500),
                new Oferta(Magia.Hechizo.CURACION::pergamino, 3000),
                new Oferta(Magia.Hechizo.LLAMA_PURIFICADORA::pergamino, 2500),
                new Oferta(Magia.Hechizo.BENDICION::pergamino, 4000),
                new Oferta(Magia.Hechizo.LANZA_SAGRADA::pergamino, 5000));
        Menu m = new Menu(4, "<dark_gray>Mercader · " + Util.num(d(p).almas) + " almas");
        int slot = 9;
        for (Oferta o : ofertas) {
            ItemStack muestra = o.item().get();
            ItemMeta meta = muestra.getItemMeta();
            List<net.kyori.adventure.text.Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
            lore.add(Util.mmItem(""));
            lore.add(Util.mmItem("<gold>" + Util.num(o.precio()) + " almas <gray>· clic para comprar"));
            meta.lore(lore);
            muestra.setItemMeta(meta);
            m.poner(slot++, muestra, pl -> {
                if (!modo.gastarAlmas(pl, o.precio())) return;
                pl.getInventory().addItem(o.item().get()).values().forEach(r -> pl.getWorld().dropItem(pl.getLocation(), r));
                pl.playSound(pl, Sound.ENTITY_VILLAGER_YES, 1f, 1f);
                mercader(pl);
            });
        }
        m.abrir(p);
    }

    // ------------------------------------------------------------------ guardiana

    public void guardiana(Player p) {
        DatosJugador d = d(p);
        Menu m = new Menu(3, "<dark_gray>La Guardiana del Fuego");
        m.poner(11, Util.item(Material.BLAZE_POWDER, "<gold><bold>Fortalecer", "<gray>Subir atributos con almas y",
                "<gray>gastar brasas en el árbol."), modo::abrirHoguera);
        boolean puede = d.jefes.contains("durmiente@" + d.ciclo);
        m.poner(15, Util.item(puede ? Material.END_CRYSTAL : Material.GRAY_DYE,
                "<dark_red><bold>Empezar el Ciclo+" + (d.ciclo + 1),
                "<gray>El mismo mundo, con enemigos y jefes",
                "<gray>×1,5 más fuertes que dan ×1,5 almas.",
                "<gray>Conservás nivel, equipo y árbol.",
                "",
                puede ? "<yellow>Clic para empezar" : "<dark_gray>Primero derrotá al Durmiente en este ciclo"), pl -> {
            DatosJugador dd = d(pl);
            if (!dd.jefes.contains("durmiente@" + dd.ciclo)) return;
            dd.ciclo++;
            pl.closeInventory();
            Util.titulo(pl, "<dark_red><bold>CICLO+" + dd.ciclo, "<gray>La llama vuelve a arder", 800, 3500, 1200);
            pl.playSound(pl, Sound.BLOCK_END_PORTAL_SPAWN, 1f, 0.5f);
        });
        m.abrir(p);
    }

    // ------------------------------------------------------------------ altar

    public void altar(Player p) {
        DatosJugador d = d(p);
        Menu m = new Menu(3, "<dark_gray>Altar de las Almas");
        if (d.almasJefe.isEmpty()) {
            m.poner(13, Util.item(Material.LODESTONE, "<gray>No tenés almas de jefe para canjear",
                    "<gray>Derrotá a los Señores de las zonas."), pl -> { });
        }
        int slot = 10;
        for (String id : new ArrayList<>(d.almasJefe)) {
            String nombre = switch (id) {
                case "gloton" -> "Alma del Glotón";
                case "tejedora" -> "Alma de la Tejedora";
                case "abismo" -> "Alma del Abismo";
                case "vigia" -> "Alma del Vigía";
                default -> "Alma del Durmiente";
            };
            String premio = switch (id) {
                case "gloton" -> "Gran maza del Glotón";
                case "tejedora" -> "Set de seda + hechizo Red Pútrida";
                case "abismo" -> "Espada del Abismo + set del Abismo";
                case "vigia" -> "Lanza del Faro";
                default -> "Un arma de jefe a elección";
            };
            m.poner(slot, Util.item(Material.SOUL_LANTERN, "<gold><bold>" + nombre, "<gray>Se canjea por:", "<white>" + premio,
                    "", "<yellow>Clic para canjear"), pl -> canjear(pl, id));
            slot += 2;
        }
        m.abrir(p);
    }

    private void canjear(Player p, String id) {
        DatosJugador d = d(p);
        if (!d.almasJefe.contains(id)) return;
        if (id.equals("durmiente")) {
            Menu m = new Menu(3, "<dark_gray>Elegí un arma");
            ArmaRpg[] opciones = {ArmaRpg.GRAN_MAZA_GLOTON, ArmaRpg.ESPADA_ABISMO, ArmaRpg.LANZA_FARO};
            for (int i = 0; i < opciones.length; i++) {
                ArmaRpg a = opciones[i];
                m.poner(11 + i * 2, a.crear(5), pl -> {
                    if (!d(pl).almasJefe.remove("durmiente")) return;
                    dar(pl, a.crear(5));
                    pl.closeInventory();
                });
            }
            m.abrir(p);
            return;
        }
        d.almasJefe.remove(id);
        switch (id) {
            case "gloton" -> dar(p, ArmaRpg.GRAN_MAZA_GLOTON.crear(0));
            case "tejedora" -> {
                for (ItemStack it : set(Color.fromRGB(235, 235, 230), TrimPattern.SILENCE, TrimMaterial.QUARTZ, "Seda de la Tejedora",
                        Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS)) dar(p, it);
                dar(p, Magia.Hechizo.RED_PUTRIDA.pergamino());
            }
            case "abismo" -> {
                dar(p, ArmaRpg.ESPADA_ABISMO.crear(0));
                for (ItemStack it : set(null, TrimPattern.EYE, TrimMaterial.AMETHYST, "del Abismo",
                        Material.NETHERITE_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.CHAINMAIL_BOOTS)) dar(p, it);
            }
            case "vigia" -> dar(p, ArmaRpg.LANZA_FARO.crear(0));
            default -> { }
        }
        p.playSound(p, Sound.PARTICLE_SOUL_ESCAPE, 1f, 0.6f);
        p.playSound(p, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 0.8f);
        altar(p);
    }

    private static List<ItemStack> set(Color cuero, TrimPattern patron, TrimMaterial mat, String nombre, Material... piezas) {
        List<ItemStack> l = new ArrayList<>();
        String[] tipo = {"Yelmo", "Peto", "Grebas", "Botas"};
        for (int i = 0; i < piezas.length; i++) {
            ItemStack it = Util.item(piezas[i], "<white><bold>" + tipo[i] + " " + (nombre.startsWith("del") ? nombre : "de " + nombre));
            ItemMeta meta = it.getItemMeta();
            meta.setUnbreakable(true);
            if (meta instanceof LeatherArmorMeta lm && cuero != null) lm.setColor(cuero);
            if (meta instanceof ArmorMeta am) am.setTrim(new ArmorTrim(mat, patron));
            it.setItemMeta(meta);
            l.add(it);
        }
        return l;
    }

    private static void dar(Player p, ItemStack it) {
        PlayerInventory inv = p.getInventory();
        inv.addItem(it).values().forEach(r -> p.getWorld().dropItem(p.getLocation(), r));
    }

    private DatosJugador d(Player p) {
        return plugin.almacen().de(p);
    }
}
