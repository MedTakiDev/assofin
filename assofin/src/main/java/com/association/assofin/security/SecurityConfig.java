package com.association.assofin.security;

import com.association.assofin.security.handlers.RestAccessDeniedHandler;
import com.association.assofin.security.handlers.RestAuthenticationEntryPoint;
import com.association.assofin.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import static org.springframework.security.config.http.SessionCreationPolicy.STATELESS;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfig {
    private final JwtAuthenticationFilter jwtAuthFilter;
    //private final AuthenticationProvider authenticationProvider;
    private final AuthenticationManager authenticationManager;

    private final CorsProperties corsProperties;


    private final RestAuthenticationEntryPoint authenticationEntryPoint;

    private final RestAccessDeniedHandler accessDeniedHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(req ->
                        req.requestMatchers(
                                        // Servlet error dispatch: without this, an error raised outside
                                        // MVC (e.g. in a filter) is re-authorized and hidden behind a 401.
                                        "/error",
                                        "/auth/login",
                                        "/auth/logout",
                                        "/ldap/**",
                                        "/v2/api-docs",
                                        "/v3/api-docs",
                                        "/v3/api-docs/**",
                                        "/swagger-resources",
                                        "/swagger-resources/**",
                                        "/configuration/ui",
                                        "/configuration/security",
                                        "/swagger-ui/**",
                                        "/webjars/**",
                                        "/swagger-ui.html").permitAll()
                                .anyRequest().authenticated()
                )
                // ---------------------------------------------------------
                // EXCEPTION HANDLING
                // ---------------------------------------------------------
                .exceptionHandling(exception ->
                        exception
                                .authenticationEntryPoint(authenticationEntryPoint)
                                .accessDeniedHandler(accessDeniedHandler)
                )

                // ---------------------------------------------------------
                // SESSION
                // ---------------------------------------------------------
                .sessionManagement((session) -> session.sessionCreationPolicy(STATELESS))
                // ---------------------------------------------------------
                // AUTHENTICATION MANAGER
                // ---------------------------------------------------------
                //.authenticationProvider(authenticationProvider)//we can use the AuthenticationManager Bean in the bottom to avoid hard coded authentication provider wiring
                .authenticationManager(authenticationManager)
                // ---------------------------------------------------------
                // APPEND JWT FILTER
                // ---------------------------------------------------------
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Allow Angular origin
        configuration.setAllowedOrigins(corsProperties.getAllowedOrigins());
        // Allow HTTP Methods
        configuration.setAllowedMethods(corsProperties.getAllowedMethods()); // might use the cors properties class to refactor the hard coded section

        // Allow headers (Authorization, Content-Type, etc.)
        configuration.setAllowedHeaders(corsProperties.getAllowedHeaders());

        // Expose headers to Angular if needed
        configuration.setExposedHeaders(corsProperties.getExposedHeaders());

        // Allow credentials (cookies / auth headers)
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

}
