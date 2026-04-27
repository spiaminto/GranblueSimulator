async function processEnemyAttack(response) {
    let attackCount = response.normalAttackCount;
    let attackMultiHitCount = response.attackMultiHitCount;
    if (attackCount === 0) throw new Error('[processEnemyAttack], attackCount = 0, moveType = ' + response.moveType.name);
    let attackPlayingPromise = null;
    let effectDuration = 0;
    for (let [index, _] of Array(attackCount).entries()) {
        if (attackPlayingPromise) await attackPlayingPromise; // 이전 모션을 기다림
        effectDuration = 0;
        effectDuration = await player.play(Player.playRequest('actor-0', Player.c_animations.ATTACK));
        attackPlayingPromise = new Promise(resolve => setTimeout(function () {
            resolve(effectDuration);
        }, effectDuration));

        if (index === 0) { // 첫번째 모션 재생시 데미지 후처리 한꺼번에 다 함 CHECK 어차피 적은 공격모션이 하나뿐이라 간단하게 작성함.
            postProcessEnemyDamage(response, effectDuration * attackCount);
        }
    }

    let multiHitDelay = attackMultiHitCount > 1 ? 200 : 0; // 히트수 증가있을경우 보정
    let totalEndTime = effectDuration + multiHitDelay;
    // console.log("[processEnemyAttack] DONE total = " + totalEndTime);

    return wait(totalEndTime);
}

async function processEnemyAbility(response) {
    let hasDamage = response.damages.length > 0;
    let isEffectOnly = response.motion === Player.c_animations.ABILITY_EFFECT_ONLY;

    let abilityCjsObj = player.actors.get(`actor-${response.actorOrder}`).animation.abilities[response.moveId];
    let responseCjsObj = response?.visualInfo; // response 로 내려온 이펙트가 첫 로드시 로드되지 않은 이펙트인 경우 지정 (트리거 어빌리티, 변화 어빌리티 등)
    let hasEffect = abilityCjsObj || responseCjsObj;

    if (hasEffect) {
        // 효과있는경우만 인디케이터 갱신
        window.gameStateManager.setState('indicator.moveName', response.moveName);
    }

    // 모션, 이펙트 처리
    let animationDuration = await player.play(Player.playRequest('actor-' + response.actorOrder, response.motion, {
        abilityType: response.moveId,
        cjsName: responseCjsObj?.moveCjsName,
        isTargetedEnemy: responseCjsObj?.isTargetedEnemy,
    }));

    let animationDurationScale = hasEffect ? 1 : 0.25; // 이펙트가 없으면 단축
    if (!hasDamage) { // 데미지 처리가 없을시, 모션을 기다림
        await wait(animationDuration * animationDurationScale);
    }

    // 데미지 처리
    let damageDuration = 0;
    if (response.damages.length > 0) damageDuration = await postProcessEnemyDamage(response, animationDuration); // durationScale 처리 보류

    // 스테이터스 처리
    let statusEffectDelayScale = hasEffect ? 1 : 0.5; // 이펙트가 없으면 단축
    let statusEffectDuration = await processStatusEffect(response, statusEffectDelayScale);

    // 폼 체인지
    if (response.isEnemyFormChange) {
        await processFormChange(response);
    }

    // console.log('[processEnemyAbility] DONE animationDuration', animationDuration, ' damageDuration = ', damageDuration, ' statusEffectDuration = ', statusEffectDuration);
    return animationDuration + damageDuration + statusEffectDuration;
}

async function processEnemyChargeAttackPreEffect() {
    return player.play(Player.playRequest('actor-0', Player.c_animations.ABILITY_EFFECT_ONLY, {
        abilityType: BASE_ABILITY.ENEMY_AB_START.name,
    }), true);
}

