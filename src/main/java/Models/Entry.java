package Models;

import java.util.Map;

public class Entry {

    String id;
    Map<String, String> keyValueMap;

    public Entry(String id, Map<String, String> keyValueMap) {
        this.id = id;
        this.keyValueMap = keyValueMap;
    }

    public String getId() {
        return id;
    }

    public Map<String, String> getKeyValueMap() {
        return keyValueMap;
    }

    public void setKeyValueMap(Map<String, String> keyValueMap) {
        this.keyValueMap = keyValueMap;
    }
}
