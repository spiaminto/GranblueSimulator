package com.gbf.granblue_simulator.repository;

import com.gbf.granblue_simulator.domain.BattleLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BattleLogRepository extends JpaRepository<BattleLog, Long> {

    List<BattleLog> findAllByRoomIdAndUserId(Long roomId, Long userId);

    /**
     * 직전의 배틀로그를 가져옴
     * @return
     */
    BattleLog findFirstByRoomIdAndUserIdAndMainActorIdNotOrderByIdDesc(Long roomId, Long userId, Long actorId);

    /**
     * 해당 room 의 배틀로그를 targetActorId 로 필터링
     * 디아스포라 서포어비 데미지 누계계산용
     * @param roomId
     * @param targetActorId 적의 actorId, 폼에 주의
     * @return
     */
    List<BattleLog> findAllByRoomIdAndTargetActorId(Long roomId, Long targetActorId);
}
