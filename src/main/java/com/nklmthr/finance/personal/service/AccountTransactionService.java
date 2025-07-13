package com.nklmthr.finance.personal.service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.enums.TransactionType;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.repository.AccountTransactionRepository;
import com.nklmthr.finance.personal.repository.AccountTransactionSpecifications;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountTransactionService {
	@Autowired
	private AppUserService appUserService;

	private final AccountTransactionRepository accountTransactionRepository;
	private final CategoryService categoryService;
	private static final Logger logger = LoggerFactory.getLogger(AccountTransactionService.class);

	public Page<AccountTransaction> getFilteredTransactions(Pageable pageable, String month, String accountId,
			String type, String search, String categoryId) {
		AppUser appUser = appUserService.getCurrentUser();
		Specification<AccountTransaction> spec = Specification
				.where(AccountTransactionSpecifications.isRootTransaction());

		if (StringUtils.isNotBlank(accountId)) {
			spec = spec.and(AccountTransactionSpecifications.hasAccount(accountId));
		}
		if (StringUtils.isNotBlank(type) && !"ALL".equalsIgnoreCase(type)) {
			spec = spec.and(AccountTransactionSpecifications.hasTransactionType(TransactionType.valueOf(type)));
		}
		if (StringUtils.isNotBlank(month)) {
			YearMonth ym = YearMonth.parse(month); // expected format: "2025-07"
			logger.info("Filtering transactions for month: {}", ym);
			LocalDateTime start = ym.atDay(1).atStartOfDay(); // 1st of the month, 00:00
			LocalDateTime end = ym.atEndOfMonth().atTime(LocalTime.MAX); // end of month, 23:59:59.999999999
			logger.info("Filtering transactions between start: {} and end: {}", start, end);
			spec = spec.and(AccountTransactionSpecifications.dateBetween(start, end));
		}
		if (StringUtils.isNotBlank(search)) {
			spec = spec.and(AccountTransactionSpecifications.matchesSearch(search));
		}

		if (StringUtils.isNotBlank(categoryId)) {
			Set<String> categoryIds = categoryService.getAllDescendantCategoryIds(categoryId);
			spec = spec.and(AccountTransactionSpecifications.hasCategory(categoryIds));
		}

		spec = spec.and(AccountTransactionSpecifications.belongsToUser(appUser));
		logger.info(
				"Fetching transactions with filters - Month: {}, Account ID: {}, Type: {}, Search: {}, Category ID: {}",
				month, accountId, type, search, categoryId);

		return accountTransactionRepository.findAll(spec, pageable);
	}

	public Optional<AccountTransaction> getById(String id) {
		AppUser appUser = appUserService.getCurrentUser();
		return accountTransactionRepository.findByAppUserAndId(appUser, id);
	}

	public List<AccountTransaction> getChildren(String parentId) {
		AppUser appUser = appUserService.getCurrentUser();
		return accountTransactionRepository.findByAppUserAndParentId(appUser, parentId);
	}

	public AccountTransaction save(AccountTransaction transaction, AppUser appUser) {
		transaction.setAppUser(appUser);
		return accountTransactionRepository.save(transaction);
	}

	public AccountTransaction save(AccountTransaction transaction) {
		AppUser appUser = appUserService.getCurrentUser();
		return save(transaction, appUser);
	}

	public void delete(String id) {
		AppUser appUser = appUserService.getCurrentUser();
		accountTransactionRepository.deleteByAppUserAndId(appUser, id);
	}

	public boolean isTransactionAlreadyPresent(AccountTransaction newTransaction, AppUser appUser) {

		List<AccountTransaction> existingAccTxnList = accountTransactionRepository
				.findByAppUserAndSourceThreadId(appUser, newTransaction.getSourceThreadId());

		if (existingAccTxnList.isEmpty()) {
			return false;
		}
		// Check if the new transaction matches any existing transaction
		for (AccountTransaction existingTxn : existingAccTxnList) {
			if (existingTxn.getDate().equals(newTransaction.getDate())
					&& existingTxn.getAmount().compareTo(newTransaction.getAmount()) == 0
					&& existingTxn.getDescription().equalsIgnoreCase(newTransaction.getDescription())
					&& existingTxn.getType().equals(newTransaction.getType())) {
				return true; // Transaction already exists
			}

		}
		// Check if the new transaction matches any existing transaction by
		// sourceMessageId
		for (AccountTransaction existingTxn : existingAccTxnList) {
			if (existingTxn.getSourceId().equals(newTransaction.getSourceId())) {
				return true; // Transaction already exists by sourceMessageId
			}
		}
		return false; // No match found, transaction is new
	}

	public boolean isTransactionAlreadyPresent(AccountTransaction newTransaction) {
		AppUser appUser = appUserService.getCurrentUser();
		return isTransactionAlreadyPresent(newTransaction, appUser);
	}

	public List<AccountTransaction> getFilteredTransactionsForExport(String month, String accountId, String type,
			String categoryId, String search) {
		AppUser appUser = appUserService.getCurrentUser();
		Specification<AccountTransaction> spec = Specification
				.where(AccountTransactionSpecifications.isRootTransaction());

		if (StringUtils.isNotBlank(accountId)) {
			spec = spec.and(AccountTransactionSpecifications.hasAccount(accountId));
		}
		if (StringUtils.isNotBlank(type) && !"ALL".equalsIgnoreCase(type)) {
			spec = spec.and(AccountTransactionSpecifications.hasTransactionType(TransactionType.valueOf(type)));
		}
		if (StringUtils.isNotBlank(month)) {
			YearMonth ym = YearMonth.parse(month); // expected format: "2025-07"
			logger.info("Filtering transactions for month: {}", ym);
			LocalDateTime start = ym.atDay(1).atStartOfDay(); // 1st of the month, 00:00
			LocalDateTime end = ym.atEndOfMonth().atTime(LocalTime.MAX); // end of month, 23:59:59.999999999
			logger.info("Filtering transactions between start: {} and end: {}", start, end);
			spec = spec.and(AccountTransactionSpecifications.dateBetween(start, end));
		}
		if (StringUtils.isNotBlank(search)) {
			spec = spec.and(AccountTransactionSpecifications.matchesSearch(search));
		}

		if (StringUtils.isNotBlank(categoryId)) {
			Set<String> categoryIds = categoryService.getAllDescendantCategoryIds(categoryId);
			spec = spec.and(AccountTransactionSpecifications.hasCategory(categoryIds));
		}
		spec = spec.and(AccountTransactionSpecifications.belongsToUser(appUser));
		logger.info(
				"Fetching transactions for export with filters - Month: {}, Account ID: {}, Type: {}, Search: {}, Category ID: {}",
				month, accountId, type, search, categoryId);

		return accountTransactionRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "date"));
	}
}
