package com.nklmthr.finance.personal.scheduler;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.enums.TransactionType;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.service.AccountService;

@Service
public class AxisSavingCreditDataExtractionService extends AbstractDataExtractionService {

	private static final Logger logger = LoggerFactory.getLogger(AxisSavingCreditDataExtractionService.class);

	@Autowired
	private AccountService accountService;
	

	public static void main(String[] args) {
		AxisSavingCreditDataExtractionService service = new AxisSavingCreditDataExtractionService();
		service.run();
	}

	@Scheduled(cron = "0 0/2 * * * ?") // Every 30 minutes
	public void runTask() {
		super.run();
	}

	@Override
	protected List<String> getEmailSubject() {
		return Arrays.asList("Credit transaction alert for Axis Bank A/c");
	}

	@Override
	protected String getSender() {
		return "alerts@axisbank.com";
	}

	@Override
	protected AccountTransaction extractTransactionData(AccountTransaction tx, String emailContent, AppUser appUser) {
		try {

	        // Extract amount
	        Pattern amountPattern = Pattern.compile("credited with INR ([\\d,]+\\.\\d{2})");
	        Matcher amountMatcher = amountPattern.matcher(emailContent);
	        if (amountMatcher.find()) {
	            String amountStr = amountMatcher.group(1).replace(",", "");
	            tx.setAmount(new BigDecimal(amountStr));
	        }

	        // Type
	        tx.setType(TransactionType.CREDIT);

	        // Description: use part after "by"
	        Pattern descPattern = Pattern.compile("by ([\\w\\s.\\d]+)\\.");
	        Matcher descMatcher = descPattern.matcher(emailContent);
	        if (descMatcher.find()) {
	            tx.setDescription(descMatcher.group(1).trim());
	        } else {
	            tx.setDescription("Axis Bank Credit");
	        }

	        tx.setAccount(accountService.getAccountByName("Axis Salary Acc", appUser));
	    } catch (Exception e) {
	        logger.error("Failed to parse Axis credit transaction", e);
	    }

	    return tx;
	}

	
}
