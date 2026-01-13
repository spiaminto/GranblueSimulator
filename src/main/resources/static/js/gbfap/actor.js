// variable to fix some specific enemies (enemy_4100663, enemy_4100753, enemy_4100933, enemy_4101033)
var attack_num = 0;

// see view/raid/constant.js in GBF code for those exceptions above

class Actor {

    constructor(actorId) {

        this.actorIndex = null; // initAnimation 에서 설정
        this.actorId = actorId;

        this.isLeaderCharacter = false;
        this.isEnemy = actorId.includes('actor-0'); // initAnimation 에서 하면 offset에 못맞춤

        // animation callback
        this.syncPlayCallback = this.syncPlay.bind(this);

        // textures
        this.weaponTextures = []; // list of weapon texture per version : Array<String> , 주인공의 무기. ['1010300000', '101030000'] (아마 두개인 이유는 그랑 지타 별도로 나뉘어 있어서인듯) 일반 캐릭터는 빈 배열

        // animations
        this.animation = null; // store the Animations data : Animation

        // cjs
        this.mainCjs = null; // store the instantied associated objects : Array<createjs.MovieClip> (DisplayObject → Container → MovieClip)
        this.effectCjs = null; // the playing attack, ability, summon, charge attack effect cjs : createjs.MovieClip
        this.multiAttackEffectCjses = new Set(); // 난격용 이펙트
        this.additionalCjs = null; // for boss's another specials

        this.trackingTween = null;

        // motions
        this.allMotions = []; // list of list of available motions for all animations : Array<String>, ['attack', 'wait', ...], 캐릭터 상한 별로 최대 2개인듯. 선택가능한 모든 모션이 포함됨, 표시용으로만 사용하는듯 현재는 값이 유효하지 않아도 됨.
        this.playingMotion = null;

        // mortal skip
        this.isChargeAttackSkip = false;
        this.chargeAttackStartFrame = 0;

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

        /*
            duration

            0 === 내부에서 timeline 못읽거나 잘못되었을시 0 으로 설정됨
            -1 === 내부에서 직접 스킵시키기 위해 지정함
            -2 === durationSkip = true 일때 지정함

         */

    }

    initAnimation(animation) {
        this.animation = animation;
        this.actorIndex = animation.currentOrder;
        if (!Number.isInteger(this.actorIndex)) this.actorIndex = 99; // 폼체인지 로드중
        this.isLeaderCharacter = animation.isLeaderCharacter;
        this.isEnemy = animation.isEnemy;
        this.isChargeAttackSkip = animation.isChargeAttackSkip;
        this.chargeAttackStartFrame = animation.chargeAttackStartFrame;

        if (animation.weapon != null) {
            this.weaponTextures.push(animation.weapon);
        }
        // load the files
        loader.loadAnimation(this); // 끝나고 나면 initToPlayer() 시작
    }

    initToPlayer() {
        // 원본 그랑블루 사양에 따른 추가할당 (사용하진 않음)
        // let charaId = 'charaID_' + this.actorId.replace(/\D/g, ''); // charaID_1, ...
        // stage.global[charaId] = this.animation.cjs.replace(/\D/g, ''); // charaID 는 숫자만 추출해서 구성된걸로 추정

        // 오프셋 설정
        this.initActorOffset(window.player.m_canvas.width, window.player.m_canvas.height, this.actorId);

        this.mainCjs = (this.instantiateMainCjs(this.animation.cjs));
        this.mainCjs.name = this.animation.cjs;
        if (this.isActor()) { // if actor, add to stage
            window.player.m_stage.addChild(this.mainCjs);
            window.player.m_stage.update();
            if (this.isNextEnemyForm()) return; // 폼체인지 다음 적은 addChild 까지만

            this.mainCjs[this.mainCjs.name].gotoAndPlay(Player.c_animations.WAIT); // 즉시 로드
        }

        let characterStartMotion = player.getCharacterWaitMotion(this.actorIndex);
        let enemyStartMotion = Player.c_animations.WAIT; // 초기 WAIT 지정, 로드 완료되면 entry
        if (this.isCharacter()) {
            this.playingMotion = characterStartMotion;
            this.playMotion(characterStartMotion);
        } else if (this.isEnemy) {
            this.playingMotion = enemyStartMotion;
            this.playMotion(enemyStartMotion);
        }

        let isAllAssetsLoaded = cjsStage.children.length === window.assetInfos.length;
        console.log('[initToPlayer] isAllAssetsLoaded = ', isAllAssetsLoaded, 'assetInfos.length = ', window.assetInfos.length, 'cjsStage.children.length = ', cjsStage.children.length);
        if (isAllAssetsLoaded) {
            $('#loadingOverlay').get(0).dispatchEvent(new Event('actorLoadFinished'));
        }
    }

    play(playRequest, synced = false) {
        // 자신의 이펙트 실행중엔 다음 요청이 이펙트가 아닌경우 무시 (어빌리티 사용중 오의 on 등에 대응)
        if (window.player.effectPlaying
            && window.player.effectPlayingActorIndex === this.actorIndex
            && !playRequest.options.isEffecting) return -1; // 무시후에 request 를 this 에 세팅할것

        if (this.trackingTween) this.animationCompleted(true); // 이전 모션이 실행중일경우 즉시종료

        this.playingMotion = playRequest.motion;
        this.playingOptions = playRequest.options;

        if (this.playingOptions.isEffecting)
            player.setEffectPlaying(true, this.actorIndex);

        // additional se 재생
        playAdditionalSe(this.mainCjs.name, playRequest.motion);

        // playMotion 실제 재생
        let duration = -1;
        if (synced) {
            this.mainCjs.addEventListener('syncPlay', this.syncPlayCallback); // sync 중이면 이벤트리스너 붙이고 재생 x
        } else {
            duration = this.playMotion(this.playingMotion);
        }

        console.debug('[play] actor = ', this.actorId, '\nmotion = ', playRequest.motion, '\nduration = ', duration);

        return duration;
    }

