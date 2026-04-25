package com.example.fitnessbot.telegram;

import com.example.fitnessbot.service.ProgramCreationSessionManager;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

@Component
public class DefaultMenuKeyboardFactory implements MenuKeyboardFactory {
    
    private final ProgramCreationSessionManager sessionManager;

    public DefaultMenuKeyboardFactory(ProgramCreationSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public InlineKeyboardMarkup createMainMenuKeyboard(Long userId) {
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // First row - Create Program (only shown when no active session)
        if (!sessionManager.hasActiveSession(userId)) {
            List<InlineKeyboardButton> firstRow = new ArrayList<>();
            InlineKeyboardButton createProgramBtn = new InlineKeyboardButton();
            createProgramBtn.setText("Create Program");
            createProgramBtn.setCallbackData("create_program");
            firstRow.add(createProgramBtn);

            InlineKeyboardButton viewProgramsBtn = new InlineKeyboardButton();
            viewProgramsBtn.setText("View Programs");
            viewProgramsBtn.setCallbackData("view_programs");
            firstRow.add(viewProgramsBtn);

            rows.add(firstRow);
        }

        // Cancel/Finish Program (only shown when active session exists)
        if (sessionManager.hasActiveSession(userId)) {
            List<InlineKeyboardButton> sessionControlRow = new ArrayList<>();

            InlineKeyboardButton finishProgramBtn = new InlineKeyboardButton();
            finishProgramBtn.setText("Finish Program");
            finishProgramBtn.setCallbackData("finish_program");
            sessionControlRow.add(finishProgramBtn);

            InlineKeyboardButton cancelProgramBtn = new InlineKeyboardButton();
            cancelProgramBtn.setText("Cancel Program");
            cancelProgramBtn.setCallbackData("cancel_program");
            sessionControlRow.add(cancelProgramBtn);

            rows.add(sessionControlRow);
        }

        // Last row - Help
        List<InlineKeyboardButton> lastRow = new ArrayList<>();
        InlineKeyboardButton helpBtn = new InlineKeyboardButton();
        helpBtn.setText("Help");
        helpBtn.setCallbackData("help");
        lastRow.add(helpBtn);

        rows.add(lastRow);

        markup.setKeyboard(rows);
        return markup;
    }
}