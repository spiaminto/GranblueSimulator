package com.gbf.granblue_simulator.battle.domain.actor.prop;

import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.metadata.domain.actor.ElementType;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusModifierType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.gbf.granblue_simulator.battle.logic.util.StatusUtil.*;
import static com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusModifierType.*;

@Builder
@Getter
@AllArgsConstructor(access = AccessLevel.PROTECTED)
@EqualsAndHashCode
@ToString
public class DamageStatusDetails implements Cloneable {

    private Long actorId;
    private String actorName;

    /* 베이스 */
    @Getter(AccessLevel.PROTECTED)
    private double baseAmplifyDamageRate;
    @Getter(AccessLevel.PROTECTED)
    private double baseAttackAmplifyDamageRate;
    @Getter(AccessLevel.PROTECTED)
    private double baseAbilityAmplifyDamageRate;
    @Getter(AccessLevel.PROTECTED)
    private double baseChargeAttackAmplifyDamageRate;

    @Getter(AccessLevel.PROTECTED)
    private double baseDamageCapUpRate;
    @Getter(AccessLevel.PROTECTED)
    private double baseAttackDamageCapUpRate;
    @Getter(AccessLevel.PROTECTED)
    private double baseAbilityDamageCapUpRate;
    @Getter(AccessLevel.PROTECTED)
    private double baseChargeAttackDamageCapUpRate;

    /* 장비항 */
    private double weaponDamageCapUpRate;
    private double weaponNormalAttackDamageCapUpRate;
    private double weaponAbilityDamageCapUpRate;
    private double weaponChargeAttackDamageCapUpRate;
    private int weaponSupplementalDamage;
    private double weaponSeraphicAmplifyDamageRate;

    /* 데미지 배율 상승 */
    private double abilityDamageRateUpRate;
    private double chargeAttackDamageRateUpRate;
    public double getMoveDamageRateUpRate(MoveType moveType) {
        return switch (moveType) {
            case ABILITY -> this.abilityDamageRateUpRate;
            case CHARGE_ATTACK -> this.chargeAttackDamageRateUpRate;
            default -> 0;
        };
    }

    /* 데미지 상한 상승 */
    private double damageCapUpRate;
    private double attackDamageCapUpRate;
    private double abilityDamageCapUpRate;
    private double chargeAttackDamageCapUpRate;

    public double getMoveDamageCapUpRate(MoveType moveType) {
        return switch (moveType) {
            case ATTACK -> this.attackDamageCapUpRate + this.weaponNormalAttackDamageCapUpRate;
            case ABILITY -> this.abilityDamageCapUpRate + this.weaponAbilityDamageCapUpRate;
            case CHARGE_ATTACK -> this.chargeAttackDamageCapUpRate + this.weaponChargeAttackDamageCapUpRate;
            default -> throw new IllegalArgumentException("MoveType is not valid, moveType = " + moveType);
        };
    }


    /* 데미지 증가 */
    private int supplementalDamage;
    private int supplementalAttackDamage;
    private int supplementalAbilityDamage;
    private int supplementalChargeAttackDamage;

    public int getMoveSupplementalDamage(MoveType moveType) {
        return switch (moveType) {
            case ATTACK -> this.supplementalAttackDamage;
            case ABILITY -> this.supplementalAbilityDamage;
            case CHARGE_ATTACK -> this.supplementalChargeAttackDamage;
            default -> throw new IllegalArgumentException("MoveType is not valid, moveType = " + moveType);
        };
    }

    private int supplementalTripleAttackDamage;

    /**
     * 트리플 어택시 공격 데미지 상승
     */
    public int getTripleAttackSupplementalDamage() {
        return this.supplementalTripleAttackDamage;
    }

    /* 데미지 상승 */
    private double amplifyDamageRate;
    private double amplifyAttackDamageRate;
    private double amplifyAbilityDamageRate;
    private double amplifyChargeAttackDamageRate;

