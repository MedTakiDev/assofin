package com.association.assofin.auth;

import com.association.assofin.auth.dto.AuthenticationRequestDTO;
import com.association.assofin.auth.dto.AuthenticationResponseDTO;
import com.association.assofin.auth.dto.RegistrationRequestDTO;
import com.association.assofin.security.jwt.JwtService;
import com.association.assofin.user.User;
import com.association.assofin.user.UserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthenticationService {
    private final UserService userService;
    //private final MailService mailService; implement account activation with email
    //private final TokenService tokenService; implement account activation with email through the token
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;




    public void register(RegistrationRequestDTO request){
        // Create new user
        User user = userService.saveUser(request);
        //sendActivationEmail(user);
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

    /*@Transactional
    public void activateAccount(String activationToken){
        Token savedToken = tokenService.findByToken(activationToken);
        if(savedToken.getValidatedAt() != null){
            throw new BadCredentialsException("Activation Token has already been validated");
        }
        if(LocalDateTime.now().isAfter(savedToken.getExpiredAt())) {
            sendActivationEmail(savedToken.getUser());
            throw new BadCredentialsException("Activation Token has been expired. A new token has been sent");
        }
        var user = userService.findById(savedToken.getUser().getId()); // just to double-check the user
        user.setEnabled(true);
        userService.saveUser(user);
        savedToken.setValidatedAt(LocalDateTime.now());
        tokenService.saveToken(savedToken);
    }*/

    /*private void sendActivationEmail(User user){
        //generate activation token
        var activationToken = tokenService.generateAndSaveActivationToken(user);
        //send activation mail
        try {
            mailService.sendValidationEmail(
                    user.getEmail(),
                    user.getName(),
                    EmailTemplateName.ACTIVATION_ACCOUNT,
                    activationURL,
                    activationToken,
                    "Avalon Odyssey account activation");
        } catch (MessagingException e) {
            throw new RuntimeException(e);
        }
    }*/
}
