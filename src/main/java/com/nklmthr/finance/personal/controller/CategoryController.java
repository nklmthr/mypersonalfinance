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
	public ResponseEntity<CategoryDTO> getById(@PathVariable String id) {
		CategoryDTO category = categoryService.getCategoryDTOById(id);
		return category != null ? ResponseEntity.ok(category) : ResponseEntity.notFound().build();
	}

	@GetMapping("/{id}/children")
	public List<CategoryDTO> getChildren(@PathVariable String id) {
		return categoryService.getChildrenDTO(id);
	}

	@PostMapping
	public CategoryDTO create(@RequestBody CategoryDTO category) {
		return categoryService.saveCategory(category);
	}

	@PutMapping("/{id}")
	public ResponseEntity<CategoryDTO> update(@PathVariable String id, @RequestBody CategoryDTO updated) {
		// Ensure we update the existing entity instead of creating a new one
		if (categoryService.getCategoryById(id) == null) {
			return ResponseEntity.notFound().build();
		}
		updated.setId(id);
		return ResponseEntity.ok(categoryService.saveCategory(updated));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable String id) {
		categoryService.deleteCategory(id);
		return ResponseEntity.noContent().build();
	}
}
