package com.example.samajconnectbackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/register").permitAll()
                        .requestMatchers("/api/auth/login").permitAll()
                        .requestMatchers("/api/auth/verify-email").permitAll()
                        .requestMatchers("/api/auth/resend-otp").permitAll()
                        .requestMatchers("/api/auth/forgot-password").permitAll()
                        .requestMatchers("/api/auth/reset-password").permitAll()
                        .requestMatchers("/api/samaj/**").permitAll()
                        .requestMatchers("/api/samaj/check/**").permitAll()
                        .requestMatchers("/api/samaj/create").permitAll()
                        .requestMatchers("/api/events/samaj/**").permitAll()
                        .requestMatchers("/api/events/**").permitAll()
                        .requestMatchers("/api/users/**").permitAll()
                        .requestMatchers("/api/events/{eventId}/reactions/stats").permitAll()
                        .requestMatchers("/api/events/{eventId}/reactions").permitAll()
                        .requestMatchers("/api/events/{eventId}/reactions/my-reaction").permitAll()
                        .requestMatchers("/api/users/{userId}").permitAll()
                        .requestMatchers("/api/users/{userId}/profile").permitAll()
                        .requestMatchers(
                                "/api/family-tree/health",
                                "/api/family-tree/relationship-types",
                                "/api/family-tree/relationship-sides"
                        ).permitAll()

                        // Allow GET methods only for these endpoints
                        .requestMatchers(
                                "/api/family-tree/user/**",
                                "/api/family-tree/requests/**",
                                "/api/family-tree/generation/**",
                                "/api/family-tree/side/**",
                                "/api/family-tree/mutual/**"
                        ).permitAll()

                        // Allow POST/PUT/DELETE if desired or secure them
                        .requestMatchers(
                                "/api/family-tree/relationship",
                                "/api/family-tree/search/**",
                                "/api/family-tree/requests/respond"
                        ).permitAll()
                        .requestMatchers(
                                "/api/samaj/{samajId}/members",
                                "/api/samaj/search-members",
                                "/api/samaj/{samajId}/stats"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.asList("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("*"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}