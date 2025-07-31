// variable to fix some specific enemies (enemy_4100663, enemy_4100753, enemy_4100933, enemy_4101033)
var attack_num = 0;

// see view/raid/constant.js in GBF code for those exceptions above

class Actor {

    constructor(actorId) {

        this.player = player;
        this.cjsStage = player.m_stage;
        this.actorIndex = player.actors.size;
        this.actorId = actorId;

        this.isMainCharacter = false;
        this.isEnemy = actorId.includes('actor-0'); // setAnimation 에서 하면 offset에 못맞춤

        // state
        this.isLooping = true; // looping mode (false means we stay on the same animation), 캐릭터의 경우에 한해 true, effects 는 false
        this.durationSkip = false;

        // animation callback
        this.animationCompletedCallback = this.animationCompleted.bind(this);
        this.syncPlayCallback = this.syncPlay.bind(this);

        // textures
        this.weaponTextures = []; // list of weapon texture per version : Array<String> , 주인공의 무기. ['1010300000', '101030000'] (아마 두개인 이유는 그랑 지타 별도로 나뉘어 있어서인듯) 일반 캐릭터는 빈 배열

        // animations
        this.animation = null; // store the Animations data : Animation

        // cjs
        this.mainCjs = null; // store the instantied associated objects : Array<createjs.MovieClip> (DisplayObject → Container → MovieClip)
        this.effectCjs = null; // the playing attack, ability, summon, charge attack effect cjs : createjs.MovieClip
        this.playingEffectCjses = [];
        this.multiAttackEffectCjses = []; // 난격용 이펙트
        this.additionalCjs = null; // for boss's another specials

        // motions
        this.allMotions = []; // list of list of available motions for all animations : Array<String>, ['attack', 'wait', ...], 캐릭터 상한 별로 최대 2개인듯. 선택가능한 모든 모션이 포함됨, 표시용으로만 사용하는듯 현재는 값이 유효하지 않아도 됨.
        this.playingMotions = []; // the current play list of motions // Array<String>, m_motion_lists 중 선택된 현재 재생중인 모션이름
        this.playingMotionDurations = []; // playingMotions 설정 직후 해당 motion 들의 duration 을 미리 구해논 값. : Array<Number>
        this.playingMotionIndex = 0; // the currently playing motion in the list : Number (index)


        // motion specifics
        // attack
        this.attackMultiHitCount = 1; // 난격횟수, 기본카운트 1

        // ability
        this.isAbilityTargetedEnemy = false; // ability effect positioning flag, if true target will be enemy
        this.abilityCjsType = null;

        // mortal skip
        this.isChargeAttackSkip = false;
        this.chargeAttackStartFrame = -1;

        // summons
        this.summonCjsNameIndex = 0; // current playing summon effect index from Animation.summons

        // internal use
        // tweens
        this.mainTween = null; // main animation tween : createjs.Tween , 캐릭터 트윈

        // offset, scaling -> setOffset 에서 초기화
        this.m_offset = {
            position: {x: 0.0, y: 0.0},
            target: {x: 0.0, y: 0.0},
            fullscreen: {x: 0.0, y: 0.0},
            special: {x: 0.0, y: 0.0},
            fullscreen_shift: {x: 0.0, y: 0.0},
        };
        this.characterScaling = 0;
        this.effectScaling = 0;

    }

    isEffect() {
        return this.actorId.includes('global');
    }

    isActor() {
        return this.actorId.includes('actor');
    }

    isCharacter() {
        return !this.isEnemy && this.isActor();
    }

    isNextEnemy() { // 폼체인지 다음 폼 로드시 사용
        return this.isEnemy && this.player.actors.values().filter(actor => actor.isEnemy).toArray().length >= 2;
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

        this.mainCjs = (this.instantiateMainCjs(this.animation.cjs));
        this.mainCjs.name = this.animation.cjs;
        if (this.isActor()) { // if actor, add to stage
            this.player.m_stage.addChild(this.mainCjs);
            this.player.m_stage.update(); // 실제로는 playMotion('wait') 의 gotoPlay 에서 로드됨, 여기서 update 안하면 그 다음 playMotion 2회차에서 나타남
            if (this.isNextEnemy()) {
                return; // 폼체인지 다음 적은 addChild 까지만
            }
        }

        // fetch and store the motion list for all animations
        this.setMotionLists();
        this.playingMotionIndex = 0;
        // set list of playing motion to demo
        this.playingMotions = [this.animation.startMotion];
        // play our animation
        if (this.isActor()) {
            this.playMotion(this.playingMotions[this.playingMotionIndex]);
        }
    }

