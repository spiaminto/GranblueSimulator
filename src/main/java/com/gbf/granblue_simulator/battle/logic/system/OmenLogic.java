package com.gbf.granblue_simulator.battle.logic.system;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Move;
import com.gbf.granblue_simulator.battle.domain.actor.prop.Omen;
import com.gbf.granblue_simulator.battle.logic.move.dto.MoveLogicResult;
import com.gbf.granblue_simulator.battle.repository.MoveRepository;
import com.gbf.granblue_simulator.battle.repository.OmenRepository;
import com.gbf.granblue_simulator.battle.service.MoveService;
import com.gbf.granblue_simulator.metadata.domain.move.BaseMove;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.omen.BaseOmen;
import com.gbf.granblue_simulator.metadata.domain.omen.OmenCancelCond;
import com.gbf.granblue_simulator.metadata.domain.omen.OmenType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusEffectType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusModifier;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusModifierType;
import com.gbf.granblue_simulator.metadata.repository.BaseMoveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
    private final BattleContext battleContext;

    /**
     * 전조 발생여부 판단 및 전조 발생
     *
     * @return Move standby
     */
    public Optional<Move> triggerOmen(Actor enemyActor) {
        return this.triggerOmen(enemyActor, null, null);
    }

    /**
     * 직접 지정한 전조를 발생
     *
     * @return Move standby
     */
    public Optional<Move> triggerOmen(Actor enemyActor, BaseOmen selectedOmen) {
        return this.triggerOmen(enemyActor, selectedOmen, null);
    }

    /**
     * 직접 지정한 전조와 해제조건으로 전조 발생
     *
     * @param selectedOmen 발생시킬 전조, null 인경우 로직에 의해 발동
     * @return Move standby
     */
    public Optional<Move> triggerOmen(Actor enemyActor, BaseOmen selectedOmen, List<Integer> selectedCancelCondIndexes) {
        Enemy enemy = (Enemy) enemyActor;

        // 1. 다음 전조를 결정
        BaseOmen determinedOmen = selectedOmen != null ? selectedOmen : determineOmen(enemy);
        log.info("[triggerOmen] nextIncantStandbyType = {}, hpRate = {}, ct / max = {} / {}, determinedStandby: standbyOptional = {}", enemy.getNextIncantStandbyType(), enemy.getHpRateInt(), enemy.getChargeGauge(), enemy.getMaxChargeGauge(), determinedOmen);
        if (determinedOmen == null) return Optional.empty();

        // 2. 다음 전조 해제 조건 및 초기값을 결정
        List<OmenCancelCond> cancelConditions = determinedOmen.getOmenCancelConds();
        int cancelConditionSize = cancelConditions.size();
        Integer cancelConditionCount = determinedOmen.getCancelConditionCount();

        List<Integer> cancelConditionIndexes;
        if (selectedCancelCondIndexes != null) {
            cancelConditionIndexes = selectedCancelCondIndexes;
        } else {
            List<Integer> cancelConditionIndexCandidates = IntStream.range(0, cancelConditionSize).boxed().collect(Collectors.toCollection(ArrayList::new));
            Collections.shuffle(cancelConditionIndexCandidates);
            cancelConditionIndexes = cancelConditionIndexCandidates.subList(0, cancelConditionCount).stream().sorted().toList();
        }

        List<Integer> initValues = cancelConditionIndexes.stream().map(index -> cancelConditions.get(index).getInitValue()).collect(Collectors.toList());

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

        // 4.2 타입 기록
        enemy.updateLastStandbyType(standbyMove.getType());

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

        // 2. HP 트리거 (우선 + 일반)
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
     * @return Omen HpTrigger, 없으면 null
     */
    public BaseOmen getValidHpTrigger(Enemy enemy) {
        double hpRate = enemy.getHpRateInt();
        double latestTriggeredHp = enemy.getLatestTriggeredHp();

        List<BaseOmen> hpTriggerOmens = enemy.getBaseEnemy().getOmens().values().stream()
                .filter(omen -> omen.getOmenType() == OmenType.HP_TRIGGER)
                .toList();

        record HpTrigger(BaseOmen omen, int triggerHp) {
        } // triggerHp 기록용

        // HP 트리거(우선): 유효한 트리거중 max 값 선택. 반드시 발동하는 HP 트리거
        Optional<HpTrigger> primary = hpTriggerOmens.stream()
                .filter(BaseOmen::isTriggerPrimary)
                .flatMap(omen -> omen.getTriggerHps().stream()
                        .filter(hp -> hpRate <= hp && hp < latestTriggeredHp)
                        .max(Integer::compareTo)
                        .map(hp -> new HpTrigger(omen, hp))
                        .stream())
                .max(Comparator.comparingInt(HpTrigger::triggerHp));

        if (primary.isPresent()) {
            enemy.updateLatestTriggeredHp(primary.get().triggerHp());
            return primary.get().omen();
        }

        // HP 트리거(일반): 유효한 트리거중 min 값 선택. HP 트리거(우선) 발동시 체크하지 않으며, latestTriggeredHp 의 진행으로 인해 스킵될 수 있음.
        Optional<HpTrigger> normal = hpTriggerOmens.stream()
                .filter(omen -> !omen.isTriggerPrimary())
                .flatMap(omen -> omen.getTriggerHps().stream()
                        .filter(hp -> hpRate <= hp && hp < latestTriggeredHp)
                        .min(Integer::compareTo)
                        .map(hp -> new HpTrigger(omen, hp))
                        .stream())
                .min(Comparator.comparingInt(HpTrigger::triggerHp));

        normal.ifPresent(result -> enemy.updateLatestTriggeredHp(result.triggerHp()));
        return normal.map(HpTrigger::omen).orElse(null);
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
     * 전조 해제조건을 임의 조건으로 변경, 이때 remainValues 는 초기값으로 설정됨.
     */
    public void updateOmenCancelCond(List<Integer> cancelConditionIndexes) {
        Enemy enemy = (Enemy) battleContext.getEnemy();
        Omen omen = enemy.getOmen();
        if (omen == null) {
            log.warn("[updateOmenCancelCond] omen is null, enemy = {}", enemy);
            return;
        }

        omen.updateCancelConditionIndexes(new ArrayList<>(cancelConditionIndexes));
        omen.updateRemainValues(cancelConditionIndexes.stream()
                .map(index -> omen.getBaseOmen().getOmenCancelConds().get(index).getInitValue()).collect(Collectors.toList()));
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
     * @param value            변경할 값, remainValues 원소에 대응
     * @param remainValueIndex 변경할 값의 index
     * @return 수정된 값
     */
    public int manualUpdateOmenValue(Enemy enemy, int value, int remainValueIndex) {
        if (value < 0) throw new IllegalArgumentException("[updateOmenValue] value < 0, value = " + value);
        Omen omen = enemy.getOmen();
        if (omen == null) throw new IllegalArgumentException("[updateOmenValue] omen is null");
        List<Integer> remainValues = omen.getRemainValues();
        Integer remainValue = remainValues.get(remainValueIndex);

        // 값 갱신
        remainValues.set(remainValueIndex, value);
        Integer updatedValue = remainValues.get(remainValueIndex);

        if (updatedValue <= 0) {
            // 전조 연산 결과 값이 0 이하가 되면 해당 조건 remainValueIndex 삭제
            List<Integer> cancelConditionIndexes = omen.getCancelConditionIndexes();
            Integer removedConditionIndex = cancelConditionIndexes.remove(remainValueIndex); // index 로 삭제
            remainValues.remove(remainValueIndex);
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
     */
    public void updateOmenByOtherResult(Enemy enemy, MoveLogicResult otherResult) {
        if (enemy.getOmen() == null) return;

        Omen omen = enemy.getOmen();
        List<Integer> remainValues = omen.getRemainValues();
        List<Integer> cancelConditionIndexes = omen.getCancelConditionIndexes();
        // remainValues[i] 와 cancelConditionIndexes[i] 는 같은 baseOmen.cancelCond 에서 등록됨

        List<Integer> toRemoveIndexes = new ArrayList<>();

        for (int remainValueIndex = 0; remainValueIndex < remainValues.size(); remainValueIndex++) {
            Integer remainValue = remainValues.get(remainValueIndex);
            Integer cancelConditionIndex = cancelConditionIndexes.get(remainValueIndex);
            OmenCancelCond omenCancelCond = omen.getBaseOmen().getOmenCancelConds().get(cancelConditionIndex);

            int resultValue = remainValue;
            int modifierValue = 0;

            switch (omenCancelCond.getType()) {
                case DAMAGE -> {
                    modifierValue = getDamageSum(otherResult.getDamages(), otherResult.getAdditionalDamages());
                    resultValue = Math.max(remainValue - modifierValue, 0);
                }
                case CHARGE_ATTACK_DAMAGE -> {
                    if (otherResult.getMove().getType().getParentType() != MoveType.CHARGE_ATTACK) continue;
                    modifierValue = getDamageSum(otherResult.getDamages(), otherResult.getAdditionalDamages());
                    resultValue = Math.max(remainValue - modifierValue, 0);
                }
                case ABILITY_DAMAGE -> {
                    if (!otherResult.getMove().getType().isAbilities()) continue;
                    modifierValue = getDamageSum(otherResult.getDamages(), otherResult.getAdditionalDamages());
                    resultValue = Math.max(remainValue - modifierValue, 0);
                }

                case HIT_COUNT -> {
                    modifierValue = otherResult.getTotalHitCount();
                    resultValue = Math.max(remainValue - modifierValue, 0);
                }
                case TWO_HUNDRED_THOUSAND_DAMAGE_COUNT -> {
                    modifierValue = (int) otherResult.getDamages().stream().filter(damage -> damage >= 200000).count();
                    resultValue = Math.max(remainValue - modifierValue, 0);

                    int additionalModifierValue = otherResult.getAdditionalDamages().stream()
                            .map(additionalDamage -> additionalDamage.stream()
                                    .filter(value -> value >= 200000)
                                    .count())
                            .mapToInt(Long::intValue)
                            .sum();
                    resultValue = Math.max(resultValue - additionalModifierValue, 0);
                }

                case CHARGE_ATTACK_COUNT -> {
                    if (otherResult.getMove().getType().getParentType() != MoveType.CHARGE_ATTACK) continue;
                    resultValue = Math.max(remainValue - 1, 0);
                }
                case TRIPLE_ATTACK_COUNT -> {
                    if (otherResult.getMove().getType().getParentType() != MoveType.ATTACK) continue;
                    if (otherResult.getNormalAttackCount() < 3) continue;
                    resultValue = Math.max(remainValue - 1, 0);
                }

                case DISPEL_COUNT -> {
                    if (!otherResult.hasSnapshot(enemy.getId())) continue;
                    modifierValue = otherResult.getMove().getBaseMove().getBaseStatusEffects().stream()
                            .map(baseStatusEffect -> {
                                StatusModifier dispelModifier = baseStatusEffect.getModifier(StatusModifierType.ACT_DISPEL);
                                return dispelModifier != null ? dispelModifier.getInitValue() : 0;
                            })
                            .mapToInt(Double::intValue).sum();
                    resultValue = Math.max(remainValue - modifierValue, 0);
                }
                case DEBUFF_COUNT -> {
                    if (!otherResult.hasSnapshot(enemy.getId())) continue;
                    modifierValue = (int) otherResult.getSnapshots().get(enemy.getId()).getAddedStatusEffects().stream()
                            .filter(addedStatusEffect -> addedStatusEffect.getType() == StatusEffectType.DEBUFF)
                            .filter(addedStatusEffect -> !(addedStatusEffect.getName().equals("MISS") || addedStatusEffect.getName().equals("NO EFFECT")))
                            .count(); // int 로 변환해도 무리없음
                    resultValue = Math.max(remainValue - modifierValue, 0);
                }

                case USE_FATAL_CHAIN -> {
                    if (otherResult.getMove().getType().getParentType() != MoveType.FATAL_CHAIN) continue;
                    resultValue = Math.max(remainValue - 1, 0);
                }
                case USE_ABILITY_COUNT -> {
                    if (otherResult.getMove().getType().getParentType() != MoveType.ABILITY) continue;
                    if (!otherResult.getMove().getId().equals(battleContext.getCommandAbilityId())) continue;
                    resultValue = Math.max(remainValue - 1, 0);
                }

                case IMPOSSIBLE -> {
                    // 해제불가, 아무것도 하지 않음
                }
            }

            // 연산 결과 set
            if (remainValue != resultValue) {
                omen.getRemainValues().set(remainValueIndex, resultValue);
            }
            // 전조 연산 결과 값이 0 이하가 되면 해당 조건 index 삭제예정 리스트에 추가
            if (resultValue <= 0) {
                log.info("[updateOmenByOtherResult] cancelConditionRemoved, remainValue = {}, modifierValue = {}, cancelType = {}", remainValue, modifierValue, omenCancelCond.getType());
                toRemoveIndexes.add(remainValueIndex);
            }
        }

        // 해제된 전조 해제 조건 삭제
        if (!toRemoveIndexes.isEmpty()) {
            toRemoveIndexes.sort(Comparator.reverseOrder()); // 인덱스 밀림 방어 역순정렬
            for (Integer toRemoveConditionIndex : toRemoveIndexes) {
                // int 로 바꿔 인덱스로 삭제
                cancelConditionIndexes.remove(toRemoveConditionIndex.intValue());
                remainValues.remove(toRemoveConditionIndex.intValue());
            }
        }
        // 모든 전조 해제 조건이 삭제되었을경우, 전조를 삭제
        if (cancelConditionIndexes.isEmpty()) {
            this.clearCurrentOmen(enemy);
        }

        log.info("[updateOmenByOtherResult] enemy.id = {}, omen.remainValues = {}, omen.cancelConditionIndexes = {}, omen = {}", enemy.getId(), omen.getRemainValues(), omen.getCancelConditionIndexes(), omen);
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
        log.info("[removeCurrentOmen] omen = {}", omen);

        Move standbyMove = enemy.getFirstMove(omen.getStandbyType());
        moveRepository.delete(standbyMove);
        omenRepository.delete(omen);
        enemy.updateOmen(null);

        enemy.updatePrevOmen(omen);
    }


}
