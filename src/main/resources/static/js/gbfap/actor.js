// variable to fix some specific enemies (enemy_4100663, enemy_4100753, enemy_4100933, enemy_4101033)
var attack_num = 0;
// variable to fix a skill for SR Richard (3030267000)
var highlow_result = 0;

// see view/raid/constant.js in GBF code for those exceptions above

class Actor {

    constructor(actorId) {

        this.player = player;
        this.cjsStage = player.m_stage;
        this.actorIndex = player.actors.size;
        this.actorId = actorId;

        this.isMainCharacter = false;
        this.isEnemy = false;

        // mortal skip
        this.isChargeAttackSkip = false;
        this.chargeAttackStartFrame = -1;

        // state
        this.isLooping = true; // looping mode (false means we stay on the same animation), 캐릭터의 경우에 한해 true, effects 는 false
        this.isPaused = false;

        // animation callback
        this.animationCompletedCallback = this.animationCompleted.bind(this);
        this.syncPlayCallback = this.syncPlay.bind(this);

        // textures
        this.weaponTextures = []; // list of weapon texture per version : Array<String> , 주인공의 무기. ['1010300000', '101030000'] (아마 두개인 이유는 그랑 지타 별도로 나뉘어 있어서인듯) 일반 캐릭터는 빈 배열

        // animations
        this.animation = null; // store the Animations data : Animation

        // cjs
        this.mainCjs = null; // store the instantied associated objects : Array<createjs.MovieClip> (DisplayObject → Container → MovieClip)
        this.specialCjs = null; // the playing summon/ charge attack / special : createjs.MovieClip
        this.additionalCjs = null; // for boss's another specials

        // motions
        this.allMotions = []; // list of list of available motions for all animations : Array<String>, ['attack', 'wait', ...], 캐릭터 상한 별로 최대 2개인듯. 선택가능한 모든 모션이 포함됨, 표시용으로만 사용하는듯 현재는 값이 유효하지 않아도 됨.
        this.playingMotions = []; // the current play list of motions // Array<String>, m_motion_lists 중 선택된 현재 재생중인 모션이름
        this.playingMotionIndex = 0; // the currently playing motion in the list : Number (index)

        // ability
        this.isAbilityTargetUs = false; // ability effect positioning flag, if true target will be enemy
        this.isAbilityEffectPlayCycle = false; // ability effect play mode / 0 = normal play, 1 = cycle play
        this.abilityCjsNameIndex = 0; // current playing ability effect from Animation.abilities

        // summons
        this.summonCjsNameIndex = 0; // current playing summon effect index from Animation.summons

        // internal use
        // tweens
        this.mainTween = null; // main animation tween : createjs.Tween , 캐릭터 트윈으로 추정
        this.childTweens = []; // contains the tweens : Array<createjs.Tween> , 이펙트 트윈으로 추정 (공격시 생김)
        this.tweenSourceCjses = []; // contains the source : Array<createjs.MovieClip> , 이펙트 트윈 (child_tweens)의 cjs 인듯

        // offset, scaling
        this.m_offset = {
            position: {x: 0.0, y: 0.0},
            target: {x: 0.0, y: 0.0},
            fullscreen: {x: 0.0, y: 0.0},
            special: {x: 0.0, y: 0.0},
            fullscreen_shift: {x: 0.0, y: 0.0},
        };

        this.characterScaling = this.isCharacter() ? 0.85 : 1.0; // 캐릭터는 캐릭터 크기 감소
        // this.characterScaling = this.isCharacter() ? 0.35 : 0.5;
        this.effectScaling = this.isEffect() ? 1.1 : 1.0; // 이펙트는 이펙트 크기 증가
        // this.effectScaling = this.isEffect() ? 0.6 : 0.5;

    }

    isEffect() {
        return this.actorId.indexOf('effect') >= 0;
    }

    isActor() {
        return this.actorId.indexOf('actor') >= 0;
    }

    isCharacter() {
        return this.actorId.indexOf('actor') >= 0 && this.actorIndex > 0;
    }

    setAnimation(animation) {
        this.animation = animation;
        this.isMainCharacter = animation.isMainCharacter;
        this.isEnemy = animation.isEnemy;
        this.isChargeAttackSkip = animation.isChargeAttackSkip;
        this.chargeAttackStartFrame = animation.chargeAttackStartFrame;
        if (animation.weapon != null) {
            this.weaponTextures.push(animation.weapon);
        }
        // load the files
        loader.loadAnimation(this); // 끝나고 나면 initToPlayer() 시작
    }

