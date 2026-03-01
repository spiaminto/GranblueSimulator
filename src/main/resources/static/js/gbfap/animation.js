// generic class to contain data for an animation
class Animation {
    constructor(name, obj) {
        this.name = name; // name (to be displayed in the version selector), will be playerId
        this.isLeaderCharacter = obj.isLeaderCharacter ?? false; // if it uses the main character
        this.isEnemy = obj.isEnemy ?? false; // if it's an enemy
        this.weapon = obj.weapon ?? null; // the weapon id (if any). Must be paired with a main character
        this.cjs = obj.cjs; // main animation file
        this.additionalCjs = obj.additionalCjs ?? null;
        this.additionalSpecials = obj.additionalSpecials ?? []

        this.specials = []; // list of charge attack files
        Object.entries(obj.specials).forEach(([key, value]) => {
            this.specials.push(value);
        })

        this.summons = {};
        Object.entries(obj.summons).forEach(([key, value]) => {
            // this.summons[key] = value;
        })

        this.attacks = obj.attacks ?? []; // list of auto attack ("phit") files

        this.abilities = {...BASE_ABILITY};
        Object.entries(obj.abilities).forEach(([key, value]) => {
            this.abilities[key] = value;
        }); // list of skill effect ("ab") files

        this.raidAppear = obj.raidAppear ?? []; // list of raid appear ("ra") files
        this.isChargeAttackSkip = obj.isChargeAttackSkip ?? false; // if character charge attack skip
        this.chargeAttackStartFrame = obj.chargeAttackStartFrame ?? -1; // charge attack start frame when skip true
        this.currentOrder = obj.currentOrder; // null 시 actor 에서 처리
        this.windowEffects = obj.windowEffects ?? {};
    }

    get manifests() // return a list of file to download
    {
        let manifests = [this.cjs].concat(this.attacks).concat(this.raidAppear).concat(this.additionalSpecials);
        manifests.push(...this.specials.map(obj => obj.cjs));
        if (this.abilities) manifests.push(...this.extractAbilityCjs());
        if (this.additionalCjs) manifests.push(this.additionalCjs);
        if (this.summons) {
            manifests.push(...Object.values(this.summons).map(summonCjsName => summonCjsName + "_attack"));
            manifests.push(...Object.values(this.summons).map(summonCjsName => summonCjsName + "_damage"));
        }
        return manifests;
    }

    /**
     * depth 2 이상이면 안됨
     * @return []
     */
    extractAbilityCjs () {
        if (!this.abilities) return [];
        const extractedCjs = Object.values(this.abilities).flatMap(item => {
            if (item.cjs) return [item.cjs];
            if (typeof item === 'object') { // BASE_ABILITY.UI
                return Object.values(item)
                    .map(sub => sub.cjs)
                    .filter(cjs => cjs);
            }
            return []; // none
        });
        // console.log('extractedCjs = ', extractedCjs)
        return extractedCjs;

    }
}

const BASE_ABILITY = {
    RAID_BUFF: {
        name: 'RAID_BUFF',
        cjs: 'raid_effect_buff',
        isTargetedEnemy: true
    },
    RAID_DEBUFF: {
        name: 'RAID_DEBUFF',
        cjs: 'raid_effect_debuff',
        isTargetedEnemy: true
    },
    AB_START: {
        name: 'AB_START',
        cjs: 'ab_start',
        isTargetedEnemy: false
    },
    HEAL: {
        name: 'HEAL',
        cjs: 'ab_3000',
        isTargetedEnemy: false
    },

    ENEMY_POWER_UP: {
        name: 'ENEMY_POWER_UP',
        cjs: 'ab_powerup',
        isTargetedEnemy: false
    },

    ENEMY_AB_START: {
        name: 'ENEMY_AB_START',
        cjs: "ab_enemy_action",
        isTargetedEnemy: false
    },
    ENEMY_CT_MAX: {
        name: 'ENEMY_CT_MAX',
        cjs: "ab_charge_max",
        isTargetedEnemy: false
    },
    DISPEL: {
        name: 'DISPEL',
        cjs: "ab_3100",
        isTargetedEnemy: false
    },
    ENEMY_RELEASE_POWER: {
        name: 'ENEMY_RELEASE_POWER',
        cjs: "ab_enemy_7300293_01",
        isTargetedEnemy: false
    },
    STRIKE_SEALED: {
        name: 'STRIKE_SEALED',
        cjs: "ab_3040351000_02",
        isTargetedEnemy: false
    },
    EF: {
        name: 'EF',
        cjs: "ef_0080",
        isTargetedEnemy: false
    },
    BUFF_FOR_ALL: {
        name: 'BUFF_FOR_ALL',
        cjs: 'summon_2040216000_01_damage',
        isTargetedEnemy: false
    },

    UI: {
        QUEST_CLEAR: {
            name: 'QUEST_CLEAR',
            cjs: 'quest_clear',
            isTargetedEnemy: false
        },
        QUEST_FAILED: {
            name: 'QUEST_FAILED',
            cjs: 'quest_failed',
            isTargetedEnemy: false
        },
        UNION_SUMMON_CUTIN: {
            name: 'UNION_SUMMON_CUTIN',
            cjs: 'raid_union_summon',
            isTargetedEnemy: false
        }
    },

    CLEAR: {
        name: 'CLEAR',
        cjs: 'ab_0004',
        isTargetedEnemy: false
    },

    REFLECT: {
        name: 'REFLECT',
        cjs: 'ab_0090',
        isTargetedEnemy: false
    },

    DAMAGE_INEFFECTIVE: {
        name: 'DAMAGE_INEFFECTIVE',
        cjs: 'ab_0080',
        isTargetedEnemy: false
    },

    COUNTER: {
        name: 'COUNTER',
        cjs: 'ab_3030224000_02',
        isTargetedEnemy: false
    },

    HEAL_FORCED: {
        name: 'HEAL_FORCED',
        cjs: 'ab_3040',
        isTargetedEnemy: false
    },

    HEAL_AND_CLEAR: {
        name: 'HEAL_AND_CLEAR',
        cjs: 'ab_3040098000_05',
        isTargetedEnemy: false
    },

    EF_2: {
        name: 'EF_2',
        cjs: 'ab_0050',
        isTargetedEnemy: false
    },

    DAMAGE_CUT: {
        name: 'DAMAGE_CUT',
        cjs: 'ab_0100',
        isTargetedEnemy: false
    },


    RAID_HEAL: 'RAID_HEAL',
    // // ef_0080.png
    // CLEAR: 'CLEAR', // ab_0004.js
    // REFLECT: 'REFLECT', // ab_0090.js
    // HEAL_FORCED: 'HEAL_FORCED', // ab_3040
    // EF_2: 'EF_2', // ab_0050
    // DAMAGE_CUT: 'DAMAGE_CUT', // ab_0100
    // CLEAR_HEAL: 'CLEAR_HEAL', // ab_3040098000_05

    // heal legacy?
    EF_1: {
        name: 'EF_1',
        cjs: 'ab_3030',
        isTargetedEnemy: false
    },

    EF_11: {
        name: 'EF_11',
        cjs: 'ab_3010',
        isTargetedEnemy: false
    },

};


Object.freeze(BASE_ABILITY);