package com.nklmthr.finance.personal.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.dto.CategoryDTO;
import com.nklmthr.finance.personal.model.Category;
import com.nklmthr.finance.personal.model.FlatCategory;
import com.nklmthr.finance.personal.repository.CategoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryService {

	@Autowired
	private CategoryRepository categoryRepository;

	public List<CategoryDTO> getAllCategories() {
		List<Category> allCategories = categoryRepository.findAllFlat();

		Map<String, CategoryDTO> dtoMap = new HashMap<>();
		for (Category category : allCategories) {
			dtoMap.put(category.getId(), new CategoryDTO(category));
		}

		// Connect children
		for (CategoryDTO dto : dtoMap.values()) {
			if (dto.getParentId() != null && dtoMap.containsKey(dto.getParentId())) {
				dtoMap.get(dto.getParentId()).getChildren().add(dto);
			}
		}

		// Return all (not just roots)
		return new ArrayList<>(dtoMap.values());
	}

	public Category getCategoryById(String id) {
		return categoryRepository.findById(id).orElse(null);
	}

	public Category saveCategory(Category category) {
		return categoryRepository.save(category);
	}

	public void deleteCategory(String id) {
		categoryRepository.deleteById(id);
	}

	public List<Category> getChildren(String parentId) {
		return categoryRepository.findByParentId(parentId);
	}

	public Category getNonClassifiedCategory() {
		return categoryRepository.findByName("Not Classified").get();
	}

	public List<FlatCategory> getFlatCategories() {
		return categoryRepository.findAllProjectedBy(Sort.by("name").ascending());
	}

	public Set<String> getAllDescendantCategoryIds(String categoryId) {
		Set<String> descendantIds = new HashSet<>();
		collectChildCategoryIds(categoryId, descendantIds);
		return descendantIds;
	}

	private void collectChildCategoryIds(String categoryId, Set<String> descendantIds) {
		descendantIds.add(categoryId);
		List<Category> children = categoryRepository.findByParentId(categoryId);
		for (Category child : children) {
			collectChildCategoryIds(child.getId(), descendantIds);
		}
	}

}
