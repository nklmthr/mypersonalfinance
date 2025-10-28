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
public class AmazonReturnRefundExtractionService extends AbstractDataExtractionService {
	private static final Logger logger = LoggerFactory.getLogger(AmazonReturnRefundExtractionService.class);

	@Value("${scheduler.enabled}")
	private boolean schedulerEnabled;
	
	@Autowired
	private AccountFuzzyMatcher accountFuzzyMatcher;

	@Scheduled(cron = "${my.scheduler.cron}")
	public void runTask() {
		if (!schedulerEnabled) {
			logger.info("Scheduler is disabled, skipping AmazonReturnRefundExtractionService");
			return;
		}
		super.run();
	}

	@Override
	protected List<String> getEmailSubject() {
		return List.of("Your refund for ", "Your return of");
	}

	@Override
	protected String getSender() {
		return "return@amazon.in";
	}

	@Override
	protected AccountTransaction extractTransactionData(AccountTransaction accountTransaction, String emailContent,
	                                                    AppUser appUser) {
	    try {
	        logger.debug("Extracting Amazon return refund transaction");
	        
	        // Check if refund is actually processed/initiated
	        boolean isRefundProcessed = emailContent.contains("issued your refund") ||
	                                   emailContent.contains("is now initiated") ||
	                                   emailContent.contains("Total refund") ||
	                                   emailContent.contains("Your return request is confirmed.");
	        
	        if (!isRefundProcessed) {
	            logger.info("Email indicates return accepted but no refund processed yet, skipping.");
	            return null;
	        }

	        // Use pattern library for amount extraction
	        PatternResult<BigDecimal> amountResult = TransactionPatternLibrary.extractAmount(emailContent);
	        if (amountResult.isPresent()) {
	            accountTransaction.setAmount(amountResult.getValue());
	            logger.debug("Extracted amount using pattern: {}", amountResult.getMatchedPattern());
	        }

	        // Extract Order ID
	        Pattern orderPattern = Pattern.compile("Order\\s*#\\s*([\\d-]+)");
	        Matcher orderMatcher = orderPattern.matcher(emailContent);
	        if (orderMatcher.find()) {
	            accountTransaction.setDescription(orderMatcher.group(1).trim());
	        }

	    // Use fuzzy matching to find the best account
	    List<AccountDTO> accounts = accountService.getAllAccounts(appUser);
	    MatchResult matchResult = accountFuzzyMatcher.findBestMatch(
	        accounts,
	        emailContent,
	        accountTransaction.getDescription()
	    );
	    
	    if (!matchResult.isValid()) {
	        logger.error("Failed to fuzzy match account for Amazon return refund transaction. Email content: {}", emailContent);
	        return null;
	    }
	    
	    Account matchedAccount = accountService.getAccountByName(
	        matchResult.account().name(), 
	        appUser
	    );
	    accountTransaction.setAccount(matchedAccount);
	    logger.info("Fuzzy matched account: {} with score {}", matchResult.account().name(), matchResult.score());

	        // Extract Item Name - supports both "Item returned:" and "Item to be returned:"
	        Pattern itemPattern = Pattern.compile("Item (?:to be )?returned:\\s*\\d+\\s+(?:\\(https?://[^)]+\\))?\\s*\\[?([^\\]\\n]+?)\\]?\\s+Quantity:", Pattern.DOTALL);
	        Matcher itemMatcher = itemPattern.matcher(emailContent);
	        if (itemMatcher.find()) {
	            String rawItem = itemMatcher.group(1).trim();
	            String cleanedItem = rawItem.replaceAll("\\(https?://[^)]+\\)", "").trim();
	            cleanedItem = cleanedItem.replaceAll("\\[|\\]", "").trim();
	            accountTransaction.setExplanation(cleanedItem);
	        }

	        accountTransaction.setType(TransactionType.CREDIT);
	        logger.debug("Extracted transaction: {}", accountTransaction);
	        return accountTransaction;
	    } catch (Exception e) {
	        logger.error("Failed to extract Amazon return refund transaction", e);
	        return accountTransaction;
	    }
	}


}
