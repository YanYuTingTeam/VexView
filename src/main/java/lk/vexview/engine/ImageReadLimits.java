package lk.vexview.engine;

import java.awt.image.BufferedImage;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;

/** Bounds untrusted images before allocating their decoded pixels. */
public final class ImageReadLimits {
    public static final int MAX_DIMENSION = 4096;
    public static final long MAX_PIXELS = 16777216L;
    public static final long MAX_SOURCE_BYTES = 16L * 1024 * 1024;

    private ImageReadLimits() {}

    public static boolean validDimensions(int width, int height) {
        return width > 0 && height > 0 && width <= MAX_DIMENSION && height <= MAX_DIMENSION
                && (long) width * height <= MAX_PIXELS;
    }

    public static InputStream limit(InputStream input) {
        return new BoundedStream(input, MAX_SOURCE_BYTES);
    }

    public static BufferedImage read(InputStream input) throws IOException {
        try (ImageInputStream data = new MemoryCacheImageInputStream(input)) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(data);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(data, true, true);
                if (!validDimensions(reader.getWidth(0), reader.getHeight(0))) {
                    throw new IOException("Image dimensions exceed the permitted limit");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    private static final class BoundedStream extends FilterInputStream {
        private long remaining;
        private BoundedStream(InputStream input, long limit) { super(input); remaining = limit; }
        @Override public int read() throws IOException {
            if (remaining == 0) {
                if (in.read() == -1) return -1;
                throw new IOException("Image data exceeds the permitted limit");
            }
            int value = in.read();
            if (value != -1) remaining--;
            return value;
        }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            if (length == 0) return 0;
            if (remaining == 0) return read();
            int count = in.read(bytes, offset, (int)Math.min(length, remaining));
            if (count > 0) remaining -= count;
            return count;
        }
        @Override public long skip(long count) throws IOException {
            long skipped = in.skip(Math.min(count, remaining));
            remaining -= skipped;
            return skipped;
        }
        @Override public boolean markSupported() { return false; }
    }
}
