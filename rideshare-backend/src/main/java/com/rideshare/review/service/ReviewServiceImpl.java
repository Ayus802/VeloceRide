package com.rideshare.review.service;

import com.rideshare.booking.repository.BookingRepository;
import com.rideshare.common.enums.BookingStatus;
import com.rideshare.common.enums.RideStatus;
import com.rideshare.common.event.ReviewEvent;
import com.rideshare.common.exception.BusinessRuleException;
import com.rideshare.common.exception.DuplicateResourceException;
import com.rideshare.common.exception.ForbiddenException;
import com.rideshare.common.exception.ResourceNotFoundException;
import com.rideshare.common.response.PageResponse;
import com.rideshare.common.util.PageableUtils;
import com.rideshare.review.dto.CreateReviewRequest;
import com.rideshare.review.dto.ReviewResponse;
import com.rideshare.review.dto.UserRatingResponse;
import com.rideshare.review.dto.UserReviewsResponse;
import com.rideshare.review.entity.Review;
import com.rideshare.review.repository.ReviewRepository;
import com.rideshare.ride.entity.Ride;
import com.rideshare.ride.repository.RideRepository;
import com.rideshare.user.dto.UserSummary;
import com.rideshare.user.entity.User;
import com.rideshare.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Review rules:
 * <ul>
 *   <li>the ride must be COMPLETED;</li>
 *   <li>the reviewer must be the driver or a passenger whose booking COMPLETED;</li>
 *   <li>driver -> one of those passengers, passenger -> the driver (no passenger-to-passenger, no self review);</li>
 *   <li>one review per (ride, reviewer, reviewed user) - also enforced by a unique constraint.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class ReviewServiceImpl implements ReviewService {

    private final ReviewRepository reviewRepository;
    private final RideRepository rideRepository;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private final RatingService ratingService;
    private final ApplicationEventPublisher events;

    @Override
    @Transactional
    public ReviewResponse create(UUID reviewerId, CreateReviewRequest request) {
        Ride ride = rideRepository.findById(request.rideId()).orElseThrow(() -> ResourceNotFoundException.of("Ride"));
        UUID reviewedId = request.reviewedUserId();
        if (reviewerId.equals(reviewedId)) {
            throw new BusinessRuleException("You cannot review yourself");
        }
        UUID driverId = ride.getDriver().getId();
        boolean reviewerIsDriver = driverId.equals(reviewerId);
        boolean reviewerIsPassenger = !reviewerIsDriver && bookingRepository.existsByRideIdAndPassengerIdAndBookingStatusIn(
                ride.getId(), reviewerId, Set.of(BookingStatus.ACCEPTED, BookingStatus.COMPLETED));
        if (!reviewerIsDriver && !reviewerIsPassenger) {
            throw new ForbiddenException("Only participants of this ride can submit reviews");
        }
        if (ride.getRideStatus() != RideStatus.COMPLETED) {
            throw new BusinessRuleException("Reviews can only be submitted after the ride is completed");
        }
        if (reviewerIsPassenger && !completedPassenger(ride.getId(), reviewerId)) {
            throw new ForbiddenException("Only participants of this ride can submit reviews");
        }
        if (reviewerIsDriver && !completedPassenger(ride.getId(), reviewedId)) {
            throw new BusinessRuleException("The reviewed user was not a passenger on this ride");
        }
        if (reviewerIsPassenger && !driverId.equals(reviewedId)) {
            throw new BusinessRuleException("Passengers can only review the driver of the ride");
        }
        if (reviewRepository.existsByRideIdAndReviewerIdAndReviewedUserId(ride.getId(), reviewerId, reviewedId)) {
            throw new DuplicateResourceException("You have already reviewed this user for this ride");
        }

        User reviewer = userRepository.getReferenceById(reviewerId);
        Review review = new Review();
        review.setRide(ride);
        review.setReviewer(reviewer);
        review.setReviewedUser(userRepository.getReferenceById(reviewedId));
        review.setRating(request.rating());
        review.setComment(request.comment() == null || request.comment().isBlank() ? null : request.comment().trim());
        reviewRepository.saveAndFlush(review);
        events.publishEvent(new ReviewEvent(review.getId(), ride.getId(), reviewedId, reviewer.getFullName(),
                request.rating()));
        return toResponse(review);
    }

    @Override
    @Transactional(readOnly = true)
    public UserReviewsResponse getUserReviews(UUID userId, Pageable pageable) {
        requireUser(userId);
        Pageable page = PageableUtils.sanitize(pageable, Set.of("createdAt", "rating"),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        PageResponse<ReviewResponse> reviews = PageResponse.of(reviewRepository.findVisibleForUser(userId, page),
                this::toResponse);
        return new UserReviewsResponse(ratingService.getRating(userId), reviews);
    }

    @Override
    @Transactional(readOnly = true)
    public UserRatingResponse getUserRating(UUID userId) {
        requireUser(userId);
        return ratingService.getRating(userId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ReviewResponse> getRideReviews(UUID rideId) {
        if (!rideRepository.existsById(rideId)) {
            throw ResourceNotFoundException.of("Ride");
        }
        return reviewRepository.findVisibleForRide(rideId).stream().map(this::toResponse).toList();
    }

    private boolean completedPassenger(UUID rideId, UUID userId) {
        return bookingRepository.existsByRideIdAndPassengerIdAndBookingStatus(rideId, userId, BookingStatus.COMPLETED);
    }

    private void requireUser(UUID userId) {
        if (!userRepository.existsById(userId)) {
            throw ResourceNotFoundException.of("User");
        }
    }

    private ReviewResponse toResponse(Review r) {
        return new ReviewResponse(r.getId(), r.getRide().getId(), UserSummary.from(r.getReviewer()),
                r.getReviewedUser().getId(), r.getRating(), r.getComment(), r.getCreatedAt());
    }
}
