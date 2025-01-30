package com.gbf.granblue_simulator.domain.move;

// 기본적으로 모션 여부를 기준으로 정의함
public enum MoveType {
    // 아군
    SINGLE_ATTACK,
    DOUBLE_ATTACK,
    TRIPLE_ATTACK,

    CHARGE_ATTACK,
    CHARGE_ATTACK_CHANGED, // 조건으로 인해 변화한 오의

    FIRST_ABILITY,
    SECOND_ABILITY,
    THIRD_ABILITY,

    FIRST_ABILITY_CHANGED, // 조건으로 인해 변화한 어빌리티들
    SECOND_ABILITY_CHANGED,
    THIRD_ABILITY_CHANGED,
    
    // 적
    ENEMY_ATTACK,
    ENEMY_STANDBY, // 차지어택 대기상태 -> OMEN 을 가짐, 차지어택마다 무조건 하나씩 세트로 존재해야함.
    ENEMY_CHARGE_ATTACK,
    ENEMY_DAMAGED, // 적은 피격모션이 존재해야함


    // 기타사항 (되도록 사용하지 말고 임시구현후 타입으로 추가할것)
    ETC,
    ENEMY_ETC
}
