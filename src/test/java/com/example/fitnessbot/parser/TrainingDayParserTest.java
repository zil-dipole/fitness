package com.example.fitnessbot.parser;

import com.example.fitnessbot.model.Exercise;
import com.example.fitnessbot.model.TrainingDay;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
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

        assertThat(day).isNotNull();
        List<Exercise> exercises = day.getExercises();
        assertThat(exercises).isNotNull();
        assertThat(exercises).hasSize(4);

        // Check first exercise
        Exercise ex1 = exercises.get(0);
        assertThat(ex1.getSection()).isEqualTo("Активация разминка");
        assertThat(ex1.getName()).isEqualTo("Гандболка с выпадами х20"); // The name includes the "х20" part because the parser doesn't separate it yet
        assertThat(ex1.getVideoUrls()).hasSize(1);
        assertThat(ex1.getVideoUrls().get(0)).contains("youtube.com");

        // Check second exercise
        Exercise ex2 = exercises.get(1);
        assertThat(ex2.getSection()).isEqualTo("Активация разминка");
        assertThat(ex2.getName()).isEqualTo("Пуловер лёжа х15");

        // Check third exercise
        Exercise ex3 = exercises.get(2);
        assertThat(ex3.getSection()).isEqualTo("Основная часть");
        assertThat(ex3.getName()).isEqualTo("Жим штанги лёжа");
        assertThat(ex3.getSets()).isEqualTo(3);
        assertThat(ex3.getRepsOrDuration()).isEqualTo("6");
        assertThat(ex3.getNotes()).isEqualTo("(70 кг)");
    }

    @Test
    void testParseWithEmptyLinesAndWhitespace() {
        String rawText = """
                Треня 1:

                Разминка:
                
                - Бег 5 мин   
                
                - Растяжка 10 мин  
                
                
                Основная часть:
                
                - Приседания 3 x 10
                
                Завершение:
                
                - Планка 30 сек
                """;

        TrainingDay day = parser.parse(rawText);

        assertThat(day).isNotNull();
        List<Exercise> exercises = day.getExercises();
        assertThat(exercises).hasSize(4);

        // Check exercises
        Exercise ex1 = exercises.get(0);
        assertThat(ex1.getSection()).isEqualTo("Разминка");
        assertThat(ex1.getName()).isEqualTo("Бег 5 мин");

        Exercise ex2 = exercises.get(1);
        assertThat(ex2.getSection()).isEqualTo("Разминка");
        assertThat(ex2.getName()).isEqualTo("Растяжка 10 мин");

        Exercise ex3 = exercises.get(2);
        assertThat(ex3.getSection()).isEqualTo("Основная часть");
        assertThat(ex3.getName()).isEqualTo("Приседания 3 x 10");

        Exercise ex4 = exercises.get(3);
        assertThat(ex4.getSection()).isEqualTo("Завершение");
        assertThat(ex4.getName()).isEqualTo("Планка 30 сек");
    }

    @Test
    void testParseWithDifferentBulletStyles() {
        String rawText = """
                Треня 1:

                Разминка:
                * Бег 5 мин
                • Растяжка 10 мин
                + Прыжки 15 раз
                ∙ Махи ногами 20 раз

                Основная часть:
                — Отжимания 3 x 15
                – Подтягивания 3 x 8
                — Пресс 3 x 20
                """;

        TrainingDay day = parser.parse(rawText);

        assertThat(day).isNotNull();
        List<Exercise> exercises = day.getExercises();
        assertThat(exercises).hasSize(7);

        // Check that all exercises are parsed regardless of bullet style
        assertThat(exercises.get(0).getName()).isEqualTo("Бег 5 мин");
        assertThat(exercises.get(1).getName()).isEqualTo("Растяжка 10 мин");
        assertThat(exercises.get(2).getName()).isEqualTo("Прыжки 15 раз");
        assertThat(exercises.get(3).getName()).isEqualTo("Махи ногами 20 раз");
        assertThat(exercises.get(4).getName()).isEqualTo("Отжимания 3 x 15");
        assertThat(exercises.get(5).getName()).isEqualTo("Подтягивания 3 x 8");
        assertThat(exercises.get(6).getName()).isEqualTo("Пресс 3 x 20");
    }

    @Test
    void testParseWithSpecialCharactersInNames() {
        String rawText = """
                Треня 1:

                Разминка:
                - Бег (на улице) 5 мин
                - Растяжка «спина» 10 мин
                - Прыжки „в стороны“ 15 раз

                Основная часть:
                - Приседания – классические 3 x 10
                - Отжимания — широким хватом 3 x 15
                """;

        TrainingDay day = parser.parse(rawText);

        assertThat(day).isNotNull();
        List<Exercise> exercises = day.getExercises();
        assertThat(exercises).hasSize(5);

        // Check exercises with special characters
        assertThat(exercises.get(0).getName()).isEqualTo("Бег (на улице) 5 мин");
        assertThat(exercises.get(1).getName()).isEqualTo("Растяжка «спина» 10 мин");
        assertThat(exercises.get(2).getName()).isEqualTo("Прыжки „в стороны“ 15 раз");
        assertThat(exercises.get(3).getName()).isEqualTo("Приседания – классические 3 x 10");
        assertThat(exercises.get(4).getName()).isEqualTo("Отжимания — широким хватом 3 x 15");
    }

    @Test
    void testParseWithMultipleUrls() {
        String rawText = """
                Треня 1:

                Разминка:
                - Бег 5 мин (с видео) https://www.youtube.com/watch?v=run1 https://www.youtube.com/watch?v=run2
                - Растяжка 10 мин (демонстрация) https://www.vimeo.com/stretch

                Основная часть:
                - Приседания 3 x 10 https://example.com/squats
                """;

        TrainingDay day = parser.parse(rawText);

        assertThat(day).isNotNull();
        List<Exercise> exercises = day.getExercises();
        assertThat(exercises).hasSize(3);

        // Check exercises with multiple URLs
        Exercise ex1 = exercises.get(0);
        assertThat(ex1.getName()).isEqualTo("Бег 5 мин");
        assertThat(ex1.getVideoUrls()).hasSize(2);
        assertThat(ex1.getVideoUrls()).contains("https://www.youtube.com/watch?v=run1");
        assertThat(ex1.getVideoUrls()).contains("https://www.youtube.com/watch?v=run2");

        Exercise ex2 = exercises.get(1);
        assertThat(ex2.getName()).isEqualTo("Растяжка 10 мин");
        assertThat(ex2.getVideoUrls()).hasSize(1);
        assertThat(ex2.getVideoUrls()).contains("https://www.vimeo.com/stretch");

        Exercise ex3 = exercises.get(2);
        assertThat(ex3.getName()).isEqualTo("Приседания 3 x 10");
        assertThat(ex3.getVideoUrls()).hasSize(1);
        assertThat(ex3.getVideoUrls()).contains("https://example.com/squats");
    }

    @Test
    void testParseWithComplexSetRepFormats() {
        String rawText = """
                Треня 1:

                Разминка:
                - Бег 5 мин

                Основная часть:
                - Приседания 5x5 100 кг
                - Жим штанги 3x8-10 70 кг
                - Тяга штанги 4x6+ 80 кг
                - Подтягивания AMRAP
                - Отжимания 2xMAX

                Кардио:
                - Велотренажер 20 мин
                """;

        TrainingDay day = parser.parse(rawText);

        assertThat(day).isNotNull();
        List<Exercise> exercises = day.getExercises();
        assertThat(exercises).hasSize(7);

        // Check exercises with different set/rep formats
        Exercise ex1 = exercises.get(1);
        assertThat(ex1.getName()).isEqualTo("Приседания");
        assertThat(ex1.getSets()).isEqualTo(5);
        assertThat(ex1.getRepsOrDuration()).isEqualTo("5");
        assertThat(ex1.getNotes()).isEqualTo("100 кг");

        Exercise ex2 = exercises.get(2);
        assertThat(ex2.getName()).isEqualTo("Жим штанги");
        assertThat(ex2.getSets()).isEqualTo(3);
        assertThat(ex2.getRepsOrDuration()).isEqualTo("8-10");
        assertThat(ex2.getNotes()).isEqualTo("70 кг");

        Exercise ex3 = exercises.get(3);
        assertThat(ex3.getName()).isEqualTo("Тяга штанги");
        assertThat(ex3.getSets()).isEqualTo(4);
        assertThat(ex3.getRepsOrDuration()).isEqualTo("6+");
        assertThat(ex3.getNotes()).isEqualTo("80 кг");

        Exercise ex4 = exercises.get(4);
        assertThat(ex4.getName()).isEqualTo("Подтягивания");
        assertThat(ex4.getSets()).isNull();
        assertThat(ex4.getRepsOrDuration()).isEqualTo("AMRAP");

        Exercise ex5 = exercises.get(5);
        assertThat(ex5.getName()).isEqualTo("Отжимания");
        assertThat(ex5.getSets()).isEqualTo(2);
        assertThat(ex5.getRepsOrDuration()).isEqualTo("MAX");
    }

    @Test
    void testParseWithCyrillicCharacters() {
        String rawText = """
                Тренировочный день 1:

                Активация и разминка:
                - Махи руками 20 раз
                - Наклоны корпуса 15 раз

                Основная часть:
                - Приседания со штангой на спине 4x8 60 кг
                - Жим штанги лёжа 3x6 70 кг
                - Подтягивания широким хватом 3x10  собств. вес

                Заминка:
                - Растяжка мышц спины 5 мин
                """;

        TrainingDay day = parser.parse(rawText);

        assertThat(day).isNotNull();
        List<Exercise> exercises = day.getExercises();
        assertThat(exercises).hasSize(6);

        // Check exercises with Cyrillic characters
        assertThat(day.getTitle()).isEqualTo("Тренировочный день 1:");
        assertThat(exercises.get(0).getSection()).isEqualTo("Активация и разминка");
        assertThat(exercises.get(0).getName()).isEqualTo("Махи руками 20 раз");
        assertThat(exercises.get(2).getName()).isEqualTo("Приседания со штангой на спине");
        assertThat(exercises.get(5).getSection()).isEqualTo("Заминка");
        assertThat(exercises.get(5).getName()).isEqualTo("Растяжка мышц спины 5 мин");
    }

    @Test
    void testParseEmptyInput() {
        String rawText = "";

        TrainingDay day = parser.parse(rawText);

        assertThat(day).isNotNull();
        assertThat(day.getExercises()).isEmpty();
    }

    @Test
    void testParseNullInput() {
        TrainingDay day = parser.parse(null);

        assertThat(day).isNotNull();
        assertThat(day.getExercises()).isEmpty();
    }

    @Test
    void testParseWithOnlyTitle() {
        String rawText = "Треня 1:";

        TrainingDay day = parser.parse(rawText);

        assertThat(day).isNotNull();
        assertThat(day.getTitle()).isEqualTo("Треня 1:");
        assertThat(day.getExercises()).isEmpty();
    }

    @Test
    void testParseNormalizesMistypedRussianTitle() {
        String rawText = """
                Nhtyz 2:

                Разминка:
                - Бег 5 мин
                """;

        TrainingDay day = parser.parse(rawText);

        assertThat(day.getTitle()).isEqualTo("Треня 2:");
        assertThat(day.getExercises()).hasSize(1);
        assertThat(day.getExercises().getFirst().getSection()).isEqualTo("Разминка");
    }

    @Test
    void testParseWithEmptySections() {
        String rawText = """
                Треня 1:

                Разминка:

                Основная часть:
                - Приседания 3 x 10

                Завершение:
                """;

        TrainingDay day = parser.parse(rawText);

        assertThat(day).isNotNull();
        List<Exercise> exercises = day.getExercises();
        assertThat(exercises).hasSize(1);
        assertThat(exercises.get(0).getName()).isEqualTo("Приседания 3 x 10");
        assertThat(exercises.get(0).getSection()).isEqualTo("Основная часть");
    }

    @Test
    void testParseWithMalformedText() {
        String rawText = """
                - Приседания 3 x 10
                - Жим штанги 3 x 8

                Разминка:
                - Бег 5 мин
                """;

        TrainingDay day = parser.parse(rawText);

        assertThat(day).isNotNull();
        List<Exercise> exercises = day.getExercises();
        assertThat(exercises).hasSize(3);

        // Exercises before the first section should be assigned to a default section
        assertThat(exercises.get(0).getSection()).isNotEmpty();
        assertThat(exercises.get(0).getName()).isEqualTo("Приседания 3 x 10");
        assertThat(exercises.get(1).getSection()).isNotEmpty();
        assertThat(exercises.get(1).getName()).isEqualTo("Жим штанги 3 x 8");
        
        // Exercises after the section should be properly assigned
        assertThat(exercises.get(2).getSection()).isEqualTo("Разминка");
        assertThat(exercises.get(2).getName()).isEqualTo("Бег 5 мин");
    }

    @Test
    void testParseWithVeryLongUrls() {
        String longUrl = "https://www.youtube.com/watch?v=" + "a".repeat(2000);
        String rawText = """
                Треня 1:

                Разминка:
                - Бег 5 мин """ + longUrl + """
                
                Основная часть:
                - Приседания 3 x 10
                """;

        TrainingDay day = parser.parse(rawText);

        assertThat(day).isNotNull();
        List<Exercise> exercises = day.getExercises();
        assertThat(exercises).hasSize(2);

        Exercise ex1 = exercises.get(0);
        assertThat(ex1.getName()).isEqualTo("Бег 5 мин");
        assertThat(ex1.getVideoUrls()).hasSize(1);
        assertThat(ex1.getVideoUrls().get(0)).isEqualTo(longUrl);
    }
}
