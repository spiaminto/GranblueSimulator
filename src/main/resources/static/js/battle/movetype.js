const MoveType = {
    ROOT: { name: 'ROOT', parentType: null, className: 'root' },
    IDLE: { name: 'IDLE', parentType: 'ROOT', className: 'idle' },
    IDLE_DEFAULT: { name: 'IDLE_DEFAULT', parentType: 'IDLE', className: 'idle-default' },
    IDLE_A: { name: 'IDLE_A', parentType: 'IDLE', className: 'idle-a' },
    IDLE_B: { name: 'IDLE_B', parentType: 'IDLE', className: 'idle-b' },
    IDLE_C: { name: 'IDLE_C', parentType: 'IDLE', className: 'idle-c' },
    IDLE_D: { name: 'IDLE_D', parentType: 'IDLE', className: 'idle-d' },
    IDLE_E: { name: 'IDLE_E', parentType: 'IDLE', className: 'idle-e' },
    IDLE_F: { name: 'IDLE_F', parentType: 'IDLE', className: 'idle-f' },
    IDLE_G: { name: 'IDLE_G', parentType: 'IDLE', className: 'idle-g' },
    DAMAGED: { name: 'DAMAGED', parentType: 'ROOT', className: 'damaged' },
    DAMAGED_DEFAULT: { name: 'DAMAGED_DEFAULT', parentType: 'DAMAGED', className: 'damaged-default' },
    DAMAGED_A: { name: 'DAMAGED_A', parentType: 'DAMAGED', className: 'damaged-a' },
    DAMAGED_B: { name: 'DAMAGED_B', parentType: 'DAMAGED', className: 'damaged-b' },
    DAMAGED_C: { name: 'DAMAGED_C', parentType: 'DAMAGED', className: 'damaged-c' },
    DAMAGED_D: { name: 'DAMAGED_D', parentType: 'DAMAGED', className: 'damaged-d' },
    DAMAGED_E: { name: 'DAMAGED_E', parentType: 'DAMAGED', className: 'damaged-e' },
    DAMAGED_F: { name: 'DAMAGED_F', parentType: 'DAMAGED', className: 'damaged-f' },
    DAMAGED_G: { name: 'DAMAGED_G', parentType: 'DAMAGED', className: 'damaged-g' },
    STANDBY: { name: 'STANDBY', parentType: 'ROOT', className: 'standby' },
    STANDBY_A: { name: 'STANDBY_A', parentType: 'STANDBY', className: 'standby-a' },
    STANDBY_B: { name: 'STANDBY_B', parentType: 'STANDBY', className: 'standby-b' },
    STANDBY_C: { name: 'STANDBY_C', parentType: 'STANDBY', className: 'standby-c' },
    STANDBY_D: { name: 'STANDBY_D', parentType: 'STANDBY', className: 'standby-d' },
    STANDBY_E: { name: 'STANDBY_E', parentType: 'STANDBY', className: 'standby-e' },
    STANDBY_F: { name: 'STANDBY_F', parentType: 'STANDBY', className: 'standby-f' },
    STANDBY_G: { name: 'STANDBY_G', parentType: 'STANDBY', className: 'standby-g' },
    BREAK: { name: 'BREAK', parentType: 'ROOT', className: 'break' },
    BREAK_A: { name: 'BREAK_A', parentType: 'BREAK', className: 'break-a' },
    BREAK_B: { name: 'BREAK_B', parentType: 'BREAK', className: 'break-b' },
    BREAK_C: { name: 'BREAK_C', parentType: 'BREAK', className: 'break-c' },
    BREAK_D: { name: 'BREAK_D', parentType: 'BREAK', className: 'break-d' },
    BREAK_E: { name: 'BREAK_E', parentType: 'BREAK', className: 'break-e' },
    BREAK_F: { name: 'BREAK_F', parentType: 'BREAK', className: 'break-f' },
    BREAK_G: { name: 'BREAK_G', parentType: 'BREAK', className: 'break-g' },
    ATTACK: { name: 'ATTACK', parentType: 'ROOT', className: 'attack' },
    SINGLE_ATTACK: { name: 'SINGLE_ATTACK', parentType: 'ATTACK', className: 'single-attack' },
    DOUBLE_ATTACK: { name: 'DOUBLE_ATTACK', parentType: 'ATTACK', className: 'double-attack' },
    TRIPLE_ATTACK: { name: 'TRIPLE_ATTACK', parentType: 'ATTACK', className: 'triple-attack' },
    ABILITY: { name: 'ABILITY', parentType: 'ROOT', className: 'ability' },
    FIRST_ABILITY: { name: 'FIRST_ABILITY', parentType: 'ABILITY', className: 'first-ability' },
    SECOND_ABILITY: { name: 'SECOND_ABILITY', parentType: 'ABILITY', className: 'second-ability' },
    THIRD_ABILITY: { name: 'THIRD_ABILITY', parentType: 'ABILITY', className: 'third-ability' },
    SUPPORT_ABILITY: { name: 'SUPPORT_ABILITY', parentType: 'ROOT', className: 'support-ability' },
    FIRST_SUPPORT_ABILITY: { name: 'FIRST_SUPPORT_ABILITY', parentType: 'SUPPORT_ABILITY', className: 'first-support-ability' },
    SECOND_SUPPORT_ABILITY: { name: 'SECOND_SUPPORT_ABILITY', parentType: 'SUPPORT_ABILITY', className: 'second-support-ability' },
    THIRD_SUPPORT_ABILITY: { name: 'THIRD_SUPPORT_ABILITY', parentType: 'SUPPORT_ABILITY', className: 'third-support-ability' },
    FOURTH_SUPPORT_ABILITY: { name: 'FOURTH_SUPPORT_ABILITY', parentType: 'SUPPORT_ABILITY', className: 'fourth-support-ability' },
    FIFTH_SUPPORT_ABILITY: { name: 'FIFTH_SUPPORT_ABILITY', parentType: 'SUPPORT_ABILITY', className: 'fifth-support-ability' },
    CHARGE_ATTACK: { name: 'CHARGE_ATTACK', parentType: 'ROOT', className: 'charge-attack' },
    CHARGE_ATTACK_DEFAULT: { name: 'CHARGE_ATTACK_DEFAULT', parentType: 'CHARGE_ATTACK', className: 'charge-attack-default' },
    CHARGE_ATTACK_A: { name: 'CHARGE_ATTACK_A', parentType: 'CHARGE_ATTACK', className: 'charge-attack-a' },
    CHARGE_ATTACK_B: { name: 'CHARGE_ATTACK_B', parentType: 'CHARGE_ATTACK', className: 'charge-attack-b' },
    CHARGE_ATTACK_C: { name: 'CHARGE_ATTACK_C', parentType: 'CHARGE_ATTACK', className: 'charge-attack-c' },
    CHARGE_ATTACK_D: { name: 'CHARGE_ATTACK_D', parentType: 'CHARGE_ATTACK', className: 'charge-attack-d' },
    CHARGE_ATTACK_E: { name: 'CHARGE_ATTACK_E', parentType: 'CHARGE_ATTACK', className: 'charge-attack-e' },
    CHARGE_ATTACK_F: { name: 'CHARGE_ATTACK_F', parentType: 'CHARGE_ATTACK', className: 'charge-attack-f' },
    CHARGE_ATTACK_G: { name: 'CHARGE_ATTACK_G', parentType: 'CHARGE_ATTACK', className: 'charge-attack-g' },
    PHASE_CHANGE: { name: 'PHASE_CHANGE', parentType: 'ROOT', className: 'phase-change' },
    DEAD: { name: 'DEAD', parentType: 'ROOT', className: 'dead' },
    ETC: { name: 'ETC', parentType: 'ROOT', className: 'etc' },
    NONE: { name: 'NONE', parentType: 'ROOT', className: 'none' },
};


