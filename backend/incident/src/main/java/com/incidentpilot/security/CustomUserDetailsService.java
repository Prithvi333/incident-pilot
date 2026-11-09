package com.incidentpilot.security;

import com.incidentpilot.model.User;
import com.incidentpilot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Loads user details from the database for Spring Security authentication.
 *
 * <p>Builds a {@link TenantPrincipal} that carries the organization and maps the role to the
 * {@code ROLE_} authority format required by {@code hasRole()} checks. A user without an
 * organization (only possible before the startup migration ran) cannot authenticate.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        if (user.getOrganization() == null) {
            throw new UsernameNotFoundException("User has no organization: " + username);
        }
        return new TenantPrincipal(user);
    }
}
