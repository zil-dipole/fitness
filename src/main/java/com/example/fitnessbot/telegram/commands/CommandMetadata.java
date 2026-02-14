package com.example.fitnessbot.telegram.commands;

/**
 * Metadata for a bot command including its name, description, and usage example
 */
public class CommandMetadata {
    private final String command;
    private final String description;
    private final String usageExample;

    public CommandMetadata(String command, String description, String usageExample) {
        this.command = command;
        this.description = description;
        this.usageExample = usageExample;
    }

    public String getCommand() {
        return command;
    }

    public String getDescription() {
        return description;
    }

    public String getUsageExample() {
        return usageExample;
    }

    @Override
    public String toString() {
        return "CommandMetadata{" +
                "command='" + command + '\'' +
                ", description='" + description + '\'' +
                ", usageExample='" + usageExample + '\'' +
                '}';
    }
}