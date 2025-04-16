package ru.practicum.service.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dtos.user.NewUserRequestDto;
import ru.practicum.dtos.user.UserDto;
import ru.practicum.error.exception.EntityNotFoundException;
import ru.practicum.error.exception.InvalidStateException;
import ru.practicum.mapper.UserMapper;
import ru.practicum.model.User;
import ru.practicum.repository.UserRepository;

import java.util.List;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;

    @Override
    public List<UserDto> getAllUsers(List<Integer> ids, Integer from, Integer size) {
        log.info("Получение всех пользователей");
        Pageable pageable = PageRequest.of(from / size, size, Sort.by(Sort.Direction.ASC, "id"));
        if (ids == null || ids.isEmpty()) {
            log.warn("Список пользователей пуст");
            return userRepository.findAll(pageable).stream()
                    .map(UserMapper::toUserDto)
                    .toList();
        }
        return userRepository.findAllByIdIn(pageable, ids).stream()
                .map(UserMapper::toUserDto)
                .toList();
    }

    @Override
    @Transactional
    public UserDto createUser(NewUserRequestDto newUserRequestDto) {
        log.info("Создание нового пользователя");
        if (userRepository.existsByEmail(newUserRequestDto.getEmail())) {
            log.error("Пользователь с email {} уже существует", newUserRequestDto.getEmail());
            throw new InvalidStateException("Пользователь с email " + newUserRequestDto.getEmail() + " уже существует");
        }
        User user = userRepository.save(UserMapper.toUser(newUserRequestDto));
        log.info("Пользователь с id {} успешно создан", user.getId());
        return UserMapper.toUserDto(user);
    }

    @Override
    @Transactional
    public void deleteUserById(Long userId) {
        log.info("Удаление пользователя с id {}", userId);
        if (!userRepository.existsById(userId)) {
            log.error("Пользователь с id {} не найден", userId);
            throw new EntityNotFoundException("Пользователь с id " + userId + " не найден");
        }
        userRepository.deleteById(userId);
    }
}
