package com.gbf.granblue_simulator.battle.logic.move.enemy;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.dto.DefaultMoveRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.ResultMapperRequest;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusEffectResult;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseEnemy;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.metadata.domain.visual.ActorVisual;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static com.gbf.granblue_simulator.battle.logic.util.StatusUtil.*;

@Slf4j
@Transactional
@Component
public class Diaspora1Logic extends DefaultEnemyMoveLogic {

    private final int ACTIVATE_VALUE = 1500000;
    private final String gid = "4300903";

    protected Diaspora1Logic(EnemyMoveLogicDependencies enemyMoveLogicDependencies) {
        super(enemyMoveLogicDependencies);
        registerLogics();
    }

    protected void registerLogics() {
        moveLogicRegistry.register(normalAttackKey(gid), this::normalAttack);
        moveLogicRegistry.register(supportAbilityKey(gid, 1), this::firstSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 2), this::secondSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 3), this::thirdSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 4), this::fourthSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 5), this::fifthSupportAbility);
        moveLogicRegistry.register(chargeAttackKey(gid, "a"), this::chargeAttackA);
        moveLogicRegistry.register(chargeAttackKey(gid, "b"), this::chargeAttackB);
        moveLogicRegistry.register(chargeAttackKey(gid, "c"), this::chargeAttackC);
        moveLogicRegistry.register("stb_" + gid, this::triggerOmen);
    }

    protected MoveLogicResult normalAttack(MoveLogicRequest request) {
        Move attack = request.getMove();
        return resultMapper.fromDefaultResult(defaultAttack(attack));
    }

    // 전조 발생 [TURN_END_OMEN]
    public MoveLogicResult triggerOmen(MoveLogicRequest request) {
        Enemy self = (Enemy) battleContext.getEnemy();

        // 5의 배수턴 마다 자괴인자 발동
        if ((battleContext.getCurrentTurn() + 1) % 5 == 0) {
            self.updateNextIncantStandbyType(MoveType.STANDBY_B);
        }

        // 전조발생
        MoveLogicResult result = omenLogic.triggerOmen(self).map(standby -> {
            if (standby.getType() == MoveType.STANDBY_C && self.isPrevOmenSame(self.getOmen())) { // 긴급 수복모드, 전조값 이어가기
                omenLogic.manualUpdateOmenValue(self, self.getTransientPrevOmen().getRemainValues());
            }
            return resultMapper.toResult(ResultMapperRequest.from(standby));
        }).orElseGet(resultMapper::emptyResult);

        return result;
    }

    // 경성방사 : 적에게 랜덤 대상 5배 데미지 5회, 랜덤 디버프 2개 부여
    protected MoveLogicResult chargeAttackA(MoveLogicRequest request) {
        List<BaseStatusEffect> baseStatusEffects = new ArrayList<>(request.getMove().getBaseMove().getBaseStatusEffects());
        Collections.shuffle(baseStatusEffects);
        List<BaseStatusEffect> selectedStatusEffect = baseStatusEffects.subList(0, 2);
        return resultMapper.fromDefaultResult(defaultChargeAttack(DefaultMoveRequest.withSelectedBaseStatusEffects(request.getMove(), selectedStatusEffect)));
    }

    // 자괴인자: 적 전체에 10배 데미지, 자신의 자괴인자 레벨 상승
    protected MoveLogicResult chargeAttackB(MoveLogicRequest request) {
        return resultMapper.fromDefaultResult(defaultChargeAttack(request.getMove()));
    }

    // 긴급 회복 시스템: 긴급 회복 시스템을 유지한다. (효과 없음)
    protected MoveLogicResult chargeAttackC(MoveLogicRequest request) {
        return resultMapper.fromDefaultResult(defaultChargeAttack(request.getMove()));
    }

    // 전투 시작시 자신에게 활성레벨 알파 / 베타 / 감마 부여, 인자발생 부여 [BATTLE_START]
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        Enemy self = (Enemy) request.getMove().getActor();
        // CHECK 다른 보스들과 다르게 디아스포라의 경우 타인의 보스가 폼체인지 후라도, 반드시 긴급수복모드를 해제해야 폼체인지 되도록 설계됨

        // 1. 서포어비1 -> 전투 시작시 자신에게 활성, 자괴인자 버프
        defaultAbility(request.getMove());

        // 2. 자신의 활성레벨 갱신 (프론트 표시 없이 내부적으로 한번에 다 올림)
        // 알파
        StatusEffect modeAlphaStatusEffect = getEffectByName(self, "활성『알파』").orElseThrow(() -> new IllegalArgumentException("[processBattleStart] 활성 알파 없음"));
        int attackDamageSum = battleLogService.getEnemyTakenDamageSumByMoveType(MoveType.ATTACK, true);
        int levelFromAttackDamageSum = attackDamageSum / ACTIVATE_VALUE + 1;
        if (levelFromAttackDamageSum > 1)
            setStatusLogic.addStatusEffectsLevel(self, levelFromAttackDamageSum - 1, modeAlphaStatusEffect);
        // 베타
        StatusEffect modeBetaStatusEffect = getEffectByName(self, "활성『베타』").orElseThrow(() -> new IllegalArgumentException("[processBattleStart] 활성 베타 없음"));
        int abilityDamageSum = battleLogService.getEnemyTakenDamageSumByMoveType(MoveType.ABILITY, true);
        int levelFromAbilityDamageSum = abilityDamageSum / ACTIVATE_VALUE + 1;
        if (levelFromAbilityDamageSum > 1)
            setStatusLogic.addStatusEffectsLevel(self, levelFromAbilityDamageSum - 1, modeBetaStatusEffect);
        // 감마
        StatusEffect modeGammaStatusEffect = getEffectByName(self, "활성『감마』").orElseThrow(() -> new IllegalArgumentException("[processBattleStart] 활성 감마 없음"));
        int chargeAttackDamageSum = battleLogService.getEnemyTakenDamageSumByMoveType(MoveType.CHARGE_ATTACK, true);
        int levelFromChargeAttackDamageSum = chargeAttackDamageSum / ACTIVATE_VALUE + 1;
        if (levelFromChargeAttackDamageSum > 1)
            setStatusLogic.addStatusEffectsLevel(self, levelFromChargeAttackDamageSum - 1, modeGammaStatusEffect);

        // 3. 갱신된 활성버프를 기반으로 서포어비 3 발동 (최대활성시, 타 활성레벨 삭제 후 긴급수복모드 이행)
        // CHECK 원래 이렇게 Actor 의존적으로 사용하면 안됨
        thirdSupportAbility(MoveLogicRequest.of(self.getFirstMove(MoveType.THIRD_SUPPORT_ABILITY), null));

        // 4. 전조 발생가능시 발생 (긴급 수복모드인 경우만 상정)
        MoveLogicResult result = omenLogic.triggerOmen(self)
                .map(standby -> resultMapper.toResult(ResultMapperRequest.from(standby)))
                .orElseGet(resultMapper::emptyResult);

        log.info("[processBattleStart] levelFromAttackDamageSum = {}, levelFromAbilityDamageSum = {}, levelFromChargeAttackDamageSum = {}", levelFromAttackDamageSum, levelFromAbilityDamageSum, levelFromChargeAttackDamageSum);
        return result;
    }

    // [REACT_CHARACTER] 자신이 입은 일반공격 / 어빌리티 / 오의데미지의 누적값이 N 에 도달시 자신의 알파 / 베타 / 감마 레벨 증가
    protected MoveLogicResult secondSupportAbility(MoveLogicRequest request) {
        MoveType otherMoveParentType = request.getOtherResult().getMove().getType().getParentType();
        Move ability = request.getMove();
        Actor self = ability.getActor();

        // 공격 타입에 따른 활성 상태효과 이름
        String matchingStatusName = switch (otherMoveParentType) {
            case ATTACK -> "활성『알파』";
            case ABILITY -> "활성『베타』";
            case CHARGE_ATTACK -> "활성『감마』";
            default -> "없음";
        };
        log.info("[secondSupportAbility] matchingStatusName = {}", matchingStatusName);
        if (matchingStatusName.equals("없음")) return resultMapper.emptyResult();

        // 해당 활성효과 확인
        StatusEffect matchedStatusEffect = getEffectByName(self, matchingStatusName).orElse(null);
        log.info("[secondSupportAbility] matchedStatusEffect = {}", matchedStatusEffect);
        if (matchedStatusEffect == null || matchedStatusEffect.isMaxLevel())
            return resultMapper.emptyResult(); // 긴급 수복 모드 전조 발생 등으로 이미 제거됨 || 이미 최고레벨

        // 현재까지 받은 데미지에 따른 타겟 레벨, 레벨 차
        int takenDamageSum = battleLogService.getEnemyTakenDamageSumByMoveType(otherMoveParentType, true);
        int levelFromTakenDamage = takenDamageSum / ACTIVATE_VALUE + 1; // 상태 효과가 레벨 1부터 시작하므로 +1
        int levelDiff = levelFromTakenDamage - matchedStatusEffect.getLevel();
        log.info("[secondSupportAbility] levelFromTakenDamage = {}, levelDiff = {}", levelFromTakenDamage, levelDiff);
        if (levelDiff <= 0) return resultMapper.emptyResult(); // 레벨상승 없음

        // 레벨 상승
        if (levelDiff > 1) // 차이가 1보다 크면, 초과분은 직접레벨업
            setStatusLogic.addStatusEffectsLevel(self, levelDiff - 1, matchedStatusEffect);
        SetStatusEffectResult setStatusEffectResult = setStatusLogic.setStatusEffect(List.of(matchedStatusEffect.getBaseStatusEffect()));
        log.info("[secondSupportAbility] setStatusEffectResult = {}", setStatusEffectResult);
        return resultMapper.toResult(ResultMapperRequest.of(ability, setStatusEffectResult));
    }

    // 어느 하나의 활성 레벨이 최고레벨이 된 턴 종료시 긴급 수복 모드 전조 발생, 최고레벨 활성 제외 제거 [TURN_END]
    protected MoveLogicResult thirdSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Enemy self = (Enemy) ability.getActor();
        List<StatusEffect> activateStatuses = new ArrayList<>(getEffectsByNameContains(self, "활성『"));
        return activateStatuses.stream()
                .filter(StatusEffect::isMaxLevel)
                .max(Comparator.comparing(StatusEffect::getUpdatedAt))
                .map(activatedEffect -> {
                    // 전환활 활성효과를 제외한 제거목록 설정
                    activateStatuses.remove(activatedEffect);
                    // 고유버프 '인자 발생', 모드 『활성』 있을시 제거 목록에 추가
                    getEffectByName(self, "인자 발생").ifPresent(activateStatuses::add);
                    getEffectByName(self, "모드『활성』").ifPresent(activateStatuses::add);
                    // 전환할 활성 남기고 제거
                    SetStatusEffectResult setStatusEffectResult = setStatusLogic.removeStatusEffectsWithResult(self, activateStatuses);
                    // 긴급 수복모드 부여
                    SetStatusEffectResult secondSupportAbilityStatusResult = setStatusLogic.setStatusEffect(ability.getBaseMove().getBaseStatusEffects());
                    setStatusEffectResult.merge(secondSupportAbilityStatusResult);
                    // 긴급수복모드 발동
                    self.updateNextIncantStandbyType(MoveType.STANDBY_C);
                    return resultMapper.toResult(ResultMapperRequest.of(ability, setStatusEffectResult));
                })
                .orElseGet(resultMapper::emptyResult);
    }

    // 긴급 수복모드 종료시 자신에게 남아있는 활성레벨 중 가장 높은 활성 레벨의 모드로 전환, 자신의 모든 디버프 해제, 폼 체인지 [REACT_CHARACTER]
    protected MoveLogicResult fourthSupportAbility(MoveLogicRequest request) {
        if (!checkCondition.isEnemyBreak(request.getOtherResult(), MoveType.STANDBY_C))
            return resultMapper.emptyResult();

        Move ability = request.getMove();
        Enemy self = (Enemy) ability.getActor();
        BaseMove baseMove = ability.getBaseMove();

        // 1. 현재 활성 제거
        StatusEffect currentActivateStatus = getEffectByNameContains(self, "활성").orElseThrow(() -> new IllegalStateException("[fourthSupportAbility] 모드 전환에 필요한 활성효과 없음"));
        String currentActivateStatusName = currentActivateStatus.getBaseStatusEffect().getName();
        String currentActivateStatusNameType = currentActivateStatusName.substring(currentActivateStatusName.indexOf("『"), currentActivateStatusName.indexOf("』")); // "활성『알파』" 에서 "『알파" 만 남김.
        SetStatusEffectResult removeActivateResult = setStatusLogic.removeStatusEffectsWithResult(self, currentActivateStatus);

        // 2. 활성 효과에 맞는 모드 적용
        BaseStatusEffect modeBaseStatusEffect = getBaseEffectByNameContains(baseMove, currentActivateStatusNameType);
        SetStatusEffectResult setModeResult = setStatusLogic.setStatusEffect(List.of(modeBaseStatusEffect));
        // 2.1 2회차 전조부터 붙어있는 긴급 회복 시스템 효과 제거
        SetStatusEffectResult removeEmergencyRecoveryResult = getEffectByName(self, "긴급 회복 시스템").map(statusEffect -> setStatusLogic.removeStatusEffectsWithResult(self, statusEffect)).orElse(null);

        // 3.병합
        removeActivateResult.merge(setModeResult, removeEmergencyRecoveryResult);

        // 4.폼 체인지
        BaseEnemy currentBaseEnemy = (BaseEnemy) self.getBaseActor();
        String rootNameEn = currentBaseEnemy.getRootNameEn();
        BaseEnemy nextBaseEnemy = baseActorService.findByRootNameEn(rootNameEn).stream().filter(baseEnemy -> baseEnemy.getFormOrder() == 2).findAny().orElseThrow(() -> new IllegalArgumentException("다음 폼 없음"));

        // 4.1 자신의 Move 교체
        List<Move> currentBaseEnemyMoves = self.getMoves().stream()
                .filter(move -> currentBaseEnemy.getDefaultMoveIds().contains(move.getBaseMove().getId()))
                .toList();
        moveService.deleteAll(currentBaseEnemyMoves);
        self.removeMoves(currentBaseEnemyMoves);
        List<BaseMove> nextDefaultBaseMoves = baseMoveService.findAllByIds(nextBaseEnemy.getDefaultMoveIds());
        List<Move> nextDefaultMoves = nextDefaultBaseMoves.stream().map(nextEnemyBaseMove -> Move.fromBaseMove(nextEnemyBaseMove).mapActor(self)).toList();
        moveService.saveAll(nextDefaultMoves);
        self.addMoves(nextDefaultMoves);

        // 4.2 자신의 BaseActor 교체
        self.updateBaseActor(nextBaseEnemy);
        
        // 4.3 자신의 Visual 교체
        ActorVisual nextActorVisual = nextBaseEnemy.getDefaultVisual();
        self.updateActorVisual(nextActorVisual);
        self.updateCurrentForm(nextBaseEnemy.getFormOrder());
        
        // 4.4 다음 폼의 인자방출 영창기 등록
        self.updateNextIncantStandbyType(MoveType.STANDBY_D);

        return resultMapper.toResult(ResultMapperRequest.builder()
                .move(ability)
                .setStatusEffectResult(removeActivateResult)
                .executeOptions(ResultMapperRequest.ExecuteOptions.enemyFormChange())
                .build());
    }

    // 전조 자괴인자 해제시 자신의 자괴인자 효과레벨 1 감소 [REACT_CHARACTER]
    protected MoveLogicResult fifthSupportAbility(MoveLogicRequest request) {
        if (!checkCondition.isEnemyBreak(request.getOtherResult(), MoveType.STANDBY_B))
            return resultMapper.emptyResult();
        Move ability = request.getMove();
        Actor self = ability.getActor();
        SetStatusEffectResult setStatusEffectResult = getEffectByName(self, "자괴인자")
                .map(battleStatus -> setStatusLogic.subtractStatusEffectLevel(self, 1, battleStatus))
                .orElse(null);
        return resultMapper.toResult(ResultMapperRequest.of(ability, setStatusEffectResult));
    }
}
