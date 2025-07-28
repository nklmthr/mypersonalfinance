package com.nklmthr.finance.personal.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.dto.BalanceSheetDTO;
import com.nklmthr.finance.personal.dto.BalanceSheetMonthlyDTO;
import com.nklmthr.finance.personal.model.Account;
import com.nklmthr.finance.personal.model.AccountBalanceSnapshot;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.repository.AccountBalanceSnapshotRepository;

@Service
public class BalanceSheetService {

	@Autowired
	private AppUserService appUserService;

	@Autowired
	private AccountBalanceSnapshotRepository accountBalanceSnapshotRepository;
	
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(BalanceSheetService.class);

	public List<BalanceSheetDTO> generateBalanceSheet(int year) {
		List<BalanceSheetDTO> result = new ArrayList<>();
		int currentYear = LocalDate.now().getYear();

		if (year == currentYear) {
			// Rolling last 12 months including current month
			logger.info("Generating balance sheet for the last 12 months including current month");
			for (int i = 0; i <= 11; i++) {
				LocalDate targetMonth = LocalDate.now().minusMonths(i).withDayOfMonth(1);
				BalanceSheetDTO dto = generateMonthlyBalanceSheet(targetMonth);
				result.add(dto);
			}
		} else {
			// All 12 months of given year
			logger.info("Generating balance sheet for all 12 months of year: " + year);
			for (int month = 12; month > 0; month--) {
				LocalDate targetMonth = LocalDate.of(year, month, 1);
				BalanceSheetDTO dto = generateMonthlyBalanceSheet(targetMonth);
				result.add(dto);
			}
		}

		return result;
	}

	public BalanceSheetDTO generateMonthlyBalanceSheet(LocalDate date) {
		AppUser appUser = appUserService.getCurrentUser();

		String monthLabel = formatMonth(date);

		LocalDate fromDate = date.minusDays(7);
		LocalDate toDate = date.plusDays(7);
		logger.info("Generating balance sheet for user: " + appUser.getUsername() + " for month: " + monthLabel
				+ " from: " + fromDate + " to: " + toDate);
		List<AccountBalanceSnapshot> snapshots = accountBalanceSnapshotRepository.findByAppUserAndSnapshotRange(appUser,
				fromDate.atStartOfDay(), toDate.atStartOfDay());

		Map<String, BigDecimal> classificationTotals = new LinkedHashMap<>();
		BigDecimal total = BigDecimal.ZERO;

		for (AccountBalanceSnapshot snapshot : snapshots) {
			Account account = snapshot.getAccount();
			if (account.getAccountType() == null || account.getAccountType().getClassification() == null) {
				logger.warn("Account or AccountType classification is null for account: " + account.getName());
				continue;
			}

			String classification = account.getAccountType().getClassification();
			BigDecimal balance = snapshot.getBalance() != null ? snapshot.getBalance() : BigDecimal.ZERO;

			classificationTotals.merge(classification, balance, BigDecimal::add);
			total = total.add(balance);
		}

		List<BalanceSheetMonthlyDTO> rows = classificationTotals.entrySet().stream()
				.map(e -> new BalanceSheetMonthlyDTO(e.getKey(), Map.of(monthLabel, e.getValue())))
				.collect(Collectors.toList());

		Map<String, BigDecimal> summaryRow = Map.of(monthLabel, total);
		logger.info("Generated balance sheet for month: " + monthLabel + " with total: " + total);
		return new BalanceSheetDTO(rows, summaryRow);
	}

	private String formatMonth(LocalDate date) {
		return date.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH));
	}
}
