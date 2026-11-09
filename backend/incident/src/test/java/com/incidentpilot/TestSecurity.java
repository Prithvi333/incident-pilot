package com.incidentpilot;

import com.incidentpilot.model.Incident;
import com.incidentpilot.model.IncidentStatus;
import com.incidentpilot.model.Organization;
import com.incidentpilot.model.Role;
import com.incidentpilot.model.Severity;
import com.incidentpilot.model.User;
import com.incidentpilot.security.TenantPrincipal;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/** Test helpers: build tenant users/incidents and set the authenticated principal. */
public final class TestSecurity {

    private TestSecurity() {
    }

    public static Organization org(long id) {
        return Organization.builder().id(id).name("Org" + id).inviteCode("code" + id).build();
    }

    public static User user(long id, String username, Role role, Organization org) {
        return User.builder().id(id).username(username).password("x").role(role).organization(org).build();
    }

    public static TenantPrincipal login(User user) {
        TenantPrincipal principal = new TenantPrincipal(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
        return principal;
    }

    public static Incident incident(long id, Organization org, IncidentStatus status, Severity severity) {
        return Incident.builder()
                .id(id).organization(org).title("DB down").serviceName("db")
                .severity(severity).status(status).createdBy("alice")
                .build();
    }

    public static void clear() {
        SecurityContextHolder.clearContext();
    }
}