    /**
     * 실제 플레이
     * @param playRequest Player.XXrequest()
     * @param synced
     */
    playMotions(playRequest, synced = false) {
        this.playingMotions = playRequest.motions;
        let playingMotionEffectIndex = playRequest.index;

        // 인덱스 설정 (어빌 또는 소환인데 나중에 필드를 통합하거나 그럴지도?
        if (playRequest.type === Player.c_animations.SUMMON) this.summonCjsNameIndex = playingMotionEffectIndex;

        // 전처리
        switch (playRequest.type) {
            case Player.c_animations.ABILITY:
                this.abilityCjsType = playRequest.moveType !== 'NONE' ? playRequest.moveType : null;
                this.isAbilityTargetedEnemy = this.abilityCjsType ? this.animation.abilities[this.abilityCjsType]?.isTargetedEnemy : false;
                break;
            case Player.c_animations.ATTACK:
                this.attackMultiHitCount = playRequest.multiHitCount;
                break;
            default: // nothing
                break;
        }

        // playMotion 전처리
        this.playingMotionDurations = this.playingMotions.map(motion => this.preProcessPlayMotion(motion));

        // playMotion 실제 재생
        if (synced) {
            this.mainCjs.addEventListener('syncPlay', this.syncPlayCallback); // sync 중이면 이벤트리스너 붙이고 재생 x
        } else {
            this.playMotion(this.playingMotions[0]) // 재생 시작, 자동 loop
        }

        let motionDurationSum = this.playingMotionDurations.reduce((acc, duration) => {
            let durationSeconds = duration / createjs.Ticker.framerate; // fps -> s
            let ceiledDurationSeconds = Math.ceil(durationSeconds * 10) / 10; // ceil to X.Y
            return acc + ceiledDurationSeconds;
        }, 0);

        if (this.durationSkip) { // 계산한 duration 을 무시, 이펙트와 관계없이 처리를 진행 (주로 서포트 어빌리티 계열)
            motionDurationSum = 0;
        }
        console.debug('motion = ', playRequest.motions, 'motionDurationSum (s)', motionDurationSum, 'motionDurationSum (ms)', motionDurationSum * 1000, ' durationSkip = ', this.durationSkip);
        this.durationSkip = false;
        return motionDurationSum * 1000; // ms
    }

