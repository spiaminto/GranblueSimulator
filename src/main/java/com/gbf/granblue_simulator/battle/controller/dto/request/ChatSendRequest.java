package com.gbf.granblue_simulator.battle.controller.dto.request;

import com.gbf.granblue_simulator.battle.domain.ChatStamp;
import com.gbf.granblue_simulator.battle.domain.ChatType;
import lombok.Data;

@Data
public class ChatSendRequest {
    private ChatType type;
    private String content;
    private ChatStamp chatStamp;
}