async function processEnemyChargeAttack(response) {
    await processEnemyChargeAttackPreEffect();

    let currentOmen = gameStateManager.getState('omen');
    if (currentOmen.type === OmenType.HP_TRIGGER) {
        // HP 트리거 사용시, HP 트리거 제거 (response.omen 갱신보다 우선)
        let triggerHps = gameStateManager.getState('enemyTriggerHps');
        gameStateManager.setState('enemyTriggerHps', triggerHps.filter(hp => hp < currentOmen.lastTriggeredHp));
    }
    gameStateManager.setState('omen', response.omen); // omen 갱신 (해제)

    // 모션 처리
    let effectDuration = 0;
    let hasDamage = response.damages.length > 0;
    let designatedCjsObj = {};
    if (Player.c_animations.isAbilityMotion(response.motion)) {
        designatedCjsObj = response.visualInfo
    }
    effectDuration = await player.play(Player.playRequest('actor-0', response.motion, {
        cjsName: designatedCjsObj?.moveCjsName,
        isTargetedEnemy: designatedCjsObj?.isTargetedEnemy,
    }));

    let enemyActor = player.actors.get('actor-0');
    let cjs = enemyActor.additionalCjs ? enemyActor.additionalCjs : enemyActor.mainCjs;
    let customDuration = (Constants.enemy[cjs.name].customDuration[response.motion] || 0) * Constants.defaultCjsInterval;
    // console.log('[processEnemyChargeAttack] cjs = ', cjs, ' customDuration = ', customDuration);

    if (!hasDamage || customDuration) await wait(customDuration); // 데미지가 없거나, 모션 에 지정된 길이가 존재할경우 기다림 (지정되지 않은경우 데미지 처리를 동시에 진행)
    let damageDuration = effectDuration - customDuration; // 적 특수기 사용시, 데미지 표시전에 딜레이가 있는 경우 motionCustomDuration 이 지정되어있음. 빼서 사용

    // 데미지 처리
    if (hasDamage) await postProcessEnemyDamage(response, damageDuration);

    // 상태 갱신
    // gameStateManager.setState('chargeGauges', response.chargeGauges);

    // 스테이터스 처리
    let lastDelay = await processStatusEffect(response);

    // console.log('[processEnemyChargeAttack] DONE lastDelay = ', lastDelay, ' effectDuration =', effectDuration);
    return effectDuration + lastDelay;
}

async function processEnemyStandBy(response) {
    let effectDuration = 0;

    await wait(Constants.Delay.globalMoveDelay); // 전조 발동시, 약간의 딜레이 후 발동

    // 전조 갱신
    await processStatusEffect(response, 0.5);

    // 이펙트 재생
    effectDuration = await player.play(Player.playRequest('actor-0', response.omen.motion));
    effectDuration /= 2; // 24 프레임 정도만 실제 전조, 일단 반으로 나눠놓음.

    let totalEndTime = effectDuration;
    // console.log('[processEnemyStandBy] DONE, move = ', response.moveType.name, ' totalEndTime = ', totalEndTime);
    return await wait(totalEndTime);
}

async function processEnemyBreak(response) {
    // 상태 갱신
    window.gameStateManager.setState('omen', response.omen);
    window.gameStateManager.setState('chargeGauges', response.chargeGauges);

    // 이펙트 재생
    let effectDuration = await player.play(Player.playRequest('actor-0', response.motion));

    let totalEndTime = effectDuration;
    // console.log('[processEnemyBreak] DONE move =', response.moveType);
    return await wait(totalEndTime);
}

async function processFormChange(response) {
    // 상태변경
    let formChangeInfo = window.formChangeInfo;
    gameStateManager.setState('enemyActorName', formChangeInfo.enemyActorName);
    gameStateManager.setState('enemyFormOrder', formChangeInfo.enemyFormOrder);
    gameStateManager.setState('enemyMainCjsNames', formChangeInfo.enemyMainCjsNames);

    // 이펙트
    let enemyActor = player.actors.get('actor-0');
    let nextEnemyActor = player.actors.get('actor-01');
    let formChangeDurationSum = 0;
    // 직전 폼의 폼체인지
    let formChangeDuration = await player.play(Player.playRequest('actor-0', Player.c_animations.ENEMY_FORM_CHANGE), true);
    // 추가 처리
    player.setBackgroundImage(Constants.raidBgUrl(gameStateManager.getState('enemyMainCjsNames')[0]));
    // 다음 폼의 폼체인지 입장
    let formChangeEntryMotion = formChangeInfo.enemyMainCjsNames[0] === 'enemy_7300843' ? Player.c_animations.ENEMY_PHASE_1 : Player.c_animations.ENEMY_PHASE_4; // 천원은 PHASE_1 사용
    let formChangeEntryDuration = await player.play(Player.playRequest('actor-01', formChangeEntryMotion));
    // 기다리지 않고 일단 이전 적 투명도 0
    enemyActor.mainCjs.alpha = 0;
    updateBgm(response); // 입장 애니메이션과 동시에 bgm 갱신
    await wait(formChangeEntryDuration);

    // 엔트리 종료 후 속성처리
    enemyActor.actorId = 'actor-00';
    enemyActor.actorIndex = -1;
    nextEnemyActor.actorIndex = 0;
    nextEnemyActor.actorId = 'actor-0';
    nextEnemyActor.animation.name = 'actor-0';

    // 기존 적 정리
    enemyActor.animationCompleted(true); // 애니메이션 즉시 종료
    player.actors.delete('actor-00');
    player.actors.delete('actor-01');
    player.actors.set('actor-0', nextEnemyActor);

    // 직전 폼 완전제거
    cjsStage.enemyLayer.removeChild(enemyActor.mainCjs);
    formChangeDurationSum += formChangeDuration + formChangeEntryDuration;

    let totalEndTime = formChangeDurationSum;
    // console.log('[processFormChange] DONE totalEndTime = ', totalEndTime);
    return totalEndTime;
}

