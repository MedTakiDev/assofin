package com.association.assofin.auth.service;

import com.association.assofin.auth.dto.request.AuthenticationRequestDTO;
import com.association.assofin.auth.dto.response.AuthenticationResponseDTO;
import com.association.assofin.security.jwt.JwtService;
import com.association.assofin.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthenticationService {
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

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


}
