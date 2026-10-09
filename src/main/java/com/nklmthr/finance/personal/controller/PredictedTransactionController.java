package com.nklmthr.finance.personal.controller;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nklmthr.finance.personal.dto.AccountTransactionDTO;
import com.nklmthr.finance.personal.dto.HistoricalMonthlySummaryDTO;
import com.nklmthr.finance.personal.dto.PredictedTransactionDTO;
import com.nklmthr.finance.personal.mapper.AccountTransactionMapper;
import com.nklmthr.finance.personal.mapper.PredictedTransactionMapper;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.PredictedTransaction;
import com.nklmthr.finance.personal.model.PredictionActualTxnMapping;
import com.nklmthr.finance.personal.model.PredictionHistoricalTxnMapping;
import com.nklmthr.finance.personal.repository.AccountTransactionRepository;
import com.nklmthr.finance.personal.repository.PredictedTransactionRepository;
import com.nklmthr.finance.personal.repository.PredictionActualTxnMappingRepository;
import com.nklmthr.finance.personal.repository.PredictionHistoricalTxnMappingRepository;
import com.nklmthr.finance.personal.service.AppUserService;
import com.nklmthr.finance.personal.service.PredictionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/predicted-transactions")
@RequiredArgsConstructor
@Slf4j
public class PredictedTransactionController {

	private final PredictionService predictionService;
	private final PredictedTransactionMapper predictedTransactionMapper;
	private final PredictedTransactionRepository predictedTransactionRepository;
	private final PredictionHistoricalTxnMappingRepository historicalMappingRepository;
	private final PredictionActualTxnMappingRepository actualMappingRepository;
	private final AccountTransactionMapper accountTransactionMapper;
	private final AccountTransactionRepository accountTransactionRepository;
	private final AppUserService appUserService;

	/**
	 * Get predicted transactions for a specific month
	 */
	@GetMapping
	public ResponseEntity<List<PredictedTransactionDTO>> getPredictionsForMonth(
		@RequestParam String month
	) {
		try {
			List<PredictedTransaction> predictions = predictionService.getPredictionsForMonth(month);
			List<PredictedTransactionDTO> dtos = predictions.stream()
				.map(predictedTransactionMapper::toDTO)
				.collect(Collectors.toList());
			return ResponseEntity.ok(dtos);
		} catch (Exception e) {
			log.error("Failed to get predictions for month: {}", month, e);
			return ResponseEntity.badRequest().build();
		}
	}

	/**
	 * Get predicted transactions for a month range
	 */
	@GetMapping("/range")
	public ResponseEntity<List<PredictedTransactionDTO>> getPredictionsForMonthRange(
		@RequestParam String startMonth,
		@RequestParam String endMonth
	) {
		try {
			List<PredictedTransaction> predictions = predictionService.getPredictionsForMonthRange(startMonth, endMonth);
			List<PredictedTransactionDTO> dtos = predictions.stream()
				.map(predictedTransactionMapper::toDTO)
				.collect(Collectors.toList());
			return ResponseEntity.ok(dtos);
		} catch (Exception e) {
			log.error("Failed to get predictions for month range: {} to {}", startMonth, endMonth, e);
			return ResponseEntity.badRequest().build();
		}
	}
	
