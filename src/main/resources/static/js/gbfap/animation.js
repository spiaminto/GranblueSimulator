// generic class to contain data for an animation
class Animation {
    constructor(name, obj) {
        this.name = name; // name (to be displayed in the version selector), will be playerId
        this.startMotion = obj.startMotion; // default motions
        this.isMainCharacter = obj.isMainCharacter ?? false; // if it uses the main character
        this.isEnemy = obj.isEnemy ?? false; // if it's an enemy
        this.weapon = obj.weapon ?? null; // the weapon id (if any). Must be paired with a main character
        this.cjs = obj.cjs; // main animation file
        this.additionalCjs = obj.additionalCjs ?? null;
        this.specials = obj.specials ?? []; // list of charge attack files
        this.additionalSpecials = obj.additionalSpecials ?? []
        this.summons = obj.summons ?? []; // list of summon files CHECK added, only 'attack' needed
        this.attack = obj.attack ?? null; // list of auto attack ("phit") files
        this.abilities = obj.abilities ?? []; // list of skill effect ("ab") files
        this.raidAppear = obj.raidAppear ?? []; // list of raid appear ("ra") files
        this.isChargeAttackSkip = obj.isChargeAttackSkip ?? false; // if character charge attack skip
        this.chargeAttackStartFrame = obj.chargeAttackStartFrame ?? -1; // charge attack start frame when skip true
    }

    get manifests() // return a list of file to download
    {
        let manifests = [this.cjs].concat(this.specials).concat(this.abilities).concat(this.raidAppear).concat(this.summons).concat(this.additionalSpecials);
        if (this.additionalCjs) manifests.push(this.additionalCjs);
        if (this.attack) manifests.push(this.attack);
        if (this.summons.length > 0) {
            manifests.push(...this.summons.map(summonAttack => summonAttack.replace('attack', 'damage'))); // add also _damage if the summon file ends with attack
        }
        return manifests;
    }
}