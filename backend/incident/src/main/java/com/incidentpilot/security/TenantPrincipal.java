package com.incidentpilot.security;

import com.incidentpilot.model.Role;
import com.incidentpilot.model.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * Authenticated principal that carries the caller's tenant (organization) next to the usual
 * Spring Security fields. Services read the organization from here, never from request input.
 */
@Getter
public class TenantPrincipal implements UserDetails {

    private final Long userId;
    private final String username;
    private final String password;
    private final Long organizationId;
    private final String organizationName;
    private final Role role;

    public TenantPrincipal(User user) {
        this.userId = user.getId();
        this.username = user.getUsername();
        this.password = user.getPassword();
        this.organizationId = user.getOrganization().getId();
        this.organizationName = user.getOrganization().getName();
        this.role = user.getRole();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
