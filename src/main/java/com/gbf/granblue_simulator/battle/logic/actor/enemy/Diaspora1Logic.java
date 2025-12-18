package com.gbf.granblue_simulator.battle.logic.actor.enemy;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.battle.logic.damage.DamageLogic;
import com.gbf.granblue_simulator.battle.logic.actor.dto.ActorLogicResult;
import com.gbf.granblue_simulator.battle.logic.actor.dto.DefaultActorLogicResult;
import com.gbf.granblue_simulator.battle.logic.actor.dto.ResultStatusEffectDto;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusLogic;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusResult;
import com.gbf.granblue_simulator.battle.logic.system.ChargeGaugeLogic;
import com.gbf.granblue_simulator.battle.logic.system.OmenLogic;
import com.gbf.granblue_simulator.battle.logic.system.dto.OmenResult;
import com.gbf.granblue_simulator.battle.service.BattleLogService;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseActor;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseEnemy;
import com.gbf.granblue_simulator.metadata.domain.move.Move;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.metadata.repository.BaseActorRepository;
import com.gbf.granblue_simulator.metadata.repository.BaseEnemyRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.gbf.granblue_simulator.battle.logic.util.StatusUtil.*;

@Component
@Slf4j
public class Diaspora1Logic extends EnemyLogic {

    private final BaseEnemyRepository baseEnemyRepository;

    public Diaspora1Logic(BattleContext battleContext, EnemyLogicResultMapper resultMapper, DamageLogic damageLogic, ChargeGaugeLogic chargeGaugeLogic, SetStatusLogic setStatusLogic, OmenLogic omenLogic, BattleLogService battleLogService, BaseActorRepository baseActorRepository, BaseEnemyRepository baseEnemyRepository) {
        super(battleContext, resultMapper, damageLogic, chargeGaugeLogic, setStatusLogic, omenLogic, battleLogService, baseActorRepository);
        this.baseEnemyRepository = baseEnemyRepository;
    }

    @Override
    public List<ActorLogicResult> processBattleStart() {
        List<ActorLogicResult> battleStartResults = new ArrayList<>();
        Enemy self = self();
        // CHECK 다른 보스들과 다르게 디아스포라의 경우 타인의 보스가 폼체인지 후라도, 반드시 긴급수복모드를 해제해야 폼체인지 되도록 설계됨

        // 1. 서포어비4 -> 자신에게 활성 버프
        battleStartResults.add(fourthSupportAbility());

        // 2. 자신의 활성레벨 갱신 (프론트 표시 없이 내부적으로 한번에 다 올림)
        // 알파
        StatusEffect modeAlphaStatusEffect = getEffectByName(self, "활성 『알파』").orElseThrow(() -> new IllegalArgumentException("[processBattleStart] 모드 알파 없음"));
        int attackDamageSum = battleLogService.getEnemyTakenDamageSumByMoveType(self, MoveType.ATTACK);
        int levelFromAttackDamageSum = attackDamageSum / 1000000 + 1;
        setStatusLogic.addStatusEffectsLevel(self, levelFromAttackDamageSum - 1, modeAlphaStatusEffect);
        // 베타
        StatusEffect modeBetaStatusEffect = getEffectByName(self, "활성 『베타』").orElseThrow(() -> new IllegalArgumentException("[processBattleStart] 모드 베타 없음"));
        int abilityDamageSum = battleLogService.getEnemyTakenDamageSumByMoveType(self, MoveType.ABILITY);
        int levelFromAbilityDamageSum = abilityDamageSum / 1000000 + 1;
        setStatusLogic.addStatusEffectsLevel(self, levelFromAbilityDamageSum - 1, modeBetaStatusEffect);
        // 감마
        StatusEffect modeGammaStatusEffect = getEffectByName(self, "활성 『감마』").orElseThrow(() -> new IllegalArgumentException("[processBattleStart] 모드 감마 없음"));
        int chargeAttackDamageSum = battleLogService.getEnemyTakenDamageSumByMoveType(self, MoveType.CHARGE_ATTACK);
        int levelFromChargeAttackDamageSum = chargeAttackDamageSum / 1000000 + 1;
        setStatusLogic.addStatusEffectsLevel(self, levelFromChargeAttackDamageSum - 1, modeGammaStatusEffect);

        // 3. 갱신된 활성버프를 기반으로 서포어비 2 발동 (최대활성시, 타 활성레벨 삭제 후 긴급수복모드 이행)
        battleStartResults.add(secondSupportAbility());

        // 4. 전조 발생가능시 발생 (긴급 수복모드인 경우만 상정)
        omenLogic.triggerOmen(self).ifPresent(standby -> battleStartResults.add(resultMapper.toResultWithOmen(standby, OmenResult.from(self))));

        log.info("[processBattleStart] levelFromAttackDamageSum = {}, levelFromAbilityDamageSum = {}, levelFromChargeAttackDamageSum = {}", levelFromAttackDamageSum, levelFromAbilityDamageSum, levelFromChargeAttackDamageSum);
        return battleStartResults;
    }

