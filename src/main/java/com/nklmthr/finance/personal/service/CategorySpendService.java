package com.nklmthr.finance.personal.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.dto.CategorySpendDTO;
import com.nklmthr.finance.personal.dto.MonthlySpend;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.repository.AccountTransactionRepository;
import com.nklmthr.finance.personal.repository.CategoryMonthlyProjection;

@Service
public class CategorySpendService {

	private static final Logger logger = LoggerFactory.getLogger(CategorySpendService.class);

	@Autowired
	private AppUserService appUserService;

	@Autowired
	private CategoryService categoryService;

	@Autowired
	private AccountTransactionRepository accountTransactionRepository;

	public List<CategorySpendDTO> getCategorySpendingLast6Months() {
		AppUser user = appUserService.getCurrentUser();
		logger.info("Fetching category spending for user: {}", user.getUsername());
		LocalDate sixMonthsAgo = LocalDate.now().withDayOfMonth(1).minusMonths(5);
		List<CategoryMonthlyProjection> projections = accountTransactionRepository.getCategoryMonthlySpend(user.getId(),
				sixMonthsAgo,
				List.of(categoryService.getTransferCategory().getId(),
						categoryService.getSplitTrnsactionCategory().getId()));
		logger.info("Found {} category monthly projections for user: {}", projections.size(), user.getUsername());
		Map<String, CategorySpendDTO> dtoMap = new HashMap<>();

		for (CategoryMonthlyProjection p : projections) {
			Double total = p.getTotal() != null ? p.getTotal() : 0.0;

			dtoMap.computeIfAbsent(p.getCategoryId(), id -> {
				CategorySpendDTO dto = new CategorySpendDTO();
				dto.setId(id);
				dto.setName(p.getCategoryName());
				dto.setParentId(p.getParentId());
				return dto;
			}).getMonthlySpends().add(new MonthlySpend(p.getMonth(), total));
		}

		List<CategorySpendDTO> roots = new ArrayList<>();
		for (CategorySpendDTO dto : dtoMap.values()) {
			if (dto.getParentId() != null && dtoMap.containsKey(dto.getParentId())) {
				dtoMap.get(dto.getParentId()).getChildren().add(dto);
			} else {
				roots.add(dto);
			}
		}

		for (CategorySpendDTO root : roots) {
			foldUpSpending(root);
		}
		logger.info("Returning {} root categories with spending data for user: {}", roots.size(), user.getUsername());
		return roots;
	}

	private void foldUpSpending(CategorySpendDTO node) {
		for (CategorySpendDTO child : node.getChildren()) {
			foldUpSpending(child);
			mergeMonthlySpends(node, child);
		}
	}

	private void mergeMonthlySpends(CategorySpendDTO parent, CategorySpendDTO child) {
		Map<String, MonthlySpend> parentMap = parent.getMonthlySpends().stream()
				.collect(Collectors.toMap(MonthlySpend::getMonth, ms -> ms, (a, b) -> a));

		for (MonthlySpend childSpend : child.getMonthlySpends()) {
			parentMap.merge(childSpend.getMonth(), new MonthlySpend(childSpend.getMonth(), childSpend.getAmount()),
					(existing, incoming) -> {
						existing.setAmount(existing.getAmount() + incoming.getAmount());
						return existing;
					});
		}

		parent.setMonthlySpends(new ArrayList<>(parentMap.values()));
	}

}
