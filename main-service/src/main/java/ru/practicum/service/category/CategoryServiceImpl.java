package ru.practicum.service.category;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
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
        if (categoryRepository.findByName(newCategoryDto.getName()).isPresent()) {
            log.error("Ошибка добавления категории: Категория с именем '{}' уже существует", newCategoryDto.getName());
            throw new DuplicateCategoryException("Категория уже существует");
        }
        CategoryDto createdCategory = CategoryMapper.toCategoryDto(categoryRepository.save(CategoryMapper.toNewCategoryDto(newCategoryDto)));
        log.info("Категория '{}' успешно добавлена", createdCategory.getName());
        return createdCategory;
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
        if (categoryRepository.findByName(categoryDto.getName()).isPresent()) {
            log.error("Ошибка обновления категории: Категория с именем '{}' уже существует", categoryDto.getName());
            throw new DuplicateCategoryException("Категория уже существует");
        }
        Category category = categoryRepository.findById(categoryId).orElseThrow(
                () -> {
                    log.error("Ошибка обновления категории: Категория с ID '{}' не найдена", categoryId);
                    return new EntityNotFoundException("Категория не найдена");
                }
        );
        Optional.ofNullable(categoryDto.getName()).ifPresent(category::setName);
        log.info("Категория с ID '{}' успешно обновлена", categoryId);
        return CategoryMapper.toCategoryDto(categoryRepository.save(category));
    }

    @Override
    public List<CategoryDto> getAllCategories(Integer from, Integer size) {
        log.info("Получение всех категорий");
        return categoryRepository.findAll(PageRequest.of(from, size)).stream()
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