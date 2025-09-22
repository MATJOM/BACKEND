package com.matjom.matjom.common.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

public class CustomUserDetails implements UserDetails {

    private final String email;    // 로그인 식별자
    private final String name;     // 사용자 이름
    private final String provider; // LOCAL / GOOGLE

    public CustomUserDetails(String email, String name, String provider) {
        this.email = email;
        this.name = name;
        this.provider = provider;
    }

    // 권한: ROLE_USER 고정
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singleton(() -> "ROLE_USER");
    }

    @Override
    public String getPassword() {
        return null;
    }

    // username은 email로 사용
    @Override
    public String getUsername() {
        return email;
    }

    // getter
    public String getEmail() { return email; }
    public String getName() { return name; }
    public String getProvider() { return provider; }

    // 계정 상태 관련 메서드 (모두 true로 설정)
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
