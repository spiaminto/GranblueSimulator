package com.gbf.granblue_simulator.web.auth;

import com.gbf.granblue_simulator.user.domain.User;
import com.gbf.granblue_simulator.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * SpringSecurity 일반 유저 인증 처리 클래스
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PrincipalDetailsService implements UserDetailsService {
    private final UserService userService;

    @Override
    public UserDetails loadUserByUsername(String loginId) {
//        log.info("loadByUsername, username={}", username);
        User user = userService.findByLoginId(loginId).orElseThrow(() -> new UsernameNotFoundException("해당 유저를 찾을 수 없습니다. loginId = " + loginId)); // 존재하지 않는 ID
        return new PrincipalDetails(user);
    }

}
