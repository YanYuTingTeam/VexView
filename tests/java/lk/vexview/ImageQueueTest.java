package lk.vexview;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class ImageQueueTest {
    public static void main(String[] args) throws Exception {
        CountDownLatch running = new CountDownLatch(10);
        CountDownLatch release = new CountDownLatch(1);
        List<Future<Boolean>> tasks = new ArrayList<>();
        AtomicInteger completed = new AtomicInteger();
        AtomicBoolean ranOnCaller = new AtomicBoolean();
        Thread caller = Thread.currentThread();
        try {
            for (int i = 0; i < 10; ++i) {
                Future<Boolean> future = VexView.m2(() -> {
                    running.countDown();
                    release.await();
                    return true;
                });
                require(future != null, "worker task was rejected unexpectedly");
                tasks.add(future);
            }
            require(running.await(10, TimeUnit.SECONDS), "worker tasks did not start");
            for (int i = 0; i < 512; ++i) {
                Future<Boolean> future = VexView.m2(() -> {
                    if (Thread.currentThread() == caller) ranOnCaller.set(true);
                    completed.incrementAndGet();
                    return true;
                });
                require(future != null, "queue rejected task before capacity");
                tasks.add(future);
            }
            AtomicBoolean rejectedRan = new AtomicBoolean();
            Future<Boolean> rejected = VexView.m2(() -> {
                rejectedRan.set(true);
                return true;
            });
            require(rejected == null, "saturated queue must reject immediately");
            require(!rejectedRan.get(), "rejected task must not run on caller");
            require(completed.get() == 0, "queued work ran while workers were blocked");
            release.countDown();
            for (Future<Boolean> task : tasks) require(task.get(10, TimeUnit.SECONDS), "accepted task failed");
            require(completed.get() == 512 && !ranOnCaller.get(), "accepted tasks did not run on workers");
            require(!rejectedRan.get(), "rejected task ran after saturation cleared");
            Future<Boolean> recovered = VexView.m2(() -> true);
            require(recovered != null && recovered.get(10, TimeUnit.SECONDS), "queue did not recover");
            System.out.println("PASS: bounded image queue rejects without caller execution and recovers");
        } finally {
            release.countDown();
            VexView.s6.shutdownNow();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
