package com.gbf.granblue_simulator.battle.logic.move.character;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.exception.MoveValidationException;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.battle.logic.move.dto.DefaultMoveRequest;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusModifierType;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class HairaLogic extends DefaultCharacterMoveLogic {

    private final String gid = "3040502000";

    protected HairaLogic(CharacterMoveLogicDependencies dependencies) {
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
        moveLogicRegistry.register(supportAbilityKey(gid, 3), this::thirdSupportAbility);
    }

    protected MoveLogicResult normalAttack(MoveLogicRequest request) {
        Move attack = request.getMove();
        return resultMapper.fromDefaultResult(defaultAttack(attack));
    }

    // 근하신년: 적에게 데미지, 자신에게 오의봉인 재공격, 모든 어빌리티 쿨타임 2턴 단축
    protected MoveLogicResult chargeAttack(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        Actor self = chargeAttack.getActor();
        self.getFirstMove(MoveType.FIRST_ABILITY).modifyCooldown(-2);
        self.getFirstMove(MoveType.SECOND_ABILITY).modifyCooldown(-2);
        self.getFirstMove(MoveType.THIRD_ABILITY).modifyCooldown(-2);
        return resultMapper.fromDefaultResult(defaultChargeAttack(DefaultMoveRequest.from(chargeAttack)));
    }

    // 와룡봉희: 적에게 데미지, 자신이 지보의 황성 효과 부여중 스택에 비례해 자신에게 재행동, 적에게 감전, 지보의 황성 효과 삭제
    protected MoveLogicResult firstAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Actor self = ability.getActor();
        return checkCondition.hasEffect(self, "지보의 황성")
                .map(statusEffect -> {
                    StatusModifierType multiStrikeModifier = statusEffect.getLevel() >= 5 ? StatusModifierType.QUADRUPLE_STRIKE
                            : statusEffect.getLevel() >= 3 ? StatusModifierType.TRIPLE_STRIKE
                            : StatusModifierType.DOUBLE_STRIKE; // 스택 1~2 2회, 3~4 3회, 5 4회
                    List<BaseStatusEffect> selectedBaseStatusEffects = ability.getBaseMove().getBaseStatusEffects().stream() // !(버프 && 선택된 다회행동이 아닌경우)
                            .filter(status -> !(status.getType() == StatusEffectType.BUFF && status.getModifiers().get(multiStrikeModifier) == null))
                            .toList();
                    setStatusLogic.removeStatusEffect(self, statusEffect); // 지보의 황성 삭제
                    return resultMapper.fromDefaultResult(defaultAbility(DefaultMoveRequest.withSelectedBaseStatusEffects(ability, selectedBaseStatusEffects)));
                })
                .orElseThrow(() -> new MoveValidationException("지보의 황성 스택이 부족해 어빌리티를 사용할 수 없습니다.", true));
    }

    // 운룡증변: 아군 전체에 운룡 효과
    protected MoveLogicResult secondAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 잠룡천상: 자신에게 감싸기, 회피율상승, 마운트, 자신 이외의 아군에 재행동
    protected MoveLogicResult thirdAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 진신궁의 주인: 패시브 [BATTLE_START]
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 진신궁의 희구: 아군이 2회 이상 행동할때마다 자신에게 지보의 황성 레벨 상승, 오의게이지 20% 증가 [CHARACTER_STRIKE_END]
    protected MoveLogicResult secondSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        MoveLogicResult otherResult = request.getOtherResult();
        Actor otherResultActor = otherResult.getMainActor();
        if (checkCondition.executedStrikeMoreThan(otherResultActor, 2)) {
            chargeGaugeLogic.modifyChargeGauge(ability.getActor(), 20);
            return resultMapper.fromDefaultResult(defaultAbility(ability));
        } else {
            return resultMapper.emptyResult();
        }
    }

    // 동남동의 수호신: 자신이 공격행동시, 두번째 공격행동부터 자신에게 어쌔신 효과 [SELF_STRIKE_START]
    protected MoveLogicResult thirdSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Actor self = ability.getActor();
        if (checkCondition.executedStrikeMoreThan(self, 1)) {
            defaultAbility(DefaultMoveRequest.from(ability));
        }
        return resultMapper.emptyResult(); // 결과 반환 없음
    }

}
