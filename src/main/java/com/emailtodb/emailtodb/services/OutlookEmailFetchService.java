package com.emailtodb.emailtodb.services;

import com.emailtodb.emailtodb.config.OutlookConfig;
import com.emailtodb.emailtodb.entities.EmailMessage;
import com.emailtodb.emailtodb.enums.EmailProvider;
import com.emailtodb.emailtodb.services.OutlookExceptionHandler.ErrorInfo;
import com.emailtodb.emailtodb.services.interfaces.EmailFetchServiceInterface;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.MessageCollectionPage;
import com.microsoft.graph.requests.GraphServiceClient;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/**
 * Service for fetching emails from Microsoft Outlook using Microsoft Graph API
 */
@Service
public class OutlookEmailFetchService implements EmailFetchServiceInterface {

    private static final Logger logger = LoggerFactory.getLogger(OutlookEmailFetchService.class);

    @Autowired
    private OutlookConfig outlookConfig;
    
    @Autowired
    private OutlookExceptionHandler exceptionHandler;
    
    @Value("${outlook.retry.maxAttempts:3}")
    private int maxRetryAttempts;
    
    @Value("${outlook.retry.initialBackoffMs:1000}")
    private long initialBackoffMs;

    @Override
    @Retryable(value = {IOException.class}, 
               maxAttempts = 3, 
               backoff = @Backoff(delay = 1000, multiplier = 2))
    public List<Object> fetchMessages() throws IOException {
        logger.info("Fetching all Outlook messages started");

        List<Object> messages = new ArrayList<>();
        try {
            GraphServiceClient<Request> graphClient = outlookConfig.getGraphServiceClient();
            String userEmail = outlookConfig.getUserEmail();

            if (userEmail == null || userEmail.isEmpty()) {
                logger.error("Outlook user email is not configured");
                return messages;
            }

            // Fetch messages from inbox
            MessageCollectionPage messagesPage = graphClient.users(userEmail)
                    .mailFolders("inbox")
                    .messages()
                    .buildRequest()
                    .get();

            if (messagesPage != null) {
                List<Message> currentPageMessages = messagesPage.getCurrentPage();
                messages.addAll(currentPageMessages);

                // Handle pagination
                while (messagesPage.getNextPage() != null) {
                    try {
                        messagesPage = messagesPage.getNextPage().buildRequest().get();
                        if (messagesPage.getCurrentPage() != null) {
                            messages.addAll(messagesPage.getCurrentPage());
                        }
                    } catch (Exception e) {
                        OutlookErrorHandlingService.ErrorInfo errorInfo = errorHandlingService.handleOutlookException(e);
                        logger.warn("Error fetching next page: {} - {}", errorInfo.getErrorType(), errorInfo.getErrorMessage());
                        
                        // If rate limited, stop fetching more pages
                        if (errorHandlingService.isRateLimitExceeded(e)) {
                            logger.warn("API rate limit exceeded, stopping further requests");
                            break;
                        }
                        // If it's an authentication error, rethrow to trigger retry
                        if (errorHandlingService.isAuthenticationError(e)) {
                            throw new IOException("Authentication error fetching Outlook messages", e);
                        }
                    }
                }
            }

            logger.info("Fetched {} Outlook messages", messages.size());

        } catch (Exception e) {
            OutlookErrorHandlingService.ErrorInfo errorInfo = errorHandlingService.handleOutlookException(e);
            logger.error("An error occurred while fetching Outlook messages: {} - {}", 
                    errorInfo.getErrorType(), errorInfo.getErrorMessage(), e);
            throw new IOException("Failed to fetch Outlook messages: " + errorInfo.getErrorMessage(), e);
        }

        logger.info("Fetching Outlook messages completed");
        return messages;
    }

    @Override
    @Retryable(value = {IOException.class}, 
               maxAttempts = 3, 
               backoff = @Backoff(delay = 1000, multiplier = 2))
    public List<Object> fetchMessagesSince(Date sinceDate) throws IOException {
        logger.info("Fetching Outlook messages since {}", sinceDate);

        List<Object> messages = new ArrayList<>();
        try {
            GraphServiceClient<Request> graphClient = outlookConfig.getGraphServiceClient();
            String userEmail = outlookConfig.getUserEmail();

            if (userEmail == null || userEmail.isEmpty()) {
                logger.error("Outlook user email is not configured");
                return messages;
            }

            // Convert Date to OffsetDateTime for Microsoft Graph API
            OffsetDateTime sinceDateOffset = OffsetDateTime.ofInstant(sinceDate.toInstant(), ZoneOffset.UTC);
            
            // Format the date for the filter query
            SimpleDateFormat iso8601Format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
            iso8601Format.setTimeZone(TimeZone.getTimeZone("UTC"));
            String formattedDate = iso8601Format.format(sinceDate);

            // Build the filter query
            String filter = String.format("receivedDateTime ge %s", formattedDate);
            
            logger.debug("Using Outlook filter query: {}", filter);

            // Fetch messages with date filter
            MessageCollectionPage messagesPage = graphClient.users(userEmail)
                    .mailFolders("inbox")
                    .messages()
                    .buildRequest()
                    .filter(filter)
                    .orderBy("receivedDateTime desc")
                    .get();

            if (messagesPage != null) {
                List<Message> currentPageMessages = messagesPage.getCurrentPage();
                messages.addAll(currentPageMessages);

                // Handle pagination
                while (messagesPage.getNextPage() != null) {
                    try {
                        messagesPage = messagesPage.getNextPage().buildRequest().get();
                        if (messagesPage.getCurrentPage() != null) {
                            messages.addAll(messagesPage.getCurrentPage());
                        }
                    } catch (Exception e) {
                        OutlookErrorHandlingService.ErrorInfo errorInfo = errorHandlingService.handleOutlookException(e);
                        logger.warn("Error fetching next page since {}: {} - {}", 
                                sinceDate, errorInfo.getErrorType(), errorInfo.getErrorMessage());
                        
                        // If rate limited, stop fetching more pages
                        if (errorHandlingService.isRateLimitExceeded(e)) {
                            logger.warn("API rate limit exceeded, stopping further requests");
                            break;
                        }
                        // If it's an authentication error, rethrow to trigger retry
                        if (errorHandlingService.isAuthenticationError(e)) {
                            throw new IOException("Authentication error fetching Outlook messages", e);
                        }
                    }
                }
            }

            logger.info("Fetched {} Outlook messages since {}", messages.size(), sinceDate);

        } catch (Exception e) {
            OutlookErrorHandlingService.ErrorInfo errorInfo = errorHandlingService.handleOutlookException(e);
            logger.error("An error occurred while fetching Outlook messages since {}: {} - {}", 
                    sinceDate, errorInfo.getErrorType(), errorInfo.getErrorMessage(), e);
            throw new IOException("Failed to fetch Outlook messages since date: " + errorInfo.getErrorMessage(), e);
        }

        return messages;
    }

