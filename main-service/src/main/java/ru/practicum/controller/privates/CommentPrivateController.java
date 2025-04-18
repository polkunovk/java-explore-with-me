package ru.practicum.controller.privates;

import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dtos.comment.CommentDto;
import ru.practicum.dtos.comment.NewCommentDto;
import ru.practicum.dtos.comment.UpdateCommentDto;
import ru.practicum.service.comment.CommentService;

import java.util.List;

@Slf4j
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/comments/users/{userId}")
public class CommentPrivateController {

    private final CommentService commentService;

    @GetMapping("/comments")
    public List<CommentDto> getUserComments(@PathVariable @PositiveOrZero Long userId) {
        log.info("Request to get all comments by user ID: {}", userId);
        return commentService.getUserAuthoredComments(userId);
    }

    @GetMapping("/{commentId}")
    public CommentDto getComment(@PathVariable @PositiveOrZero Long userId,
                                 @PathVariable @PositiveOrZero Long commentId) {
        log.info("Request to get comment ID: {} by user ID: {}", commentId, userId);
        return commentService.getCommentById(userId, commentId);
    }

    @PostMapping("/events/{eventId}")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentDto createComment(@PathVariable @PositiveOrZero Long userId,
                                    @PathVariable @PositiveOrZero Long eventId,
                                    @RequestBody @Valid NewCommentDto newCommentDto) {
        log.info("Request to create comment for event ID: {} by user ID: {}", eventId, userId);
        return commentService.addNewComment(userId, eventId, newCommentDto);
    }

    @PatchMapping("/{commentId}")
    public CommentDto updateComment(@PathVariable @PositiveOrZero Long userId,
                                    @PathVariable @PositiveOrZero Long commentId,
                                    @RequestBody @Valid UpdateCommentDto updateCommentDto) {
        log.info("Request to update comment ID: {} by user ID: {}", commentId, userId);
        return commentService.updateCommentByUser(userId, commentId, updateCommentDto);
    }

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteComment(@PathVariable @PositiveOrZero Long userId,
                              @PathVariable @PositiveOrZero Long commentId) {
        log.info("Request to delete comment ID: {} by user ID: {}", commentId, userId);
        commentService.deleteCommentById(userId, commentId);
    }

    @PostMapping("/events/{eventId}/comments/{parentCommentId}/replies")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentDto createReply(@PathVariable @PositiveOrZero Long userId,
                                  @PathVariable @PositiveOrZero Long eventId,
                                  @PathVariable @PositiveOrZero Long parentCommentId,
                                  @RequestBody @Valid NewCommentDto newCommentDto) {
        log.info("Request to create reply to comment ID: {} for event ID: {} by user ID: {}",
                parentCommentId, eventId, userId);
        return commentService.addNewReply(userId, eventId, parentCommentId, newCommentDto);
    }
}