package Models;

import java.net.Socket;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;

public class ExpiryKey {

    Object value;
    Long expiryTime;
    BlockingQueue<Socket> clients;

    public ExpiryKey(Object value, long expiryTime, BlockingQueue<Socket> clients) {
        this.value = value;
        this.expiryTime = expiryTime;
        this.clients = clients;
    }

    public Object getValue() {
        return value;
    }

    public Long getExpiryTime() {
        return expiryTime;
    }

    public BlockingQueue<Socket> getClients() {
        return clients;
    }

    public void setExpiryTime(Long expiryTime) {
        this.expiryTime = expiryTime;
    }

    public void setValue(Object value) {
        this.value = value;
    }

    public void setClients(BlockingQueue<Socket> clients) {
        this.clients = clients;
    }
}
