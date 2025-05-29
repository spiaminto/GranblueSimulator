package com.gbf.granblue_simulator.logic.common;

import com.gbf.granblue_simulator.domain.actor.battle.BattleEnemy;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.prop.omen.Omen;
import com.gbf.granblue_simulator.domain.move.prop.omen.OmenCancelCond;
import com.gbf.granblue_simulator.domain.move.prop.omen.OmenType;
import com.gbf.granblue_simulator.domain.move.prop.status.StatusType;
import com.gbf.granblue_simulator.logic.actor.dto.ActorLogicResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
@Transactional
public class OmenLogic {

    public Move determineStandbyMove(BattleEnemy enemy) {
        Move standby = null;
        // hp 트리거
        Optional<Omen> hpTriggerOmen = this.getTriggeredOmen(enemy, OmenType.HP_TRIGGER);

        if (enemy.getNextStandbyType() != null) {
            log.info("INCANT ATTACK");
            // 영창기 (로직내부에서 발동설정)
            standby = enemy.getActor().getMoves().get(enemy.getNextStandbyType());
        } else if (hpTriggerOmen.isPresent() && !hpTriggerOmen.get().getTriggerHp().equals(enemy.getLatestTriggeredHp())) {
            // hp 트리거가 존재하며, 마지막으로 발동한 hp 트리거와 다름
            log.info("HPTRIGGER rate = {}, target = {}", enemy.calcHpRate(), hpTriggerOmen.get().getTriggerHp());
            // HP 트리거
            Omen triggeredOmen = hpTriggerOmen.get();
            standby = enemy.getActor().getMoves().get(triggeredOmen.getMove().getType());
            enemy.setNextStandbyType(standby.getType());
            enemy.setLatestTriggeredHp(triggeredOmen.getTriggerHp());
        } else if (enemy.getChargeGauge() >= enemy.getMaxChargeGauge()) {
            log.info("CHARGEATTACK");
            // 차지어택
            Optional<Omen> triggeredOmenOptional = this.getTriggeredOmen(enemy, OmenType.CHARGE_ATTACK);
            if (triggeredOmenOptional.isPresent()) {
                standby = enemy.getActor().getMoves().get(triggeredOmenOptional.get().getMove().getType());
                enemy.setNextStandbyType(standby.getType());
            }
        }
        log.info("\n\n determineStandbyMove, hpRate = {}, move = {}, chargeTurn = {}, maxCT = {}", enemy.calcHpRate(), standby, enemy.getChargeGauge(), enemy.getActor().getMaxChargeGauge());
        if (standby == null) return null;

        // 스탠바이의 전조 결정
        List<OmenCancelCond> omenCancelConds = standby.getOmen().getOmenCancelConds();
        OmenCancelCond omenCancelCond = omenCancelConds.get((int) (Math.random() * omenCancelConds.size()));
        // 전조 해제 조건 설정
        enemy.setOmenCancelCondIndex(omenCancelConds.indexOf(omenCancelCond));
        enemy.setOmenValue(omenCancelCond.getInitValue());

        return standby;
    }

    /**
     * 현재 적의 체력 상태에서 트리거 되는 HP 트리거 또는 차지어택 의 Omen 을 찾아 반환
     * @param enemy
     * @param omenType HP_TRIGGER 또는 CHARGE_ATTACK
     * @return
     */
    protected Optional<Omen> getTriggeredOmen (BattleEnemy enemy, OmenType omenType) {
        // triggerHP 가 더 작은쪽을 우선
        return enemy.getActor().getMoves().values().stream()
                .map(Move::getOmen)
                .filter(Objects::nonNull) // Omen 이 없는 move 의 경우 null 이므로 제거
                .filter(omen -> omen.getOmenType() == omenType && enemy.calcHpRate() <= omen.getTriggerHp())
                .min(Comparator.comparing(Omen::getTriggerHp)); // triggerHP 가 가장 작은쪽을 반환
    }

    // onOtherMove 에서 실행될 전조처리
    public Integer processOmen(BattleEnemy enemy, ActorLogicResult otherResult) {
        Move standbyMove = enemy.getActor().getMoves().get(enemy.getNextStandbyType());
        Omen omen = standbyMove.getOmen();
        OmenCancelCond omenCancelCond = omen.getOmenCancelConds().get(enemy.getOmenCancelCondIndex());
        int omenValue = enemy.getOmenValue();
        Integer processedOmenValue = this.process(enemy, omenCancelCond, otherResult);
        log.info("\n\n\nomenvaleu = {} , processedOmenvalue = {}", omenValue, processedOmenValue);

        // REMIND standby 프론트로 넘겨주고 프론트에서 standby 재생중이면 값만 처리하도록
        return processedOmenValue;
    }

    public Integer process(BattleEnemy enemy, OmenCancelCond cancelCond, ActorLogicResult otherResult) {
        Integer omenValue = enemy.getOmenValue();
        switch (cancelCond.getType()) {
            case HIT_COUNT -> {
                Integer totalHitCount = otherResult.getTotalHitCount();
                enemy.setOmenValue(Math.max(omenValue - totalHitCount, 0));
            }
            case DAMAGE -> {
                Integer damageSum = getDamageSum(otherResult.getDamages(), otherResult.getAdditionalDamages());
                enemy.setOmenValue(Math.max(omenValue - damageSum, 0));

            }
            case DEBUFF_COUNT -> {
                int debuffCount = otherResult.getAddedBattleStatusesList().stream()
                        .flatMap(battleStatuses -> battleStatuses.stream()
                                .map(battleStatus -> battleStatus.getStatus().getType()))
                        .filter(type -> type == StatusType.DEBUFF || type == StatusType.DEBUFF_FOR_ALL)
                        .toList().size();
                enemy.setOmenValue(Math.max(omenValue - debuffCount, 0));
            }
            case IMPOSSIBLE -> {
                // 해제불가, 아무것도 하지 않음
            }
        }
        log.info("OmenLogic.process() type = {}, originValue = {}, modifiedValue = {}", cancelCond.getType(), omenValue, enemy.getOmenValue());
        return enemy.getOmenValue();
    }

    /**
     * 데미지 합 구함,
     * 지금 BattleLogService 쪽이랑 중복있으니 차후에 한군데로 통합
     * @return
     */
    protected Integer getDamageSum(List<Integer> damages, List<List<Integer>> additionalDamages) {
        int damageSum = damages.stream().mapToInt(Integer::intValue).sum();
        int additionalDamageSum = additionalDamages.stream()
                .map(additionalDamage -> additionalDamage.stream()
                        .mapToInt(Integer::intValue)
                        .sum())
                .mapToInt(Integer::intValue)
                .sum();
        return damageSum + additionalDamageSum;
    }

}
