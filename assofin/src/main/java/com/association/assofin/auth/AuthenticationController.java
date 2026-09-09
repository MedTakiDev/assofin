package com.association.assofin.auth;

import com.association.assofin.auth.dto.AuthenticationRequestDTO;
import com.association.assofin.auth.dto.AuthenticationResponseDTO;
import com.association.assofin.auth.dto.RegistrationRequestDTO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("auth")
@RequiredArgsConstructor
@Tag(name = "authentication")
public class AuthenticationController {

    private final AuthenticationService authService;//this is for username password authentication from the database
    //private final LdapAuthenticationService ldapAuthenticationService;//this is used for ldap authentication

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<?> register(@RequestBody @Valid RegistrationRequestDTO request) throws MessagingException {
        authService.register(request);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<AuthenticationResponseDTO> authenticate (@RequestBody @Valid AuthenticationRequestDTO authenticationRequest, HttpServletResponse httpServletResponse){

        //Http only cookie based authentication
        /*AuthenticationResponse response = authService.authenticate(authenticationRequest);

        // Create a secure HTTP-only cookie for the JWT token
        ResponseCookie cookie = ResponseCookie.from("token", response.getToken())
                .httpOnly(true)           // Cannot be accessed via JavaScript
                .secure(false)            // set to true in production (requires HTTPS)
                .path("/")                // Cookie is valid for the entire domain
                .maxAge(24 * 60 * 60)     // 1 day
                .sameSite("Strict")       // Helps mitigate CSRF
                .build();

        // Add the cookie to the response header
        httpServletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        // Optionally, you can exclude the token from the response body for better security
        AuthenticationResponse safeResponse = AuthenticationResponse.builder()
                .message("Login successful")
                .build();

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(safeResponse);*/
        //old implementation return the token within the authentication response
        System.out.println(authenticationRequest.email());
        System.out.println(authenticationRequest.password());
        //TODO: check the user authentication then retrieve the user authorities from the database
        return ResponseEntity.ok(authService.authenticate(authenticationRequest));
    }

    /*@PostMapping("/ldap-login")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<AuthenticationResponseDTO> authenticate (@RequestBody @Valid LdapAuthenticationRequest ldapAuthenticationRequest, HttpServletResponse httpServletResponse){

        //Http only cookie based authentication
        /*AuthenticationResponse response = authService.authenticate(authenticationRequest);

        // Create a secure HTTP-only cookie for the JWT token
        ResponseCookie cookie = ResponseCookie.from("token", response.getToken())
                .httpOnly(true)           // Cannot be accessed via JavaScript
                .secure(false)            // set to true in production (requires HTTPS)
                .path("/")                // Cookie is valid for the entire domain
                .maxAge(24 * 60 * 60)     // 1 day
                .sameSite("Strict")       // Helps mitigate CSRF
                .build();

        // Add the cookie to the response header
        httpServletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        // Optionally, you can exclude the token from the response body for better security
        AuthenticationResponse safeResponse = AuthenticationResponse.builder()
                .message("Login successful")
                .build();

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(safeResponse);---------------------
        //old implementation return the token within the authentication response
        System.out.println(ldapAuthenticationRequest.getUsername());
        System.out.println(ldapAuthenticationRequest.getPassword());
        return ResponseEntity.ok(ldapAuthenticationService.authenticate(ldapAuthenticationRequest));
    }*/
    /*@GetMapping("/activate-account")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<AuthenticationResponseDTO> activateAccount(@RequestParam String token){
        authService.activateAccount(token);
        return ResponseEntity.accepted().build();
    }*/




}
