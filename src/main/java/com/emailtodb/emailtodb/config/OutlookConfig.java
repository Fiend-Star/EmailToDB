package com.emailtodb.emailtodb.config;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.authentication.BaseAuthenticationProvider;
import com.microsoft.graph.httpcore.HttpClients;
import com.microsoft.graph.options.HeaderOption;
import com.microsoft.graph.options.Option;
import com.microsoft.graph.options.QueryOption;
import com.microsoft.graph.requests.GraphServiceClient;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;

import java.io.IOException;
import java.net.URL;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Configuration class for Microsoft Graph (Outlook) email service
 * Enhanced with retry capabilities and timeouts
 */
@Configuration
public class OutlookConfig {
    private static final Logger logger = LoggerFactory.getLogger(OutlookConfig.class);

    @Value("${outlook.client.id:}")
    private String clientId;

    @Value("${outlook.client.secret:}")
    private String clientSecret;

    @Value("${outlook.tenant.id:}")
    private String tenantId;

    @Value("${outlook.user.email:}")
    private String userEmail;

    @Value("${outlook.connection.timeout:30}")
    private int connectionTimeoutSeconds;

    @Value("${outlook.read.timeout:60}")
    private int readTimeoutSeconds;

    @Value("${outlook.max.idle.connections:10}")
    private int maxIdleConnections;

    @Value("${outlook.keepalive.duration:300}")
    private int keepAliveDurationSeconds;

    private static final String[] SCOPES = {"https://graph.microsoft.com/Mail.Read", "https://graph.microsoft.com/Mail.Send"};

    /**
     * Creates and configures a Microsoft Graph service client for accessing Outlook emails
     * with retry capability and improved connection settings
     * 
     * @return GraphServiceClient configured with proper authentication
     * @throws IOException if configuration fails
     */
    @Bean
    @Retryable(value = {IOException.class}, 
               maxAttempts = 3, 
               backoff = @Backoff(delay = 1000, multiplier = 2))
    public GraphServiceClient<Request> getGraphServiceClient() throws IOException {
        logger.debug("Initializing Microsoft Graph client");
        
        validateConfiguration();

        try {
            // Create the client secret credential
            ClientSecretCredential clientSecretCredential = new ClientSecretCredentialBuilder()
                    .clientId(clientId)
                    .clientSecret(clientSecret)
                    .tenantId(tenantId)
                    .build();

            // Create authentication provider with retry and error handling
            BaseAuthenticationProvider authProvider = createAuthProvider(clientSecretCredential);

            // Create an optimized OkHttpClient for the Graph client with timeouts
            OkHttpClient httpClient = createOptimizedHttpClient(authProvider);

            // Build Graph client with default headers for consistent application identification
            return GraphServiceClient.builder()
                    .authenticationProvider(authProvider)
                    .httpClient(httpClient)
                    .buildClient();
        } catch (Exception e) {
            logger.error("Failed to create Microsoft Graph client", e);
            throw new IOException("Failed to initialize Microsoft Graph client: " + e.getMessage(), e);
        }
    }

    /**
     * Create authentication provider with retry logic
     */
    private BaseAuthenticationProvider createAuthProvider(ClientSecretCredential credential) {
        return new BaseAuthenticationProvider() {
            @Override
            public CompletableFuture<String> getAuthorizationTokenAsync(URL requestUrl) {
                return credential.getToken(
                        new com.azure.core.credential.TokenRequestContext()
                                .addScopes("https://graph.microsoft.com/.default"))
                        .toFuture()
                        .thenApply(token -> token.getToken())
                        .exceptionally(ex -> {
                            // Handle token acquisition failures
                            logger.error("Failed to acquire Microsoft Graph authentication token", ex);
                            if (ex instanceof CompletionException && ex.getCause() != null) {
                                throw new CompletionException(ex.getCause());
                            }
                            throw new CompletionException(ex);
                        });
            }
        };
    }

    /**
     * Create an optimized HTTP client with timeouts and connection pooling
     */
    private OkHttpClient createOptimizedHttpClient(BaseAuthenticationProvider authProvider) {
        // Get the default HTTP client from the provider
        OkHttpClient defaultClient = HttpClients.createDefault(authProvider);
        
        // Create a custom client with our optimized settings
        return defaultClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(connectionTimeoutSeconds))
                .readTimeout(Duration.ofSeconds(readTimeoutSeconds))
                .writeTimeout(Duration.ofSeconds(readTimeoutSeconds))
                .retryOnConnectionFailure(true)
                .connectionPool(new okhttp3.ConnectionPool(
                        maxIdleConnections, 
                        keepAliveDurationSeconds, 
                        java.util.concurrent.TimeUnit.SECONDS))
                .build();
    }

    /**
     * Create standard options for Microsoft Graph API requests
     * 
     * @return List of options to be included with requests
     */
    public List<Option> createStandardOptions() {
        List<Option> options = new ArrayList<>();
        
        // Add standard headers
        options.add(new HeaderOption("Accept", "application/json"));
        options.add(new HeaderOption("Client-Request-Id", java.util.UUID.randomUUID().toString()));
        
        // Add optional query parameters
        options.add(new QueryOption("$top", "100")); // Limit results
        
        return options;
    }

    /**
     * Validate that the configuration is complete
     */
    private void validateConfiguration() {
        if (clientId == null || clientId.isEmpty() || 
            clientSecret == null || clientSecret.isEmpty() || 
            tenantId == null || tenantId.isEmpty()) {
            throw new IllegalStateException("Outlook configuration is incomplete. Please check outlook.client.id, outlook.client.secret, and outlook.tenant.id properties.");
        }
        
        if (userEmail == null || userEmail.isEmpty()) {
            logger.warn("Outlook user email is not configured. Some operations may fail.");
        }
        
        logger.info("Outlook configuration validated for tenant: {}", tenantId);
    }

    public String getUserEmail() {
        return userEmail;
    }
}
