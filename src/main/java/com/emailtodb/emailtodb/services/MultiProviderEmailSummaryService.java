package com.emailtodb.emailtodb.services;

import com.emailtodb.emailtodb.config.GmailConfig;
import com.emailtodb.emailtodb.entities.EmailMessage;
import com.emailtodb.emailtodb.enums.EmailProvider;
import com.emailtodb.emailtodb.repositories.EmailMessageRepository;
import com.google.api.services.gmail.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Enhanced email summary service that supports multiple email providers (Gmail and Outlook)
 */
@Service
public class MultiProviderEmailSummaryService {

    private static final Logger logger = LoggerFactory.getLogger(MultiProviderEmailSummaryService.class);
    private static final String PROCESSED_LABEL = "Processed";
    private static final String FAILED_TO_PROCESS_LABEL = "FailedToProcess";
    private static final String USER_ID = "me";

    @Autowired
    private EmailMessageRepository emailMessageRepository;
    
    @Autowired
    private GmailConfig gmailConfig;
    
    @Autowired
    private EmailLabelService emailLabelService;
    
    @Autowired
    private EmailSaveService emailSaveService;
    
    @Autowired
    private EmailFetchCoordinatorService emailFetchCoordinatorService;
    
    @Autowired
    private GmailEmailFetchService gmailEmailFetchService;

    @Value("${email.providers.enabled:gmail}")
    private String enabledProviders;
    
    @Value("${gmail.sender.emailFilter}")
    private String emailFilter;
    
    @Value("${gmail.user.email}")
    private String userEmail;
    
    @Value("${gmail.user.email.summary.to}")
    private List<String> toEmails;
    
    @Value("${gmail.user.email.summary.cc}")
    private List<String> ccEmails;

    private int totalEmailsAdded;
    private int totalEmailsFailedToLoad;
    private int totalEmailsRead;

    /**
     * Fetch and save emails from all enabled providers
     */
    public void fetchAndSaveEmailsFromAllProviders() throws IOException {
        logger.info("Starting multi-provider email fetch and save process");

        totalEmailsRead = 0;
        totalEmailsAdded = 0;
        totalEmailsFailedToLoad = 0;

        processAllEmailsFromAllProviders();

        logger.info("Multi-provider email fetch and save process completed");
    }

    /**
     * Process emails from all enabled providers
     */
    public void processAllEmailsFromAllProviders() throws IOException {
        Set<String> providers = getEnabledProviders();
        
        StringBuilder summaryBuilder = new StringBuilder();
        
        for (String provider : providers) {
            try {
                EmailProvider emailProvider = EmailProvider.valueOf(provider.toUpperCase());
                String providerSummary = processEmailsForProvider(emailProvider);
                summaryBuilder.append(providerSummary).append("\n");
            } catch (Exception e) {
                logger.error("Failed to process emails for provider {}: {}", provider, e.getMessage());
                summaryBuilder.append("Failed to process emails for provider ").append(provider)
                           .append(": ").append(e.getMessage()).append("\n");
            }
        }

        // Send combined summary email if necessary
        String combinedSummary = summaryBuilder.toString();
        sendSummaryEmailIfNecessary(combinedSummary);
    }

