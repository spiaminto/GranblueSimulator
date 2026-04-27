package com.gbf.granblue_simulator.battle.logic.move.enemy;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.*;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusEffectResult;
import com.gbf.granblue_simulator.battle.service.BattleLogDamageSumDto;
import com.gbf.granblue_simulator.metadata.domain.actor.BaseEnemy;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.metadata.domain.visual.ActorVisual;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

import static com.gbf.granblue_simulator.battle.logic.util.StatusUtil.*;

@Slf4j
@Transactional
@Component
public class Diaspora1Logic extends DefaultEnemyMoveLogic {

    private final int ACTIVATE_VALUE = 2250000; // 활성 효과 상승 누적 데미지
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

    // 긴급 회복 시스템: 자신의 HP 1010101 회복
    protected MoveLogicResult chargeAttackC(MoveLogicRequest request) {
        return resultMapper.fromDefaultResult(defaultChargeAttack(request.getMove()));
    }

    // 전투 시작시 자신에게 인자발생, 무적, 활성레벨 알파 / 감마 부여 [BATTLE_START]
    // CHECK 다른 보스들과 다르게 디아스포라의 경우 타인의 보스가 폼체인지 후라도, 반드시 긴급수복모드를 해제해야 폼체인지 되도록 설계됨
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        Enemy self = (Enemy) request.getMove().getActor();

        // 1. 서포어비1 적용, (인자발생, 무적 만 / 활성효과는 아래에서 직접 적용)
        Map<Integer, List<BaseStatusEffect>> groupedEffects = request.getMove().getBaseMove().getEffectsGroupByApplyOrder();
        DefaultMoveLogicResult defaultResult = defaultAbility(DefaultMoveRequest.withSelectedBaseStatusEffects(request.getMove(), groupedEffects.get(0)));

        // 2. 활성효과 부여
        BattleLogDamageSumDto enemyTakenDamageSum = battleLogService.getEnemyTakenDamageSumByMoveType(List.of(MoveType.ATTACK, MoveType.CHARGE_ATTACK), true);
        // 알파
        List<BaseStatusEffect> alphaEffect = groupedEffects.get(1);
        int attackDamageSum = enemyTakenDamageSum.getAttackDamageSum();
        int levelFromAttackDamageSum = attackDamageSum / ACTIVATE_VALUE + 1;
        SetStatusEffectResult alphaResult = setStatusLogic.setStatusEffect(SetEffectRequest.builder()
                .baseStatusEffects(alphaEffect)
                .targetLevel(levelFromAttackDamageSum)
                .build());
        // 감마
        List<BaseStatusEffect> gammaEffect = groupedEffects.get(2);
        int chargeAttackDamageSum = enemyTakenDamageSum.getChargeAttackDamageSum();
        int levelFromChargeAttackDamageSum = chargeAttackDamageSum / ACTIVATE_VALUE + 1;
        SetStatusEffectResult gammaResult = setStatusLogic.setStatusEffect(SetEffectRequest.builder()
                .baseStatusEffects(gammaEffect)
                .targetLevel(levelFromChargeAttackDamageSum)
                .build());
        // 머지
        log.debug("[processBattleStart] levelFromAttackDamageSum = {}, levelFromChargeAttackDamageSum = {}", levelFromAttackDamageSum, levelFromChargeAttackDamageSum);
        defaultResult.getSetStatusEffectResult().merge(alphaResult, gammaResult);

