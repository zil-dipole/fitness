package com.example.fitnessbot.exception;

/**
 * Custom exception class for Fitness Bot application
 */
public class FitnessBotException extends Exception {
    
    public FitnessBotException(String message) {
        super(message);
    }
    
    public FitnessBotException(String message, Throwable cause) {
        super(message, cause);
    }
}