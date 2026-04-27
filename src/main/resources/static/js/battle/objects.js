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

        this.allTarget = data.isAllTarget ?? false;

        // damage result
        this.totalHitCount = data.totalHitCount;
        this.damages = data.damages || [];
        this.additionalDamages = data.additionalDamages || [];
        this.elementTypes = data.elementTypes || [];
        this.damageTypes = data.damageTypes || [];
        this.attackMultiHitCount = data.attackMultiHitCount;
        this.normalAttackCount = data.normalAttackCount;

        this.enemyAttackTargetOrders = (data.enemyAttackTargetIds || []).map(id => gameStateManager.getState('actorIds').indexOf(id));


        // status result [적][아군][아군][아군][아군]
        this.addedBattleStatusesList = (data.addedBattleStatusesList || []).map(statusList => statusList.map(s => new StatusDto(s)).sort((a, b) => b.displayPriority - a.displayPriority));
        this.addedBuffStatusesList = this.addedBattleStatusesList.map(addedBattleStatuses => addedBattleStatuses.filter(status => status.type === 'BUFF' || status.type === 'BUFF_FOR_ALL'));
        this.addedDebuffStatusesList = this.addedBattleStatusesList.map(addedBattleStatuses => addedBattleStatuses.filter(status => status.type === 'DEBUFF' || status.type === 'DEBUFF_FOR_ALL'));
        this.removedBattleStatusesList = (data.removedBattleStatusesList || []).map(statusList => statusList.map(s => new StatusDto(s, true)).sort((a, b) => b.displayPriority - a.displayPriority));
        this.removedBuffStatusesList = this.removedBattleStatusesList.map(removedStatuses => removedStatuses.filter(status => status.type === 'BUFF' || status.type === 'BUFF_FOR_ALL'));
        this.removedDebuffStatusesList = this.removedBattleStatusesList.map(removedStatuses => removedStatuses.filter(status => status.type === 'DEBUFF' || status.type === 'DEBUFF_FOR_ALL'));
        this.levelDownedBattleStatusesList = (data.levelDownedBattleStatusesList || []).map(statusList => statusList.map(s => new StatusDto(s))); // 얘는 버프, 디버프 구분없음
        this.heals = data.heals || [];
        this.effectDamages = data.effectDamages || [];

        // omen
        this.omen = data.omen !== null ? new OmenDto(data.omen) : OmenDto.empty();

        // snapshot
        this.hps = data.hps || [];
        this.hpRates = data.hpRates || [];
        this.barriers = data.barriers || [];
        this.chargeGauges = data.chargeGauges || [];
        this.canChargeAttacks = data.canChargeAttacks || [];
        this.fatalChainGauge = data.fatalChainGauge ?? 0;
        this.enemyMaxChargeGauge = data.enemyMaxChargeGauge ?? 0;
        this.abilityCoolDowns = data.abilityCoolDowns || [];
        this.abilitySealeds = data.abilitySealeds || [];
        this.abilityUseCounts = data.abilityUseCounts || [];
        this.doubleAttackRates = data.doubleAttackRates || [];
        this.tripleAttackRates = data.tripleAttackRates || [];
        this.currentStatusEffectsList = data.currentBattleStatusesList.map(statuses => statuses.filter(s => s.type === 'BUFF' || s.type === 'DEBUFF').map(s => new StatusDto(s)));
        // visual
        this.visualInfo = data.visualInfo !== null ? new VisualInfo(data.visualInfo) : null;

        // honor
        this.resultHonor = data.resultHonor ?? 0;

        // changedMove
        this.changedMoveInfo = data.changedMoveInfo !== null ? new MoveInfo(data.changedMoveInfo) : null;
        this.deletedMoveId = data.deletedMoveId ?? null;

        // etc
        this.summonCooldowns = data.summonCooldowns || [];
        this.enemyEstimatedAtk = data.estimatedEnemyAtk || [];
        this.isEnemyFormChange = data.isEnemyFormChange ?? false;
        this.unionSummonInfo = data.unionSummonInfo !== null ? new MoveInfo(data.unionSummonInfo) : null;
        this.forMemberAbilityInfo = data.forMemberAbilityInfo !== null ? {
            sourceUsername: data.forMemberAbilityInfo.sourceUsername,
            moveName: data.forMemberAbilityInfo.moveName,
        } : null;

    }

    print() {
        console.log(JSON.stringify(this, null, 2));
    }
}

