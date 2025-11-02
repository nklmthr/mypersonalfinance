package com.nklmthr.finance.personal.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.scheduler.ConfigurableDataExtractionService;
import com.nklmthr.finance.personal.scheduler.config.ExtractionConfigRegistry;

/**
 * Service for manually triggering data extraction.
 * Works with the new ConfigurableDataExtractionService which processes all bank configurations.
 */
@Service
public class DataExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(DataExtractionService.class);

    @Autowired
    private ConfigurableDataExtractionService configurableDataExtractionService;

    @Autowired
    private ExtractionConfigRegistry configRegistry;

    /**
     * Manually trigger all data extraction services
     * This method runs asynchronously to avoid blocking the UI
     */
    @Async
    public CompletableFuture<String> triggerAllDataExtractionServices() {
        logger.info("Manual trigger: Starting data extraction for all bank configurations");
        
        try {
            configurableDataExtractionService.run();
            
            int configCount = configRegistry.getAllConfigs().size();
            String result = String.format("Data extraction completed successfully for %d bank configurations", configCount);
            logger.info("Manual trigger completed: {}", result);
            
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            String error = "Data extraction failed: " + e.getMessage();
            logger.error("Manual trigger failed", e);
            return CompletableFuture.completedFuture(error);
        }
    }

    /**
     * Manually trigger data extraction for specific configurations only
     * This method runs asynchronously to avoid blocking the UI
     */
    @Async
    public CompletableFuture<String> triggerSpecificDataExtractionServices(List<String> configNames) {
        logger.info("Manual trigger: Starting data extraction for specific configurations: {}", configNames);
        
        try {
            configurableDataExtractionService.runForSpecificConfigs(configNames);
            
            String result = String.format("Data extraction completed successfully for %d bank configurations", configNames.size());
            logger.info("Manual trigger completed: {}", result);
            
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            String error = "Data extraction failed: " + e.getMessage();
            logger.error("Manual trigger failed", e);
            return CompletableFuture.completedFuture(error);
        }
    }

    /**
     * Get the list of available data extraction configurations
     */
    public List<String> getAvailableServices() {
        return configRegistry.getAllConfigs().stream()
                .map(config -> config.getName())
                .toList();
    }
}
