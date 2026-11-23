package lk.vexview.engine;

import java.util.concurrent.Executor;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Carries packet origin across asynchronous image work and validates it on the client thread. */
public final class ImageConnectionGuard<C> {
    private final ThreadLocal<C> packetSource = new ThreadLocal<>();
    private final Supplier<C> current;
    private final Predicate<C> open;
    private final Executor client;

    public ImageConnectionGuard(Supplier<C> current, Predicate<C> open, Executor client) {
        this.current = current;
        this.open = open;
        this.client = client;
    }

    public C enter(C source) {
        C previous = packetSource.get();
        if (source == null) packetSource.remove();
        else packetSource.set(source);
        return previous;
    }

    public void leave(C previous) {
        if (previous == null) packetSource.remove();
        else packetSource.set(previous);
    }

    public C capture() {
        C source = packetSource.get();
        return source == null ? current.get() : source;
    }

    public boolean isCurrent(C source) {
        return source == current.get() && (source == null || open.test(source));
    }

    public void run(C source, Runnable action) {
        client.execute(() -> {
            if (isCurrent(source)) action.run();
        });
    }
}
