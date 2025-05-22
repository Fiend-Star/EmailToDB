package com.emailtodb.emailtodb.config;

import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.microsoft.graph.authentication.BaseAuthenticationProvider;
import com.microsoft.graph.httpcore.HttpClients;
import com.microsoft.graph.requests.GraphServiceClient;
import okhttp3.Request;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.CompletableFuture;

/**
 * Configuration class for Microsoft Graph (Outlook) email service
 */
@Configuration
public class OutlookConfig {

    @Value("${outlook.client.id:}")
    private String clientId;

    @Value("${outlook.client.secret:}")
    private String clientSecret;

    @Value("${outlook.tenant.id:}")
    private String tenantId;

    @Value("${outlook.user.email:}")
    private String userEmail;

    private static final String[] SCOPES = {"https://graph.microsoft.com/Mail.Read", "https://graph.microsoft.com/Mail.Send"};

    /**
     * Creates and configures a Microsoft Graph service client for accessing Outlook emails
     * @return GraphServiceClient configured with proper authentication
     * @throws IOException if configuration fails
     */
    public GraphServiceClient<Request> getGraphServiceClient() throws IOException {
        
        if (clientId == null || clientId.isEmpty() || 
            clientSecret == null || clientSecret.isEmpty() || 
            tenantId == null || tenantId.isEmpty()) {
            throw new IllegalStateException("Outlook configuration is incomplete. Please check outlook.client.id, outlook.client.secret, and outlook.tenant.id properties.");
        }

        // Create the client secret credential
        ClientSecretCredential clientSecretCredential = new ClientSecretCredentialBuilder()
                .clientId(clientId)
                .clientSecret(clientSecret)
                .tenantId(tenantId)
                .build();

        // Create authentication provider
        BaseAuthenticationProvider authProvider = new BaseAuthenticationProvider() {
            @Override
            public CompletableFuture<String> getAuthorizationTokenAsync(URL requestUrl) {
                return clientSecretCredential.getToken(
                    new com.azure.core.credential.TokenRequestContext()
                        .addScopes("https://graph.microsoft.com/.default"))
                    .toFuture()
                    .thenApply(token -> token.getToken());
            }
        };

        // Build Graph client
        return GraphServiceClient.builder()
                .authenticationProvider(authProvider)
                .httpClient(HttpClients.createDefault())
                .buildClient();
    }

    public String getUserEmail() {
        return userEmail;
    }
}
