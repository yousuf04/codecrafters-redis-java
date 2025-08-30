package Models;

import java.util.Map;

public class Entry {

    Long milliseconds;
    Long sequenceNumber;
    Map<String, String> keyValueMap;

    public Entry(Long milliseconds, Long sequenceNumber, Map<String, String> keyValueMap) {
        this.milliseconds = milliseconds;
        this.sequenceNumber = sequenceNumber;
        this.keyValueMap = keyValueMap;
    }

    public Long getMilliseconds() {
        return milliseconds;
    }

    public Long getSequenceNumber() {
        return sequenceNumber;
    }

    public Map<String, String> getKeyValueMap() {
        return keyValueMap;
    }

    public String getId() {
        return milliseconds + "-" + sequenceNumber;
    }

    public void setKeyValueMap(Map<String, String> keyValueMap) {
        this.keyValueMap = keyValueMap;
    }
}