async function loadNextEnemyActor() {
    let memberId = $('#memberInfo').data('member-id');
    let assetInfo = null;
    let actorName = null;
    let enemyFormOrder = 0;
    $.ajax({
        url: '/api/enemy-src?memberId=' + memberId,
        type: 'GET',
        async: false,
        success: function (response) {
            assetInfo = response.assetInfo;
            actorName = response.actorName;
            enemyFormOrder = Number(response.formOrder);
            // console.log('assetInfo', assetInfo);
        },
        error: function (response) {
            // console.log(response);
        }
    });

    if (!assetInfo) {
        alert('적의 정보를 받아오지 못했습니다. 새로고침합니다.');
        location.reload();
        return;
    }
    // 서버에서 받아온 다음 폼 정보 (actor-01 로 임시 설정 후 폼체인지 처리중에 actor-0 로 변경)
    let enemyAnimation = new Animation('actor-01', {
        cjs: assetInfo.mainCjs,
        weapon: assetInfo.weaponId,
        attacks: assetInfo.attackCjses,
        abilities: assetInfo.abilityCjses,
        specials: assetInfo.specialCjses,
        additionalCjs: assetInfo.additionalMainCjs,
        additionalSpecials: assetInfo.additionalSpecialCjses,
        chargeAttackStartFrame: assetInfo.chargeAttackStartFrame,
        summons: assetInfo.summonCjses,
        isEnemy: assetInfo.isEnemy,
        isLeaderCharacter: assetInfo.isLeaderCharacter,
        isChargeAttackSkip: assetInfo.isChargeAttackSkip,
    });

    // 상태변경 (임시)
    let enemyMainCjsNames = [assetInfo.mainCjs, ...gameStateManager.getState('enemyMainCjsNames')];
    window.formChangeInfo = {
        enemyActorName: actorName,
        enemyFormOrder: enemyFormOrder,
        enemyMainCjsNames: enemyMainCjsNames
    } // bgm 실시간 처리등의 이유로, processFormChange() 에서 직접 gameStateManager 갱신


    // console.log('[loadNextEnemyActor] next enemyAnimation = ', enemyAnimation);

    loadActor(enemyAnimation); // 스테이지에 로드

    return new Promise(resolve => {
        const interval = setInterval(() => {
            let found = cjsStage.enemyLayer.children.find(child => child.name === assetInfo.mainCjs);
            if (found) {
                clearInterval(interval);
                clearTimeout(timeout);
                resolve();
            }
        }, 500);

        const timeout = setTimeout(() => {
            clearInterval(interval);
            alert("적의 다음 에셋 로드에 실패하였습니다. 새로고침 합니다.");
            location.reload();
        }, 3000);
    });
}

async function processEnemyDead(response) {
    // console.log('[processEnemyDead resp = ', response);

    // 상태 갱신 (hp 등, 타 플레이어에 의해 사망했을경우 내쪽 갱신해야됨)
    processStatusEffect(response);

    // 모션 재생
    await player.play(Player.playRequest('actor-0', Player.c_animations.DEAD), true);

    window.gameStateManager.setState('isQuestCleared', true);
    window.gameStateManager.setState('omen', OmenDto.empty());
    player.lockPlayer(true);

    // 스테이지에서 제거
    player.removeActor(0);

    // 캐릭터 승리모션
    player.actors.values().filter(actor => actor.isCharacter()).forEach(actor => player.play(Player.playRequest(actor.actorId, Player.c_animations.WIN)));

    // 소리
    player.play(Player.playRequest('global', Player.c_animations.ABILITY_UI, {abilityType: 'QUEST_CLEAR'}));
    updateBgm(response, {stopBgm: true});

    // 동기화 종료
    stopSync();

    // 적이 죽으면 모든 처리를 즉시 종료
    return 0;
}

