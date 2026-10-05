package com.nklmthr.finance.personal.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nklmthr.finance.personal.dto.RecurringTransactionDTO;
import com.nklmthr.finance.personal.enums.RecurrenceType;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.model.Category;
import com.nklmthr.finance.personal.model.PredictedTransaction;
import com.nklmthr.finance.personal.model.RecurringTransaction;
import com.nklmthr.finance.personal.repository.CategoryRepository;
import com.nklmthr.finance.personal.repository.PredictedTransactionRepository;
import com.nklmthr.finance.personal.repository.RecurringTransactionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringTransactionService {

	private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("yyyy-MM");

	private final AppUserService appUserService;
	private final RecurringTransactionRepository recurringTransactionRepository;
	private final PredictedTransactionRepository predictedTransactionRepository;
	private final CategoryRepository categoryRepository;

	// ── CRUD ──────────────────────────────────────────────────────────────────

	public List<RecurringTransactionDTO> getAll() {
		AppUser user = appUserService.getCurrentUser();
		return recurringTransactionRepository.findByAppUser(user).stream()
			.map(this::toDTO)
			.collect(Collectors.toList());
	}

	@Transactional
	public RecurringTransactionDTO create(RecurringTransactionDTO dto) {
		AppUser user = appUserService.getCurrentUser();
		Category category = categoryRepository.findById(dto.getCategoryId())
			.orElseThrow(() -> new IllegalArgumentException("Category not found: " + dto.getCategoryId()));

		RecurringTransaction rt = RecurringTransaction.builder()
			.appUser(user)
			.description(dto.getDescription().trim())
			.category(category)
			.amount(dto.getAmount())
			.transactionType(dto.getTransactionType())
			.recurrenceType(dto.getRecurrenceType())
			.activeMonths(resolveActiveMonths(dto.getRecurrenceType(), dto.getActiveMonths()))
			.dayOfMonth(dto.getDayOfMonth() > 0 ? dto.getDayOfMonth() : 1)
			.startYearMonth(dto.getStartYearMonth())
			.endYearMonth(dto.getEndYearMonth())
			.enabled(dto.isEnabled())
			.build();

		return toDTO(recurringTransactionRepository.save(rt));
	}

	@Transactional
	public RecurringTransactionDTO update(String id, RecurringTransactionDTO dto) {
		AppUser user = appUserService.getCurrentUser();
		RecurringTransaction rt = recurringTransactionRepository.findByAppUserAndId(user, id)
			.orElseThrow(() -> new IllegalArgumentException("Recurring transaction not found: " + id));

		Category category = categoryRepository.findById(dto.getCategoryId())
			.orElseThrow(() -> new IllegalArgumentException("Category not found: " + dto.getCategoryId()));

		rt.setDescription(dto.getDescription().trim());
		rt.setCategory(category);
		rt.setAmount(dto.getAmount());
		rt.setTransactionType(dto.getTransactionType());
		rt.setRecurrenceType(dto.getRecurrenceType());
		rt.setActiveMonths(resolveActiveMonths(dto.getRecurrenceType(), dto.getActiveMonths()));
		rt.setDayOfMonth(dto.getDayOfMonth() > 0 ? dto.getDayOfMonth() : 1);
		rt.setStartYearMonth(dto.getStartYearMonth());
		rt.setEndYearMonth(dto.getEndYearMonth());
		rt.setEnabled(dto.isEnabled());

		return toDTO(recurringTransactionRepository.save(rt));
	}

	@Transactional
	public void delete(String id) {
		AppUser user = appUserService.getCurrentUser();
		RecurringTransaction rt = recurringTransactionRepository.findByAppUserAndId(user, id)
			.orElseThrow(() -> new IllegalArgumentException("Recurring transaction not found: " + id));
		predictedTransactionRepository.deleteAll(
			predictedTransactionRepository.findByRecurringTransaction(rt));
		recurringTransactionRepository.delete(rt);
	}

	// ── Prediction generation ─────────────────────────────────────────────────

	/**
	 * Generate PredictedTransaction rows for all enabled recurring transactions
	 * that are active in the given month. Already-existing rows are skipped.
	 */
	@Transactional
	public int generateForMonth(String targetMonthStr) {
		AppUser user = appUserService.getCurrentUser();
		YearMonth targetMonth = (targetMonthStr != null && !targetMonthStr.isEmpty())
			? YearMonth.parse(targetMonthStr, MONTH_FMT)
			: YearMonth.now();
		String monthString = targetMonth.format(MONTH_FMT);

		List<RecurringTransaction> enabled = recurringTransactionRepository.findByAppUserAndEnabledTrue(user);
		int count = 0;
		for (RecurringTransaction rt : enabled) {
			if (!isActiveInMonth(rt, targetMonth)) continue;

			if (predictedTransactionRepository
					.findByRecurringTransactionAndPredictionMonth(rt, monthString)
					.isPresent()) {
				log.debug("Recurring prediction already exists for {} in {}, skipping", rt.getDescription(), monthString);
				continue;
			}

			PredictedTransaction pt = buildPredictedTransaction(user, rt, targetMonth, monthString);
			predictedTransactionRepository.save(pt);
			count++;
		}
		log.info("Generated {} recurring predictions for {}", count, monthString);
		return count;
	}

	// ── Helpers ───────────────────────────────────────────────────────────────

	boolean isActiveInMonth(RecurringTransaction rt, YearMonth targetMonth) {
		String monthString = targetMonth.format(MONTH_FMT);

		// Check start/end bounds
		if (rt.getStartYearMonth() != null && monthString.compareTo(rt.getStartYearMonth()) < 0) return false;
		if (rt.getEndYearMonth() != null && monthString.compareTo(rt.getEndYearMonth()) > 0) return false;

		if (rt.getRecurrenceType() == RecurrenceType.MONTHLY) return true;

		String months = rt.getActiveMonths();
		if (months == null || months.isBlank()) return false;

		int monthValue = targetMonth.getMonthValue();
		return Arrays.stream(months.split(","))
			.map(String::trim)
			.anyMatch(m -> {
				try { return Integer.parseInt(m) == monthValue; }
				catch (NumberFormatException e) { return false; }
			});
	}

	/** Returns canonical activeMonths string for the given recurrence type. */
	private String resolveActiveMonths(RecurrenceType type, String provided) {
		return switch (type) {
			case MONTHLY -> null;
			case QUARTERLY -> "1,4,7,10";
			case HALF_YEARLY -> "1,7";
			case YEARLY, CUSTOM -> (provided != null && !provided.isBlank()) ? provided.trim() : null;
		};
	}

	private PredictedTransaction buildPredictedTransaction(AppUser user, RecurringTransaction rt,
			YearMonth targetMonth, String monthString) {
		String description = String.format("Recurring: %s (%s)",
			rt.getDescription(), targetMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")));

		return PredictedTransaction.builder()
			.appUser(user)
			.predictionRule(null)
			.recurringTransaction(rt)
			.category(rt.getCategory())
			.predictedAmount(rt.getAmount())
			.remainingAmount(rt.getAmount())
			.actualSpent(BigDecimal.ZERO)
			.transactionType(rt.getTransactionType())
			.predictionMonth(monthString)
			.description(description)
			.explanation("Fixed recurring obligation: " + rt.getDescription())
			.currency("INR")
			.calculationDate(LocalDateTime.now())
			.basedOnTransactionCount(0)
			.visible(true)
			.build();
	}

	public RecurringTransactionDTO toDTO(RecurringTransaction rt) {
		return RecurringTransactionDTO.builder()
			.id(rt.getId())
			.description(rt.getDescription())
			.categoryId(rt.getCategory().getId())
			.categoryName(rt.getCategory().getName())
			.amount(rt.getAmount())
			.transactionType(rt.getTransactionType())
			.recurrenceType(rt.getRecurrenceType())
			.activeMonths(rt.getActiveMonths())
			.dayOfMonth(rt.getDayOfMonth())
			.startYearMonth(rt.getStartYearMonth())
			.endYearMonth(rt.getEndYearMonth())
			.enabled(rt.isEnabled())
			.build();
	}

	/** Returns the display-friendly active months string for a recurring transaction. */
	public String describeSchedule(RecurringTransaction rt) {
		return switch (rt.getRecurrenceType()) {
			case MONTHLY -> "Every month";
			case QUARTERLY -> "Quarterly (Jan, Apr, Jul, Oct)";
			case HALF_YEARLY -> "Half-yearly (Jan, Jul)";
			case YEARLY -> "Yearly (month " + rt.getActiveMonths() + ")";
			case CUSTOM -> "Custom: " + rt.getActiveMonths();
		};
	}
}
