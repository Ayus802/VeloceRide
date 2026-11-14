package com.rideshare.review.service;

import com.rideshare.review.dto.UserRatingResponse;
import com.rideshare.review.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Average rating and review count computed with an indexed SQL aggregate, batched for lists.
 */
@Service
@RequiredArgsConstructor
public class RatingService {

    private final ReviewRepository reviewRepository;

    @Transactional(readOnly = true)
    public UserRatingResponse getRating(UUID userId) {
        return getRatings(List.of(userId)).getOrDefault(userId, UserRatingResponse.empty(userId));
    }

    @Transactional(readOnly = true)
    public Map<UUID, UserRatingResponse> getRatings(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return reviewRepository.aggregateRatings(userIds).stream()
                .map(p -> new UserRatingResponse(p.getUserId(), round(p.getAverage()), p.getTotal()))
                .collect(Collectors.toMap(UserRatingResponse::userId, Function.identity()));
    }

    private static Double round(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
