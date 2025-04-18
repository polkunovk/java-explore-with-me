package ru.practicum.controller.admin;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import ru.practicum.dtos.category.CategoryDto;
import ru.practicum.dtos.category.NewCategoryDto;
import ru.practicum.service.category.CategoryService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/categories")
public class CategoryAdminController {

    private final CategoryService categoryService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryDto addNewCategory(@RequestBody @Valid NewCategoryDto newCategoryDto) {
        return categoryService.addNewCategory(newCategoryDto);
    }

    @DeleteMapping("/{catId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCategoryById(@PathVariable Long catId) {
        categoryService.deleteCategoryById(catId);
    }

    @PatchMapping("/{catId}")
    @ResponseStatus(HttpStatus.OK)
    public CategoryDto updateCategory(@PathVariable Long catId, @RequestBody @Valid CategoryDto categoryDto) {
        return categoryService.updateCategory(catId, categoryDto);
    }
}