package com.gbf.granblue_simulator.logic.actor.enemy;

import com.gbf.granblue_simulator.domain.actor.Actor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleEnemy;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
import com.gbf.granblue_simulator.domain.move.prop.status.Status;
import com.gbf.granblue_simulator.logic.actor.dto.DefaultActorLogicResult;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.common.*;
import com.gbf.granblue_simulator.logic.common.dto.SetStatusResult;
import com.gbf.granblue_simulator.repository.actor.ActorRepository;
import com.gbf.granblue_simulator.service.BattleLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import static com.gbf.granblue_simulator.logic.common.StatusUtil.*;

@Component
@Slf4j
public class Diaspora1Logic extends EnemyLogic {

    public Diaspora1Logic(EnemyLogicResultMapper resultMapper, DamageLogic damageLogic, ChargeGaugeLogic chargeGaugeLogic, SetStatusLogic setStatusLogic, OmenLogic omenLogic, BattleLogService battleLogService, ActorRepository actorRepository, CalcStatusLogic calcStatusLogic) {
        super(resultMapper, damageLogic, chargeGaugeLogic, setStatusLogic, omenLogic, battleLogService, actorRepository);
    }

    @Override
    public List<ActorLogicResult> processBattleStart(BattleActor mainActor, List<BattleActor> partyMembers) {

        Move firstSupportAbility = mainActor.getActor().getMoves().get(MoveType.FIRST_SUPPORT_ABILITY);
        SetStatusResult setStatusResult = setStatusLogic.setStatus(mainActor, mainActor, partyMembers, firstSupportAbility.getStatuses());

        return List.of(resultMapper.toResult(mainActor, partyMembers, firstSupportAbility, null, null, setStatusResult));
    }

    @Override
    public ActorLogicResult attack(BattleActor mainActor, List<BattleActor> partyMembers) {
        // TEST
//         processBattleStart(mainActor, partyMembers);

        DefaultActorLogicResult attackResult = defaultAttack(mainActor, partyMembers);
        List<Integer> targetOrders = attackResult.getEnemyAttackTargets().stream().map(BattleActor::getCurrentOrder).toList();
        return resultMapper.attackToResult(mainActor, partyMembers, attackResult.getResultMove(), attackResult.getDamageLogicResult(), targetOrders, attackResult.getNextMoveType());
    }

    @Override
    public ActorLogicResult chargeAttack(BattleActor mainActor, List<BattleActor> partyMembers) {
        BattleEnemy mainEnemy = (BattleEnemy) mainActor;
        Move standby = mainEnemy.getActor().getMoves().get(mainEnemy.getCurrentStandbyType());
        DefaultActorLogicResult chargeAttackResult = defaultChargeAttack(mainActor, partyMembers, standby);
        List<Integer> targetOrders = chargeAttackResult.getEnemyAttackTargets().stream().map(BattleActor::getCurrentOrder).toList();
        return resultMapper.toResult(mainActor, partyMembers, chargeAttackResult.getResultMove(), chargeAttackResult.getDamageLogicResult(), targetOrders, chargeAttackResult.getSetStatusResult(), chargeAttackResult.getNextMoveType());
    }

    @Override
    public List<ActorLogicResult> postProcessToPartyMove(BattleActor mainActor, List<BattleActor> partyMembers, ActorLogicResult otherResult) {
        List<ActorLogicResult> results = new ArrayList<>();

        // 전조처리
        DefaultActorLogicResult omenResult = this.defaultOmen(mainActor, otherResult);
        if (omenResult.getResultMove() != null) {
            results.add(resultMapper.toResultWithOmen(mainActor, partyMembers, omenResult.getResultMove(), omenResult.getResultOmen()));
            // 자신의 긴급회복모드 전조 중단시 폼 체인지
            if (omenResult.getResultMove().getType() == MoveType.BREAK_D) {
                results.addAll(formChange(mainActor, partyMembers));
            }
        }

        if (!otherResult.getDamages().isEmpty()) {
            // 적의 행동에 데미지 발생시 서포어비 1
            results.add(firstSupportAbility(mainActor, partyMembers, mainActor.getActor().getMoves().get(MoveType.FIRST_SUPPORT_ABILITY), otherResult));
        }
        return results;
    }

    @Override
    public List<ActorLogicResult> postProcessToEnemyMove(BattleActor mainActor, List<BattleActor> partyMembers, ActorLogicResult enemyResult) {
        // 자신의 자괴인자 STANDBY_B 가 해제됬을시 서포어비 5 발동 -> 자괴인자 레벨 감소
        if (enemyResult.getMoveType() == MoveType.BREAK_B) {
            return List.of(fifthSupportAbility(mainActor, partyMembers, mainActor.getActor().getMoves().get(MoveType.SECOND_SUPPORT_ABILITY), enemyResult));
        }
        return Collections.emptyList();
    }

