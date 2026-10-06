package za.co.solar.fleet.integration;

public record ConnectorResult<T>(boolean success, T data, String error) {
    public static <T> ConnectorResult<T> ok(T data) { return new ConnectorResult<>(true, data, null); }
    public static <T> ConnectorResult<T> fail(String error) { return new ConnectorResult<>(false, null, error); }
}
