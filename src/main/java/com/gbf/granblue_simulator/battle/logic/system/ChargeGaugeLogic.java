package com.gbf.granblue_simulator.battle.logic.system;

import com.gbf.granblue_simulator.battle.domain.BattleContext;
import com.gbf.granblue_simulator.battle.domain.Member;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.battle.domain.actor.Enemy;
import com.gbf.granblue_simulator.battle.domain.actor.prop.StatusEffect;
import com.gbf.granblue_simulator.battle.exception.MoveProcessingException;
import com.gbf.granblue_simulator.metadata.domain.omen.OmenType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusModifierType;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
@Transactional
public class ChargeGaugeLogic {

    private final Integer baseSingleAttackGaugePoint = 10; // 통상공격 첫타 오의게이지
    private final Integer baseDoubleAttackGaugePoint = baseSingleAttackGaugePoint + 12; // 2타 오의게이지
    private final Integer baseTripleAttackGaugePoint = baseSingleAttackGaugePoint + baseDoubleAttackGaugePoint + 15; // 3타 오의게이지
    private final Integer baseEnemyAttackGaugePoint = 1;

    private final Integer baseFatalChainGaugePoint = 15; // 통상 오의시 페이탈 체인 게이지 + 15

    private final Integer baseGaugePointWhenOtherActorChargeAttack = 10; // 다른 캐릭터가 오의쓰면 후열의 캐릭터 오의게이지증가치
    private final BattleContext battleContext;

    /**
     * 캐릭터의 일반공격 후 오의게이지 갱신
     *
     * @param hitCount 일반공격 횟수 (싱글1, 더블2, 트리플3, 쿼드라 X)
     */
    public void afterNormalAttack(int hitCount) {
        Actor mainActor = battleContext.getMainActor();
        double attackChargeGaugeIncreaseRate = mainActor.getStatus().getStatusDetails().getCalcedAttackChargeGaugeIncreaseRate(); // 일반공격 오의데미지 증가율 (현재 0 or 1.0)
        switch (hitCount) {
            case 1 -> modifyChargeGauge(mainActor, baseSingleAttackGaugePoint * attackChargeGaugeIncreaseRate);
            case 2 -> modifyChargeGauge(mainActor, baseDoubleAttackGaugePoint * attackChargeGaugeIncreaseRate);
            case 3 -> modifyChargeGauge(mainActor, baseTripleAttackGaugePoint * attackChargeGaugeIncreaseRate);
            default -> {
            }
        }
    }

    /**
     * 캐릭터의 오의 후 오의 게이지 갱신
     */
    public void afterChargeAttack() {
        Actor mainActor = battleContext.getMainActor();
        List<Actor> partyMembers = battleContext.getFrontCharacters();
        if (!mainActor.getStatusDetails().isExecutingReactivatedChargeAttack()) {
            // 재발동이 아닐때, 사용자의 오의게이지 초기화
            this.setChargeGauge(mainActor, mainActor.getChargeGauge() - 100);
        }
        modifyFatalChainGauge(baseFatalChainGaugePoint); // 페이탈 체인 게이지 증가
        partyMembers.stream() // 자신의 오의 발동 시점 기준 오의를 사용하지 않은 아군의 오의게이지 증가
                .filter(actor -> !actor.getId().equals(mainActor.getId()) && actor.getStatusDetails().getExecutedChargeAttackCount() < 1)
                .forEach(actor -> modifyChargeGauge(actor, baseGaugePointWhenOtherActorChargeAttack));
    }


    /**
     * 적의 통상공격, 오의 후 차지턴 설정
     * 적 역시 chargeGauge 필드를 사용. 1, 2, 3... 정수형으로 +1 씩증가. increaseRate 역시 100%, 200% 단위로 설정
     * 적의 공격은 아군의 오의게이지역시 변화시킴
     *
     * @param targets 적의 공격타겟 (공격대상 없을시 null)
     * @param damages 피해량에 따른 아군 오의게이지 상승을 계산하기 위한 데미지
     */
    public void afterEnemyAttack(List<Actor> targets, List<Integer> damages) {
        Enemy enemy = (Enemy) battleContext.getEnemy();

        if (enemy.getOmen() != null && enemy.getOmen().getBaseOmen().getOmenType() == OmenType.CHARGE_ATTACK) {
            setChargeGauge(enemy, 0); // 적의 CT 특수기 -> 0으로 초기화
        } else {
            modifyChargeGauge(enemy, baseEnemyAttackGaugePoint); // 적의 나머지 특수기, 일반공격 -> add
        }

        if (targets != null) {
            // 적의 공격에 따른 아군의 오의게이지 변화
            for (int i = 0; i < targets.size(); i++) {
                Actor target = targets.get(i);
                int damage = damages.get(i);
                double addValue = Math.ceil(100 * ((double) damage / target.getMaxHp()) * 0.5);
                int resultAddValue = (int) Math.ceil(addValue * (1 + target.getStatusDetails().getCalcedChargeGaugeIncreaseRateOnDamaged())); // 피데미지시 오의게이지 상승률 적용 (-1.0 ~ 2.0)
                modifyChargeGauge(target, resultAddValue);
            }
        }
    }

    /**
     * SetStatus 에서 오의게이지 업 상태효과 후처리용 (어빌리티, 오의 등에서 상태효과 부여로 오의게이지가 증가하는 경우)
     *
     * @param actor
     * @param chargeGaugeUpEffect
     */
    public int processChargeGaugeUpFromStatus(Actor actor, StatusEffect chargeGaugeUpEffect) {
        double chargeGaugeUpValue = chargeGaugeUpEffect.getModifierValue(StatusModifierType.ACT_CHARGE_GAUGE_UP);
        int addedChargeGauge = this.modifyChargeGauge(actor, chargeGaugeUpValue);
        return addedChargeGauge;
    }

