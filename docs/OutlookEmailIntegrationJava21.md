# Outlook Email Integration with Java 21 Features

This document outlines the implementation of Microsoft Outlook integration in the EmailToDB application, utilizing Java 21 features and modern resilience patterns.

## Overview

The EmailToDB application connects to Microsoft Outlook using Microsoft Graph API to retrieve emails and attachments. The integration has been enhanced with the following features:

- Java 21 compatibility
- Advanced error handling with AOP
- Retry mechanisms for transient failures
- Circuit breaker pattern for fault tolerance
- Comprehensive health monitoring
- Actuator endpoints for observability

## Configuration

### Properties

Add the following properties to your `application.properties` file:

```properties
# Outlook Configuration
outlook.client.id=your-client-id
outlook.client.secret=your-client-secret
outlook.tenant.id=your-tenant-id
outlook.user.email=user@example.com

# Outlook Connection Settings
outlook.connection.timeout=30
outlook.read.timeout=60
outlook.max.idle.connections=10
outlook.keepalive.duration=300

# Outlook Retry Settings
outlook.retry.maxAttempts=3
outlook.retry.initialBackoffMs=1000
outlook.retry.maxBackoffMs=60000
outlook.retry.multiplier=2.0

# Email Provider Configuration
email.providers.enabled=gmail,outlook-enhanced
email.multi.provider.enabled=true
```

### Circuit Breaker Configuration

The circuit breaker pattern is implemented to prevent system overload during API outages. Circuit breakers have the following default settings:

- Network operations: 3 failures to open, 30 seconds timeout
- Service operations: 5 failures to open, a 1-minute timeout

## Resilience Patterns

### Retry Mechanism

Transient failures such as network timeouts or temporary API unavailability are handled with a retry mechanism:

- Maximum 3 retry attempts
- Exponential backoff starting at 1 second
- Doubling the wait time between retries

### Circuit Breaker

When multiple failures occur in a short time, the circuit breaker opens to prevent cascading failures:

- Stops making API calls when the system is unavailable
- Gradually tests the service when the timeout period expires
- Provides fast failure when the service is known to be down
- Metrics for monitoring circuit breaker state

### Error Handling

Comprehensive error handling is implemented using Aspect-Oriented Programming (AOP):

- Standardized error types and handling
- Detailed logging for troubleshooting
- Clear separation of transient vs. permanent failures
- Custom error aspects for different service components

## Health Monitoring

The application exposes health endpoints via Spring Boot Actuator:

- `/actuator/health` - Overall application health
- `/actuator/health/liveness` - Kubernetes liveness probe
- `/actuator/health/readiness` - Kubernetes readiness probe
- `/actuator/metrics` - Application metrics
- `/actuator/prometheus` - Prometheus-compatible metrics endpoint

Custom health indicators are implemented:

- Outlook connectivity health indicator
- Circuit breaker state health indicator

## Metrics

Micrometer metrics are available for monitoring:

- Circuit breaker states and transitions
- API call success/failure rates
- Response times for key operations
- Resource utilization metrics

## Docker & Kubernetes

The application is containerized with Java 21 support:

- Eclipse Temurin 21 JRE as the base image
- Container-aware memory settings
- Health probes for Kubernetes
- Resource limits and requests

## Testing

Integration tests validate resilience patterns:

- Circuit breaker tests
- Retry behavior tests
- Fault injection tests

## Example Usage

```java
// Using the enhanced Outlook service with circuit breaker
@Autowired
private OutlookAttachmentFetchServiceEnhanced outlookService;

// Getting attachments with resilience built-in
List<EmailAttachment> attachments = outlookService.getAttachments(
    outlookMessageId, emailMessage);
```

## Troubleshooting

Common issues and their resolutions:

1. Circuit breaker open: Wait for the timeout period or check API availability
2. Authentication failures: Verify client ID, secret, and permissions
3. Connection timeouts: Check network connectivity and timeout settings
4. Rate limiting: Implement backoff or reduce request frequency

## Best Practices

- Monitor circuit breaker metrics to detect API issues early
- Configure appropriate timeout values based on expected response times
- Adjust retry parameters based on observed transient failure patterns
- Use health checks in Kubernetes for automatic recovery