    preProcessPlayMotion(motion) {
        // console.log('[preProcessPlayMotion] this.actor = ', this.actorId, 'motion = ', motion, 'mainCjs = ', this.mainCjs);
        let duration = 0;
        if (Player.c_animations.isMortal(motion)) { // Charge attacks / specials
            if (motion.includes('additional')) {
                // additional 모션 초기화
                let additionalCjs = this.instantiateMainCjs(this.animation.additionalCjs);
                additionalCjs.name = this.animation.additionalCjs;
                this.additionalCjs = additionalCjs;
                // additional 이펙트 초기화
                let specialIndex = 0; // 문제생기면 변경
                let additionalSpecialCjs = this.instantiateEffectCjs(this.animation.additionalSpecials[specialIndex]);
                this.playingEffectCjses.push(additionalSpecialCjs);
                // 모션 duration 계산
                let replacedMotion = motion.replace('additional_', '');  // additional_mortal_A -> mortal_A
                duration = this.getCjsDuration(this.additionalCjs.getMotionCjs(replacedMotion));
            } else {
                // 이펙트 초기화
                let specialIndex = 0; // 문제생기면 변경
                let specialCjs = this.instantiateEffectCjs(this.animation.specials[specialIndex]);
                this.playingEffectCjses.push(specialCjs);
                // 모션 duration 계산
                duration = this.getCjsDuration(this.mainCjs.getMotionCjs(motion));
                if (this.isMainCharacter && this.weaponTextures.length > 0) { // 무기 sp 파일의 duration 구해 더함
                    let weaponSpecialCjs = this.instantiateEffectCjs(this.animation.specials[0]);
                    duration += this.getCjsDuration(weaponSpecialCjs.getPlayableCjs('_special'));
                }
            }
            // duration 보정
            if (this.isChargeAttackSkip && this.chargeAttackStartFrame > 0) duration -= this.chargeAttackStartFrame + 9; // chargeAttackSKip 보정
            if (this.isEffect()) duration = 10; // effect.mortal_A -> 오의어택 스킵 preEffect

        } else if (Player.c_animations.isAttack(motion)) {
            // 이펙트 초기화
            let attackEffectIndex =
                (motion === Player.c_animations.ATTACK || motion === Player.c_animations.ATTACK_SHORT) ? 0
                    : motion === Player.c_animations.ATTACK_DOUBLE ? 1
                        : motion === Player.c_animations.ATTACK_TRIPLE ? 2 : 3;
            if (attackEffectIndex >= this.animation.attacks.length) attackEffectIndex = this.animation.attacks.length - 1; // index 초과시 마지막 (이펙트 1~2 개짜리도 있음)
            let attackCjsName = this.animation.attacks[attackEffectIndex];
            console.log('attack index', attackEffectIndex, 'cjs', attackCjsName)
            let attackCjs = this.instantiateEffectCjs(attackCjsName);
            this.playingEffectCjses.push(attackCjs);
            // 모션 duration 계산
            let isLastAttack = this.playingMotions.lastIndexOf(motion) >= this.playingMotions.length - 1;
            duration = isLastAttack ? this.getCjsDuration(this.mainCjs.getMotionCjs(motion)) : 10; // 마지막 공격이 아니면 10프레임으로 고정
            if (this.attackMultiHitCount > 1) duration += 4 * (this.attackMultiHitCount - 1); // 난격 보정
            if (motion === Player.c_animations.ATTACK_MOTION_ONLY) duration = 30; // 어택 모션만 있는경우 30으로 설정 (모션: attack)

        } else if (Player.c_animations.isAbilityMotion(motion)) { // ab_motion, to_stb_wait, win, ab_motion_damage 등
            motion = Player.c_animations.getCleanAbilityMotion(motion); // 원래 모션으로 변환
            // 모션 duration 계산
            let abilityMotionDuration = this.getCjsDuration(this.mainCjs.getMotionCjs(motion));
            if (motion.includes('win')) abilityMotionDuration = 30; // 승리모션의 경우 지나치게 길어 30fps 로 컷
            // 이펙트
            let abilityEffectDuration = 0;
            let abilityCjsName = this.getAbilityCjsName(this.abilityCjsType); // abilityCjsType 은 요청에서 채워옴
            if (abilityCjsName) { // 어빌리티의 이펙트 cjs 가 존재
                // 이펙트 초기화
                let isChainEffect = abilityCjsName.includes('burst'); // chainBurst, fatalChain (테스트는 페이탈체인만)
                if (isChainEffect) abilityCjsName = 'mc_' + abilityCjsName + '_root';
                let abilityCjs = this.instantiateEffectCjs(abilityCjsName);
                this.playingEffectCjses.push(abilityCjs);
                // 이펙트 duration 계산
                if (isChainEffect) {
                    abilityEffectDuration = 40; // 페이탈 체인 기준, 40fps 고정
                } else { // normal abilities
                    let isAoe = abilityCjsName.includes("_all_") || abilityCjsName.includes("raid");
                    let childNamePostfix = isAoe ? "end" : "effect";
                    abilityEffectDuration = this.getCjsDuration(abilityCjs.getPlayableCjs(childNamePostfix));
                }
            }
            // duration 계산 (최종)
            duration = Math.max(abilityMotionDuration, abilityEffectDuration); // 모션과 이펙트중 긴쪽의 duration 을 사용
            // 이펙트 duration 스킵 : 재생 자체는 원래 duration 대로 하나, 외부에서 이펙트를 무시하고 처리를 진행
            if (abilityCjsName && abilityCjsName.includes('ab_powerup')) this.durationSkip = true; // 적 파워업시 깎기
            if (!abilityCjsName && this.isCharacter()) this.durationSkip = true; // 캐릭터, 이펙트 없을시 모션 상관없이 duration 단축 -> 서포트 어빌리티 등 가속

        } else if (Player.c_animations.isSummoning(motion)) {
            let summonIndex = 0; //CHECK 나중에 summonId 받도록 변경.
            // 이펙트 초기화
            let currentSummonCjsName = this.animation.summons[summonIndex];
            if (motion === Player.c_animations.SUMMON_DAMAGE) currentSummonCjsName = currentSummonCjsName.replace('attack', 'damage'); // update attack to damage accordingly
            let summonCjs = this.instantiateEffectCjs(currentSummonCjsName);
            this.playingEffectCjses.push(summonCjs);
            // 이펙트 duration 계산
            if (!(summonCjs.name in summonCjs)) console.error("faisafe, summon_cjs_name = " + summonCjs.name + " not found in summon_cjs"); // CHECK 필요하면 추가
            duration = this.getCjsDuration(summonCjs.getPlayableCjs());
            if (summonCjs.name.includes("2040425000")) duration -= 2; // adjust for triple zero

        } else if (Player.c_animations.isRaidAppear(motion)) {
            // 이펙트 초기화
            let appearIndex = parseInt(motion.split('_')[2]);
            let appearCjsName = this.animation.raidAppear[appearIndex]; // get file to play
            let appearCjs = this.instantiateEffectCjs(appearCjsName);
            this.playingEffectCjses.push(appearCjs);
            // 이펙트 duration 계산
            duration = this.getCjsDuration(appearCjs.getPlayableCjs()); // get duration

        } else if (Player.c_animations.isDamage(motion)) {
            // 모션 duration 계산
            duration = this.isCharacter() ? 15 : 8; // 캐릭터는 기본 0.5s (15fps), 적은 0.27s (8fps) 로 보임, 연타등 최적화를 위해 계산하지 않음.
            if (!this.mainCjs.hasEventListener('syncPlay') && this.isEnemy && Player.c_animations.isDamage(this.nextMotion())) { // 적의 연타 피격 대응
                duration = Math.max(duration - this.playingMotions.length, 2) // damage 많이 재생해야할수록 줄임, 최소 2프레임
            }

        } else if (Player.c_animations.SUMMON === motion) {
            // 이펙트 초기화 - 연속재생시 이펙트가 없는 모션이 섞이면 안됨... CHECK 나중에 고민
            this.playingEffectCjses.push(null);
            // 모션 duration 계산
            duration = this.getCjsDuration(this.mainCjs.getMotionCjs(motion));
        } else { // default
            // 모션 duration 계산
            duration = this.getCjsDuration(this.mainCjs.getMotionCjs(motion));
        }

        // console.log('motion = ', motion, ' duration', duration)

        return duration; // frames
    }

