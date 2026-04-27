package com.gbf.granblue_simulator.web.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class GmailSender {

    private final JavaMailSender javaMailSender; // autowire 못하는거 버그인듯

    @Value("${spring.mail.username}")
    private String from;

    @Value("${spring.mail.to.username}")
    private String to;


    public void sendInquiry(String text) {
        sendMail("그랑블루 시뮬레이터 문의", text);
    }

    public void sendError(String text) {
        sendMail("그랑블루 시뮬레이터 에러", text);
    }

    private void sendMail(String title, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(title);
        message.setText(text);
        javaMailSender.send(message);
    }

}
