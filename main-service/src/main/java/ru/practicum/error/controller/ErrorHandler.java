package ru.practicum.error.controller;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import ru.practicum.error.exception.*;
import ru.practicum.error.model.ApiError;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class ErrorHandler {

    @ExceptionHandler({ValidationException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentNotValidException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleBadRequestsException(Exception e) {
        log.warn(e.getMessage(), e);

        String message = "";
        List<String> errors = Collections.emptyList();
        String reason = "";
        Map<String, Object> context = null;

        if (e instanceof MethodArgumentNotValidException ex) {
            errors = ex.getBindingResult().getFieldErrors().stream()
                    .map(error -> "Field '%s': %s".formatted(error.getField(), error.getDefaultMessage()))
                    .toList();
            context = Map.of("invalidFieldsCount", errors.size());
        } else if (e instanceof MissingServletRequestParameterException ex) {
            message = "Required parameter '%s' is missing".formatted(ex.getParameterName());
            reason = "MissingServletRequestParameterException";
            context = Map.of("missingParameter", ex.getParameterName());
        } else if (e instanceof ValidationException ex) {
            message = ex.getMessage();
            reason = "ValidationException";
            context = parseValidationMessage(message);
        }

        return buildApiError(errors, message, reason, HttpStatus.BAD_REQUEST, context);
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError handleNotFound(EntityNotFoundException e) {
        log.warn(e.getMessage(), e);
        return buildApiError(Collections.emptyList(), e.getMessage(),
                "EntityNotFoundException", HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler({DuplicateParticipationRequestException.class,
            InvalidStateException.class,
            SelfParticipationException.class,
            DataIntegrityViolationException.class,
            DuplicateCategoryException.class})
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleConflict(Exception e) {
        log.warn(e.getMessage(), e);

        return switch (e) {
            case DuplicateParticipationRequestException ex ->
                    buildConflictError("Duplicate participation request for event with id=%s".formatted(ex.getMessage()),
                            "DuplicateParticipationRequestException",
                            Map.of("event", ex.getMessage()));

            case InvalidStateException ex ->
                    buildConflictError("Invalid state: %s".formatted(ex.getMessage()),
                            "InvalidStateException",
                            Map.of("state", ex.getMessage()));

            case SelfParticipationException ex ->
                    buildConflictError("User cannot participate in their own event",
                            "SelfParticipationException",
                            Map.of("user", ex.getMessage()));

            case DataIntegrityViolationException ex ->
                    buildConflictError("Data integrity violation occurred",
                            "DataIntegrityViolationException",
                            Map.of("constraint", extractConstraintName(ex),
                                    "message", ex.getMessage()));

            case DuplicateCategoryException ex ->
                    buildConflictError("Category with name '%s' already exists".formatted(ex.getMessage()),
                            "DuplicateCategoryException",
                            Map.of("categoryName", ex.getMessage()));

            default ->
                    buildConflictError("Unexpected conflict error",
                            "ConflictException",
                            Map.of("message", e.getMessage()));
        };
    }

    @ExceptionHandler(ParticipantLimitReachedException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError handleParticipantLimitReached(ParticipantLimitReachedException e) {
        log.warn(e.getMessage(), e);
        return buildApiError(Collections.emptyList(), e.getMessage(),
                "ParticipantLimitReachedException", HttpStatus.CONFLICT);
    }

    private Map<String, Object> parseValidationMessage(String message) {
        if (message.contains(":")) {
            String[] parts = message.split(":", 2);
            return Map.of("entityId", parts[1].trim());
        }
        return Map.of("errorMessage", message);
    }

    private String extractConstraintName(DataIntegrityViolationException ex) {
        Throwable cause = ex.getRootCause();
        return (cause instanceof ConstraintViolationException cvEx)
                ? cvEx.getConstraintName()
                : "Unknown constraint";
    }

    private ApiError buildApiError(List<String> errors, String message, String reason,
                                   HttpStatus status, Map<String, Object> context) {
        return ApiError.builder()
                .errors(errors)
                .message(message)
                .reason(reason)
                .status(status.name())
                .localDateTime(LocalDateTime.now())
                .context(context)
                .build();
    }

    private ApiError buildApiError(List<String> errors, String message,
                                   String reason, HttpStatus status) {
        return buildApiError(errors, message, reason, status, null);
    }

    private ApiError buildConflictError(String message, String reason, Map<String, Object> context) {
        return buildApiError(Collections.emptyList(), message, reason, HttpStatus.CONFLICT, context);
    }
}