package com.example.fitnessbot.parser;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FormulaEvaluator;
import org.apache.poi.ss.usermodel.Hyperlink;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class TrainingDayWorkbookParser {

    private static final Logger log = LoggerFactory.getLogger(TrainingDayWorkbookParser.class);

    private static final Pattern LIST_ITEM_PATTERN = Pattern.compile("^(?:[\\u2043\\-\\u2022\\u2023\\u25E6\\*\\+\\u2219\\u2014\\u2013]|\\d+[.)])\\s*.+");
    private static final Pattern HEADER_ROW_PATTERN = Pattern.compile(
            "(?iu)^(#|no\\.?|№|exercise|movement|name|sets?|reps?|volume|duration|intensity.*|tempo|rest|notes?|comment|video|url|section|block|упражнени[ея]|подходы|повторы|об[ъь]ем|интенсивность.*|темп|отдых|заметки|комментарий|секция|блок)$"
    );
    private static final Pattern EXERCISE_INDEX_PATTERN = Pattern.compile("^\\d+(?:[.,]0+)?[a-zA-Zа-яА-Я]?$");
    private static final Pattern EXERCISE_VOLUME_PATTERN = Pattern.compile(
            "(?i).*(?:\\b\\d+\\s*[xх]\\s*\\S+|\\b[xх]\\s*\\S+|\\b\\d+\\s*(?:min|мин|sec|сек|kg|кг)\\b).*"
    );
    private static final Pattern GROUP_HEADER_PATTERN = Pattern.compile(
            "(?iu).*(warm\\s*-?\\s*up|activation|power|strength|prehab|posterior|dynamic|circuit|rounds?|superset|triset|mobility|control|balance|hypertro(?:phy|fy)|upper|lower|vertical|unilateral|разминка|активац|круг|сил|плеч|кор|голеностоп|баланс|контроль|мобил|трисет|суперсет|прехаб).*"
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

            log.info("Parsing Excel workbook with {} sheet(s)", workbook.getNumberOfSheets());
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                String sheetName = workbook.getSheetName(sheetIndex);
                if (workbook.isSheetHidden(sheetIndex) || workbook.isSheetVeryHidden(sheetIndex)) {
                    log.info("Skipping hidden Excel sheet '{}'", sheetName);
                    continue;
                }

                Sheet sheet = workbook.getSheetAt(sheetIndex);
                log.info("Parsing Excel sheet '{}' with {} physical row(s)", sheet.getSheetName(), sheet.getPhysicalNumberOfRows());
                String rawText = sheetToTrainingDayText(sheet, formatter, evaluator);
                if (!rawText.isBlank()) {
                    String aiRawText = sheetToOpenAiTrainingDayText(sheet, formatter, evaluator);
                    log.info(
                            "Parsed Excel sheet '{}' into training day text ({} chars, OpenAI text {} chars)",
                            sheet.getSheetName(),
                            rawText.length(),
                            aiRawText.length()
                    );
                    trainingDays.add(new WorkbookTrainingDay(sheet.getSheetName(), rawText, aiRawText));
                } else {
                    log.info("Skipping Excel sheet '{}' because it has no workout rows", sheet.getSheetName());
                }
            }

            if (trainingDays.isEmpty()) {
                throw new IllegalArgumentException("Excel file does not contain any visible sheets with workout text");
            }

            log.info("Parsed Excel workbook into {} training day(s)", trainingDays.size());
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
        List<List<String>> rows = sheetRows(sheet, formatter, evaluator);

        boolean sawBodyRow = false;
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<String> values = rows.get(rowIndex);
            if (values.isEmpty() || isHeaderRow(values)) {
                continue;
            }

            List<String> rowLines = rowToLines(values, nextContentRow(rows, rowIndex + 1));
            for (String line : rowLines) {
                if (!line.isBlank()) {
                    lines.add(line);
                    sawBodyRow = true;
                }
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
        List<List<String>> rows = sheetRows(sheet, formatter, evaluator);

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<String> values = rows.get(rowIndex);
            if (!values.isEmpty()) {
                lines.addAll(valuesToSpreadsheetLines(values, nextContentRow(rows, rowIndex + 1)));
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

    private List<List<String>> sheetRows(Sheet sheet, DataFormatter formatter, FormulaEvaluator evaluator) {
        List<List<String>> rows = new ArrayList<>();
        for (Row row : sheet) {
            rows.add(rowValues(row, formatter, evaluator));
        }
        return rows;
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

            String value = cellTextWithHyperlink(cell, formatter, evaluator);
            if (!value.isBlank()) {
                values.add(truncateCellText(value));
            }
        }
        return values;
    }

    private String cellTextWithHyperlink(Cell cell, DataFormatter formatter, FormulaEvaluator evaluator) {
        String value = formatter.formatCellValue(cell, evaluator).trim().replaceAll("\\s+", " ");
        Hyperlink hyperlink = cell.getHyperlink();
        if (hyperlink == null || hyperlink.getAddress() == null || hyperlink.getAddress().isBlank()) {
            return value;
        }

        String hyperlinkAddress = hyperlink.getAddress().trim();
        if (value.contains(hyperlinkAddress)) {
            return value;
        }
        if (value.isBlank()) {
            return hyperlinkAddress;
        }
        return value + " " + hyperlinkAddress;
    }

    private List<String> rowToLines(List<String> values, List<String> nextValues) {
        if (values.size() == 1) {
            String value = values.getFirst();
            if (isSectionLine(value) || isListItem(value) || looksLikeUrl(value)) {
                return List.of(value);
            }
            if (isGroupHeader(value)) {
                return List.of(toSectionLine(value));
            }
            return List.of("- " + value);
        }

        List<String> exerciseValues = stripExerciseIndex(values);
        InlineGroupHeader inlineGroupHeader = extractInlineGroupHeader(exerciseValues, nextValues);
        exerciseValues = inlineGroupHeader.exerciseValues();
        String exerciseText = valuesToExerciseText(exerciseValues);
        List<String> lines = new ArrayList<>();
        if (isListItem(exerciseText)) {
            lines.add(exerciseText);
        } else {
            lines.add("- " + exerciseText);
        }

        if (inlineGroupHeader.header() != null) {
            lines.add(toSectionLine(inlineGroupHeader.header()));
        }
        return lines;
    }

    private String valuesToExerciseText(List<String> values) {
        if (values.isEmpty()) {
            return "";
        }

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

    private List<String> stripExerciseIndex(List<String> values) {
        if (values.size() > 1 && isExerciseIndex(values.getFirst())) {
            return values.subList(1, values.size());
        }
        return values;
    }

    private List<String> nextContentRow(List<List<String>> rows, int startIndex) {
        for (int i = startIndex; i < rows.size(); i++) {
            List<String> values = rows.get(i);
            if (!values.isEmpty() && !isHeaderRow(values)) {
                return values;
            }
        }
        return List.of();
    }

    private InlineGroupHeader extractInlineGroupHeader(List<String> exerciseValues, List<String> nextValues) {
        int headerIndex = inlineGroupHeaderIndex(exerciseValues, nextValues);
        if (headerIndex < 0) {
            return new InlineGroupHeader(exerciseValues, null);
        }

        List<String> valuesWithoutHeader = new ArrayList<>(exerciseValues);
        String header = valuesWithoutHeader.remove(headerIndex);
        return new InlineGroupHeader(valuesWithoutHeader, header);
    }

    private int inlineGroupHeaderIndex(List<String> exerciseValues, List<String> nextValues) {
        if (!startsNewIndexedGroup(nextValues)) {
            return -1;
        }

        for (int i = 2; i < exerciseValues.size(); i++) {
            String value = exerciseValues.get(i);
            if (isGroupHeader(value) && looksLikeStandaloneHeaderCell(value)) {
                return i;
            }
        }
        return -1;
    }

    private boolean startsNewIndexedGroup(List<String> values) {
        if (values.size() < 2 || !isExerciseIndex(values.getFirst())) {
            return false;
        }

        String normalized = values.getFirst().replace(',', '.').toLowerCase(Locale.ROOT);
        return normalized.equals("1")
                || normalized.equals("1.0")
                || normalized.matches("1[a-zа-я]");
    }

    private boolean looksLikeStandaloneHeaderCell(String value) {
        return value.length() <= 80
                && !value.contains(".")
                && !value.contains(",")
                && !value.contains(";");
    }

    private List<String> valuesToSpreadsheetLines(List<String> values, List<String> nextValues) {
        if (values.size() == 1 && isGroupHeader(values.getFirst())) {
            return List.of("Section: " + values.getFirst());
        }

        int indexOffset = values.size() > 1 && isExerciseIndex(values.getFirst()) ? 1 : 0;
        List<String> exerciseValues = values.subList(indexOffset, values.size());
        int headerIndex = inlineGroupHeaderIndex(exerciseValues, nextValues);
        if (headerIndex >= 0) {
            List<String> valuesWithoutHeader = new ArrayList<>(values);
            String header = valuesWithoutHeader.remove(headerIndex + indexOffset);
            return List.of(valuesToSpreadsheetLine(valuesWithoutHeader), "Section: " + header);
        }
        return List.of(valuesToSpreadsheetLine(values));
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

    private boolean isGroupHeader(String value) {
        return GROUP_HEADER_PATTERN.matcher(value).matches() && !looksLikeExerciseVolume(value);
    }

    private boolean looksLikeExerciseVolume(String value) {
        return EXERCISE_VOLUME_PATTERN.matcher(value).matches();
    }

    private String toSectionLine(String value) {
        return value.endsWith(":") ? value : value + ":";
    }

    private boolean isExerciseIndex(String value) {
        return EXERCISE_INDEX_PATTERN.matcher(value).matches();
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

    private record InlineGroupHeader(List<String> exerciseValues, String header) {
    }
}
