package com.gbf.granblue_simulator.logic.common;

import com.gbf.granblue_simulator.domain.actor.battle.BattleActor;
import com.gbf.granblue_simulator.domain.actor.battle.BattleStatus;
import com.gbf.granblue_simulator.domain.move.prop.status.Status;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffect;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusEffectType;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusType;
import com.gbf.granblue_simulator.repository.BattleStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

import static com.gbf.granblue_simulator.logic.common.StatusUtil.getBattleStatuesByStatusType;
import static com.gbf.granblue_simulator.logic.common.StatusUtil.getEffectValueSum;

/**
 * 직접 처리가 필요한 일부 스테이터스들의 효과를 처리
 */
@Component
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ProcessStatusLogic {

    private final ChargeGaugeLogic chargeGaugeLogic;
    private final BattleStatusRepository battleStatusRepository;

    /**
     * 페이탈 체인 게이지 업 스테이터스를 받아 표시용 BattleStatus 로 반환 (DB 저장 x)
     *
     * @param targetActor
     * @param fatalGaugeUpStatus
     * @return
     */
    public BattleStatus processFatalGaugeUpStatus(BattleActor targetActor, Status fatalGaugeUpStatus) {
        if (!fatalGaugeUpStatus.getStatusEffects().containsKey(StatusEffectType.ACT_FATAL_CHAIN_GAUGE_UP))
            throw new IllegalArgumentException("페이탈 체인 게이지 업 아님, Status.id = " + fatalGaugeUpStatus.getId());
        chargeGaugeLogic.processFatalChainGaugeFromStatus(targetActor, fatalGaugeUpStatus);

        return BattleStatus.builder()
                .duration(0)
                .status(Status.builder().type(StatusType.BUFF).name("페이탈체인 상승").effectText("페이탈체인 상승").build())
                .level(0)
                .iconSrc("")
                .build()
                .setBattleActor(targetActor);
    }

    /**
     * 오의 게이지 업 스테이터스를 받아 표시용 BattleStatus 로 반환 (DB 저장 x)
     *
     * @param targetActor
     * @param chargeGaugeUpStatus
     * @return
     */
    public BattleStatus processChargeGaugeUpStatus(BattleActor targetActor, Status chargeGaugeUpStatus) {
        if (!chargeGaugeUpStatus.getStatusEffects().containsKey(StatusEffectType.ACT_CHARGE_GAUGE_UP))
            throw new IllegalArgumentException("오의 게이지 업 아님, Status.id = " + chargeGaugeUpStatus.getId());
        chargeGaugeLogic.processChargeGaugeFromStatus(targetActor, chargeGaugeUpStatus);

        return BattleStatus.builder()
                .duration(0)
                .status(Status.builder().type(StatusType.BUFF).name("오의게이지 상승").effectText("오의게이지 상승").build())
                .level(0)
                .iconSrc("")
                .build()
                .setBattleActor(targetActor);
    }

    /**
     * 디스펠 스테이터스를 받아 디스펠로 삭제된 배틀 스테이터스 (버프) 를 반환
     *
     * @param target
     * @param dispelStatus
     * @return
     */
    public List<BattleStatus> processDispel(BattleActor target, Status dispelStatus) {
        if (!dispelStatus.getStatusEffects().containsKey(StatusEffectType.ACT_DISPEL))
            throw new IllegalArgumentException("디스펠 효과 없음 Status.id = " + dispelStatus.getId());
        List<BattleStatus> dispelGuardStatuses = getBattleStatuesByStatusType(target, StatusType.DISPEL_GUARD);
        if (!dispelGuardStatuses.isEmpty()) {
            // 디스펠 가드 성공
            BattleStatus dispelGuardStatus = dispelGuardStatuses.getFirst(); // 중복 불가긴 함
            target.getBattleStatuses().remove(dispelGuardStatus);
            battleStatusRepository.delete(dispelGuardStatus);
            return List.of(dispelGuardStatus);
        }
        List<BattleStatus> dispelledBattleStatuses = target.getBattleStatuses().stream()
                .filter(status -> status.getStatus().getType().isBuff() && status.getStatus().removable())
                .sorted(Comparator.comparing(BattleStatus::getUpdatedAt).reversed())
                .limit((int) dispelStatus.getStatusEffects().get(StatusEffectType.ACT_DISPEL).getValue()) // 적의 dispel 은 99정도로 들어옴
                .toList();
        target.getBattleStatuses().removeAll(dispelledBattleStatuses);
        battleStatusRepository.deleteAll(dispelledBattleStatuses);
        // CHECK BUFF_FOR_ALL 에 대한 DISPEL 처리 동기화 미구현
        return dispelledBattleStatuses;
    }

    /**
     * 클리어 스테이터스를 받아 삭제된 배틀 스테이터스 (디버프) 를 반환
     *
     * @param target
     * @param clearStatus
     * @return
     */
    public List<BattleStatus> processClear(BattleActor target, Status clearStatus) {
        if (!clearStatus.getStatusEffects().containsKey(StatusEffectType.ACT_CLEAR))
            throw new IllegalArgumentException("클리어 효과 없음 Status.id = " + clearStatus.getId());
        List<BattleStatus> clearedBattleStatuses = target.getBattleStatuses().stream()
                .filter(status -> status.getStatus().getType().isDebuff() && status.getStatus().removable())
                .sorted(Comparator.comparing(BattleStatus::getUpdatedAt).reversed())
                .limit((int) clearStatus.getStatusEffects().get(StatusEffectType.ACT_CLEAR).getValue())
                .toList(); // 해제될 디버프 (클리어의 value 값 갯수만큼만 최근에 추가된 디버프부터 해제함)
        target.getBattleStatuses().removeAll(clearedBattleStatuses);
        battleStatusRepository.deleteAll(clearedBattleStatuses);
        // CHECK CLEAR_FOR_ALL 처리 미구현
        return clearedBattleStatuses;
    }

    /**
     * ACT_HEAL effect 를 가진 스테이터스를 받아 힐 처리후 힐량을 반환
     * HEAL, HEAL_FOR_ALL, BUFF.TURN_RECOVERY 에서 사용
     *
     * @param target
     * @param healStatus
     * @return
     */
    public int processHeal(BattleActor target, Status healStatus) {
        if (!healStatus.getStatusEffects().containsKey(StatusEffectType.ACT_HEAL))
            throw new IllegalArgumentException("힐 이펙트가 없음 Status.id = " + healStatus.getId());
        Integer currentHp = target.getHp();
        int healInitValue = (int) healStatus.getStatusEffects().get(StatusEffectType.ACT_HEAL).getValue();
        double healUpRate = getEffectValueSum(target, StatusEffectType.HEAL_UP);
        double healDownRate = getEffectValueSum(target, StatusEffectType.HEAL_DOWN);
        double resultHealRate = Math.clamp(0, 1 + healUpRate + healDownRate, 2.0); // 하한 0 상한 2 (100%증가)
        Integer healResultValue = (int) (healInitValue * resultHealRate);
        Integer healedHp = currentHp + healResultValue;
        target.updateHp(healedHp);
//        log.info("[processHeal] battleActor.name = {} currentHp = {}, healInitValue = {}, resultHealRate = {}, healedHp = {}", target.getName(), currentHp, healInitValue, resultHealRate, healedHp);
        // CHECK HEAL_FOR_ALL 미구현
        return healResultValue;
    }

    public int processStatusDamage(BattleActor target, Status damageStatus) {
        StatusEffect constantDamageEffect = damageStatus.getStatusEffects().get(StatusEffectType.ACT_DAMAGE);
        StatusEffect rateDamageEffect = damageStatus.getStatusEffects().get(StatusEffectType.ACT_RATE_DAMAGE);
        if (constantDamageEffect == null && rateDamageEffect == null)
            throw new IllegalArgumentException("데미지 이펙트가 없음 Status.id = " + damageStatus.getId());

        Integer currentHp = target.getHp();
        int damage = constantDamageEffect != null
                ? (int) constantDamageEffect.getValue() // 고정데미지
                : (int) (target.getMaxHp() * rateDamageEffect.getValue()); // 비율은 최대체력 비율

        Integer damagedHp = Math.max(currentHp - damage, 0); // 하한 0
        target.updateHp(damagedHp);
//        log.info("[processStatusDamage] battleActor.name = {} currentHp = {}, healInitValue = {}, resultHealRate = {}, healedHp = {}", target.getName(), currentHp, healInitValue, resultHealRate, healedHp);
        return damage;
    }

}