    @Override
    public ActorLogicResult attack() {
        return resultMapper.fromDefaultResult(defaultAttack());
    }

    @Override
    public ActorLogicResult chargeAttack() {
        return resultMapper.fromDefaultResult(defaultChargeAttack());
    }

    @Override
    public List<ActorLogicResult> postProcessToPartyMove(ActorLogicResult partyMoveResult) {
        List<ActorLogicResult> results = new ArrayList<>();

        if (!partyMoveResult.getDamages().isEmpty()) {
            results.add(firstSupportAbility(partyMoveResult));
        }

        // 전조처리
        DefaultActorLogicResult omenResult = this.defaultOmen(partyMoveResult);
        if (omenResult != null) {
            results.add(resultMapper.fromDefaultResult(omenResult));
            // 긴급 수복 모드 전조 해제시
            if (omenResult.getResultMove().getType() == MoveType.BREAK_D) {
                results.addAll(formChange());
            }
            // 자괴 인자 해제시
            if (omenResult.getResultMove().getType() == MoveType.BREAK_B) {
                results.add(fifthSupportAbility());
            }
        }

        return results;
    }

    @Override
    public List<ActorLogicResult> postProcessToEnemyMove(ActorLogicResult enemyResult) {
        return Collections.emptyList();
    }

    @Override
    public List<ActorLogicResult> processTurnEnd() {
        return List.of(secondSupportAbility());
    }

    @Override
    public List<ActorLogicResult> activateOmen() {
        List<ActorLogicResult> results = new ArrayList<>();

        // 5의 배수턴 마다 자괴인자 발동
        if ((battleContext.getCurrentTurn() + 1) % 5 == 0)
            setStandbyBEveryFiveTurns();

        // 전조발생
        omenLogic.triggerOmen(self()).ifPresent(standby -> results.add(resultMapper.toResultWithOmen(standby, OmenResult.from(self()))));

        return results;
    }