    public double getMoveAmplifyDamageRate(MoveType moveType) {
        return switch (moveType) {
            case ATTACK -> this.amplifyAttackDamageRate;
            case ABILITY -> this.amplifyAbilityDamageRate;
            case CHARGE_ATTACK -> this.amplifyChargeAttackDamageRate;
            default -> throw new IllegalArgumentException("MoveType is not valid, moveType = " + moveType);
        };
    }

    /* 데미지 감소 */
    private double amplifyChargeAttackDamageDownRate; // 적의 특수기 데미지 다운 에서 사용

    public double getChargeAttackAmplifyDamageDownRate() {
        return Math.min(amplifyChargeAttackDamageDownRate, 1); // 일단 상한 100%
    }


    // CHECK 상한 및 요다메 관련 감소 및 하락은 적의 받는 피해 감소로 구현.

    /* 받는 데미지 증가, 감소 */
    private int takenSupplementalDamageUpPoint;
    private int takenSupplementalDamageDownPoint;

    /* 받는 데미지 상승, 경감 */
    private double takenAmplifyDamageUpRate;
    private double takenAmplifyDamageDownRate;

    /* 받는 데미지 행동별 상승, 경감 */
    private double takenAttackDamageUpRate;
    private double takenAttackDamageDownRate;
    private double takenAbilityDamageUpRate;
    private double takenAbilityDamageDownRate;
    private double takenChargeAttackDamageUpRate;
    private double takenChargeAttackDamageDownRate;

    // CHECK 기본적으로 행동별 받는 데미지 경감은 배율만 적용. (아가스티아 고정경감 외에 기억안남)
    // CHECK 기억엔 어빌리티 데미지 컷, 어빌리티 데미지 고정 같은경우도 있었던거 같은데, 일단 위의 행동별 경감으로 대체
    public double getMoveTakenDamageUpRate(MoveType moveType) {
        return switch (moveType) {
            case ATTACK -> this.takenAttackDamageUpRate;
            case ABILITY -> this.takenAbilityDamageUpRate;
            case CHARGE_ATTACK -> this.takenChargeAttackDamageUpRate;
            default -> throw new IllegalArgumentException("MoveType is not valid, moveType = " + moveType);
        };
    }

    public double getMoveTakenDamageDownRate(MoveType moveType) {
        return switch (moveType) {
            case ATTACK -> this.takenAttackDamageDownRate;
            case ABILITY -> this.takenAbilityDamageDownRate;
            case CHARGE_ATTACK -> this.takenChargeAttackDamageDownRate;
            default -> throw new IllegalArgumentException("MoveType is not valid, moveType = " + moveType);
        };
    }

    /* 피격 속성 데미지 감소 (속성 내성, 일반적으로 캐릭터용) */
    private double takenFireDamageDown;
    private double takenWaterDamageDown;
    private double takenEarthDamageDown;
    private double takenWindDamageDown;
    private double takenLightDamageDown;
    private double takenDarkDamageDown;

    public double getTakenElementDamageDownRate(ElementType elementType) {
        return switch (elementType) {
            case FIRE -> this.takenFireDamageDown;
            case WATER -> this.takenWaterDamageDown;
            case EARTH -> this.takenEarthDamageDown;
            case WIND -> this.takenWindDamageDown;
            case LIGHT -> this.takenLightDamageDown;
            case DARK -> this.takenDarkDamageDown;
            default -> throw new IllegalArgumentException("ElementType is not valid, elementType = " + elementType);
        };
    }

    /* 데미지 컷 */
    private double takenDamageCutRate;
    private double takenFireDamageCutRate;
    private double takenWaterDamageCutRate;
    private double takenEarthDamageCutRate;
    private double takenWindDamageCutRate;
    private double takenLightDamageCutRate;
    private double takenDarkDamageCutRate;

