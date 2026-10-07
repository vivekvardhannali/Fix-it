package com.fixit.exception;

/** Wrong username/password (401). One message for every cause, so it reveals nothing. */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}