    playMotion(motion) {
        console.debug("[playMotion] START================================================ \nactor = ", this.actorId, "\nmotion = ", motion, "\nmainCjs = ", this.mainCjs);
        if (!(this.mainCjs instanceof createjs.MovieClip)) throw new Error("[playMotion] invalid mainCjs, mainCjs = " + JSON.stringify(cjs)); // check if it's a valid animation
        if (motion === Player.c_animations.NONE) return 0;

        let duration = -1; // CHECK 내부에서 duration 을 최솟값으로 설정할때, -1 로 설정함
        let motionStageIndex = Player.z_index.UNDER_TOP; // 이펙트 발생시 기본 모션위치는 TOP - 1, 이펙트는 TOP (자동), 적의 경우 이펙트 보다 모션 우선인것 같아 TOP 로 설정

        if (Player.c_animations.isAttack(motion)) {
            duration = this.processAttack(motion);

        } else if (this.playingOptions?.abilityType === 'UNION_SUMMON' && Player.c_animations.isAbilityMotion(motion)) { // 합체소환 컷인
            duration = this.processUnionSummonCutin();

        } else if (Player.c_animations.isAbilityMotion(motion)) { // ab_motion, to_stb_wait, ab_motion_win, ab_motion_damage, ab_motion_effect_only 등
            motion = Player.c_animations.getCleanAbilityMotion(motion, this.isEnemy); // ab_motion_damage 시, CHECK 되도록 motion 안건드리도록 고민 해봐야될듯
            duration = this.processAbility(motion);

        } else if (Player.c_animations.isMortal(motion)) { // Charge attacks / specials
            duration = motion.includes('additional')
                ? this.processAdditionalMortal(motion)
                : this.processMortal(motion);
            if (this.isCharacter()) motionStageIndex = Player.z_index.UUNDER_TOP; // 캐릭터 모션은 '모션' - '오의이펙트' - '오의스킵이펙트' 순서

        } else if (Player.c_animations.isSummoning(motion)) {
            duration = this.processSummon(motion);

            // } else if (Player.c_animations.isFormChange(motion)) {
            // Note: does nothing different from default, keeping it this way in case it must be changed / improved

        } else if (Player.c_animations.isRaidAppear(motion)) {
            duration = this.processRaidAppear(motion);

        } else if (Player.c_animations.isDamage(motion)) {
            duration = this.isCharacter() ? 15 : 8; // 캐릭터는 기본 0.5s (15fps), 적은 0.27s (8fps) 로 보임, 연타등 최적화를 위해 계산하지 않음.
            motionStageIndex = null; // 순서 변경 없음

        } else if (Player.c_animations.isWaiting(motion)) {
            duration = this.getCjsDuration(this.mainCjs.getMotionCjs(motion));
            motionStageIndex = Player.z_index.INITIAL;
            if (motion === Player.c_animations.ABILITY) {
                motionStageIndex = window.player.effectPlaying ? Player.c_animations.UNDER_TOP : Player.z_index.TOP; // 좀 구린듯
            }

        } else if (motion === Player.c_animations.ABILITY_UI) {
            duration = this.processUi();
            motionStageIndex = null;

        } else { // default
            // 모션 duration 계산
            console.log('[playMotion] else default, motion = ', motion);
            duration = this.getCjsDuration(this.mainCjs.getMotionCjs(motion));
            if (motion === Player.c_animations.DEAD) {
                motionStageIndex = Player.z_index.TOP;
            }
        }

        console.log('currentFrame character = ', this.actorIndex)

        // 모션 위치조정
        player.alterStageIndex(this.mainCjs, motionStageIndex);

        // syncPlay 이벤트 디스패치
        if (this.effectCjs && !(this.mainCjs.hasEventListener('syncPlay'))) {
            window.player.actors.values()
                .filter(actor => actor.actorId !== this.actorId && actor.mainCjs && actor.mainCjs.hasEventListener('syncPlay'))
                .forEach(actor => requestAnimationFrame(() => actor.mainCjs.dispatchEvent('syncPlay'))); // 약간 지연
        }

        // setTimeout(() => actor.mainCjs.dispatchEvent('syncPlay'), 50)

        let notPlayMotion =
            motion.includes('additional') // additional_mortal_A 등은 메인 모션 재생 x
            || motion.includes('effect_only') // 모션 only 인경우 재생 x (ABILITY_EFFECT_ONLY, ....)
            || Player.c_animations.isSummoning(motion) // the check is a hack to avoid character moving during summoning
            || Player.c_animations.isRaidAppear(motion)

        // play animation, addSpecial 등으로 gotoAndPlay 한다 해도, 여기서 gotoAndPlay 로 motion 을 직접 재생해줘야 함.
        if (!notPlayMotion) {
            this.mainCjs[this.mainCjs.name].gotoAndPlay(motion);
        }

        console.debug('[playMotion] PLAYED==================================================== \nactor = ', this.actorId, 'motion = ', motion, '\nduration = ', duration, '\ncjs = ', this.mainCjs, '\ncjs.motion = ', this.mainCjs.getMotionCjs(motion), '\neffectCjs = ', this.effectCjs, '\nplayingOptions = ', this.playingOptions);

        // duration 보정 (첫 로드 등 duration 이 불안정한 경우 재 계산)
        if (duration === 0) {
            duration = this.getCjsDuration(this.mainCjs.getMotionCjs(motion)) || 30.1;
            console.debug('[playMotion] DEBUG DURATION = 0 RECALC, mainCjs = ', this.mainCjs, 'motion = ', motion, 'index', this.actorIndex, 'duration = ', duration);
        }

        // create main tween, all the tweens are merely used to keep track of the animation durations
        this.trackingTween = createjs.Tween.get(this.mainCjs, { // instead of stage, use individual cjs to getTween
            useTicks: true,
            override: true,
        }).wait(duration).call(function (actor) {
            actor.animationCompleted();
        }, [this]);

        // 최종 duration 변환하여 반환
        let durationMiliSeconds = duration * createjs.Ticker.interval; // fps -> ms
        let ceiledDurationMiliSeconds = Math.ceil(durationMiliSeconds * 100) / 100; // 333.3333 -> 333.33

        return ceiledDurationMiliSeconds;
    }

