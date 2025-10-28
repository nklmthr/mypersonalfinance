package com.nklmthr.finance.personal.scheduler;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
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
public class AmazonPayMonthlyRewardExtractionService extends AbstractDataExtractionService {

	private static final Logger logger = LoggerFactory.getLogger(AmazonPayMonthlyRewardExtractionService.class);

	@Value("${scheduler.enabled}")
	private boolean schedulerEnabled;
	
	@Autowired
	private AccountFuzzyMatcher accountFuzzyMatcher;

	@Scheduled(cron = "${my.scheduler.cron}")
	public void runTask() {
		if (!schedulerEnabled) {
			logger.info("Scheduler is disabled, skipping AmazonPayMonthlyRewardExtractionService");
			return;
		}
		super.run();
	}

	@Override
	protected List<String> getEmailSubject() {
		return List.of("Your monthly reward points for using Amazon Pay ICICI Bank credit card added to your Amazon Pay balance");
	}

	@Override
	protected String getSender() {
		return "no-reply@amazonpay.in";
	}

	@Override
	protected AccountTransaction extractTransactionData(AccountTransaction accountTransaction, String emailContent,
	        AppUser appUser) {
	    try {
	        logger.debug("Extracting Amazon Pay reward transaction");
	        
	        // Use pattern library for amount extraction
	        PatternResult<BigDecimal> amountResult = TransactionPatternLibrary.extractAmount(emailContent);
	        if (amountResult.isPresent()) {
	            accountTransaction.setAmount(amountResult.getValue());
	            logger.debug("Extracted amount using pattern: {}", amountResult.getMatchedPattern());
	        }

	        // Extract reference ID for description
	        Pattern refIdPattern = Pattern.compile("Reference ID\\s*(\\d+)");
	        Matcher refMatcher = refIdPattern.matcher(emailContent);
	        if (refMatcher.find()) {
	            accountTransaction.setDescription(refMatcher.group(1).trim());
	        }

	        // Extract and append expiry date
	        Pattern expiryPattern = Pattern.compile("Expiry date\\s*([\\d\\w-]+)");
	        Matcher expiryMatcher = expiryPattern.matcher(emailContent);
	        if (expiryMatcher.find()) {
	            String expiryStr = expiryMatcher.group(1);
	            try {
	                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);
	                LocalDate expiry = LocalDate.parse(expiryStr, formatter);
	                accountTransaction.setDescription(accountTransaction.getDescription() + " | Expiry: " + expiry.toString());
	            } catch (Exception e) {
	                logger.warn("Failed to parse expiry date: {}", expiryStr, e);
	            }
	        }

	    // Extract issuer for explanation
	    Pattern issuerPattern = Pattern.compile("Issued by\\s*(.*?)\\s*(View Statement|$)");
	    Matcher issuerMatcher = issuerPattern.matcher(emailContent);
	    if (issuerMatcher.find()) {
	        accountTransaction.setExplanation(issuerMatcher.group(1).trim());
	    }

	    // Use fuzzy matching to find the best account
	    List<AccountDTO> accounts = accountService.getAllAccounts(appUser);
	    MatchResult matchResult = accountFuzzyMatcher.findBestMatch(
	        accounts,
	        emailContent,
	        accountTransaction.getDescription()
	    );
	    
	    if (!matchResult.isValid()) {
	        logger.error("Failed to fuzzy match account for Amazon Pay monthly reward transaction. Email content: {}", emailContent);
	        return null;
	    }
	    
	    Account matchedAccount = accountService.getAccountByName(
	        matchResult.account().name(), 
	        appUser
	    );
	    accountTransaction.setAccount(matchedAccount);
	    logger.info("Fuzzy matched account: {} with score {}", matchResult.account().name(), matchResult.score());

	    // Common fields
	    accountTransaction.setAppUser(appUser);
	    accountTransaction.setType(TransactionType.CREDIT);
	    return accountTransaction;
	    } catch (Exception e) {
	        logger.error("Failed to extract Amazon Pay reward transaction", e);
	        return accountTransaction;
	    }
	}


}
