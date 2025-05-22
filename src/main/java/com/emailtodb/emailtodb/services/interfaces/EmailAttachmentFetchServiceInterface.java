package com.emailtodb.emailtodb.services.interfaces;

import com.emailtodb.emailtodb.entities.EmailAttachment;
import com.emailtodb.emailtodb.entities.EmailMessage;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Interface for email attachment fetching services
 */
public interface EmailAttachmentFetchServiceInterface {
    
    /**
     * Get attachments from a message
     * @param messageObject The message object (can be Gmail Message or Outlook message ID)
     * @param emailMessage The EmailMessage entity
     * @return List of EmailAttachment objects
     * @throws IOException if fetching fails
     * @throws NoSuchAlgorithmException if hashing fails
     */
    List<EmailAttachment> getAttachments(Object messageObject, EmailMessage emailMessage) 
            throws IOException, NoSuchAlgorithmException;
    
    /**
     * Get the provider name
     * @return String representing the provider name
     */
    String getProviderName();
}
