package ru.practicum.service.event;

import com.querydsl.core.BooleanBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.StatClient;
import ru.practicum.dto.ViewStats;
import ru.practicum.dtos.event.*;
import ru.practicum.dtos.request.ParticipationRequestDto;
import ru.practicum.enums.State;
import ru.practicum.enums.StateAction;
import ru.practicum.enums.Status;
import ru.practicum.error.exception.*;
import ru.practicum.mapper.EventMapper;
import ru.practicum.mapper.RequestMapper;
import ru.practicum.model.*;
import ru.practicum.repository.CategoryRepository;
import ru.practicum.repository.EventRepository;
import ru.practicum.repository.RequestRepository;
import ru.practicum.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static ru.practicum.model.QEvent.event;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {
    private final EventRepository eventRepository;
    private final RequestRepository requestRepository;
    private final CategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final StatClient statClient;


    @Override
    public List<EventFullDto> getAllEventsByUserId(Long userId, Integer from, Integer size) {
        log.info("Получение всех событий пользователя {}", userId);
        checkUserExists(userId);
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

        User user = findUserById(userId);
        Category category = findCategoryById(newEventDto.getCategory());
        return EventMapper.toEventFullDto(eventRepository.save(EventMapper.mapToEvent(newEventDto, category, user)));
    }

    @Override
    public EventFullDto getEventOfUser(Long userId, Long eventId) {
        log.info("Получение события пользователя {}", userId);
        checkUserExists(userId);
        Event event = findEventById(eventId);
        if (!event.getInitiator().getId().equals(userId)) {
            log.warn("Пользователь с id {} не является инициатором события с id {}", userId, eventId);
            throw new ValidationException("Пользователь не является инициатором события");
        }
        return EventMapper.toEventFullDto(event);
    }

    @Override
    @Transactional
    public EventFullDto updateEventOfUser(Long userId, Long eventId, UpdateEventUserRequest eventUserRequest) {
        log.info("Обновление события пользователя {}", userId);
        checkUserExists(userId);
        Event event = findEventById(eventId);
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
                    () -> new EntityNotFoundException("Category not found"));
            event.setCategory(category);
        }
        Event savedEvent = eventRepository.save(event);
        log.debug("Сохраненное событие: {}", savedEvent);
        return EventMapper.toEventFullDto(savedEvent);
    }

    @Override
    public List<ParticipationRequestDto> getRequestsOfUserEvent(Long userId, Long eventId) {
        log.info("Получение запросов пользователя {} для события {}", userId, eventId);
        checkUserExists(userId);
        checkEventExists(eventId);
        return requestRepository.findByEventId(eventId)
                .stream().map(RequestMapper::toParticipationRequestDto).toList();
    }

    @Override
    @Transactional
    public EventRequestStatusUpdateResult updateRequestsStatusOfUserEvent(
            Long userId, Long eventId, EventRequestStatusUpdateRequest updateRequest) {
        log.info("Обновление статуса запросов пользователя {} для события {}", userId, eventId);
        checkUserExists(userId);
        Event event = findEventById(eventId);
        if (Objects.equals(event.getConfirmedRequests(), event.getParticipantLimit())) {
            log.error("Лимит участников для события с ID: {} достигнут", eventId);
            throw new ParticipantLimitReachedException("Лимит участников для события с ID:"
                    + eventId + " достигнут");
        }
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

        log.debug("Найденные запросы: {}", requests);
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
                    log.debug("Текущие подтвержденные запросы: {}, Лимит участников: {}",
                            event.getConfirmedRequests(), event.getParticipantLimit());
                    if (event.getConfirmedRequests() >= event.getParticipantLimit()) {
                        log.warn("Невозможно подтвердить запрос из-за достижения лимита участников.");
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
    public List<EventFullDto> getAdminEvents(List<Long> userIds, List<State> states, List<Long> categories,
                                             LocalDateTime rangeStart, LocalDateTime rangeEnd,
                                             Integer from, Integer size) {
        log.info("Получение событий администраторов с пользователями {}, состояниями {}, категориями {}, началом диапазона {}, концом диапазона {}",
                userIds, states, categories, rangeStart, rangeEnd);
        Page<Event> eventPage;
        Pageable pageable = createPageable(from, size, Sort.by(Sort.Direction.ASC, "createdOn"));
        BooleanBuilder queryBuilder = new BooleanBuilder();
        applyDateRangeFilter(rangeStart, rangeEnd, queryBuilder);

        if (userIds != null && !userIds.isEmpty()) {
            queryBuilder.and(event.initiator.id.in(userIds));
        }
        if (categories != null && !categories.isEmpty()) {
            queryBuilder.and(event.category.id.in(categories));
        }
        if (states != null && !states.isEmpty()) {
            queryBuilder.and(event.state.in(states));
        }

        if (queryBuilder.getValue() != null) {
            eventPage = eventRepository.findAll(queryBuilder, pageable);
        } else {
            eventPage = eventRepository.findAll(pageable);
        }
        List<Event> result = eventPage.getContent();
        setViews(result);
        return result.stream()
                .map(EventMapper::toEventFullDto)
                .toList();
    }

    @Override
    @Transactional
    public EventFullDto updateEventOfAdmin(Long eventId, UpdateEventAdminRequest adminDto) {
        log.info("Обновление события администратора с id {}", eventId);
        Event event = findEventById(eventId);

        if (event.getState() != State.PENDING) {
            log.warn("Событие с id {} не находится в состоянии ожидания. Невозможно выполнить эту операцию.", event.getId());
            throw new InvalidStateException("Событие уже было опубликовано");
        }

        // Проверка даты события только если событие уже опубликовано
        if (adminDto.getEventDate() != null) {
            if (event.getPublishedOn() != null && adminDto.getEventDate().isBefore(event.getPublishedOn().plusHours(1))) {
                log.warn("The start date of the event to be modified must be no earlier than one hour from" +
                        " the date of publication");
                throw new ValidationException("The start time of the event to be modified must be no earlier than one hour" +
                        " from the date of publication");
            }
        }

        // Обработка действия администратора
        if (adminDto.getStateAction() != null) {
            switch (adminDto.getStateAction()) {
                case PUBLISH_EVENT -> event.setState(State.PUBLISHED);
                case REJECT_EVENT -> event.setState(State.CANCELED);
                default -> throw new InvalidStateException("Invalid state action");
            }
        }

        // Установка даты публикации только при публикации события
        if (adminDto.getStateAction() == StateAction.PUBLISH_EVENT) {
            event.setPublishedOn(LocalDateTime.now());
        }

        Event savedEvent = updateEvent(event, adminDto.getCategoryId(), adminDto.getLocation(), adminDto.getAnnotation(),
                adminDto.getDescription(), adminDto.getEventDate(), adminDto.getPaid(), adminDto.getParticipantLimit(),
                adminDto.getRequestModeration(), adminDto.getTitle());

        setViews(List.of(savedEvent));
        log.info("Событие {} обновлено администратором", savedEvent);
        return EventMapper.toEventFullDto(savedEvent);
    }

    //Получение событий с возможностью фильтрации
    @Override
    @Transactional(readOnly = true)
    public List<EventShortDto> getShortEventPublicByFilter(String text,
                                                           List<Long> categories,
                                                           Boolean paid,
                                                           LocalDateTime rangeStart,
                                                           LocalDateTime rangeEnd,
                                                           boolean onlyAvailable,
                                                           String sort,
                                                           int from,
                                                           int size) {
        log.info("Получение списка событий по фильтру: текст={}, категории={}, платные={}, начало диапазона={}, конец диапазона={}, " +
                        "только доступные={}, сортировка={}, от={}, размер={}",
                text, categories, paid, rangeStart, rangeEnd, onlyAvailable, sort, from, size);
        Page<Event> events;
        Pageable pageable;
        switch (sort) {
            case "EVENT_DATE" -> pageable = createPageable(from, size, Sort.by(Sort.Direction.ASC, "eventDate"));
            case "VIEWS" -> pageable = createPageable(from, size, Sort.by(Sort.Direction.ASC, "id"));
            case null -> pageable = createPageable(from, size, Sort.unsorted());
            default -> {
                log.warn("Неподдерживаемый тип сортировки");
                throw new InvalidStateException("Неподдерживаемый тип сортировки");
            }
        }
        BooleanBuilder queryBuilder = new BooleanBuilder();
        applyDateRangeFilter(rangeStart, rangeEnd, queryBuilder);
        if (text != null && !text.isBlank()) {
            queryBuilder.and(event.title.containsIgnoreCase(text).or(event.annotation.containsIgnoreCase(text)));
        }
        if (categories != null && !categories.isEmpty()) {
            queryBuilder.and(event.category.id.in(categories));
        }
        if (paid != null) {
            queryBuilder.and(event.paid.eq(paid));
        }
        if (onlyAvailable) {
            queryBuilder.and(event.confirmedRequests.gt(0)
                    .and(event.participantLimit.gt(event.confirmedRequests)));
        }
        if (queryBuilder.getValue() != null) {
            events = eventRepository.findAll(queryBuilder, pageable);
        } else {
            events = eventRepository.findAll(pageable);
        }
        List<Event> result = events.getContent();
        setViews(result);
        if (sort != null && sort.equals("VIEWS")) {
            result = result.stream()
                    .sorted(Comparator.comparing(Event::getViews))
                    .toList();
        }
        return result.stream()
                .map(EventMapper::mapToShortDto)
                .toList();
    }

    @Override
    public EventFullDto getPublicEventById(Long eventId) {
        log.info("Получение полного события по id: {}", eventId);
        Event event = findEventById(eventId);
        if (event.getState() != State.PUBLISHED) {
            log.warn("Событие с Id={} не опубликовано", eventId);
            throw new EntityNotFoundException("Событие с Id=" + eventId + " не найдено");
        }
        setViews(List.of(event));
        return EventMapper.toEventFullDto(event);
    }

    private List<ViewStats> getViews(List<Event> events) {
        List<String> uris = events.stream()
                .map(event -> "/events/" + event.getId())
                .toList();

        List<ViewStats> viewStats = statClient.getViewStats(LocalDateTime.now().minusHours(1),
                LocalDateTime.now(), uris, true);
        log.info("Получены данные статистики: {}", viewStats);
        if (viewStats == null) {
            return Collections.emptyList();
        }
        return viewStats;
    }

    private void setViews(List<Event> events) {
        if (events.isEmpty()) {
            return;
        }
        Map<String, Long> mapUriAndHits = getViews(events).stream()
                .collect(Collectors.toMap(ViewStats::getUri, ViewStats::getHits));

        for (Event event : events) {
            event.setViews(mapUriAndHits.getOrDefault("/events/" + event.getId(), 0L));
        }
    }

    private Event updateEvent(Event event, Long categoryId, Location location, String annotation, String description,
                              LocalDateTime eventDate, Boolean paid, Integer participantLimit,
                              Boolean requestModeration, String title) {
        if (categoryId != null) {
            Category category = categoryRepository.findById(categoryId).orElseThrow(
                    () -> new EntityNotFoundException("Категория не найдена"));
            event.setCategory(category);
        }
        if (location != null) {
            event.setLat(location.getLat());
            event.setLon(location.getLon());
        }

        Optional.ofNullable(annotation).ifPresent(event::setAnnotation);
        Optional.ofNullable(description).ifPresent(event::setDescription);
        Optional.ofNullable(eventDate).ifPresent(event::setEventDate);
        Optional.ofNullable(paid).ifPresent(event::setPaid);
        Optional.ofNullable(participantLimit).ifPresent(event::setParticipantLimit);
        Optional.ofNullable(requestModeration).ifPresent(event::setRequestModeration);
        Optional.ofNullable(title).ifPresent(event::setTitle);

        return eventRepository.save(event);
    }

    private Pageable createPageable(int from, int size, Sort sort) {
        log.debug("Create Pageable with offset from {}, size {}", from, size);
        return PageRequest.of(from / size, size, sort);
    }

    private void applyDateRangeFilter(LocalDateTime rangeStart, LocalDateTime rangeEnd, BooleanBuilder queryBuilder) {
        if (rangeStart != null && rangeEnd != null) {
            if (rangeStart.isAfter(rangeEnd)) {
                log.warn("Время начала не должно быть позже времени окончания");
                throw new ValidationException("Время начала не должно быть позже времени окончания");
            }
            queryBuilder.and(event.eventDate.between(rangeStart, rangeEnd));
        } else if (rangeStart == null && rangeEnd != null) {
            queryBuilder.and(event.eventDate.before(rangeEnd));
        } else if (rangeStart != null) {
            queryBuilder.and(event.eventDate.after(rangeStart));
        }
    }

    private void checkUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            log.warn("Пользователь с id {} не найден", userId);
            throw new EntityNotFoundException("Пользователь с ID: " + userId + " не найден");
        }
    }

    private void checkEventExists(Long eventId) {
        if (!eventRepository.existsById(eventId)) {
            log.warn("Событие с id {} не найдено", eventId);
            throw new EntityNotFoundException("Событие с ID: " + eventId + " не найдено");
        }
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException("User not found"));
    }

    private Category findCategoryById(Long categoryId) {
        return categoryRepository.findById(categoryId).orElseThrow(
                () -> new EntityNotFoundException("Категория с id={} не найдена"));
    }

    private Event findEventById(Long eventId) {
        return eventRepository.findById(eventId).orElseThrow(
                () -> new EntityNotFoundException("Событие с id={} не найдено"));
    }

}
