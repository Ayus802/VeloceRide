package com.rideshare.review.repository;

import com.rideshare.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    /** Projection for the rating aggregate. */
    interface RatingProjection {
        UUID getUserId();

        Double getAverage();

        Long getTotal();
    }

    boolean existsByRideIdAndReviewerIdAndReviewedUserId(UUID rideId, UUID reviewerId, UUID reviewedUserId);

    /** One aggregate query (index-backed) for any number of users - no per-user N+1. */
    @Query("""
            select r.reviewedUser.id as userId, avg(r.rating) as average, count(r) as total
            from Review r
            where r.reviewedUser.id in :userIds and r.hidden = false
            group by r.reviewedUser.id
            """)
    List<RatingProjection> aggregateRatings(@Param("userIds") Collection<UUID> userIds);

    @Query(value = "select r from Review r join fetch r.reviewer where r.reviewedUser.id = :userId and r.hidden = false",
            countQuery = "select count(r) from Review r where r.reviewedUser.id = :userId and r.hidden = false")
    Page<Review> findVisibleForUser(@Param("userId") UUID userId, Pageable pageable);

    @Query("""
            select r from Review r join fetch r.reviewer join fetch r.reviewedUser
            where r.ride.id = :rideId and r.hidden = false
            order by r.createdAt desc
            """)
    List<Review> findVisibleForRide(@Param("rideId") UUID rideId);

    @Query(value = "select r from Review r join fetch r.reviewer join fetch r.reviewedUser",
            countQuery = "select count(r) from Review r")
    Page<Review> findAllDetailed(Pageable pageable);

    @Query(value = "select r from Review r join fetch r.reviewer join fetch r.reviewedUser where r.hidden = :hidden",
            countQuery = "select count(r) from Review r where r.hidden = :hidden")
    Page<Review> findAllDetailedByHidden(@Param("hidden") boolean hidden, Pageable pageable);
}
