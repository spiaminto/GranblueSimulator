package com.gbf.granblue_simulator.battle.service;

import com.gbf.granblue_simulator.battle.domain.Member;
import com.gbf.granblue_simulator.battle.domain.Room;
import com.gbf.granblue_simulator.battle.domain.RoomStatus;
import com.gbf.granblue_simulator.battle.exception.MoveProcessingException;
import com.gbf.granblue_simulator.battle.repository.RoomRepository;
import com.gbf.granblue_simulator.metadata.domain.Raid;
import com.gbf.granblue_simulator.metadata.domain.RaidType;
import com.gbf.granblue_simulator.metadata.repository.RaidRepository;
import com.gbf.granblue_simulator.user.domain.User;
import com.gbf.granblue_simulator.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RoomService {

    private final RoomRepository roomRepository;
    private final UserRepository userRepository;
    private final RaidRepository raidRepository;

    public List<Room> findAll() {
        return roomRepository.findAll();
    }

    public List<Room> findAllNotHidden() {
        return roomRepository.findAllByIsHiddenIs(false);
    }

    public List<Room> findActiveRooms() {
        return roomRepository.findAllByRoomStatus(RoomStatus.ACTIVE);
    }

    public Optional<Room> findById(Long id) {
        return roomRepository.findById(id);
    }

    public Room enterTutorialRoom(Long userId) {
        List<Room> tutorialRooms = roomRepository.findByOwnerIdAndRoomStatus(userId, RoomStatus.TUTORIAL);
        if (tutorialRooms.size() > 1) {
            roomRepository.deleteAll(tutorialRooms); // 이상상황. 기존 방 전부 삭제후 시작
        }
        if (tutorialRooms.isEmpty()) {
            User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("튜토리얼 방 생성 에러: userId 없음 userId = " + userId));
            final Long tutorialRaidId = 10200L;
            Raid raid = raidRepository.findById(tutorialRaidId).orElseThrow(() -> new IllegalStateException("레이드 id 가 잘못되었습니다. raidId = " + tutorialRaidId));
            Room room = Room.builder()
                    .roomStatus(RoomStatus.TUTORIAL)
                    .raid(raid)
                    .ownerId(user.getId())
                    .ownerUsername(user.getUsername())
                    .currentEnemyBaseId(10200L)
                    .currentFormOrder(1)
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

    public Room addRoom(Long ownerUserId, Long raidId, String message) {
        User user = userRepository.findById(ownerUserId).orElseThrow(() -> new IllegalStateException("유효하지 않은 사용자입니다."));
        long selfOwnerRoomCount = user.getMembers().stream()
                .filter(member -> member.getRoom().getRoomStatus() == RoomStatus.ACTIVE && member.getRoom().getOwnerId().equals(ownerUserId))
                .count();
        if (selfOwnerRoomCount > 0) throw new IllegalStateException("동시에 1개의 방만 생성 가능합니다.");
        LocalDateTime latestRoomCreatedAt = user.getMembers().stream()
                .filter(member -> member.getRoom().getOwnerId().equals(user.getId()) && member.getRoom().getRaid().getType() != RaidType.TUTORIAL && member.getRoom().isFinished()) // 튜토리얼이 아닌, 종료된 방
                .max(Comparator.comparing((Member member) -> member.getRoom().getCreatedAt()))
                .map(member -> member.getRoom().getCreatedAt()).orElse(LocalDateTime.MIN);
        long betweenSeconds = Duration.between(latestRoomCreatedAt, LocalDateTime.now()).toSeconds();
        // TEST
        if (betweenSeconds < 120) throw new IllegalStateException(120 - betweenSeconds + "초 후에 생성 가능합니다.");

        final Long hexachromaticRaidId = 10300L;
        final Long diasporaRaidId = 10000L;
        if (hexachromaticRaidId.equals(raidId)
                && user.getMembers().stream().noneMatch(member ->
                diasporaRaidId.equals(member.getRoom().getRaid().getId()) && member.getRoom().getRoomStatus() == RoomStatus.CLEARED)) {
            throw new IllegalStateException("디아스포라HL 레이드를 1번이상 클리어 해야 도전가능합니다.");
        }

        Raid raid = raidRepository.findById(raidId).orElseThrow(() -> new IllegalStateException("레이드 id 가 잘못되었습니다. raidId = " + raidId));
        Room room = Room.builder()
                .roomStatus(RoomStatus.ACTIVE)
                .raid(raid)
                .ownerId(user.getId())
                .ownerUsername(user.getUsername())
                .currentEnemyBaseId(raid.getFirstBaseEnemyId())
                .currentFormOrder(1)
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
     * 방의 상태를 시간초과로 변경, throw 와 같이쓰여 롤백 대응
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void timeoutRoom(Long roomId) {
        Room room = roomRepository.findById(roomId).orElseThrow(() -> new MoveProcessingException("시간 초과 처리중 에러발생"));
        if (room.getRoomStatus() == RoomStatus.FAILED_TIMEOUT && room.getEndedAt() != null) return;
        roomRepository.updateStatusAndEndedAtById(roomId, RoomStatus.FAILED_TIMEOUT, LocalDateTime.now());
    }

    /**
     * 새로은 트랜잭션으로 unionSummonId 강제 업데이트 (Exception 롤백 대응)
     *
     * @param unionSummonId 리셋시 null
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void forceUpdateUnionSummonId(Long roomId, Long unionSummonId) {
        roomRepository.updateUnionSummonIdById(roomId, unionSummonId);
    }

}
