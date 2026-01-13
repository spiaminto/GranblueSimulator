package com.gbf.granblue_simulator.battle.logic.statuseffect;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.battle.logic.actor.dto.ResultStatusEffectDto;
import com.gbf.granblue_simulator.battle.logic.system.ChargeGaugeLogic;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.*;
import com.gbf.granblue_simulator.metadata.repository.StatusEffectRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import static com.gbf.granblue_simulator.battle.logic.util.StatusUtil.*;

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
    public static class ProcessStatusLogicResult {
        private List<ResultStatusEffectDto> addedStatusEffects; // A(오의 게이지 증가, 페이탈 체인 게이지 증가) 도 가능
        @Builder.Default
        private List<ResultStatusEffectDto> removedStatusEffects = new ArrayList<>();
        private Integer healValue;
        private Integer damageValue;
    }

    public ProcessStatusLogicResult process(Actor targetActor, BaseStatusEffect effect) {
        return process(targetActor, effect, null);
    }

    /**
     * 단일 modifier 에 따른 처리
     * @param targetActor
     * @param effect
     * @param selectedModifierType
     * @return ProcessStatusLogicResult, 처리할 효과가 없다면 null 반환
     */
    public ProcessStatusLogicResult process(Actor targetActor, BaseStatusEffect effect, StatusModifierType selectedModifierType) {

        List<ResultStatusEffectDto> addedStatusEffect = new ArrayList<>();
        List<ResultStatusEffectDto> removedStatusEffects = new ArrayList<>();
        Integer healValue = null;
        Integer damageValue = null;

        // ACT_ 있는 modifier 만 처리
        List<StatusModifier> toProcessModifiers = effect.getStatusModifiers().values().stream()
                .filter(modifier -> modifier.getType().needPostProcess())
                .toList();
        if (toProcessModifiers.isEmpty()) return null; // 없으면 바로 종료

        for (StatusModifier modifier : toProcessModifiers) {
            // 지정된 타입이 있는경우 해당 타입만 처리
            if (selectedModifierType != null && selectedModifierType != modifier.getType()) continue;

            switch (modifier.getType()) {
                case ACT_CHARGE_GAUGE_UP:
                    addedStatusEffect.add(ResultStatusEffectDto.of(processChargeGaugeUpStatus(targetActor, modifier)));
                    break;
                case ACT_CHARGE_GAUGE_DOWN:
                    addedStatusEffect.add(ResultStatusEffectDto.of(processChargeGaugeDownStatus(targetActor, modifier)));
                    break;
                case ACT_WEAPON_BURST:
                    addedStatusEffect.add(ResultStatusEffectDto.of(processWeaponBurstStatus(targetActor, modifier)));
                    break;
                case ACT_FATAL_CHAIN_GAUGE_UP:
                    addedStatusEffect.add(ResultStatusEffectDto.of(processFatalGaugeUpStatus(targetActor, modifier)));
                    break;
                case ACT_FATAL_CHAIN_GAUGE_DOWN:
                    addedStatusEffect.add(ResultStatusEffectDto.of(processFatalGaugeDownStatus(targetActor, modifier)));
                    break;
                case ACT_DISPEL:
                    removedStatusEffects.addAll(processDispel(targetActor, modifier).stream().map(ResultStatusEffectDto::of).toList());
                    break;
                case ACT_CLEAR:
                    removedStatusEffects.addAll(processClear(targetActor, modifier).stream().map(ResultStatusEffectDto::of).toList());
                    break;
                case ACT_HEAL:
                    int heal = processHeal(targetActor, modifier);
                    if (heal >= 0) {
                        healValue = Objects.requireNonNullElse(healValue, 0) + heal;
                    } else {
                        // 언데드등의 효과로 힐값이 음수가 되었을시, 슬립 데미지로 매핑
                        damageValue = Objects.requireNonNullElse(damageValue, 0) + Math.abs(heal);
                    }
                    break;
                case ACT_DAMAGE, ACT_RATE_DAMAGE:
                    int damage = processStatusDamage(targetActor, modifier);
                    damageValue = Objects.requireNonNullElse(damageValue, 0) + damage;
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
     * 페이탈 체인 게이지 감소 스테이터스를 받아 표시용 BattleStatus 로 반환 (DB 저장 x)
     *
     * @param targetActor
     * @param fatalGaugeUpModifier
     * @return
     */
    protected StatusEffect processFatalGaugeDownStatus(Actor targetActor, StatusModifier fatalGaugeUpModifier) {
        chargeGaugeLogic.processFatalChainGaugeFromStatus(targetActor, fatalGaugeUpModifier.getBaseStatusEffect());

        return StatusEffect.getTransientStatusEffect(StatusEffectType.DEBUFF, "페이탈 체인 감소", targetActor);
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
     * 오의 게이지 다운 스테이터스를 받아 표시용 BattleStatus 로 반환 (DB 저장 x)
     *
     * @param targetActor
     * @param chargeGaugeUpModifier
     * @return
     */
    protected StatusEffect processChargeGaugeDownStatus(Actor targetActor, StatusModifier chargeGaugeUpModifier) {
        chargeGaugeLogic.processChargeGaugeFromStatus(targetActor, chargeGaugeUpModifier.getBaseStatusEffect());

        return StatusEffect.getTransientStatusEffect(StatusEffectType.DEBUFF, "오의 게이지 감소", targetActor);
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
        Integer currentHp = target.getHp();
        int healInitValue = (int) healBaseStatusEffect.getStatusModifiers().get(StatusModifierType.ACT_HEAL).getValue();
        boolean hasUndeadEffect = getEffectByModifierType(target, StatusModifierType.UNDEAD).isPresent(); // 언데드 있을경우 회복상승 적용 x, 회복량을 음수로
        boolean isForAllHealEffect = healBaseStatusEffect.getTargetType() == StatusEffectTargetType.ALL_PARTY_MEMBERS; // 참전자 힐인경우, 언데드 무효
        double healRate = hasUndeadEffect && !isForAllHealEffect ? -1 : target.getStatus().getStatusDetails().getCalcedHealRate();
        int resultHealValue = (int) (healInitValue * healRate);
        int healedHp = currentHp + resultHealValue;
        target.updateHp(healedHp);
//        log.info("[processHeal] battleActor.name = {} currentHp = {}, healInitValue = {}, resultHealRate = {}, healedHp = {}", target.getName(), currentHp, healInitValue, healRate, healedHp);
        return resultHealValue;
    }

    protected int processStatusDamage(Actor target, StatusModifier damageModifier) {
        BaseStatusEffect damageBaseStatusEffect = damageModifier.getBaseStatusEffect();
        StatusModifier constantDamageEffect = damageBaseStatusEffect.getStatusModifiers().get(StatusModifierType.ACT_DAMAGE);
        StatusModifier rateDamageEffect = damageBaseStatusEffect.getStatusModifiers().get(StatusModifierType.ACT_RATE_DAMAGE);

        int currentHp = target.getHp();
        int damage = constantDamageEffect != null
                ? (int) constantDamageEffect.getValue() // 고정데미지
                : (int) (target.getMaxHp() * rateDamageEffect.getValue()); // 비율은 최대체력 비율

        Integer damagedHp = Math.max(currentHp - damage, 0); // 하한 0
        target.updateHp(damagedHp);
//        log.info("[processStatusDamage] battleActor.name = {} currentHp = {}, healInitValue = {}, resultHealRate = {}, healedHp = {}", target.getName(), currentHp, healInitValue, resultHealRate, healedHp);
        return damage;
    }

}
