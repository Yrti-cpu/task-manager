package org.yrti.taskmanager.exception;

public class UserHasTasksException extends RuntimeException {
    public UserHasTasksException(Long id) {
        super("User has tasks: " + id);
    }
}
