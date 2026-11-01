package android.os;
import java.util.concurrent.ConcurrentLinkedQueue;
public final class Handler {
    public static final ConcurrentLinkedQueue<Runnable> QUEUE = new ConcurrentLinkedQueue<Runnable>();
    public Handler(Looper looper) {}
    public boolean post(Runnable task) { QUEUE.add(task); return true; }
}