// JSON 배열을 MoveResponse 인스턴스 배열로 변환하는 함수
function parseMoveResponseList(jsonArray) {
    // console.log('[parseMoveResponseList] jsonArray = ', jsonArray);
    return jsonArray.map(item => new MoveResponse(item));
}

class VisualInfo {
    constructor({moveCjsName, isTargetedEnemy, voiceLabel}) {
        this.moveCjsName = moveCjsName;
        this.isTargetedEnemy = !!isTargetedEnemy;
        this.voiceLabel = voiceLabel;
    }
}

class StatusDto {
    constructor({
                    type,
                    name,
                    iconSrc,
                    effectText,
                    statusText,
                    duration,
                    durationType,
                    remainingDuration,
                    level,
                    maxLevel,
                    removable,
                    resistible,
                    displayPriority,
                    modifiers,
                }, removed = false) {
        this.type = type;
        this.name = name;
        this.iconSrc = iconSrc || '';
        this.effectText = effectText;
        this.statusText = statusText;
        this.durationType = durationType;
        this.duration = duration;
        this.level = level;
        this.maxLevel = maxLevel;
        this.remainingDuration = remainingDuration;
        this.removed = removed; // 상태효과 해제처리시 사용

        this.removable = !!removable;
        this.resistible = !!resistible;

        this.displayPriority = displayPriority || 0;
        this.modifiers = modifiers || null;

        if (this.resistible === false) {
            this.statusText += ' - 필중'
        }

    }
}

class OmenDto {
    constructor({
                    type,
                    name,
                    omenInfo,
                    motion,
                    chargeAttackInfo,
                    standbyMoveType,
                    omenBreak,
                    lastTriggeredHp,
                    omenCancelCond
                }) {
        this.name = name;
        this.info = omenInfo || '';
        this.chargeAttackInfo = chargeAttackInfo;
        this.type = OmenType.byName(type);
        this.motion = motion;
        this.isBreak = omenBreak;
        this.lastTriggeredHp = lastTriggeredHp;
        this.cancelConditions = (omenCancelCond || []).map(c => new OmenCancelCondition(c));
        this.standbyMoveType = MoveType.byName(standbyMoveType); // updateBgm 에서 사용
    }

    static empty() {
        return new OmenDto({
            type: 'NONE',
            name: '',
            info: '',
            motion: null,
            standbyMoveType: null,
            isBreak: false,
            omenCancelCond: []
        });
    }

    isEmpty() {
        return this.type === OmenType.NONE;
    }
}

class OmenCancelCondition {
    constructor({index, remainValue, info, updateTiming, type}) {
        this.index = index;
        this.remainValue = remainValue;
        this.info = info;
        this.updateTiming = updateTiming;
        this.type = type;
    }
}

/* Move ============================================================================================================= */
class MoveInfo {
    constructor({
                    type,
                    id,
                    name,
                    order, // ability.order, summon.order
                    actorId,
                    actorIndex,
                    info,
                    damageRate,
                    hitCount,
                    cooldown,
                    maxCooldown,
                    iconImageSrc,
                    portraitImageSrc,
                    cutinImageSrc,
                    count,
                    potionType, // 직접 지정필요
                    abilityType,
                    displayAbilityType,
                    cjsName,
                    statusEffects,
                    sealed,
                    nextMoveId,
                }) {
        this.type = type || '';
        this.type === 'FATAL_CHAIN_DEFAULT' ? this.type = 'FATAL_CHAIN' : this.type;
        this.damageRate = damageRate || 0;
        this.hitCount = hitCount || 0;
        this.id = Number(id) || -1;
        this.name = name || '';
        this.order = Number(order) || -1;
        this.actorId = Number(actorId) || -1;
        this.targetActorId = Number(actorId) || -1; // 포션사용 등 특수한 경우에 사용
        this.actorIndex = actorIndex || '';
        this.info = info || '';
        this.cooldown = cooldown !== null ? Number(cooldown) : -999; // 0, -1(재사용불가) 가능
        this.maxCooldown = maxCooldown !== null ? Number(maxCooldown) : -999; // 0, -1(재사용불가) 가능
        this.iconImageSrc = iconImageSrc;
        this.portraitImageSrc = portraitImageSrc;
        this.cutinImageSrc = cutinImageSrc;
        this.sealed = sealed;
        // this.count = Number(count) || -1;
        this.potionType = potionType || '';
        this.abilityType = abilityType || '';
        this.displayAbilityType = displayAbilityType || '';
        this.cjsName = cjsName || '';
        this.statusEffects = (statusEffects || []).map(s => new StatusDto(s));
        this.nextMoveId = nextMoveId || null;
    }
}

