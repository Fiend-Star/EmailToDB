package com.emailtodb.emailtodb.circuitbreaker;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class CircuitBreakerTest {

    @Test
    public void testCircuitBreaker_SuccessfulExecution() throws Exception {
        // Create a circuit breaker
        CircuitBreaker<String> circuitBreaker = new CircuitBreaker<>(
                "test-success",
                3,
                Duration.ofSeconds(5),
                e -> true
        );
        
        // Execute a successful operation
        String result = circuitBreaker.executeWithCircuitBreaker(() -> "Success");
        
        // Check result
        assertEquals("Success", result);
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertEquals(0, circuitBreaker.getFailureCount());
    }
    
    @Test
    public void testCircuitBreaker_OpensAfterFailures() {
        // Create a circuit breaker with low threshold
        CircuitBreaker<String> circuitBreaker = new CircuitBreaker<>(
                "test-failure",
                2, // Open after 2 failures
                Duration.ofSeconds(60),
                e -> true
        );
        
        // Create a failing operation
        Callable<String> failingOperation = () -> {
            throw new RuntimeException("Simulated failure");
        };
        
        // Execute and expect failures
        for (int i = 0; i < 2; i++) {
            try {
                circuitBreaker.executeWithCircuitBreaker(failingOperation);
                fail("Expected exception was not thrown");
            } catch (Exception e) {
                assertEquals("Simulated failure", e.getMessage());
            }
        }
        
        // Circuit should be open now
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        
        // Next call should throw CircuitBreakerOpenException
        try {
            circuitBreaker.executeWithCircuitBreaker(() -> "This should not execute");
            fail("Expected CircuitBreakerOpenException was not thrown");
        } catch (Exception e) {
            assertTrue(e instanceof CircuitBreaker.CircuitBreakerOpenException);
        }
    }
    
    @Test
    public void testCircuitBreaker_HalfOpenTransition() throws Exception {
        // Create a circuit breaker with very short reset timeout
        CircuitBreaker<String> circuitBreaker = new CircuitBreaker<>(
                "test-half-open",
                2, // Open after 2 failures
                Duration.ofMillis(100), // Very short timeout for testing
                e -> true
        );
        
        // Force the circuit into OPEN state
        for (int i = 0; i < 2; i++) {
            try {
                circuitBreaker.executeWithCircuitBreaker(() -> {
                    throw new RuntimeException("Simulated failure");
                });
            } catch (Exception ignored) {
                // Expected
            }
        }
        
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        
        // Wait for reset timeout to expire
        Thread.sleep(200);
        
        // Next successful call should close the circuit
        String result = circuitBreaker.executeWithCircuitBreaker(() -> "Success");
        
        assertEquals("Success", result);
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
    }
    
    @Test
    public void testCircuitBreaker_RemainsOpenOnHalfOpenFailure() throws Exception {
        // Create a circuit breaker with very short reset timeout
        CircuitBreaker<String> circuitBreaker = new CircuitBreaker<>(
                "test-half-open-failure",
                2, // Open after 2 failures
                Duration.ofMillis(100), // Very short timeout for testing
                e -> true
        );
        
        // Force the circuit into OPEN state
        for (int i = 0; i < 2; i++) {
            try {
                circuitBreaker.executeWithCircuitBreaker(() -> {
                    throw new RuntimeException("Simulated failure");
                });
            } catch (Exception ignored) {
                // Expected
            }
        }
        
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        
        // Wait for reset timeout to expire
        Thread.sleep(200);
        
        // Next call will be in HALF-OPEN state but still fails
        try {
            circuitBreaker.executeWithCircuitBreaker(() -> {
                throw new RuntimeException("Still failing");
            });
            fail("Expected exception was not thrown");
        } catch (Exception e) {
            assertEquals("Still failing", e.getMessage());
        }
        
        // Circuit should return to OPEN state
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
    }
    
    @Test
    public void testCircuitBreaker_NonFailingExceptions() throws Exception {
        // Create a circuit breaker that only counts certain exceptions as failures
        CircuitBreaker<String> circuitBreaker = new CircuitBreaker<>(
                "test-selective-exceptions",
                2,
                Duration.ofSeconds(30),
                e -> e instanceof IllegalArgumentException // Only IllegalArgumentException counts
        );
        
        AtomicInteger callCount = new AtomicInteger(0);
        
        // Throw non-counted exception multiple times
        for (int i = 0; i < 5; i++) {
            try {
                circuitBreaker.executeWithCircuitBreaker(() -> {
                    callCount.incrementAndGet();
                    throw new IllegalStateException("Not a counted failure");
                });
                fail("Expected exception was not thrown");
            } catch (Exception e) {
                assertEquals("Not a counted failure", e.getMessage());
            }
        }
        
        // Circuit should still be CLOSED despite exceptions
        assertEquals(CircuitBreaker.State.CLOSED, circuitBreaker.getState());
        assertEquals(0, circuitBreaker.getFailureCount());
        assertEquals(5, callCount.get());
        
        // Now throw counted exceptions
        for (int i = 0; i < 2; i++) {
            try {
                circuitBreaker.executeWithCircuitBreaker(() -> {
                    callCount.incrementAndGet();
                    throw new IllegalArgumentException("Counted failure");
                });
                fail("Expected exception was not thrown");
            } catch (Exception e) {
                assertEquals("Counted failure", e.getMessage());
            }
        }
        
        // Circuit should be OPEN now
        assertEquals(CircuitBreaker.State.OPEN, circuitBreaker.getState());
        assertEquals(7, callCount.get());
    }
}
