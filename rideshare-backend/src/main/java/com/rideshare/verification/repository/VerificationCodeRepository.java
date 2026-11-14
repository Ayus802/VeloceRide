package com.rideshare.verification.repository;

import com.rideshare.common.enums.VerificationChannel;
import com.rideshare.verification.entity.VerificationCode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface VerificationCodeRepository extends JpaRepository<VerificationCode, UUID> {

    /** Newest unconsumed code for a channel, locked so concurrent guesses are counted correctly. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select c from VerificationCode c
            where c.user.id = :userId and c.channel = :channel and c.consumedAt is null
            order by c.createdAt desc
            """)
    List<VerificationCode> findOpenForUpdate(@Param("userId") UUID userId, @Param("channel") VerificationChannel channel);

    @Query("""
            select c from VerificationCode c
            where c.user.id = :userId and c.channel = :channel
            order by c.createdAt desc
            limit 1
            """)
    List<VerificationCode> findLatest(@Param("userId") UUID userId, @Param("channel") VerificationChannel channel);

    long countByUserIdAndChannelAndCreatedAtAfter(UUID userId, VerificationChannel channel, Instant after);

    @Modifying(flushAutomatically = true)
    @Query("""
            update VerificationCode c set c.consumedAt = :now
            where c.user.id = :userId and c.channel = :channel and c.consumedAt is null
            """)
    int invalidateOpen(@Param("userId") UUID userId, @Param("channel") VerificationChannel channel, @Param("now") Instant now);
}
