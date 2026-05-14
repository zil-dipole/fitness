package com.example.fitnessbot.telegram;

import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.service.UserLanguageService;
import com.example.fitnessbot.telegram.commands.BotText;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.ArrayList;
import java.util.List;

@Component
public class DefaultMenuKeyboardFactory implements MenuKeyboardFactory {
    
    private final ProgramCreationSessionManager sessionManager;
    private final UserLanguageService languageService;

    @Autowired
    public DefaultMenuKeyboardFactory(ProgramCreationSessionManager sessionManager,
                                      UserLanguageService languageService) {
        this.sessionManager = sessionManager;
        this.languageService = languageService;
    }

    public DefaultMenuKeyboardFactory(ProgramCreationSessionManager sessionManager) {
        this.sessionManager = sessionManager;
        this.languageService = null;
    }

    @Override
    public InlineKeyboardMarkup createMainMenuKeyboard(Long userId) {
        var language = BotText.language(languageService, userId);
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> rows = new ArrayList<>();

        // First row - Create Program (only shown when no active session)
        if (!sessionManager.hasActiveSession(userId)) {
            List<InlineKeyboardButton> firstRow = new ArrayList<>();
            InlineKeyboardButton createProgramBtn = new InlineKeyboardButton();
            createProgramBtn.setText(BotText.createProgramButton(language));
            createProgramBtn.setCallbackData("create_program");
            firstRow.add(createProgramBtn);

            InlineKeyboardButton viewProgramsBtn = new InlineKeyboardButton();
            viewProgramsBtn.setText(BotText.viewProgramsButton(language));
            viewProgramsBtn.setCallbackData("view_programs");
            firstRow.add(viewProgramsBtn);

            rows.add(firstRow);
        }

        // Cancel/Finish Program (only shown when active session exists)
        if (sessionManager.hasActiveSession(userId)) {
            List<InlineKeyboardButton> sessionControlRow = new ArrayList<>();

            InlineKeyboardButton finishProgramBtn = new InlineKeyboardButton();
            finishProgramBtn.setText(BotText.finishProgramButton(language));
            finishProgramBtn.setCallbackData("finish_program");
            sessionControlRow.add(finishProgramBtn);

            InlineKeyboardButton cancelProgramBtn = new InlineKeyboardButton();
            cancelProgramBtn.setText(BotText.cancelProgramButton(language));
            cancelProgramBtn.setCallbackData("cancel_program");
            sessionControlRow.add(cancelProgramBtn);

            rows.add(sessionControlRow);
        }

        // Last row - language and help
        List<InlineKeyboardButton> lastRow = new ArrayList<>();
        InlineKeyboardButton languageBtn = new InlineKeyboardButton();
        languageBtn.setText(BotText.languageButton(language));
        languageBtn.setCallbackData("language");
        lastRow.add(languageBtn);

        InlineKeyboardButton helpBtn = new InlineKeyboardButton();
        helpBtn.setText(BotText.helpButton(language));
        helpBtn.setCallbackData("help");
        lastRow.add(helpBtn);

        rows.add(lastRow);

        markup.setKeyboard(rows);
        return markup;
    }
}
