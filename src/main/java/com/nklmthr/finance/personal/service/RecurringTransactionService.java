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
import com.nklmthr.finance.personal.repository.PredictionActualTxnMappingRepository;
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
	private final PredictionActualTxnMappingRepository actualMappingRepository;
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

		List<RecurringTransaction> existing = recurringTransactionRepository.findByAppUserAndCategory(user, category);
		if (!existing.isEmpty()) {
			String resolvedMonths = resolveActiveMonths(dto.getRecurrenceType(), dto.getActiveMonths());
			RecurringTransaction incoming = RecurringTransaction.builder()
				.recurrenceType(dto.getRecurrenceType())
				.activeMonths(resolvedMonths)
				.startYearMonth(dto.getStartYearMonth())
				.endYearMonth(dto.getEndYearMonth())
				.enabled(dto.isEnabled())
				.build();
			for (RecurringTransaction ex : existing) {
				Set<Integer> collision = overlappingMonths(incoming, ex);
				if (!collision.isEmpty()) {
					throw new IllegalArgumentException(
						"Collision with existing recurring transaction \"" + ex.getDescription() + "\": " +
						"both would fire in month(s) " + collision + " for category \"" + category.getName() + "\". " +
						"Use sub-categories to track multiple obligations under the same category.");
				}
			}
		}

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

		List<RecurringTransaction> existing = recurringTransactionRepository.findByAppUserAndCategory(user, category);
		List<RecurringTransaction> others = existing.stream().filter(e -> !e.getId().equals(id)).collect(Collectors.toList());
		if (!others.isEmpty()) {
			String resolvedMonths = resolveActiveMonths(dto.getRecurrenceType(), dto.getActiveMonths());
			RecurringTransaction incoming = RecurringTransaction.builder()
				.recurrenceType(dto.getRecurrenceType())
				.activeMonths(resolvedMonths)
				.startYearMonth(dto.getStartYearMonth())
				.endYearMonth(dto.getEndYearMonth())
				.enabled(dto.isEnabled())
				.build();
			for (RecurringTransaction ex : others) {
				Set<Integer> collision = overlappingMonths(incoming, ex);
				if (!collision.isEmpty()) {
					throw new IllegalArgumentException(
						"Collision with existing recurring transaction \"" + ex.getDescription() + "\": " +
						"both would fire in month(s) " + collision + " for category \"" + category.getName() + "\". " +
						"Use sub-categories to track multiple obligations under the same category.");
				}
			}
		}

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
		List<PredictedTransaction> predictions = predictedTransactionRepository.findByRecurringTransaction(rt);
		if (!predictions.isEmpty()) {
			actualMappingRepository.deleteByPredictedTransactionIn(predictions);
		}
		predictedTransactionRepository.deleteAll(predictions);
		recurringTransactionRepository.delete(rt);
	}

	@Transactional
	public int clearPredictions(String id) {
		AppUser user = appUserService.getCurrentUser();
		RecurringTransaction rt = recurringTransactionRepository.findByAppUserAndId(user, id)
			.orElseThrow(() -> new IllegalArgumentException("Recurring transaction not found: " + id));
		List<PredictedTransaction> predictions = predictedTransactionRepository.findByRecurringTransaction(rt);
		if (!predictions.isEmpty()) {
			actualMappingRepository.deleteByPredictedTransactionIn(predictions);
		}
		predictedTransactionRepository.deleteAll(predictions);
		return predictions.size();
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

	/**
	 * Returns the set of calendar month numbers (1-12) where both rules would fire,
	 * after intersecting their date bounds. An empty set means no collision.
	 * MONTHLY fires every month so its active set is {1..12}.
	 */
	Set<Integer> overlappingMonths(RecurringTransaction a, RecurringTransaction b) {
		Set<Integer> ma = activeMonthSet(a);
		Set<Integer> mb = activeMonthSet(b);
		// Intersect month sets
		Set<Integer> common = new java.util.HashSet<>(ma);
		common.retainAll(mb);
		if (common.isEmpty()) return common;

		// Also check if their date ranges overlap — if one ends before the other starts, no collision
		String aStart = a.getStartYearMonth();
		String aEnd   = a.getEndYearMonth();
		String bStart = b.getStartYearMonth();
		String bEnd   = b.getEndYearMonth();
		// No date overlap: a ends before b starts, or b ends before a starts
		if (aEnd != null && bStart != null && aEnd.compareTo(bStart) < 0) return java.util.Collections.emptySet();
		if (bEnd != null && aStart != null && bEnd.compareTo(aStart) < 0) return java.util.Collections.emptySet();

		return common;
	}

	private Set<Integer> activeMonthSet(RecurringTransaction rt) {
		if (rt.getRecurrenceType() == RecurrenceType.MONTHLY) {
			return new java.util.HashSet<>(Arrays.asList(1,2,3,4,5,6,7,8,9,10,11,12));
		}
		String months = rt.getActiveMonths();
		if (months == null || months.isBlank()) return java.util.Collections.emptySet();
		Set<Integer> result = new java.util.HashSet<>();
		for (String m : months.split(",")) {
			try { result.add(Integer.parseInt(m.trim())); } catch (NumberFormatException ignored) {}
		}
		return result;
	}

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
