package com.nklmthr.finance.personal.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.dto.CategoryDTO;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.model.Category;
import com.nklmthr.finance.personal.repository.CategoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryService {

	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CategoryService.class);

	private final AppUserService appUserService;
	private final CategoryRepository categoryRepository;

	// ----------------- Public Web Accessors -------------------

	public List<CategoryDTO> getAllCategories() {
		return getAllCategories(appUserService.getCurrentUser());
	}

	public Category getCategoryById(String id) {
		return getCategoryById(appUserService.getCurrentUser(), id);
	}

	public Category saveCategory(Category category) {
		AppUser user = appUserService.getCurrentUser();
		category.setAppUser(user);
		return categoryRepository.save(category);
	}

	public void deleteCategory(String id) {
		deleteCategory(appUserService.getCurrentUser(), id);
	}

	public List<Category> getChildren(String parentId) {
		return getChildren(appUserService.getCurrentUser(), parentId);
	}

	public Category getNonClassifiedCategory() {
		return getNonClassifiedCategory(appUserService.getCurrentUser());
	}

	public List<Category> getFlatCategories() {
		return getFlatCategories(appUserService.getCurrentUser());
	}

	public Set<String> getAllDescendantCategoryIds(String categoryId) {
		return getAllDescendantCategoryIds(appUserService.getCurrentUser(), categoryId);
	}
	
	public Category getTransferCategory() {
		return categoryRepository.findByAppUserAndName(appUserService.getCurrentUser(), "TRANSFERS")
				.orElseThrow(() -> new RuntimeException("TRANSFERS category not found"));
	}

	// ----------------- Background Job / Explicit AppUser -------------------

	public List<CategoryDTO> getAllCategories(AppUser appUser) {
		List<Category> allCategories = categoryRepository.findByAppUser(appUser, Sort.by("name").ascending());

		Map<String, CategoryDTO> dtoMap = new HashMap<>();
		for (Category category : allCategories) {
			dtoMap.put(category.getId(), new CategoryDTO(category));
		}

		for (CategoryDTO dto : dtoMap.values()) {
			if (dto.getParentId() != null && dtoMap.containsKey(dto.getParentId())) {
				dtoMap.get(dto.getParentId()).getChildren().add(dto);
			}
		}

		logger.info("Found {} categories for user {}", dtoMap.size(), appUser.getUsername());

		return dtoMap.values().stream()
				.filter(dto -> dto.getParentId() == null)
				.collect(Collectors.toList());
	}

	public Category getCategoryById(AppUser appUser, String id) {
		return categoryRepository.findByAppUserAndId(appUser, id).orElse(null);
	}

	public void deleteCategory(AppUser appUser, String id) {
		categoryRepository.deleteByAppUserAndId(appUser, id);
	}

	public List<Category> getChildren(AppUser appUser, String parentId) {
		return categoryRepository.findByAppUserAndParentId(appUser, parentId);
	}

	public Category getNonClassifiedCategory(AppUser appUser) {
		return categoryRepository.findByAppUserAndName(appUser, "Not Classified")
				.orElseThrow(() -> new RuntimeException("Default category not found"));
	}

	public List<Category> getFlatCategories(AppUser appUser) {
		return categoryRepository.findByAppUser(appUser, Sort.by("name").ascending());
	}

	public Set<String> getAllDescendantCategoryIds(AppUser appUser, String categoryId) {
		Set<String> descendantIds = new HashSet<>();
		collectChildCategoryIds(appUser, categoryId, descendantIds);
		return descendantIds;
	}

	private void collectChildCategoryIds(AppUser appUser, String categoryId, Set<String> descendantIds) {
		descendantIds.add(categoryId);
		List<Category> children = categoryRepository.findByAppUserAndParentId(appUser, categoryId);
		for (Category child : children) {
			collectChildCategoryIds(appUser, child.getId(), descendantIds);
		}
	}

	
}
