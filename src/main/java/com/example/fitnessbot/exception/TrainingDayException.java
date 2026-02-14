package com.example.fitnessbot.exception;

/**
 * Exception for training day-related operations
 */
public class TrainingDayException extends FitnessBotException {
    
    public TrainingDayException(String message) {
        super(message);
    }
    
    public TrainingDayException(String message, Throwable cause) {
        super(message, cause);
    }
}