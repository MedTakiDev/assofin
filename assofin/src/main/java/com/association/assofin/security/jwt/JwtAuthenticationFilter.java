package com.association.assofin.security.jwt;


import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.WebUtils;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        // ================================================================
        // 1. Extract JWT from HttpOnly cookie
        // ================================================================
        String jwt = extractTokenFromCookie(request);
        // ================================================================
        // 2. No token
        // ================================================================
        if (jwt == null || jwt.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        // ================================================================
        // 3. Process JWT
        // ================================================================
        try {
            String userEmail =
                    jwtService.extractUsername(jwt);
            if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails =userDetailsService.loadUserByUsername(userEmail);
                if (jwtService.isTokenValid(jwt,userDetails)) {

                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities());

                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    SecurityContextHolder.getContext().setAuthentication(authToken);
                }
            }

        } catch (Exception ex) {
            /*
             * JWT is invalid, expired, malformed, etc.
             *
             * Important:
             *
             * DO NOT throw the exception here.
             *
             * DO NOT authenticate the user.
             *
             * Continue the filter chain.
             *
             * If the requested endpoint requires authentication,
             * Spring Security will invoke AuthenticationEntryPoint
             * and return 401.
             */
            SecurityContextHolder.clearContext();
        }
        // ================================================================
        // 4. Continue filter chain
        // ================================================================
        filterChain.doFilter(request, response);
    }


    private String extractTokenFromCookie(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, "token");
        return cookie != null ? cookie.getValue() : null;
    }
}