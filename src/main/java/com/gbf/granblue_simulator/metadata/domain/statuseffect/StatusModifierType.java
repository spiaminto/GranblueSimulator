package com.gbf.granblue_simulator.metadata.domain.statuseffect;

import lombok.Getter;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * StatusEffect.StatusEffectType. StatusEffect.value 가 가지는 값의 의미를 [] 안에 써넣을것
 * 값은 모두 읽기전용!!!!
 * 효과량은 반드시 양수로 할것.
 */
public enum StatusModifierType {

    // 공격력

    // 공인항
    ATK_UP,     // [상승배율]
    ATK_DOWN,   // [감소배율]

    // 속성항
    ATK_UP_FIRE,
    ATK_UP_WATER,
    ATK_UP_EARTH,
    ATK_UP_WIND,
    ATK_UP_LIGHT,
    ATK_UP_DARK,
    ATK_DOWN_FIRE,
    ATK_DOWN_WATER,
    ATK_DOWN_EARTH,
    ATK_DOWN_WIND,
    ATK_DOWN_LIGHT,
    ATK_DOWN_DARK,

    // 혼신항
    STRENGTH, // [최대상승배율] - 감소없음
    // 배수항
    JAMMED, // [최대상승배율] - 감소없음
    // 별항
    ATK_UP_UNIQUE("별항 상승 배율"),
    ATK_DOWN_FORFEIT("공격력 감소 하한을 무시하고 감소할 배율(상실효과), [0.0 ~ 0.10]"), // 주로 적에게
    
    // 데미지 배율 상승
    ABILITY_DAMAGE_RATE_UP("어빌리티 배율 상승"),
    CHARGE_ATTACK_DAMAGE_RATE_UP("오의 데미지 배율 상승"),

    // 공격 데미지 상승
    SUPPLEMENTAL_DAMAGE_UP, // 공격 데미지 상승 [상승(가산)수치]
    SUPPLEMENTAL_ATTACK_DAMAGE_UP, // 공격 데미지 상승 [상승(가산)수치]
    SUPPLEMENTAL_ABILITY_DAMAGE_UP, // 어빌리티 데미지 상승 [상승(가산)수치]
    SUPPLEMENTAL_CHARGE_ATTACK_DAMAGE_UP, // [상승(가산)수치]

    SUPPLEMENTAL_TRIPLE_ATTACK_DAMAGE_UP("트리플 어택시 데미지 상승 [1 ~ ]"),

    // 공격 데미지 업
    AMPLIFY_DAMAGE_UP, // 공격 데미지 N% 업 [상승(승산)배율]
    AMPLIFY_ATTACK_DAMAGE_UP, // 공격 데미지 N% 업 [상승(승산)배율]
    AMPLIFY_ABILITY_DAMAGE_UP, // 어빌리티 데미지 N% 업 [상승(승산)배율]
    AMPLIFY_CHARGE_ATTACK_DAMAGE_UP, // [상승(승산)배율]
    // 특수기 데미지 다운
    AMPLIFY_CHARGE_ATTACK_DAMAGE_DOWN("주로 적의 특수기 데미지 다운, [0.0 ~ 1.0]"), //
    // 데미지 상한
    DAMAGE_CAP_UP, // [상승배율] - 감소없음
    ATTACK_DAMAGE_CAP_UP, // [상승배율]
    ABILITY_DAMAGE_CAP_UP, // [상승배율]
    CHARGE_ATTACK_DAMAGE_CAP_UP, // [상승배율]
    
    // 데미지 고정
    DAMAGE_FIX("데미지 고정, 주로 0 [0 ~ ]"), // [고정치]

    // 방어력
    DEF_UP, // [상승배율]
    DEF_DOWN, // [감소배율]

    // 별항 [주로 적]
    DEF_DOWN_FORFEIT("방어력 감소 하한을 무시하고 감소할 배율(상실효과) [0.0 ~ 0.10]"),

    // 속성 방어력 [주로 적]
    DEF_DOWN_FIRE, // [감소배율]
    DEF_DOWN_WATER, // [감소배율]
    DEF_DOWN_EARTH, // [감소배율]
    DEF_DOWN_WIND, // [감소배율]
    DEF_DOWN_LIGHT, // [감소배율]
    DEF_DOWN_DARK, // [감소배율]

