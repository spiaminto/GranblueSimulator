package com.gbf.granblue_simulator.battle.logic.move.character;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.*;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusEffectResult;
import com.gbf.granblue_simulator.battle.logic.util.TrackingConditionUtil;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.move.TrackingCondition;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectTargetType;
import com.gbf.granblue_simulator.metadata.service.BaseMoveService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@Transactional
public class YachimaLogic extends DefaultCharacterMoveLogic {

    private final String gid = "3040566000";
    private final BaseMoveService baseMoveService;

    protected YachimaLogic(CharacterMoveLogicDependencies dependencies, BaseMoveService baseMoveService) {
        super(dependencies);
        registerLogics();
        this.baseMoveService = baseMoveService;
    }

    protected void registerLogics() {
        moveLogicRegistry.register(normalAttackKey(gid), this::normalAttack);
        moveLogicRegistry.register(chargeAttackKey(gid), this::chargeAttack);
        moveLogicRegistry.register(abilityKey(gid, 1), this::firstAbility);
        moveLogicRegistry.register(abilityKey(gid, 2), this::secondAbility);
        moveLogicRegistry.register(abilityKey(gid, 3), this::thirdAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 1), this::firstSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 2), this::secondSupportAbility);
        moveLogicRegistry.register(triggerAbilityKey(gid, 1), this::firstTriggerAbility);
    }

    protected MoveLogicResult normalAttack(MoveLogicRequest request) {
        Move attack = request.getMove();
        return resultMapper.fromDefaultResult(defaultAttack(attack));
    }

    // 자신의 첫번째 어빌리티 자동발동
    protected MoveLogicResult chargeAttack(MoveLogicRequest request) {
        return resultMapper.fromDefaultResult(defaultChargeAttack(request.getMove()));
    }

    // [REACT_SELF] 적의 공격력, 방어력 감소(누적)
    // ◆레코데이션 싱크 효과중, 약화효과 부여횟수 2배
    protected MoveLogicResult firstAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Actor self = ability.getActor();
        if (checkCondition.triggered(request) && !checkCondition.isMoveParentType(request.getOtherResult(), MoveType.CHARGE_ATTACK)) return resultMapper.emptyResult();

        List<BaseStatusEffect> toApplyEffects = new ArrayList<>(ability.getBaseMove().getBaseStatusEffects());
        toApplyEffects.addAll(checkCondition.hasEffect(self, "레코데이션 싱크")
                .map(effect -> ability.getBaseMove().getBaseStatusEffects())
                .orElse(Collections.emptyList()));

        return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.withSelectedBaseStatusEffects(ability, toApplyEffects)));
    }

    protected MoveLogicResult secondAbility(MoveLogicRequest request) {
        return resultMapper.fromDefaultResult(defaultAbility(request.getMove()));
    }

    protected MoveLogicResult thirdAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Actor self = ability.getActor();
        StatusEffectTargetType applyEffectTargetType = checkCondition.hasEffect(self, "레코데이션 싱크").isPresent()
                ? StatusEffectTargetType.PARTY_MEMBERS : StatusEffectTargetType.SELF; // 상태효과, 턴진행 없이 통상공격 실행 타겟

        // 타겟에 맞게 상태효과 적용
        List<Actor> statusEffectTargets = applyEffectTargetType == StatusEffectTargetType.SELF ? List.of(self) : battleContext.getFrontCharacters();
        SetStatusEffectResult setStatusEffectResult = setStatusLogic.setStatusEffect(SetEffectRequest.withSelectedTargets(ability.getBaseMove().getBaseStatusEffects(), statusEffectTargets));

        // 기본 어빌리티 수행 (상태효과는 빈 리스트)
        defaultAbility(DefaultMoveRequest.withSelectedBaseStatusEffects(ability, Collections.emptyList()));

        return resultMapper.toResult(ResultMapperRequest.builder()
                .move(ability)
                .damageLogicResult(null)
                .setStatusEffectResult(setStatusEffectResult)
                .executeOptions(ResultMapperRequest.ExecuteOptions.attack(applyEffectTargetType))
                .build());
    }

    // [REACT_CHARACTER] 아군 전체가 적에게 누적 100회 데미지를 입힐때 마다 자신의 알파레벨 1 상승
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Actor self = ability.getActor();
        if (checkCondition.hasEffectLevel(self, "알파", 4).isPresent()) return resultMapper.emptyResult();

        int currentValue = TrackingConditionUtil.getInt(ability.getConditionTracker(), TrackingCondition.HIT_COUNT_BY_CHARACTER_ACC);
        int threshold = TrackingConditionUtil.getInt(ability.getBaseMove().getConditionTracker(), TrackingCondition.HIT_COUNT_BY_CHARACTER_ACC);
        if (currentValue < threshold) return resultMapper.emptyResult();

        TrackingConditionUtil.subtractCondition(ability.getConditionTracker(), TrackingCondition.HIT_COUNT_BY_CHARACTER_ACC, threshold);
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 턴 종료시 알파 레벨이 4 일때, 효과를 전체화 [/]자신에게 레코데이션 싱크 효과, 3번째 어빌리티 쿨타임 초기화
    protected MoveLogicResult secondSupportAbility(MoveLogicRequest request) {
        Actor self = request.getMove().getActor();
        if (checkCondition.hasEffect(self, "레코데이션 싱크").isPresent()
                || !checkCondition.isEffectMaxLevel(self, "알파")) {
            return resultMapper.emptyResult();
        }

        // 자신을 포함한 아군 전체에게 알파, 효과 재적용 (타겟 아군 전체로 변경)
        List<BaseStatusEffect> firstSupportAbilityStatusEffects = baseMoveService.findByLogicId(supportAbilityKey(gid, 1)).getBaseStatusEffects();

        SetStatusEffectResult setAlphaResult = setStatusLogic.setStatusEffect(SetEffectRequest
                .builder()
                .selectedTargets(battleContext.getFrontCharacters())
                .baseStatusEffects(firstSupportAbilityStatusEffects)
                .targetLevel(4)
                .build());

        DefaultMoveLogicResult defaultMoveLogicResult = defaultAbility(request.getMove()); // 자신에게 레코데이션 싱크 적용
        defaultMoveLogicResult.getSetStatusEffectResult().merge(setAlphaResult);

        // 자신의 3어빌 쿨타임 0으로 감소
        self.getFirstMove(MoveType.THIRD_ABILITY).updateCooldown(0);

        // 트리거어빌리티 추가
        saveTriggeredMove(List.of(self), triggerAbilityKey(gid, 1));

        // 레코데이션 싱크 적용
        return resultMapper.fromDefaultResult(defaultMoveLogicResult);
    }

    // 자신이 레코데이션 싱크 효과중 통상공격 후 5배 데미지 3회, 방어력 다운 [REACT_SELF]
    protected MoveLogicResult firstTriggerAbility(MoveLogicRequest request) {
        return checkCondition.isMoveParentType(request.getOtherResult(), MoveType.ATTACK)
                && checkCondition.hasEffect(request.getMove().getActor(), "레코데이션 싱크").isPresent()
                ? resultMapper.fromDefaultResult(defaultAbility(request.getMove()))
                : resultMapper.emptyResult();
    }


}
