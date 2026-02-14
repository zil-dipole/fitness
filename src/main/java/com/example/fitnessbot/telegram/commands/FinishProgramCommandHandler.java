package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.model.TrainingDay;
import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.service.ProgramService;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * Handler for the /finish_program command
 */
@Component
public class FinishProgramCommandHandler implements CommandHandler {

    public static final String COMMAND = "/finish_program";
    private final ProgramService programService;
    private final ProgramCreationSessionManager sessionManager;
    
    public FinishProgramCommandHandler(ProgramService programService, 
                                      ProgramCreationSessionManager sessionManager) {
        this.programService = programService;
        this.sessionManager = sessionManager;
    }
    
    @Override
    public boolean canHandle(String command) {
        return COMMAND.equals(command);
    }
    
    @Override
    public SendMessage handle(Update update) {
        Long userId = update.getMessage().getFrom().getId();
        
        // Check if user has an active session
        if (!sessionManager.hasActiveSession(userId)) {
            SendMessage response = new SendMessage();
            response.setChatId(update.getMessage().getChatId().toString());
            response.setText("You don't have an active program creation session. Start one with /create_program <program_name>");
            return response;
        }
        
        try {
            // Get the session
            var session = sessionManager.getSession(userId);
            var program = session.getProgram();
            var trainingDays = session.getTrainingDays();
            
            // Check if any training days were added
            if (trainingDays.isEmpty()) {
                SendMessage response = new SendMessage();
                response.setChatId(update.getMessage().getChatId().toString());
                response.setText("⚠️ No training days were added to your program. Please forward at least one training day message before finishing.");
                return response;
            }
            
            // Add all training days to the program
            int position = 1;
            for (TrainingDay trainingDay : trainingDays) {
                programService.addTrainingDayToProgram(program.getId(), trainingDay.getId(), position++);
            }
            
            // End the session
            sessionManager.endSession(userId);
            
            SendMessage response = new SendMessage();
            response.setChatId(update.getMessage().getChatId().toString());
            response.setText("✅ Program \"" + program.getName() + "\" created successfully!\n" +
                          "Added " + trainingDays.size() + " training days to the program.");
            return response;
        } catch (Exception e) {
            SendMessage response = new SendMessage();
            response.setChatId(update.getMessage().getChatId().toString());
            response.setText("❌ Sorry, there was an error finishing program creation. Please try again.");
            return response;
        }
    }

    @Override
    public String getCommand() {
        return COMMAND;
    }

    @Override
    public String getCommandDescription() {
        return "Finish current session of programm creation";
    }
}