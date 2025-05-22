package com.emailtodb.emailtodb.services;

import com.azure.core.exception.ResourceNotFoundException;
import com.emailtodb.emailtodb.services.OutlookExceptionHandler.ErrorInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

@ExtendWith(MockitoExtension.class)
public class OutlookExceptionHandlerTest {

    @InjectMocks
    private OutlookExceptionHandler exceptionHandler;

    @Test
    public void testHandleAuthenticationError() {
        // Create authentication error
        Exception authException = new Exception("401 Unauthorized: Authentication failed");
        
        // Test handling
        ErrorInfo errorInfo = exceptionHandler.handleOutlookException(authException);
        
        // Assertions
        assertEquals(OutlookExceptionHandler.AUTHENTICATION_ERROR, errorInfo.getErrorType());
        assertTrue(errorInfo.getErrorMessage().contains("Authentication failed"));
        assertTrue(exceptionHandler.isAuthenticationError(authException));
        assertFalse(exceptionHandler.isRateLimitExceeded(authException));
    }
    
    @Test
    public void testHandleRateLimitError() {
        // Create rate limit error
        Exception rateLimitException = new Exception("429 Too Many Requests: Rate limit exceeded");
        
        // Test handling
        ErrorInfo errorInfo = exceptionHandler.handleOutlookException(rateLimitException);
        
        // Assertions
        assertEquals(OutlookExceptionHandler.API_LIMIT_EXCEEDED, errorInfo.getErrorType());
        assertTrue(errorInfo.getErrorMessage().contains("Rate limit exceeded"));
        assertTrue(exceptionHandler.isRateLimitExceeded(rateLimitException));
        assertFalse(exceptionHandler.isAuthenticationError(rateLimitException));
    }
    
    @Test
    public void testHandleResourceNotFoundError() {
        // Create resource not found error
        ResourceNotFoundException resourceNotFoundException = new ResourceNotFoundException("Resource not found", null);
        
        // Test handling
        ErrorInfo errorInfo = exceptionHandler.handleOutlookException(resourceNotFoundException);
        
        // Assertions
        assertEquals(OutlookExceptionHandler.RESOURCE_NOT_FOUND, errorInfo.getErrorType());
        assertTrue(errorInfo.getErrorMessage().contains("Resource not found"));
    }
    
    @Test
    public void testHandleConnectionError() {
        // Create connection error
        IOException ioException = new IOException("Connection error occurred");
        
        // Test handling
        ErrorInfo errorInfo = exceptionHandler.handleOutlookException(ioException);
        
        // Assertions
        assertEquals(OutlookExceptionHandler.CONNECTION_ERROR, errorInfo.getErrorType());
        assertTrue(errorInfo.getErrorMessage().contains("Connection error occurred"));
    }
    
    @Test
    public void testHandleCompletionException() {
        // Create completion exception with nested authentication error
        Exception authException = new Exception("401 Unauthorized: Authentication failed");
        CompletionException completionException = new CompletionException(authException);
        
        // Test handling
        ErrorInfo errorInfo = exceptionHandler.handleOutlookException(completionException);
        
        // Assertions
        assertEquals(OutlookExceptionHandler.AUTHENTICATION_ERROR, errorInfo.getErrorType());
        assertTrue(errorInfo.getErrorMessage().contains("Authentication failed"));
    }
    
    @Test
    public void testHandleNullException() {
        // Test handling null exception
        ErrorInfo errorInfo = exceptionHandler.handleOutlookException(null);
        
        // Assertions
        assertEquals(OutlookExceptionHandler.GENERAL_ERROR, errorInfo.getErrorType());
        assertEquals("Unknown error occurred", errorInfo.getErrorMessage());
    }
    
    @Test
    public void testHandleGeneralError() {
        // Create general error
        Exception generalException = new Exception("Some unexpected error");
        
        // Test handling
        ErrorInfo errorInfo = exceptionHandler.handleOutlookException(generalException);
        
        // Assertions
        assertEquals(OutlookExceptionHandler.GENERAL_ERROR, errorInfo.getErrorType());
        assertTrue(errorInfo.getErrorMessage().contains("Some unexpected error"));
    }
}
