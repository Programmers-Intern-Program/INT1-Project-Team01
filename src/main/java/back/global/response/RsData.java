package back.global.response;

public record RsData<T>(T data, String message) {
    public RsData(String message) {
        this(null, message);
    }
}
