package lk.vexview.net;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.Deque;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import javax.imageio.ImageIO;
import org.json.JSONObject;

public final class Base64ImagePackets<C> {
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
            session.header = packet;
            return;
        }

        String header = session.header;
        session.header = null;
        if (header == null || packet == null || packet.isEmpty()) {
            return;
        }
        session.images.addLast(new ImagePacket(header, packet));
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
        }
    }

    private void submitDecode(C connection, Session session) {
        try {
            worker.execute(() -> decodeNext(connection, session));
        } catch (RejectedExecutionException exception) {
            session.images.clear();
            session.decoding = false;
        }
    }

    private void decodeNext(C connection, Session session) {
        ImagePacket image;
        synchronized (this) {
            image = session.images.pollFirst();
        }
        try {
            if (image == null || session.closed) {
                return;
            }
            String id = new JSONObject(image.header).getString("id");
            if (id.isEmpty()) {
                return;
            }
            byte[] bytes = Base64.getDecoder().decode(image.payload);
            BufferedImage pixels = ImageIO.read(new ByteArrayInputStream(bytes));
            if (pixels != null && !session.closed) {
                client.execute(() -> {
                    if (!session.closed && isCurrentConnection.test(connection)) {
                        upload.accept(id, pixels);
                    }
                });
            }
        } catch (IOException | RuntimeException exception) {
            // Ignore a malformed image pair and resume at the next header.
        } finally {
            synchronized (this) {
                if (session.images.isEmpty()) {
                    session.decoding = false;
                } else {
                    submitDecode(connection, session);
                }
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
