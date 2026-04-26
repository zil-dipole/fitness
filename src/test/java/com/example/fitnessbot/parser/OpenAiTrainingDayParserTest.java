package com.example.fitnessbot.parser;

import com.example.fitnessbot.exception.TrainingDayException;
import com.example.fitnessbot.model.TrainingDay;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class OpenAiTrainingDayParserTest {

    @Test
    void parseMapsStructuredResponseToTrainingDay() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        OpenAiTrainingDayParser parser = new OpenAiTrainingDayParser(client, new ObjectMapper(), "gpt-4o-mini", "test-key");

        server.expect(requestTo("http://localhost/responses"))
                .andExpect(method(POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("""
                        {
                          "output": [
                            {
                              "content": [
                                {
                                  "text": "{\\"title\\":\\"Workout A\\",\\"exercises\\":[{\\"position\\":0,\\"section\\":\\"Warmup\\",\\"name\\":\\"Run\\",\\"sets\\":null,\\"repsOrDuration\\":\\"5 min\\",\\"videoUrls\\":[],\\"notes\\":null,\\"lastWeightKg\\":null},{\\"position\\":1,\\"section\\":\\"Main\\",\\"name\\":\\"Bench Press\\",\\"sets\\":3,\\"repsOrDuration\\":\\"6\\",\\"videoUrls\\":[\\"https://example.com/bench\\"],\\"notes\\":\\"70 kg\\",\\"lastWeightKg\\":70}]}"
                                }
                              ]
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        TrainingDay result = parser.parse("Workout A");

        assertThat(result.getTitle()).isEqualTo("Workout A");
        assertThat(result.getExercises()).hasSize(2);
        assertThat(result.getExercises().get(0).getName()).isEqualTo("Run");
        assertThat(result.getExercises().get(0).getRepsOrDuration()).isEqualTo("5 min");
        assertThat(result.getExercises().get(1).getName()).isEqualTo("Bench Press");
        assertThat(result.getExercises().get(1).getSets()).isEqualTo(3);
        assertThat(result.getExercises().get(1).getLastWeightKg()).isEqualTo(70.0);
        assertThat(result.getExercises().get(1).getVideoUrls()).containsExactly("https://example.com/bench");

        server.verify();
    }

    @Test
    void parseFallsBackToChatCompletionsWhenResponsesApiIsUnsupported() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        OpenAiTrainingDayParser parser = new OpenAiTrainingDayParser(client, new ObjectMapper(), "gpt-4o-mini", "test-key");

        server.expect(requestTo("http://localhost/responses"))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                { "detail": "This model does not support Responses API" }
                                """));
        server.expect(requestTo("http://localhost/chat/completions"))
                .andExpect(method(POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "{\\"title\\":\\"Workout B\\",\\"exercises\\":[]}"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        TrainingDay result = parser.parse("Workout B");

        assertThat(result.getTitle()).isEqualTo("Workout B");
        assertThat(result.getExercises()).isEmpty();
        server.verify();
    }

    @Test
    void parseUsesChatCompletionsFirstForGptOssProviderModel() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        OpenAiTrainingDayParser parser = new OpenAiTrainingDayParser(client, new ObjectMapper(), "openai/gpt-oss-120b-fast", "test-key");

        server.expect(requestTo("http://localhost/chat/completions"))
                .andExpect(method(POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "{\\"title\\":\\"Workout C\\",\\"exercises\\":[]}"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        TrainingDay result = parser.parse("Workout C");

        assertThat(result.getTitle()).isEqualTo("Workout C");
        assertThat(result.getExercises()).isEmpty();
        server.verify();
    }

    @Test
    void parseRetriesChatCompletionsWithoutStructuredOutputWhenProviderRejectsJsonSchema() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.build();
        OpenAiTrainingDayParser parser = new OpenAiTrainingDayParser(client, new ObjectMapper(), "openai/gpt-oss-120b-fast", "test-key");

        server.expect(requestTo("http://localhost/chat/completions"))
                .andExpect(method(POST))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                { "message": "response_format json_schema is not supported by this model" }
                                """));
        server.expect(requestTo("http://localhost/chat/completions"))
                .andExpect(method(POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer test-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("""
                        {
                          "choices": [
                            {
                              "message": {
                                "content": "{\\"title\\":\\"Workout D\\",\\"exercises\\":[]}"
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        TrainingDay result = parser.parse("Workout D");

        assertThat(result.getTitle()).isEqualTo("Workout D");
        assertThat(result.getExercises()).isEmpty();
        server.verify();
    }

    @Test
    void parseFailsFastWhenApiKeyMissing() {
        OpenAiTrainingDayParser parser = new OpenAiTrainingDayParser(RestClient.builder().build(), new ObjectMapper(), "gpt-4o-mini", "");

        assertThatThrownBy(() -> parser.parse("Workout A"))
                .isInstanceOf(TrainingDayException.class)
                .hasMessageContaining("OPENAI_API_KEY or NEBIUS_API_KEY");
    }
}