    @Override
    public List<ActorLogicResult> processTurnEnd(BattleActor mainActor, List<BattleActor> partyMembers) {
        BattleEnemy enemy = (BattleEnemy) mainActor;
        List<ActorLogicResult> results = new ArrayList<>();

        // TEST 서포어비 4
        results.add(fourthSupportAbility(mainActor, partyMembers, mainActor.getActor().getMoves().get(MoveType.FOURTH_SUPPORT_ABILITY), null));

        // 서포어비 2
        secondSupportAbility(mainActor, partyMembers, null, null);

        // 5의 배수턴 마다 자괴인자 발동
        if ((mainActor.getMember().getCurrentTurn() + 1) % 5 == 0)
            setStandbyBEveryFiveTurns(mainActor);

        // 전조발생
        omenLogic.determineStandbyMove(enemy).ifPresent(standby -> {
            omenLogic.setStandbyMove(enemy, standby);
            results.add(resultMapper.toResultWithOmen(enemy, partyMembers, standby, standby.getOmen()));
        });

        return results;
    }

    @Override
    // 자신이 입은 일반공격 / 어빌리티 / 오의 데미지의 누적값이 N 에 도달시 자신의 알파 / 베타 / 감마 레벨 증가
    protected ActorLogicResult firstSupportAbility(BattleActor mainActor, List<BattleActor> partyMembers, Move ability, ActorLogicResult otherResult) {
        MoveType otherMoveParentType = otherResult.getMoveType().getParentType();
        String matchingStatusName =
                otherMoveParentType == MoveType.ATTACK ? "활성 『알파』" :
                        otherMoveParentType == MoveType.ABILITY ? "활성 『베타』" :
                                otherMoveParentType == MoveType.CHARGE_ATTACK ? "활성 『감마』" : "해당 스테이터스 없음";
        if (!matchingStatusName.equals("해당 스테이터스 없음")) {
            // 해당 공격 타입 누적데미지 합과 증가시킬 스테이터스
            Integer takenDamageSum = battleLogService.getTakenDamageSumByMoveType(mainActor, otherMoveParentType);
            BattleStatus matchedBattleStatus = getBattleStatusByName(mainActor, matchingStatusName).orElse(null);
            if (matchedBattleStatus == null) {
                // 해당 스테이터스가 제거됨 (긴급수복모드 등)
                log.info("[firstSupportAbility] matchedBattleStatus is null, matchingStatusName = {}", matchingStatusName);
                return resultMapper.emptyResult();
            }
            // log.info("[firstSupportAbility] otherMovetype = {}, takenDamageSum = {}, mathcingStatusNAme = {}, matchedBattleStatus: {}", otherMoveType, takenDamageSum, matchingStatusName, matchedBattleStatus);
            int levelFromTakenDamage = takenDamageSum / 3000000 + 1; // 배틀 스테이터스가 레벨 1부터 시작하므로 +1 TODO 나중에 수치 바꿀것
            if (levelFromTakenDamage > matchedBattleStatus.getLevel()) {
                // 스테이터스 레벨 상승 - CHECK 불가능 하진 않지만 한 행동이 레벨을 2회 올릴수도 있으나, 일단 이대로 킵.
                SetStatusResult setStatusResult = setStatusLogic.setStatus(mainActor, mainActor, partyMembers, List.of(matchedBattleStatus.getStatus()));
                return resultMapper.toResult(mainActor, partyMembers, ability, null, null, setStatusResult);
            }
        }
        return resultMapper.emptyResult();
    }

    @Override
    // 어느 하나의 활성 레벨이 최고레벨이 된 턴 종료시 긴급 수복 모드 발생 및 타 활성레벨 제거 (동일 레벨의 경우 이전 순서 우선)
    protected ActorLogicResult secondSupportAbility(BattleActor mainActor, List<BattleActor> partyMembers, Move ability, ActorLogicResult otherResult) {
        BattleEnemy mainEnemy = (BattleEnemy) mainActor;
        List<BattleStatus> activateStatuses = new ArrayList<>(getBattleStatusesByName(mainActor, "활성"));
        if (!activateStatuses.isEmpty()) {
            activateStatuses.stream()
                    .filter(battleStatus -> battleStatus.getLevel().equals(battleStatus.getStatus().getMaxLevel()))
                    .findFirst()
                    .ifPresent(battleStatus -> {
                        // 전환할 활성 남기고 제거
                        activateStatuses.remove(battleStatus);
                        setStatusLogic.removeBattleStatuses(mainActor, activateStatuses);
                        // 긴급수복모드 발동
                        mainEnemy.setNextIncantStandbyType(MoveType.STANDBY_D);
                    });
        }
        return resultMapper.emptyResult();
    }

