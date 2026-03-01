package com.gbf.granblue_simulator.battle.service;

import com.gbf.granblue_simulator.battle.domain.Room;
import com.gbf.granblue_simulator.battle.domain.RoomStatus;
import com.gbf.granblue_simulator.battle.repository.RoomRepository;
import com.gbf.granblue_simulator.user.domain.User;
import com.gbf.granblue_simulator.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RoomService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;

    public List<Room> findAll() {
        return roomRepository.findAll();
    }

    public List<Room> findAllNotHidden() {
        return roomRepository.findAllByIsHiddenIs(false);
    }

    public List<Room> findActiveRooms() {
        return roomRepository.findAllByRoomStatus(RoomStatus.ACTIVE);
    }

    public Room findById(Long id) {
        return roomRepository.findById(id).orElse(null);
    }

    public Room enterTutorialRoom(Long userId) {
        List<Room> tutorialRooms = roomRepository.findByOwnerIdAndRoomStatus(userId, RoomStatus.TUTORIAL);
        if (tutorialRooms.size() > 1) {
            roomRepository.deleteAll(tutorialRooms); // 이상상황. 기존 방 전부 삭제후 시작
        }
        if (tutorialRooms.isEmpty()) {
            User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("튜토리얼 방 생성 에러: userId 없음 userId = " + userId));
            Room room = Room.builder()
                    .roomStatus(RoomStatus.TUTORIAL)
                    .ownerId(user.getId())
                    .ownerUsername(user.getUsername())
                    .enemyBaseId(10200L)
                    .info("")
                    .maxUserCount(1)
                    .enterUserCount(0) // member.enterRoom 에서 변경
                    .isHidden(true) // 나중에 파라미터로
                    .build();
            return roomRepository.save(room);
        } else {
            return tutorialRooms.getFirst();
        }
    }

    public Room addRoom(Long ownerUserId, String message) {
        User user = userRepository.findById(ownerUserId).orElseThrow(() -> new IllegalArgumentException("owner 가 조회되지 않음"));
        Room room = Room.builder()
                .roomStatus(RoomStatus.ACTIVE)
                .ownerId(user.getId())
                .ownerUsername(user.getUsername())
                .enemyBaseId(10000L)
                .info(message)
                .maxUserCount(3)
                .enterUserCount(0) // member.enterRoom 에서 변경
                .isHidden(false) // 나중에 파라미터로
                .build();
        roomRepository.save(room);
        return room;
    }

    /**
     * 직접 삭제시 사용
     */
    public void deleteRoom(Long roomId) {
        roomRepository.deleteById(roomId);
    }

    /**
     * 새로은 트랜잭션으로 unionSummonId 강제 업데이트 (Exception 롤백 대응)
     * @param unionSummonId 리셋시 null
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void forceUpdateUnionSummonId(Long roomId, Long unionSummonId) {
        roomRepository.updateUnionSummonIdById(roomId, unionSummonId);
    }

}