    /**
     * 재생 종료 콜백
     * @param isCanceled 중지 여부, true 일시 대기모션으로 돌아가지 않음
     */
    animationCompleted(isCanceled = false) {
        // console.log('[animationCompleted] actor.index = ', this.actorIndex, 'motion = ', this.playingMotion, ' cjs = ', this.mainCjs, ' isCanceled = ', isCanceled);

        // clean
        if (this.trackingTween) {
            this.trackingTween.paused = true;
            this.trackingTween = null;
        }
        createjs.Tween.removeTweens(this.mainCjs);

        if (this.effectCjs) {  // 이펙트 초기화
            createjs.Tween.removeTweens(this.effectCjs);
            player.m_stage.removeChild(this.effectCjs);
            this.effectCjs = null;
        }

        if (this.multiAttackEffectCjses.size > 0) { // 난격 초기화
            this.multiAttackEffectCjses.forEach(effect => {
                createjs.Tween.removeTweens(effect)
                player.m_stage.removeChild(effect);
            });
            this.multiAttackEffectCjses.clear();
        }

        if (this.additionalCjs != null) { // 적의 추가오의 main 초기화
            createjs.Tween.removeTweens(this.additionalCjs)
            player.m_stage.removeChild(this.additionalCjs);
            this.additionalCjs = null;
            this.mainCjs.visible = true; // additionalSpecial 사용시 false 임
        }

        // player 이펙트 상태 초기화
        if (window.player.effectPlaying && window.player.effectPlayingActorIndex === this.actorIndex) {
            window.player.setEffectPlaying(false, this.actorIndex);
        }

        // 다음 모션으로 대기모션을 자동으로 재생할지 결정
        let isWaiting = Player.c_animations.isWaiting(this.playingMotion); // 대기모션(wait, stbwait, ability, down) 재생중인지 여부
        let isDead = gameStateManager.getState('hps')[this.actorIndex] <= 0; // dead 일시 damaged 에서 멈춤
        let isFormChange = this.playingMotion === Player.c_animations.ENEMY_FORM_CHANGE;
        let isGlobalEffect = this.isGlobalActor();
        let isQuestCleared = stage.gGameStatus.isQuestCleared;
        let willPlayWaitMotion = !isCanceled && !isWaiting && !isDead && !isFormChange && !isGlobalEffect && !isQuestCleared;

        // console.log('[actor.animationCompleted] actorIndex = ', this.actorIndex, 'currentMotion = ', this.playingMotion, ' willPlayWaitMotion = ', willPlayWaitMotion, 'isWaiting = ', isWaiting, 'isDead = ', isDead, 'isFormChange = ', isFormChange, 'isGlobalEffect = ', isGlobalEffect, 'isQuestCleared = ', isQuestCleared)

        // 대기모션 재생
        if (willPlayWaitMotion) {
            let waitMotion = Player.c_animations.WAIT;
            if (this.isCharacter())
                waitMotion = player.getCharacterWaitMotion(this.actorIndex);
            if (this.isEnemy) {
                let standbyMotion = stage.gGameStatus.omen.motion;
                console.log('[actor.animationCompleted] omen = ', stage.gGameStatus.omen, 'standbyMotion = ', standbyMotion, 'waitMotion = ', waitMotion)
                waitMotion = standbyMotion && standbyMotion !== 'none' ? standbyMotion : Player.c_animations.WAIT;
            }
            this.playingMotion = waitMotion;
            this.playMotion(waitMotion);
        }
    }

    syncPlay(event) {
        // console.log('[syncPlay] actor.index = ', this.actorIndex, 'motion = ', this.playingMotions[this.playingMotionIndex], ' cjs = ', this.mainCjs);
        event.target.removeEventListener("syncPlay", this.syncPlayCallback);
        this.playMotion(this.playingMotion);
    }