    initToPlayer() { // from Player.start_animation()
        // 원본 그랑블루 사양에 따른 추가할당 (사용하진 않음)
        let charaId = 'charaID_' + this.actorId.replace(/\D/g, ''); // charaID_1, ...
        stage.global[charaId] = this.animation.cjs.replace(/\D/g, ''); // charaID 는 숫자만 추출해서 구성된걸로 추정

        // 오프셋 설정
        this.setOffset(this.player.width, this.player.height, this.actorId);

        // instantiate all main animations
        // 실제로 화면에 그려지기 위한 조건(추정)
        // 1. stage 에 addChild 후 update() -> addChild 를 즉시 stage 에 갱신
        // 2. 최소 한번의 gotoAndPlay(motion) -> 컨테이너가 아닌 motion 을 직접 재생해야함. (wait, ...)
        this.mainCjs = (this.instantiateMainCjs(this.animation.cjs));
        if (this.isActor()) { // if actor, add to stage
            this.player.m_stage.addChild(this.mainCjs);
            this.player.m_stage.setChildIndex(this.mainCjs, this.actorIndex);
            this.player.m_stage.update(); // 실제로는 playMotion('wait') 의 gotoPlay 에서 로드됨, 여기서 update 안하면 그 다음 playMotion 2회차에서 나타남
        }

        // fetch and store the motion list for all animations
        this.setMotionLists();
        // make sure the state is reset
        // reset motion (else it will be set to the last one, because reset is intended for use DURING playing)
        this.playingMotionIndex = 0;
        // set list of playing motion to demo
        this.playingMotions = [this.animation.startMotion];
        // unpause all tweens
        this.isPaused = false;
        if (this.mainTween)
            this.mainTween.paused = false;
        for (let child of this.childTweens)
            child.paused = false;
        // play our animation
        if (this.actorId.indexOf('actor') >= 0) {
            this.playMotion(this.playingMotions[this.playingMotionIndex]);
        }

    }

    setMotionLists() {
        let new_lists = [];
        const animation = this.animation;
        let cjs = this.mainCjs;
        let motion_list = [];
        if (animation.summons.length > 0) { // special "hacky" exception for summons
            // there are two types : attack + damage / old ones
            if (animation.specials.length > 0 && animation.specials[0].includes("_attack")) {
                motion_list = ["summon", "summon_atk", "summon_dmg"]; // set list to character summon, summon atk and summon dmg
            } else {
                motion_list = ["summon", "summon_atk"]; // set list to character summon and summon atk
            }
        }
        let unsorted_motions = [];
        for (const motion in cjs[cjs.name]) { // iterate over all keys
            let motion_str = motion.toString();
            if (motion_str.startsWith(cjs.name)) { // a motion always start with the file name
                // hack to disable ougi options on mc beside mortal_B
                if (animation.isMainCharacter
                    && motion_str.includes("mortal")
                    && ((
                            animation.weapon == null
                            && !motion_str.endsWith("_mortal_B"))
                        || (
                            animation.weapon != null
                            && ["_1", "_2"].includes(motion_str.slice(-2)))
                    )
                ) continue;
                // remove the file name part
                motion_str = motion_str.substring(cjs.name.length + 1);
                // add to list
                unsorted_motions.push(motion_str);
            }
        }
        // add appear animation
        if (animation.isEnemy) {
            for (let i = 0; i < animation.raidAppear.length; ++i) {
                unsorted_motions.push("raid_appear_" + i);
            }
        }
        if (animation.additionalCjs && animation.additionalSpecials.length > 0 && this.additionalCjs) {
            console.log("additionalCjs = ", animation.additionalCjs);
            console.log("additionalSpecials = ", animation.additionalSpecials);
            // additional will be start from additional_mortal_a
            let additionalCjs = this.additionalCjs;
            for (const motion in additionalCjs[additionalCjs.name]) { // iterate over all keys
                let motion_str = motion.toString();
                if (motion_str.indexOf('mortal') >= 0) { // only mortal will be used
                    motion_str = motion_str.substring(cjs.name.length + 1); // mortal_A, mortal_B, ...
                    motion_str = 'additional_' + motion_str; // additional_mortal_A, additional_mortal_B, ...
                    unsorted_motions.push(motion_str);
                }
            }
        }

        // create a table of translate name and motion
        let table = {};
        for (const m of unsorted_motions) {
            table[this.player.translateMotion(m)] = m;
        }
        // get a list of sorted translated name
        const keys = Object.keys(table).sort();
        // build motion list according to sorted order
        for (const k of keys) {
            motion_list.push(table[k]);
        }
        // append list to motion list
        new_lists.push(motion_list);

        // update m_motion_lists
        this.allMotions = new_lists;
    }

