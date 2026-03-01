package com.gbf.granblue_simulator.battle.logic.statuseffect;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.battle.exception.MoveProcessingException;
import com.gbf.granblue_simulator.battle.logic.move.dto.StatusEffectDto;
import com.gbf.granblue_simulator.battle.logic.system.ChargeGaugeLogic;
import com.gbf.granblue_simulator.metadata.domain.omen.OmenType;
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

import static com.gbf.granblue_simulator.battle.logic.util.StatusUtil.getEffectByModifierType;

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
        private List<StatusEffectDto> addedStatusEffects; // A(오의 게이지 증가, 페이탈 체인 게이지 증가) 도 가능
        @Builder.Default
        private List<StatusEffectDto> removedStatusEffects = new ArrayList<>();
        private Integer healValue;
        private Integer damageValue;
    }

    public ProcessStatusLogicResult process(Actor sourceActor, Actor targetActor, StatusEffect effect) {
        return process(sourceActor, targetActor, effect, null);
    }

    /**
     * 단일 modifier 에 따른 처리
     *
     * @param sourceActor          상태효과가 발생한 actor
     *                             CHECK 현재 회복성능 업을 적용하기 만을 위해서 사용중 (턴 종료시 효과 발생은 자신이 들어옴)
     * @param targetActor          상태효과가 부여되는 actor
     * @param selectedModifierType 처리할 modifierType 지정시 필요
     * @return ProcessStatusLogicResult, 처리할 효과가 없다면 null 반환
     */
    public ProcessStatusLogicResult process(Actor sourceActor, Actor targetActor, StatusEffect statusEffect, StatusModifierType selectedModifierType) {

        List<StatusEffectDto> addedStatusEffect = new ArrayList<>();
        List<StatusEffectDto> removedStatusEffects = new ArrayList<>();
        Integer healValue = null;
        Integer damageValue = null;

        // ACT_ 있는 modifier 만 처리
        List<StatusModifier> toProcessModifiers = statusEffect.getActiveModifiers().values().stream()
                .filter(modifier -> modifier.getType().needPostProcess())
                .toList();
        if (toProcessModifiers.isEmpty()) return null; // 없으면 바로 종료

        for (StatusModifier modifier : toProcessModifiers) {
            // 지정된 타입이 있는경우 해당 타입만 처리
            if (selectedModifierType != null && selectedModifierType != modifier.getType()) continue;
            // CHECK 턴종 처리시 별도의 순서로 처리가 필요해서 다중 modifier 를 가진 StatusEffect 의 중복처리 방지를 위해 작성했으나 유효성에 대해 생각해볼 필요 있음

            switch (modifier.getType()) {
                case ACT_CHARGE_GAUGE_UP:
                    addedStatusEffect.add(processChargeGaugeUpStatus(targetActor, statusEffect));
                    break;
                case ACT_CHARGE_GAUGE_DOWN:
                    addedStatusEffect.add(processChargeGaugeDownStatus(targetActor, statusEffect));
                    break;
                case ACT_WEAPON_BURST:
                    addedStatusEffect.add(StatusEffectDto.of(processWeaponBurstStatus(targetActor, statusEffect)));
                    break;
                case ACT_CHARGE_TURN_UP:
                    addedStatusEffect.add(processChargeTurnUpStatus(targetActor, statusEffect));
                    break;
                case ACT_CHARGE_TURN_DOWN:
                    addedStatusEffect.add(processChargeTurnDownStatus(targetActor, statusEffect));
                    break;
                case ACT_FATAL_CHAIN_GAUGE_UP:
                    addedStatusEffect.add(StatusEffectDto.of(processFatalGaugeUpStatus(targetActor, statusEffect)));
                    break;
                case ACT_FATAL_CHAIN_GAUGE_DOWN:
                    addedStatusEffect.add(StatusEffectDto.of(processFatalGaugeDownStatus(targetActor, statusEffect)));
                    break;
                case ACT_DISPEL:
                    removedStatusEffects.addAll(processDispel(targetActor, statusEffect).stream().map(StatusEffectDto::of).toList());
                    break;
                case ACT_CLEAR:
                    removedStatusEffects.addAll(processClear(targetActor, statusEffect).stream().map(StatusEffectDto::of).toList());
                    break;
                case ACT_HEAL, ACT_RATE_HEAL:
                    int heal = processHeal(sourceActor, targetActor, statusEffect);
                    if (heal >= 0) {
                        healValue = Objects.requireNonNullElse(healValue, 0) + heal;
                    } else {
                        // 언데드등의 효과로 힐값이 음수가 되었을시, 슬립 데미지로 매핑
                        damageValue = Objects.requireNonNullElse(damageValue, 0) + Math.abs(heal);
                    }
                    break;
                case ACT_DAMAGE, ACT_RATE_DAMAGE:
                    int damage = processStatusDamage(targetActor, statusEffect);
                    damageValue = Objects.requireNonNullElse(damageValue, 0) + damage;
                    break;
                case ACT_SHORTEN_ABILITY_COOLDOWN:
                    shortenAbilityCooldown(targetActor, statusEffect);
                    addedStatusEffect.add(StatusEffectDto.of(statusEffect));
                    break;
                case ACT_EXTEND_ABILITY_COOLDOWN:
                    extendAbilityCooldown(targetActor, statusEffect);
                    addedStatusEffect.add(StatusEffectDto.of(statusEffect));
                    break;
                case ACT_SHORTEN_SUMMON_COOLDOWN:
                    shortenSummonCooldown(targetActor, statusEffect);
                    addedStatusEffect.add(StatusEffectDto.of(statusEffect));
                    break;
                case ACT_EXTEND_SUMMON_COOLDOWN:
                    extendSummonCooldown(targetActor, statusEffect);
                    addedStatusEffect.add(StatusEffectDto.of(statusEffect));
                    break;
                case ACT_SHORTEN_DEBUFF_DURATION:
                    shortenDebuffDuration(targetActor, statusEffect);
                    addedStatusEffect.add(StatusEffectDto.of(statusEffect));
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
     * 페이탈 체인 게이지 업 처리 (DB 저장 x)
     */
    protected StatusEffect processFatalGaugeUpStatus(Actor targetActor, StatusEffect statusEffect) {
        chargeGaugeLogic.processFatalChainGaugeUpFromStatus(targetActor, statusEffect);

        return statusEffect;
    }

    /**
     * 페이탈 체인 게이지 감소 처리 (DB 저장 x)
     */
    protected StatusEffect processFatalGaugeDownStatus(Actor targetActor, StatusEffect statusEffect) {
        chargeGaugeLogic.processFatalChainGaugeDownFromStatus(targetActor, statusEffect);

        return statusEffect;
    }

    /**
     * 오의 게이지 업 처리 (DB 저장 x)
     */
    protected StatusEffectDto processChargeGaugeUpStatus(Actor targetActor, StatusEffect chargeGaugeUpEffect) {
        int addedChargeGauge = chargeGaugeLogic.processChargeGaugeUpFromStatus(targetActor, chargeGaugeUpEffect);

        return StatusEffectDto.fromChargeGaugeEffect(chargeGaugeUpEffect, addedChargeGauge);
    }

    /**
     * 오의 게이지 다운 처리 (DB 저장 x)
     */
    protected StatusEffectDto processChargeGaugeDownStatus(Actor targetActor, StatusEffect chargeGaugeDownEffect) {
        int subtractedChargeGauge = chargeGaugeLogic.processChargeGaugeDownFromStatus(targetActor, chargeGaugeDownEffect);

        return StatusEffectDto.fromChargeGaugeEffect(chargeGaugeDownEffect, subtractedChargeGauge);
    }


    /**
     * 웨폰버스트 처리 (DB 저장 x)
     */
    protected StatusEffect processWeaponBurstStatus(Actor targetActor, StatusEffect weaponBurstEffect) {
        chargeGaugeLogic.setChargeGauge(targetActor, 100);
        return weaponBurstEffect;
    }

    /**
     * CT 증가 처리
     *
     * @param enemy 적 만 허용
     */
    protected StatusEffectDto processChargeTurnUpStatus(Actor enemy, StatusEffect chargeTurnUpEffect) {
        if (!enemy.isEnemy())
            throw new MoveProcessingException("차지턴 변경 대상이 적이 아님, 타겟: " + enemy.getId() + " " + enemy.getName());
        Enemy concreteEnemy = (Enemy) enemy;

        if ((concreteEnemy.getOmen() != null && concreteEnemy.getOmen().getBaseOmen().getOmenType() == OmenType.CHARGE_ATTACK)
                || enemy.getChargeGauge() >= enemy.getMaxChargeGauge()) {
            // 적의 CT 전조 발생중 CT 조작 불가, CT 최대치일때 NO EFFECT
            return StatusEffectDto.of(StatusEffect.getTransientStatusEffect(StatusEffectType.DEBUFF, "NO EFFECT", enemy));
        }

        int delta = chargeGaugeLogic.modifyChargeTurn(enemy, chargeTurnUpEffect.getModifierValue(StatusModifierType.ACT_CHARGE_TURN_UP));
        if (delta == 0)
            return StatusEffectDto.of(StatusEffect.getTransientStatusEffect(StatusEffectType.BUFF, "NO EFFECT", enemy));
        return StatusEffectDto.fromChargeGaugeEffect(chargeTurnUpEffect, delta);
    }

    /**
     * CT 감소 처리
     *
     * @param enemy 적 만 허용
     */
    protected StatusEffectDto processChargeTurnDownStatus(Actor enemy, StatusEffect chargeTurnDownEffect) {
        if (!enemy.isEnemy())
            throw new MoveProcessingException("차지턴 변경 대상이 적이 아님, 타겟: " + enemy.getId() + " " + enemy.getName());

        Enemy concreteEnemy = (Enemy) enemy;
        if ((concreteEnemy.getOmen() != null && concreteEnemy.getOmen().getBaseOmen().getOmenType() == OmenType.CHARGE_ATTACK)
                || enemy.getChargeGauge() <= 0) {
            // 적의 CT 전조 발생중 CT 조작 불가, CT 0일때 NO EFFECT
            return StatusEffectDto.of(StatusEffect.getTransientStatusEffect(StatusEffectType.DEBUFF, "NO EFFECT", enemy));
        }

        int delta = chargeGaugeLogic.modifyChargeTurn(enemy, -chargeTurnDownEffect.getModifierValue(StatusModifierType.ACT_CHARGE_TURN_DOWN));
        if (delta == 0)
            return StatusEffectDto.of(StatusEffect.getTransientStatusEffect(StatusEffectType.DEBUFF, "NO EFFECT", enemy));
        return StatusEffectDto.fromChargeGaugeEffect(chargeTurnDownEffect, delta);
    }

    /**
     * 디스펠 스테이터스를 받아 디스펠로 삭제된 배틀 스테이터스 (버프) 를 반환
     *
     * @param target
     * @param dispelEffect
     * @return
     */
    protected List<StatusEffect> processDispel(Actor target, StatusEffect dispelEffect) {
        return getEffectByModifierType(target, StatusModifierType.ACT_DISPEL_GUARD) // 먼저 처리
                .or(() -> getEffectByModifierType(target, StatusModifierType.ACT_DISPEL_GUARD_ONCE))
                .map(dispelGuardEffect -> {
                    target.getStatusEffects().remove(dispelGuardEffect);
                    if (dispelGuardEffect.getActiveModifiers().containsKey(StatusModifierType.ACT_DISPEL_GUARD_ONCE)) {
                        statusEffectRepository.delete(dispelGuardEffect);
                    }
                    return List.of(dispelGuardEffect);
                }).orElseGet(() -> {
                    int dispelValue = (int) dispelEffect.getModifierValue(StatusModifierType.ACT_DISPEL); // 적의 dispel 은 99정도로 들어옴
                    List<StatusEffect> dispelledStatusEffects = target.getStatusEffects().stream()
                            .filter(status -> status.getBaseStatusEffect().getType().isBuff() && status.getBaseStatusEffect().isRemovable())
                            .sorted(Comparator.comparing(StatusEffect::getUpdatedAt).reversed())
                            .limit(dispelValue)
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
     * @param clearEffect
     * @return
     */
    protected List<StatusEffect> processClear(Actor target, StatusEffect clearEffect) {
        int clearValue = (int) clearEffect.getModifierValue(StatusModifierType.ACT_CLEAR);
        List<StatusEffect> clearedStatusEffects = target.getStatusEffects().stream()
                .filter(status -> status.getBaseStatusEffect().getType().isDebuff() && status.getBaseStatusEffect().isRemovable())
                .sorted(Comparator.comparing(StatusEffect::getUpdatedAt).reversed())
                .limit(clearValue)
                .toList(); // 해제될 디버프 (클리어의 value 값 갯수만큼만 최근에 추가된 디버프부터 해제함)
        target.getStatusEffects().removeAll(clearedStatusEffects);
        statusEffectRepository.deleteAll(clearedStatusEffects);
        return clearedStatusEffects;
    }

    /**
     * ACT_HEAL effect 를 가진 스테이터스를 받아 힐 처리후 힐량을 반환
     * HEAL, HEAL_FOR_ALL, BUFF.TURN_RECOVERY 에서 사용
     *
     * @param sourceActor : 회복성능 업을 확인하기 위한 상태효과 발생 대상 (턴 종료시 회복하는 재생효과의 경우, 자신이 들어옴)
     * @return
     */
    protected int processHeal(Actor sourceActor, Actor target, StatusEffect healEffect) {
        boolean isConstantHealEffect = healEffect.hasModifier(StatusModifierType.ACT_HEAL);
        double healInitValue =isConstantHealEffect
                ? healEffect.getModifierValue(StatusModifierType.ACT_HEAL)
                : target.getMaxHp() * healEffect.getModifierValue(StatusModifierType.ACT_RATE_HEAL);
        // 회복 상한 적용
        boolean hasUndeadEffect = getEffectByModifierType(target, StatusModifierType.UNDEAD).isPresent(); // 언데드 있을경우 회복상승 적용 x, 회복량을 음수로
        double healRate = hasUndeadEffect ? -1 : sourceActor.getStatus().getStatusDetails().getCalcedHealRate();
        // 참전자 회복
        boolean isForAllHealEffect = healEffect.getBaseStatusEffect().getTargetType() == StatusEffectTargetType.ALL_PARTY_MEMBERS;
        if (isForAllHealEffect) healRate = 1; // 참전자 대상 회복효과의 경우 언데드무시, 회복률 100%로 고정
        // 최종적용
        int currentHp = target.getHp();
        int resultHealValue = (int) (healInitValue * healRate);
        int healedHp = currentHp + resultHealValue;
        target.updateHp(healedHp);
//        log.info("[processHeal] battleActor.name = {} currentHp = {}, healInitValue = {}, resultHealRate = {}, healedHp = {}", target.getName(), currentHp, healInitValue, healRate, healedHp);
        return resultHealValue;
    }

    protected int processStatusDamage(Actor target, StatusEffect damageEffect) {
        // CHECK 체력비례데미지와 고정 데미지가 모두 붙은 상태효과는 없음을 전제로함
        boolean isConstantDamageEffect = damageEffect.hasModifier(StatusModifierType.ACT_DAMAGE);
        int damage = isConstantDamageEffect
                ? (int) damageEffect.getModifierValue(StatusModifierType.ACT_DAMAGE)
                : (int) (target.getMaxHp() * damageEffect.getModifierValue(StatusModifierType.ACT_RATE_DAMAGE));

        int currentHp = target.getHp();
        int damagedHp = Math.max(currentHp - damage, 0); // 하한 0
        target.updateHp(damagedHp);
//        log.info("[processStatusDamage] battleActor.name = {} currentHp = {}, healInitValue = {}, resultHealRate = {}, healedHp = {}", target.getName(), currentHp, healInitValue, resultHealRate, healedHp);
        return damage;
    }

    /**
     * 모든 어빌리티 쿨타임 단축
     */
    protected void shortenAbilityCooldown(Actor target, StatusEffect abilityShortenEffect) {
        int shortenTurnValue = (int) abilityShortenEffect.getModifierValue(StatusModifierType.ACT_SHORTEN_ABILITY_COOLDOWN);
        for (Move ability : target.getAbilities()) {
            ability.modifyCooldown(-shortenTurnValue);
        }
    }

    /**
     * 모든 어빌리티 쿨타임 연장
     */
    protected void extendAbilityCooldown(Actor target, StatusEffect abilityExtendEffect) {
        int extendTurnValue = (int) abilityExtendEffect.getModifierValue(StatusModifierType.ACT_EXTEND_ABILITY_COOLDOWN);
        for (Move ability : target.getAbilities()) {
            ability.modifyCooldown(extendTurnValue);
        }
    }

    /**
     * 모든 소환석 쿨타임 단축
     */
    protected void shortenSummonCooldown(Actor target, StatusEffect summonShortenEffect) {
        if (!target.getBaseActor().isLeaderCharacter())
            throw new MoveProcessingException("소환석 쿨타임 단축은 주인공에게만 효과가 부여됩니다. 타겟: " + target.getId() + " " + target.getName());
        int shortenTurnValue = (int) summonShortenEffect.getModifierValue(StatusModifierType.ACT_SHORTEN_SUMMON_COOLDOWN);
        for (Move summonMove : target.getSummons()) {
            summonMove.modifyCooldown(-shortenTurnValue);
        }
    }

    /**
     * 모든 소환석 쿨타임 연장
     */
    protected void extendSummonCooldown(Actor target, StatusEffect summonExtendEffect) {
        if (!target.getBaseActor().isLeaderCharacter())
            throw new MoveProcessingException("소환석 쿨타임 연장은 주인공에게만 효과가 부여됩니다. 타겟: " + target.getId() + " " + target.getName());
        int extendTurnValue = (int) summonExtendEffect.getModifierValue(StatusModifierType.ACT_EXTEND_SUMMON_COOLDOWN);
        for (Move summonMove : target.getSummons()) {
            summonMove.modifyCooldown(extendTurnValue);
        }
    }

    /**
     * 약화효과 효과시간 단축
     */
    protected void shortenDebuffDuration(Actor target, StatusEffect shortenDebuffDurationEffect) {
        int shortenTurnValue = (int) shortenDebuffDurationEffect.getModifierValue(StatusModifierType.ACT_SHORTEN_DEBUFF_DURATION);
        target.getStatusEffects().stream()
                .filter(statusEffect -> statusEffect.getBaseStatusEffect().getType().equals(StatusEffectType.DEBUFF)
                        && statusEffect.getBaseStatusEffect().getDurationType() == StatusDurationType.TURN)
                .forEach(statusEffect -> {
                    statusEffect.subtractDuration(shortenTurnValue);
                    if (statusEffect.getDuration() <= 0) {
                        statusEffectRepository.delete(statusEffect);
                        statusEffect.getActor().getStatusEffects().remove(statusEffect);
                        statusEffect.getActor().getStatus().syncStatus();
                    }
                });
        for (Move summonMove : target.getSummons()) {
            summonMove.modifyCooldown(-shortenTurnValue);
        }
    }

}