    // 데미지 컷
    TAKEN_DAMAGE_CUT, // [데미지컷 배율]
    TAKEN_DAMAGE_CUT_FIRE, // [데미지컷 배율]
    TAKEN_DAMAGE_CUT_WATER, // [데미지컷 배율]
    TAKEN_DAMAGE_CUT_EARTH, // [데미지컷 배율]
    TAKEN_DAMAGE_CUT_WIND, // [데미지컷 배율]
    TAKEN_DAMAGE_CUT_LIGHT, // [데미지컷 배율]
    TAKEN_DAMAGE_CUT_DARK, // [데미지컷 배율]

    // 피격 데미지 무효 [캐릭터만]
    TAKEN_DAMAGE_INEFFECTIVE, // [1: N회, 2: 턴제] -> 항 하나 공유, 턴제가 반드시 덮어쓰며 횟수제는 영속&& 리필제 효과로 지정

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

    // 속성 데미지 경감 [캐릭터만, 적은 컷 사용]
    TAKEN_DAMAGE_DOWN_FIRE, // 속성데미지 경감 [배율]
    TAKEN_DAMAGE_DOWN_WATER, // 속성데미지 경감 [배율]
    TAKEN_DAMAGE_DOWN_EARTH, // 속성데미지 경감 [배율]
    TAKEN_DAMAGE_DOWN_WIND, // 속성데미지 경감 [배율]
    TAKEN_DAMAGE_DOWN_LIGHT, // 속성데미지 경감 [배율]
    TAKEN_DAMAGE_DOWN_DARK, // 속성데미지 경감 [배율]

    // 피데미지 블록
    TAKEN_DAMAGE_BLOCK("피격 데미지 블록, 발동률/경감률 50% 고정, 값은 발동률 [0.5]"), // 일단 고정,

    // 베리어
    BARRIER("베리어, 기본수치 [1 ~ ]"),

    // 피데미지 고정
    TAKEN_DAMAGE_FIX("전속성 피격 데미지 고정, 고정수치 [1 ~ ]"),
    TAKEN_DAMAGE_FIX_FIRE("화속성 피격 데미지 고정, 고정수치 [1 ~ ]"),
    TAKEN_DAMAGE_FIX_WATER("수속성 피격 데미지 고정, 고정수치 [1 ~ ]"),
    TAKEN_DAMAGE_FIX_EARTH("토속성 피격 데미지 고정, 고정수치 [1 ~ ]"),
    TAKEN_DAMAGE_FIX_WIND("풍속성 피격 데미지 고정, 고정수치 [1 ~ ]"),
    TAKEN_DAMAGE_FIX_LIGHT("빛속성 피격 데미지 고정, 고정수치 [1 ~ ]"),
    TAKEN_DAMAGE_FIX_DARK("암속성 피격 데미지 고정, 고정수치 [1 ~ ]"),

    // 약점 속성 적용
    TAKEN_WEAK_FIRE, // 속성적용[1]
    TAKEN_WEAK_WATER, // 속성적용[1]
    TAKEN_WEAK_EARTH, // 속성적용[1]
    TAKEN_WEAK_WIND, // 속성적용[1]
    TAKEN_WEAK_LIGHT, // 속성적용[1]
    TAKEN_WEAK_DARK, // 속성적용[1]

    // 피격속성 변환
    TAKEN_DAMAGE_SWITCH_FIRE, // 속성변환 화속성 [1]
    TAKEN_DAMAGE_SWITCH_WATER, // 속성변환 수속성 [1]
    TAKEN_DAMAGE_SWITCH_EARTH, // 속성변환 토속성 [1]
    TAKEN_DAMAGE_SWITCH_WIND, // 속성변환 풍속성 [1]
    TAKEN_DAMAGE_SWITCH_LIGHT, // 속성변환 빛속성 [1]
    TAKEN_DAMAGE_SWITCH_DARK, // 속성변환 암속성 [1]

    // 더블어택 / 트리플 어택
    // 반드시~ -> 999 (9999%)
    DOUBLE_ATTACK_RATE_UP, // [상승배율]
    DOUBLE_ATTACK_RATE_DOWN, // [감소배율]
    TRIPLE_ATTACK_RATE_UP, // [상승배율]
    TRIPLE_ATTACK_RATE_DOWN, // [감소배율]

