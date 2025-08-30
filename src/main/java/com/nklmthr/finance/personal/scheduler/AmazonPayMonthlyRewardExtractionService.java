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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.enums.TransactionType;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.AppUser;

@Service
public class AmazonPayMonthlyRewardExtractionService extends AbstractDataExtractionService {

	private static final Logger logger = LoggerFactory.getLogger(AmazonPayMonthlyRewardExtractionService.class);

	@Value("${scheduler.enabled}")
	private boolean schedulerEnabled;

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
	    logger.debug("Extracting transaction data from email content: {}", emailContent);
	    Pattern amountPattern = Pattern.compile("Received Amount.*?₹([\\d,]+\\.\\d{2})");
	    Pattern refIdPattern = Pattern.compile("Reference ID\\s*(\\d+)");
	    Pattern expiryPattern = Pattern.compile("Expiry date\\s*([\\d\\w-]+)");
	    Pattern issuerPattern = Pattern.compile("Issued by\\s*(.*?)\\s*(View Statement|$)");
	    Matcher m;
	    m = amountPattern.matcher(emailContent);
	    if (m.find()) {
	        String amt = m.group(1).replace(",", "");
	        try {
	            accountTransaction.setAmount(new BigDecimal(amt));
	        } catch (NumberFormatException e) {
	            logger.warn("Failed to parse amount: {}", amt, e);
	        }
	    }

	    m = refIdPattern.matcher(emailContent);
	    if (m.find()) {
	        accountTransaction.setDescription(m.group(1).trim());
	    }

	    m = expiryPattern.matcher(emailContent);
	    if (m.find()) {
	        String expiryStr = m.group(1);
	        try {
	            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);
	            LocalDate expiry = LocalDate.parse(expiryStr, formatter);
	            accountTransaction.setDescription(accountTransaction.getDescription() + " | Expiry: " + expiry.toString());
	        } catch (Exception e) {
	            logger.warn("Failed to parse expiry date: {}", expiryStr, e);
	        }
	    }

	    m = issuerPattern.matcher(emailContent);
	    if (m.find()) {
	        accountTransaction.setExplanation(m.group(1).trim());
	    }

	    // Common fields
	    accountTransaction.setAppUser(appUser);
	    accountTransaction.setType(TransactionType.CREDIT);
	    accountTransaction.setAccount(accountService.getAccountByName("AMZN-WLT-Amazon Pay", appUser));
	    return accountTransaction;
	}


}
