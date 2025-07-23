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

		return uploadedStatementRepository.save(statement);
	}

	public List<UploadedStatement> listStatements() {
		AppUser appUser = appUserService.getCurrentUser();
		return uploadedStatementRepository.findAllByAppUser(appUser);
	}

	@Transactional
	public void process(String id) {
		AppUser appUser = appUserService.getCurrentUser();

		UploadedStatement statement = uploadedStatementRepository.findByAppUserAndId(appUser, id)
				.orElseThrow(() -> new IllegalArgumentException("Statement not found: " + id));

		if (!Status.UPLOADED.equals(statement.getStatus())) {
			throw new IllegalStateException("Only uploaded statements can be processed.");
		}

		StatementParser parser;
		String accountName = statement.getAccount().getName().toLowerCase();

		if (accountName.contains("sbi")) {
			parser = new SBICsvParser();
		} else {
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

		List<AccountTransaction> transactions = accountTransactionService.getTransactionsByUploadedStatement(statement);
		accountTransactionService.deleteAll(transactions);

		statement.setStatus(Status.UPLOADED); // Reset status after deletion");
		uploadedStatementRepository.save(statement);
	}

	@Transactional
	public void delete(String id) {
		AppUser appUser = appUserService.getCurrentUser();
		if (!uploadedStatementRepository.existsByAppUserAndId(appUser, id)) {
			throw new IllegalArgumentException("Statement not found: " + id);
		}
		uploadedStatementRepository.deleteByAppUserAndId(appUser, id);

	}
}