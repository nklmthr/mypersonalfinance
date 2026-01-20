package com.nklmthr.finance.personal.service;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.nklmthr.finance.personal.model.Account;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.model.UploadedStatement;
import com.nklmthr.finance.personal.model.UploadedStatement.Status;
import com.nklmthr.finance.personal.repository.AccountRepository;
import com.nklmthr.finance.personal.repository.UploadedStatementRepository;
import com.nklmthr.finance.personal.upload.parser.ICICIStatementParserXLS;
import com.nklmthr.finance.personal.upload.parser.SBIStatentParserXLS;
import com.nklmthr.finance.personal.upload.parser.StatementParser;

import jakarta.transaction.Transactional;

@Service
public class UploadedStatementService {
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(UploadedStatementService.class);

	@Autowired
	private UploadedStatementRepository uploadedStatementRepository;
	
	@Autowired
	private AppUserService appUserService;

	@Autowired
	private CategoryService categoryService;

	@Autowired
	private AccountRepository accountRepository;
	
	@Autowired
	private AccountTransactionService accountTransactionService;
	
	@Autowired
	private com.nklmthr.finance.personal.repository.AccountTransactionRepository accountTransactionRepository;

	@Transactional
	public UploadedStatement upload(MultipartFile file, String accountId, String password) throws Exception {
		AppUser appUser = appUserService.getCurrentUser(); // Ensure user context
		Account account = accountRepository.findByAppUserAndId(appUser, accountId).get();
		if (account == null) {
			throw new IllegalArgumentException("Account not found: " + accountId);
		}
		if (file.isEmpty()) {
			throw new IllegalArgumentException("File is empty");
		}
		
		String filename = file.getOriginalFilename();
		String contentType = file.getContentType();
		
		logger.info("Uploading file: {}, contentType: {}, size: {} bytes", 
				filename, contentType, file.getSize());
		
		UploadedStatement statement = new UploadedStatement();
		statement.setAppUser(appUser);
		statement.setFilename(filename);
		statement.setAccount(account);
		statement.setUploadedAt(LocalDateTime.now());
		statement.setStatus(Status.UPLOADED);
		statement.setPassword(password);
		
		// Check file extension to determine if it's binary (Excel) or text (CSV)
		if (filename != null && (filename.toLowerCase().endsWith(".xls") || filename.toLowerCase().endsWith(".xlsx"))) {
			// Binary Excel file - store as byte array
			byte[] binaryContent = file.getBytes();
			statement.setBinaryContent(binaryContent);
			statement.setContent(null); // Explicitly set content to null for Excel files
			
			logger.info("Uploading Excel statement (binary) for account: {} by user: {}, size: {} bytes, first 4 bytes: {}", 
					account.getName(), appUser.getUsername(), binaryContent.length,
					binaryContent.length >= 4 ? String.format("0x%02X%02X%02X%02X", 
							binaryContent[0], binaryContent[1], binaryContent[2], binaryContent[3]) : "N/A");
		} else {
			// Text CSV file - store as string
			String content;
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
				content = reader.lines().collect(Collectors.joining("\n"));
			}
			statement.setContent(content);
			statement.setBinaryContent(null); // Explicitly set binaryContent to null for CSV files
			logger.info("Uploading CSV statement (text) for account: {} by user: {}, size: {} chars", 
					account.getName(), appUser.getUsername(), content.length());
		}
		
		UploadedStatement saved = uploadedStatementRepository.save(statement);
		logger.info("Statement saved with id: {}, binaryContent size: {}, content size: {}", 
				saved.getId(), 
				saved.getBinaryContent() != null ? saved.getBinaryContent().length : 0,
				saved.getContent() != null ? saved.getContent().length() : 0);
		
