package com.gbf.granblue_simulator.logic.actor.enemy;

import com.gbf.granblue_simulator.domain.base.actor.BaseActor;
import com.gbf.granblue_simulator.domain.battle.actor.Actor;
import com.gbf.granblue_simulator.domain.battle.actor.Enemy;
import com.gbf.granblue_simulator.domain.battle.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.domain.base.move.Move;
import com.gbf.granblue_simulator.domain.base.move.MoveType;
import com.gbf.granblue_simulator.domain.base.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.logic.actor.dto.StatusEffectDto;
import com.gbf.granblue_simulator.logic.actor.dto.DefaultActorLogicResult;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.logic.common.*;
import com.gbf.granblue_simulator.logic.common.dto.SetStatusResult;
import com.gbf.granblue_simulator.repository.actor.BaseActorRepository;
import com.gbf.granblue_simulator.service.BattleLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

import static com.gbf.granblue_simulator.logic.common.StatusUtil.*;

@Component
@Slf4j
public class Diaspora1Logic extends EnemyLogic {

    public Diaspora1Logic(EnemyLogicResultMapper resultMapper, DamageLogic damageLogic, ChargeGaugeLogic chargeGaugeLogic, SetStatusLogic setStatusLogic, OmenLogic omenLogic, BattleLogService battleLogService, BaseActorRepository baseActorRepository) {
        super(resultMapper, damageLogic, chargeGaugeLogic, setStatusLogic, omenLogic, battleLogService, baseActorRepository);
    }

    @Override
    public List<ActorLogicResult> processBattleStart(Actor mainActor, List<Actor> partyMembers) {
        List<ActorLogicResult> battleStartResults = new ArrayList<>();
        // CHECK 다른 보스들과 다르게 디아스포라의 경우 타인의 보스가 폼체인지 후라도, 반드시 긴급수복모드를 해제해야 폼체인지 되도록 설계됨
        // 1. 서포어비4 -> 자신에게 활성 버프
        battleStartResults.add(fourthSupportAbility(mainActor, partyMembers, mainActor.getMove(MoveType.FOURTH_SUPPORT_ABILITY), null));
        // 2. 자신의 활성레벨 갱신 (프론트 표시 없이 내부적으로 한번에 다 올림)
        // 알파
        StatusEffect modeAlphaStatusEffect = getEffectByName(mainActor, "활성 『알파』").orElseThrow(() -> new IllegalArgumentException("[processBattleStart] 모드 알파 없음"));
        Integer attackDamageSum = battleLogService.getEnemyTakenDamageSumByMoveType(mainActor, MoveType.ATTACK);
        int levelFromAttackDamageSum = attackDamageSum / 300000 + 1;
        log.info("levelFromAttackDamageSum = {}", levelFromAttackDamageSum);
        setStatusLogic.addStatusEffectLevel(mainActor, levelFromAttackDamageSum - 1, modeAlphaStatusEffect);
        // 베타
        StatusEffect modeBetaStatusEffect = getEffectByName(mainActor, "활성 『베타』").orElseThrow(() -> new IllegalArgumentException("[processBattleStart] 모드 베타 없음"));
        Integer abilityDamageSum = battleLogService.getEnemyTakenDamageSumByMoveType(mainActor, MoveType.ABILITY);
        int levelFromAbilityDamageSum = abilityDamageSum / 300000 + 1;
        log.info("levelFromAbilityDamageSum = {}", levelFromAbilityDamageSum);
        setStatusLogic.addStatusEffectLevel(mainActor, levelFromAbilityDamageSum - 1, modeBetaStatusEffect);
        // 감마
        StatusEffect modeGammaStatusEffect = getEffectByName(mainActor, "활성 『감마』").orElseThrow(() -> new IllegalArgumentException("[processBattleStart] 모드 감마 없음"));
        Integer chargeAttackDamageSum = battleLogService.getEnemyTakenDamageSumByMoveType(mainActor, MoveType.CHARGE_ATTACK);
        int levelFromChargeAttackDamageSum = chargeAttackDamageSum / 300000 + 1;
        log.info("levelFromChargeAttackDamageSum = {}", levelFromChargeAttackDamageSum);
        setStatusLogic.addStatusEffectLevel(mainActor, levelFromChargeAttackDamageSum - 1, modeGammaStatusEffect);
        // 3. 갱신된 활성버프를 기반으로 서포어비 2 발동 (최대활성시, 타 활성레벨 삭제 후 긴급수복모드 이행)
        battleStartResults.add(secondSupportAbility(mainActor, partyMembers, null, null));
        // 4. 전조 발생가능시 발생 (긴급 수복모드인 경우만 상정)
        omenLogic.triggerOmen(mainActor).ifPresent(standby -> battleStartResults.add(resultMapper.toResultWithOmen(mainActor, partyMembers, standby, standby.getOmen())));

        return battleStartResults;
    }

