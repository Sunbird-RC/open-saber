package io.opensaber.registry.middleware.transform.commons;

public class ResponseData<T> {

    private final T responseData;

    public ResponseData(T data) {
        this.responseData = data;
    }

    public T getResponseData() {
        return responseData;
    }
}
