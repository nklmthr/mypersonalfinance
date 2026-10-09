package com.nklmthr.finance.personal.controller;

import java.util.List;

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

import com.nklmthr.finance.personal.dto.RecurringTransactionDTO;
import com.nklmthr.finance.personal.service.RecurringTransactionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/recurring-transactions")
@RequiredArgsConstructor
@Slf4j
public class RecurringTransactionController {

	private final RecurringTransactionService recurringTransactionService;

	@GetMapping
	public ResponseEntity<List<RecurringTransactionDTO>> getAll() {
		return ResponseEntity.ok(recurringTransactionService.getAll());
	}

	@PostMapping
	public ResponseEntity<?> create(@RequestBody RecurringTransactionDTO dto) {
		try {
			return ResponseEntity.ok(recurringTransactionService.create(dto));
		} catch (Exception e) {
			log.error("Failed to create recurring transaction", e);
			return ResponseEntity.badRequest().body(e.getMessage());
		}
	}

	@PutMapping("/{id}")
	public ResponseEntity<?> update(@PathVariable String id, @RequestBody RecurringTransactionDTO dto) {
		try {
			return ResponseEntity.ok(recurringTransactionService.update(id, dto));
		} catch (Exception e) {
			log.error("Failed to update recurring transaction {}", id, e);
			return ResponseEntity.badRequest().body(e.getMessage());
		}
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<?> delete(@PathVariable String id) {
		try {
			recurringTransactionService.delete(id);
			return ResponseEntity.noContent().build();
		} catch (Exception e) {
			log.error("Failed to delete recurring transaction {}", id, e);
			return ResponseEntity.badRequest().body(e.getMessage());
		}
	}

	@PostMapping("/generate")
	public ResponseEntity<?> generateForMonth(@RequestParam(required = false) String targetMonth) {
		try {
			int count = recurringTransactionService.generateForMonth(targetMonth);
			return ResponseEntity.ok("Generated " + count + " recurring predictions");
		} catch (Exception e) {
			log.error("Failed to generate recurring predictions for month {}", targetMonth, e);
			return ResponseEntity.badRequest().body(e.getMessage());
		}
	}
}
