package com.gbf.granblue_simulator.battle.domain.actor.prop;

import com.gbf.granblue_simulator.metadata.domain.actor.BaseActor;
import com.gbf.granblue_simulator.metadata.domain.actor.ElementType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusModifierType;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.gbf.granblue_simulator.battle.logic.util.StatusUtil.*;
import static com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusModifierType.*;

@Builder
@Getter(value = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
@ToString
@Slf4j
public class StatusDetails implements Cloneable {

    private Long actorId;
    private String actorName;
    private boolean isEnemy;

    /* 기본 스텟 */
    // 기본스텟은 캐릭터 LB, 반지, 귀걸이 등으로 수정가능한 옵션을 상정, BaseActor 값을 사용
    private int baseAtk; // 캐릭터 10000, 적 N
    private double baseDef; // 캐릭터 1.0, 적 N > 10.0 /.1f
    private int baseMaxHp; // 캐릭터 20000
    private double baseDoubleAttackRate; // 캐릭터 0.25
    private double baseTripleAttackRate; // 캐릭터 0.1
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
    private double atkDownForfeitRate; // 상실 항 (일방공인 감소 상한 50% 에 추가 감소)
    // 공인 - 별항
    private double atkUniqueUpRate; // 일반공인과 별도로 합산시 별항 합산 및 곱연산

    // 공격력 감소 효과합
    protected double getCalcedAtkDownRate(ElementType elementType) {
        double elementDown = switch (elementType) {
            case FIRE  -> atkFireDownRate;
            case WATER -> atkWaterDownRate;
            case EARTH -> atkEarthDownRate;
            case WIND  -> atkWindDownRate;
            case LIGHT -> atkLightDownRate;
            case DARK  -> atkDarkDownRate;
            default    -> 0;
        };
        double forfeit = Math.clamp(atkDownForfeitRate, 0, 0.1); // 상실항 - 하한을 무시하고 감소, 값의 상한은 10%
        double downMax = isEnemy ? 0.5 + forfeit : 0.99; // 아군에 대한 공격력 감소 상한 99% / 적에대한 공격력 감소상한 50% + 상실항 10%
        return Math.min(atkDownRate + elementDown + forfeit, downMax);
    }

    // 공인 - 속성항
    private double atkFireDownRate;
    private double atkWaterDownRate;
    private double atkEarthDownRate;
    private double atkWindDownRate;
    private double atkLightDownRate;
    private double atkDarkDownRate;

    private double atkFireUpRate;
    private double atkWaterUpRate;
    private double atkEarthUpRate;
    private double atkWindUpRate;
    private double atkLightUpRate;
    private double atkDarkUpRate;

    protected double getElementAtkUpRate(ElementType elementType) {
        return switch (elementType) {
            case FIRE  -> atkFireUpRate;
            case WATER -> atkWaterUpRate;
            case EARTH -> atkEarthUpRate;
            case WIND  -> atkWindUpRate;
            case LIGHT -> atkLightUpRate;
            case DARK  -> atkDarkUpRate;
            default    -> 0;
        };
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

    // 최종공격력
    public int getCalcedAtk(double hpRate, ElementType elementType) {
        double resultAtk = baseAtk
                * (1 + atkUpRate - getCalcedAtkDownRate(elementType))
                * (1 + this.getElementAtkUpRate(elementType))
                * (1 + this.atkUniqueUpRate)
                * (1 + this.getWeaponAtkUpRate())
                * (1 + this.getCalcedStrengthRate(hpRate))
                * (1 + this.getCalcedJammedRate(hpRate))
                ;
        // log.info("[getCalcedAtk] atkRate = {}, elementAtkRate = {}, weaponAtkUpRate = {}, strengthRate = {}, jammedRate = {}, uniqueUpRate = {}", this.getCalcedAtkRate(), this.getElementAtkRate(elementType), this.getWeaponAtkUpRate(), this.getCalcedStrengthRate(hpRate), this.getCalcedJammedRate(hpRate), this.getUniqueUpRate());
        return (int) resultAtk;
    }

    /* 방어 */
    // 방어 - 일반항
    private double defUpRate;
    private double defDownRate;
    // 방어 - 상실항
    private double defDownForfeitRate; // 일반항 방어력 감소 상한 50% 에 추가감소
    // 방어 - 속성항
    private double fireDefDown;
    private double waterDefDown;
    private double earthDefDown;
    private double windDefDown;
    private double lightDefDown;
    private double darkDefDown;

    // 방어력 감소
    protected double getCalcedDefDownRate(ElementType elementType) {
        double elementDown = getElementDefDown(elementType);
        double forfeit = Math.clamp(defDownForfeitRate, 0, 0.1);

        double downMax = isEnemy ? 0.5 + forfeit : 0.99; // 적에대한 방어력 감소 상한 50% + 상실항 10% / 아군에 대한 방어력 감소 상한 99%
        return Math.min(defDownRate + elementDown + forfeit, downMax);
    }

    protected double getElementDefDown(ElementType elementType) {
        return switch (elementType) {
            case FIRE -> this.fireDefDown;
            case WATER -> this.waterDefDown;
            case EARTH -> this.earthDefDown;
            case WIND -> this.windDefDown;
            case LIGHT -> this.lightDefDown;
            case DARK -> this.darkDefDown;
            default -> 0; // 무속성 0 반환, 기본 Status.def 에서 PLAIN 으로 사용
        };
    }

    // 방어력 증가
    protected double getCalcedDefUpRate() {
        return defUpRate; // 증가 상한 없음
    }

    // 최종 방어력
    public double getCalcedDef(ElementType elementType) {
        double resultDef = baseDef
                * (1 + getCalcedDefUpRate() - getCalcedDefDownRate(elementType));

        resultDef = Math.ceil(resultDef * 10) / 10; // 소수점 1째자리까지 허용
        return Math.max(resultDef, 0.1); // 최소값 0.1 ( 기초값 1 기준 피격데미지 10배 )
    }

    /* 수호 */
    // 수호 - 장비항
    private double weaponMaxHpUpRate;

    // 수호 - 스킬항
    private double maxHpDownRate;
    private double maxHpUpRate;

    protected double getCalcedMaxHpRate() {
        return Math.clamp(maxHpUpRate - maxHpDownRate, -0.99, 1.0); // 상한 100%, 하한 -99%
    }

    public int getCalcedMaxHp() {
        double resultMaxHp = baseMaxHp
                * (1 + this.getWeaponMaxHpUpRate())
                * (1 + this.getCalcedMaxHpRate());
        return (int) resultMaxHp;
    }

    // 베리어
    private double barrierInitValue; // 중복, 중첩 불가

    public int getCalcedBarrierInitValue() {
        return (int) barrierInitValue;
    }

    /* 적대심, 감싸기 */
    private LocalDateTime substituteAppliedTime; // 최신값
    /**
     *  감싸기 효과 부여 시간반환, 감싸기 효과 없을시 null
     */
    public LocalDateTime getCalcedSubstituteAppliedTime() { return substituteAppliedTime; }

    private double hostilityUpPoint; // 최댓값
    private double hostilityDownPoint; // 최댓값
    public int getCalcedHostilityPoint() { return (int) Math.max(hostilityUpPoint - hostilityDownPoint, 0); } // 하한0, 상한은 일반적으로 10000 (99%)

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

    /**
     * 약화 효과 성공률 (증가율, 로직내 기본 명중률 1.0)
     */
    public double getCalcedDebuffSuccessRate() {
        double upRate = Math.clamp(debuffSuccessUpRate, 0.0, 1.0);
        double downRate = Math.clamp(debuffSuccessDownRate, 0.0, 1.0);
        return Math.clamp(upRate - downRate, 0.0, 1.0); // 0 ~ 100%
    }

    // 약체 내성
    private double debuffResistUpRate;
    private double debuffResistDownRate;

    /**
     * 약화 효과 내성
     */
    public double getCalcedDebuffResistRate() {
        double upRate = Math.clamp(debuffResistUpRate, 0.0, 2.0);
        double downRate = Math.clamp(debuffResistDownRate, 0.0, 1.0);
        return Math.clamp(upRate - downRate, -1.0, 2.0); // 0 ~ 200%
    }

    /* 공격 명중률, 회피율 */
    // 명중률
    private double accuracyUpRate;
    private double accuracyDownRate;

    public double getCalcedAccuracyRate() {
        return baseAccuracyRate + accuracyUpRate - accuracyDownRate; // 상 하한은 최종 데미지 계산시 적용 (반드시 빗나감 회피 등)
    }

    private double normalAttackAccuracyDownRate;

    /**
     * 일반공격 명중률 감소 (암흑 효과)
     */
    public double getNormalAttackAccuracyDownRate() {
        return normalAttackAccuracyDownRate;
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

    // 피데미지 오의게이지 상승률
    private double chargeGaugeIncreaseUpRateOnDamaged;
    public double getCalcedChargeGaugeIncreaseRateOnDamaged() {
        return Math.clamp(chargeGaugeIncreaseUpRateOnDamaged, -1.0, 2.0); // 상한 200% (일단은), 하한 -100%
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

    /* 공격행동 횟수 */
    // modifier 을 분리해서 횟수별로 중복 부여 가능, 실제 적용은 가장 높은 값만
    private double doubleStrike;
    private double tripleStrike;
    private double quadrupleStrike;
    private double plusStrike;

    public int getCalcedStrikeCount() { // 공격 행동 시작 후에 로직에서 sync 를 통해 수정되면 다음턴 공격행동 시작시 반영됨
        double strikeCount = quadrupleStrike > 0 ? quadrupleStrike
                : tripleStrike > 0 ? tripleStrike
                : doubleStrike > 0 ? doubleStrike
                : 1;
        if (plusStrike > 0) strikeCount += plusStrike;
        return (int) Math.clamp(strikeCount, 0, 5); // 최대 5회로 고정해놓음 일단
    }

    /* 공격행동 봉인 */
    private double strikeSealed;

    public double getCalcedStrikeSealed() {
        return Math.clamp(this.strikeSealed, 0, 1.0); // 합산, 하한 0, 상한 1.0 (행동 방해확률 100%)
    }

    /* 오의 봉인 */
    private double chargeAttackSealed;

    public boolean getCalcedChargeAttackSealed() {
        return this.chargeAttackSealed > 0; // 있으면 1.0 고정
    }

    /* 조건 오의 */
    private Boolean conditionalChargeAttackCan;

    // 관련 효과가 없거나 조건을 달성하면 true
    public boolean isConditionalChargeAttackCan() {
        return this.conditionalChargeAttackCan == null || this.conditionalChargeAttackCan;
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

    /* 어빌리티 재사용 가능 */
    private double abilityReactivate;

    public int getMaxAbilityUseCount() {
        return abilityReactivate <= 1 ? 1 : (int) abilityReactivate; // reactivate 최소 유효값 2
    }

    // 요청 내에서 갱신, 마킹 =====================================================================================
    @Getter // 오의 발동후 마킹
    private int executedChargeAttackCount;

    public void increaseExecutedChargeAttackCount() {
        this.executedChargeAttackCount++;
    }

    @Getter // 오의 재발동시 마킹 -> 재발동시 오의 게이지를 소모하지 않아야 함. 여기서 마킹 후 체크
    private boolean isExecutingReactivatedChargeAttack;

    public void updateIsExecutingReactivatedChargeAttack(boolean executingReactivatedChargeAttack) {
        this.isExecutingReactivatedChargeAttack = executingReactivatedChargeAttack;
    }

    // 비 갱신, 기록용 init 후 수정금지 =============================================================================

    /* 공격행동 시작시 설정되는 공격행동 총 횟수 */
    @Getter
    private Integer endStrikeCount;

    public void initEndStrikeCount(int endStrikeCount) {
        this.endStrikeCount = endStrikeCount;
    }

    public void resetEndStrikeCount() {
        this.endStrikeCount = null;
    }


    // 초기화 =====================================================================================================

    public static StatusDetails init(Actor actor) {
        BaseActor baseActor = actor.getBaseActor();
        // CHECK 나중에 혹시 LB, 반지, 귀걸이 등 구현한다면, StatusEffect 로 부여시키는게 나을듯
        double weaponAtkUpRate = 30.0;  // 장비항 (양면 일반공인 600, ex 공인 100, 혼신 50, 기타 50 => 31.5 / 30정도로 일단 적용)
        double weaponMaxHpUpRate = 3.0; // 수호항 일단 상한 400% 인데 처리 x, 장비 수호항 300% 상정
        if (baseActor.isEnemy()) {
            weaponAtkUpRate = 0.0;
            weaponMaxHpUpRate = 0.0;
        }

        return StatusDetails.builder()
                .actorId(actor.getId())
                .actorName(baseActor.getName())
                .isEnemy(baseActor.isEnemy())

                .baseAtk(baseActor.getAtk())
                .baseDef(baseActor.getDef())
                .baseMaxHp(baseActor.getMaxHp())
                .baseDoubleAttackRate(baseActor.getDoubleAttackRate())
                .baseTripleAttackRate(baseActor.getTripleAttackRate())
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
        Map<StatusModifierType, List<StatusEffect>> map = getModifierMap(actor);
        this.atkUpRate = getModifierValueSum(map, ATK_UP);
        this.atkDownRate = getModifierValueSum(map, ATK_DOWN);
        this.strengthRate = getModifierValueSum(map, STRENGTH);
        this.jammedRate = getModifierValueSum(map, JAMMED);
        this.atkUniqueUpRate = getModifierValueSum(map, ATK_UP_UNIQUE);
        this.atkDownForfeitRate = getModifierValueMax(map, ATK_DOWN_FORFEIT);

        this.defUpRate = getModifierValueSum(map, DEF_UP);
        this.defDownRate = getModifierValueSum(map, DEF_DOWN);
        this.defDownForfeitRate = getModifierValueMax(map, DEF_DOWN_FORFEIT);

        // 속성공격력 상승, 속성 공격력 감소 - 합산
        this.atkFireUpRate = getModifierValueSum(map, ATK_UP_FIRE);
        this.atkFireDownRate = getModifierValueSum(map, ATK_DOWN_FIRE);
        this.atkWaterUpRate = getModifierValueSum(map, ATK_UP_WATER);
        this.atkWaterDownRate = getModifierValueSum(map, ATK_DOWN_WATER);
        this.atkEarthUpRate = getModifierValueSum(map, ATK_UP_EARTH);
        this.atkEarthDownRate = getModifierValueSum(map, ATK_DOWN_EARTH);
        this.atkWindUpRate = getModifierValueSum(map, ATK_UP_WIND);
        this.atkWindDownRate = getModifierValueSum(map, ATK_DOWN_WIND);
        this.atkLightUpRate = getModifierValueSum(map, ATK_UP_LIGHT);
        this.atkLightDownRate = getModifierValueSum(map, ATK_DOWN_LIGHT);
        this.atkDarkUpRate = getModifierValueSum(map, ATK_UP_DARK);
        this.atkDarkDownRate = getModifierValueSum(map, ATK_DOWN_DARK);

        // 속성 방어력 감소 - 합산
        this.fireDefDown = getModifierValueSum(map, DEF_DOWN_FIRE);
        this.waterDefDown = getModifierValueSum(map, DEF_DOWN_WATER);
        this.earthDefDown = getModifierValueSum(map, DEF_DOWN_EARTH);
        this.windDefDown = getModifierValueSum(map, DEF_DOWN_WIND);
        this.lightDefDown = getModifierValueSum(map, DEF_DOWN_LIGHT);
        this.darkDefDown = getModifierValueSum(map, DEF_DOWN_DARK);

        this.maxHpDownRate = getModifierValueSum(map, MAX_HP_DOWN);
        this.maxHpUpRate = getModifierValueSum(map, MAX_HP_UP);

        this.barrierInitValue = getModifierValueMax(map, BARRIER);

        this.substituteAppliedTime = getLatestModifierTime(map, SUBSTITUTE);
        this.hostilityUpPoint = getModifierValueMax(map, HOSTILITY_UP);
        this.hostilityDownPoint = getModifierValueMax(map, HOSTILITY_DOWN);

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
        this.normalAttackAccuracyDownRate = getModifierValueSum(map, NORMAL_ATTACK_ACCURACY_DOWN);

        this.dodgeUpRate = getModifierValueSum(map, DODGE_RATE_UP);

        this.criticalUpRate = getModifierValueSum(map, CRITICAL_RATE_UP);
        this.criticalDamageUpRate = getModifierValueSum(map, CRITICAL_DAMAGE_UP);

        this.chargeGaugeIncreaseUpRate = getModifierValueSum(map, CHARGE_GAUGE_INCREASE_UP);
        this.chargeGaugeIncreaseDownRate = getModifierValueSum(map, CHARGE_GAUGE_INCREASE_DOWN);
        this.chargeGaugeIncreaseUpRateOnDamaged = getModifierValueSum(map, CHARGE_GAUGE_INCREASE_UP_ON_DAMAGED);
        this.attackChargeGaugeIncreaseDownRate = getModifierValueSum(map, ATTACK_CHARGE_GAUGE_INCREASE_DOWN);

        this.conditionalChargeAttackCan = isReachedMaxLevelByModifier(map, CONDITIONAL_CHARGE_ATTACK);

        this.additionalDamageARate = getModifierValueMax(map, ADDITIONAL_DAMAGE_A);
        this.additionalDamageSRate = getModifierValueMax(map, ADDITIONAL_DAMAGE_S);
        this.additionalDamageCRate = getModifierValueMax(map, ADDITIONAL_DAMAGE_C);
        this.additionalDamageWRate = getModifierValueMax(map, ADDITIONAL_DAMAGE_W);
        this.additionalDamageURate = getModifierValueMax(map, ADDITIONAL_DAMAGE_U);

        this.attackMultiHitCount = getModifierValueMax(map, ATTACK_MULTI_HIT);

        this.doubleStrike = getModifierValueMax(map, DOUBLE_STRIKE);
        this.tripleStrike = getModifierValueMax(map, TRIPLE_STRIKE);
        this.quadrupleStrike = getModifierValueMax(map, QUADRUPLE_STRIKE);
        this.plusStrike = getModifierValueMax(map, PLUS_STRIKE);

        this.strikeSealed = getModifierValueSum(map, STRIKE_SEALED); // 공격행동 방해는 방해율 합산

        this.chargeAttackSealed = getModifierValueMax(map, CHARGE_ATTACK_SEALED); // 오의 봉인은 반드시 오의 불가

        this.firstAbilitySealed = getModifierValueMax(map, ABILITY_SEALED_FIRST); // 어빌리티 봉인은 반드시 어빌리티 불가
        this.secondAbilitySealed = getModifierValueMax(map, ABILITY_SEALED_SECOND);
        this.thirdAbilitySealed = getModifierValueMax(map, ABILITY_SEALED_THIRD);
        this.fourthAbilitySealed = getModifierValueMax(map, ABILITY_SEALED_FOURTH);
        this.allAbilitySealed = getModifierValueMax(map, ABILITY_SEALED_ALL);

        this.abilityReactivate = getModifierValueMax(map, ABILITY_REACTIVATE);
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
