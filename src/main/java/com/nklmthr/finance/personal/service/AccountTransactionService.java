package com.nklmthr.finance.personal.service;

import java.math.BigDecimal;
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
			spec = spec.and(AccountTransactionSpecifications.isRootTransaction());
		}
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
			if (tx.getExplanation() != null && tx.getExplanation().length() > 60) {
				tx.setExplanation(tx.getExplanation().substring(0, 60));
			}
			if (tx.getCategory().equals(categoryService.getSplitTrnsactionCategory())) {
				tx.setAmount(tx.getChildren().stream().map(AccountTransaction::getAmount).reduce(BigDecimal.ZERO,
						BigDecimal::add));
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
		logger.info("Saving {} transactions for user: {}", transactions.size(), appUser.getUsername());
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
		// Check if the new transaction matches any existing transaction

		for (AccountTransaction existingTxn : existingAccTxnList) {
			if (existingTxn.getSourceId() != null && existingTxn.getSourceId().equals(newTransaction.getSourceId())) {
				return true; // Transaction already exists by sourceMessageId
			}
			boolean isDateClose = Math
					.abs(ChronoUnit.MINUTES.between(existingTxn.getDate(), newTransaction.getDate())) <= 1;

			boolean isAmountEqual = existingTxn.getAmount().compareTo(newTransaction.getAmount()) == 0;
			boolean isDescriptionEqual = existingTxn.getDescription().equalsIgnoreCase(newTransaction.getDescription());
			boolean isTypeEqual = existingTxn.getType().equals(newTransaction.getType());
			boolean isMatch = isDateClose && isAmountEqual && isDescriptionEqual && isTypeEqual;

			logger.debug(
					"Checking against existing transaction: date: {}, amount: {}, description: {}, type: {}, match: {}",
					existingTxn.getDate(), existingTxn.getAmount(), existingTxn.getDescription(), existingTxn.getType(),
					isMatch);

			if (isMatch) {
				logger.info("Applied Dedupe logic: Found existing transaction matching new transaction");
				if (existingTxn.getDataVersionId() == null && existingTxn.getDataVersionId().equals("V1.1")) {
					logger.info("Values already updated for existing transaction, updating source info");
				} else {
					logger.info("Updating existing transaction with source info");
					updateTransactionWithSourceInfo(existingTxn, newTransaction);
				}

				return true; // Transaction already exists (within 1-minute window)
			} else {
				newTransaction.setDataVersionId("V2.0");
			}
		}

		return false; // No match found, transaction is new
	}

	@Transactional
	private void updateTransactionWithSourceInfo(AccountTransaction existingTxn, AccountTransaction newTransaction) {
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
			return List.of();
		}
		return accountTransactionRepository.findByAppUserAndUploadedStatement(appUser, statement);
	}

	public void deleteAll(List<AccountTransaction> transactions) {
		if (transactions == null || transactions.isEmpty()) {
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
