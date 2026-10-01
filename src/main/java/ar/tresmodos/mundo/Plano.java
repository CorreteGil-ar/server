package ar.tresmodos.mundo;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.generator.ChunkGenerator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Plano de bloques en memoria. Se arma una sola vez y lo usan el generador del mundo, el cálculo de
 * puntos de aparición y la restauración del mapa después de destruirlo.
 *
 * Guarda cada bloque como texto ("oak_stairs[facing=north]") y recién lo convierte a BlockData al
 * cerrarse, así el plano se puede armar y revisar sin un servidor (ver herramientas/).
 */
public class Plano {
    private final Map<Long, String> bloques = new HashMap<>();
    /** Pisos transitables: la posición de los pies de alguien parado ahí (para elegir dónde aparecer). */
    private final Set<Long> pisos = new HashSet<>();
    private Map<String, BlockData> datos;
    private Map<Long, List<Long>> porChunk;

    private static long clave(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private static int[] coords(long k) {
        int x = (int) (k >> 38);
        int z = (int) (k << 26 >> 38);
        int y = (int) (k & 0xFFF);
        if (y >= 2048) y -= 4096;
        return new int[]{x, y, z};
    }

    private static String normalizar(String s) {
        return s.startsWith("minecraft:") ? s.substring(10) : s;
    }

    public void set(int x, int y, int z, String s) {
        if (porChunk != null) throw new IllegalStateException("El plano ya está cerrado");
        bloques.put(clave(x, y, z), normalizar(s));
    }

    /** Texto del bloque o null si el plano no tiene nada ahí. */
    public String texto(int x, int y, int z) {
        return bloques.get(clave(x, y, z));
    }

    /** Nombre del bloque sin propiedades ("air" si no hay nada). */
    public String tipo(int x, int y, int z) {
        String s = texto(x, y, z);
        if (s == null) return "air";
        int i = s.indexOf('[');
        return i < 0 ? s : s.substring(0, i);
    }

    public void borrar(int x, int y, int z) {
        bloques.remove(clave(x, y, z));
    }

    /** Caja llena entre dos esquinas (inclusive). */
    public void caja(int x1, int y1, int z1, int x2, int y2, int z2, String s) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) set(x, y, z, s);
    }

    /** Solo las paredes verticales de la caja (sin piso ni techo). */
    public void paredes(int x1, int y1, int z1, int x2, int y2, int z2, String s) {
        int ax = Math.min(x1, x2), bx = Math.max(x1, x2), az = Math.min(z1, z2), bz = Math.max(z1, z2);
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++) {
            for (int x = ax; x <= bx; x++) {
                set(x, y, az, s);
                set(x, y, bz, s);
            }
            for (int z = az; z <= bz; z++) {
                set(ax, y, z, s);
                set(bx, y, z, s);
            }
        }
    }

    public void aire(int x1, int y1, int z1, int x2, int y2, int z2) {
        caja(x1, y1, z1, x2, y2, z2, "air");
    }

    /** Marca como transitable el piso de la caja: los pies quedan en y + 1. */
    public void marcarPiso(int x1, int z1, int x2, int z2, int y) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++) pisos.add(clave(x, y + 1, z));
    }

    /** Recorre las posiciones de los pies sobre los pisos marcados: (x, y, z). */
    public void paraCadaPiso(Visitante v) {
        for (Long k : pisos) {
            int[] c = coords(k);
            v.visitar(c[0], c[1], c[2], null);
        }
    }

    /**
     * Copia los bloques de una región girados 180° alrededor de (0, y, 0): (x, z) pasa a (-x, -z).
     * Gira también la orientación (facing y conexiones) y reemplaza materiales según el mapa.
     */
    public void copiarGirado180(int x1, int z1, int x2, int z2, Map<String, String> sustituciones) {
        List<Map.Entry<Long, String>> copia = new ArrayList<>();
        for (Map.Entry<Long, String> en : bloques.entrySet()) {
            if (dentro(en.getKey(), x1, z1, x2, z2)) copia.add(en);
        }
        for (Map.Entry<Long, String> en : copia) {
            int[] c = coords(en.getKey());
            set(-c[0], c[1], -c[2], girar180(en.getValue(), sustituciones));
        }
        List<Long> pisosCopia = new ArrayList<>();
        for (Long k : pisos) if (dentro(k, x1, z1, x2, z2)) pisosCopia.add(k);
        for (Long k : pisosCopia) {
            int[] c = coords(k);
            pisos.add(clave(-c[0], c[1], -c[2]));
        }
    }

    private static final Map<String, String> OPUESTO = Map.of("north", "south", "south", "north", "east", "west", "west", "east");

    /** Gira 180° un texto de bloque: facing opuesto y conexiones intercambiadas. */
    static String girar180(String s, Map<String, String> sustituciones) {
        int i = s.indexOf('[');
        String nombre = i < 0 ? s : s.substring(0, i);
        nombre = sustituciones.getOrDefault(nombre, nombre);
        if (i < 0) return nombre;
        StringBuilder sb = new StringBuilder(nombre).append('[');
        String[] pares = s.substring(i + 1, s.length() - 1).split(",");
        for (int j = 0; j < pares.length; j++) {
            String[] kv = pares[j].split("=", 2);
            String k = kv[0], v = kv.length > 1 ? kv[1] : "";
            if (k.equals("facing")) v = OPUESTO.getOrDefault(v, v);
            else if (OPUESTO.containsKey(k)) k = OPUESTO.get(k);
            else if (k.equals("rotation")) v = String.valueOf((Integer.parseInt(v) + 8) % 16);
            if (j > 0) sb.append(',');
            sb.append(k).append('=').append(v);
        }
        return sb.append(']').toString();
    }

    private static boolean dentro(long k, int x1, int z1, int x2, int z2) {
        int[] c = coords(k);
        return c[0] >= Math.min(x1, x2) && c[0] <= Math.max(x1, x2) && c[2] >= Math.min(z1, z2) && c[2] <= Math.max(z1, z2);
    }

    /**
     * Cierra el plano: convierte los textos a BlockData (en el hilo principal) y lo indexa por chunk
     * para el generador, que lo lee desde otros hilos sin modificarlo.
     */
    public void cerrar() {
        Map<String, BlockData> d = new HashMap<>();
        for (String s : bloques.values()) d.computeIfAbsent(s, k -> Bukkit.createBlockData("minecraft:" + k));
        datos = d;
        Map<Long, List<Long>> idx = new HashMap<>();
        for (Long k : bloques.keySet()) {
            int[] c = coords(k);
            idx.computeIfAbsent(chunk(c[0] >> 4, c[2] >> 4), u -> new ArrayList<>()).add(k);
        }
        porChunk = idx;
    }

    private static long chunk(int cx, int cz) {
        return ((long) cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    public BlockData get(int x, int y, int z) {
        String s = texto(x, y, z);
        return s == null ? null : datos.get(s);
    }

    /** Escribe en el chunk los bloques del plano que le corresponden. */
    public void volcar(int cx, int cz, ChunkGenerator.ChunkData data) {
        List<Long> lista = porChunk.get(chunk(cx, cz));
        if (lista == null) return;
        for (long k : lista) {
            int[] c = coords(k);
            data.setBlock(c[0] & 15, c[1], c[2] & 15, datos.get(bloques.get(k)));
        }
    }

    /** Vuelve a poner en el mundo un bloque del plano (o aire si el plano no tiene nada ahí). */
    public void restaurar(World w, int x, int y, int z) {
        BlockData d = get(x, y, z);
        w.getBlockAt(x, y, z).setBlockData(d == null ? Bukkit.createBlockData("minecraft:air") : d, false);
    }

    public int tamanio() {
        return bloques.size();
    }

    /** Recorre todos los bloques del plano: (x, y, z, texto). */
    public void paraCada(VisitanteTexto v) {
        for (Map.Entry<Long, String> en : bloques.entrySet()) {
            int[] c = coords(en.getKey());
            v.visitar(c[0], c[1], c[2], en.getValue());
        }
    }

    @FunctionalInterface
    public interface Visitante {
        void visitar(int x, int y, int z, BlockData d);
    }

    @FunctionalInterface
    public interface VisitanteTexto {
        void visitar(int x, int y, int z, String texto);
    }
}
