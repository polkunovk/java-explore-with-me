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
        log.info("Try to save stat: {}", statDto);
        endpointHitRepository.save(EndpointHitMapper.toEntity(statDto));
        log.info("save stat: {}", statDto);
    }

    @Transactional(readOnly = true)
    @Override
    public List<ViewStats> getViewStats(String start, String end, List<String> uris, boolean unique) {
        log.info("get statistics on visits: start={}, end={}, uris={}, unique={}", start, end, uris, unique);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        LocalDateTime startDateTime = LocalDateTime.parse(start, formatter);
        LocalDateTime endDateTime = LocalDateTime.parse(end, formatter);

        if (startDateTime.isAfter(endDateTime)) {
            log.error("Start date is after end date");
            throw new ValidationException("Start date is after end date");
        }

        List<String> urisToPass = (uris == null || uris.isEmpty()) ? null : uris;
        return endpointHitRepository.findStats(startDateTime, endDateTime, urisToPass, unique);
    }
}