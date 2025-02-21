package com.gbf.granblue_simulator.logic.actor.enemy;

import com.gbf.granblue_simulator.domain.actor.Enemy;
import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleEnemy;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.omen.Omen;
import com.gbf.granblue_simulator.domain.move.prop.omen.OmenCancelCond;
import com.gbf.granblue_simulator.domain.move.prop.omen.OmenType;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffectType;
import com.gbf.granblue_simulator.logic.actor.ActorLogicUtil;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.common.*;
import com.gbf.granblue_simulator.logic.common.dto.DamageLogicResult;
import com.gbf.granblue_simulator.service.BattleLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

@Component
@RequiredArgsConstructor
@Slf4j
public class DiasporaLogic implements EnemyLogic {

    private final StatusUtil statusUtil;
    private final ActorLogicUtil actorLogicUtil;
    private final DamageLogic damageLogic;
    private final EnemyLogicResultMapper enemyLogicResultMapper;
    private final ChargeGaugeLogic chargeGaugeLogic;
    private final SetStatusLogic setStatusLogic;
    private final BattleLogService battleLogService;
    private final OmenLogic omenLogic;

    /**
     * 보스의 공격 타겟 결정후 반환 (전체공격의 경우 partyMembers 그대로 사용하면 됨)
     * 적용효과 : 감싸기
     *
     * @param hitCount
     * @param partyMembers
     * @return
     */
    protected List<BattleActor> getTargets(boolean isAllTarget, int hitCount, List<BattleActor> partyMembers) {
        // 감싸기 효과 적용 확인
        Optional<BattleStatus> substituteEffect = statusUtil.getEffectiveCoveringEffect(partyMembers, StatusEffectType.SUBSTITUTE);
        return substituteEffect // 감싸기 있으면 해당 actor id 로 전부, 아니면 랜덤으로 id 채워 반환
                .map(battleStatus -> Collections.nCopies(hitCount, battleStatus.getBattleActor()))
                .orElseGet(() -> isAllTarget ?
                        partyMembers :
                        IntStream.range(0, hitCount)
                                .mapToObj(i -> partyMembers.get((int) (Math.random() * partyMembers.size())))
                                .toList());
    }

    @Override
    public ActorLogicResult attack(BattleActor mainActor, List<BattleActor> partyMembers) {
        Move attackMove = actorLogicUtil.determineAttackMove(mainActor);
        // 타겟
        List<BattleActor> targets = getTargets(attackMove.isAllTarget(), attackMove.getHitCount(), partyMembers);
        // 데미지
        DamageLogicResult damageLogicResult = damageLogic.processEnemy(mainActor, targets, attackMove);
        // 차지턴
        chargeGaugeLogic.afterEnemyAttack(mainActor, attackMove.getType());

        return enemyLogicResultMapper.toResult(mainActor, targets, attackMove, damageLogicResult);
    }

    @Override
    public ActorLogicResult secondAbility(BattleActor mainActor, List<BattleActor> partyMembers) {
        return null;
    }

    @Override
    public ActorLogicResult thirdAbility(BattleActor mainActor, List<BattleActor> partyMembers) {
        return null;
    }

    @Override
    public ActorLogicResult chargeAttack(BattleActor mainActor, List<BattleActor> partyMembers) {
        BattleEnemy enemy = (BattleEnemy) mainActor;
        Move standByMove = enemy.getActor().getMoves().get(enemy.getNextStandbyType());
        Omen omen = standByMove.getOmen();
        Move chargeAttack = enemy.getActor().getMoves().get(standByMove.getType().getChargeAttackType());
        // 타겟
        List<BattleActor> targets = getTargets(chargeAttack.isAllTarget(), chargeAttack.getHitCount(), partyMembers);
        // 데미지
        DamageLogicResult damageLogicResult = damageLogic.processEnemy(mainActor, targets, chargeAttack);
        // 차지턴 (차지어택의 경우 초기화, 아니면 그대로)
        if (omen.getOmenType() == OmenType.CHARGE_ATTACK)
            chargeGaugeLogic.afterEnemyAttack(mainActor, chargeAttack.getType());
        // 스탠바이 초기화
        enemy.setNextStandbyType(null);
        return enemyLogicResultMapper.toResult(mainActor, targets, chargeAttack, damageLogicResult);
    }

    @Override
    public ActorLogicResult firstSupportAbility(BattleActor mainActor, List<BattleActor> partyMembers) {
        return null;
    }

