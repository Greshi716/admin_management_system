package com.admin.management.security;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByUsername(String username);

	Optional<User> findFirstByRoleOrderByIdAsc(String role);

	/** Counts one failed sign-in in a single statement, so two at the same moment cannot collide on the row version. */
	@Modifying(clearAutomatically = true)
	@Query("update User u set u.failedAttempts = u.failedAttempts + 1 where u.id = :id")
	void addFailedAttempt(@Param("id") Long id);

	@Query("select u.failedAttempts from User u where u.id = :id")
	int failedAttempts(@Param("id") Long id);

	/** Starts the pause and gives a fresh set of attempts once it ends. */
	@Modifying(clearAutomatically = true)
	@Query("update User u set u.lockedUntil = :until, u.failedAttempts = 0 where u.id = :id")
	void pause(@Param("id") Long id, @Param("until") Instant until);

}
