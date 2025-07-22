package com.nklmthr.finance.personal.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.nklmthr.finance.personal.model.Account;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.model.UploadedStatement;
import com.nklmthr.finance.personal.model.UploadedStatement.Status;
import com.nklmthr.finance.personal.repository.AccountTransactionRepository;
import com.nklmthr.finance.personal.repository.UploadedStatementRepository;

import jakarta.transaction.Transactional;

@Service
public class UploadedStatementService {

	@Autowired
	private UploadedStatementRepository statementRepository;
	@Autowired
	private AccountTransactionRepository transactionRepository;
	@Autowired
	private AppUserService appUserService;

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

		return statementRepository.save(statement);
	}

	public List<UploadedStatement> listStatements() {
		AppUser appUser = appUserService.getCurrentUser();
		return statementRepository.findAllByAppUser(appUser);
	}

	@Transactional
	public void process(String id) throws Exception {
		AppUser appUser = appUserService.getCurrentUser();
		UploadedStatement statement = statementRepository.findByAppUserAndId(appUser, id)
				.orElseThrow(() -> new IllegalArgumentException("Statement not found: " + id));

		if (!"UPLOADED".equals(statement.getStatus())) {
			throw new IllegalStateException("Only uploaded statements can be processed.");
		}

		String[] lines = statement.getContent().split("\n");
		for (String line : lines) {
			if (line.trim().isEmpty())
				continue;

			// Example: assuming CSV format: date,amount,description
			String[] parts = line.split(",");
			if (parts.length < 3)
				continue;

		}

		statement.setStatus(Status.PROCESSED);
		statementRepository.save(statement);
	}

	@Transactional
	public void deleteTransactions(String id) {
		AppUser appUser = appUserService.getCurrentUser();
		UploadedStatement statement = statementRepository.findByAppUserAndId(appUser, id)
				.orElseThrow(() -> new IllegalArgumentException("Statement not found: " + id));

		// List<AccountTransaction> transactions =
		// transactionRepository.findAllByUploadedStatementId(id);
		// transactionRepository.deleteAll(transactions);

		statement.setStatus(Status.UPLOADED); // Reset status after deletion");
		statementRepository.save(statement);
	}

	@Transactional
	public void delete(String id) {
		AppUser appUser = appUserService.getCurrentUser();
		if (!statementRepository.existsByAppUserAndId(appUser, id)) {
			throw new IllegalArgumentException("Statement not found: " + id);
		}
		statementRepository.deleteByAppUserAndId(appUser, id);

	}
}