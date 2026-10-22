package org.yrti.taskmanager.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ResourceNotFoundException.class)
    ProblemDetail notFound(ResourceNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception.getMessage());
    }

    @ExceptionHandler({DuplicateUsernameException.class, UserHasTasksException.class, StaleTaskException.class})
    ProblemDetail conflict(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, exception.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail concurrentModification(ObjectOptimisticLockingFailureException exception) {
        return problem(HttpStatus.CONFLICT, "Resource was modified by another request");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrityViolation(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof org.hibernate.exception.ConstraintViolationException violation) {
                String constraint = violation.getConstraintName();
                if ("uk_users_username".equalsIgnoreCase(constraint)) {
                    return problem(HttpStatus.CONFLICT, "Username already exists");
                }
                if ("fk_tasks_on_owner".equalsIgnoreCase(constraint)) {
                    return problem(HttpStatus.CONFLICT,
                            "Task owner was removed or user still has tasks");
                }
            }
        }
        log.error("Unexpected database integrity violation", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected database integrity violation");
    }

    @ExceptionHandler(InvalidTaskRequestException.class)
    ProblemDetail invalidTaskRequest(InvalidTaskRequestException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail validation(MethodArgumentNotValidException exception) {
        String message = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .distinct().reduce((a, b) -> a + "; " + b).orElse("Invalid request");
        return problem(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ProblemDetail malformed(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "Malformed request or invalid enum value");
    }

    private ProblemDetail problem(HttpStatus status, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        return problem;
    }
}
