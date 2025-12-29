package com.nklmthr.finance.personal.controller;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nklmthr.finance.personal.dto.AccountTransactionDTO;
import com.nklmthr.finance.personal.dto.PredictedTransactionDTO;
import com.nklmthr.finance.personal.mapper.AccountTransactionMapper;
import com.nklmthr.finance.personal.mapper.PredictedTransactionMapper;
import com.nklmthr.finance.personal.model.PredictedTransaction;
import com.nklmthr.finance.personal.model.PredictionActualTxnMapping;
import com.nklmthr.finance.personal.model.PredictionHistoricalTxnMapping;
import com.nklmthr.finance.personal.repository.PredictedTransactionRepository;
import com.nklmthr.finance.personal.repository.PredictionActualTxnMappingRepository;
import com.nklmthr.finance.personal.repository.PredictionHistoricalTxnMappingRepository;
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
}

