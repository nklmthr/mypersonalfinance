package com.nklmthr.finance.personal.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.nklmthr.finance.personal.model.UploadedStatement;
import com.nklmthr.finance.personal.service.UploadedStatementService;

@RestController
@RequestMapping("/api/uploaded-statements")
public class UploadedStatementController {
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(UploadedStatementController.class);
	@Autowired
	private UploadedStatementService statementService;

	// Upload CSV File
	@PostMapping("/upload")
	public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file,
			@RequestParam("accountId") String accountId) {
		try {
			UploadedStatement statement = statementService.upload(file, accountId);
			return ResponseEntity.ok(statement);
		} catch (Exception e) {
			logger.error("Error uploading statement file: {}", file.getOriginalFilename(), e);
			return ResponseEntity.internalServerError().body("Upload failed: " + e.getMessage());
		}
	}

	// List uploaded statements
	@GetMapping
	public List<UploadedStatement> listStatements() {
		logger.info("Listing all uploaded statements");
		return statementService.listStatements();
	}

	@PostMapping("/{id}/process")
	public ResponseEntity<?> process(@PathVariable String id) {
		try {
			logger.info("Processing statement with id: {}", id);
			statementService.process(id);
			return ResponseEntity.ok("Processing complete");
		} catch (Exception e) {
			logger.error("Error processing statement with id: {}", id, e); // ✅ logs full stack trace
			return ResponseEntity.internalServerError().body("Processing failed: " + e.getMessage());
		}
	}

//	@DeleteMapping("/{id}")
//	public ResponseEntity<?> deleteStatement(@PathVariable String id) {
//		logger.info("Attempting to delete statement with id: {}", id);
//
//		try {
//			// Step 1: Delete associated transactions
//			statementService.deleteTransactions(id);
//			logger.info("Deleted transactions for statement id: {}", id);
//
//			// Step 2: Delete the statement itself
//			statementService.delete(id);
//			logger.info("Deleted statement with id: {}", id);
//
//			return ResponseEntity.noContent().build();
//
//		} catch (Exception e) {
//			logger.error("Failed to delete statement or its transactions for id: {}", id, e);
//			return ResponseEntity.internalServerError()
//					.body("Failed to delete statement or transactions: " + e.getMessage());
//		}
//	}
//
//	@DeleteMapping("/{id}/transactions")
//	public ResponseEntity<?> deleteTransactions(@PathVariable String id) {
//		logger.info("Attempting to delete transactions for statement id: {}", id);
//
//		try {
//			statementService.deleteTransactions(id);
//			logger.info("Successfully deleted transactions for statement id: {}", id);
//			return ResponseEntity.ok("Transactions deleted successfully");
//		} catch (Exception e) {
//			logger.error("Error deleting transactions for statement id: {}", id, e);
//			return ResponseEntity.internalServerError().body("Failed to delete transactions: " + e.getMessage());
//		}
//	}

}
