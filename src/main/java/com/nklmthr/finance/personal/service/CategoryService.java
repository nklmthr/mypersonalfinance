package com.nklmthr.finance.personal.service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.dto.CategoryDTO;
import com.nklmthr.finance.personal.mapper.CategoryMapper;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.model.Category;
import com.nklmthr.finance.personal.repository.CategoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(CategoryService.class);

    @Autowired
    private final AppUserService appUserService;

    @Autowired
    private final CategoryRepository categoryRepository;
    
    @Autowired
    private final CategoryMapper categoryMapper;

    // ----------------- Public API -------------------

    // Overload: without user param (for controllers)
    public List<CategoryDTO> getAllCategories() {
        return getAllCategories(appUserService.getCurrentUser());
    }

    @Cacheable(value = "allCategories", key = "#appUser.id")
    public List<CategoryDTO> getAllCategories(AppUser appUser) {
        logger.info("Fetching all categories for user: {}", appUser.getUsername());
        List<Category> allCategories = categoryRepository.findByAppUser(appUser, Sort.by("name").ascending());
        logger.info("Found {} categories for user: {}", allCategories.size(), appUser.getUsername());
        return categoryMapper.toDTOList(allCategories);
        
    }

    // Overload
    public Category getCategoryById(String id) {
        return getCategoryById(appUserService.getCurrentUser(), id);
    }

    @Cacheable(value = "categoryById", key = "#appUser.id + '_' + #id")
    public Category getCategoryById(AppUser appUser, String id) {
        logger.info("Fetching category by id: {} for user: {}", id, appUser.getUsername());
        return categoryRepository.findByAppUserAndId(appUser, id).orElse(null);
    }

    // Overload
    public Category saveCategory(Category category) {
        return saveCategory(appUserService.getCurrentUser(), category);
    }

    @Caching(evict = {
        @CacheEvict(value = "allCategories", key = "#appUser.id"),
        @CacheEvict(value = "categoryById", key = "#appUser.id + '_' + #category.id"),
        @CacheEvict(value = "categoryChildrenById", key = "#appUser.id", allEntries = true),
        @CacheEvict(value = "categoryDescendentsById", key = "#appUser.id", allEntries = true),
        @CacheEvict(value = "categoryNonClassified", key = "#appUser.id"),
        @CacheEvict(value = "categoryTransfer", key = "#appUser.id"),
        @CacheEvict(value = "categorySplitTransaction", key = "#appUser.id")
    })
    public Category saveCategory(AppUser appUser, Category category) {
        category.setAppUser(appUser);
        logger.info("Saving category: {} for user: {}", category.getName(), appUser.getUsername());
        return categoryRepository.save(category);
    }

    // Overload
    public void deleteCategory(String id) {
        deleteCategory(appUserService.getCurrentUser(), id);
    }

    @Caching(evict = {
        @CacheEvict(value = "allCategories", key = "#appUser.id"),
        @CacheEvict(value = "categoryById", key = "#appUser.id + '_' + #id"),
        @CacheEvict(value = "categoryChildrenById", key = "#appUser.id", allEntries = true),
        @CacheEvict(value = "categoryDescendentsById", key = "#appUser.id", allEntries = true),
        @CacheEvict(value = "categoryNonClassified", key = "#appUser.id"),
        @CacheEvict(value = "categoryTransfer", key = "#appUser.id"),
        @CacheEvict(value = "categorySplitTransaction", key = "#appUser.id")
    })
    public void deleteCategory(AppUser appUser, String id) {
        logger.info("Deleting category with id: {} for user: {}", id, appUser.getUsername());
        categoryRepository.deleteByAppUserAndId(appUser, id);
    }

    // Overload
    public List<Category> getChildren(String parentId) {
        return getChildren(appUserService.getCurrentUser(), parentId);
    }

    @Cacheable(value = "categoryChildrenById", key = "#appUser.id + '_' + #parentId")
    public List<Category> getChildren(AppUser appUser, String parentId) {
        logger.info("Fetching children categories for parentId: {} for user: {}", parentId, appUser.getUsername());
        return categoryRepository.findByAppUserAndParent(appUser, parentId);
    }

    // Overload
    public Category getNonClassifiedCategory() {
        return getNonClassifiedCategory(appUserService.getCurrentUser());
    }

    @Cacheable(value = "categoryNonClassified", key = "#appUser.id")
    public Category getNonClassifiedCategory(AppUser appUser) {
        logger.info("Fetching non-classified category for user: {}", appUser.getUsername());
        return categoryRepository.findByAppUserAndName(appUser, "Not Classified")
                .orElseThrow(() -> new RuntimeException("Default category not found"));
    }

    // Overload
    public Set<String> getAllDescendantCategoryIds(String categoryId) {
        return getAllDescendantCategoryIds(appUserService.getCurrentUser(), categoryId);
    }

    @Cacheable(value = "categoryDescendentsById", key = "#appUser.id + '_' + #categoryId")
    public Set<String> getAllDescendantCategoryIds(AppUser appUser, String categoryId) {
        logger.info("Fetching all descendant category IDs for categoryId: {} for user: {}", categoryId, appUser.getUsername());
        Set<String> descendantIds = new HashSet<>();
        collectChildCategoryIds(appUser, categoryId, descendantIds);
        return descendantIds;
    }

	private void collectChildCategoryIds(AppUser appUser, String categoryId, Set<String> descendantIds) {
		descendantIds.add(categoryId);
		List<Category> children = categoryRepository.findByAppUserAndParent(appUser, categoryId);
		for (Category child : children) {
			collectChildCategoryIds(appUser, child.getId(), descendantIds);
		}
	}

    // Overload
    public Category getTransferCategory() {
        return getTransferCategory(appUserService.getCurrentUser());
    }

    @Cacheable(value = "categoryTransfer", key = "#appUser.id")
    public Category getTransferCategory(AppUser appUser) {
        logger.info("Fetching TRANSFERS category for user: {}", appUser.getUsername());
        return categoryRepository.findByAppUserAndName(appUser, "TRANSFERS")
                .orElseThrow(() -> new RuntimeException("TRANSFERS category not found"));
    }

    // Overload
    public Category getSplitTrnsactionCategory() {
        return getSplitTrnsactionCategory(appUserService.getCurrentUser());
    }

    @Cacheable(value = "categorySplitTransaction", key = "#appUser.id")
    public Category getSplitTrnsactionCategory(AppUser appUser) {
        logger.debug("Fetching SPLIT category for user: {}", appUser.getUsername());
        return categoryRepository.findByAppUserAndName(appUser, "SPLIT")
                .orElseThrow(() -> new RuntimeException("Default category not found"));
    }
}