    /**
     * 자신이 입은 일반공격 / 어빌리티 / 오의데미지의 누적값이 N 에 도달시 자신의 알파 / 베타 / 감마 레벨 증가
     *
     * @param partyMoveResult
     * @return
     */
    protected ActorLogicResult firstSupportAbility(ActorLogicResult partyMoveResult) {
        MoveType otherMoveParentType = partyMoveResult.getMove().getType().getParentType();

        // 공격 타입에 따른 활성 상태효과 이름
        String matchingStatusName = switch (otherMoveParentType) {
            case ATTACK -> "활성 『알파』";
            case ABILITY -> "활성 『베타』";
            case CHARGE_ATTACK -> "활성 『감마』";
            default -> "없음";
        };
        if (matchingStatusName.equals("없음")) return resultMapper.emptyResult();

        // 해당 활성효과 확인
        StatusEffect matchedStatusEffect = getEffectByName(self(), matchingStatusName).orElse(null);
        if (matchedStatusEffect == null || matchedStatusEffect.isMaxLevel())
            return resultMapper.emptyResult(); // 긴급 수복 모드 전조 발생 등으로 이미 제거됨 || 이미 최고레벨

        // 현재까지 받은 데미지에 따른 타겟 레벨, 레벨 차
        int takenDamageSum = battleLogService.getEnemyTakenDamageSumByMoveType(self(), otherMoveParentType);
        // TEST 값 3000000 (삼백만) -> 백만
        int levelFromTakenDamage = takenDamageSum / 1000000 + 1; // 상태 효과가 레벨 1부터 시작하므로 +1
        int levelDiff = levelFromTakenDamage - matchedStatusEffect.getLevel();
        if (levelDiff <= 0) return resultMapper.emptyResult(); // 레벨상승 없음

        // 레벨 상승
        if (levelDiff > 1) // 차이가 1보다 크면, 초과분은 직접레벨업
            setStatusLogic.addStatusEffectsLevel(self(), levelDiff - 1, matchedStatusEffect);
        SetStatusResult setStatusResult = setStatusLogic.setStatusEffect(List.of(matchedStatusEffect.getBaseStatusEffect()));
        return resultMapper.toResult(selfMove(MoveType.FIRST_SUPPORT_ABILITY), setStatusResult);
    }

    /**
     * 어느 하나의 활성 레벨이 최고레벨이 된 턴 종료시 긴급 수복 모드 전조 발생, 최고레벨 활성 제외 제거
     *
     * @return
     */
    @Override
    protected ActorLogicResult secondSupportAbility() {
        List<StatusEffect> activateStatuses = new ArrayList<>(getEffectsByName(self(), "활성"));
        return activateStatuses.stream()
                .filter(StatusEffect::isMaxLevel)
                .max(Comparator.comparing(StatusEffect::getUpdatedAt))
                .map(statusEffect -> {
                    // 전환할 활성 남기고 제거
                    activateStatuses.remove(statusEffect);
                    SetStatusResult setStatusResult = setStatusLogic.subtractStatusEffectLevel(self(), 10, activateStatuses.toArray(StatusEffect[]::new));
                    // 긴급수복모드 발동
                    self().setNextIncantStandbyType(MoveType.STANDBY_D);
                    return resultMapper.toResult(selfMove(MoveType.SECOND_SUPPORT_ABILITY), setStatusResult);
                })
                .orElseGet(resultMapper::emptyResult);
    }

    /**
     * [긴급 수복 모드 종료시, BREAK_D] 자신에게 남아있는 활성레벨 중 가장 높은 활성 레벨의 모드로 전환, 자신의 모든 디버프 해제
     *
     * @return
     */
    @Override
    // 긴급 수복모드 종료시 자신에게 남아있는 활성레벨에 맞는 모드로 전환, 자신에게 걸린 모든 디버프 해제
    protected ActorLogicResult thirdSupportAbility() {
        Actor self = self();
        Move ability = selfMove(MoveType.THIRD_SUPPORT_ABILITY);
        // 현재 활성 제거
        StatusEffect currentActivateStatus = getEffectByName(self, "활성").orElseThrow(() -> new IllegalStateException("[thirdSupportAbility] 모드 전환에 필요한 활성효과 없음"));
        String currentActivateStatusName = currentActivateStatus.getBaseStatusEffect().getName();
        String currentActivateStatusNameType = currentActivateStatusName.substring(currentActivateStatusName.indexOf("『"), currentActivateStatusName.indexOf("』")); // "활성『알파』" 에서 "『알파" 만 남김.
        setStatusLogic.removeStatusEffect(self, currentActivateStatus);

        // 활성 효과에 맞는 모드 적용
        BaseStatusEffect modeBaseStatusEffect = getBaseEffectByNameFromMove(ability, currentActivateStatusNameType);
        SetStatusResult setStatusResult = setStatusLogic.setStatusEffect(List.of(modeBaseStatusEffect));
        List<ResultStatusEffectDto> removedStatusEffectsInResult = setStatusResult.getRemovedStatuesList().get(self.getCurrentOrder());
        removedStatusEffectsInResult.add(ResultStatusEffectDto.of(currentActivateStatus)); // 활성 지우는 효과 추가

        // 2회차 전조부터 붙어있는 긴급 회복 시스템 효과 제거
        getEffectByName(self, "긴급 회복 시스템").ifPresent(statusEffect -> {
            setStatusLogic.removeStatusEffect(self, statusEffect);
            removedStatusEffectsInResult.add(ResultStatusEffectDto.of(currentActivateStatus)); // 긴급 회복 시스템 지우는 효과 추가
        });

        return resultMapper.toResult(ability, setStatusResult);
    }

