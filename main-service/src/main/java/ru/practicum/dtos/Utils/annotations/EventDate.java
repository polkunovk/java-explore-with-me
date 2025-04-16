package ru.practicum.dtos.utils.annotations;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import ru.practicum.dtos.utils.constraints.EventDateValidator;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = EventDateValidator.class)
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EventDate {
    String message() default "дата и время, на которые запланировано событие, " +
            "не могут быть ранее чем через два часа от текущего момента";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
