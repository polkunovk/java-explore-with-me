package ru.practicum.service.UserRequest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dtos.request.ParticipationRequestDto;
import ru.practicum.enums.State;
import ru.practicum.enums.Status;
import ru.practicum.error.exception.*;
import ru.practicum.mapper.RequestMapper;
import ru.practicum.model.Event;
import ru.practicum.model.Request;
import ru.practicum.model.User;
import ru.practicum.repository.EventRepository;
import ru.practicum.repository.RequestRepository;
import ru.practicum.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UserRequestServiceImpl implements UserRequestService {
    private final RequestRepository requestRepository;
    private final UserRepository userRepository;
    private final EventRepository eventRepository;

    @Override
    public List<ParticipationRequestDto> getAllUserRequests(Long userId) {
        log.info("Получение всех запросов пользователя с userId={}", userId);
        checkUserExists(userId);
        List<ParticipationRequestDto> result = requestRepository.findAllByRequesterId(userId).stream()
                .map(RequestMapper::toParticipationRequestDto)
                .toList();
        log.info("Найдено {} запросов для пользователя с id={}", result.size(), userId);
        return result;
    }

    @Transactional
    @Override
    public ParticipationRequestDto createUserRequest(Long userId, Long eventId) {
        log.info("Создание запроса пользователя с userId={} и eventId={}", userId, eventId);

        User user = userRepository.findById(userId).orElseThrow(
                () -> new EntityNotFoundException(String.format("Пользователь с id=%d не найден", userId)));
        Event event = eventRepository.findById(eventId).orElseThrow(
                () -> new EntityNotFoundException(String.format("Событие с id=%d не найдено", eventId)));

        if (!event.getState().equals(State.PUBLISHED)) {
            log.info("Событие с id={} не опубликовано", eventId);
            throw new InvalidStateException(String.format("Событие с id=%d не опубликовано", eventId));
        }

        if (requestRepository.existsByEventIdAndRequesterId(eventId, userId)) {
            log.info("Запрос уже создан с userId={} и eventId={}", userId, eventId);
            throw new InvalidStateException(String.format(
                    "Запрос уже создан с userId=%d и eventId=%d", userId, eventId));
        }

        if (event.getInitiator().getId().equals(userId)) {
            log.info("Пользователь с id={} является инициатором события с id={}", userId, eventId);
            throw new SelfParticipationException(String.format("Инициатор события с id=%d " +
                    "не может добавить запрос на участие в своем событии с id=%d", userId, eventId));
        }

        Integer requestCount = requestRepository.countByEventId(Math.toIntExact(eventId));
        log.info("Всего запросов на участие в событии {}: {}", eventId, requestCount);

        if (event.getParticipantLimit() > 0 && requestCount >= event.getParticipantLimit()) {
            log.info("Событие с id={} уже достигло лимита участников", eventId);
            throw new ParticipantLimitReachedException(String.format(
                    "Событие с id=%d уже достигло лимита участников", eventId));
        }

        Request newRequest = new Request();
        newRequest.setEvent(event);
        newRequest.setRequester(user);
        newRequest.setCreated(LocalDateTime.now());

        if (!event.getRequestModeration() || event.getParticipantLimit() == 0) {
            log.info("Событие с id={} не требует модерации", eventId);
            newRequest.setStatus(Status.CONFIRMED);

            event.setConfirmedRequests(Optional.ofNullable(event.getConfirmedRequests()).orElse(0) + 1);

            eventRepository.save(event);
        } else {
            log.info("Событие с id={} требует модерации", eventId);
            newRequest.setStatus(Status.PENDING);
        }
        Request result = requestRepository.save(newRequest);
        log.info("Сохранен новый запрос с id={}", newRequest.getId());
        return RequestMapper.toParticipationRequestDto(result);
    }

    @Transactional
    @Override
    public ParticipationRequestDto cancelUserRequest(Long userId, Long requestId) {
        log.info("Отмена запроса пользователя с userId={} и requestId={}", userId, requestId);
        checkUserExists(userId);
        Request request = requestRepository.findById(requestId).orElseThrow(
                () -> new EntityNotFoundException(String.format("Запрос с id=%d не найден", requestId)));
        if (!request.getRequester().getId().equals(userId)) {
            log.info("Запрос с id={} не был создан пользователем с id={}", requestId, userId);
            throw new ValidationException(String.format(
                    "Запрос с id=%d не был создан пользователем с id=%d", requestId, userId));
        }
        request.setStatus(Status.CANCELED);
        return RequestMapper.toParticipationRequestDto(requestRepository.save(request));
    }

    private void checkUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            log.info("Пользователь с id={} не найден", userId);
            throw new EntityNotFoundException(String.format("Пользователь с id=%d не найден", userId));
        }
    }
}
