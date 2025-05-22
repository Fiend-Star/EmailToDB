package com.emailtodb.emailtodb.services;

import com.emailtodb.emailtodb.config.OutlookConfig;
import com.emailtodb.emailtodb.entities.EmailMessage;
import com.emailtodb.emailtodb.enums.EmailProvider;
import com.microsoft.graph.models.BodyType;
import com.microsoft.graph.models.EmailAddress;
import com.microsoft.graph.models.ItemBody;
import com.microsoft.graph.models.Message;
import com.microsoft.graph.models.Recipient;
import com.microsoft.graph.requests.GraphServiceClient;
import okhttp3.Request;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
public class OutlookEmailFetchServiceTest {

    @Mock
    private OutlookConfig outlookConfig;

    @Mock
    private OutlookExceptionHandler exceptionHandler;

    @Mock
    private GraphServiceClient<Request> graphClient;

    @InjectMocks
    private OutlookEmailFetchService outlookEmailFetchService;

    @BeforeEach
    public void setup() {
        ReflectionTestUtils.setField(outlookEmailFetchService, "maxRetryAttempts", 3);
        ReflectionTestUtils.setField(outlookEmailFetchService, "initialBackoffMs", 1000L);
    }

    @Test
    public void testConvertToEmailMessage() {
        // Create sample Outlook message
        Message outlookMessage = new Message();
        outlookMessage.id = "test-id-123";
        outlookMessage.subject = "Test Subject";
        
        // Setup sender
        outlookMessage.from = new Recipient();
        outlookMessage.from.emailAddress = new EmailAddress();
        outlookMessage.from.emailAddress.address = "sender@example.com";
        outlookMessage.from.emailAddress.name = "Sender Name";
        
        // Setup recipients
        List<Recipient> toRecipients = new ArrayList<>();
        Recipient toRecipient = new Recipient();
        toRecipient.emailAddress = new EmailAddress();
        toRecipient.emailAddress.address = "recipient@example.com";
        toRecipient.emailAddress.name = "Recipient Name";
        toRecipients.add(toRecipient);
        outlookMessage.toRecipients = toRecipients;
        
        // Setup CC recipients
        List<Recipient> ccRecipients = new ArrayList<>();
        Recipient ccRecipient = new Recipient();
        ccRecipient.emailAddress = new EmailAddress();
        ccRecipient.emailAddress.address = "cc@example.com";
        ccRecipient.emailAddress.name = "CC Recipient";
        ccRecipients.add(ccRecipient);
        outlookMessage.ccRecipients = ccRecipients;
        
        // Setup body
        outlookMessage.body = new ItemBody();
        outlookMessage.body.contentType = BodyType.HTML;
        outlookMessage.body.content = "<p>This is a test email body</p>";
        
        // Setup received date time
        outlookMessage.receivedDateTime = OffsetDateTime.now(ZoneOffset.UTC);
        
        // Convert using service
        EmailMessage emailMessage = outlookEmailFetchService.convertToEmailMessage(outlookMessage);
        
        // Assertions
        assertNotNull(emailMessage);
        assertEquals("test-id-123", emailMessage.getMessageId());
        assertEquals("Test Subject", emailMessage.getSubject());
        assertEquals("Sender Name <sender@example.com>", emailMessage.getFrom());
        assertEquals("recipient@example.com", emailMessage.getTo());
        assertEquals("cc@example.com", emailMessage.getCc());
        assertEquals("<p>This is a test email body</p>", emailMessage.getBody());
        assertEquals("This is a test email body", emailMessage.getBriefBody());
        assertEquals(EmailProvider.OUTLOOK, emailMessage.getEmailProvider());
        assertEquals("OUTLOOK", outlookEmailFetchService.getProviderName());
    }
    
    @Test
    public void testConvertToEmailMessageWithInvalidInput() {
        // Test invalid input (not a Message object)
        String invalidInput = "Not a Message object";
        
        // Assert that exception is thrown
        assertThrows(IllegalArgumentException.class, () -> {
            outlookEmailFetchService.convertToEmailMessage(invalidInput);
        });
    }
    
    @Test
    public void testConvertToEmailMessageWithNullValues() {
        // Create message with minimal data
        Message message = new Message();
        message.id = "minimal-id";
        // No other fields set
        
        // Convert using service
        EmailMessage emailMessage = outlookEmailFetchService.convertToEmailMessage(message);
        
        // Assertions
        assertNotNull(emailMessage);
        assertEquals("minimal-id", emailMessage.getMessageId());
        assertEquals("", emailMessage.getSubject());
        assertEquals("", emailMessage.getFrom());
        assertEquals("", emailMessage.getTo());
        assertEquals("", emailMessage.getCc());
        assertEquals("", emailMessage.getBody());
        assertEquals("", emailMessage.getBriefBody());
        assertEquals(EmailProvider.OUTLOOK, emailMessage.getEmailProvider());
    }
}
