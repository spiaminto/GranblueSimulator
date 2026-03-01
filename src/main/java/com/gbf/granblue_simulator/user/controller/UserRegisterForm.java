package com.gbf.granblue_simulator.user.controller;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UserRegisterForm {

    @NotBlank(message = "닉네임을 입력해주세요")
    @Size(max = 8, message = "닉네임은 최대 8자까지 입력 가능합니다")
    @Pattern(regexp = "^[a-zA-Z0-9가-힣]+$", message = "닉네임은 한글, 영문, 숫자만 입력 가능합니다")
    private String username;

    @NotBlank(message = "아이디를 입력해주세요")
    @Size(max = 8, message = "아이디는 최대 8자까지 입력 가능합니다")
    @Pattern(regexp = "^[a-zA-Z0-9]*$", message = "아이디는 영문, 숫자만 입력 가능합니다")
    private String loginId;

    @NotBlank(message = "비밀번호를 입력해주세요")
    @Size(min = 4, max = 16, message = "비밀번호는 4자 이상 16자 이하로 입력해주세요")
    private String password;
}
