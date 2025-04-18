package ru.practicum.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;
import ru.practicum.dtos.comment.CommentDto;
import ru.practicum.dtos.comment.NewCommentDto;
import ru.practicum.model.Comment;
import ru.practicum.model.Event;
import ru.practicum.model.User;
import ru.practicum.enums.StatusComment;

@Mapper(unmappedTargetPolicy = ReportingPolicy.IGNORE,
        componentModel = MappingConstants.ComponentModel.SPRING,
        imports = StatusComment.class)
public interface CommentMapper {

    @Mapping(source = "author.name", target = "authorName")
    @Mapping(source = "parentComment.id", target = "parentId")
    @Mapping(source = "author.id", target = "authorId")
    @Mapping(source = "event.id", target = "eventId")
    CommentDto toDto(Comment comment);

    @Mapping(target = "author", source = "user")
    @Mapping(target = "event", source = "event")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", expression = "java(StatusComment.CHECKING)")
    @Mapping(target = "isDeleted", constant = "false")
    Comment toEntity(NewCommentDto newCommentDto, User user, Event event);
}