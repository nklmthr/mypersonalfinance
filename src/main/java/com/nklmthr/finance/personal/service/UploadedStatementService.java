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
import com.nklmthr.finance.personal.repository.UploadedStatementRepository;
import com.nklmthr.finance.personal.upload.parser.SBICsvParser;
import com.nklmthr.finance.personal.upload.parser.StatementParser;

import jakarta.transaction.Transactional;

@Service
public class UploadedStatementService {
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(UploadedStatementService.class);

	@Autowired
	private UploadedStatementRepository uploadedStatementRepository;
	@Autowired
	private AccountTransactionService accountTransactionService;
	@Autowired
	private AppUserService appUserService;
	
	@Autowired
	private CategoryService categoryService;

	@Autowired
	private AccountService accountService;

	@Transactional
	public UploadedStatement upload(MultipartFile file, String accountId) throws Exception {
		AppUser appUser = appUserService.getCurrentUser(); // Ensure user context
		Account account = accountService.getAccount(accountId);
		if (account == null) {
			throw new IllegalArgumentException("Account not found: " + accountId);
		}
		if (file.isEmpty()) {
			throw new IllegalArgumentException("File is empty");
		}
		String content;
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
			content = reader.lines().collect(Collectors.joining("\n"));
		}
		
		UploadedStatement statement = new UploadedStatement();
		statement.setAppUser(appUser); // associate with current user
		statement.setFilename(file.getOriginalFilename());
		statement.setContent(content);
		statement.setAccount(account);
		statement.setUploadedAt(LocalDateTime.now());
		statement.setStatus(Status.UPLOADED);
		logger.info("Uploading statement for account: {} by user: {}", account.getName(), appUser.getUsername());
		return uploadedStatementRepository.save(statement);
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
			logger.info("Using SBICsvParser for account: {}", accountName);
			parser = new SBICsvParser();
		} else {
			logger.error("No parser implemented for account: {}", accountName);
			throw new UnsupportedOperationException("No parser implemented for account: " + accountName);
		}

		// ✅ Convert String to InputStream and parse
		InputStream inputStream = new ByteArrayInputStream(statement.getContent().getBytes(StandardCharsets.UTF_8));
		List<AccountTransaction> transactions = parser.parse(inputStream, statement);
		for (AccountTransaction tx : transactions) {
			tx.setAppUser(appUser); // Associate with current user
			tx.setCategory(categoryService.getNonClassifiedCategory());
		}
		logger.info("Parsed {} transactions from statement {}", transactions.size(), id);
		accountTransactionService.save(transactions);
		logger.info("Saved {} transactions for statement {}", transactions.size(), id);
		statement.setStatus(Status.PROCESSED);
		uploadedStatementRepository.save(statement);
		logger.info("Statement {} processed successfully", id);
	}

	@Transactional
	public void deleteTransactions(String id) {
		AppUser appUser = appUserService.getCurrentUser();
		UploadedStatement statement = uploadedStatementRepository.findByAppUserAndId(appUser, id)
				.orElseThrow(() -> new IllegalArgumentException("Statement not found: " + id));
		logger.info("Deleting transactions for statement with id: {} for user: {}", id, appUser.getUsername());
		List<AccountTransaction> transactions = accountTransactionService.getTransactionsByUploadedStatement(statement);
		logger.info("Found {} transactions to delete for statement {}", transactions.size(), id);
		accountTransactionService.deleteAll(transactions);
		logger.info("Deleted {} transactions for statement {}", transactions.size(), id);
		statement.setStatus(Status.UPLOADED); // Reset status after deletion");
		logger.info("Resetting status of statement {} to UPLOADED", id);
		uploadedStatementRepository.save(statement);
	}

	@Transactional
	public void delete(String id) {
		AppUser appUser = appUserService.getCurrentUser();
		if (!uploadedStatementRepository.existsByAppUserAndId(appUser, id)) {
			logger.error("Statement not found with id: {}", id);
			throw new IllegalArgumentException("Statement not found: " + id);
		}
		logger.info("Deleting statement with id: {} for user: {}", id, appUser.getUsername());
		uploadedStatementRepository.deleteByAppUserAndId(appUser, id);

	}
}