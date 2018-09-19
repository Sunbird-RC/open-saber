package io.opensaber.registry.middleware.transform.commons;



public class NodeMappingNotDefinedException extends Exception {

    private ErrorCode errorCode;

    public NodeMappingNotDefinedException(String message, Throwable error, ErrorCode errorCode) {
        super(message, error);
        this.errorCode = errorCode;
    }

    public NodeMappingNotDefinedException(String message, ErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

}