    // play an animation
    // the most important function
    // motion is the specific animation to play
    playMotion(motion) {
        console.debug("[playMotion] actor.index = ", this.actorIndex, "motion = ", motion, " animation = ", this.animation, " cjs = ", this.mainCjs);
        if (!(this.mainCjs instanceof createjs.MovieClip)) throw new Error("Invalid animation, cjs = " + JSON.stringify(cjs)); // check if it's a valid animation
        this.isLooping = !(Player.c_animations.isWaiting(motion) || motion.includes(Player.c_animations.ENEMY_FORM_CHANGE)); // waiting, 폼체인지 일땐 반복재생 x
        this.effectCjs = this.playingEffectCjses[this.playingMotionIndex];
        this.mainCjs.visible = true; // default to visible
        let stageIndex = Player.z_index.UNDER_TOP; // 이펙트 발생시 기본 모션위치는 TOP - 1, 이펙트는 TOP (자동)

        // add to stage
        if (Player.c_animations.isMortal(motion)) { // Charge attacks / specials
            if (this.isCharacter()) {
                stageIndex = Player.z_index.UUNDER_TOP; // 오의모션 - 오의이펙트 - 오의스킵이펙트
            } else {
                stageIndex = Player.z_index.TOP; // 적과 global.mortal_skip 은 TOP. 특히 적은 이펙트가 아닌 모션의 index 를 따라가므로 반드시 TOP
            }
            if (motion.includes('additional')) { // for additional mortal, has no mortal skip only for enemy now
                cjsStage.addChild(this.additionalCjs);
                cjsStage.getChildByName(this.mainCjs.name).visible = false; // mainCjs 를 invisible 로
                cjsStage.update(); // 스테이지 갱신
                let replacedMotion = motion.replace('additional_', '');
                this.additionalCjs[this.additionalCjs.name].gotoAndPlay(replacedMotion); // force load
                stageIndex = Player.z_index.BOTTOM; // mainCjs 는 BOTTOM 으로.
                // motion's additional_ prefix will removed before gotoAndPlay() because preProcessPlayMotion need additional prefix
            }
            this.addEffectsToStage();
            this.addSpecial();

        } else if (Player.c_animations.isAttack(motion)) {
            if (motion !== Player.c_animations.ATTACK_MOTION_ONLY) {
                this.addEffectsToStage();
                this.addAttack()
            } else {
                motion = motion.replace('_motion_only', '');
            }
            if (this.isEnemy) stageIndex = Player.z_index.TOP; // 적은 이펙트가 아닌 모션의 index 를 따라가므로 TOP
            attack_num = (attack_num + 1) % 2; // cycling atk num here (it seems to control which arm attack, see line 1 for concerned monsters)

        } else if (Player.c_animations.isAbilityMotion(motion)) {
            if (this.effectCjs) {
                this.addEffectsToStage();
                let isAoe = this.effectCjs.name.includes("_all_") || this.effectCjs.name.includes("raid");
                this.addAbility(isAoe);
            }
            motion = Player.c_animations.getCleanAbilityMotion(motion); // 원래 모션으로 변환

        } else if (Player.c_animations.isSummoning(motion)) {
            this.addEffectsToStage();
            this.addSummon();

        } else if (Player.c_animations.isFormChange(motion)) {
            // Note: does nothing different from default, keeping it this way in case it must be changed / improved
        } else if (Player.c_animations.isRaidAppear(motion)) {
            this.addEffectsToStage();
            this.addAppear();
        } else if (Player.c_animations.isWaiting(motion) || Player.c_animations.isDamage(motion)) {
            stageIndex = null; // actorIndex 를 따라감
        }

        // stageIndex 설정
        player.alterStageIndex(this.mainCjs, stageIndex);

        // 이펙트 있고, 자신의 메인 cjs 가 syncPlay 없을때
        if (this.effectCjs && !(this.mainCjs.hasEventListener('syncPlay'))) {
            // 다른 actor 의 syncPlay 실행
            this.player.actors.values()
                .filter(actor => actor.actorId !== this.actorId && actor.mainCjs && actor.mainCjs.hasEventListener('syncPlay'))
                .forEach(actor => setTimeout(() => actor.mainCjs.dispatchEvent('syncPlay'), 100)); // 약간 지연
        }

        // console.log('motion = ', motion, 'cjs = ', cjs, 'cjs.motion = ', cjs[cjsName + '_' + motion]);
        let duration = this.playingMotionDurations[this.playingMotionIndex];
        if (!duration) {
            console.warn('[playMotion] unexpected preProcessPlayMotion, motion = ', motion, 'playingMotionIndex = ', this.playingMotionIndex, ' playingMotions = ', this.playingMotions, ' playingMotionDurations = ', this.playingMotionDurations);
            duration = this.preProcessPlayMotion(motion);
        }
        // console.log('duration = ', duration);

        // set listener for animation completion
        this.mainCjs.addEventListener("animationComplete", this.animationCompletedCallback);

        let notPlayMotion =
            motion.includes('additional') // additional_mortal_A 등은 메인 모션 재생 x
            || motion.includes('effect_only') // 모션 only 인경우 재생 x (ABILITY_EFFECT_ONLY, ....)
        // play animation
        if (!(Player.c_animations.isSummoning(motion) || Player.c_animations.isRaidAppear(motion))) { // the check is a hack to avoid character moving during summoning
            // 실제 재생부, addSpecial 등으로 gotoAndPlay 한다 해도, 여기서 gotoAndPlay 로 motion 을 직접 재생해줘야 함.
            if (!notPlayMotion) {
                this.mainCjs[this.mainCjs.name].gotoAndPlay(motion);
            }
        }

        // create main tween, all the tweens are merely used to keep track of the animation durations
        let isLooping = this.isLooping;
        this.mainTween = createjs.Tween.get(this.mainCjs, { // instead of stage, use individual cjs to getTween
            useTicks: true,
            override: true,
            paused: false, // we do not pause
        }).wait(duration).call(function (tween, cjs) {
            if (isLooping)
                cjs.dispatchEvent('animationComplete')
        }, [this, this.mainCjs]);
    }

// called when the animation is completed
    animationCompleted(event) { // 주의! event.target === this.mainCjs[this.mainCjs.name]
        // clean up listener
        event.target.removeEventListener("animationComplete", this.animationCompletedCallback);
        if (this.effectCjs) {  // if there is a special, clean up
            this.cjsStage.removeChild(this.effectCjs);
            this.effectCjs = null;
            this.abilityCjsType = null;
        }
        if (this.multiAttackEffectCjses.length > 0) {
            this.cjsStage.removeChild(...this.multiAttackEffectCjses);
            this.multiAttackEffectCjses = [];
        }
        if (this.additionalCjs != null) {
            this.cjsStage.removeChild(this.additionalCjs);
            this.additionalCjs = null;
        }

        this.playingMotionIndex++;
        this.mainTween.pause();
        this.mainTween = null;
        this.mainCjs.visible = true; // additionalSpecial 사용시 false 로 변하므로 초기화

        if (event.target.hasEventListener('syncPlay')) { // syncPlay 중에는 재생한 모션까지만 하고, 다음 재생없이 return;
            return; // 바로 리턴해줘야 looping 상태를 유지하면서 wait 상태를 유지함
        }

        if (this.playingMotionIndex >= this.playingMotions.length) {
            // 마지막 모션, 후처리 시작
            event.target.removeEventListener("syncPlay", this.syncPlayCallback); // 보험

            this.playingMotionIndex = 0; // 모션 인덱스 초기화
            this.playingMotions = []; // 모션 초기화
            this.playingMotionDurations = [];
            this.playingEffectCjses = [];
            this.attackMultiHitCount = 1;
            this.isLooping = true; // 기본 isLooping 을true 로 해야 다음 모션을 이어서 재생함

            if (this.isEffect()) return; // 이펙트 플레이어는 wait 상태로 돌리지 않고 그대로 종료

            let waitMotion = Player.c_animations.WAIT;
            if (this.isCharacter()) {
                waitMotion = Player.c_animations.STB_WAIT;
            } else { // 적
                let standbyMotion = $('.enemy-info-container').attr('data-standby-motion');
                waitMotion = standbyMotion !== 'none' ? standbyMotion : Player.c_animations.WAIT;
            }
            this.playingMotions.push(waitMotion);
            this.playingMotionDurations.push(this.preProcessPlayMotion(waitMotion));
            this.playMotion(this.playingMotions[this.playingMotionIndex]); // wait play
        } else {
            // 마지막 모션 아님, 다음모션 시작
            this.playMotion(this.playingMotions[this.playingMotionIndex]); // play next motion
        }
    }

