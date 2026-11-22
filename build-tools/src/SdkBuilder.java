import com.nothome.delta.GDiffPatcher;
import lzma.sdk.lzma.Decoder;
import lzma.streams.LzmaInputStream;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.Pack200;
import java.util.zip.*;

/**
 * Builds a Minecraft 1.8.9 + Forge compile SDK in MCP names, without ForgeGradle.
 *
 *   1. notebook client jar
 *   2. apply forge binpatches (binpatches.pack.lzma -> pack200 -> javaxdelta)
 *   3. remap notch -> SRG member names + MCP class names via joined.srg (hierarchy aware)
 *   4. remap SRG member names -> readable MCP names via mcp_stable csVs
 *   5. fold in net/minecraftforge/** from the Forge universal jar
 *   6. apply Forge access transformers
 *
 * usage: SdkBuilder <clientJar> <forgeUniversal> <joined.srg> <stableDir> <atFile|-> <outJar>
 */
public final class SdkBuilder {

    static final Map<String, String> CLASS_MAP = new HashMap<String, String>();
    static final Map<String, String> METHOD_MAP = new HashMap<String, String>(); // owner.name desc -> new
    static final Map<String, String> FIELD_MAP = new HashMap<String, String>();  // owner.name -> new
    static final Map<String, String> SRG_METHODS = new HashMap<String, String>();
    static final Map<String, String> SRG_FIELDS = new HashMap<String, String>();
    static final Map<String, String[]> HIER = new HashMap<String, String[]>();

    public static void main(String[] a) throws Exception {
        File client = new File(a[0]), universal = new File(a[1]);
        File joinedSrg = new File(a[2]);
        File stableDir = new File(a[3]);
        File atFile = "-".equals(a[4]) ? null : new File(a[4]);
        File out = new File(a[5]);

        Map<String, byte[]> classes = new LinkedHashMap<String, byte[]>();
        readJarInto(client, classes);
        System.out.println("client entries: " + classes.size());

        Map<String, ClassPatch> patches = loadPatches(universal);
        System.out.println("binpatches: " + patches.size());

        int patched = 0, checksumFail = 0, missing = 0;
        GDiffPatcher patcher = new GDiffPatcher();
        Map<String, byte[]> mc = new LinkedHashMap<String, byte[]>();
        for (Map.Entry<String, byte[]> e : classes.entrySet()) {
            String name = e.getKey();
            byte[] data = e.getValue();
            if (!name.endsWith(".class")) { mc.put(name, data); continue; }
            ClassPatch p = patches.get(name);
            if (p != null) {
                int h = adler(data);
                if (p.existsAtTarget && h != p.inputChecksum) { checksumFail++; }
                else {
                    try { data = patcher.patch(data, p.patch); patched++; }
                    catch (Throwable t) { missing++; }
                }
            }
            mc.put(name, data);
        }
        System.out.println("patched=" + patched + " checksumFail=" + checksumFail + " patchFail=" + missing);

        for (Map.Entry<String, byte[]> e : mc.entrySet()) {
            if (!e.getKey().endsWith(".class")) continue;
            try {
                ClassReader cr = new ClassReader(e.getValue());
                String[] sup = cr.getSuperName() == null ? new String[0] : new String[]{cr.getSuperName()};
                String[] itf = cr.getInterfaces();
                String[] all = new String[sup.length + itf.length];
                System.arraycopy(sup, 0, all, 0, sup.length);
                System.arraycopy(itf, 0, all, sup.length, itf.length);
                HIER.put(cr.getClassName(), all);
            } catch (Throwable t) { }
        }

        loadSrg(joinedSrg);
        System.out.println("srg classes=" + CLASS_MAP.size() + " methods=" + METHOD_MAP.size() + " fields=" + FIELD_MAP.size());
        loadStable(new File(stableDir, "fields.csv"), new File(stableDir, "methods.csv"));

        Map<String, byte[]> outEntries = new LinkedHashMap<String, byte[]>();
        int remapped = 0, failed = 0;
        for (Map.Entry<String, byte[]> e : mc.entrySet()) {
            if (!e.getKey().endsWith(".class")) continue;
            try {
                byte[] nb = remap(e.getValue());
                outEntries.put(new ClassReader(nb).getClassName() + ".class", nb);
                remapped++;
            } catch (Throwable t) {
                failed++;
                if (failed < 8) System.err.println("remap fail " + e.getKey() + " : " + t);
            }
        }
        System.out.println("remapped=" + remapped + " failed=" + failed);

        // Forge's own classes: already MCP class names, members still SRG.
        Map<String, byte[]> uni = new LinkedHashMap<String, byte[]>();
        readJarInto(universal, uni);
        int forgeAdded = 0;
        for (Map.Entry<String, byte[]> e : uni.entrySet()) {
            String n = e.getKey();
            if (!n.endsWith(".class")) continue;
            if (!n.startsWith("net/minecraftforge/")) continue;
            if (n.startsWith("META-INF/")) continue;
            if (outEntries.containsKey(n)) continue;
            try {
                byte[] nb = remap(e.getValue());
                outEntries.put(new ClassReader(nb).getClassName() + ".class", nb);
                forgeAdded++;
            } catch (Throwable t) { }
        }
        System.out.println("forge classes added: " + forgeAdded);

        if (atFile != null && atFile.isFile()) {
            int changedRules = applyAT(atFile, outEntries);
            System.out.println("at rules applied to classes: " + changedRules);
        }

        writeJar(out, outEntries);
        System.out.println("wrote " + out + " entries=" + outEntries.size());
    }

