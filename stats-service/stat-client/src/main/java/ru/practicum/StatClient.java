package ru.practicum;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.practicum.dto.EndpointHitDto;
import ru.practicum.dto.ViewStats;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static ru.practicum.dto.TimeFormat.FORMATTER;

@Slf4j
@Component
public class StatClient {
    private final RestClient client;

    public StatClient(@Value("${stats-service.url}") String serverUrl) {
        this.client = RestClient.create(serverUrl);
        log.info("URL статистического сервера: {}", serverUrl);
    }

    public void save(String app, HttpServletRequest request) {
        log.info("Сохранение хита для приложения: {} , uri {}", app, request.getRequestURI());
        EndpointHitDto dto = getDto(app, request);
        log.info("Начало создания запроса для статистического сервиса");
        ResponseEntity<Void> response = client.post()
                .uri("/hit")
                .contentType(MediaType.APPLICATION_JSON)
                .body(dto)
                .retrieve().toBodilessEntity();
        log.info("Сохранение хита для приложения: {} с успешным кодом {}", app, response.getStatusCode());
    }

    public List<ViewStats> getViewStats(LocalDateTime start, LocalDateTime end,
                                        List<String> uris, boolean unique) {
        log.info("Получение статистики просмотров для uri: {}", uris);
        try {
            return client.get()
                    .uri(uriBuilder -> uriBuilder.path("/stats")
                            .queryParam("start", start.format(FORMATTER))
                            .queryParam("end", end.format(FORMATTER))
                            .queryParam("uris", uris)
                            .queryParam("unique", unique)
                            .build())
                    .retrieve()
                    .onStatus(HttpStatusCode::is2xxSuccessful,
                            ((request, response) -> log.info("Получение статистики для {} с успешным кодом {}", uris,
                                    response.getStatusCode())))
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (Exception e) {
            log.error("Получение статистики для {} с ошибкой {}", uris, e.getMessage());
            return Collections.emptyList();
        }
    }

    private EndpointHitDto getDto(String app, HttpServletRequest request) {
        log.info("Начало построения dto для приложения {}", app);
        return EndpointHitDto.builder()
                .app(app)
                .uri(request.getRequestURI())
                .ip(request.getRemoteAddr())
                .timestamp(LocalDateTime.now())
                .build();
    }
}