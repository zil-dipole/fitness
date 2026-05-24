package com.example.fitnessbot.parser;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class TrainingDayWorkbookParser {

    private static final Pattern LIST_ITEM_PATTERN = Pattern.compile("^(?:[\\u2043\\-\\u2022\\u2023\\u25E6\\*\\+\\u2219\\u2014\\u2013]|\\d+[.)])\\s*.+");
    private static final Pattern HEADER_ROW_PATTERN = Pattern.compile(
            "(?i)^(exercise|movement|name|sets?|reps?|duration|notes?|video|url|section|block|упражнение|подходы|повторы|заметки|секция|блок)$"
    );
    private static final int MAX_CELL_TEXT_LENGTH = 500;
    private static final int MAX_SHEET_TEXT_LENGTH = 10_000;

    public List<WorkbookTrainingDay> parse(InputStream inputStream) {
        if (inputStream == null) {
            throw new IllegalArgumentException("Excel file cannot be empty");
        }

        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            FormulaEvaluator evaluator = workbook.getCreationHelper().createFormulaEvaluator();
            List<WorkbookTrainingDay> trainingDays = new ArrayList<>();

            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                if (workbook.isSheetHidden(sheetIndex) || workbook.isSheetVeryHidden(sheetIndex)) {
                    continue;
                }

                Sheet sheet = workbook.getSheetAt(sheetIndex);
                String rawText = sheetToTrainingDayText(sheet, formatter, evaluator);
                if (!rawText.isBlank()) {
                    String aiRawText = sheetToOpenAiTrainingDayText(sheet, formatter, evaluator);
                    trainingDays.add(new WorkbookTrainingDay(sheet.getSheetName(), rawText, aiRawText));
                }
            }

            if (trainingDays.isEmpty()) {
                throw new IllegalArgumentException("Excel file does not contain any visible sheets with workout text");
            }

            return trainingDays;
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read Excel file", e);
        } catch (RuntimeException e) {
            if (e instanceof IllegalArgumentException) {
                throw e;
            }
            throw new IllegalArgumentException("Could not parse Excel file", e);
        }
    }

    private String sheetToTrainingDayText(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        List<String> lines = new ArrayList<>();
        lines.add(sheet.getSheetName() + ":");

        boolean sawBodyRow = false;
        for (Row row : sheet) {
            List<String> values = rowValues(row, formatter, evaluator);
            if (values.isEmpty() || isHeaderRow(values)) {
                continue;
            }

            String line = rowToLine(values, sawBodyRow);
            if (!line.isBlank()) {
                lines.add(line);
                sawBodyRow = true;
            }
        }

        if (!sawBodyRow) {
            return "";
        }

        String rawText = String.join("\n", lines);
        if (rawText.length() > MAX_SHEET_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "Sheet \"" + sheet.getSheetName() + "\" is too large (max 10KB after parsing)"
            );
        }
        return rawText;
    }

    private String sheetToOpenAiTrainingDayText(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        List<String> lines = new ArrayList<>();
        lines.add("Sheet: " + sheet.getSheetName());
        lines.add("Spreadsheet rows; cells are separated by \" | \":");

        for (Row row : sheet) {
            List<String> values = rowValues(row, formatter, evaluator);
            if (!values.isEmpty()) {
                lines.add(valuesToSpreadsheetLine(values));
            }
        }

        String rawText = String.join("\n", lines);
        if (rawText.length() > MAX_SHEET_TEXT_LENGTH) {
            throw new IllegalArgumentException(
                    "Sheet \"" + sheet.getSheetName() + "\" is too large (max 10KB after parsing)"
            );
        }
        return rawText;
    }

    private List<String> rowValues(Row row, DataFormatter formatter, FormulaEvaluator evaluator) {
        List<String> values = new ArrayList<>();
        short firstCell = row.getFirstCellNum();
        short lastCell = row.getLastCellNum();
        if (firstCell < 0 || lastCell < 0) {
            return values;
        }

        for (int cellIndex = firstCell; cellIndex < lastCell; cellIndex++) {
            Cell cell = row.getCell(cellIndex, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
            if (cell == null) {
                continue;
            }

            String value = formatter.formatCellValue(cell, evaluator).trim().replaceAll("\\s+", " ");
            if (!value.isBlank()) {
                values.add(truncateCellText(value));
            }
        }
        return values;
    }

    private String rowToLine(List<String> values, boolean sawBodyRow) {
        if (values.size() == 1) {
            String value = values.getFirst();
            if (isSectionLine(value) || isListItem(value) || looksLikeUrl(value)) {
                return value;
            }
            return "- " + value;
        }

        String exerciseText = valuesToExerciseText(values);
        if (isListItem(exerciseText)) {
            return exerciseText;
        }
        return "- " + exerciseText;
    }

    private String valuesToExerciseText(List<String> values) {
        if (values.size() >= 3 && isInteger(values.get(1)) && !values.get(2).isBlank()) {
            StringBuilder builder = new StringBuilder();
            builder.append(values.getFirst())
                    .append(' ')
                    .append(values.get(1))
                    .append(" x ")
                    .append(values.get(2));
            appendRemaining(builder, values, 3);
            return builder.toString();
        }

        StringBuilder builder = new StringBuilder(values.getFirst());
        appendRemaining(builder, values, 1);
        return builder.toString();
    }

    private void appendRemaining(StringBuilder builder, List<String> values, int startIndex) {
        for (int i = startIndex; i < values.size(); i++) {
            builder.append(' ').append(values.get(i));
        }
    }

    private String valuesToSpreadsheetLine(List<String> values) {
        return String.join(" | ", values.stream()
                .map(this::sanitizeSpreadsheetCell)
                .toList());
    }

    private String sanitizeSpreadsheetCell(String value) {
        return value.replace("|", "/");
    }

    private boolean isHeaderRow(List<String> values) {
        if (values.size() == 1) {
            return HEADER_ROW_PATTERN.matcher(values.getFirst()).matches();
        }

        long headerCells = values.stream()
                .filter(value -> HEADER_ROW_PATTERN.matcher(value).matches())
                .count();
        return headerCells >= 2;
    }

    private boolean isSectionLine(String value) {
        return value.endsWith(":");
    }

    private boolean isListItem(String value) {
        return LIST_ITEM_PATTERN.matcher(value).matches();
    }

    private boolean looksLikeUrl(String value) {
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private boolean isInteger(String value) {
        try {
            Integer.parseInt(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String truncateCellText(String value) {
        if (value.length() <= MAX_CELL_TEXT_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_CELL_TEXT_LENGTH).trim();
    }

    public record WorkbookTrainingDay(String sheetName, String rawText, String aiRawText) {
    }
}
