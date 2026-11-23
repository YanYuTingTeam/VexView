import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lk.vexview.engine.ConnectionTextureCache;

public final class ConnectionTextureCacheTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Object oldConnection = new Object(), newConnection = new Object();
        Map<String, String> guiTextures = new HashMap<>(), hudTextures = new HashMap<>();
        List<String> disposed = new ArrayList<>();
        ConnectionTextureCache<Object, String> gui = new ConnectionTextureCache<>(guiTextures, disposed::add);
        ConnectionTextureCache<Object, String> hud = new ConnectionTextureCache<>(hudTextures, disposed::add);
        gui.put(oldConnection, "shared", () -> "old-gui");
        gui.put(oldConnection, "old-only", () -> "old-only");
        hud.put(oldConnection, "shared", () -> "old-hud");
        gui.put(oldConnection, "shared", () -> { throw new AssertionError("same owner replaced cached image"); });
        gui.put(newConnection, "shared", () -> "new-gui");
        check("new-gui".equals(guiTextures.get("shared")), "new connection must replace the old texture");
        check(disposed.contains("old-gui"), "replaced texture was not disposed");
        gui.clear(oldConnection);
        hud.clear(oldConnection);
        check("new-gui".equals(guiTextures.get("shared")), "late old disconnect removed new texture");
        check(!guiTextures.containsKey("old-only") && hudTextures.isEmpty(), "old textures not removed");
        check(disposed.contains("old-only") && disposed.contains("old-hud"), "old textures not disposed");
        gui.clear(newConnection);
        check(guiTextures.isEmpty() && disposed.contains("new-gui"), "new connection cleanup failed");
        check(disposed.size() == 4, "texture disposed more than once");
        System.out.println("PASS: separate channel caches, reconnect, late disconnect and texture disposal");
    }
}