/**
 * 적의 데미지 발생관련 후처리
 * 적은 일반공격, (서포트)어빌리티, 차지어택의 데미지 표시방식 및 아군 피격처리가 거의 동일하므로 통합하여 사용
 *
 * @param response
 * @param damageDuration 데미지가 사용할 duration, 기본적으로 전체 모션 길이를 사용하며, 데미지 표시 전에 딜레이가 있는 경우 전체 모션 길이 - 딜레이 만큼 사용
 * @return {Promise<Number>} 마지막 데미지 가 페이드아웃 할때까지의 시간 (동안 대기)
 */
async function postProcessEnemyDamage(response, damageDuration) {
    let normalAttackCount = response.normalAttackCount || 0;
    let uniqueTargetOrders = [...new Set(response.enemyAttackTargetOrders)];

    let characterCount = player.getCharacters().length;
    let isAllTarget = response.allTarget && characterCount > 1 // 캐릭터가 1명이면 allTarget 이어도 일반공격처럼 표시
    let isAllTargetSubstitute = isAllTarget && uniqueTargetOrders.length === 1; // 전체공격 감싸기 여부
    if (isAllTargetSubstitute) uniqueTargetOrders = Array(player.getCharacters().length).fill(uniqueTargetOrders[0]); // 전체공격 감싸기 상황. 파티 인원만큼 감싸기 캐릭터의 order 늘림.

    let perHitDuration = normalAttackCount > 0
        ? damageDuration / normalAttackCount // 일반공격시 1타가 사용할 길이
        : damageDuration / response.damages.length; // 어빌리티, 오의시 1타가 사용할 길이
    let perHitDurationMin = isAllTarget ? 300 : 50; // 전체공격일때, 최소 75 * 4 + a 만큼 딜레이
    perHitDuration = Math.max(perHitDuration, perHitDurationMin);

    // 후행동 공격데미지와 겹치지 않도록 미리 데미지 래퍼 추가
    let $enemyDamageWrappers = new Map();
    uniqueTargetOrders.forEach(targetOrder => {
        $enemyDamageWrappers.set(targetOrder, $('<div>', {class: 'damage-wrapper enemy actor-' + targetOrder})); // CHECK 캐릭터랑 다르게 순서정보 없음. 차후 필요시 추가
    })

    // console.log('[enemyDamagePostProcess] perHitDuration = ', perHitDuration, ' damageDuration = ', damageDuration, ' isAllTarget =', isAllTarget);

    //  데미지 마다 반복 - 데미지삽입, 데미지표시, 피격이펙트 재생
    let lastDamageShowStartDelay = 0; // 마지막 데미지가 표시시작하는 딜레이
    response.damages.forEach(function (damage, index) {
        let damageShowStartDelay = perHitDuration * index; // 일반 랜덤타겟 N 히트 (또는 일반공격 1히트)
        if (isAllTarget) {
            // 전체공격
            let damageCountPerMotion = uniqueTargetOrders.length; // 전체 공격은 기본적으로, 하나의 모션에 타겟 갯수만큼 데미지 발생
            damageCountPerMotion = response.attackMultiHitCount > 1 ? damageCountPerMotion * response.attackMultiHitCount : damageCountPerMotion; // 히트수증가 또는 난격이 있을경우 해당 값 만큼 하나의 모션에 데미지 발생 (4인대상 전체 일반공격, 2히트 일때 공격 모션 1회당 데미지 8회 발생)
            let perMotionDelay = perHitDuration * (Math.floor(index / damageCountPerMotion)); // 4인 대상 2히트 전체 일반공격시, 12345678 V 9101112...16 의 딜레이
            let perOneDamageDelay = index % damageCountPerMotion * 75; // 데미지 한개당 딜레이
            damageShowStartDelay = perMotionDelay + perOneDamageDelay;
        } else if (response.attackMultiHitCount > 1) {
            // 전체공격이 아닌 멀티히트 (일반공격 2히트 이상)
            let damageCountPerMotion = response.attackMultiHitCount;
            let perMotionDelay = perHitDuration * (Math.floor(index / damageCountPerMotion));
            let perOneDamageDelay = index % damageCountPerMotion * 75;
            damageShowStartDelay = perMotionDelay + perOneDamageDelay;
        }

        let targetOrder = response.enemyAttackTargetOrders[index];
        let elementType = response.elementTypes[index];
        let damageType = response.damageTypes[index];
        lastDamageShowStartDelay = damageShowStartDelay;

        // console.log('[enemyDamagePostProcessForeach] damageShowStartDelay = ', damageShowStartDelay, ' targetOrder = ', targetOrder, ' index = ', index)

        // 데미지 채우기
        let $damageElements = getDamageElement(targetOrder, elementType, 'attack', damageType, index, damage, response.additionalDamages[index], true);
        let $enemyDamageWrapper = $enemyDamageWrappers.get(targetOrder);
        $enemyDamageWrapper.append($damageElements.$damage, $damageElements.$additionalDamage);
        // 마지막에 돔에 추가
        if (index >= response.damages.length - 1) {
            $enemyDamageWrappers.entries().forEach(([key, value], index) => {
                $(`#actorContainer > .actor-${key}`).append(value);
            })
        }

        setTimeout(function () {
            // 상태갱신 (첫번째 데미지가 표시되기 시작하는 시점)
            if (index >= response.damages.length - 1) {
                // 마지막엔 제대로 적용
                gameStateManager.setState('hps', response.hps);
                gameStateManager.setState('hpRates', response.hpRates);
                gameStateManager.setState('barriers', response.barriers);
            } else {
                // 마지막이 아니면, 임시 hp 로 갱신 : 적의 타수가 많을때, 1번만 갱신하면 너무 빨리 갱신되서 임시로 여러번 갱신하게함.
                let beforeBarriers = gameStateManager.getState('barriers');
                let barrierDiffs = beforeBarriers.map((barrier, index) => barrier - response.barriers[index]);
                let barrierDiffsPerHit = barrierDiffs.map(barrierDiff => barrierDiff / (response.damages.length - index));
                let tempBarriers = beforeBarriers.map((barrier, index) => Math.floor(barrier - barrierDiffsPerHit[index]));
                gameStateManager.setState('barriers', tempBarriers);

                let beforeHps = gameStateManager.getState('hps');
                let beforeHpRates = gameStateManager.getState('hpRates');
                let hpDiffs = beforeHps.map((hp, index) => hp - response.hps[index]);

                let hpDiffsPerHit = hpDiffs.map(hpDiff => hpDiff / (response.damages.length - index));
                let tempHps = beforeHps.map((beforeHp, index) => {
                    let tempHp = Math.floor(beforeHp - hpDiffsPerHit[index]);
                    if (tempHp)
                        return Math.floor(beforeHp - hpDiffsPerHit[index]);
                });
                gameStateManager.setState('hps', tempHps);

                if (index === 0) {
                    // 첫번째 데미지 처리시, 데미지 보이스 출력 여부 설정
                    let isFatalDamagedVoice = gameStateManager.getState('isFatalDamagedVoice'); // array, actorIndex
                    // 자신의 체력의 25% 이상의 데미지를 입음
                    hpDiffs.forEach((hpDiff, index) => {
                        if (index === 0) return;
                        let maxHp = beforeHps[index] / beforeHpRates[index] * 100;
                        if (hpDiff > maxHp * 0.25) {
                            isFatalDamagedVoice[index] = true;
                        }
                    });
                    // 빈사 상태임
                    response.hpRates.forEach((hpRate, index) => {
                        if (index === 0) return;
                        if (hpRate <= 25) {
                            isFatalDamagedVoice[index] = true;
                        }
                    })
                    // console.debug('postProcessEnemyDamage fatalDamagedVoice = ', ...isFatalDamagedVoice);
                    gameStateManager.setState('isFatalDamagedVoiceVoice', isFatalDamagedVoice);
                }
            }
            // 데미지 표시
            $damageElements.$damage.addClass('enemy-damage-show'); // 본 데미지 표시
            $damageElements.$additionalDamage.children().each(function (index, additionalDamage) { // 추가데미지 표시
                setTimeout(() => $(additionalDamage).addClass('enemy-damage-show'), (index + 1) * 50);
            });
            // 아군 피격 재생
            if (damageType === 'INEFFECTIVE') {
                // 데미지 무효화
                player.play(Player.playRequest('actor-' + targetOrder, Player.c_animations.ABILITY_EFFECT_ONLY, {abilityType: BASE_ABILITY.DAMAGE_INEFFECTIVE.name}));
            } else {
                player.play(Player.playRequest('actor-' + targetOrder, Player.c_animations.DAMAGE));
            }
        }, damageShowStartDelay)
    });

    return new Promise(resolve => setTimeout(() => {
        resolve();
    }, lastDamageShowStartDelay + Constants.Delay.damageShowToNext));
}
