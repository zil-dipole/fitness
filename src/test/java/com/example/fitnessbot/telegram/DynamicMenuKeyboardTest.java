package com.example.fitnessbot.telegram;

import com.example.fitnessbot.model.Program;
import com.example.fitnessbot.model.UserLanguage;
import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.service.TestProgramCreationSessionManagers;
import com.example.fitnessbot.service.UserLanguageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DynamicMenuKeyboardTest {

    private static final Long TEST_USER_ID = 12345L;

    private ProgramCreationSessionManager sessionManager;
    private DefaultMenuKeyboardFactory menuKeyboardFactory;

    @BeforeEach
    void setUp() {
        sessionManager = TestProgramCreationSessionManagers.redisBacked();
        menuKeyboardFactory = new DefaultMenuKeyboardFactory(sessionManager);
    }

    @Test
    void testMainMenuWithoutActiveSession() {
        InlineKeyboardMarkup markup = menuKeyboardFactory.createMainMenuKeyboard(TEST_USER_ID);

        List<List<InlineKeyboardButton>> keyboard = markup.getKeyboard();
        assertThat(keyboard).hasSize(2);
        assertThat(keyboard.get(0)).extracting(InlineKeyboardButton::getText)
                .containsExactly("Create Program", "View Programs");
        assertThat(keyboard.get(0)).extracting(InlineKeyboardButton::getCallbackData)
                .containsExactly("create_program", "view_programs");
        assertThat(keyboard.get(1)).extracting(InlineKeyboardButton::getText)
                .containsExactly("Language", "Help");
        assertThat(keyboard.get(1)).extracting(InlineKeyboardButton::getCallbackData)
                .containsExactly("language", "help");
    }

    @Test
    void testMainMenuWithActiveSession() {
        Program program = new Program();
        program.setName("Test Program");
        sessionManager.startSession(TEST_USER_ID, program);

        InlineKeyboardMarkup markup = menuKeyboardFactory.createMainMenuKeyboard(TEST_USER_ID);

        List<List<InlineKeyboardButton>> keyboard = markup.getKeyboard();
        assertThat(keyboard).hasSize(2);
        assertThat(keyboard.get(0)).extracting(InlineKeyboardButton::getText)
                .containsExactly("Finish Program Creation", "Cancel Program Creation");
        assertThat(keyboard.get(0)).extracting(InlineKeyboardButton::getCallbackData)
                .containsExactly("finish_program", "cancel_program");
        assertThat(keyboard.get(1)).extracting(InlineKeyboardButton::getText)
                .containsExactly("Language", "Help");
    }

    @Test
    void testMainMenuWhileWaitingForProgramName() {
        sessionManager.startAwaitingProgramName(TEST_USER_ID);

        InlineKeyboardMarkup markup = menuKeyboardFactory.createMainMenuKeyboard(TEST_USER_ID);

        List<List<InlineKeyboardButton>> keyboard = markup.getKeyboard();
        assertThat(keyboard).hasSize(2);
        assertThat(keyboard.get(0)).extracting(InlineKeyboardButton::getText)
                .containsExactly("Cancel Program Creation");
        assertThat(keyboard.get(0)).extracting(InlineKeyboardButton::getCallbackData)
                .containsExactly("cancel_program");
        assertThat(keyboard.get(1)).extracting(InlineKeyboardButton::getText)
                .containsExactly("Language", "Help");
    }

    @Test
    void testMainMenuTransitionsBetweenStates() {
        InlineKeyboardMarkup withoutSession = menuKeyboardFactory.createMainMenuKeyboard(TEST_USER_ID);
        assertThat(withoutSession.getKeyboard().getFirst().getFirst().getText()).isEqualTo("Create Program");

        Program program = new Program();
        program.setName("Test Program");
        sessionManager.startSession(TEST_USER_ID, program);

        InlineKeyboardMarkup withSession = menuKeyboardFactory.createMainMenuKeyboard(TEST_USER_ID);
        assertThat(withSession.getKeyboard().getFirst().getFirst().getText()).isEqualTo("Finish Program Creation");

        sessionManager.endSession(TEST_USER_ID);

        InlineKeyboardMarkup afterCancel = menuKeyboardFactory.createMainMenuKeyboard(TEST_USER_ID);
        assertThat(afterCancel.getKeyboard().getFirst().getFirst().getText()).isEqualTo("Create Program");
    }

    @Test
    void testMainMenuUsesRussianLabels() {
        UserLanguageService languageService = mock(UserLanguageService.class);
        when(languageService.getLanguage(TEST_USER_ID)).thenReturn(UserLanguage.RUSSIAN);
        DefaultMenuKeyboardFactory russianFactory = new DefaultMenuKeyboardFactory(sessionManager, languageService);

        InlineKeyboardMarkup markup = russianFactory.createMainMenuKeyboard(TEST_USER_ID);

        List<List<InlineKeyboardButton>> keyboard = markup.getKeyboard();
        assertThat(keyboard.get(0)).extracting(InlineKeyboardButton::getText)
                .containsExactly("Создать программу", "Мои программы");
        assertThat(keyboard.get(1)).extracting(InlineKeyboardButton::getText)
                .containsExactly("Язык", "Помощь");
    }

    @Test
    void testMainMenuUsesRussianDraftSessionLabels() {
        UserLanguageService languageService = mock(UserLanguageService.class);
        when(languageService.getLanguage(TEST_USER_ID)).thenReturn(UserLanguage.RUSSIAN);
        DefaultMenuKeyboardFactory russianFactory = new DefaultMenuKeyboardFactory(sessionManager, languageService);
        Program program = new Program();
        program.setName("Тестовая программа");
        sessionManager.startSession(TEST_USER_ID, program);

        InlineKeyboardMarkup markup = russianFactory.createMainMenuKeyboard(TEST_USER_ID);

        List<List<InlineKeyboardButton>> keyboard = markup.getKeyboard();
        assertThat(keyboard.get(0)).extracting(InlineKeyboardButton::getText)
                .containsExactly("Завершить создание программы", "Отменить создание программы");
        assertThat(keyboard.get(0)).extracting(InlineKeyboardButton::getCallbackData)
                .containsExactly("finish_program", "cancel_program");
    }
}
