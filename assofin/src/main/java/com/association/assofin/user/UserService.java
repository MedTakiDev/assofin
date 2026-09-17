package com.association.assofin.user;

import com.association.assofin.auth.dto.RegistrationRequestDTO;
import com.association.assofin.role.RoleEnum;
import com.association.assofin.role.RoleService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class UserService implements UserDetailsService {
    private final  RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return userRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with username: " + username));
    }

    public User saveUser(RegistrationRequestDTO request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already exists");
        }
        //add default user role
        var userRole = roleService.findByName(RoleEnum.ROLE_USER.name());
        User user = User.builder()
                .firstname(request.firstname())
                .lastname(request.lastname())
                .username(request.firstname() + " " +  request.lastname()) // handle username case in ldap approach
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(userRole)
                .accountLocked(false)
                .enabled(false)
                .createdDate(LocalDate.now())
                .build();
        return userRepository.save(user);
    }

    public User saveUser(User user) {
        return userRepository.save(user);
    }

    public User findById(long id) {
        return userRepository.findById(id)
                .orElseThrow(()-> new IllegalArgumentException("User not found"));
    }
}



