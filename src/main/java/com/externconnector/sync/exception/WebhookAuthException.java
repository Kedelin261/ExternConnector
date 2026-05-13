package com.externconnector.sync.exception;

public class WebhookAuthException extends RuntimeException {
    public WebhookAuthException(String message) {
        super(message);
    }
}
