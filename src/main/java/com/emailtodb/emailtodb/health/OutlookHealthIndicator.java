package com.emailtodb.emailtodb.health;

import com.emailtodb.emailtodb.config.OutlookConfig;
import com.emailtodb.emailtodb.services.OutlookExceptionHandler;
import com.microsoft.graph.requests.GraphServiceClient;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Enhanced health indicator for Outlook connectivity
 * Used by Spring Boot Actuator to report Outlook health status with more detailed diagnostics
 */
@Component
public class OutlookHealthIndicator implements HealthIndicator {

    private static final Logger logger = LoggerFactory.getLogger(OutlookHealthIndicator.class);
    private static final int HEALTH_CACHE_TTL_MS = 60000; // 1 minute

    @Autowired
    private OutlookConfig outlookConfig;
    
    @Autowired
    private OutlookExceptionHandler exceptionHandler;

    // Cache health status to avoid too frequent calls to Microsoft Graph API
    private final AtomicReference<CachedHealth> cachedHealth = new AtomicReference<>(null);

    @Override
    public Health health() {
        CachedHealth cached = cachedHealth.get();
        long currentTime = System.currentTimeMillis();
        
        // Return cached health if still valid
        if (cached != null && (currentTime - cached.timestamp) < HEALTH_CACHE_TTL_MS) {
            return cached.health;
        }
        
        // Perform fresh health check
        Health freshHealth = checkOutlookHealth();
        cachedHealth.set(new CachedHealth(freshHealth, currentTime));
        return freshHealth;
    }
    
    /**
     * Perform actual health check against Microsoft Graph API
     * @return Health object with status and details
     */
    private Health checkOutlookHealth() {
        try {
            // Check if Outlook configuration is valid
            String userEmail = outlookConfig.getUserEmail();
            
            if (userEmail == null || userEmail.isEmpty()) {
                return Health.down()
                        .withDetail("reason", "Outlook configuration is incomplete. User email is missing.")
                        .withDetail("status", "CONFIGURATION_ERROR")
                        .build();
            }
            
            try {
                // Get the client
                GraphServiceClient<Request> client = outlookConfig.getGraphServiceClient();
                
                // Perform a lightweight request to verify connectivity
                var user = client.users(userEmail)
                        .buildRequest(outlookConfig.createStandardOptions())
                        .select("displayName,mail")
                        .get();
                
                if (user == null) {
                    return Health.down()
                            .withDetail("reason", "Could not retrieve user information")
                            .withDetail("status", "API_ERROR")
                            .build();
                }
                
                return Health.up()
                        .withDetail("user", userEmail)
                        .withDetail("displayName", user.displayName)
                        .withDetail("status", "CONNECTED")
                        .build();
                
            } catch (Exception e) {
                // Use the exception handler to get standardized error information
                OutlookExceptionHandler.ErrorInfo errorInfo = exceptionHandler.handleOutlookException(e);
                
                Status status = determineStatusFromErrorType(errorInfo.getErrorType());
                
                return Health.status(status)
                        .withDetail("reason", errorInfo.getErrorMessage())
                        .withDetail("status", errorInfo.getErrorType())
                        .withDetail("exception", e.getClass().getName())
                        .build();
            }
        } catch (Exception e) {
            logger.warn("Outlook health check failed with unexpected error: {}", e.getMessage());
            return Health.down()
                    .withDetail("reason", e.getMessage())
                    .withDetail("status", "UNEXPECTED_ERROR")
                    .withDetail("exception", e.getClass().getName())
                    .build();
        }
    }
    
    /**
     * Determine appropriate Spring Boot Actuator Status based on error type
     * @param errorType the error type from OutlookExceptionHandler
     * @return appropriate Status (DOWN, OUT_OF_SERVICE, or UNKNOWN)
     */
    private Status determineStatusFromErrorType(String errorType) {
        switch (errorType) {
            case OutlookExceptionHandler.AUTHENTICATION_ERROR:
            case OutlookExceptionHandler.PERMISSION_ERROR:
                return Status.DOWN; // Critical errors requiring intervention
                
            case OutlookExceptionHandler.API_LIMIT_EXCEEDED:
            case OutlookExceptionHandler.CONNECTION_ERROR:
                return Status.OUT_OF_SERVICE; // Temporary issues that might resolve
                
            case OutlookExceptionHandler.RESOURCE_NOT_FOUND:
            case OutlookExceptionHandler.GENERAL_ERROR:
            default:
                return Status.UNKNOWN; // Unclear state
        }
    }
    
    /**
     * Inner class to hold cached health data
     */
    private static class CachedHealth {
        private final Health health;
        private final long timestamp;
        
        public CachedHealth(Health health, long timestamp) {
            this.health = health;
            this.timestamp = timestamp;
        }
    }
}
