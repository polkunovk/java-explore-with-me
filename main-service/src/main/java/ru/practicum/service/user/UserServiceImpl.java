package ru.practicum.service.user;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dtos.user.NewUserRequestDto;
import ru.practicum.dtos.user.UserDto;
import ru.practicum.error.exception.EntityNotFoundException;
import ru.practicum.mapper.UserMapper;
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
        PageRequest pageRequest = PageRequest.of(from / size, size);
        if (ids.isEmpty()) {
            log.info("Получение всех пользователей без указания ID");
            return userRepository.findAll(pageRequest).getContent().stream()
                    .map(UserMapper::toUserDto)
                    .toList();
        } else {
            log.info("Получение всех пользователей с указанием ID");
            return userRepository.findAllByIdIn(pageRequest, ids).getContent().stream()
                    .map(UserMapper::toUserDto)
                    .toList();
        }
    }

    @Override
    @Transactional
    public UserDto createUser(NewUserRequestDto newUserRequestDto) {
        log.info("Создание нового пользователя");
        return UserMapper.toUserDto(userRepository.save(UserMapper.toUser(newUserRequestDto)));
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