    /**
     * SetStatus 에서 오의게이지 다운 상태효과 후처리용
     * 하나의 상태에 오의 게이지 업 & 다운 모두 있는경우 독립적인 처리를 위해 UP 과 별도로 작성 (게이지 흡수 효과 등)
     *
     * @param actor
     * @param chargeGaugeDownEffect
     */
    public int processChargeGaugeDownFromStatus(Actor actor, StatusEffect chargeGaugeDownEffect) {
        double chargeGaugeDownValue = chargeGaugeDownEffect.getModifierValue(StatusModifierType.ACT_CHARGE_GAUGE_DOWN);
        double setGaugeValue = actor.getChargeGauge() - chargeGaugeDownValue;
        this.setChargeGauge(actor, setGaugeValue);
        return (int) chargeGaugeDownValue;
    }


    /**
     * SetStatus 에서 페이탈체인 게이지 업 상태효과 후처리용 (어빌리티, 오의 등에서 상태효과 부여로 페이탈 체인 게이지가 증가하는 경우)
     *
     * @param actor
     * @param fatalChainGaugeUpEffect
     */
    public void processFatalChainGaugeUpFromStatus(Actor actor, StatusEffect fatalChainGaugeUpEffect) {
        double fatalChanGaugeUpValue = fatalChainGaugeUpEffect.getModifierValue(StatusModifierType.ACT_FATAL_CHAIN_GAUGE_UP);
        double setGaugeValue = actor.getMember().getFatalChainGauge() + fatalChanGaugeUpValue;
        this.setFatalChainGauge(setGaugeValue);
    }

    /**
     * SetStatus 에서 페이탈체인 게이지 다운 상태효과 후처리용 <br>
     * 하나의 상태에 페이탈 체인 게이지 업 & 다운 모두 있는경우 독립적인 처리를 위해 UP 과 별도로 작성 (사실 이건 거의 없긴 함)
     *
     * @param actor
     * @param fatalChainGaugeDownEffect
     */
    public void processFatalChainGaugeDownFromStatus(Actor actor, StatusEffect fatalChainGaugeDownEffect) {
        double fatalChainGaugeDownValue = fatalChainGaugeDownEffect.getModifierValue(StatusModifierType.ACT_FATAL_CHAIN_GAUGE_DOWN);
        double setGaugeValue = actor.getMember().getFatalChainGauge() - fatalChainGaugeDownValue;
        this.setFatalChainGauge(setGaugeValue);
    }

    /**
     * 실제로 오의게이지를 변경 하는 메서드<br>
     * 오의게이지 증가량 상태가 적용되지 않음!
     *
     * @param actor
     * @param setValue set 할 값
     */
    public int setChargeGauge(Actor actor, double setValue) {
        int maxGauge = actor.getMaxChargeGauge();
        int setChargeGauge = Math.clamp((int) setValue, 0, maxGauge);
        actor.updateChargeGauge(setChargeGauge);
        return setChargeGauge;
    }

    /**
     * 실제로 오의게이지를 증감하는 메서드<br>
     *
     * @param delta 증감값. 양수일경우 오의게이지 상승률 증가가 적용
     * @return 실제로 증감한 값
     */
    public int modifyChargeGauge(Actor actor, double delta) {
        int maxGauge = actor.getMaxChargeGauge();
        int resultDelta = delta > 0
                ? (int) Math.ceil(delta * (1 + actor.getStatus().getChargeGaugeIncreaseRate())) // 오의게이지 상승률 증가 적용 ( -1.0 ~ 1.0 )
                : (int) delta;
        int chargeGauge = actor.getChargeGauge();
        int resultChargeGauge = Math.clamp(chargeGauge + resultDelta, 0, maxGauge);

        actor.updateChargeGauge(resultChargeGauge);
        return resultChargeGauge - chargeGauge;
    }

    /**
     * 실제로 오의게이지를 증감하는 메서드<br>
     *
     * @param enemy 적, 적 만 허용함.
     * @param delta 증감값. 양수일경우 오의게이지 상승률 증가가 적용
     * @return 실제로 증감한 값
     */
    public int modifyChargeTurn(Actor enemy, double delta) {
        int maxGauge = enemy.getMaxChargeGauge();
        int resultDelta = (int) delta; // 적의 오의게이지 상승률 증가는 나중에 INCREASE_CHARGE_TURN_RATE 등 별도로 구현예정
        int resultChargeGauge = Math.clamp(enemy.getChargeGauge() + resultDelta, 0, maxGauge);

        enemy.updateChargeGauge(resultChargeGauge);
        return resultDelta;
    }

    /**
     * @param value
     * @return
     */
    public int setFatalChainGauge(double value) {
        int setValue = Math.clamp((int) value, 0, 100);
        battleContext.getMember().updateFatalChainGauge(setValue);
        return setValue;
    }

    /**
     * 실제로 페이탈 체인 게이지를 증감하는 메서드
     *
     * @param delta 증감값
     * @return
     */
    public int modifyFatalChainGauge(double delta) {
        Member member = battleContext.getMember();
        int resultFatalChainGauge = Math.clamp(member.getFatalChainGauge() + (int) delta, 0, 100);
        member.updateFatalChainGauge(resultFatalChainGauge);
        return resultFatalChainGauge;
    }

}