    @Override
    public ActorLogicResult secondSupportAbility(BattleActor mainActor, List<BattleActor> partyMembers) {
        return null;
    }

    @Override
    public ActorLogicResult thirdSupportAbility(BattleActor mainActor, List<BattleActor> partyMembers) {
        return null;
    }

    @Override
    public ActorLogicResult postProcessOtherMove(BattleActor mainActor, List<BattleActor> partyMembers) {
        return null;
    }

    @Override
    public ActorLogicResult postProcessEnemyMove(BattleActor mainActor, List<BattleActor> partyMembers) {
        return null;
    }

    @Override
    public ActorLogicResult onBattleStart(BattleActor mainActor, List<BattleActor> partyMembers) {
        setStatusLogic.initStatus(mainActor);

        Move firstSupportAbility = mainActor.getActor().getMoves().get(MoveType.FIRST_SUPPORT_ABILITY);
        setStatusLogic.setStatus(mainActor, mainActor, partyMembers, firstSupportAbility);

        return enemyLogicResultMapper.toResult(mainActor, partyMembers, firstSupportAbility, null);
    }

    @Override
    public List<ActorLogicResult> onOtherMove(BattleActor mainActor, List<BattleActor> partyMembers, ActorLogicResult otherResult) {
        BattleEnemy enemy = (BattleEnemy) mainActor;
        Enemy enemyActor = (Enemy) enemy.getActor();
        List<ActorLogicResult> results = new ArrayList<>();

        if (enemy.getNextStandbyType() != null) {
            Move standbyMove = enemy.getActor().getMoves().get(enemy.getNextStandbyType());
            Omen standbyOmen = standbyMove.getOmen();
            Integer processedOmenValue = processOmen(enemy, otherResult);
            if (processedOmenValue == 0) {
                // 전조 해제
                Move breakMove = enemy.getActor().getMoves().get(standbyMove.getType().getBreakType());
                enemy.setNextStandbyType(null);
                if (standbyOmen.getOmenType() == OmenType.CHARGE_ATTACK) enemy.setChargeGauge(0);
                results.add(enemyLogicResultMapper.toResult(mainActor, partyMembers, breakMove));
            } else {
                results.add(enemyLogicResultMapper.toResultWithOmenValue(mainActor, partyMembers, standbyMove, processedOmenValue));
            }
        }


        // 활성버프 =========================================
        List<BattleStatus> activateStatuses = statusUtil.getUniqueStatuses(mainActor, "활성");
        if (!activateStatuses.isEmpty()) {
            // 활성 버프 붙어있음
            Optional<BattleStatus> activatedStatus = activateStatuses.stream().filter(status -> status.getLevel().equals(status.getStatus().getMaxLevel())).findFirst();
            if (activatedStatus.isPresent()) {
                // 활성 레벨 10 있음
                enemy.setNextStandbyType(MoveType.STANDBY_D); // 다음 오의 긴급 회복 시스템
            } else {
                // 활성레벨 10 없음
                List<Integer> damages = otherResult.getDamages();
                if (!damages.isEmpty()) {
                    // 다른 캐릭터의 행동결과로 데미지 발생
                    MoveType otherMoveType = otherResult.getMoveType();
                    Integer takenDamageSum = battleLogService.getTakenDamageSumByMoveType(mainActor, otherMoveType);
                    String activeStatusName = "";
                    if (otherMoveType.getParentType() == MoveType.ATTACK) activeStatusName = "알파";
                    else if (otherMoveType.getParentType() == MoveType.ABILITY) activeStatusName = "베타";
                    else if (otherMoveType.getParentType() == MoveType.CHARGE_ATTACK) activeStatusName = "감마";
                    final String statusName = activeStatusName;
                    Optional<BattleStatus> activeStatusOptional = statusUtil.getUniqueStatus(mainActor, activeStatusName);
                    if (activeStatusOptional.isPresent()) {
                        BattleStatus activeStatus = activeStatusOptional.get();
                        if (takenDamageSum / 300000 > activeStatus.getLevel()) {
                            int increasingLevel = takenDamageSum / 300000 - activeStatus.getLevel();
                            for (int i = 1; i <= increasingLevel; i++) {
                                // 증가량 만큼 스테이터스 set, 해당하는 레벨의 스테이터스만 적용시킴
                                setStatusLogic.setStatus(mainActor, mainActor, partyMembers, List.of(activeStatus.getStatus()));
                            }
                            statusUtil.addUniqueStatusLevel(mainActor, takenDamageSum / 300000 - activeStatus.getLevel(), statusName);
                            Move firstSupportAbility = mainActor.getActor().getMoves().get(MoveType.FIRST_SUPPORT_ABILITY);
                            results.add(enemyLogicResultMapper.toResultWithStatus(mainActor, partyMembers, firstSupportAbility, List.of(activeStatus.getStatus())));
                        }
                    }
                }
            }
        }
        return results; // 반환없음
    }

