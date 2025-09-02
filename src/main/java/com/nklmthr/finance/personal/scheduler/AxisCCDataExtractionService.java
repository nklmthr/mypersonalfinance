package com.nklmthr.finance.personal.scheduler;

import java.math.BigDecimal;
import java.util.List;
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
public class AxisCCDataExtractionService extends AbstractDataExtractionService {

	private static final Logger logger = LoggerFactory.getLogger(AxisCCDataExtractionService.class);

	@Value("${scheduler.enabled}")
	private boolean schedulerEnabled;

	@Scheduled(cron = "${my.scheduler.cron}")
	public void runTask() {
		if (!schedulerEnabled) {
			logger.info("Scheduler is disabled, skipping AxisCCDataExtractionService");
			return;
		}
		super.run();
	}

	public static void main(String[] args) {
		AxisCCDataExtractionService service = new AxisCCDataExtractionService();
		service.run();
	}

	@Override
	protected List<String> getEmailSubject() {
		return List.of("Transaction alert on Axis Bank Credit Card no. XX0434", "spent on credit card no. XX0434",
				"Transaction alert on Axis Bank Credit Card no. XX7002", "Axis Bank Credit Card Transaction Alert");
	}

	@Override
	protected String getSender() {
		return "alerts@axisbank.com";
	}

	protected AccountTransaction extractTransactionData(AccountTransaction tx, String emailContent, AppUser appUser) {
	    try {
	        logger.info("AxisCCDataExtractionService content: {}", emailContent);
	        BigDecimal amount = null;
	        Pattern amountNewPattern = Pattern.compile("Transaction Amount:\\s*INR\\s*([\\d,]+\\.?\\d*)");
	        Matcher amountNewMatcher = amountNewPattern.matcher(emailContent);
	        if (amountNewMatcher.find()) {
	            amount = new BigDecimal(amountNewMatcher.group(1).replace(",", ""));
	        } else {
	            Pattern amountOldPattern = Pattern.compile("for INR ([\\d,]+\\.?\\d*)");
	            Matcher amountOldMatcher = amountOldPattern.matcher(emailContent);
	            if (amountOldMatcher.find()) {
	                amount = new BigDecimal(amountOldMatcher.group(1).replace(",", ""));
	            }
	        }
	        tx.setAmount(amount);
	        String description = null;
	        Pattern merchantNewPattern = Pattern.compile("Merchant Name:\\s*([A-Za-z0-9 &\\-]+)");
	        Matcher merchantNewMatcher = merchantNewPattern.matcher(emailContent);
	        if (merchantNewMatcher.find()) {
	            description = merchantNewMatcher.group(1).trim();
	        } else {
	            Pattern merchantOldPattern = Pattern.compile("at ([A-Za-z0-9 &\\-]+?) on \\d{2}-\\d{2}-\\d{4}");
	            Matcher merchantOldMatcher = merchantOldPattern.matcher(emailContent);
	            if (merchantOldMatcher.find()) {
	                description = merchantOldMatcher.group(1).trim();
	            }
	        }
	        tx.setDescription(description);
	        tx.setType(TransactionType.DEBIT);
	        if (emailContent.contains("0434")) {
	            tx.setAccount(accountService.getAccountByName("AXIS-CCA-Airtel", appUser));
	        } else if (emailContent.contains("7002")) {
	            tx.setAccount(accountService.getAccountByName("Axis-Citi-PremierMiles", appUser));
	        }

	    } catch (Exception e) {
	        logger.error("Error parsing Axis CC transaction", e);
	    }
	    return tx;
	}


}
