package com.gbf.granblue_simulator.battle.service;

import com.gbf.granblue_simulator.battle.controller.dto.request.ChatSendRequest;
import com.gbf.granblue_simulator.battle.controller.dto.response.ChatResponse;
import com.gbf.granblue_simulator.battle.domain.ChatType;
import com.gbf.granblue_simulator.battle.domain.RoomChat;
import com.gbf.granblue_simulator.battle.exception.ChatException;
import com.gbf.granblue_simulator.battle.repository.RoomChatRepository;
import com.gbf.granblue_simulator.user.domain.User;
import com.gbf.granblue_simulator.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Service
@Transactional
@Slf4j
@RequiredArgsConstructor
public class RoomChatService {

    private final RoomChatRepository chatRepository;
    private final UserRepository userRepository;

    public List<ChatResponse> getChats(Long roomId, Long after) {
        List<RoomChat> chats;
        if (after == null) {
            chats = chatRepository.findTop20ByRoomIdOrderByCreatedAtDesc(roomId);
            Collections.reverse(chats); // 렌더링 편의를 위해 뒤집음
        } else {
            chats = chatRepository.findByRoomIdAndIdGreaterThanOrderByCreatedAtAsc(roomId, after);
        }

        List<ChatResponse> results =  chats.stream().map(ChatResponse::from).toList();
        return results;
    }

    public ChatResponse save(Long roomId, Long userId, ChatSendRequest request) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("없는 유저입니다."));
        if (request.getType() == ChatType.TEXT && (request.getContent() == null || request.getContent().isBlank()))
            throw new ChatException("내용이 없습니다.");
        if (request.getType() == ChatType.STAMP && (request.getChatStamp() == null))
            throw new ChatException("스탬프 내용이 없습니다.");
        if (LocalDateTime.now().isBefore(user.getLastChatTime().plusSeconds(3)))
            throw new ChatException("채팅은 3초에 1번씩만 가능합니다.");

        RoomChat chat = RoomChat.builder()
                .roomId(roomId)
                .userId(user.getId())
                .username(user.getUsername())
                .type(request.getType())
                .content(request.getContent())
                .stamp(request.getChatStamp())
                .build();
        user.updateLastChatTime(LocalDateTime.now());

        return ChatResponse.from(chatRepository.save(chat));
    }

}
