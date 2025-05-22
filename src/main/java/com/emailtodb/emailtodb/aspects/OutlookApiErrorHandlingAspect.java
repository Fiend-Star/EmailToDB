package com.emailtodb.emailtodb.aspects;

import com.emailtodb.emailtodb.services.OutlookExceptionHandler;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Aspect for centralized error handling of Outlook API operations
 * This follows AOP principles to separate cross-cutting concerns
 */
@Aspect
@Component
public class OutlookApiErrorHandlingAspect {

    private static final Logger logger = LoggerFactory.getLogger(OutlookApiErrorHandlingAspect.class);

    @Autowired
    private OutlookExceptionHandler exceptionHandler;

    /**
     * Handles errors for all Outlook service methods that interact with the Graph API
     * @param joinPoint The AOP join point
     * @return The result of the target method execution or throws an appropriate exception
     * @throws Throwable if the operation fails
     */
    @Around("execution(* com.emailtodb.emailtodb.services.Outlook*FetchService.*(..))")
    public Object handleOutlookApiErrors(ProceedingJoinPoint joinPoint) throws Throwable {
        try {
            return joinPoint.proceed();
        } catch (Exception e) {
            logger.error("Error in Outlook API operation: {}", e.getMessage(), e);
            OutlookExceptionHandler.ErrorInfo errorInfo = exceptionHandler.handleOutlookException(e);
            
            // Re-throw IOException for retry mechanisms
            if (exceptionHandler.isAuthenticationError(e)) {
                throw new IOException("Authentication error in Outlook API", e);
            }
            
            // Re-throw the original exception for other cases
            throw e;
        }
    }
}
