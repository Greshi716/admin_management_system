package com.admin.management.security;

import java.util.Optional;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.admin.management.shared.CurrentUserProvider;

@Component
class SecurityCurrentUserProvider implements CurrentUserProvider {

	private final UserRepository users;

	SecurityCurrentUserProvider(UserRepository users) {
		this.users = users;
	}

	@Override
	public Optional<Long> currentUserId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !authentication.isAuthenticated()
				|| authentication instanceof AnonymousAuthenticationToken) {
			return Optional.empty();
		}
		return users.findByUsername(authentication.getName()).map(User::getId);
	}

}
