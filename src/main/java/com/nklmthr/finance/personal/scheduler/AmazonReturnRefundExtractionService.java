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
public class AmazonReturnRefundExtractionService extends AbstractDataExtractionService {
	private static final Logger logger = LoggerFactory.getLogger(AmazonReturnRefundExtractionService.class);

	@Value("${scheduler.enabled}")
	private boolean schedulerEnabled;

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
	    logger.debug("Extracting transaction data from email content: {}", emailContent);
	    if(!emailContent.contains("Your return request is confirmed.")) {
	        logger.info("Email indicates return accepted but no refund processed yet, skipping.");
	        return null;
	    }
	    Pattern amountPattern = Pattern.compile("(?:Refund subtotal|Total estimated refund:|(?:Rs\\.?|₹))\\s*([\\d,]+(?:\\.\\d{1,2})?)");
	    Pattern orderPattern = Pattern.compile("Order\\s*#\\s*([\\d-]+)");
	    Pattern refundModePattern = Pattern.compile("(?:refunded to your|will be refunded to your)\\s+(.*?ending in \\d{4})");
	    Pattern itemPattern = Pattern.compile("Item to be returned:\\s*\\d+\\s+(.*?)\\s+Quantity:", Pattern.DOTALL);

	    Matcher m;

	    m = amountPattern.matcher(emailContent);
	    if (m.find()) {
	        String amt = m.group(1).replace(",", "");
	        accountTransaction.setAmount(new BigDecimal(amt));
	    }

	    m = orderPattern.matcher(emailContent);
	    if (m.find()) {
	        accountTransaction.setDescription(m.group(1).trim());
	    }

	    m = refundModePattern.matcher(emailContent);
	    if (m.find()) {
	        String mode = m.group(1).trim();
	        logger.debug("Detected refund mode: {}", mode);
	        if (mode.contains("9057")) {
	            accountTransaction.setAccount(accountService.getAccountByName("ICICI-CCA-Amazon", appUser));
	        } else {
	            accountTransaction.setAccount(accountService.getAccountByName("AMZN-WLT-Amazon Pay", appUser));
	        }
	        logger.debug("Set account based on refund mode: {}", accountTransaction.getAccount().getName());
	    } else {
	        logger.warn("No refund mode detected, assigning default Amazon Pay account");
	        accountTransaction.setAccount(accountService.getAccountByName("AMZN-WLT-Amazon Pay", appUser));
	    }

	    m = itemPattern.matcher(emailContent);
	    if (m.find()) {
	        String rawItem = m.group(1).trim();
	        String cleanedItem = rawItem.replaceAll("\\(https?://[^)]+\\)", "").trim();
	        cleanedItem = cleanedItem.replaceAll("\\[|\\]", "").trim();
	        accountTransaction.setExplanation(cleanedItem);
	    }

	    accountTransaction.setType(TransactionType.CREDIT);
	    logger.debug("Extracted transaction: {}", accountTransaction);
	    return accountTransaction;
	}


}