    processAttack(motion) {
        let duration = -1;
        let attackMultiHitCount = this.playingOptions.attackMultiHitCount || 1;

        // 몇번째 공격 이펙트인지 확인
        let attackEffectIndex =
            (motion === Player.c_animations.ATTACK || motion === Player.c_animations.ATTACK_SHORT) ? 0
                : motion === Player.c_animations.ATTACK_DOUBLE ? 1
                    : motion === Player.c_animations.ATTACK_TRIPLE ? 2 : 3;
        if (attackEffectIndex >= this.animation.attacks.length) attackEffectIndex = this.animation.attacks.length - 1; // index 초과시 마지막 (이펙트 1~2 개짜리도 있음)

        // 이펙트 초기화 및 add
        let attackCjsName = this.animation.attacks[attackEffectIndex];
        this.effectCjs = this.instantiateEffectCjs(attackCjsName);
        this.setEffectCjsOffset('target');
        cjsStage.addChild(this.effectCjs);

        // 이펙트 duration 계산
        duration = 10; // 캐릭터, 연타시 1타당 기본 길이
        if (this.playingOptions.isLastAttack || motion === Player.c_animations.ATTACK)
            duration = this.getCjsDuration(this.mainCjs.getMotionCjs(motion)); // 풀 모션 길이 사용
        if (this.isEnemy)
            duration = this.getCjsDuration(this.mainCjs.getMotionCjs(motion)); // 적은 모션대로 사용
        if (attackMultiHitCount >= 2)
            duration += 3 * (attackMultiHitCount - 1); // 난격 보정
        if (motion === Player.c_animations.ATTACK_MOTION_ONLY)
            duration = 30; // 어택 모션만 있는경우 30으로 설정 (모션: attack)

        // 난격 있을시 추가재생
        let attackMultiHitPlayCount = attackMultiHitCount - 1 || 0; // 1 부터 시작하는걸 0부터 (attackMultiHitcount = 3 인경우, 실제 추가재생은 2회)
        if (attackMultiHitPlayCount > 0) {
            let tick = 0;
            let multiAttackEffectCjs = null;
            let actor = this;
            let attackCjsName = this.effectCjs.name;
            let offsetX = this.m_offset.target.x;
            let offsetY = this.m_offset.target.y;
            createjs.Ticker.addEventListener("tick", playMultiHit);

            // 난격 이펙트 재생 콜백
            function playMultiHit() {
                tick++;
                if (tick % 3 === 0) { // 12'3' 45'6' 78'9'
                    multiAttackEffectCjs = new lib[attackCjsName];
                    multiAttackEffectCjs.x = offsetX;
                    multiAttackEffectCjs.y = offsetY;
                    cjsStage.addChild(multiAttackEffectCjs);
                    actor.multiAttackEffectCjses.add(multiAttackEffectCjs);
                }
                // if (tick / 3 === 1) {
                //     player.play(Player.playRequest('actor-0', player.getEnemyDamageMotion())); // 1회만 추가재생
                // }
                if (tick >= 3 * attackMultiHitPlayCount || tick > 50) {
                    createjs.Ticker.removeEventListener('tick', playMultiHit);
                }
            }
        }

        return duration;
    }

    /**
     * 어빌리티 이펙트 처리
     * @param motion
     * @return {number}
     */
    processAbility(motion) {
        let duration = -1;
        let isEffectOnlyMotion = motion === Player.c_animations.ABILITY_EFFECT_ONLY; // 일반적으로 이펙트만 있는 모션
        let isPlayingEffectOnly = this.playingOptions.isEffectOnly; // 외부에서 특수한경우 전달 CHECK 나중에 통합?
        let abilityType = this.playingOptions.abilityType;
        let isAbilityTargetedEnemy = abilityType ? this.animation.abilities[abilityType]?.isTargetedEnemy : false;

        // 모션 정상화
        if (isEffectOnlyMotion) {
            motion = this.isEnemy ? Player.c_animations.WAIT : player.getCharacterWaitMotion(this.actorIndex);
            console.log('[processAbility] ABILITY_EFFECT_ONLY motionChanged = ', motion);
        }

        // 이펙트
        let abilityEffectDuration = 0;
        let abilityCjsName = this.getAbilityCjsName(abilityType) || ''; // abilityType 은 요청에서 채워옴
        if (abilityCjsName) { // 어빌리티의 이펙트 cjs 가 존재
            let isFatalChainEffect = abilityCjsName.includes('burst'); // 페이탈체인: burst_341, ... (체인버스트 도 비슷하게 생김, 테스트 x)
            // if (isChainEffect) abilityCjsName = 'mc_' + abilityCjsName + '_root'; // CHECK 후처리 나중에 필요없으면 지우기
            let isAoe = isFatalChainEffect || abilityCjsName.includes('_all_') || abilityCjsName.includes('raid') || abilityCjsName.includes('summon'); // 페이탈 체인, raid_, summon(이경우 BUFF_FOR_ALL) 은 AOE
            let childNamePostfix = isAoe ? "end" : "effect";
            let offsetOption = isAoe
                ? 'fullscreen'
                : isAbilityTargetedEnemy
                    ? 'target'
                    : this.isEnemy
                        ? 'special' // 적의 경우 self 대신 special 로 좌표 조정
                        : 'self';

            // 이펙트 초기화
            this.effectCjs = this.instantiateEffectCjs(abilityCjsName);
            this.setEffectCjsOffset(offsetOption);
            cjsStage.addChild(this.effectCjs);

            if (isFatalChainEffect) player.alterStageIndex(this.effectCjs, Player.z_index.UPPER_BOTTOM); // 페이탈체인은 적 바로위로

            // 이펙트 duration 계산
            abilityEffectDuration = this.getCjsDuration(this.effectCjs.getPlayableCjs(childNamePostfix));
            if (isFatalChainEffect)
                abilityEffectDuration = 40; // 페이탈 체인 40fps 고정 (postfix = 'effect')
        }

        // 모션
        let abilityMotionDuration = this.getCjsDuration(this.mainCjs.getMotionCjs(motion));
        if (motion.includes('win') || motion.includes('down'))
            abilityMotionDuration = 30; // 승리, 다운 모션의 경우 지나치게 길어 30fps 로 컷

        // 최종 duration: 모션과 이펙트중 긴쪽의 duration 을 사용
        duration = Math.max(abilityMotionDuration, abilityEffectDuration);
        if (isPlayingEffectOnly || isEffectOnlyMotion) // effectOnly 면 effect 사용
            duration = abilityEffectDuration;

        return duration;
    }

