package com.emailtodb.emailtodb.services;

import com.emailtodb.emailtodb.entities.EmailMessage;
import com.emailtodb.emailtodb.enums.EmailProvider;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;

/**
 * Coordinator service for fetching emails from multiple providers (Gmail and Outlook)
 */
@Service
public class EmailFetchCoordinatorService {

    private static final Logger logger = LoggerFactory.getLogger(EmailFetchCoordinatorService.class);

    @Autowired
    private GmailEmailFetchService gmailEmailFetchService;

    @Autowired
    private OutlookEmailFetchService outlookEmailFetchService;

    /**
     * Fetch messages from all configured email providers
     * @return Map of provider to list of EmailMessage objects
     */
    public Map<EmailProvider, List<EmailMessage>> fetchAllMessages() {
        Map<EmailProvider, List<EmailMessage>> allMessages = new HashMap<>();
        
        // Fetch Gmail messages
        try {
            List<Object> gmailMessages = gmailEmailFetchService.fetchMessages();
            List<EmailMessage> convertedGmailMessages = new ArrayList<>();
            for (Object message : gmailMessages) {
                convertedGmailMessages.add(gmailEmailFetchService.convertToEmailMessage(message));
            }
            allMessages.put(EmailProvider.GMAIL, convertedGmailMessages);
            logger.info("Fetched {} Gmail messages", convertedGmailMessages.size());
        } catch (Exception e) {
            logger.error("Failed to fetch Gmail messages: {}", e.getMessage());
            allMessages.put(EmailProvider.GMAIL, new ArrayList<>());
        }

        // Fetch Outlook messages
        try {
            List<Object> outlookMessages = outlookEmailFetchService.fetchMessages();
            List<EmailMessage> convertedOutlookMessages = new ArrayList<>();
            for (Object message : outlookMessages) {
                convertedOutlookMessages.add(outlookEmailFetchService.convertToEmailMessage(message));
            }
            allMessages.put(EmailProvider.OUTLOOK, convertedOutlookMessages);
            logger.info("Fetched {} Outlook messages", convertedOutlookMessages.size());
        } catch (Exception e) {
            logger.error("Failed to fetch Outlook messages: {}", e.getMessage());
            allMessages.put(EmailProvider.OUTLOOK, new ArrayList<>());
        }

        return allMessages;
    }

    /**
     * Fetch messages since a specific date from all providers
     * @param sinceDate The date since which to fetch messages
     * @return Map of provider to list of EmailMessage objects
     */
    public Map<EmailProvider, List<EmailMessage>> fetchMessagesSince(Date sinceDate) {
        Map<EmailProvider, List<EmailMessage>> allMessages = new HashMap<>();
        
        // Fetch Gmail messages since date
        try {
            List<Object> gmailMessages = gmailEmailFetchService.fetchMessagesSince(sinceDate);
            List<EmailMessage> convertedGmailMessages = new ArrayList<>();
            for (Object message : gmailMessages) {
                convertedGmailMessages.add(gmailEmailFetchService.convertToEmailMessage(message));
            }
            allMessages.put(EmailProvider.GMAIL, convertedGmailMessages);
            logger.info("Fetched {} Gmail messages since {}", convertedGmailMessages.size(), sinceDate);
        } catch (Exception e) {
            logger.error("Failed to fetch Gmail messages since {}: {}", sinceDate, e.getMessage());
            allMessages.put(EmailProvider.GMAIL, new ArrayList<>());
        }

        // Fetch Outlook messages since date
        try {
            List<Object> outlookMessages = outlookEmailFetchService.fetchMessagesSince(sinceDate);
            List<EmailMessage> convertedOutlookMessages = new ArrayList<>();
            for (Object message : outlookMessages) {
                convertedOutlookMessages.add(outlookEmailFetchService.convertToEmailMessage(message));
            }
            allMessages.put(EmailProvider.OUTLOOK, convertedOutlookMessages);
            logger.info("Fetched {} Outlook messages since {}", convertedOutlookMessages.size(), sinceDate);
        } catch (Exception e) {
            logger.error("Failed to fetch Outlook messages since {}: {}", sinceDate, e.getMessage());
            allMessages.put(EmailProvider.OUTLOOK, new ArrayList<>());
        }

        return allMessages;
    }
}
