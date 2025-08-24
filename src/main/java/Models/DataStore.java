package Models;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

public class DataStore {

    private static DataStore instance;
    private final ConcurrentHashMap<String, ExpiryKey> keyMap;
    private final  ConcurrentHashMap<String, List<String>> listMap;
    private final ConcurrentHashMap<String, Entry> streamMap;
    private final ConcurrentHashMap<String, BlockingQueue<Thread>> clientWaiters;

    private DataStore() {
        keyMap = new ConcurrentHashMap<>();
        listMap = new ConcurrentHashMap<>();
        streamMap = new ConcurrentHashMap<>();
        clientWaiters = new ConcurrentHashMap<>();
    }

    public static synchronized DataStore getInstance() {
        if(instance == null) {
            instance = new DataStore();
        }
        return instance;
    }

    public ConcurrentHashMap<String, ExpiryKey> getKeyMap() {
        return keyMap;
    }

    public ConcurrentHashMap<String, List<String>> getListMap() {
        return listMap;
    }

    public ConcurrentHashMap<String, Entry> getStreamMap() {
        return streamMap;
    }

    public ConcurrentHashMap<String, BlockingQueue<Thread>> getClientWaiters() {
        return clientWaiters;
    }
}