    @Override
    public ActorLogicResult onTurnEnd(BattleActor mainActor, List<BattleActor> partyMembers) {
        BattleEnemy enemy = (BattleEnemy) mainActor;
        Enemy enemyActor = (Enemy) enemy.getActor();

        // 전조발생
        Move standbyMove = determineStandbyMove(enemy);
        if (standbyMove != null) {
            // 전조 발생함
            return enemyLogicResultMapper.toResult(enemy, partyMembers, standbyMove);
        }
        return null;
    }

    protected Move determineStandbyMove(BattleEnemy enemy) {
        Enemy enemyActor = (Enemy) enemy.getActor();
        Move standby = null;
        // hp 트리거
        Optional<Omen> hpTriggerOmen = omenLogic.getTriggeredOmen(enemy, OmenType.HP_TRIGGER);

        if (enemy.getNextStandbyType() != null) {
            // 영창기 (로직내부에서 발동설정)
            standby = enemy.getActor().getMoves().get(enemy.getNextStandbyType());
        } else if (hpTriggerOmen.isPresent() && !hpTriggerOmen.get().getTriggerHp().equals(enemy.getLatestTriggeredHp())) {
            log.warn("HPTRIGGER rate = {}, target = {}", enemy.getHpRateInteger(), hpTriggerOmen.get().getTriggerHp());
            // HP 트리거
            Omen triggeredOmen = hpTriggerOmen.get();
            standby = enemy.getActor().getMoves().get(triggeredOmen.getMove().getType());
            enemy.setNextStandbyType(standby.getType());
            enemy.setLatestTriggeredHp(triggeredOmen.getTriggerHp());
        } else if (enemy.getChargeGauge() >= enemy.getActor().getMaxChargeGauge()) {
            log.warn("CHARGEATTACK");
            // 차지어택
            Optional<Omen> triggeredOmenOptional = omenLogic.getTriggeredOmen(enemy, OmenType.CHARGE_ATTACK);
            if (triggeredOmenOptional.isPresent()) {
                standby = enemy.getActor().getMoves().get(triggeredOmenOptional.get().getMove().getType());
                enemy.setNextStandbyType(standby.getType());
            }
        }
        log.info("\n\n\n determineStandbyMove, hprate = {}, move = {}, chargeTurn = {}, maxCT = {}", enemy.getHpRateInteger(), standby, enemy.getChargeGauge(), enemy.getActor().getMaxChargeGauge());
        if (standby == null) return null;

        // 스탠바이의 전조 결정
        List<OmenCancelCond> omenCancelConds = standby.getOmen().getOmenCancelConds();
        OmenCancelCond omenCancelCond = omenCancelConds.get((int) (Math.random() * omenCancelConds.size()));
        // 전조 해제 조건 설정
        enemy.setOmenCancelCondIndex(omenCancelConds.indexOf(omenCancelCond));
        enemy.setOmenValue(omenCancelCond.getInitValue());

        return standby;
    }

    // onOtherMove 에서 실행될 전조처리
    protected Integer processOmen(BattleEnemy enemy, ActorLogicResult otherResult) {
        Move standbyMove = enemy.getActor().getMoves().get(enemy.getNextStandbyType());
        Omen omen = standbyMove.getOmen();
        OmenCancelCond omenCancelCond = omen.getOmenCancelConds().get(enemy.getOmenCancelCondIndex());
        int omenValue = enemy.getOmenValue();
        Integer processedOmenValue = omenLogic.process(enemy, omenCancelCond, otherResult);
        log.info("\n\n\nomenvaleu = {} , processedOmenvalue = {}", omenValue, processedOmenValue);

        // REMIND standby 프론트로 넘겨주고 프론트에서 standby 재생중이면 값만 처리하도록
        return processedOmenValue;
    }
}

