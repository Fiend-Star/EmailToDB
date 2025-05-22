# Email to DB: Your Personal Email Archivist

Welcome to Email to DB, a powerful Java application built with Spring Boot and Maven. This application is your personal email archivist, designed to fetch emails from Gmail and Outlook accounts and store them, along with their attachments, in Azure Storage.

## Electrifying Features

- **Multi-Provider Email Support**: Fetch emails from both Gmail and Microsoft Outlook using their respective APIs.
- **Enhanced Error Handling**: Robust error handling for Outlook email operations with automatic retry mechanisms for transient failures.
- **Relentless Email Fetching**: Harness the Gmail API and Microsoft Graph API to systematically retrieve new emails, ensuring you never miss a beat.
- **Comprehensive Storage Solution**: Not just emails, but their attachments are securely stored in Azure Storage, offering a complete data preservation experience.
- **Drive Integration**: The application is integrated with Google Drive, allowing it to fetch and store attachments directly from and to the Drive.
- **Duplicate Deterrence**: Employs a sophisticated system to identify and avoid redundancy, using unique email IDs and SHA-256 hashes.
- **Cost-Efficient Database Management**: Utilizes HikariCP settings to manage database connections efficiently, reducing costs by ensuring that the MS SQL database does not accrue consistent costs due to always having a connection with the application.
- **Efficient Email Processing**: The project is responsible for processing the parts of an email message, including fetching attachments and extracting the body of the email.
- **Staging and Final Tables**: The application uses staging tables to temporarily store data before it is transferred to the final tables, ensuring data integrity and efficient processing.

## Embarking on the Journey

Get set to launch this powerhouse on your Azure environment for an unmatched experience.

### Essential Prerequisites

- Java 17 or higher for unmatched efficiency and performance.
- Maven, your trusty companion for impeccable dependency management.
- Azure Storage, the bedrock of your data storage needs.
- Docker, for a seamless, containerized environment.

## Crafted With Passion

- **[Java](https://www.java.com/)** - The cornerstone programming language offering unmatched reliability.
- **[Spring Boot](https://spring.io/projects/spring-boot)** - The dynamic web framework powering our application's backbone.
- **[Maven](https://maven.apache.org/)** - The essential tool for streamlined dependency management.
- **[Azure Storage](https://azure.microsoft.com/en-us/services/storage/)** - The robust cloud storage ensuring your data's integrity and security.
- **[Gmail API](https://developers.google.com/gmail/api)** - The key to unlocking and fetching your Gmail emails.
- **[Microsoft Graph API](https://learn.microsoft.com/en-us/graph/overview)** - Powerful API for accessing Outlook emails and Microsoft 365 data.
- **[Google Drive API](https://developers.google.com/drive/api)** - The bridge to your Google Drive, enabling seamless attachment handling.

Dive into the world of Email to DB, where efficiency meets data management, and take the first step towards revolutionizing your email archival process!

## Getting Started

The application will start and begin fetching emails from the configured email accounts.

### Running with Docker Compose

If you have Docker installed, you can use Docker Compose to run the application along with its Azure Storage in separate Docker containers:

1. Build the Docker images:
   ```bash
   docker-compose build
   ```
2. Start the Docker containers:
   ```bash
   docker-compose up
   ```
3. The application and Azure Storage will start in their own Docker containers. The application will begin fetching emails
   from the configured email accounts.

### Email Provider Configuration

The application supports both Gmail and Outlook email providers:

- **Gmail**: Configured by default. See environment variables in the Dockerfile.
- **Outlook**: Requires additional configuration. See [Outlook Email Integration Guide](./docs/OutlookEmailIntegration.md) for setup instructions.

To enable multiple email providers, set the following environment variables:
```
emailProvidersEnabled=gmail,outlook
multiProviderEnabled=true
```
