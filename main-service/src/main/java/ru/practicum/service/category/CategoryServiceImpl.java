package ru.practicum.service.category;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.practicum.dtos.category.CategoryDto;
import ru.practicum.dtos.category.NewCategoryDto;
import ru.practicum.error.exception.DuplicateCategoryException;
import ru.practicum.error.exception.EntityNotFoundException;
import ru.practicum.mapper.CategoryMapper;
import ru.practicum.model.Category;
import ru.practicum.repository.CategoryRepository;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;

    @Override
    @Transactional
    public CategoryDto addNewCategory(NewCategoryDto newCategoryDto) {
        log.info("Добавление новой категории");
        try {
            Category category = categoryRepository.save(CategoryMapper.toNewCategoryDto(newCategoryDto));
            return CategoryMapper.toCategoryDto(category);
        } catch (DataIntegrityViolationException e) {
            throw new DuplicateCategoryException("Категория с таким именем уже существует");
        }
    }

    @Override
    @Transactional
    public void deleteCategoryById(Long categoryId) {
        log.info("Удаление категории по id: {}", categoryId);
        if (!categoryRepository.existsById(categoryId)) {
            log.error("Категория с id: {} не найдена", categoryId);
            throw new EntityNotFoundException("Категория не найдена");
        }
        categoryRepository.deleteById(categoryId);
        log.info("Категория с ID: '{}' успешно удалена", categoryId);
    }

    @Override
    @Transactional
    public CategoryDto updateCategory(Long categoryId, CategoryDto categoryDto) {
        log.info("Обновление категории по id: {}", categoryId);
        Category category = categoryRepository.findById(categoryId).orElseThrow(
                () -> {
                    log.error("Ошибка обновления категории: Категория с ID '{}' не найдена", categoryId);
                    return new EntityNotFoundException("Категория не найдена");
                }
        );
        if (categoryRepository.findByName(categoryDto.getName()).isPresent() &&
                !categoryDto.getName().equals(category.getName())) {
            log.error("Ошибка обновления: Категория с именем '{}' уже существует", categoryDto.getName());
            throw new DuplicateCategoryException("Категория уже существует");
        }
        Optional.ofNullable(categoryDto.getName()).ifPresent(category::setName);
        category.setId(categoryId);
        log.info("Категория с ID '{}' успешно обновлена", categoryId);
        return CategoryMapper.toCategoryDto(categoryRepository.save(category));
    }

    @Override
    public List<CategoryDto> getAllCategories(Integer from, Integer size) {
        log.info("Получение всех категорий");
        PageRequest pageRequest = PageRequest.of(from / size, size, Sort.by(Sort.Direction.DESC, "id"));
        return categoryRepository.findAll(pageRequest).stream()
                .map(CategoryMapper::toCategoryDto)
                .toList();
    }

    @Override
    public CategoryDto getInfoAboutCategoryById(Long catId) {
        log.info("Получение информации о категории по ID:{}", catId);
        if (categoryRepository.findById(catId).isPresent()) {
            return CategoryMapper.toCategoryDto(categoryRepository.findById(catId).get());
        } else {
            log.error("Категория с ID:{} не найдена", catId);
            throw new EntityNotFoundException("Категория с ID:" + catId + " не найдена");
        }
    }
}