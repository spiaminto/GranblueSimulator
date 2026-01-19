package com.gbf.granblue_simulator.battle.domain.actor.prop;

import com.gbf.granblue_simulator.metadata.domain.actor.ElementType;
import com.gbf.granblue_simulator.metadata.domain.statuseffect.StatusModifierType;
import com.gbf.granblue_simulator.battle.domain.actor.Actor;
import com.gbf.granblue_simulator.metadata.domain.move.MoveType;
import lombok.*;
import org.springframework.cglib.core.Local;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Stream;

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

    /* 데미지 상한 상승 */
    private double damageCapUpRate;
    private double attackDamageCapUpRate;
    private double abilityDamageCapUpRate;
    private double chargeAttackDamageCapUpRate;

    public double getMoveDamageCapUpRate(MoveType moveType) {
        return switch (moveType) {
            case ATTACK -> this.attackDamageCapUpRate;
            case ABILITY -> this.abilityDamageCapUpRate;
            case CHARGE_ATTACK -> this.chargeAttackDamageCapUpRate;
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

    /* 데미지 고정 */
    private int takenDamageFixPoint;

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
        return Stream.of(
                        Map.entry(ElementType.FIRE, takenFireSwitchTime),
                        Map.entry(ElementType.WATER, takenWaterSwitchTime),
                        Map.entry(ElementType.EARTH, takenEarthSwitchTime),
                        Map.entry(ElementType.WIND, takenWindSwitchTime),
                        Map.entry(ElementType.LIGHT, takenLightSwitchTime),
                        Map.entry(ElementType.DARK, takenDarkSwitchTime))
                .filter(entry -> entry.getValue() != null)
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(ElementType.NONE);
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

        // 공격 데미지 증가 (승산)
        this.amplifyDamageRate = this.baseAmplifyDamageRate + getModifierValueSum(map, AMPLIFY_DAMAGE_UP);
        this.amplifyAttackDamageRate = this.baseAttackAmplifyDamageRate + getModifierValueSum(map, AMPLIFY_ATTACK_DAMAGE_UP);
        this.amplifyAbilityDamageRate = this.baseAbilityAmplifyDamageRate + getModifierValueSum(map, AMPLIFY_ABILITY_DAMAGE_UP);
        this.amplifyChargeAttackDamageRate = this.baseChargeAttackAmplifyDamageRate + getModifierValueSum(map, AMPLIFY_CHARGE_ATTACK_DAMAGE_UP);

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

        // 피격 속성 데미지 감소
        this.takenFireDamageDown = getModifierValueMultiplied(map, TAKEN_FIRE_DAMAGE_DOWN);
        this.takenWaterDamageDown = getModifierValueMultiplied(map, TAKEN_WATER_DAMAGE_DOWN);
        this.takenEarthDamageDown = getModifierValueMultiplied(map, TAKEN_EARTH_DAMAGE_DOWN);
        this.takenWindDamageDown = getModifierValueMultiplied(map, TAKEN_WIND_DAMAGE_DOWN);
        this.takenLightDamageDown = getModifierValueMultiplied(map, TAKEN_LIGHT_DAMAGE_DOWN);
        this.takenDarkDamageDown = getModifierValueMultiplied(map, TAKEN_DARK_DAMAGE_DOWN);

        // 피격 데미지, 속성 데미지 컷
        this.takenDamageCutRate = getModifierValueSum(map, TAKEN_DAMAGE_CUT);
        this.takenFireDamageCutRate = getModifierValueSum(map, TAKEN_FIRE_DAMAGE_CUT);
        this.takenWaterDamageCutRate = getModifierValueSum(map, TAKEN_WATER_DAMAGE_CUT);
        this.takenEarthDamageCutRate = getModifierValueSum(map, TAKEN_EARTH_DAMAGE_CUT);
        this.takenWindDamageCutRate = getModifierValueSum(map, TAKEN_WIND_DAMAGE_CUT);
        this.takenLightDamageCutRate = getModifierValueSum(map, TAKEN_LIGHT_DAMAGE_CUT);
        this.takenDarkDamageCutRate = getModifierValueSum(map, TAKEN_DARK_DAMAGE_CUT);

        // 피격 데미지 고정
        this.takenDamageFixPoint = (int) getModifierValueMin(map, TAKEN_DAMAGE_FIX);

        // 피격 데미지 블록
        this.takenDamageBlockRate = getModifierValueMin(map, TAKEN_DAMAGE_BLOCK); // 현재 0.5 고정

        // 피격 속성 변환
        this.takenFireSwitchTime = getLatestModifierTime(map, TAKEN_FIRE_SWITCH);
        this.takenWaterSwitchTime = getLatestModifierTime(map, TAKEN_WATER_SWITCH);
        this.takenEarthSwitchTime = getLatestModifierTime(map, TAKEN_EARTH_SWITCH);
        this.takenWindSwitchTime = getLatestModifierTime(map, TAKEN_WIND_SWITCH);
        this.takenLightSwitchTime = getLatestModifierTime(map, TAKEN_LIGHT_SWITCH);
        this.takenDarkSwitchTime = getLatestModifierTime(map, TAKEN_DARK_SWITCH);


    }

    public static DamageStatusDetails init(Actor actor) {
        return DamageStatusDetails.builder()
                .actorId(actor.getId())
                .actorName(actor.getName())
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
