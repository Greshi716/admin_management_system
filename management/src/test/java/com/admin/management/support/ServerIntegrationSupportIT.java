package com.admin.management.support;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;

class ServerIntegrationSupportIT extends ServerIntegrationTest {

	@Value("${local.server.port}")
	int port;

	@Test
	void startsOnRandomPort() {
		assertThat(port).isPositive();
	}

}