    /**
     * 실제 플레이
     * @param playRequests Player.playRequest(), [{actorId, motion, index}, ...]
     * @param synced
     */
    playMotions(playRequests, synced = false) {
        this.playingMotions = playRequests.map(playRequest => playRequest.motion)
        let playingMotionEffectIndex = playRequests.find(playRequest => playRequest.index >= 0)?.index ?? null;

        // 인덱스 설정 (어빌 또는 소환인데 나중에 필드를 통합하거나 그럴지도?
        if (playRequests[0].motion.indexOf('summon') >= 0) this.summonCjsNameIndex = playingMotionEffectIndex;
        else if (playRequests[0].motion.indexOf('ability') >= 0) this.abilityCjsNameIndex = playingMotionEffectIndex;

        if (synced) {
            this.mainCjs[this.mainCjs.name].addEventListener('syncPlay', this.syncPlayCallback); // sync 중이면 이벤트리스너 붙이고 재생 x
        } else {
            this.playMotion(this.playingMotions[0]) // 재생 시작, 자동 loop
        }
        beep();
    }

    // play an animation
    // the most important function
    // motion is the specific animation to play
    playMotion(motion, isAdditional = false) {
        if (motion.startsWith("switch_version_")) return; // switch_version 일경우 처리를 무시
        // retrieve the current animation data and instance
        // let cjsWithAnimation = this.getCurrentCjsWithAnimation();
        // retrieve further down for clarity
        let cjsName = this.mainCjs.name;
        var cjs = this.mainCjs[cjsName]; // why because mainCjs is container which containing same name container it's inside
        const animation = this.animation;
        if (isAdditional) {
            cjsName = this.additionalCjs.name;
            cjs = this.additionalCjs[cjsName];
        }
        console.debug("[playMotion] actor.index = ", this.actorIndex, "motion = ", motion, " animation = ", animation, " cjs = ", cjs);
        if (!(cjs instanceof createjs.MovieClip)) throw new Error("Invalid animation, cjs = " + JSON.stringify(cjs)); // check if it's a valid animation

        cjs.visible = true; // default to visible
        // duration will contain the animation duration
        let duration = 0;

        // check which motion it is
        if (Player.c_animations.isMortal(motion)) { // Charge attacks / specials

            // if it has at least a special file
            if (animation.specials.length > 0) {
                if (motion.indexOf('additional') >= 0) { // for additional mortal, has no mortal skip only for enemy now
                    motion = motion.replace('additional_', ''); // additional_mortal_A -> mortal_A
                    this.specialCjs = this.addSpecial(animation.additionalSpecials[0], animation, true); // index 0 고정이어도 일단 문제 없음
                    this.playMotion(motion, true); // mortal_A, additional = true 로 playMotion 재호출
                    return; // 기존 요청 (additional_mortal_A) 는 여기서 버림.
                } else {
                    // retrieve index, example: mortal_A is index 0, mortal_B is index 1...
                    let special_index = motion.split('_')[1].charCodeAt() - 65; // CHECK 0으로 고정해도 디아스포라는 변화없이 mortalA, B, C 모두 사용가능
                    // check if index is in bound, else default to 0
                    if (special_index >= animation.specials.length) special_index = 0;
                    // play the special file
                    this.specialCjs = this.addSpecial(animation.specials[special_index], animation);
                    duration = this.getCjsDuration(cjs[cjsName + "_" + motion]);
                    // store it in class attriute
                    // add file duration if it's a weapon animation
                    if (this.isMainCharacter && this.weaponTextures.length > 0) {
                        duration += this.getCjsDuration(this.specialCjs[this.specialCjs.name][this.specialCjs.name + "_special"]);
                    }
                    console.log("duration = ", duration);
                    if (this.isChargeAttackSkip) duration -= this.chargeAttackStartFrame + 10; // 10 for adjustment
                    console.log("duration = ", duration);
                }

                // raid-mortal-skip 보정
                if (this.isEffect()) duration = 10; // raid_mortal_skip.raid_mortal_skip.raid_mortal_skip_special.timeline.duration
            }

        } else if (Player.c_animations.isAttack(motion)) {
            // retrieve and play file
            let atk = this.addAttack(animation.attack);
            // get the duration
            let atk_duration = this.getCjsDuration(atk[animation.attack][animation.attack + "_effect"]);
            // set tween with the attack duration
            this.addChildTween(atk, atk_duration);
            // set combo
            // i.e. if the following move is another attack...
            let next_motion = this.nextMotion();
            if ([
                Player.c_animations.ATTACK_DOUBLE,
                Player.c_animations.ATTACK_TRIPLE,
                Player.c_animations.ATTACK_QUADRUPLE
            ].includes(next_motion)) {
                duration = 10; // limit to 10 frames so that they follow right away
            } else {
                // else set duration normally
                duration = this.getCjsDuration(cjs[cjsName + "_" + motion]);
            }
            // cycling atk num here (it seems to control which arm attack, see line 1 for concerned monsters)
            attack_num = (attack_num + 1) % 2;

        } else if (Player.c_animations.isSummoning(motion)) {
            // Summon files
            // Note: this isn't native and kinda hacked on top
            let summon_cjs_name = motion == Player.c_animations.SUMMON_DAMAGE
                ? animation.summons[this.summonCjsNameIndex].replace("attack", "damage") // update attack to damage accordingly
                : animation.summons[this.summonCjsNameIndex];

            // play the summon file
            let summon_cjs = this.addSummon(summon_cjs_name);
            // store it in class attriute
            this.specialCjs = summon_cjs;
            // get duration
            if (!(summon_cjs_name in summon_cjs[summon_cjs_name])) { // faisafe for old summons
                console.log("faisafe, summon_cjs_name = " + summon_cjs_name + " not found in summon_cjs");
                for (const k in summon_cjs[summon_cjs_name]) { // go over each key
                    if (k.includes("_attack")) { // find the one named attack
                        summon_cjs[summon_cjs_name].gotoAndPlay("attack");
                        duration = this.getCjsDuration(summon_cjs[summon_cjs_name][k]);
                        break;
                    }
                }
            } else {
                duration = this.getCjsDuration(summon_cjs[summon_cjs_name][summon_cjs_name]);
                if (summon_cjs_name.indexOf("2040425000") >= 0) duration -= 2; // adjust for triple zero
            }

        } else if (Player.c_animations.isFormChange(motion)) {
            // Note: does nothing different from default
            // keeping it this way in case it must be changed / improved
            duration = this.getCjsDuration(cjs[cjsName + "_" + motion]);

        } else if (Player.c_animations.isAbilityMotion(motion)) {
            // get the animation duration
            let base_duration = this.getCjsDuration(cjs[cjsName + "_" + motion]);
            // check if animation got skill effects AND ui ability select is not set on None
            if (!this.isAbilityEffectPlayCycle && animation.abilities.length > 0) {
                // get file to play
                let skill_cjs = animation.abilities[this.abilityCjsNameIndex];
                let is_aoe = skill_cjs.includes("_all_");
                let isFatalChain = skill_cjs.includes('burst'); // if fatalChain (burst_NNN)

                if (isFatalChain) {
                    skill_cjs = 'mc_' + skill_cjs + '_root'; // mc_burst_341_root
                    is_aoe = true;
                }

                // instantiate
                const skill = this.addAbility(skill_cjs, is_aoe);
                // get duration
                // note: name change between aoe and single target files
                let skill_duration = isFatalChain ? 40 // maybe fixed
                    : is_aoe
                        ? this.getCjsDuration(skill[skill_cjs][skill_cjs + "_end"])
                        : this.getCjsDuration(skill[skill_cjs][skill_cjs + "_effect"]);

                // add tween for this duration
                this.addChildTween(skill, skill_duration);
                // get the highest duration between the element and skill effect
                duration = Math.max(base_duration, skill_duration);
                if (this.isAbilityEffectPlayCycle) { // if set to Cycle mode
                    // increase index
                    this.abilityCjsNameIndex = (this.abilityCjsNameIndex + 1) % animation.abilities.length;
                }
                if (skill_cjs == "ab_all_3030267000_01") { // SR Richard highlow skill
                    // cycling highlow_result here (it determines if Richard's skill is a win or not)
                    highlow_result = (highlow_result + 1) % 2;
                }
            } else // else the duration is just the base animation's
            {
                duration = base_duration;
            }

        } else if (Player.c_animations.isRaidAppear(motion)) {
            let appear_index = parseInt(motion.split('_')[2]);
            // get file to play
            let appear_cjs = animation.raid_appear[appear_index];
            // instantiate
            const appear = this.addAppear(appear_cjs);
            // get duration
            duration = this.getCjsDuration(appear[appear_cjs][appear_cjs]);
            // set as special
            this.specialCjs = appear;
            // add tween for this duration
            this.addChildTween(appear, duration);
            // make character invisible
            cjs.visible = false;

        } else { // default
            // we just set the duration to the animation's
            duration = this.getCjsDuration(cjs[cjsName + "_" + motion]);
        }

        // syncPlay 이벤트 dispatch
        if (!['wait', 'stbwait'].includes(motion)) { // 자신이 wait 상태일때는 syncPlay 하지 않음. (wait 일때 허용하면, 다른 캐릭터의 wait 후에도 play 가능함. 만약 wait 를 허용하려면 pairedActor 에 대한 정보를 가져야할듯.
            let otherActors = this.player.actors.values().filter(actor => actor.actorId !== this.actorId).toArray();
            let pairedActor = otherActors.find(actor => actor.mainCjs && actor.mainCjs[actor.mainCjs.name].hasEventListener('syncPlay'));
            if (pairedActor) {
                setTimeout(() => pairedActor.mainCjs[pairedActor.mainCjs.name].dispatchEvent('syncPlay'), 100); // 약간 지연 for damage
            }
        }

        // set listener for animation completion
        cjs.addEventListener("animationComplete", this.animationCompletedCallback);
        // play animation
        if (!(Player.c_animations.isSummoning(motion) || Player.c_animations.isRaidAppear(motion))) {
            // the check is a hack to avoid character moving during summoning
            // 실제 재생부, addSpecial 등으로 gotoAndPlay 한다 해도, 여기서 gotoAndPlay 로 motion 을 직접 재생해줘야 함.
            // 특히 special 의 경우, 파일이 2개여도 index 와 상관없이 mortal_A, mortal_B, mortal_C 가 모두 재생되는건 여기서 재생하는것이 유효하기 떄문임.
            cjs.gotoAndPlay(motion);
        }

        // create main tween
        // all the tweens are merely used to keep track of the animation durations
        this.mainTween = createjs.Tween.get(this.mainCjs, { // instead of stage, use individual cjs to getTween
            useTicks: true,
            override: true,
            paused: false, // we do not pause
        }).wait(duration).call(function (actor, cjs) {
            if (actor.isLooping)
                cjs.dispatchEvent('animationComplete')
        }, [this, cjs]);
    }

// called when the animation is completed
    animationCompleted(event) { // 주의! event.target === this.mainCjs[this.mainCjs.name]
        // clean up listener
        event.target.removeEventListener("animationComplete", this.animationCompletedCallback);
        if (this.specialCjs != null) {  // if there is a special, clean up
            this.cjsStage.removeChild(this.specialCjs);
            this.specialCjs = null;
        }
        if (this.additionalCjs != null) {
            this.cjsStage.removeChild(this.additionalCjs);
            this.additionalCjs = null;
        }
        // increase motion index
        this.playingMotionIndex++;
        this.mainCjs.visible = true; // additionalSpecial 사용시 false 로 변하므로 초기화

        if (event.target.hasEventListener('syncPlay')) { // syncPlay 중에는 재생한 모션까지만 하고, 다음 재생없이 return;
            return; // 바로 리턴해줘야 looping 상태를 유지하면서 wait 상태를 유지함
        }

        if (this.playingMotionIndex >= this.playingMotions.length) {
            // 마지막 모션, 후처리 시작
            event.target.removeEventListener("syncPlay", this.syncPlayCallback); // 보험

            this.playingMotionIndex = 0; // 모션 인덱스 초기화
            this.playingMotions = []; // 모션 초기화

            if (this.isEffect()) return; // 이펙트 플레이어는 wait 상태로 돌리지 않고 그대로 종료

            let waitMotionName = this.actorIndex === 0 ? "wait" : "stbwait"; // CHECK 나중에 적standby 등일때 추가
            this.playingMotions.push(waitMotionName);
            this.playMotion(this.playingMotions[this.playingMotionIndex]); // wait play
        } else {
            // 마지막 모션 아님, 다음모션 시작
            this.playMotion(this.playingMotions[this.playingMotionIndex]); // play next motion
        }
    }