    /**
     * Process emails for a specific provider
     */
    private String processEmailsForProvider(EmailProvider provider) throws IOException {
        logger.info("Processing emails for provider: {}", provider);
        
        StringBuilder providerSummary = new StringBuilder();
        providerSummary.append("=== ").append(provider.getDisplayName()).append(" Results ===\n");
        
        int providerEmailsRead = 0;
        int providerEmailsAdded = 0;
        int providerEmailsFailedToLoad = 0;

        try {
            // Fetch new messages for this provider
            Optional<EmailMessage> latestEmail = emailMessageRepository.findTopByOrderByDateReceivedDesc();
            Map<EmailProvider, List<EmailMessage>> allMessages = fetchNewMessagesForProvider(provider, latestEmail);
            
            List<EmailMessage> providerMessages = allMessages.get(provider);
            if (providerMessages != null) {
                providerEmailsRead = providerMessages.size();
                
                for (EmailMessage emailMessage : providerMessages) {
                    try {
                        if (!emailMessageRepository.existsByMessageId(emailMessage.getMessageId())) {
                            // Apply filtering logic (currently only for Gmail)
                            if (provider == EmailProvider.GMAIL) {
                                if (emailMessage.getBody().contains(emailFilter) || 
                                    emailMessage.getFrom().contains(emailFilter)) {
                                    
                                    saveEmailMessage(emailMessage);
                                    providerEmailsAdded++;
                                }
                            } else {
                                // For Outlook, save all emails for now (can be configured later)
                                saveEmailMessage(emailMessage);
                                providerEmailsAdded++;
                            }
                        }
                    } catch (Exception e) {
                        providerEmailsFailedToLoad++;
                        logger.error("Failed to process email {} from {}: {}", 
                                   emailMessage.getMessageId(), provider, e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error processing emails for provider {}: {}", provider, e.getMessage());
            throw e;
        }

        // Update totals
        totalEmailsRead += providerEmailsRead;
        totalEmailsAdded += providerEmailsAdded;
        totalEmailsFailedToLoad += providerEmailsFailedToLoad;

        // Build provider summary
        providerSummary.append("Emails read: ").append(providerEmailsRead).append("\n");
        providerSummary.append("Emails added: ").append(providerEmailsAdded).append("\n");
        providerSummary.append("Emails failed: ").append(providerEmailsFailedToLoad).append("\n");
        
        logger.info("Completed processing for {}: read={}, added={}, failed={}", 
                   provider, providerEmailsRead, providerEmailsAdded, providerEmailsFailedToLoad);
        
        return providerSummary.toString();
    }

    /**
     * Fetch new messages for a specific provider
     */
    private Map<EmailProvider, List<EmailMessage>> fetchNewMessagesForProvider(EmailProvider provider, Optional<EmailMessage> latestEmail) {
        Map<EmailProvider, List<EmailMessage>> result = new HashMap<>();
        
        try {
            if (latestEmail.isPresent()) {
                Date sinceDate = latestEmail.get().getDateReceived();
                if (sinceDate != null) {
                    Map<EmailProvider, List<EmailMessage>> allMessages = emailFetchCoordinatorService.fetchMessagesSince(sinceDate);
                    result.put(provider, allMessages.getOrDefault(provider, new ArrayList<>()));
                } else {
                    Map<EmailProvider, List<EmailMessage>> allMessages = emailFetchCoordinatorService.fetchAllMessages();
                    result.put(provider, allMessages.getOrDefault(provider, new ArrayList<>()));
                }
            } else {
                Map<EmailProvider, List<EmailMessage>> allMessages = emailFetchCoordinatorService.fetchAllMessages();
                result.put(provider, allMessages.getOrDefault(provider, new ArrayList<>()));
            }
        } catch (Exception e) {
            logger.error("Failed to fetch messages for provider {}: {}", provider, e.getMessage());
            result.put(provider, new ArrayList<>());
        }
        
        return result;
    }

    /**
     * Save email message and its attachments
     */
    private void saveEmailMessage(EmailMessage emailMessage) {
        try {
            if (emailMessage.getEmailProvider() == EmailProvider.GMAIL) {
                // For Gmail, we need to convert back to Gmail Message for attachment processing
                // This is a limitation of the current architecture that could be improved
                Message gmailMessage = findGmailMessageById(emailMessage.getMessageId());
                if (gmailMessage != null) {
                    emailSaveService.saveEmailMessageAndItsAttachmentsIfNotExists(gmailMessage, emailMessage);
                } else {
                    // Just save the email without attachments if we can't find the Gmail message
                    emailMessageRepository.save(emailMessage);
                }
            } else {
                // For Outlook, we'll implement a separate save method
                saveOutlookEmailMessage(emailMessage);
            }
        } catch (Exception e) {
            logger.error("Failed to save email message {}: {}", emailMessage.getMessageId(), e.getMessage());
            throw new RuntimeException("Failed to save email message", e);
        }
    }

    /**
     * Save Outlook email message (simplified version for now)
     */
    private void saveOutlookEmailMessage(EmailMessage emailMessage) {
        try {
            emailMessage.setStatusUploadStaging(true);
            emailMessageRepository.save(emailMessage);
            // TODO: Implement Outlook attachment handling
            logger.info("Saved Outlook email message: {}", emailMessage.getSubject());
        } catch (Exception e) {
            logger.error("Failed to save Outlook email message: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Find Gmail message by ID (helper method for backward compatibility)
     */
    private Message findGmailMessageById(String messageId) {
        try {
            return gmailEmailFetchService.getMessageById(messageId);
        } catch (Exception e) {
            logger.warn("Could not find Gmail message by ID {}: {}", messageId, e.getMessage());
            return null;
        }
    }

    /**
     * Get enabled email providers from configuration
     */
    private Set<String> getEnabledProviders() {
        Set<String> providers = new HashSet<>();
        if (enabledProviders != null && !enabledProviders.trim().isEmpty()) {
            String[] providerArray = enabledProviders.split(",");
            for (String provider : providerArray) {
                providers.add(provider.trim().toLowerCase());
            }
        } else {
            providers.add("gmail"); // Default to Gmail
        }
        return providers;
    }

    /**
     * Send summary email if necessary
     */
    private void sendSummaryEmailIfNecessary(String summary) throws IOException {
        if (!summary.isEmpty() && (totalEmailsAdded > 0 || totalEmailsFailedToLoad > 0)) {
            sendSummaryEmail(summary);
        } else {
            logger.info("No summary email sent as there were no new emails");
        }
    }

    /**
     * Send summary email using Gmail
     */
    private void sendSummaryEmail(String summary) throws IOException {
        try {
            Locale locale = new Locale("en", "US");
            TimeZone timeZone = TimeZone.getTimeZone("America/New_York");
            SimpleDateFormat dateFormat = new SimpleDateFormat("MM/dd/yyyy , EEEE, HH:mm:ss", locale);
            dateFormat.setTimeZone(timeZone);
            String formattedDate = dateFormat.format(new Date());

            String subject = "Multi-Provider Email Summary for " + formattedDate;
            
            StringBuilder fullSummary = new StringBuilder();
            fullSummary.append("=== MULTI-PROVIDER EMAIL PROCESSING SUMMARY ===\n\n");
            fullSummary.append("Total emails read: ").append(totalEmailsRead).append("\n");
            fullSummary.append("Total emails added: ").append(totalEmailsAdded).append("\n");
            fullSummary.append("Total emails failed: ").append(totalEmailsFailedToLoad).append("\n\n");
            fullSummary.append(summary);

            Message message = createMessageWithEmail(subject, fullSummary.toString());
            gmailConfig.getGmailServiceAccount().users().messages().send(USER_ID, message).execute();
            logger.info("Multi-provider summary email sent");
        } catch (Exception e) {
            logger.error("Error sending multi-provider summary email: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Create email message for sending
     */
    private Message createMessageWithEmail(String subject, String bodyText) {
        String cc = String.join(", ", ccEmails);
        String to = String.join(", ", toEmails);

        String emailContent = "From: %s%nTo: %s%nCc: %s%nSubject: %s%n%n%s"
                .formatted(userEmail, to, cc, subject, bodyText);

        byte[] emailBytes = emailContent.getBytes(StandardCharsets.UTF_8);
        String encodedEmail = Base64.getEncoder().encodeToString(emailBytes);
        
        Message message = new Message();
        message.setRaw(encodedEmail);
        return message;
    }
}
