/* 응답 ================================================================================================================ */
class MoveResponse {
    constructor(data) {
        // actor
        this.actorOrder = data.actorOrder;
        this.actorName = data.actorName;

        // move
        this.moveId = data.moveId;
        this.moveType = MoveType.byName(data.moveType);
        this.moveName = data.moveName;
        this.motion = data.motion || 'none';
        this.motionCustomDuration = (data.motionCustomDuration || 0) * createjs.Ticker.interval;

        this.allTarget = data.isAllTarget ?? false;

        // damage result
        this.totalHitCount = data.totalHitCount;
        this.damages = data.damages || [];
        this.additionalDamages = data.additionalDamages || [];
        this.elementTypes = data.elementTypes || [];
        this.damageTypes = data.damageTypes || [];
        this.attackMultiHitCount = data.attackMultiHitCount;

        this.enemyAttackTargetOrders = data.enemyAttackTargetIds.map(id => stage.gGameStatus.actorIds.indexOf(id + ''));


        // status result [적][아군][아군][아군][아군]
        this.addedBattleStatusesList = (data.addedBattleStatusesList || []).map(statusList => statusList.map(s => new StatusDto(s)));
        this.addedBuffStatusesList = this.addedBattleStatusesList.map(addedBattleStatuses => addedBattleStatuses.filter(status => status.type === 'BUFF' || status.type === 'BUFF_FOR_ALL'));
        this.addedDebuffStatusesList = this.addedBattleStatusesList.map(addedBattleStatuses => addedBattleStatuses.filter(status => status.type === 'DEBUFF' || status.type === 'DEBUFF_FOR_ALL'));
        this.removedBattleStatusesList = (data.removedBattleStatusesList || []).map(statusList => statusList.map(s => new StatusDto(s, true)));
        this.removedBuffStatusesList = this.removedBattleStatusesList.map(removedStatuses => removedStatuses.filter(status => status.type === 'BUFF' || status.type === 'BUFF_FOR_ALL'));
        this.removedDebuffStatusesList = this.removedBattleStatusesList.map(removedStatuses => removedStatuses.filter(status => status.type === 'DEBUFF' || status.type === 'DEBUFF_FOR_ALL'));
        this.heals = data.heals || [];
        this.effectDamages = data.effectDamages || [];

        // omen
        this.omen = new OmenDto(data.omen || {});

        // snapshot
        this.hps = data.hps || [];
        this.hpRates = data.hpRates || [];
        this.hpRates.forEach(function (hpRate, index) {
            if (window.player) player.actors.get('actor-' + index)?.setHpRate(hpRate);
        })
        this.chargeGauges = data.chargeGauges || [];
        this.fatalChainGauge = data.fatalChainGauge ?? 0;
        this.enemyMaxChargeGauge = data.enemyMaxChargeGauge ?? 0;
        this.abilityCoolDowns = data.abilityCoolDowns || [];
        this.abilitySealeds = data.abilitySealeds || [];
        this.abilityUseCounts = data.abilityUseCounts || [];
        this.currentStatusEffectsList = data.currentBattleStatusesList.map(statuses => statuses.filter(s => s.type !== 'PASSIVE').map(s => new StatusDto(s)));

        // honor
        this.resultHonor = data.resultHonor ?? 0;

        // etc
        this.summonCooldowns = data.summonCooldowns || [];
        this.unionSummonId = data.unionSummonId ?? null;
        this.isUnionSummon = data.isUnionSummon ?? false;
        this.hasUnionSummon = data.hasUnionSummon ?? false;
        this.enemyEstimatedAtk = data.estimatedEnemyAtk || [];
    }

    print() {
        console.log(JSON.stringify(this, null, 2));
    }
}

// JSON 배열을 MoveResponse 인스턴스 배열로 변환하는 함수
function parseMoveResponseList(jsonArray) {
    return jsonArray.map(item => new MoveResponse(item));
}

class StatusDto {
    constructor({
                    type,
                    name,
                    imageSrc,
                    effectText,
                    statusText,
                    duration,
                    durationType,
                    remainingDuration
                }, removed = false) {
        this.type = type;
        this.name = name;
        this.imageSrc = imageSrc;
        this.effectText = effectText;
        this.statusText = statusText;
        this.durationType = durationType;
        this.duration = duration;
        this.remainingDuration =
            durationType.includes('INFINITE') ? '영속'
                : durationType.includes('TURN') ? remainingDuration + ' 턴'
                    : durationType.includes('TIME') ? Math.floor(remainingDuration / 60) + ':' + (remainingDuration % 60).toString().padStart(2, '0') : '오류';
        this.removed = removed; // 상태효과 해제처리시 사용
    }
}

