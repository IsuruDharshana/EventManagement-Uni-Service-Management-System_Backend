package com.group8.eventservice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * API freeze guard (S3-07): the live OpenAPI document must equal docs/event-service-openapi.json,
 * the contract shared with the frontend, communication-feedback-service and other groups. Any
 * change to paths, fields, types or documented responses fails the build.
 *
 * After an intentional, agreed change, regenerate the file and commit it with the change:
 *   mvn test -Dtest=ApiContractTest -Dcontract.update=true
 */
@SpringBootTest(properties = {
        "spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3307/event_service_db}",
        "spring.datasource.username=${DB_USERNAME:group8}",
        "spring.datasource.password=${DB_PASSWORD:group8}",
        "events.auto-complete.enabled=false"
})
@AutoConfigureMockMvc
class ApiContractTest {

    private static final Path CONTRACT = Path.of("docs", "event-service-openapi.json");
    private static final JsonMapper JSON = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

    @Autowired
    private MockMvc mockMvc;

    @Test
    void liveApiMatchesTheFrozenContract() throws Exception {
        String body = mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        ObjectNode live = (ObjectNode) JSON.readTree(body);
        live.remove("servers"); // depends on where the service runs, not part of the contract

        if (Boolean.getBoolean("contract.update")) {
            Files.writeString(CONTRACT, JSON.writeValueAsString(live) + System.lineSeparator(), StandardCharsets.UTF_8);
        }

        assertThat(Files.exists(CONTRACT))
                .as("%s is missing; generate it with -Dcontract.update=true", CONTRACT).isTrue();
        JsonNode frozen = JSON.readTree(Files.readString(CONTRACT, StandardCharsets.UTF_8));
        assertThat(live)
                .as("The API no longer matches %s (API freeze). If this change is intentional and agreed with the "
                        + "API consumers, regenerate the file with -Dcontract.update=true and commit it.", CONTRACT)
                .isEqualTo(frozen);
    }
}