    syncPlay(event) {
        // console.log('[syncPlay] actor.index = ', this.actorIndex, 'motion = ', this.playingMotions[this.playingMotionIndex], ' cjs = ', this.mainCjs);
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
            console.warn("cjs is null", 'this.actor = ', this.actorId, ' effectCjs = ', this.effectCjs, ' motions = ', this.playingMotions, ' motionIndex = ', this.playingMotionIndex, ' abilityCjsType = ', this.abilityCjsType); // fatalChain 의 burstNNN.js 사용시 undefined cjs 가 넘어옴. 이펙트 재생에는 문제없으므로 스킵
            return 0; // 주인공이 어빌리티 사용시 ab_motion 이 없어서 null 나옴
        }
        if (!(cjs instanceof createjs.MovieClip)) // shouldn't happen
            return null;
        else if (cjs.timeline.duration)
            return +cjs.timeline.duration;
        else // default for fallback purpose
            return +cjs.timeline.Id;
    }

    getAbilityCjsName(moveType) {
        let abilityObj = this.animation.abilities[moveType]; // this is nullable when ability doesn't have their own effect
        return abilityObj ? abilityObj.cjs : null; // will use global efect or only motion
    }

// return the next motion in the play list
    nextMotion() {
        let next_index = this.playingMotionIndex + 1;
        if (next_index >= this.playingMotions.length)
            next_index = 0;
        return this.playingMotions[next_index];
    }

    instantiateEffectCjs(cjsName) {
        console.log('[instantiateEffectCjs] cjsName = ', cjsName);
        if (!cjsName) throw new Error("not valid cjsName. cjsName =" + cjsName);
        let cjs = new lib[cjsName];
        cjs.name = cjsName;
        cjs.getPlayableCjs = function (postFix = '') {
            if (postFix) postFix = '_' + postFix;
            return this[this.name][this.name + postFix];
        }
        return cjs; // cjs Container
    }

