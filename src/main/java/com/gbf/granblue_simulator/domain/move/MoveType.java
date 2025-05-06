package com.gbf.granblue_simulator.domain.move;

import lombok.Getter;

// 논리적 행동을 통해 정의함. Asset 은 필요에 의해 같을 수 있음
@Getter
public enum MoveType {
    ROOT(null, "root"),

    IDLE(ROOT, "idle"),
        IDLE_DEFAULT(IDLE, "idle-default"),
        IDLE_A(IDLE, "idle-a"),
        IDLE_B(IDLE, "idle-b"),
        IDLE_C(IDLE, "idle-c"),
        IDLE_D(IDLE, "idle-d"),
        IDLE_E(IDLE, "idle-e"),
        IDLE_F(IDLE, "idle-f"),
        IDLE_G(IDLE, "idle-g"),

    DAMAGED(ROOT, "damaged"),
        DAMAGED_DEFAULT(DAMAGED, "damaged-default"),
        DAMAGED_A(DAMAGED, "damaged-a"),
        DAMAGED_B(DAMAGED, "damaged-b"),
        DAMAGED_C(DAMAGED, "damaged-c"),
        DAMAGED_D(DAMAGED, "damaged-d"),
        DAMAGED_E(DAMAGED, "damaged-e"),
        DAMAGED_F(DAMAGED, "damaged-f"),
        DAMAGED_G(DAMAGED, "damaged-g"),

    STANDBY(ROOT, "standby"),
        STANDBY_A(STANDBY, "standby-a"),
        STANDBY_B(STANDBY, "standby-b"),
        STANDBY_C(STANDBY, "standby-c"),
        STANDBY_D(STANDBY, "standby-d"),
        STANDBY_E(STANDBY, "standby-e"),
        STANDBY_F(STANDBY, "standby-f"),
        STANDBY_G(STANDBY, "standby-g"),

    BREAK(ROOT, "break"),
        BREAK_A(BREAK, "break-a"),
        BREAK_B(BREAK, "break-b"),
        BREAK_C(BREAK, "break-c"),
        BREAK_D(BREAK, "break-d"),
        BREAK_E(BREAK, "break-e"),
        BREAK_F(BREAK, "break-f"),
        BREAK_G(BREAK, "break-g"),

    ATTACK(ROOT, "attack"),
        SINGLE_ATTACK(ATTACK, "single-attack"),
        DOUBLE_ATTACK(ATTACK, "double-attack"),
        TRIPLE_ATTACK(ATTACK, "triple-attack"),

    ABILITY(ROOT, "ability"),
        FIRST_ABILITY(ABILITY, "first-ability"),
        SECOND_ABILITY(ABILITY, "second-ability"),
        THIRD_ABILITY(ABILITY, "third-ability"),

    SUPPORT_ABILITY(ROOT, "support-ability"),
        FIRST_SUPPORT_ABILITY(SUPPORT_ABILITY, "first-support-ability"),
        SECOND_SUPPORT_ABILITY(SUPPORT_ABILITY, "second-support-ability"),
        THIRD_SUPPORT_ABILITY(SUPPORT_ABILITY, "third-support-ability"),
        FOURTH_SUPPORT_ABILITY(SUPPORT_ABILITY, "fourth-support-ability"),
        FIFTH_SUPPORT_ABILITY(SUPPORT_ABILITY, "fifth-support-ability"),

    CHARGE_ATTACK(ROOT, "charge-attack"),
        CHARGE_ATTACK_DEFAULT(CHARGE_ATTACK, "charge-attack-default"),
        CHARGE_ATTACK_A(CHARGE_ATTACK, "charge-attack-a"),
        CHARGE_ATTACK_B(CHARGE_ATTACK, "charge-attack-b"),
        CHARGE_ATTACK_C(CHARGE_ATTACK, "charge-attack-c"),
        CHARGE_ATTACK_D(CHARGE_ATTACK, "charge-attack-d"),
        CHARGE_ATTACK_E(CHARGE_ATTACK, "charge-attack-e"),
        CHARGE_ATTACK_F(CHARGE_ATTACK, "charge-attack-f"),
        CHARGE_ATTACK_G(CHARGE_ATTACK, "charge-attack-g"),

    PHASE_CHANGE(ROOT, "phase-change"),

    DEAD(ROOT, "dead"),

    SUMMON(ROOT, "summon"), // 소환

    ETC(ROOT, "etc"),
    
    // NULL 대응
    NONE(ROOT, "none"), // null 대응
    ;

    private final MoveType parentType;
    private final String className;

    MoveType(MoveType parentType, String className) {
        this.parentType = parentType;
        this.className = className;
    }


    // STANDBY 타입으로 가져오기
    public MoveType getDamagedType() {
        return getMoveType(DAMAGED_A, DAMAGED_B, DAMAGED_C, DAMAGED_D, DAMAGED_E, DAMAGED_F, DAMAGED_G);
    }
    public MoveType getChargeAttackType() {
        return getMoveType(CHARGE_ATTACK_A, CHARGE_ATTACK_B, CHARGE_ATTACK_C, CHARGE_ATTACK_D, CHARGE_ATTACK_E, CHARGE_ATTACK_F, CHARGE_ATTACK_G);
    }
    public MoveType getBreakType() {
        return getMoveType(BREAK_A, BREAK_B, BREAK_C, BREAK_D, BREAK_E, BREAK_F, BREAK_G);
    }
    public MoveType getIdleType() {
        return getMoveType(IDLE_A, IDLE_B, IDLE_C, IDLE_D, IDLE_E, IDLE_F, IDLE_G);
    }
    private MoveType getMoveType(MoveType moveType, MoveType moveType2, MoveType moveType3, MoveType moveType4, MoveType moveType5, MoveType moveType6, MoveType moveType7) {
        return switch (this) {
            case STANDBY_A -> moveType;
            case STANDBY_B -> moveType2;
            case STANDBY_C -> moveType3;
            case STANDBY_D -> moveType4;
            case STANDBY_E -> moveType5;
            case STANDBY_F -> moveType6;
            case STANDBY_G -> moveType7;
            default -> NONE;
        };
    }

    public MoveType getStandbyByIdle() {
        return switch (this) {
            case IDLE_A -> STANDBY_A;
            case IDLE_B -> STANDBY_B;
            case IDLE_C -> STANDBY_C;
            case IDLE_D -> STANDBY_D;
            case IDLE_E -> STANDBY_E;
            case IDLE_F -> STANDBY_F;
            case IDLE_G -> STANDBY_G;
            default -> NONE;
        };
    }

    // NONE 확인
    public boolean isNone() {
        return this == NONE;
    }

    public int getOrder() {
        return switch (this) {
            case FIRST_ABILITY, FIRST_SUPPORT_ABILITY -> 1;
            case SECOND_ABILITY, SECOND_SUPPORT_ABILITY -> 2;
            case THIRD_ABILITY, THIRD_SUPPORT_ABILITY -> 3;
            case FOURTH_SUPPORT_ABILITY -> 4;
            case FIFTH_SUPPORT_ABILITY -> 5;
            default -> -1;
        };
    }
}


