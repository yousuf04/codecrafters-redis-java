package Models;

import java.net.Socket;
import java.util.concurrent.BlockingQueue;

public class ExpiryKey {

    Object value;
    Long expiryTime;
    BlockingQueue<Thread> waiters;

    public ExpiryKey(Object value, long expiryTime, BlockingQueue<Thread> waiters) {
        this.value = value;
        this.expiryTime = expiryTime;
        this.waiters = waiters;
    }

    public Object getValue() {
        return value;
    }

    public Long getExpiryTime() {
        return expiryTime;
    }

    public BlockingQueue<Thread> getWaiters() {
        return waiters;
    }

    public void setExpiryTime(Long expiryTime) {
        this.expiryTime = expiryTime;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public void setWaiters(BlockingQueue<Thread> waiters) {
        this.waiters = waiters;
    }
}
