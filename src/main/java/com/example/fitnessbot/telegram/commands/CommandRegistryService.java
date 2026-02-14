package com.example.fitnessbot.telegram.commands;

import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service that maintains a registry of all available bot commands with their metadata
 */
@Service
public class CommandRegistryService {

    private final Map<String, CommandMetadata> commandRegistry = new HashMap<>();

    public CommandRegistryService() {
        initializeCommands();
    }

    private void initializeCommands() {
        // Register all known commands with their metadata
        registerCommand(new CommandMetadata(
            "/start",
            "Start the bot and get welcome message",
            "/start"
        ));

        registerCommand(new CommandMetadata(
            "/help",
            "Show help information about available commands",
            "/help"
        ));

        registerCommand(new CommandMetadata(
            "/menu",
            "Show main menu with available options",
            "/menu"
        ));

        registerCommand(new CommandMetadata(
            "/create_program",
            "Start creating a new workout program",
            "/create_program My Workout Plan"
        ));

        registerCommand(new CommandMetadata(
            "/show_program",
            "Show details of the current program being created",
            "/show_program"
        ));

        registerCommand(new CommandMetadata(
            "/finish_program",
            "Finish and save the current program creation session",
            "/finish_program"
        ));

        registerCommand(new CommandMetadata(
            "/cancel_program",
            "Cancel the current program creation session",
            "/cancel_program"
        ));
    }

    /**
     * Register a command with its metadata
     * @param metadata The command metadata
     */
    public void registerCommand(CommandMetadata metadata) {
        commandRegistry.put(metadata.getCommand(), metadata);
    }

    /**
     * Get metadata for a specific command
     * @param command The command name
     * @return The command metadata or null if not found
     */
    public CommandMetadata getCommandMetadata(String command) {
        return commandRegistry.get(command);
    }

    /**
     * Get all registered commands
     * @return List of all command metadata
     */
    public List<CommandMetadata> getAllCommands() {
        return new ArrayList<>(commandRegistry.values());
    }

    /**
     * Find commands that start with the given prefix
     * @param prefix The prefix to match
     * @return List of matching command metadata
     */
    public List<CommandMetadata> findCommandsByPrefix(String prefix) {
        return commandRegistry.entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    /**
     * Find commands that are similar to the given input (for typo correction)
     * @param input The input to match against
     * @return List of similar command metadata
     */
    public List<CommandMetadata> findSimilarCommands(String input) {
        // Simple approach: find commands where the input is a substring
        return commandRegistry.entrySet().stream()
                .filter(entry -> entry.getKey().contains(input) ||
                               similarity(entry.getKey(), input) > 0.5)
                .map(Map.Entry::getValue)
                .collect(Collectors.toList());
    }

    /**
     * Calculate similarity between two strings (0.0 to 1.0)
     * @param s1 First string
     * @param s2 Second string
     * @return Similarity score
     */
    private double similarity(String s1, String s2) {
        String longer = s1, shorter = s2;
        if (s1.length() < s2.length()) {
            longer = s2;
            shorter = s1;
        }
        int longerLength = longer.length();
        if (longerLength == 0) {
            return 1.0;
        }
        return (longerLength - editDistance(longer, shorter)) / (double) longerLength;
    }

    /**
     * Calculate edit distance between two strings
     * @param s1 First string
     * @param s2 Second string
     * @return Edit distance
     */
    private int editDistance(String s1, String s2) {
        s1 = s1.toLowerCase();
        s2 = s2.toLowerCase();

        int[] costs = new int[s2.length() + 1];
        for (int i = 0; i <= s1.length(); i++) {
            int lastValue = i;
            for (int j = 0; j <= s2.length(); j++) {
                if (i == 0) {
                    costs[j] = j;
                } else {
                    if (j > 0) {
                        int newValue = costs[j - 1];
                        if (s1.charAt(i - 1) != s2.charAt(j - 1)) {
                            newValue = Math.min(Math.min(newValue, lastValue), costs[j]) + 1;
                        }
                        costs[j - 1] = lastValue;
                        lastValue = newValue;
                    }
                }
            }
            if (i > 0) {
                costs[s2.length()] = lastValue;
            }
        }
        return costs[s2.length()];
    }
}