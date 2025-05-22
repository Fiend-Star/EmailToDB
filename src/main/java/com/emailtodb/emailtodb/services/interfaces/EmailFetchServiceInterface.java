package com.emailtodb.emailtodb.services.interfaces;

import com.emailtodb.emailtodb.entities.EmailMessage;

import java.io.IOException;
import java.util.Date;
import java.util.List;

/**
 * Interface for email fetching services that can be implemented by different email providers
 */
public interface EmailFetchServiceInterface {
    
    /**
     * Fetch all emails from the service
     * @return List of email messages as generic objects that will be converted to EmailMessage
     * @throws IOException if fetching fails
     */
    List<Object> fetchMessages() throws IOException;

    /**
     * Fetch emails since a specific date
     * @param sinceDate The date from which to fetch emails
     * @return List of email messages as generic objects
     * @throws IOException if fetching fails
     */
    List<Object> fetchMessagesSince(Date sinceDate) throws IOException;

    /**
     * Convert provider-specific message object to EmailMessage entity
     * @param message Provider-specific message object
     * @return EmailMessage entity
     */
    EmailMessage convertToEmailMessage(Object message);

    /**
     * Get the provider name
     * @return String representing the provider name
     */
    String getProviderName();
}
