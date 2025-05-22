package com.emailtodb.emailtodb.services;

import com.emailtodb.emailtodb.circuitbreaker.CircuitBreaker;
import com.emailtodb.emailtodb.circuitbreaker.CircuitBreakerFactory;
import com.emailtodb.emailtodb.config.OutlookConfig;
import com.emailtodb.emailtodb.entities.EmailAttachment;
import com.emailtodb.emailtodb.entities.EmailMessage;
import com.emailtodb.emailtodb.repositories.EmailAttachmentRepository;
import com.emailtodb.emailtodb.services.OutlookExceptionHandler.ErrorInfo;
import com.emailtodb.emailtodb.services.interfaces.EmailAttachmentFetchServiceInterface;
import com.microsoft.graph.models.Attachment;
import com.microsoft.graph.models.FileAttachment;
import com.microsoft.graph.options.Option;
import com.microsoft.graph.requests.GraphServiceClient;
import jakarta.annotation.PostConstruct;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * Enhanced service for fetching email attachments from Microsoft Outlook using Microsoft Graph API
 * With improved error handling and retry capabilities
 */
@Service("enhancedOutlookAttachmentFetchService")
public class OutlookAttachmentFetchServiceEnhanced implements EmailAttachmentFetchServiceInterface {

    private static final Logger logger = LoggerFactory.getLogger(OutlookAttachmentFetchServiceEnhanced.class);

    @Autowired
    private OutlookConfig outlookConfig;

    @Autowired
    private EmailAttachmentRepository emailAttachmentRepository;
    
    @Autowired
    private OutlookExceptionHandler exceptionHandler;
    
    @Autowired
    private CircuitBreakerFactory circuitBreakerFactory;
    
    private CircuitBreaker<List<Attachment>> attachmentCircuitBreaker;
    private CircuitBreaker<FileAttachment> attachmentContentCircuitBreaker;

    private static final String UNKNOWN = "unknown";
    private static final String CIRCUIT_ATTACHMENT_LIST = "outlook-attachment-list";
    private static final String CIRCUIT_ATTACHMENT_CONTENT = "outlook-attachment-content";
    
    @PostConstruct
    public void init() {
        // Initialize circuit breakers for different operations
        attachmentCircuitBreaker = circuitBreakerFactory.getCircuitBreaker(
                CIRCUIT_ATTACHMENT_LIST,
                3, // 3 failures to open circuit
                Duration.ofSeconds(30), // 30 seconds reset timeout
                this::isCircuitBreakerFailure
        );
        
        attachmentContentCircuitBreaker = circuitBreakerFactory.getCircuitBreaker(
                CIRCUIT_ATTACHMENT_CONTENT,
                5, // 5 failures to open circuit
                Duration.ofSeconds(60), // 60 seconds reset timeout
                this::isCircuitBreakerFailure
        );
        
        logger.info("Circuit breakers initialized for Outlook attachment fetching");
    }
    
    /**
     * Determine if an exception should trigger the circuit breaker
     */
    private boolean isCircuitBreakerFailure(Throwable throwable) {
        // Network issues and API limits should trigger the circuit breaker
        if (throwable instanceof IOException) {
            return true;
        }
        
        // Check for API limit errors in other exceptions
        if (throwable instanceof Exception) {
            Exception exception = (Exception) throwable;
            ErrorInfo errorInfo = exceptionHandler.handleOutlookException(exception);
            return OutlookExceptionHandler.API_LIMIT_EXCEEDED.equals(errorInfo.getErrorType()) ||
                   OutlookExceptionHandler.CONNECTION_ERROR.equals(errorInfo.getErrorType());
        }
        
        return false;
    }

    @Override
    public List<EmailAttachment> getAttachments(Object messageObject, EmailMessage emailMessage) 
            throws IOException, NoSuchAlgorithmException {
        if (messageObject instanceof String) {
            return getAttachments((String) messageObject, emailMessage);
        } else {
            logger.error("Unsupported message object type: {}", messageObject.getClass().getName());
            throw new IllegalArgumentException("Enhanced Outlook attachment service requires a String messageId");
        }
    }
    
