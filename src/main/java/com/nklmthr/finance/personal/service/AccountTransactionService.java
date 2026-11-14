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
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.dto.AccountTransactionDTO;
import com.nklmthr.finance.personal.dto.BulkLabelsRequest;
import com.nklmthr.finance.personal.dto.BulkUpdateResponse;
import com.nklmthr.finance.personal.dto.LabelDTO;
import com.nklmthr.finance.personal.dto.TransactionPageDTO;
import com.nklmthr.finance.personal.dto.TransferDestination;
import com.nklmthr.finance.personal.dto.TransferRequest;
import com.nklmthr.finance.personal.dto.TransferSplitDTO;
import com.nklmthr.finance.personal.model.TransferSplit;
import com.nklmthr.finance.personal.repository.TransferSplitRepository;
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
import com.nklmthr.finance.personal.repository.AttachmentRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import com.nklmthr.finance.personal.exception.TransferDestinationDeleteException;

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
	
	@Autowired
	private AttachmentRepository attachmentRepository;

	@Autowired
	private TransferSplitRepository transferSplitRepository;

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
		logger.info("Creating transfer from transaction ID: {}", request.getSourceTransactionId());
		AccountTransaction source = accountTransactionRepository.findByAppUserAndId(appUser, request.getSourceTransactionId())
				.orElseThrow(() -> new IllegalArgumentException(
						"Source transaction not found for user: " + appUser.getUsername()));

		// Block transfer from a split parent
		List<AccountTransaction> children = accountTransactionRepository.findByAppUserAndParent(appUser, source.getId());
		if (!children.isEmpty()) {
			throw new IllegalArgumentException(
				"Cannot transfer a split transaction. Please delete the split first or transfer the individual child transactions.");
		}

		List<TransferDestination> destinations = request.getDestinations();
		if (destinations == null || destinations.isEmpty()) {
			throw new IllegalArgumentException("At least one destination is required.");
		}

		BigDecimal totalRequested = destinations.stream()
			.map(TransferDestination::getAmount)
			.reduce(BigDecimal.ZERO, BigDecimal::add);

		// Chaining: if the source is a CREDIT transaction, auto-create a DEBIT on the same account
		AccountTransaction debit;
		if (source.getType() == TransactionType.CREDIT) {
			if (totalRequested.compareTo(source.getAmount()) > 0) {
				throw new IllegalArgumentException(
					"Sum of destination amounts (" + totalRequested + ") exceeds source credit amount (" + source.getAmount() + ").");
			}
			debit = new AccountTransaction();
			debit.setAccount(source.getAccount());
			debit.setGptAccount(source.getAccount());
			debit.setAmount(totalRequested);
			debit.setDate(source.getDate().plusSeconds(1));
			debit.setDescription(source.getDescription());
			debit.setExplanation("Chained transfer (auto-created debit)");
			debit.setType(TransactionType.DEBIT);
			debit.setCategory(categoryService.getTransferCategory());
			debit.setAppUser(appUser);
			debit.setCurrency(source.getCurrency());
			// Subtract from source account balance
			Account sourceAccount = debit.getAccount();
			sourceAccount.setBalance(sourceAccount.getBalance().subtract(totalRequested));
			accountRepository.save(sourceAccount);
			debit = accountTransactionRepository.save(debit);
			logger.info("Chaining: auto-created debit ID: {} on account: {} for amount: {}",
				debit.getId(), sourceAccount.getId(), totalRequested);
		} else {
			// Normal DEBIT source: validate sum == source amount
			if (totalRequested.compareTo(source.getAmount()) != 0) {
				throw new IllegalArgumentException(
					"Sum of destination amounts (" + totalRequested + ") must equal source transaction amount (" + source.getAmount() + ").");
			}
			debit = source;
		}

		// Set debit category to TRANSFERS
		debit.setCategory(categoryService.getTransferCategory());
		if (debit.getGptAccount() == null) {
			debit.setGptAccount(debit.getAccount());
		}
		accountTransactionRepository.save(debit);

		// Create a credit transaction + TransferSplit row for each destination
		for (TransferDestination dest : destinations) {
			Account toAccount = accountRepository.findByAppUserAndId(appUser, dest.getAccountId())
				.orElseThrow(() -> new IllegalArgumentException("Destination account not found: " + dest.getAccountId()));

			toAccount.setBalance(toAccount.getBalance().add(dest.getAmount()));
			accountRepository.save(toAccount);

			AccountTransaction credit = new AccountTransaction();
			credit.setAccount(toAccount);
			credit.setGptAccount(toAccount);
			credit.setAmount(dest.getAmount());
			credit.setDate(debit.getDate().plusSeconds(1));
			credit.setDescription(debit.getDescription());
			credit.setExplanation(dest.getExplanation());
			credit.setType(TransactionType.CREDIT);
			credit.setCategory(categoryService.getTransferCategory());
			credit.setAppUser(appUser);
			credit.setCurrency(debit.getCurrency());
			credit.setGptDescription(null);
			credit.setGptExplanation(null);
			credit.setGptAmount(null);
			credit.setGptType(null);
			credit = accountTransactionRepository.save(credit);

			TransferSplit split = TransferSplit.builder()
				.sourceTransactionId(debit.getId())
				.destTransactionId(credit.getId())
				.amount(dest.getAmount())
				.explanation(dest.getExplanation())
				.build();
			transferSplitRepository.save(split);

			logger.info("Transfer split created. Debit ID: {}, Credit ID: {}, Amount: {}",
				debit.getId(), credit.getId(), dest.getAmount());
		}
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

		// Validate split amounts against parent amount BEFORE any mutations
		List<AccountTransaction> existingChildren = accountTransactionRepository.findByAppUserAndParent(appUser, parent.getId());
		BigDecimal restoredAmount = parent.getAmount();
		for (AccountTransaction child : existingChildren) {
			restoredAmount = restoredAmount.add(child.getAmount());
		}

		BigDecimal totalSplitAmount = BigDecimal.ZERO;
		for (AccountTransactionDTO st : splitTransactions) {
			totalSplitAmount = totalSplitAmount.add(st.amount());
		}

		if (totalSplitAmount.compareTo(restoredAmount) != 0) {
			return ResponseEntity.badRequest().body(
				String.format("Split amounts total (%.2f) does not match parent amount (%.2f)",
					totalSplitAmount, restoredAmount));
		}

		// All validation passed — now mutate
		for (AccountTransaction child : existingChildren) {
			child.setParent(null);
			accountTransactionRepository.delete(child);
		}

		// Set parent amount to the validated total and assign split category
		parent.setAmount(restoredAmount);
		parent.setCategory(categoryService.getSplitTrnsactionCategory());

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

			AccountTransaction savedChild = accountTransactionRepository.save(child);
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

			// Prevent amount change if new-model transfer splits exist on this transaction
			if (newAmount.compareTo(oldAmount) != 0 && transferSplitRepository.existsBySourceTransactionId(id)) {
				throw new IllegalArgumentException(
					"Cannot change amount of a transaction with active transfer splits. Delete the transfer first.");
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
			
			// Handle linked transfer transactions - legacy 1-to-1 linkedTransferId sync.
			// Skipped for new-model transfers (those use transfer_splits and don't need sync here).
			AccountTransaction linkedTx = null;
			boolean hasNewModelSplits = transferSplitRepository.existsBySourceTransactionId(id)
				|| !transferSplitRepository.findByDestTransactionId(id).isEmpty();

			if (!hasNewModelSplits) {
				if (existingTx.getLinkedTransferId() != null) {
					Optional<AccountTransaction> linkedTxOpt = accountTransactionRepository
						.findByAppUserAndId(appUser, existingTx.getLinkedTransferId());
					if (linkedTxOpt.isPresent()) {
						linkedTx = linkedTxOpt.get();
						logger.info("Found linked transaction (case 1): {}", linkedTx.getId());
					}
				}
				if (linkedTx == null) {
					Optional<AccountTransaction> linkedTxOpt = accountTransactionRepository
						.findByAppUserAndLinkedTransferId(appUser, existingTx.getId());
					if (linkedTxOpt.isPresent()) {
						linkedTx = linkedTxOpt.get();
						logger.info("Found linked transaction (case 2): {}", linkedTx.getId());
					}
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
			
			// Update existing entity instead of creating a new one to avoid constraint violations
			// Update the fields from the DTO
			existingTx.setDescription(txUpdate.description());
			existingTx.setExplanation(txUpdate.explanation());
			existingTx.setAmount(newAmount);
			existingTx.setDate(txUpdate.date());
			existingTx.setType(newType);
			existingTx.setAccount(newAccount);
			existingTx.setCategory(categoryService.getCategoryById(txUpdate.category().getId()));
			existingTx.setCurrency(txUpdate.currency());
			// Note: Don't update parent, linkedTransferId, source fields as they should remain unchanged
			// Note: Don't update gpt fields as they should remain unchanged
			
			// Process labels - this will properly clear and update the managed entity's labels
			processLabels(existingTx, txUpdate, appUser);
			
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

			AccountTransaction saved = accountTransactionRepository.save(existingTx);

			return accountTransactionMapper.toDTO(saved);
		});
	}

	public Optional<AccountTransactionDTO> getById(String id) {
		AppUser appUser = appUserService.getCurrentUser();
		return accountTransactionRepository.findByAppUserAndId(appUser, id).map(tx -> {
			AccountTransactionDTO dto = accountTransactionMapper.toDTO(tx);
			List<TransferSplitDTO> splits = getTransferSplits(id);
			return new AccountTransactionDTO(
				dto.id(), dto.date(), dto.amount(), dto.description(), dto.shortDescription(),
				dto.explanation(), dto.shortExplanation(), dto.type(), dto.account(), dto.category(),
				dto.parentId(), dto.children(), dto.linkedTransferId(),
				dto.gptAmount(), dto.gptDescription(), dto.gptExplanation(), dto.gptType(),
				dto.currency(), dto.gptAccount(), dto.gptCurrency(), dto.labels(),
				dto.attachmentCount(), splits.isEmpty() ? null : splits, splits.size(),
				transferSplitRepository.existsByDestTransactionId(id)
			);
		});
	}

	public List<TransferSplitDTO> getTransferSplits(String transactionId) {
		AppUser appUser = appUserService.getCurrentUser();
		return transferSplitRepository.findBySourceTransactionId(transactionId).stream()
			.map(split -> {
				AccountTransactionDTO destDto = accountTransactionRepository
					.findByAppUserAndId(appUser, split.getDestTransactionId())
					.map(accountTransactionMapper::toDTO)
					.orElse(null);
				return new TransferSplitDTO(
					split.getId(), split.getSourceTransactionId(), destDto,
					split.getAmount(), split.getExplanation()
				);
			})
			.collect(Collectors.toList());
	}

	@Transactional
	public void removeTransferSplits(String sourceId, boolean deleteDestinations) {
		AppUser appUser = appUserService.getCurrentUser();
		AccountTransaction source = accountTransactionRepository.findByAppUserAndId(appUser, sourceId)
			.orElseThrow(() -> new IllegalArgumentException("Source transaction not found"));

		List<TransferSplit> splits = transferSplitRepository.findBySourceTransactionId(sourceId);
		if (splits.isEmpty()) {
			// Maybe the caller passed a dest ID — look up its source and redirect
			List<TransferSplit> asDest = transferSplitRepository.findByDestTransactionId(sourceId);
			if (!asDest.isEmpty()) {
				removeTransferSplits(asDest.get(0).getSourceTransactionId(), deleteDestinations);
				return;
			}
			throw new IllegalArgumentException("No transfer splits found for transaction: " + sourceId);
		}

		if (deleteDestinations) {
			for (TransferSplit split : splits) {
				accountTransactionRepository.findByAppUserAndId(appUser, split.getDestTransactionId())
					.ifPresent(credit -> {
						Account creditAccount = credit.getAccount();
						if (creditAccount != null) {
							creditAccount.setBalance(creditAccount.getBalance().subtract(credit.getAmount()));
							accountRepository.save(creditAccount);
						}
						accountTransactionRepository.deleteByAppUserAndId(appUser, credit.getId());
					});
			}
			// Add back to source account (undo the debit effect)
			Account sourceAccount = source.getAccount();
			BigDecimal totalAmount = splits.stream().map(TransferSplit::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
			sourceAccount.setBalance(sourceAccount.getBalance().add(totalAmount));
			accountRepository.save(sourceAccount);
		}
		// In unlink-only mode: credit transactions become standalone, no balance changes

		transferSplitRepository.deleteAllBySourceTransactionId(sourceId);
		source.setCategory(null);
		if (source.getGptAccount() == null) source.setGptAccount(source.getAccount());
		accountTransactionRepository.save(source);
		logger.info("Removed transfer splits for source ID: {}, deleteDestinations={}", sourceId, deleteDestinations);
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
		
		// Check if this is an update (has ID) or a new transaction
		AccountTransaction entity;
		if (transaction.id() != null) {
			// UPDATE: Load existing entity from database
			entity = accountTransactionRepository.findByAppUserAndId(appUser, transaction.id())
					.orElseThrow(() -> new IllegalArgumentException("Transaction not found for user: " + appUser.getUsername()));
			
			// Update fields from DTO
			entity.setDate(transaction.date());
			entity.setAmount(transaction.amount());
			entity.setDescription(transaction.description());
			entity.setExplanation(transaction.explanation());
			entity.setType(transaction.type());
			entity.setCurrency(transaction.currency());
			
			// Update account if changed
			if (transaction.account() != null && transaction.account().id() != null) {
				entity.setAccount(accountRepository.findByAppUserAndId(appUser, transaction.account().id())
						.orElseThrow(() -> new IllegalArgumentException("Account not found")));
			}
			
			// Update category if changed
			if (transaction.category() != null && transaction.category().getId() != null) {
				entity.setCategory(categoryService.getCategoryById(appUser, transaction.category().getId()));
			}
			
			// Update gptAccount if changed
			if (transaction.gptAccount() != null && transaction.gptAccount().id() != null) {
				entity.setGptAccount(accountRepository.findByAppUserAndId(appUser, transaction.gptAccount().id())
						.orElseThrow(() -> new IllegalArgumentException("GPT Account not found")));
			}
		} else {
			// NEW: Create entity from DTO
			entity = accountTransactionMapper.toEntity(transaction);
		}
		
		// For controller-created transactions, set gptAccount to the same as account
		if (entity.getGptAccount() == null) {
			entity.setGptAccount(entity.getAccount());
		}
		// Process labels
		processLabels(entity, transaction, appUser);
		// Bare save() handles prediction adjustment centrally (idempotent).
		return save(entity, appUser);
	}

	@Transactional
	public void delete(String id) {
		AppUser appUser = appUserService.getCurrentUser();
		logger.info("Deleting transaction ID: {} for user: {}", id, appUser.getUsername());
		AccountTransaction existingTransaction = accountTransactionRepository.findByAppUserAndId(appUser, id)
				.orElseThrow(
						() -> new IllegalArgumentException("Transaction not found for user: " + appUser.getUsername()));

		// Fail fast before any balance mutations — otherwise a blocked parent
		// delete would reverse the account and then throw, desyncing balances.
		if (accountTransactionRepository.findByParentAndAppUser(existingTransaction.getId(), appUser).size() > 0) {
			throw new IllegalArgumentException(
					"Cannot delete transaction with children. Please delete children first.");
		}
		
		// Handle linked transfer - reverse balances and delete both sides.
		// Check new-model transfer_splits first, fall back to legacy linkedTransferId.
		List<TransferSplit> splitsAsSource = transferSplitRepository
			.findBySourceTransactionId(existingTransaction.getId());
		List<TransferSplit> splitsAsDest = transferSplitRepository
			.findByDestTransactionId(existingTransaction.getId());

		if (!splitsAsSource.isEmpty()) {
			// Deleting the debit (source) leg: delete all credit destinations + reverse their balances
			for (TransferSplit split : splitsAsSource) {
				accountTransactionRepository.findByAppUserAndId(appUser, split.getDestTransactionId())
					.ifPresent(credit -> {
						Account creditAccount = credit.getAccount();
						if (creditAccount != null) {
							creditAccount.setBalance(creditAccount.getBalance().subtract(credit.getAmount()));
							accountRepository.save(creditAccount);
						}
						accountTransactionRepository.deleteByAppUserAndId(appUser, credit.getId());
						logger.info("Deleted transfer destination ID: {}", credit.getId());
					});
			}
			transferSplitRepository.deleteAllBySourceTransactionId(existingTransaction.getId());
		} else if (!splitsAsDest.isEmpty()) {
			// This credit is a transfer destination. Block direct delete and tell the caller
			// to use the undo-transfer flow on the source transaction so the user is asked
			// whether to delete both legs or just unlink.
			String sourceId = splitsAsDest.get(0).getSourceTransactionId();
			throw new TransferDestinationDeleteException(
				"This transaction is a transfer destination. Use the undo-transfer action to remove it.",
				sourceId
			);
		} else {
			// Legacy fallback: use linkedTransferId bidirectional lookup for unconverted records
			AccountTransaction linkedTx = null;

			if (existingTransaction.getLinkedTransferId() != null) {
				Optional<AccountTransaction> linkedTxOpt = accountTransactionRepository
					.findByAppUserAndId(appUser, existingTransaction.getLinkedTransferId());
				if (linkedTxOpt.isPresent()) {
					linkedTx = linkedTxOpt.get();
					logger.info("Found linked transaction (legacy case 1): {}", linkedTx.getId());
				}
			}
			if (linkedTx == null) {
				Optional<AccountTransaction> linkedTxOpt = accountTransactionRepository
					.findByAppUserAndLinkedTransferId(appUser, existingTransaction.getId());
				if (linkedTxOpt.isPresent()) {
					linkedTx = linkedTxOpt.get();
					logger.info("Found linked transaction (legacy case 2): {}", linkedTx.getId());
				}
			}
			if (linkedTx != null) {
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
		}
		
		// Account balance was applied once when the root (non-child) transaction
		// was created. Split children only reshuffle parent/child amounts and never
		// touch the account — so deleting a child must restore the parent amount
		// only, not reverse the account again.
		boolean isSplitChild = existingTransaction.getParent() != null;
		if (!isSplitChild) {
			Account account = existingTransaction.getAccount();
			if (existingTransaction.getType() == TransactionType.DEBIT) {
				account.setBalance(account.getBalance().add(existingTransaction.getAmount()));
			} else if (existingTransaction.getType() == TransactionType.CREDIT) {
				account.setBalance(account.getBalance().subtract(existingTransaction.getAmount()));
			}
			account.setAppUser(appUser);
			accountRepository.save(account);
		} else {
			logger.info("Skipping account balance reverse for split child ID: {}; restoring parent amount only", id);
		}
		
		if (isSplitChild) {
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
	
		accountTransactionRepository.deleteByAppUserAndId(appUser, id);
}

	@Transactional
	public BulkUpdateResponse bulkDelete(List<String> transactionIds) {
		if (transactionIds == null || transactionIds.isEmpty()) {
			return new BulkUpdateResponse(0, 0, List.of());
		}
		int requested = transactionIds.size();
		int deleted = 0;
		List<BulkUpdateResponse.SkippedItem> skipped = new ArrayList<>();
		for (String id : transactionIds) {
			try {
				delete(id);
				deleted++;
			} catch (Exception e) {
				skipped.add(new BulkUpdateResponse.SkippedItem(id, e.getMessage()));
			}
		}
		return new BulkUpdateResponse(requested, deleted, skipped);
	}

	@Transactional
	public BulkUpdateResponse bulkAssignCategory(List<String> transactionIds, String categoryId) {
		AppUser appUser = appUserService.getCurrentUser();
		if (transactionIds == null || transactionIds.isEmpty()) {
			return new BulkUpdateResponse(0, 0, List.of());
		}
		// Resolve & validate the target category up front so we fail fast.
		var category = categoryService.getCategoryById(appUser, categoryId);
		if (category == null) {
			throw new IllegalArgumentException("Category not found");
		}

		List<BulkUpdateResponse.SkippedItem> skipped = new ArrayList<>();
		int updated = 0;

		for (String id : transactionIds) {
			Optional<AccountTransaction> opt = accountTransactionRepository.findByAppUserAndId(appUser, id);
			if (opt.isEmpty()) {
				skipped.add(new BulkUpdateResponse.SkippedItem(id, "Transaction not found"));
				continue;
			}
			AccountTransaction tx = opt.get();

			// Skip transfer transactions (either side) — their category is locked to TRANSFER.
			boolean isTransfer = tx.getLinkedTransferId() != null
					|| accountTransactionRepository.findByAppUserAndLinkedTransferId(appUser, tx.getId()).isPresent()
					|| transferSplitRepository.existsBySourceTransactionId(tx.getId())
					|| !transferSplitRepository.findByDestTransactionId(tx.getId()).isEmpty();
			if (isTransfer) {
				skipped.add(new BulkUpdateResponse.SkippedItem(id,
						"Cannot change category of a transfer transaction"));
				continue;
			}

			// Skip split parents (their children carry the real category) to avoid silently
			// changing aggregated rows.
			List<AccountTransaction> children = accountTransactionRepository
					.findByAppUserAndParent(appUser, tx.getId());
			if (!children.isEmpty()) {
				skipped.add(new BulkUpdateResponse.SkippedItem(id,
						"Cannot change category of a split parent — change its children instead"));
				continue;
			}

			tx.setCategory(category);
			if (tx.getGptAccount() == null) {
				tx.setGptAccount(tx.getAccount());
			}
			accountTransactionRepository.save(tx);
			updated++;
		}

		logger.info("Bulk category update for user {}: requested={}, updated={}, skipped={}",
				appUser.getUsername(), transactionIds.size(), updated, skipped.size());
		return new BulkUpdateResponse(transactionIds.size(), updated, skipped);
	}

	@Transactional
	public BulkUpdateResponse bulkUpdateLabels(List<String> transactionIds,
			List<LabelDTO> labels, BulkLabelsRequest.Mode mode) {
		AppUser appUser = appUserService.getCurrentUser();
		if (transactionIds == null || transactionIds.isEmpty()) {
			return new BulkUpdateResponse(0, 0, List.of());
		}
		if (mode == null) {
			throw new IllegalArgumentException("Mode is required (ADD or REPLACE)");
		}
		if (mode == BulkLabelsRequest.Mode.ADD && (labels == null || labels.isEmpty())) {
			throw new IllegalArgumentException("At least one label is required for ADD mode");
		}

		// Resolve all labels once (creates new ones if needed) — same pattern as processLabels.
		List<com.nklmthr.finance.personal.model.Label> resolved;
		if (labels == null || labels.isEmpty()) {
			resolved = new ArrayList<>();
		} else {
			resolved = labels.stream()
					.map(LabelDTO::name)
					.filter(n -> n != null && !n.trim().isEmpty())
					.distinct()
					.map(name -> labelService.findOrCreateLabel(appUser, name))
					.collect(Collectors.toList());
		}

		List<BulkUpdateResponse.SkippedItem> skipped = new ArrayList<>();
		int updated = 0;

		for (String id : transactionIds) {
			Optional<AccountTransaction> opt = accountTransactionRepository.findByAppUserAndId(appUser, id);
			if (opt.isEmpty()) {
				skipped.add(new BulkUpdateResponse.SkippedItem(id, "Transaction not found"));
				continue;
			}
			AccountTransaction tx = opt.get();

			List<com.nklmthr.finance.personal.model.Label> nextLabels;
			if (mode == BulkLabelsRequest.Mode.REPLACE) {
				nextLabels = new ArrayList<>(resolved);
			} else {
				// ADD mode: union of existing and new (deduplicate by id).
				nextLabels = new ArrayList<>(tx.getLabels() != null ? tx.getLabels() : List.of());
				for (com.nklmthr.finance.personal.model.Label newLabel : resolved) {
					boolean already = nextLabels.stream()
							.anyMatch(l -> l != null && l.getId() != null && l.getId().equals(newLabel.getId()));
					if (!already) {
						nextLabels.add(newLabel);
					}
				}
			}
			tx.setLabels(nextLabels, appUser);
			if (tx.getGptAccount() == null) {
				tx.setGptAccount(tx.getAccount());
			}
			accountTransactionRepository.save(tx);
			updated++;
		}

		logger.info("Bulk label update for user {}: mode={}, requested={}, updated={}, skipped={}",
				appUser.getUsername(), mode, transactionIds.size(), updated, skipped.size());
		return new BulkUpdateResponse(transactionIds.size(), updated, skipped);
	}

	/**
	 * Check if a statement transaction already exists (for uploaded statement deduplication).
	 * Keys off description (original bank import text), amount, type, account, and date.
	 * Explanation is intentionally ignored so human notes do not break re-import dedupe.
	 * Also accepts a legacy layout where older SBI imports stored the full bank text in explanation.
	 */
	@Transactional
	public boolean isStatementTransactionDuplicate(AccountTransaction newTransaction, AppUser appUser) {
		if (newTransaction == null || newTransaction.getAccount() == null) {
			return false;
		}
		
		// Get transactions for the same account within a time window
		LocalDateTime startDate = newTransaction.getDate().minusSeconds(MATCH_TIME_WINDOW_SECONDS);
		LocalDateTime endDate = newTransaction.getDate().plusSeconds(MATCH_TIME_WINDOW_SECONDS);
		
		List<AccountTransaction> candidateTransactions = accountTransactionRepository
			.findByAccountAndDateBetween(
				newTransaction.getAccount(),
				startDate,
				endDate
			);
		
		if (candidateTransactions == null || candidateTransactions.isEmpty()) {
			return false;
		}
		
		String newDescription = normalizeDescription(newTransaction.getDescription());
		
		for (AccountTransaction existing : candidateTransactions) {
			// Split parents have their amount drained to 0 (redistributed to children),
			// so skip the amount check for them — description + type + account is enough.
			boolean isSplitParent = existing.getCategory() != null
					&& "SPLIT".equals(existing.getCategory().getName());

			boolean isAmountEqual = isSplitParent
					|| (existing.getAmount() != null && newTransaction.getAmount() != null
							&& existing.getAmount().compareTo(newTransaction.getAmount()) == 0);

			boolean isTypeEqual = existing.getType() != null && existing.getType().equals(newTransaction.getType());

			boolean isAccountEqual = StringUtils.equals(
					existing.getAccount().getId(),
					newTransaction.getAccount().getId()
			);

			if (!isAmountEqual || !isTypeEqual || !isAccountEqual || StringUtils.isBlank(newDescription)) {
				continue;
			}

			String existingDescription = normalizeDescription(existing.getDescription());
			boolean isDescriptionEqual = StringUtils.isNotBlank(existingDescription)
					&& existingDescription.equals(newDescription);

			// Legacy SBI rows: full bank text lived in explanation, short extract in description
			String existingExplanation = normalizeDescription(existing.getExplanation());
			boolean isLegacyBankTextEqual = StringUtils.isNotBlank(existingExplanation)
					&& existingExplanation.equals(newDescription);

			// Legacy short extract in description is a substring of the new full bank text
			// (or vice versa). Require a minimum length to avoid weak matches.
			boolean isLegacyDescriptionContained = false;
			if (StringUtils.isNotBlank(existingDescription) && existingDescription.length() >= 20) {
				isLegacyDescriptionContained = newDescription.contains(existingDescription)
						|| existingDescription.contains(newDescription);
			}
			
			if (isDescriptionEqual || isLegacyBankTextEqual || isLegacyDescriptionContained) {
				logger.info("Found duplicate statement transaction: existing ID={}, date={}, amount={}, description={}", 
						existing.getId(), existing.getDate(), existing.getAmount(), 
						existing.getDescription() != null ? existing.getDescription().substring(0, Math.min(50, existing.getDescription().length())) : "null");
				return true;
			}
		}
		
		return false;
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

private Specification<AccountTransaction> buildTransactionSpec(String month, String date, String startDate, String endDate,
            String accountId, String type, String search, String categoryId, String labelId, boolean rootOnly,
            Boolean hasAttachments) {

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
		if (Boolean.TRUE.equals(hasAttachments)) {
			spec = spec.and(AccountTransactionSpecifications.hasAttachments());
		}
    // Date range filtering - priority order: date range > specific date > month
    if (StringUtils.isNotBlank(startDate) && StringUtils.isNotBlank(endDate)) {
        LocalDateTime start = LocalDateTime.parse(startDate + "T00:00:00");
        LocalDateTime end = LocalDateTime.parse(endDate + "T23:59:59");
        spec = spec.and(AccountTransactionSpecifications.dateBetween(start, end));
    } else if (StringUtils.isNotBlank(date)) {
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
public List<AccountTransactionDTO> getFilteredTransactionsForExport(String month, String date, String startDate, String endDate,
            String accountId, String type, String categoryId, String labelId, String search, Boolean hasAttachments) {
		logger.info(
				"Fetching transactions for export for month: {}, date: {}, startDate: {}, endDate: {}, accountId: {}, type: {}, search: {}, categoryId: {}, labelId: {}, hasAttachments: {}",
				month, date, startDate, endDate, accountId, type, search, categoryId, labelId, hasAttachments);
    Specification<AccountTransaction> spec = StringUtils.isNotBlank(categoryId)
                ? buildTransactionSpec(month, date, startDate, endDate, accountId, type, search, categoryId, labelId, false, hasAttachments)
                : buildTransactionSpec(month, date, startDate, endDate, accountId, type, search, null, labelId, true, hasAttachments);
		List<AccountTransaction> list = accountTransactionRepository.findAll(spec,
				Sort.by(Sort.Direction.DESC, "date"));
		logger.info("Total transactions found for export: {}", list.size());
		return accountTransactionMapper.toDTOList(list);
	}

public TransactionPageDTO getFilteredTransactions(Pageable pageable, String month, String date, String startDate, String endDate,
            String accountId, String type, String search, String categoryId, String labelId, Boolean hasAttachments) {
		logger.info("Fetching transactions for month: {}, date: {}, startDate: {}, endDate: {}, accountId: {}, type: {}, search: {}, categoryId: {}, labelId: {}, hasAttachments: {}",
				month, date, startDate, endDate, accountId, type, search, categoryId, labelId, hasAttachments);
        Specification<AccountTransaction> spec = StringUtils.isNotBlank(categoryId)
                ? buildTransactionSpec(month, date, startDate, endDate, accountId, type, search, categoryId, labelId, false, hasAttachments)
                : buildTransactionSpec(month, date, startDate, endDate, accountId, type, search, null, labelId, true, hasAttachments);

		Page<AccountTransaction> page = accountTransactionRepository.findAll(spec, pageable);
		logger.info("Total transactions found: {}", page.getTotalElements());

		List<String> pageIds = page.getContent().stream()
			.map(AccountTransaction::getId)
			.toList();

		// Batch-fetch children
		Map<String, List<AccountTransactionDTO>> childrenByParent = new HashMap<>();
		if (!pageIds.isEmpty()) {
			AppUser currentUser = appUserService.getCurrentUser();
			List<AccountTransaction> allChildren = accountTransactionRepository
				.findByAppUserAndParentIn(currentUser, pageIds);
			for (AccountTransaction child : allChildren) {
				childrenByParent
					.computeIfAbsent(child.getParent(), k -> new ArrayList<>())
					.add(accountTransactionMapper.toDTO(child));
			}
		}

		// Batch-fetch labels — separate query avoids HHH90003004 in-memory pagination
		Map<String, List<AccountTransaction>> labelsByTxId = new HashMap<>();
		if (!pageIds.isEmpty()) {
			List<AccountTransaction> withLabels = accountTransactionRepository.findWithLabelsByIdIn(pageIds);
			for (AccountTransaction tx : withLabels) {
				labelsByTxId.put(tx.getId(), List.of(tx));
			}
		}

		// Batch-fetch attachment counts — single grouped query keeps this O(1)
		// per page instead of N+1. Used by the UI to show a distinct icon for
		// transactions that already have receipts uploaded.
		Map<String, Integer> attachmentCountByTxId = new HashMap<>();
		if (!pageIds.isEmpty()) {
			List<Object[]> rows = attachmentRepository.countByTransactionIdIn(pageIds);
			for (Object[] row : rows) {
				String txId = (String) row[0];
				Long cnt = (Long) row[1];
				attachmentCountByTxId.put(txId, cnt != null ? cnt.intValue() : 0);
			}
		}

		// Batch-fetch transfer split counts — single grouped query for the page
		Map<String, Integer> splitCountByTxId = new HashMap<>();
		Set<String> destTxIds = new java.util.HashSet<>();
		if (!pageIds.isEmpty()) {
			List<Object[]> splitRows = transferSplitRepository.countBySourceTransactionIdIn(pageIds);
			for (Object[] row : splitRows) {
				String txId = (String) row[0];
				Long cnt = (Long) row[1];
				splitCountByTxId.put(txId, cnt != null ? cnt.intValue() : 0);
			}
			destTxIds.addAll(transferSplitRepository.findDestTransactionIdIn(pageIds));
		}

		List<AccountTransactionDTO> formatted = page.getContent().stream()
			    .map(tx -> {
			        List<AccountTransactionDTO> children = childrenByParent.getOrDefault(tx.getId(), List.of());
					List<AccountTransaction> labelSource = labelsByTxId.get(tx.getId());
					List<com.nklmthr.finance.personal.dto.LabelDTO> labels = labelSource != null && !labelSource.isEmpty()
						? labelMapper.toDTOList(labelSource.get(0).getLabels())
						: List.of();
					Integer attachmentCount = attachmentCountByTxId.getOrDefault(tx.getId(), 0);
					Integer splitCount = splitCountByTxId.getOrDefault(tx.getId(), 0);
					boolean isTransferDest = destTxIds.contains(tx.getId());

		        return new AccountTransactionDTO(
		            tx.getId(),
		            tx.getDate(),
		            tx.getAmount(),
		            tx.getDescription(),
		            null,
		            tx.getExplanation(),
		            null,
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
		            labels,
		            attachmentCount,
		            null,
		            splitCount,
		            isTransferDest
		        );
			    })
			    .collect(java.util.stream.Collectors.toList());

		BigDecimal currentTotal = calculateTotalWithSpec(spec).setScale(2, RoundingMode.HALF_UP);
		return new TransactionPageDTO(formatted, page.getTotalPages(), page.getTotalElements(),
				page.getNumber(), page.getSize(), currentTotal);
	}


public BigDecimal getCurrentTotal(String month, String date, String startDate, String endDate, String accountId, String type, String search, String categoryId, String labelId, Boolean hasAttachments) {
        logger.info("Calculating current total for month: {}, date: {}, startDate: {}, endDate: {}, accountId: {}, type: {}, search: {}, categoryId: {}, labelId: {}, hasAttachments: {}",
                month, date, startDate, endDate, accountId, type, search, categoryId, labelId, hasAttachments);
        
        Specification<AccountTransaction> spec = StringUtils.isNotBlank(categoryId)
                ? buildTransactionSpec(month, date, startDate, endDate, accountId, type, search, categoryId, labelId, false, hasAttachments)
                : buildTransactionSpec(month, date, startDate, endDate, accountId, type, search, null, labelId, true, hasAttachments);
        
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
public TransactionPageDTO getFilteredTransactions(Pageable pageable, String month, String accountId,
        String type, String search, String categoryId) {
    return getFilteredTransactions(pageable, month, null, null, null, accountId, type, search, categoryId, null, null);
}

public BigDecimal getCurrentTotal(String month, String accountId, String type, String search, String categoryId) {
    return getCurrentTotal(month, null, null, null, accountId, type, search, categoryId, null, null);
}

public List<AccountTransactionDTO> getFilteredTransactionsForExport(String month, String accountId, String type,
        String categoryId, String search) {
    return getFilteredTransactionsForExport(month, null, null, null, accountId, type, categoryId, null, search, null);
}
}
