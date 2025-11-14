package com.gbf.granblue_simulator.domain.base.statuseffect;

/**
 * StatusEffect.StatusEffectType. StatusEffect.value 가 가지는 값의 의미를 [] 안에 써넣을것
 * 값은 모두 읽기전용!!!!
 */
public enum StatusModifierType {

    // 공격력
    // 공인항
    ATK_UP,     // [상승배율]
    ATK_DOWN,   // [감소배율]
    // 혼신항
    STRENGTH, // [최대상승배율] - 감소없음
    // 배수항
    JAMMED, // [최대상승배율] - 감소없음
    // 별항
    ATK_UP_UNIQUE, //[상승배율]
    //    ATK_DOWN_UNIQUE, // [감소배율]
    // 공격 데미지 상승
    SUPPLEMENTAL_DAMAGE_UP, // 공격 데미지 상승 [상승(가산)수치]
    SUPPLEMENTAL_ATTACK_DAMAGE_UP, // 공격 데미지 상승 [상승(가산)수치]
    SUPPLEMENTAL_ABILITY_DAMAGE_UP, // 어빌리티 데미지 상승 [상승(가산)수치]
    SUPPLEMENTAL_CHARGE_ATTACK_DAMAGE_UP, // [상승(가산)수치]
    // 공격 데미지 업
    AMPLIFY_DAMAGE_UP, // 공격 데미지 N% 업 [상승(승산)배율]
    AMPLIFY_ATTACK_DAMAGE_UP, // 공격 데미지 N% 업 [상승(승산)배율]
    AMPLIFY_ABILITY_DAMAGE_UP, // 어빌리티 데미지 N% 업 [상승(승산)배율]
    AMPLIFY_CHARGE_ATTACK_DAMAGE_UP, // [상승(승산)배율]
    // 데미지 상한
    DAMAGE_CAP_UP, // [상승배율] - 감소없음
    ATTACK_DAMAGE_CAP_UP, // [상승배율]
    ABILITY_DAMAGE_CAP_UP, // [상승배율]
    CHARGE_ATTACK_DAMAGE_CAP_UP, // [상승배율]

    // 방어력
    DEF_UP, // [상승배율]
    DEF_DOWN, // [감소배율]
    TAKEN_DAMAGE_CUT, // [데미지컷 배율]
    // 피격 데미지 상승
    TAKEN_SUPPLEMENTAL_DAMAGE_DOWN, // 피격데미지 감소 [상승수치]
    TAKEN_SUPPLEMENTAL_DAMAGE_UP, // 피격 데미지 증가 [감소수치]
    // 피격 데미지 업
    TAKEN_AMPLIFY_DAMAGE_UP, // 피격 데미지 업 [상승배율]
    TAKEN_AMPLIFY_DAMAGE_DOWN, // 피격 데미지 다운 [감소배율]
    TAKEN_ATTACK_AMPLIFY_DAMAGE_UP, // 피격 일반공격 데미지 업 [상승배율]
    TAKEN_ATTACK_AMPLIFY_DAMAGE_DOWN, // 피격 일반공격 데미지 다운 [감소배율]
    TAKEN_ABILITY_AMPLIFY_DAMAGE_UP, // 피격 어빌리티 데미지 업 [상승배율]
    TAKEN_ABILITY_AMPLIFY_DAMAGE_DOWN, // 피격 어빌리티 데미지 다운 [감소배율]
    TAKEN_CHARGE_ATTACK_AMPLIFY_DAMAGE_UP, // 피격 오의(특수기) 데미지 업 [상승배율]
    TAKEN_CHARGE_ATTACK_AMPLIFY_DAMAGE_DOWN, // 피격 오의(특수기) 데미지 다운 [감소배율]

    TAKEN_DAMAGE_BLOCK, // 피격 데미지 블록 [경감률] 확률은 50%로 고정, 경감률은 일단 50% 상정 고정

    TAKEN_DAMAGE_FIX, // 피격 최대 데미지 고정 [고정수치]

    BARRIER, // [베리어 수치]

    // 더블어택 / 트리플 어택
    // 반드시~ -> 999 (9999%)
    DOUBLE_ATTACK_RATE_UP, // [상승배율]
    DOUBLE_ATTACK_RATE_DOWN, // [감소배율]
    TRIPLE_ATTACK_RATE_UP, // [상승배율]
    TRIPLE_ATTACK_RATE_DOWN, // [감소배율]

    // 공격 행동관련
    MULTI_STRIKE, // 다회공격 [공격횟수]
    STRIKE_SEALED, // 공격행동 불가 [1], < 장악, 수면 >
    SUBJUGATED, // 장악 [1]

    // 크리티컬
    CRITICAL_RATE_UP, // [상승배율]
    CRITICAL_DAMAGE_UP, // [상승배율]

    // 추격 [추격배율]
    ADDITIONAL_DAMAGE_A, // 추격 Ability 항
    ADDITIONAL_DAMAGE_C, // 추격 ChargeAttack 항
    ADDITIONAL_DAMAGE_S, // 추격 SupportAbility 항
    ADDITIONAL_DAMAGE_U, // 추격 Unique(별)항
    ADDITIONAL_DAMAGE_W, // 추격 Weapon 항

    // 난격 [횟수 2 ~ 6] random_attack, flurry, attack_multi_hit 중 고민중
    ATTACK_MULTI_HIT,

    // 명중률 [가산배율]
    HIT_ACCURACY_UP,
    HIT_ACCURACY_DOWN,
    // 회피율 [가산배율]
    DODGE_RATE_UP,
    DODGE_RATE_DOWN,

