// generic class to contain data for an animation
class Animation {
    constructor(name, obj) {
        this.name = name; // name (to be displayed in the version selector), will be playerId
        this.startMotion = obj.startMotion; // default motions
        this.isLeaderCharacter = obj.isLeaderCharacter ?? false; // if it uses the main character
        this.isEnemy = obj.isEnemy ?? false; // if it's an enemy
        this.weapon = obj.weapon ?? null; // the weapon id (if any). Must be paired with a main character
        this.cjs = obj.cjs; // main animation file
        this.additionalCjs = obj.additionalCjs ?? null;
        this.specials = obj.specials ?? []; // list of charge attack files
        this.additionalSpecials = obj.additionalSpecials ?? []
        this.summons = {};
        Object.entries(obj.summons).forEach(([key, value]) => {
            this.summons[key] = value;
        })
        this.attacks = obj.attacks ?? []; // list of auto attack ("phit") files
        this.abilities = this.getBaseAbilities();
        Object.entries(obj.abilities).forEach(([key, value]) => {
            this.abilities[key] = value;
        }); // list of skill effect ("ab") files
        this.raidAppear = obj.raidAppear ?? []; // list of raid appear ("ra") files
        this.isChargeAttackSkip = obj.isChargeAttackSkip ?? false; // if character charge attack skip
        this.chargeAttackStartFrame = obj.chargeAttackStartFrame ?? -1; // charge attack start frame when skip true
        this.currentOrder = obj.currentOrder; // null 시 actor 에서 처리
    }

    get manifests() // return a list of file to download
    {
        let manifests = [this.cjs].concat(this.attacks).concat(this.specials).concat(this.raidAppear).concat(this.additionalSpecials);
        if (this.abilities) manifests.push(...Object.values(this.abilities).map(ability => ability.cjs));
        if (this.additionalCjs) manifests.push(this.additionalCjs);
        if (this.summons) {
            manifests.push(...Object.values(this.summons).map(summonCjsName => summonCjsName + "_attack"));
            manifests.push(...Object.values(this.summons).map(summonCjsName => summonCjsName + "_damage"));
        }
        return manifests;
    }

    getBaseAbilities() {
        return {
            [BASE_ABILITY.RAID_BUFF]: {
                cjs: 'raid_effect_buff',
                isTargetedEnemy: true
            },
            [BASE_ABILITY.RAID_DEBUFF]: {
                cjs: 'raid_effect_debuff',
                isTargetedEnemy: true
            },
            [BASE_ABILITY.RAID_HEAL]: {
                cjs: 'raid_effect_heal',
                isTargetedEnemy: false
            },
            [BASE_ABILITY.RAID_ATK_UP_WATER]: {
                cjs: "raid_effect_atk_up_02",
                isTargetedEnemy: false
            },
            [BASE_ABILITY.AB_START]: {
                cjs: 'ab_start',
                isTargetedEnemy: false
            },
            [BASE_ABILITY.HEAL]: {
                cjs: 'ab_3000',
                isTargetedEnemy: false
            },
            [BASE_ABILITY.ENEMY_POWER_UP]: {
                cjs: 'ab_powerup',
                isTargetedEnemy: false
            },
            [BASE_ABILITY.ENEMY_AB_START]: {
                cjs: "ab_enemy_action",
                isTargetedEnemy: false
            },
            [BASE_ABILITY.ENEMY_CT_MAX]: {
                cjs: "ab_charge_max",
                isTargetedEnemy: false
            },
            [BASE_ABILITY.DISPEL]: {
                cjs: "ab_3100",
                isTargetedEnemy: false
            },
            [BASE_ABILITY.ENEMY_RELEASE_POWER]: {
                cjs: "ab_enemy_7300293_01",
                isTargetedEnemy: false
            },
            [BASE_ABILITY.ATTACK_SEALED]: {
              cjs: "ab_3040351000_02",
                isTargetedEnemy: false
            },
            EF: {
                cjs: "ef_0080",
                isTargetedEnemy: false
            },
        };
    }
}

const BASE_ABILITY = {
    AB_START: 'AB_START',

    RAID_BUFF: 'RAID_BUFF',
    RAID_DEBUFF: 'RAID_DEBUFF',

    RAID_HEAL: 'RAID_HEAL',
    HEAL: 'HEAL',

    RAID_ATK_UP_WATER: 'RAID_ATK_UP_WATER',

    DISPEL: 'DISPEL',
    CLEAR: 'CLEAR', // ab_0004.js

    REFLECT: 'REFLECT', // ab_0090.js
    // ef_0080.png

    ENEMY_POWER_UP: 'ENEMY_POWER_UP',
    ENEMY_AB_START: 'ENEMY_AB_START',
    ENEMY_CT_MAX: 'ENEMY_CT_MAX',
    ENEMY_RELEASE_POWER: 'ENEMY_RELEASE_POWER',


    ATTACK_SEALED: 'ATTACK_SEALED',
};
Object.freeze(BASE_ABILITY);