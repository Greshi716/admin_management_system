package com.admin.management.shared;

import java.util.Optional;

/** Who is signed in. Declared here so other modules do not read the security module's tables. */
public interface CurrentUserProvider {

	/** The signed-in user's id, empty when nobody is signed in. */
	Optional<Long> currentUserId();

}
