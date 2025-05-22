package com.emailtodb.emailtodb.services;

import com.emailtodb.emailtodb.entities.EmailMessage;
import com.emailtodb.emailtodb.services.OutlookExceptionHandler.ErrorInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Enhanced Outlook email processing service with comprehensive error handling
 * This service demonstrates how to use the OutlookExceptionHandler with OutlookEmailFetchService
 */
@Service
public class OutlookEmailProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(OutlookEmailProcessingService.class);
    
    @Autowired
    private OutlookEmailFetchService emailFetchService;
    
    @Autowired
    private OutlookExceptionHandler exceptionHandler;
    
    @Autowired
    private EmailSaveService emailSaveService;
    
    @Value("${outlook.enabled:false}")
    private boolean outlookEnabled;
    
    @Value("${outlook.batch.size:50}")
    private int batchSize;
    
    /**
     * Process new Outlook emails - this shows an example of using the enhanced error handling
     */
    @Scheduled(cron = "${outlook.email.process.cron:0 */30 * * * *}")
    public void processNewOutlookEmails() {
        if (!outlookEnabled) {
            logger.debug("Outlook email processing is disabled");
            return;
        }
        
        logger.info("Starting Outlook email processing");
        
        try {
            // Get the most recent processing date (or fallback to default)
            Date sinceDate = getLastProcessedDate();
            
            // Fetch new messages since the last processing date
            List<Object> messages = emailFetchService.fetchMessagesSince(sinceDate);
            logger.info("Fetched {} Outlook messages to process", messages.size());
            
            // Process in batches to avoid memory issues with large email volumes
            List<List<Object>> batches = createBatches(messages, batchSize);
            for (List<Object> batch : batches) {
                processBatch(batch);
            }
            
            // Update the last processed date
            updateLastProcessedDate(new Date());
            logger.info("Outlook email processing completed successfully");
            
        } catch (Exception e) {
            ErrorInfo errorInfo = exceptionHandler.handleOutlookException(e);
            logger.error("Error processing Outlook emails: {} - {}", 
                    errorInfo.getErrorType(), errorInfo.getErrorMessage(), e);
        }
    }
    
    /**
     * Process a batch of Outlook messages
     * @param batch The batch of messages to process
     */
    private void processBatch(List<Object> batch) {
        logger.debug("Processing batch of {} Outlook messages", batch.size());
        
        List<EmailMessage> emailMessages = batch.stream()
                .map(message -> {
                    try {
                        return emailFetchService.convertToEmailMessage(message);
                    } catch (Exception e) {
                        ErrorInfo errorInfo = exceptionHandler.handleOutlookException(e);
                        logger.warn("Error converting Outlook message: {} - {}", 
                                errorInfo.getErrorType(), errorInfo.getErrorMessage(), e);
                        return null;
                    }
                })
                .filter(msg -> msg != null)
                .collect(Collectors.toList());
        
        // Save email messages to database
        try {
            emailSaveService.saveMessages(emailMessages);
            logger.info("Saved {} Outlook messages to database", emailMessages.size());
        } catch (Exception e) {
            ErrorInfo errorInfo = exceptionHandler.handleOutlookException(e);
            logger.error("Error saving Outlook messages to database: {} - {}", 
                    errorInfo.getErrorType(), errorInfo.getErrorMessage(), e);
        }
    }
    
    /**
     * Create batches from a list of messages
     * @param messages The list of messages
     * @param batchSize The batch size
     * @return List of batches
     */
    private List<List<Object>> createBatches(List<Object> messages, int batchSize) {
        return messages.stream()
                .collect(Collectors.groupingBy(message -> 
                        messages.indexOf(message) / batchSize))
                .values()
                .stream()
                .collect(Collectors.toList());
    }
    
    /**
     * Get the date of the last processed email
     * @return The last processed date or a default date (7 days ago)
     */
    private Date getLastProcessedDate() {
        // In a real implementation, this would retrieve from a database or configuration
        // For this example, we'll use a default of 7 days ago
        Date sevenDaysAgo = new Date(System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000);
        logger.debug("Using since date for Outlook processing: {}", sevenDaysAgo);
        return sevenDaysAgo;
    }
    
    /**
     * Update the last processed date
     * @param date The new last processed date
     */
    private void updateLastProcessedDate(Date date) {
        // In a real implementation, this would save to a database or configuration
        logger.debug("Updating last processed date for Outlook to: {}", date);
    }
}
