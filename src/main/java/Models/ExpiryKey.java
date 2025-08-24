package Models;

public class ExpiryKey {

    private String value;
    private Long expiryTime;

    public ExpiryKey(String value, long expiryTime) {
        this.value = value;
        this.expiryTime = expiryTime;
    }

    public String getValue() {
        return value;
    }

    public Long getExpiryTime() {
        return expiryTime;
    }

    public void setExpiryTime(Long expiryTime) {
        this.expiryTime = expiryTime;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
