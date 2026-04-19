package com.example.projectname.microservice.authentication.exception;

public class LockedException extends RuntimeException {
    public LockedException(String message) {
        super(message);
    }
}
