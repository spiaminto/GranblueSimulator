package com.gbf.granblue_simulator.battle.logic.system;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Omen;
import com.gbf.granblue_simulator.battle.repository.MoveRepository;
import com.gbf.granblue_simulator.battle.repository.OmenRepository;
import com.gbf.granblue_simulator.battle.service.MoveService;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.omen.BaseOmen;
import com.gbf.granblue_simulator.metadata.domain.omen.OmenCancelCond;
import com.gbf.granblue_simulator.metadata.domain.omen.OmenType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectType;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.metadata.repository.BaseMoveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Component
@Slf4j
@RequiredArgsConstructor
@Transactional
public class OmenLogic {

    private final MoveRepository moveRepository;
    private final OmenRepository omenRepository;
    private final BaseMoveRepository baseMoveRepository;
    private final ChargeGaugeLogic chargeGaugeLogic;
    private final MoveService moveService;

    /**
     * 전조 발생 여부 판단 및 발생
     *
     * @param enemyActor
     * @return Move standby
     */
    public Optional<Move> triggerOmen(Actor enemyActor) {
        Enemy enemy = (Enemy) enemyActor;

        // 1. 다음 전조를 결정
        BaseOmen determinedOmen = determineOmen(enemy);
        log.info("[triggerOmen] nextIncantStandbyType = {}, hpRate = {}, ct / max = {} / {}, determinedStandby: standbyOptional = {}", enemy.getNextIncantStandbyType(), enemy.getHpRateInt(), enemy.getChargeGauge(), enemy.getMaxChargeGauge(), determinedOmen);
        if (determinedOmen == null) return Optional.empty();

        // 2. 다음 전조 해제 조건 및 초기값을 결정
        List<OmenCancelCond> cancelConditions = determinedOmen.getOmenCancelConds();
        int cancelConditionSize = cancelConditions.size();
        Integer cancelConditionCount = determinedOmen.getCancelConditionCount();
        List<Integer> cancelConditionIndexes = new Random().ints(0, cancelConditionSize).distinct()
                .limit(cancelConditionCount)
                .sorted()
                .boxed().toList();
        List<Integer> initValues = cancelConditionIndexes.stream().map(index -> cancelConditions.get(index).getInitValue()).collect(Collectors.toList()); // 초기값 수정 가능

        // 2.1 전조 삽입
        Omen omen = Omen.builder()
                .baseOmen(determinedOmen)
                .cancelConditionIndexes(cancelConditionIndexes)
                .remainValues(initValues)
                .build()
                .mapEnemy(enemy);
        omenRepository.save(omen);

        // 3. 전조 상태 변경을 위한 standbyMove 저장
        Long standbyId = enemy.getBaseEnemy().getMappedMove().getStandbyId();
        BaseMove baseStandby = baseMoveRepository.findById(standbyId).orElseThrow(() -> new IllegalArgumentException("적의 전조 정보가 없습니다. enemy.baseEnemy.id = " + enemy.getBaseEnemy().getId() + "enemy.name = " + enemy.getName() + " standbyId = " + standbyId + " omen = " + omen.toString().replace("\n", "")));
        Move standbyMove = Move.fromBaseMove(baseStandby);
        standbyMove.mapType(omen.getStandbyType());
        standbyMove.mapActor(enemyActor);
        moveRepository.save(standbyMove);

        // 4. 후처리
        // 4.1 이전 발동한 HP 트리거를 재발동하지 않기위한 필드. CHECK 찰나의 순간 대량으로 HP가 깎인경우 이 값이 의도대로 동작하지 않음
        if (determinedOmen.getOmenType() == OmenType.HP_TRIGGER) {
            enemy.updateLatestTriggeredHp(enemy.getHpRateInt());
        }

        return Optional.of(standbyMove);
    }

    /**
     * 다음 전조를 결정.
     *
     * @param enemy
     * @return 전조에 따른 standby Move
     */
    protected BaseOmen determineOmen(Enemy enemy) {
        // 우선순위대로 전조를 결정
        BaseOmen incantAttackOmen = enemy.getBaseOmen(enemy.getNextIncantStandbyType());

        // 0. 다음 영창기 초기화
        enemy.updateNextIncantStandbyType(null);
        // CHECK nextIncantStandbyType 은 HP 트리거 등이 발동하더라도 초기화 되어야 함. 만약 nextIncantStandbyType 의 발동 조건이 유지되는경우 어차피 다음턴에 다시 발동하므로 조건오염을 방지하기 위해 무조건 초기화
        // CHECK 만약 조건 달성즉시 무조건 발동해야 한다면 영창기(우선) 으로 등록해서 사용

        // 1. 영창기 (우선)
        if (incantAttackOmen != null && incantAttackOmen.isTriggerPrimary())
            return incantAttackOmen;

        // 2. HP 트리거
        BaseOmen hpTriggerOmen = this.getValidHpTrigger(enemy);
        if (hpTriggerOmen != null) return hpTriggerOmen;

        // 3. 영창기
        if (incantAttackOmen != null) {
            return incantAttackOmen;
        }

        // 4. 차지어택
        BaseOmen chargeAttackOmen = this.getValidChargeAttack(enemy);
        if (chargeAttackOmen != null) return chargeAttackOmen;

        // 없음
        return null;
    }