	/**
	 * Get linked transactions for a predicted transaction
	 * Returns both historical transactions (used for calculation) and actual transactions (spending against prediction)
	 */
	@GetMapping("/{id}/linked-transactions")
	public ResponseEntity<?> getLinkedTransactions(@PathVariable String id) {
		try {
			long startTime = System.currentTimeMillis();
			
			// Get prediction
			PredictedTransaction prediction = predictedTransactionRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Prediction not found"));
			log.debug("Fetched prediction in {} ms", System.currentTimeMillis() - startTime);
			
			// Fetch historical and actual transactions in parallel using CompletableFuture
			CompletableFuture<List<AccountTransactionDTO>> historicalFuture = CompletableFuture.supplyAsync(() -> {
				long historicalStartTime = System.currentTimeMillis();
				List<PredictionHistoricalTxnMapping> historicalMappings = 
					historicalMappingRepository.findByPredictedTransaction(prediction);
				log.debug("Fetched {} historical mappings in {} ms", 
					historicalMappings.size(), System.currentTimeMillis() - historicalStartTime);
				
				long dtoStartTime = System.currentTimeMillis();
				List<AccountTransactionDTO> result = historicalMappings.stream()
					.map(m -> accountTransactionMapper.toDTO(m.getHistoricalTransaction()))
					.collect(Collectors.toList());
				log.debug("Converted {} historical transactions to DTO in {} ms", 
					result.size(), System.currentTimeMillis() - dtoStartTime);
				return result;
			});
			
			CompletableFuture<List<AccountTransactionDTO>> actualFuture = CompletableFuture.supplyAsync(() -> {
				long actualStartTime = System.currentTimeMillis();
				List<PredictionActualTxnMapping> actualMappings = 
					actualMappingRepository.findByPredictedTransaction(prediction);
				log.debug("Fetched {} actual mappings in {} ms", 
					actualMappings.size(), System.currentTimeMillis() - actualStartTime);
				
				long dtoStartTime = System.currentTimeMillis();
				List<AccountTransactionDTO> result = actualMappings.stream()
					.map(m -> accountTransactionMapper.toDTO(m.getActualTransaction()))
					.collect(Collectors.toList());
				log.debug("Converted {} actual transactions to DTO in {} ms", 
					result.size(), System.currentTimeMillis() - dtoStartTime);
				return result;
			});
			
			// Wait for both futures to complete
			CompletableFuture<Void> allFutures = CompletableFuture.allOf(historicalFuture, actualFuture);
			allFutures.join(); // Wait for both to complete
			
			// Get results
			List<AccountTransactionDTO> historicalTxns = historicalFuture.join();
			List<AccountTransactionDTO> actualTxns = actualFuture.join();
			
			log.info("Total linked-transactions processing completed in {} ms (parallel execution)", 
				System.currentTimeMillis() - startTime);
			
			return ResponseEntity.ok(Map.of(
				"historicalTransactions", historicalTxns,
				"actualTransactions", actualTxns,
				"prediction", predictedTransactionMapper.toDTO(prediction)
			));
		} catch (Exception e) {
			log.error("Failed to get linked transactions for prediction: {}", id, e);
			return ResponseEntity.badRequest().body("Failed to get linked transactions: " + e.getMessage());
		}
	}

	@GetMapping("/{id}/historical-summary")
	public ResponseEntity<?> getHistoricalSummary(@PathVariable String id) {
		try {
			PredictedTransaction prediction = predictedTransactionRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Prediction not found"));

			List<PredictionHistoricalTxnMapping> mappings = historicalMappingRepository.findByPredictedTransaction(prediction);

			// Collect all parent transaction IDs to batch-fetch their children
			List<String> parentIds = mappings.stream()
				.map(m -> m.getHistoricalTransaction().getId())
				.collect(Collectors.toList());

			Map<String, List<AccountTransaction>> childrenByParent = accountTransactionRepository
				.findByAppUserAndParentIn(appUserService.getCurrentUser(), parentIds)
				.stream()
				.collect(Collectors.groupingBy(AccountTransaction::getParent));

			// Accumulate effective amounts per YYYY-MM
			// Key: "YYYY-MM", Value: [totalAmount, count]
			Map<String, BigDecimal> amountByMonth = new LinkedHashMap<>();
			Map<String, Integer> countByMonth = new LinkedHashMap<>();

			for (PredictionHistoricalTxnMapping mapping : mappings) {
				AccountTransaction parent = mapping.getHistoricalTransaction();
				List<AccountTransaction> children = childrenByParent.get(parent.getId());

				String month = parent.getDate().format(DateTimeFormatter.ofPattern("yyyy-MM"));

				if (children != null && !children.isEmpty()) {
					for (AccountTransaction child : children) {
						String childMonth = child.getDate().format(DateTimeFormatter.ofPattern("yyyy-MM"));
						amountByMonth.merge(childMonth, child.getAmount(), BigDecimal::add);
						countByMonth.merge(childMonth, 1, Integer::sum);
					}
				} else {
					amountByMonth.merge(month, parent.getAmount(), BigDecimal::add);
					countByMonth.merge(month, 1, Integer::sum);
				}
			}

			// Sort by month ascending and build result
			DateTimeFormatter labelFormatter = DateTimeFormatter.ofPattern("MMM yyyy");
			List<HistoricalMonthlySummaryDTO> summary = amountByMonth.entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.map(e -> {
					String month = e.getKey();
					String label = java.time.YearMonth.parse(month).atDay(1)
						.format(labelFormatter);
					return new HistoricalMonthlySummaryDTO(month, label, countByMonth.get(month), e.getValue());
				})
				.collect(Collectors.toList());

			int totalCount = countByMonth.values().stream().mapToInt(Integer::intValue).sum();
			log.info("Historical summary for prediction {}: {} months, {} effective transactions", id, summary.size(), totalCount);

			return ResponseEntity.ok(Map.of("summary", summary, "totalCount", totalCount));
		} catch (Exception e) {
			log.error("Failed to get historical summary for prediction: {}", id, e);
			return ResponseEntity.badRequest().body("Failed to get historical summary: " + e.getMessage());
		}
	}

