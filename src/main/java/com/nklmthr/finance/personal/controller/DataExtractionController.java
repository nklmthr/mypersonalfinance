package com.nklmthr.finance.personal.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

import com.nklmthr.finance.personal.service.DataExtractionService;

@RestController
@RequestMapping("/api/data-extraction")
public class DataExtractionController {

    private static final Logger logger = LoggerFactory.getLogger(DataExtractionController.class);

    @Autowired
    private DataExtractionService dataExtractionService;

    /**
     * Manually trigger all data extraction services
     * Returns immediately while processing runs in background
     */
    @PostMapping("/trigger")
    public ResponseEntity<Map<String, Object>> triggerDataExtraction(
        @RequestParam(name = "services", required = false) List<String> services
    ) {
        logger.info("Manual data extraction trigger requested");
        
        try {
            // Start async processing - all or subset
            if (services == null || services.isEmpty()) {
                dataExtractionService.triggerAllDataExtractionServices();
            } else {
                dataExtractionService.triggerSpecificServices(services);
            }
            
            return ResponseEntity.ok(Map.of(
                "status", "started",
                "message", "Data extraction started in background. Check logs for progress.",
                "services", dataExtractionService.getAvailableServices()
            ));
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
     * Get list of available data extraction services
     */
    @GetMapping("/services")
    public ResponseEntity<Map<String, Object>> getAvailableServices() {
        try {
            List<String> services = dataExtractionService.getAvailableServices();
            return ResponseEntity.ok(Map.of(
                "services", services,
                "count", services.size()
            ));
        } catch (Exception e) {
            logger.error("Failed to get available services", e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to get services: " + e.getMessage()));
        }
    }
}
