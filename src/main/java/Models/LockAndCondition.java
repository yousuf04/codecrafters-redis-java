package Models;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class LockAndCondition {

    private final ReentrantLock lock;
    private final BlockingQueue<Condition> waiters;

    public LockAndCondition() {
        this.lock = new ReentrantLock(true);
        this.waiters = new LinkedBlockingQueue<>();
    }

    public ReentrantLock getLock() {
        return lock;
    }

    public BlockingQueue<Condition> getWaiters() {
        return waiters;
    }
}