class OmenDto {
    constructor({type, remainValue, cancelCondition, name, info, motion}) {
        this.type = OmenType.byName(type);
        this.remainValue = remainValue;
        this.cancelCondition = cancelCondition;
        this.name = name;
        this.info = info;
        this.motion = motion;
    }
}

/* Move ============================================================================================================= */
class MoveInfo {
    constructor({
                    type,
                    id,
                    name,
                    order, // ability.order
                    actorId,
                    actorIndex,
                    info,
                    coolDown,
                    iconSrc,
                    portraitSrc,
                    count,
                    additionalType // 추가타입 (포션타입, 어빌리티 타입)
                }) {
        this.type = type || '';
        this.id = Number(id) || -1;
        this.name = name || '';
        this.order = Number(order) || -1;
        this.actorId = Number(actorId) || -1;
        this.actorIndex = actorIndex || '';
        this.info = info || '';
        this.coolDown = Number(coolDown) || -1;
        this.iconSrc = iconSrc;
        this.portraitSrc = portraitSrc;
        this.count = Number(count) || -1;
        this.additionalType = additionalType || '';
    }
}

/* 상수 ================================================================================================================ */

const Constants = {
    enemy: {
        // key: cjsName (stage.gGameStatus.enemyMainCjsName)
        "enemy_4300903" : { // diaspora1
            backgroundImage: '/static/assets/bg/dia-1.jpg',
        },
        "enemy_4300913" : { // diaspora2
            backgroundImage: '/static/assets/bg/dia-2.jpg',
        }
    },

    summon: {
        // id
        69: {
            name: '제우스',
            info: '적에게 2배 빛속성 데미지 2회, 공격력 다운, 방어력 다운, 아군 전체의 오의 게이지 상승량 증가',
            cjs: 'summon_2040080000_02',
            portraitSrc: 'https://prd-game-a1-granbluefantasy.akamaized.net/assets/img/sp/assets/summon/raid_normal/2040080000_03.jpg',
            cutinSrc: 'https://prd-game-a1-granbluefantasy.akamaized.net/assets/img/sp/assets/summon/cutin/2040080000_03.jpg',
        }
    },

    Delay: {
        // 1200 데미지표시 ~ 데미지 삭제까지 딜레이 (데미지 표시시간 최대치)
        damageShowDelete: 1200,
        // 800 데미지 표시 ~ 스테이터스 표시까지 딜레이 (일반적으로 첫 데미지 페이드아웃 시작)
        damageShowToNext: 800,
        // 300 processXXMove 일반 딜레이
        globalMoveDelay: 300,
        // 150 processXXMove 중, 어빌리티 레일에서 기본적으로 딜레이가 걸리는경우 globalMoveDelay 대신 이쪽사용
        railMoveDelay: 150,
    },
}
Object.freeze(Constants);