    public double getTakenElementDamageCutRate(ElementType elementType) {
        return switch (elementType) {
            case FIRE -> this.takenFireDamageCutRate;
            case WATER -> this.takenWaterDamageCutRate;
            case EARTH -> this.takenEarthDamageCutRate;
            case WIND -> this.takenWindDamageCutRate;
            case LIGHT -> this.takenLightDamageCutRate;
            case DARK -> this.takenDarkDamageCutRate;
            default -> throw new IllegalArgumentException("ElementType is not valid, elementType = " + elementType);
        };
    }
    
    /* 공격 데미지 고정 */
    private Double damageFixPoint;

    /**
     * 공격 데미지 고정수치를 반환, 효과 적용중이 아닌경우 null 반환
     */
    public Integer getDamageFixPoint() {
        return this.damageFixPoint != null ? this.damageFixPoint.intValue() : null;
    }

    /* 피격 데미지 고정 */
    private Double takenDamageFixPoint;

    /**
     * 피격 데미지 고정수치를 반환 (0 ~ ), 효과 적용중이 아닌경우 null이 반환됨
     */
    public Integer getTakenDamageFixPoint() {
        return this.takenDamageFixPoint != null ? this.takenDamageFixPoint.intValue() : null;
    }

    private Double takenDamageFixPointFire;
    private Double takenDamageFixPointWater;
    private Double takenDamageFixPointEarth;
    private Double takenDamageFixPointWind;
    private Double takenDamageFixPointLight;
    private Double takenDamageFixPointDark;

    /**
     * 피격 데미지 고정수치를 반환( 0 ~ ), 효과 적용중이 아닌 경우 null 이 반환됨
     */
    public Integer getTakenElementDamageFixPoint(ElementType elementType) {
        return switch (elementType) {
            case FIRE -> this.takenDamageFixPointFire != null ? this.takenDamageFixPointFire.intValue() : null;
            case WATER -> this.takenDamageFixPointWater != null ? this.takenDamageFixPointWater.intValue() : null;
            case EARTH -> this.takenDamageFixPointEarth != null ? this.takenDamageFixPointEarth.intValue() : null;
            case WIND -> this.takenDamageFixPointWind != null ? this.takenDamageFixPointWind.intValue() : null;
            case LIGHT -> this.takenDamageFixPointLight != null ? this.takenDamageFixPointLight.intValue() : null;
            case DARK -> this.takenDamageFixPointDark != null ? this.takenDamageFixPointDark.intValue() : null;
            default -> throw new IllegalArgumentException("ElementType is not valid, elementType = " + elementType);
        };
    }

    /* 데미지 블록 (50%, 0.5 고정) */
    private double takenDamageBlockRate;

    /* 피격속성 변환 */
    private LocalDateTime takenFireSwitchTime;
    private LocalDateTime takenWaterSwitchTime;
    private LocalDateTime takenEarthSwitchTime;
    private LocalDateTime takenWindSwitchTime;
    private LocalDateTime takenLightSwitchTime;
    private LocalDateTime takenDarkSwitchTime;

    /**
     * 변환 속성 반환
     *
     * @return ElementType, 없으면 NONE
     */
    public ElementType getElementSwitchType() {
        List<Map.Entry<ElementType, LocalDateTime>> entries = new ArrayList<>();
        if (takenFireSwitchTime != null)
            entries.add(Map.entry(ElementType.FIRE, takenFireSwitchTime));
        if (takenWaterSwitchTime != null)
            entries.add(Map.entry(ElementType.WATER, takenWaterSwitchTime));
        if (takenEarthSwitchTime != null)
            entries.add(Map.entry(ElementType.EARTH, takenEarthSwitchTime));
        if (takenWindSwitchTime != null)
            entries.add(Map.entry(ElementType.WIND, takenWindSwitchTime));
        if (takenLightSwitchTime != null)
            entries.add(Map.entry(ElementType.LIGHT, takenLightSwitchTime));
        if (takenDarkSwitchTime != null)
            entries.add(Map.entry(ElementType.DARK, takenDarkSwitchTime));
        return entries.stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(ElementType.NONE);
    }

