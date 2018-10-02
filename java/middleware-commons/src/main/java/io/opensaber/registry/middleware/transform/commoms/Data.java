package io.opensaber.registry.middleware.transform.commoms;

public class Data<T> {

    private final T data;

    public Data(T data) {
        this.data = data;
    }

    public T getData() {
        return data;
    }
}
