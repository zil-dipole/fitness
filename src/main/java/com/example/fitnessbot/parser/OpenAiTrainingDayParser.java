package com.example.fitnessbot.parser;

import com.example.fitnessbot.exception.TrainingDayException;
import com.example.fitnessbot.model.Exercise;
import com.example.fitnessbot.model.TrainingDay;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.client.RestClientBuilderConfigurer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class OpenAiTrainingDayParser {

    static final String PARSING_PROMPT = """
            You are an information extraction engine for a fitness training service.

            Task:
            Parse the user's raw workout text into a single JSON object. Return JSON only.

            Output JSON shape:
            {
              "title": string | null,
              "exercises": [
                {
                  "position": number,
                  "section": string,
                  "name": string,
                  "sets": number | null,
                  "repsOrDuration": string | null,
                  "videoUrls": string[],
                  "notes": string | null,
                  "lastWeightKg": number | null
                }
              ]
            }

            Rules:
            1. Preserve the original language of the input. Do not translate.
            2. `title` is the workout/day title if clearly present, otherwise null.
            3. `exercises` must preserve source order.
            4. `position` is zero-based: 0, 1, 2, ...
            5. `section` is the nearest active section header for the exercise.
            6. If no section exists before an exercise, use "General".
            7. `name` must contain only the exercise name, without sets, reps, duration, URLs, or trailing weight/load notes.
            8. `sets` is an integer if explicitly present, otherwise null.
            9. `repsOrDuration` should capture reps, duration, or effort target exactly as written when practical.
            10. Extract all valid video/demo URLs into `videoUrls`.
            11. Put leftover useful qualifiers into `notes`.
            12. Parse load into `lastWeightKg` only when the weight is clearly expressed in kilograms.
            13. Ignore empty lines, bullets, numbering, and decoration characters.
            14. Section headers may end with ":" but do not have to if their intent is obvious.
            15. Exercises may be written with bullets like "-", "*", "•", "—", "–", "+", "∙", or with numbering.
            16. If a line contains both exercise data and URLs, extract the URLs and still parse the exercise.
            17. If something is ambiguous, prefer a conservative parse: keep uncertain text in `notes`, and use null instead of guessing numeric fields.
            18. Numbered list items like `1.` or `2)` can be exercises and should be parsed just like bullet items.
            19. If a section/header indicates rounds or circuits, such as `3 rounds`, `3 circles`, or `3 круга`, keep that section and also carry the round-by-round instruction into each exercise's `notes` when useful.
            20. Do not invent exercises or fields.
            21. Return valid JSON only.
            """;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final String apiKey;

    @Autowired
    public OpenAiTrainingDayParser(RestClientBuilderConfigurer restClientBuilderConfigurer,
                                   ObjectMapper objectMapper,
                                   @Value("${openai.base-url}") String baseUrl,
                                   @Value("${openai.model}") String model,
                                   @Value("${openai.api-key:}") String apiKey) {
        this(restClientBuilderConfigurer.configure(RestClient.builder())
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build(), objectMapper, model, apiKey);
    }

    OpenAiTrainingDayParser(RestClient restClient, ObjectMapper objectMapper, String model, String apiKey) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
        this.model = model;
        this.apiKey = apiKey;
    }

    public TrainingDay parse(String rawText) throws TrainingDayException {
        if (rawText == null || rawText.isBlank()) {
            return emptyTrainingDay();
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new TrainingDayException("AI parser is enabled for the user, but no API key is configured. Set OPENAI_API_KEY or NEBIUS_API_KEY");
        }

        JsonNode requestBody = buildRequestBody(rawText);
        OpenAiResponse response = restClient.post()
                .uri("/responses")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .body(requestBody)
                .retrieve()
                .body(OpenAiResponse.class);

        if (response == null) {
            throw new TrainingDayException("OpenAI parser returned an empty response");
        }
        if (StringUtils.hasText(response.errorMessage())) {
            throw new TrainingDayException("OpenAI parser request failed: " + response.errorMessage());
        }

        String jsonPayload = response.extractFirstText();
        if (!StringUtils.hasText(jsonPayload)) {
            throw new TrainingDayException("OpenAI parser did not return structured training data");
        }

        try {
            ParsedTrainingDay parsed = objectMapper.readValue(jsonPayload, ParsedTrainingDay.class);
            return toTrainingDay(parsed);
        } catch (Exception e) {
            throw new TrainingDayException("Failed to deserialize OpenAI parser response", e);
        }
    }

    private JsonNode buildRequestBody(String rawText) throws TrainingDayException {
        try {
            String payload = """
                    {
                      "model": "%s",
                      "input": [
                        {
                          "role": "system",
                          "content": [
                            {
                              "type": "input_text",
                              "text": %s
                            }
                          ]
                        },
                        {
                          "role": "user",
                          "content": [
                            {
                              "type": "input_text",
                              "text": %s
                            }
                          ]
                        }
                      ],
                      "text": {
                        "format": {
                          "type": "json_schema",
                          "name": "training_day_parse",
                          "strict": true,
                          "schema": {
                            "type": "object",
                            "additionalProperties": false,
                            "properties": {
                              "title": {
                                "anyOf": [
                                  { "type": "string" },
                                  { "type": "null" }
                                ]
                              },
                              "exercises": {
                                "type": "array",
                                "items": {
                                  "type": "object",
                                  "additionalProperties": false,
                                  "properties": {
                                    "position": { "type": "integer" },
                                    "section": { "type": "string" },
                                    "name": { "type": "string" },
                                    "sets": {
                                      "anyOf": [
                                        { "type": "integer" },
                                        { "type": "null" }
                                      ]
                                    },
                                    "repsOrDuration": {
                                      "anyOf": [
                                        { "type": "string" },
                                        { "type": "null" }
                                      ]
                                    },
                                    "videoUrls": {
                                      "type": "array",
                                      "items": { "type": "string" }
                                    },
                                    "notes": {
                                      "anyOf": [
                                        { "type": "string" },
                                        { "type": "null" }
                                      ]
                                    },
                                    "lastWeightKg": {
                                      "anyOf": [
                                        { "type": "number" },
                                        { "type": "null" }
                                      ]
                                    }
                                  },
                                  "required": [
                                    "position",
                                    "section",
                                    "name",
                                    "sets",
                                    "repsOrDuration",
                                    "videoUrls",
                                    "notes",
                                    "lastWeightKg"
                                  ]
                                }
                              }
                            },
                            "required": ["title", "exercises"]
                          }
                        }
                      }
                    }
                    """.formatted(
                    escapeJson(model),
                    objectMapper.writeValueAsString(PARSING_PROMPT),
                    objectMapper.writeValueAsString(rawText)
            );
            return objectMapper.readTree(payload);
        } catch (Exception e) {
            throw new TrainingDayException("Failed to build OpenAI parser request", e);
        }
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private TrainingDay toTrainingDay(ParsedTrainingDay parsed) {
        TrainingDay day = new TrainingDay();
        day.setTitle(parsed.title());

        List<Exercise> exercises = new ArrayList<>();
        List<ParsedExercise> parsedExercises = parsed.exercises() == null ? List.of() : parsed.exercises();
        for (int i = 0; i < parsedExercises.size(); i++) {
            ParsedExercise parsedExercise = parsedExercises.get(i);
            Exercise exercise = new Exercise();
            exercise.setPosition(parsedExercise.position() != null ? parsedExercise.position() : i);
            exercise.setSection(StringUtils.hasText(parsedExercise.section()) ? parsedExercise.section() : "General");
            exercise.setName(parsedExercise.name());
            exercise.setSets(parsedExercise.sets());
            exercise.setRepsOrDuration(parsedExercise.repsOrDuration());
            exercise.setVideoUrls(parsedExercise.videoUrls() == null ? new ArrayList<>() : new ArrayList<>(parsedExercise.videoUrls()));
            exercise.setNotes(parsedExercise.notes());
            exercise.setLastWeightKg(parsedExercise.lastWeightKg());
            exercises.add(exercise);
        }

        day.setExercises(exercises);
        return day;
    }

    private TrainingDay emptyTrainingDay() {
        TrainingDay day = new TrainingDay();
        day.setExercises(new ArrayList<>());
        return day;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OpenAiResponse(List<OutputItem> output, ErrorBody error) {
        String extractFirstText() {
            if (output == null) {
                return null;
            }
            for (OutputItem item : output) {
                if (item == null || item.content() == null) {
                    continue;
                }
                for (OutputContent contentItem : item.content()) {
                    if (contentItem != null && StringUtils.hasText(contentItem.text())) {
                        return contentItem.text();
                    }
                }
            }
            return null;
        }

        String errorMessage() {
            return error == null ? null : error.message();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OutputItem(List<OutputContent> content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record OutputContent(String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ErrorBody(String message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ParsedTrainingDay(String title, List<ParsedExercise> exercises) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ParsedExercise(
            Integer position,
            String section,
            String name,
            Integer sets,
            String repsOrDuration,
            List<String> videoUrls,
            String notes,
            Double lastWeightKg
    ) {
        public List<String> videoUrls() {
            return videoUrls == null ? Collections.emptyList() : videoUrls;
        }
    }
}
