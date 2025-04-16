package ru.practicum.service.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.StatClient;
import ru.practicum.dtos.event.*;
import ru.practicum.dtos.request.ParticipationRequestDto;
import ru.practicum.enums.State;
import ru.practicum.enums.StateAction;
import ru.practicum.enums.Status;
import ru.practicum.error.exception.EntityNotFoundException;
import ru.practicum.error.exception.InvalidStateException;
import ru.practicum.error.exception.ValidationException;
import ru.practicum.mapper.EventMapper;
import ru.practicum.mapper.RequestMapper;
import ru.practicum.model.Category;
import ru.practicum.model.Event;
import ru.practicum.model.Request;
import ru.practicum.model.User;
import ru.practicum.repository.CategoryRepository;
import ru.practicum.repository.EventRepository;
import ru.practicum.repository.RequestRepository;
import ru.practicum.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {
    private final EventRepository eventRepository;
    private final RequestRepository requestRepository;
    private final CategoryRepository categoryRepository;
    private final StatClient statClient;
    private final EntityManager entityManager;
    private final UserRepository userRepository;

    @Override
    public List<EventFullDto> getAllEventsByUserId(Long userId, Integer from, Integer size) {
        log.info("Получение всех событий пользователя {}", userId);
        if (!userRepository.existsById(userId)) {
            log.error("Пользователь с id {} не найден", userId);
            throw new EntityNotFoundException("Пользователь не найден");
        }
        PageRequest pageRequest = PageRequest.of(from / size, size);
        Page<Event> eventPage = eventRepository.findAllByInitiatorId(userId, pageRequest);
        List<EventFullDto> events = eventPage.getContent().stream()
                .map(EventMapper::toEventFullDto)
                .toList();
        return events.isEmpty() ? Collections.emptyList() : events;
    }

    @Override
    @Transactional
    public EventFullDto addNewEvent(Long userId, NewEventDto newEventDto) {
        log.info("Добавление нового события пользователем {}", userId);
        if (newEventDto.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
            log.error("Дата события {} раньше чем через 2 часа от текущего времени", newEventDto.getEventDate());
            throw new ValidationException("Дата события должна быть минимум на 2 часа позже текущего времени");
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("Пользователь не найден"));
        Category category = categoryRepository.findById(newEventDto.getCategory()).orElseThrow(
                () -> new EntityNotFoundException("Категория не найдена"));
        return EventMapper.toEventFullDto(eventRepository.save(EventMapper.mapToEvent(newEventDto, category, user)));
    }

    @Override
    public EventFullDto getEventOfUser(Long userId, Long eventId) {
        log.info("Получение события пользователя {}", userId);
        if (!userRepository.existsById(userId)) {
            log.error("Пользователь с id {} не найден", userId);
            throw new EntityNotFoundException("Пользователь не найден");
        }
        Event event = eventRepository.findById(eventId).orElseThrow(
                () -> new EntityNotFoundException("Событие не найдено"));
        if (!event.getInitiator().getId().equals(userId)) {
            log.error("Пользователь с id {} не является инициатором события с id {}", userId, eventId);
            throw new ValidationException("Пользователь не является инициатором события");
        }
        return EventMapper.toEventFullDto(event);
    }

    @Override
    @Transactional
    public EventFullDto updateEventOfUser(Long userId, Long eventId, UpdateEventUserRequest eventUserRequest) {
        log.info("Обновление события пользователем {}", userId);
        if (!userRepository.existsById(userId)) {
            log.error("Пользователь с id {} не найден", userId);
            throw new EntityNotFoundException("Пользователь не найден");
        }
        Event event = eventRepository.findById(eventId).orElseThrow(
                () -> new EntityNotFoundException("Событие не найдено"));
        if (!event.getInitiator().getId().equals(userId)) {
            log.error("Пользователь с id {} не является инициатором события с id {}", userId, eventId);
            throw new ValidationException("Пользователь не является инициатором события");
        }
        if (event.getState() == State.PUBLISHED) {
            log.error("Событие с id {} уже опубликовано", eventId);
            throw new InvalidStateException("Нельзя редактировать опубликованное событие");
        }
        Optional.ofNullable(eventUserRequest.getAnnotation()).ifPresent(event::setAnnotation);
        Optional.ofNullable(eventUserRequest.getDescription()).ifPresent(event::setDescription);
        if (eventUserRequest.getEventDate() != null) {
            if (eventUserRequest.getEventDate().isBefore(LocalDateTime.now().plusHours(2))) {
                log.error("Дата события {} раньше чем через 2 часа от текущего времени", eventUserRequest.getEventDate());
                throw new ValidationException("Дата события должна быть минимум на 2 часа позже текущего времени");
            }
            event.setEventDate(eventUserRequest.getEventDate());
        }
        if (eventUserRequest.getLocation() != null) {
            event.setLat(eventUserRequest.getLocation().getLat());
            event.setLon(eventUserRequest.getLocation().getLon());
        }
        Optional.ofNullable(eventUserRequest.getPaid()).ifPresent(event::setPaid);
        Optional.ofNullable(eventUserRequest.getParticipantLimit()).ifPresent(event::setParticipantLimit);
        Optional.ofNullable(eventUserRequest.getRequestModeration()).ifPresent(event::setRequestModeration);
        Optional.ofNullable(eventUserRequest.getTitle()).ifPresent(event::setTitle);
        Optional.ofNullable(eventUserRequest.getStateAction()).ifPresent(stateAction -> {
            if (stateAction.equals(StateAction.CANCEL_REVIEW)) {
                event.setState(State.CANCELED);
            } else if (stateAction.equals(StateAction.SEND_TO_REVIEW)) {
                event.setState(State.PENDING);
            }
        });
        if (eventUserRequest.getCategory() != null) {
            Category category = categoryRepository.findById(eventUserRequest.getCategory()).orElseThrow(
                    () -> new EntityNotFoundException("Категория не найдена"));
            event.setCategory(category);
        }
        return EventMapper.toEventFullDto(eventRepository.save(event));
    }

    @Override
    public List<ParticipationRequestDto> getRequestsOfUserEvent(Long userId, Long eventId) {
        log.info("Получение запросов пользователя {} для события {}", userId, eventId);
        if (!userRepository.existsById(userId)) {
            log.error("Пользователь с id {} не найден", userId);
            throw new EntityNotFoundException("Пользователь не найден");
        }
        Event event = eventRepository.findById(eventId).orElseThrow(
                () -> new EntityNotFoundException("Событие не найдено"));
        if (!event.getInitiator().getId().equals(userId)) {
            log.error("Пользователь с id {} не является инициатором события с id {}", userId, eventId);
            throw new ValidationException("Пользователь не является инициатором события");
        }

        List<Request> requests = requestRepository.findAllByEventInitiatorIdAndEventId(userId, eventId);
        return RequestMapper.toParticipationRequestDto(requests);
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateRequestsStatusOfUserEvent(Long userId, Long eventId,
                                                                          EventRequestStatusUpdateRequest updateRequest) {
        log.info("Обновление статуса запросов пользователя {} для события {}", userId, eventId);
        if (!userRepository.existsById(userId)) {
            log.error("Пользователь с id {} не найден", userId);
            throw new EntityNotFoundException("Пользователь не найден");
        }
        Event event = eventRepository.findById(eventId).orElseThrow(
                () -> new EntityNotFoundException("Событие не найдено"));
        if (!event.getInitiator().getId().equals(userId)) {
            log.error("Пользователь с id {} не является инициатором события с id {}", userId, eventId);
            throw new ValidationException("Пользователь не является инициатором события");
        }
        if (event.getParticipantLimit() == 0 || !event.getRequestModeration()) {
            log.error("Подтверждение заявок отключено для события с ID:{}", eventId);
            throw new ValidationException("Подтверждение заявок отключено для события с ID:" + eventId);
        }
        List<Long> requestIds = updateRequest.getRequestIds();
        List<Request> requests = requestRepository.findAllById(requestIds);

        if (!requests.stream()
                .anyMatch(r -> r.getEvent().getId().equals(eventId))) {
            log.error("Один или несколько запросов с ID: {} не принадлежат событию с ID:{}", requestIds, eventId);
            throw new ValidationException("Все заявки должны относиться к одному событию");
        }

        List<Request> confirmedRequests = new ArrayList<>();
        List<Request> rejectedRequests = new ArrayList<>();

        switch (updateRequest.getStatus()) {
            case CONFIRMED:
                for (Request request : requests) {
                    if (request.getStatus() != Status.PENDING) {
                        log.error("Заявка ID: {} не находится в статусе ожидания", request.getId());
                        throw new ValidationException("Можно подтверждать только заявки в статусе ожидания");
                    }
                    if (event.getConfirmedRequests() >= event.getParticipantLimit()) {
                        request.setStatus(Status.REJECTED);
                        rejectedRequests.add(request);
                    } else {
                        request.setStatus(Status.CONFIRMED);
                        confirmedRequests.add(request);
                        event.setConfirmedRequests(event.getConfirmedRequests() + 1);
                    }
                }
                break;
            case REJECTED:
                for (Request request : requests) {
                    if (request.getStatus() != Status.PENDING) {
                        log.error("Заявка ID: {} не находится в статусе ожидания", request.getId());
                        throw new ValidationException("Можно отклонять только заявки в статусе ожидания");
                    }
                    request.setStatus(Status.REJECTED);
                    rejectedRequests.add(request);
                }
                break;
            default:
                log.error("Неверный статус запроса");
                throw new ValidationException("Неверный статус запроса");
        }
        requestRepository.saveAll(Stream.concat(confirmedRequests.stream(), rejectedRequests.stream()).toList());
        eventRepository.save(event);

        return new EventRequestStatusUpdateResult(
                confirmedRequests.stream()
                        .map(RequestMapper::toParticipationRequestDto)
                        .toList(),
                rejectedRequests.stream()
                        .map(RequestMapper::toParticipationRequestDto)
                        .toList());
    }

    @Override
    public List<EventFullDto> searchEvents(List<Long> users, List<String> states, List<Long> categories,
                                           LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                           Integer from, Integer size) {
        return null;
    }

    @Override
    @Transactional
    public EventFullDto updateEvent(Long eventId, UpdateEventAdminRequest eventAdminRequest) {
        return null;
    }

    @Override
    public List<EventFullDto> getAllEventPublic(SearchEventParamPublic searchEventParamPublic) {
        return null;
    }

    @Override
    public EventFullDto getEventPrivate(Long userId, Long eventId) {
        return null;
    }

    @Override
    public EventFullDto getEvent(Long id) throws JsonProcessingException {
        return null;
    }
}