package com.emailtodb.emailtodb.scheduler;

import com.emailtodb.emailtodb.services.EmailSummaryService;
import com.emailtodb.emailtodb.services.MultiProviderEmailSummaryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;

/**
 * Scheduler that handles processing emails from multiple providers
 */
@Configuration
@EnableScheduling
public class MultiProviderJobScheduler {

    private static final Logger logger = LoggerFactory.getLogger(MultiProviderJobScheduler.class);

    @Autowired
    private MultiProviderEmailSummaryService multiProviderEmailSummaryService;
    
    @Autowired
    private EmailSummaryService legacyEmailSummaryService;

    @Value("${email.providers.enabled:gmail}")
    private String enabledProviders;
    
    @Value("${email.multi.provider.enabled:false}")
    private boolean multiProviderEnabled;

    /**
     * Scheduled job to fetch and process emails from all configured providers
     * Runs every 15 minutes (configurable)
     */
    @Scheduled(cron = "${email.fetch.cron:0 */15 * * * *}")
    public void processEmailsFromAllProviders() {
        logger.info("Starting scheduled job to process emails from providers: {}", enabledProviders);
        
        try {
            if (multiProviderEnabled) {
                // Use the multi-provider service if enabled
                multiProviderEmailSummaryService.fetchAndSaveEmailsFromAllProviders();
                logger.info("Multi-provider email processing completed successfully");
            } else {
                // Fall back to legacy Gmail-only processing
                logger.info("Using legacy Gmail-only email processing");
                legacyEmailSummaryService.fetchAndSaveEmailsConditionally();
            }
        } catch (IOException e) {
            logger.error("Error processing emails from providers {}: {}", enabledProviders, e.getMessage());
        }
    }
}
