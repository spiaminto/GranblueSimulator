package com.gbf.granblue_simulator.domain.move.prop.status;

public enum StatusEffectType {

    // 공격력
    ATK_UP, // 일반공격력
    ATK_DOWN, // 일반공격력 다운
    ATK_UP_UNIQUE, // 별항
    STRENGTH, // 현재 체력이 많을수록 공격력 증가
    JAMMED, // 배수

    // 더블어택 / 트리플 어택
    DOUBLE_ATTACK_RATE_UP,
    DOUBLE_ATTACK_RATE_DOWN,
    TRIPLE_ATTACK_RATE_UP,
    TRIPLE_ATTACK_RATE_DOWN,

    // 크리티컬
    CRITICAL_RATE_UP,

    // 추격
    ADDITIONAL_DAMAGE_A, // 추격 Ability 항
    ADDITIONAL_DAMAGE_C, // 추격 ChargeAttack 항
    ADDITIONAL_DAMAGE_S, // 추격 SupportAbility 항
    ADDITIONAL_DAMAGE_E, // 추격 Extra(별)항
    ADDITIONAL_DAMAGE_W, // 추격 Weapon 항

    // 요다메
    SUPPLEMENTAL_DAMAGE, // 요다메 상승(가산)
    AMPLIFY_DAMAGE, // 요다메 UP(증산)

    // 데미지 상한
    DAMAGE_CAP_UP, // 데미지 상한

    // 방어
    DEF_UP, // 방어력 증가
    DEF_DOWN,
    TAKEN_DAMAGE_CUT, // 데미지컷
    TAKEN_DAMAGE_FIXED_DOWN, // 피격데미지 감소
    TAKEN_DAMAGE_FIXED_UP, // 피격 데미지 증가
    TAKEN_DAMAGE_DOWN, // 피격 데미지 감소 %
    TAKEN_DAMAGE_UP, // 피격 데미지 증가 %
    TAKEN_DAMAGE_FIX, // 받는 데미지 고정

    BARRIER, // 배리어

    // 명중률
    HIT_ACCURACY_UP,
    HIT_ACCURACY_DOWN,

    // 회피율
    DODGE_RATE_UP,
    DODGE_RATE_DOWN,

    // 디버프성공률
    DEBUFF_SUCCESS_UP,
    DEBUFF_SUCCESS_DOWN,

    // 디버프저항
    DEBUFF_RESIST_DOWN,
    DEBUFF_RESIST_UP,

    // 오의게이지
    CHARGE_GAUGE_INCREASE_UP,
    CHARGE_GAUGE_INCREASE_DOWN,
    PETRIFIED, // 공포

    // 도트데미지 류
    BURNED, // 작열

    // 디스펠가드
    DISPEL_GUARD,


    // 방어 특수
    IMMORTAL, // 불사신 효과 (체력0 됬을때 1로 버팀)
    SUBSTITUTE, // 감싸기

    // 체력
    MAX_HP_DOWN, // 최대 체력 감소

    // 힐
    ACT_HEAL, // 힐 사용
    HEAL_UP, // 회복 성능 업
    HEAL_DOWN, // 회복 성능 다운

    // 없음
    NONE, // 버프가 표시이외에 효과를 가지지 않을떄 사용 (ex 보스의 고유기 등)

    // 즉시변경류
    ACT_DISPEL, // 디스펠
    ACT_CHARGE_GAUGE_UP, // value 만큼 오의게이지 상승

    // 후 행동류 (재행동류 제외)
    ACT_FIRST_ABILITY, // 1어빌 자동발동
    ACT_SECOND_ABILITY, // 2어빌 자동발동
    ACT_THIRD_ABILITY, // 3어빌 자동발동
    ACT_NORMAL_ATTACK, // 통상공격 자동발동
    ACT_CHARGE_ATTACK, // 오의 자동발동 (오의 연속 2회, 오의 재발동과 별개임)
    ACT_TURN_DAMAGE, // 턴 종료시 ~ 데미지 등 기타 데미지

    // 테스트
    TEST,

    // 여기선 List<BattleCharacter>, Enemy, StatusEffect 세개 다 받아야 할듯.

    // 고유버프 (UNIQUE_[캐릭터명]_[ABILITY or CHARGE or SUPPORT]_[해당순서]
    UNIQUE_PALADIN_SUPPORT_1 // 방패의 수호

    ;

    // 메서드 많아지면 확장

    /**
     * 효과를 합산하지 않고 덮어씌우는 이펙트의 경우 true 로 반환
     * 추격
     * @return 효과를 덮어씌우는 경우 true
     */
    public boolean isCoveringEffect() {
        return this == ADDITIONAL_DAMAGE_A ||
                this == ADDITIONAL_DAMAGE_C ||
                this == ADDITIONAL_DAMAGE_S ||
                this == ADDITIONAL_DAMAGE_E ||
                this == ADDITIONAL_DAMAGE_W ||
                this == BARRIER
                ;
    }

    /**
     * 추격인경우 true 반환
     * @return
     */
    public boolean isAdditionalDamage() {
        return this == ADDITIONAL_DAMAGE_A ||
                this == ADDITIONAL_DAMAGE_C ||
                this == ADDITIONAL_DAMAGE_S ||
                this == ADDITIONAL_DAMAGE_E ||
                this == ADDITIONAL_DAMAGE_W;
    }

}
