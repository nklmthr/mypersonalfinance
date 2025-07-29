package com.nklmthr.finance.personal.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
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
import com.nklmthr.finance.personal.model.Attachment;
import com.nklmthr.finance.personal.model.UploadedStatement;
import com.nklmthr.finance.personal.repository.AccountTransactionRepository;
import com.nklmthr.finance.personal.repository.AccountTransactionSpecifications;
import com.nklmthr.finance.personal.repository.AttachmentRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountTransactionService {
	@Autowired
	private AppUserService appUserService;

	@Autowired
	private AccountService accountService;

	@Autowired
	private AccountTransactionRepository accountTransactionRepository;

	@Autowired
	private CategoryService categoryService;

	@Autowired
	private AttachmentRepository attachmentRepository;

	private static final Logger logger = LoggerFactory.getLogger(AccountTransactionService.class);

	public Page<AccountTransaction> getFilteredTransactions(Pageable pageable, String month, String accountId,
			String type, String search, String categoryId) {
		AppUser appUser = appUserService.getCurrentUser();
		Specification<AccountTransaction> spec = Specification.where(null);

		if (StringUtils.isBlank(categoryId)) {
			logger.info("No category filter applied, fetching all root transactions");
			spec = spec.and(AccountTransactionSpecifications.isRootTransaction());
		}
		if (StringUtils.isNotBlank(accountId)) {
			logger.info("Filtering transactions for account ID: {}", accountId);
			spec = spec.and(AccountTransactionSpecifications.hasAccount(accountId));
		}
		if (StringUtils.isNotBlank(type) && !"ALL".equalsIgnoreCase(type)) {
			logger.info("Filtering transactions for type: {}", type);
			spec = spec.and(AccountTransactionSpecifications.hasTransactionType(TransactionType.valueOf(type)));
		}
		if (StringUtils.isNotBlank(month)) {
			YearMonth ym = YearMonth.parse(month); // expected format: "2025-07"
			logger.info("Filtering transactions for month: {}", ym);
			LocalDateTime start = ym.atDay(1).atStartOfDay(); // 1st of the month, 00:00
			LocalDateTime end = ym.atEndOfMonth().atTime(LocalTime.MAX); // end of month, 23:59:59.999999999
			logger.info("Filtering transactions between start: {} and end: {}", start, end);
			logger.info("Applying date filter for transactions {} for month: {}", start, month);
			spec = spec.and(AccountTransactionSpecifications.dateBetween(start, end));
		}
		if (StringUtils.isNotBlank(search)) {
			logger.info("Applying search filter for transactions with search term: {}", search);
			spec = spec.and(AccountTransactionSpecifications.matchesSearch(search));
		}
		if (StringUtils.isNotBlank(categoryId)) {
			logger.info("Filtering transactions for category ID: {}", categoryId);
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
			if (tx.getExplanation() != null && tx.getExplanation().length() > 60) {
				tx.setExplanation(tx.getExplanation().substring(0, 60));
			}
			if (tx.getCategory().equals(categoryService.getSplitTrnsactionCategory())) {
				logger.info("Setting split transaction amount as sum of children for transaction ID: {}", tx.getId());
				BigDecimal totalChildrenAmount = tx.getChildren().stream().map(AccountTransaction::getAmount)
						.reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
				tx.setAmount(tx.getAmount().add(totalChildrenAmount).setScale(2, RoundingMode.HALF_UP));
			}
		});
		return page;
	}

	@Transactional
	public void createTransfer(TransferRequest request) throws Exception {
		AppUser appUser = appUserService.getCurrentUser();

		AccountTransaction debit = getById(request.getSourceTransactionId())
				.orElseThrow(() -> new Exception("Source transaction not found"));

		Account toAccount = accountService.findById(request.getDestinationAccountId());

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
		logger.info("Creating transfer transaction: {}", credit);
		logger.info("Updating source transaction: {}", debit);
		save(debit, appUser);
		save(credit, appUser);
	}

	@Transactional
	public ResponseEntity<String> splitTransaction(List<SplitTransactionRequest> splitTransactions) {
		if (splitTransactions == null || splitTransactions.isEmpty()) {
			logger.warn("No split transactions provided");
			return ResponseEntity.badRequest().body("No split transactions provided");
		}

		AppUser appUser = appUserService.getCurrentUser();

		String parentId = splitTransactions.get(0).getParentId();
		Optional<AccountTransaction> parentOpt = getById(parentId);
		if (parentOpt.isEmpty()) {
			logger.warn("Parent transaction with ID {} not found", parentId);
			return ResponseEntity.badRequest().body("Parent transaction not found");
		}

		AccountTransaction parent = parentOpt.get();

		List<AccountTransaction> children = getChildren(parentId);
		for (AccountTransaction child : children) {
			logger.info("Deleting existing child transaction: {}", child);
			parent.setAmount(parent.getAmount().add(child.getAmount()));
			child.setParent(null);
			parent.getChildren().remove(child);
			accountTransactionRepository.delete(child);
		}
		BigDecimal parentAmount = parent.getAmount();
		accountTransactionRepository.save(parent);
		parent.setCategory(categoryService.getSplitTrnsactionCategory());
		BigDecimal totalSplitAmount = BigDecimal.ZERO;
		for (SplitTransactionRequest st : splitTransactions) {
			logger.info("Processing split transaction: {}", st);
			AccountTransaction child = new AccountTransaction();
			child.setDescription(st.getDescription());
			child.setAmount(st.getAmount());
			child.setDate(st.getDate());
			child.setType(st.getType());
			child.setAccount(accountService.findById(st.getAccount().getId()));
			child.setCategory(
					st.getCategory() != null ? categoryService.getCategoryById(st.getCategory().getId()) : null);
			child.setParent(parent);
			child.setAppUser(appUser);
			parent.setAmount(parent.getAmount().subtract(st.getAmount()));
			totalSplitAmount = totalSplitAmount.add(child.getAmount());
			accountTransactionRepository.save(child);
		}

		if (totalSplitAmount.compareTo(parentAmount) != 0) {
			logger.warn("Parent transaction amount does not match split amounts");
			return ResponseEntity.badRequest().body("Parent transaction amount does not match split amounts");
		}
		logger.info("Total split amount matches parent transaction amount: {}", totalSplitAmount);
		accountTransactionRepository.save(parent);
		return ResponseEntity.ok("Split successful");
	}

	@Transactional
	public Optional<AccountTransaction> updateTransaction(String id, AccountTransaction tx) {
		logger.info("Updating transaction with ID: {}", id);
		return getById(id).map(existing -> {
			AppUser appUser = appUserService.getCurrentUser();

			tx.setId(id);
			tx.setAppUser(appUser);
			tx.setAccount(accountService.findById(tx.getAccount().getId()));
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
				logger.info("Updating transaction as DEBIT, deducting amount {} from account balance {}",
						tx.getAmount(), tx.getAccount().getBalance());
				tx.getAccount().setBalance(tx.getAccount().getBalance().subtract(tx.getAmount()));
			} else {
				logger.info("Updating transaction as CREDIT, adding amount {} to account balance {}", tx.getAmount(),
						tx.getAccount().getBalance());
				tx.getAccount().setBalance(tx.getAccount().getBalance().add(tx.getAmount()));
			}
			logger.info("Update Transaction: {}", tx);
			return accountTransactionRepository.save(tx);
		});
	}

	public Optional<AccountTransaction> getById(String id) {
		AppUser appUser = appUserService.getCurrentUser();
		logger.info("Fetching transaction by ID: {}", id);
		return accountTransactionRepository.findByAppUserAndId(appUser, id);
	}

	public List<AccountTransaction> getChildren(String parentId) {
		AppUser appUser = appUserService.getCurrentUser();
		logger.info("Fetching children transactions for parent ID: {}", parentId);
		return accountTransactionRepository.findByAppUserAndParentId(appUser, parentId);
	}

	@Transactional
	public AccountTransaction save(AccountTransaction transaction, AppUser appUser) {
		Account account = accountService.findByAppUserAndId(transaction.getAccount().getId(), appUser);
		if (account == null) {
			logger.error("Account with ID {} not found, cannot save transaction", transaction.getAccount().getId());
			throw new IllegalArgumentException("Account not found");
		}
		if (transaction.getType() == null) {
			logger.error("Transaction type is null, cannot save transaction");
			throw new IllegalArgumentException("Transaction type cannot be null");
		} else {
			logger.info("Setting account balance for transaction type: {}", transaction.getType());
			if (transaction.getType().equals(TransactionType.DEBIT)) {
				account.setBalance(account.getBalance().subtract(transaction.getAmount()));
			} else {
				account.setBalance(account.getBalance().add(transaction.getAmount()));
			}
		}
		transaction.setAppUser(appUser);
		logger.info("Saving transaction for user: {}", appUser.getUsername());
		return accountTransactionRepository.save(transaction);
	}

	@Transactional
	public List<AccountTransaction> save(List<AccountTransaction> transactions) {
		AppUser appUser = appUserService.getCurrentUser();
		logger.info("Saving {} transactions for user: {}", transactions.size(), appUser.getUsername());
		if (transactions == null || transactions.isEmpty()) {
			logger.warn("No transactions provided for saving");
			return List.of(); // Return empty list if no transactions to save
		} else {
			for (AccountTransaction transaction : transactions) {
				if (transaction.getAccount() == null || transaction.getAccount().getId() == null) {
					logger.error("Transaction with ID {} has no associated account, cannot save", transaction.getId());
					throw new IllegalArgumentException("Transaction must have an associated account");
				}
				save(transaction, appUser);
			}
			logger.info("All transactions saved successfully");
			return transactions;

		}

	}

	@Transactional
	public AccountTransaction save(AccountTransaction transaction) {
		AppUser appUser = appUserService.getCurrentUser();
		logger.info("Saving transaction for user: {}", appUser.getUsername());
		return save(transaction, appUser);
	}

	@Transactional
	public void delete(String id) {
		AppUser appUser = appUserService.getCurrentUser();
		AccountTransaction existingTransaction = accountTransactionRepository.findByAppUserAndId(appUser, id)
				.orElseThrow(
						() -> new IllegalArgumentException("Transaction not found for user: " + appUser.getUsername()));
		if (existingTransaction.getParent() != null) {
			logger.info("Removing transaction ID: {} from parent transaction ID: {}", id,
					existingTransaction.getParent().getId());
			AccountTransaction parent = existingTransaction.getParent();
			parent.setAmount(parent.getAmount().add(existingTransaction.getAmount()));
			parent.setDescription(parent.getDescription() + " | delete child:" + existingTransaction.getDescription());
			logger.info("Updating parent transaction with new amount: {}", parent.getAmount());
			accountTransactionRepository.save(parent);
		}

		logger.info("Deleting transaction with ID: {} for user: {}", id, appUser.getUsername());
		accountTransactionRepository.deleteByAppUserAndId(appUser, id);
	}

	@Transactional
	public boolean isTransactionAlreadyPresent(AccountTransaction newTransaction, AppUser appUser) {
		logger.info(
				"New transaction date: {}, amount: {}, description: {}, type: {}, sourceId: {}, sourceThreadId: {}, sourceTime: {}",
				newTransaction.getDate(), newTransaction.getAmount(), newTransaction.getDescription(),
				newTransaction.getType(), newTransaction.getSourceId(), newTransaction.getSourceThreadId(),
				newTransaction.getSourceTime());

		List<AccountTransaction> existingAccTxnList = accountTransactionRepository
				.findByAppUserAndSourceThreadId(appUser, newTransaction.getSourceThreadId());

		logger.info("Found {} existing transactions for sourceThreadId: {}", existingAccTxnList.size(),
				newTransaction.getSourceThreadId());

		if (existingAccTxnList.isEmpty()) {
			newTransaction.setDataVersionId("V2.0");
			return false;
		}

		for (AccountTransaction existingTxn : existingAccTxnList) {
			if (existingTxn.getSourceId() != null && existingTxn.getSourceId().equals(newTransaction.getSourceId())) {
				logger.info("Found existing transaction with same sourceId: dedupe match");
				return true;
			}
		}

		for (AccountTransaction existingTxn : existingAccTxnList) {
			boolean isDateClose = Math
					.abs(ChronoUnit.SECONDS.between(existingTxn.getDate(), newTransaction.getDate())) <= 30;

			boolean isAmountEqual = existingTxn.getAmount().compareTo(newTransaction.getAmount()) == 0;
			boolean isDescriptionEqual = existingTxn.getDescription().equalsIgnoreCase(newTransaction.getDescription());
			boolean isTypeEqual = existingTxn.getType().equals(newTransaction.getType());

			boolean isMatch = isDateClose && isAmountEqual && isDescriptionEqual && isTypeEqual;

			logger.info("Checking existing transaction: date: {}, amount: {}, description: {}, type: {}, match: {}",
					existingTxn.getDate(), existingTxn.getAmount(), existingTxn.getDescription(), existingTxn.getType(),
					isMatch);

			if (isMatch) {
				logger.info("Applied Dedupe logic: Found existing transaction matching new transaction");
				if (existingTxn.getDataVersionId() == null || !existingTxn.getDataVersionId().equals("V1.1")) {
					logger.info("Updating existing transaction with source info");
					updateTransactionWithSourceInfo(existingTxn, newTransaction);
				} else {
					logger.info("Values already updated for existing transaction, skipping update");
				}
				return true;
			}
		}
		newTransaction.setDataVersionId("V2.0");
		logger.info("No matching transaction found, treating as new transaction");
		return false;
	}

	@Transactional
	private void updateTransactionWithSourceInfo(AccountTransaction existingTxn, AccountTransaction newTransaction) {
		logger.info("Updating existing transaction with new source info");
		existingTxn.setSourceId(newTransaction.getSourceId());
		existingTxn.setSourceThreadId(newTransaction.getSourceThreadId());
		existingTxn.setSourceTime(newTransaction.getSourceTime());
		existingTxn.setDate(newTransaction.getDate());
		existingTxn.setDataVersionId("V1.1");
		accountTransactionRepository.save(existingTxn);
	}

	@Transactional
	public boolean isTransactionAlreadyPresent(AccountTransaction newTransaction) {
		AppUser appUser = appUserService.getCurrentUser();
		logger.info("Checking if transaction is already present for user: {}", appUser.getUsername());
		return isTransactionAlreadyPresent(newTransaction, appUser);
	}

	@Transactional
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

	public List<AccountTransaction> getTransactionsByUploadedStatement(UploadedStatement statement) {
		AppUser appUser = appUserService.getCurrentUser();
		if (statement == null || statement.getId() == null) {
			logger.warn("UploadedStatement is null or has no ID, returning empty transaction list");
			return List.of();
		}
		logger.info("Fetching transactions for UploadedStatement ID: {}", statement.getId());
		return accountTransactionRepository.findByAppUserAndUploadedStatement(appUser, statement);
	}

	public void deleteAll(List<AccountTransaction> transactions) {
		if (transactions == null || transactions.isEmpty()) {
			logger.warn("No transactions provided for deletion");
			return; // Nothing to delete
		}
		AppUser appUser = appUserService.getCurrentUser();
		logger.info("Deleting {} transactions for user: {}", transactions.size(), appUser.getUsername());
		accountTransactionRepository.deleteAllByAppUserAndIdIn(appUser,
				transactions.stream().map(AccountTransaction::getId).toList());
		logger.info("Deleted {} transactions successfully", transactions.size());

	}

	public List<Attachment> getTransactionAttachments(String id) {
		AppUser appUser = appUserService.getCurrentUser();
		if (StringUtils.isBlank(id)) {
			logger.warn("Transaction ID is blank, returning empty attachment list");
			return List.of(); // Return empty list if ID is blank
		}
		logger.info("Fetching attachments for transaction ID: {}", id);
		return attachmentRepository.findByAccountTransaction_IdAndAccountTransaction_AppUser_Id(id, appUser.getId());

	}

	@Transactional
	public Attachment addTransactionAttachment(String id, Attachment attachment) {
		AppUser appUser = appUserService.getCurrentUser();
		if (StringUtils.isBlank(id)) {
			logger.warn("Transaction ID is blank, cannot add attachment");
			return null; // Cannot add attachment without a valid transaction ID
		}
		Optional<AccountTransaction> transactionOpt = getById(id);
		if (transactionOpt.isEmpty()) {
			logger.warn("Transaction with ID {} not found, cannot add attachment", id);
			return null; // Transaction not found
		}
		AccountTransaction transaction = transactionOpt.get();
		attachment.setAccountTransaction(transaction);
		attachment.setAppUser(appUser);
		transaction.getAttachments().add(attachment);
		logger.info("Adding attachment to transaction ID: {}", id);
		accountTransactionRepository.save(transaction);
		return attachmentRepository.save(attachment);

	}

}
