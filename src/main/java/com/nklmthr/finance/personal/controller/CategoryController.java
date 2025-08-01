package com.nklmthr.finance.personal.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nklmthr.finance.personal.dto.CategoryDTO;
import com.nklmthr.finance.personal.model.Category;
import com.nklmthr.finance.personal.service.CategoryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

	private final CategoryService categoryService;

	@GetMapping
	public List<CategoryDTO> getAll() {
		return categoryService.getAllCategories();
	}

	@GetMapping("/{id}")
	public ResponseEntity<Category> getById(@PathVariable String id) {
		Category category = categoryService.getCategoryById(id);
		if (category != null) {
			return ResponseEntity.ok(category);
		} else {
			return ResponseEntity.notFound().build();
		}
	}

	@GetMapping("/{id}/children")
	public List<Category> getChildren(@PathVariable String id) {
		return categoryService.getChildren(id);
	}

	@PostMapping
	public Category create(@RequestBody Category category) {
		return categoryService.saveCategory(category);
	}

	@PutMapping("/{id}")
	public ResponseEntity<Category> update(@PathVariable String id, @RequestBody Category updated) {
		return categoryService.getCategoryById(id) != null ? ResponseEntity.ok(categoryService.saveCategory(updated))
				: ResponseEntity.notFound().build();
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable String id) {
		categoryService.deleteCategory(id);
		return ResponseEntity.noContent().build();
	}
}
