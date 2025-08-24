package Models;

public class ExpiryKey {

    Object value;
    Long expiryTime;

    public ExpiryKey(Object value, long expiryTime) {
        this.value = value;
        this.expiryTime = expiryTime;
    }

    public Object getValue() {
        return value;
    }

    public Long getExpiryTime() {
        return expiryTime;
    }

    public void setExpiryTime(Long expiryTime) {
        this.expiryTime = expiryTime;
    }

    public void setValue(Object value) {
        this.value = value;
    }
}
