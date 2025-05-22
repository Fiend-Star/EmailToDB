package com.emailtodb.emailtodb.enums;

/**
 * Enumeration representing different email service providers
 */
public enum EmailProvider {
    GMAIL("Gmail"),
    OUTLOOK("Outlook");

    private final String displayName;

    EmailProvider(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