    /* 약점 속성 적용 */
    private double takenFireWeak;
    private double takenWaterWeak;
    private double takenEarthWeak;
    private double takenWindWeak;
    private double takenLightWeak;
    private double takenDarkWeak;

    public boolean isWeakenFor(ElementType elementType) {
        return switch (elementType) {
            case FIRE -> this.takenFireWeak > 0;
            case WATER -> this.takenWaterWeak > 0;
            case EARTH -> this.takenEarthWeak > 0;
            case WIND -> this.takenWindWeak > 0;
            case LIGHT -> this.takenLightWeak > 0;
            case DARK -> this.takenDarkWeak > 0;
            default -> false;
        };
    }


    public void syncDamageStatusDetails(Actor actor) {
        this.baseDamageCapUpRate = 0.0;
        this.baseAttackDamageCapUpRate = 0.0;
        this.baseAbilityDamageCapUpRate = 0.0;
        this.baseChargeAttackDamageCapUpRate = 0.0;
        this.baseAmplifyDamageRate = 0.0;
        this.baseAttackAmplifyDamageRate = 0.0;
        this.baseAbilityAmplifyDamageRate = 0.0;
        this.baseChargeAttackAmplifyDamageRate = 0.0;
        // CHECK 베이스 스탯들은 LB, 귀걸이, 반지 등 적용용. 현재 미구현. 해당 요다메는 amplify 로 통일

        Map<StatusModifierType, List<StatusEffect>> map = getModifierMap(actor);

        // 데미지 배율 증가
        this.abilityDamageRateUpRate = getModifierValueSum(map, ABILITY_DAMAGE_RATE_UP);
        this.chargeAttackDamageRateUpRate = getModifierValueSum(map, CHARGE_ATTACK_DAMAGE_RATE_UP);

        // 데미지 상한
        this.damageCapUpRate = this.baseDamageCapUpRate + getModifierValueSum(map, DAMAGE_CAP_UP);
        this.attackDamageCapUpRate = this.baseAttackDamageCapUpRate + getModifierValueSum(map, ATTACK_DAMAGE_CAP_UP);
        this.abilityDamageCapUpRate = this.baseAbilityDamageCapUpRate + getModifierValueSum(map, ABILITY_DAMAGE_CAP_UP);
        this.chargeAttackDamageCapUpRate = this.baseChargeAttackDamageCapUpRate + getModifierValueSum(map, CHARGE_ATTACK_DAMAGE_CAP_UP);

        // 공격 데미지 증가 (합산)
        this.supplementalDamage = (int) getModifierValueSum(map, SUPPLEMENTAL_DAMAGE_UP);
        this.supplementalAttackDamage = (int) getModifierValueSum(map, SUPPLEMENTAL_ATTACK_DAMAGE_UP);
        this.supplementalAbilityDamage = (int) getModifierValueSum(map, SUPPLEMENTAL_ABILITY_DAMAGE_UP);
        this.supplementalChargeAttackDamage = (int) getModifierValueSum(map, SUPPLEMENTAL_CHARGE_ATTACK_DAMAGE_UP);

        this.supplementalTripleAttackDamage = (int) getModifierValueSum(map, SUPPLEMENTAL_TRIPLE_ATTACK_DAMAGE_UP);

        // 공격 데미지 증가 (승산)
        this.amplifyDamageRate = this.baseAmplifyDamageRate + getModifierValueSum(map, AMPLIFY_DAMAGE_UP);
        this.amplifyAttackDamageRate = this.baseAttackAmplifyDamageRate + getModifierValueSum(map, AMPLIFY_ATTACK_DAMAGE_UP);
        this.amplifyAbilityDamageRate = this.baseAbilityAmplifyDamageRate + getModifierValueSum(map, AMPLIFY_ABILITY_DAMAGE_UP);
        this.amplifyChargeAttackDamageRate = this.baseChargeAttackAmplifyDamageRate + getModifierValueSum(map, AMPLIFY_CHARGE_ATTACK_DAMAGE_UP);
        this.amplifyChargeAttackDamageDownRate = getModifierValueSum(map, AMPLIFY_CHARGE_ATTACK_DAMAGE_DOWN);

        // 피격 데미지 증가, 감소 (합산)
        this.takenSupplementalDamageUpPoint = (int) getModifierValueSum(map, TAKEN_SUPPLEMENTAL_DAMAGE_UP);
        this.takenSupplementalDamageDownPoint = (int) getModifierValueSum(map, TAKEN_SUPPLEMENTAL_DAMAGE_DOWN);

        // 피격 데미지 증가, 감소 (승산)
        this.takenAmplifyDamageUpRate = getModifierValueSum(map, TAKEN_AMPLIFY_DAMAGE_UP);
        this.takenAmplifyDamageDownRate = getModifierValueSum(map, TAKEN_AMPLIFY_DAMAGE_DOWN);

        // 피격 행동 데미지 증가, 감소
        this.takenAttackDamageUpRate = getModifierValueSum(map, TAKEN_ATTACK_AMPLIFY_DAMAGE_UP);
        this.takenAttackDamageDownRate = getModifierValueSum(map, TAKEN_ATTACK_AMPLIFY_DAMAGE_DOWN);
        this.takenAbilityDamageUpRate = getModifierValueSum(map, TAKEN_ABILITY_AMPLIFY_DAMAGE_UP);
        this.takenAbilityDamageDownRate = getModifierValueSum(map, TAKEN_ABILITY_AMPLIFY_DAMAGE_DOWN);
        this.takenChargeAttackDamageUpRate = getModifierValueSum(map, TAKEN_CHARGE_ATTACK_AMPLIFY_DAMAGE_UP);
        this.takenChargeAttackDamageDownRate = getModifierValueSum(map, TAKEN_CHARGE_ATTACK_AMPLIFY_DAMAGE_DOWN);

        // 피격 속성 데미지 감소 (속성 내성 증가)
        this.takenFireDamageDown = getModifierValueMultiplied(map, TAKEN_DAMAGE_DOWN_FIRE);
        this.takenWaterDamageDown = getModifierValueMultiplied(map, TAKEN_DAMAGE_DOWN_WATER);
        this.takenEarthDamageDown = getModifierValueMultiplied(map, TAKEN_DAMAGE_DOWN_EARTH);
        this.takenWindDamageDown = getModifierValueMultiplied(map, TAKEN_DAMAGE_DOWN_WIND);
        this.takenLightDamageDown = getModifierValueMultiplied(map, TAKEN_DAMAGE_DOWN_LIGHT);
        this.takenDarkDamageDown = getModifierValueMultiplied(map, TAKEN_DAMAGE_DOWN_DARK);

        // 피격 데미지, 속성 데미지 컷
        this.takenDamageCutRate = getModifierValueSum(map, TAKEN_DAMAGE_CUT);
        this.takenFireDamageCutRate = getModifierValueSum(map, TAKEN_DAMAGE_CUT_FIRE);
        this.takenWaterDamageCutRate = getModifierValueSum(map, TAKEN_DAMAGE_CUT_WATER);
        this.takenEarthDamageCutRate = getModifierValueSum(map, TAKEN_DAMAGE_CUT_EARTH);
        this.takenWindDamageCutRate = getModifierValueSum(map, TAKEN_DAMAGE_CUT_WIND);
        this.takenLightDamageCutRate = getModifierValueSum(map, TAKEN_DAMAGE_CUT_LIGHT);
        this.takenDarkDamageCutRate = getModifierValueSum(map, TAKEN_DAMAGE_CUT_DARK);

        // 피격 데미지 고정
        this.takenDamageFixPoint =  getModifierValueMin(map, TAKEN_DAMAGE_FIX);
        this.takenDamageFixPointFire = getModifierValueMin(map, TAKEN_DAMAGE_FIX_FIRE);
        this.takenDamageFixPointWater = getModifierValueMin(map, TAKEN_DAMAGE_FIX_WATER);
        this.takenDamageFixPointEarth = getModifierValueMin(map, TAKEN_DAMAGE_FIX_EARTH);
        this.takenDamageFixPointWind = getModifierValueMin(map, TAKEN_DAMAGE_FIX_WIND);
        this.takenDamageFixPointLight = getModifierValueMin(map, TAKEN_DAMAGE_FIX_LIGHT);
        this.takenDamageFixPointDark = getModifierValueMin(map, TAKEN_DAMAGE_FIX_DARK);

        // 피격 데미지 블록
        this.takenDamageBlockRate = getModifierValueMax(map, TAKEN_DAMAGE_BLOCK);

        // 피격 속성 변환
        this.takenFireSwitchTime = getLatestModifierTime(map, TAKEN_DAMAGE_SWITCH_FIRE);
        this.takenWaterSwitchTime = getLatestModifierTime(map, TAKEN_DAMAGE_SWITCH_WATER);
        this.takenEarthSwitchTime = getLatestModifierTime(map, TAKEN_DAMAGE_SWITCH_EARTH);
        this.takenWindSwitchTime = getLatestModifierTime(map, TAKEN_DAMAGE_SWITCH_WIND);
        this.takenLightSwitchTime = getLatestModifierTime(map, TAKEN_DAMAGE_SWITCH_LIGHT);
        this.takenDarkSwitchTime = getLatestModifierTime(map, TAKEN_DAMAGE_SWITCH_DARK);


    }