/* 상수 ================================================================================================================ */

const Constants = {

    // 10
    defaultMortalStartFrame: 10,

    // cjs interval (createjs.Ticker.framerate = 30.303030303030305; // interval 33)
    // 31
    defaultCjsInterval: 31,
    // framerate = 32.2580...
    // 27
    fastCjsInterval: 27,
    // framerate = 37.03703703703704...


    enemy: {
        // key: cjsName (stage.gGameStatus.enemyMainCjsName)
        "enemy_4300903": { // diaspora1
            backgroundImage: '/static/assets/bg/dia-1.jpg',
            customDuration: { // key: motion value: fps
                'mortal_A': 45, // 자괴인자
                'mortal_B': 37, // 경성방사
                'mortal_C': 40, // 긴급회복
            } // motion mainCjs.motion 을 따라가서 그냥 고정
        },
        "enemy_4300913": { // diaspora2
            backgroundImage: '/static/assets/bg/dia-2.jpg',
            customDuration: {
                'mortal_A': 41, // 인자붕괴
                'mortal_B': 25, // 이성임계
                'mortal_C': 90, // 허수몽핵
            }
        },
        "enemy_4300743": { // diaspora2a
            customDuration: {
                'additional_mortal_B': 33, // 경성방사
            }
        },

        "enemy_7300813": {
            customDuration: {
                // 'mortal_A' : 45, // 입장
                'mortal_B': 50, // hp
                'mortal_C': 30, // 화룡
                'mortal_D': 55, // 풍룡
            }
        },

        "enemy_7300823": {
            customDuration: {
                // 'mortal_A' : 45, // 입장
                'mortal_B': 60, // hp
                'mortal_C': 70, // 토룡
                'mortal_D': 20, // 수룡
            }
        },

        "enemy_7300833": {
            customDuration: {
                // 'mortal_A' : 45, // 입장
                'mortal_B': 216, // hp
                'mortal_C': 80, // 암룡
                'mortal_D': 30, // 광룡
            }
        },

        "enemy_7300843": {
            customDuration: {
                'mortal_A': 150, // 리베라티오
                'mortal_B': 25, // 엑세레이저 5
                'mortal_C': 10, // 아트로피아 2
                'mortal_D': 40, // 루체아 1
                'ab_motion_mortal_E': 35, // 루프레스
            }
        },

    },

    raidBgUrl: (cjsName) => `/static/gbf/img/raid_bg/${cjsName}.jpg`,

    Delay: {
        // 1200 데미지표시 ~ 데미지 삭제까지 딜레이 (데미지 표시시간 최대치)
        damageShowDelete: 1200,
        // 800 데미지 표시 ~ 스테이터스 표시까지 딜레이 (일반적으로 첫 데미지 페이드아웃 시작)
        damageShowToNext: 800,
        // 미사용
        statusShowToNext: 100,
        // 200 processXXMove 일반 딜레이
        globalMoveDelay: 200,
        // 300 커맨드 실행 딜레이
        commandExecuteDelay: 300,
    },
}
Object.freeze(Constants);

