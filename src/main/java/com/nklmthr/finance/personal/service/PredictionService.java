package com.nklmthr.finance.personal.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nklmthr.finance.personal.enums.PredictionType;
import com.nklmthr.finance.personal.enums.TransactionType;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.model.Category;
import com.nklmthr.finance.personal.model.PredictedTransaction;
import com.nklmthr.finance.personal.model.PredictionHistoricalTxnMapping;
import com.nklmthr.finance.personal.model.PredictionRule;
import com.nklmthr.finance.personal.model.RecurringTransaction;
import com.nklmthr.finance.personal.repository.AccountTransactionRepository;
import com.nklmthr.finance.personal.repository.PredictedTransactionRepository;
import com.nklmthr.finance.personal.repository.PredictionHistoricalTxnMappingRepository;
import com.nklmthr.finance.personal.repository.PredictionRuleRepository;
import com.nklmthr.finance.personal.repository.RecurringTransactionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class PredictionService {

	private final PredictionRuleRepository predictionRuleRepository;
	private final PredictedTransactionRepository predictedTransactionRepository;
	private final AccountTransactionRepository accountTransactionRepository;
	private final AppUserService appUserService;
	private final PredictionHistoricalTxnMappingRepository historicalMappingRepository;
	private final CategoryService categoryService;
	private final RecurringTransactionRepository recurringTransactionRepository;

	/**
	 * Get all prediction rules for a user
	 */
	public List<PredictionRule> getAllRulesForUser() {
		AppUser user = appUserService.getCurrentUser();
		return predictionRuleRepository.findByAppUser(user);
	}

	/**
	 * Get enabled prediction rules for a user
	 */
	public List<PredictionRule> getEnabledRulesForUser(AppUser user) {
		return predictionRuleRepository.findByAppUserAndEnabledTrue(user);
	}

	/**
	 * Get a specific prediction rule by ID
	 */
	public Optional<PredictionRule> getRuleById(String id) {
		return predictionRuleRepository.findById(id);
	}

	/**
	 * Create or update a prediction rule
	 */
	@Transactional
	public PredictionRule saveRule(PredictionRule rule) {
		// Get fresh user from security context
		AppUser user = appUserService.getCurrentUser();
		
		// Set app user if not already set
		if (rule.getAppUser() == null) {
			rule.setAppUser(user);
		}
		
		return predictionRuleRepository.save(rule);
	}

	/**
	 * Delete a prediction rule and all its predicted transactions
	 */
	@Transactional
	public void deleteRule(String id) {
		Optional<PredictionRule> ruleOpt = predictionRuleRepository.findById(id);
		if (ruleOpt.isPresent()) {
			PredictionRule rule = ruleOpt.get();
			
			// Get all predicted transactions for this rule
			List<PredictedTransaction> predictions = predictedTransactionRepository.findByPredictionRule(rule);
			
			// Delete mappings for each predicted transaction first
			for (PredictedTransaction prediction : predictions) {
				// Delete historical mappings
				historicalMappingRepository.deleteByPredictedTransaction(prediction);
			}
			
			// Now delete predicted transactions
			predictedTransactionRepository.deleteByPredictionRule(rule);
			
			// Finally delete the rule
			predictionRuleRepository.deleteById(id);
			
			log.info("Deleted prediction rule {} and {} associated predictions with their mappings", 
				id, predictions.size());
		}
	}

	/**
	 * Get predicted transactions for a specific month
	 */
	public List<PredictedTransaction> getPredictionsForMonth(String month) {
		AppUser user = appUserService.getCurrentUser();
		return predictedTransactionRepository.findByAppUserAndPredictionMonthAndVisibleTrue(user, month);
	}

	/**
	 * Get predicted transactions for a month range
	 */
	public List<PredictedTransaction> getPredictionsForMonthRange(String startMonth, String endMonth) {
		AppUser user = appUserService.getCurrentUser();
		return predictedTransactionRepository.findByAppUserAndMonthRange(user, startMonth, endMonth);
	}

	/**
	 * Regenerate predictions for a specific rule
	 */
	@Transactional
	public void regeneratePredictionsForRule(String ruleId, int monthsAhead) {
		Optional<PredictionRule> ruleOpt = predictionRuleRepository.findById(ruleId);
		if (ruleOpt.isEmpty()) {
			return;
		}
		
		PredictionRule rule = ruleOpt.get();
		YearMonth currentMonth = YearMonth.now();

		// Start from i=0 to allow generating predictions for the current month
		for (int i = 0; i <= monthsAhead; i++) {
			YearMonth targetMonth = currentMonth.plusMonths(i);
			
			// Skip if this is a yearly prediction and current month doesn't match
			if (rule.getPredictionType() == PredictionType.YEARLY) {
				if (rule.getSpecificMonth() == null || 
				    rule.getSpecificMonth() != targetMonth.getMonthValue()) {
					continue;
				}
			}
			
			String monthString = targetMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));
			
			// Delete existing prediction and its mappings if any
			Optional<PredictedTransaction> existingPredictionOpt = 
				predictedTransactionRepository.findByPredictionRuleAndPredictionMonth(rule, monthString);
			
			if (existingPredictionOpt.isPresent()) {
				PredictedTransaction existingPrediction = existingPredictionOpt.get();
				// Delete mappings first (cascade should handle this, but being explicit)
				historicalMappingRepository.deleteByPredictedTransaction(existingPrediction);
				predictedTransactionRepository.delete(existingPrediction);
				// Force flush to ensure deletion is committed before inserting new prediction
				predictedTransactionRepository.flush();
				log.debug("Deleted existing prediction for rule {} and month {}", ruleId, monthString);
			}

			// Calculate and create new prediction
			PredictedTransaction prediction = calculatePrediction(rule.getAppUser(), rule, targetMonth);
			if (prediction == null) {
				// No historical data — fall back to recurring obligation amount for same category+month, if any
				prediction = buildFallbackFromRecurring(rule.getAppUser(), rule, targetMonth, monthString);
			}
			if (prediction != null) {
				// Save prediction first
				PredictedTransaction savedPrediction = predictedTransactionRepository.save(prediction);
				// Then create historical mappings
				createHistoricalMappings(savedPrediction, rule, targetMonth);
			}
		}
	}

	/**
	 * Calculate prediction based on historical transactions
	 */
	private PredictedTransaction calculatePrediction(AppUser user, PredictionRule rule, YearMonth targetMonth) {
		Category category = rule.getCategory();
		int lookbackMonths = rule.getLookbackMonths();
		
		// Calculate the date range for lookback
		YearMonth startMonth = targetMonth.minusMonths(lookbackMonths);
		LocalDateTime startDate = startMonth.atDay(1).atStartOfDay();
		LocalDateTime endDate = targetMonth.minusMonths(1).atEndOfMonth().atTime(23, 59, 59);
		
		// Get historical transactions sum across this category and all descendants
		Set<String> categoryIds = categoryService.getAllDescendantCategoryIds(user, category.getId());
		List<Object[]> results = accountTransactionRepository
			.findAverageAmountByCategoryIdsAndDateRange(user, categoryIds, startDate, endDate);
		
		if (results.isEmpty() || results.get(0) == null) {
			log.debug("No historical data found for category: {} in date range: {} to {}", 
				category.getName(), startDate, endDate);
			return null;
		}
		
		Object[] result = results.get(0);
		
		// Convert to BigDecimal - handle both Double and BigDecimal from database
		BigDecimal totalSum;
		if (result[0] instanceof Double) {
			totalSum = BigDecimal.valueOf((Double) result[0]);
		} else if (result[0] instanceof BigDecimal) {
			totalSum = (BigDecimal) result[0];
		} else if (result[0] == null) {
			return null;
		} else {
			// Fallback: try to parse as string
			totalSum = new BigDecimal(result[0].toString());
		}
		
		// Get transaction type - result[1] is a String from the CASE statement
		String txTypeString = (String) result[1];
		TransactionType txType = TransactionType.valueOf(txTypeString);
		Long count = (Long) result[2];
		
		if (totalSum == null || totalSum.compareTo(BigDecimal.ZERO) == 0) {
			return null;
		}
		
		// Calculate monthly average by dividing total sum by lookback months
		BigDecimal monthlyAverage = totalSum.divide(
			BigDecimal.valueOf(lookbackMonths), 
			2, 
			RoundingMode.HALF_UP
		).abs();
		
		// Build description
		String description = String.format("Predicted %s for %s", 
			category.getName(), 
			targetMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy")));
		
		String explanation = String.format(
			"Based on monthly average of %d transaction(s) from past %d month(s) across %d category(ies)",
			count, lookbackMonths, categoryIds.size());
		
		// Create predicted transaction
		PredictedTransaction prediction = PredictedTransaction.builder()
			.appUser(user)
			.predictionRule(rule)
			.category(category)
			.predictedAmount(monthlyAverage)
			.remainingAmount(monthlyAverage)  // Initially, remaining equals predicted
			.actualSpent(BigDecimal.ZERO)  // No spending yet
			.transactionType(txType)
			.predictionMonth(targetMonth.format(DateTimeFormatter.ofPattern("yyyy-MM")))
			.description(description)
			.explanation(explanation)
			.currency("INR")
			.calculationDate(LocalDateTime.now())
			.basedOnTransactionCount(count.intValue())
			.visible(true)
			.build();
		
		// Note: We don't save prediction here or create mappings
		// The caller (regeneratePredictionsForRule or generatePredictions) will save it
		// and call createHistoricalMappings afterwards
		return prediction;
	}

	/**
	 * When there is no historical data for a rule, fall back to the recurring obligation amount
	 * for the same category and month (if one exists). This allows the Merged view to work
	 * even for newly-created rules with no spending history yet.
	 */
	private PredictedTransaction buildFallbackFromRecurring(AppUser user, PredictionRule rule,
			YearMonth targetMonth, String monthString) {
		String month = monthString != null ? monthString : targetMonth.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"));
		return recurringTransactionRepository.findByAppUserAndEnabledTrue(user).stream()
			.filter(rt -> rt.getCategory().getId().equals(rule.getCategory().getId()))
			.findFirst()
			.flatMap(rt -> predictedTransactionRepository.findByRecurringTransactionAndPredictionMonth(rt, month))
			.map(recurringPred -> {
				String description = String.format("Predicted %s for %s (no history — based on recurring obligation)",
					rule.getCategory().getName(), targetMonth.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy")));
				return PredictedTransaction.builder()
					.appUser(user)
					.predictionRule(rule)
					.category(rule.getCategory())
					.predictedAmount(recurringPred.getPredictedAmount())
					.remainingAmount(recurringPred.getPredictedAmount())
					.actualSpent(java.math.BigDecimal.ZERO)
					.transactionType(recurringPred.getTransactionType())
					.predictionMonth(month)
					.description(description)
					.explanation("No historical transactions found. Using recurring obligation amount as baseline.")
					.currency("INR")
					.calculationDate(java.time.LocalDateTime.now())
					.basedOnTransactionCount(0)
					.visible(true)
					.build();
			})
			.orElse(null);
	}

	/**
	 * Create mappings between a predicted transaction and the historical transactions used for calculation
	 * Includes ALL transactions (both DEBIT and CREDIT) since they all contribute to the net average
	 */
	@Transactional
	public void createHistoricalMappings(PredictedTransaction prediction, PredictionRule rule, YearMonth targetMonth) {
		AppUser user = prediction.getAppUser();
		Category category = rule.getCategory();
		int lookbackMonths = rule.getLookbackMonths();
		
		// Calculate the same date range used for prediction
		YearMonth startMonth = targetMonth.minusMonths(lookbackMonths);
		LocalDateTime startDate = startMonth.atDay(1).atStartOfDay();
		LocalDateTime endDate = targetMonth.minusMonths(1).atEndOfMonth().atTime(23, 59, 59);
		
		// Get all historical transactions across this category and all descendants
		// Include both DEBIT and CREDIT since both are used in net average calculation
		Set<String> categoryIds = categoryService.getAllDescendantCategoryIds(user, category.getId());
		List<AccountTransaction> historicalTxns = accountTransactionRepository
			.findByAppUserAndCategoryIdsAndDateBetween(user, categoryIds, startDate, endDate);
		
		// Create mappings for each historical transaction
		for (AccountTransaction historicalTxn : historicalTxns) {
			PredictionHistoricalTxnMapping mapping = PredictionHistoricalTxnMapping.builder()
				.predictedTransaction(prediction)
				.historicalTransaction(historicalTxn)
				.build();
			historicalMappingRepository.save(mapping);
		}
		
		log.debug("Created {} historical transaction mappings for prediction {} (includes all transaction types)",
			historicalTxns.size(), prediction.getId());
	}

	/**
	 * Variant of createHistoricalMappings used when there is no PredictionRule
	 * (auto-generated predictions from generate-all-categories).
	 */
	@Transactional
	public void createHistoricalMappingsForCategory(PredictedTransaction prediction, Category category,
			int lookbackMonths, YearMonth targetMonth) {
		AppUser user = prediction.getAppUser();
		YearMonth startMonth = targetMonth.minusMonths(lookbackMonths);
		LocalDateTime startDate = startMonth.atDay(1).atStartOfDay();
		LocalDateTime endDate = targetMonth.minusMonths(1).atEndOfMonth().atTime(23, 59, 59);

		Set<String> categoryIds = categoryService.getAllDescendantCategoryIds(user, category.getId());
		List<AccountTransaction> historicalTxns = accountTransactionRepository
			.findByAppUserAndCategoryIdsAndDateBetween(user, categoryIds, startDate, endDate);

		for (AccountTransaction historicalTxn : historicalTxns) {
			PredictionHistoricalTxnMapping mapping = PredictionHistoricalTxnMapping.builder()
				.predictedTransaction(prediction)
				.historicalTransaction(historicalTxn)
				.build();
			historicalMappingRepository.save(mapping);
		}
		log.debug("Created {} historical mappings for auto-prediction {} (category {})",
			historicalTxns.size(), prediction.getId(), category.getName());
	}

	/**
	 * Check if a prediction rule exists for a category
	 */
	public boolean ruleExistsForCategory(Category category) {
		AppUser user = appUserService.getCurrentUser();
		return predictionRuleRepository.existsByAppUserAndCategory(user, category);
	}

	/**
	 * Get prediction rule for a specific category
	 */
	public Optional<PredictionRule> getRuleForCategory(Category category) {
		AppUser user = appUserService.getCurrentUser();
		return predictionRuleRepository.findByAppUserAndCategory(user, category);
	}
	
	/**
	 * Generate predictions for the next N months based on enabled rules
	 */
	@Transactional
	public void generatePredictions(int monthsAhead) {
		AppUser user = appUserService.getCurrentUser();
		List<PredictionRule> enabledRules = getEnabledRulesForUser(user);
		YearMonth currentMonth = YearMonth.now();

		for (PredictionRule rule : enabledRules) {
			// Start from i=0 to allow generating predictions for the current month
			for (int i = 0; i <= monthsAhead; i++) {
				YearMonth targetMonth = currentMonth.plusMonths(i);
				
				// Skip if this is a yearly prediction and current month doesn't match
				if (rule.getPredictionType() == PredictionType.YEARLY) {
					if (rule.getSpecificMonth() == null || 
					    rule.getSpecificMonth() != targetMonth.getMonthValue()) {
						continue;
					}
				}
				
				PredictedTransaction prediction = calculatePrediction(user, rule, targetMonth);
				if (prediction != null) {
					// Save prediction first
					PredictedTransaction savedPrediction = predictedTransactionRepository.save(prediction);
					// Then create historical mappings
					createHistoricalMappings(savedPrediction, rule, targetMonth);
				}
			}
		}
	}
	
	/**
	 * Regenerate predictions for a specific rule for a specific target month
	 * @param ruleId The prediction rule ID
	 * @param targetMonthStr The target month in yyyy-MM format (e.g., "2025-12")
	 */
	@Transactional
	public void regeneratePredictionsForRuleByMonth(String ruleId, String targetMonthStr) {
		Optional<PredictionRule> ruleOpt = predictionRuleRepository.findById(ruleId);
		if (ruleOpt.isEmpty()) {
			return;
		}
		
		PredictionRule rule = ruleOpt.get();
		YearMonth targetMonth;
		
		// Parse target month or use current month if not provided
		if (targetMonthStr != null && !targetMonthStr.isEmpty()) {
			targetMonth = YearMonth.parse(targetMonthStr, DateTimeFormatter.ofPattern("yyyy-MM"));
		} else {
			targetMonth = YearMonth.now();
		}
		
		// Skip if this is a yearly prediction and target month doesn't match the specific month
		if (rule.getPredictionType() == PredictionType.YEARLY) {
			if (rule.getSpecificMonth() == null || 
			    rule.getSpecificMonth() != targetMonth.getMonthValue()) {
				log.warn("Cannot generate yearly prediction for rule {} - target month {} doesn't match specific month {}", 
					ruleId, targetMonth.getMonthValue(), rule.getSpecificMonth());
				return;
			}
		}
		
		String monthString = targetMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));
		
		// Delete existing prediction and its mappings if any
		Optional<PredictedTransaction> existingPredictionOpt = 
			predictedTransactionRepository.findByPredictionRuleAndPredictionMonth(rule, monthString);
		
		if (existingPredictionOpt.isPresent()) {
			PredictedTransaction existingPrediction = existingPredictionOpt.get();
			// Delete mappings first (cascade should handle this, but being explicit)
			historicalMappingRepository.deleteByPredictedTransaction(existingPrediction);
			predictedTransactionRepository.delete(existingPrediction);
			predictedTransactionRepository.flush();
			log.debug("Deleted existing prediction for rule {} and month {}", ruleId, monthString);
		}

		// Calculate and create new prediction
		PredictedTransaction prediction = calculatePrediction(rule.getAppUser(), rule, targetMonth);
		if (prediction != null) {
			PredictedTransaction savedPrediction = predictedTransactionRepository.save(prediction);
			createHistoricalMappings(savedPrediction, rule, targetMonth);
		}
	}
	
	/**
	 * Generate predictions for all enabled rules for a specific target month
	 * @param targetMonthStr The target month in yyyy-MM format (e.g., "2025-12")
	 */
	@Transactional
	public void generatePredictionsByMonth(String targetMonthStr) {
		AppUser user = appUserService.getCurrentUser();
		List<PredictionRule> enabledRules = getEnabledRulesForUser(user);
		YearMonth targetMonth;
		
		// Parse target month or use current month if not provided
		if (targetMonthStr != null && !targetMonthStr.isEmpty()) {
			targetMonth = YearMonth.parse(targetMonthStr, DateTimeFormatter.ofPattern("yyyy-MM"));
		} else {
			targetMonth = YearMonth.now();
		}
		
		for (PredictionRule rule : enabledRules) {
			// Skip if this is a yearly prediction and target month doesn't match the specific month
			if (rule.getPredictionType() == PredictionType.YEARLY) {
				if (rule.getSpecificMonth() == null || 
				    rule.getSpecificMonth() != targetMonth.getMonthValue()) {
					continue;
				}
			}
			
			String monthString = targetMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));
			
			// Check if prediction already exists
			Optional<PredictedTransaction> existingPredictionOpt = 
				predictedTransactionRepository.findByPredictionRuleAndPredictionMonth(rule, monthString);
			
			if (existingPredictionOpt.isPresent()) {
				log.debug("Prediction already exists for rule {} and month {}, skipping", rule.getId(), monthString);
				continue;
			}
			
			PredictedTransaction prediction = calculatePrediction(user, rule, targetMonth);
			if (prediction != null) {
				// Save prediction first
				PredictedTransaction savedPrediction = predictedTransactionRepository.save(prediction);
				// Then create historical mappings
				createHistoricalMappings(savedPrediction, rule, targetMonth);
			}
		}
	}

	/**
	 * Generate predictions for every category that has transaction history in the
	 * lookback window, regardless of whether a PredictionRule exists for it.
	 * Categories that already have a rule-based prediction for the target month are
	 * skipped — the rule takes precedence. Uses a default lookback of 3 months.
	 */
	@Transactional
	public int generateAllCategoriesByMonth(String targetMonthStr) {
		AppUser user = appUserService.getCurrentUser();
		YearMonth targetMonth = (targetMonthStr != null && !targetMonthStr.isEmpty())
			? YearMonth.parse(targetMonthStr, DateTimeFormatter.ofPattern("yyyy-MM"))
			: YearMonth.now();

		int defaultLookback = 6;
		String monthString = targetMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));

		// Collect category IDs already covered by an existing rule or recurring transaction prediction
		Set<String> ruleCategoryIds = getEnabledRulesForUser(user).stream()
			.map(r -> r.getCategory().getId())
			.collect(java.util.stream.Collectors.toSet());

		Set<String> recurringCategoryIds = predictedTransactionRepository
			.findByAppUserAndPredictionMonthAndRecurringTransactionIsNotNull(user, monthString).stream()
			.map(p -> p.getCategory().getId())
			.collect(java.util.stream.Collectors.toSet());

		// Only generate for leaf categories (those that are never a parent of another category)
		// — parent categories don't have direct transactions so ₹0 predictions there add noise
		java.util.Set<String> parentIds = allUserCategories.stream()
			.map(Category::getParent)
			.filter(p -> p != null)
			.collect(java.util.stream.Collectors.toSet());

		int generated = 0;
		for (Category category : allUserCategories) {
			// Skip parent/container categories
			if (parentIds.contains(category.getId())) {
				continue;
			}
			// Skip if already covered by a rule or a recurring prediction
			if (ruleCategoryIds.contains(category.getId()) || recurringCategoryIds.contains(category.getId())) {
				continue;
			}

			// Skip if an auto-generated prediction already exists for this month
			if (predictedTransactionRepository
					.findByAppUserAndCategoryAndPredictionMonthAndPredictionRuleIsNullAndRecurringTransactionIsNull(
						user, category, monthString)
					.isPresent()) {
				log.debug("Auto-prediction already exists for category {} month {}, skipping", category.getName(), monthString);
				continue;
			}

			// Build a transient rule to reuse calculatePrediction (not persisted)
			PredictionRule transientRule = PredictionRule.builder()
				.appUser(user)
				.category(category)
				.predictionType(PredictionType.MONTHLY)
				.lookbackMonths(defaultLookback)
				.enabled(true)
				.build();

			PredictedTransaction prediction = calculatePrediction(user, transientRule, targetMonth);
			if (prediction == null) {
				// No history — create a ₹0 placeholder so actual spend is still visible
				prediction = PredictedTransaction.builder()
					.appUser(user)
					.predictionRule(null)
					.category(category)
					.predictedAmount(BigDecimal.ZERO)
					.remainingAmount(BigDecimal.ZERO)
					.actualSpent(BigDecimal.ZERO)
					.transactionType(com.nklmthr.finance.personal.enums.TransactionType.DEBIT)
					.predictionMonth(monthString)
					.description("No history for " + category.getName())
					.explanation("No transactions found in the lookback window. Predicted ₹0.")
					.currency("INR")
					.calculationDate(java.time.LocalDateTime.now())
					.basedOnTransactionCount(0)
					.visible(true)
					.build();
				predictedTransactionRepository.save(prediction);
			} else {
				prediction.setPredictionRule(null);
				PredictedTransaction saved = predictedTransactionRepository.save(prediction);
				createHistoricalMappingsForCategory(saved, category, defaultLookback, targetMonth);
			}
			generated++;
			log.debug("Auto-generated prediction for category {} month {}: {}",
				category.getName(), monthString, prediction.getPredictedAmount());
		}

		log.info("generate-all-categories for {}: {} new predictions created", monthString, generated);
		return generated;
	}
}

