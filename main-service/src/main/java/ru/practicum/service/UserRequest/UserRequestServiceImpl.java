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
        log.info("Get all user requests with userId={}", userId);
        checkUserExists(userId);

        List<ParticipationRequestDto> result = requestRepository.findAllByRequesterId(userId).stream()
                .map(RequestMapper::toParticipationRequestDto)
                .toList();

        log.info("Found {} requests for user with id={}", result.size(), userId);
        return result;
    }

    @Transactional
    @Override
    public ParticipationRequestDto createUserRequest(Long userId, Long eventId) {
        log.info("Create user request with userId={} and eventId={}", userId, eventId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        String.format("User with id=%d not found", userId)));

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException(
                        String.format("Event with id=%d not found", eventId)));

        if (!event.getState().equals(State.PUBLISHED)) {
            log.info("Event with id={} not published", eventId);
            throw new InvalidStateException("Event not published: " + eventId);
        }

        if (requestRepository.existsByEventIdAndRequesterId(eventId, userId)) {
            log.info("Duplicate request userId={}, eventId={}", userId, eventId);
            throw new InvalidStateException("Duplicate request for user: " + userId + ", event: " + eventId);
        }

        if (event.getInitiator().getId().equals(userId)) {
            log.info("Self-participation attempt userId={}, eventId={}", userId, eventId);
            throw new SelfParticipationException(
                    "Initiator cannot participate in own event: " + eventId);
        }

        Integer confirmedRequests = requestRepository.countByEventIdAndStatus(eventId, Status.CONFIRMED);
        log.info("Current confirmed requests for event {}: {}", eventId, confirmedRequests);

        if (event.getParticipantLimit() > 0 && confirmedRequests >= event.getParticipantLimit()) {
            log.info("Participant limit reached for event {}", eventId);
            throw new ParticipantLimitReachedException("Event participant limit reached: " + eventId);
        }

        Request newRequest = new Request();
        newRequest.setEvent(event);
        newRequest.setRequester(user);
        newRequest.setCreated(LocalDateTime.now());

        if (!event.getRequestModeration() || event.getParticipantLimit() == 0) {
            newRequest.setStatus(Status.CONFIRMED);
            int currentCount = Optional.ofNullable(event.getConfirmedRequests()).orElse(0);
            event.setConfirmedRequests(currentCount + 1);
            eventRepository.save(event);
            log.info("Auto-confirmed request for event {}", eventId);
        } else {
            newRequest.setStatus(Status.PENDING);
            log.info("Request requires moderation for event {}", eventId);
        }

        Request result = requestRepository.save(newRequest);
        log.info("Created new request with id={}", result.getId());
        return RequestMapper.toParticipationRequestDto(result);
    }

    @Transactional
    @Override
    public ParticipationRequestDto cancelUserRequest(Long userId, Long requestId) {
        log.info("Cancel user request with userId={} and requestId={}", userId, requestId);
        checkUserExists(userId);

        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new EntityNotFoundException(
                        String.format("Request with id=%d not found", requestId)));

        if (!request.getRequester().getId().equals(userId)) {
            log.info("Request ownership mismatch: requestId={}, userId={}", requestId, userId);
            throw new ValidationException(
                    "Request does not belong to user: " + requestId + ", " + userId);
        }

        request.setStatus(Status.CANCELED);
        Request updated = requestRepository.save(request);
        log.info("Canceled request with id={}", requestId);
        return RequestMapper.toParticipationRequestDto(updated);
    }

    private void checkUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            log.info("User not found: userId={}", userId);
            throw new EntityNotFoundException("User not found: " + userId);
        }
    }
}