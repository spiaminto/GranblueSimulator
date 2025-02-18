package com.gbf.granblue_simulator.repository;

import com.gbf.granblue_simulator.domain.BattleLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BattleLogRepository extends JpaRepository<BattleLog, Long> {

    List<BattleLog> findAllByRoomIdAndUserId(Long roomId, Long userId);

    List<BattleLog> findAllByRoomIdAndUserIdAndMainActorIdNot(Long roomId, Long userId, Long actorId);
}
