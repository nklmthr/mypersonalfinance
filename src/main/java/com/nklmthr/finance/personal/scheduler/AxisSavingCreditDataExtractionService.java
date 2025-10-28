package com.nklmthr.finance.personal.scheduler;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.dto.AccountDTO;
import com.nklmthr.finance.personal.enums.TransactionType;
import com.nklmthr.finance.personal.model.Account;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.scheduler.util.PatternResult;
import com.nklmthr.finance.personal.scheduler.util.TransactionPatternLibrary;
import com.nklmthr.finance.personal.service.AccountService;
import com.nklmthr.finance.personal.util.AccountFuzzyMatcher;
import com.nklmthr.finance.personal.util.AccountFuzzyMatcher.MatchResult;

@Service
public class AxisSavingCreditDataExtractionService extends AbstractDataExtractionService {

	private static final Logger logger = LoggerFactory.getLogger(AxisSavingCreditDataExtractionService.class);

	@Autowired
	private AccountService accountService;

	@Value("${scheduler.enabled}")
	private boolean schedulerEnabled;
	
	@Autowired
	private AccountFuzzyMatcher accountFuzzyMatcher;

	@Scheduled(cron = "${my.scheduler.cron}")
	public void runTask() {
		if (!schedulerEnabled) {
			logger.info("Scheduler is disabled, skipping AxisSavingCreditDataExtractionService");
			return;
		}
		super.run();
	}

	public static void main(String[] args) {
		AxisSavingCreditDataExtractionService service = new AxisSavingCreditDataExtractionService();
		service.run();
	}

	@Override
	protected List<String> getEmailSubject() {
		return Arrays.asList("Credit transaction alert for Axis Bank A/c", "was credited to your A/c.",
				"Credit notification from Axis Bank");
	}

	@Override
	protected String getSender() {
		return "alerts@axisbank.com";
	}

	@Override
	protected AccountTransaction extractTransactionData(AccountTransaction tx, String emailContent, AppUser appUser) {
		try {
			logger.debug("Parsing Axis Bank credit transaction");

		// Use pattern library for extraction
		PatternResult<BigDecimal> amountResult = TransactionPatternLibrary.extractAmount(emailContent);
		if (amountResult.isPresent()) {
			tx.setAmount(amountResult.getValue());
			logger.debug("Extracted amount using pattern: {}", amountResult.getMatchedPattern());
		}

		PatternResult<String> descriptionResult = TransactionPatternLibrary.extractDescription(emailContent);
		if (descriptionResult.isPresent()) {
			tx.setDescription(descriptionResult.getValue());
			logger.debug("Extracted description using pattern: {}", descriptionResult.getMatchedPattern());
		}

		// Use fuzzy matching to find the best account
		List<AccountDTO> accounts = accountService.getAllAccounts(appUser);
		MatchResult matchResult = accountFuzzyMatcher.findBestMatch(
			accounts,
			emailContent,
			tx.getDescription()
		);
		
		if (!matchResult.isValid()) {
			logger.error("Failed to fuzzy match account for Axis Saving Credit transaction. Email content: {}", emailContent);
			return null;
		}
		
		Account matchedAccount = accountService.getAccountByName(
			matchResult.account().name(), 
			appUser
		);
		tx.setAccount(matchedAccount);
		logger.info("Fuzzy matched account: {} with score {}", matchResult.account().name(), matchResult.score());

		tx.setType(TransactionType.CREDIT);

		} catch (Exception e) {
			logger.error("Failed to parse Axis credit transaction", e);
		}

		return tx;
	}
}