    /**
     * mortal 이펙트 처리
     * @param motion
     * @return number duration
     */
    processMortal(motion) {
        // console.log("processMortal, cjsName = ", cjsName, "animation = ", animation, "isAdditional = ", isAdditional)
        let duration = -1;

        // 이펙트 초기화 (참고, 적의 경우 필요없음)
        let effectCjs = this.instantiateEffectCjs(this.animation.specials[0]); // mortal 은 이펙트 지정 없이 모션 라벨로 재생
        this.effectCjs = effectCjs;
        let offsetOption = effectCjs.name.includes("_s2") || effectCjs.name.includes("_s3")
            ? 'fullscreen'
            : this.isGlobalActor()
                ? 'special' // mortal skip
                : 'target'; // else target
        this.setEffectCjsOffset(offsetOption);
        cjsStage.addChild(this.effectCjs);

        // let stageIndex = Player.z_index.TOP; // 캐릭터는 오의모션 - 오의이펙트 - 오의스킵이펙트, 스킵이펙트가 나중에 재생되므로 오의 이펙트 TOP 으로 올려도됨
        // window.player.alterStageIndex(this.effectCjs, stageIndex); // 기본 TOP 이라 필요없을듯

        // 캐릭터 오의 스킵 활성화시 프레임 지정 재생 처리
        let hasCharacterPart = false;
        if (!this.isEnemy && this.isChargeAttackSkip && this.chargeAttackStartFrame > 0) {
            // 오의 스킵 시작 이펙트 동시재생
            player.getGlobalActor().playMotion('mortal_A');

            let startFrame = this.chargeAttackStartFrame; // 이펙트 시작 프레임
            let characterPartStartFrame = 0; // 이펙트 중 캐릭터 파트 시작 프레임 [실험용]
            let baseChild = effectCjs[effectCjs.name][effectCjs.name + '_special']; // 기본 재생되는 이펙트

            // 스킵을 통해 강제로 재생할 이펙트 파트
            let specialChildToPlay = null; // 실제 재생되는 child, gotoAndPlay(startFrame) 이 먹는 위치.
            specialChildToPlay = Object.values(baseChild).find(value => value instanceof createjs.MovieClip && value !== baseChild.parent) || baseChild;

            // 스킵을 통해 강제로 재생할 이펙트 중 캐릭터 파트 [실험용]
            let specialChildCharacterPart = null;
            if (effectCjs.name.includes('3040566000')) {// 야치마
                specialChildCharacterPart = specialChildToPlay['mc_first_npc_all'];
                characterPartStartFrame = 118; // specialChildToPlay.mc_first_npc_all 의 frame_ 확인
                hasCharacterPart = true;
            }

            // console.log('[processMortal] specialChildToPlay = ', specialChildToPlay);
            createjs.Ticker.on("tick", function () {
                // 이펙트 파트 재생
                baseChild.gotoAndPlay(startFrame);
                specialChildToPlay.gotoAndPlay(startFrame);

                // 캐릭터 파트 강제재생 [실험용]
                if (specialChildCharacterPart && characterPartStartFrame > 0)
                    createjs.Ticker.on('tick', () => specialChildCharacterPart.gotoAndPlay(characterPartStartFrame), null, true); // 한번만 재생

            }, null, true); // 한번만 재생
        }

        // 모션 duration 계산
        duration = this.getCjsDuration(this.mainCjs.getMotionCjs(motion)); // 모션 길이
        if (this.isLeaderCharacter && this.weaponTextures.length > 0)
            duration = this.getCjsDuration(effectCjs.getPlayableCjs('special')); // 주인공, 무기 있을경우 무기 이펙트 길이 사용
        if (this.isChargeAttackSkip && this.chargeAttackStartFrame > 0)
            duration -= this.chargeAttackStartFrame + 9; // 오의 스킵시 보정
        if (this.isChargeAttackSkip && hasCharacterPart)
            duration -= 3; // 캐릭터 파트 있을시 살짝 보정
        if (this.isGlobalActor())
            duration = 10; // effect.mortal_A -> 오의어택 스킵 preEffect

        return duration;
    }

