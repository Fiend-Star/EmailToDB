package com.emailtodb.emailtodb.circuitbreaker;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Factory class for creating and managing circuit breakers.
 * Ensures that circuit breakers with the same name share state.
 */
@Component
public class CircuitBreakerFactory {
    
    private final Map<String, Object> circuitBreakers = new ConcurrentHashMap<>();
    
    /**
     * Creates or retrieves a circuit breaker with the given configuration.
     *
     * @param name The name of the circuit breaker
     * @param failureThreshold Number of failures before the circuit opens
     * @param resetTimeout Duration after which to attempt resetting the circuit
     * @param failurePredicate Predicate to determine which exceptions count as failures
     * @param <T> The return type of the protected operation
     * @return A circuit breaker instance
     */
    @SuppressWarnings("unchecked")
    public <T> CircuitBreaker<T> getCircuitBreaker(String name, 
                                               int failureThreshold, 
                                               Duration resetTimeout, 
                                               Predicate<Throwable> failurePredicate) {
        return (CircuitBreaker<T>) circuitBreakers.computeIfAbsent(
                name, 
                key -> new CircuitBreaker<T>(key, failureThreshold, resetTimeout, failurePredicate)
        );
    }
    
    /**
     * Creates a circuit breaker for network operations with default settings.
     *
     * @param name The name of the circuit breaker
     * @param <T> The return type of the protected operation
     * @return A circuit breaker with default network operation settings
     */
    public <T> CircuitBreaker<T> getNetworkCircuitBreaker(String name) {
        return getCircuitBreaker(
                name,
                3, // 3 failures to open
                Duration.ofSeconds(30), // 30 seconds timeout
                throwable -> throwable instanceof java.io.IOException || 
                            throwable instanceof java.net.SocketTimeoutException ||
                            throwable instanceof java.net.ConnectException
        );
    }
    
    /**
     * Creates a circuit breaker for service operations with default settings.
     *
     * @param name The name of the circuit breaker
     * @param <T> The return type of the protected operation
     * @return A circuit breaker with default service operation settings
     */
    public <T> CircuitBreaker<T> getServiceCircuitBreaker(String name) {
        return getCircuitBreaker(
                name,
                5, // 5 failures to open
                Duration.ofMinutes(1), // 1 minute timeout
                throwable -> true // All exceptions count as failures
        );
    }
}
