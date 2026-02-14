package com.example.fitnessbot.exception;

/**
 * Exception for program-related operations
 */
public class ProgramException extends FitnessBotException {
    
    public ProgramException(String message) {
        super(message);
    }
    
    public ProgramException(String message, Throwable cause) {
        super(message, cause);
    }
}