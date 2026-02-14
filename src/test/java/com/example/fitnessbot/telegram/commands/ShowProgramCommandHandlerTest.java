package com.example.fitnessbot.telegram.commands;

import com.example.fitnessbot.model.Program;
import com.example.fitnessbot.model.ProgramTrainingDay;
import com.example.fitnessbot.model.TrainingDay;
import com.example.fitnessbot.model.User;
import com.example.fitnessbot.service.ProgramCreationSessionManager;
import com.example.fitnessbot.service.ProgramService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ShowProgramCommandHandlerTest {

    @Mock
    private ProgramService programService;

    @Mock
    private ProgramCreationSessionManager sessionManager;

    private ShowProgramCommandHandler handler;

    @BeforeEach
    void setUp() {
        handler = new ShowProgramCommandHandler(programService, sessionManager);
    }

    @Test
    void testCanHandle() {
        assertTrue(handler.canHandle("/show_program"));
        assertFalse(handler.canHandle("/start"));
        assertFalse(handler.canHandle("/help"));
        assertFalse(handler.canHandle("/create_program"));
    }

    @Test
    void testHandleWithoutActiveProgram() {
        // Given
        Update update = createMockUpdate();
        when(programService.getActiveProgram(12345L)).thenReturn(null);

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertNotNull(response);
        assertEquals("6789", response.getChatId());
        assertTrue(response.getText().contains("You don't have an active program"));
        assertNull(response.getParseMode()); // No markdown for plain text
        assertNull(response.getReplyMarkup()); // No keyboard for this case
        
        // Verify interactions
        verify(programService).getActiveProgram(12345L);
        verifyNoMoreInteractions(programService);
    }

    @Test
    void testHandleWithActiveProgramButNoTrainingDays() {
        // Given
        Update update = createMockUpdate();
        
        Program program = new Program();
        program.setId(1L);
        program.setName("My Workout Program");
        User user = new User();
        user.setId(1L);
        program.setUser(user);
        program.setProgramTrainingDays(Collections.emptyList());

        when(programService.getActiveProgram(12345L)).thenReturn(program);

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertNotNull(response);
        assertEquals("6789", response.getChatId());
        assertEquals("Markdown", response.getParseMode());
        assertTrue(response.getText().contains("*Active Program: My Workout Program*"));
        assertTrue(response.getText().contains("No training days added yet."));
        assertNull(response.getReplyMarkup()); // No keyboard when no training days
        
        // Verify interactions
        verify(programService).getActiveProgram(12345L);
        verifyNoMoreInteractions(programService);
    }

    @Test
    void testHandleWithActiveProgramAndTrainingDays() {
        // Given
        Update update = createMockUpdate();
        
        Program program = new Program();
        program.setId(1L);
        program.setName("My Workout Program");
        User user = new User();
        user.setId(1L);
        program.setUser(user);
        
        TrainingDay td1 = new TrainingDay();
        td1.setId(1L);
        td1.setTitle("Upper Body");
        
        TrainingDay td2 = new TrainingDay();
        td2.setId(2L);
        td2.setTitle("Lower Body");
        
        ProgramTrainingDay ptd1 = new ProgramTrainingDay();
        ptd1.setProgram(program);
        ptd1.setTrainingDay(td1);
        ptd1.setPosition(1);
        
        ProgramTrainingDay ptd2 = new ProgramTrainingDay();
        ptd2.setProgram(program);
        ptd2.setTrainingDay(td2);
        ptd2.setPosition(2);
        
        program.setProgramTrainingDays(Arrays.asList(ptd1, ptd2));

        when(programService.getActiveProgram(12345L)).thenReturn(program);

        // When
        SendMessage response = handler.handle(update);

        // Then
        assertNotNull(response);
        assertEquals("6789", response.getChatId());
        assertEquals("Markdown", response.getParseMode());
        assertTrue(response.getText().contains("*Active Program: My Workout Program*"));
        assertTrue(response.getText().contains("Training Days:"));
        assertTrue(response.getText().contains("- Upper Body"));
        assertTrue(response.getText().contains("- Lower Body"));
        
        // Verify inline keyboard
        assertNotNull(response.getReplyMarkup());
        assertTrue(response.getReplyMarkup() instanceof InlineKeyboardMarkup);
        
        InlineKeyboardMarkup markup = (InlineKeyboardMarkup) response.getReplyMarkup();
        List<List<InlineKeyboardButton>> keyboard = markup.getKeyboard();
        
        assertEquals(2, keyboard.size()); // Two rows for two training days
        assertEquals(1, keyboard.get(0).size()); // One button per row
        assertEquals(1, keyboard.get(1).size()); // One button per row
        
        InlineKeyboardButton button1 = keyboard.get(0).get(0);
        assertEquals("Upper Body", button1.getText());
        assertEquals("show_day_1", button1.getCallbackData());
        
        InlineKeyboardButton button2 = keyboard.get(1).get(0);
        assertEquals("Lower Body", button2.getText());
        assertEquals("show_day_2", button2.getCallbackData());
        
        // Verify interactions
        verify(programService).getActiveProgram(12345L);
        verifyNoMoreInteractions(programService);
    }

    private Update createMockUpdate() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);
        org.telegram.telegrambots.meta.api.objects.User user = mock(org.telegram.telegrambots.meta.api.objects.User.class);

        lenient().when(update.getMessage()).thenReturn(message);
        lenient().when(message.getText()).thenReturn("/show_program");
        lenient().when(message.getFrom()).thenReturn(user);
        lenient().when(user.getId()).thenReturn(12345L);
        lenient().when(message.getChatId()).thenReturn(6789L);

        return update;
    }
}