package com.example.fitnessbot.parser;

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
            upperExercise.createCell(0).setCellValue("Bench press");
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
        assertThat(result.get(0).rawText()).contains("- Bench press 3 x 8");
        assertThat(result.get(0).rawText()).contains("- Pull-ups 3 x 6");
        assertThat(result.get(0).aiRawText()).contains("Sheet: Upper Body");
        assertThat(result.get(0).aiRawText()).contains("Exercise | Sets | Reps");
        assertThat(result.get(0).aiRawText()).contains("Bench press | 3 | 8");
        assertThat(result.get(1).rawText()).contains("Lower Body:");
        assertThat(result.get(1).rawText()).contains("Warm-up:");
        assertThat(result.get(1).rawText()).contains("- Hip thrust 2 x 12");
        assertThat(result.get(2).rawText()).contains("Simple Day:");
        assertThat(result.get(2).rawText()).contains("- Deadlift 3 x 5");
        assertThat(result.get(2).rawText()).doesNotContain("- Exercise");
    }

    private byte[] write(XSSFWorkbook workbook) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        return outputStream.toByteArray();
    }
}