    // ---------------------------------------------------------------- binpatches

    static int adler(byte[] b) {
        Adler32 h = new Adler32();
        h.update(b);
        return (int) h.getValue();
    }

    static final class ClassPatch {
        String source, target;
        boolean existsAtTarget;
        int inputChecksum;
        byte[] patch;
    }

    static Map<String, ClassPatch> loadPatches(File universal) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<String, byte[]>();
        readJarInto(universal, entries);
        byte[] lz = entries.get("binpatches.pack.lzma");
        if (lz == null) throw new IOException("binpatches.pack.lzma not found");
        ByteArrayOutputStream jarBytes = new ByteArrayOutputStream();
        LzmaInputStream in = new LzmaInputStream(new ByteArrayInputStream(lz), new Decoder());
        java.util.jar.JarOutputStream jos = new java.util.jar.JarOutputStream(jarBytes);
        Pack200.newUnpacker().unpack(in, jos);
        jos.close();
        in.close();
        Map<String, ClassPatch> out = new HashMap<String, ClassPatch>();
        JarInputStream jis = new JarInputStream(new ByteArrayInputStream(jarBytes.toByteArray()));
        JarEntry entry;
        while ((entry = jis.getNextJarEntry()) != null) {
            if (!entry.getName().matches("binpatch/client/.*\\.binpatch")) continue;
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = jis.read(buf)) > 0) bo.write(buf, 0, r);
            DataInputStream d = new DataInputStream(new ByteArrayInputStream(bo.toByteArray()));
            ClassPatch cp = new ClassPatch();
            d.readUTF(); // patch name
            cp.source = d.readUTF();
            cp.target = d.readUTF();
            cp.existsAtTarget = d.readBoolean();
            if (cp.existsAtTarget) cp.inputChecksum = d.readInt();
            int len = d.readInt();
            cp.patch = new byte[len];
            d.readFully(cp.patch);
            out.put(cp.source.replace('.', '/') + ".class", cp);
            jis.closeEntry();
        }
        jis.close();
        return out;
    }

    // ---------------------------------------------------------------- mappings

    static void loadSrg(File f) throws IOException {
        for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
            if (line.isEmpty()) continue;
            if (line.startsWith("CL: ")) {
                String[] p = line.substring(4).split("\\s+");
                if (p.length >= 2) CLASS_MAP.put(p[0], p[1]);
            } else if (line.startsWith("FD: ")) {
                String[] p = line.substring(4).split("\\s+");
                if (p.length < 2) continue;
                String from = p[0], to = p[1];
                int slash = to.lastIndexOf('/');
                String owner = to.substring(0, slash);
                String name = to.substring(slash + 1);
                FIELD_MAP.put(from.replace('/', '.'), owner + '/' + name);
            } else if (line.startsWith("MD: ")) {
                String[] p = line.substring(4).split("\\s+");
                if (p.length < 4) continue;
                String from = p[0], fdesc = p[1], to = p[2], tdesc = p[3];
                int slash = to.lastIndexOf('/');
                String owner = to.substring(0, slash);
                String name = to.substring(slash + 1);
                METHOD_MAP.put(from.replace('/', '.') + ' ' + fdesc, owner + '/' + name + ' ' + tdesc);
            }
        }
    }

    static void loadStable(File fieldsCsv, File methodsCsv) throws IOException {
        for (String line : Files.readAllLines(fieldsCsv.toPath(), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("searge")) continue;
            String[] p = line.split(",", 3);
            if (p.length >= 2) SRG_FIELDS.put(p[0], p[1]);
        }
        for (String line : Files.readAllLines(methodsCsv.toPath(), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("searge")) continue;
            String[] p = line.split(",", 3);
            if (p.length >= 2) SRG_METHODS.put(p[0], p[1]);
        }
    }

    static String resolve(Map<String, String> map, String owner, String name, String desc) {
        String k = owner + '.' + name + (desc == null ? "" : ' ' + desc);
        String v = map.get(k);
        if (v != null) return v;
        Set<String> seen = new HashSet<String>();
        Deque<String> q = new ArrayDeque<String>();
        q.add(owner);
        while (!q.isEmpty()) {
            String c = q.poll();
            if (c == null || !seen.add(c)) continue;
            String[] sups = HIER.get(c);
            if (sups != null) for (String s : sups) if (s != null) q.add(s);
        }
        for (String c : seen) {
            String kk = c + '.' + name + (desc == null ? "" : ' ' + desc);
            v = map.get(kk);
            if (v != null) return v;
        }
        return null;
    }

    static byte[] remap(byte[] data) {
        ClassReader cr = new ClassReader(data);
        ClassWriter cw = new ClassWriter(0);
        cr.accept(new ClassRemapper(cw, new Remapper() {
            @Override public String map(String internalName) {
                String v = CLASS_MAP.get(internalName);
                return v != null ? v : internalName;
            }
            @Override public String mapMethodName(String owner, String name, String desc) {
                String v = resolve(METHOD_MAP, owner, name, desc);
                if (v != null) {
                    int sp = v.indexOf(' ');
                    String member = sp < 0 ? v : v.substring(0, sp);
                    int slash = member.lastIndexOf('/');
                    if (slash >= 0) member = member.substring(slash + 1);
                    v = member;
                }
                if (v == null || SRG_METHODS.containsKey(v)) {
                    String s = SRG_METHODS.get(v);
                    if (s != null) v = s;
                }
                if (v == null && SRG_METHODS.containsKey(name)) v = SRG_METHODS.get(name);
                return v != null ? v : name;
            }
            @Override public String mapFieldName(String owner, String name, String desc) {
                String v = resolve(FIELD_MAP, owner, name, null);
                if (v != null) {
                    int slash = v.lastIndexOf('/');
                    if (slash >= 0) v = v.substring(slash + 1);
                }
                if (v == null || SRG_FIELDS.containsKey(v)) {
                    String s = SRG_FIELDS.get(v);
                    if (s != null) v = s;
                }
                if (v == null && SRG_FIELDS.containsKey(name)) v = SRG_FIELDS.get(name);
                return v != null ? v : name;
            }
        }), 0);
        return cw.toByteArray();
    }

    // ---------------------------------------------------------------- access transformers

    static int applyAT(File atFile, Map<String, byte[]> entries) throws Exception {
        Map<String, List<String[]>> rules = new HashMap<String, List<String[]>>();
        for (String raw : Files.readAllLines(atFile.toPath(), StandardCharsets.UTF_8)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int hash = line.indexOf('#');
            if (hash >= 0) line = line.substring(0, hash).trim();
            String[] p = line.split("\\s+");
            if (p.length < 3) continue;
            String access = p[0];
            String owner = p[1].replace('.', '/');
            String member = p[2];
            if (SRG_FIELDS.containsKey(member)) member = SRG_FIELDS.get(member);
            else if (SRG_METHODS.containsKey(member)) member = SRG_METHODS.get(member);
            rules.computeIfAbsent(owner, k -> new ArrayList<String[]>()).add(new String[]{access, member});
        }
        int changed = 0;
        for (Map.Entry<String, byte[]> e : new ArrayList<Map.Entry<String, byte[]>>(entries.entrySet())) {
            if (!e.getKey().endsWith(".class")) continue;
            org.objectweb.asm.tree.ClassNode cn = new org.objectweb.asm.tree.ClassNode();
            new ClassReader(e.getValue()).accept(cn, 0);
            List<String[]> rs = rules.get(cn.name);
            if (rs == null) continue;
            boolean any = false;
            for (String[] r : rs) {
                String member = r[1];
                String mName = member, mDesc = null;
                if (member.contains("(")) {
                    mName = member.substring(0, member.indexOf('('));
                    mDesc = member.substring(member.indexOf('('));
                }
                for (Object o : cn.fields) {
                    org.objectweb.asm.tree.FieldNode f = (org.objectweb.asm.tree.FieldNode) o;
                    if (!f.name.equals(member)) continue;
                    int old = f.access;
                    f.access = atAccess(f.access, r[0]);
                    if (f.access != old) any = true;
                }
                for (Object o : cn.methods) {
                    org.objectweb.asm.tree.MethodNode m = (org.objectweb.asm.tree.MethodNode) o;
                    if (!m.name.equals(mName)) continue;
                    if (mDesc != null && !m.desc.equals(mDesc)) continue;
                    int old = m.access;
                    m.access = atAccess(m.access, r[0]);
                    if (m.access != old) any = true;
                }
            }
            if (any) {
                changed++;
                ClassWriter cw = new ClassWriter(0);
                cn.accept(cw);
                entries.put(e.getKey(), cw.toByteArray());
            }
        }
        return changed;
    }

    static int atAccess(int access, String rule) {
        String spec = rule;
        boolean removeFinal = false, addFinal = false;
        int dash = spec.indexOf('-');
        int plus = spec.indexOf('+');
        String base = spec;
        if (dash > 0) { base = spec.substring(0, dash); removeFinal = true; }
        else if (plus > 0) { base = spec.substring(0, plus); addFinal = true; }
        int mod = 0;
        if (base.startsWith("public")) mod = Opcodes.ACC_PUBLIC;
        else if (base.startsWith("protected")) mod = Opcodes.ACC_PROTECTED;
        else if (base.startsWith("private")) mod = Opcodes.ACC_PRIVATE;
        int cleared = access & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED | Opcodes.ACC_PRIVATE);
        int res = cleared | mod;
        if (removeFinal) res &= ~Opcodes.ACC_FINAL;
        if (addFinal) res |= Opcodes.ACC_FINAL;
        return res;
    }

    // ---------------------------------------------------------------- io

    static void readJarInto(File f, Map<String, byte[]> map) throws IOException {
        ZipFile z = new ZipFile(f);
        for (Enumeration<? extends ZipEntry> en = z.entries(); en.hasMoreElements();) {
            ZipEntry e = en.nextElement();
            if (e.isDirectory()) continue;
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            InputStream in = z.getInputStream(e);
            byte[] buf = new byte[16384];
            int r;
            while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
            in.close();
            map.put(e.getName(), bo.toByteArray());
        }
        z.close();
    }

    static void writeJar(File f, Map<String, byte[]> entries) throws IOException {
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

