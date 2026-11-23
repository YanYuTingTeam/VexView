/*
 * Decompiled with CFR 0.152.
 */
package lk.vexview.engine;

import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import lk.vexview.VexConfig;
import lk.vexview.VexView;
import lk.vexview.gui.core.Downable;
import lk.vexview.gui.core.GifDecoder;
import lk.vexview.gui.core.VexGifImage;
import lk.vexview.net.NetPacketManager;
import lk.vexview.net.packet.PacketDownload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.NetworkManager;
import net.minecraft.util.ResourceLocation;

public class BufferImageLib {
    public static Map<String, ResourceLocation> s1 = new ConcurrentHashMap<String, ResourceLocation>();
    public static Map<String, List<ResourceLocation>> s2;
    public static Map<String, ResourceLocation> s3;
    public static Map<String, ResourceLocation> s4;
    public static Map<String, ResourceLocation> s5;
    private static ConnectionTextureCache<NetworkManager, ResourceLocation> hudBase64;
    private static ConnectionTextureCache<NetworkManager, ResourceLocation> guiBase64;

    static {
        s4 = new ConcurrentHashMap<String, ResourceLocation>();
        s5 = new ConcurrentHashMap<String, ResourceLocation>();
        s3 = new ConcurrentHashMap<String, ResourceLocation>();
        s2 = new ConcurrentHashMap<String, List<ResourceLocation>>();
        hudBase64 = new ConnectionTextureCache<>(s3, texture -> Minecraft.getMinecraft().getTextureManager().deleteTexture(texture));
        guiBase64 = new ConnectionTextureCache<>(s5, texture -> Minecraft.getMinecraft().getTextureManager().deleteTexture(texture));
    }

    public static void m2(String string, BufferedImage bufferedImage) {
        storeBase64(guiBase64, "normal", string, bufferedImage);
    }

    public static Future<Boolean> m4(String string, Downable downable, String string2) {
        if (string != null && s4.containsKey(string)) {
            return CompletableFuture.completedFuture(true);
        }
        return loadTexture(string == null ? string2 : null, () -> readImage(string, ImageReadLimits::read),
            image -> storeImage(string, image));
    }

    public static void m5(String string, BufferedImage bufferedImage) {
        storeBase64(hudBase64, "base64", string, bufferedImage);
    }

    private static void storeBase64(ConnectionTextureCache<NetworkManager, ResourceLocation> cache,
            String textureType, String id, BufferedImage pixels) {
        Minecraft minecraft = Minecraft.getMinecraft();
        NetHandlerPlayClient handler = minecraft.getNetHandler();
        if (handler == null) return;
        NetworkManager owner = handler.getNetworkManager();
        minecraft.addScheduledTask(() -> {
            NetHandlerPlayClient current = minecraft.getNetHandler();
            if (current != null && current.getNetworkManager() == owner && owner.isChannelOpen()) {
                cache.put(owner, id, () -> minecraft.getTextureManager().getDynamicTextureLocation(textureType, new DynamicTexture(pixels)));
            }
        });
    }

    public static void clearBase64Textures(NetworkManager owner) {
        if (owner == null) return;
        Minecraft.getMinecraft().addScheduledTask(() -> {
            guiBase64.clear(owner);
            hudBase64.clear(owner);
        });
    }

    public static void m8(String string, BufferedImage bufferedImage) {
        if (!s4.containsKey(string)) {
            Minecraft.getMinecraft().addScheduledTask(() -> storeImage(string, bufferedImage));
        }
    }

    public static void storeImageForConnection(String id, BufferedImage image, NetworkManager owner) {
        VexView.onImageConnection(owner, () -> storeImage(id, image));
    }

    private static void storeImage(String string, BufferedImage bufferedImage) {
        if (!s4.containsKey(string)) {
            s4.put(string, Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("normal", new DynamicTexture(bufferedImage)));
        }
    }

    public static Future<Boolean> m9(String string, VexGifImage vexGifImage) {
        if (string != null && s2.containsKey(string)) {
            return CompletableFuture.completedFuture(true);
        }
        return loadTexture("GIFIMAGE", () -> readImage(string, input -> {
            GifDecoder decoder = new GifDecoder();
            if (decoder.m12(input) != GifDecoder.s3 || decoder.m21() == 0) {
                return null;
            }
            return decoder;
        }), decoder -> {
            List<BufferedImage> frames = new ArrayList<>();
            for (int frame = 0; frame < decoder.m21(); ++frame) {
                frames.add(decoder.m9(frame));
            }
            if (vexGifImage.f2 == 0) {
                vexGifImage.f2 = decoder.f1;
            }
            VexGifImage.s1.put(string, vexGifImage.f2);
            storeGif(string, frames);
        });
    }

    public static void m13(String string, List<BufferedImage> list) {
        if (!s2.containsKey(string)) {
            Minecraft.getMinecraft().addScheduledTask(() -> storeGif(string, list));
        }
    }

    public static void storeGifForConnection(String id, List<BufferedImage> frames,
            NetworkManager owner, Runnable metadata) {
        VexView.onImageConnection(owner, () -> {
            metadata.run();
            storeGif(id, frames);
        });
    }

    private static void storeGif(String string, List<BufferedImage> frames) {
        if (!s2.containsKey(string) && !frames.isEmpty()) {
            List<ResourceLocation> textures = new ArrayList<>(frames.size());
            for (BufferedImage frame : frames) {
                textures.add(Minecraft.getMinecraft().getTextureManager().getDynamicTextureLocation("gif", new DynamicTexture(frame)));
            }
            s2.put(string, textures);
        }
    }

    private static <T> Future<Boolean> loadTexture(String type, Callable<T> decode, Consumer<T> upload) {
        CompletableFuture<Boolean> ready = new CompletableFuture<>();
        NetworkManager owner = VexView.imageConnection();
        Future<Boolean> task = VexView.m2(() -> {
            if (ready.isCancelled()) {
                return false;
            }
            try {
                T image = decode.call();
                if (image == null) {
                    ready.complete(false);
                    return false;
                }
                Minecraft.getMinecraft().addScheduledTask(() -> {
                    if (ready.isCancelled()) {
                        return;
                    }
                    if (!VexView.isImageConnectionCurrent(owner)) {
                        ready.complete(false);
                        return;
                    }
                    try {
                        upload.accept(image);
                        ready.complete(true);
                    } catch (RuntimeException exception) {
                        ready.complete(false);
                    }
                });
                return true;
            } catch (Exception exception) {
                if (ready.complete(false) && type != null) {
                    VexView.onImageConnection(owner, () -> NetPacketManager.m4(new PacketDownload(type)));
                }
                return false;
            }
        });
        if (task == null) {
            ready.complete(false);
        }
        return ready;
    }

    public static <T> T readImage(String source, ImageReader<T> reader) throws IOException {
        if (source.contains("[local]")) {
            try (InputStream input = ImageReadLimits.limit(new FileInputStream(LocalImagePaths.resolve(VexConfig.s1, source)))) {
                return reader.read(input);
            }
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(source).openConnection();
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        try (InputStream input = ImageReadLimits.limit(connection.getInputStream())) {
            return reader.read(input);
        } finally {
            connection.disconnect();
        }
    }

    public interface ImageReader<T> {
        T read(InputStream input) throws IOException;
    }

    public static void m14(String string) {
        if (!s1.containsKey(string)) {
            Minecraft.getMinecraft().addScheduledTask(() -> {
                String string2 = string;
                String string3 = string;
                s1.put(string3, new ResourceLocation(string3));
            });
        }
    }
}

