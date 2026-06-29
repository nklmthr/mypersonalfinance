package com.nklmthr.finance.personal.upload.parser;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.nklmthr.finance.personal.enums.TransactionType;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.UploadedStatement;

public class AmazonPayStatementParser extends StatementParser {
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(AmazonPayStatementParser.class);
	private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy hh:mm a");

	@Override
	protected List<AccountTransaction> mapTransactions(List<String[]> rows, UploadedStatement statement) {
		List<AccountTransaction> transactions = new ArrayList<>();

		for (String[] row : rows) {
			// Skip header row
			if (row[0].equalsIgnoreCase("Date")) {
				continue;
			}

			try {
				// Columns: Date, Time, Description, Type, Amount (INR)
				String dateStr = row[0].trim();
				String timeStr = row[1].trim();
				String description = row[2].trim();
				String typeStr = row[3].trim();
				String amountStr = row[4].trim().replaceAll("[^0-9.]", "");

				LocalDateTime date = LocalDateTime.parse(dateStr + " " + timeStr, DATETIME_FORMATTER);
				BigDecimal amount = new BigDecimal(amountStr);
				TransactionType type = typeStr.equalsIgnoreCase("Credit") ? TransactionType.CREDIT : TransactionType.DEBIT;

				AccountTransaction tx = new AccountTransaction();
				tx.setDate(date);
				tx.setAmount(amount);
				tx.setType(type);
				tx.setDescription(description);
				tx.setExplanation(description);
				tx.setAccount(statement.getAccount());
				tx.setUploadedStatement(statement);

				transactions.add(tx);
				logger.info("Parsed transaction: date={}, amount={}, type={}, description={}", date, amount, type, description);

			} catch (Exception e) {
				logger.error("Error parsing row: {}", String.join(",", row), e);
			}
		}

		return transactions;
	}
}
