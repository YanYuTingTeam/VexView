import org.objectweb.asm.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/**
 * Emits the owner-aware MCP -> SRG member map that the reobfuscation step needs for MC 1.8.9.
 *
 * Sources of truth:
 *   - joined.srg                        (notch member -> SRG name, MCP class names + MCP descriptors)
 *   - mcp_stable fields.csv/methods.csv (SRG name -> readable MCP name)
 *   - the built MCP SDK jar             (real field descriptors)
 *
 * Output format (matches Reobf.java):
 *   METHOD <mcpOwner> <mcpName> <mcpDesc> <srgName>
 *   FIELD  <mcpOwner> <mcpName> <mcpDesc> <srgName>
 */
public final class GenReobf189 {

    static final Map<String, String> CLASS_MAP = new HashMap<String, String>();
    static final Map<String, String[]> METHOD_MAP = new HashMap<String, String[]>(); // obfOwner.obfName descObf -> {mcpOwner, srgName, descMcp}
    static final Map<String, String[]> FIELD_MAP = new HashMap<String, String[]>();  // obfOwner.obfName -> {mcpOwner, srgName}
    static final Map<String, String> SRG2MCP_M = new HashMap<String, String>();
    static final Map<String, String> SRG2MCP_F = new HashMap<String, String>();

    public static void main(String[] a) throws Exception {
        File joinedSrg = new File(a[0]);
        File stableDir = new File(a[1]);
        File sdkJar = new File(a[2]);
        File out = new File(a[3]);

        loadCsv(new File(stableDir, "methods.csv"), SRG2MCP_M);
        loadCsv(new File(stableDir, "fields.csv"), SRG2MCP_F);
        loadSrg(joinedSrg);

        List<String> lines = new ArrayList<String>();
        int methods = 0, fields = 0;

        for (Map.Entry<String, String[]> e : METHOD_MAP.entrySet()) {
            String[] v = e.getValue();
            String srg = v[1];
            String mcpName = SRG2MCP_M.containsKey(srg) ? SRG2MCP_M.get(srg) : srg;
            lines.add("METHOD " + v[0] + " " + mcpName + " " + v[2] + " " + srg);
            methods++;
        }

        Map<String, String> fieldByMcp = new HashMap<String, String>();
        for (Map.Entry<String, String[]> e : FIELD_MAP.entrySet()) {
            String[] v = e.getValue();
            String srg = v[1];
            String mcpName = SRG2MCP_F.containsKey(srg) ? SRG2MCP_F.get(srg) : srg;
            fieldByMcp.put(v[0] + '.' + mcpName, srg);
        }

        // fields need their descriptor, which joined.srg does not carry: read it from the SDK
        ZipFile z = new ZipFile(sdkJar);
        for (Enumeration<? extends ZipEntry> en = z.entries(); en.hasMoreElements();) {
            ZipEntry entry = en.nextElement();
            if (!entry.getName().endsWith(".class")) continue;
            byte[] data;
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            InputStream in = z.getInputStream(entry);
            byte[] buf = new byte[16384];
            int r;
            while ((r = in.read(buf)) > 0) bo.write(buf, 0, r);
            in.close();
            data = bo.toByteArray();
            final String[] holder = new String[1];
            final List<String[]> flds = new ArrayList<String[]>();
            try {
                ClassReader cr = new ClassReader(data);
                holder[0] = cr.getClassName();
                cr.accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public FieldVisitor visitField(int acc, String n, String d, String s, Object v) {
                        flds.add(new String[]{n, d});
                        return null;
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
            } catch (Throwable t) {
                continue;
            }
            String mcpOwner = holder[0];
            for (String[] f : flds) {
                String srg = fieldByMcp.get(mcpOwner + '.' + f[0]);
                if (srg == null) continue;
                lines.add("FIELD " + mcpOwner + " " + f[0] + " " + f[1] + " " + srg);
                fields++;
            }
        }
        z.close();

        Files.write(out.toPath(), lines, StandardCharsets.UTF_8);
        System.out.println("reobf map: methods=" + methods + " fields=" + fields + " -> " + out);
    }

    static void loadSrg(File f) throws IOException {
        for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
            if (line.isEmpty()) continue;
            if (line.startsWith("CL: ")) {
                String[] p = line.substring(4).split("\\s+");
                if (p.length >= 2) CLASS_MAP.put(p[0], p[1]);
            } else if (line.startsWith("FD: ")) {
                String[] p = line.substring(4).split("\\s+");
                if (p.length < 2) continue;
                int slash = p[1].lastIndexOf('/');
                FIELD_MAP.put(p[0].replace('/', '.'), new String[]{p[1].substring(0, slash), p[1].substring(slash + 1)});
            } else if (line.startsWith("MD: ")) {
                String[] p = line.substring(4).split("\\s+");
                if (p.length < 4) continue;
                int slash = p[2].lastIndexOf('/');
                METHOD_MAP.put(p[0].replace('/', '.') + ' ' + p[1],
                        new String[]{p[2].substring(0, slash), p[2].substring(slash + 1), p[3]});
            }
        }
        System.out.println("joined.srg: classes=" + CLASS_MAP.size() + " methods=" + METHOD_MAP.size() + " fields=" + FIELD_MAP.size());
    }

    static void loadCsv(File f, Map<String, String> into) throws IOException {
        for (String line : Files.readAllLines(f.toPath(), StandardCharsets.UTF_8)) {
            if (line.isEmpty() || line.startsWith("searge")) continue;
            String[] p = line.split(",", 3);
            if (p.length >= 2 && !p[1].isEmpty()) into.put(p[0], p[1]);
        }
    }
}
