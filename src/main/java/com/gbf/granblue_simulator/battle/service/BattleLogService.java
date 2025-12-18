package com.gbf.granblue_simulator.battle.service;

import com.gbf.granblue_simulator.battle.domain.BattleLog;
import com.gbf.granblue_simulator.battle.domain.Member;
import com.gbf.granblue_simulator.metadata.domain.move.Move;
import com.gbf.granblue_simulator.metadata.domain.actor.ElementType;
import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.battle.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.battle.repository.BattleLogRepository;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 배틀로그를 저장 및 필요정보 집계후 반환 <br>
 * 필요 정보 집계는, 되도록 해당 case 에 명확하게 맞춰서 작성, 쿼리 여러번 안나가게.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class BattleLogService {

    private final BattleLogRepository battleLogRepository;
    private final BattleContext battleContext;


    /**
     * enemy 가 해당 방에서 모든 멤버로 부터 받은 데미지를 parentMoveType 을 key 로 해서 합산반환
     * 디아스포라 서포어비에서 사용
     *
     * @param enemy          enemy
     * @param parentMoveType
     * @return
     */
    public int getEnemyTakenDamageSumByMoveType(Actor enemy, MoveType parentMoveType) {
        Long roomId = enemy.getMember().getRoom().getId();

        List<BattleLog> battleLogs = battleLogRepository.findAllByRoomIdAndEnemyActorBaseIdAndParentMoveType(roomId, enemy.getBaseActor().getId(), parentMoveType);

        int damageSum = battleLogs.stream()
                .mapToInt(battleLog -> battleLog.getDamages().stream()
                        .mapToInt(Integer::intValue).sum())
                .sum();
        int additionalDamageSum = 0;
        if (parentMoveType == MoveType.ATTACK) {
            additionalDamageSum = battleLogs.stream()
                    .mapToInt(battleLog ->
                            Arrays.stream(battleLog.getAdditionalDamages())
                                    .flatMapToInt(Arrays::stream).sum()
                    ).sum();
        }
        int totalDamageSum = damageSum + additionalDamageSum;
        log.info("[getEnemyTakenDamageSumByMoveType] moveParentType = {} damageSum = {}, ", parentMoveType, totalDamageSum);
        return totalDamageSum;
    }

    public int getEnemyTakenDamageSumByMember(Member member, Actor enemy) {
        List<BattleLog> battleLogs = battleLogRepository.findByRoomIdAndUserIdAndEnemyActorBaseIdNotNull(member.getRoom().getId(), member.getUser().getId());

        // 데미지 합
        int damageSum = battleLogs.stream()
                .mapToInt(battleLog -> battleLog.getDamages().stream()
                        .mapToInt(Integer::intValue).sum())
                .sum();
        int additionalDamageSum = battleLogs.stream()
                .filter(battleLog -> battleLog.getParentMoveType() == MoveType.ATTACK)
                .mapToInt(battleLog ->
                        Arrays.stream(battleLog.getAdditionalDamages())
                                .flatMapToInt(Arrays::stream).sum()
                ).sum();
        int effectDamageSum = battleLogs.stream()
                .map(BattleLog::getEffectDamages)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();

        int totalDamageSum = damageSum + additionalDamageSum + effectDamageSum;
        return totalDamageSum;
    }

    public void saveBattleLogAll(List<ActorLogicResult> results) {
        results.forEach(this::saveBattleLog);
    }

    @SneakyThrows
    public void saveBattleLog(ActorLogicResult logicResult) {
        Move resultMove = logicResult.getMove();
        if (resultMove.getType().isNone()) return;

        Actor mainActor = logicResult.getMainActor();

        Actor enemy = battleContext.getEnemy();
        Long saveEnemyId = mainActor.isEnemy() ? null : enemy.getBaseActor().getId(); // 적이 mainActor 일때는 null 저장해서 구분하기 쉽게

        List<Integer> damages = logicResult.getDamages();
        List<Integer> effectDamages = logicResult.getEffectDamages()
                .stream().map(damage -> damage == null ? 0 : damage).toList();

        List<String> damageElementTypes = logicResult.getDamageElementTypes().stream()
                .map(ElementType::name)
                .toList();

        int[][] additionalDamages = logicResult.getAdditionalDamages().stream()
                .map(list -> list.stream()
                        .mapToInt(Integer::intValue)
                        .toArray())
                .toArray(int[][]::new);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        List<String> statuses = new ArrayList<>();
        List<String> statusDetails = new ArrayList<>();
        List<String> damageStatusDetails = new ArrayList<>();
        Map<Long, ActorLogicResult.Snapshot> snapshots = logicResult.getSnapshots();
        snapshots.forEach((id, snapshot) -> {
            statuses.add(gson.toJson(snapshot.getStatus()));
            statusDetails.add(gson.toJson(snapshot.getStatusDetails()));
            damageStatusDetails.add(gson.toJson(snapshot.getDamageStatusDetails()));
        });

        battleLogRepository.save(BattleLog.builder()
                .roomId(mainActor.getMember().getRoom().getId())
                .userId(mainActor.getMember().getUser().getId())
                .currentTurn(logicResult.getCurrentTurn())

                .mainActorBaseId(mainActor.getBaseActor().getId())
                .enemyActorBaseId(saveEnemyId)
                .moveType(resultMove.getType())
                .parentMoveType(resultMove.getType().getParentType())

                .hitCount(logicResult.getTotalHitCount())
                .damages(damages)
                .effectDamages(effectDamages)
                .damageElementTypes(damageElementTypes)
                .additionalDamages(additionalDamages)

                .statuses(statuses)
                .statusDetails(statusDetails)
                .damageStatusDetails(damageStatusDetails)
                .build());

    }

}
