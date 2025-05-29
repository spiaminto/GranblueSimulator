package com.gbf.granblue_simulator.logic.actor.enemy;

import com.gbf.granblue_simulator.domain.actor.Actor;
import com.gbf.granblue_simulator.domain.actor.Enemy;
import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleEnemy;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.prop.omen.Omen;
import com.gbf.granblue_simulator.domain.move.prop.omen.OmenType;
import com.gbf.granblue_simulator.logic.actor.ActorLogicUtil;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.common.*;
import com.gbf.granblue_simulator.logic.common.dto.DamageLogicResult;
import com.gbf.granblue_simulator.logic.common.dto.SetStatusResult;
import com.gbf.granblue_simulator.repository.actor.ActorRepository;
import com.gbf.granblue_simulator.service.BattleLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
@Slf4j
public class Diaspora1Logic implements EnemyLogic {

    private final StatusUtil statusUtil;
    private final ActorLogicUtil actorLogicUtil;
    private final DamageLogic damageLogic;
    private final EnemyLogicResultMapper enemyLogicResultMapper;
    private final ChargeGaugeLogic chargeGaugeLogic;
    private final SetStatusLogic setStatusLogic;
    private final BattleLogService battleLogService;
    private final OmenLogic omenLogic;
    private final ActorRepository actorRepository;

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
        List<BattleActor> targets = actorLogicUtil.getEnemyAttackTargets(attackMove.isAllTarget(), attackMove.getHitCount(), partyMembers);
        // 데미지
        DamageLogicResult damageLogicResult = damageLogic.processEnemy(mainActor, targets, attackMove);
        List<Integer> targetOrders = targets.stream().map(BattleActor::getCurrentOrder).toList();
        // 차지턴
        chargeGaugeLogic.afterEnemyAttack(mainActor, targets, damageLogicResult.getDamages(), attackMove.getType(), null);

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
        List<BattleActor> targets = actorLogicUtil.getEnemyAttackTargets(chargeAttack.isAllTarget(), chargeAttack.getHitCount(), partyMembers);
        List<BattleActor> statusTargets = getStatusTargets(targets);
        List<Integer> targetOrders = targets.stream().map(BattleActor::getCurrentOrder).toList();
        // 데미지
        DamageLogicResult damageLogicResult = damageLogic.processEnemy(mainActor, targets, chargeAttack);
        // 스테이터스
        SetStatusResult setStatusResult = setStatusLogic.setRandomStatusFromMove(mainActor, mainActor, statusTargets, chargeAttack);
        // 차지턴
        chargeGaugeLogic.afterEnemyAttack(mainActor, targets, damageLogicResult.getDamages(), chargeAttack.getType(), omen.getOmenType());
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
            Integer processedOmenValue = omenLogic.processOmen(enemy, otherResult);
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
    public List<ActorLogicResult> onTurnEnd(BattleActor mainActor, List<BattleActor> partyMembers) {
        BattleEnemy enemy = (BattleEnemy) mainActor;

        // 폼체인지 (hp 95%이하), 다음 폼의 전조를 우선발생시키기 위해 전조보다 먼저실행
        if (mainActor.calcHpRate() <= 99) {
            List<ActorLogicResult> formChangeResults = formChange(mainActor, partyMembers);
            return formChangeResults;
        }

        // 전조발생
        Move standbyMove = omenLogic.determineStandbyMove(enemy);
        if (standbyMove != null) {
            // 전조 발생함
            return List.of(enemyLogicResultMapper.toResultWithOmen(enemy, partyMembers, standbyMove, standbyMove.getOmen()));
        }
        return new ArrayList<>();
    }

    protected List<ActorLogicResult> formChange(BattleActor mainActor, List<BattleActor> partyMembers) {
        BattleEnemy enemy = (BattleEnemy) mainActor;
        // 폼체인지 무브
        Move formChangeMove = mainActor.getActor().getMoves().get(MoveType.FORM_CHANGE);
        // 다음 폼 및 폼체인지 입장 무브
        Actor diaspora2 = actorRepository.findByNameEnContains("diaspora").stream().filter(actor -> !Objects.equals(actor.getId(), mainActor.getActor().getId())).findFirst().orElse(null);
        if (diaspora2 == null) return null;
        Move formChangeEntryMove = diaspora2.getMoves().get(MoveType.FORM_CHANGE_ENTRY);
        // 다음 폼으로 set
        mainActor.setActor(diaspora2);
        enemy.setCurrentForm(2);
        // 영창기 스킵 (현재 사양상 영창기는 스킵)
        enemy.setNextStandbyType(null);

        // 폼체인지 / 엔트리 결과 반환
        List<ActorLogicResult> results = new ArrayList<>();
        results.add(enemyLogicResultMapper.toResultMoveOnly(mainActor, partyMembers, formChangeMove));
        results.add(enemyLogicResultMapper.toResultMoveOnly(mainActor, partyMembers, formChangeEntryMove));
        return results;
    }
}