    @Override
    public String getProviderName() {
        return "outlook-enhanced";
    }
    
    /**
     * Get attachments from an Outlook message
     * @param messageId The Outlook message ID
     * @param emailMessage The EmailMessage entity
     * @return List of EmailAttachment objects
     * @throws IOException if fetching fails
     * @throws NoSuchAlgorithmException if hashing fails
     */
    @Retryable(value = {IOException.class}, 
               maxAttempts = 3, 
               backoff = @Backoff(delay = 1000, multiplier = 2))
    public List<EmailAttachment> getAttachments(String messageId, EmailMessage emailMessage) 
            throws IOException, NoSuchAlgorithmException {
        
        logger.info("Getting Outlook attachments started for message: {}", messageId);
        
        List<EmailAttachment> attachments = new ArrayList<>();
        
        try {
            GraphServiceClient<Request> graphClient = outlookConfig.getGraphServiceClient();
            String userEmail = outlookConfig.getUserEmail();

            if (userEmail == null || userEmail.isEmpty()) {
                logger.error("Outlook user email is not configured");
                return attachments;
            }

            // Create standard options
            List<Option> options = outlookConfig.createStandardOptions();
            
            // Use circuit breaker for fetching attachments
            List<Attachment> attachmentList = fetchAttachmentsWithCircuitBreaker(graphClient, userEmail, messageId, options);
            
            // Process each attachment
            for (Attachment attachment : attachmentList) {
                if (attachment instanceof FileAttachment) {
                    try {
                        FileAttachment fileAttachment = (FileAttachment) attachment;
                        
                        // Ensure attachment has content bytes using circuit breaker if needed
                        fileAttachment = ensureAttachmentContent(graphClient, userEmail, messageId, fileAttachment, options);
                        
                        EmailAttachment emailAttachment = createEmailAttachment(fileAttachment, emailMessage);
                        
                        if (isNewAttachment(emailAttachment.getFileContentHash())) {
                            attachments.add(emailAttachment);
                            logger.info("Outlook attachment added: {}", emailAttachment.getFileName());
                        } else {
                            logger.info("Outlook attachment already exists: {}", emailAttachment.getFileName());
                        }
                    } catch (Exception e) {
                        ErrorInfo errorInfo = exceptionHandler.handleOutlookException(e);
                        logger.warn("Error processing individual attachment: {} - {}", 
                                errorInfo.getErrorType(), errorInfo.getErrorMessage());
                    }
                }
            }

            logger.info("Getting Outlook attachments completed: {} attachments", attachments.size());

        } catch (CircuitBreaker.CircuitBreakerOpenException e) {
            logger.error("Circuit breaker open, attachment fetching not possible at this time");
            throw new IOException("Outlook service unavailable due to circuit breaker: " + e.getMessage(), e);
        } catch (Exception e) {
            ErrorInfo errorInfo = exceptionHandler.handleOutlookException(e);
            logger.error("Error getting Outlook attachments for message {}: {} - {}", 
                    messageId, errorInfo.getErrorType(), errorInfo.getErrorMessage(), e);
            throw new IOException("Failed to get Outlook attachments: " + errorInfo.getErrorMessage(), e);
        }

        return attachments;
    }
    
    /**
     * Fetch attachments with circuit breaker protection
     */
    private List<Attachment> fetchAttachmentsWithCircuitBreaker(
            GraphServiceClient<Request> graphClient,
            String userEmail,
            String messageId,
            List<Option> options) throws Exception {
        
        Callable<List<Attachment>> fetchOperation = () -> {
            try {
                var attachmentPage = graphClient.users(userEmail)
                        .messages(messageId)
                        .attachments()
                        .buildRequest(options)
                        .get();
                
                if (attachmentPage != null && attachmentPage.getCurrentPage() != null) {
                    return attachmentPage.getCurrentPage();
                }
                return new ArrayList<>();
            } catch (Exception e) {
                logger.warn("Error fetching attachments: {}", e.getMessage());
                throw e;
            }
        };
        
        try {
            return attachmentCircuitBreaker.executeWithCircuitBreaker(fetchOperation);
        } catch (CircuitBreaker.CircuitBreakerOpenException e) {
            throw e; // Pass through circuit breaker exceptions
        } catch (Exception e) {
            logger.error("Error in attachment fetch operation", e);
            throw new IOException("Failed to fetch attachments from Outlook: " + e.getMessage(), e);
        }
    }
    
