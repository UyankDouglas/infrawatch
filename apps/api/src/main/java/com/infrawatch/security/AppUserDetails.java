package com.infrawatch.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Principal autenticado. Guarda somente o necessário (id, e-mail, nome, hash) para a sessão. */
public class AppUserDetails implements UserDetails {

    private final UUID id;
    private final String email;
    private final String fullName;
    private final String passwordHash;
    private final boolean active;

    public AppUserDetails(UUID id, String email, String fullName, String passwordHash, boolean active) {
        this.id = id;
        this.email = email;
        this.fullName = fullName;
        this.passwordHash = passwordHash;
        this.active = active;
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getFullName() { return fullName; }

    @Override public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }
    @Override public String getPassword() { return passwordHash; }
    @Override public String getUsername() { return email; }
    @Override public boolean isEnabled() { return active; }
    @Override public boolean isAccountNonLocked() { return active; }
}
