package com.gbf.granblue_simulator.logic.common;

import com.gbf.granblue_simulator.domain.battle.actor.Actor;
import com.gbf.granblue_simulator.domain.battle.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.domain.base.statuseffect.BaseStatusEffect;
import com.gbf.granblue_simulator.domain.base.statuseffect.StatusModifier;
import com.gbf.granblue_simulator.domain.base.statuseffect.StatusModifierType;
import com.gbf.granblue_simulator.domain.base.statuseffect.StatusEffectType;
import com.gbf.granblue_simulator.repository.StatusEffectRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static com.gbf.granblue_simulator.logic.common.StatusUtil.*;

/**
 * 직접 처리가 필요한 일부 스테이터스들의 효과를 처리
 */
@Component
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ProcessStatusLogic {

    private final ChargeGaugeLogic chargeGaugeLogic;
    private final StatusEffectRepository statusEffectRepository;

    @Data
    @Builder
    static class ProcessStatusLogicResult {
        private List<StatusEffect> addedStatusEffects; // A(오의 게이지 증가, 페이탈 체인 게이지 증가) 도 가능
        @Builder.Default
        private List<StatusEffect> removedStatusEffects = new ArrayList<>();
        private Integer healValue;
        private Integer damageValue;
    }

    public ProcessStatusLogicResult process(Actor targetActor, BaseStatusEffect effect) {
        return process(targetActor, effect, null);
    }

    public ProcessStatusLogicResult process(Actor targetActor, BaseStatusEffect effect, StatusModifierType selectedModifierType) {

        List<StatusEffect> addedStatusEffect = new ArrayList<>();
        List<StatusEffect> removedStatusEffects = new ArrayList<>();
        Integer healValue = null;
        Integer damageValue = null;

        List<StatusModifier> toProcessModifiers = effect.getStatusModifiers().values().stream()
                .filter(modifier -> modifier.getType().needPostProcess())
                .toList();

        for (StatusModifier modifier : toProcessModifiers) {
            // 지정된 타입이 있는경우 해당 타입만 처리
            if (selectedModifierType != null && selectedModifierType != modifier.getType()) continue;

            switch (modifier.getType()) {
                case ACT_CHARGE_GAUGE_UP:
                    addedStatusEffect.add(processChargeGaugeUpStatus(targetActor, modifier));
                    break;
                case ACT_CHARGE_GAUGE_DOWN:
                    addedStatusEffect.add(processChargeGaugeUpStatus(targetActor, modifier));
                    break;
                case ACT_WEAPON_BURST:
                    addedStatusEffect.add(processWeaponBurstStatus(targetActor, modifier));
                    break;
                case ACT_FATAL_CHAIN_GAUGE_UP:
                    addedStatusEffect.add(processFatalGaugeUpStatus(targetActor, modifier));
                    break;
                case ACT_FATAL_CHAIN_GAUGE_DOWN:
                    addedStatusEffect.add(processFatalGaugeUpStatus(targetActor, modifier));
                    break;
                case ACT_DISPEL:
                    removedStatusEffects.addAll(processDispel(targetActor, modifier));
                    break;
                case ACT_CLEAR:
                    removedStatusEffects.addAll(processClear(targetActor, modifier));
                    break;
                case ACT_HEAL:
                    int heal = processHeal(targetActor, modifier);
                    healValue = healValue == null ? heal : healValue + heal;
                    break;
                case ACT_DAMAGE, ACT_RATE_DAMAGE:
                    int damage = processStatusDamage(targetActor, modifier);
                    damageValue = damageValue == null ? damage : damageValue + damage;
                    break;
                default:
                    log.warn("[process] modifier.type = {} not supported", modifier.getType());
                    break;
            }
        }

        return ProcessStatusLogicResult.builder()
                .addedStatusEffects(addedStatusEffect)
                .removedStatusEffects(removedStatusEffects)
                .damageValue(damageValue)
                .healValue(healValue)
                .build();
    }


    /**
     * 페이탈 체인 게이지 업 스테이터스를 받아 표시용 BattleStatus 로 반환 (DB 저장 x)
     *
     * @param targetActor
     * @param fatalGaugeUpModifier
     * @return
     */
    protected StatusEffect processFatalGaugeUpStatus(Actor targetActor, StatusModifier fatalGaugeUpModifier) {
        chargeGaugeLogic.processFatalChainGaugeFromStatus(targetActor, fatalGaugeUpModifier.getBaseStatusEffect());

        return StatusEffect.getTransientStatusEffect(StatusEffectType.BUFF, "페이탈 체인 상승", targetActor);
    }

    /**
     * 오의 게이지 업 스테이터스를 받아 표시용 BattleStatus 로 반환 (DB 저장 x)
     *
     * @param targetActor
     * @param chargeGaugeUpModifier
     * @return
     */
    protected StatusEffect processChargeGaugeUpStatus(Actor targetActor, StatusModifier chargeGaugeUpModifier) {
        chargeGaugeLogic.processChargeGaugeFromStatus(targetActor, chargeGaugeUpModifier.getBaseStatusEffect());

        return StatusEffect.getTransientStatusEffect(StatusEffectType.BUFF, "오의 게이지 상승", targetActor);
    }


    /**
     * 웨폰버스트 스테이터스를 받아 표시용 BattleStatus 로 반환 (DB 저장 x)
     *
     * @param targetActor
     * @param weaponBurstModifier
     * @return
     */
    protected StatusEffect processWeaponBurstStatus(Actor targetActor, StatusModifier weaponBurstModifier) {
        chargeGaugeLogic.setChargeGauge(targetActor, 100);
        return StatusEffect.getTransientStatusEffect(StatusEffectType.BUFF, "오의 사용 가능", targetActor);
    }

    /**
     * 디스펠 스테이터스를 받아 디스펠로 삭제된 배틀 스테이터스 (버프) 를 반환
     *
     * @param target
     * @param dispelModifier
     * @return
     */
    protected List<StatusEffect> processDispel(Actor target, StatusModifier dispelModifier) {
        BaseStatusEffect dispelBaseStatusEffect = dispelModifier.getBaseStatusEffect();
        if (!dispelBaseStatusEffect.getStatusModifiers().containsKey(StatusModifierType.ACT_DISPEL))
            throw new IllegalArgumentException("디스펠 효과 없음 Status.id = " + dispelBaseStatusEffect.getId());
        return getEffectByModifierType(target, StatusModifierType.ACT_DISPEL_GUARD)
                .map(dispelGuardStatus -> {
                    // 디스펠 가드 성공
                    target.getStatusEffects().remove(dispelGuardStatus);
                    statusEffectRepository.delete(dispelGuardStatus);
                    return List.of(dispelGuardStatus);
                }).orElseGet(() -> {
                    List<StatusEffect> dispelledStatusEffects = target.getStatusEffects().stream()
                            .filter(status -> status.getBaseStatusEffect().getType().isBuff() && status.getBaseStatusEffect().isRemovable())
                            .sorted(Comparator.comparing(StatusEffect::getUpdatedAt).reversed())
                            .limit((int) dispelBaseStatusEffect.getStatusModifiers().get(StatusModifierType.ACT_DISPEL).getValue()) // 적의 dispel 은 99정도로 들어옴
                            .toList();
                    target.getStatusEffects().removeAll(dispelledStatusEffects);
                    statusEffectRepository.deleteAll(dispelledStatusEffects);
                    return dispelledStatusEffects;
                });
    }

    /**
     * 클리어 스테이터스를 받아 삭제된 배틀 스테이터스 (디버프) 를 반환
     *
     * @param target
     * @param clearModifier
     * @return
     */
    protected List<StatusEffect> processClear(Actor target, StatusModifier clearModifier) {
        BaseStatusEffect clearBaseStatusEffect = clearModifier.getBaseStatusEffect();
        if (!clearBaseStatusEffect.getStatusModifiers().containsKey(StatusModifierType.ACT_CLEAR))
            throw new IllegalArgumentException("클리어 효과 없음 Status.id = " + clearBaseStatusEffect.getId());
        List<StatusEffect> clearedStatusEffects = target.getStatusEffects().stream()
                .filter(status -> status.getBaseStatusEffect().getType().isDebuff() && status.getBaseStatusEffect().isRemovable())
                .sorted(Comparator.comparing(StatusEffect::getUpdatedAt).reversed())
                .limit((int) clearBaseStatusEffect.getStatusModifiers().get(StatusModifierType.ACT_CLEAR).getValue())
                .toList(); // 해제될 디버프 (클리어의 value 값 갯수만큼만 최근에 추가된 디버프부터 해제함)
        target.getStatusEffects().removeAll(clearedStatusEffects);
        statusEffectRepository.deleteAll(clearedStatusEffects);
        return clearedStatusEffects;
    }

    /**
     * ACT_HEAL effect 를 가진 스테이터스를 받아 힐 처리후 힐량을 반환
     * HEAL, HEAL_FOR_ALL, BUFF.TURN_RECOVERY 에서 사용
     *
     * @param target
     * @param healModifier
     * @return
     */
    protected int processHeal(Actor target, StatusModifier healModifier) {
        BaseStatusEffect healBaseStatusEffect = healModifier.getBaseStatusEffect();
        if (!healBaseStatusEffect.getStatusModifiers().containsKey(StatusModifierType.ACT_HEAL))
            throw new IllegalArgumentException("힐 이펙트가 없음 Status.id = " + healBaseStatusEffect.getId());
        Integer currentHp = target.getHp();
        int healInitValue = (int) healBaseStatusEffect.getStatusModifiers().get(StatusModifierType.ACT_HEAL).getValue();
        Integer healResultValue = StatusUtil.getEffectByModifierType(target, StatusModifierType.UNDEAD)
                .map(undeadBattleStatus ->
                        -1 * healInitValue // 언데드는 힐 상승 미적용
                ).orElseGet(() -> {
                    double healRate = target.getStatus().getStatusDetails().getCalcedHealRate();
                    return (int) (healInitValue * healRate);
                });
        int healedHp = currentHp + healResultValue;
        target.updateHp(healedHp);
//        log.info("[processHeal] battleActor.name = {} currentHp = {}, healInitValue = {}, resultHealRate = {}, healedHp = {}", target.getName(), currentHp, healInitValue, resultHealRate, healedHp);
        return healResultValue;
    }

    protected int processStatusDamage(Actor target, StatusModifier damageModifier) {
        BaseStatusEffect damageBaseStatusEffect = damageModifier.getBaseStatusEffect();
        StatusModifier constantDamageEffect = damageBaseStatusEffect.getStatusModifiers().get(StatusModifierType.ACT_DAMAGE);
        StatusModifier rateDamageEffect = damageBaseStatusEffect.getStatusModifiers().get(StatusModifierType.ACT_RATE_DAMAGE);
        if (constantDamageEffect == null && rateDamageEffect == null)
            throw new IllegalArgumentException("데미지 이펙트가 없음 Status.id = " + damageBaseStatusEffect.getId());

        Integer currentHp = target.getHp();
        int damage = constantDamageEffect != null
                ? (int) constantDamageEffect.getValue() // 고정데미지
                : (int) (target.getMaxHp() * rateDamageEffect.getValue()); // 비율은 최대체력 비율

        Integer damagedHp = Math.max(currentHp - damage, 0); // 하한 0
        target.updateHp(damagedHp);
//        log.info("[processStatusDamage] battleActor.name = {} currentHp = {}, healInitValue = {}, resultHealRate = {}, healedHp = {}", target.getName(), currentHp, healInitValue, resultHealRate, healedHp);
        return damage;
    }

    /**
     * 캐릭터에 어빌리티 봉인 효과가 있는경우 설정
     *
     * @param target
     * @param abilitySealedBaseStatusEffect
     */
    public void processAbilitySealed(Actor target, BaseStatusEffect abilitySealedBaseStatusEffect) {
        StatusModifier abilitySealedEffect = abilitySealedBaseStatusEffect.getStatusModifiers().get(StatusModifierType.ABILITY_SEALED);
        double abilitySealedType = abilitySealedEffect.getValue(); // 0:전체 1:공격 2:강화 3:약체 4:회복
        if (abilitySealedType != 1)
            throw new IllegalArgumentException("[processAbilitySealed] 미구현 상태, abilitySealedType = " + abilitySealedType);
//        target.sealAbilityCoolDown(MoveType.FIRST_ABILITY);
//        target.sealAbilityCoolDown(MoveType.SECOND_ABILITY);
//        target.sealAbilityCoolDown(MoveType.THIRD_ABILITY);
//        target.sealAbilityCoolDown(MoveType.FOURTH_ABILITY);
        //CHECK 나중에 어빌리티별로 잠그고 싶으면 구조를 좀 변경해야할듯
    }

}
