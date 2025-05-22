package com.emailtodb.emailtodb.services;

import com.emailtodb.emailtodb.entities.EmailMessage;
import com.emailtodb.emailtodb.enums.EmailProvider;
import com.emailtodb.emailtodb.services.interfaces.EmailFetchServiceInterface;
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
public class EmailFetchService {

    private static final Logger logger = LoggerFactory.getLogger(EmailFetchService.class);

    @Autowired
    private GmailEmailFetchService gmailEmailFetchService;

    @Autowired
    private OutlookEmailFetchService outlookEmailFetchService;

    public List<Message> fetchMessages(Gmail service) {
        logger.info("Fetching messages started");

        List<Message> messages = new ArrayList<>();
        try {

            if (service == null) {
                logger.error("Gmail service is null");
                return messages;
            }

            ListMessagesResponse messageResponse = service.users().messages().list(USER_ID).setLabelIds(Collections.singletonList("INBOX")).execute();

            List<Message> messageIds = messageResponse.getMessages();

            if (messageIds != null) {
                for (Message messageId : messageIds) {
                    // Fetch the full message using the ID
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

        logger.info("Fetching messages since {}", sinceDate);

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

        ListMessagesResponse response = service.users().messages().list(userId).setQ(query).setLabelIds(Collections.singletonList("INBOX")).execute();

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


}
