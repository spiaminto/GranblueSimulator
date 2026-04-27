package com.gbf.granblue_simulator.user.controller;

import com.gbf.granblue_simulator.user.service.UserService;
import com.gbf.granblue_simulator.web.auth.PrincipalDetails;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

@Controller
@Slf4j
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    // 회원가입 처리
    @PostMapping("/user/register")
    public String userRegisterPost(@Valid @ModelAttribute UserRegisterForm form, BindingResult bindingResult, RedirectAttributes redirectAttributes) {

        // Validation 에러
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.userRegisterForm", bindingResult);
            redirectAttributes.addFlashAttribute("userRegisterForm", form);
            return "redirect:/?showRegisterModal=true";
        }

        // 아이디 중복 체크
        if (userService.existsByLoginId(form.getLoginId())) {
            bindingResult.rejectValue("loginId", "error.user.loginId.duplicate", "이미 사용중인 아이디 입니다.");
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.userRegisterForm", bindingResult);
            redirectAttributes.addFlashAttribute("userRegisterForm", form);
            return "redirect:/?showRegisterModal=true";
        }

        // 닉네임 중복체크
        if (userService.existsByUsername(form.getUsername())) {
            bindingResult.rejectValue("username", "error.user.username.duplicate", "이미 사용중인 닉네임 입니다.");
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.userRegisterForm", bindingResult);
            redirectAttributes.addFlashAttribute("userRegisterForm", form);
            return "redirect:/?showRegisterModal=true";
        }

        // 회원가입 처리
        try {
            userService.registerUser(form);
            redirectAttributes.addFlashAttribute("alertMessage", "회원가입이 완료되었습니다. 로그인해주세요.");
            return "redirect:/?needLogin=true";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("registerErrorMessage", "회원가입 중 오류가 발생했습니다. 문의 부탁드립니다.");
            redirectAttributes.addFlashAttribute("userRegisterForm", form);
            return "redirect:/?showRegisterModal=true";
        }
    }

    @DeleteMapping("/users/{userId}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> userDeletePost(@PathVariable Long userId,
                                                              @AuthenticationPrincipal PrincipalDetails principalDetails,
                                                              HttpServletRequest request,
                                                              HttpServletResponse response) {
        if (principalDetails == null || !principalDetails.getUser().getId().equals(userId)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "isSuccess", false,
                    "message", "잘봇된 요청입니다."
            ));
        }

        // 로그아웃
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, auth);

        // 삭제
        userService.deleteUser(userId);

        return ResponseEntity.ok().body(Map.of(
                "isSuccess", true
        ));
    }
}
