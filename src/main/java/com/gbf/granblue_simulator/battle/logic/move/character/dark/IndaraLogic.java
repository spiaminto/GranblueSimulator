package com.gbf.granblue_simulator.battle.logic.move.character.dark;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.battle.logic.move.character.CharacterMoveLogicDependencies;
import com.gbf.granblue_simulator.battle.logic.move.character.DefaultCharacterMoveLogic;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.dto.DefaultMoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.dto.DefaultMoveRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.StatusEffectDto;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

import static com.gbf.granblue_simulator.battle.logic.util.StatusUtil.*;

@Component
public class IndaraLogic extends DefaultCharacterMoveLogic {
    private final String gid = "3040569000";

    protected IndaraLogic(CharacterMoveLogicDependencies dependencies) {
        super(dependencies);
        registerLogics();
    }

    protected void registerLogics() {
        moveLogicRegistry.register(normalAttackKey(gid), this::normalAttack);
        moveLogicRegistry.register(chargeAttackKey(gid), this::chargeAttack);
        moveLogicRegistry.register(abilityKey(gid, 1), this::firstAbility);
        moveLogicRegistry.register(abilityKey(gid, 2), this::secondAbility);
        moveLogicRegistry.register(abilityKey(gid, 3), this::thirdAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 1), this::firstSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 2), this::secondSupportAbility);
        // moveLogicRegistry.register(supportAbilityKey(gid, 3), this::thirdSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 4), this::fourthSupportAbility);
    }

    protected MoveLogicResult normalAttack(MoveLogicRequest request) {
        Move attack = request.getMove();
        return resultMapper.fromDefaultResult(defaultAttack(attack));
    }

    // 근하신년: 데미지, 불휴활기 효과시간 2턴 연장
    protected MoveLogicResult chargeAttack(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        Actor self = chargeAttack.getActor();

        self.updateAbilityCooldowns(0, MoveType.FIRST_ABILITY, MoveType.SECOND_ABILITY);

        return resultMapper.fromDefaultResult(defaultChargeAttack(chargeAttack));
    }

    // 유도사미: 적에게 암속성 8배 데미지 / 연속공격 확률 감소 (누적식, 최대 15%) / 극독 레벨 1 상승
    protected MoveLogicResult firstAbility(MoveLogicRequest request) {
        Move ability = request.getMove();

        int hitCount = ability.getBaseMove().getHitCount();
        List<BaseStatusEffect> selectedStatusEffects = new ArrayList<>(ability.getBaseMove().getBaseStatusEffects());
        if (checkCondition.hasEffectLevel(battleContext.getEnemy(), "극독", 7).isPresent()) {
            hitCount *= 2;
            selectedStatusEffects.addAll(ability.getBaseMove().getBaseStatusEffects());
        }
        DefaultMoveLogicResult defaultResult = defaultAbility(DefaultMoveRequest.builder().move(ability).modifiedHitCount(hitCount).selectedBaseEffects(selectedStatusEffects).build());

        // 불휴활기 있을때, 사용카운트가 2회 미만인경우 쿨타임 초기화
        checkCondition.hasEffect(ability.getActor(), "불휴활기")
                .ifPresent(actor -> {
                    if (ability.getCurrentTurnUseCount() < 2) ability.updateCooldown(0);
                });
        return resultMapper.fromDefaultResult(defaultResult);
    }

    // 구불구불 긴 뱀: 적에게 1.0 X 6회 데미지 ◆적의 극독 레벨 7 이상시 히트수 2배
    protected MoveLogicResult secondAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        int baseHitCount = ability.getBaseMove().getHitCount();
        int hitCount = checkCondition.hasEffectLevel(battleContext.getEnemy(), "극독", 7).isPresent()
                ? baseHitCount * 2
                : baseHitCount;
        // 어빌리티 본 처리
        DefaultMoveLogicResult defaultResult = defaultAbility(DefaultMoveRequest.withHitCount(ability, hitCount));
        // 불휴활기 있을때, 사용카운트가 2회 미만인경우 쿨타임 초기화
        checkCondition.hasEffect(ability.getActor(), "불휴활기")
                .ifPresent(actor -> {
                    if (ability.getCurrentTurnUseCount() < 2) ability.updateCooldown(0);
                });
        return resultMapper.fromDefaultResult(defaultResult);
    }

    // 상산사세: 자신에게 불휴활기 효과
    // ◆불휴활기 효과중 일반공격 후
    protected MoveLogicResult thirdAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Actor self = ability.getActor();
        DefaultMoveLogicResult defaultResult = defaultAbility(ability);

        List<StatusEffect> kakkiEffects = getEffectsByName(self, "불휴활기"); // 여러개임
        if (!kakkiEffects.isEmpty()) {
            setStatusLogic.shortenStatusEffectsDuration(kakkiEffects, 2); // 2턴 감소
            if (kakkiEffects.getFirst().getDuration() == 0) {
                setStatusLogic.extendStatusEffectsDuration(kakkiEffects, 1); // 1턴 미만으로 줄어들지 않음
            }
        }
        return resultMapper.fromDefaultResult(defaultResult);
    }

    // 뱀신궁의 주인: 전투시작지 자신에게 불휴활기 효과 [BATTLE_START]
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.from(ability)));
    }

    // [TURN_FINISH]
    // 휴식의 때: 턴 종료시 자신의 불휴활기 효과가 해제될때, 자신에게 수면 효과 부여(해제불가) ◆수면 효과중 공격행동, 어빌리티사용이 불가능 / 받는 데미지 증가
    protected MoveLogicResult secondSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Actor self = ability.getActor();
        List<StatusEffectDto> removedStatusEffects = request.getOtherResult().getSnapshots().get(self.getId()).getRemovedStatusEffects();
        if (checkCondition.hasEffectInResult(removedStatusEffects, "불휴활기")) {
            return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.from(ability)));
        }
        return resultMapper.emptyResult();
    }

    // 야토노카미: 아군이 "극독" 효과를 부여하는 어빌리티 사용시 적의 흉역 레벨 상승 [REACT_CHARACTER]
    // <수정> 삭제
    protected MoveLogicResult thirdSupportAbility(MoveLogicRequest request) {
        if (!checkCondition.isMoveParentType(request.getOtherResult(), MoveType.ABILITY)) return resultMapper.emptyResult();

        Move ability = request.getMove();
        if (checkCondition.hasEffectInMove(request.getOtherResult().getMove(), "극독")) {
            return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.from(ability)));
        }
        return resultMapper.emptyResult();
    }

    // [TURN_END] 남남동의 수호신: 턴 종료시 적의 극독 레벨이 10일때 적에게 디스펠
    protected MoveLogicResult fourthSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Actor enemy = battleContext.getEnemy();
        return checkCondition.hasEffectLevel(enemy, "극독", 10)
                .flatMap(statusEffect -> checkCondition.targetDispelled(defaultAbility(DefaultMoveRequest.from(ability)), enemy)) // 디스펠 되었는지 확인
                .map(resultMapper::fromDefaultResult)
                .orElseGet(resultMapper::emptyResult);
    }
}
