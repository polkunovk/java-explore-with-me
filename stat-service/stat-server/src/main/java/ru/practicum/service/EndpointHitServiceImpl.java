package ru.practicum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dto.EndpointHitDto;
import ru.practicum.dto.ViewStats;
import ru.practicum.exception.ValidationException;
import ru.practicum.model.mapper.EndpointHitMapper;
import ru.practicum.repository.EndpointHitRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class EndpointHitServiceImpl implements EndpointHitService {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final EndpointHitMapper endpointHitMapper;
    private final EndpointHitRepository endpointHitRepository;

    @Override
    public void saveStat(EndpointHitDto statDto) {
        log.info("Сохранение статистики: {}", statDto);
        endpointHitRepository.save(endpointHitMapper.toEntity(statDto));
        log.info("Статистика успешно сохранена");
    }

    @Override
    @Transactional(readOnly = true)
    public List<ViewStats> getStat(String start, String end, List<String> uris, boolean unique) {
        log.info("Получение статистики: start={}, end={}, uris={}, unique={}", start, end, uris, unique);

        LocalDateTime startDateTime = parseDateTime(start);
        LocalDateTime endDateTime = parseDateTime(end);

        validateTimeRange(startDateTime, endDateTime);

        List<String> processedUris = processUris(uris);

        return unique
                ? getUniqueStats(startDateTime, endDateTime, processedUris)
                : getAllStats(startDateTime, endDateTime, processedUris);
    }

    private LocalDateTime parseDateTime(String dateTime) {
        try {
            return LocalDateTime.parse(dateTime, FORMATTER);
        } catch (Exception e) {
            log.error("Ошибка парсинга даты: {}", dateTime);
            throw new ValidationException("Некорректный формат даты. Используйте yyyy-MM-dd HH:mm:ss");
        }
    }

    private void validateTimeRange(LocalDateTime start, LocalDateTime end) {
        if (start.isAfter(end)) {
            log.error("Начальная дата {} позже конечной {}", start, end);
            throw new ValidationException("Начальная дата не может быть позже конечной");
        }
    }

    private List<String> processUris(List<String> uris) {
        return (uris != null && !uris.isEmpty())
                ? uris
                : Collections.emptyList();
    }

    private List<ViewStats> getUniqueStats(LocalDateTime start, LocalDateTime end, List<String> uris) {
        log.info("Получение статистики по уникальным IP");
        return uris.isEmpty()
                ? endpointHitRepository.findUniqueIpViewStats(start, end, null)
                : endpointHitRepository.findUniqueIpViewStats(start, end, uris);
    }

    private List<ViewStats> getAllStats(LocalDateTime start, LocalDateTime end, List<String> uris) {
        log.info("Получение полной статистики");
        return uris.isEmpty()
                ? endpointHitRepository.findAllViewStats(start, end, null)
                : endpointHitRepository.findAllViewStats(start, end, uris);
    }
}