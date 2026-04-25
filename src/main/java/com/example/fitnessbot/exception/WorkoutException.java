package com.example.fitnessbot.exception;

public class WorkoutException extends FitnessBotException {
    public WorkoutException(String message) {
        super(message);
    }

    public WorkoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
