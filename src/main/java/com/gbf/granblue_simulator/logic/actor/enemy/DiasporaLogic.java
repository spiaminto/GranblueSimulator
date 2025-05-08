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
import com.gbf.granblue_simulator.logic.common.dto.SetStatusResult;
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
        return substituteEffect
                .map(battleStatus -> isAllTarget ?
                        Collections.nCopies(partyMembers.size(), battleStatus.getBattleActor()) : // 전체타겟인 경우 전원분 감싸기 id
                        Collections.nCopies(hitCount, battleStatus.getBattleActor())) // 전체타겟 아닌경우 히트수만큼 감싸기 id
                .orElseGet(() -> isAllTarget ?
                        partyMembers :
                        IntStream.range(0, hitCount)
                                .mapToObj(i -> partyMembers.get((int) (Math.random() * partyMembers.size())))
                                .toList());
    }

    /**
     * 스테이터스 효과는 한번만 들어가므로 중복제거
     *
     * @param targets
     * @return
     */
    protected List<BattleActor> getStatusTargets(List<BattleActor> targets) {
        return targets.stream().distinct().toList();
    }

    @Override
    public ActorLogicResult attack(BattleActor mainActor, List<BattleActor> partyMembers) {
        Move attackMove = actorLogicUtil.determineAttackMove(mainActor);
        // 타겟
        List<BattleActor> targets = getTargets(attackMove.isAllTarget(), attackMove.getHitCount(), partyMembers);
        // 데미지
        DamageLogicResult damageLogicResult = damageLogic.processEnemy(mainActor, targets, attackMove);
        List<Integer> targetOrders = targets.stream().map(BattleActor::getCurrentOrder).toList();
        // 차지턴
        chargeGaugeLogic.afterEnemyAttack(mainActor, targets, damageLogicResult.getDamages(), attackMove.getType());

        return enemyLogicResultMapper.attackToResult(mainActor, partyMembers, attackMove, damageLogicResult, targetOrders);
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
        List<BattleActor> statusTargets = getStatusTargets(targets);
        List<Integer> targetOrders = targets.stream().map(BattleActor::getCurrentOrder).toList();
        // 데미지
        DamageLogicResult damageLogicResult = damageLogic.processEnemy(mainActor, targets, chargeAttack);
        // 스테이터스
        SetStatusResult setStatusResult = setStatusLogic.setStatus(mainActor, mainActor, statusTargets, chargeAttack);
        // 차지턴 (차지어택의 경우 초기화, 아니면 그대로)
        if (omen.getOmenType() == OmenType.CHARGE_ATTACK)
            chargeGaugeLogic.afterEnemyAttack(mainActor, targets, damageLogicResult.getDamages(), chargeAttack.getType());
        // 스탠바이 초기화
        enemy.setNextStandbyType(null);
        return enemyLogicResultMapper.toResult(mainActor, partyMembers, chargeAttack, damageLogicResult, targetOrders, setStatusResult);
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
        SetStatusResult setStatusResult = setStatusLogic.setStatus(mainActor, mainActor, partyMembers, firstSupportAbility.getStatuses());

        return enemyLogicResultMapper.toResult(mainActor, partyMembers, firstSupportAbility, null, null, setStatusResult);
    }

    @Override
    public List<ActorLogicResult> onOtherMove(BattleActor mainActor, List<BattleActor> partyMembers, ActorLogicResult otherResult) {
        BattleEnemy enemy = (BattleEnemy) mainActor;
        Enemy enemyActor = (Enemy) enemy.getActor();
        List<ActorLogicResult> results = new ArrayList<>();

        // 전조처리
        if (enemy.getNextStandbyType() != null) {
            Move standbyMove = enemy.getActor().getMoves().get(enemy.getNextStandbyType());
            Omen standbyOmen = standbyMove.getOmen();
            Integer processedOmenValue = processOmen(enemy, otherResult);
            if (processedOmenValue == 0) {
                // 전조 해제됨
                Move breakMove = enemy.getActor().getMoves().get(standbyMove.getType().getBreakType());
                enemy.setNextStandbyType(null);
                if (standbyOmen.getOmenType() == OmenType.CHARGE_ATTACK) enemy.setChargeGauge(0);
                results.add(enemyLogicResultMapper.toResultWithOmen(mainActor, partyMembers, breakMove, standbyOmen));
            } else {
                // 전조 갱신됨
                results.add(enemyLogicResultMapper.toResultWithOmen(mainActor, partyMembers, standbyMove, standbyMove.getOmen()));
            }
        }


        // 서포어비 1,2
        List<BattleStatus> activateStatuses = statusUtil.getUniqueStatuses(mainActor, "활성");
        if (!activateStatuses.isEmpty()) {
            // 활성 버프 붙어있음
            // 서포어비2 - 활성레벨 최고레벨(10) 잇을 시 다음 행동을 긴급회복시스템으로 변화
            activateStatuses.stream()
                    .filter(battleStatus -> battleStatus.getLevel().equals(battleStatus.getStatus().getMaxLevel()))
                    .findFirst()
                    .ifPresent(battleStatus -> enemy.setNextStandbyType(MoveType.STANDBY_D));

            // 서포어비1 - 타 활성레벨 최고레벨과 관계없이 활성레벨링 자체는 독립적으로 수행
            List<Integer> damages = otherResult.getDamages();
            MoveType otherMoveType = otherResult.getMoveType();
            if (!damages.isEmpty()) { // 적의 행동에 데미지 발생
                String matchingStatusName = // 적의 공격 타입에 따른 스테이터스 매칭
                        otherMoveType.getParentType() == MoveType.ATTACK ? "알파" :
                                otherMoveType.getParentType() == MoveType.ABILITY ? "베타" :
                                        otherMoveType.getParentType() == MoveType.CHARGE_ATTACK ? "감마" : "해당 스테이터스 없음";
                if (!matchingStatusName.equals("해당 스테이터스 없음")) { // 공격, 어빌리티, 오의가 아니면 패스
                    Integer takenDamageSum = battleLogService.getTakenDamageSumByMoveType(mainActor, otherMoveType);
                    // 타입에 따라 매칭된 배틀 스테이터스 (활성 버프는 3개가 세트로 전투시작시 달림 사라질때도 동시에 사라짐)
                    BattleStatus matchedBattleStatus = activateStatuses.stream()
                            .filter(battleStatus -> battleStatus.getStatus().getName().contains(matchingStatusName))
                            .findFirst().orElseThrow(() -> new IllegalStateException("해당 이름의 BattleStatus가 없습니다. statusName: " + matchingStatusName));
                    log.info("otherMovetype = {}, takenDamageSum = {}, mathcingStatusNAme = {}, matchedBattleStatus: {}", otherMoveType, takenDamageSum, matchingStatusName, matchedBattleStatus);
                    // 입은 데미지에 비례해 매칭된 배틀스테이터스 레벨 상승
                    int levelFromTakenDamage = takenDamageSum / 3000000 + 1; // 배틀 스테이터스가 레벨 1부터 시작하므로 +1 TODO 나중에 수치 바꿀것
                    if (levelFromTakenDamage > matchedBattleStatus.getLevel()) {
                        int increasingLevel = levelFromTakenDamage - matchedBattleStatus.getLevel();
                        SetStatusResult setStatusResult = null;
                        for (int i = 1; i <= increasingLevel; i++) {
                            // 증가량 만큼 스테이터스 set (레벨상승), 결과는 마지막것만 사용
                            setStatusResult = setStatusLogic.setStatus(mainActor, mainActor, partyMembers, List.of(matchedBattleStatus.getStatus()));
                        }
                        Move firstSupportAbility = mainActor.getActor().getMoves().get(MoveType.FIRST_SUPPORT_ABILITY);
                        // 결과에 추가
                        results.add(enemyLogicResultMapper.toResult(mainActor, partyMembers, firstSupportAbility, null, null, setStatusResult));
                    }
                }
            }
        }

        return results;
    }

    @Override
    public ActorLogicResult onTurnEnd(BattleActor mainActor, List<BattleActor> partyMembers) {
        BattleEnemy enemy = (BattleEnemy) mainActor;
        Enemy enemyActor = (Enemy) enemy.getActor();
        // 전조발생
        Move standbyMove = determineStandbyMove(enemy);
        if (standbyMove != null) {
            // 전조 발생함
            return enemyLogicResultMapper.toResultWithOmen(enemy, partyMembers, standbyMove, standbyMove.getOmen());
        }
        return null;
    }

    protected Move determineStandbyMove(BattleEnemy enemy) {
        Enemy enemyActor = (Enemy) enemy.getActor();
        Move standby = null;
        // hp 트리거
        Optional<Omen> hpTriggerOmen = omenLogic.getTriggeredOmen(enemy, OmenType.HP_TRIGGER);

        if (enemy.getNextStandbyType() != null) {
            log.info("INCANT ATTACK");
            // 영창기 (로직내부에서 발동설정)
            standby = enemy.getActor().getMoves().get(enemy.getNextStandbyType());
        } else if (hpTriggerOmen.isPresent() && !hpTriggerOmen.get().getTriggerHp().equals(enemy.getLatestTriggeredHp())) {
            // hp 트리거가 존재하며, 마지막으로 발동한 hp 트리거와 다름
            log.info("HPTRIGGER rate = {}, target = {}", enemy.calcHpRate(), hpTriggerOmen.get().getTriggerHp());
            // HP 트리거
            Omen triggeredOmen = hpTriggerOmen.get();
            standby = enemy.getActor().getMoves().get(triggeredOmen.getMove().getType());
            enemy.setNextStandbyType(standby.getType());
            enemy.setLatestTriggeredHp(triggeredOmen.getTriggerHp());
        } else if (enemy.getChargeGauge() >= enemy.getMaxChargeGauge()) {
            log.info("CHARGEATTACK");
            // 차지어택
            Optional<Omen> triggeredOmenOptional = omenLogic.getTriggeredOmen(enemy, OmenType.CHARGE_ATTACK);
            if (triggeredOmenOptional.isPresent()) {
                standby = enemy.getActor().getMoves().get(triggeredOmenOptional.get().getMove().getType());
                enemy.setNextStandbyType(standby.getType());
            }
        }
        log.info("\n\n determineStandbyMove, hprate = {}, move = {}, chargeTurn = {}, maxCT = {}", enemy.calcHpRate(), standby, enemy.getChargeGauge(), enemy.getActor().getMaxChargeGauge());
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

