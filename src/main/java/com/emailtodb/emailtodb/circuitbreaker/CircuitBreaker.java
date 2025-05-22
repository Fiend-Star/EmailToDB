package com.emailtodb.emailtodb.circuitbreaker;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

/**
 * Implementation of the Circuit Breaker pattern for resilient network and service calls.
 * Prevents cascading failures by failing fast when a service is unavailable.
 * 
 * @param <T> The return type of the protected operation
 */
public class CircuitBreaker<T> {
    private static final Logger logger = LoggerFactory.getLogger(CircuitBreaker.class);
    
    // States of the circuit breaker
    public enum State {
        CLOSED,     // Normal operation, requests allowed
        OPEN,       // Failing fast, no requests allowed
        HALF_OPEN   // Testing the service with limited requests
    }
    
    private final String name;
    private final int failureThreshold;
    private final Duration resetTimeout;
    private final Predicate<Throwable> failurePredicate;
    
    private final AtomicReference<State> state = new AtomicReference<>(State.CLOSED);
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicReference<Instant> lastFailureTime = new AtomicReference<>(Instant.now());
    
    /**
     * Creates a new circuit breaker.
     * 
     * @param name The name of this circuit breaker for logging and identification
     * @param failureThreshold Number of failures before the circuit opens
     * @param resetTimeout Duration after which to attempt resetting the circuit
     * @param failurePredicate Predicate to determine which exceptions count as failures
     */
    public CircuitBreaker(String name, int failureThreshold, Duration resetTimeout, 
                         Predicate<Throwable> failurePredicate) {
        this.name = name;
        this.failureThreshold = failureThreshold;
        this.resetTimeout = resetTimeout;
        this.failurePredicate = failurePredicate;
    }
    
    /**
     * Executes the given callable with circuit breaker protection.
     * 
     * @param operation The operation to execute
     * @return The result of the operation
     * @throws Exception If the operation fails and the circuit is still closed,
     *                  or if the circuit is open
     */
    public T executeWithCircuitBreaker(Callable<T> operation) throws Exception {
        State currentState = state.get();
        
        if (currentState == State.OPEN) {
            // Check if we've waited long enough to try again
            if (Duration.between(lastFailureTime.get(), Instant.now()).compareTo(resetTimeout) > 0) {
                // Transition to half-open state to test the service
                logger.info("Circuit '{}' transitioning from OPEN to HALF-OPEN state", name);
                state.compareAndSet(State.OPEN, State.HALF_OPEN);
                currentState = State.HALF_OPEN; 
            } else {
                // Still in timeout period, fail fast
                logger.debug("Circuit '{}' is OPEN, failing fast", name);
                throw new CircuitBreakerOpenException("Circuit '" + name + "' is open");
            }
        }
        
        try {
            // Execute the operation
            T result = operation.call();
            
            // If we're in half-open and succeeded, we can close the circuit
            if (currentState == State.HALF_OPEN) {
                logger.info("Circuit '{}' recovered, transitioning from HALF-OPEN to CLOSED", name);
                state.compareAndSet(State.HALF_OPEN, State.CLOSED);
                failureCount.set(0);
            } else if (currentState == State.CLOSED) {
                // Reset failure count on successful call
                failureCount.set(0);
            }
            
            return result;
            
        } catch (Exception e) {
            handleFailure(e, currentState);
            throw e;
        }
    }
    
    /**
     * Handles a failure by updating the circuit state if necessary.
     * 
     * @param e The exception that occurred
     * @param currentState The current state of the circuit
     */
    private void handleFailure(Exception e, State currentState) {
        // Check if this exception counts as a failure for the circuit
        if (!failurePredicate.test(e)) {
            logger.debug("Exception in circuit '{}' does not count as circuit failure: {}", 
                    name, e.getMessage());
            return;
        }
        
        lastFailureTime.set(Instant.now());
        
        if (currentState == State.HALF_OPEN) {
            // Failed during testing, back to open state
            logger.info("Circuit '{}' failed in HALF-OPEN state, returning to OPEN for {} seconds", 
                    name, resetTimeout.getSeconds());
            state.compareAndSet(State.HALF_OPEN, State.OPEN);
        } else if (currentState == State.CLOSED) {
            // Increment failure count and check if threshold is reached
            int failures = failureCount.incrementAndGet();
            if (failures >= failureThreshold) {
                logger.warn("Circuit '{}' tripped after {} consecutive failures, " +
                        "transitioning to OPEN for {} seconds", 
                        name, failures, resetTimeout.getSeconds());
                state.compareAndSet(State.CLOSED, State.OPEN);
            } else {
                logger.debug("Circuit '{}' failure count: {}/{}", name, failures, failureThreshold);
            }
        }
    }
    
    /**
     * Get the current state of the circuit breaker.
     * 
     * @return The current state
     */
    public State getState() {
        return state.get();
    }
    
    /**
     * Get the current failure count.
     * 
     * @return The current failure count
     */
    public int getFailureCount() {
        return failureCount.get();
    }
    
    /**
     * Exception thrown when the circuit is open.
     */
    public static class CircuitBreakerOpenException extends RuntimeException {
        public CircuitBreakerOpenException(String message) {
            super(message);
        }
    }
}
