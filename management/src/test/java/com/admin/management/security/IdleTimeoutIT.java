package com.admin.management.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.context.ServletWebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.boot.tomcat.TomcatWebServer;

import com.admin.management.support.Covers;
import com.admin.management.support.ServerIntegrationTest;
import com.admin.management.support.TestAdmin;

/**
 * Runs against a real servlet container because the idle sign-out comes from the container's session
 * expiry. Boot rounds the Tomcat session timeout up to whole minutes, so the test does not wait for it:
 * it expires the server-side session directly and checks what the listener records.
 */
@Covers("FR-008")
class IdleTimeoutIT extends ServerIntegrationTest {

	@Autowired
	ServletWebServerApplicationContext server;

	@Autowired
	JdbcTemplate jdbc;

	@Value("${server.servlet.session.timeout}")
	Duration configuredTimeout;

	@Test
	void theIdleTimeoutIsTenMinutes() {
		assertThat(configuredTimeout).isEqualTo(Duration.ofMinutes(10));
	}

	@Test
	void anExpiredSessionProducesExactlyOneIdleSignOutEntry() throws Exception {
		CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
		HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).build();
		String base = "http://localhost:" + server.getWebServer().getPort();
		client.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/me")).build(), BodyHandlers.discarding());
		String xsrf = cookie(cookies, base, "XSRF-TOKEN");
		HttpResponse<String> login = client.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/login"))
			.header("Content-Type", "application/json")
			.header("X-XSRF-TOKEN", xsrf)
			.POST(BodyPublishers.ofString("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(TestAdmin.USERNAME,
					TestAdmin.PASSWORD)))
			.build(), BodyHandlers.ofString());
		assertThat(login.statusCode()).isEqualTo(200);
		String sessionId = cookie(cookies, base, "JSESSIONID");
		int before = idleRows();

		expire(sessionId);
		expire(sessionId);

		assertThat(idleRows()).isEqualTo(before + 1);
		HttpResponse<String> after = client.send(HttpRequest.newBuilder(URI.create(base + "/api/auth/me")).build(),
				BodyHandlers.ofString());
		assertThat(after.statusCode()).isEqualTo(401);
	}

	private void expire(String sessionId) throws Exception {
		Container host = ((TomcatWebServer) server.getWebServer()).getTomcat().getHost();
		for (Container child : host.findChildren()) {
			Session session = ((Context) child).getManager().findSession(sessionId);
			if (session != null) {
				session.expire();
			}
		}
	}

	private static String cookie(CookieManager cookies, String base, String name) {
		return cookies.getCookieStore()
			.get(URI.create(base))
			.stream()
			.filter((cookie) -> cookie.getName().equals(name))
			.map(HttpCookie::getValue)
			.findFirst()
			.orElseThrow(() -> new AssertionError("No " + name + " cookie was set"));
	}

	private int idleRows() {
		return jdbc.queryForObject("""
				select count(*) from audit_log where area = 'Security' and changes->>'event' = 'idle'
				""", Integer.class);
	}

}
