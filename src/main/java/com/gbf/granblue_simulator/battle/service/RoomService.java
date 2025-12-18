package com.gbf.granblue_simulator.battle.service;

import com.gbf.granblue_simulator.battle.domain.Room;
import com.gbf.granblue_simulator.user.User;
import com.gbf.granblue_simulator.battle.repository.RoomRepository;
import com.gbf.granblue_simulator.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class RoomService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    public List<Room> findAll() {
        return roomRepository.findAllByIsHiddenIs(false);
    }

    public Room findById(Long id) {
        return roomRepository.findById(id).orElse(null);
    }

    public Room addRoom(Long ownerUserId, String message) {
        User user = userRepository.findById(ownerUserId).orElseThrow(() -> new IllegalArgumentException("owner 가 조회되지 않음"));
        Room room = Room.builder()
                .ownerId(user.getId())
                .ownerUsername(user.getUsername())
                .enemyActorId(7L)
                .info(message)
                .maxUserCount(3)
                .enterUserCount(0) // member.enterRoom 에서 변경
                .isHidden(false) // 나중에 파라미터로
                .build();
        roomRepository.save(room);
        return room;
    }

}
