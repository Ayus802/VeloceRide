package com.rideshare.security;

import com.rideshare.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    /** Used by the login flow: the "username" is the email address. */
    @Override
    @Transactional(readOnly = true)
    public UserPrincipal loadUserByUsername(String email) {
        return userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT))
                .map(UserPrincipal::from)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid email or password"));
    }

    /** Used for token-based authentication: re-reads the account so deactivation takes effect immediately. */
    @Transactional(readOnly = true)
    public Optional<UserPrincipal> loadUserById(UUID id) {
        return userRepository.findById(id).map(UserPrincipal::from);
    }
}
