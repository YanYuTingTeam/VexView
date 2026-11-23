package lk.vexview.engine;

import java.io.File;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/** Resolves a legacy [local] image reference within the configured image directory. */
public final class LocalImagePaths {
    private LocalImagePaths() {
    }

    public static File resolve(String root, String source) throws IOException {
        if (root == null || source == null || !source.contains("[local]")) {
            throw new IOException("Invalid local image reference");
        }
        try {
            Path rootPath = new File(root).toPath().toRealPath();
            // Retain File(parent, child) semantics used by existing [local] references.
            Path imagePath = new File(root, source.replace("[local]", "")).toPath().toRealPath();
            if (!imagePath.startsWith(rootPath) || imagePath.equals(rootPath)) {
                throw new IOException("Local image is outside the configured directory");
            }
            return imagePath.toFile();
        } catch (InvalidPathException exception) {
            throw new IOException("Invalid local image path", exception);
        }
    }
}
