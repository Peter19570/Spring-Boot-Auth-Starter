package com.example.projectname.microservice.authentication.exception;

public class DeletedAccountException extends RuntimeException {
    public DeletedAccountException(String message) {
        super(message);
    }
}
