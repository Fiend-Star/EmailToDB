package com.emailtodb.emailtodb.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Enhanced configuration for Spring Retry with custom RetryTemplate and listeners
 */
@Configuration
@EnableRetry
public class RetryConfig {
    
    private static final Logger logger = LoggerFactory.getLogger(RetryConfig.class);
    
    /**
     * Creates a RetryTemplate for programmatic retry operations
     * @return configured RetryTemplate
     */
    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        
        // Configure backoff policy
        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000); // 1 second
        backOffPolicy.setMultiplier(2.0);       // double the interval each time
        backOffPolicy.setMaxInterval(60000);    // max 1 minute wait
        retryTemplate.setBackOffPolicy(backOffPolicy);
        
        // Configure retry policy
        Map<Class<? extends Throwable>, Boolean> retryableExceptions = new HashMap<>();
        retryableExceptions.put(java.io.IOException.class, true);
        retryableExceptions.put(java.net.SocketTimeoutException.class, true);
        retryableExceptions.put(java.net.ConnectException.class, true);
        retryableExceptions.put(javax.net.ssl.SSLException.class, true);
        retryableExceptions.put(java.util.concurrent.TimeoutException.class, true);
        
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy(3, retryableExceptions, true);
        retryTemplate.setRetryPolicy(retryPolicy);
        
        // Add retry listeners for better visibility into retry operations
        retryTemplate.registerListener(new LoggingRetryListener());
        
        return retryTemplate;
    }
    
    /**
     * A retry listener that logs retry operations for observability
     */
    public static class LoggingRetryListener implements RetryListener {
        @Override
        public <T, E extends Throwable> boolean open(RetryContext context, RetryCallback<T, E> callback) {
            return true; // always proceed with retry
        }

        @Override
        public <T, E extends Throwable> void close(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
            if (context.getRetryCount() > 0) {
                logger.info("Retry operation completed after {} attempts", context.getRetryCount() + 1);
            }
        }

        @Override
        public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
            logger.warn("Retry attempt {} failed with error: {}", 
                    context.getRetryCount(), 
                    throwable.getMessage());
            
            if (context.getRetryCount() >= context.getAttribute(RetryContext.RETRY_EXHAUSTED)) {
                logger.error("Retry operation failed after maximum attempts", throwable);
            }
        }
    }
}