/* MoveType ========================================================================================================== */
const MoveType = {
    ROOT: {name: 'ROOT', parentType: null, className: 'root'},
    IDLE: {name: 'IDLE', parentType: 'ROOT', className: 'idle'},
    IDLE_DEFAULT: {name: 'IDLE_DEFAULT', parentType: 'IDLE', className: 'idle-default'},
    IDLE_A: {name: 'IDLE_A', parentType: 'IDLE', className: 'idle-a'},
    IDLE_B: {name: 'IDLE_B', parentType: 'IDLE', className: 'idle-b'},
    IDLE_C: {name: 'IDLE_C', parentType: 'IDLE', className: 'idle-c'},
    IDLE_D: {name: 'IDLE_D', parentType: 'IDLE', className: 'idle-d'},
    IDLE_E: {name: 'IDLE_E', parentType: 'IDLE', className: 'idle-e'},
    IDLE_F: {name: 'IDLE_F', parentType: 'IDLE', className: 'idle-f'},
    IDLE_G: {name: 'IDLE_G', parentType: 'IDLE', className: 'idle-g'},
    DAMAGED: {name: 'DAMAGED', parentType: 'ROOT', className: 'damaged'},
    DAMAGED_DEFAULT: {name: 'DAMAGED_DEFAULT', parentType: 'DAMAGED', className: 'damaged-default'},
    DAMAGED_A: {name: 'DAMAGED_A', parentType: 'DAMAGED', className: 'damaged-a'},
    DAMAGED_B: {name: 'DAMAGED_B', parentType: 'DAMAGED', className: 'damaged-b'},
    DAMAGED_C: {name: 'DAMAGED_C', parentType: 'DAMAGED', className: 'damaged-c'},
    DAMAGED_D: {name: 'DAMAGED_D', parentType: 'DAMAGED', className: 'damaged-d'},
    DAMAGED_E: {name: 'DAMAGED_E', parentType: 'DAMAGED', className: 'damaged-e'},
    DAMAGED_F: {name: 'DAMAGED_F', parentType: 'DAMAGED', className: 'damaged-f'},
    DAMAGED_G: {name: 'DAMAGED_G', parentType: 'DAMAGED', className: 'damaged-g'},
    STANDBY: {name: 'STANDBY', parentType: 'ROOT', className: 'standby'},
    STANDBY_A: {name: 'STANDBY_A', parentType: 'STANDBY', className: 'standby-a'},
    STANDBY_B: {name: 'STANDBY_B', parentType: 'STANDBY', className: 'standby-b'},
    STANDBY_C: {name: 'STANDBY_C', parentType: 'STANDBY', className: 'standby-c'},
    STANDBY_D: {name: 'STANDBY_D', parentType: 'STANDBY', className: 'standby-d'},
    STANDBY_E: {name: 'STANDBY_E', parentType: 'STANDBY', className: 'standby-e'},
    STANDBY_F: {name: 'STANDBY_F', parentType: 'STANDBY', className: 'standby-f'},
    STANDBY_G: {name: 'STANDBY_G', parentType: 'STANDBY', className: 'standby-g'},
    BREAK: {name: 'BREAK', parentType: 'ROOT', className: 'break'},
    BREAK_A: {name: 'BREAK_A', parentType: 'BREAK', className: 'break-a'},
    BREAK_B: {name: 'BREAK_B', parentType: 'BREAK', className: 'break-b'},
    BREAK_C: {name: 'BREAK_C', parentType: 'BREAK', className: 'break-c'},
    BREAK_D: {name: 'BREAK_D', parentType: 'BREAK', className: 'break-d'},
    BREAK_E: {name: 'BREAK_E', parentType: 'BREAK', className: 'break-e'},
    BREAK_F: {name: 'BREAK_F', parentType: 'BREAK', className: 'break-f'},
    BREAK_G: {name: 'BREAK_G', parentType: 'BREAK', className: 'break-g'},
    ATTACK: {name: 'ATTACK', parentType: 'ROOT', className: 'attack'},
    SINGLE_ATTACK: {name: 'SINGLE_ATTACK', parentType: 'ATTACK', className: 'single-attack', attackCount: 1},
    DOUBLE_ATTACK: {name: 'DOUBLE_ATTACK', parentType: 'ATTACK', className: 'double-attack', attackCount: 2},
    TRIPLE_ATTACK: {name: 'TRIPLE_ATTACK', parentType: 'ATTACK', className: 'triple-attack', attackCount: 3},
    QUADRUPLE_ATTACK: {name: 'QUADRUPLE_ATTACK', parentType: 'ATTACK', className: 'quadruple-attack', attackCount: 4},
    ABILITY: {name: 'ABILITY', parentType: 'ROOT', className: 'ability'},
    FIRST_ABILITY: {name: 'FIRST_ABILITY', parentType: 'ABILITY', className: 'first-ability'},
    SECOND_ABILITY: {name: 'SECOND_ABILITY', parentType: 'ABILITY', className: 'second-ability'},
    THIRD_ABILITY: {name: 'THIRD_ABILITY', parentType: 'ABILITY', className: 'third-ability'},
    SUPPORT_ABILITY: {name: 'SUPPORT_ABILITY', parentType: 'ROOT', className: 'support-ability'},
    FIRST_SUPPORT_ABILITY: {
        name: 'FIRST_SUPPORT_ABILITY',
        parentType: 'SUPPORT_ABILITY',
        className: 'first-support-ability'
    },
    SECOND_SUPPORT_ABILITY: {
        name: 'SECOND_SUPPORT_ABILITY',
        parentType: 'SUPPORT_ABILITY',
        className: 'second-support-ability'
    },
    THIRD_SUPPORT_ABILITY: {
        name: 'THIRD_SUPPORT_ABILITY',
        parentType: 'SUPPORT_ABILITY',
        className: 'third-support-ability'
    },
    FOURTH_SUPPORT_ABILITY: {
        name: 'FOURTH_SUPPORT_ABILITY',
        parentType: 'SUPPORT_ABILITY',
        className: 'fourth-support-ability'
    },
    FIFTH_SUPPORT_ABILITY: {
        name: 'FIFTH_SUPPORT_ABILITY',
        parentType: 'SUPPORT_ABILITY',
        className: 'fifth-support-ability'
    },
    SIXTH_SUPPORT_ABILITY: {
        name: 'SIXTH_SUPPORT_ABILITY',
        parentType: 'SUPPORT_ABILITY',
        className: 'sixth-support-ability'
    },
    SEVENTH_SUPPORT_ABILITY: {
        name: 'SEVENTH_SUPPORT_ABILITY',
        parentType: 'SUPPORT_ABILITY',
        className: 'seventh-support-ability'
    },
    EIGHTH_SUPPORT_ABILITY: {
        name: 'EIGHTH_SUPPORT_ABILITY',
        parentType: 'SUPPORT_ABILITY',
        className: 'eighth-support-ability'
    },
    NINTH_SUPPORT_ABILITY: {
        name: 'NINTH_SUPPORT_ABILITY',
        parentType: 'SUPPORT_ABILITY',
        className: 'ninth-support-ability'
    },
    TENTH_SUPPORT_ABILITY: {
        name: 'TENTH_SUPPORT_ABILITY',
        parentType: 'SUPPORT_ABILITY',
        className: 'tenth-support-ability'
    },
    CHARGE_ATTACK: {name: 'CHARGE_ATTACK', parentType: 'ROOT', className: 'charge-attack'},
    CHARGE_ATTACK_DEFAULT: {
        name: 'CHARGE_ATTACK_DEFAULT',
        parentType: 'CHARGE_ATTACK',
        className: 'charge-attack-default'
    },
    CHARGE_ATTACK_A: {name: 'CHARGE_ATTACK_A', parentType: 'CHARGE_ATTACK', className: 'charge-attack-a'},
    CHARGE_ATTACK_B: {name: 'CHARGE_ATTACK_B', parentType: 'CHARGE_ATTACK', className: 'charge-attack-b'},
    CHARGE_ATTACK_C: {name: 'CHARGE_ATTACK_C', parentType: 'CHARGE_ATTACK', className: 'charge-attack-c'},
    CHARGE_ATTACK_D: {name: 'CHARGE_ATTACK_D', parentType: 'CHARGE_ATTACK', className: 'charge-attack-d'},
    CHARGE_ATTACK_E: {name: 'CHARGE_ATTACK_E', parentType: 'CHARGE_ATTACK', className: 'charge-attack-e'},
    CHARGE_ATTACK_F: {name: 'CHARGE_ATTACK_F', parentType: 'CHARGE_ATTACK', className: 'charge-attack-f'},
    CHARGE_ATTACK_G: {name: 'CHARGE_ATTACK_G', parentType: 'CHARGE_ATTACK', className: 'charge-attack-g'},

    FORM_CHANGE: {name: 'FORM_CHANGE', parentType: 'ROOT', className: 'form-change'},
    FORM_CHANGE_DEFAULT: {name: 'FORM_CHANGE_DEFAULT', parentType: 'FORM_CHANGE', className: 'form-change-default'},
    FORM_CHANGE_ENTRY: {name: 'FORM_CHANGE_ENTRY', parentType: 'FORM_CHANGE', className: 'form-change-entry'},

    SUMMON: {name: 'SUMMON', parentType: 'ROOT', className: 'summon'},
    SUMMON_DEFAULT: {name: 'SUMMON_DEFAULT', parentType: 'SUMMON', className: 'summon'},

    DEAD: {name: 'DEAD', parentType: 'ROOT', className: 'dead'},
    DEAD_DEFAULT: {name: 'DEAD_DEFAULT', parentType: 'DEAD', className: 'dead'},

    GUARD: {name: 'GUARD', parentType: 'ROOT', className: 'guard'},
    GUARD_DEFAULT: {name: 'GUARD_DEFAULT', parentType: 'GUARD', className: 'guard'},

    FATAL_CHAIN: {name: 'FATAL_CHAIN', parentType: 'ROOT', className: 'fatal-chain'},
    FATAL_CHAIN_DEFAULT: {name: 'FATAL_CHAIN_DEFAULT', parentType: 'FATAL_CHAIN', className: 'fatal-chain'},

    TURN_END: {name: 'TURN_END', parentType: 'ROOT', className: 'turn-end'},
    TURN_END_PROCESS: {name: 'TURN_END_PROCESS', parentType: 'TURN_END', className: 'turn-end'},
    TURN_END_HEAL: {name: 'TURN_END_HEAL', parentType: 'TURN_END', className: 'turn-end-heal'},
    TURN_END_DAMAGE: {name: 'TURN_END_DAMAGE', parentType: 'TURN_END', className: 'turn-end-damage'},
    TURN_END_CHARGE_GAUGE: {name: 'TURN_END_CHARGE_GAUGE', parentType: 'TURN_END', className: 'turn-end-charge-gauge'},
    TURN_FINISH: {name: 'TURN_FINISH', parentType: 'TURN_END', className: 'turn-finish'},

    ETC: {name: 'ETC', parentType: 'ROOT', className: 'etc'},
    STRIKE_SEALED: {name: 'STRIKE_SEALED', parentType: 'ETC', className: 'strike-sealed'},
    SYNC: {name: 'SYNC', parentType: 'ETC', className: 'sync'},

    NONE: {name: 'NONE', parentType: 'ROOT', className: 'none'},
};
MoveType.byName = function getMoveTypeByName(name) {
    return Object.values(MoveType).find(type => type.name === name);
}
MoveType.byClassName = function getMoveTypeByName(className) {
    return Object.values(MoveType).find(type => type.className === className);
}
Object.values(MoveType).forEach(moveType => { // 부모타입 반환 getParentType();
    if (moveType?.name) {  // MoveType 항목만 필터링
        moveType.getParentType = function () {
            return this.parentType
                ? MoveType.byName(this.parentType)
                : null;
        };
        moveType.isNone = function () {
            return this === MoveType.NONE;
        }
    }
});
Object.freeze(MoveType);

