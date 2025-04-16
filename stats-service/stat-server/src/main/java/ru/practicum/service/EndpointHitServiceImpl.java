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
import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class EndpointHitServiceImpl implements EndpointHitService {
    private final EndpointHitRepository endpointHitRepository;

    @Transactional
    @Override
    public void saveStat(EndpointHitDto statDto) {
        log.info("Попытка сохранить статистику: {}", statDto);
        endpointHitRepository.save(EndpointHitMapper.toEntity(statDto));
        log.info("Статистика сохранена: {}", statDto);
    }

    @Transactional
    @Override
    public List<ViewStats> getViewStats(String start, String end, List<String> uris, boolean unique) {
        log.info("Получение статистики посещений: start={}, end={}, uris={}, unique={}", start, end, uris, unique);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime startDateTime = LocalDateTime.parse(start, formatter);
        LocalDateTime endDateTime = LocalDateTime.parse(end, formatter);

        if (startDateTime.isAfter(endDateTime)) {
            log.error("Дата начала позже даты окончания");
            throw new ValidationException("Дата начала позже даты окончания");
        }

        if (uris == null || uris.isEmpty()) {
            if (unique) {
                log.info("Получение статистики посещений без uris: уникальные IP");
                return endpointHitRepository
                        .findAllByTimestampBetweenStartAndEndWithUniqueIp(startDateTime, endDateTime);
            } else {
                log.info("Получение статистики посещений без uris: IP не уникальные");
                return endpointHitRepository
                        .findAllByTimestampBetweenStartAndEndWhereIpNotUnique(startDateTime, endDateTime);
            }
        } else {
            if (unique) {
                log.info("Получение статистики посещений с uris: уникальные IP");
                return endpointHitRepository
                        .findAllByTimestampBetweenStartAndEndAndUriUniqueIp(startDateTime, endDateTime, uris);
            } else {
                log.info("Получение статистики посещений с uris: IP не уникальные");
                return endpointHitRepository
                        .findAllByTimestampBetweenStartAndEndAndUriWhereIpNotUnique(startDateTime, endDateTime, uris);
            }
        }
    }
}