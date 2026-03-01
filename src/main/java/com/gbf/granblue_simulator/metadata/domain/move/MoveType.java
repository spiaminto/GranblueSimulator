package com.gbf.granblue_simulator.metadata.domain.move;

import lombok.Getter;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

// 논리적 행동을 통해 정의함. Asset 은 필요에 의해 같을 수 있음
@Getter
public enum MoveType {
    ROOT(null, "root"),

    STANDBY(ROOT, "standby"),
        STANDBY_A(STANDBY, "standby-a"),
        STANDBY_B(STANDBY, "standby-b"),
        STANDBY_C(STANDBY, "standby-c"),
        STANDBY_D(STANDBY, "standby-d"),
        STANDBY_E(STANDBY, "standby-e"),
        STANDBY_F(STANDBY, "standby-f"),
        STANDBY_G(STANDBY, "standby-g"),

    ATTACK(ROOT, "attack"),
        NORMAL_ATTACK(ATTACK, "normal-attack"),

    ABILITY(ROOT, "ability"),
        FIRST_ABILITY(ABILITY, "first-ability", 1),
        SECOND_ABILITY(ABILITY, "second-ability", 2),
        THIRD_ABILITY(ABILITY, "third-ability", 3),
        FOURTH_ABILITY(ABILITY, "fourth-ability", 4),

    SUPPORT_ABILITY(ROOT, "support-ability"),
        TRIGGERED_ABILITY(SUPPORT_ABILITY, "triggered-ability"),
        FIRST_SUPPORT_ABILITY(SUPPORT_ABILITY, "first-support-ability", 1),
        SECOND_SUPPORT_ABILITY(SUPPORT_ABILITY, "second-support-ability", 2),
        THIRD_SUPPORT_ABILITY(SUPPORT_ABILITY, "third-support-ability", 3),
        FOURTH_SUPPORT_ABILITY(SUPPORT_ABILITY, "fourth-support-ability", 4),
        FIFTH_SUPPORT_ABILITY(SUPPORT_ABILITY, "fifth-support-ability", 5),
        SIXTH_SUPPORT_ABILITY(SUPPORT_ABILITY, "sixth-support-ability", 6),
        SEVENTH_SUPPORT_ABILITY(SUPPORT_ABILITY, "seventh-support-ability", 7),
        EIGHTH_SUPPORT_ABILITY(SUPPORT_ABILITY, "eighth-support-ability", 8),
        NINTH_SUPPORT_ABILITY(SUPPORT_ABILITY, "ninth-support-ability", 9),
        TENTH_SUPPORT_ABILITY(SUPPORT_ABILITY, "tenth-support-ability", 10),

    CHARGE_ATTACK(ROOT, "charge-attack"),
        CHARGE_ATTACK_DEFAULT(CHARGE_ATTACK, "charge-attack-default", 1),
        CHARGE_ATTACK_A(CHARGE_ATTACK, "charge-attack-a", 1),
        CHARGE_ATTACK_B(CHARGE_ATTACK, "charge-attack-b", 2),
        CHARGE_ATTACK_C(CHARGE_ATTACK, "charge-attack-c", 3),
        CHARGE_ATTACK_D(CHARGE_ATTACK, "charge-attack-d", 4),
        CHARGE_ATTACK_E(CHARGE_ATTACK, "charge-attack-e", 5),
        CHARGE_ATTACK_F(CHARGE_ATTACK, "charge-attack-f", 6),
        CHARGE_ATTACK_G(CHARGE_ATTACK, "charge-attack-g", 7),

    DEAD(ROOT, "dead"),
        DEAD_DEFAULT(DEAD, "dead"),

    SUMMON(ROOT, "summon"), // 소환
        SUMMON_DEFAULT(SUMMON, "summon"),
        FIRST_SUMMON(SUMMON, "first-summon", 1),
        SECOND_SUMMON(SUMMON, "second-summon", 2),
        THIRD_SUMMON(SUMMON, "third-summon", 3),
        FOURTH_SUMMON(SUMMON, "fourth-summon",4),
        FIFTH_SUMMON(SUMMON, "fifth-summon", 5),
        UNION_SUMMON(SUMMON, "union-summon"),

    GUARD(ROOT, "guard"), // 가드
        GUARD_DEFAULT(GUARD, "guard"),

