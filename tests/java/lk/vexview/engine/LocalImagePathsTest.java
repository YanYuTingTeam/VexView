package lk.vexview.engine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class LocalImagePathsTest {
    private static void expectRejected(String root, String source) throws Exception {
        try {
            LocalImagePaths.resolve(root, source);
            throw new AssertionError("Allowed " + source);
        } catch (IOException expected) {
            // Expected rejection or missing file.
        }
    }

    public static void main(String[] args) throws Exception {
        Path temp = Paths.get(args[0]);
        Path root = Files.createDirectories(temp.resolve("assets"));
        Path nested = Files.createDirectories(root.resolve("nested"));
        Path good = Files.createFile(nested.resolve("test image.png"));
        Path outside = Files.createDirectories(temp.resolve("assets-sibling"));
        Files.createFile(outside.resolve("secret.png"));
        String configuredRoot = root.toString();
        if (!LocalImagePaths.resolve(configuredRoot, "[local]nested/test image.png").toPath().equals(good.toRealPath())) {
            throw new AssertionError("Normal relative image did not resolve");
        }
        if (!LocalImagePaths.resolve(configuredRoot, "[local]/nested/test image.png").toPath().equals(good.toRealPath())) {
            throw new AssertionError("Leading separator image did not resolve");
        }
        if (!LocalImagePaths.resolve(configuredRoot, "[local]nested/../nested/test image.png").toPath().equals(good.toRealPath())) {
            throw new AssertionError("Safe normalized image did not resolve");
        }
        expectRejected(configuredRoot, "[local]../assets-sibling/secret.png");
        expectRejected(configuredRoot, "[local]missing.png");
        expectRejected(configuredRoot, "nested/test image.png");
        expectRejected(configuredRoot, "[local]");
        Path link = root.resolve("outside-link");
        try {
            if (!Files.exists(link)) {
                Files.createSymbolicLink(link, outside);
            }
            expectRejected(configuredRoot, "[local]outside-link/secret.png");
            System.out.println("PASS: external symlink or junction blocked");
        } catch (UnsupportedOperationException | SecurityException | IOException exception) {
            System.out.println("SKIP: external symlink creation unavailable: " + exception.getClass().getSimpleName());
        }
        System.out.println("PASS: local image path boundaries");
    }
}
