package com.aninditb.shortlink.exception;

public class InvalidUrlException extends RuntimeException {

    public InvalidUrlException(String message) {
        super(message);
    }
}
