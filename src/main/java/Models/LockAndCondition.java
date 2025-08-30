package Models;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class LockAndCondition {

    private final ReentrantLock lock;
    private final Condition condition;

    public LockAndCondition() {
        this.lock = new ReentrantLock(true);
        this.condition = lock.newCondition();
    }

    public ReentrantLock getLock() {
        return lock;
    }

    public Condition getCondition() {
        return condition;
    }
}
