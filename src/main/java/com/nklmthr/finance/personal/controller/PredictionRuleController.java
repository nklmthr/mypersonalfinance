package com.nklmthr.finance.personal.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nklmthr.finance.personal.dto.PredictionRuleDTO;
import com.nklmthr.finance.personal.mapper.PredictionRuleMapper;
import com.nklmthr.finance.personal.model.Category;
import com.nklmthr.finance.personal.model.PredictionRule;
import com.nklmthr.finance.personal.repository.CategoryRepository;
import com.nklmthr.finance.personal.service.PredictionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/prediction-rules")
@RequiredArgsConstructor
@Slf4j
public class PredictionRuleController {

	private final PredictionService predictionService;
	private final PredictionRuleMapper predictionRuleMapper;
	private final CategoryRepository categoryRepository;

	/**
	 * Get all prediction rules for the authenticated user
	 */
	@GetMapping
	public ResponseEntity<List<PredictionRuleDTO>> getAllRules() {
		List<PredictionRule> rules = predictionService.getAllRulesForUser();
		List<PredictionRuleDTO> dtos = rules.stream()
			.map(predictionRuleMapper::toDTO)
			.collect(Collectors.toList());
		return ResponseEntity.ok(dtos);
	}

	/**
	 * Get a specific prediction rule by ID
	 */
	@GetMapping("/{id}")
	public ResponseEntity<PredictionRuleDTO> getRuleById(@PathVariable String id) {
		return predictionService.getRuleById(id)
			.map(predictionRuleMapper::toDTO)
			.map(ResponseEntity::ok)
			.orElse(ResponseEntity.notFound().build());
	}

	/**
	 * Create a new prediction rule
	 */
	@PostMapping
	public ResponseEntity<?> createRule(@RequestBody PredictionRuleDTO dto) {
		try {
			// Validate category
			Category category = categoryRepository.findById(dto.getCategoryId())
				.orElseThrow(() -> new IllegalArgumentException("Category not found"));

			// Check if rule already exists for this category
			if (predictionService.ruleExistsForCategory(category)) {
				return ResponseEntity.badRequest().body("Prediction rule already exists for this category");
			}

			// Create new rule
			PredictionRule rule = PredictionRule.builder()
				.category(category)
				.predictionType(dto.getPredictionType())
				.enabled(dto.isEnabled())
				.lookbackMonths(dto.getLookbackMonths())
				.specificMonth(dto.getSpecificMonth())
				.build();

		PredictionRule savedRule = predictionService.saveRule(rule);

		// Generate predictions for the next month
		predictionService.regeneratePredictionsForRule(savedRule.getId(), 1);

		return ResponseEntity.ok(predictionRuleMapper.toDTO(savedRule));
		} catch (Exception e) {
			log.error("Failed to create prediction rule", e);
			return ResponseEntity.badRequest().body("Failed to create prediction rule: " + e.getMessage());
		}
	}

	/**
	 * Update an existing prediction rule
	 */
	@PutMapping("/{id}")
	public ResponseEntity<?> updateRule(
		@PathVariable String id,
		@RequestBody PredictionRuleDTO dto
	) {
		try {
			PredictionRule rule = predictionService.getRuleById(id)
				.orElseThrow(() -> new IllegalArgumentException("Prediction rule not found"));

			// Update fields
			rule.setPredictionType(dto.getPredictionType());
			rule.setEnabled(dto.isEnabled());
			rule.setLookbackMonths(dto.getLookbackMonths());
			rule.setSpecificMonth(dto.getSpecificMonth());

		PredictionRule savedRule = predictionService.saveRule(rule);

		// Regenerate predictions if the rule is enabled
		if (savedRule.isEnabled()) {
			predictionService.regeneratePredictionsForRule(savedRule.getId(), 1);
		}

		return ResponseEntity.ok(predictionRuleMapper.toDTO(savedRule));
		} catch (Exception e) {
			log.error("Failed to update prediction rule", e);
			return ResponseEntity.badRequest().body("Failed to update prediction rule: " + e.getMessage());
		}
	}

	/**
	 * Delete a prediction rule
	 */
	@DeleteMapping("/{id}")
	public ResponseEntity<?> deleteRule(@PathVariable String id) {
		try {
			predictionService.getRuleById(id)
				.orElseThrow(() -> new IllegalArgumentException("Prediction rule not found"));

			predictionService.deleteRule(id);
			return ResponseEntity.ok().build();
		} catch (Exception e) {
			log.error("Failed to delete prediction rule", e);
			return ResponseEntity.badRequest().body("Failed to delete prediction rule: " + e.getMessage());
		}
	}

	/**
	 * Regenerate predictions for a specific rule
	 */
	@PostMapping("/{id}/regenerate")
	public ResponseEntity<?> regeneratePredictions(
		@PathVariable String id,
		@RequestParam(required = false) String targetMonth
	) {
		try {
			predictionService.getRuleById(id)
				.orElseThrow(() -> new IllegalArgumentException("Prediction rule not found"));

			predictionService.regeneratePredictionsForRuleByMonth(id, targetMonth);
			return ResponseEntity.ok().body("Predictions regenerated successfully");
		} catch (Exception e) {
			log.error("Failed to regenerate predictions", e);
			return ResponseEntity.badRequest().body("Failed to regenerate predictions: " + e.getMessage());
		}
	}

	/**
	 * Generate predictions for all enabled rules
	 */
	@PostMapping("/generate-all")
	public ResponseEntity<?> generateAllPredictions(
		@RequestParam(required = false) String targetMonth
	) {
		try {
			predictionService.generatePredictionsByMonth(targetMonth);
			return ResponseEntity.ok().body("Predictions generated successfully for all enabled rules");
		} catch (Exception e) {
			log.error("Failed to generate predictions", e);
			return ResponseEntity.badRequest().body("Failed to generate predictions: " + e.getMessage());
		}
	}

	/**
	 * Recalculate predictions for a specific month by reapplying actual transactions
	 */
	@PostMapping("/recalculate")
	public ResponseEntity<?> recalculatePredictions(
		@RequestParam String month
	) {
		try {
			predictionService.recalculatePredictionsForMonth(month);
			return ResponseEntity.ok().body("Predictions recalculated successfully for month " + month);
		} catch (Exception e) {
			log.error("Failed to recalculate predictions for month {}", month, e);
			return ResponseEntity.badRequest().body("Failed to recalculate predictions: " + e.getMessage());
		}
	}
}