		return saved;
	}

	public List<UploadedStatement> listStatements() {
		AppUser appUser = appUserService.getCurrentUser();
		logger.info("Listing all uploaded statements for user: {}", appUser.getUsername());
		return uploadedStatementRepository.findAllByAppUser(appUser);
	}

	@Transactional
	public void process(String id) {
		AppUser appUser = appUserService.getCurrentUser();

		UploadedStatement statement = uploadedStatementRepository.findByAppUserAndId(appUser, id)
				.orElseThrow(() -> new IllegalArgumentException("Statement not found: " + id));
		logger.info("Processing statement with id: {} for user: {}", id, appUser.getUsername());
		if (!Status.UPLOADED.equals(statement.getStatus())) {
			logger.error("Statement with id: {} is not in UPLOADED status", id);
			throw new IllegalStateException("Only uploaded statements can be processed.");
		}

		StatementParser parser;
		String accountName = statement.getAccount().getName().toLowerCase();
		logger.info("Determining parser for account: {}", accountName);
		if (accountName.contains("sbi")) {
			logger.info("Using SBIStatentParserXLS for account: {}", accountName);
			parser = new SBIStatentParserXLS();
		} else if (accountName.contains("icici")) {
			logger.info("Using ICICIStatementParserXLS for account: {}", accountName);
			parser = new ICICIStatementParserXLS();
		} else {
			logger.error("No parser implemented for account: {}", accountName);
			throw new UnsupportedOperationException("No parser implemented for account: " + accountName);
		}

		// ✅ Create InputStream from either binary content (Excel) or text content (CSV)
		InputStream inputStream;
		if (statement.getBinaryContent() != null && statement.getBinaryContent().length > 0) {
			// Excel file - use binary content
			inputStream = new ByteArrayInputStream(statement.getBinaryContent());
			logger.info("Processing Excel file from binary content, size: {} bytes", statement.getBinaryContent().length);
		} else if (statement.getContent() != null && !statement.getContent().isEmpty()) {
			// CSV file - use text content
			inputStream = new ByteArrayInputStream(statement.getContent().getBytes(StandardCharsets.UTF_8));
			logger.info("Processing CSV file from text content");
		} else {
			throw new IllegalStateException("Statement has no content to process");
		}
		
		List<AccountTransaction> transactions = parser.parse(inputStream, statement);
		
		int savedCount = 0;
		int duplicateCount = 0;
		
		for (AccountTransaction tx : transactions) {
			tx.setAppUser(appUser); // Associate with current user
			tx.setCategory(categoryService.getNonClassifiedCategory());
			
			// Check for duplicates using statement-specific duplicate detection
			// This checks based on account, explanation (full UPI reference), amount, type, and date
			if (accountTransactionService.isStatementTransactionDuplicate(tx, appUser)) {
				logger.info("Skipping duplicate transaction: date={}, amount={}, explanation={}", 
						tx.getDate(), tx.getAmount(), 
						tx.getExplanation() != null ? tx.getExplanation().substring(0, Math.min(50, tx.getExplanation().length())) : "null");
				duplicateCount++;
			} else {
				accountTransactionService.save(tx, appUser);
				savedCount++;
				logger.info("Saved new transaction: date={}, amount={}, description={}", 
						tx.getDate(), tx.getAmount(), tx.getDescription());
			}
		}
		
		logger.info("Parsed {} transactions from statement {}. Saved: {}, Duplicates skipped: {}", 
				transactions.size(), id, savedCount, duplicateCount);
		
		statement.setStatus(Status.PROCESSED);
		uploadedStatementRepository.save(statement);
		logger.info("Statement {} processed successfully", id);
	}

	@Transactional
	public void unlinkTransactions(String id) {
		AppUser appUser = appUserService.getCurrentUser();
		UploadedStatement statement = uploadedStatementRepository.findByAppUserAndId(appUser, id)
				.orElseThrow(() -> new IllegalArgumentException("Statement not found: " + id));
		
		if (!Status.PROCESSED.equals(statement.getStatus())) {
			logger.error("Statement with id: {} is not in PROCESSED status", id);
			throw new IllegalStateException("Only processed statements can have transactions unlinked.");
		}
		
		logger.info("Unlinking transactions for statement with id: {} for user: {}", id, appUser.getUsername());
		
		// Get all transactions for this statement (using entity list, not DTOs)
		List<AccountTransaction> transactions = accountTransactionRepository.findByAppUserAndUploadedStatement(appUser, statement);
		logger.info("Found {} transactions to unlink for statement {}", transactions.size(), id);
		
		if (transactions.isEmpty()) {
			logger.warn("No transactions found for statement {}", id);
			statement.setStatus(Status.UPLOADED);
			uploadedStatementRepository.save(statement);
			return;
		}
		
		// Calculate net balance changes per account before deletion
		// Group transactions by account and calculate the net change
		java.util.Map<String, java.math.BigDecimal> accountBalanceChanges = new java.util.HashMap<>();
		
		for (AccountTransaction tx : transactions) {
			String accountId = tx.getAccount().getId();
			java.math.BigDecimal change = accountBalanceChanges.getOrDefault(accountId, java.math.BigDecimal.ZERO);
			
			// Reverse the balance change: if it was DEBIT (subtracted), we add it back; if CREDIT (added), we subtract it
			if (tx.getType() == com.nklmthr.finance.personal.enums.TransactionType.DEBIT) {
				change = change.add(tx.getAmount()); // Reverse debit: add back
			} else if (tx.getType() == com.nklmthr.finance.personal.enums.TransactionType.CREDIT) {
				change = change.subtract(tx.getAmount()); // Reverse credit: subtract back
			}
			
			accountBalanceChanges.put(accountId, change);
		}
		
		// Apply balance changes to accounts
		for (java.util.Map.Entry<String, java.math.BigDecimal> entry : accountBalanceChanges.entrySet()) {
			String accountId = entry.getKey();
			java.math.BigDecimal balanceChange = entry.getValue();
			
			Account account = accountRepository.findByAppUserAndId(appUser, accountId)
					.orElseThrow(() -> new IllegalArgumentException("Account not found: " + accountId));
			
			account.setBalance(account.getBalance().add(balanceChange));
			account.setAppUser(appUser);
			accountRepository.save(account);
			
			logger.info("Reversed balance for account {}: change = {}, new balance = {}", 
					account.getName(), balanceChange, account.getBalance());
		}
		
		// FIXED: Delete child transactions first (those with a parent_id), then parent transactions
		// This avoids the foreign key constraint violation
		
		// Get the IDs of all transactions from the statement (these are potential parent transactions)
		List<String> statementTransactionIds = transactions.stream()
				.map(AccountTransaction::getId)
				.collect(Collectors.toList());
		
		logger.info("Statement has {} transactions that could be parent transactions", statementTransactionIds.size());
		
		// Find all child transactions where parent_id is in the statement's transaction IDs
		// These are transactions that were created by splitting the statement's transactions
		List<AccountTransaction> childTransactions = accountTransactionRepository
				.findByAppUserAndParentIn(appUser, statementTransactionIds);
		
		logger.info("Found {} child transactions (splits) that reference statement transactions", childTransactions.size());
		
		// Delete child transactions first (those with parent_id referencing statement transactions)
		if (!childTransactions.isEmpty()) {
			List<String> childTransactionIds = childTransactions.stream()
					.map(AccountTransaction::getId)
					.collect(Collectors.toList());
			
			logger.info("Deleting {} child transactions first", childTransactionIds.size());
			accountTransactionRepository.deleteAllByAppUserAndIdIn(appUser, childTransactionIds);
		}
		
		// Then delete the parent transactions from the statement
		logger.info("Deleting {} parent transactions from statement", statementTransactionIds.size());
		accountTransactionRepository.deleteAllByAppUserAndIdIn(appUser, statementTransactionIds);
		
		logger.info("Unlinked and deleted {} transactions for statement {}", transactions.size(), id);
		statement.setStatus(Status.UPLOADED); // Reset status after unlinking
		logger.info("Resetting status of statement {} to UPLOADED", id);
		uploadedStatementRepository.save(statement);
	}

	@Transactional
	public void delete(String id) {
		AppUser appUser = appUserService.getCurrentUser();
		logger.info("Attempting to delete statement with id: {} for user: {}", id, appUser.getUsername());
		
		if (!uploadedStatementRepository.existsByAppUserAndId(appUser, id)) {
			logger.error("Statement not found with id: {}", id);
			throw new IllegalArgumentException("Statement not found: " + id);
		}
		
		logger.info("Statement found, proceeding with deletion for id: {}", id);
		uploadedStatementRepository.deleteByAppUserAndId(appUser, id);
		logger.info("Delete method called successfully for statement id: {}", id);
	}
}