    // 공격 행동관련
    // MULTI_STRIKE, // 다회공격 [공격횟수]
    DOUBLE_STRIKE, // 재공격 [2]
    TRIPLE_STRIKE, // 3회공격 [3]
    QUADRUPLE_STRIKE, // 4회공격 [4]
    PLUS_STRIKE, // 공격횟수 증가 [1]

    STRIKE_SEALED, // 공격행동 불가 [행동방해율 0.0 ~ 1.0] , < 장악, 수면 >
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

    NORMAL_ATTACK_ACCURACY_DOWN("암흑효과, 일반공격 미스 [0.0 ~ 1.0]"),

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
    CHARGE_GAUGE_INCREASE_UP_ON_DAMAGED("피데미지시 오의 게이지 상승률 증가 [0.0 ~ ]"),
    CHARGE_TURN_INCREASE_UP, // [증가수치]
    CHARGE_TURN_INCREASE_DOWN, // [감소수치] -> 공포 시 999.0
    CHARGE_TURN_FIX, // 공포, 장악 효과 [0]
    CHARGE_ATTACK_SEALED, // 오의 봉인 [1]

    REACTIVATE_CHARGE_ATTACK_ONCE, // 오의 재발동 1회 [1]
    REACTIVATE_CHARGE_ATTACK, // 오의 재발동 [1]
    CONDITIONAL_CHARGE_ATTACK, // 조건 만족시 오의 발동 [1], 효과 레벨이 max 일시 발동가능 하도록 구현예정

    // 어빌리티 봉인 -> 우열계산시 value 를 사용하므로, value 로 구분하지 않고 name 으로 직접구별하도록 구현
    // [우선순위 1...] 일반적으로, 캐릭터 로직에서 자신에게 거는걸 2 / 적에게 받는걸 1로 설정
    ABILITY_SEALED_FIRST("우선순위, 자신에게 걸떄 2 / 적에게 받을때 1 [1, 2]"),
    ABILITY_SEALED_SECOND("우선순위, 자신에게 걸떄 2 / 적에게 받을때 1 [1, 2]"),
    ABILITY_SEALED_THIRD("우선순위, 자신에게 걸떄 2 / 적에게 받을때 1 [1, 2]"),
    ABILITY_SEALED_FOURTH("우선순위, 자신에게 걸떄 2 / 적에게 받을때 1 [1, 2]"),
    ABILITY_SEALED_ALL("우선순위, 자신에게 걸떄 2 / 적에게 받을때 1 [1, 2]"),

    // 어빌리티 재사용
    ABILITY_REACTIVATE, // [최대 횟수 2, ...]

    // 어빌리티 쿨다운은 로직에서 처리

    // 방어 특수
    IMMORTAL("불사신, 횟수는 레벨 리필로 조정 [1]"), // 불사신 효과 [버틸 횟수 1 고정]

    // 적대심, 감싸기
    SUBSTITUTE("감싸기(전체). 일반 감싸기는 적대심으로. [1]"),
    HOSTILITY_UP("적대심 업: 0 (1.0배, 25%) / 50 (1.5배, 33%) / 100 (2.0배, 40%) / 200 (3배, 50%) / 800 (9배, 75%) / 10000 (99.01%) [0 ~ 10000]"),
    HOSTILITY_DOWN("적대심 다운: 0 (1.0배, 25%)/ 25 (0.75배, 20%) / 50 (0.5배, 14.3%) / 75 (0.25배, 7.7%) / 100 (0%) [0 ~ 100]"),

    // 체력
    MAX_HP_DOWN("최대 체력 감소, 감소배율 [0.0 ~ 1]"),
    MAX_HP_UP("최대 체력 증가. 증가배율 [0.0 ~ ]"),

    // 힐
    HEAL_UP, // 회복 성능 업 [상승배율]
    HEAL_DOWN, // 회복 성능 다운 [감소배율] -> 강압시 999.0
    UNDEAD, // 언데드 [1]
    
    // 방어 커맨드
    GUARD_DISABLED, // 방어불가 [1] -> 사용 안할듯


