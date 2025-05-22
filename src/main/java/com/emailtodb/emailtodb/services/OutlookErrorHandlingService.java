package com.emailtodb.emailtodb.services;

import com.azure.core.exception.ResourceNotFoundException;
import com.microsoft.graph.core.ClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.CompletionException;

/**
 * Service for handling Outlook-specific errors and exceptions
 */
@Service
public class OutlookErrorHandlingService {

    private static final Logger logger = LoggerFactory.getLogger(OutlookErrorHandlingService.class);
    
    // Common error types
    public static final String AUTHENTICATION_ERROR = "AUTHENTICATION_ERROR";
    public static final String API_LIMIT_EXCEEDED = "API_LIMIT_EXCEEDED";
    public static final String CONNECTION_ERROR = "CONNECTION_ERROR";
    public static final String PERMISSION_ERROR = "PERMISSION_ERROR";
    public static final String RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND";
    public static final String GENERAL_ERROR = "GENERAL_ERROR";
    
    /**
     * Handle Outlook API exceptions and provide standardized error information
     * @param exception The caught exception
     * @return ErrorInfo containing error type and details
     */
    public ErrorInfo handleOutlookException(Exception exception) {
        if (exception == null) {
            return new ErrorInfo(GENERAL_ERROR, "Unknown error occurred");
        }
        
        logger.error("Handling Outlook exception: {}", exception.getMessage(), exception);
        
        if (exception instanceof ClientException) {
            return handleClientException((ClientException) exception);
        } else if (exception instanceof CompletionException) {
            return handleCompletionException((CompletionException) exception);
        } else if (exception instanceof ResourceNotFoundException) {
            return new ErrorInfo(RESOURCE_NOT_FOUND, "The requested resource could not be found: " + exception.getMessage());
        } else if (exception instanceof IOException) {
            return new ErrorInfo(CONNECTION_ERROR, "Connection error occurred: " + exception.getMessage());
        } else {
            return new ErrorInfo(GENERAL_ERROR, "Unexpected error: " + exception.getMessage());
        }
    }
    
    /**
     * Handle Microsoft Graph Client exceptions
     * @param exception The ClientException
     * @return ErrorInfo with appropriate error type and message
     */
    private ErrorInfo handleClientException(ClientException exception) {
        String message = exception.getMessage() != null ? exception.getMessage() : "Unknown client error";
        int statusCode = exception.getResponseCode();
        
        switch (statusCode) {
            case 401:
                return new ErrorInfo(AUTHENTICATION_ERROR, "Authentication failed: " + message);
            case 403:
                return new ErrorInfo(PERMISSION_ERROR, "Permission denied: " + message);
            case 404:
                return new ErrorInfo(RESOURCE_NOT_FOUND, "Resource not found: " + message);
            case 429:
                return new ErrorInfo(API_LIMIT_EXCEEDED, "API rate limit exceeded: " + message);
            default:
                return new ErrorInfo(GENERAL_ERROR, "Graph API error (code " + statusCode + "): " + message);
        }
    }
    
    /**
     * Handle CompletionException which often wraps other exceptions from async operations
     * @param exception The CompletionException
     * @return ErrorInfo with appropriate error type and message
     */
    private ErrorInfo handleCompletionException(CompletionException exception) {
        Throwable cause = exception.getCause();
        if (cause instanceof ClientException) {
            return handleClientException((ClientException) cause);
        } else {
            return new ErrorInfo(GENERAL_ERROR, "Async operation failed: " + 
                (cause != null ? cause.getMessage() : exception.getMessage()));
        }
    }
    
    /**
     * Check if an exception is due to authentication issues
     * @param exception The exception to check
     * @return true if it's an authentication error
     */
    public boolean isAuthenticationError(Exception exception) {
        return handleOutlookException(exception).errorType.equals(AUTHENTICATION_ERROR);
    }
    
    /**
     * Check if an exception is due to rate limiting
     * @param exception The exception to check
     * @return true if it's a rate limiting error
     */
    public boolean isRateLimitExceeded(Exception exception) {
        return handleOutlookException(exception).errorType.equals(API_LIMIT_EXCEEDED);
    }
    
    /**
     * Class to encapsulate error information
     */
    public static class ErrorInfo {
        private final String errorType;
        private final String errorMessage;
        
        public ErrorInfo(String errorType, String errorMessage) {
            this.errorType = errorType;
            this.errorMessage = errorMessage;
        }
        
        public String getErrorType() {
            return errorType;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        @Override
        public String toString() {
            return "ErrorInfo{" +
                    "errorType='" + errorType + '\'' +
                    ", errorMessage='" + errorMessage + '\'' +
                    '}';
        }
    }
}