    syncPlay(event) {
        // 자신의 모션이 마지막일 경우 이벤트 리스너 해제
        if (this.playingMotionIndex >= this.playingMotions.length - 1) {
            event.target.removeEventListener("syncPlay", this.syncPlayCallback);
        }
        // 자신의 playMotion 을 즉시 실행 (playingMotionIndex 여기서 조작하지 않음)
        this.playMotion(this.playingMotions[this.playingMotionIndex]);
    }

// retrieve the animation duration (in frames)
    getCjsDuration(cjs) {
        if (!cjs) {
            console.warn("cjs is null"); // fatalChain 의 burstNNN.js 사용시 undefined cjs 가 넘어옴. 이펙트 재생에는 문제없으므로 스킵
            return 0;
        }
        if (!(cjs instanceof createjs.MovieClip)) // shouldn't happen
            return null;
        else if (cjs.timeline.duration)
            return +cjs.timeline.duration;
        else // default for fallback purpose
            return +cjs.timeline.Id;
    }

// return the next motion in the play list
    nextMotion() {
        let next_index = this.playingMotionIndex + 1;
        if (next_index >= this.playingMotions.length)
            next_index = 0;
        return this.playingMotions[next_index];
    }

// instantiate the main element (i.e. the main animation, character, etc...)
    instantiateMainCjs(cjs) {
        let element = new lib[cjs];
        element.name = cjs; // set name
        // set position to position offset
        element.x = this.m_offset.position.x;
        element.y = this.m_offset.position.y;
        // apply scaling
        element.scaleX *= this.characterScaling;
        element.scaleY *= this.characterScaling;
        // note: zindex default to 0 (BOTTOM)
        return element;
    }

// instantiate an auto attack effect (phit file)
    addAttack(cjs) {
        if (cjs == null) throw new Error("cjs is null");
        let atk = new lib[cjs];
        atk.name = cjs; // set name
        atk.stop();
        // set position to target offset
        atk.x = this.m_offset.target.x;
        atk.y = this.m_offset.target.y;
        // apply scaling
        atk.scaleX *= this.effectScaling;
        atk.scaleY *= this.effectScaling;
        // add to stage
        this.cjsStage.addChild(atk);
        // set zindex to be on top
        this.cjsStage.setChildIndex(atk, Player.c_zindex.TOP);
        // play the animation
        atk[cjs].gotoAndPlay(6); // always 6
        return atk;
    }

// instantiate a charge attack (sp file)
    addSpecial(cjsName, animation, isAdditional = false) { // must pass the associated animation data
        // console.log("addSpecial, cjsName = ", cjsName, "animation = ", animation, "isAdditional = ", isAdditional)

        if (isAdditional) { // additional_mortal_A 등의 요청
            cjsStage.getChildByName(this.mainCjs.name).visible = false; // mainCjs 를 invisible 로
            // instantiate and add to stage additionalCjs (which uses additionalSpecials)
            this.additionalCjs = this.instantiateMainCjs(this.animation.additionalCjs);
            cjsStage.addChild(this.additionalCjs) // 스테이지에 추가
            cjsStage.update(); // 스테이지 갱신
            this.additionalCjs.gotoAndPlay('wait'); // force load (wait 없으면 오류)
            return; // 이후 playMotion() 에서 다시 playMotion(motion, true) 를 재호출하여 mortal 을 재생함.
        }

        // 오의 스킵 시작 이펙트
        if (this.isChargeAttackSkip) player.actors.get('effects').playMotion('mortal_A');

        // 오의(sp) 를 스테이지에 추가
        let special = new lib[cjsName];
        special.name = cjsName; // set name
        this.cjsStage.addChild(special); // 적은 add 안해도 작동하긴 하는데 캐릭은 해야됨.

        // 시작 프레임 지정시 및 재생
        let baseChild = special[cjsName][cjsName + '_special'];
        let specialChildToPlay = null; // 실제 재생되는 child, gotoAndPlay(startFrame) 이 먹는 위치임.
        specialChildToPlay = Object.values(baseChild).find(value => value instanceof createjs.MovieClip && value !== baseChild.parent)
        if (!specialChildToPlay) specialChildToPlay = baseChild; // 해당 cjs 없으면 special 그대로 사용
        /*
        specialChildToPlay 는 nsp.nsp.nsp_special.XXX 에 위치하며,
        이름이 _special.mc_eff_all, _special.mc_quake, _special.mc_all (주인공) or 'ef_nsp', 'nsp_all' (캐릭터) 등으로 다양하므로,
        필드를 인스턴스 여부로 직접 거르는 방식을 사용함. MoveClip 인스턴스는 parent 와 자식 (children [...] 제외) 2개뿐이므로 이렇게 함.
        specialChildToPlay 가 존재하지 않는 캐릭도 있으며, 이때는 nsp.nsp.nsp_special 을 재생하도록함.
        되도록 nsp.nsp.nsp_special 을 재생하지 않는 이유는 얘가 데미지 효과라 타임라인이 제대로 살아있지 않는 경우가 있기 때문.
         */
        console.log('addSpecial, specialChildToPlay', specialChildToPlay);

        // speical[cjsName][cjsName + '_special'].children[0].gotoAndPlay(startFrame) // lazy-load 로 인해 children = [] -> 사용불가
        // special[cjsName][cjsName + '_special'].gotoAndPlay(startFrame); // TODO 나중에 야치마로 화면 흔들림 테스트

        let startFrame = this.chargeAttackStartFrame > 0 ? this.chargeAttackStartFrame : 0
        if (this.isChargeAttackSkip && this.chargeAttackStartFrame > 0) { // nsp, sp 를 제외한 효과 (raid_mortal_skip 등) 은 자동재생으로 ok
            createjs.Ticker.on("tick", function () { // 한틱 후, 한번만 실행
                specialChildToPlay.gotoAndPlay(startFrame)
            }, null, true);
        }
        // 참고
        // special : 루트 컨테이너? currentFrame 0(1) 으로 고정, 시작프레임 적용불가
        // speical[cjsName] : 실제효과? currentFrame 6으로 고정, 시작프레임 적용불가
        // special[cjsName][cjsName + '_special'] : 화면 흔들림, 데미지표시 등 화면효과로 보임. 시작프레임 적용가능
        // special[cjsName][cjsName + '_special']['mc_nsp_all'] : 실제 프레임이 진행되는 효과. 시작프레임 적용가능, 'mc_nsp_all' 말고 다양한 이름으로 존재한다.
        // console.log("startFrame = ", special[cjsName][cjsName + '_special']['mc_nsp_all']);

        if (this.isEnemy) { // if it's an enemy animation
            special.x = this.m_offset.target.x;
            special.y = this.m_offset.target.y;
            this.cjsStage.setChildIndex(special, Player.c_zindex.TOP);
        } else if (this.isCharacter()) { // else player or weapon
            // newer "fullscreen" animations cover all the screen
            // and have s2 or s3 in their file names
            if (cjsName.includes("_s2") || cjsName.includes("_s3")) {
                special.x = this.m_offset.fullscreen.x;
                special.y = this.m_offset.fullscreen.y;
                special.scaleX *= this.m_fullscreen_scale;
                special.scaleY *= this.m_fullscreen_scale;
                this.cjsStage.setChildIndex(special, this.cjsStage.numChildren - 2); // 캐릭터가 mortal skip effect 보다 하나아래
            } else { // regular ones
                special.x = this.m_offset.target.x;
                special.y = this.m_offset.target.y + this.m_offset.special.y;
                this.cjsStage.setChildIndex(special, Player.c_zindex.BOTTOM);
            }
        } else if (this.isEffect()) {
            special.x = this.m_offset.special.x;
            special.y = this.m_offset.special.y;
            special.scaleX *= this.effectScaling;
            special.scaleY *= this.effectScaling;
            // index 최상위로 추가됨.
        } else {
            throw new Error("Invalid special animation, cjs: " + cjsName);
        }
        // 기본스케일 사용(1.0), skip

        return special;
    }

// instantiate a summon
    addSummon(cjs) {
        console.log('addSummon', cjs);
        let summon = new lib[cjs];
        summon.name = cjs; // set name
        console.log('addSummon', summon);
        // add to stage
        this.cjsStage.addChild(summon);
        // the newer files are in two files (attack and damage)
        // both seems to use the fullscreen offset
        if (cjs.includes("_attack") || cjs.includes("_damage")) {
            summon.x = this.m_offset.fullscreen.x;
            summon.y = this.m_offset.fullscreen.y;
            summon.scaleX *= this.m_fullscreen_scale;
            summon.scaleY *= this.m_fullscreen_scale;
        } else { // old summons (N, R, ...)
            // set to target
            summon.x = this.m_offset.target.x;
            summon.y = this.m_offset.target.y;
            this.cjsStage.setChildIndex(summon, Player.c_zindex.TOP);
            summon.gotoAndPlay(0);
        }
        summon.gotoAndPlay(0);

        // apply scaling
        summon.scaleX *= this.effectScaling;
        summon.scaleY *= this.effectScaling;
        return summon;
    }

// instantiate a skill effect
    addAbility(cjs, is_aoe) // must pass if it's an aoe ability
    {
        console.log('addAbility', cjs, is_aoe);
        let skill = new lib[cjs];
        console.log('addAbility', skill);
        skill.name = cjs; // set name
        // add to stage
        this.cjsStage.addChild(skill);
        // display on top
        // this.cjsStage.setChildIndex(skill, Player.c_zindex.TOP);
        // aoe are like fullscreen special
        if (is_aoe) {
            skill.x = this.m_offset.fullscreen.x;
            skill.y = this.m_offset.fullscreen.y;
            skill.scaleX *= this.m_fullscreen_scale;
            skill.scaleY *= this.m_fullscreen_scale;
        } else {
            // set position according to m_ability_target state
            if (this.isAbilityTargetUs) {
                skill.x = this.m_offset.position.x;
                skill.y = this.m_offset.position.y;
            } else {
                skill.x = this.m_offset.target.x;
                skill.y = this.m_offset.target.y;
            }
        }
        // apply scaling
        skill.scaleX *= this.effectScaling;
        skill.scaleY *= this.effectScaling;
        return skill;
    }

// instantiate a raid appear animation
    addAppear(cjs) {
        let appear = new lib[cjs];
        appear.name = cjs; // set name
        // add to stage
        this.cjsStage.addChild(appear);
        // display on top
        this.cjsStage.setChildIndex(appear, Player.c_zindex.TOP);
        // fullscreen
        appear.x = this.m_offset.fullscreen.x;
        appear.y = this.m_offset.fullscreen.y;
        appear.scaleX *= this.m_fullscreen_scale;
        appear.scaleY *= this.m_fullscreen_scale;
        // apply scaling
        appear.scaleX *= this.effectScaling;
        appear.scaleY *= this.effectScaling;
        return appear;
    }

// create a tween for N duration and store it
    addChildTween(tween_source, duration) {
        var _player_ = this;
        const ref = tween_source;
        this.tweenSourceCjses.push(tween_source);
        const child_tween = createjs.Tween.get(tween_source, {
            useTicks: true,
            paused: this.isPaused
        }).wait(duration).call(function () {
            //_player_.clean_tween(ref, child_tween);
            _player_.cleanTween(ref, child_tween);
        });
        this.childTweens.push(child_tween);
    }

// remove/clean up a specific tween
    cleanTween(tween_source, child_tween) {
        let i = this.tweenSourceCjses.indexOf(tween_source);
        if (i != -1)
            this.tweenSourceCjses.splice(i, 1);
        this.cjsStage.removeChild(tween_source);
        i = this.childTweens.indexOf(child_tween);
        if (i != -1)
            this.childTweens.splice(i, 1);
    }

