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
public class ICICICCDataExtractionServiceImpl extends AbstractDataExtractionService {

	private static final Logger logger = LoggerFactory.getLogger(ICICICCDataExtractionServiceImpl.class);

	@Value("${scheduler.enabled}")
	private boolean schedulerEnabled;

	@Scheduled(cron = "${my.scheduler.cron}")
	public void runTask() {
		if (!schedulerEnabled) {
			logger.info("Scheduler is disabled, skipping ICICICCDataExtractionServiceImpl");
			return;
		}
		super.run();
	}

	public static void main(String[] args) {
		ICICICCDataExtractionServiceImpl impl = new ICICICCDataExtractionServiceImpl();
		impl.run();
	}

	@Override
	protected List<String> getEmailSubject() {
		return List.of("Transaction alert for your ICICI Bank Credit Card");
	}

	@Override
	protected String getSender() {
		return "credit_cards@icicibank.com";
	}

	@Override
	protected AccountTransaction extractTransactionData(AccountTransaction tx, String emailContent, AppUser appUser) {
		Pattern pattern = Pattern.compile(
				"INR ([\\d,]+\\.\\d{2}) on (\\w{3} \\d{2}, \\d{4}) at (\\d{2}:\\d{2}:\\d{2})\\. Info: (.+?)\\.");
		Matcher matcher = pattern.matcher(emailContent);
		if (matcher.find()) {
			try {
				String amountStr = matcher.group(1).replace(",", "");
				String merchant = matcher.group(4).trim();

				BigDecimal amount = new BigDecimal(amountStr);

				tx.setAmount(amount);
				tx.setDescription(merchant);
				tx.setType(TransactionType.DEBIT);
				tx.setAccount(accountService.getAccountByName("ICICI-CCA-Amazon", appUser));
			} catch (Exception e) {
				logger.error("Error parsing transaction data: {}", e.getMessage());
			}
		}
		return tx;
	}

}
