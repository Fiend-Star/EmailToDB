# Outlook Email Integration

This document provides instructions for configuring Outlook email support in the EmailToDB application.

## Overview

EmailToDB now supports fetching emails from both Gmail and Microsoft Outlook (using Microsoft Graph API). This allows the application to archive emails and attachments from multiple sources.

## Prerequisites

To use Outlook integration, you need:

1. A Microsoft Azure tenant with admin access
2. An Azure App Registration with appropriate permissions
3. Credentials for the registered application (client ID, client secret, tenant ID)
4. A user account with access to the mailbox you want to process

## Configuration Steps

### 1. Azure App Registration

1. Sign in to the [Azure Portal](https://portal.azure.com/)
2. Navigate to "App registrations" and click "New registration"
3. Provide a name (e.g., "EmailToDB")
4. Select "Accounts in this organizational directory only" for Supported account types
5. Click "Register"
6. Note the "Application (client) ID" and "Directory (tenant) ID" shown on the Overview page

### 2. Configure API Permissions

1. In your app registration, go to "API permissions"
2. Click "Add a permission"
3. Select "Microsoft Graph"
4. Choose "Application permissions"
5. Add the following permissions:
   - Mail.Read
   - Mail.ReadBasic
   - Mail.ReadBasic.All
   - Mail.Send
6. Click "Add permissions"
7. Click "Grant admin consent for [your organization]"

### 3. Create Client Secret

1. Go to "Certificates & secrets"
2. Click "New client secret"
3. Add a description and select an expiry period
4. Click "Add"
5. Copy the secret value (you won't be able to see it again)

### 4. Configure EmailToDB for Outlook

Update your application configuration with the following environment variables:

```
# Outlook Configuration
outlookClientId=your-client-id
outlookClientSecret=your-client-secret
outlookTenantId=your-tenant-id
outlookUserEmail=user@example.com

# Optional - Outlook Retry Configuration
outlookRetryMaxAttempts=3
outlookRetryInitialBackoffMs=1000

# Enable multi-provider support
emailProvidersEnabled=gmail,outlook
multiProviderEnabled=true
```

## Error Handling & Reliability

The Outlook integration includes enhanced error handling capabilities to ensure reliable email processing:

1. **Automatic Retries**: The application will automatically retry operations that fail due to transient errors (like network issues or temporary authentication failures)
2. **Rate Limiting Protection**: The application detects and handles API rate limiting from Microsoft Graph API
3. **Comprehensive Error Classification**: Different error types (authentication, permissions, network issues) are handled appropriately
4. **Detailed Logging**: Enhanced logging provides visibility into Outlook API operations and issues

You can configure the retry behavior using these environment variables:
- `outlookRetryMaxAttempts`: Maximum number of retry attempts (default: 3)
- `outlookRetryInitialBackoffMs`: Initial delay before first retry in milliseconds (default: 1000)

## Using Multiple Email Providers

When multi-provider support is enabled:

1. The application will fetch emails from both Gmail and Outlook based on your configuration
2. Emails from all providers will be processed and stored in the same database
3. Attachments will be handled appropriately based on the email provider
4. Summary emails will include information from all configured providers

You can configure which providers are enabled using the `emailProvidersEnabled` property:
- For Gmail only: `gmail`
- For Outlook only: `outlook`
- For both: `gmail,outlook`
