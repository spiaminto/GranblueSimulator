package com.gbf.granblue_simulator.battle.domain.actor.prop;

import com.gbf.granblue_simulator.metadata.domain.actor.BaseActor;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusModifier;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusModifierType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusModifierType;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import lombok.*;

import java.util.List;
import java.util.Map;

import static com.gbf.granblue_simulator.battle.logic.util.StatusUtil.*;
import static com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusModifierType.*;

@Builder
@Getter(value = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
@ToString
public class StatusDetails implements Cloneable {

    private Long actorId;
    private String actorName;

    /* 기본 스텟 */
    // 기본스텟은 캐릭터 LB, 반지, 귀걸이 등으로 수정가능한 옵션을 상정, BaseActor 값을 사용
    private int baseAtk; // 캐릭터 10000, 적 N
    private double baseDef; // 캐릭터 2.0, 적 N > 10.0 /.1f
    private int baseMaxHp; // 캐릭터 20000
    private double baseDoubleAttackRate; // 캐릭터 0.25
    private double baseTripleAttackRate; // 캐릭터 0.1
    private double baseDebuffSuccessRate; // 1
    private double baseDebuffResistRate; // 0
    private double baseAccuracyRate; // 1
    private double baseDodgeRate; // 0
    private double baseCriticalRate; // 0
    private double baseCriticalDamageRate; // 0.5
    private double baseChargeGaugeIncreaseRate; // 0

    /* 공인 */
    // 공인 - 장비항
    private double weaponAtkUpRate;

    // 공인 - 일반항
    private double atkUpRate;
    private double atkDownRate;

    protected double getCalcedAtkRate() {
        return Math.max(atkUpRate - atkDownRate, -0.99); // 공격력 상승 상한 X, 공격력 감소 상한 -99%
    }

    public int getCalcedAtk(double hpRate) {
        double resultAtk = baseAtk
                * (1 + this.getCalcedAtkRate())
                * (1 + this.getWeaponAtkUpRate())
                * (1 + this.getCalcedStrengthRate(hpRate))
                * (1 + this.getCalcedJammedRate(hpRate))
                * (1 + this.getUniqueUpRate());
        return (int) resultAtk;
    }

    // 공인 - 혼신항
    private double strengthRate;

    public double getCalcedStrengthRate(double hpRate) {
        return hpRate > 0.5 ? this.strengthRate * hpRate : this.strengthRate * 0.5; // 아군의 체력이 50% 미만이면 최소배율 (50%) 적용
    }

    // 공인 - 배수항
    private double jammedRate;

    public double getCalcedJammedRate(double hpRate) {
        return hpRate < 0.5 ? this.jammedRate * (1 - hpRate) : this.jammedRate * 0.5; // 아군의 체력이 50% 초과면 최소배율 (50%) 적용
    }

    // 공인 - 별항
    private double uniqueUpRate;

    /* 방어 */
    // 방어 - 일반항
    private double defUpRate;
    private double defDownRate;

    protected double getCalcedDefRate() {
        return Math.max(defUpRate - defDownRate, -0.5); // 방어령 상승 X, 하한 -50%
    }

    public double getCalcedDef() {
        double resultDef = baseDef
                * (1 + this.getCalcedDefRate());
        resultDef = Math.ceil(resultDef * 10) / 10; // 소숫점 1째자리 까지만 허용
        return resultDef;
    }

    /* 수호 */
    // 수호 - 장비항
    private double weaponMaxHpUpRate;

    // 수호 - 스킬항
    private double maxHpDownRate;

    protected double getCalcedMaxHpDownRate() {
        return maxHpDownRate = Math.min(maxHpDownRate, 0.99); // 최대체력 감소 상한 -99%
    }

    public int getCalcedMaxHp() {
        double resultMaxHp = baseMaxHp
                * (1 + this.getWeaponMaxHpUpRate())
                * (1 - this.getCalcedMaxHpDownRate());
        return (int) resultMaxHp;
    }

    /* 회복 */
    // 회복량 상승, 감소
    private double healUpRate;
    private double healDownRate;

    public double getCalcedHealRate() {
        return Math.clamp(0, 1 + healUpRate - healDownRate, 2.0); // 하한 0 상한 2 (100%증가)
    }

    /* 연공 */
    // 더블어택
    private double doubleAttackUpRate; // 반드시 더블어택 99.0
    private double doubleAttackDownRate; // 반드시 싱글어택 -90.0

    public double getCalcedDoubleAttackRate() {
        return Math.max(baseDoubleAttackRate + doubleAttackUpRate - doubleAttackDownRate, 0); // 상한 x, 하한 0
    }

    // 트리플 어택
    private double tripleAttackUpRate; // 반드시 트리플어택 99.0
    private double tripleAttackDownRate; // 반드시 싱글어택 -90.0

    public double getCalcedTripleAttackRate() {
        return Math.max(baseTripleAttackRate + tripleAttackUpRate - tripleAttackDownRate, 0); // 상한 x, 하한 0
    }

    /* 약체 성공, 내성 */
    // 약체성공
    private double debuffSuccessUpRate;
    private double debuffSuccessDownRate;

    public double getCalcedDebuffSuccessRate() {
        return Math.max(baseDebuffSuccessRate + debuffSuccessUpRate - debuffSuccessDownRate, 0); // 상한 x 하한 0 필중처리를 위해 상한 없음. (필중의 경우 100 이상 값 필수, 999예정)
    }

    // 약체내성
    private double debuffResistUpRate;
    private double debuffResistDownRate;

    public double getCalcedDebuffResistRate() {
        return Math.clamp(baseDebuffResistRate + debuffResistUpRate - debuffResistDownRate, -0.99, 1.0); // 상한 1.0, 하한 - 99% 저항이 음수일경우 성공률에 양으로 곱해짐
    }

    /* 공격 명중률, 회피율 */
    // 명중률
    private double accuracyUpRate;
    private double accuracyDownRate;

    public double getCalcedAccuracyRate() {
        return baseAccuracyRate + accuracyUpRate - accuracyDownRate; // 상 하한은 최종 데미지 계산시 적용 (반드시 빗나감 회피 등)
    }

    // 회피율
    private double dodgeUpRate;

    public double getCalcedDodgeRate() {
        return Math.min(baseDodgeRate + dodgeUpRate, 1.0); // 상 하한은 최종 데미지 계산시 적용 (반드시 회피 효과 등)
    }

    /* 크리티컬 확률, 크리티컬 배율 */
    // 크리티컬 확률
    private double criticalUpRate;

    public double getCalcedCriticalRate() {
        return Math.min(baseCriticalRate + criticalUpRate, 1.0); // 상한 100%, 하한 X
    }

    // 크리티컬 (데미지) 배율
    private double criticalDamageUpRate;

    public double getCalcedCriticalDamageRate() {
        return Math.min(baseCriticalDamageRate + criticalDamageUpRate, 1.0); // 상한 100%, 하한 X
    }

    /* 오의 게이지 상승률 */
    // 오의게이지 상승률
    private double chargeGaugeIncreaseUpRate;
    private double chargeGaugeIncreaseDownRate;

    public double getCalcedChargeGaugeIncreaseRate() {
        return Math.clamp(baseChargeGaugeIncreaseRate + chargeGaugeIncreaseUpRate - chargeGaugeIncreaseDownRate, -1.0, 1.0); // 상한 100% 하한 -100%
    }

    // 일반공격 오의게이지 상승률
    private double attackChargeGaugeIncreaseDownRate; // 일반공격 오의게이지 상승량 감소, 주로 서포어비로만 사용되며 일반 상승량 계산 x, 로직에서 직접사용

    public double getCalcedAttackChargeGaugeIncreaseRate() {
        return Math.clamp(1 - attackChargeGaugeIncreaseDownRate, 0, 1.0); // 최소 0: 일반공격으로 오의게이지 상승하지 않음, 상한 1.0: 기본
    }

    /* 추격 */
    private double additionalDamageARate; // 어빌리티 항
    private double additionalDamageSRate; // 서포트 어빌리티 항
    private double additionalDamageCRate; // 오의 항
    private double additionalDamageWRate; // 무기 항 (허사항)
    private double additionalDamageURate; // 별 항

    public List<Double> getCalcedAdditionalDamageRateList() {
        // 모든 추격의 상한은 100%
        double additionalDamageARate = Math.min(this.additionalDamageARate, 1.0);
        double additionalDamageSRate = Math.min(this.additionalDamageSRate, 1.0);
        double additionalDamageCRate = Math.min(this.additionalDamageCRate, 1.0);
        double additionalDamageWRate = Math.min(this.additionalDamageWRate, 1.0);
        double additionalDamageURate = Math.min(this.additionalDamageURate, 1.0);
        return List.of(additionalDamageARate, additionalDamageSRate, additionalDamageCRate, additionalDamageWRate, additionalDamageURate);
    }

    /* 난격 */
    private double attackMultiHitCount; // 난격 카운트
    public int getCalcedAttackMultiHitCount() {
        return (int) Math.max(attackMultiHitCount, 1); // 효과 없을시 1부터 시작
    }

    /* 어빌리티 봉인 */
    private double firstAbilitySealed;
    private double secondAbilitySealed;
    private double thirdAbilitySealed;
    private double fourthAbilitySealed;
    private double allAbilitySealed;

    public List<Boolean> getCalcedAbilitySealedList() {
        // 값이 0 인경우 없음, 있으면 1 ~
        return allAbilitySealed > 0
                ? List.of(true, true, true, true)
                : List.of(firstAbilitySealed > 0, secondAbilitySealed > 0, thirdAbilitySealed > 0, fourthAbilitySealed > 0);
    }

    public static StatusDetails init(Actor actor) {
        BaseActor baseActor = actor.getBaseActor();
        // CHECK 나중에 혹시 LB, 반지, 귀걸이 등 BaseActor 관련 스테이터스를 수정할경우 여기서 base 에 가산, sync 는 없이.
        double weaponAtkUpRate = 50.0;  // 장비항 (마그나 400, 일반 100, ex 100, 속성 150 상정, 5 * 2 * 2 * 2.5 50배율 상정)
        double weaponMaxHpUpRate = 3.0; // 수호항 일단 상한 400% 인데 처리 x, 장비 수호항 300% 상정
        if (baseActor.isEnemy()) {
            weaponAtkUpRate = 0.0;
            weaponMaxHpUpRate = 0.0;
        }

        return StatusDetails.builder()
                .actorId(actor.getId())
                .actorName(baseActor.getName())

                .baseAtk(baseActor.getAtk())
                .baseDef(baseActor.getDef())
                .baseMaxHp(baseActor.getMaxHp())
                .baseDoubleAttackRate(baseActor.getDoubleAttackRate())
                .baseTripleAttackRate(baseActor.getTripleAttackRate())
                .baseDebuffSuccessRate(baseActor.getDebuffSuccessRate())
                .baseDebuffResistRate(baseActor.getDebuffResistRate())
                .baseAccuracyRate(baseActor.getAccuracyRate())
                .baseDodgeRate(baseActor.getDodgeRate())
                .baseCriticalRate(baseActor.getCriticalRate())
                .baseCriticalDamageRate(baseActor.getCriticalDamageRate())
                .baseChargeGaugeIncreaseRate(baseActor.getChargeGaugeIncreaseRate())

                .weaponAtkUpRate(weaponAtkUpRate)
                .weaponMaxHpUpRate(weaponMaxHpUpRate)
                .build();
    }


    /**
     * 스테이터스 변동 동기화
     */
    public void syncStatusDetails(Actor actor) {
        Map<StatusModifierType, List<StatusModifier>> map = getModifierMap(actor);
        this.atkUpRate = getModifierValueSum(map, ATK_UP);
        this.atkDownRate = getModifierValueSum(map, ATK_DOWN);
        this.strengthRate = getModifierValueSum(map, STRENGTH);
        this.jammedRate = getModifierValueSum(map, JAMMED);
        this.uniqueUpRate = getModifierValueSum(map, ATK_UP_UNIQUE);

        this.defUpRate = getModifierValueSum(map, DEF_UP);
        this.defDownRate = getModifierValueSum(map, DEF_DOWN);

        this.maxHpDownRate = getModifierValueSum(map, MAX_HP_DOWN);

        this.healUpRate = getModifierValueSum(map, HEAL_UP);
        this.healDownRate = getModifierValueSum(map, HEAL_DOWN);

        this.doubleAttackDownRate = getModifierValueSum(map, DOUBLE_ATTACK_RATE_DOWN);
        this.doubleAttackUpRate = getModifierValueSum(map, DOUBLE_ATTACK_RATE_UP);

        this.tripleAttackDownRate = getModifierValueSum(map, TRIPLE_ATTACK_RATE_DOWN);
        this.tripleAttackUpRate = getModifierValueSum(map, TRIPLE_ATTACK_RATE_UP);

        this.debuffSuccessUpRate = getModifierValueSum(map, DEBUFF_SUCCESS_UP);
        this.debuffSuccessDownRate = getModifierValueSum(map, DEBUFF_SUCCESS_DOWN);

        this.debuffResistUpRate = getModifierValueSum(map, DEBUFF_RESIST_UP);
        this.debuffResistDownRate = getModifierValueSum(map, DEBUFF_RESIST_DOWN);

        this.accuracyUpRate = getModifierValueSum(map, HIT_ACCURACY_UP);
        this.accuracyDownRate = getModifierValueSum(map, HIT_ACCURACY_DOWN);

        this.dodgeUpRate = getModifierValueSum(map, DODGE_RATE_UP);

        this.criticalUpRate = getModifierValueSum(map, CRITICAL_RATE_UP);
        this.criticalDamageUpRate = getModifierValueSum(map, CRITICAL_DAMAGE_UP);

        this.chargeGaugeIncreaseUpRate = getModifierValueSum(map, CHARGE_GAUGE_INCREASE_UP);
        this.chargeGaugeIncreaseDownRate = getModifierValueSum(map, CHARGE_GAUGE_INCREASE_DOWN);
        this.attackChargeGaugeIncreaseDownRate = getModifierValueSum(map, ATTACK_CHARGE_GAUGE_INCREASE_DOWN);

        this.additionalDamageARate = getModifierValueMax(map, ADDITIONAL_DAMAGE_A);
        this.additionalDamageSRate = getModifierValueMax(map, ADDITIONAL_DAMAGE_S);
        this.additionalDamageCRate = getModifierValueMax(map, ADDITIONAL_DAMAGE_C);
        this.additionalDamageWRate = getModifierValueMax(map, ADDITIONAL_DAMAGE_W);
        this.additionalDamageURate = getModifierValueMax(map, ADDITIONAL_DAMAGE_U);

        this.attackMultiHitCount = getModifierValueMax(map, ATTACK_MULTI_HIT);

        this.firstAbilitySealed = getModifierValueMax(map, ABILITY_SEALED_FIRST);
        this.secondAbilitySealed = getModifierValueMax(map, ABILITY_SEALED_SECOND);
        this.thirdAbilitySealed = getModifierValueMax(map, ABILITY_SEALED_THIRD);
        this.fourthAbilitySealed = getModifierValueMax(map, ABILITY_SEALED_FOURTH);
        this.allAbilitySealed = getModifierValueMax(map, ABILITY_SEALED_ALL);
    }

    /**
     * 로그 저장용 복사
     * 필드는 모두 immutable 로 관리, shallow copy
     *
     * @return
     */
    @Override
    public StatusDetails clone() {
        try {
            StatusDetails clone = (StatusDetails) super.clone();
            // TODO: copy mutable state here, so the clone can't change the internals of the original
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
