package com.gbf.granblue_simulator.web.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * SimpleUrlAuthenticationFailureHandler 를 상속하여 SpringSecurity 일반 로그인 실패 처리
 */
@Slf4j
@Component
public class CustomAuthenticationFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        log.info("[onAuthenticationFailure] exception={} message={}", exception.getClass(), exception.getMessage());

        String message = switch (exception) {
            case BadCredentialsException e -> "아이디 또는 비밀번호가 맞지 않습니다."; // 자격 증명 실패
            case UsernameNotFoundException e -> "계정이 존재하지 않습니다."; // ID 를 찾을 수 없음
            case InternalAuthenticationServiceException e -> "존재 하지 않는 ID 입니다."; // 내부 인증 서비스 예외 인데, 아이디가 존재하지 않아도 해당 예외가 터짐.
            default -> "시스템 오류로 로그인에 실패하였습니다.";
        };

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> result = new HashMap<>();
        result.put("error", true);
        result.put("message", message);

        objectMapper.writeValue(response.getWriter(), result);
    }
}
