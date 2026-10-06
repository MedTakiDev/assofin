package com.association.assofin.auth;

import com.association.assofin.auth.activation.ActivationTokenService;
import com.association.assofin.auth.dto.AuthenticationRequestDTO;
import com.association.assofin.auth.dto.AuthenticationResponseDTO;
import com.association.assofin.auth.dto.RegistrationRequestDTO;
import com.association.assofin.infrastructure.app.properties.FrontendProperties;
import com.association.assofin.mail.EmailTemplateName;
import com.association.assofin.mail.MailService;
import com.association.assofin.security.jwt.JwtService;
import com.association.assofin.user.User;
import com.association.assofin.user.UserRepository;
import com.association.assofin.user.UserService;
import jakarta.mail.MessagingException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthenticationService {
    private final UserService userService;
    private final MailService mailService; //implement account activation with email
    private final FrontendProperties frontendProperties;

    //private final TokenService tokenService; implement account activation with email through the redis
    private final ActivationTokenService activationTokenService;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final UserRepository userRepository;


    public void register(RegistrationRequestDTO request){
        // Create new user
        User user = userService.saveUser(request);
        sendActivationEmail(user);
    }

    public AuthenticationResponseDTO authenticate(AuthenticationRequestDTO authenticationRequest) {
        var auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        authenticationRequest.email(),
                        authenticationRequest.password()
                )
        );
        Map<String, Object> claims = new HashMap<>();
        var user = (User) auth.getPrincipal();
        assert user != null;
        claims.put("fullname", user.getFirstname() + " " + user.getLastname());
        var jwtToken = jwtService.generateToken(claims, user);
        return AuthenticationResponseDTO.builder()
                .token(jwtToken)
                .build();
    }

    @Transactional
    public void activateAccount(String activationToken){
        Long userId = activationTokenService.validateToken(activationToken);
        var user = userRepository.findById(userId).orElseThrow(()->new EntityNotFoundException("User not found"));
        user.setEnabled(true);
        userService.saveUser(user);
        activationTokenService.consumeToken(activationToken);
    }

    private void sendActivationEmail(User user) {
        String activationToken = activationTokenService.createToken(user.getId());

        // Appends ?token=... safely to http://localhost:4200/activate-account
        String confirmationUrl = UriComponentsBuilder
                .fromUriString(frontendProperties.activationUrl())
                .queryParam("token", activationToken)
                .build()
                .toUriString();

        mailService.sendAccountActivationEmail(
                user.getEmail(),
                user.getUsername(),
                EmailTemplateName.ACTIVATION_ACCOUNT,
                confirmationUrl,
                "Avalon Odyssey account activation"
        );
    }
}
