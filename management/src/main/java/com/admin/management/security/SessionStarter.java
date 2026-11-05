package com.admin.management.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Starts a server session for a user, after sign-in or sign-up (F6 FR-008). */
@Component
class SessionStarter {

	private final SecurityContextRepository contextRepository;

	SessionStarter(SecurityContextRepository contextRepository) {
		this.contextRepository = contextRepository;
	}

	void start(User user, HttpServletRequest request, HttpServletResponse response) {
		if (request.getSession(false) != null) {
			request.changeSessionId();
		}
		Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(user.getUsername(), null,
				AuthorityUtils.createAuthorityList("ROLE_" + user.getRole()));
		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		contextRepository.saveContext(context, request, response);
	}

}
