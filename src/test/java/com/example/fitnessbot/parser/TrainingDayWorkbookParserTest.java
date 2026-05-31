package com.example.fitnessbot.parser;

import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TrainingDayWorkbookParserTest {

    private final TrainingDayWorkbookParser parser = new TrainingDayWorkbookParser();

    @Test
    void parsesVisibleSheetsAsSeparateTrainingDays() throws Exception {
        byte[] workbookBytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet upper = workbook.createSheet("Upper Body");
            upper.createRow(0).createCell(0).setCellValue("Exercise");
            upper.getRow(0).createCell(1).setCellValue("Sets");
            upper.getRow(0).createCell(2).setCellValue("Reps");
            Row upperExercise = upper.createRow(1);
            var benchPressCell = upperExercise.createCell(0);
            benchPressCell.setCellValue("Bench press");
            var benchPressLink = workbook.getCreationHelper().createHyperlink(HyperlinkType.URL);
            benchPressLink.setAddress("https://example.com/bench");
            benchPressCell.setHyperlink(benchPressLink);
            upperExercise.createCell(1).setCellValue(3);
            upperExercise.createCell(2).setCellValue(8);
            Row upperListedExercise = upper.createRow(2);
            upperListedExercise.createCell(0).setCellValue("- Pull-ups 3 x 6");

            Sheet lower = workbook.createSheet("Lower Body");
            lower.createRow(0).createCell(0).setCellValue("Warm-up:");
            lower.createRow(1).createCell(0).setCellValue("Hip thrust 2 x 12");

            Sheet simple = workbook.createSheet("Simple Day");
            simple.createRow(0).createCell(0).setCellValue("Exercise");
            simple.createRow(1).createCell(0).setCellValue("Deadlift 3 x 5");

            Sheet hidden = workbook.createSheet("Hidden Notes");
            hidden.createRow(0).createCell(0).setCellValue("Should not be imported");
            workbook.setSheetHidden(workbook.getSheetIndex(hidden), true);

            workbookBytes = write(workbook);
        }

        List<TrainingDayWorkbookParser.WorkbookTrainingDay> result =
                parser.parse(new ByteArrayInputStream(workbookBytes));

        assertThat(result).hasSize(3);
        assertThat(result.get(0).sheetName()).isEqualTo("Upper Body");
        assertThat(result.get(0).rawText()).contains("Upper Body:");
        assertThat(result.get(0).rawText()).contains("- Bench press https://example.com/bench 3 x 8");
        assertThat(result.get(0).rawText()).contains("- Pull-ups 3 x 6");
        assertThat(result.get(0).aiRawText()).contains("Sheet: Upper Body");
        assertThat(result.get(0).aiRawText()).contains("Exercise | Sets | Reps");
        assertThat(result.get(0).aiRawText()).contains("Bench press https://example.com/bench | 3 | 8");
        assertThat(new TrainingDayParser().parse(result.get(0).rawText())
                .getExercises()
                .getFirst()
                .getVideoUrls())
                .containsExactly("https://example.com/bench");
        assertThat(result.get(1).rawText()).contains("Lower Body:");
        assertThat(result.get(1).rawText()).contains("Warm-up:");
        assertThat(result.get(1).rawText()).contains("- Hip thrust 2 x 12");
        assertThat(result.get(2).rawText()).contains("Simple Day:");
        assertThat(result.get(2).rawText()).contains("- Deadlift 3 x 5");
        assertThat(result.get(2).rawText()).doesNotContain("- Exercise");
    }

    @Test
    void parsesIndexedRussianTemplateRowsAndGroupHeaders() throws Exception {
        byte[] workbookBytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Треня 1");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("№");
            header.createCell(1).setCellValue("Упражнения");
            header.createCell(2).setCellValue("Объем");
            header.createCell(3).setCellValue("Интенсивность (Вес, %, ИВН, ПВЗ)");
            header.createCell(4).setCellValue("Темп");
            header.createCell(5).setCellValue("Отдых");
            header.createCell(6).setCellValue("Комментарий");

            sheet.createRow(1).createCell(0).setCellValue("Dynamic warmup (выполняем 2-3 круга)");

            Row warmupExercise = sheet.createRow(2);
            warmupExercise.createCell(0).setCellValue(1);
            warmupExercise.createCell(1).setCellValue("Frankenstein walk");
            warmupExercise.createCell(2).setCellValue("x 6+6");

            sheet.createRow(3).createCell(0).setCellValue("Activation (2 круга)");

            Row activationExercise = sheet.createRow(4);
            activationExercise.createCell(0).setCellValue("1b");
            activationExercise.createCell(1).setCellValue("ER shoulders hold");
            activationExercise.createCell(2).setCellValue("x 20 sec");
            activationExercise.createCell(3).setCellValue("ИВН 7");
            activationExercise.createCell(5).setCellValue("0-30 sec");
            activationExercise.createCell(6).setCellValue("Локоть прижат к корпусу");

            sheet.createRow(5).createCell(0).setCellValue("Power upper");

            Row powerExercise = sheet.createRow(6);
            powerExercise.createCell(0).setCellValue(1);
            powerExercise.createCell(1).setCellValue("Chest med ball throws");
            powerExercise.createCell(2).setCellValue("4 x 5");
            powerExercise.createCell(3).setCellValue("МПУ 100%");
            powerExercise.createCell(5).setCellValue("2 мин");
            powerExercise.createCell(6).setCellValue("Strength unilateral lower");
            powerExercise.createCell(7).setCellValue("8;8");

            Row strengthExercise = sheet.createRow(7);
            strengthExercise.createCell(0).setCellValue(1);
            strengthExercise.createCell(1).setCellValue("Bulgarian split squat");
            strengthExercise.createCell(2).setCellValue("3 x 6");
            strengthExercise.createCell(3).setCellValue("ПВР 3");

            workbookBytes = write(workbook);
        }

        List<TrainingDayWorkbookParser.WorkbookTrainingDay> result =
                parser.parse(new ByteArrayInputStream(workbookBytes));

        assertThat(result).hasSize(1);
        String rawText = result.getFirst().rawText();
        assertThat(rawText).contains("Треня 1:");
        assertThat(rawText).contains("Dynamic warmup (выполняем 2-3 круга):");
        assertThat(rawText).contains("- Frankenstein walk x 6+6");
        assertThat(rawText).contains("Activation (2 круга):");
        assertThat(rawText).contains("- ER shoulders hold x 20 sec ИВН 7 0-30 sec Локоть прижат к корпусу");
        assertThat(rawText).contains("Power upper:");
        assertThat(rawText).contains("- Chest med ball throws 4 x 5 МПУ 100% 2 мин 8;8");
        assertThat(rawText).contains("Strength unilateral lower:");
        assertThat(rawText).contains("- Bulgarian split squat 3 x 6 ПВР 3");
        assertThat(rawText).doesNotContain("Упражнения");
        assertThat(rawText).doesNotContain("- 1 Frankenstein walk");
        assertThat(rawText).doesNotContain("Strength unilateral lower 8;8");
        assertThat(result.getFirst().aiRawText()).contains("Dynamic warmup (выполняем 2-3 круга):");
        assertThat(result.getFirst().aiRawText()).contains("Strength unilateral lower:");
        assertThat(result.getFirst().aiRawText()).contains("1 | Chest med ball throws | 4 x 5 | МПУ 100% | 2 мин | 8;8");

        var parsedTrainingDay = new TrainingDayParser().parse(rawText);
        var dynamicWarmupExercise = parsedTrainingDay.getExercises().stream()
                .filter(exercise -> exercise.getName().contains("Frankenstein walk"))
                .findFirst()
                .orElseThrow();
        assertThat(dynamicWarmupExercise.getSection()).isEqualTo("Dynamic warmup (выполняем 2-3 круга)");
        assertThat(dynamicWarmupExercise.getName()).isEqualTo("Frankenstein walk");
        assertThat(dynamicWarmupExercise.getRepsOrDuration()).isEqualTo("6+6");
        assertThat(dynamicWarmupExercise.getNotes()).contains("Dynamic warmup (выполняем 2-3 круга)");

        var activationExercise = parsedTrainingDay.getExercises().stream()
                .filter(exercise -> exercise.getName().contains("ER shoulders hold"))
                .findFirst()
                .orElseThrow();
        assertThat(activationExercise.getName()).isEqualTo("ER shoulders hold");
        assertThat(activationExercise.getRepsOrDuration()).isEqualTo("20 sec");
        assertThat(activationExercise.getNotes())
                .contains("Activation (2 круга)")
                .contains("ИВН 7 0-30 sec Локоть прижат к корпусу");
    }

    private byte[] write(XSSFWorkbook workbook) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        return outputStream.toByteArray();
    }
}
