package com.gbf.granblue_simulator.battle.controller.dto.response;

import com.gbf.granblue_simulator.battle.domain.ChatStamp;
import com.gbf.granblue_simulator.battle.domain.ChatType;
import com.gbf.granblue_simulator.battle.domain.RoomChat;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChatResponse {
    private Long id;
    private String username;
    private ChatType type;
    private String content;
    private ChatStamp chatStamp;
    private LocalDateTime createdAt;

    public static ChatResponse from(RoomChat c) {
        return ChatResponse.builder()
                .id(c.getId())
                .username(c.getUsername())
                .type(c.getType())
                .content(c.getContent())
                .chatStamp(c.getStamp())
                .createdAt(c.getCreatedAt())
                .build();
    }
}