    /**
     * 오프셋 설정
     * @param w 플레이어 width
     * @param h 플레이어 height
     * @param actorId : Animation.name, 'actor0', 'actor1', ...
     * @param scaling = 1.0
     */
    setOffset(w, h, actorId, scaling = 1.0) {
        // initialize offsets
        this.m_fullscreen_scale = w / Player.c_gbf_animation_width;
        const center = Player.c_canvas_size / 2.0;
        const characterOffsetMultiplier = [
            {x: 0.0, y: 0.0}, // none use
            {x: 0.32, y: 0.17},
            {x: 0.42, y: 0.27},
            {x: 0.32, y: 0.37},
            {x: 0.42, y: 0.47},
            {x: 0.0, y: 0.0}, // effect
            {x: 0.0, y: 0.0}, // none use
            {x: 0.0, y: 0.0}, // none use
        ]
        const characterTargetOffsetMultiplier = {x: 0.20, y: 0.30};
        if (this.isEnemy) { // enemy
            this.m_offset.position.x = Math.round(center - w * 0.30 * scaling);
            this.m_offset.position.y = Math.round(center + h * 0.50 * scaling);
            this.m_offset.target.x = Math.round(center + w * 0.25 * scaling);
            this.m_offset.target.y = Math.round(center + h * 0.40 * scaling);
            this.m_offset.fullscreen.x = Math.round(center - w * 0.5 / scaling);
            this.m_offset.fullscreen.y = Math.round(center - h * 0.5 / scaling);
            this.m_offset.special.y = Math.round(0.15 * w / scaling);
            this.m_offset.special.y = Math.round(0.15 * h / scaling);
        } else if (this.isEffect()) {
            this.m_offset.position.x = Math.round(center - w * 0.30 * scaling);
            this.m_offset.position.y = Math.round(center + h * 0.50 * scaling);
            // this.m_offset.position.x = Math.round(center - w * 0.51 * scaling); // 0.30
            // this.m_offset.position.y = Math.round(center - h * 0.58 * scaling); // + 0.50
            this.m_offset.target.x = Math.round(center + w * 0.25 * scaling);
            this.m_offset.target.y = Math.round(center + h * 0.40 * scaling);
            this.m_offset.fullscreen.x = Math.round(center - w * 0.5 / scaling); // 0.5
            this.m_offset.fullscreen.y = Math.round(center - h * 0.5 / scaling); // 0.5
            this.m_offset.special.x = Math.round(center - w * 0.53 * scaling); // 0.30
            this.m_offset.special.y = Math.round(center - h * 0.60 * scaling); // + 0.50
            // this.m_offset.special.y = Math.round(0.15 * w / scaling);
            // this.m_offset.special.y = Math.round(0.15 * h / scaling);
        } else { // normal
            // element is on the right, target on the left
            this.m_offset.position.x = Math.round(center + w * characterOffsetMultiplier[this.actorIndex].x * scaling); // origin: 0.25
            this.m_offset.position.y = Math.round(center + h * characterOffsetMultiplier[this.actorIndex].y * scaling); // origin: 0.15
            this.m_offset.target.x = Math.round(center - w * characterTargetOffsetMultiplier.x * scaling); // origin: 0.10
            this.m_offset.target.y = Math.round(center + h * characterTargetOffsetMultiplier.y * scaling); // origin: 0.30
            this.m_offset.fullscreen.x = Math.round(center - w * 0.5 / scaling);
            this.m_offset.fullscreen.y = Math.round(center - h * 0.5 / scaling);
            this.m_offset.special.y = Math.round(0.15 * w / scaling);
            this.m_offset.special.y = Math.round(0.15 * h / scaling);
        }
    }

    resetActor() {
        // clean up extra animations
        for (let ex of this.tweenSourceCjses)
            this.player.m_stage.removeChild(ex);
        this.tweenSourceCjses = [];
        this.childTweens = [];
        // stop playing audio
        if (window.audio) window.audio.stop_all();
        // set current motion to last one
        this.playingMotionIndex = this.playingMotions.length - 1;
        // fire animation complete
        // it will do additional clean up and cycle the animation to the first one (0)
        this.mainCjs.children[0].dispatchEvent("animationComplete");
    }

}