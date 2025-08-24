package Models;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

public class DataStore {

    private static DataStore instance;
    public ConcurrentHashMap<String, ExpiryKey> keyMap;
    public ConcurrentHashMap<String, List<String>> listMap;
    public ConcurrentHashMap<String, BlockingQueue<Thread>> clientWaiters;

    private DataStore() {
        keyMap = new ConcurrentHashMap<>();
        listMap = new ConcurrentHashMap<>();
        clientWaiters = new ConcurrentHashMap<>(); // Initialized the new map
    }

    public static synchronized DataStore getInstance() {
        if(instance == null) {
            instance = new DataStore();
        }
        return instance;
    }

    public ConcurrentHashMap<String, List<String>> getListMap() {
        return listMap;
    }

    public ConcurrentHashMap<String, ExpiryKey> getKeyMap() {
        return keyMap;
    }

    public ConcurrentHashMap<String, BlockingQueue<Thread>> getClientWaiters() {
        return clientWaiters;
    }
}