    @Override
    public ActorLogicResult attack(Actor mainActor, List<Actor> partyMembers) {
        // TEST
//         processBattleStart(mainActor, partyMembers);

        DefaultActorLogicResult attackResult = defaultAttack(mainActor, partyMembers);
        List<Integer> targetOrders = attackResult.getEnemyAttackTargets().stream().map(Actor::getCurrentOrder).toList();
        return resultMapper.attackToResult(mainActor, partyMembers, attackResult.getResultMove(), attackResult.getDamageLogicResult(), targetOrders);
    }

    @Override
    public ActorLogicResult chargeAttack(Actor mainActor, List<Actor> partyMembers) {
        Enemy mainEnemy = (Enemy) mainActor;
        Move standby = mainEnemy.getBaseActor().getMoves().get(mainEnemy.getCurrentStandbyType());
        DefaultActorLogicResult chargeAttackResult = defaultChargeAttack(mainActor, partyMembers, standby);
        List<Integer> targetOrders = chargeAttackResult.getEnemyAttackTargets().stream().map(Actor::getCurrentOrder).toList();
        return resultMapper.toResult(mainActor, partyMembers, chargeAttackResult.getResultMove(), chargeAttackResult.getDamageLogicResult(), targetOrders, chargeAttackResult.getSetStatusResult());
    }

