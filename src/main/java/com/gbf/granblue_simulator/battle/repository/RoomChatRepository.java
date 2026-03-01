package com.gbf.granblue_simulator.battle.repository;

import com.gbf.granblue_simulator.battle.domain.RoomChat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomChatRepository extends JpaRepository<RoomChat, Long> {

    // 초기 로드
    List<RoomChat> findTop20ByRoomIdOrderByCreatedAtDesc(Long roomId);

    List<RoomChat> findByRoomIdAndIdGreaterThanOrderByCreatedAtAsc(Long roomId, Long lastId);
}