    FATAL_CHAIN(ROOT, "fatal-chain"), // 페이탈 체인
        FATAL_CHAIN_DEFAULT(FATAL_CHAIN, "fatal-chain"),

    TURN_END(ROOT, "turn-end"), // 턴종처리용 (DB 저장 X)
        TURN_END_PROCESS(TURN_END, "turn-end-process"),
        TURN_END_HEAL(TURN_END, "turn-end-heal"),
        TURN_END_DAMAGE(TURN_END, "turn-end-damage"),
        TURN_END_CHARGE_GAUGE(TURN_END, "turn-end-charge-gauge"),
        TURN_FINISH(TURN_END, "turn-finish"),

    ETC(ROOT, "etc"),
        STRIKE_SEALED(ETC, "strike-sealed"), // 공격불가일때 사용 (DB 저장 X)
        SYNC(ETC, "sync"), // 동기화용

    NONE(ROOT, "none"),
    ;

    public static final Set<MoveType> ABILITIES = Collections.unmodifiableSet(EnumSet.of(
            FIRST_ABILITY, SECOND_ABILITY, THIRD_ABILITY, FOURTH_ABILITY
    ));
    public static final Set<MoveType> SUPPORT_ABILITIES = Collections.unmodifiableSet(EnumSet.of(
            TRIGGERED_ABILITY, // 999
            FIRST_SUPPORT_ABILITY, SECOND_SUPPORT_ABILITY, THIRD_SUPPORT_ABILITY, FOURTH_SUPPORT_ABILITY,
            FIFTH_SUPPORT_ABILITY, SIXTH_SUPPORT_ABILITY, SEVENTH_SUPPORT_ABILITY, EIGHTH_SUPPORT_ABILITY,
            NINTH_SUPPORT_ABILITY, TENTH_SUPPORT_ABILITY
    ));
    public static final Set<MoveType> CHARGE_ATTACKS = Collections.unmodifiableSet(EnumSet.of(
            CHARGE_ATTACK_DEFAULT,
            CHARGE_ATTACK_A, CHARGE_ATTACK_B, CHARGE_ATTACK_C, CHARGE_ATTACK_D,
            CHARGE_ATTACK_E, CHARGE_ATTACK_F, CHARGE_ATTACK_G
    ));
    public static final Set<MoveType> SUMMONS = Collections.unmodifiableSet(EnumSet.of(
            FIRST_SUMMON, SECOND_SUMMON, THIRD_SUMMON, FOURTH_SUMMON, FIFTH_SUMMON
    ));
    private final MoveType parentType;
    private final String className;
    private final int order;

    MoveType(MoveType parentType, String className) {
        this(parentType, className, 999);
    }

    MoveType(MoveType parentType, String className, int order) {
        this.parentType = parentType;
        this.className = className;
        this.order = order;
    }

    // STANDBY 타입으로 가져오기
    public MoveType getChargeAttackType() {
        return switch (this) {
            case STANDBY_A -> MoveType.CHARGE_ATTACK_A;
            case STANDBY_B -> MoveType.CHARGE_ATTACK_B;
            case STANDBY_C -> MoveType.CHARGE_ATTACK_C;
            case STANDBY_D -> MoveType.CHARGE_ATTACK_D;
            case STANDBY_E -> MoveType.CHARGE_ATTACK_E;
            case STANDBY_F -> MoveType.CHARGE_ATTACK_F;
            case STANDBY_G -> MoveType.CHARGE_ATTACK_G;
            default -> NONE;
        };
    }

    // NONE 확인
    public boolean isNone() {
        return this == NONE;
    }

    /**
     * 어빌리티 & 서포트 어빌리티 인지 판별
     * @return 어빌리티, 서포트 어빌리티면 true
     */
    public boolean isAbilities() {
        return this.parentType == ABILITY || this.parentType == SUPPORT_ABILITY;
    }

    /**
     * MoveType 이 ABILITY 에 속하는경우, 해당 타입의 어빌리티 순서를 반환 (1부터)
     * @return 어빌리티 순서 (1 시작)
     * @throws IllegalArgumentException 어빌리티가 아닌경우
     */
    public int getAbilityOrder() {
        if (this.parentType != ABILITY) throw new IllegalArgumentException("[MoveType.getAbilityOrder] MoveType is not ability type, type = " + this.name());
        return this.order;
    }
}