// instantiate the main element (i.e. the main animation, character, etc...)
    instantiateMainCjs(cjs) {
        let element = new lib[cjs];
        element.name = cjs; // set name
        element.getMotionCjs = function (motion) {
            return this[this.name][this.name + '_' + motion];
        }
        // set position to position offset
        element.x = this.m_offset.position.x;
        element.y = this.m_offset.position.y;
        // apply scaling
        element.scaleX *= this.characterScaling;
        element.scaleY *= this.characterScaling;
        // note: zindex default to 0 (BOTTOM)
        return element;
    }

    addEffectsToStage() {
        console.log('[addEffectsToStage] effectCjses = ', this.effectCjs);
        cjsStage.addChild(this.effectCjs);
    }

// instantiate an auto attack effect (phit file)
    addAttack() {
        // console.log('[addAttack] cjs = ', cjs);
        let attackCjs = this.effectCjs;

        // atk.stop();
        // set position to target offset
        attackCjs.x = this.m_offset.target.x;
        attackCjs.y = this.m_offset.target.y;
        // apply scaling
        attackCjs.scaleX *= this.effectScaling;
        attackCjs.scaleY *= this.effectScaling;

        // set zindex to be on top
        // this.player.alterStageIndex(atk, Player.z_index.TOP);
        // play the animation
        // atk[cjs].gotoAndPlay(6); // always 6

        let multiHitCount = this.attackMultiHitCount;
        if (multiHitCount > 1) {
            let tick = 1;
            let multiHitAtk = null;
            let multiAttackEffectCjses = this.multiAttackEffectCjses;
            let attackCjsName = this.effectCjs.name;
            let offsetX = this.m_offset.target.x;
            let offsetY = this.m_offset.target.y;
            createjs.Ticker.addEventListener("tick", playMultiHit);

            function playMultiHit() {
                tick++;
                if (tick % 3 === 0) {
                    multiHitAtk = new lib[attackCjsName];
                    multiHitAtk.x = offsetX;
                    multiHitAtk.y = offsetY;
                    cjsStage.addChild(multiHitAtk)
                    multiAttackEffectCjses.push(multiHitAtk);
                }
                if (tick >= 3 * (multiHitCount - 1) || tick > 50) {
                    createjs.Ticker.removeEventListener('tick', playMultiHit);
                }
            }
        }

        return attackCjs;
    }


    // instantiate a charge attack (sp file)
    addSpecial() { // must pass the associated animation data
        // console.log("addSpecial, cjsName = ", cjsName, "animation = ", animation, "isAdditional = ", isAdditional)

        let specialCjs = this.effectCjs;
        // 오의 스킵 시작 이펙트
        if (this.isChargeAttackSkip && this.chargeAttackStartFrame > 0) player.getGlobalActor().playMotion('mortal_A');

        // 시작 프레임 지정시 및 재생
        let baseChild = specialCjs[specialCjs.name][specialCjs.name + '_special'];
        let specialChildToPlay = null; // 실제 재생되는 child, gotoAndPlay(startFrame) 이 먹는 위치.
        specialChildToPlay = Object.values(baseChild).find(value => value instanceof createjs.MovieClip && value !== baseChild.parent) || baseChild;
        // 캐릭터 파트 시작프레임 지정 (현재 야치마 only, 불안정)
        let specialChildCharacterPart = null;
        let characterPartStartFrame = 0;
        if (specialCjs.name.includes('3040566000')) {// 야치마
            specialChildCharacterPart = specialChildToPlay['mc_first_npc_all'];
            characterPartStartFrame = 118; // specialChildToPlay.mc_first_npc_all 의 frame_ 확인
        }

        let startFrame = this.chargeAttackStartFrame > 0 ? this.chargeAttackStartFrame : 0
        if (this.isChargeAttackSkip && this.chargeAttackStartFrame > 0) { // nsp, sp 를 제외한 효과 (raid_mortal_skip 등) 은 자동재생으로 ok
            // console.log('addSpecial, specialChildToPlay', specialChildToPlay);
            createjs.Ticker.on("tick", function () { // 한틱 후, 한번만 실행
                baseChild.gotoAndPlay(startFrame);
                specialChildToPlay.gotoAndPlay(startFrame);

                // 캐릭터파트 강제재생 (불안정)
                if (specialChildCharacterPart && characterPartStartFrame > 0) {
                    createjs.Ticker.on('tick', function () {
                        specialChildCharacterPart.gotoAndPlay(characterPartStartFrame);
                    }, null, true);
                }
            }, null, true);
        }

        if (this.isEnemy) { // if it's an enemy animation
            specialCjs.x = this.m_offset.target.x;
            specialCjs.y = this.m_offset.target.y;
            specialCjs.scaleX *= this.effectScaling;
            specialCjs.scaleY *= this.effectScaling;
        } else if (this.isCharacter()) { // else player or weapon
            // newer "fullscreen" animations cover all the screen
            // and have s2 or s3 in their file names
            if (specialCjs.name.includes("_s2") || specialCjs.name.includes("_s3")) {
                specialCjs.x = this.m_offset.fullscreen.x;
                specialCjs.y = this.m_offset.fullscreen.y;
                specialCjs.scaleX *= this.m_fullscreen_scale;
                specialCjs.scaleY *= this.m_fullscreen_scale;
                this.player.alterStageIndex(specialCjs, Player.z_index.UNDER_TOP); // 캐릭터가 mortal skip effect 보다 하나아래
            } else { // regular ones
                specialCjs.x = this.m_offset.target.x;
                specialCjs.y = this.m_offset.target.y + this.m_offset.special.y;
                this.player.alterStageIndex(specialCjs, Player.z_index.BOTTOM);
            }
        } else if (this.isEffect()) { // 최상위로 추가
            specialCjs.x = this.m_offset.special.x;
            specialCjs.y = this.m_offset.special.y;
            specialCjs.scaleX *= this.effectScaling;
            specialCjs.scaleY *= this.effectScaling;
        } else {
            throw new Error("Invalid special animation, cjs: " + specialCjs.name);
        }

        return specialCjs;
    }

