package com.gbf.granblue_simulator.auth;
import com.gbf.granblue_simulator.domain.User;
import com.gbf.granblue_simulator.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
/**
 * SpringSecurity 일반 유저 인증 처리 클래스
 */
public class PrincipalDetailsService implements UserDetailsService {
    private final UserService userService;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
//        log.info("loadByUsername, username={}", username);
        Optional<User> findMember = userService.findByUsername(username);

        if (findMember.isPresent()) {
            return new PrincipalDetails(findMember.get());
        } else {
            log.info("[loadUserByUsername] username = {}", username);
            throw new UsernameNotFoundException("해당 유저를 찾을 수 없습니다. username=" + username);
        }
    }

}