    /**
     * 현재 적의 체력 상태에서 트리거 되는 HP 트리거를 찾아 반환
     *
     * @param enemy
     * @return Omen HpTrigger, 없으면 null
     */
    protected BaseOmen getValidHpTrigger(Enemy enemy) {
        double hpRate = enemy.getHpRateInt();
        double latestTriggeredHp = enemy.getLatestTriggeredHp();

        return enemy.getBaseEnemy().getOmens().values().stream()
                .filter(omen -> omen.getOmenType() == OmenType.HP_TRIGGER) // HP_TRIGGER만
                // triggerHps 중 "아직 발동하지 않았고, 현재 HP 이하"인 값이 하나라도 있으면 통과
                .filter(omen ->
                        omen.getTriggerHps().stream()
                                .anyMatch(triggerHp -> hpRate <= triggerHp && triggerHp < latestTriggeredHp)
                )
                // 트리거 가능한 HP 중 가장 높은 값들 뽑은뒤, 그중 제일 작은값을 고름 
                // hpRate 가 39, latestTriggeredHP 가 60 일때 {50, 40}(=40) 과 {60, 45, 30}(=45) 중 {50, 40} 을 고름
                .min(Comparator.comparing(omen ->
                        omen.getTriggerHps().stream()
                                .filter(triggerHp -> triggerHp >= hpRate && triggerHp < latestTriggeredHp)
                                .min(Integer::compareTo)
                                .orElse(Integer.MAX_VALUE)
                )).orElse(null);
    }

    /**
     * 현재 적의 체력 상태에서 트리거되는 차지어택의 Omen 을 찾아 반환
     *
     * @param enemy
     * @return Omen ChargeAttack, 없으면 null
     */
    protected BaseOmen getValidChargeAttack(Enemy enemy) {
        if (enemy.getChargeGauge() < enemy.getMaxChargeGauge()) return null;
        double hpRate = enemy.getHpRateInt();
        return enemy.getBaseEnemy().getOmens().values().stream()
                .filter(omen -> omen.getOmenType() == OmenType.CHARGE_ATTACK)
                .filter(omen -> hpRate <= omen.getTriggerHps().getFirst()) // CT기는 트리거 1개
                .max(Comparator.comparing(omen -> omen.getTriggerHps().getFirst()))
                .orElse(null);
    }


