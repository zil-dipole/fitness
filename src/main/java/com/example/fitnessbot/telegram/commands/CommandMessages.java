package com.example.fitnessbot.telegram.commands;

/**
 * Constants class for command handler messages
 */
public class CommandMessages {
    
    // CreateProgramCommandHandler messages
    public static final String CREATE_PROGRAM_UNAVAILABLE = 
        "You already have an active program creation session. Please finish it first with /finish_program or cancel it with /cancel_program.";
    public static final String CREATE_PROGRAM_SUCCESS_PREFIX = "✅ Started creating program: \"";
    public static final String CREATE_PROGRAM_SUCCESS_SUFFIX = 
        "\"\n\nNow forward the training day messages you want to include in this program.\n" +
        "When you're done, send /finish_program to complete the process.";
    public static final String CREATE_PROGRAM_ERROR_PREFIX = "❌ ";
    public static final String CREATE_PROGRAM_GENERIC_ERROR = 
        "❌ Sorry, there was an error starting program creation. Please try again.";
    
    // FinishProgramCommandHandler messages
    public static final String FINISH_PROGRAM_NO_SESSION = 
        "You don't have an active program creation session. Start one with /create_program";
    public static final String FINISH_PROGRAM_SUCCESS_PREFIX = "🎉 Successfully created program: \"";
    public static final String FINISH_PROGRAM_SUCCESS_SUFFIX = 
        "\"!\n\nYou can now view your programs with /show_program.";
    public static final String FINISH_PROGRAM_ERROR_PREFIX = "❌ ";
    public static final String FINISH_PROGRAM_GENERIC_ERROR = 
        "❌ Sorry, there was an error finishing program creation. Please try again.";
        
    // CancelProgramCommandHandler messages
    public static final String CANCEL_PROGRAM_NO_SESSION = 
        "You don't have an active program creation session to cancel.";
    public static final String CANCEL_PROGRAM_SUCCESS = 
        "✅ Program creation cancelled.";
        
    // StartCommandHandler messages
    public static final String START_WELCOME_MESSAGE = 
        "Welcome to Fitness Bot! 🏋️\n\n" +
        "I help you organize and track your workout programs.\n\n" +
        "Forward your workout programs to me, and I'll parse them and save them for later use.\n\n" +
        "Simply forward your workout program messages and I'll automatically organize them for you!";
    public static final String START_MENU_BUTTON_TEXT = "Open Menu";
    public static final String START_ALREADY_ACTIVE = 
        "You're already using the bot. Type /menu to see available options.";
        
    // MenuCommandHandler messages
    public static final String MENU_TITLE = "📋 Main Menu";
    public static final String MENU_CREATE_PROGRAM = "Create Program";
    public static final String MENU_VIEW_PROGRAMS = "View Programs";
    public static final String MENU_FINISH_PROGRAM = "Finish Program Creation";
    public static final String MENU_CANCEL_PROGRAM = "Cancel Program Creation";
    public static final String MENU_HELP = "Help";
    public static final String MENU_COMMAND_PLACEHOLDER = "cmd:";
        
    // ShowProgramCommandHandler messages
    public static final String SHOW_PROGRAM_NO_SESSION = 
        "You don't have an active program creation session. Start one with /create_program";
    public static final String SHOW_PROGRAM_SESSION_TITLE_PREFIX = 
        "*Program Creation Session: ";
    public static final String SHOW_PROGRAM_SESSION_TITLE_SUFFIX = "*";
    public static final String SHOW_PROGRAM_NO_TRAINING_DAYS = 
        "\n\nNo training days added yet.";
    public static final String SHOW_PROGRAM_TRAINING_DAYS_HEADER = 
        "\n\nTraining Days Added:";
    public static final String SHOW_PROGRAM_TRAINING_DAY_FORMAT = 
        "\n- ";
        
    // ShowDayCommandHandler messages
    public static final String SHOW_DAY_NOT_FOUND = "Training day not found.";
    public static final String SHOW_DAY_INVALID_ID = "Invalid training day ID.";
    public static final String SHOW_DAY_UNAUTHORIZED = 
        "You don't have permission to view this training day.";
    public static final String SHOW_DAY_TITLE_PREFIX = "*";
    public static final String SHOW_DAY_TITLE_SUFFIX = "*";
    public static final String SHOW_DAY_EXERCISE_NUMBER_FORMAT = "%d. ";
    public static final String SHOW_DAY_EXERCISE_SETS_REPS_FORMAT = " %d x %s";
    public static final String SHOW_DAY_EXERCISE_WEIGHT_FORMAT = " @ %.1f kg";
    public static final String SHOW_DAY_EXERCISE_NOTES_PREFIX = "\nNotes: ";
    public static final String SHOW_DAY_EXERCISE_VIDEO_PREFIX = "\n🎥 Video: ";
    
    // HelpCommandHandler messages
    public static final String HELP_TITLE = "ℹ️ Fitness Bot Help";
    public static final String HELP_DESCRIPTION = 
        "I help you organize and track your workout programs.\n\n" +
        "Forward your workout program messages to me, and I'll parse them and save them for later use.";
    public static final String HELP_FORMAT_TITLE = "Supported format:";
    public static final String HELP_FORMAT_EXAMPLE = 
        "```\n" +
        "Day 1: Chest & Triceps\n\n" +
        "Activation:\n" +
        "- Band Pull-Apart 2 x 15\n" +
        "- Arm Circles 2 x 20\n\n" +
        "Main Part:\n" +
        "- Bench Press 4 x 6 (60 kg)\n" +
        "- Incline Dumbbell Press 3 x 8 (20 kg)\n" +
        "- Close-Grip Bench Press 3 x 10 (40 kg)\n\n" +
        "Finish:\n" +
        "- Cable Flyes 3 x 12 (15 kg)\n" +
        "- Tricep Pushdowns 3 x 15 (20 kg)\n" +
        "```";
    public static final String HELP_INSTRUCTIONS = 
        "Simply forward your workout program messages and I'll automatically organize them for you!";
    public static final String HELP_COMMANDS_TITLE = "Available commands:";
    public static final String HELP_COMMAND_FORMAT = "/%s - %s";
    
    private CommandMessages() {
        // Private constructor to prevent instantiation
    }
}