    @Override
    public EmailMessage convertToEmailMessage(Object message) {
        if (!(message instanceof Message)) {
            throw new IllegalArgumentException("Message must be a Microsoft Graph Message object");
        }

        Message outlookMessage = (Message) message;
        EmailMessage emailMessage = new EmailMessage();

        try {
            // Set basic properties
            emailMessage.setMessageId(outlookMessage.id);
            emailMessage.setSubject(outlookMessage.subject != null ? outlookMessage.subject : "");
            
            // Set sender information
            String fromEmail = "";
            if (outlookMessage.from != null && outlookMessage.from.emailAddress != null) {
                fromEmail = outlookMessage.from.emailAddress.address != null ? outlookMessage.from.emailAddress.address : "";
                if (outlookMessage.from.emailAddress.name != null) {
                    fromEmail = outlookMessage.from.emailAddress.name + " <" + fromEmail + ">";
                }
            }
            emailMessage.setFrom(fromEmail);

            // Set recipient information
            StringBuilder toEmails = new StringBuilder();
            if (outlookMessage.toRecipients != null) {
                for (int i = 0; i < outlookMessage.toRecipients.size(); i++) {
                    if (i > 0) toEmails.append(", ");
                    var recipient = outlookMessage.toRecipients.get(i);
                    if (recipient.emailAddress != null && recipient.emailAddress.address != null) {
                        toEmails.append(recipient.emailAddress.address);
                    }
                }
            }
            emailMessage.setTo(toEmails.toString());

            // Set CC recipients
            StringBuilder ccEmails = new StringBuilder();
            if (outlookMessage.ccRecipients != null) {
                for (int i = 0; i < outlookMessage.ccRecipients.size(); i++) {
                    if (i > 0) ccEmails.append(", ");
                    var recipient = outlookMessage.ccRecipients.get(i);
                    if (recipient.emailAddress != null && recipient.emailAddress.address != null) {
                        ccEmails.append(recipient.emailAddress.address);
                    }
                }
            }
            emailMessage.setCc(ccEmails.toString());

            // Set BCC recipients
            StringBuilder bccEmails = new StringBuilder();
            if (outlookMessage.bccRecipients != null) {
                for (int i = 0; i < outlookMessage.bccRecipients.size(); i++) {
                    if (i > 0) bccEmails.append(", ");
                    var recipient = outlookMessage.bccRecipients.get(i);
                    if (recipient.emailAddress != null && recipient.emailAddress.address != null) {
                        bccEmails.append(recipient.emailAddress.address);
                    }
                }
            }
            emailMessage.setBcc(bccEmails.toString());

            // Set date received
            if (outlookMessage.receivedDateTime != null) {
                emailMessage.setDateReceived(Date.from(outlookMessage.receivedDateTime.toInstant()));
            }

            // Set email body
            String body = "";
            String briefBody = "";
            
            if (outlookMessage.body != null) {
                body = outlookMessage.body.content != null ? outlookMessage.body.content : "";
                
                // Create a brief version by removing HTML tags and truncating
                briefBody = body.replaceAll("<[^>]*>", "").trim();
                if (briefBody.length() > 500) {
                    briefBody = briefBody.substring(0, 500) + "...";
                }
            }

            emailMessage.setBody(body);
            emailMessage.setBriefBody(briefBody);

            // Set email provider
            emailMessage.setEmailProvider(EmailProvider.OUTLOOK);

            // Set default status values
            emailMessage.setStatusUploadStaging(false);
            emailMessage.setStatusMigrate(false);

            logger.debug("Converted Outlook message to EmailMessage: {}", emailMessage.getSubject());

        } catch (Exception e) {
            logger.error("Error converting Outlook message to EmailMessage: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to convert Outlook message", e);
        }

        return emailMessage;
    }

    @Override
    public String getProviderName() {
        return EmailProvider.OUTLOOK.getDisplayName();
    }
}