    /**
     * 전조 값을 갱신 (수동)
     *
     * @param enemy
     * @param values
     * @return
     */
    public List<Integer> manualUpdateOmenValue(Enemy enemy, List<Integer> values) {
        if (values.isEmpty()) return Collections.emptyList();
        List<Integer> results = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            results.add(manualUpdateOmenValue(enemy, values.get(i), i));
        }
        return results;
    }


    /**
     * 전조 값을 갱신 (수동) <br>
     * 주로 전조의 초기값을 갱신할때 사용
     * CHECK 야마토 구현시 1어빌, 바다가르기? 랑 트리제로 뒷면 구현때도 사용할거같음
     *
     * @param enemy
     * @param value
     * @return
     */
    public int manualUpdateOmenValue(Enemy enemy, int value, int index) {
        if (value < 0) throw new IllegalArgumentException("[updateOmenValue] value < 0, value = " + value);
        Omen omen = enemy.getOmen();
        if (omen == null) throw new IllegalArgumentException("[updateOmenValue] omen is null");
        List<Integer> remainValues = omen.getRemainValues();
        Integer remainValue = remainValues.get(index);

        // 값 갱신
        remainValues.set(index, value);
        Integer updatedValue = remainValues.get(index);

        if (updatedValue <= 0) {
            // 전조 연산 결과 값이 0 이하가 되면 해당 조건 index 삭제
            List<Integer> cancelConditionIndexes = omen.getCancelConditionIndexes();
            Integer removedConditionIndex = cancelConditionIndexes.remove(index);
            remainValues.remove(index);
            log.info("[manualUpdateOmenValue] cancelConditionRemoved, cancelType = {}, remainValue = {}, modifierValue = {}", omen.getBaseOmen().getOmenCancelConds().get(removedConditionIndex), remainValue, value);

            if (cancelConditionIndexes.isEmpty()) {
                // 모든 전조 해제 조건이 삭제되었을경우, 전조를 삭제
                this.clearCurrentOmen(enemy);

                enemy.updateOmen(null);
            }
        }

        log.info("[manulUpdateOmenValue] beforeOmenValue = {} , updateValue = {}", remainValue, updatedValue);
        return updatedValue;
    }

    /**
     * ActorLogicResult 결과에 따라 적의 전조값을 갱신. 전조가 해제될경우 해당 전조를 삭제함. <br>
     * 결과는 enemy.getOmen() != null 로 확인
     *
     * @param enemy
     * @param otherResult
     */
    public void updateOmenByOtherResult(Enemy enemy, MoveLogicResult otherResult) {
        if (enemy.getOmen() == null) return;

        Omen omen = enemy.getOmen();
        List<Integer> cancelConditionIndexes = omen.getCancelConditionIndexes();
        List<Integer> remainValues = omen.getRemainValues();

        for (int i = 0; i < cancelConditionIndexes.size(); i++) {
            Integer cancelConditionIndex = cancelConditionIndexes.get(i); // remove(int index) 구분을 위해 Integer
            Integer remainValue = omen.getRemainValues().get(i);// remove(int index) 구분을 위해 Integer
            OmenCancelCond omenCancelCond = omen.getBaseOmen().getOmenCancelConds().get(cancelConditionIndex);
            int resultValue = remainValue;
            int modifierValue = 0;

            switch (omenCancelCond.getType()) {
                case HIT_COUNT -> {
                    modifierValue = otherResult.getTotalHitCount();
                    resultValue = Math.max(remainValue - modifierValue, 0);
                }
                case DAMAGE -> {
                    modifierValue = getDamageSum(otherResult.getDamages(), otherResult.getAdditionalDamages());
                    resultValue = Math.max(remainValue - modifierValue, 0);
                }
                case DEBUFF_COUNT -> {
                    if (!otherResult.hasSnapshot(enemy.getId())) return;
                    modifierValue = (int) otherResult.getSnapshots().get(enemy.getId()).getAddedStatusEffects().stream()
                            .filter(addedStatusEffect -> addedStatusEffect.getType() == StatusEffectType.DEBUFF)
                            .filter(addedStatusEffect -> !(addedStatusEffect.getName().equals("MISS") || addedStatusEffect.getName().equals("NO EFFECT")))
                            .count(); // int 로 변환해도 무리없음
                    resultValue = Math.max(remainValue - modifierValue, 0);
                }
                case IMPOSSIBLE -> {
                    // 해제불가, 아무것도 하지 않음
                }
            }
            log.info("[updateOmenByOtherResult] cancelConditionRemoved, remainValue = {}, modifierValue = {}, cancelType = {}", remainValue, modifierValue, omenCancelCond.getType());

            if (remainValue != resultValue) {
                // 연산 결과 set
                omen.getRemainValues().set(i, resultValue);
            }

            if (resultValue <= 0) {
                // 전조 연산 결과 값이 0 이하가 되면 해당 조건 index 삭제
                cancelConditionIndexes.remove(cancelConditionIndex);
                remainValues.remove(remainValue);
            }
        }

        if (cancelConditionIndexes.isEmpty()) {
            // 모든 전조 해제 조건이 삭제되었을경우, 전조를 삭제
            this.clearCurrentOmen(enemy);
        }

        log.info("[updateOmenByOtherResult] omen.remainValues = {}, enemy.id = {}, omen = {}", omen.getRemainValues(), enemy.getId(), omen);
    }

    /**
     * 데미지 합 구함,
     * 지금 BattleLogService 쪽이랑 중복있으니 차후에 한군데로 통합
     *
     * @return
     */
    protected Integer getDamageSum(List<Integer> damages, List<List<Integer>> additionalDamages) {
        int damageSum = damages.stream().filter(value -> value >= 0).mapToInt(Integer::intValue).sum();
        int additionalDamageSum = additionalDamages.stream()
                .map(additionalDamage -> additionalDamage.stream()
                        .filter(value -> value >= 0)
                        .mapToInt(Integer::intValue)
                        .sum())
                .mapToInt(Integer::intValue)
                .sum();
        return damageSum + additionalDamageSum;
    }

    /**
     * 현재 적의 전조를 해제<br>
     * 플레이어가 적의 전조를 직접 해제함
     *
     * @param enemy
     */
    public void clearCurrentOmen(Enemy enemy) {
        Omen omen = enemy.getOmen();
        if (omen.getBaseOmen().getOmenType() == OmenType.CHARGE_ATTACK) {
            // CT 전조 해제시 차지게이지 초기화
            chargeGaugeLogic.setChargeGauge(enemy, 0);
        }

        Move standbyMove = enemy.getFirstMove(omen.getStandbyType());
        moveRepository.delete(standbyMove);
        omenRepository.delete(omen);
        enemy.updateOmen(null);
    }

    /**
     * 현재 적의 전조를 해제<br>
     * 로직에서 해제함
     */
    public void removeCurrentOmen(Enemy enemy) {
        Omen omen = enemy.getOmen();

        Move standbyMove = enemy.getFirstMove(omen.getStandbyType());
        moveRepository.delete(standbyMove);
        omenRepository.delete(omen);
        enemy.updateOmen(null);

        enemy.updatePrevOmen(omen);
    }


}
