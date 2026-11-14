package com.rideshare.user.repository;

import com.rideshare.common.enums.AccountStatus;
import com.rideshare.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Emails are stored lower-cased, so callers must pass a lower-cased value. */
    Optional<User> findByEmail(String email);

    Optional<User> findByGoogleSubject(String googleSubject);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByPhoneNumberAndIdNot(String phoneNumber, UUID id);

    long countByAccountStatus(AccountStatus status);

    @Query("""
            select u from User u
            where lower(u.fullName) like :pattern or lower(u.email) like :pattern or u.phoneNumber like :pattern
            """)
    Page<User> search(@Param("pattern") String pattern, Pageable pageable);
}