    /**
     * 적의 주가 mortal 처리
     * @return number duration
     */
    processAdditionalMortal(motion) {
        let duration = -1;

        // 모션 정상화
        let replacedMotion = motion.replace('additional_', ''); // additional_mortal_A -> mortal_A

        // 추가 이펙트 재생을 위한 추가 mainCjs 초기화
        this.additionalCjs = this.instantiateMainCjs(this.animation.additionalCjs);
        cjsStage.addChild(this.additionalCjs);
        cjsStage.update(); // 스테이지 갱신
        this.additionalCjs[this.additionalCjs.name].gotoAndPlay(replacedMotion); // 모션 재생을 통한 강제 초기화
        this.mainCjs.visible = false;

        // 추가 이펙트 초기화
        this.effectCjs = this.instantiateEffectCjs(this.animation.additionalSpecials[0]); // mortal 은 이펙트 지정 없이 모션 라벨로 재생
        let offsetOption = this.effectCjs.name.includes("_s2") || this.effectCjs.name.includes("_s3") ? 'fullscreen' : 'target';
        this.setEffectCjsOffset(offsetOption);
        // 적은 이펙트 추가 안해도 됨
        // cjsStage.addChild(this.effectCjs);
        // player.alterStageIndex(this.additionalCjs, Player.z_index.TOP);

        // 모션 duration 계산
        duration = this.getCjsDuration(this.additionalCjs.getMotionCjs(replacedMotion));

        return duration;
    }

    /**
     * 합체소환 컷인 처리
     * @return {number|*}
     */
    processUnionSummonCutin() {
        let summonId = this.playingOptions.summonId;
        let unionSummonId = this.playingOptions.unionSummonId;
        let duration = -1;
        let abilityType = this.playingOptions.abilityType // unionSummon

        // 컷인 이펙트 초기화
        let abilityCjsName = this.getAbilityCjsName(abilityType);
        this.effectCjs = this.instantiateEffectCjs(abilityCjsName);

        // 컷인 이미지 세팅
        console.log('processUnionSummonCutin, summonId = ', summonId, 'unionSummonId = ', unionSummonId);
        let currentSummonCutinSrc = Constants.summon[summonId].cutinSrc;
        let unionSummonCutinSrc = Constants.summon[unionSummonId].cutinSrc;
        let imageUrls = [currentSummonCutinSrc, unionSummonCutinSrc];
        const promises = imageUrls.map((url) => {
            return new Promise((resolve, reject) => {
                const image = new Image();
                image.src = url;
                image.onload = () => resolve(image);
                image.onerror = () => reject(`Image failed to load: ${url}`);
            });
        });
        // 이미지 로드되면 스테이지에 추가
        Promise.all(promises).then((images) => {
            this.effectCjs.raid_union_summon.ui_parts.instance_13.instance.image.src = currentSummonCutinSrc;
            this.effectCjs.raid_union_summon.ui_parts.instance_14.instance.image.src = unionSummonCutinSrc;
            cjsStage.addChild(this.effectCjs);
        });

        duration = this.getCjsDuration(this.effectCjs.getPlayableCjs());
        return duration;
    }

    /**
     * 소환 이펙트 처리
     * @param motion
     * @return {*}
     */
    processSummon(motion) {
        // console.log('addSummon', cjs);
        let summonId = this.playingOptions.summonId;
        if (!summonId) throw new Error("summonId is not provided. motion = " + motion + " cjs = " + this.mainCjs);
        let duration = -1;

        // 이펙트 초기화
        let currentSummonCjsName = this.animation.summons[summonId]; // 'summon_2040080000_02'
        let cjsSuffix = motion === Player.c_animations.SUMMON_DAMAGE ? '_damage' : '_attack'; // CHECK old summons 지원 x
        currentSummonCjsName += cjsSuffix;
        this.effectCjs = this.instantiateEffectCjs(currentSummonCjsName);
        this.setEffectCjsOffset('fullscreen'); // CHECK old summons 지원 x
        cjsStage.addChild(this.effectCjs);

        if (!(this.effectCjs.name in this.effectCjs)) console.error("faisafe, summon_cjs_name = " + this.effectCjs.name + " not found in summon_cjs"); // CHECK old summons

        // 이펙트 duration 계산
        duration = this.getCjsDuration(this.effectCjs.getPlayableCjs());
        if (this.effectCjs.name.includes("2040425000"))
            duration -= 2; // adjust for triple zero

        return duration;
    }

    /**
     * 레이드 입장 이펙트 처리 (테스트 x)
     * @param motion
     * @return {number|*}
     */
    processRaidAppear(motion) {
        let duration = -1;

        // 이펙트 초기화
        let appearIndex = parseInt(motion.split('_')[2]);
        let appearCjsName = this.animation.raidAppear[appearIndex];
        this.effectCjs = this.instantiateEffectCjs(appearCjsName);
        this.setEffectCjsOffset('fullscreen');
        cjsStage.addChild(this.effectCjs);

        this.mainCjs.visible = false;

        // 이펙트 duration 계산
        duration = this.getCjsDuration(this.effectCjs.getPlayableCjs());

        // display on top
        window.player.alterStageIndex(this.effectCjs, Player.z_index.TOP);

        return duration;
    }

    /**
     * UI 처리, 기본적으로 abilityType 사용
     */
    processUi() {
        let duration = 0;
        // let cjsName = this.animation.abilities[this.playingOptions.abilityType].cjs;
        let cjsName = this.getAbilityCjsName(this.playingOptions.abilityType);
        console.log('[processUi] cjsName = ', cjsName);
        if (cjsName) {
            let effectCjs = this.instantiateEffectCjs(cjsName);
            this.effectCjs = effectCjs;
            cjsStage.addChild(effectCjs);

            setTimeout(() => effectCjs[effectCjs.name].gotoAndPlay(8), 300); // QUEST_CLEAR, QUEST_FAILED
            duration = this.getCjsDuration(effectCjs.getPlayableCjs('end')) + 10;
        }

        return duration;
    }

