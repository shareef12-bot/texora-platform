package com.texora.secops.siem.web;

public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
