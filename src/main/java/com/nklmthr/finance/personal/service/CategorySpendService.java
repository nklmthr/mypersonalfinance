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

import com.nklmthr.finance.personal.dto.CategoryDTO;
import com.nklmthr.finance.personal.dto.CategorySpendDTO;
import com.nklmthr.finance.personal.enums.TransactionType;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.repository.AccountTransactionRepository;

@Service
public class CategorySpendService {

	private static final Logger logger = LoggerFactory.getLogger(CategorySpendService.class);

	@Autowired
	private AppUserService appUserService;

	@Autowired
	private CategoryService categoryService;

	@Autowired
	private AccountTransactionRepository accountTransactionRepository;

	public List<CategorySpendDTO> getMonthlyCategorySpendHierarchy(int month, int year) {
		AppUser appUser = appUserService.getCurrentUser();
		List<AccountTransaction> transactions = accountTransactionRepository.findByAppUserAndMonthAndYear(appUser,
				month, year);
		logger.info("Fetched {} transactions for {}/{}", transactions.size(), month, year);

		Map<String, BigDecimal> categorySums = new HashMap<>();
		int uncategorized = 0;

		for (AccountTransaction tx : transactions) {
			String categoryId = tx.getCategory().getId();
			BigDecimal current = categorySums.getOrDefault(categoryId, BigDecimal.ZERO);
			if (tx.getType().equals(TransactionType.CREDIT)) {
				categorySums.put(categoryId, current.add(tx.getAmount()));
			} else {
				categorySums.put(categoryId, current.subtract(tx.getAmount()));
			}
			logger.info("Processing Category {} with Transaction {} of type {} with Amount {} making categorySum {}",
					tx.getCategory().getName(), tx.getDescription(), tx.getType(), tx.getAmount(),
					categorySums.get(tx.getCategory().getId()));
		}
		logger.info("Transactions with no category: {}", uncategorized);
		logger.info("Category sums built for {} categories", categorySums.size());

		List<CategoryDTO> rootCategories = categoryService.getAllCategories();
		List<CategoryDTO> allCategories = flattenCategoryTree(rootCategories);
		logger.info("Fetched {} categories", allCategories.size());

		allCategories.sort(Comparator.comparingInt(this::countDescendants).reversed());
		Map<String, CategorySpendDTO> dtoMap = new HashMap<>();
		for (CategoryDTO category : allCategories) {
			CategorySpendDTO dto = new CategorySpendDTO();
			dto.setCategoryId(category.getId());
			dto.setCategoryName(category.getName());
			dto.setAmount(categorySums.getOrDefault(category.getId(), BigDecimal.ZERO));
			dto.setChildren(new ArrayList<>());
			dtoMap.put(category.getId(), dto);
		}

		Map<String, CategorySpendDTO> rootMap = new LinkedHashMap<>();
		for (CategoryDTO category : allCategories) {
			CategorySpendDTO current = dtoMap.get(category.getId());
			if (category.getParentId() != null && dtoMap.containsKey(category.getParentId())) {
				CategorySpendDTO parent = dtoMap.get(category.getParentId());
				parent.getChildren().add(current);
			} else {
				rootMap.put(category.getId(), current);
			}
		}

		for (CategorySpendDTO root : rootMap.values()) {
			rollupAmount(root);
		}

		logger.info("Returning {} root categories", rootMap.size());
		return new ArrayList<>(rootMap.values());
	}

	private BigDecimal rollupAmount(CategorySpendDTO dto) {
		BigDecimal total = dto.getAmount() != null ? dto.getAmount() : BigDecimal.ZERO;

		for (CategorySpendDTO child : dto.getChildren()) {
			total = total.add(rollupAmount(child));
		}

		dto.setAmount(total);
		return total;
	}

	private int countDescendants(CategoryDTO node) {
		int count = node.getChildren().size();
		for (CategoryDTO child : node.getChildren()) {
			count += countDescendants(child);
		}
		return count;
	}

	private List<CategoryDTO> flattenCategoryTree(List<CategoryDTO> roots) {
		List<CategoryDTO> flatList = new ArrayList<>();
		for (CategoryDTO root : roots) {
			collectCategories(root, flatList);
		}
		return flatList;
	}

	private void collectCategories(CategoryDTO node, List<CategoryDTO> flatList) {
		flatList.add(node);
		for (CategoryDTO child : node.getChildren()) {
			collectCategories(child, flatList);
		}
	}

}
