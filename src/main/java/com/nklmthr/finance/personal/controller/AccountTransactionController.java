package com.nklmthr.finance.personal.controller;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nklmthr.finance.personal.dto.TransferRequest;
import com.nklmthr.finance.personal.enums.TransactionType;
import com.nklmthr.finance.personal.model.Account;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.Category;
import com.nklmthr.finance.personal.service.AccountService;
import com.nklmthr.finance.personal.service.AccountTransactionService;
import com.nklmthr.finance.personal.service.CategoryService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class AccountTransactionController {

	private static final Logger logger=LoggerFactory.getLogger(AccountTransactionController.class);

	private final AccountTransactionService accountTransactionService;

	private final AccountService accountService;

	private final CategoryService categoryService;

	@GetMapping
    public Page<AccountTransaction> getFilteredTransactions(
        @RequestParam int page,
        @RequestParam int size,
        @RequestParam(required = false) String month,
        @RequestParam(required = false) String accountId,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String categoryId
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("date").descending());
        logger.info("Fetching transactions with filters - Month: {}, Account ID: {}, Type: {}, Search: {}", month, accountId, type, search);
        return accountTransactionService.getFilteredTransactions(pageable, month, accountId, type, search, categoryId);
    }

	@GetMapping("/export")
	public List<AccountTransaction> exportTransactions(
	    @RequestParam(required = false) String month,
	    @RequestParam(required = false) String accountId,
	    @RequestParam(required = false) String type,
	    @RequestParam(required = false) String categoryId,
	    @RequestParam(required = false) String search
	) {
	    return accountTransactionService.getFilteredTransactionsForExport(month, accountId, type, categoryId, search);
	}
	
	@GetMapping("/{id}")
	public ResponseEntity<AccountTransaction> getById(@PathVariable String id) {
		return accountTransactionService.getById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}

	@GetMapping("/{id}/children")
	public List<AccountTransaction> getChildren(@PathVariable String id) {
		return accountTransactionService.getChildren(id);
	}

	@PostMapping
	public AccountTransaction create(@RequestBody AccountTransaction tx) {
		return accountTransactionService.save(tx);
	}

	@PutMapping("/{id}")
	public ResponseEntity<AccountTransaction> update(@PathVariable String id, @RequestBody AccountTransaction tx) {
		return accountTransactionService.getById(id).map(existing -> {
			tx.setId(id);
			tx.setAccount(accountService.getAccount(tx.getAccount().getId()));
			tx.setCategory(categoryService.getCategoryById(tx.getCategory().getId()));
			tx.setParent(existing.getParent());
			tx.setChildren(existing.getChildren());
			tx.setSourceId(existing.getSourceId());
			tx.setSourceThreadId(existing.getSourceThreadId());
			tx.setHref(existing.getHref());
			tx.setHrefText(existing.getHrefText());
			tx.setSourceTime(existing.getSourceTime());
			if (tx.getType().equals(TransactionType.DEBIT)) {
				tx.getAccount().setBalance(tx.getAccount().getBalance().subtract(tx.getAmount()));
			} else {
				tx.getAccount().setBalance(tx.getAccount().getBalance().add(tx.getAmount()));
			}
			return ResponseEntity.ok(accountTransactionService.save(tx));
		}).orElse(ResponseEntity.notFound().build());
	}
	
	@PostMapping("/transfer")
    public ResponseEntity<?> createTransfer(@RequestBody TransferRequest request) throws Exception {
		AccountTransaction debit = accountTransactionService.getById(request.getSourceTransactionId()).orElseThrow(() -> new Exception());
		Account fromAccount = accountService.getAccount(debit.getAccount().getId());
		
        Account toAccount = accountService.getAccount(request.getDestinationAccountId());
        debit.setCategory(categoryService.getTransferCategory());

        // Create CREDIT transaction
        AccountTransaction credit = new AccountTransaction();
        credit.setAccount(toAccount);
        credit.setAmount(debit.getAmount());
        credit.setDate(debit.getDate());
        credit.setDescription(debit.getDescription());
        credit.setExplanation(request.getExplanation());
        credit.setType(TransactionType.CREDIT);
        credit.setCategory(categoryService.getTransferCategory());

        accountTransactionService.save(debit);
        accountTransactionService.save(credit);

        return ResponseEntity.ok(Map.of("message", "Transfer successful"));
    }
	@PostMapping("/split")
	public ResponseEntity<AccountTransaction> splitTransaction(@RequestBody AccountTransaction transaction) {
		if (transaction.getParent() == null || transaction.getParent().getId() == null) {
			return ResponseEntity.badRequest().build();
		}
		return ResponseEntity.ok(accountTransactionService.save(transaction));
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable String id) {
		accountTransactionService.delete(id);
		return ResponseEntity.noContent().build();
	}
}
