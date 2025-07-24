// function to create the player instance and initialize the html and stage
function initPlayer() {
    if (player != null) {
        console.debug('[initPlayer] player loaded, skip init')
    } else {
        player = new Player();
        player.ui.set_html();
        player.initStage();
        player.setSize(640, 640);
    }
}

// the animation player
class Player {

    constructor() {
        this.initAttributes();
        this.ui = new PlayerUI(this); // the HTML ui is separated in another instance
    }

    initAttributes() {

        // player size
        this.width = Player.c_gbf_animation_width;
        this.height = Player.c_gbf_animation_width;

        // player state
        this.playbackRate = 1.0; // speed
        this.paused = true; // 처음에 true 여야함, resume 에서 false 로 바꿈
        this.audioEnabled = false; // audio mute state
        this.audioVolume = 1.0;

        // the createjs stage
        this.m_stage = null;
        this.actors = new Map();
    }

    // create the stage and set the ticker framerate
    initStage() {
        if (!this.ui.m_canvas) throw new Error("No canvas initialized");
        if (cjsStage) throw new Error("global cjsStage has been initialized already.");

        this.m_stage = new createjs.Stage(this.ui.m_canvas);
        createjs.Ticker.timingMode = createjs.Ticker.RAF_SYNCHED;
        createjs.Ticker.framerate = 30.3; // original : 30.303030303030305
        createjs.Ticker.addEventListener("tick", this.m_stage);
        window.cjsStage = this.m_stage;

        // 그랑블루 사양에 따른 일부 변수추가할당
        // 페이탈체인 용
        window.stage = {};
        window.stage.global = {};
        window.stage.global.is_pair_chain = false; // 보이스 재생용인듯. 잇는캐릭 잇고 없는 캐릭 잇어서 비활성화
    }

    // change the player size
    setSize(w, h, scaling = 1.0) {
        if (this.width) {
            console.debug("[setSize Player size already set, skip setSize");
            return;
        }
        if (w > Player.c_canvas_size || h > Player.c_canvas_size)
            throw new Error("Player size can't be greater than " + Player.c_canvas_size);

        this.m_width = w;
        this.m_height = h;
        // set visible player size
        // this.ui.set_canvas_container_size(w, h);
    }

    /**
     * playReqeust 객체 만들어 반환
     * @param actorId
     * @param motion
     * @param index optional, 없으면 -1 (0부터 시작)
     * @returns {{actorId, motion, index: number}}
     */
    static playRequest(actorId, motion, index = -1) {
        return {
            actorId: actorId,
            motion: motion,
            index: index ?? -1 // additional index for ability and summons start with 0
        }
    }

    /**
     * 한 액터의 모션 시퀀스 재생
     * @param playRequests
     * @param synced
     */
    async playMotions(playRequests, synced = false) {
        let actorIds = new Set(playRequests.map(playRequest => playRequest.actorId));
        for (let actorId of actorIds) {
            let actor = this.actors.get(actorId);
            actor.playMotions(playRequests, synced);
        }
    }

    /**
     * 두 액터의 playReqeusts 를 첫번째 액터의 이펙트에 맞춰 재생
     * @param playRequests
     * @param paringPlayRequests
     */
    playMotionsWithPairing(playRequests, paringPlayRequests) {
        if (playRequests.length !== paringPlayRequests.length) throw new Error("playRequests length must be same");
        this.playMotions(paringPlayRequests, true); // 이쪽은 미리 synced 이벤트 처리
        this.playMotions(playRequests); // 실 재생을 나중에
    }


    /**
     * set playbackRate (speed)
     * @param playbackRate float, 0.1 ~ 3
     */
    setPlaybackRate(playbackRate) {
        // set value
        this.playbackRate = parseFloat(speed);
        // apply value to audio and player framerate
        if (window.audio)
            window.audio.set_playback_speed(this.playbackRate);
        createjs.Ticker.framerate = 30 * this.playbackRate;
        beep();
    }

    // audio toggle button
    /**
     * set audio enabled
     * @param enabled boolean
     */
    setAudioEnabled(enabled) {
        let changed = this.audioEnabled !== enabled;
        this.audioEnabled = enabled;
        if (window.audio && changed)
            window.audio.update_mute();
        beep();
    }

