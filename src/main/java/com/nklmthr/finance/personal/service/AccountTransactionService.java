package com.nklmthr.finance.personal.service;

import java.math.BigDecimal;
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
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.dto.SplitTransactionRequest;
import com.nklmthr.finance.personal.dto.TransferRequest;
import com.nklmthr.finance.personal.enums.TransactionType;
import com.nklmthr.finance.personal.model.Account;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.repository.AccountTransactionRepository;
import com.nklmthr.finance.personal.repository.AccountTransactionSpecifications;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountTransactionService {
	@Autowired
	private AppUserService appUserService;

	@Autowired
	private AccountService accountService;

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
		Page<AccountTransaction> page = accountTransactionRepository.findAll(spec, pageable);
		page.getContent().forEach(tx -> {
			if (tx.getDescription() != null && tx.getDescription().length() > 40) {
				tx.setDescription(tx.getDescription().substring(0, 40));
			}
			if (tx.getExplanation() != null && tx.getExplanation().length() > 40) {
				tx.setExplanation(tx.getExplanation().substring(0, 40));
			}
		});
		return page;
	}

	@Transactional
	public void createTransfer(TransferRequest request) throws Exception {
		AppUser appUser = appUserService.getCurrentUser();

		AccountTransaction debit = getById(request.getSourceTransactionId())
				.orElseThrow(() -> new Exception("Source transaction not found"));

		Account toAccount = accountService.getAccount(request.getDestinationAccountId());

		// Set category to Transfer
		debit.setCategory(categoryService.getTransferCategory());

		// Create CREDIT transaction
		AccountTransaction credit = new AccountTransaction();
		credit.setAccount(toAccount);
		toAccount.setBalance(toAccount.getBalance().add(debit.getAmount()));
		credit.setAmount(debit.getAmount());
		credit.setDate(debit.getDate());
		credit.setDescription(debit.getDescription());
		credit.setExplanation(request.getExplanation());
		credit.setType(TransactionType.CREDIT);
		credit.setCategory(categoryService.getTransferCategory());

		save(debit, appUser);
		save(credit, appUser);
	}

	@Transactional
	public ResponseEntity<String> splitTransaction(List<SplitTransactionRequest> splitTransactions) {
		if (splitTransactions == null || splitTransactions.isEmpty()) {
			return ResponseEntity.badRequest().body("No split transactions provided");
		}

		AppUser appUser = appUserService.getCurrentUser();

		String parentId = splitTransactions.get(0).getParentId();
		Optional<AccountTransaction> parentOpt = getById(parentId);
		if (parentOpt.isEmpty()) {
			return ResponseEntity.badRequest().body("Parent transaction not found");
		}

		AccountTransaction parent = parentOpt.get();
		parent.setCategory(categoryService.getSplitTrnsactionCategory());

		BigDecimal totalSplitAmount = BigDecimal.ZERO;

		for (SplitTransactionRequest st : splitTransactions) {
			logger.info("Processing split transaction: {}", st);
			AccountTransaction child = new AccountTransaction();
			child.setDescription(st.getDescription());
			child.setAmount(st.getAmount());
			child.setDate(st.getDate());
			child.setType(st.getType());
			child.setAccount(accountService.getAccount(st.getAccount().getId()));
			child.setCategory(
					st.getCategory() != null ? categoryService.getCategoryById(st.getCategory().getId()) : null);
			child.setParent(parent);
			child.setAppUser(appUser);
			totalSplitAmount = totalSplitAmount.add(child.getAmount());
			accountTransactionRepository.save(child);
		}

		if (totalSplitAmount.compareTo(parent.getAmount()) != 0) {
			return ResponseEntity.badRequest().body("Parent transaction amount does not match split amounts");
		}

		accountTransactionRepository.save(parent);
		return ResponseEntity.ok("Split successful");
	}

	@Transactional
	public Optional<AccountTransaction> updateTransaction(String id, AccountTransaction tx) {
		return getById(id).map(existing -> {
			AppUser appUser = appUserService.getCurrentUser();

			tx.setId(id);
			tx.setAppUser(appUser);
			tx.setAccount(accountService.getAccount(tx.getAccount().getId()));
			tx.setCategory(categoryService.getCategoryById(tx.getCategory().getId()));

			// Preserve immutable fields
			tx.setParent(existing.getParent());
			tx.setChildren(existing.getChildren());
			tx.setSourceId(existing.getSourceId());
			tx.setSourceThreadId(existing.getSourceThreadId());
			tx.setHref(existing.getHref());
			tx.setHrefText(existing.getHrefText());
			tx.setSourceTime(existing.getSourceTime());

			// Update balance logic
			if (tx.getType().equals(TransactionType.DEBIT)) {
				tx.getAccount().setBalance(tx.getAccount().getBalance().subtract(tx.getAmount()));
			} else {
				tx.getAccount().setBalance(tx.getAccount().getBalance().add(tx.getAmount()));
			}

			return accountTransactionRepository.save(tx);
		});
	}

	public Optional<AccountTransaction> getById(String id) {
		AppUser appUser = appUserService.getCurrentUser();
		return accountTransactionRepository.findByAppUserAndId(appUser, id);
	}

	public List<AccountTransaction> getChildren(String parentId) {
		AppUser appUser = appUserService.getCurrentUser();
		return accountTransactionRepository.findByAppUserAndParentId(appUser, parentId);
	}

	@Transactional
	public AccountTransaction save(AccountTransaction transaction, AppUser appUser) {
		transaction.setAppUser(appUser);
		return accountTransactionRepository.save(transaction);
	}

	@Transactional
	public List<AccountTransaction> save(List<AccountTransaction> transactions) {
		AppUser appUser = appUserService.getCurrentUser();
		transactions.forEach(tx -> tx.setAppUser(appUser));
		return accountTransactionRepository.saveAll(transactions);
	}

	@Transactional
	public AccountTransaction save(AccountTransaction transaction) {
		AppUser appUser = appUserService.getCurrentUser();
		return save(transaction, appUser);
	}

	@Transactional
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
