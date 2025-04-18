package ru.practicum.service.comment;

import com.querydsl.core.BooleanBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dtos.comment.CommentDto;
import ru.practicum.dtos.comment.NewCommentDto;
import ru.practicum.dtos.comment.StatusUpdateDto;
import ru.practicum.dtos.comment.UpdateCommentDto;
import ru.practicum.enums.State;
import ru.practicum.enums.StatusComment;
import ru.practicum.error.exception.EntityNotFoundException;
import ru.practicum.error.exception.SelfParticipationException;
import ru.practicum.error.exception.ValidationException;
import ru.practicum.mapper.CommentMapper;
import ru.practicum.model.Comment;
import ru.practicum.model.Event;
import ru.practicum.model.QComment;
import ru.practicum.model.User;
import ru.practicum.repository.CommentRepository;
import ru.practicum.repository.EventRepository;
import ru.practicum.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {
    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final CommentRepository commentRepository;
    private final CommentMapper commentMapper;

    @Override
    @Transactional(readOnly = true)
    public List<CommentDto> getAllCommentsByFilter(String text, StatusComment statusComment,
                                                   Integer from, Integer size) {
        log.info("Getting comments by filter");
        QComment comment = QComment.comment;
        BooleanBuilder builder = new BooleanBuilder();

        if (text != null && !text.isBlank()) {
            builder.and(comment.text.containsIgnoreCase(text));
        }

        if (statusComment != null) {
            builder.and(comment.status.eq(statusComment));
            if (statusComment != StatusComment.DELETED) {
                builder.and(comment.isDeleted.eq(false));
            }
        } else {
            builder.and(comment.isDeleted.eq(false));
        }

        Pageable pageable = PageRequest.of(from / size, size, Sort.by(Sort.Direction.ASC, "created"));
        Page<Comment> commentsPage = commentRepository.findAll(builder, pageable);
        List<Comment> allComments = new ArrayList<>(commentsPage.getContent());

        for (Comment c : commentsPage.getContent()) {
            allComments.addAll(findAllReplyComments(c, statusComment));
        }

        return allComments.stream()
                .map(commentMapper::mapToCommentDto)
                .toList();
    }

    @Transactional(readOnly = true)
    @Override
    public CommentDto getCommentByIdFromAdmin(Long commentId) {
        log.info("Getting comment by ID {} from Admin", commentId);
        return commentMapper.mapToCommentDto(createCommentById(commentId));
    }

    @Transactional
    @Override
    public CommentDto updateCommentStatusByAdmin(Long commentId, StatusUpdateDto status) {
        log.info("Updating status of comment with ID {} by admin", commentId);
        Comment comment = createCommentById(commentId);

        if (comment.getStatus().equals(StatusComment.PUBLISHED)) {
            log.warn("Comment with ID {} is already published", commentId);
            throw new ValidationException("Comment is not in CHECKING state");
        }

        comment.setStatus(status.getStatus());
        commentRepository.save(comment);
        return commentMapper.mapToCommentDto(comment);
    }

    @Transactional
    @Override
    public void deleteCommentByIdFromAdmin(Long commentId) {
        log.info("Soft deleting comment with ID {}", commentId);
        Comment comment = createCommentById(commentId);
        comment.setIsDeleted(true);
        comment.setStatus(StatusComment.DELETED);
        commentRepository.save(comment);
    }

    @Transactional
    @Override
    public void hardDeleteComment(Long commentId) {
        log.info("Hard deleting comment with ID {}", commentId);
        commentRepository.delete(createCommentById(commentId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentDto> getCommentsAboutEvent(Long eventId, Integer from, Integer size) {
        log.info("Getting comments for event ID {}", eventId);
        Event event = createEventById(eventId);

        if (!event.getState().equals(State.PUBLISHED)) {
            throw new ValidationException("Event is not published");
        }

        Pageable pageable = PageRequest.of(from / size, size);
        List<Comment> comments = commentRepository.findAllByEventIdAndStatusAndIsDeletedFalse(
                eventId, StatusComment.PUBLISHED, pageable
        );

        return comments.stream()
                .map(commentMapper::mapToCommentDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommentDto> getUserAuthoredComments(Long userId) {
        log.info("Getting comments by user ID {}", userId);
        checkUserExists(userId);

        return commentRepository.findAllByAuthorIdAndStatusAndIsDeletedFalse(userId, StatusComment.PUBLISHED)
                .stream()
                .map(commentMapper::mapToCommentDto)
                .toList();
    }

    @Override
    public CommentDto getCommentById(Long userId, Long commentId) {
        log.info("Getting comment ID {} for user ID {}", commentId, userId);
        checkUserExists(userId);

        return commentRepository.findByIdAndAuthorIdAndIsDeletedFalse(commentId, userId)
                .map(commentMapper::mapToCommentDto)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found: " + commentId));
    }

    @Override
    @Transactional
    public CommentDto addNewComment(Long userId, Long eventId, NewCommentDto newCommentDto) {
        log.info("Adding new comment by user {} to event {}", userId, eventId);
        User user = createUserById(userId);
        Event event = createEventById(eventId);

        if (!event.getState().equals(State.PUBLISHED)) {
            throw new ValidationException("Event is not published");
        }

        Comment comment = commentMapper.mapToComment(newCommentDto, user, event);
        return commentMapper.mapToCommentDto(commentRepository.save(comment));
    }

    @Override
    @Transactional
    public CommentDto updateCommentByUser(Long userId, Long commentId, UpdateCommentDto updateCommentDto) {
        log.info("Updating comment {} by user {}", commentId, userId);
        User user = createUserById(userId);
        Comment comment = createCommentById(commentId);

        checkCommentByAuthor(user, comment);
        comment.setText(updateCommentDto.getText());
        comment.setUpdated(LocalDateTime.now());

        return commentMapper.mapToCommentDto(commentRepository.save(comment));
    }

    @Override
    @Transactional
    public void deleteCommentById(Long userId, Long commentId) {
        log.info("Deleting comment {} by user {}", commentId, userId);
        Comment comment = createCommentById(commentId);
        checkCommentByAuthor(createUserById(userId), comment);

        comment.setIsDeleted(true);
        comment.setStatus(StatusComment.DELETED);
        commentRepository.save(comment);
    }

    @Override
    @Transactional
    public CommentDto addNewReply(Long userId, Long eventId, Long parentCommentId, NewCommentDto newCommentDto) {
        log.info("Adding reply to comment {} by user {} in event {}", parentCommentId, userId, eventId);
        User user = createUserById(userId);
        Event event = createEventById(eventId);
        Comment parentComment = createCommentById(parentCommentId);

        if (parentComment.getIsDeleted()) {
            throw new ValidationException("Parent comment is deleted");
        }

        if (!parentComment.getEvent().getId().equals(eventId)) {
            throw new ValidationException("Parent comment in different event");
        }

        Comment reply = commentMapper.mapToComment(newCommentDto, user, event);
        reply.setParentComment(parentComment);

        Comment savedReply = commentRepository.save(reply);
        parentComment.getReplies().add(savedReply);
        commentRepository.save(parentComment);

        return commentMapper.mapToCommentDto(savedReply);
    }

    @Override
    public String getUserNameById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId))
                .getName();
    }

    private Event createEventById(Long eventId) {
        return eventRepository.findById(eventId)
                .orElseThrow(() -> new EntityNotFoundException("Event not found: " + eventId));
    }

    private User createUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));
    }

    private Comment createCommentById(Long commentId) {
        return commentRepository.findById(commentId)
                .orElseThrow(() -> new EntityNotFoundException("Comment not found: " + commentId));
    }

    private void checkUserExists(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new EntityNotFoundException("User not found: " + userId);
        }
    }

    private void checkCommentByAuthor(User user, Comment comment) {
        if (!comment.getAuthor().equals(user)) {
            throw new SelfParticipationException("User is not comment author");
        }
    }

    private List<Comment> findAllReplyComments(Comment parentComment, StatusComment status) {
        List<Comment> allReplies = new ArrayList<>();
        Queue<Comment> queue = new LinkedList<>(parentComment.getReplies());

        while (!queue.isEmpty()) {
            Comment reply = queue.poll();
            if (status == null || reply.getStatus() == status) {
                allReplies.add(reply);
                queue.addAll(reply.getReplies());
            }
        }
        return allReplies;
    }
}