    // 후처리를 동반하는 Modifier ===================================================================

    ACT_DISPEL, // 디스펠 [디스펠 횟수 1 ~ , 적은 99 (모두디스펠)]
    ACT_DISPEL_GUARD_ONCE, // 디스펠 가드, 가드시 소거 [1]
    ACT_DISPEL_GUARD, // 디스펠 가드, 가드시 소거 하지 않고, 상태효과 해제될때 해제, ONCE 보다 우선 처리! [1]
    ACT_CLEAR, // 클리어 [클리어 횟수 1 ~ 99]
    ACT_CHARGE_GAUGE_UP, // 오의게이지 업 [상승할 오의게이지 수치]
    ACT_CHARGE_GAUGE_DOWN, // 오의게이지 감소 [감소할 오의게이지 수치]
    ACT_CHARGE_TURN_UP("적의 차지턴 증가, [1 ~]"), // 차지턴 증가
    ACT_CHARGE_TURN_DOWN("적의 차지턴 감소, [1 ~]"), // 차지턴 감소
    ACT_WEAPON_BURST, // 즉시 오의 사용 가능 [1]
    ACT_FATAL_CHAIN_GAUGE_UP, // 페이탈 체인 게이지 상승 [상승할 페이탈 체인 게이지 수치]
    ACT_FATAL_CHAIN_GAUGE_DOWN, // 페이탈 체인 게이지 감소 [감소할 페이탈 체인 게이지 수치]
    ACT_HEAL, // 힐 사용 [힐 수치]
    ACT_RATE_HEAL, // 힐 [수치 rate]
    ACT_DAMAGE, // 데미지 [수치 고정값]
    ACT_RATE_DAMAGE, // 데미지 [수치 rate]
    ACT_SHORTEN_ABILITY_COOLDOWN("어빌리티 쿨다운 단축, 단축턴 [1 ~]"),
    ACT_EXTEND_ABILITY_COOLDOWN("어빌리티 쿨다운 연장, 연장턴 [1 ~]"),
    ACT_SHORTEN_SUMMON_COOLDOWN("소환석 쿨다운 단축, 단축턴 [1 ~]"),
    ACT_EXTEND_SUMMON_COOLDOWN("소환석 쿨다운 연장, 연장턴 [1 ~]"),
    ACT_SHORTEN_DEBUFF_DURATION("약화효과단축 단축턴 [1 ~]"),


    // 후처리를 동반하는 Modifier ===================================================================

    // 고유버프용
    UNIQUE, // 고유버프를 만들기 위한 modifier [0] 표시 외의 효과 없음

    // 없음, null
    NONE,

    // 테스트
    TEST,


    ;

    @Getter
    private final String valueInfo;

    StatusModifierType() {
        this.valueInfo = "";
    }

    StatusModifierType(String valueInfo) {
        this.valueInfo = valueInfo;
    }

    private static final Set<StatusModifierType> IMMEDIATE_MODIFIERS = Collections.unmodifiableSet(EnumSet.of(
            ACT_DISPEL, ACT_CLEAR,
            ACT_CHARGE_GAUGE_UP, ACT_CHARGE_GAUGE_DOWN, ACT_WEAPON_BURST,
            ACT_CHARGE_TURN_UP, ACT_CHARGE_TURN_DOWN,
            ACT_FATAL_CHAIN_GAUGE_UP, ACT_FATAL_CHAIN_GAUGE_DOWN,
            ACT_HEAL, ACT_RATE_HEAL, ACT_DAMAGE, ACT_RATE_DAMAGE,
            ACT_SHORTEN_ABILITY_COOLDOWN, ACT_SHORTEN_SUMMON_COOLDOWN, ACT_EXTEND_ABILITY_COOLDOWN, ACT_EXTEND_SUMMON_COOLDOWN,
            ACT_SHORTEN_DEBUFF_DURATION
    ));


    // 메서드 많아지면 확장

    /**
     * 후처리가 필요한 효과들.  ProcessStatusLogic 에서 후처리
     *
     * @return
     */
    public boolean needPostProcess() {
        return IMMEDIATE_MODIFIERS.contains(this);
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

    public boolean isNone() {
        return this == NONE;
    }

}