    public static DamageStatusDetails init(Actor actor) {
        double weaponDamageCapUpRate = actor.isCharacter() ? 0.1 : 0; // 무기 일반데미지상한 상승항 10%
        double weaponNormalAttackDamageCapUpRate = actor.isCharacter() ? 0.1 : 0; // 무기 일반공격 상한 상승 10%
        double weaponAbilityDamageCapUpRate = actor.isCharacter() ? 0.5 : 0; // 무기 어빌리티 데미지 상한 상승 50%
        double weaponChargeAttackDamageCapUpRate = actor.isCharacter() ? 0.15 : 0; // 무기 어빌리티 데미지 상한 상승 15% (상향전)
        int weaponSupplementalDamage = actor.isCharacter() ? 5000 : 0; // 무기 요다메 5천
        double weaponSeraphicAmplifyDamageRate = actor.isCharacter() ? 0.2 : 0; // 천사항 20%

        return DamageStatusDetails.builder()
                .actorId(actor.getId())
                .actorName(actor.getName())

                .weaponDamageCapUpRate(weaponDamageCapUpRate)
                .weaponNormalAttackDamageCapUpRate(weaponNormalAttackDamageCapUpRate)
                .weaponAbilityDamageCapUpRate(weaponAbilityDamageCapUpRate)
                .weaponChargeAttackDamageCapUpRate(weaponChargeAttackDamageCapUpRate)
                .weaponSupplementalDamage(weaponSupplementalDamage)
                .weaponSeraphicAmplifyDamageRate(weaponSeraphicAmplifyDamageRate)
                .build();
    }

    /**
     * 로그 저장용 복사
     * 필드는 모두 immutable 로 관리, shallow copy
     *
     * @return
     */
    @Override
    public DamageStatusDetails clone() {
        try {
            DamageStatusDetails clone = (DamageStatusDetails) super.clone();
            // TODO: copy mutable state here, so the clone can't change the internals of the original
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }
}
