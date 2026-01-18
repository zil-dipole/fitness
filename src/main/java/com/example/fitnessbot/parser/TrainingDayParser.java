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
    private static final Pattern BULLET_PATTERN = Pattern.compile("[\\u2043\\-]\\s*(.+)");
    // Pattern for sets × reps like "3 x 6" or "2 x 10" (allow spaces around 'x')
    private static final Pattern SET_REP_PATTERN = Pattern.compile("(\\d+)\\s*[xхX]\\s*(\\d+|\\w+)");
    // Pattern for any URL
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+", Pattern.CASE_INSENSITIVE);

    public TrainingDay parse(String rawText) {
        TrainingDay day = new TrainingDay();
        List<Exercise> exercises = new ArrayList<>();
        day.setExercises(exercises);

        String[] lines = rawText.split("\\r?\\n");
        String currentSection = null;
        int position = 0;

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // Section header – ends with ':'
            if (line.endsWith(":")) {
                currentSection = line.substring(0, line.length() - 1).trim();
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
                    urls.add(urlMatcher.group());
                }
                ex.setVideoUrls(urls);

                // Remove URLs from the text to simplify further parsing
                String withoutUrls = content.replaceAll(URL_PATTERN.pattern(), "").trim();

                // Try to find sets×reps pattern
                Matcher setRepMatcher = SET_REP_PATTERN.matcher(withoutUrls);
                if (setRepMatcher.find()) {
                    ex.setSets(Integer.parseInt(setRepMatcher.group(1)));
                    ex.setRepsOrDuration(setRepMatcher.group(2));
                    // Remove that part from name
                    withoutUrls = withoutUrls.replace(setRepMatcher.group(), "").trim();
                }

                // The remaining text up to the first '(' is considered the name
                String name = withoutUrls;
                int parenIdx = name.indexOf('(');
                if (parenIdx > 0) {
                    ex.setNotes(name.substring(parenIdx).trim());
                    name = name.substring(0, parenIdx).trim();
                }
                ex.setName(name);

                exercises.add(ex);
            }
        }
        return day;
    }
}
