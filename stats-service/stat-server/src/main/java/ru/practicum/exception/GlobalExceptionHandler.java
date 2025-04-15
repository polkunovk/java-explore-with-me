package ru.practicum.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        log.warn("Ошибка валидации аргументов: {}", ex.getMessage(), ex);

        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fieldError -> String.format("Поле '%s': %s",
                        fieldError.getField(),
                        fieldError.getDefaultMessage()))
                .toList();

        return buildApiError(
                "Ошибка валидации полей запроса",
                "MethodArgumentNotValid",
                errors,
                Map.of("invalidFieldsCount", errors.size())
        );
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleMissingServletRequestParameter(MissingServletRequestParameterException ex) {
        log.warn("Отсутствует обязательный параметр: {}", ex.getMessage(), ex);

        String message = String.format("Отсутствует обязательный параметр: '%s'", ex.getParameterName());
        return buildApiError(
                message,
                "MissingServletRequestParameter",
                Collections.emptyList(),
                Map.of("missingParameter", ex.getParameterName())
        );
    }

    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError handleValidationException(ValidationException ex) {
        log.warn("Ошибка валидации: {}", ex.getMessage(), ex);

        String message = ex.getMessage();
        String reason = "ValidationException";
        Map<String, Object> context = null;

        if (message != null) {
            String[] parts = message.split(":", 2);
            if (parts.length > 1) {
                context = Map.of("entityId", parts[1].trim());
                message = parts[0].trim();
            }
        }

        return buildApiError(
                message,
                reason,
                Collections.emptyList(),
                context
        );
    }

    private ApiError buildApiError(String message,
                                   String reason,
                                   List<String> errors,
                                   Map<String, Object> context) {
        return ApiError.builder()
                .message(message)
                .reason(reason)
                .errors(errors)
                .status(HttpStatus.BAD_REQUEST.name())
                .localDateTime(LocalDateTime.now())
                .context(context)
                .build();
    }
}