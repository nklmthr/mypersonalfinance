package com.nklmthr.finance.personal.service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.nklmthr.finance.personal.scheduler.AbstractDataExtractionService;

@Service
public class DataExtractionService {

    private static final Logger logger = LoggerFactory.getLogger(DataExtractionService.class);

    @Autowired
    private List<AbstractDataExtractionService> dataExtractionServices;

    /**
     * Manually trigger all data extraction services
     * This method runs asynchronously to avoid blocking the UI
     */
    @Async
    public CompletableFuture<String> triggerAllDataExtractionServices() {
        logger.info("Manual trigger: Starting all data extraction services");
        
        int successCount = 0;
        int failureCount = 0;
        
        for (AbstractDataExtractionService service : dataExtractionServices) {
            try {
                logger.info("Running data extraction service: {}", service.getClass().getSimpleName());
                service.run();
                successCount++;
                logger.info("Successfully completed: {}", service.getClass().getSimpleName());
            } catch (Exception e) {
                failureCount++;
                logger.error("Failed to run data extraction service: {}", service.getClass().getSimpleName(), e);
            }
        }
        
        String result = String.format("Data extraction completed. Success: %d, Failures: %d", successCount, failureCount);
        logger.info("Manual trigger completed: {}", result);
        
        return CompletableFuture.completedFuture(result);
    }

    /**
     * Get the list of available data extraction services
     */
    public List<String> getAvailableServices() {
        return dataExtractionServices.stream()
                .map(service -> service.getClass().getSimpleName())
                .toList();
    }
}
