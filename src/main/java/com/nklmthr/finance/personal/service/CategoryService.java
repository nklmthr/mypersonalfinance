package com.nklmthr.finance.personal.service;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.model.Category;
import com.nklmthr.finance.personal.repository.CategoryRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CategoryService {

	@Autowired
    private CategoryRepository categoryRepository;
    
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    public List<Category> getRootCategories() {
        return categoryRepository.findByParentIsNull();
    }

    public Optional<Category> getCategoryById(String id) {
        return categoryRepository.findById(id);
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
		return categoryRepository.findByName("Non Classified")
				.orElseGet(() -> categoryRepository.save(new Category("Non Classified")));
	}

}
