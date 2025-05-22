package com.emailtodb.emailtodb.services;

import com.emailtodb.emailtodb.config.OutlookConfig;
import com.emailtodb.emailtodb.entities.EmailAttachment;
import com.emailtodb.emailtodb.entities.EmailMessage;
import com.emailtodb.emailtodb.repositories.EmailAttachmentRepository;
import com.emailtodb.emailtodb.services.OutlookExceptionHandler.ErrorInfo;
import com.microsoft.graph.models.Attachment;
import com.microsoft.graph.models.FileAttachment;
import com.microsoft.graph.requests.GraphServiceClient;
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
import java.util.ArrayList;
import java.util.List;

/**
 * Service for fetching email attachments from Microsoft Outlook using Microsoft Graph API
 * With enhanced error handling and retry capabilities
 */
@Service
public class OutlookAttachmentFetchService {

    private static final Logger logger = LoggerFactory.getLogger(OutlookAttachmentFetchService.class);

    @Autowired
    private OutlookConfig outlookConfig;

    @Autowired
    private EmailAttachmentRepository emailAttachmentRepository;
    
    @Autowired
    private OutlookExceptionHandler exceptionHandler;

    private static final String UNKNOWN = "unknown";

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

            // Fetch attachments for the message
            var attachmentPage = graphClient.users(userEmail)
                    .messages(messageId)
                    .attachments()
                    .buildRequest()
                    .get();

            if (attachmentPage != null && attachmentPage.getCurrentPage() != null) {
                for (Attachment attachment : attachmentPage.getCurrentPage()) {
                    if (attachment instanceof FileAttachment) {
                        try {
                            FileAttachment fileAttachment = (FileAttachment) attachment;
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
            }

            logger.info("Getting Outlook attachments completed: {} attachments", attachments.size());

        } catch (Exception e) {
            ErrorInfo errorInfo = exceptionHandler.handleOutlookException(e);
            logger.error("Error getting Outlook attachments for message {}: {} - {}", 
                    messageId, errorInfo.getErrorType(), errorInfo.getErrorMessage(), e);
            throw new IOException("Failed to get Outlook attachments: " + errorInfo.getErrorMessage(), e);
        }

        return attachments;
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
