package com.group8.eventservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Lightweight context-wiring smoke test. Excludes datasource/JPA/Flyway
 * autoconfiguration so it doesn't need a live database — schema and entity
 * correctness against a real MySQL is covered separately (see repository
 * package tests / manual docker compose verification in the README).
 */
@SpringBootTest
@ImportAutoConfiguration(exclude = {
		DataSourceAutoConfiguration.class,
		HibernateJpaAutoConfiguration.class,
		FlywayAutoConfiguration.class
})
class EventServiceApplicationTests {

	@Test
	void contextLoads() {
	}

}
