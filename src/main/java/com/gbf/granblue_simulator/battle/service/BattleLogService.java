package com.gbf.granblue_simulator.battle.service;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.BattleLog;
import com.gbf.granblue_simulator.battle.domain.Member;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.DamageStatusDetails;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusDetails;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.dto.ResultStatusDto;
import com.gbf.granblue_simulator.battle.repository.BattleLogRepository;
import com.gbf.granblue_simulator.metadata.domain.actor.ElementType;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
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
     * enemy 가 해당 방에서 받은 데미지를 parentMoveType 을 key 로 해서 합산반환
     * 디아스포라 서포어비에서 사용
     *
     * @param parentMoveTypes
     * @param isFromAllMember 참전자 전체 여부 (디아스포라 1 인경우 true)
     * @return
     */
    public BattleLogDamageSumDto getEnemyTakenDamageSumByMoveType(List<MoveType> parentMoveTypes, boolean isFromAllMember) {
        Actor enemy = battleContext.getEnemy();
        Member member = battleContext.getMember();
        Long roomId = member.getRoom().getId();
        Long userId = member.getUser().getId();

        log.info("[getEnemyTakenDamageSumByMoveType] startQuery");
        List<BattleLog> battleLogs = isFromAllMember
                ? battleLogRepository.findAllByRoomIdAndEnemyActorBaseIdAndParentMoveTypeIn(roomId, enemy.getBaseActor().getId(), parentMoveTypes)
                : battleLogRepository.findAllByRoomIdAndUserIdAndEnemyActorBaseIdAndParentMoveTypeIn(roomId, userId, enemy.getBaseActor().getId(), parentMoveTypes);
        log.info("[getEnemyTakenDamageSumByMoveType] end Query and Mapping");

        int attackDamageSum = 0;
        int chargeAttackDamageSum = 0;

        for (BattleLog battleLog : battleLogs) {
            for (int damage : battleLog.getDamages()) {
                switch (battleLog.getParentMoveType()) {
                    case ATTACK -> {
                        attackDamageSum += damage;
                        // 추격
                        for (int[] additionalDamage : battleLog.getAdditionalDamages()) {
                            for (int additionalDamageValue : additionalDamage) {
                                attackDamageSum += additionalDamageValue;
                            }
                        }
                    }
                    case CHARGE_ATTACK -> chargeAttackDamageSum += damage;
                    default -> log.warn("[getEnemyTakenDamageSumByMoveType] unsupported moveType, moveType = " + battleLog.getParentMoveType());
                }
            }
        }

        BattleLogDamageSumDto result = new BattleLogDamageSumDto(attackDamageSum, chargeAttackDamageSum);
        log.info("[getEnemyTakenDamageSumByMoveType] moveParentTypes = {} result = {}, ", parentMoveTypes, result);
        return result;
    }

    public int getEnemyTakenDamageSumByMember(Member member) {
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

    public ResultStatusDto getLastEnemyStatus(Member member) {
        return battleLogRepository.findFirstByRoomIdOrderByIdDesc(member.getRoom().getId())
                .flatMap(battleLog -> battleLog.getStatuses().values().stream()
                        .filter(ResultStatusDto::isEnemy).findAny())
                .orElse(null);
    }

    public void saveBattleLogAll(List<MoveLogicResult> results) {
        results.forEach(this::saveBattleLog);
    }

    public void saveBattleLog(MoveLogicResult logicResult) {
        Move resultMove = logicResult.getMove();
        BaseMove baseMove = resultMove.getBaseMove();
        if (baseMove.getType().isNone()) return;

        Actor mainActor = logicResult.getMainActor();

        Actor enemy = battleContext.getEnemy();
        Long saveEnemyId = mainActor.isEnemy() ? null : enemy.getBaseActor().getId(); // 적이 mainActor 일때는 null 저장해서 구분하기 쉽게

        List<Integer> damages = logicResult.getDamages();

        List<String> damageElementTypes = logicResult.getDamageElementTypes().stream().map(ElementType::name).toList();

        int[][] additionalDamages = logicResult.getAdditionalDamages().stream()
                .map(list -> list.stream()
                        .mapToInt(Integer::intValue)
                        .toArray())
                .toArray(int[][]::new);

        // CHECK 나중에 용량 최적화 해야할수있음 key 이름 단축, bytea 압축, key 당 actorOrder 기반 array 로 저장 등의 방법 고려
        Map<Long, ResultStatusDto> statuses = new LinkedHashMap<>();
        Map<Long, StatusDetails> statusDetails = new LinkedHashMap<>();
        Map<Long, DamageStatusDetails> damageStatusDetails = new LinkedHashMap<>();
        List<Integer> effectDamages = new ArrayList<>(Collections.nCopies(5, null));
        Map<Long, MoveLogicResult.Snapshot> snapshots = logicResult.getSnapshots();
        for (MoveLogicResult.Snapshot snapshot : snapshots.values()) {
            statuses.put(snapshot.getActorId(), snapshot.getStatus());
            statusDetails.put(snapshot.getActorId(), snapshot.getStatusDetails());
            damageStatusDetails.put(snapshot.getActorId(), snapshot.getDamageStatusDetails());
            effectDamages.set(snapshot.getCurrentOrder(), snapshot.getEffectDamage() != null ? snapshot.getEffectDamage() : 0);
        }

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
