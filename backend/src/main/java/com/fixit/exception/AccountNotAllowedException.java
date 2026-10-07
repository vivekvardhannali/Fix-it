package com.fixit.exception;

/** Thrown when a Google identity may not use Fix It (non-IITM or unverified email). */
public class AccountNotAllowedException extends RuntimeException {
    public AccountNotAllowedException(String message) {
        super(message);
    }
}