// instantiate a summon
    addSummon() {
        // console.log('addSummon', cjs);
        let summonCjs = this.effectCjs;
        // the newer files are in two files (attack and damage)
        // both seems to use the fullscreen offset
        if (summonCjs.name.includes("_attack") || summonCjs.name.includes("_damage")) {
            summonCjs.x = this.m_offset.fullscreen.x;
            summonCjs.y = this.m_offset.fullscreen.y;
            summonCjs.scaleX *= this.m_fullscreen_scale;
            summonCjs.scaleY *= this.m_fullscreen_scale;
        } else { // old summons (N, R, ...)
            // set to target
            summonCjs.x = this.m_offset.target.x;
            summonCjs.y = this.m_offset.target.y;
            this.player.alterStageIndex(summonCjs, Player.z_index.TOP);
            // summon.gotoAndPlay(0);
        }
        // summon.gotoAndPlay(0);

        // apply scaling
        summonCjs.scaleX *= this.effectScaling;
        summonCjs.scaleY *= this.effectScaling;
        return summonCjs;
    }

// instantiate a skill effect
    addAbility(isAoe) { // must pass if it's an aoe ability

        let abilityCjs = this.effectCjs;
        if (abilityCjs.name.includes("burst")) player.alterStageIndex(abilityCjs, Player.z_index.UPPER_BOTTOM); // 페이탈체인은 적 바로위로

        // aoe are like fullscreen special
        if (isAoe) {
            if (this.isCharacter()) {
                abilityCjs.x = this.m_offset.fullscreen.x;
                abilityCjs.y = this.m_offset.fullscreen.y;
                abilityCjs.scaleX *= this.m_fullscreen_scale;
                abilityCjs.scaleY *= this.m_fullscreen_scale;
            } else if (this.isEnemy) {
                abilityCjs.x = this.m_offset.fullscreen.x * 0.5; // 적은 aoe 를 왼쪽으로 절반만큼 옮김
                abilityCjs.y = this.m_offset.fullscreen.y * 0.5;
                abilityCjs.scaleX *= this.m_fullscreen_scale;
                abilityCjs.scaleY *= this.m_fullscreen_scale;
            }
        } else {
            // set position according to m_ability_target state
            if (this.isAbilityTargetedEnemy) {
                abilityCjs.x = this.m_offset.target.x;
                abilityCjs.y = this.m_offset.target.y;
                // scale = 1
            } else {
                if (this.isCharacter()) {
                    abilityCjs.x = this.m_offset.position.x;
                    abilityCjs.y = this.m_offset.position.y;
                    abilityCjs.scaleX *= this.characterScaling; // 아군 캐릭터 대상 이펙트 -> 스케일 캐릭터 따라감
                    abilityCjs.scaleY *= this.characterScaling;
                } else if (this.isEnemy) {
                    abilityCjs.x = this.m_offset.special.x;
                    abilityCjs.y = this.m_offset.special.y;
                    abilityCjs.scaleX *= this.effectScaling;
                    abilityCjs.scaleY *= this.effectScaling;
                }
            }
        }
        return abilityCjs;
    }