    /**
     * Ensure attachment has content, fetching it if needed, with circuit breaker protection
     */
    private FileAttachment ensureAttachmentContent(
            GraphServiceClient<Request> graphClient,
            String userEmail,
            String messageId,
            FileAttachment attachment,
            List<Option> options) throws Exception {
        
        // If content is already present, no need to fetch
        if (attachment.contentBytes != null && attachment.contentBytes.length > 0) {
            return attachment;
        }
        
        logger.debug("Fetching content for attachment: {}", attachment.name);
        
        Callable<FileAttachment> fetchContentOperation = () -> {
            try {
                return (FileAttachment) graphClient.users(userEmail)
                        .messages(messageId)
                        .attachments(attachment.id)
                        .buildRequest(options)
                        .get();
            } catch (Exception e) {
                logger.warn("Error fetching attachment content: {}", e.getMessage());
                throw e;
            }
        };
        
        try {
            return attachmentContentCircuitBreaker.executeWithCircuitBreaker(fetchContentOperation);
        } catch (CircuitBreaker.CircuitBreakerOpenException e) {
            throw e; // Pass through circuit breaker exceptions
        } catch (Exception e) {
            logger.error("Error in attachment content fetch operation for {}", attachment.name, e);
            throw new IOException("Failed to fetch attachment content: " + e.getMessage(), e);
        }
    }

    /**
     * Create an EmailAttachment from an Outlook FileAttachment
     * @param fileAttachment The Outlook FileAttachment
     * @param emailMessage The EmailMessage entity
     * @return EmailAttachment object
     * @throws NoSuchAlgorithmException if hashing fails
     */
    private EmailAttachment createEmailAttachment(FileAttachment fileAttachment, EmailMessage emailMessage) 
            throws NoSuchAlgorithmException {
        
        EmailAttachment attachment = new EmailAttachment();
        
        // Get file content
        byte[] fileContent = fileAttachment.contentBytes;
        if (fileContent == null) {
            fileContent = new byte[0];
            logger.warn("Attachment '{}' has no content bytes", fileAttachment.name);
        }
        
        attachment.setFileContent(fileContent);
        attachment.setFileName(determineFileName(fileAttachment));
        attachment.setFileExtension(determineFileExtension(fileAttachment));
        attachment.setEmailMessage(emailMessage);
        attachment.setFileContentHash(generateFileContentHash(fileContent));
        
        return attachment;
    }

    /**
     * Determine the file name from the attachment
     * @param fileAttachment The FileAttachment
     * @return The file name or "unknown" if not available
     */
    private String determineFileName(FileAttachment fileAttachment) {
        if (fileAttachment.name != null && !fileAttachment.name.isEmpty()) {
            return fileAttachment.name;
        }
        logger.debug("Attachment name is missing, using 'unknown'");
        return UNKNOWN;
    }

    /**
     * Determine the file extension from the attachment
     * @param fileAttachment The FileAttachment
     * @return The file extension
     */
    private String determineFileExtension(FileAttachment fileAttachment) {
        String fileName = determineFileName(fileAttachment);
        if (fileName != null && fileName.contains(".")) {
            return fileName.substring(fileName.lastIndexOf(".") + 1);
        }
        logger.debug("Could not determine file extension for '{}', using 'unknown'", fileName);
        return UNKNOWN;
    }

    /**
     * Generate SHA-256 hash for file content
     * @param fileContent The file content bytes
     * @return SHA-256 hash string
     * @throws NoSuchAlgorithmException if SHA-256 is not available
     */
    private String generateFileContentHash(byte[] fileContent) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(fileContent);
        
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Check if an attachment is new (not already in database)
     * @param fileContentHash The file content hash
     * @return true if the attachment is new, false otherwise
     */
    private boolean isNewAttachment(String fileContentHash) {
        return emailAttachmentRepository.findByFileContentHash(fileContentHash).isEmpty();
    }
}
