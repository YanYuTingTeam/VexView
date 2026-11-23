import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicReference;
import lk.vexview.engine.ImageConnectionGuard;

public final class ImageConnectionGuardTest {
    private static final class Connection { boolean open = true; }
    private static void check(boolean result, String message) { if (!result) throw new AssertionError(message); }
    public static void main(String[] args) {
        Queue<Runnable> clientQueue = new ArrayDeque<>();
        AtomicReference<Connection> active = new AtomicReference<>();
        List<String> published = new ArrayList<>();
        ImageConnectionGuard<Connection> guard = new ImageConnectionGuard<>(active::get, c -> c.open, clientQueue::add);
        Connection old = new Connection(), next = new Connection();
        active.set(old);
        Connection oldOwner = guard.capture();
        guard.run(oldOwner, () -> published.add("old-texture"));
        guard.run(oldOwner, () -> published.add("old-gif-metadata"));
        guard.run(oldOwner, () -> published.add("old-failure-packet"));
        active.set(next);
        while (!clientQueue.isEmpty()) clientQueue.remove().run();
        check(published.isEmpty(), "late old task escaped the connection guard");
        Connection previous = guard.enter(old);
        check(guard.capture() == old, "late packet lost its source connection");
        guard.leave(previous);
        check(guard.capture() == next, "packet context leaked to the next operation");
        guard.run(guard.capture(), () -> published.add("new-texture"));
        clientQueue.remove().run();
        check(published.size() == 1 && published.contains("new-texture"), "new connection could not publish");
        next.open = false;
        guard.run(next, () -> published.add("closed"));
        clientQueue.remove().run();
        check(!published.contains("closed"), "closed channel was accepted");
        active.set(null);
        guard.run(guard.capture(), () -> published.add("offline-local"));
        clientQueue.remove().run();
        check(published.contains("offline-local"), "offline local image was rejected");
        guard.run(null, () -> published.add("late-offline"));
        active.set(next);
        clientQueue.remove().run();
        check(!published.contains("late-offline"), "offline task escaped into new connection");
        System.out.println("PASS: source connection, stale uploads, GIF metadata, failures, new and offline sessions");
    }
}
