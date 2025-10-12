package com.gbf.granblue_simulator.service;

import com.gbf.granblue_simulator.domain.BattleLog;
import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusTargetType;
import com.gbf.granblue_simulator.repository.BattleLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class BattleLogService {

    private final BattleLogRepository battleLogRepository;

    public BattleLog getLatestBattleLog(BattleActor mainActor, StatusTargetType statusTargetType) {
        Long userId = mainActor.getMember().getUser().getId();
        Long roomId = mainActor.getMember().getRoom().getId();


        return null;
    }

    /**
     * enemy 가 해당 방에서 모든 멤버로 부터 받은 데미지 중, moveParentType 에 해당하는 데미지의 누계를 반환
     * 디아스포라 서포어비에서 사용
     *
     * @param mainActor enemy
     * @param moveParentType 
     * @return
     */
    public Integer getEnemyTakenDamageSumByMoveType(BattleActor mainActor, MoveType moveParentType) {
        Long roomId = mainActor.getMember().getRoom().getId();
        int damageSum = 0;
        int additionalDamageSum = 0;

        List<BattleLog> battleLogs = battleLogRepository.findAllByRoomIdAndTargetActorId(roomId, mainActor.getActor().getId());
//        battleLogs.forEach(b -> log.info("[battleLogService] battleLog = {}", b));
        List<BattleLog> filteredByMoveType = battleLogs.stream()
                .filter(battleLog -> battleLog.getMoveType().getParentType() == moveParentType).toList(); // CHECK 어플리케이션 부담 체크

        // 1. moveType 에 따른 기본 데미지 합산
        damageSum = filteredByMoveType.stream()
                .mapToInt(battleLog -> battleLog.getDamages().stream()
                        .mapToInt(Integer::intValue).sum())
                .sum();

        // 2. moveType 이 일반공격인 경우, 추격 데미지 합산
        if (moveParentType == MoveType.ATTACK) {
            additionalDamageSum = filteredByMoveType.stream()
                    .flatMapToInt(battleLog ->
                            Arrays.stream(battleLog.getAdditionalDamages())
                                    .flatMapToInt(Arrays::stream)
                    )
                    .sum();
        }
        log.info("[getEnemyTakenDamageSumByMovetype] moveParentType = {} damageSum = {}, additionalDamageSum ={}", moveParentType, damageSum, additionalDamageSum);
        return damageSum + additionalDamageSum;
    }

}
