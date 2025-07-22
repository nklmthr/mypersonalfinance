package com.nklmthr.finance.personal.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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

	@Autowired
	private UploadedStatementService statementService;

	// Upload CSV File
	@PostMapping("/upload")
	public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file, @RequestParam("accountId") String accountId) {
		try {
			UploadedStatement statement = statementService.upload(file, accountId);
			return ResponseEntity.ok(statement);
		} catch (Exception e) {
			return ResponseEntity.internalServerError().body("Upload failed: " + e.getMessage());
		}
	}

	// List uploaded statements
	@GetMapping
	public List<UploadedStatement> listStatements() {
		return statementService.listStatements();
	}

	// Process a specific uploaded statement
	@PostMapping("/{id}/process")
	public ResponseEntity<?> process(@PathVariable String id) {
		try {
			statementService.process(id);
			return ResponseEntity.ok("Processing complete");
		} catch (Exception e) {
			return ResponseEntity.internalServerError().body("Processing failed: " + e.getMessage());
		}
	}
	
	@DeleteMapping("/{id}")
	public ResponseEntity<?> deleteStatement(@PathVariable String id) {
		statementService.delete(id);
	    return ResponseEntity.noContent().build();
	}

	// Delete transactions created from a statement
	@DeleteMapping("/{id}/transactions")
	public ResponseEntity<?> deleteTransactions(@PathVariable String id) {
		try {
			statementService.deleteTransactions(id);
			return ResponseEntity.ok("Transactions deleted successfully");
		} catch (Exception e) {
			return ResponseEntity.internalServerError().body("Delete failed: " + e.getMessage());
		}
	}
}
