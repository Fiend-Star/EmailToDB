package com.emailtodb.emailtodb.aspects;

import com.emailtodb.emailtodb.circuitbreaker.CircuitBreaker;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.metrics.MetricsEndpoint;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Aspect for monitoring circuit breakers and collecting metrics
 */
@Aspect
@Component
public class CircuitBreakerMonitoringAspect {
    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerMonitoringAspect.class);
    
    private final MeterRegistry meterRegistry;
    private final Map<String, CircuitBreaker<?>> monitoredCircuitBreakers = new HashMap<>();
    
    @Autowired
    public CircuitBreakerMonitoringAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Monitors circuit breaker execution
     */
    @Around("execution(* com.emailtodb.emailtodb.circuitbreaker.CircuitBreaker.executeWithCircuitBreaker(..))")
    public Object monitorCircuitBreaker(ProceedingJoinPoint joinPoint) throws Throwable {
        CircuitBreaker<?> circuitBreaker = (CircuitBreaker<?>) joinPoint.getTarget();
        String name = getCircuitBreakerName(circuitBreaker);
        
        // Register circuit breaker for monitoring if not already registered
        if (!monitoredCircuitBreakers.containsKey(name)) {
            registerCircuitBreakerMetrics(name, circuitBreaker);
            monitoredCircuitBreakers.put(name, circuitBreaker);
        }
        
        // Get current state for logging
        CircuitBreaker.State stateBefore = circuitBreaker.getState();
        
        try {
            // Record attempt
            incrementCounter(name + ".attempt");
            
            long startTime = System.currentTimeMillis();
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - startTime;
            
            // Record successful execution
            incrementCounter(name + ".success");
            recordGauge(name + ".lastExecutionTime", duration);
            
            return result;
            
        } catch (CircuitBreaker.CircuitBreakerOpenException e) {
            // Record short-circuited execution
            incrementCounter(name + ".shortCircuit");
            throw e;
        } catch (Throwable e) {
            // Record failure
            incrementCounter(name + ".failure");
            throw e;
        } finally {
            // Check if state changed
            CircuitBreaker.State stateAfter = circuitBreaker.getState();
            if (stateBefore != stateAfter) {
                logger.info("Circuit breaker '{}' state changed: {} -> {}", 
                        name, stateBefore, stateAfter);
                
                // Set state gauge
                updateStateGauge(name, stateAfter);
            }
        }
    }
    
    /**
     * Register metrics for a circuit breaker
     */
    private void registerCircuitBreakerMetrics(String name, CircuitBreaker<?> circuitBreaker) {
        // Create counters
        meterRegistry.counter("circuitbreaker." + name + ".attempt", "name", name);
        meterRegistry.counter("circuitbreaker." + name + ".success", "name", name);
        meterRegistry.counter("circuitbreaker." + name + ".failure", "name", name);
        meterRegistry.counter("circuitbreaker." + name + ".shortCircuit", "name", name);
        
        // Create gauges
        Gauge.builder("circuitbreaker." + name + ".state", 
                () -> getStateValue(circuitBreaker.getState()))
            .tag("name", name)
            .description("Circuit breaker state: 0=CLOSED, 1=HALF_OPEN, 2=OPEN")
            .register(meterRegistry);
        
        Gauge.builder("circuitbreaker." + name + ".failureCount", 
                circuitBreaker::getFailureCount)
            .tag("name", name)
            .description("Circuit breaker failure count")
            .register(meterRegistry);
        
        logger.info("Registered metrics for circuit breaker: {}", name);
    }
    
    /**
     * Get the circuit breaker name using reflection
     */
    private String getCircuitBreakerName(CircuitBreaker<?> circuitBreaker) {
        try {
            Field nameField = CircuitBreaker.class.getDeclaredField("name");
            nameField.setAccessible(true);
            return (String) nameField.get(circuitBreaker);
        } catch (Exception e) {
            // Fallback to object identity if reflection fails
            return "unnamed-" + System.identityHashCode(circuitBreaker);
        }
    }
    
    /**
     * Convert circuit breaker state to numeric value for metrics
     */
    private int getStateValue(CircuitBreaker.State state) {
        switch (state) {
            case CLOSED: return 0;
            case HALF_OPEN: return 1;
            case OPEN: return 2;
            default: return -1;
        }
    }
    
    /**
     * Increment a counter metric
     */
    private void incrementCounter(String name) {
        Counter counter = meterRegistry.counter("circuitbreaker." + name);
        counter.increment();
    }
    
    /**
     * Record a gauge value
     */
    private void recordGauge(String name, double value) {
        meterRegistry.gauge("circuitbreaker." + name, value);
    }
    
    /**
     * Update the state gauge
     */
    private void updateStateGauge(String name, CircuitBreaker.State state) {
        recordGauge(name + ".state", getStateValue(state));
    }
}
