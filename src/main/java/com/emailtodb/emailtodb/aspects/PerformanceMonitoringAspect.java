package com.emailtodb.emailtodb.aspects;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Performance monitoring aspect for email-related operations
 * Uses AOP to track and log performance metrics
 */
@Aspect
@Component
public class PerformanceMonitoringAspect {

    private static final Logger logger = LoggerFactory.getLogger(PerformanceMonitoringAspect.class);

    /**
     * Monitors performance of email fetching operations
     * @param joinPoint The AOP join point
     * @return The result of the target method execution
     * @throws Throwable if the operation fails
     */
    @Around("execution(* com.emailtodb.emailtodb.services.*EmailFetchService.*(..))")
    public Object monitorEmailFetchPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        return monitorPerformance(joinPoint, "Email Fetch Operation");
    }

    /**
     * Monitors performance of email saving operations
     * @param joinPoint The AOP join point
     * @return The result of the target method execution
     * @throws Throwable if the operation fails
     */
    @Around("execution(* com.emailtodb.emailtodb.services.*SaveService.*(..))")
    public Object monitorEmailSavePerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        return monitorPerformance(joinPoint, "Email Save Operation");
    }

    /**
     * Generic performance monitoring implementation
     * @param joinPoint The AOP join point
     * @param operationName Name of the operation being monitored
     * @return The result of the target method execution
     * @throws Throwable if the operation fails
     */
    private Object monitorPerformance(ProceedingJoinPoint joinPoint, String operationName) throws Throwable {
        long startTime = System.nanoTime();
        
        try {
            return joinPoint.proceed();
        } finally {
            long endTime = System.nanoTime();
            long executionTime = TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
            
            logger.info("[Performance] {} - {}.{} completed in {} ms", 
                    operationName,
                    joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName(),
                    executionTime);
        }
    }
}
