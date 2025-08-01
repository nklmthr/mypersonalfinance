package com.nklmthr.finance.personal.controller;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

import com.nklmthr.finance.personal.dto.SplitTransactionRequest;
import com.nklmthr.finance.personal.dto.TransferRequest;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.Attachment;
import com.nklmthr.finance.personal.service.AccountTransactionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class AccountTransactionController {

	private final AccountTransactionService transactionService;

	@GetMapping
	public Page<AccountTransaction> getTransactions(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size, @RequestParam(required = false) String month,
			@RequestParam(required = false) String accountId, @RequestParam(required = false) String type,
			@RequestParam(required = false) String categoryId, @RequestParam(required = false) String search) {
		return transactionService.getFilteredTransactions(PageRequest.of(page, size, Sort.by("date").descending()),
				month, accountId, type, search, categoryId);
	}

	@GetMapping("/export")
	public List<AccountTransaction> exportTransactions(@RequestParam(required = false) String month,
			@RequestParam(required = false) String accountId, @RequestParam(required = false) String type,
			@RequestParam(required = false) String categoryId, @RequestParam(required = false) String search) {
		return transactionService.getFilteredTransactionsForExport(month, accountId, type, categoryId, search);
	}

	@GetMapping("/{id}")
	public ResponseEntity<AccountTransaction> getById(@PathVariable String id) {
		return transactionService.getById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}

	@GetMapping("/{id}/children")
	public List<AccountTransaction> getChildren(@PathVariable String id) {
		return transactionService.getChildren(id);
	}

	@PostMapping
	public AccountTransaction create(@RequestBody AccountTransaction tx) {
		return transactionService.save(tx);
	}

	@PutMapping("/{id}")
	public ResponseEntity<AccountTransaction> update(@PathVariable String id, @RequestBody AccountTransaction tx) {
		return transactionService.updateTransaction(id, tx).map(ResponseEntity::ok)
				.orElse(ResponseEntity.notFound().build());
	}

	@PostMapping("/transfer")
	public ResponseEntity<?> createTransfer(@RequestBody TransferRequest request) {
		try {
			transactionService.createTransfer(request);
			return ResponseEntity.ok(Map.of("message", "Transfer successful"));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body("Transfer failed: " + e.getMessage());
		}
	}

	@PostMapping("/split")
	public ResponseEntity<String> splitTransaction(@RequestBody List<SplitTransactionRequest> splitTransactions) {
		return transactionService.splitTransaction(splitTransactions);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable String id) {
		transactionService.delete(id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("{id}/attachments/")
	public List<Attachment> getAttachments(@PathVariable String id) {
		return transactionService.getTransactionAttachments(id);
	}

	@PostMapping("/{id}/attachments")
	public Attachment addAttachment(@PathVariable String id, @RequestBody Attachment attachment) {
		return transactionService.addTransactionAttachment(id, attachment);
	}
}
