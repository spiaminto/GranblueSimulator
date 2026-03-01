package com.gbf.granblue_simulator.battle.logic.move.character;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.battle.logic.move.MoveLogicRequest;
import com.gbf.granblue_simulator.battle.logic.move.dto.*;
import com.gbf.granblue_simulator.battle.logic.statuseffect.SetStatusEffectResult;
import com.gbf.granblue_simulator.battle.logic.util.TrackingConditionUtil;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.move.TrackingCondition;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.BaseStatusEffect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Component
@Transactional
@Slf4j
public class WamdusLogic extends DefaultCharacterMoveLogic {

    private final String gid = "3040419000";

    protected WamdusLogic(CharacterMoveLogicDependencies dependencies) {
        super(dependencies);
        registerLogics();
    }

    protected void registerLogics() {
        moveLogicRegistry.register(normalAttackKey(gid), this::normalAttack);
        moveLogicRegistry.register(chargeAttackKey(gid), this::chargeAttack);
        moveLogicRegistry.register(abilityKey(gid, 1), this::firstAbility);
        moveLogicRegistry.register(abilityKey(gid, 2), this::secondAbility);
        moveLogicRegistry.register(abilityKey(gid, 3), this::thirdAbility);
        moveLogicRegistry.register(abilityKey(gid, 4), this::fourthAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 1), this::firstSupportAbility);
        moveLogicRegistry.register(supportAbilityKey(gid, 2), this::secondSupportAbility);
        moveLogicRegistry.register(triggerAbilityKey(gid, 1), this::firstTriggeredAbility);
    }

    // <구현> 4 어빌리티에 의해 발동시 자신의 체력 1000 회복
    protected MoveLogicResult normalAttack(MoveLogicRequest request) {
        Move normalAttack = request.getMove();
        List<BaseStatusEffect> toApplyEffects = checkCondition.hasEffect(normalAttack.getActor(), "『 벽 』의 월운")
                .map(effect -> normalAttack.getBaseMove().getBaseStatusEffects())
                .orElseGet(Collections::emptyList);
        return resultMapper.fromDefaultResult(defaultAttack(DefaultMoveRequest.withSelectedBaseStatusEffects(normalAttack, toApplyEffects)));
    }

    // 히도로조아: 적에게 12.5배 수속성 데미지, 아군 전체에 피격데미지 감소 효과
    // <구현> 1 어빌리티에 의해 텐타클 카르넬 효과시 효과를 소모하여 아군 전체에 피격 데미지 무효 (1회)
    // <구현> 4 어빌리티에 의해 발동시 자신의 체력 1000 회복
    protected MoveLogicResult chargeAttack(MoveLogicRequest request) {
        Move chargeAttack = request.getMove();
        Map<Integer, List<BaseStatusEffect>> effectsGroupByApplyOrder = chargeAttack.getBaseMove().getEffectsGroupByApplyOrder();
        List<BaseStatusEffect> toApplyEffects = effectsGroupByApplyOrder.get(0);
        Optional<StatusEffect> tentacleEffect = checkCondition.hasEffect(chargeAttack.getActor(), "텐타클 카르넬");
        if (tentacleEffect.isPresent()) {
            toApplyEffects.addAll(effectsGroupByApplyOrder.get(1));
            setStatusLogic.removeStatusEffect(chargeAttack.getActor(), tentacleEffect.get());
        }
        if (checkCondition.hasEffect(chargeAttack.getActor(), "『 벽 』의 월운").isPresent()) {
            toApplyEffects.addAll(effectsGroupByApplyOrder.get(2));
        }
        DefaultMoveLogicResult defaultResult = defaultChargeAttack(DefaultMoveRequest.withSelectedBaseStatusEffects(chargeAttack, toApplyEffects));
        chargeGaugeLogic.setChargeGauge(chargeAttack.getActor(), 0); // 200% 전부 소모
        return resultMapper.fromDefaultResult(defaultResult);
    }

    // 엠비언트 드레인: 적의 CT 1개와 자신을 제외한 아군의 오의게이지 15%를 흡수하여 자신의 오의게이지로 변환. 자신에게 텐타클 카르넬 효과, 재공격효과. 페이탈 체인 게이지 20% 상승 ◆자신의 오의 게이로 변환시 적의 CT 1개 당 30%, 아군의 오의게이지 15% 당 60% 로 변환. 적의 CT 전조 발생중 CT 흡수 불가
    // <수정> 독립사용, 쿨다운 9턴
    protected MoveLogicResult firstAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        Actor self = ability.getActor();
        Map<Integer, List<BaseStatusEffect>> effectsGroupByApplyOrder = ability.getBaseMove().getEffectsGroupByApplyOrder();

        // CT 흡수
        SetStatusEffectResult setStatusEffectResult = setStatusLogic.setStatusEffect(effectsGroupByApplyOrder.get(0));
        Map<Long, SetStatusEffectResult.Result> statusEffectResults = setStatusEffectResult.getResults();
        boolean isAbsorbed = statusEffectResults.get(battleContext.getEnemy().getId()).getAddedStatusEffects().stream()
                .allMatch(resultEffectDto -> resultEffectDto.getBaseId() != null); // 실제로 흡수효과 부여됫는지 확인
        int fromEnemyChargeGauge = isAbsorbed ? 30 : 0; // 흡수했다면 30

        // 아군 오의 게이지 흡수
        BaseStatusEffect partyChargeGaugeDownEffect = effectsGroupByApplyOrder.get(1).getFirst();
        int absorbedChargeGaugeSum = battleContext.getFrontCharacters().stream()
                .filter(character -> !character.getId().equals(self.getId()))
                .mapToInt(character -> {
                    int modifiedActual = chargeGaugeLogic.modifyChargeGauge(character, -15);
                    statusEffectResults.put(
                            character.getId(),
                            SetStatusEffectResult.Result.builder()
                                    .addedStatusEffects(List.of(StatusEffectDto.fromChargeGaugeEffect(
                                            StatusEffect.fromBaseEffect(partyChargeGaugeDownEffect, character), modifiedActual)
                                    )).build()
                    );
                    return modifiedActual;
                }).sum();

        // 적 CT 당 30 + 아군 흡수량의 4배만큼 자신의 오의 게이지 증가
        int chargeGaugeUp = fromEnemyChargeGauge + Math.abs(absorbedChargeGaugeSum) * 4;
        int actualChargeGaugeUp = chargeGaugeLogic.modifyChargeGauge(self, chargeGaugeUp);
        BaseStatusEffect chargeGaugeUpEffect = effectsGroupByApplyOrder.get(2).getFirst();
        statusEffectResults.put(
                self.getId(),
                SetStatusEffectResult.Result.builder()
                        .addedStatusEffects(new ArrayList<>(List.of(StatusEffectDto.fromChargeGaugeEffect(
                                StatusEffect.fromBaseEffect(chargeGaugeUpEffect, self), actualChargeGaugeUp)))
                        ).build()
        );

        // 자신에게 텐타클 카르넬 효과, 재공격 효과, 페이탈 체인 게이지 상승
        SetStatusEffectResult tentacleEffectResult = setStatusLogic.setStatusEffect(effectsGroupByApplyOrder.get(3));
        setStatusEffectResult.merge(tentacleEffectResult);

        // 후처리
        postProcessAbility(ability);

        log.info("[wamdus firstAbility] isAbsorbed = {}, absorbedSum = {}, acturalUp = {}", isAbsorbed, absorbedChargeGaugeSum, actualChargeGaugeUp);
        return resultMapper.toResult(ResultMapperRequest.of(ability, setStatusEffectResult));
    }

    // 보텍스 아트락스: 적의 강화효과를 2개 삭제, 자신에게 텐타클 포스 효과 ◆효과중 일반공격 데미지 50% 증가, 반드시 트리플 어택, 난격(4회) 효과, 재공격 효과
    // <수정> 독립사용, 쿨다운 9턴
    protected MoveLogicResult secondAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [CHARACTER_TURN_END] 이노센트 톡신: 적에게 1.5배 수속성 데미지 6회, 독, 극독 효과
    // <수정> 피데미지시 -> 아군 공격턴 종료시 해당 턴 중 아군의 트리플 어택이 4회 이상 발생시 자동발동
    protected MoveLogicResult thirdAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (checkCondition.triggered(request)) {
            int currentValue = TrackingConditionUtil.getInt(ability.getConditionTracker(), TrackingCondition.TRIPLE_ATTACK_COUNT);
            int threshold = TrackingConditionUtil.getInt(ability.getBaseMove().getConditionTracker(), TrackingCondition.TRIPLE_ATTACK_COUNT);
            if (currentValue < threshold) {
                return resultMapper.emptyResult();
            }
        }
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 『 벽 』의 월운: 자신에게 『 벽 』의 월운 효과 ◆효과중 방어력 300% 증가, 약화효과 내성 100% 증가, 피 데미지시 오의 게이지 상승률 200% 증가, 적대심 증가(타겟 확률 1.5배) 일반공격과 오의 사용시 자신의 체력 1000 회복 [재사용 불가]
    protected MoveLogicResult fourthAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // 루-도- 의 행복: 자신의 오의게이지 최대치를 200% 로 변경하고 200% 를 모두 채웠을시 오의 사용 가능. 최대 HP가 높은 대신 오의 게이지 상승률이 감소
    protected MoveLogicResult firstSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();

        saveTriggeredMove(List.of(ability.getActor()), triggerAbilityKey(gid, 1));

        return resultMapper.fromDefaultResult(defaultAbility(ability));
    }

    // [REACT_CHARACTER]<STATUS_PRE> 오의조건확인 효과: 자신의 오의게이지가 200% 일때, 레벨 2로 증가 (maxlevel 2), 아니면 레벨이 2 이상일때 1 감소
    protected MoveLogicResult firstTriggeredAbility(MoveLogicRequest request) {
        Actor self = request.getMove().getActor();
        checkCondition.hasEffect(self, "오의조건확인")
                .ifPresent(effect -> {
                    if (self.getChargeGauge() >= 200)
                        setStatusLogic.addStatusEffectsLevel(self, 1, effect); // 2이상 증가하지 않음
                    else if (effect.getLevel() >= 2)
                        // 오의 게이지가 200 미만 && 레벨이 2 이상
                        setStatusLogic.subtractStatusEffectLevel(self, 1, effect);
                });
        return resultMapper.emptyResult();
    }

    // [REACT_CHARACTER] 이외의『 벽 』: 페이탈 체인 발동시 아군 전체에 피격 데미지 감소 효과
    protected MoveLogicResult secondSupportAbility(MoveLogicRequest request) {
        Move ability = request.getMove();
        if (checkCondition.isMoveParentType(request.getOtherResult(), MoveType.FATAL_CHAIN))
            return resultMapper.fromDefaultResult(defaultAbility(ability));
        return resultMapper.emptyResult();
    }

}