/* MoveType ========================================================================================================== */
const MoveType = {
    ROOT: {name: 'ROOT', parentType: null, className: 'root'},
    STANDBY: {name: 'STANDBY', parentType: 'ROOT', className: 'standby'},
    STANDBY_A: {name: 'STANDBY_A', parentType: 'STANDBY', className: 'standby-a'},
    STANDBY_B: {name: 'STANDBY_B', parentType: 'STANDBY', className: 'standby-b'},
    STANDBY_C: {name: 'STANDBY_C', parentType: 'STANDBY', className: 'standby-c'},
    STANDBY_D: {name: 'STANDBY_D', parentType: 'STANDBY', className: 'standby-d'},
    STANDBY_E: {name: 'STANDBY_E', parentType: 'STANDBY', className: 'standby-e'},
    STANDBY_F: {name: 'STANDBY_F', parentType: 'STANDBY', className: 'standby-f'},
    STANDBY_G: {name: 'STANDBY_G', parentType: 'STANDBY', className: 'standby-g'},
    STANDBY_H: {name: 'STANDBY_H', parentType: 'STANDBY', className: 'standby-h'},
    STANDBY_I: {name: 'STANDBY_I', parentType: 'STANDBY', className: 'standby-i'},
    STANDBY_J: {name: 'STANDBY_J', parentType: 'STANDBY', className: 'standby-j'},
    STANDBY_K: {name: 'STANDBY_K', parentType: 'STANDBY', className: 'standby-k'},
    STANDBY_L: {name: 'STANDBY_L', parentType: 'STANDBY', className: 'standby-l'},

    ATTACK: {name: 'ATTACK', parentType: 'ROOT', className: 'attack'},
    NORMAL_ATTACK: {name: 'NORMAL_ATTACK', parentType: 'ATTACK', className: 'normal_attack'},
    ABILITY: {name: 'ABILITY', parentType: 'ROOT', className: 'ability'},
    FIRST_ABILITY: {name: 'FIRST_ABILITY', parentType: 'ABILITY', className: 'first-ability'},
    SECOND_ABILITY: {name: 'SECOND_ABILITY', parentType: 'ABILITY', className: 'second-ability'},
    THIRD_ABILITY: {name: 'THIRD_ABILITY', parentType: 'ABILITY', className: 'third-ability'},
    FOURTH_ABILITY: {name: 'FOURTH_ABILITY', parentType: 'ABILITY', className: 'fourth-ability'},
    SUPPORT_ABILITY: {name: 'SUPPORT_ABILITY', parentType: 'ROOT', className: 'support-ability'},
    TRIGGERED_ABILITY: {name: 'TRIGGERED_ABILITY', parentType: 'SUPPORT_ABILITY', className: 'triggered-ability'},
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
    CHARGE_ATTACK_H: {name: 'CHARGE_ATTACK_H', parentType: 'CHARGE_ATTACK', className: 'charge-attack-h'},
    CHARGE_ATTACK_I: {name: 'CHARGE_ATTACK_I', parentType: 'CHARGE_ATTACK', className: 'charge-attack-i'},
    CHARGE_ATTACK_J: {name: 'CHARGE_ATTACK_J', parentType: 'CHARGE_ATTACK', className: 'charge-attack-j'},
    CHARGE_ATTACK_K: {name: 'CHARGE_ATTACK_K', parentType: 'CHARGE_ATTACK', className: 'charge-attack-k'},
    CHARGE_ATTACK_L: {name: 'CHARGE_ATTACK_L', parentType: 'CHARGE_ATTACK', className: 'charge-attack-l'},

    SUMMON: {name: 'SUMMON', parentType: 'ROOT', className: 'summon'},
    SUMMON_DEFAULT: {name: 'SUMMON_DEFAULT', parentType: 'SUMMON', className: 'summon-default'},
    FIRST_SUMMON: {name: 'FIRST_SUMMON', parentType: 'SUMMON', className: 'first-summon'},
    SECOND_SUMMON: {name: 'SECOND_SUMMON', parentType: 'SUMMON', className: 'second-summon'},
    THIRD_SUMMON: {name: 'THIRD_SUMMON', parentType: 'SUMMON', className: 'third-summon'},
    FOURTH_SUMMON: {name: 'FOURTH_SUMMON', parentType: 'SUMMON', className: 'fourth-summon'},
    FIFTH_SUMMON: {name: 'FIFTH_SUMMON', parentType: 'SUMMON', className: 'fifth-summon'},
    UNION_SUMMON: {name: 'UNION_SUMMON', parentType: 'SUMMON', className: 'union-summon'},

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
    CHARGE_ATTACK: {name: 'CHARGE_ATTACK', info: 'CT기', className: 'charge-attack'}, // CT기
    INCANT_ATTACK: {name: 'INCANT_ATTACK', info: '영창기', className: 'incant-attack'},
    HP_TRIGGER: {name: 'HP_TRIGGER', info: 'HP트리거', className: 'hp-trigger'},
}
OmenType.byName = function (name) {
    return Object.values(OmenType).find(type => type.name === name);
}
Object.freeze(OmenType);
