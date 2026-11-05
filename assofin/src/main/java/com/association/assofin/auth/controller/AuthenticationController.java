package com.association.assofin.auth.controller;

import com.association.assofin.auth.dto.request.AuthenticationRequestDTO;
import com.association.assofin.auth.dto.response.AuthenticationResponseDTO;
import com.association.assofin.auth.service.AuthenticationService;
import com.association.assofin.security.cookie.AuthCookieService;
import io.swagger.v3.oas.annotations.tags.Tag;
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
    private final AuthCookieService authCookieService;
    //private final LdapAuthenticationService ldapAuthenticationService;//this is used for ldap authentication

    @PostMapping("/login")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ResponseEntity<AuthenticationResponseDTO> authenticate(@RequestBody @Valid AuthenticationRequestDTO authenticationRequest, HttpServletResponse httpServletResponse) {
        //Http only cookie based authentication
        AuthenticationResponseDTO response = authService.authenticate(authenticationRequest);
        // Create a secure HTTP-only cookie for the JWT token
        ResponseCookie cookie = authCookieService.createAccessTokenCookie(response.token());

        // Add the cookie to the response header
        httpServletResponse.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        // Optionally, you can exclude the token from the response body for better security
        AuthenticationResponseDTO safeResponse = AuthenticationResponseDTO.builder()
                .message("Login successful")
                .build();

        return ResponseEntity
                .status(HttpStatus.ACCEPTED)
                .body(safeResponse);
        //old implementation return the token within the authentication response
        /*
        //TODO: check the user authentication then retrieve the user authorities from the database
        return ResponseEntity.ok(authService.authenticate(authenticationRequest));
        */
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


    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, authCookieService.createClearingCookie().toString());
        return ResponseEntity.ok().build();
    }


}
