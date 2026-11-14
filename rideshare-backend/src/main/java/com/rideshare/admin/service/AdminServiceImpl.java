package com.rideshare.admin.service;

import com.rideshare.admin.dto.AdminReviewResponse;
import com.rideshare.admin.dto.AdminUserResponse;
import com.rideshare.admin.dto.ModerateReviewRequest;
import com.rideshare.admin.dto.PlatformStatsResponse;
import com.rideshare.auth.service.RefreshTokenService;
import com.rideshare.booking.repository.BookingRepository;
import com.rideshare.common.enums.AccountStatus;
import com.rideshare.common.enums.BookingStatus;
import com.rideshare.common.enums.PaymentStatus;
import com.rideshare.common.enums.RideStatus;
import com.rideshare.common.enums.VerificationStatus;
import com.rideshare.common.exception.BusinessRuleException;
import com.rideshare.common.exception.ResourceNotFoundException;
import com.rideshare.common.response.PageResponse;
import com.rideshare.common.util.PageableUtils;
import com.rideshare.payment.repository.PaymentRepository;
import com.rideshare.review.entity.Review;
import com.rideshare.review.repository.ReviewRepository;
import com.rideshare.ride.repository.RideRepository;
import com.rideshare.user.entity.User;
import com.rideshare.user.repository.UserRepository;
import com.rideshare.vehicle.repository.VehicleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {

    private final UserRepository userRepository;
    private final VehicleRepository vehicleRepository;
    private final RideRepository rideRepository;
    private final BookingRepository bookingRepository;
    private final ReviewRepository reviewRepository;
    private final PaymentRepository paymentRepository;
    private final RefreshTokenService refreshTokenService;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminUserResponse> listUsers(String query, Pageable pageable) {
        Pageable page = PageableUtils.sanitize(pageable, Set.of("createdAt", "fullName", "email", "accountStatus"),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> users = (query == null || query.isBlank())
                ? userRepository.findAll(page)
                : userRepository.search("%" + query.trim().toLowerCase(Locale.ROOT) + "%", page);
        return PageResponse.of(users, AdminUserResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserResponse getUser(UUID userId) {
        return AdminUserResponse.from(loadUser(userId));
    }

    @Override
    @Transactional
    public AdminUserResponse updateAccountStatus(UUID adminId, UUID userId, AccountStatus status) {
        if (adminId.equals(userId)) {
            throw new BusinessRuleException("Admins cannot change the status of their own account");
        }
        User user = loadUser(userId);
        user.setAccountStatus(status);
        if (status == AccountStatus.DEACTIVATED) {
            refreshTokenService.revokeAll(userId); // access tokens stop working immediately (filter re-checks status)
        }
        return AdminUserResponse.from(userRepository.saveAndFlush(user));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminReviewResponse> listReviews(Boolean hidden, Pageable pageable) {
        Pageable page = PageableUtils.sanitize(pageable, Set.of("createdAt", "rating"),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Review> reviews = hidden == null
                ? reviewRepository.findAllDetailed(page)
                : reviewRepository.findAllDetailedByHidden(hidden, page);
        return PageResponse.of(reviews, AdminReviewResponse::from);
    }

    @Override
    @Transactional
    public AdminReviewResponse moderateReview(UUID reviewId, ModerateReviewRequest request) {
        Review review = reviewRepository.findById(reviewId).orElseThrow(() -> ResourceNotFoundException.of("Review"));
        review.setHidden(request.hidden());
        review.setModerationNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());
        return AdminReviewResponse.from(reviewRepository.saveAndFlush(review));
    }

    @Override
    @Transactional(readOnly = true)
    public PlatformStatsResponse stats() {
        Map<RideStatus, Long> rides = new EnumMap<>(RideStatus.class);
        Arrays.stream(RideStatus.values()).forEach(s -> rides.put(s, rideRepository.countByRideStatus(s)));
        Map<BookingStatus, Long> bookings = new EnumMap<>(BookingStatus.class);
        Arrays.stream(BookingStatus.values()).forEach(s -> bookings.put(s, bookingRepository.countByBookingStatus(s)));
        return new PlatformStatsResponse(
                userRepository.count(),
                userRepository.countByAccountStatus(AccountStatus.ACTIVE),
                userRepository.countByAccountStatus(AccountStatus.DEACTIVATED),
                vehicleRepository.countByDeletedFalse(),
                vehicleRepository.countByVerificationStatusAndDeletedFalse(VerificationStatus.PENDING),
                vehicleRepository.countByVerificationStatusAndDeletedFalse(VerificationStatus.VERIFIED),
                rides.values().stream().mapToLong(Long::longValue).sum(),
                rides,
                bookings.values().stream().mapToLong(Long::longValue).sum(),
                bookings,
                reviewRepository.count(),
                paymentRepository.sumAmountByStatus(PaymentStatus.SUCCESS));
    }

    private User loadUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> ResourceNotFoundException.of("User"));
    }
}
