package com.gbf.granblue_simulator.logic.common;

import com.gbf.granblue_simulator.domain.actor.battle.BattleEnemy;
import com.gbf.granblue_simulator.domain.move.Move;
import com.gbf.granblue_simulator.domain.move.MoveType;
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

    /**
     * 다음 전조를 결정
     *
     * @param enemy
     * @return Optional Move 전조.
     */
    public Optional<Move> determineStandbyMove(BattleEnemy enemy) {
        Move standby = null;
        if (enemy.getNextIncantStandbyType() != null) {
            // 영창기 : 로직 내부에서 설정된 다음 영창기를 발동
            standby = enemy.getActor().getMoves().get(enemy.getNextIncantStandbyType());
            enemy.setCurrentStandbyType(enemy.getNextIncantStandbyType());
            log.info("[determineStandbyMove] INCANT_ATTACK, nextIncantStandbyType = {}", enemy.getNextIncantStandbyType());
        } else {
            // HP 트리거, CT기 : 트리거 조건을 확인하여 발동 
            standby = this.getTriggeredOmen(enemy).map(
                    triggeredOmen -> {
                        Move triggeredStandby = triggeredOmen.getMove();
                        enemy.setCurrentStandbyType(triggeredStandby.getType());
                        if (triggeredOmen.getOmenType() == OmenType.HP_TRIGGER)
                            enemy.setLatestTriggeredHp(triggeredOmen.getTriggerHp()); // 이전에 실행된 HP 트리거가 재실행 되지않도록 하기 위한 필드
                        log.info("[determineStandbyMove] HP_TRIGGER OR CHARGE_ATTACK hpRate = {}, hpTriggerRate = {}, ct = {}, ctMax = {}", enemy.calcHpRate(), triggeredOmen.getTriggerHp(), enemy.getChargeGauge(), enemy.getMaxChargeGauge());
                        return triggeredStandby;
                    }).orElse(null);
        }
        if (standby != null) {
            // 전조 있을시, 전조 해제조건 결정 (랜덤) 및 set
            List<OmenCancelCond> omenCancelConds = standby.getOmen().getOmenCancelConds();
            OmenCancelCond omenCancelCond = omenCancelConds.get((int) (Math.random() * omenCancelConds.size()));
            enemy.setOmenCancelCondIndex(omenCancelConds.indexOf(omenCancelCond));
            enemy.setOmenValue(omenCancelCond.getInitValue());
        }
        return Optional.ofNullable(standby);
    }

    /**
     * 현재 적의 체력 상태에서 트리거 되는 HP 트리거 또는 차지어택 의 Omen 을 찾아 반환 (HP 트리거 우선)
     *
     * @param enemy
     * @return Omen HpTrigger or ChargeAttack
     */
    protected Optional<Omen> getTriggeredOmen(BattleEnemy enemy) {
        return enemy.getActor().getMoves().values().stream()
                .filter(move -> move.getType().getParentType().equals(MoveType.STANDBY))
                .map(Move::getOmen)
                .filter(omen -> enemy.calcHpRate() <= omen.getTriggerHp()) // 현 HP 에서 발생가능한 CT기 + HP트리거
                .filter(omen -> !(omen.getOmenType() == OmenType.HP_TRIGGER && omen.getTriggerHp() >= enemy.getLatestTriggeredHp())) // HP 트리거중 이전 발동햇던 HP 트리거들 제거
                .filter(omen -> !(omen.getOmenType() == OmenType.CHARGE_ATTACK && enemy.getChargeGauge() < enemy.getMaxChargeGauge())) // 차지어택 불가능할시 차지어택 제거
                .min(Comparator.comparing(Omen::getTriggerHp).thenComparing(Omen::getOmenType)); // triggerHp, OmenType ordinal 오름차순으로 정렬 (HP 트리거 우선) CHECK OmenType ordinal 순서로 정렬함
    }

    // onOtherMove 에서 실행될 전조처리
    public Integer processOmen(BattleEnemy enemy, ActorLogicResult otherResult) {
        Move standbyMove = enemy.getActor().getMoves().get(enemy.getCurrentStandbyType());
        Omen omen = standbyMove.getOmen();
        OmenCancelCond omenCancelCond = omen.getOmenCancelConds().get(enemy.getOmenCancelCondIndex());
        int omenValue = enemy.getOmenValue();
        Integer processedOmenValue = this.process(enemy, omenCancelCond, otherResult);
        log.info("[processOmen] beforeOmenValue = {} , processedOmenValue = {}", omenValue, processedOmenValue);

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
                                .filter(battleStatus -> !battleStatus.getStatus().getName().equals("MISS"))
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
     *
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
