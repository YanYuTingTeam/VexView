import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/**
 * Reobfuscates a Minecraft-1.8.9 mod from MCP member names to SRG member names.
 *
 * Unlike a plain "replace this string" mapper, member lookups are hierarchy aware: a call
 * whose declared owner is a mod class that extends an MC class is resolved by walking up to
 * the MC declaring class, exactly like ForgeGradle's reobf step.
 *
 * Additionally reports every MC member reference that could not be mapped, which would be a
 * NoSuchMethodError / NoSuchFieldError at runtime.
 *
 * usage: Reobf <inJar|classesDir> <sdkJar> <mapFile> <outJar>
 */
public final class Reobf {

    static final Map<String, String> METHOD = new HashMap<String, String>(); // owner.name desc -> srg
    static final Map<String, String> FIELD = new HashMap<String, String>();  // owner.name desc -> srg
    static final Map<String, String[]> HIER = new HashMap<String, String[]>(); // name -> {super, itf...}

    static final Set<String> UNMAPPED = new LinkedHashSet<String>();
    static int refs = 0, mapped = 0;

    public static void main(String[] a) throws Exception {
        File in = new File(a[0]);
        File sdk = new File(a[1]);
        File mapFile = new File(a[2]);
        File out = new File(a[3]);

        loadMap(mapFile);
        buildHierarchy(sdk, HIER);
        Map<String, byte[]> classes = new LinkedHashMap<String, byte[]>();
        if (in.isDirectory()) readDir(in, classes); else readJar(in, classes);
        buildHierarchy(classes);

        Map<String, byte[]> outEntries = new LinkedHashMap<String, byte[]>();
        int changed = 0;
        for (Map.Entry<String, byte[]> e : classes.entrySet()) {
            if (!e.getKey().endsWith(".class")) {
                outEntries.put(e.getKey(), e.getValue());
                continue;
            }
            byte[] nb = remap(e.getValue());
            outEntries.put(new ClassReader(nb).getClassName() + ".class", nb);
            changed++;
        }
        writeJar(out, outEntries);

        System.out.println("classes=" + changed + " refs=" + refs + " mapped=" + mapped);
        if (!UNMAPPED.isEmpty()) {
            System.out.println("UNMAPPED (" + UNMAPPED.size() + "):");
            for (String s : UNMAPPED) System.out.println("  " + s);
        } else {
            System.out.println("UNMAPPED: none");
        }
    }

    static void loadMap(File f) throws IOException {
        for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
            line = line.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] p = line.split("\\s+");
            if (p.length < 5) continue;
            if ("METHOD".equals(p[0])) {
                METHOD.put(p[1] + '.' + p[2] + ' ' + p[3], p[4]);
            } else if ("FIELD".equals(p[0])) {
                FIELD.put(p[1] + '.' + p[2] + ' ' + p[3], p[4]);
            }
        }
        System.out.println("map: methods=" + METHOD.size() + " fields=" + FIELD.size());
    }

    static void buildHierarchy(File jar, Map<String, String[]> into) throws IOException {
        ZipFile z = new ZipFile(jar);
        for (Enumeration<? extends ZipEntry> en = z.entries(); en.hasMoreElements();) {
            ZipEntry e = en.nextElement();
            if (!e.getName().endsWith(".class")) continue;
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            InputStream in = z.getInputStream(e);
            byte[] buf = new byte[16384];
            int r;
            while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
            in.close();
            recordHierarchy(bo.toByteArray(), into);
        }
        z.close();
    }

    static void recordHierarchy(byte[] data, Map<String, String[]> into) {
        try {
            ClassReader cr = new ClassReader(data);
            String sup = cr.getSuperName();
            String[] itf = cr.getInterfaces();
            int n = (sup == null ? 0 : 1) + (itf == null ? 0 : itf.length);
            String[] all = new String[n];
            int k = 0;
            if (sup != null) all[k++] = sup;
            if (itf != null) for (String s : itf) all[k++] = s;
            into.put(cr.getClassName(), all);
        } catch (Throwable ignored) {
        }
    }

    static void buildHierarchy(Map<String, byte[]> classes) {
        for (Map.Entry<String, byte[]> e : classes.entrySet()) {
            if (e.getKey().endsWith(".class")) recordHierarchy(e.getValue(), HIER);
        }
    }

    static boolean isMc(String owner) {
        // Forge's own classes are never obfuscated at runtime; only vanilla members need SRG names.
        return owner.startsWith("net/minecraft/") && !owner.startsWith("net/minecraftforge/");
    }

    /** ordered BFS through superclasses/interfaces starting at owner */
    static List<String> walk(String owner) {
        List<String> out = new ArrayList<String>();
        Set<String> seen = new HashSet<String>();
        Deque<String> q = new ArrayDeque<String>();
        q.add(owner);
        while (!q.isEmpty()) {
            String c = q.poll();
            if (c == null || !seen.add(c)) continue;
            out.add(c);
            String[] sup = HIER.get(c);
            if (sup != null) for (String s : sup) if (s != null) q.add(s);
        }
        return out;
    }

    static String mapMethod(String owner, String name, String desc) {
        if ("<init>".equals(name) || "<clinit>".equals(name)) return name;
        for (String c : walk(owner)) {
            String v = METHOD.get(c + '.' + name + ' ' + desc);
            if (v != null) {
                mapped++;
                return v;
            }
        }
        if (isMc(owner)) UNMAPPED.add("METHOD " + owner + '.' + name + ' ' + desc);
        return name;
    }

    static String mapField(String owner, String name, String desc) {
        for (String c : walk(owner)) {
            String v = FIELD.get(c + '.' + name + ' ' + desc);
            if (v != null) {
                mapped++;
                return v;
            }
        }
        if (isMc(owner)) UNMAPPED.add("FIELD " + owner + '.' + name + ' ' + desc);
        return name;
    }

    static byte[] remap(byte[] data) {
        ClassReader cr = new ClassReader(data);
        ClassWriter cw = new ClassWriter(0);
        cr.accept(new ClassRemapper(cw, new Remapper() {
            @Override public String map(String internalName) {
                return internalName; // class names stay MCP: FML's deobf remapper handles them at runtime
            }
            @Override public String mapMethodName(String owner, String name, String desc) {
                refs++;
                return mapMethod(owner, name, desc);
            }
            @Override public String mapFieldName(String owner, String name, String desc) {
                refs++;
                return mapField(owner, name, desc);
            }
        }), 0);
        return cw.toByteArray();
    }

    static void readDir(File dir, Map<String, byte[]> into) throws IOException {
        Path root = dir.toPath();
        Files.walk(root).filter(Files::isRegularFile).forEach(p -> {
            try {
                into.put(root.relativize(p).toString().replace('\\', '/'), Files.readAllBytes(p));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    static void readJar(File f, Map<String, byte[]> into) throws IOException {
        ZipInputStream z = new ZipInputStream(new BufferedInputStream(new FileInputStream(f)));
        ZipEntry e;
        while ((e = z.getNextEntry()) != null) {
            if (e.isDirectory()) continue;
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[16384];
            int r;
            while ((r = z.read(buf)) > 0) bo.write(buf, 0, r);
            into.put(e.getName(), bo.toByteArray());
        }
        z.close();
    }

    static void writeJar(File f, Map<String, byte[]> entries) throws IOException {
        f.getParentFile().mkdirs();
        ZipOutputStream z = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(f)));
        z.setLevel(9);
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            z.putNextEntry(new ZipEntry(e.getKey()));
            z.write(e.getValue());
            z.closeEntry();
        }
        z.close();
    }
}
