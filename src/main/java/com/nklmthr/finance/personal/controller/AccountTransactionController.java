package com.nklmthr.finance.personal.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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

import com.nklmthr.finance.personal.dto.AccountTransactionDTO;
import com.nklmthr.finance.personal.dto.TransferRequest;
import com.nklmthr.finance.personal.service.AccountTransactionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class AccountTransactionController {

	@Autowired
	private AccountTransactionService transactionService;

	private static final Logger logger = LoggerFactory.getLogger(AccountTransactionController.class);

	@GetMapping
	public Page<AccountTransactionDTO> getTransactions(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size, @RequestParam(required = false) String month,
            @RequestParam(required = false) String date,
			@RequestParam(required = false) String accountId, @RequestParam(required = false) String type,
			@RequestParam(required = false) String categoryId, @RequestParam(required = false) String labelId,
			@RequestParam(required = false) String search,
			@RequestParam(required = false) String sortBy,
			@RequestParam(required = false) String sortDir) {
		logger.debug(
                "Fetching transactions - page: {}, size: {}, month: {}, date: {}, accountId: {}, type: {}, categoryId: {}, labelId: {}, search: {}, sortBy: {}, sortDir: {}",
                page, size, month, date, accountId, type, categoryId, labelId, search, sortBy, sortDir);
		
		// Validate sortBy parameter - if not provided, use default date descending
		Sort sort;
		if (sortBy != null && !sortBy.isEmpty()) {
			if ("amount".equalsIgnoreCase(sortBy)) {
				sort = "asc".equalsIgnoreCase(sortDir) 
					? Sort.by("amount").ascending() 
					: Sort.by("amount").descending();
			} else {
				// Default to date sorting
				sort = "asc".equalsIgnoreCase(sortDir) 
					? Sort.by("date").ascending() 
					: Sort.by("date").descending();
			}
		} else {
			// No sort specified - use default date descending
			sort = Sort.by("date").descending();
		}
		
		return transactionService.getFilteredTransactions(PageRequest.of(page, size, sort),
                month, date, accountId, type, search, categoryId, labelId);
	}

    @GetMapping("/currentTotal")
    public BigDecimal getCurrentTotal(@RequestParam(required = false) String month,
            @RequestParam(required = false) String date,
			@RequestParam(required = false) String accountId, @RequestParam(required = false) String type,
			@RequestParam(required = false) String categoryId, @RequestParam(required = false) String labelId,
			@RequestParam(required = false) String search) {
        logger.debug("Calculating current total - month: {}, date: {}, accountId: {}, type: {}, categoryId: {}, labelId: {}, search: {}",
                month, date, accountId, type, categoryId, labelId, search);
        return transactionService.getCurrentTotal(month, date, accountId, type, search, categoryId, labelId);
	}

    @GetMapping("/export")
    public List<AccountTransactionDTO> exportTransactions(@RequestParam(required = false) String month,
            @RequestParam(required = false) String date,
			@RequestParam(required = false) String accountId, @RequestParam(required = false) String type,
			@RequestParam(required = false) String categoryId, @RequestParam(required = false) String labelId,
			@RequestParam(required = false) String search) {
        logger.debug("Exporting transactions - month: {}, date: {}, accountId: {}, type: {}, categoryId: {}, labelId: {}, search: {}",
                month, date, accountId, type, categoryId, labelId, search);
        return transactionService.getFilteredTransactionsForExport(month, date, accountId, type, categoryId, labelId, search);
	}

	@GetMapping("/{id}")
	public ResponseEntity<AccountTransactionDTO> getById(@PathVariable String id) {
		logger.debug("Fetching transaction by ID: {}", id);
		return transactionService.getById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}

	@GetMapping("/{id}/children")
	public List<AccountTransactionDTO> getChildren(@PathVariable String id) {
		logger.debug("Fetching children for transaction ID: {}", id);
		return transactionService.getChildren(id);
	}

	@PostMapping
	public AccountTransactionDTO create(@RequestBody AccountTransactionDTO tx) {
		logger.debug("Creating new transaction: {}", tx);
		return transactionService.save(tx);
	}

	@PutMapping("/{id}")
	public ResponseEntity<?> update(@PathVariable String id,
			@RequestBody AccountTransactionDTO tx) {
		try {
			return transactionService.updateTransaction(id, tx).map(dto -> ResponseEntity.ok((Object) dto))
					.orElse(ResponseEntity.notFound().build());
		} catch (IllegalArgumentException e) {
			logger.warn("Update transaction failed for ID {}: {}", id, e.getMessage());
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		}
	}

	@PostMapping("/transfer")
	public ResponseEntity<?> createTransfer(@RequestBody TransferRequest request) {
		try {
			logger.debug("Creating transfer: {}", request);
			transactionService.createTransfer(request);
			return ResponseEntity.ok(Map.of("message", "Transfer successful"));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body("Transfer failed: " + e.getMessage());
		}
	}

	@PostMapping("/split")
	public ResponseEntity<String> splitTransaction(@RequestBody List<AccountTransactionDTO> splitTransactions) {
		logger.debug("Splitting transaction into: {}", splitTransactions);
		return transactionService.splitTransaction(splitTransactions);
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<?> delete(@PathVariable String id) {
		logger.debug("Deleting transaction with ID: {}", id);
		try {
			transactionService.delete(id);
			return ResponseEntity.noContent().build();
		} catch (IllegalArgumentException e) {
			logger.warn("Delete transaction failed for ID {}: {}", id, e.getMessage());
			return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
		}
	}

//    @GetMapping("{id}/attachments/")
//    public List<AttachmentDTO> getAttachments(@PathVariable String id) {
//        return transactionService.getTransactionAttachments(id);
//    }
//
//    @PostMapping("/{id}/attachments")
//    public AttachmentDTO addAttachment(@PathVariable String id, @RequestBody AttachmentDTO attachment) {
//        return transactionService.addTransactionAttachment(id, attachment);
//    }
}
