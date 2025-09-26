package com.matjom.matjom.common.security;

import com.matjom.matjom.common.ratelimit.RateLimitFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.FormLoginConfigurer;
import org.springframework.security.config.annotation.web.configurers.HttpBasicConfigurer;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final RateLimitFilter rateLimitFilter;

    public SecurityConfig(RateLimitFilter rateLimitFilter) {
        this.rateLimitFilter = rateLimitFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(new DisableCsrf());
        http.formLogin(new DisableFormLogin());
        http.httpBasic(new DisableHttpBasic());
        http.authorizeHttpRequests(new PermitAllRequests());
        http.addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    private static final class DisableCsrf implements Customizer<CsrfConfigurer<HttpSecurity>> {
        @Override
        public void customize(CsrfConfigurer<HttpSecurity> configurer) {
            configurer.disable();
        }
    }

    private static final class DisableFormLogin implements Customizer<FormLoginConfigurer<HttpSecurity>> {
        @Override
        public void customize(FormLoginConfigurer<HttpSecurity> configurer) {
            configurer.disable();
        }
    }

    private static final class DisableHttpBasic implements Customizer<HttpBasicConfigurer<HttpSecurity>> {
        @Override
        public void customize(HttpBasicConfigurer<HttpSecurity> configurer) {
            configurer.disable();
        }
    }

    private static final class PermitAllRequests implements Customizer<AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry> {
        @Override
        public void customize(AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry registry) {
            registry.anyRequest().permitAll();
        }
    }
}
