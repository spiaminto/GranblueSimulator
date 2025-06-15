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
     * mainActor 가 파라미터로 주어진 moveType 으로 받은 모든 데미지의 합산을 반환
     *
     * @param mainActor
     * @param moveParentType
     * @return
     */
    public Integer getTakenDamageSumByMoveType(BattleActor mainActor, MoveType moveParentType) {
        Long userId = mainActor.getMember().getUser().getId();
        Long roomId = mainActor.getMember().getRoom().getId();
        int damageSum = 0;
        int additionalDamageSum = 0;

        log.info("moveType = {}", moveParentType);
        List<BattleLog> battleLogs = battleLogRepository.findAllByRoomIdAndUserIdAndTargetActorId(roomId, userId, mainActor.getActor().getId());
//        battleLogs.forEach(b -> log.info("[battleLogService] battleLog = {}", b));
        damageSum = battleLogs.stream()
                .filter(battleLog -> battleLog.getMoveType().getParentType() == moveParentType)
                .mapToInt(battleLog -> battleLog.getDamages().stream()
                        .mapToInt(Integer::intValue).sum())
                .sum();
        if (moveParentType == MoveType.ATTACK) {
            additionalDamageSum = battleLogs.stream()
                    .filter(battleLog -> battleLog.getMoveType().getParentType() == moveParentType)
                    .map(battleLog -> Arrays.stream(battleLog.getAdditionalDamages())
                            .map(Arrays::asList)
                            .flatMap(List::stream)
                            .mapToInt(Integer::intValue)
                            .sum())
                    .mapToInt(Integer::intValue)
                    .sum();
        }
        return damageSum + additionalDamageSum;
    }

}
