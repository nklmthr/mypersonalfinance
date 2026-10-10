package com.nklmthr.finance.personal.controller;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nklmthr.finance.personal.dto.HistoricalMonthlySummaryDTO;
import com.nklmthr.finance.personal.dto.PredictedTransactionDTO;
import com.nklmthr.finance.personal.mapper.PredictedTransactionMapper;
import com.nklmthr.finance.personal.model.AccountTransaction;
import com.nklmthr.finance.personal.model.AppUser;
import com.nklmthr.finance.personal.model.PredictedTransaction;
import com.nklmthr.finance.personal.model.PredictionHistoricalTxnMapping;
import com.nklmthr.finance.personal.repository.AccountTransactionRepository;
import com.nklmthr.finance.personal.repository.PredictedTransactionRepository;
import com.nklmthr.finance.personal.repository.PredictionActualTxnMappingRepository;
import com.nklmthr.finance.personal.repository.PredictionHistoricalTxnMappingRepository;
import com.nklmthr.finance.personal.service.AppUserService;
import com.nklmthr.finance.personal.service.CategoryService;
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
	private final AccountTransactionRepository accountTransactionRepository;
	private final CategoryService categoryService;
	private final AppUserService appUserService;

	@GetMapping
	public ResponseEntity<List<PredictedTransactionDTO>> getPredictionsForMonth(@RequestParam String month) {
		try {
			AppUser user = appUserService.getCurrentUser();
			List<PredictedTransaction> predictions = predictionService.getPredictionsForMonth(month);

			YearMonth ym = YearMonth.parse(month);
			LocalDateTime start = ym.atDay(1).atStartOfDay();
			LocalDateTime end = ym.atEndOfMonth().atTime(23, 59, 59);

			List<PredictedTransactionDTO> dtos = new java.util.ArrayList<>();
			for (PredictedTransaction p : predictions) {
				try {
					PredictedTransactionDTO dto = predictedTransactionMapper.toDTO(p);
					Set<String> categoryIds = categoryService.getAllDescendantCategoryIds(user, p.getCategory().getId());
					BigDecimal netActual = accountTransactionRepository.sumActualByCategoryIdsAndDateRange(user, categoryIds, start, end);
					BigDecimal actual = netActual.abs();
					dto.setActualSpent(actual);
					dto.setRemainingAmount(dto.getPredictedAmount().subtract(actual));
					dtos.add(dto);
				} catch (Exception e) {
					log.error("Skipping prediction {} (category {}) due to error: {}", p.getId(), p.getCategory().getId(), e.getMessage());
				}
			}

			return ResponseEntity.ok(dtos);
		} catch (Exception e) {
			log.error("Failed to get predictions for month: {}", month, e);
			return ResponseEntity.badRequest().build();
		}
	}

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

	@GetMapping("/{id}/historical-summary")
	public ResponseEntity<?> getHistoricalSummary(@PathVariable String id) {
		try {
			PredictedTransaction prediction = predictedTransactionRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Prediction not found"));

			List<PredictionHistoricalTxnMapping> mappings = historicalMappingRepository.findByPredictedTransaction(prediction);

			List<String> parentIds = mappings.stream()
				.map(m -> m.getHistoricalTransaction().getId())
				.collect(Collectors.toList());

			Map<String, List<AccountTransaction>> childrenByParent = accountTransactionRepository
				.findByAppUserAndParentIn(appUserService.getCurrentUser(), parentIds)
				.stream()
				.collect(Collectors.groupingBy(AccountTransaction::getParent));

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

			DateTimeFormatter labelFormatter = DateTimeFormatter.ofPattern("MMM yyyy");
			List<HistoricalMonthlySummaryDTO> summary = amountByMonth.entrySet().stream()
				.sorted(Map.Entry.comparingByKey())
				.map(e -> {
					String month = e.getKey();
					String label = YearMonth.parse(month).atDay(1).format(labelFormatter);
					return new HistoricalMonthlySummaryDTO(month, label, countByMonth.get(month), e.getValue());
				})
				.collect(Collectors.toList());

			int totalCount = countByMonth.values().stream().mapToInt(Integer::intValue).sum();
			return ResponseEntity.ok(Map.of("summary", summary, "totalCount", totalCount));
		} catch (Exception e) {
			log.error("Failed to get historical summary for prediction: {}", id, e);
			return ResponseEntity.badRequest().body("Failed to get historical summary: " + e.getMessage());
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
			AppUser user = appUserService.getCurrentUser();
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

	@Transactional
	@PatchMapping("/{id}/override")
	public ResponseEntity<?> overridePrediction(
		@PathVariable String id,
		@RequestParam BigDecimal amount
	) {
		try {
			PredictedTransaction prediction = predictedTransactionRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Prediction not found: " + id));
			// Save original amount only on first override so reset always returns to auto-calculated value
			if (!prediction.isManualOverride()) {
				prediction.setManualOverrideOriginalAmount(prediction.getPredictedAmount());
			}
			prediction.setPredictedAmount(amount);
			prediction.setRemainingAmount(amount);
			prediction.setManualOverride(true);
			PredictedTransaction saved = predictedTransactionRepository.save(prediction);
			PredictedTransactionDTO dto = predictedTransactionMapper.toDTO(saved);
			dto.setActualSpent(BigDecimal.ZERO);
			dto.setRemainingAmount(amount);
			return ResponseEntity.ok(dto);
		} catch (Exception e) {
			log.error("Failed to override prediction {}", id, e);
			return ResponseEntity.badRequest().body("Failed to override: " + e.getMessage());
		}
	}

	@Transactional
	@DeleteMapping("/{id}/override")
	public ResponseEntity<?> resetOverride(@PathVariable String id) {
		try {
			PredictedTransaction prediction = predictedTransactionRepository.findById(id)
				.orElseThrow(() -> new IllegalArgumentException("Prediction not found: " + id));
			if (prediction.isManualOverride() && prediction.getManualOverrideOriginalAmount() != null) {
				prediction.setPredictedAmount(prediction.getManualOverrideOriginalAmount());
				prediction.setRemainingAmount(prediction.getManualOverrideOriginalAmount());
			}
			prediction.setManualOverride(false);
			prediction.setManualOverrideOriginalAmount(null);
			PredictedTransaction saved = predictedTransactionRepository.save(prediction);
			return ResponseEntity.ok(predictedTransactionMapper.toDTO(saved));
		} catch (Exception e) {
			log.error("Failed to reset override for prediction {}", id, e);
			return ResponseEntity.badRequest().body("Failed to reset: " + e.getMessage());
		}
	}
}