    /**
     * cjs 의 duration 을 구함 (fps)
     * @param playableCjs 실제 타임라인 참조가 가능한 cjs
     * @return {*|number} fps
     */
    getCjsDuration(playableCjs) {
        if (!playableCjs) {
            // fatalChain 의 burstNNN.js 사용시 undefined cjs 가 넘어옴. 이펙트 재생에는 문제없으므로 스킵
            // 주인공이 어빌리티 사용시 ab_motion 이 없어서 null 나옴
            console.warn("[getCjsDuration] cjs is null", '\nthis.actor = ', this.actorId, '\nmotion = ', this.playingMotion, '\neffectCjs = ', this.effectCjs, '\nplayingOptions = ', this.playingOptions);
            return 0;
        }
        let duration = playableCjs.timeline?.duration;
        if (!duration && duration !== 0) throw new Error("[getCjsDuration] invalidDuration \nduration = " + duration + "\nmotion = " + this.playingMotion + "\ncjs = " + playableCjs);
        return duration;
    }

    /**
     * moveType 에 맞는 abilityCjsName 반환
     * @param moveType
     * @return {*|null} abilityCjsName
     */
    getAbilityCjsName(moveType) {
        let abilityObj = this.animation.abilities[moveType]; // this is nullable when ability doesn't have their own effect
        return abilityObj ? abilityObj.cjs : null; // will use global effect or only motion
    }

    isGlobalActor() {
        return this.actorId.includes('global');
    }

    isActor() {
        return this.actorId.includes('actor');
    }

    isCharacter() {
        return !this.isEnemy && this.isActor();
    }

    isNextEnemyForm() { // 폼체인지 다음 폼 로드시 사용
        return this.isEnemy && window.player.actors.values().filter(actor => actor.isEnemy).toArray().length >= 2;
    }

    /**
     * 캐릭터(적) 메인 cjs container 를 인스턴스화 한 후 반환
     * @param cjsName
     * @returns cjs container
     */
    instantiateMainCjs(cjsName) {
        if (!cjsName) throw new Error("[instantiateMainCjs] invalid cjsName \ncjsName =" + cjsName);
        let element = new lib[cjsName];
        element.name = cjsName; // set name
        element.getMotionCjs = function (motion) {
            return this[this.name][this.name + '_' + motion]; // 실제 timeline 을 참조할 수 있는 cjs 를 반환
        }
        // set position to position offset
        element.x = this.m_offset.position.x;
        element.y = this.m_offset.position.y;
        element.scaleX *= this.characterScaling;
        element.scaleY *= this.characterScaling;
        return element;
    }

    /**
     * 이펙트 cjs container 를 인스턴스화 한 후 반환
     * @param cjsName
     * @returns cjs container
     */
    instantiateEffectCjs(cjsName) {
        // console.log('[instantiateEffectCjs] cjsName = ', cjsName);
        if (!cjsName) throw new Error("[instantiateEffectCjs] invalid cjsName \ncjsName =" + cjsName);
        let cjs = new lib[cjsName];
        cjs.name = cjsName;
        cjs.getPlayableCjs = function (postFix = '') {
            if (postFix) postFix = '_' + postFix;
            return this[this.name][this.name + postFix]; // 실제 timeline 을 참조할 수 있는 cjs 를 반환
        }
        return cjs; // cjs Container
    }

    /**
     * 이펙트 cjs 오프셋 지정
     * @param option self, target, fullscreen, special
     */
    setEffectCjsOffset(option = 'self') {

        let effectCjs = this.effectCjs;

        if (option === 'self') {
            // party: ability(self)
            // enemy: ability(AOE)
            effectCjs.x = this.m_offset.position.x;
            effectCjs.y = this.m_offset.position.y;
            effectCjs.scaleX *= this.characterScaling; // 아군 캐릭터 대상 이펙트 -> 스케일 캐릭터 따라감
            effectCjs.scaleY *= this.characterScaling;
        } else if (option === 'fullscreen') {
            // party: ability(AOE), mortal(s2, s3), summon(attack, damage)
            effectCjs.x = this.m_offset.fullscreen.x;
            effectCjs.y = this.m_offset.fullscreen.y + 25; // y축으로 살짝만 내림
            effectCjs.scaleX *= this.m_fullscreen_scale;
            effectCjs.scaleY *= this.m_fullscreen_scale;
        } else if (option === 'target') {
            // party: summon(old), ability(targetedEnemy)
            effectCjs.x = this.m_offset.target.x;
            effectCjs.y = this.m_offset.target.y;
            effectCjs.scaleX *= this.effectScaling;
            effectCjs.scaleY *= this.effectScaling;
        } else if (option === 'special') {
            // global: mortal(mortal_skip)
            // enemy: ability(self)
            effectCjs.x = this.m_offset.special.x;
            effectCjs.y = this.m_offset.special.y;
            effectCjs.scaleX *= this.effectScaling;
            effectCjs.scaleY *= this.effectScaling;
        }

        /*
        enemy: AOE (일단 self 로 )
        abilityCjs.x = this.m_offset.fullscreen.x * 0.5; // 적은 aoe 를 왼쪽으로 절반만큼 옮김 -> 예전 힐효과 때문인듯? 아마 필요없을듯
        abilityCjs.y = this.m_offset.fullscreen.y * 0.5;
         */

    }

