package com.gbf.granblue_simulator.battle.domain.actor.prop;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class WeaponStatus {

    private double weaponAtkUpRate; // 무기공인
    private double weaponAtkUpUniqueRate; // 천사항 대신 적용하는 무기공인 (별도)
    private double weaponMaxHpUpRate; // 무기 수호

    private double weaponDamageCapUpRate; // 일반데미지 상한
    private double weaponNormalAttackDamageCapUpRate; // 일반공격 데미지 상한
    private double weaponAbilityDamageCapUpRate; // 어빌리티 데미지 상한
    private double weaponChargeAttackDamageCapUpRate; // 오의 데미지 상한
    private int weaponSupplementalDamage; // 무기 공격데미지상한 (볼티지)
    private double weaponSeraphicAmplifyDamageRate; // 천사항

}
