package com.nklmthr.finance.personal.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.dto.AccountTransactionDTO;
import com.nklmthr.finance.personal.dto.TransferRequest;
import com.nklmthr.finance.personal.enums.TransactionType;
import com.nklmthr.finance.personal.mapper.AccountMapper;
import com.nklmthr.finance.personal.mapper.AccountTransactionMapper;
import com.nklmthr.finance.personal.mapper.CategoryMapper;
import com.nklmthr.finance.personal.model.Account;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.model.UploadedStatement;
import com.nklmthr.finance.personal.repository.AccountRepository;
import com.nklmthr.finance.personal.repository.AccountTransactionRepository;
import com.nklmthr.finance.personal.repository.AccountTransactionSpecifications;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AccountTransactionService {

	@Autowired
	private AppUserService appUserService;

	@Autowired
	private AccountRepository accountRepository;

	@Autowired
	private AccountTransactionRepository accountTransactionRepository;

	@Autowired
	private CategoryService categoryService;

	@Autowired
	private LabelService labelService;

	@Autowired
	AccountTransactionMapper accountTransactionMapper;
	
	@Autowired
	AccountMapper accountMapper;
	
	@Autowired
	CategoryMapper categoryMapper;
	
	@Autowired
	com.nklmthr.finance.personal.mapper.LabelMapper labelMapper;
	
	@PersistenceContext
	private EntityManager entityManager;

	private static final Logger logger = LoggerFactory.getLogger(AccountTransactionService.class);

	// Data version constants
	private static final String DATA_VERSION_V11 = "V1.1";
	private static final String DATA_VERSION_V20 = "V2.0";
	// Fuzzy match time window in seconds
	private static final long MATCH_TIME_WINDOW_SECONDS = 60;

	private void processLabels(AccountTransaction entity, AccountTransactionDTO dto, AppUser appUser) {
		if (dto.labels() != null && !dto.labels().isEmpty()) {
			List<com.nklmthr.finance.personal.model.Label> labels = dto.labels().stream()
				.map(labelDTO -> {
					if (labelDTO.id() != null) {
						// Existing label - find by ID or name
						return labelService.findOrCreateLabel(appUser, labelDTO.name());
					} else {
						// New label - create it
						return labelService.findOrCreateLabel(appUser, labelDTO.name());
					}
				})
				.collect(Collectors.toList());
			entity.setLabels(labels, appUser);
		} else {
			entity.setLabels(new ArrayList<>(), appUser);
		}
	}

	@Transactional
	public void createTransfer(TransferRequest request) throws Exception {
		AppUser appUser = appUserService.getCurrentUser();
		logger.info("Creating transfer from transaction ID: {} to account ID: {}", request.getSourceTransactionId(),
				request.getDestinationAccountId());
		AccountTransaction debit = accountTransactionRepository.findByAppUserAndId(appUser, request.getSourceTransactionId())
				.orElseThrow(() -> new IllegalArgumentException(
						"Source transaction not found for user: " + appUser.getUsername()));
		
		// Check if transaction has children (is a split transaction parent)
		List<AccountTransaction> children = accountTransactionRepository.findByAppUserAndParent(appUser, debit.getId());
		if (!children.isEmpty()) {
			logger.warn("Attempted to transfer split transaction parent ID: {}. Transfers not allowed for split transactions.", debit.getId());
			throw new IllegalArgumentException(
				"Cannot transfer a split transaction. Please delete the split first or transfer the individual child transactions.");
		}
		
		// Check if transaction is already part of a transfer (bidirectional check)
		// Case 1: Transaction has linkedTransferId (points to another transaction)
		if (debit.getLinkedTransferId() != null) {
			logger.warn("Attempted to transfer transaction ID: {} that is already part of a transfer (linkedTransferId: {}).", 
				debit.getId(), debit.getLinkedTransferId());
			throw new IllegalArgumentException(
				"Cannot transfer a transaction that is already part of a transfer. Please delete the existing transfer first.");
		}
		
		// Case 2: Another transaction points to this one
		Optional<AccountTransaction> existingLinkedTx = accountTransactionRepository
			.findByAppUserAndLinkedTransferId(appUser, debit.getId());
		if (existingLinkedTx.isPresent()) {
			logger.warn("Attempted to transfer transaction ID: {} that is already referenced by another transfer transaction (ID: {}).", 
				debit.getId(), existingLinkedTx.get().getId());
			throw new IllegalArgumentException(
				"Cannot transfer a transaction that is already part of a transfer. Please delete the existing transfer first.");
		}
		
		Account toAccount = accountRepository.findByAppUserAndId(appUser, request.getDestinationAccountId()).get();
		toAccount.setBalance(toAccount.getBalance().add(debit.getAmount()));
		
		Account fromAccount = debit.getAccount();
		fromAccount.setBalance(fromAccount.getBalance().subtract(debit.getAmount()));
		
		accountRepository.save(fromAccount);
		accountRepository.save(toAccount);
		
		// Create the credit transaction (destination)
		AccountTransaction credit = new AccountTransaction();
		credit.setAccount(toAccount);
		credit.setCurrency(debit.getCurrency());
		credit.setGptAccount(toAccount);
		credit.setAmount(debit.getAmount());
		credit.setDate(debit.getDate());
		credit.setDescription(debit.getDescription());
		credit.setExplanation(request.getExplanation());
		credit.setType(TransactionType.CREDIT);
		credit.setCategory(categoryService.getTransferCategory());
		credit.setAppUser(appUser);
		credit.setGptAccount(toAccount);
		// Clear GPT fields for transfer credit transaction to avoid showing incorrect enriched data
		credit.setGptDescription(null);
		credit.setGptExplanation(null);
		credit.setGptAmount(null);
		credit.setGptType(null);
		
		// Save credit first to get its ID
		credit = accountTransactionRepository.save(credit);
		
		// Now set bidirectional links
		debit.setLinkedTransferId(credit.getId());
		debit.setCategory(categoryService.getTransferCategory());
		// Default gptAccount to account if null (for old records)
		if (debit.getGptAccount() == null) {
			debit.setGptAccount(debit.getAccount());
		}
		credit.setLinkedTransferId(debit.getId());
		
		// Save both with the links
		accountTransactionRepository.save(debit);
		accountTransactionRepository.save(credit);
		
		logger.info("Transfer created. Debit ID: {}, Credit ID: {}, Amount: {}", 
			debit.getId(), credit.getId(), debit.getAmount());
	}

	@Transactional
	public ResponseEntity<String> splitTransaction(List<AccountTransactionDTO> splitTransactions) {
		if (splitTransactions == null || splitTransactions.isEmpty()) {
			return ResponseEntity.badRequest().body("No split transactions provided");
		}

		AppUser appUser = appUserService.getCurrentUser();

		String parentId = splitTransactions.get(0).parentId();
		Optional<AccountTransaction> parentOpt = accountTransactionRepository.findById(parentId);
		if (parentOpt.isEmpty()) {
			return ResponseEntity.badRequest().body("Parent transaction not found");
		}

		AccountTransaction parent = parentOpt.get();

		// First, delete existing children and add their amounts back to parent
		List<AccountTransaction> existingChildren = accountTransactionRepository.findByAppUserAndParent(appUser, parent.getId());
		for (AccountTransaction child : existingChildren) {
			parent.setAmount(parent.getAmount().add(child.getAmount()));
			child.setParent(null);
			accountTransactionRepository.delete(child);
		}
		
		// Set parent category to "Split Transaction"
		parent.setCategory(categoryService.getSplitTrnsactionCategory());
		
		// Validate that the sum of new children equals the (restored) parent amount
		BigDecimal totalSplitAmount = BigDecimal.ZERO;
		for (AccountTransactionDTO st : splitTransactions) {
			totalSplitAmount = totalSplitAmount.add(st.amount());
		}
		
		if (totalSplitAmount.compareTo(parent.getAmount()) != 0) {
			return ResponseEntity.badRequest().body(
				String.format("Split amounts total (%.2f) does not match parent amount (%.2f)", 
					totalSplitAmount, parent.getAmount()));
		}
		
		// Create new children and deduct their amounts from parent
		for (AccountTransactionDTO st : splitTransactions) {
			AccountTransaction child = new AccountTransaction();
			child.setDescription(st.description());
			child.setAmount(st.amount());
			child.setDate(st.date());
			child.setType(st.type());
			child.setAccount(parent.getAccount());
			child.setCategory(categoryService.getCategoryById(st.category().getId()));
			// Default gptAccount to account if parent's gptAccount is null (for old records)
			child.setGptAccount(parent.getGptAccount() != null ? parent.getGptAccount() : parent.getAccount());
			child.setCurrency(parent.getCurrency());
			child.setParent(parent.getId());
			child.setAppUser(appUser);
			
			// Deduct child amount from parent
			parent.setAmount(parent.getAmount().subtract(st.amount()));
			
			accountTransactionRepository.save(child);
		}

		// Parent amount should now be zero
		if (parent.getAmount().compareTo(BigDecimal.ZERO) != 0) {
			logger.warn("Parent amount after split is not zero: {}", parent.getAmount());
		}

		// Default gptAccount to account if null (for old records)
		if (parent.getGptAccount() == null) {
			parent.setGptAccount(parent.getAccount());
		}
		accountTransactionRepository.save(parent);
		logger.info("Split transaction successful. Parent ID: {}, Children count: {}, Parent final amount: {}", 
			parent.getId(), splitTransactions.size(), parent.getAmount());
		return ResponseEntity.ok("Split successful");
	}

	@Transactional
	public Optional<AccountTransactionDTO> updateTransaction(String id, AccountTransactionDTO txUpdate) {
		return accountTransactionRepository.findById(id).map(existingTx -> {
			AppUser appUser = appUserService.getCurrentUser();

			// Check if transaction has children (is a split transaction parent)
			List<AccountTransaction> children = accountTransactionRepository.findByAppUserAndParent(appUser, id);
			boolean hasChildren = !children.isEmpty();
			
			// Check if transaction is a child (has a parent)
			boolean isChild = existingTx.getParent() != null;
			
			BigDecimal oldAmount = existingTx.getAmount();
			TransactionType oldType = existingTx.getType();
			Account oldAccount = existingTx.getAccount();
			Account newAccount = accountRepository.findByAppUserAndId(appUser, txUpdate.account().id()).get();
			TransactionType newType = txUpdate.type();
			BigDecimal newAmount = txUpdate.amount();
			
			// Prevent amount change for split transaction parents
			if (hasChildren && newAmount.compareTo(oldAmount) != 0) {
				logger.warn("Attempted to change amount of split transaction parent ID: {}. Amount changes not allowed.", id);
				throw new IllegalArgumentException(
					"Cannot change amount of a split transaction. Please delete the split first or edit the child transactions.");
			}
			
			// Prevent amount, account, or type change for child transactions
			if (isChild) {
				if (newAmount.compareTo(oldAmount) != 0) {
					logger.warn("Attempted to change amount of child transaction ID: {}. Amount changes not allowed.", id);
					throw new IllegalArgumentException(
						"Cannot change amount of a child transaction. Please edit the parent split instead.");
				}
				
				if (!newAccount.getId().equals(oldAccount.getId())) {
					logger.warn("Attempted to change account of child transaction ID: {}. Account changes not allowed.", id);
					throw new IllegalArgumentException(
						"Cannot change account of a child transaction. Please edit the parent split instead.");
				}
				
				if (newType != oldType) {
					logger.warn("Attempted to change type of child transaction ID: {}. Type changes not allowed.", id);
					throw new IllegalArgumentException(
						"Cannot change transaction type of a child transaction. Please edit the parent split instead.");
				}
			}
			
			// Handle linked transfer transactions - handle both directions (bidirectional)
			// Case 1: This transaction points to another (has linkedTransferId)
			// Case 2: Another transaction points to this one
			AccountTransaction linkedTx = null;
			
			if (existingTx.getLinkedTransferId() != null) {
				// Case 1: This transaction has a linkedTransferId pointing to another transaction
				Optional<AccountTransaction> linkedTxOpt = accountTransactionRepository
					.findByAppUserAndId(appUser, existingTx.getLinkedTransferId());
				
				if (linkedTxOpt.isPresent()) {
					linkedTx = linkedTxOpt.get();
					logger.info("Found linked transaction (case 1): {}", linkedTx.getId());
				}
			}
			
			// Case 2: Another transaction points to this one (bidirectional check)
			if (linkedTx == null) {
				Optional<AccountTransaction> linkedTxOpt = accountTransactionRepository
					.findByAppUserAndLinkedTransferId(appUser, existingTx.getId());
				
				if (linkedTxOpt.isPresent()) {
					linkedTx = linkedTxOpt.get();
					logger.info("Found linked transaction (case 2): {}", linkedTx.getId());
				}
			}
			
			if (linkedTx != null) {
				logger.info("Updating linked transfer. Transaction ID: {}, Linked ID: {}", id, linkedTx.getId());
				
				// Validate transfer integrity constraints
				if (!txUpdate.category().getId().equals(existingTx.getCategory().getId())) {
					logger.warn("Attempted to change category of transfer transaction ID: {}", id);
					throw new IllegalArgumentException(
						"Cannot change category of a transfer transaction. Please delete the transfer first.");
				}
				
				// Store old values for linked transaction to reverse balances
				Account linkedOldAccount = linkedTx.getAccount();
				BigDecimal linkedOldAmount = linkedTx.getAmount();
				TransactionType linkedOldType = linkedTx.getType();
				
				// Reverse old balances for linked transaction
				if (linkedOldAccount != null) {
					if (linkedOldType == TransactionType.DEBIT) {
						linkedOldAccount.setBalance(linkedOldAccount.getBalance().add(linkedOldAmount));
					} else if (linkedOldType == TransactionType.CREDIT) {
						linkedOldAccount.setBalance(linkedOldAccount.getBalance().subtract(linkedOldAmount));
					}
				}
				
				// Determine if linked transaction's account should change
				// For transfers: if main transaction account changes, linked transaction might need to change
				// But typically: debit (source) and credit (destination) are independent
				// We'll keep linked transaction's account unchanged unless explicitly needed
				Account linkedNewAccount = linkedOldAccount; // Default: keep same account
				
				// Update linked transaction with synchronized values
				linkedTx.setAmount(newAmount); // Sync amount
				linkedTx.setDate(txUpdate.date()); // Sync date
				linkedTx.setDescription(txUpdate.description()); // Sync description
				
				// Keep linked transaction's type (DEBIT stays DEBIT, CREDIT stays CREDIT)
				// Don't change type as it represents the other side of the transfer
				
				// Apply new balances for linked transaction on its account
				if (linkedNewAccount != null) {
					if (linkedOldType == TransactionType.DEBIT) {
						linkedNewAccount.setBalance(linkedNewAccount.getBalance().subtract(newAmount));
					} else if (linkedOldType == TransactionType.CREDIT) {
						linkedNewAccount.setBalance(linkedNewAccount.getBalance().add(newAmount));
					}
					linkedNewAccount.setAppUser(appUser);
					accountRepository.save(linkedNewAccount);
				}
				
				// Default gptAccount to account if null (for old records)
				if (linkedTx.getGptAccount() == null) {
					linkedTx.setGptAccount(linkedTx.getAccount());
				}
				
				accountTransactionRepository.save(linkedTx);
				
				logger.info("Synced linked transfer transaction ID: {} with new amount: {}", linkedTx.getId(), newAmount);
			}
			
			AccountTransaction txUpdateEntity = accountTransactionMapper.toEntity(txUpdate);
			txUpdateEntity.setId(id);
			txUpdateEntity.setAppUser(appUser);
			txUpdateEntity.setAccount(newAccount);
			txUpdateEntity.setCategory(categoryService.getCategoryById(txUpdate.category().getId()));
			if (txUpdateEntity.getParent() == null) {
				txUpdateEntity.setParent(existingTx.getParent());
			}
			if (txUpdateEntity.getLinkedTransferId() == null) {
				txUpdateEntity.setLinkedTransferId(existingTx.getLinkedTransferId());
			}
			txUpdateEntity.setSourceId(existingTx.getSourceId());
			txUpdateEntity.setSourceThreadId(existingTx.getSourceThreadId());
			txUpdateEntity.setHref(existingTx.getHref());
			txUpdateEntity.setHrefText(existingTx.getHrefText());
			txUpdateEntity.setSourceTime(existingTx.getSourceTime());
			// Default gptAccount to account if existing one is null (for old records)
			txUpdateEntity.setGptAccount(existingTx.getGptAccount() != null ? existingTx.getGptAccount() : newAccount);
			txUpdateEntity.setGptAmount(existingTx.getGptAmount());
			txUpdateEntity.setGptDescription(existingTx.getGptDescription());
			txUpdateEntity.setGptExplanation(existingTx.getGptExplanation());
			txUpdateEntity.setGptType(existingTx.getGptType());
			// Keep currency from the incoming update (already mapped by mapper)
			
			// Process labels
			processLabels(txUpdateEntity, txUpdate, appUser);
			
			if (oldType == TransactionType.DEBIT) {
				oldAccount.setBalance(oldAccount.getBalance().add(oldAmount));
			} else if (oldType == TransactionType.CREDIT) {
				oldAccount.setBalance(oldAccount.getBalance().subtract(oldAmount));
			}

			if (newAccount.getId().equals(oldAccount.getId())) {
				// Same account: apply new transaction amount
				if (newType == TransactionType.DEBIT) {
					newAccount.setBalance(newAccount.getBalance().subtract(newAmount));
				} else if (newType == TransactionType.CREDIT) {
					newAccount.setBalance(newAccount.getBalance().add(newAmount));
				}
				newAccount.setAppUser(appUser);
				accountRepository.save(newAccount);
			} else {
				// Different account: update both accounts
				if (newType == TransactionType.DEBIT) {
					newAccount.setBalance(newAccount.getBalance().subtract(newAmount));
				} else if (newType == TransactionType.CREDIT) {
					newAccount.setBalance(newAccount.getBalance().add(newAmount));
				}
				oldAccount.setAppUser(appUser);
				newAccount.setAppUser(appUser);
				accountRepository.save(oldAccount);
				accountRepository.save(newAccount);
			}

			AccountTransaction saved = accountTransactionRepository.save(txUpdateEntity);
			return accountTransactionMapper.toDTO(saved);
		});
	}

	public Optional<AccountTransactionDTO> getById(String id) {
		AppUser appUser = appUserService.getCurrentUser();
		return accountTransactionRepository.findByAppUserAndId(appUser, id).map(accountTransactionMapper::toDTO);
	}

	public List<AccountTransactionDTO> getChildren(String parentId) {
		AppUser appUser = appUserService.getCurrentUser();
		return accountTransactionMapper
				.toDTOList(accountTransactionRepository.findByAppUserAndParent(appUser, parentId));
	}

	@Transactional
	public AccountTransactionDTO save(AccountTransaction transaction, AppUser appUser) {
		Account account = accountRepository.findByAppUserAndId(appUser, transaction.getAccount().getId()).get();
		if (account == null) {
			throw new IllegalArgumentException("Account not found");
		}
		if (transaction.getType() == null) {
			throw new IllegalArgumentException("Transaction type cannot be null");
		} else if (transaction.getAmount() == null) {
			throw new IllegalArgumentException("Transaction amount cannot be null");
		} else {
			if (transaction.getType().equals(TransactionType.DEBIT)) {
				account.setBalance(account.getBalance().subtract(transaction.getAmount()));
			} else {
				account.setBalance(account.getBalance().add(transaction.getAmount()));
			}
		}
		// Ensure data version for newly created transactions
		if (transaction.getDataVersionId() == null) {
			transaction.setDataVersionId(DATA_VERSION_V20);
		}
		// Default gptAccount to account if null to avoid constraint violation
		if (transaction.getGptAccount() == null) {
			transaction.setGptAccount(account);
		}
		transaction.setAppUser(appUser);
		accountRepository.save(account);
		AccountTransaction saved = accountTransactionRepository.save(transaction);
		return accountTransactionMapper.toDTO(saved);
	}

	@Transactional
	public List<AccountTransactionDTO> save(List<AccountTransactionDTO> transactions) {
		AppUser appUser = appUserService.getCurrentUser();
		if (transactions == null || transactions.isEmpty()) {
			return List.of();
		} else {
			transactions.forEach(tx -> {
				AccountTransaction transaction = accountTransactionMapper.toEntity(tx);
				if (isTransactionAlreadyPresent(transaction, appUser)) {
					logger.info("Transaction already exists for user: {}", appUser.getUsername());
				} else {
					save(transaction, appUser);
				}
			});
			return transactions;
		}
	}

	@Transactional
	public AccountTransactionDTO save(AccountTransactionDTO transaction) {
		AppUser appUser = appUserService.getCurrentUser();
		AccountTransaction entity = accountTransactionMapper.toEntity(transaction);
		// For controller-created transactions, set gptAccount to the same as account
		if (entity.getGptAccount() == null) {
			entity.setGptAccount(entity.getAccount());
		}
		// Process labels
		processLabels(entity, transaction, appUser);
		return save(entity, appUser);
	}

	@Transactional
	public void delete(String id) {
		AppUser appUser = appUserService.getCurrentUser();
		logger.info("Deleting transaction ID: {} for user: {}", id, appUser.getUsername());
		AccountTransaction existingTransaction = accountTransactionRepository.findByAppUserAndId(appUser, id)
				.orElseThrow(
						() -> new IllegalArgumentException("Transaction not found for user: " + appUser.getUsername()));
		
		// Handle linked transfer - reverse balances and delete both sides
		// Case 1: This transaction points to another (has linkedTransferId)
		// Case 2: Another transaction points to this one (we need to find it)
		AccountTransaction linkedTx = null;
		
		if (existingTransaction.getLinkedTransferId() != null) {
			// Case 1: This transaction has a linkedTransferId pointing to another transaction
			Optional<AccountTransaction> linkedTxOpt = accountTransactionRepository
				.findByAppUserAndId(appUser, existingTransaction.getLinkedTransferId());
			
			if (linkedTxOpt.isPresent()) {
				linkedTx = linkedTxOpt.get();
				logger.info("Found linked transaction (case 1): {}", linkedTx.getId());
			}
		}
		
		// Case 2: Another transaction points to this one (bidirectional check)
		if (linkedTx == null) {
			Optional<AccountTransaction> linkedTxOpt = accountTransactionRepository
				.findByAppUserAndLinkedTransferId(appUser, existingTransaction.getId());
			
			if (linkedTxOpt.isPresent()) {
				linkedTx = linkedTxOpt.get();
				logger.info("Found linked transaction (case 2): {}", linkedTx.getId());
			}
		}
		
		// Delete the linked transaction if found
		if (linkedTx != null) {
			logger.info("Deleting linked transfer transaction ID: {}", linkedTx.getId());
			
			// Reverse balance for linked transaction's account
			Account linkedAccount = linkedTx.getAccount();
			if (linkedAccount != null) {
				if (linkedTx.getType() == TransactionType.DEBIT) {
					linkedAccount.setBalance(linkedAccount.getBalance().add(linkedTx.getAmount()));
				} else if (linkedTx.getType() == TransactionType.CREDIT) {
					linkedAccount.setBalance(linkedAccount.getBalance().subtract(linkedTx.getAmount()));
				}
				linkedAccount.setAppUser(appUser);
				accountRepository.save(linkedAccount);
			}
			
			accountTransactionRepository.deleteByAppUserAndId(appUser, linkedTx.getId());
		}
		
		// Reverse balance for this transaction's account
		Account account = existingTransaction.getAccount();
		if (existingTransaction.getType() == TransactionType.DEBIT) {
			account.setBalance(account.getBalance().add(existingTransaction.getAmount()));
		} else if (existingTransaction.getType() == TransactionType.CREDIT) {
			account.setBalance(account.getBalance().subtract(existingTransaction.getAmount()));
		}
		account.setAppUser(appUser);
		accountRepository.save(account);
		
		if (existingTransaction.getParent() != null) {
			AccountTransaction parent = accountTransactionRepository.findByAppUserAndId(appUser, existingTransaction.getParent()).get();
			parent.setAmount(parent.getAmount().add(existingTransaction.getAmount()));
			logger.info("Updated parent transaction ID: {} amount to: {}", parent.getId(), parent.getAmount());
			parent.setDescription(parent.getDescription() + " ||deleted:" + existingTransaction.getDescription()+"|"+existingTransaction.getAmount());
			// Default gptAccount to account if null (for old records)
			if (parent.getGptAccount() == null) {
				parent.setGptAccount(parent.getAccount());
			}
			accountTransactionRepository.save(parent);
		}
		if (accountTransactionRepository.findByParentAndAppUser(existingTransaction.getId(), appUser).size() > 0) {
			throw new IllegalArgumentException(
					"Cannot delete transaction with children. Please delete children first.");
		}
		accountTransactionRepository.deleteByAppUserAndId(appUser, id);
	}

	@Transactional
	public boolean isTransactionAlreadyPresent(AccountTransaction newTransaction, AppUser appUser) {
		return findDuplicate(newTransaction, appUser)
				.map(existing -> {
					mergeSourceInfoIfNeeded(existing, newTransaction);
					return true;
				})
				.orElse(false);
	}

	@Transactional
	public Optional<AccountTransaction> findDuplicate(AccountTransaction newTransaction, AppUser appUser) {
		// Null-safety guards
		if (newTransaction == null || newTransaction.getSourceThreadId() == null) {
			return Optional.empty();
		}

		List<AccountTransaction> existingAccTxnList = accountTransactionRepository
				.findByAppUserAndSourceThreadId(appUser, newTransaction.getSourceThreadId());

		if (existingAccTxnList == null || existingAccTxnList.isEmpty()) {
			return Optional.empty();
		}

		// Exact sourceId match first
		for (AccountTransaction existingTxn : existingAccTxnList) {
			String existingSourceId = existingTxn.getSourceId();
			String newSourceId = newTransaction.getSourceId();
			if (existingSourceId != null && existingSourceId.equals(newSourceId)) {
				return Optional.of(existingTxn);
			}
		}

		// Fuzzy match
		for (AccountTransaction existingTxn : existingAccTxnList) {
			boolean isDateClose = false;
			if (existingTxn.getDate() != null && newTransaction.getDate() != null) {
				isDateClose = Math.abs(ChronoUnit.SECONDS.between(existingTxn.getDate(), newTransaction.getDate())) <= MATCH_TIME_WINDOW_SECONDS;
			}
			boolean isAmountEqual = existingTxn.getAmount() != null && newTransaction.getAmount() != null
					&& existingTxn.getAmount().compareTo(newTransaction.getAmount()) == 0;
			String existingDesc = normalizeDescription(existingTxn.getDescription());
			String newDesc = normalizeDescription(newTransaction.getDescription());
			boolean isDescriptionEqual = StringUtils.isNotBlank(existingDesc) && existingDesc.equals(newDesc);
			boolean isTypeEqual = existingTxn.getType() != null && existingTxn.getType().equals(newTransaction.getType());
			boolean isAccountOk = true; // Only enforce when both present
			if (existingTxn.getAccount() != null && newTransaction.getAccount() != null) {
				isAccountOk = StringUtils.equals(existingTxn.getAccount().getId(), newTransaction.getAccount().getId());
			}
			boolean isCurrencyOk = true; // Only enforce when both present
			if (existingTxn.getCurrency() != null && newTransaction.getCurrency() != null) {
				isCurrencyOk = existingTxn.getCurrency().equals(newTransaction.getCurrency());
			}

			boolean isMatch = isDateClose && isAmountEqual && isDescriptionEqual && isTypeEqual && isAccountOk && isCurrencyOk;
			if (isMatch) {
				return Optional.of(existingTxn);
			}
		}

		return Optional.empty();
	}

	@Transactional
	private void updateTransactionWithSourceInfo(AccountTransaction existingTxn, AccountTransaction newTransaction) {
		existingTxn.setSourceId(newTransaction.getSourceId());
		existingTxn.setSourceThreadId(newTransaction.getSourceThreadId());
		existingTxn.setSourceTime(newTransaction.getSourceTime());
		// Do not overwrite the existing transaction date here
		existingTxn.setDataVersionId(DATA_VERSION_V11);
		// Default gptAccount to account if null (for old records)
		if (existingTxn.getGptAccount() == null) {
			existingTxn.setGptAccount(existingTxn.getAccount());
		}
		accountTransactionRepository.save(existingTxn);
	}

	@Transactional
	public void mergeSourceInfoIfNeeded(AccountTransaction existingTxn, AccountTransaction newTransaction) {
		if (existingTxn == null || newTransaction == null) return;
		if (existingTxn.getDataVersionId() == null || !existingTxn.getDataVersionId().equals(DATA_VERSION_V11)) {
			updateTransactionWithSourceInfo(existingTxn, newTransaction);
		}
	}

	@Transactional
	public boolean isTransactionAlreadyPresent(AccountTransaction newTransaction) {
		AppUser appUser = appUserService.getCurrentUser();
		return isTransactionAlreadyPresent(newTransaction, appUser);
	}

	private String normalizeDescription(String input) {
		if (input == null) return "";
		String trimmed = input.trim();
		if (trimmed.isEmpty()) return "";
		return trimmed.replaceAll("\\s+", " ").toLowerCase();
	}

	public List<AccountTransactionDTO> getTransactionsByUploadedStatement(UploadedStatement statement) {
		AppUser appUser = appUserService.getCurrentUser();
		if (statement == null || statement.getId() == null) {
			return List.of();
		}
		return accountTransactionMapper
				.toDTOList(accountTransactionRepository.findByAppUserAndUploadedStatement(appUser, statement));
	}

	public void deleteAll(List<AccountTransactionDTO> transactions) {
		if (transactions == null || transactions.isEmpty()) {
			return;
		}
		AppUser appUser = appUserService.getCurrentUser();
		accountTransactionRepository.deleteAllByAppUserAndIdIn(appUser,
				transactions.stream().map(AccountTransactionDTO::id).toList());
	}

private Specification<AccountTransaction> buildTransactionSpec(String month, String date, String accountId, String type,
            String search, String categoryId, String labelId, boolean rootOnly) {

		AppUser appUser = appUserService.getCurrentUser();
		Specification<AccountTransaction> spec = Specification.where(null);

		if (rootOnly) {
			spec = spec.and(AccountTransactionSpecifications.isRootTransaction());
		}

		if (StringUtils.isNotBlank(categoryId)) {
			Set<String> categoryIds = categoryService.getAllDescendantCategoryIds(categoryId);
			spec = spec.and(AccountTransactionSpecifications.hasCategory(categoryIds));
			// When filtering by category, only include leaf transactions (no children)
			// This ensures we don't double-count split transactions
			spec = spec.and(AccountTransactionSpecifications.isLeafTransaction());
		}
		if (StringUtils.isNotBlank(labelId)) {
			spec = spec.and(AccountTransactionSpecifications.hasLabel(labelId));
		}
		if (StringUtils.isNotBlank(accountId)) {
			spec = spec.and(AccountTransactionSpecifications.hasAccount(accountId));
		}
		if (StringUtils.isNotBlank(type) && !"ALL".equalsIgnoreCase(type)) {
			spec = spec.and(AccountTransactionSpecifications.hasTransactionType(TransactionType.valueOf(type)));
		}
    if (StringUtils.isNotBlank(date)) {
        LocalDateTime start = LocalDateTime.parse(date + "T00:00:00");
        LocalDateTime end = LocalDateTime.parse(date + "T23:59:59");
        spec = spec.and(AccountTransactionSpecifications.dateBetween(start, end));
    } else if (StringUtils.isNotBlank(month)) {
        YearMonth ym = YearMonth.parse(month);
        LocalDateTime start = ym.atDay(1).atStartOfDay();
        LocalDateTime end = ym.atEndOfMonth().atTime(LocalTime.MAX);
        spec = spec.and(AccountTransactionSpecifications.dateBetween(start, end));
    }
		if (StringUtils.isNotBlank(search)) {
			spec = spec.and(AccountTransactionSpecifications.matchesSearch(search));
		}
		spec = spec.and(AccountTransactionSpecifications.belongsToUser(appUser));
		return spec;
	}
	
	@Transactional
public List<AccountTransactionDTO> getFilteredTransactionsForExport(String month, String date, String accountId, String type,
            String categoryId, String labelId, String search) {
		logger.info(
				"Fetching transactions for export for month: {}, accountId: {}, type: {}, search: {}, categoryId: {}, labelId: {}",
				month, accountId, type, search, categoryId, labelId);
    Specification<AccountTransaction> spec = StringUtils.isNotBlank(categoryId)
                ? buildTransactionSpec(month, date, accountId, type, search, categoryId, labelId, false)
                : buildTransactionSpec(month, date, accountId, type, search, null, labelId, true);
		List<AccountTransaction> list = accountTransactionRepository.findAll(spec,
				Sort.by(Sort.Direction.DESC, "date"));
		logger.info("Total transactions found for export: {}", list.size());
		return accountTransactionMapper.toDTOList(list);
	}

public Page<AccountTransactionDTO> getFilteredTransactions(Pageable pageable, String month, String date, String accountId,
            String type, String search, String categoryId, String labelId) {
		logger.info("Fetching transactions for month: {}, accountId: {}, type: {}, search: {}, categoryId: {}, labelId: {}", month,
				accountId, type, search, categoryId, labelId);
    Specification<AccountTransaction> spec = StringUtils.isNotBlank(categoryId)
                ? buildTransactionSpec(month, date, accountId, type, search, categoryId, labelId, false)
                : buildTransactionSpec(month, date, accountId, type, search, null, labelId, true);
		
		Page<AccountTransaction> page = accountTransactionRepository.findAll(spec, pageable);
		logger.info("Total transactions found: {}", page.getTotalElements());
		
		// FIX N+1 QUERY: Batch fetch all children for this page in a single query
		List<String> parentIds = page.getContent().stream()
			.map(AccountTransaction::getId)
			.toList();
		
		Map<String, List<AccountTransactionDTO>> childrenByParent = new HashMap<>();
		if (!parentIds.isEmpty()) {
			AppUser currentUser = appUserService.getCurrentUser();
			List<AccountTransaction> allChildren = accountTransactionRepository
				.findByAppUserAndParentIn(currentUser, parentIds);
			
			// Group children by parent ID
			for (AccountTransaction child : allChildren) {
				childrenByParent
					.computeIfAbsent(child.getParent(), k -> new ArrayList<>())
					.add(accountTransactionMapper.toDTO(child));
			}
		}
		
		List<AccountTransactionDTO> formatted = page.getContent().stream()
			    .map(tx -> {
			        List<AccountTransactionDTO> children = childrenByParent.getOrDefault(tx.getId(), List.of());

		        return new AccountTransactionDTO(
		            tx.getId(),
		            tx.getDate(),
		            tx.getAmount(),
		            tx.getDescription(),
		            null, // shortDescription handled inside record
		            tx.getExplanation(),
		            null, // shortExplanation handled inside record
		            tx.getType(),
		            accountMapper.toDTO(tx.getAccount()),
		            categoryMapper.toDTO(tx.getCategory()),
		            tx.getParent(),
		            children,
		            tx.getLinkedTransferId(),
		            tx.getGptAmount(),
		            tx.getGptDescription(),
		            tx.getGptExplanation(),
		            tx.getGptType(),
		            tx.getCurrency(),
		            accountMapper.toDTO(tx.getGptAccount()),
		            tx.getGptCurrency(),
		            tx.getLabels() != null ? labelMapper.toDTOList(tx.getLabels()) : List.of()
		        );
			    })
			    .toList();
		return new PageImpl<>(formatted, pageable, page.getTotalElements());
	}


public BigDecimal getCurrentTotal(String month, String date, String accountId, String type, String search, String categoryId, String labelId) {
        logger.info("Calculating current total for month: {}, date: {}, accountId: {}, type: {}, search: {}, categoryId: {}, labelId: {}",
                month, date, accountId, type, search, categoryId, labelId);
        
        Specification<AccountTransaction> spec = StringUtils.isNotBlank(categoryId)
                ? buildTransactionSpec(month, date, accountId, type, search, categoryId, labelId, false)
                : buildTransactionSpec(month, date, accountId, type, search, null, labelId, true);
        
        // Calculate total using database-level aggregation
        BigDecimal total = calculateTotalWithSpec(spec);
        
        logger.info("Current total calculated: {}", total);
        return total.setScale(2, RoundingMode.HALF_UP);
    }
    
    /**
     * Calculate total amount using database-level aggregation with Specification
     * This is much more efficient than fetching all records and summing in Java
     */
    private BigDecimal calculateTotalWithSpec(Specification<AccountTransaction> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<BigDecimal> query = cb.createQuery(BigDecimal.class);
        Root<AccountTransaction> root = query.from(AccountTransaction.class);

        // Apply the same filters from the specification
        Predicate predicate = spec.toPredicate(root, query, cb);

        // Create CASE expression: 
        // CASE WHEN type = 'CREDIT' THEN amount ELSE -amount END
        Expression<BigDecimal> caseExpression = cb.<BigDecimal>selectCase()
                .when(cb.equal(root.get("type"), TransactionType.CREDIT), root.get("amount"))
                .otherwise(cb.neg(root.get("amount")));

        // Sum the case expression
        Expression<BigDecimal> sum = cb.sum(caseExpression);
        
        // Use COALESCE to return 0 if no results
        Expression<BigDecimal> total = cb.coalesce(sum, BigDecimal.ZERO);

        query.select(total);
        if (predicate != null) {
            query.where(predicate);
        }

        BigDecimal result = entityManager.createQuery(query).getSingleResult();
        return result != null ? result : BigDecimal.ZERO;
    }

// --- Backward-compatible overloads (without 'date' and 'labelId') for existing tests/integrations ---
public Page<AccountTransactionDTO> getFilteredTransactions(Pageable pageable, String month, String accountId,
        String type, String search, String categoryId) {
    return getFilteredTransactions(pageable, month, null, accountId, type, search, categoryId, null);
}

public BigDecimal getCurrentTotal(String month, String accountId, String type, String search, String categoryId) {
    return getCurrentTotal(month, null, accountId, type, search, categoryId, null);
}

public List<AccountTransactionDTO> getFilteredTransactionsForExport(String month, String accountId, String type,
        String categoryId, String search) {
    return getFilteredTransactionsForExport(month, null, accountId, type, categoryId, null, search);
}
}