    /**
     * [전투시작시] 자신에게 활성레벨 알파 / 베타 / 감마 부여, 인자발생 부여
     *
     * @return
     */
    @Override // (전투시작시) 자신에게 활성 알파, 베타, 감마, 인자발생 부여
    protected ActorLogicResult fourthSupportAbility() {
        return resultMapper.fromDefaultResult(defaultAbility(selfMove(MoveType.FOURTH_SUPPORT_ABILITY)));
    }

    /**
     * [전조 자괴인자 해제시, BREAK_B] 자신의 자괴인자 status 레벨 1 감소
     *
     * @return
     */
    @Override
    protected ActorLogicResult fifthSupportAbility() {
        SetStatusResult setStatusResult = getEffectByName(self(), "자괴인자")
                .map(battleStatus -> setStatusLogic.subtractStatusEffectLevel(self(), 1, battleStatus))
                .orElse(null);
        return resultMapper.toResult(selfMove(MoveType.FIFTH_SUPPORT_ABILITY), null, null, setStatusResult);
    }

    protected List<ActorLogicResult> formChange() {
        // 서포트어빌리티 3 모드전환 발동
        ActorLogicResult thirdSupportAbilityResult = thirdSupportAbility();
        // 폼체인지 무브
        Move formChangeMove = selfMove(MoveType.FORM_CHANGE_DEFAULT);
        // 다음 폼 set
        BaseEnemy currentBaseEnemy = (BaseEnemy) self().getBaseActor();
        String rootNameEn = currentBaseEnemy.getRootNameEn();
        List<BaseEnemy> baseEnemies = baseEnemyRepository.findByRootNameEn(rootNameEn);
        BaseEnemy nextBaseEnemy = baseEnemies.stream().filter(baseEnemy -> baseEnemy.getFormOrder() == 2).findAny().orElseThrow(() -> new IllegalArgumentException("다음 폼 없음"));
        self().updateBaseActor(nextBaseEnemy);
        self().setCurrentForm(nextBaseEnemy.getFormOrder());
        // 입장무브
        Move formChangeEntryMove = self().getMove(MoveType.FORM_CHANGE_ENTRY);
        // 폼체인지 후 2페이즈의 인자방출 영창기 등록
        self().setNextIncantStandbyType(MoveType.STANDBY_D);

        // 폼체인지 / 엔트리 / 모드전환 결과 반환
        List<ActorLogicResult> results = new ArrayList<>();
        results.add(resultMapper.toResult(formChangeMove));
        results.add(resultMapper.toResult(formChangeEntryMove));
        results.add(thirdSupportAbilityResult);
        return results;
    }

    // 기타 표시되지 않는 개인 로직 ======================================================================

    /**
     * 턴 종료시 5의 배수턴마다 자괴인자가 발동 (스테이터스로 표시)
     */
    protected void setStandbyBEveryFiveTurns() {
        if (self().getNextIncantStandbyType() == null) // 긴급회복시스템 (STANDBY_D) 가 더 우선
            self().setNextIncantStandbyType(MoveType.STANDBY_B);
    }

}


