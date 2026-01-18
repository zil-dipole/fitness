package com.example.fitnessbot.parser;

import com.example.fitnessbot.model.Exercise;
import com.example.fitnessbot.model.TrainingDay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TrainingDayParserTest {

    private TrainingDayParser parser;

    @BeforeEach
    void setUp() {
        parser = new TrainingDayParser();
    }

    @Test
    void testParseSimpleWorkout() {
        String rawText = """
                Треня 3:

                Активация разминка:
                - Гандболка с выпадами х20 (с видео) https://www.youtube.com/watch?v=example1
                - Пуловер лёжа х15 (с видео) https://www.youtube.com/watch?v=example2

                Основная часть:
                - Жим штанги лёжа 3 x 6 (70 кг)
                - Жим гантелей сидя 3 x 8 (25 кг)
                """;

        TrainingDay day = parser.parse(rawText);

        assertNotNull(day);
        List<Exercise> exercises = day.getExercises();
        assertNotNull(exercises);
        assertEquals(4, exercises.size());

        // Check first exercise
        Exercise ex1 = exercises.get(0);
        assertEquals("Активация разминка", ex1.getSection());
        assertEquals("Гандболка с выпадами х20", ex1.getName()); // The name includes the "х20" part because the parser doesn't separate it yet
        assertEquals(1, ex1.getVideoUrls().size());
        assertTrue(ex1.getVideoUrls().get(0).contains("youtube.com"));

        // Check second exercise
        Exercise ex2 = exercises.get(1);
        assertEquals("Активация разминка", ex2.getSection());
        assertEquals("Пуловер лёжа х15", ex2.getName());

        // Check third exercise
        Exercise ex3 = exercises.get(2);
        assertEquals("Основная часть", ex3.getSection());
        assertEquals("Жим штанги лёжа", ex3.getName());
        assertEquals(Integer.valueOf(3), ex3.getSets());
        assertEquals("6", ex3.getRepsOrDuration());
        assertEquals("(70 кг)", ex3.getNotes());
    }
}