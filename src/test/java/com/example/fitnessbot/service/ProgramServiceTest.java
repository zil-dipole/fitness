package com.example.fitnessbot.service;

import com.example.fitnessbot.exception.ProgramException;
import com.example.fitnessbot.exception.TrainingDayException;
import com.example.fitnessbot.model.Program;
import com.example.fitnessbot.model.ProgramTrainingDay;
import com.example.fitnessbot.model.TrainingDay;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.assertj.core.api.Assertions.*;
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

    @Test
    void testStartProgramCreationSuccess() throws Exception {
        // Given
        Long telegramUserId = 123L;
        String programName = "New Program";
        User user = new User();
        user.setId(1L);
        user.setTelegramId(telegramUserId);

        Program program = new Program();
        program.setId(1L);
        program.setName(programName);
        program.setUser(user);

        when(userRepository.findByTelegramId(telegramUserId)).thenReturn(Optional.of(user));
        when(programRepository.save(any(Program.class))).thenReturn(program);

        // When
        Program result = programService.startProgramCreation(telegramUserId, programName);

        // Then
        assertNotNull(result);
        assertEquals(programName, result.getName());
        verify(userRepository).findByTelegramId(telegramUserId);
        verify(programRepository).save(any(Program.class));
    }

    @Test
    void testStartProgramCreationCreatesUserIfNotExists() throws Exception {
        // Given
        Long telegramUserId = 123L;
        String programName = "New Program";
        User newUser = new User();
        newUser.setId(1L);
        newUser.setTelegramId(telegramUserId);

        Program program = new Program();
        program.setId(1L);
        program.setName(programName);
        program.setUser(newUser);

        when(userRepository.findByTelegramId(telegramUserId)).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(newUser);
        when(programRepository.save(any(Program.class))).thenReturn(program);

        // When
        Program result = programService.startProgramCreation(telegramUserId, programName);

        // Then
        assertNotNull(result);
        assertEquals(programName, result.getName());
        verify(userRepository).findByTelegramId(telegramUserId);
        verify(userRepository).save(any(User.class));
        verify(programRepository).save(any(Program.class));
    }

    @Test
    void testAddTrainingDayToProgramSuccess() throws Exception {
        // Given
        Long programId = 1L;
        Long trainingDayId = 2L;
        Integer position = 1;

        User user = new User();
        user.setId(1L);

        Program program = new Program();
        program.setId(programId);
        program.setUser(user);

        TrainingDay trainingDay = new TrainingDay();
        trainingDay.setId(trainingDayId);
        trainingDay.setUser(user); // Same user as program

        ProgramTrainingDay programTrainingDay = new ProgramTrainingDay();
        programTrainingDay.setProgram(program);
        programTrainingDay.setTrainingDay(trainingDay);
        programTrainingDay.setPosition(position);

        when(programRepository.findById(programId)).thenReturn(Optional.of(program));
        when(trainingDayRepository.findById(trainingDayId)).thenReturn(Optional.of(trainingDay));
        when(programTrainingDayRepository.save(any(ProgramTrainingDay.class))).thenReturn(programTrainingDay);

        // When
        ProgramTrainingDay result = programService.addTrainingDayToProgram(programId, trainingDayId, position);

        // Then
        assertNotNull(result);
        assertEquals(program, result.getProgram());
        assertEquals(trainingDay, result.getTrainingDay());
        assertEquals(position, result.getPosition());
        verify(programRepository).findById(programId);
        verify(trainingDayRepository).findById(trainingDayId);
        verify(programTrainingDayRepository).save(any(ProgramTrainingDay.class));
    }

    @Test
    void testAddTrainingDayToProgramProgramNotFound() {
        // Given
        Long programId = 1L;
        Long trainingDayId = 2L;
        Integer position = 1;

        when(programRepository.findById(programId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ProgramException.class, () -> {
            programService.addTrainingDayToProgram(programId, trainingDayId, position);
        });
        
        verify(programRepository).findById(programId);
        verifyNoMoreInteractions(trainingDayRepository);
        verifyNoMoreInteractions(programTrainingDayRepository);
    }

    @Test
    void testAddTrainingDayToProgramTrainingDayNotFound() {
        // Given
        Long programId = 1L;
        Long trainingDayId = 2L;
        Integer position = 1;

        User user = new User();
        user.setId(1L);

        Program program = new Program();
        program.setId(programId);
        program.setUser(user);

        when(programRepository.findById(programId)).thenReturn(Optional.of(program));
        when(trainingDayRepository.findById(trainingDayId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(TrainingDayException.class, () -> {
            programService.addTrainingDayToProgram(programId, trainingDayId, position);
        });
        
        verify(programRepository).findById(programId);
        verify(trainingDayRepository).findById(trainingDayId);
        verifyNoMoreInteractions(programTrainingDayRepository);
    }

    @Test
    void testAddTrainingDayToProgramUserMismatch() {
        // Given
        Long programId = 1L;
        Long trainingDayId = 2L;
        Integer position = 1;

        User programUser = new User();
        programUser.setId(1L);

        User trainingDayUser = new User();
        trainingDayUser.setId(2L); // Different user

        Program program = new Program();
        program.setId(programId);
        program.setUser(programUser);

        TrainingDay trainingDay = new TrainingDay();
        trainingDay.setId(trainingDayId);
        trainingDay.setUser(trainingDayUser); // Different user from program

        when(programRepository.findById(programId)).thenReturn(Optional.of(program));
        when(trainingDayRepository.findById(trainingDayId)).thenReturn(Optional.of(trainingDay));

        // When & Then
        assertThrows(ProgramException.class, () -> {
            programService.addTrainingDayToProgram(programId, trainingDayId, position);
        });

        verify(programRepository).findById(programId);
        verify(trainingDayRepository).findById(trainingDayId);
        verifyNoMoreInteractions(programTrainingDayRepository);
    }
    
    @Test
    void testGetProgramsForUser() {
        // Given
        Long telegramUserId = 123L;
        Long userId = 1L;
        
        User user = new User();
        user.setId(userId);
        user.setTelegramId(telegramUserId);
        
        Program program1 = new Program();
        program1.setId(1L);
        program1.setName("Program 1");
        program1.setUser(user);
        
        Program program2 = new Program();
        program2.setId(2L);
        program2.setName("Program 2");
        program2.setUser(user);
        
        List<Program> programs = List.of(program1, program2);
        
        when(programRepository.findByUserId(userId)).thenReturn(programs);
        when(userRepository.findByTelegramId(telegramUserId)).thenReturn(Optional.of(user));
        
        // When
        List<Program> result = programService.getProgramsForUser(telegramUserId);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Program 1");
        assertThat(result.get(1).getName()).isEqualTo("Program 2");
        
        verify(userRepository).findByTelegramId(telegramUserId);
        verify(programRepository).findByUserId(userId);
    }
    
    @Test
    void testGetProgramsForUserNoUser() {
        // Given
        Long telegramUserId = 123L;
        
        when(userRepository.findByTelegramId(telegramUserId)).thenReturn(Optional.empty());
        
        // When
        List<Program> result = programService.getProgramsForUser(telegramUserId);
        
        // Then
        assertThat(result).isNotNull();
        assertThat(result).isEmpty();
        
        verify(userRepository).findByTelegramId(telegramUserId);
        verifyNoMoreInteractions(programRepository);
    }
    
    @Test
    void testGetProgramForUser() {
        // Given
        Long programId = 1L;
        Long telegramUserId = 123L;
        Long userId = 1L;
        
        User user = new User();
        user.setId(userId);
        user.setTelegramId(telegramUserId);
        
        Program program = new Program();
        program.setId(programId);
        program.setName("Test Program");
        program.setUser(user);
        
        when(programRepository.findByIdAndUserId(programId, userId)).thenReturn(Optional.of(program));
        when(userRepository.findByTelegramId(telegramUserId)).thenReturn(Optional.of(user));
        
        // When
        Optional<Program> result = programService.getProgramForUser(programId, telegramUserId);
        
        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Test Program");
        
        verify(userRepository).findByTelegramId(telegramUserId);
        verify(programRepository).findByIdAndUserId(programId, userId);
    }
    
    @Test
    void testGetProgramForUserNotFound() {
        // Given
        Long programId = 1L;
        Long telegramUserId = 123L;
        Long userId = 1L;
        
        User user = new User();
        user.setId(userId);
        user.setTelegramId(telegramUserId);
        
        when(programRepository.findByIdAndUserId(programId, userId)).thenReturn(Optional.empty());
        when(userRepository.findByTelegramId(telegramUserId)).thenReturn(Optional.of(user));
        
        // When
        Optional<Program> result = programService.getProgramForUser(programId, telegramUserId);
        
        // Then
        assertThat(result).isEmpty();
        
        verify(userRepository).findByTelegramId(telegramUserId);
        verify(programRepository).findByIdAndUserId(programId, userId);
    }
    
    @Test
    void testGetProgramForUserNoUser() {
        // Given
        Long programId = 1L;
        Long telegramUserId = 123L;
        
        when(userRepository.findByTelegramId(telegramUserId)).thenReturn(Optional.empty());
        
        // When
        Optional<Program> result = programService.getProgramForUser(programId, telegramUserId);
        
        // Then
        assertThat(result).isEmpty();
        
        verify(userRepository).findByTelegramId(telegramUserId);
        verifyNoMoreInteractions(programRepository);
    }

    @Test
    void testGetProgramTrainingDaysForUser() {
        // Given
        Long programId = 1L;
        Long telegramUserId = 123L;
        Long userId = 1L;

        User user = new User();
        user.setId(userId);
        user.setTelegramId(telegramUserId);

        Program program = new Program();
        program.setId(programId);
        program.setUser(user);

        ProgramTrainingDay firstDay = new ProgramTrainingDay();
        firstDay.setPosition(1);
        ProgramTrainingDay secondDay = new ProgramTrainingDay();
        secondDay.setPosition(2);
        List<ProgramTrainingDay> trainingDays = List.of(firstDay, secondDay);

        when(userRepository.findByTelegramId(telegramUserId)).thenReturn(Optional.of(user));
        when(programRepository.findByIdAndUserId(programId, userId)).thenReturn(Optional.of(program));
        when(programTrainingDayRepository.findByProgramIdOrderByPositionAsc(programId)).thenReturn(trainingDays);

        // When
        List<ProgramTrainingDay> result = programService.getProgramTrainingDaysForUser(programId, telegramUserId);

        // Then
        assertThat(result).containsExactly(firstDay, secondDay);
        verify(userRepository).findByTelegramId(telegramUserId);
        verify(programRepository).findByIdAndUserId(programId, userId);
        verify(programTrainingDayRepository).findByProgramIdOrderByPositionAsc(programId);
    }

    @Test
    void testGetProgramTrainingDaysForUserProgramNotFound() {
        // Given
        Long programId = 1L;
        Long telegramUserId = 123L;
        Long userId = 1L;

        User user = new User();
        user.setId(userId);
        user.setTelegramId(telegramUserId);

        when(userRepository.findByTelegramId(telegramUserId)).thenReturn(Optional.of(user));
        when(programRepository.findByIdAndUserId(programId, userId)).thenReturn(Optional.empty());

        // When
        List<ProgramTrainingDay> result = programService.getProgramTrainingDaysForUser(programId, telegramUserId);

        // Then
        assertThat(result).isEmpty();
        verifyNoInteractions(programTrainingDayRepository);
    }

    @Test
    void testStartProgramForUserSetsActiveProgramAndFirstTrainingDay() throws Exception {
        Long programId = 1L;
        Long telegramUserId = 123L;
        Long userId = 1L;

        User user = new User();
        user.setId(userId);
        user.setTelegramId(telegramUserId);

        Program program = new Program();
        program.setId(programId);
        program.setName("Strength");
        program.setUser(user);

        TrainingDay trainingDay = new TrainingDay();
        trainingDay.setId(10L);
        trainingDay.setTitle("Upper Body");

        ProgramTrainingDay programTrainingDay = new ProgramTrainingDay();
        programTrainingDay.setPosition(1);
        programTrainingDay.setTrainingDay(trainingDay);

        when(userRepository.findByTelegramId(telegramUserId)).thenReturn(Optional.of(user));
        when(programRepository.findByIdAndUserId(programId, userId)).thenReturn(Optional.of(program));
        when(programTrainingDayRepository.findByProgramIdOrderByPositionAsc(programId)).thenReturn(List.of(programTrainingDay));

        ProgramService.ActiveProgramSelection result = programService.startProgramForUser(programId, telegramUserId);

        assertThat(result.program()).isEqualTo(program);
        assertThat(result.trainingDay()).isEqualTo(trainingDay);
        assertThat(user.getActiveProgram()).isEqualTo(program);
        assertThat(user.getActiveTrainingDay()).isEqualTo(trainingDay);
        verify(userRepository).save(user);
    }

    @Test
    void testStartProgramForUserWithoutTrainingDaysThrows() {
        Long programId = 1L;
        Long telegramUserId = 123L;
        Long userId = 1L;

        User user = new User();
        user.setId(userId);
        user.setTelegramId(telegramUserId);

        Program program = new Program();
        program.setId(programId);
        program.setUser(user);

        when(userRepository.findByTelegramId(telegramUserId)).thenReturn(Optional.of(user));
        when(programRepository.findByIdAndUserId(programId, userId)).thenReturn(Optional.of(program));
        when(programTrainingDayRepository.findByProgramIdOrderByPositionAsc(programId)).thenReturn(List.of());

        assertThatThrownBy(() -> programService.startProgramForUser(programId, telegramUserId))
                .isInstanceOf(ProgramException.class)
                .hasMessage("Cannot start a program without training days.");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void testDeleteProgramForUserClearsActiveProgram() {
        Long programId = 1L;
        Long telegramUserId = 123L;
        Long userId = 1L;

        Program program = new Program();
        program.setId(programId);

        TrainingDay trainingDay = new TrainingDay();
        trainingDay.setId(10L);

        User user = new User();
        user.setId(userId);
        user.setTelegramId(telegramUserId);
        user.setActiveProgram(program);
        user.setActiveTrainingDay(trainingDay);

        when(userRepository.findByTelegramId(telegramUserId)).thenReturn(Optional.of(user));
        when(programRepository.findByIdAndUserId(programId, userId)).thenReturn(Optional.of(program));

        boolean deleted = programService.deleteProgramForUser(programId, telegramUserId);

        assertThat(deleted).isTrue();
        assertThat(user.getActiveProgram()).isNull();
        assertThat(user.getActiveTrainingDay()).isNull();
        verify(userRepository).save(user);
        verify(programTrainingDayRepository).deleteByProgramId(programId);
        verify(programRepository).delete(program);
    }

    @Test
    void testDeleteProgramForUserNotFound() {
        Long programId = 1L;
        Long telegramUserId = 123L;
        Long userId = 1L;

        User user = new User();
        user.setId(userId);
        user.setTelegramId(telegramUserId);

        when(userRepository.findByTelegramId(telegramUserId)).thenReturn(Optional.of(user));
        when(programRepository.findByIdAndUserId(programId, userId)).thenReturn(Optional.empty());

        boolean deleted = programService.deleteProgramForUser(programId, telegramUserId);

        assertThat(deleted).isFalse();
        verifyNoInteractions(programTrainingDayRepository);
        verify(programRepository, never()).delete(any(Program.class));
    }

    @Test
    void testGetActiveTrainingDayForUser() {
        Long telegramUserId = 123L;
        TrainingDay trainingDay = new TrainingDay();
        trainingDay.setId(10L);

        User user = new User();
        user.setTelegramId(telegramUserId);
        user.setActiveTrainingDay(trainingDay);

        when(userRepository.findByTelegramId(telegramUserId)).thenReturn(Optional.of(user));

        TrainingDay result = programService.getActiveTrainingDayForUser(telegramUserId);

        assertThat(result).isEqualTo(trainingDay);
    }
}
