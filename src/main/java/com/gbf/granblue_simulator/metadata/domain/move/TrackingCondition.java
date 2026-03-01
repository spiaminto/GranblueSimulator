package com.gbf.granblue_simulator.metadata.domain.move;

public enum TrackingCondition {
    //일반 (턴 종료시 초기화)
    HIT_COUNT_BY_ENEMY, // 피격 횟수
    
    HIT_COUNT_BY_CHARACTER, // 캐릭터 전체 히트수
    TRIPLE_ATTACK_COUNT, // 트리플 어택 횟수
    
    TAKEN_HEAL_EFFECT_COUNT, // 회복효과 받은 횟수


    //누적제 (조건완료시 초기화, 로직에서 직접 초기화 수행)
    HIT_COUNT_BY_CHARACTER_ACC, // 캐릭터 전체 히트수(누적)

    PASSED_TURN_COUNT, // 턴 마다

    // ...



    // GENERIC apply, level down status effect
    STATUS_EFFECT_ID,
    STATUS_EFFECT_NAME,
    LEVEL_DOWN_CONDITION,
    LEVEL_DOWN_THRESHOLD,

    APPLY_CONDITION,
    APPLY_THRESHOLD,
    APPLY_STATUS_EFFECT_ID, // BaseStatusEffect.id

    NONE // null
    ;

    /**
     * 기본값이 추가된 valueOf, 없으면 NONE 을 반환
     */
    public static TrackingCondition valueOfOrDefault(String name) {
        try {
            return TrackingCondition.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException e) {
            return TrackingCondition.NONE;
        }
    }

    /**
     * 턴 종료시 초기화 되지 않는 조건인지 확인
     */
    public boolean isAccumulative() {
        return this == PASSED_TURN_COUNT || this == HIT_COUNT_BY_CHARACTER_ACC;
    }
}