        // 3. 활성버프를 기반으로 서포어비 3 발동 시도 (최대활성시, 타 활성레벨 삭제 후 긴급수복모드 이행)
        MoveLogicResult thirdSupportAbilityResult = thirdSupportAbility(MoveLogicRequest.of(self.getFirstMove(MoveType.THIRD_SUPPORT_ABILITY), null)); // 내부에서 최대 활성시, STANDBY_C 등록함.
        MoveLogicResult recoveryModeResult = null; // 서포어비 3 발동시 반환할 결과
        if (self.getNextIncantStandbyType() == MoveType.STANDBY_C) {
            // 서포어비 3 발동해서 긴급수복모드 전조 발생시 해당 결과에 상태효과 결과 옮김, 결과 생성
            List<StatusEffectDto> thirdSupportAbilityAddedEffects = thirdSupportAbilityResult.getSnapshots().get(self.getId()).getAddedStatusEffects();
            List<StatusEffectDto> thirdSupportAbilityRemovedEffects = thirdSupportAbilityResult.getSnapshots().get(self.getId()).getRemovedStatusEffects();
            Map<Long, SetStatusEffectResult.Result> fromThirdSupportAbilityResult = Map.of(
                    self.getId(),
                    SetStatusEffectResult.Result.builder()
                            .actorId(self.getId())
                            .addedStatusEffects(thirdSupportAbilityAddedEffects)
                            .removedStatusEffects(thirdSupportAbilityRemovedEffects)
                            .build());
            SetStatusEffectResult fromThirdSupportAbilitySetEffectResult = SetStatusEffectResult.builder().results(fromThirdSupportAbilityResult).build();
            recoveryModeResult = omenLogic.triggerOmen(self)
                    .map(standby -> resultMapper.toResult(ResultMapperRequest.of(standby, fromThirdSupportAbilitySetEffectResult)))
                    .orElse(null);
        }

        // 보여줄 결과 확정
        MoveLogicResult finalResult = recoveryModeResult != null ? recoveryModeResult : resultMapper.fromDefaultResult(defaultResult);

