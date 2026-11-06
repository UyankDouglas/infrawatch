package com.infrawatch.security;

import com.infrawatch.auth.UserRepository;
import java.util.Locale;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public AppUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return users.findByEmail(username.trim().toLowerCase(Locale.ROOT))
                .map(u -> new AppUserDetails(u.getId(), u.getEmail(), u.getFullName(), u.getPasswordHash(), u.isActive()))
                .orElseThrow(() -> new UsernameNotFoundException("Usuário não encontrado"));
    }
}
