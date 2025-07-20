package com.nklmthr.finance.personal.scheduler;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
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
public class AxisSavingDebitDataExtractionService extends AbstractDataExtractionService {

	private static final Logger logger = LoggerFactory.getLogger(AxisSavingDebitDataExtractionService.class);

	@Autowired
	private AccountService accountService;

	@Scheduled(cron = "${my.scheduler.cron}")
	public void runTask() {
		super.run();
	}
	
	// Matches typical debit messages, including ATM debits
	private static final Pattern AMOUNT_PATTERN = Pattern.compile(
			"Amount Debited: INR\\s+([\\d,]+(?:\\.\\d+)?)"
			+ "|INR\\s+([\\d,]+(?:\\.\\d+)?)\\s+has been debited"
			+ "|debited from your A/c no\\. .*? on .*? for INR ([\\d,]+(?:\\.\\d+)?)"
			+ "|debited with INR\\s+([\\d,]+(?:\\.\\d+)?)",
			Pattern.CASE_INSENSITIVE);

	private static final String[] DESCRIPTION_REGEXES = {
			"Transaction Info:\\s*([\\p{L}0-9/\\- ]+?)(?=(?: If | by | at |\\.|$))",
			"by ([\\p{L}0-9 \\-]+?)(?=(?:\\.| If | at |$))",
			"at ([\\p{L}0-9 \\-/]+?)(?=(?:\\.| If |$))",
			"at ([\\p{L}0-9 \\-/]+?)(?= on \\d{2}-\\d{2}-\\d{4})" // for ATM formats
	};


	

	@Override
	protected List<String> getEmailSubject() {
		return Arrays.asList(
				"Debit transaction alert for Axis Bank A/c",
				"was debited from your A/c no. XX2804.",
				"Notification from Axis Bank");
	}

	@Override
	protected String getSender() {
		return "alerts@axisbank.com";
	}

	@Override
	protected AccountTransaction extractTransactionData(AccountTransaction tx, String emailContent, AppUser appUser) {
		extractAmount(emailContent).ifPresent(tx::setAmount);
		tx.setAccount(accountService.getAccountByName("Axis Salary Acc", appUser));
		extractDescription(emailContent).ifPresent(tx::setDescription);
		tx.setType(TransactionType.DEBIT);
		logger.debug("Extracted transaction: {}", tx);
		return tx;
	}

	// ---------------- Helpers ----------------

	private static Optional<BigDecimal> extractAmount(String content) {
		Matcher m = AMOUNT_PATTERN.matcher(content);
		if (m.find()) {
			for (int i = 1; i <= m.groupCount(); i++) {
				String val = m.group(i);
				if (val != null) {
					try {
						return Optional.of(new BigDecimal(val.replace(",", "")));
					} catch (NumberFormatException e) {
						logger.error("Invalid amount '{}'", val, e);
					}
				}
			}
		}
		return Optional.empty();
	}

	private static Optional<String> extractDescription(String content) {
		for (String regex : DESCRIPTION_REGEXES) {
			Optional<String> match = extractGroup(Pattern.compile(regex), content, 1);
			if (match.isPresent() && !match.get().isBlank()) {
				logger.debug("Extracted description: {}", match.get());
				return match;
			}
		}
		return Optional.empty();
	}

	private static Optional<String> extractGroup(Pattern pattern, String input, int groupIndex) {
		Matcher m = pattern.matcher(input);
		if (m.find()) {
			String g = m.group(groupIndex);
			if (g != null && !g.isBlank()) {
				return Optional.of(g.trim());
			}
		}
		return Optional.empty();
	}
}