        return finalResult;
    }

    // [REACT_CHARACTER] 자신이 입은 일반공격, 오의데미지의 누적값이 N 에 도달시 자신의 알파, 감마 레벨 증가
    protected MoveLogicResult secondSupportAbility(MoveLogicRequest request) {
        MoveType otherMoveParentType = request.getOtherResult().getMove().getType().getParentType();
        if (otherMoveParentType != MoveType.ATTACK
                && otherMoveParentType != MoveType.CHARGE_ATTACK
                && !(otherMoveParentType == MoveType.ABILITY && checkCondition.isUsedAbility(request.getOtherResult(), battleContext.getCommandAbilityId()))
        ) {
            return resultMapper.emptyResult(); // 일반공격, 오의, 커맨드어빌리티 인경우가 아니면 실행하지 않음
        }
        Move ability = request.getMove();
        Actor self = ability.getActor();

        List<StatusEffect> activateEffects = getEffectsByNameContains(self, "활성『");

        SetStatusEffectResult displayResult = SetStatusEffectResult.emptyResult();
        BattleLogDamageSumDto enemyTakenDamageSum = battleLogService.getEnemyTakenDamageSumByMoveType(List.of(MoveType.ATTACK, MoveType.CHARGE_ATTACK), true);
        for (StatusEffect activateEffect : activateEffects) {
            boolean isAlphaActivate = activateEffect.getBaseStatusEffect().getName().contains("알파");
            // 현재까지 받은 데미지에 따른 타겟 레벨, 레벨 차
            int takenDamageSum = isAlphaActivate ? enemyTakenDamageSum.getAttackDamageSum() : enemyTakenDamageSum.getChargeAttackDamageSum(); // 알파, 감마만 있다고 상정
            int levelFromTakenDamage = takenDamageSum / ACTIVATE_VALUE + 1; // 상태 효과가 레벨 1부터 시작하므로 +1
            int levelDiff = levelFromTakenDamage - activateEffect.getLevel();
            log.debug("[secondSupportAbility] diaspora1 활성 레벨 처리중 statusEffect.name = {}, takenDamageSum = {}, levelFromTakenDamage = {}, levelDiff = {}", activateEffect.getBaseStatusEffect().getName(), takenDamageSum, levelFromTakenDamage, levelDiff);
            if (levelDiff <= 0) continue; // 레벨상승 없음

            // 레벨 상승
            if (levelDiff > 1) // 차이가 1보다 크면, 초과분은 직접레벨업
                setStatusLogic.addStatusEffectsLevel(self, levelDiff - 1, activateEffect);
            SetStatusEffectResult setStatusEffectResult = setStatusLogic.setStatusEffect(List.of(activateEffect.getBaseStatusEffect()));
            log.debug("[secondSupportAbility] diaspora1 활성레벨 결과 setStatusEffectResult = {}", setStatusEffectResult);

            String matchingStatusName = otherMoveParentType == MoveType.ATTACK ? "활성『알파』"
                    : otherMoveParentType == MoveType.CHARGE_ATTACK ? "활성『감마』"
                      : "없음";
            if (matchingStatusName.equals(activateEffect.getBaseStatusEffect().getName())) {
                displayResult.merge(setStatusEffectResult); // 현재 행동타입과 맞는 효과가 상승한경우, 결과 보여주기 위해 merge
            }
        }

        return displayResult.getResults().isEmpty()
                ? resultMapper.emptyResult()
                : resultMapper.toResult(ResultMapperRequest.of(ability, displayResult));
    }

    // 어느 하나의 활성 레벨이 최고레벨이 된 턴 종료시 긴급 수복 모드 전조 발생, 최고레벨 활성 제외 제거 [TURN_END]
    protected MoveLogicResult thirdSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Enemy self = (Enemy) ability.getActor();
        List<StatusEffect> activateStatuses = new ArrayList<>(getEffectsByNameContains(self, "활성『"));
        return activateStatuses.stream()
                .filter(StatusEffect::isMaxLevel)
                .min(Comparator.comparing(StatusEffect::getUpdatedAt))
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

        // 3. 2회차 전조부터 붙어있는 긴급 회복 시스템 효과 제거, 무적 제거
        SetStatusEffectResult removeEmergencyRecoveryResult = getEffectByName(self, "긴급 회복 시스템").map(statusEffect -> setStatusLogic.removeStatusEffectsWithResult(self, statusEffect)).orElse(null);
        removeActivateResult.merge(setModeResult, removeEmergencyRecoveryResult);
        SetStatusEffectResult removeIneffectiveEffectResult = checkCondition.hasEffect(self, "무적").map(effect -> setStatusLogic.removeStatusEffectsWithResult(self, effect)).orElse(null);
        removeActivateResult.merge(removeIneffectiveEffectResult);

        // 4.폼 체인지
        BaseEnemy currentBaseEnemy = (BaseEnemy) self.getBaseActor();
        String rootNameEn = currentBaseEnemy.getRootNameEn();
        BaseEnemy nextBaseEnemy = baseEnemyService.findByRootNameEn(rootNameEn).stream().filter(baseEnemy -> baseEnemy.getFormOrder() == 2).findAny().orElseThrow(() -> new IllegalArgumentException("다음 폼 없음"));

        // 4.1 자신의 Move 교체
        List<Move> currentBaseEnemyMoves = self.getMoves().stream()
                .filter(move -> currentBaseEnemy.getDefaultMoveIds().contains(move.getBaseMove().getId()))
                .toList();
        moveService.deleteAll(currentBaseEnemyMoves);
        self.removeMoves(currentBaseEnemyMoves);

        List<BaseMove> nextDefaultBaseMoves = baseMoveService.findAllByIds(nextBaseEnemy.getDefaultMoveIds());
        Map<Long, MoveType> moveTypeById = nextBaseEnemy.getMappedMove().getMoveTypeById();
        List<Move> nextDefaultMoves = nextDefaultBaseMoves.stream().map(nextEnemyBaseMove ->
                Move.fromBaseMove(nextEnemyBaseMove)
                        .mapActor(self)
                        .mapType(moveTypeById.get(nextEnemyBaseMove.getId()))
        ).toList();
        moveService.saveAll(nextDefaultMoves);

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

}
