package Models;

import java.util.HashMap;
import java.util.Map;

public class DataStore {
    private static DataStore instance;
    public Map<String, ExpiryKey> keyMap;

    private DataStore() {
        keyMap = new HashMap<>();
    }

    public static synchronized DataStore getInstance() {
        if(instance == null) {
            instance = new DataStore();
        }
        return instance;
    }
    public Map<String, ExpiryKey> getKeyMap() {
        return keyMap;
    }
}
