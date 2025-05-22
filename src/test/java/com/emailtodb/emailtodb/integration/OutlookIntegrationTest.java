package com.emailtodb.emailtodb.integration;

import com.emailtodb.emailtodb.config.OutlookConfig;
import com.emailtodb.emailtodb.entities.EmailMessage;
import com.emailtodb.emailtodb.repositories.EmailMessageRepository;
import com.emailtodb.emailtodb.services.OutlookAttachmentFetchServiceEnhanced;
import com.emailtodb.emailtodb.services.OutlookExceptionHandler;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Integration tests for Outlook functionality
 * Note: These tests are disabled by default as they would require actual Outlook credentials
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@ActiveProfiles("test")
@Disabled("Requires actual Outlook credentials - enable manually when testing with credentials")
public class OutlookIntegrationTest {

    @Autowired
    private OutlookAttachmentFetchServiceEnhanced outlookService;
    
    @Autowired
    private OutlookConfig outlookConfig;
    
    @Autowired
    private OutlookExceptionHandler exceptionHandler;
    
    @MockBean
    private EmailMessageRepository emailMessageRepository;
    
    /**
     * Test that the Outlook client can be initialized correctly
     */
    @Test
    public void testOutlookClientInitialization() {
        assertDoesNotThrow(() -> {
            var client = outlookConfig.getGraphServiceClient();
            assertNotNull(client, "Graph client should not be null");
        }, "Graph client initialization should not throw exceptions");
    }
    
    /**
     * Test fetching attachments from an existing message
     */
    @Test
    public void testFetchAttachments() {
        // Mock repository behavior
        when(emailMessageRepository.save(any(EmailMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
        
        // Create a test message
        EmailMessage message = new EmailMessage();
        message.setId(1L);
        message.setMessageId(UUID.randomUUID().toString());
        message.setSubject("Test Message");
        
        // Use a message ID that exists in your test account
        // This is the tricky part as you need a real message ID
        String outlookMessageId = "TEST_MESSAGE_ID"; // Replace with real ID when running test
        
        assertDoesNotThrow(() -> {
            var attachments = outlookService.getAttachments(outlookMessageId, message);
            // Just verify that the call completes, the attachments list may be empty
            assertNotNull(attachments, "Attachments list should not be null");
        }, "Attachment fetching should not throw exceptions");
    }
    
    /**
     * Test error handling for non-existent message
     */
    @Test
    public void testErrorHandling() {
        // Create a test message
        EmailMessage message = new EmailMessage();
        message.setId(2L);
        message.setMessageId(UUID.randomUUID().toString());
        message.setSubject("Test Message for Error Handling");
        
        // Use a non-existent message ID
        String nonExistentId = "NON_EXISTENT_ID";
        
        Exception exception = assertThrows(Exception.class, () -> {
            outlookService.getAttachments(nonExistentId, message);
        }, "Should throw exception for non-existent message ID");
        
        OutlookExceptionHandler.ErrorInfo errorInfo = exceptionHandler.handleOutlookException(exception);
        assertEquals(OutlookExceptionHandler.RESOURCE_NOT_FOUND, errorInfo.getErrorType(), 
                "Error type should be RESOURCE_NOT_FOUND for non-existent message");
    }
}
