package com.emailtodb.emailtodb.services;

import com.emailtodb.emailtodb.config.GmailConfig;
import com.emailtodb.emailtodb.entities.EmailMessage;
import com.emailtodb.emailtodb.enums.EmailProvider;
import com.emailtodb.emailtodb.services.interfaces.EmailFetchServiceInterface;
import com.google.api.services.gmail.Gmail;
import com.google.api.services.gmail.model.ListMessagesResponse;
import com.google.api.services.gmail.model.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Service for fetching emails from Gmail using Gmail API
 */
@Service
public class GmailEmailFetchService implements EmailFetchServiceInterface {

    private static final Logger logger = LoggerFactory.getLogger(GmailEmailFetchService.class);
    private static final String USER_ID = "me";

    @Autowired
    private GmailConfig gmailConfig;

    @Autowired
    private EmailSaveService emailSaveService;

    @Override
    public List<Object> fetchMessages() throws IOException {
        logger.info("Fetching Gmail messages started");

        List<Object> messages = new ArrayList<>();
        try {
            Gmail service = gmailConfig.getGmailServiceAccount();

            if (service == null) {
                logger.error("Gmail service is null");
                return messages;
            }

            ListMessagesResponse messageResponse = service.users().messages().list(USER_ID)
                    .setLabelIds(Collections.singletonList("INBOX")).execute();

            List<Message> messageIds = messageResponse.getMessages();

            if (messageIds != null) {
                for (Message messageId : messageIds) {
                    // Fetch the full message using the ID
                    Message message = service.users().messages().get(USER_ID, messageId.getId()).execute();
                    messages.add(message);
                }
            }
            logger.info("Fetched {} Gmail messages", messages.size());

        } catch (IOException e) {
            logger.error("An error occurred while fetching Gmail messages: {}", e.getMessage());
            throw e;
        }

        logger.info("Fetching Gmail messages completed");
        return messages;
    }

    @Override
    public List<Object> fetchMessagesSince(Date sinceDate) throws IOException {
        logger.info("Fetching Gmail messages since {}", sinceDate);

        try {
            Gmail service = gmailConfig.getGmailServiceAccount();
            return new ArrayList<>(fetchMessagesSinceInternal(service, USER_ID, sinceDate));
        } catch (IOException e) {
            logger.error("An error occurred while fetching Gmail messages since {}: {}", sinceDate, e.getMessage());
            throw e;
        }
    }

    private List<Message> fetchMessagesSinceInternal(Gmail service, String userId, Date sinceDate) throws IOException {
        // Create a Calendar object with the sinceDate in "America/New_York" timezone
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("America/New_York"));
        cal.setTime(sinceDate);

        // Change the timezone of the Calendar object to GMT
        cal.setTimeZone(TimeZone.getTimeZone("GMT"));

        // Get the sinceDate in GMT
        Date sinceDateInGMT = cal.getTime();

        SimpleDateFormat gmailDateFormat = new SimpleDateFormat("yyyy/MM/dd");
        gmailDateFormat.setTimeZone(TimeZone.getTimeZone("GMT")); // Gmail uses GMT

        String query = "after:" + gmailDateFormat.format(sinceDateInGMT);

        ListMessagesResponse response = service.users().messages().list(userId)
                .setQ(query).setLabelIds(Collections.singletonList("INBOX")).execute();

        List<Message> messages = new ArrayList<>();
        while (response.getMessages() != null) {
            messages.addAll(response.getMessages());
            if (response.getNextPageToken() != null) {
                String pageToken = response.getNextPageToken();
                response = service.users().messages().list(userId).setQ(query).setPageToken(pageToken).execute();
            } else {
                break;
            }
        }

        // Fetch the full details for each message
        List<Message> detailedMessages = new ArrayList<>();
        for (Message message : messages) {
            Message detailedMessage = service.users().messages().get(userId, message.getId()).execute();
            detailedMessages.add(detailedMessage);
        }
        return detailedMessages;
    }

    @Override
    public EmailMessage convertToEmailMessage(Object message) {
        if (!(message instanceof Message)) {
            throw new IllegalArgumentException("Message must be a Gmail Message object");
        }

        Message gmailMessage = (Message) message;
        EmailMessage emailMessage = emailSaveService.extractEmailMessageFromGmailMessage(gmailMessage);
        emailMessage.setEmailProvider(EmailProvider.GMAIL);
        return emailMessage;
    }

    @Override
    public String getProviderName() {
        return EmailProvider.GMAIL.getDisplayName();
    }

    // Legacy methods for backward compatibility
    public List<Message> fetchMessages(Gmail service) {
        logger.info("Fetching messages started (legacy method)");

        List<Message> messages = new ArrayList<>();
        try {
            if (service == null) {
                logger.error("Gmail service is null");
                return messages;
            }

            ListMessagesResponse messageResponse = service.users().messages().list(USER_ID)
                    .setLabelIds(Collections.singletonList("INBOX")).execute();

            List<Message> messageIds = messageResponse.getMessages();

            if (messageIds != null) {
                for (Message messageId : messageIds) {
                    Message message = service.users().messages().get(USER_ID, messageId.getId()).execute();
                    messages.add(message);
                }
            }
            logger.info("Fetched {} messages", messages.size());

        } catch (IOException e) {
            logger.error("An error occurred: {}", e.getMessage());
        }

        logger.info("Fetching messages completed");
        return messages;
    }

    public List<Message> fetchMessagesSince(Gmail service, String userId, Date sinceDate) throws IOException {
        return fetchMessagesSinceInternal(service, userId, sinceDate);
    }
}
