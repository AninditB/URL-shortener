package com.aninditb.shortlink.exception;

public class AliasAlreadyExistsException extends RuntimeException {

    public AliasAlreadyExistsException(String message) {
        super(message);
    }
}
