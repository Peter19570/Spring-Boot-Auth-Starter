package com.example.projectname.microservice.authentication.exception;

public class MailSenderException extends RuntimeException {
    public MailSenderException(String message) {
        super(message);
    }
}
