package com.nklmthr.finance.personal.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.dto.CategorySpendDTO;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.Category;
import com.nklmthr.finance.personal.repository.AccountTransactionRepository;
import com.nklmthr.finance.personal.repository.CategoryRepository;

@Service
public class CategorySpendService {

	private static final Logger logger = LoggerFactory.getLogger(CategorySpendService.class);
	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private AccountTransactionRepository accountTransactionRepository;

	public List<CategorySpendDTO> getMonthlyCategorySpendHierarchy(int month, int year) {
		List<AccountTransaction> transactions = accountTransactionRepository.findByMonthAndYear(month, year);
		Map<String, BigDecimal> categorySums = new HashMap<>();

		// Aggregate transaction amounts by category
		for (AccountTransaction tx : transactions) {
			if (tx.getCategory() == null)
				continue;

			String categoryId = tx.getCategory().getId();
			BigDecimal current = categorySums.getOrDefault(categoryId, BigDecimal.ZERO);
			categorySums.put(categoryId, current.add(tx.getAmount()));
		}

		List<Category> allCategories = categoryRepository.findAll();
		allCategories.sort(Comparator.comparingInt(this::countDescendants));
		// Create DTOs with base amounts
		Map<String, CategorySpendDTO> dtoMap = new HashMap<>();
		for (Category category : allCategories) {
			CategorySpendDTO dto = new CategorySpendDTO();
			dto.setCategoryId(category.getId());
			dto.setCategoryName(category.getName());
			dto.setAmount(categorySums.getOrDefault(category.getId(), BigDecimal.ZERO));
			dto.setChildren(new ArrayList<>());
			dtoMap.put(category.getId(), dto);
		}

		// Build parent-child tree structure
		Map<String, CategorySpendDTO> rootMap = new LinkedHashMap<>();
		for (Category category : allCategories) {
			CategorySpendDTO current = dtoMap.get(category.getId());
			if (category.getParent() != null) {
				CategorySpendDTO parent = dtoMap.get(category.getParent().getId());
				parent.getChildren().add(current);
			} else {
				rootMap.put(category.getId(), current);
			}
		}

		// Perform bottom-up roll-up
		for (CategorySpendDTO root : rootMap.values()) {
			rollupAmount(root);
		}

		List<CategorySpendDTO> sortedRoots = new ArrayList<>(rootMap.values());

		// Return as a list (not a map!)
		return sortedRoots;
		
	}

	private BigDecimal rollupAmount(CategorySpendDTO dto) {
		BigDecimal total = dto.getAmount() != null ? dto.getAmount() : BigDecimal.ZERO;

		for (CategorySpendDTO child : dto.getChildren()) {
			total = total.add(rollupAmount(child));
		}

		dto.setAmount(total);
		return total;
	}

	private int countDescendants(Category node) {
		int count = node.getChildren().size();
		for (Category child : node.getChildren()) {
			count += countDescendants(child);
		}
		return count;
	}

}
