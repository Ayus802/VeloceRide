package com.rideshare.security;

import com.rideshare.common.enums.Role;
import com.rideshare.user.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Authenticated principal. {@link #getUsername()} returns the user id so that STOMP user destinations
 * ({@code /user/queue/...}) are addressed by id. Note: no "RIDER"/"PASSENGER" authority exists on purpose -
 * every USER can both drive and book; ownership is checked in the services.
 */
public record UserPrincipal(UUID id, String email, String password, Role role, boolean active) implements UserDetails {

    public static UserPrincipal from(User user) {
        return new UserPrincipal(user.getId(), user.getEmail(), user.getPassword(), user.getRole(), user.isActive());
    }

    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return role == Role.ADMIN
                ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("ROLE_USER"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return id.toString();
    }

    @Override
    public boolean isEnabled() {
        return active;
    }

    @Override
    public String toString() {
        return "UserPrincipal[id=" + id + ", role=" + role + "]"; // never print the password hash
    }
}
