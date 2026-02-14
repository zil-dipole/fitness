package com.example.fitnessbot.service;

import com.example.fitnessbot.model.Program;
import com.example.fitnessbot.model.User;
import com.example.fitnessbot.repository.ProgramRepository;
import com.example.fitnessbot.repository.ProgramTrainingDayRepository;
import com.example.fitnessbot.repository.TrainingDayRepository;
import com.example.fitnessbot.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProgramServiceTest {

    @Mock
    private ProgramRepository programRepository;

    @Mock
    private ProgramTrainingDayRepository programTrainingDayRepository;

    @Mock
    private TrainingDayRepository trainingDayRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProgramCreationSessionManager sessionManager;

    private ProgramService programService;

    @BeforeEach
    void setUp() {
        programService = new ProgramService(
            programRepository,
            programTrainingDayRepository,
            trainingDayRepository,
            userRepository,
            sessionManager
        );
    }

    @Test
    void testHasActiveSession() {
        // Given
        Long userId = 123L;
        when(sessionManager.hasActiveSession(userId)).thenReturn(true);

        // When
        boolean result = programService.hasActiveSession(userId);

        // Then
        assertTrue(result);
        verify(sessionManager).hasActiveSession(userId);
    }

    @Test
    void testGetCurrentProgramInCreationWithActiveSession() {
        // Given
        Long userId = 123L;
        Program program = new Program();
        program.setId(1L);
        program.setName("Test Program");
        User user = new User();
        user.setId(1L);
        program.setUser(user);

        when(sessionManager.hasActiveSession(userId)).thenReturn(true);

        ProgramCreationSessionManager.ProgramCreationSession session =
            mock(ProgramCreationSessionManager.ProgramCreationSession.class);
        when(sessionManager.getSession(userId)).thenReturn(session);
        when(session.getProgram()).thenReturn(program);

        // When
        Program result = programService.getCurrentProgramInCreation(userId);

        // Then
        assertNotNull(result);
        assertEquals(program, result);
        assertEquals("Test Program", result.getName());
    }

    @Test
    void testGetCurrentProgramInCreationWithoutActiveSession() {
        // Given
        Long userId = 123L;
        when(sessionManager.hasActiveSession(userId)).thenReturn(false);

        // When
        Program result = programService.getCurrentProgramInCreation(userId);

        // Then
        assertNull(result);
    }

    @Test
    void testGetActiveProgramWhenUserExists() {
        // Given
        Long telegramUserId = 123L;
        Long userId = 1L;
        
        User user = new User();
        user.setId(userId);
        user.setTelegramId(telegramUserId);

        Program program = new Program();
        program.setId(1L);
        program.setName("Active Program");
        program.setUser(user);

        when(userRepository.findByTelegramId(telegramUserId)).thenReturn(Optional.of(user));
        when(programRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.of(program));

        // When
        Program result = programService.getActiveProgram(telegramUserId);

        // Then
        assertNotNull(result);
        assertEquals(program, result);
        assertEquals("Active Program", result.getName());
        verify(userRepository).findByTelegramId(telegramUserId);
        verify(programRepository).findFirstByUserIdOrderByCreatedAtDesc(userId);
    }

    @Test
    void testGetActiveProgramWhenUserDoesNotExist() {
        // Given
        Long telegramUserId = 123L;
        when(userRepository.findByTelegramId(telegramUserId)).thenReturn(Optional.empty());

        // When
        Program result = programService.getActiveProgram(telegramUserId);

        // Then
        assertNull(result);
        verify(userRepository).findByTelegramId(telegramUserId);
        verifyNoMoreInteractions(programRepository);
    }

    @Test
    void testGetActiveProgramWhenUserExistsButNoPrograms() {
        // Given
        Long telegramUserId = 123L;
        Long userId = 1L;
        
        User user = new User();
        user.setId(userId);
        user.setTelegramId(telegramUserId);

        when(userRepository.findByTelegramId(telegramUserId)).thenReturn(Optional.of(user));
        when(programRepository.findFirstByUserIdOrderByCreatedAtDesc(userId)).thenReturn(Optional.empty());

        // When
        Program result = programService.getActiveProgram(telegramUserId);

        // Then
        assertNull(result);
        verify(userRepository).findByTelegramId(telegramUserId);
        verify(programRepository).findFirstByUserIdOrderByCreatedAtDesc(userId);
    }
}