package lk.vexview.gui.core;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import lk.vexview.engine.ImageReadLimits;

public final class ImageReadLimitsTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static byte[] png(int width, int height) throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB), "png", stream);
        byte[] bytes = stream.toByteArray();
        for (int index = 0; index < 4; index++) {
            bytes[16 + index] = (byte) (width >>> (24 - 8 * index));
            bytes[20 + index] = (byte) (height >>> (24 - 8 * index));
        }
        CRC32 crc = new CRC32();
        crc.update(bytes, 12, 17);
        for (int index = 0; index < 4; index++) bytes[29 + index] = (byte) (crc.getValue() >>> (24 - 8 * index));
        return bytes;
    }

    private static byte[] gif(int canvasWidth, int canvasHeight, int frameWidth, int frameHeight, int frames) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(new byte[] {'G', 'I', 'F', '8', '9', 'a', (byte) canvasWidth, (byte) (canvasWidth >> 8),
                (byte) canvasHeight, (byte) (canvasHeight >> 8), (byte) 0x80, 0, 0, 0, 0, 0, -1, -1, -1});
        for (int frame = 0; frame < frames; frame++) {
            out.write(new byte[] {0x2c, 0, 0, 0, 0, (byte) frameWidth, (byte) (frameWidth >> 8),
                    (byte) frameHeight, (byte) (frameHeight >> 8), 0, 2, 2, 0x44, 1, 0});
        }
        out.write(0x3b);
        return out.toByteArray();
    }

    private static final class Bytes extends InputStream {
        long remaining;
        Bytes(long length) { remaining = length; }
        @Override public int read() { if (remaining == 0) return -1; remaining--; return 7; }
        @Override public int read(byte[] dst, int off, int len) {
            if (len == 0) return 0;
            if (remaining == 0) return -1;
            int n = (int) Math.min(remaining, len);
            Arrays.fill(dst, off, off + n, (byte) 7);
            remaining -= n;
            return n;
        }
    }

    private static void consume(InputStream input) throws IOException {
        byte[] buffer = new byte[8192];
        while (input.read(buffer) != -1) { }
    }

    private static final class CanvasProbe extends GifDecoder {
        void nextFrame(int width, int height, int alreadyDecoded) {
            f34 = width;
            f12 = height;
            f25 = alreadyDecoded;
            f33 = 0;
            // Frame rectangle for a 1 x 1 image; m8 must reject based on canvas budget before decoding.
            f10 = new BufferedInputStream(new ByteArrayInputStream(new byte[] {0, 0, 0, 0, 1, 0, 1, 0}));
            m8();
        }
    }

    public static void main(String[] args) throws Exception {
        BufferedImage tiny = ImageReadLimits.read(ImageReadLimits.limit(new ByteArrayInputStream(png(1, 1))));
        check(tiny != null && tiny.getWidth() == 1 && tiny.getHeight() == 1, "normal PNG rejected");
        try {
            ImageReadLimits.read(ImageReadLimits.limit(new ByteArrayInputStream(png(4097, 1))));
            throw new AssertionError("oversize PNG accepted");
        } catch (IOException expected) { }
        check(ImageReadLimits.validDimensions(4096, 4096), "maximum dimensions rejected");
        check(!ImageReadLimits.validDimensions(4097, 1), "oversize dimension accepted");

        GifDecoder normal = new GifDecoder();
        check(normal.m12(ImageReadLimits.limit(new ByteArrayInputStream(gif(1, 1, 1, 1, 2)))) == GifDecoder.s3, "normal GIF rejected");
        check(normal.m21() == 2, "normal GIF lost frames");
        GifDecoder canvas = new GifDecoder();
        check(canvas.m12(new ByteArrayInputStream(gif(4097, 1, 1, 1, 1))) != GifDecoder.s3, "oversize GIF canvas accepted");
        GifDecoder frame = new GifDecoder();
        check(frame.m12(new ByteArrayInputStream(gif(1, 1, 4097, 1, 1))) != GifDecoder.s3, "oversize GIF frame accepted");
        GifDecoder maxFrames = new GifDecoder();
        check(maxFrames.m12(new ByteArrayInputStream(gif(1, 1, 1, 1, 256))) == GifDecoder.s3, "256 GIF frames rejected");
        check(maxFrames.m21() == 256, "256 GIF frames not retained");
        GifDecoder tooMany = new GifDecoder();
        check(tooMany.m12(new ByteArrayInputStream(gif(1, 1, 1, 1, 257))) != GifDecoder.s3, "257 GIF frames accepted");
        CanvasProbe total = new CanvasProbe();
        total.nextFrame(4096, 4096, 4);
        check(total.m16(), "GIF cumulative pixel budget exceeded without rejection");

        consume(ImageReadLimits.limit(new Bytes(ImageReadLimits.MAX_SOURCE_BYTES)));
        try {
            consume(ImageReadLimits.limit(new Bytes(ImageReadLimits.MAX_SOURCE_BYTES + 1)));
            throw new AssertionError("source larger than 16 MiB accepted");
        } catch (IOException expected) { }
        System.out.println("PASS: PNG, GIF dimensions, frame/pixel budgets and 16 MiB source cap");
    }
}
