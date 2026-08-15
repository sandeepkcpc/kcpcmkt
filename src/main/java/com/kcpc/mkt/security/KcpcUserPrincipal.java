package com.kcpc.mkt.security;

import com.kcpc.mkt.identity.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Wraps the authenticated {@link User} for Spring Security. Authorities carry only the coarse
 * internal access class (ROLE_CEO_OWNER / ROLE_MARKETING_MANAGER / ROLE_EMPLOYEE); fine-grained
 * Operational-Permission + scope + self-review evaluation happens in the application/service
 * layer (AuthorizationService), never declaratively here - the frontend/annotation layer is
 * never the security authority (KCPC-MKT-R3.5-DEVELOPMENT-HANDOFF.md).
 */
public class KcpcUserPrincipal implements UserDetails {

    private final User user;

    public KcpcUserPrincipal(User user) {
        this.user = user;
    }

    public User user() {
        return user;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + user.resolvedAccessClass().name()));
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return user.isActive();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.isActive();
    }
}
