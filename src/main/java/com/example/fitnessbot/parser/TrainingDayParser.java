package com.example.fitnessbot.parser;

import com.example.fitnessbot.model.Exercise;
import com.example.fitnessbot.model.TrainingDay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Very simple deterministic parser for the free‑form workout description you provided.
 * It extracts:
 *  - Section titles (lines ending with ':')
 *  - Each bullet ("⁃" or "-") → name, optional sets×reps, duration, URLs, notes
 *  The parser does **not** use any AI – it relies on regex patterns that match the current format.
 *  The result is a {@link TrainingDay} with a list of {@link Exercise} objects preserving order.
 */
@Component
public class TrainingDayParser {

    private static final Logger log = LoggerFactory.getLogger(TrainingDayParser.class);

    // Pattern to capture a bullet line. Captures the whole line after the bullet marker.
    private static final Pattern BULLET_PATTERN = Pattern.compile("^[\\u2043\\-\\u2022\\u2023\\u25E6\\*\\+\\u2219\\u2014\\u2013]\\s*(.+)");
    // Pattern for sets x reps like "3 x 6", "3x8-10", "4x6+", or "2xMAX".
    private static final Pattern SET_REP_PATTERN = Pattern.compile("(\\d+)\\s*[xхX]\\s*([\\p{L}\\d]+(?:-[\\p{L}\\d]+)?\\+?)");
    private static final Pattern TRAILING_PAREN_NOTE_PATTERN = Pattern.compile("\\s+(\\([^)]*\\))\\s*$");
    private static final Pattern TRAILING_REPS_ONLY_PATTERN = Pattern.compile("^(.*\\S)\\s+(AMRAP|MAX)$", Pattern.CASE_INSENSITIVE);
    // Pattern for any URL
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+", Pattern.CASE_INSENSITIVE);

    public TrainingDay parse(String rawText) {
        TrainingDay day = new TrainingDay();
        List<Exercise> exercises = new ArrayList<>();
        day.setExercises(exercises);

        if (rawText == null || rawText.isBlank()) {
            return day;
        }

        String[] lines = rawText.split("\\r?\\n");
        String currentSection = "General"; // Default section name
        int position = 0;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // Section header – ends with ':'
            if (line.endsWith(":")) {
                String normalizedTitle = TrainingDayTitleNormalizer.normalize(line);
                if (day.getTitle() == null && exercises.isEmpty()
                        && TrainingDayTitleNormalizer.looksLikeTrainingDayTitle(normalizedTitle)) {
                    day.setTitle(normalizedTitle);
                } else {
                    currentSection = line.substring(0, line.length() - 1).trim();
                }
                continue;
            }

            Matcher bulletMatcher = BULLET_PATTERN.matcher(line);
            if (bulletMatcher.find()) {
                String content = bulletMatcher.group(1).trim();
                Exercise ex = new Exercise();
                ex.setSection(currentSection);
                ex.setPosition(position++);

                // Extract URLs first
                List<String> urls = new ArrayList<>();
                Matcher urlMatcher = URL_PATTERN.matcher(content);
                while (urlMatcher.find()) {
                    String url = urlMatcher.group();
                    // Validate URL before adding
                    if (isValidUrl(url)) {
                        urls.add(url);
                    }
                }
                ex.setVideoUrls(urls);

                // Remove URLs from the text to simplify further parsing
                String withoutUrls = content.replaceAll(URL_PATTERN.pattern(), "").trim();

                // Try to find sets×reps pattern.
                Matcher setRepMatcher = SET_REP_PATTERN.matcher(withoutUrls);
                if (setRepMatcher.find()) {
                    ex.setSets(Integer.parseInt(setRepMatcher.group(1)));
                    ex.setRepsOrDuration(setRepMatcher.group(2));

                    String match = setRepMatcher.group();
                    String notes = withoutUrls.substring(setRepMatcher.end()).trim();
                    boolean compactSetRep = !match.contains(" ");
                    if (compactSetRep || !notes.isBlank()) {
                        String name = withoutUrls.substring(0, setRepMatcher.start()).trim();
                        ex.setName(name);
                        setNotesIfPresent(ex, notes);
                    } else {
                        ex.setName(withoutUrls);
                    }
                } else {
                    Matcher repsOnlyMatcher = TRAILING_REPS_ONLY_PATTERN.matcher(withoutUrls);
                    if (repsOnlyMatcher.matches()) {
                        ex.setName(repsOnlyMatcher.group(1).trim());
                        ex.setRepsOrDuration(repsOnlyMatcher.group(2));
                    } else {
                        String name = withoutUrls;
                        Matcher noteMatcher = TRAILING_PAREN_NOTE_PATTERN.matcher(name);
                        if (noteMatcher.find()) {
                            ex.setNotes(noteMatcher.group(1).trim());
                            name = name.substring(0, noteMatcher.start()).trim();
                        }
                        ex.setName(name);
                    }
                }

                exercises.add(ex);
            }
        }
        return day;
    }

    /**
     * Validates if a URL is safe to store
     * @param url The URL to validate
     * @return true if the URL is valid, false otherwise
     */
    private boolean isValidUrl(String url) {
        // Basic validation - check if URL is not too long and has expected format
        if (url == null || url.length() > 2048) { // Limit URL length
            return false;
        }

        // Check that URL starts with http or https
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return false;
        }

        // More sophisticated validation could be added here if needed
        return true;
    }

    private void setNotesIfPresent(Exercise exercise, String notes) {
        if (notes == null || notes.isBlank()) {
            return;
        }
        exercise.setNotes(notes);
    }
}
