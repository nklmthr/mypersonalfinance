package com.nklmthr.finance.personal.upload.parser;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.apache.poi.ss.usermodel.*;

import com.nklmthr.finance.personal.enums.TransactionType;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.UploadedStatement;

public class SBIStatentParserXLS extends StatementParser {
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SBIStatentParserXLS.class);
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
	private static final int DATA_START_ROW_INDEX = 21; // Row 22 (0-indexed) is where data starts

	@Override
	public List<AccountTransaction> parse(InputStream inputStream, UploadedStatement statement) {
		List<AccountTransaction> transactions = new ArrayList<>();
		
		try (Workbook workbook = createWorkbook(inputStream, statement.getPassword())) {
			Sheet sheet = workbook.getSheetAt(0); // Get first sheet
			
			// Start reading from row 22 (index 21) onwards
			for (int rowIndex = DATA_START_ROW_INDEX; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
				Row row = sheet.getRow(rowIndex);
				if (row == null) {
					continue;
				}
				
				// Check if we've reached the summary/footer section and stop processing
				if (isFooterRow(row)) {
					logger.info("Reached statement footer/summary section at row {}, stopping transaction parsing", rowIndex);
					break;
				}
				
				AccountTransaction tx = parseRow(row, statement);
				if (tx != null) {
					transactions.add(tx);
					logger.info("Parsed transaction: date={}, amount={}, type={}, description={}", 
							tx.getDate(), tx.getAmount(), tx.getType(), tx.getDescription());
				}
			}
			
		} catch (IOException e) {
			logger.error("Error reading Excel file", e);
			throw new RuntimeException("Failed to read Excel file", e);
		}
		
		return transactions;
	}

	private Workbook createWorkbook(InputStream inputStream, String password) throws IOException {
		try {
			if (password != null && !password.isBlank()) {
				return WorkbookFactory.create(inputStream, password);
			} else {
				return WorkbookFactory.create(inputStream);
			}
		} catch (Exception e) {
			logger.error("Failed to create workbook from input stream", e);
			throw new IOException("Unable to read Excel file. File may be corrupted, encrypted or in unsupported format.", e);
		}
	}

	private AccountTransaction parseRow(Row row, UploadedStatement statement) {
		try {
			// Column A (index 0): Txn Date
			Cell txnDateCell = row.getCell(0);
			if (txnDateCell == null || getCellValueAsString(txnDateCell).isBlank()) {
				return null; // Skip empty rows
			}
			
			String txnDateStr = getCellValueAsString(txnDateCell).trim();
			LocalDateTime date = parseDate(txnDateStr);
			if (date == null) {
				logger.warn("Skipping row due to invalid date: {}", txnDateStr);
				return null;
			}
			
			// Column C (index 2): Description
			Cell descCell = row.getCell(2);
			String descPart = descCell != null ? getCellValueAsString(descCell).trim() : "";
			
			// Column D (index 3): Ref No./Cheque No.
			Cell refNoCell = row.getCell(3);
			String refNoPart = refNoCell != null ? getCellValueAsString(refNoCell).trim() : "";
			
			// Concatenate Description and Ref No. for the full transaction description
			// This ensures UPI transaction IDs and unique identifiers are included
			String description = buildDescription(descPart, refNoPart);
			
			// Column E (index 4): Debit
			Cell debitCell = row.getCell(4);
			String debitStr = debitCell != null ? getCellValueAsString(debitCell).replace(",", "").trim() : "";
			
			// Column F (index 5): Credit
			Cell creditCell = row.getCell(5);
			String creditStr = creditCell != null ? getCellValueAsString(creditCell).replace(",", "").trim() : "";
			
			// Determine amount and transaction type
			BigDecimal amount;
			boolean isCredit;
			
			if (!debitStr.isEmpty() && !debitStr.equals("-")) {
				amount = new BigDecimal(debitStr);
				isCredit = false;
			} else if (!creditStr.isEmpty() && !creditStr.equals("-")) {
				amount = new BigDecimal(creditStr);
				isCredit = true;
			} else {
				logger.warn("Skipping row with no valid debit or credit value");
				return null;
			}
			
			// Create transaction
			AccountTransaction tx = new AccountTransaction();
			tx.setDate(date);
			tx.setAmount(amount);
			tx.setType(isCredit ? TransactionType.CREDIT : TransactionType.DEBIT);
			tx.setDescription(description);
			tx.setExplanation(null); // Set explanation to null as it's now part of description
			tx.setAccount(statement.getAccount());
			tx.setUploadedStatement(statement);
			
			return tx;
			
		} catch (Exception e) {
			logger.error("Error parsing row {}", row.getRowNum(), e);
			return null;
		}
	}
	
	/**
	 * Builds the transaction description by concatenating Description and Ref No./Cheque No.
	 * This ensures unique identifiers like UPI transaction IDs are included for deduplication.
	 * 
	 * @param descPart Description from Column C
	 * @param refNoPart Ref No./Cheque No. from Column D
	 * @return Concatenated description
	 */
	private String buildDescription(String descPart, String refNoPart) {
		StringBuilder description = new StringBuilder();
		
		if (descPart != null && !descPart.isEmpty()) {
			description.append(descPart);
		}
		
		if (refNoPart != null && !refNoPart.isEmpty()) {
			if (description.length() > 0) {
				description.append(" - ");
			}
			description.append(refNoPart);
		}
		
		return description.toString();
	}

	private LocalDateTime parseDate(String dateStr) {
		try {
			LocalDate datePart = LocalDate.parse(dateStr, DATE_FORMATTER);
			return datePart.atStartOfDay();
		} catch (Exception e) {
			logger.error("Error parsing date: {}", dateStr, e);
			return null;
		}
	}

	private String getCellValueAsString(Cell cell) {
		if (cell == null) {
			return "";
		}
		
		switch (cell.getCellType()) {
			case STRING:
				return cell.getStringCellValue();
			case NUMERIC:
				if (DateUtil.isCellDateFormatted(cell)) {
					// Format date cells
					LocalDateTime date = cell.getLocalDateTimeCellValue();
					return date.format(DATE_FORMATTER);
				} else {
					// Format numeric cells
					double numericValue = cell.getNumericCellValue();
					// Remove decimal if it's a whole number
					if (numericValue == Math.floor(numericValue)) {
						return String.valueOf((long) numericValue);
					}
					return String.valueOf(numericValue);
				}
			case BOOLEAN:
				return String.valueOf(cell.getBooleanCellValue());
			case FORMULA:
				// Evaluate formula and get the result
				FormulaEvaluator evaluator = cell.getSheet().getWorkbook().getCreationHelper().createFormulaEvaluator();
				CellValue cellValue = evaluator.evaluate(cell);
				switch (cellValue.getCellType()) {
					case STRING:
						return cellValue.getStringValue();
					case NUMERIC:
						double numVal = cellValue.getNumberValue();
						if (numVal == Math.floor(numVal)) {
							return String.valueOf((long) numVal);
						}
						return String.valueOf(numVal);
					case BOOLEAN:
						return String.valueOf(cellValue.getBooleanValue());
					default:
						return "";
				}
			case BLANK:
			case ERROR:
			default:
				return "";
		}
	}
	
	/**
	 * Checks if a row is part of the statement footer/summary section.
	 * Footer rows typically contain summary information like "Statement Summary",
	 * "Brought Forward", disclaimer text, etc.
	 * 
	 * @param row The row to check
	 * @return true if this is a footer/summary row, false otherwise
	 */
	private boolean isFooterRow(Row row) {
		// Check the first column (Date column) for footer indicators
		Cell firstCell = row.getCell(0);
		if (firstCell == null) {
			return false;
		}
		
		String cellValue = getCellValueAsString(firstCell).trim().toLowerCase();
		
		// Common footer row indicators in SBI statements
		return cellValue.contains("statement summary") ||
		       cellValue.contains("brought forward") ||
		       cellValue.contains("please do not share") ||
		       cellValue.contains("this is a computer generated");
	}

	@Override
	protected List<AccountTransaction> mapTransactions(List<String[]> rows, UploadedStatement statement) {
		// This method is not used for XLS parsing, but required by parent class
		throw new UnsupportedOperationException("CSV parsing not supported for XLS files");
	}
}