// instantiate a raid appear animation
    addAppear(cjs) {
        this.mainCjs.visible = false;
        let appearCjs = this.effectCjs;
        // display on top
        this.player.alterStageIndex(appearCjs, Player.z_index.TOP);
        // fullscreen
        appearCjs.x = this.m_offset.fullscreen.x;
        appearCjs.y = this.m_offset.fullscreen.y;
        appearCjs.scaleX *= this.m_fullscreen_scale;
        appearCjs.scaleY *= this.m_fullscreen_scale;
        // apply scaling
        appearCjs.scaleX *= this.effectScaling;
        appearCjs.scaleY *= this.effectScaling;
        return appearCjs;
    }

    /**
     * 오프셋 설정
     * @param w 플레이어 width
     * @param h 플레이어 height
     * @param actorId : Animation.name, 'actor0', 'actor1', ...
     * @param scaling = 1.0
     */
    setOffset(w, h, actorId, scaling = 1.0) {
        this.m_fullscreen_scale = w / Player.c_gbf_animation_width; // = 1
        this.characterScaling = this.isEnemy ? 1.1 : 1.0; // 적 크기 증가
        this.effectScaling = this.isEffect() ? 1.1 : 1.0; // 오의스킵 이펙트 증가
        const center = Player.c_canvas_size / 2.0;
        const characterOffsetMultiplier = [
            {x: 0.0, y: 0.0}, // enemyIndex, none use
            {x: 0.27, y: 0.13},
            {x: 0.40, y: 0.24},
            {x: 0.27, y: 0.35},
            {x: 0.40, y: 0.47},
            {x: 0.0, y: 0.0}, // effect
        ]
        const characterTargetOffsetMultiplier = {x: 0.20, y: 0.30};
        if (this.isEnemy) { // enemy
            this.m_offset.position.x = Math.round(center - w * 0.35 * scaling); // 1.0: 0.30
            this.m_offset.position.y = Math.round(center + h * 0.58 * scaling); // 1.0: 0.50
            // enemy 는 attack, mortal 을 offset.target.x, y 사용하는것으로 되있으나, 실적용 되지 않음 (effectScale 마찬가지)
            this.m_offset.target.x = Math.round(center + w * 0.20 * scaling); // 1.0: 0.25
            this.m_offset.target.y = Math.round(center + h * 0.95 * scaling); // 1.0: 0.40
            this.m_offset.fullscreen.x = Math.round(center - w * 0.5 / scaling);
            this.m_offset.fullscreen.y = Math.round(center - h * 0.5 / scaling);
            // enemy 는 ability 사용시 special 사용
            this.m_offset.special.x = Math.round(center - w * 0.25 * scaling); // 1.0: 0.30
            this.m_offset.special.y = Math.round(center + h * 0.40 * scaling); // 1.0: 0.50
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

        // CHECK 야치마 오의의 경우 약간 잘리는데 _s2 오의의 경우 fullScreenScale 을 약간 키우고 fullscreen.x, y 를 약간 왼쪽 위로 옮겨주면됨
        // CHECK 예시값 : fullScreenScale = 1.05, fullscreen x = center - w * 0.525 , ... 이러면 대략맞음
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

}