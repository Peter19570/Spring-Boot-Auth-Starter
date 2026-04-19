package com.example.projectname.microservice.authentication.exception;

public class ExpiredSetupException extends RuntimeException {
    public ExpiredSetupException(String message) {
        super(message);
    }
}