    // 디버프성공률 [가산배율]
    DEBUFF_SUCCESS_UP,
    DEBUFF_SUCCESS_DOWN,
    // 디버프저항 [가산배율]
    DEBUFF_RESIST_DOWN,
    DEBUFF_RESIST_UP,
    MOUNT, // 마운트 [1]

    // 오의게이지
    CHARGE_GAUGE_INCREASE_UP, // [증가배율]
    CHARGE_GAUGE_INCREASE_DOWN, // [감소배율]
    ATTACK_CHARGE_GAUGE_INCREASE_DOWN, // 일반공격 오의게이지 감소율 [감소배율]
    CHARGE_TURN_INCREASE_UP, // [증가수치]
    CHARGE_TURN_INCREASE_DOWN, // [감소수치]
    CHARGE_TURN_FIX, // 공포, 장악 효과 [0]
    PETRIFIED, // 공포 [1] -> 오의게이지 감소배율 999 로 변경예정
    CHARGE_ATTACK_SEALED, // 오의 봉인 [1]
    MULTI_CHARGE_ATTACK, // 오의 다회발동 [2] 현재 구현상 2회발동 밖에 없음

    // 어빌리티
    ABILITY_SEALED, // 어빌리티 봉인[0: 전체, 1: 공격, 2:강화, 3:약체, 4:회복 ]
    // 쿨다운은 로직에서 처리

    // 방어 특수
    IMMORTAL, // 불사신 효과 [버틸 횟수 1 고정]
    SUBSTITUTE, // 감싸기 [우선순위 1 or 2] -> value 가 클수록 우선순위 높음
    GUARD_DISABLED, // 방어불가 [1]

    // 체력
    MAX_HP_DOWN, // 최대 체력 감소 [감소배율]

    // 힐
    HEAL_UP, // 회복 성능 업 [상승배율]
    HEAL_DOWN, // 회복 성능 다운 [감소배율]
    UNDEAD, // 언데드 [1]
    // 강압: HEAL_DOWN 200%


    // 후처리를 동반하는 Modifier ===================================================================

    ACT_DISPEL, // 디스펠 [디스펠 횟수 1 ~ , 적은 99 (모두디스펠)]
    ACT_DISPEL_GUARD, // 디스펠 가드 [1]
    ACT_CLEAR, // 클리어 [클리어 횟수 1 ~ 99]
    ACT_CHARGE_GAUGE_UP, // 오의게이지 업 [상승할 오의게이지 수치]
    ACT_CHARGE_GAUGE_DOWN, // 오의게이지 감소 [감소할 오의게이지 수치]
    ACT_WEAPON_BURST, // 즉시 오의 사용 가능 [1]
    ACT_FATAL_CHAIN_GAUGE_UP, // 페이탈 체인 게이지 상승 [상승할 페이탈 체인 게이지 수치]
    ACT_FATAL_CHAIN_GAUGE_DOWN, // 페이탈 체인 게이지 감소 [감소할 페이탈 체인 게이지 수치]
    ACT_HEAL, // 힐 사용 [힐 수치]
    ACT_DAMAGE, // 데미지 [수치 고정값]
    ACT_RATE_DAMAGE, // 데미지 [수치 rate]

    // 후처리를 동반하는 Modifier ===================================================================


    // 트리거
    TRIGGER, // 다른 스테이터스의 트리거가 될경우 사용 [TRIGGERED 와 동일한 값]
    TRIGGERED, // 다른 스테이터스에 트리거링 될 경우 사용 [TRIGGER 와 동일한 값]

    // 고유버프용
    UNIQUE, // 고유버프를 만들기 위한 modifier [0] 표시 외의 효과 없음

    // 없음
    NONE, // 표시 이외의 효과 없음 [0]

    // 테스트
    TEST,


    ;


    // 메서드 많아지면 확장

    /**
     * 후처리가 필요한 효과들.  ProcessStatusLogic 에서 후처리
     * @return
     */
    public boolean needPostProcess() {
        return this == ACT_DISPEL ||
                this == ACT_CLEAR ||
                this == ACT_CHARGE_GAUGE_UP ||
                this == ACT_CHARGE_GAUGE_DOWN ||
                this == ACT_WEAPON_BURST ||
                this == ACT_FATAL_CHAIN_GAUGE_UP ||
                this == ACT_FATAL_CHAIN_GAUGE_DOWN ||
                this == ACT_HEAL ||
                this == ACT_DAMAGE ||
                this == ACT_RATE_DAMAGE
                ;
    }


    /**
     * 효과를 합산하지 않고 덮어씌우는 이펙트의 경우 true 로 반환
     * 추격, 베리어, 감싸기
     * value 가 큰쪽이 우선되며, value 다음으로 duration 이 긴쪽이 우선된다.
     *
     * @return 효과를 덮어씌우는 경우 true
     */
    public boolean isCoveringEffect() {
        return this == ADDITIONAL_DAMAGE_A ||
                this == ADDITIONAL_DAMAGE_C ||
                this == ADDITIONAL_DAMAGE_S ||
                this == ADDITIONAL_DAMAGE_U ||
                this == ADDITIONAL_DAMAGE_W ||
                this == SUBSTITUTE
                ;
    }

    /**
     * 추격인경우 true 반환
     *
     * @return
     */
    public boolean isAdditionalDamage() {
        return this == ADDITIONAL_DAMAGE_A ||
                this == ADDITIONAL_DAMAGE_C ||
                this == ADDITIONAL_DAMAGE_S ||
                this == ADDITIONAL_DAMAGE_U ||
                this == ADDITIONAL_DAMAGE_W;
    }

}