    /**
     * set audio volume
     * @param audioVolume float, 0 ~ 2.0
     */
    setAudioVolume(audioVolume) {
        if (audioVolume > 2.0) alert("max audio volume is 2.0");
        this.audioVolume = parseFloat(audioVolume);
        if (window.audio) {
            // set volume
            window.audio.set_master_volume(this.audioVolume);
        }
    }


// constants ================================================================================================
    static c_canvas_size = Object.freeze(900); // canvas size, width and height
    static c_gbf_animation_width = Object.freeze(640); // GBF animation width
    static c_enemy_shift = Object.freeze({ // constant to shift enemies around
        x: 71,
        y: 117
    });
// enum, z_index shorthand
    static c_zindex = Object.freeze({
        BOTTOM: 0,
        MIDDLE: 4, // 1
        TOP: 5 // 2
    });

// constant associated with in-game motion names.
// the list is non exhaustive.
// constant associated with in-game motion names.
// the list is non exhaustive.
    static c_animations = Object.freeze({
        // special ones added for the player innerworkings
        // for summon
        SUMMON_ATTACK: "summon_atk",
        SUMMON_DAMAGE: "summon_dmg",
        // for boss appear animations
        RAID_APPEAR_0: "raid_appear_0",
        RAID_APPEAR_1: "raid_appear_1",
        RAID_APPEAR_2: "raid_appear_2",
        RAID_APPEAR_3: "raid_appear_3",
        RAID_APPEAR_4: "raid_appear_4",
        RAID_APPEAR_5: "raid_appear_5",
        RAID_APPEAR_6: "raid_appear_6",
        RAID_APPEAR_7: "raid_appear_7",
        RAID_APPEAR_8: "raid_appear_8",
        RAID_APPEAR_9: "raid_appear_9",
        // GBF ones
        WAIT: "wait",
        WAIT_2: "wait_2",
        WAIT_3: "wait_3",
        TO_STB_WAIT: "setup",
        STB_WAIT: "stbwait",
        STB_WAIT_ADV: "stbwait_adv",
        CHARA_SELECT: "chara_select",
        CHARA_IN: "chara_in",
        CHARA_OUT: "chara_out",
        CHARGE: "charge",
        ABILITY: "ability",
        ABILITY_WAIT: "ability_wait",
        MORTAL: "mortal",
        MORTAL_A: "mortal_A",
        MORTAL_A_1: "mortal_A_1",
        MORTAL_A_2: "mortal_A_2",
        MORTAL_B: "mortal_B",
        MORTAL_B_1: "mortal_B_1",
        MORTAL_B_2: "mortal_B_2",
        MORTAL_C: "mortal_C",
        MORTAL_C_1: "mortal_C_1",
        MORTAL_C_2: "mortal_C_2",
        MORTAL_D: "mortal_D",
        MORTAL_D_1: "mortal_D_1",
        MORTAL_D_2: "mortal_D_2",
        MORTAL_E: "mortal_E",
        MORTAL_E_1: "mortal_E_1",
        MORTAL_E_2: "mortal_E_2",
        MORTAL_F: "mortal_F",
        MORTAL_F_1: "mortal_F_1",
        MORTAL_F_2: "mortal_F_2",
        MORTAL_G: "mortal_G",
        MORTAL_G_1: "mortal_G_1",
        MORTAL_G_2: "mortal_G_2",
        MORTAL_H: "mortal_H",
        MORTAL_H_1: "mortal_H_1",
        MORTAL_H_2: "mortal_H_2",
        MORTAL_I: "mortal_I",
        MORTAL_I_1: "mortal_I_1",
        MORTAL_I_2: "mortal_I_2",
        MORTAL_J: "mortal_J",
        MORTAL_J_1: "mortal_J_1",
        MORTAL_J_2: "mortal_J_2",
        MORTAL_K: "mortal_K",
        MORTAL_K_1: "mortal_K_1",
        MORTAL_K_2: "mortal_K_2",
        ATTACK: "attack",
        ATTACK_SHORT: "short_attack",
        ATTACK_SHORT_ADV: "short_attack_adv",
        ATTACK_DOUBLE: "double",
        ATTACK_TRIPLE: "triple",
        ATTACK_QUADRUPLE: "quadruple",
        SPECIAL_ATTACK: "attack_2",
        ENEMY_ATTACK: "attack_3",
        CHANGE: "change",
        CHANGE_TO: "change_1",
        CHANGE_FROM: "change_2",
        CHANGE_TO_2: "change_1_2",
        CHANGE_FROM_2: "change_2_2",
        DEAD: "dead",
        DEAD_2: "dead_2",
        DAMAGE: "damage",
        DAMAGE_1: "damage_1",
        DAMAGE_2: "damage_2",
        DAMAGE_3: "damage_3",
        DAMAGE_4: "damage_4",
        DAMAGE_5: "damage_5",
        WIN: "win",
        WIN1: "win1",
        WIN2: "win2",
        WIN_1: "win_1",
        WIN_2: "win_2",
        WIN_3: "win_3",
        INVISIBLE: "invisible",
        HIDE: "hide",
        DOWN: "down",
        WAIT_SPECIAL: "pf",
        WAIT_SPECIAL_1: "pf_1",
        WAIT_SPECIAL_2: "pf_2",
        WAIT_SPECIAL_3: "pf_3",
        WAIT_SPECIAL_4: "pf_4",
        WAIT_SPECIAL_5: "pf_5",
        MISS: "miss",
        SUMMON: "summon",
        ABILITY_MOTION_OLD: "attack_noeffect",
        ABILITY_MOTION: "ab_motion",
        ABILITY_MOTION_2: "ab_motion_2",
        ABILITY_MOTION_3: "ab_motion_3",
        ABILITY_MOTION_4: "ab_motion_4",
        VS_MOTION_1: "vs_motion_1",
        VS_MOTION_2: "vs_motion_2",
        VS_MOTION_3: "vs_motion_3",
        VS_MOTION_4: "vs_motion_4",
        VS_MOTION_5: "vs_motion_5",
        VS_MOTION_6: "vs_motion_6",
        ENEMY_PHASE_1: "setin",
        ENEMY_PHASE_2: "setin_2",
        ENEMY_PHASE_3: "setin_3",
        ENEMY_PHASE_4: "setin_4",
        ENEMY_PHASE_5: "setin_5",
        ENEMY_FORM_CHANGE: "form_change",
        ENEMY_STANDBY_A: "standby_A",
        ENEMY_STANDBY_B: "standby_B",
        ENEMY_STANDBY_C: "standby_C",
        ENEMY_STANDBY_D: "standby_D",
        ENEMY_BREAK_STANDBY_A: "break_standby_A",
        ENEMY_BREAK_STANDBY_B: "break_standby_B",
        ENEMY_BREAK_STANDBY_C: "break_standby_C",
        ENEMY_BREAK_STANDBY_D: "break_standby_D",
        ENEMY_DAMAGE_STANDBY_A: "damage_standby_A",
        ENEMY_DAMAGE_STANDBY_B: "damage_standby_B",
        ENEMY_DAMAGE_STANDBY_C: "damage_standby_C",
        ENEMY_DAMAGE_STANDBY_D: "damage_standby_D",
        LINK_PHASE_1: "setin_link",
        LINK_PHASE_1_2: "setin_link_2",
        LINK_PHASE_1_F2: "setin_link_f2",
        LINK_PHASE_1_F2_2: "setin_link_f2_2",
        LINK_PHASE_2: "setin_2_link",
        LINK_PHASE_2_2: "setin_2_link_2",
        LINK_PHASE_2_F2: "setin_2_link_f2",
        LINK_PHASE_2_F2_2: "setin_2_link_f2_2",
        LINK_PHASE_3: "setin_3_link",
        LINK_PHASE_3_2: "setin_3_link_2",
        LINK_PHASE_3_F2: "setin_3_link_f2",
        LINK_PHASE_3_F2_2: "setin_3_link_f2_2",
        LINK_PHASE_4: "setin_4_link",
        LINK_PHASE_4_2: "setin_4_link_2",
        LINK_PHASE_4_F2: "setin_4_link_f2",
        LINK_PHASE_4_F2_2: "setin_4_link_f2_2",
        LINK_PHASE_5: "setin_5_link",
        LINK_PHASE_5_2: "setin_5_link_2",
        LINK_PHASE_5_F2: "setin_5_link_f2",
        LINK_PHASE_5_F2_2: "setin_5_link_f2_2",
        LINK_DAMAGE: "damage_link",
        LINK_DAMAGE_2: "damage_link_2",
        LINK_DEAD: "dead_link",
        LINK_DEAD_1: "dead_1_link",
        LINK_DEAD_2: "dead_2_link",
        LINK_DEAD_3: "dead_3_link",
        LINK_DEAD_A: "dead_link_1",
        LINK_DEAD_B: "dead_link_2",
        LINK_DEAD_C: "dead_link_3",
        LINK_MORTAL_A: "mortal_A_link",
        LINK_MORTAL_A_2: "mortal_A_link_2",
        LINK_MORTAL_A_F2: "mortal_A_link_f2",
        LINK_MORTAL_A_F2_2: "mortal_A_link_f2_2",
        LINK_MORTAL_B: "mortal_B_link",
        LINK_MORTAL_B_2: "mortal_B_link_2",
        LINK_MORTAL_B_F2: "mortal_B_link_f2",
        LINK_MORTAL_B_F2_2: "mortal_B_link_f2_2",
        LINK_MORTAL_C: "mortal_C_link",
        LINK_MORTAL_C_2: "mortal_C_link_2",
        LINK_MORTAL_C_F2: "mortal_C_link_f2",
        LINK_MORTAL_C_F2_2: "mortal_C_link_f2_2",
        LINK_MORTAL_D: "mortal_D_link",
        LINK_MORTAL_D_2: "mortal_D_link_2",
        LINK_MORTAL_D_F2: "mortal_D_link_f2",
        LINK_MORTAL_D_F2_2: "mortal_D_link_f2_2",
        LINK_MORTAL_E: "mortal_E_link",
        LINK_MORTAL_E_2: "mortal_E_link_2",
        LINK_MORTAL_E_F2: "mortal_E_link_f2",
        LINK_MORTAL_E_F2_2: "mortal_E_link_f2_2",
        LINK_MORTAL_F: "mortal_F_link",
        LINK_MORTAL_F_2: "mortal_F_link_2",
        LINK_MORTAL_F_F2: "mortal_F_link_f2",
        LINK_MORTAL_F_F2_2: "mortal_F_link_f2_2",
        LINK_MORTAL_G: "mortal_G_link",
        LINK_MORTAL_G_2: "mortal_G_link_2",
        LINK_MORTAL_G_F2: "mortal_G_link_f2",
        LINK_MORTAL_G_F2_2: "mortal_G_link_f2_2",
        LINK_MORTAL_H: "mortal_H_link",
        LINK_MORTAL_H_2: "mortal_H_link_2",
        LINK_MORTAL_H_F2: "mortal_H_link_f2",
        LINK_MORTAL_H_F2_2: "mortal_H_link_f2_2",
        LINK_MORTAL_I: "mortal_I_link",
        LINK_MORTAL_I_2: "mortal_I_link_2",
        LINK_MORTAL_I_F2: "mortal_I_link_f2",
        LINK_MORTAL_I_F2_2: "mortal_I_link_f2_2",
        LINK_MORTAL_J: "mortal_J_link",
        LINK_MORTAL_J_2: "mortal_J_link_2",
        LINK_MORTAL_J_F2: "mortal_J_link_f2",
        LINK_MORTAL_J_F2_2: "mortal_J_link_f2_2",
        LINK_MORTAL_K: "mortal_K_link",
        LINK_MORTAL_K_2: "mortal_K_link_2",
        LINK_MORTAL_K_F2: "mortal_K_link_f2",
        LINK_MORTAL_K_F2_2: "mortal_K_link_f2_2",
        LINK_ATTACK: "attack_link",
        LINK_ATTACK_2: "attack_link_2",
        LINK_ATTACK_F2: "attack_link_f2",
        LINK_ATTACK_F2_2: "attack_link_f2_2",
        LINK_FORM_CHANGE: "form_change_link",
        LINK_FORM_CHANGE_2: "form_change_link_2",
        MY_PAGE: "mypage",

        isSummoning(motion) {
            return [
                Player.c_animations.SUMMON_ATTACK,
                Player.c_animations.SUMMON_DAMAGE,
            ].includes(motion);
        },

        isRaidAppear(motion) {
            return [
                Player.c_animations.RAID_APPEAR_0,
                Player.c_animations.RAID_APPEAR_1,
                Player.c_animations.RAID_APPEAR_2,
                Player.c_animations.RAID_APPEAR_3,
                Player.c_animations.RAID_APPEAR_4,
                Player.c_animations.RAID_APPEAR_5,
                Player.c_animations.RAID_APPEAR_6,
                Player.c_animations.RAID_APPEAR_7,
                Player.c_animations.RAID_APPEAR_8,
                Player.c_animations.RAID_APPEAR_9
            ].includes(motion);
        },

        isMortal(motion) { // Charge attacks / specials
            return [
                'additional_mortal_A',
                'additional_mortal_B',
                'additional_mortal_C',
                'additional_mortal_D',
                Player.c_animations.MORTAL,
                Player.c_animations.MORTAL_A,
                Player.c_animations.MORTAL_A_1,
                Player.c_animations.MORTAL_A_2,
                Player.c_animations.MORTAL_B,
                Player.c_animations.MORTAL_B_1,
                Player.c_animations.MORTAL_B_2,
                Player.c_animations.MORTAL_C,
                Player.c_animations.MORTAL_C_1,
                Player.c_animations.MORTAL_C_2,
                Player.c_animations.MORTAL_D,
                Player.c_animations.MORTAL_D_1,
                Player.c_animations.MORTAL_D_2,
                Player.c_animations.MORTAL_E,
                Player.c_animations.MORTAL_E_1,
                Player.c_animations.MORTAL_E_2,
                Player.c_animations.MORTAL_F,
                Player.c_animations.MORTAL_F_1,
                Player.c_animations.MORTAL_F_2,
                Player.c_animations.MORTAL_G,
                Player.c_animations.MORTAL_G_1,
                Player.c_animations.MORTAL_G_2,
                Player.c_animations.MORTAL_H,
                Player.c_animations.MORTAL_H_1,
                Player.c_animations.MORTAL_H_2,
                Player.c_animations.MORTAL_I,
                Player.c_animations.MORTAL_I_1,
                Player.c_animations.MORTAL_I_2,
                Player.c_animations.MORTAL_J,
                Player.c_animations.MORTAL_J_1,
                Player.c_animations.MORTAL_J_2,
                Player.c_animations.MORTAL_K,
                Player.c_animations.MORTAL_K_1,
                Player.c_animations.MORTAL_K_2
            ].includes(motion);
        },

        isAttack(motion) { // auto attack animation
            switch (motion) {
                case Player.c_animations.ATTACK:
                case Player.c_animations.ATTACK_SHORT:
                case Player.c_animations.ATTACK_SHORT_ADV:
                case Player.c_animations.ATTACK_DOUBLE:
                case Player.c_animations.ATTACK_TRIPLE:
                case Player.c_animations.ATTACK_QUADRUPLE:
                case Player.c_animations.SPECIAL_ATTACK:
                case Player.c_animations.ENEMY_ATTACK:
                    return true;
                default:
                    return false;
            }
        },

        isFormChange(motion) { // form change
            switch (motion) {
                case Player.c_animations.CHANGE:
                case Player.c_animations.CHANGE_FROM:
                case Player.c_animations.CHANGE_FROM_2:
                    return true;
                default:
                    return false;
            }
        },

        isAbilityMotion(motion) { // skill / ability use
            switch (motion) {
                case Player.c_animations.ABILITY_MOTION:
                case Player.c_animations.ABILITY_MOTION_2:
                case Player.c_animations.ABILITY_MOTION_3:
                case Player.c_animations.ABILITY_MOTION_4:
                case Player.c_animations.VS_MOTION_1:
                case Player.c_animations.VS_MOTION_2:
                case Player.c_animations.VS_MOTION_3:
                case Player.c_animations.VS_MOTION_4:
                case Player.c_animations.VS_MOTION_5:
                case Player.c_animations.VS_MOTION_6:
                    return true;
                default:
                    return false;
            }
        },


    });

// translate animation to more humanly readable names.
// Unofficial/Made up and Non exhaustive.
    translateMotion(motion) {
        switch (motion) {
            // specials
            case Player.c_animations.SUMMON_ATTACK:
                return "Summon Call";
            case Player.c_animations.SUMMON_DAMAGE:
                return "Summon Damage";
            case Player.c_animations.RAID_APPEAR_0:
                return "Appear";
            case Player.c_animations.RAID_APPEAR_1:
                return "Appear A";
            case Player.c_animations.RAID_APPEAR_2:
                return "Appear B";
            case Player.c_animations.RAID_APPEAR_3:
                return "Appear C";
            case Player.c_animations.RAID_APPEAR_4:
                return "Appear D";
            case Player.c_animations.RAID_APPEAR_5:
                return "Appear E";
            case Player.c_animations.RAID_APPEAR_6:
                return "Appear F";
            case Player.c_animations.RAID_APPEAR_7:
                return "Appear G";
            case Player.c_animations.RAID_APPEAR_8:
                return "Appear H";
            case Player.c_animations.RAID_APPEAR_9:
                return "Appear I";
            // game
            case Player.c_animations.WAIT:
                return "Idle";
            case Player.c_animations.WAIT_2:
                return "Idle (Overdrive)";
            case Player.c_animations.WAIT_3:
                return "Idle (Break)";
            case Player.c_animations.TO_STB_WAIT:
                return "Weapon Drew";
            case Player.c_animations.STB_WAIT:
                return "Wpn. Drew (Idle)";
            case Player.c_animations.STB_WAIT_ADV:
                return "Wpn. Drew (Idle)(Adv)";
            case Player.c_animations.CHARA_SELECT:
                return "Selection";
            case Player.c_animations.CHARA_IN:
                return "Fade In";
            case Player.c_animations.CHARA_OUT:
                return "Fade Out";
            case Player.c_animations.CHARGE:
                return "Charged";
            case Player.c_animations.ABILITY:
                return "C.A. Charged";
            case Player.c_animations.ABILITY_WAIT:
                return "Skill (Wait)";
            case Player.c_animations.MORTAL:
                return "Charge Attack";
            case Player.c_animations.MORTAL_A:
                return "Charge Attack A";
            case Player.c_animations.MORTAL_A_1:
                return "Charge Attack A1";
            case Player.c_animations.MORTAL_A_2:
                return "Charge Attack A2";
            case Player.c_animations.MORTAL_B:
                return "Charge Attack B";
            case Player.c_animations.MORTAL_B_1:
                return "Charge Attack B1";
            case Player.c_animations.MORTAL_B_2:
                return "Charge Attack B2";
            case Player.c_animations.MORTAL_C:
                return "Charge Attack C";
            case Player.c_animations.MORTAL_C_1:
                return "Charge Attack C1";
            case Player.c_animations.MORTAL_C_2:
                return "Charge Attack C2";
            case Player.c_animations.MORTAL_D:
                return "Charge Attack D";
            case Player.c_animations.MORTAL_D_1:
                return "Charge Attack D1";
            case Player.c_animations.MORTAL_D_2:
                return "Charge Attack D2";
            case Player.c_animations.MORTAL_E:
                return "Charge Attack E";
            case Player.c_animations.MORTAL_E_1:
                return "Charge Attack E1";
            case Player.c_animations.MORTAL_E_2:
                return "Charge Attack E2";
            case Player.c_animations.MORTAL_F:
                return "Charge Attack F";
            case Player.c_animations.MORTAL_F_1:
                return "Charge Attack F1";
            case Player.c_animations.MORTAL_F_2:
                return "Charge Attack F2";
            case Player.c_animations.MORTAL_G:
                return "Charge Attack G";
            case Player.c_animations.MORTAL_G_1:
                return "Charge Attack G1";
            case Player.c_animations.MORTAL_G_2:
                return "Charge Attack G2";
            case Player.c_animations.MORTAL_H:
                return "Charge Attack H";
            case Player.c_animations.MORTAL_H_1:
                return "Charge Attack H1";
            case Player.c_animations.MORTAL_H_2:
                return "Charge Attack H2";
            case Player.c_animations.MORTAL_I:
                return "Charge Attack I";
            case Player.c_animations.MORTAL_I_1:
                return "Charge Attack I1";
            case Player.c_animations.MORTAL_I_2:
                return "Charge Attack I2";
            case Player.c_animations.MORTAL_J:
                return "Charge Attack J";
            case Player.c_animations.MORTAL_J_1:
                return "Charge Attack J1";
            case Player.c_animations.MORTAL_J_2:
                return "Charge Attack J2";
            case Player.c_animations.MORTAL_K:
                return "Charge Attack K";
            case Player.c_animations.MORTAL_K_1:
                return "Charge Attack K1";
            case Player.c_animations.MORTAL_K_2:
                return "Charge Attack K2";
            case Player.c_animations.ATTACK:
                return "Attack";
            case Player.c_animations.ATTACK_SHORT:
                return "Attack 1";
            case Player.c_animations.ATTACK_SHORT_ADV:
                return "Attack 1 (Adv)";
            case Player.c_animations.ATTACK_DOUBLE:
                return "Attack 2";
            case Player.c_animations.ATTACK_TRIPLE:
                return "Attack 3";
            case Player.c_animations.ATTACK_QUADRUPLE:
                return "Attack 4";
            case Player.c_animations.SPECIAL_ATTACK:
                return "Attack B (Alt/OD)";
            case Player.c_animations.ENEMY_ATTACK:
                return "Attack C (Break)";
            case Player.c_animations.CHANGE:
                return "Change Form";
            case Player.c_animations.CHANGE_TO:
                return "Change Form 1";
            case Player.c_animations.CHANGE_FROM:
                return "Change Form 2";
            case Player.c_animations.CHANGE_TO_2:
                return "Change Form 3";
            case Player.c_animations.CHANGE_FROM_2:
                return "Change Form 4";
            case Player.c_animations.DEAD:
                return "Dead";
            case Player.c_animations.DEAD_1:
                return "Dead 1";
            case Player.c_animations.DEAD_2:
                return "Dead 2";
            case Player.c_animations.DAMAGE:
                return "Damaged";
            case Player.c_animations.DAMAGE_1:
                return "Damaged A";
            case Player.c_animations.DAMAGE_2:
                return "Damaged B (OD)";
            case Player.c_animations.DAMAGE_3:
                return "Damaged C (Break)";
            case Player.c_animations.DAMAGE_4:
                return "Damaged D";
            case Player.c_animations.DAMAGE_5:
                return "Damaged E";
            case Player.c_animations.WIN:
                return "Win";
            case Player.c_animations.WIN1:
                return "Win 1";
            case Player.c_animations.WIN2:
                return "Win 2";
            case Player.c_animations.WIN_1:
                return "Win Alt. 1";
            case Player.c_animations.WIN_2:
                return "Win Alt. 2";
            case Player.c_animations.WIN_3:
                return "Win Alt. 3";
            case Player.c_animations.INVISIBLE:
                return "Invisible";
            case Player.c_animations.HIDE:
                return "Hide";
            case Player.c_animations.DOWN:
                return "Low HP";
            case Player.c_animations.WAIT_SPECIAL:
                return "Idle (Spe)";
            case Player.c_animations.WAIT_SPECIAL_1:
                return "Idle (Spe) A";
            case Player.c_animations.WAIT_SPECIAL_2:
                return "Idle (Spe) B";
            case Player.c_animations.WAIT_SPECIAL_3:
                return "Idle (Spe) C";
            case Player.c_animations.WAIT_SPECIAL_4:
                return "Idle (Spe) D";
            case Player.c_animations.WAIT_SPECIAL_5:
                return "Idle (Spe) E";
            case Player.c_animations.MISS:
                return "Miss";
            case Player.c_animations.SUMMON:
                return "Summoning";
            case Player.c_animations.ABILITY_MOTION_OLD:
                return "Miss (Old)";
            case Player.c_animations.ABILITY_MOTION:
                return "Skill A";
            case Player.c_animations.ABILITY_MOTION_2:
                return "Skill B";
            case Player.c_animations.ABILITY_MOTION_3:
                return "Skill C";
            case Player.c_animations.ABILITY_MOTION_4:
                return "Skill D";
            case Player.c_animations.VS_MOTION_1:
                return "Custom Skill A";
            case Player.c_animations.VS_MOTION_2:
                return "Custom Skill B";
            case Player.c_animations.VS_MOTION_3:
                return "Custom Skill C";
            case Player.c_animations.VS_MOTION_4:
                return "Custom Skill D";
            case Player.c_animations.VS_MOTION_5:
                return "Custom Skill E";
            case Player.c_animations.VS_MOTION_6:
                return "Custom Skill F";
            case Player.c_animations.ENEMY_PHASE_1:
                return "Phase 1 (Entry)";
            case Player.c_animations.ENEMY_PHASE_2:
                return "Phase 2 (OD)";
            case Player.c_animations.ENEMY_PHASE_3:
                return "Phase 3 (Break)";
            case Player.c_animations.ENEMY_PHASE_4:
                return "Phase 4";
            case Player.c_animations.ENEMY_PHASE_5:
                return "Phase 5";
            case Player.c_animations.ENEMY_FORM_CHANGE:
                return "Form Change";
            case Player.c_animations.ENEMY_STANDBY_A:
                return "Standby A";
            case Player.c_animations.ENEMY_STANDBY_B:
                return "Standby B";
            case Player.c_animations.ENEMY_STANDBY_C:
                return "Standby C";
            case Player.c_animations.ENEMY_STANDBY_D:
                return "Standby D";
            case Player.c_animations.ENEMY_BREAK_STANDBY_A:
                return "Standby A (Break)";
            case Player.c_animations.ENEMY_BREAK_STANDBY_B:
                return "Standby B (Break)";
            case Player.c_animations.ENEMY_BREAK_STANDBY_C:
                return "Standby C (Break)";
            case Player.c_animations.ENEMY_BREAK_STANDBY_D:
                return "Standby D (Break)";
            case Player.c_animations.ENEMY_DAMAGE_STANDBY_A:
                return "Standby A (Dmgd)";
            case Player.c_animations.ENEMY_DAMAGE_STANDBY_B:
                return "Standby B (Dmgd)";
            case Player.c_animations.ENEMY_DAMAGE_STANDBY_C:
                return "Standby C (Dmgd)";
            case Player.c_animations.ENEMY_DAMAGE_STANDBY_D:
                return "Standby D (Dmgd)";
            case Player.c_animations.LINK_PHASE_1:
                return "Phase 1 (Entry)(Lk)";
            case Player.c_animations.LINK_PHASE_1_2:
                return "Phase 1B (Entry)(Lk)";
            case Player.c_animations.LINK_PHASE_1_F2:
                return "Phase 1C (Entry)(Lk)";
            case Player.c_animations.LINK_PHASE_1_F2_2:
                return "Phase 1D (Entry)(Lk)";
            case Player.c_animations.LINK_PHASE_2:
                return "Phase 2 (OD)(Lk)";
            case Player.c_animations.LINK_PHASE_2_2:
                return "Phase 2B (OD)(Lk)";
            case Player.c_animations.LINK_PHASE_2_F2:
                return "Phase 2C (OD)(Lk)";
            case Player.c_animations.LINK_PHASE_2_F2_2:
                return "Phase 2D (OD)(Lk)";
            case Player.c_animations.LINK_PHASE_3:
                return "Phase 3 (Break)(Lk)";
            case Player.c_animations.LINK_PHASE_3_2:
                return "Phase 3B (Break)(Lk)";
            case Player.c_animations.LINK_PHASE_3_F2:
                return "Phase 3C (Break)(Lk)";
            case Player.c_animations.LINK_PHASE_3_F2_2:
                return "Phase 3D (Break)(Lk)";
            case Player.c_animations.LINK_PHASE_4:
                return "Phase 4 (Lk)";
            case Player.c_animations.LINK_PHASE_4_2:
                return "Phase 4B (Lk)";
            case Player.c_animations.LINK_PHASE_4_F2:
                return "Phase 4C (Lk)";
            case Player.c_animations.LINK_PHASE_4_F2_2:
                return "Phase 4D (Lk)";
            case Player.c_animations.LINK_PHASE_5:
                return "Phase 5 (Lk)";
            case Player.c_animations.LINK_PHASE_5_2:
                return "Phase 5B (Lk)";
            case Player.c_animations.LINK_PHASE_5_F2:
                return "Phase 5C (Lk)";
            case Player.c_animations.LINK_PHASE_5_F2_2:
                return "Phase 5D (Lk)";
            case Player.c_animations.LINK_DAMAGE:
                return "Damaged (Link)";
            case Player.c_animations.LINK_DAMAGE_2:
                return "Damaged 2 (Link)";
            case Player.c_animations.LINK_DEAD:
                return "Dead (Link)";
            case Player.c_animations.LINK_DEAD_1:
                return "Dead 1 (Link)";
            case Player.c_animations.LINK_DEAD_2:
                return "Dead 2 (Link)";
            case Player.c_animations.LINK_DEAD_3:
                return "Dead 3 (Link)";
            case Player.c_animations.LINK_DEAD_A:
                return "Dead 1B (Link)";
            case Player.c_animations.LINK_DEAD_B:
                return "Dead 2B (Link)";
            case Player.c_animations.LINK_DEAD_C:
                return "Dead 3B (Link)";
            case Player.c_animations.LINK_MORTAL_A:
                return "Charge Atk. A (Lk)";
            case Player.c_animations.LINK_MORTAL_A_2:
                return "Charge Atk. A2 (Lk)";
            case Player.c_animations.LINK_MORTAL_A_F2:
                return "Charge Atk. A3 (Lk)";
            case Player.c_animations.LINK_MORTAL_A_F2_2:
                return "Charge Atk. A4 (Lk)";
            case Player.c_animations.LINK_MORTAL_B:
                return "Charge Atk. B (Lk)";
            case Player.c_animations.LINK_MORTAL_B_2:
                return "Charge Atk. B2 (Lk)";
            case Player.c_animations.LINK_MORTAL_B_F2:
                return "Charge Atk. B3 (Lk)";
            case Player.c_animations.LINK_MORTAL_B_F2_2:
                return "Charge Atk. B4 (Lk)";
            case Player.c_animations.LINK_MORTAL_C:
                return "Charge Atk. C (Lk)";
            case Player.c_animations.LINK_MORTAL_C_2:
                return "Charge Atk. C2 (Lk)";
            case Player.c_animations.LINK_MORTAL_C_F2:
                return "Charge Atk. C3 (Lk)";
            case Player.c_animations.LINK_MORTAL_C_F2_2:
                return "Charge Atk. C4 (Lk)";
            case Player.c_animations.LINK_MORTAL_D:
                return "Charge Atk. D (Lk)";
            case Player.c_animations.LINK_MORTAL_D_2:
                return "Charge Atk. D2 (Lk)";
            case Player.c_animations.LINK_MORTAL_D_F2:
                return "Charge Atk. D3 (Lk)";
            case Player.c_animations.LINK_MORTAL_D_F2_2:
                return "Charge Atk. D4 (Lk)";
            case Player.c_animations.LINK_MORTAL_E:
                return "Charge Atk. E (Lk)";
            case Player.c_animations.LINK_MORTAL_E_2:
                return "Charge Atk. E2 (Lk)";
            case Player.c_animations.LINK_MORTAL_E_F2:
                return "Charge Atk. E3 (Lk)";
            case Player.c_animations.LINK_MORTAL_E_F2_2:
                return "Charge Atk. E4 (Lk)";
            case Player.c_animations.LINK_MORTAL_F:
                return "Charge Atk. F (Lk)";
            case Player.c_animations.LINK_MORTAL_F_2:
                return "Charge Atk. F2 (Lk)";
            case Player.c_animations.LINK_MORTAL_F_F2:
                return "Charge Atk. F3 (Lk)";
            case Player.c_animations.LINK_MORTAL_F_F2_2:
                return "Charge Atk. F4 (Lk)";
            case Player.c_animations.LINK_MORTAL_G:
                return "Charge Atk. G (Lk)";
            case Player.c_animations.LINK_MORTAL_G_2:
                return "Charge Atk. G2 (Lk)";
            case Player.c_animations.LINK_MORTAL_G_F2:
                return "Charge Atk. G3 (Lk)";
            case Player.c_animations.LINK_MORTAL_G_F2_2:
                return "Charge Atk. G4 (Lk)";
            case Player.c_animations.LINK_MORTAL_H:
                return "Charge Atk. H (Lk)";
            case Player.c_animations.LINK_MORTAL_H_2:
                return "Charge Atk. H2 (Lk)";
            case Player.c_animations.LINK_MORTAL_H_F2:
                return "Charge Atk. H3 (Lk)";
            case Player.c_animations.LINK_MORTAL_H_F2_2:
                return "Charge Atk. H4 (Lk)";
            case Player.c_animations.LINK_MORTAL_I:
                return "Charge Atk. I (Lk)";
            case Player.c_animations.LINK_MORTAL_I_2:
                return "Charge Atk. I2 (Lk)";
            case Player.c_animations.LINK_MORTAL_I_F2:
                return "Charge Atk. I3 (Lk)";
            case Player.c_animations.LINK_MORTAL_I_F2_2:
                return "Charge Atk. I4 (Lk)";
            case Player.c_animations.LINK_MORTAL_J:
                return "Charge Atk. J (Lk)";
            case Player.c_animations.LINK_MORTAL_J_2:
                return "Charge Atk. J2 (Lk)";
            case Player.c_animations.LINK_MORTAL_J_F2:
                return "Charge Atk. J3 (Lk)";
            case Player.c_animations.LINK_MORTAL_J_F2_2:
                return "Charge Atk. J4 (Lk)";
            case Player.c_animations.LINK_MORTAL_K:
                return "Charge Atk. K (Lk)";
            case Player.c_animations.LINK_MORTAL_K_2:
                return "Charge Atk. K2 (Lk)";
            case Player.c_animations.LINK_MORTAL_K_F2:
                return "Charge Atk. K3 (Lk)";
            case Player.c_animations.LINK_MORTAL_K_F2_2:
                return "Charge Atk. K4 (Lk)";
            case Player.c_animations.LINK_ATTACK:
                return "Attack (Link)";
            case Player.c_animations.LINK_ATTACK_2:
                return "Attack B (Link)";
            case Player.c_animations.LINK_ATTACK_F2:
                return "Attack C (Link)";
            case Player.c_animations.LINK_ATTACK_F2_2:
                return "Attack D (Link)";
            case Player.c_animations.LINK_FORM_CHANGE:
                return "Form Change (Link)";
            case Player.c_animations.LINK_FORM_CHANGE_2:
                return "Form Change 2 (Link)"
            case Player.c_animations.MY_PAGE:
                return "My Page"
            // Unknown name
            default:
                return "??? (" + motion + ")";
        }
        ;
    }

// constants ================================================================================================
}