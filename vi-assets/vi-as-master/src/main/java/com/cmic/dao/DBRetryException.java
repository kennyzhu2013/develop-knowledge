package com.cmic.dao;

public class DBRetryException extends RuntimeException {
    private static final long serialVersionUID = -1801314887995671258L;

    public DBRetryException(String message) {
        super(message);
    }

    public DBRetryException(String message, Throwable t) {
        super(message, t);
    }
}
