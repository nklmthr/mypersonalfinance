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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.dto.AccountTransactionDTO;
import com.nklmthr.finance.personal.dto.SplitTransactionRequest;
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
	AccountTransactionMapper accountTransactionMapper;
	
	@Autowired
	AccountMapper accountMapper;
	
	@Autowired
	CategoryMapper categoryMapper;

	private static final Logger logger = LoggerFactory.getLogger(AccountTransactionService.class);

	@Transactional
	public void createTransfer(TransferRequest request) throws Exception {
		AppUser appUser = appUserService.getCurrentUser();
		logger.info("Creating transfer from transaction ID: {} to account ID: {}", request.getSourceTransactionId(),
				request.getDestinationAccountId());
		AccountTransaction debit = accountTransactionRepository.findByAppUserAndId(appUser, request.getSourceTransactionId())
				.orElseThrow(() -> new IllegalArgumentException(
						"Source transaction not found for user: " + appUser.getUsername()));
		debit.setCategory(categoryService.getTransferCategory());
		accountTransactionRepository.save(debit);
		
		Account toAccount = accountRepository.findByAppUserAndId(appUser, request.getDestinationAccountId()).get();
		toAccount.setBalance(toAccount.getBalance().add(debit.getAmount()));
		
		Account fromAccount = debit.getAccount();
		fromAccount.setBalance(fromAccount.getBalance().subtract(debit.getAmount()));
		
		
		accountRepository.save(fromAccount);
		accountRepository.save(toAccount);
		
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
				
		accountTransactionRepository.save(credit);
	}

	@Transactional
	public ResponseEntity<String> splitTransaction(List<SplitTransactionRequest> splitTransactions) {
		if (splitTransactions == null || splitTransactions.isEmpty()) {
			return ResponseEntity.badRequest().body("No split transactions provided");
		}

		AppUser appUser = appUserService.getCurrentUser();

		String parentId = splitTransactions.get(0).getParentId();
		Optional<AccountTransaction> parentOpt = accountTransactionRepository.findById(parentId);
		if (parentOpt.isEmpty()) {
			return ResponseEntity.badRequest().body("Parent transaction not found");
		}

		AccountTransaction parent = parentOpt.get();

		List<AccountTransaction> children = accountTransactionRepository.findByAppUserAndParent(appUser, parent.getId());
		for (AccountTransaction child : children) {
			parent.setAmount(parent.getAmount().add(child.getAmount()));
			child.setParent(null);
			accountTransactionRepository.delete(child);
		}
		parent.setCategory(categoryService.getSplitTrnsactionCategory());
		BigDecimal parentAmount = parent.getAmount();
		BigDecimal totalSplitAmount = BigDecimal.ZERO;
		for (SplitTransactionRequest st : splitTransactions) {
			AccountTransaction child = new AccountTransaction();
			child.setDescription(st.getDescription());
			child.setAmount(st.getAmount());
			child.setDate(st.getDate());
			child.setType(st.getType());
			child.setAccount(parent.getAccount());
			child.setCategory(categoryService.getCategoryById(st.getCategory().getId()));
			child.setGptAccount(parent.getGptAccount());
			child.setCurrency(parent.getCurrency());
			if (child.getParent() == null) {
				child.setParent(parent.getId());
			}
			child.setAppUser(appUser);
			parent.setAmount(parent.getAmount().subtract(st.getAmount()));
			totalSplitAmount = totalSplitAmount.add(child.getAmount());
			accountTransactionRepository.save(child);
		}

		if (totalSplitAmount.compareTo(parentAmount) != 0) {
			return ResponseEntity.badRequest().body("Parent transaction amount does not match split amounts");
		}

		accountTransactionRepository.save(parent);
		return ResponseEntity.ok("Split successful");
	}

	@Transactional
	public Optional<AccountTransactionDTO> updateTransaction(String id, AccountTransactionDTO txUpdate) {
		return accountTransactionRepository.findById(id).map(existingTx -> {
			AppUser appUser = appUserService.getCurrentUser();

			BigDecimal oldAmount = existingTx.getAmount();
			TransactionType oldType = existingTx.getType();
			Account oldAccount = existingTx.getAccount();
			Account newAccount = accountRepository.findByAppUserAndId(appUser, txUpdate.account().id()).get();
			TransactionType newType = txUpdate.type();
			BigDecimal newAmount = txUpdate.amount();
			AccountTransaction txUpdateEntity = accountTransactionMapper.toEntity(txUpdate);
			txUpdateEntity.setId(id);
			txUpdateEntity.setAppUser(appUser);
			txUpdateEntity.setAccount(newAccount);
			txUpdateEntity.setCategory(categoryService.getCategoryById(txUpdate.category().getId()));
			if (txUpdateEntity.getParent() == null) {
				txUpdateEntity.setParent(existingTx.getParent());
			}
			txUpdateEntity.setSourceId(existingTx.getSourceId());
			txUpdateEntity.setSourceThreadId(existingTx.getSourceThreadId());
			txUpdateEntity.setHref(existingTx.getHref());
			txUpdateEntity.setHrefText(existingTx.getHrefText());
			txUpdateEntity.setSourceTime(existingTx.getSourceTime());
			txUpdateEntity.setGptAccount(existingTx.getGptAccount());
			txUpdateEntity.setGptAmount(existingTx.getGptAmount());
			txUpdateEntity.setGptDescription(existingTx.getGptDescription());
			txUpdateEntity.setGptExplanation(existingTx.getGptExplanation());
			txUpdateEntity.setGptType(existingTx.getGptType());
			txUpdateEntity.setCurrency(existingTx.getCurrency());
			
			if (oldType == TransactionType.DEBIT) {
				oldAccount.setBalance(oldAccount.getBalance().add(oldAmount));
			} else if (oldType == TransactionType.CREDIT) {
				oldAccount.setBalance(oldAccount.getBalance().subtract(oldAmount));
			}

			if (newAccount.getId().equals(oldAccount.getId())) {
				if (newType == TransactionType.DEBIT) {
					newAccount.setBalance(newAccount.getBalance().subtract(newAmount));
				} else if (newType == TransactionType.CREDIT) {
					newAccount.setBalance(newAccount.getBalance().add(newAmount));
				}
			} else {
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
		} else {
			if (transaction.getType().equals(TransactionType.DEBIT)) {
				account.setBalance(account.getBalance().subtract(transaction.getAmount()));
			} else {
				account.setBalance(account.getBalance().add(transaction.getAmount()));
			}
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
		return save(entity, appUser);
	}

	@Transactional
	public void delete(String id) {
		AppUser appUser = appUserService.getCurrentUser();
		logger.info("Deleting transaction ID: {} for user: {}", id, appUser.getUsername());
		AccountTransaction existingTransaction = accountTransactionRepository.findByAppUserAndId(appUser, id)
				.orElseThrow(
						() -> new IllegalArgumentException("Transaction not found for user: " + appUser.getUsername()));
		if (existingTransaction.getParent() != null) {
			AccountTransaction parent = accountTransactionRepository.findByAppUserAndId(appUser, existingTransaction.getParent()).get();
			parent.setAmount(parent.getAmount().add(existingTransaction.getAmount()));
			logger.info("Updated parent transaction ID: {} amount to: {}", parent.getId(), parent.getAmount());
			parent.setDescription(parent.getDescription() + " ||deleted:" + existingTransaction.getDescription()+"|"+existingTransaction.getAmount());
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
		List<AccountTransaction> existingAccTxnList = accountTransactionRepository
				.findByAppUserAndSourceThreadId(appUser, newTransaction.getSourceThreadId());

		if (existingAccTxnList.isEmpty()) {
			newTransaction.setDataVersionId("V2.0");
			return false;
		}

		for (AccountTransaction existingTxn : existingAccTxnList) {
			if (existingTxn.getSourceId() != null && existingTxn.getSourceId().equals(newTransaction.getSourceId())) {
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
			if (isMatch) {
				if (existingTxn.getDataVersionId() == null || !existingTxn.getDataVersionId().equals("V1.1")) {
					updateTransactionWithSourceInfo(existingTxn, newTransaction);
				}
				return true;
			}
		}
		newTransaction.setDataVersionId("V2.0");
		return false;
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

	private Specification<AccountTransaction> buildTransactionSpec(String month, String accountId, String type,
			String search, String categoryId, boolean rootOnly) {

		AppUser appUser = appUserService.getCurrentUser();
		Specification<AccountTransaction> spec = Specification.where(null);

		if (rootOnly) {
			spec = spec.and(AccountTransactionSpecifications.isRootTransaction());
		}

		if (StringUtils.isNotBlank(categoryId)) {
			Set<String> categoryIds = categoryService.getAllDescendantCategoryIds(categoryId);
			spec = spec.and(AccountTransactionSpecifications.hasCategory(categoryIds));
		}
		if (StringUtils.isNotBlank(accountId)) {
			spec = spec.and(AccountTransactionSpecifications.hasAccount(accountId));
		}
		if (StringUtils.isNotBlank(type) && !"ALL".equalsIgnoreCase(type)) {
			spec = spec.and(AccountTransactionSpecifications.hasTransactionType(TransactionType.valueOf(type)));
		}
		if (StringUtils.isNotBlank(month)) {
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
	public List<AccountTransactionDTO> getFilteredTransactionsForExport(String month, String accountId, String type,
			String categoryId, String search) {
		logger.info(
				"Fetching transactions for export for month: {}, accountId: {}, type: {}, search: {}, categoryId: {}",
				month, accountId, type, search, categoryId);
		Specification<AccountTransaction> spec = StringUtils.isNotBlank(categoryId)
				? buildTransactionSpec(month, accountId, type, search, categoryId, true)
				: buildTransactionSpec(month, accountId, type, search, null, false);
		List<AccountTransaction> list = accountTransactionRepository.findAll(spec,
				Sort.by(Sort.Direction.DESC, "date"));
		logger.info("Total transactions found for export: {}", list.size());
		return accountTransactionMapper.toDTOList(list);
	}

	public Page<AccountTransactionDTO> getFilteredTransactions(Pageable pageable, String month, String accountId,
			String type, String search, String categoryId) {
		logger.info("Fetching transactions for month: {}, accountId: {}, type: {}, search: {}, categoryId: {}", month,
				accountId, type, search, categoryId);
		Specification<AccountTransaction> spec = StringUtils.isNotBlank(categoryId)
				? buildTransactionSpec(month, accountId, type, search, categoryId, false)
				: buildTransactionSpec(month, accountId, type, search, null, true);
		
		Page<AccountTransaction> page = accountTransactionRepository.findAll(spec, pageable);
		logger.info("Total transactions found: {}", page.getTotalElements());
		List<AccountTransactionDTO> formatted = page.getContent().stream()
			    .map(tx -> {
			        List<AccountTransactionDTO> children = accountTransactionRepository
			                .findByAppUserAndParent(tx.getAppUser(), tx.getId())
			                .stream()
			                .map(accountTransactionMapper::toDTO)
			                .toList();

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
		            tx.getGptAmount(),
		            tx.getGptDescription(),
		            tx.getGptExplanation(),
		            tx.getGptType(),
		            tx.getCurrency(),
		            accountMapper.toDTO(tx.getGptAccount())
		        );
			    })
			    .toList();
		return new PageImpl<>(formatted, pageable, page.getTotalElements());
	}


	public BigDecimal getCurrentTotal(String month, String accountId, String type, String search, String categoryId) {
		Specification<AccountTransaction> spec = StringUtils.isNotBlank(categoryId)
				? buildTransactionSpec(month, accountId, type, search, categoryId, false)
				: buildTransactionSpec(month, accountId, type, search, null, true);
		Page<AccountTransaction> page = accountTransactionRepository.findAll(spec, Pageable.unpaged());
		logger.info("Calculating current total for month: {}, accountId: {}, type: {}, search: {}, categoryId: {}",
				month, accountId, type, search, categoryId);
		logger.info("Total transactions found: {}", page.getTotalElements());
		BigDecimal total = page.getContent().stream()
				.map(t -> t.getType() == TransactionType.CREDIT ? t.getAmount() : t.getAmount().negate())
				.reduce(BigDecimal.ZERO, BigDecimal::add);
		logger.info("Current total calculated: {}", total);
		return total.setScale(2, RoundingMode.HALF_UP);
	}
}