/*

삭제 예정 ===================

Object.values(MoveType).forEach(moveType => { // StandBy 로 idle, damaged, chargeAttack, break 찾기
    if (moveType?.name.includes('STANDBY')) {  // MoveType.STANDBY_X 항목만 필터링
        moveType.getIdleType = function () {
            return this.parentType
                ? MoveType.getMoveType(moveType, MoveType.IDLE_A, MoveType.IDLE_B, MoveType.IDLE_C, MoveType.IDLE_D, MoveType.IDLE_E, MoveType.IDLE_F, MoveType.IDLE_G)
                : null;
        };
        moveType.getDamagedType = function () {
            return this.parentType
                ? MoveType.getMoveType(moveType, MoveType.DAMAGED_A, MoveType.DAMAGED_B, MoveType.DAMAGED_C, MoveType.DAMAGED_D, MoveType.DAMAGED_E, MoveType.DAMAGED_F, MoveType.DAMAGED_G)
                : null;
        };
        moveType.getChargeAttackType = function () {
            return this.parentType
                ? MoveType.getMoveType(moveType, MoveType.CHARGE_ATTACK_A, MoveType.CHARGE_ATTACK_B, MoveType.CHARGE_ATTACK_C, MoveType.CHARGE_ATTACK_D, MoveType.CHARGE_ATTACK_E, MoveType.CHARGE_ATTACK_F, MoveType.CHARGE_ATTACK_G)
                : null;
        };
        moveType.getBreakType = function () {
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
*/

const OmenType = {
    NONE: {name: 'NONE', info: '없음', className: 'none'},
    CHARGE_ATTACK: {name: 'CHARGE_ATTACK', info: '차지어택', className: 'charge-attack'}, // CT기
    INCANT_ATTACK: {name: 'INCANT_ATTACK', info: '영창기', className: 'incant-attack'},
    HP_TRIGGER: {name: 'HP_TRIGGER', info: 'HP트리거', className: 'hp-trigger'},
}
OmenType.byName = function (name) {
    return Object.values(OmenType).find(type => type.name === name);
}
Object.freeze(OmenType);