    @Override
    // 긴급 수복모드 종료시 자신에게 남아있는 활성레벨에 맞는 모드로 전환, 자신에게 걸린 모든 디버프 해제
    protected ActorLogicResult thirdSupportAbility(BattleActor mainActor, List<BattleActor> partyMembers, Move ability, ActorLogicResult otherResult) {
        // 현재 활성 제거
        BattleStatus currentActivateStatus = getBattleStatusByName(mainActor, "활성").orElseThrow(() -> new IllegalStateException("[thirdSupportAbility] 모드 전환에 필요한 활성효과 없음"));
        String currentActivateStatusType = currentActivateStatus.getStatus().getName().substring(4, 6); // 활성 『알파』 에서 알파만 남김. 일단 구리지만 이렇게.
        setStatusLogic.removeBattleStatus(mainActor, currentActivateStatus);

        // 2회차 전조부터 붙어있는 긴급 수복모드 제거
        BattleStatus recoveryStatus = getBattleStatusByName(mainActor, "긴급 회복 시스템").orElse(null);
        setStatusLogic.removeBattleStatus(mainActor, recoveryStatus);

        // 활성 효과에 맞는 모드 적용
        Status modeStatus = getStatusByNameFromMove(mainActor, MoveType.THIRD_SUPPORT_ABILITY, currentActivateStatusType);
        SetStatusResult setStatusResult = setStatusLogic.setStatus(mainActor, mainActor, partyMembers, List.of(modeStatus));

        return resultMapper.toResult(mainActor, partyMembers, ability, null, null, setStatusResult);
    }

    @Override // (전투시작시) 자신에게 활성 알파, 베타, 감마 부여
    protected ActorLogicResult fourthSupportAbility(BattleActor mainActor, List<BattleActor> partyMembers, Move ability, ActorLogicResult otherResult) {
        SetStatusResult setStatusResult = setStatusLogic.setStatus(mainActor, mainActor, partyMembers, ability.getStatuses());
        return resultMapper.toResult(mainActor, partyMembers, ability, null, null, setStatusResult);
    }

    @Override
    protected ActorLogicResult fifthSupportAbility(BattleActor mainActor, List<BattleActor> partyMembers, Move ability, ActorLogicResult otherResult) {
        SetStatusResult setStatusResult = getBattleStatusByName(mainActor, "자괴인자")
                .map(battleStatus -> setStatusLogic.subtractBattleStatusLevel(mainActor, 1, true, battleStatus))
                .orElse(null);
        return resultMapper.toResult(mainActor, partyMembers, ability, null, null, setStatusResult);
    }

    protected List<ActorLogicResult> formChange(BattleActor mainActor, List<BattleActor> partyMembers) {
        BattleEnemy enemy = (BattleEnemy) mainActor;
        // 서포트어빌리티 3 모드전환 발동
        ActorLogicResult thirdSupportAbilityResult = thirdSupportAbility(mainActor, partyMembers, enemy.getActor().getMoves().get(MoveType.THIRD_SUPPORT_ABILITY), null);
        // 폼체인지 무브
        Move formChangeMove = mainActor.getActor().getMoves().get(MoveType.FORM_CHANGE);
        // 다음 폼 및 폼체인지 입장 무브
        Actor diaspora2 = actorRepository.findByNameEnContains("diaspora").stream().filter(actor -> !Objects.equals(actor.getId(), mainActor.getActor().getId())).findFirst().orElse(null);
        if (diaspora2 == null) return null;
        Move formChangeEntryMove = diaspora2.getMoves().get(MoveType.FORM_CHANGE_ENTRY);
        // 다음 폼으로 set
        mainActor.setActor(diaspora2);
        enemy.setCurrentForm(2);
        // 폼체인지 후 2페이즈의 인자방출 영창기 등록
        enemy.setNextIncantStandbyType(MoveType.STANDBY_D);

        // 폼체인지 / 엔트리 / 모드전환 결과 반환
        List<ActorLogicResult> results = new ArrayList<>();
        results.add(resultMapper.toResultMoveOnly(mainActor, partyMembers, formChangeMove));
        results.add(resultMapper.toResultMoveOnly(mainActor, partyMembers, formChangeEntryMove));
        results.add(thirdSupportAbilityResult);
        return results;
    }

    // 기타 표시되지 않는 개인 로직 ======================================================================

    /**
     * 턴 종료시 5의 배수턴마다 자괴인자가 발동 (스테이터스로 표시)
     *
     * @param mainActor
     */
    protected void setStandbyBEveryFiveTurns(BattleActor mainActor) {
        BattleEnemy battleEnemy = (BattleEnemy) mainActor;
        if (battleEnemy.getNextIncantStandbyType() == null) // 긴급회복시스템 (STANDBY_D) 가 더 우선
            battleEnemy.setNextIncantStandbyType(MoveType.STANDBY_B);
    }

}


