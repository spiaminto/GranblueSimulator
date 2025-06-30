package com.gbf.granblue_simulator._config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

@Configuration
@EnableWebSecurity // 시큐리티 필터 등록
@RequiredArgsConstructor
public class SecurityConfig {

    private final AuthenticationFailureHandler customFailureHandler;
    private final AuthenticationSuccessHandler customSuccessHandler;

    @Bean
    public BCryptPasswordEncoder pwEncoder() { return new BCryptPasswordEncoder(); }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .sessionManagement(session -> session
                        .maximumSessions(1)
                        .expiredUrl("/?needLogin=true")
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/room/**").authenticated()
                        .requestMatchers("/api/**").permitAll() // CHECK 나중에 잠글것
                        .anyRequest().permitAll()
                )
                .formLogin(form -> form
                        .loginPage("/?needLogin=true")
                        .loginProcessingUrl("/login-process")
                        .successHandler(customSuccessHandler) // JSON 으로 내려줌
                        .failureHandler(customFailureHandler) // JSON 으로 내려줌
                )
                .logout(logout -> logout
                        .permitAll()
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                );
        return http.build();
    }

}

