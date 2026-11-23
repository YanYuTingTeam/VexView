package lk.vexview.net;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Iterator;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.json.JSONObject;

public final class Base64ImagePackets<C> {
    // Bounds apply independently to each connection and image channel.
    private static final int MAX_HEADER_CHARS = 64 * 1024;
    private static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
    private static final int MAX_ENCODED_CHARS = 4 * ((MAX_IMAGE_BYTES + 2) / 3);
    public static final int MAX_PACKET_BYTES = MAX_ENCODED_CHARS + MAX_HEADER_CHARS;
    private static final int MAX_PENDING_CHARS = 24 * 1024 * 1024;
    private static final int MAX_PENDING_IMAGES = 16;
    private static final int MAX_DIMENSION = 4096;
    private static final long MAX_PIXELS = 4096L * 4096;
    private final Map<C, Session> sessions = new WeakHashMap<>();
    private final Executor worker;
    private final Executor client;
    private final Predicate<C> isCurrentConnection;
    private final BiConsumer<String, BufferedImage> upload;

    public Base64ImagePackets(Executor worker, Executor client, Predicate<C> isCurrentConnection,
            BiConsumer<String, BufferedImage> upload) {
        this.worker = worker;
        this.client = client;
        this.isCurrentConnection = isCurrentConnection;
        this.upload = upload;
    }

    public synchronized void receive(C connection, String packet) {
        if (connection == null) {
            return;
        }
        Session session = sessions.computeIfAbsent(connection, key -> new Session());
        if (session.closed) {
            return;
        }
        if (isHeader(packet)) {
            session.header = packet.length() <= MAX_HEADER_CHARS ? packet : null;
            return;
        }

        String header = session.header;
        session.header = null;
        if (header == null || packet == null || packet.isEmpty()) {
            return;
        }
        if (packet.length() > MAX_ENCODED_CHARS) return;
        int chars = header.length() + packet.length();
        if (session.images.size() >= MAX_PENDING_IMAGES
                || chars > MAX_PENDING_CHARS - session.pendingChars) {
            return;
        }
        session.images.addLast(new ImagePacket(header, packet));
        session.pendingChars += chars;
        if (!session.decoding) {
            session.decoding = true;
            submitDecode(connection, session);
        }
    }

    public synchronized void disconnect(C connection) {
        if (connection != null) {
            Session session = sessions.computeIfAbsent(connection, key -> new Session());
            session.closed = true;
            session.header = null;
            session.images.clear();
            session.pendingChars = 0;
        }
    }

    public synchronized void rejectPacket(C connection) {
        Session session = sessions.get(connection);
        if (session != null) session.header = null;
    }

    private void submitDecode(C connection, Session session) {
        try {
            worker.execute(() -> decodeNext(connection, session));
        } catch (RejectedExecutionException exception) {
            session.images.clear();
            session.pendingChars = 0;
            session.decoding = false;
        }
    }

    private void decodeNext(C connection, Session session) {
        ImagePacket image;
        synchronized (this) {
            image = session.images.pollFirst();
            if (image != null) {
                session.pendingChars -= image.header.length() + image.payload.length();
            }
        }
        boolean uploadQueued = false;
        AtomicBoolean finished = new AtomicBoolean();
        Runnable finish = () -> {
            if (finished.compareAndSet(false, true)) finishDecode(connection, session);
        };
        try {
            if (image == null || session.closed) {
                return;
            }
            String id = new JSONObject(image.header).getString("id");
            if (id.isEmpty()) {
                return;
            }
            byte[] bytes = Base64.getDecoder().decode(image.payload);
            if (bytes.length > MAX_IMAGE_BYTES) return;
            BufferedImage pixels = readBoundedImage(bytes);
            if (pixels != null && !session.closed) {
                client.execute(() -> {
                    try {
                        if (!session.closed && isCurrentConnection.test(connection)) {
                            upload.accept(id, pixels);
                        }
                    } finally {
                        finish.run();
                    }
                });
                uploadQueued = true;
            }
        } catch (IOException | RuntimeException exception) {
            // Ignore a malformed image pair and resume at the next header.
        } finally {
            if (!uploadQueued) {
                finish.run();
            }
        }
    }

    private synchronized void finishDecode(C connection, Session session) {
        if (session.images.isEmpty() || session.closed) {
            session.decoding = false;
        } else {
            submitDecode(connection, session);
        }
    }

    private static BufferedImage readBoundedImage(byte[] bytes) throws IOException {
        try (ImageInputStream input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || width > MAX_DIMENSION || height > MAX_DIMENSION
                        || (long) width * height > MAX_PIXELS) return null;
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    private static boolean isHeader(String packet) {
        if (packet != null) {
            for (int i = 0; i < packet.length(); i++) {
                char next = packet.charAt(i);
                if (next > ' ') {
                    return next == '{';
                }
            }
        }
        return false;
    }

    private static final class Session {
        private final Deque<ImagePacket> images = new ArrayDeque<>();
        private String header;
        private int pendingChars;
        private boolean decoding;
        private volatile boolean closed;
    }

    private static final class ImagePacket {
        private final String header;
        private final String payload;

        private ImagePacket(String header, String payload) {
            this.header = header;
            this.payload = payload;
        }
    }
}