    /**
     * 오프셋 설정
     * @param w 플레이어 width
     * @param h 플레이어 height
     * @param actorId : Animation.name, 'actor0', 'actor1', ...
     * @param scaling = 1.0
     */
    initActorOffset(w, h, actorId, scaling = 1.0) {
        this.m_fullscreen_scale = 1;
        this.characterScaling = this.isEnemy ? 1.1 : 1.0; // 적 크기 증가
        this.effectScaling = this.isGlobalActor() || this.isEnemy ? 1.1 : 1.0; // 적, 글로벌 이펙트 크기증가 (글로벌이펙트는 오의 스킵때문)
        const center = 320;
        const characterOffsetMultiplier = [
            {x: 0.0, y: 0.0}, // enemyIndex, none use
            {x: 0.25, y: 0.13},
            {x: 0.39, y: 0.24},
            {x: 0.25, y: 0.35},
            {x: 0.39, y: 0.47},
            {x: 0.0, y: 0.0}, // effect
        ]

        const characterTargetOffsetMultiplier = {x: 0.20, y: 0.30};
        if (this.isEnemy) { // enemy
            this.m_offset.position.x = Math.round(center - w * 0.37 * scaling); // 1.0: 0.30
            this.m_offset.position.y = Math.round(center + h * 0.58 * scaling); // 1.0: 0.50
            // enemy 는 attack, mortal 을 offset.target.x, y 사용하는것으로 되있으나, 실적용 되지 않음 (effectScale 마찬가지)
            this.m_offset.target.x = Math.round(center + w * 0.20 * scaling); // 1.0: 0.25
            this.m_offset.target.y = Math.round(center + h * 0.95 * scaling); // 1.0: 0.40
            this.m_offset.fullscreen.x = Math.round(center - w * 0.5 / scaling);
            this.m_offset.fullscreen.y = Math.round(center - h * 0.5 / scaling);
            // enemy 는 ability 사용시 special 사용
            this.m_offset.special.x = Math.round(center - w * 0.25 * scaling); // 1.0: 0.30
            this.m_offset.special.y = Math.round(center + h * 0.40 * scaling); // 1.0: 0.50
        } else if (this.isGlobalActor()) {
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
            this.m_offset.special.x = Math.round(0.15 * w / scaling);
            this.m_offset.special.y = Math.round(0.15 * h / scaling);
        }

        // CHECK 야치마 오의의 경우 약간 잘리는데 _s2 오의의 경우 fullScreenScale 을 약간 키우고 fullscreen.x, y 를 약간 왼쪽 위로 옮겨주면됨
        // CHECK 예시값 : fullScreenScale = 1.05, fullscreen x = center - w * 0.525 , ... 이러면 대략맞음
    }

    //
    // setMotionLists() {
    //     let new_lists = [];
    //     const animation = this.animation;
    //     let cjs = this.mainCjs;
    //     let motion_list = [];
    //     if (animation.summons.length > 0) { // special "hacky" exception for summons
    //         // there are two types : attack + damage / old ones
    //         if (animation.specials.length > 0 && animation.specials[0].includes("_attack")) {
    //             motion_list = ["summon", "summon_atk", "summon_dmg"]; // set list to character summon, summon atk and summon dmg
    //         } else {
    //             motion_list = ["summon", "summon_atk"]; // set list to character summon and summon atk
    //         }
    //     }
    //     let unsorted_motions = [];
    //     for (const motion in cjs[cjs.name]) { // iterate over all keys
    //         let motion_str = motion.toString();
    //         if (motion_str.startsWith(cjs.name)) { // a motion always start with the file name
    //             // hack to disable ougi options on mc beside mortal_B
    //             if (animation.isMainCharacter
    //                 && motion_str.includes("mortal")
    //                 && ((
    //                         animation.weapon == null
    //                         && !motion_str.endsWith("_mortal_B"))
    //                     || (
    //                         animation.weapon != null
    //                         && ["_1", "_2"].includes(motion_str.slice(-2)))
    //                 )
    //             ) continue;
    //             // remove the file name part
    //             motion_str = motion_str.substring(cjs.name.length + 1);
    //             // add to list
    //             unsorted_motions.push(motion_str);
    //         }
    //     }
    //     // add appear animation
    //     if (animation.isEnemy) {
    //         for (let i = 0; i < animation.raidAppear.length; ++i) {
    //             unsorted_motions.push("raid_appear_" + i);
    //         }
    //     }
    //     if (animation.additionalCjs && animation.additionalSpecials.length > 0 && this.additionalCjs) {
    //         // additional will be start from additional_mortal_a
    //         let additionalCjs = this.additionalCjs;
    //         for (const motion in additionalCjs[additionalCjs.name]) { // iterate over all keys
    //             let motion_str = motion.toString();
    //             if (motion_str.indexOf('mortal') >= 0) { // only mortal will be used
    //                 motion_str = motion_str.substring(cjs.name.length + 1); // mortal_A, mortal_B, ...
    //                 motion_str = 'additional_' + motion_str; // additional_mortal_A, additional_mortal_B, ...
    //                 unsorted_motions.push(motion_str);
    //             }
    //         }
    //     }
    //
    //     // create a table of translate name and motion
    //     let table = {};
    //     for (const m of unsorted_motions) {
    //         table[window.player.translateMotion(m)] = m;
    //     }
    //     // get a list of sorted translated name
    //     const keys = Object.keys(table).sort();
    //     // build motion list according to sorted order
    //     for (const k of keys) {
    //         motion_list.push(table[k]);
    //     }l
    //     // append list to motion list
    //     new_lists.push(motion_list);
    //
    //     // update m_motion_lists
    //     this.allMotions = new_lists;
    // }

}