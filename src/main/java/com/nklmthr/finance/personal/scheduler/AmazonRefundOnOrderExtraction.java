package com.nklmthr.finance.personal.scheduler;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
import com.nklmthr.finance.personal.util.AccountFuzzyMatcher;
import com.nklmthr.finance.personal.util.AccountFuzzyMatcher.MatchResult;

@Service
public class AmazonRefundOnOrderExtraction extends AbstractDataExtractionService {

	private static final Logger logger = LoggerFactory.getLogger(AmazonRefundOnOrderExtraction.class);

	@Value("${scheduler.enabled}")
	private boolean schedulerEnabled;
	
	@Autowired
	private AccountFuzzyMatcher accountFuzzyMatcher;

	@Scheduled(cron = "${my.scheduler.cron}")
	public void runTask() {
		if (!schedulerEnabled) {
			logger.info("Scheduler is disabled, skipping AmazonRefundOnOrderExtraction");
			return;
		}
		super.run();
	}

	@Override
	protected List<String> getEmailSubject() {
		return List.of("Refund on order ");
	}

	@Override
	protected String getSender() {
		return "payments-messages@amazon.in";
	}

	@Override
	protected AccountTransaction extractTransactionData(AccountTransaction accountTransaction, String emailContent,
			AppUser appUser) {
		try {
			logger.debug("Extracting Amazon refund transaction");

			// Use pattern library for amount extraction
			PatternResult<BigDecimal> amountResult = TransactionPatternLibrary.extractAmount(emailContent);
			if (amountResult.isPresent()) {
				accountTransaction.setAmount(amountResult.getValue());
				logger.debug("Extracted amount using pattern: {}", amountResult.getMatchedPattern());
			}

			// Extract Order ID for description
			Pattern orderIdPattern = Pattern.compile("Order #\\s*([\\d-]+)");
			Matcher orderMatcher = orderIdPattern.matcher(emailContent);
			if (orderMatcher.find()) {
				accountTransaction.setDescription(orderMatcher.group(1));
			}

			// Extract Item Name
			Pattern itemPattern = Pattern.compile("Item:\\s*(.*?)Quantity:", Pattern.DOTALL);
			Matcher itemMatcher = itemPattern.matcher(emailContent);
			if (itemMatcher.find()) {
				accountTransaction.setExplanation("Refund for: " + itemMatcher.group(1).trim());
			}

			// Extract Quantity
			Pattern qtyPattern = Pattern.compile("Quantity:\\s*(\\d+)");
			Matcher qtyMatcher = qtyPattern.matcher(emailContent);
			if (qtyMatcher.find()) {
				accountTransaction.setExplanation(accountTransaction.getExplanation() + " | Quantity: " + qtyMatcher.group(1));
			}

		// Use fuzzy matching to find the best account
		List<AccountDTO> accounts = accountService.getAllAccounts(appUser);
		MatchResult matchResult = accountFuzzyMatcher.findBestMatch(
			accounts,
			emailContent,
			accountTransaction.getDescription()
		);
		
		if (!matchResult.isValid()) {
			logger.error("Failed to fuzzy match account for Amazon refund on order transaction. Email content: {}", emailContent);
			return null;
		}
		
		Account matchedAccount = accountService.getAccountByName(
			matchResult.account().name(), 
			appUser
		);
		accountTransaction.setAccount(matchedAccount);
		logger.info("Fuzzy matched account: {} with score {}", matchResult.account().name(), matchResult.score());

		accountTransaction.setType(TransactionType.CREDIT);
			return accountTransaction;
		} catch (Exception e) {
			logger.error("Failed to extract Amazon refund transaction", e);
			return accountTransaction;
		}
	}

}
