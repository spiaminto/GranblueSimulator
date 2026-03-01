package com.gbf.granblue_simulator.user.controller;

import com.gbf.granblue_simulator.user.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
            redirectAttributes.addFlashAttribute("org.springframework.validation.BindingResult.userAddForm", bindingResult);
            redirectAttributes.addFlashAttribute("userAddForm", form);
            return "redirect:/?showRegisterModal=true";
        }

        // 아이디 중복 체크
        if (userService.existsByLoginId(form.getLoginId())) {
            redirectAttributes.addFlashAttribute("errorMessage", "이미 사용중인 아이디입니다.");
            redirectAttributes.addFlashAttribute("userAddForm", form);
            return "redirect:/?showRegisterModal=true";
        }

        // 회원가입 처리
        try {
            userService.registerUser(form);
            redirectAttributes.addFlashAttribute("message", "회원가입이 완료되었습니다. 로그인해주세요.");
            return "redirect:/?needLogin=true";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "회원가입 중 오류가 발생했습니다.");
            redirectAttributes.addFlashAttribute("userAddForm", form);
            return "redirect:/?showRegisterModal=true";
        }
    }
}
