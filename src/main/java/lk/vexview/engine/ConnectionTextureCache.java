package lk.vexview.engine;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Owns textures for one channel. All operations must run on the client thread. */
public final class ConnectionTextureCache<C, T> {
    private final Map<String, T> textures;
    private final Map<String, C> owners = new HashMap<>();
    private final Consumer<T> dispose;

    public ConnectionTextureCache(Map<String, T> textures, Consumer<T> dispose) {
        this.textures = textures;
        this.dispose = dispose;
    }

    public void put(C owner, String id, Supplier<T> create) {
        C existingOwner = owners.get(id);
        if (existingOwner == owner && textures.containsKey(id)) {
            return;
        }
        T previous = textures.remove(id);
        owners.remove(id);
        if (previous != null) {
            dispose.accept(previous);
        }
        T texture = create.get();
        textures.put(id, texture);
        owners.put(id, owner);
    }

    public void clear(C owner) {
        Iterator<Map.Entry<String, C>> entries = owners.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<String, C> entry = entries.next();
            if (entry.getValue() == owner) {
                T texture = textures.remove(entry.getKey());
                entries.remove();
                if (texture != null) {
                    dispose.accept(texture);
                }
            }
        }
    }
}