    @Override
    public List<ActorLogicResult> postProcessToPartyMove(Actor mainActor, List<Actor> partyMembers, ActorLogicResult otherResult) {
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
            results.add(firstSupportAbility(mainActor, partyMembers, mainActor.getBaseActor().getMoves().get(MoveType.FIRST_SUPPORT_ABILITY), otherResult));
        }
        return results;
    }



    @Override
    public List<ActorLogicResult> postProcessToEnemyMove(Actor mainActor, List<Actor> partyMembers, ActorLogicResult enemyResult) {
        // 자신의 자괴인자 STANDBY_B 가 해제됬을시 서포어비 5 발동 -> 자괴인자 레벨 감소
        if (enemyResult.getMoveType() == MoveType.BREAK_B) {
            return List.of(fifthSupportAbility(mainActor, partyMembers, mainActor.getBaseActor().getMoves().get(MoveType.SECOND_SUPPORT_ABILITY), enemyResult));
        }
        return Collections.emptyList();
    }

    @Override
    public List<ActorLogicResult> processTurnEnd(Actor mainActor, List<Actor> partyMembers) {
        List<ActorLogicResult> results = new ArrayList<>();

        // CHECK 테스트용 서포어비 4
//        results.add(fourthSupportAbility(mainActor, partyMembers, mainActor.getActor().getMoves().get(MoveType.FOURTH_SUPPORT_ABILITY), null));

        // 서포어비 2
        secondSupportAbility(mainActor, partyMembers, null, null);

        return results;
    }

    @Override
    public List<ActorLogicResult> activateOmen(Actor mainActor, List<Actor> partyMembers) {
        Enemy enemy = (Enemy) mainActor;
        List<ActorLogicResult> results = new ArrayList<>();

        // 5의 배수턴 마다 자괴인자 발동
        if ((mainActor.getMember().getCurrentTurn() + 1) % 5 == 0)
            setStandbyBEveryFiveTurns(mainActor);

        // 전조발생
        omenLogic.triggerOmen(enemy).ifPresent(standby -> results.add(resultMapper.toResultWithOmen(enemy, partyMembers, standby, standby.getOmen())));

        return results;
    }

    @Override
    // 자신이 입은 일반공격 / 어빌리티 / 오의 데미지의 누적값이 N 에 도달시 자신의 알파 / 베타 / 감마 레벨 증가
    protected ActorLogicResult firstSupportAbility(Actor mainActor, List<Actor> partyMembers, Move ability, ActorLogicResult otherResult) {
        MoveType otherMoveParentType = otherResult.getMoveType().getParentType();
        String matchingStatusName =
                otherMoveParentType == MoveType.ATTACK ? "활성 『알파』" :
                        otherMoveParentType == MoveType.ABILITY ? "활성 『베타』" :
                                otherMoveParentType == MoveType.CHARGE_ATTACK ? "활성 『감마』" : "해당 스테이터스 없음";
        if (!matchingStatusName.equals("해당 스테이터스 없음")) {
            // 해당 공격 타입 누적데미지 합과 증가시킬 스테이터스
            Integer takenDamageSum = battleLogService.getEnemyTakenDamageSumByMoveType(mainActor, otherMoveParentType);
            StatusEffect matchedStatusEffect = getEffectByName(mainActor, matchingStatusName).orElse(null);
            if (matchedStatusEffect == null) {
                // 해당 스테이터스가 제거됨 (긴급수복모드 등)
                log.info("[firstSupportAbility] matchedBattleStatus is null, matchingStatusName = {}", matchingStatusName);
                return resultMapper.emptyResult();
            }
            // log.info("[firstSupportAbility] otherMovetype = {}, takenDamageSum = {}, mathcingStatusNAme = {}, matchedBattleStatus: {}", otherMoveType, takenDamageSum, matchingStatusName, matchedBattleStatus);
            // TEST 값 3000000 (삼백만) -> 300000 (삼십만)
            int levelFromTakenDamage = takenDamageSum / 300000 + 1; // 배틀 스테이터스가 레벨 1부터 시작하므로 +1 TODO 나중에 수치 바꿀것
            if (levelFromTakenDamage < matchedStatusEffect.getBaseStatusEffect().getMaxLevel()) {
                int levelDiff = levelFromTakenDamage - matchedStatusEffect.getLevel();
                if (levelDiff > 1) { // 차이가 1보다 크면, 초과분은 직접레벨업
                    setStatusLogic.addStatusEffectLevel(mainActor, levelDiff - 1, matchedStatusEffect);
                }
                // 레벨상승 및 프론트로 반환
                SetStatusResult setStatusResult = setStatusLogic.setStatusEffect(mainActor, mainActor, partyMembers, List.of(matchedStatusEffect.getBaseStatusEffect()));
                return resultMapper.toResult(mainActor, partyMembers, ability, null, null, setStatusResult);
            }
        }
        return resultMapper.emptyResult();
    }

    @Override
    // 어느 하나의 활성 레벨이 최고레벨이 된 턴 종료시 긴급 수복 모드 발생 및 타 활성레벨 제거 (동일 레벨의 경우 이전 순서 우선)
    protected ActorLogicResult secondSupportAbility(Actor mainActor, List<Actor> partyMembers, Move ability, ActorLogicResult otherResult) {
        Enemy mainEnemy = (Enemy) mainActor;
        List<StatusEffect> activateStatuses = new ArrayList<>(getEffectsByName(mainActor, "활성"));
        if (!activateStatuses.isEmpty()) {
            activateStatuses.stream()
                    .filter(battleStatus -> battleStatus.getLevel().equals(battleStatus.getBaseStatusEffect().getMaxLevel()))
                    .findFirst()
                    .ifPresent(battleStatus -> {
                        // 전환할 활성 남기고 제거
                        activateStatuses.remove(battleStatus);
                        setStatusLogic.removeStatusEffects(mainActor, activateStatuses);
                        // 긴급수복모드 발동
                        mainEnemy.setNextIncantStandbyType(MoveType.STANDBY_D);
                    });
        }
        return resultMapper.emptyResult();
    }

    @Override
    // 긴급 수복모드 종료시 자신에게 남아있는 활성레벨에 맞는 모드로 전환, 자신에게 걸린 모든 디버프 해제
    protected ActorLogicResult thirdSupportAbility(Actor mainActor, List<Actor> partyMembers, Move ability, ActorLogicResult otherResult) {
        // 현재 활성 제거
        StatusEffect currentActivateStatus = getEffectByName(mainActor, "활성").orElseThrow(() -> new IllegalStateException("[thirdSupportAbility] 모드 전환에 필요한 활성효과 없음"));
        String currentActivateStatusName = currentActivateStatus.getBaseStatusEffect().getName();
        String currentActivateStatusNameType = currentActivateStatusName.substring(currentActivateStatusName.indexOf("『"), currentActivateStatusName.indexOf("』")); // "활성『알파』" 에서 "『알파" 만 남김.
        setStatusLogic.removeStatusEffect(mainActor, currentActivateStatus);

        // 2회차 전조부터 붙어있는 긴급 수복모드 제거
        StatusEffect recoveryStatus = getEffectByName(mainActor, "긴급 회복 시스템").orElse(null);
        setStatusLogic.removeStatusEffect(mainActor, recoveryStatus);

        // 활성 효과에 맞는 모드 적용
        BaseStatusEffect modeBaseStatusEffect = getBaseEffectByName(mainActor, MoveType.THIRD_SUPPORT_ABILITY, currentActivateStatusNameType);
        SetStatusResult setStatusResult = setStatusLogic.setStatusEffect(mainActor, mainActor, partyMembers, List.of(modeBaseStatusEffect));
        setStatusResult.getRemovedStatuesList().get(mainActor.getCurrentOrder()).add(StatusEffectDto.of(currentActivateStatus)); // 활성 지우는 효과 추가

        return resultMapper.toResult(mainActor, partyMembers, ability, null, null, setStatusResult);
    }

    @Override // (전투시작시) 자신에게 활성 알파, 베타, 감마, 인자발생 부여
    protected ActorLogicResult fourthSupportAbility(Actor mainActor, List<Actor> partyMembers, Move ability, ActorLogicResult otherResult) {
        SetStatusResult setStatusResult = setStatusLogic.setStatusEffect(mainActor, mainActor, partyMembers, ability.getStatusEffects());
        return resultMapper.toResult(mainActor, partyMembers, ability, null, null, setStatusResult);
    }

    @Override
    protected ActorLogicResult fifthSupportAbility(Actor mainActor, List<Actor> partyMembers, Move ability, ActorLogicResult otherResult) {
        SetStatusResult setStatusResult = getEffectByName(mainActor, "자괴인자")
                .map(battleStatus -> setStatusLogic.subtractStatusEffectLevel(mainActor, 1, battleStatus))
                .orElse(null);
        return resultMapper.toResult(mainActor, partyMembers, ability, null, null, setStatusResult);
    }

    protected List<ActorLogicResult> formChange(Actor mainActor, List<Actor> partyMembers) {
        Enemy enemy = (Enemy) mainActor;
        // 서포트어빌리티 3 모드전환 발동
        ActorLogicResult thirdSupportAbilityResult = thirdSupportAbility(mainActor, partyMembers, enemy.getBaseActor().getMoves().get(MoveType.THIRD_SUPPORT_ABILITY), null);
        // 폼체인지 무브
        Move formChangeMove = mainActor.getBaseActor().getMoves().get(MoveType.FORM_CHANGE_DEFAULT);
        // 다음 폼 및 폼체인지 입장 무브
        BaseActor diaspora2 = baseActorRepository.findByNameEnContains("diaspora").stream().filter(actor -> !Objects.equals(actor.getId(), mainActor.getBaseActor().getId())).findFirst().orElse(null);
        if (diaspora2 == null) return null;
        Move formChangeEntryMove = diaspora2.getMoves().get(MoveType.FORM_CHANGE_ENTRY);
        // 다음 폼으로 set
        mainActor.updateBaseActor(diaspora2);
        enemy.setCurrentForm(1);
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
    protected void setStandbyBEveryFiveTurns(Actor mainActor) {
        Enemy battleEnemy = (Enemy) mainActor;
        if (battleEnemy.getNextIncantStandbyType() == null) // 긴급회복시스템 (STANDBY_D) 가 더 우선
            battleEnemy.setNextIncantStandbyType(MoveType.STANDBY_B);
    }

}


