package com.rideshare.payment.repository;

import com.rideshare.common.enums.PaymentStatus;
import com.rideshare.payment.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    String DETAILED = "select p from Payment p join fetch p.booking b join fetch b.ride r join fetch r.driver ";

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.id = :id")
    Optional<Payment> findByIdForUpdate(@Param("id") UUID id);

    @Query(DETAILED + "where p.id = :id")
    Optional<Payment> findDetailedById(@Param("id") UUID id);

    @Query("select p from Payment p where p.booking.id = :bookingId and p.paymentStatus in :statuses")
    List<Payment> findByBookingAndStatuses(@Param("bookingId") UUID bookingId,
                                           @Param("statuses") Collection<PaymentStatus> statuses);

    @Query(value = DETAILED + "where p.payer.id = :payerId",
            countQuery = "select count(p) from Payment p where p.payer.id = :payerId")
    Page<Payment> findByPayer(@Param("payerId") UUID payerId, Pageable pageable);

    /** Locks the matching rows; PostgreSQL re-checks the status filter after acquiring each lock. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.booking.id = :bookingId and p.paymentStatus in :statuses")
    List<Payment> findByBookingAndStatusesForUpdate(@Param("bookingId") UUID bookingId,
                                                    @Param("statuses") Collection<PaymentStatus> statuses);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.paymentStatus = :status")
    BigDecimal sumAmountByStatus(@Param("status") PaymentStatus status);
}