MoveType.byName = function getMoveTypeByName(name) {
    return Object.values(MoveType).find(type => type.name === name);
}

MoveType.byClassName = function getMoveTypeByName(className) {
    return Object.values(MoveType).find(type => type.className === className);
}

// 부모타입 반환 getParentType();
Object.values(MoveType).forEach(moveType => {
    if (moveType?.name) {  // MoveType 항목만 필터링
        moveType.getParentType = function() {
            return this.parentType
                ? MoveType.byName(this.parentType)
                : null;
        };
        moveType.isNone = function () {
            return this === MoveType.NONE;
        }
    }
});

// StandBy 로 idle, damaged, chargeATtack, break 찾기
Object.values(MoveType).forEach(moveType => {
    if (moveType?.name.includes('STANDBY')) {  // MoveType.STANDBY_X 항목만 필터링
        moveType.getIdleType = function() {
            return this.parentType
                ? MoveType.getMoveType(moveType, MoveType.IDLE_A, MoveType.IDLE_B, MoveType.IDLE_C, MoveType.IDLE_D, MoveType.IDLE_E, MoveType.IDLE_F, MoveType.IDLE_G)
                : null;
        };
        moveType.getDamagedType = function() {
            return this.parentType
                ? MoveType.getMoveType(moveType, MoveType.DAMAGED_A, MoveType.DAMAGED_B, MoveType.DAMAGED_C, MoveType.DAMAGED_D, MoveType.DAMAGED_E, MoveType.DAMAGED_F, MoveType.DAMAGED_G)
                : null;
        };
        moveType.getChargeAttackType = function() {
            return this.parentType
                ? MoveType.getMoveType(moveType, MoveType.CHARGE_ATTACK_A, MoveType.CHARGE_ATTACK_B, MoveType.CHARGE_ATTACK_C, MoveType.CHARGE_ATTACK_D, MoveType.CHARGE_ATTACK_E, MoveType.CHARGE_ATTACK_F, MoveType.CHARGE_ATTACK_G)
                : null;
        };
        moveType.getBreakType = function() {
            return this.parentType
                ? MoveType.getMoveType(moveType, MoveType.BREAK_A, MoveType.BREAK_B, MoveType.BREAK_C, MoveType.BREAK_D, MoveType.BREAK_E, MoveType.BREAK_F, MoveType.BREAK_G)
                : null;
        };
    }
});

MoveType.getMoveType = function getMoveType(moveType, moveType1, moveType2, moveType3, moveType4, moveType5, moveType6, moveType7) {
    // null 인경우 에러남
    switch (moveType.name) {
        case MoveType.STANDBY_A.name:
            return moveType1;
        case MoveType.STANDBY_B.name:
            return moveType2;
        case MoveType.STANDBY_C.name:
            return moveType3;
        case MoveType.STANDBY_D.name:
            return moveType4;
        case MoveType.STANDBY_E.name:
            return moveType5;
        case MoveType.STANDBY_F.name:
            return moveType6;
        case MoveType.STANDBY_G.name:
            return moveType7;
        default:
            return MoveType.NONE;
    }
}

Object.freeze(MoveType);