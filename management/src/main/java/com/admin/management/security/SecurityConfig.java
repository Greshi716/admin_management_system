package com.admin.management.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Server-session security for the single-page app (FR-001, FR-006, FR-007). Failures answer with JSON, never
 * with a redirect: the Angular app decides which page to show. Everything outside {@code /api} (index.html,
 * scripts, styles) is public because it holds no data; every {@code /api} call except sign-in needs a session.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

	private static final String NOT_SIGNED_IN = """
			{"title":"Not signed in","status":401,"detail":"Sign in to continue.","code":"unauthenticated"}""";

	private static final String FORBIDDEN = """
			{"title":"Request refused","status":403,"detail":"The request could not be verified. Reload the page and try again.","code":"forbidden"}""";

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityContextRepository contextRepository,
			Environment environment) throws Exception {
		// Only the test profile has /api/test-support; elsewhere the pattern matches nothing.
		String[] open = environment.acceptsProfiles(Profiles.of("test"))
				? new String[] { "/api/auth/login", TestSupportController.PATH } : new String[] { "/api/auth/login" };
		http.csrf((csrf) -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
			.csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
			.securityContext((context) -> context.securityContextRepository(contextRepository))
			.authorizeHttpRequests((requests) -> requests.requestMatchers(open)
				.permitAll()
				.requestMatchers("/api/**")
				.authenticated()
				.anyRequest()
				.permitAll())
			.exceptionHandling((errors) -> errors
				.authenticationEntryPoint((request, response, ex) -> writeProblem(response, 401, NOT_SIGNED_IN))
				.accessDeniedHandler((request, response, ex) -> writeProblem(response, 403, FORBIDDEN)))
			.formLogin((form) -> form.disable())
			.httpBasic((basic) -> basic.disable())
			.logout((logout) -> logout.disable())
			// The token cookie must be written on the first response, or the first sign-in POST fails with 403.
			.addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class);
		return http.build();
	}

	/**
	 * Sign-in is checked by {@link AuthController}, not by Spring's form login. This bean exists so Spring Boot
	 * does not create its default user and print a generated password to the console.
	 */
	@Bean
	UserDetailsService noLoginThroughSpringUserDetails() {
		return (username) -> {
			throw new UsernameNotFoundException("Sign-in is handled by AuthController");
		};
	}

	private static void writeProblem(HttpServletResponse response, int status, String body) throws IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(body);
	}

}
