package com.gbf.granblue_simulator.domain.move;

// 기본적으로 모션 여부를 기준으로 정의함
public enum MoveType {
    // 아군
    IDLE,

    NORMAL_ATTACK, // 조건연산 전용 타입
    SINGLE_ATTACK,
    DOUBLE_ATTACK,
    TRIPLE_ATTACK,

    CHARGE_ATTACK,

    ABILITY, // 조건연산 전용타입
    FIRST_ABILITY,
    SECOND_ABILITY,
    THIRD_ABILITY,

    FIRST_SUPPORT_ABILITY,
    SECOND_SUPPORT_ABILITY,
    THIRD_SUPPORT_ABILITY,
    FOURTH_SUPPORT_ABILITY,
    FIFTH_SUPPORT_ABILITY,

    DEAD,

    // 적
    ENEMY_IDLE,
    ENEMY_IDLE_A,
    ENEMY_IDLE_B,
    ENEMY_IDLE_C,
    ENEMY_IDLE_D,

    ENEMY_DAMAGED,
    ENEMY_DAMAGED_A,
    ENEMY_DAMAGED_B,
    ENEMY_DAMAGED_C,
    ENEMY_DAMAGED_D,
    ENEMY_DAMAGED_E,
    ENEMY_DAMAGED_F,
    ENEMY_DAMAGED_G,


    ENEMY_BREAK_A,
    ENEMY_BREAK_B,
    ENEMY_BREAK_C,
    ENEMY_BREAK_D,
    ENEMY_BREAK_E,
    ENEMY_BREAK_F,
    ENEMY_BREAK_G,

    ENEMY_STANDBY_A,
    ENEMY_STANDBY_B,
    ENEMY_STANDBY_C,
    ENEMY_STANDBY_D,
    ENEMY_STANDBY_E,
    ENEMY_STANDBY_F,
    ENEMY_STANDBY_G,

    ENEMY_CHARGE_ATTACK_A,
    ENEMY_CHARGE_ATTACK_B,
    ENEMY_CHARGE_ATTACK_C,
    ENEMY_CHARGE_ATTACK_D,
    ENEMY_CHARGE_ATTACK_E,
    ENEMY_CHARGE_ATTACK_F,
    ENEMY_CHARGE_ATTACK_G,

    ENEMY_ATTACK,
    ENEMY_DEAD,
    ENEMY_PHASE_CHANGE,

    // 기타사항 (되도록 사용하지 말고 임시구현후 타입으로 추가할것)
    ETC,
    ENEMY_ETC;

    public boolean isNormalAttack() {
        return this == NORMAL_ATTACK ||
                this == SINGLE_ATTACK ||
                this == DOUBLE_ATTACK ||
                this == TRIPLE_ATTACK;
    }

    public boolean isAbility() {
        return this == FIRST_ABILITY ||
                this == SECOND_ABILITY ||
                this == THIRD_ABILITY;
    }

    public boolean isSupportAbility() {
        return this == FIRST_SUPPORT_ABILITY ||
                this == SECOND_SUPPORT_ABILITY ||
                this == THIRD_SUPPORT_ABILITY ||
                this == FOURTH_SUPPORT_ABILITY ||
                this == FIFTH_SUPPORT_ABILITY;
    }

    public boolean isChargeAttack() {
        return this == CHARGE_ATTACK ||
                this == ENEMY_CHARGE_ATTACK_A ||
                this == ENEMY_CHARGE_ATTACK_B ||
                this == ENEMY_CHARGE_ATTACK_C ||
                this == ENEMY_CHARGE_ATTACK_D ||
                this == ENEMY_CHARGE_ATTACK_E ||
                this == ENEMY_CHARGE_ATTACK_F ||
                this == ENEMY_CHARGE_ATTACK_G;
    }

    public MoveType getUpperType() {
        if (isAbility() || isSupportAbility()) {
            return ABILITY;
        } else if (isNormalAttack()) {
            return NORMAL_ATTACK;
        } else if (isChargeAttack()) {
            return CHARGE_ATTACK;
        } else
            return null;
    }

    public MoveType getChargeAttackType() {
        if (this == ENEMY_STANDBY_A) {
            return MoveType.ENEMY_CHARGE_ATTACK_A;
        } else if (this == ENEMY_STANDBY_B) {
            return MoveType.ENEMY_CHARGE_ATTACK_B;
        } else if (this == ENEMY_STANDBY_C) {
            return MoveType.ENEMY_CHARGE_ATTACK_C;
        } else if (this == ENEMY_STANDBY_D) {
            return MoveType.ENEMY_CHARGE_ATTACK_D;
        } else if (this == ENEMY_STANDBY_E) {
            return MoveType.ENEMY_CHARGE_ATTACK_E;
        } else if (this == ENEMY_STANDBY_F) {
            return MoveType.ENEMY_CHARGE_ATTACK_F;
        } else if (this == ENEMY_STANDBY_G) {
            return MoveType.ENEMY_CHARGE_ATTACK_G;
        } else {
            return null;
        }
    }

    public MoveType getBreakType() {
        if (this == ENEMY_STANDBY_A) {
            return MoveType.ENEMY_BREAK_A;
        } else if (this == ENEMY_STANDBY_B) {
            return MoveType.ENEMY_BREAK_B;
        } else if (this == ENEMY_STANDBY_C) {
            return MoveType.ENEMY_BREAK_C;
        } else if (this == ENEMY_STANDBY_D) {
            return MoveType.ENEMY_BREAK_D;
        } else if (this == ENEMY_STANDBY_E) {
            return MoveType.ENEMY_BREAK_E;
        } else if (this == ENEMY_STANDBY_F) {
            return MoveType.ENEMY_BREAK_F;
        } else if (this == ENEMY_STANDBY_G) {
            return MoveType.ENEMY_BREAK_G;
        } else {
            return null;
        }
    }
}


