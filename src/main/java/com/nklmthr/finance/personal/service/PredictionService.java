package com.nklmthr.finance.personal.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nklmthr.finance.personal.enums.PredictionType;
import com.nklmthr.finance.personal.enums.TransactionType;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.model.Category;
import com.nklmthr.finance.personal.model.PredictedTransaction;
import com.nklmthr.finance.personal.model.PredictionActualTxnMapping;
import com.nklmthr.finance.personal.model.PredictionHistoricalTxnMapping;
import com.nklmthr.finance.personal.model.PredictionRule;
import com.nklmthr.finance.personal.repository.AccountTransactionRepository;
import com.nklmthr.finance.personal.repository.PredictedTransactionRepository;
import com.nklmthr.finance.personal.repository.PredictionActualTxnMappingRepository;
import com.nklmthr.finance.personal.repository.PredictionHistoricalTxnMappingRepository;
import com.nklmthr.finance.personal.repository.PredictionRuleRepository;

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
	private final PredictionActualTxnMappingRepository actualMappingRepository;

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
				// Delete actual mappings
				actualMappingRepository.deleteByPredictedTransaction(prediction);
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
				actualMappingRepository.deleteByPredictedTransaction(existingPrediction);
				predictedTransactionRepository.delete(existingPrediction);
				// Force flush to ensure deletion is committed before inserting new prediction
				predictedTransactionRepository.flush();
				log.debug("Deleted existing prediction for rule {} and month {}", ruleId, monthString);
			}
			
			// Calculate and create new prediction
			PredictedTransaction prediction = calculatePrediction(rule.getAppUser(), rule, targetMonth);
			if (prediction != null) {
				// Save prediction first
				PredictedTransaction savedPrediction = predictedTransactionRepository.save(prediction);
				// Then create historical mappings
				createHistoricalMappings(savedPrediction, rule, targetMonth);
				
				// Automatically recalculate actual transactions for this month
				try {
					recalculatePredictionsForMonth(monthString);
					log.info("Auto-recalculated actual transactions for prediction month {}", monthString);
				} catch (Exception e) {
					log.warn("Failed to auto-recalculate actual transactions for month {}: {}", 
						monthString, e.getMessage());
				}
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
		
		// Get historical transactions sum for this category
		List<Object[]> results = accountTransactionRepository
			.findAverageAmountByCategoryAndDateRange(user, category, startDate, endDate);
		
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
		
		String explanation = String.format("Based on monthly average of %d transaction(s) from past %d month(s)", 
			count, lookbackMonths);
		
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
		
		// Get all historical transactions for this category in the lookback period
		// Include both DEBIT and CREDIT since both are used in net average calculation
		List<AccountTransaction> historicalTxns = accountTransactionRepository
			.findByAppUserAndCategoryAndDateBetween(user, category, startDate, endDate);
		
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
	 * Adjust predicted transaction when an actual transaction is added
	 * Reduces remainingAmount and increases actualSpent
	 */
	@Transactional
	public void adjustPredictionForActualTransaction(AccountTransaction actualTransaction) {
		// Get the transaction's year-month
		LocalDateTime txDate = actualTransaction.getDate();
		YearMonth txMonth = YearMonth.from(txDate);
		String monthString = txMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));
		
		// Find matching prediction for this category and month
		List<PredictedTransaction> predictions = predictedTransactionRepository
			.findByAppUserAndCategoryAndPredictionMonth(
				actualTransaction.getAppUser(), 
				actualTransaction.getCategory(), 
				monthString
			);
		
		if (predictions.isEmpty()) {
			log.debug("No prediction found for category {} in month {}", 
				actualTransaction.getCategory().getName(), monthString);
			return;
		}
		
		PredictedTransaction prediction = predictions.get(0);
		
		// Only adjust for transactions of the same type (DEBIT/CREDIT)
		if (!prediction.getTransactionType().equals(actualTransaction.getType())) {
			return;
		}
		
		BigDecimal txAmount = actualTransaction.getAmount();
		
		// Update actual spent
		BigDecimal newActualSpent = prediction.getActualSpent().add(txAmount);
		prediction.setActualSpent(newActualSpent);
		
		// Update remaining amount
		BigDecimal newRemaining = prediction.getPredictedAmount().subtract(newActualSpent);
		prediction.setRemainingAmount(newRemaining);
		
		predictedTransactionRepository.save(prediction);
		
		// Create mapping to track this actual transaction
		PredictionActualTxnMapping actualMapping = PredictionActualTxnMapping.builder()
			.predictedTransaction(prediction)
			.actualTransaction(actualTransaction)
			.amountApplied(txAmount)
			.build();
		actualMappingRepository.save(actualMapping);
		
		log.info("Adjusted prediction for category {} in {}: predicted={}, actual={}, remaining={}", 
			actualTransaction.getCategory().getName(), 
			monthString,
			prediction.getPredictedAmount(),
			newActualSpent,
			newRemaining);
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
	 * Remove transaction from prediction when deleting the transaction
	 * This reverses the adjustment made when the transaction was created
	 */
	@Transactional
	public void removePredictionAdjustmentForTransaction(AccountTransaction transaction) {
		// Find if this transaction is mapped to any prediction
		List<PredictionActualTxnMapping> mappings = actualMappingRepository
			.findByActualTransaction(transaction);
		
		if (mappings.isEmpty()) {
			log.debug("No prediction mappings found for transaction {}", transaction.getId());
			return;
		}
		
		for (PredictionActualTxnMapping mapping : mappings) {
			PredictedTransaction prediction = mapping.getPredictedTransaction();
			BigDecimal txAmount = mapping.getAmountApplied();
			
			// Reverse the adjustment
			BigDecimal newActualSpent = prediction.getActualSpent().subtract(txAmount);
			prediction.setActualSpent(newActualSpent);
			
			BigDecimal newRemaining = prediction.getPredictedAmount().subtract(newActualSpent);
			prediction.setRemainingAmount(newRemaining);
			
			predictedTransactionRepository.save(prediction);
			
			log.info("Reversed prediction adjustment for category {} in {}: removed ₹{}, new remaining: ₹{}", 
				prediction.getCategory().getName(), 
				prediction.getPredictionMonth(),
				txAmount,
				newRemaining);
		}
		
		// Delete the mappings
		actualMappingRepository.deleteByActualTransaction(transaction);
	}

	/**
	 * Recalculate all predictions for a specific month by reapplying actual transactions
	 * Useful for fixing predictions after data imports or bulk updates
	 */
	@Transactional
	public void recalculatePredictionsForMonth(String month) {
		AppUser user = appUserService.getCurrentUser();
		
		// Get all predictions for this month
		List<PredictedTransaction> predictions = predictedTransactionRepository
			.findByAppUserAndPredictionMonthAndVisibleTrue(user, month);
		
		if (predictions.isEmpty()) {
			log.info("No predictions found for month {}", month);
			return;
		}
		
		for (PredictedTransaction prediction : predictions) {
			// Reset actualSpent and remainingAmount
			prediction.setActualSpent(BigDecimal.ZERO);
			prediction.setRemainingAmount(prediction.getPredictedAmount());
			
			// Delete existing actual transaction mappings
			actualMappingRepository.deleteByPredictedTransaction(prediction);
			
			// Get all actual transactions for this category and month
			YearMonth targetMonth = YearMonth.parse(month);
			LocalDateTime startDate = targetMonth.atDay(1).atStartOfDay();
			LocalDateTime endDate = targetMonth.atEndOfMonth().atTime(23, 59, 59);
			
			List<AccountTransaction> actualTxns = accountTransactionRepository
				.findByAppUserAndCategoryAndDateBetween(user, prediction.getCategory(), startDate, endDate);
			
			// Filter by transaction type to match prediction
			List<AccountTransaction> matchingTxns = actualTxns.stream()
				.filter(txn -> txn.getType() == prediction.getTransactionType())
				.toList();
			
			// Apply each transaction
			for (AccountTransaction txn : matchingTxns) {
				BigDecimal txAmount = txn.getAmount();
				prediction.setActualSpent(prediction.getActualSpent().add(txAmount));
				prediction.setRemainingAmount(prediction.getPredictedAmount().subtract(prediction.getActualSpent()));
				
				// Create mapping
				PredictionActualTxnMapping mapping = PredictionActualTxnMapping.builder()
					.predictedTransaction(prediction)
					.actualTransaction(txn)
					.amountApplied(txAmount)
					.build();
				actualMappingRepository.save(mapping);
			}
			
			predictedTransactionRepository.save(prediction);
			log.info("Recalculated prediction for category {} in month {}: {} transactions applied, remaining: {}",
				prediction.getCategory().getName(), month, matchingTxns.size(), prediction.getRemainingAmount());
		}
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
			actualMappingRepository.deleteByPredictedTransaction(existingPrediction);
			predictedTransactionRepository.delete(existingPrediction);
			// Force flush to ensure deletion is committed before inserting new prediction
			predictedTransactionRepository.flush();
			log.debug("Deleted existing prediction for rule {} and month {}", ruleId, monthString);
		}
		
		// Calculate and create new prediction
		PredictedTransaction prediction = calculatePrediction(rule.getAppUser(), rule, targetMonth);
		if (prediction != null) {
			// Save prediction first
			PredictedTransaction savedPrediction = predictedTransactionRepository.save(prediction);
			// Then create historical mappings
			createHistoricalMappings(savedPrediction, rule, targetMonth);
			
			// Automatically recalculate actual transactions for this month
			try {
				recalculatePredictionsForMonth(monthString);
				log.info("Auto-recalculated actual transactions for prediction month {}", monthString);
			} catch (Exception e) {
				log.warn("Failed to auto-recalculate actual transactions for month {}: {}", 
					monthString, e.getMessage());
			}
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
}

