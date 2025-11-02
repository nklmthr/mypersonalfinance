package com.nklmthr.finance.personal.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nklmthr.finance.personal.service.DataExtractionService;

@RestController
@RequestMapping("/api/data-extraction")
public class DataExtractionController {

    private static final Logger logger = LoggerFactory.getLogger(DataExtractionController.class);

    @Autowired
    private DataExtractionService dataExtractionService;

    /**
     * Manually trigger data extraction for specific configurations or all
     * Returns immediately while processing runs in background
     * @param request Optional body with "configurations" array. If empty/null, triggers all.
     */
    @PostMapping("/trigger")
    public ResponseEntity<Map<String, Object>> triggerDataExtraction(
            @RequestBody(required = false) Map<String, Object> request) {
        logger.info("Manual data extraction trigger requested");
        
        try {
            List<String> configsToTrigger = null;
            
            // Check if specific configurations were requested
            if (request != null && request.containsKey("configurations")) {
                Object configs = request.get("configurations");
                if (configs instanceof List<?>) {
                    configsToTrigger = ((List<?>) configs).stream()
                            .filter(obj -> obj instanceof String)
                            .map(obj -> (String) obj)
                            .toList();
                }
            }
            
            // Trigger selected or all configurations
            if (configsToTrigger != null && !configsToTrigger.isEmpty()) {
                logger.info("Triggering specific configurations: {}", configsToTrigger);
                dataExtractionService.triggerSpecificDataExtractionServices(configsToTrigger);
                return ResponseEntity.ok(Map.of(
                    "status", "started",
                    "message", "Data extraction started in background for selected banks.",
                    "configurations", configsToTrigger
                ));
            } else {
                logger.info("Triggering all configurations");
                dataExtractionService.triggerAllDataExtractionServices();
                List<String> allServices = dataExtractionService.getAvailableServices();
                return ResponseEntity.ok(Map.of(
                    "status", "started",
                    "message", "Data extraction started in background for all banks.",
                    "configurations", allServices
                ));
            }
        } catch (Exception e) {
            logger.error("Failed to trigger data extraction", e);
            return ResponseEntity.internalServerError()
                .body(Map.of(
                    "status", "error",
                    "message", "Failed to start data extraction: " + e.getMessage()
                ));
        }
    }

    /**
     * Get list of available bank configurations
     */
    @GetMapping("/configurations")
    public ResponseEntity<Map<String, Object>> getAvailableConfigurations() {
        try {
            List<String> configurations = dataExtractionService.getAvailableServices();
            return ResponseEntity.ok(Map.of(
                "configurations", configurations,
                "count", configurations.size()
            ));
        } catch (Exception e) {
            logger.error("Failed to get available configurations", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to get configurations: " + e.getMessage()));
        }
    }
}
