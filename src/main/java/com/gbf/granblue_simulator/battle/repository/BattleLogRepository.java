package com.gbf.granblue_simulator.battle.repository;

import com.gbf.granblue_simulator.battle.domain.BattleLog;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BattleLogRepository extends JpaRepository<BattleLog, Long> {


    /**
     * 특정 폼의 적을 타겟으로한 참전자 전체의 아군전체 로그 반환<br>
     * 디아스포라1 서포어비 데미지 누계계산용
     *
     * @param roomId
     * @param enemyActorBaseId 찾을 적 baseId
     * @return
     */
    List<BattleLog> findAllByRoomIdAndEnemyActorBaseIdAndParentMoveTypeIn(Long roomId, Long enemyActorBaseId, List<MoveType> parentMoveTypes);


    /**
     * 특정 폼의 적을 타겟으로한 개인의 아군전체 로그 반환<br>
     * 디아스포라2 서포어비 데미지 누계계산용
     *
     * @param roomId
     * @param userId
     * @param enemyBaseId 찾을 적 baseId
     * @return
     */
    List<BattleLog> findAllByRoomIdAndUserIdAndEnemyActorBaseIdAndParentMoveTypeIn(Long roomId, Long userId, Long enemyBaseId, List<MoveType> parentMoveType);

    /**
     * 적 (모든 폼) 을 타겟으로한 자신의 아군전체 로그 반환
     *
     * @param roomId
     * @param userId
     * @return
     */
    List<BattleLog> findByRoomIdAndUserIdAndEnemyActorBaseIdNotNull(Long roomId, Long userId);

    /**
     * 해당 방의 마지막 로그 가져오기
     *
     * @param roomId
     * @return
     */
    Optional<BattleLog> findFirstByRoomIdOrderByIdDesc(Long roomId);

}