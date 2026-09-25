package com.group8.eventservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Full context-wiring smoke test. Requires a live MySQL reachable at the properties below —
 * run `docker compose up -d mysql` first (see README). Testcontainers would normally provide
 * this automatically, but this project's Docker Desktop has a docker-java/API incompatibility
 * that breaks it locally (works fine in CI on Linux runners); this is the pragmatic workaround.
 */
@SpringBootTest(properties = {
		"spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3307/event_service_db}",
		"spring.datasource.username=${DB_USERNAME:group8}",
		"spring.datasource.password=${DB_PASSWORD:group8}"
})
class EventServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
