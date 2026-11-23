package lk.vexview.net;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import javax.imageio.ImageIO;

public final class Base64ImagePacketsTest {
    private static final int FIRST = 0xff102030;
    private static final int SECOND = 0xffa0b0c0;

    private final Deque<Runnable> worker = new ArrayDeque<>();
    private final Deque<Runnable> client = new ArrayDeque<>();
    private final List<String> uploaded = new ArrayList<>();
    private Object active = new Object();
    private boolean rejectNextWorker;

    public static void main(String[] args) throws IOException {
        new Base64ImagePacketsTest().keepsPairsOrderedAndChannelsIndependent();
        new Base64ImagePacketsTest().recoversFromMalformedPairsAndWorkerRejection();
        new Base64ImagePacketsTest().dropsOldPendingImagesAndQueuedUploads();
        new Base64ImagePacketsTest().rejectsOversizedInputAndRecovers();
        new Base64ImagePacketsTest().boundsPendingImagesAndDecodedUploads();
        System.out.println("Base64 image transfer checks passed");
    }

    private void keepsPairsOrderedAndChannelsIndependent() throws IOException {
        Base64ImagePackets<Object> gui = channel("gui");
        Base64ImagePackets<Object> hud = channel("hud");

        gui.receive(active, header("first"));
        hud.receive(active, header("other"));
        require(worker.isEmpty(), "headers alone must not start workers");
        gui.receive(active, image(FIRST));
        gui.receive(active, header("second"));
        gui.receive(active, image(SECOND));
        hud.receive(active, image(SECOND));
        require(worker.size() == 2, "only one decode per channel may be in flight");

        drain(worker);
        require(uploaded.isEmpty(), "decode must only queue uploads");
        drain(client);
        drain(worker);
        drain(client);
        expect("gui/first/" + FIRST, "hud/other/" + SECOND, "gui/second/" + SECOND);
    }

    private void rejectsOversizedInputAndRecovers() throws IOException {
        Base64ImagePackets<Object> gui = channel("gui");
        gui.receive(active, "{\"id\":\"" + repeat('a', 65536) + "\"}");
        gui.receive(active, image(FIRST));
        gui.receive(active, header("oversized-payload"));
        gui.receive(active, repeat('A', 4 * ((8 * 1024 * 1024 + 2) / 3) + 1));
        gui.receive(active, header("oversized-dimensions"));
        gui.receive(active, image(4097, 1));
        gui.receive(active, header("packet-rejected-before-conversion"));
        gui.rejectPacket(active);
        gui.receive(active, image(FIRST));
        gui.receive(active, header("recovered"));
        gui.receive(active, image(SECOND));
        drain(worker);
        drain(client);
        expect("gui/recovered/" + SECOND);
    }

    private void boundsPendingImagesAndDecodedUploads() throws IOException {
        Base64ImagePackets<Object> gui = channel("gui");
        String payload = image(FIRST);
        for (int i = 0; i < 20; ++i) {
            gui.receive(active, header("queued-" + i));
            gui.receive(active, payload);
        }
        require(worker.size() == 1, "only one decode should be scheduled");
        drain(worker);
        require(client.size() == 1, "only one decoded image may await upload");
        require(worker.isEmpty(), "next decode must wait for upload");
        while (!client.isEmpty() || !worker.isEmpty()) {
            drain(client);
            drain(worker);
        }
        require(uploaded.size() == 16, "pending image queue must be bounded: " + uploaded.size());
        require(uploaded.get(15).equals("gui/queued-15/" + FIRST), "queue must retain order");
        gui.receive(active, header("after-full"));
        gui.receive(active, payload);
        drain(worker);
        drain(client);
        require(uploaded.get(16).equals("gui/after-full/" + FIRST), "queue must recover after overload");
    }

    private static String repeat(char c, int length) {
        char[] chars = new char[length];
        Arrays.fill(chars, c);
        return new String(chars);
    }

    private static String image(int width, int height) throws IOException {
        BufferedImage pixels = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(pixels, "png", output)) throw new IOException("No PNG writer");
        return Base64.getEncoder().encodeToString(output.toByteArray());
    }

    private void recoversFromMalformedPairsAndWorkerRejection() throws IOException {
        Base64ImagePackets<Object> gui = channel("gui");
        gui.receive(active, image(FIRST));
        gui.receive(active, "{\"wrongKey\":\"ignored\"}");
        gui.receive(active, image(FIRST));
        gui.receive(active, header("bad-base64"));
        gui.receive(active, "malformed base64!");
        gui.receive(active, header("not-an-image"));
        gui.receive(active, Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}));
        drain(worker);

        rejectNextWorker = true;
        gui.receive(active, header("rejected"));
        gui.receive(active, image(FIRST));
        gui.receive(active, header("superseded-header"));
        gui.receive(active, header("recovered"));
        gui.receive(active, image(SECOND));
        drain(worker);
        drain(client);
        expect("gui/recovered/" + SECOND);
    }

    private void dropsOldPendingImagesAndQueuedUploads() throws IOException {
        Base64ImagePackets<Object> gui = channel("gui");
        Base64ImagePackets<Object> hud = channel("hud");
        Object oldConnection = active;

        gui.receive(oldConnection, header("old-gui"));
        gui.receive(oldConnection, image(FIRST));
        hud.receive(oldConnection, header("old-hud"));
        hud.receive(oldConnection, image(FIRST));
        drain(worker);
        require(client.size() == 2, "old images must already be queued for upload");

        gui.receive(oldConnection, header("old-pending"));
        active = new Object();
        gui.receive(active, image(SECOND));
        hud.receive(active, header("new-hud"));
        hud.receive(active, image(SECOND));
        gui.receive(active, header("new-gui"));
        gui.disconnect(oldConnection);
        gui.receive(oldConnection, header("late-old-gui"));
        gui.receive(oldConnection, image(FIRST));
        gui.receive(active, image(SECOND));
        drain(worker);
        drain(client);
        expect("hud/new-hud/" + SECOND, "gui/new-gui/" + SECOND);
    }

    private Base64ImagePackets<Object> channel(String channel) {
        return new Base64ImagePackets<>(task -> {
            if (rejectNextWorker) {
                rejectNextWorker = false;
                throw new RejectedExecutionException();
            }
            worker.addLast(task);
        }, client::addLast, connection -> connection == active,
            (id, pixels) -> uploaded.add(channel + "/" + id + "/" + pixels.getRGB(0, 0)));
    }

    private static String header(String id) {
        return "{\"id\":\"" + id + "\"}";
    }

    private static String image(int color) throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, color);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", output)) {
            throw new IOException("No PNG writer");
        }
        return Base64.getEncoder().encodeToString(output.toByteArray());
    }

    private static void drain(Deque<Runnable> tasks) {
        while (!tasks.isEmpty()) {
            tasks.removeFirst().run();
        }
    }

    private void expect(String... expected) {
        require(uploaded.equals(Arrays.asList(expected)), "unexpected uploads: " + uploaded);
    }

    private static void require(boolean success, String message) {
        if (!success) {
            throw new AssertionError(message);
        }
    }
}
