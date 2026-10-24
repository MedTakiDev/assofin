package com.association.assofin.user.service;

import com.association.assofin.auth.dto.request.RegistrationRequestDTO;
import com.association.assofin.user.dto.response.UserProfileResponseDTO;
import com.association.assofin.role.RoleEnum;
import com.association.assofin.role.service.RoleService;
import com.association.assofin.user.entity.User;
import com.association.assofin.user.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class UserService {
    private final  RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;


    public User saveUser(RegistrationRequestDTO request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already exists");
        }
        //add default user role
        var userRole = roleService.findByName(RoleEnum.ROLE_ADMIN.name());
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



    @Transactional(readOnly = true)
    public User getUserEntityByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new EntityNotFoundException("User not found with email: " + email));
    }

    @Transactional(readOnly = true)
    public UserProfileResponseDTO getProfileByUsername(String email) {
        User user = getUserEntityByEmail(email);
        return UserProfileResponseDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstname(user.getFirstname())
                .lastname(user.getLastname())
                .role(user.getRole())
                .build();
    }

    @Transactional(readOnly = true)
    public UserProfileResponseDTO getUserProfileById(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found with ID: " + userId));
        return UserProfileResponseDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstname(user.getFirstname())
                .lastname(user.getLastname())
                .role(user.getRole())
                .build();
    }
}