	/**
	 * Manually map a predicted transaction to an actual transaction.
	 * Creates a PredictionActualTxnMapping and updates actualSpent / remainingAmount on the prediction.
	 */
	@PostMapping("/{id}/map-actual")
	public ResponseEntity<?> mapActual(
		@PathVariable String id,
		@RequestParam String transactionId
	) {
		try {
			var user = appUserService.getCurrentUser();
			PredictedTransaction prediction = predictedTransactionRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Prediction not found"));
			if (!prediction.getAppUser().getId().equals(user.getId())) {
				return ResponseEntity.status(403).body("Access denied");
			}

			AccountTransaction actual = accountTransactionRepository.findById(transactionId)
				.orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
			if (!actual.getAppUser().getId().equals(user.getId())) {
				return ResponseEntity.status(403).body("Access denied");
			}

			// Avoid duplicate mapping
			if (actualMappingRepository.existsByPredictedTransactionAndActualTransaction(prediction, actual)) {
				return ResponseEntity.badRequest().body("Transaction is already mapped to this prediction");
			}

			BigDecimal amount = actual.getAmount();
			PredictionActualTxnMapping mapping = PredictionActualTxnMapping.builder()
				.predictedTransaction(prediction)
				.actualTransaction(actual)
				.amountApplied(amount)
				.build();
			actualMappingRepository.save(mapping);

			prediction.setActualSpent(prediction.getActualSpent().add(amount));
			prediction.setRemainingAmount(prediction.getPredictedAmount().subtract(prediction.getActualSpent()));
			predictedTransactionRepository.save(prediction);

			return ResponseEntity.ok(predictedTransactionMapper.toDTO(prediction));
		} catch (Exception e) {
			log.error("Failed to map actual transaction {} to prediction {}", transactionId, id, e);
			return ResponseEntity.badRequest().body("Failed to map transaction: " + e.getMessage());
		}
	}

	/**
	 * Remove a manual mapping between a predicted transaction and an actual transaction.
	 */
	@PostMapping("/{id}/unmap-actual")
	public ResponseEntity<?> unmapActual(
		@PathVariable String id,
		@RequestParam String transactionId
	) {
		try {
			var user = appUserService.getCurrentUser();
			PredictedTransaction prediction = predictedTransactionRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Prediction not found"));
			if (!prediction.getAppUser().getId().equals(user.getId())) {
				return ResponseEntity.status(403).body("Access denied");
			}

			List<PredictionActualTxnMapping> mappings = actualMappingRepository.findByPredictedTransaction(prediction);
			PredictionActualTxnMapping toRemove = mappings.stream()
				.filter(m -> m.getActualTransaction().getId().equals(transactionId))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Mapping not found"));

			BigDecimal amount = toRemove.getAmountApplied();
			actualMappingRepository.delete(toRemove);

			prediction.setActualSpent(prediction.getActualSpent().subtract(amount));
			prediction.setRemainingAmount(prediction.getPredictedAmount().subtract(prediction.getActualSpent()));
			predictedTransactionRepository.save(prediction);

			return ResponseEntity.ok(predictedTransactionMapper.toDTO(prediction));
		} catch (Exception e) {
			log.error("Failed to unmap transaction {} from prediction {}", transactionId, id, e);
			return ResponseEntity.badRequest().body("Failed to unmap transaction: " + e.getMessage());
		}
	}

	@Transactional
	@DeleteMapping("/{id}")
	public ResponseEntity<?> deletePrediction(@PathVariable String id) {
		try {
			PredictedTransaction prediction = predictedTransactionRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Prediction not found: " + id));
			historicalMappingRepository.deleteByPredictedTransaction(prediction);
			actualMappingRepository.deleteByPredictedTransaction(prediction);
			predictedTransactionRepository.delete(prediction);
			return ResponseEntity.noContent().build();
		} catch (Exception e) {
			log.error("Failed to delete prediction {}", id, e);
			return ResponseEntity.badRequest().body("Failed to delete prediction: " + e.getMessage());
		}
	}

	@Transactional
	@DeleteMapping("/auto-generated")
	public ResponseEntity<?> deleteAllAutoGenerated(@RequestParam String month) {
		try {
			com.nklmthr.finance.personal.model.AppUser user = appUserService.getCurrentUser();
			List<PredictedTransaction> toDelete = predictedTransactionRepository
				.findByAppUserAndPredictionMonthAndPredictionRuleIsNullAndRecurringTransactionIsNull(user, month);
			if (!toDelete.isEmpty()) {
				historicalMappingRepository.deleteByPredictedTransactionIn(toDelete);
				actualMappingRepository.deleteByPredictedTransactionIn(toDelete);
				predictedTransactionRepository.deleteByAppUserAndPredictionMonthAutoGenerated(user, month);
			}
			return ResponseEntity.ok().body("Deleted " + toDelete.size() + " auto-generated predictions for " + month);
		} catch (Exception e) {
			log.error("Failed to delete auto-generated predictions for month {}", month, e);
			return ResponseEntity.badRequest().body("Failed to delete: " + e.getMessage());
		}
	}
}

