package com.nklmthr.finance.personal.service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
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

	@Autowired
	private AppUserService appUserService;

	@Autowired
	private CategoryRepository categoryRepository;

	// ----------------- Public Web Accessors -------------------

	@Cacheable("allCategories")
	public List<CategoryDTO> getAllCategories() {
		logger.info("Fetching all categories for user: {}", appUserService.getCurrentUser().getUsername());
		return getAllCategories(appUserService.getCurrentUser());
	}

	@Cacheable(value = "categoryById", key = "#id")
	public Category getCategoryById(String id) {
		logger.info("Fetching category by id: {} for user: {}", id, appUserService.getCurrentUser().getUsername());
		return getCategoryById(appUserService.getCurrentUser(), id);
	}

	@Caching(evict = { @CacheEvict(value = "allCategories", allEntries = true),
			@CacheEvict(value = "categoryById", key = "#id"),
			@CacheEvict(value = "categoryChildrenById", allEntries = true),
			@CacheEvict(value = "categoryDescendentsById", allEntries = true),
			@CacheEvict(value = "categoryNonClassified", allEntries = true),
			@CacheEvict(value = "categoryTransfer", allEntries = true),
			@CacheEvict(value = "categorySplitTransaction", allEntries = true) })
	public Category saveCategory(Category category) {

		AppUser user = appUserService.getCurrentUser();
		category.setAppUser(user);
		logger.info("Saving category: {} for user: {}", category.getName(), user.getUsername());
		return categoryRepository.save(category);
	}

	@Caching(evict = { @CacheEvict(value = "allCategories", allEntries = true),
			@CacheEvict(value = "categoryById", key = "#id"),
			@CacheEvict(value = "categoryChildrenById", allEntries = true),
			@CacheEvict(value = "categoryDescendentsById", allEntries = true),
			@CacheEvict(value = "categoryNonClassified", allEntries = true),
			@CacheEvict(value = "categoryTransfer", allEntries = true),
			@CacheEvict(value = "categorySplitTransaction", allEntries = true) })
	public void deleteCategory(String id) {
		logger.info("Deleting category with id: {} for user: {}", id, appUserService.getCurrentUser().getUsername());
		deleteCategory(appUserService.getCurrentUser(), id);
	}

	@Cacheable(value = "categoryChildrenById", key = "#id")
	public List<Category> getChildren(String parentId) {
		logger.info("Fetching children categories for parentId: {} for user: {}", parentId,
				appUserService.getCurrentUser().getUsername());
		return getChildren(appUserService.getCurrentUser(), parentId);
	}

	@Cacheable(value = "categoryNonClassified")
	public Category getNonClassifiedCategory() {
		logger.info("Fetching non-classified category for user: {}", appUserService.getCurrentUser().getUsername());
		return getNonClassifiedCategory(appUserService.getCurrentUser());
	}

	@Cacheable(value = "categoryDescendentsById", key = "#categoryId")
	public Set<String> getAllDescendantCategoryIds(String categoryId) {
		logger.info("Fetching all descendant category IDs for categoryId: {} for user: {}", categoryId,
				appUserService.getCurrentUser().getUsername());
		return getAllDescendantCategoryIds(appUserService.getCurrentUser(), categoryId);
	}

	@Cacheable(value = "categoryTransfer")
	public Category getTransferCategory() {
		logger.info("Fetching TRANSFERS category for user: {}", appUserService.getCurrentUser().getUsername());
		return categoryRepository.findByAppUserAndName(appUserService.getCurrentUser(), "TRANSFERS")
				.orElseThrow(() -> new RuntimeException("TRANSFERS category not found"));
	}

	private List<CategoryDTO> getAllCategories(AppUser appUser) {
		logger.info("Fetching all categories for user: {}", appUser.getUsername());
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

		return dtoMap.values().stream().filter(dto -> dto.getParentId() == null).collect(Collectors.toList());
	}

	private Category getCategoryById(AppUser appUser, String id) {
		logger.debug("Fetching category by id: {} for user: {}", id, appUser.getUsername());
		return categoryRepository.findByAppUserAndId(appUser, id).orElse(null);
	}

	private void deleteCategory(AppUser appUser, String id) {
		logger.info("Deleting category with id: {} for user: {}", id, appUser.getUsername());
		categoryRepository.deleteByAppUserAndId(appUser, id);
	}

	private List<Category> getChildren(AppUser appUser, String parentId) {
		logger.debug("Fetching children categories for parentId: {} for user: {}", parentId, appUser.getUsername());
		return categoryRepository.findByAppUserAndParentId(appUser, parentId);
	}

	public Category getNonClassifiedCategory(AppUser appUser) {
		logger.debug("Fetching non-classified category for user: {}", appUser.getUsername());
		return categoryRepository.findByAppUserAndName(appUser, "Not Classified")
				.orElseThrow(() -> new RuntimeException("Default category not found"));
	}

	private Set<String> getAllDescendantCategoryIds(AppUser appUser, String categoryId) {
		logger.debug("Fetching all descendant category IDs for categoryId: {} for user: {}", categoryId,
				appUser.getUsername());
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

	@Cacheable(value = "categorySplitTransaction")
	public Category getSplitTrnsactionCategory() {
		AppUser appUser = appUserService.getCurrentUser();
		logger.debug("Fetching SPLIT category for user: {}", appUser.getUsername());
		return categoryRepository.findByAppUserAndName(appUser, "SPLIT")
				.orElseThrow(() -> new RuntimeException("Default category not found"));
	}

}
