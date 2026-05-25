package com.example.fitnessbot.telegram.commands;

/**
 * Constants for command responses and messages
 */
public class CommandConstants {
    // CreateProgramCommandHandler messages
    public static final String CREATE_PROGRAM_UNAVAILABLE_ACTIVE_SESSION = 
        "You already have an active program creation session. Please finish it first with /finish_program or cancel it with /cancel_program.";
    
    public static final String CREATE_PROGRAM_SUCCESS_PREFIX = "✅ Started creating program: \"";
    public static final String CREATE_PROGRAM_SUCCESS_SUFFIX = "\"\n\n" +
        "Now forward the training day messages you want to include in this program.\n" +
        "When you're done, send /finish_program to complete the process.";
    
    public static final String CREATE_PROGRAM_ERROR_PREFIX = "❌ ";
    public static final String CREATE_PROGRAM_GENERIC_ERROR = 
        "❌ Sorry, there was an error starting program creation. Please try again.";
    
    // FinishProgramCommandHandler messages
    public static final String FINISH_PROGRAM_NO_ACTIVE_SESSION = 
        "You don't have an active program creation session. Start one with /create_program";
    
    public static final String FINISH_PROGRAM_CONFIRMATION_HEADER = "🎉 Program completed successfully!";
    public static final String FINISH_PROGRAM_CONFIRMATION_DETAILS = 
        "You can now view it with /show_program or start a new one with /create_program.";
    
    // CancelProgramCommandHandler messages
    public static final String CANCEL_PROGRAM_NO_ACTIVE_SESSION = 
        "You don't have an active program creation session to cancel.";
    
    public static final String CANCEL_PROGRAM_SUCCESS = 
        "✅ Program creation cancelled.";
    
    // ShowProgramCommandHandler messages
    public static final String SHOW_PROGRAM_NO_ACTIVE_SESSION = 
        "You don't have an active program creation session. Start one with /create_program";
    
    public static final String SHOW_PROGRAM_MARKDOWN_HEADER_START = "*Program Creation Session: ";
    public static final String SHOW_PROGRAM_MARKDOWN_HEADER_END = "*\n\n";
    public static final String SHOW_PROGRAM_NO_TRAINING_DAYS = "No training days added yet.";
    public static final String SHOW_PROGRAM_TRAINING_DAYS_HEADER = "Training Days Added:\n";
    public static final String SHOW_PROGRAM_TRAINING_DAY_ITEM_PREFIX = "- ";
    
    // StartCommandHandler messages
    public static final String START_WELCOME_MESSAGE = 
        "👋 Welcome to Fitness Bot!\n\n" +
        "I help you organize and track your workout programs.\n\n" +
        "✅ Simply forward your workout program messages to me\n" +
        "📊 I'll parse them and organize them for easy access\n" +
        "🏋️‍♂️ Navigate through exercises during your training sessions\n" +
        "📈 Track your progress over time\n\n" +
        "Supported format:\n" +
        "```\n" +
        "Треня 1:\n\n" +
        "Разминка:\n" +
        "- Бег 5 мин\n" +
        "\n" +
        "Основная часть:\n" +
        "- Приседания 3 x 8-10 (50 кг)\n" +
        "- Жим штанги лёжа 3 x 6 (60 кг)\n" +
        "- Подтягивания 3 x MAX\n" +
        "```\n\n" +
        "Just forward such messages to me, and I'll save them for later use!";
    
    public static final String START_ALREADY_REGISTERED = 
        "You're already using the bot! You can continue with your current session or use the menu below.";
    
    // ShowDayCommandHandler messages
    public static final String SHOW_DAY_NOT_FOUND = "Training day not found.";
    public static final String SHOW_DAY_INVALID_ID = "Invalid training day ID.";
    public static final String SHOW_DAY_UNAUTHORIZED = "You don't have permission to view this training day.";
}
