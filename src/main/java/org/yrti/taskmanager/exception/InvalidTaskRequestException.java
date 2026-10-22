package org.yrti.taskmanager.exception;

public class InvalidTaskRequestException extends RuntimeException {
    public InvalidTaskRequestException(String message) {
        super(message);
    }
}
