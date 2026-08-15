package com.kcpc.mkt.security;

import com.kcpc.mkt.identity.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Present solely so Spring Boot does not fall back to its generated-password in-memory user.
 * Not part of the actual authentication path (login is handled directly by
 * {@link AuthenticationApplicationService}; httpBasic/formLogin are disabled).
 */
@Service
public class KcpcUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public KcpcUserDetailsService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return userRepository.findByEmailIgnoreCase(email)
                .map(KcpcUserPrincipal::new)
                .orElseThrow(() -> new UsernameNotFoundException("No user with email " + email));
    }
}
