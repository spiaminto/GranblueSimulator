async function processEnemyAttack(response) {
    let attackCount = response.moveType ===
    MoveType.SINGLE_ATTACK ? 1
        : response.moveType === MoveType.DOUBLE_ATTACK ? 2
            : response.moveType === MoveType.TRIPLE_ATTACK ? 3 : 0;
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
            enemyDamagesPostProcess(response, effectDuration * attackCount);
        }
    }

    let totalEndTime = effectDuration;
    return new Promise(resolve => setTimeout(function () {
        console.log("[processEnemyAttack] DONE total = " + totalEndTime)
        resolve();
    }, totalEndTime + Constants.Delay.globalMoveDelay));
}

async function processEnemyAbility(response) {
    // 이펙트 재생
    let motion = response.motion || 'none';
    let isEffectOnly = motion.includes('ab_');// CHECK 적은 어빌리티 모션이 없으므로, ab_ 어빌리티 모션이 주어지면 effectOnly 로 간주 (나중에 다른 모션을 사용한는 어빌리티의 경우 해당 모션을 고려해야 하므로 ab_ 만 따로 설정)

    let effectDuration = await player.play(Player.playRequest('actor-0', motion, {
        abilityType: response.moveType.name,
        isEffectOnly: isEffectOnly
    }));

    // 데미지 후처리 (데미지 표시, 아군 피격 재생)
    if (response.damages.length > 0) enemyDamagesPostProcess(response, effectDuration);

    // 스테이터스 처리
    let totalEndTime = await processStatusEffect(response);

    console.log('[processEnemyAbility] DONE totalTime', totalEndTime, 'effectDuration ', effectDuration);
    return new Promise(resolve => setTimeout(() => resolve(), Constants.Delay.globalMoveDelay));
}

async function processEnemyChargeAttackPreEffect() {
    return await player.play(Player.playRequest('actor-0', Player.c_animations.ABILITY_EFFECT_ONLY, {
        abilityType: BASE_ABILITY.ENEMY_AB_START,
        isEffectOnly: true
    }), true);
}

async function processEnemyChargeAttack(response) {
    await processEnemyChargeAttackPreEffect();

    if (gameStateManager.getState('omen.type') === OmenType.HP_TRIGGER) {
        // hp 트리거인 경우 트리거 갱신
        gameStateManager.setState('enemyTriggerHps', gameStateManager.getState('enemyTriggerHps'), {force: true});
    }
    gameStateManager.setState('omen', response.omen); // omen 먼저 갱신 (해제)

    // 모션 처리
    let effectDuration = 0;
    let motionCustomDuration = response.motionCustomDuration;
    let hasDamage = response.damages.length > 0;
    effectDuration = await player.play(Player.playRequest('actor-0', response.motion));
    if (!hasDamage || motionCustomDuration) await wait(motionCustomDuration); // 데미지가 없거나, 모션 에 지정된 길이가 존재할경우 기다림 (지정되지 않은경우 데미지 처리를 동시에 진행)
    let damageDuration = effectDuration - motionCustomDuration; // 적 특수기 사용시, 데미지 표시전에 딜레이가 있는 경우 motionCustomDuration 이 지정되어있음. 빼서 사용

    // 데미지 처리
    if (hasDamage) await enemyDamagesPostProcess(response, damageDuration);

    // 상태 갱신
    gameStateManager.setState('chargeGauges', response.chargeGauges);

    // 스테이터스 처리
    let lastDelay = await processStatusEffect(response);

    console.log('[processEnemyChargeAttack] DONE lastDelay = ', lastDelay, ' effectDuration =', effectDuration);
    return new Promise(resolve => setTimeout(() => resolve(), Constants.Delay.globalMoveDelay));
}

async function processEnemyStandBy(response) {

    // 상태 변경
    let beforeOmenActivated = stage.gGameStatus.omen.type || false; // 상태 변경 전, 전조 상태였는지 확인
    let beforeRemainValue = stage.gGameStatus.omen.remainValue || 0;
    if (gameStateManager.getState('omen.type') === OmenType.HP_TRIGGER) {
        // hp 트리거인 경우 트리거 갱신
        gameStateManager.setState('enemyTriggerHps', gameStateManager.getState('enemyTriggerHps'), {force: true});
    }
    gameStateManager.setState('omen', response.omen);
    gameStateManager.setState('chargeGauges', response.chargeGauges);
    let currentRemainValue = response.omen.remainValue;

    // 이펙트 재생
    let effectDuration = 0;
    if (beforeRemainValue !== currentRemainValue) { // 전조값이 변경될때만 이펙트 재생
        effectDuration = await player.play(Player.playRequest('actor-0', response.motion));
        effectDuration = beforeOmenActivated ? effectDuration / 3 : effectDuration / 2; // standby 모션 가속
    }

    let totalEndTime = effectDuration;
    console.log('[processEnemyStandBy] DONE, move = ', response.moveType.name, ' totalEndTime = ', totalEndTime);
    return new Promise(resolve => setTimeout(() => resolve(), totalEndTime));
}

async function processEnemyBreak(response) {
    // 상태 갱신
    window.gameStateManager.setState('omen', response.omen);
    window.gameStateManager.setState('chargeGauges', response.chargeGauges);

    // 이펙트 재생
    let effectDuration = await player.play(Player.playRequest('actor-0', response.motion));

    let totalEndTime = effectDuration;
    return new Promise(resolve => setTimeout(function () {
        console.log('[processEnemyBreak] DONE move =', response.moveType);
        resolve();
    }, totalEndTime + 200));

}

async function processFormChange(formChangeResponse) {
    let enemyActor = player.actors.get('actor-0');
    let nextEnemyActor = player.actors.get('actor-01');
    nextEnemyActor.hpRate = enemyActor.hpRate;
    let formChangeDurationSum = 0;
    // 직전 폼의 폼체인지
    let formChangeDuration = await player.play(Player.playRequest('actor-0', Player.c_animations.ENEMY_FORM_CHANGE))
    await new Promise(resolve => setTimeout(resolve, formChangeDuration));
    // 추가 처리
    player.setBackgroundImage(Constants.enemy[gameStateManager.getState('enemyMainCjsName')].backgroundImage);
    // 다음 폼의 폼체인지 입장 (phase-4)
    let formChangeEntryDuration = await player.play(Player.playRequest('actor-01', Player.c_animations.ENEMY_PHASE_4));
    // 기다리지 않고 일단 이전 적 투명도 0
    enemyActor.mainCjs.alpha = 0;
    await new Promise(resolve => setTimeout(resolve, formChangeEntryDuration));
    // 엔트리 종료 후 속성처리
    enemyActor.actorId = 'actor-00';
    enemyActor.actorIndex = -1;
    nextEnemyActor.actorIndex = 0;
    nextEnemyActor.actorId = 'actor-0';
    nextEnemyActor.animation.name = 'actor-0';
    player.actors.delete('actor-00');
    player.actors.delete('actor-01');
    player.actors.set('actor-0', nextEnemyActor);
    // 직전 폼 완전제거
    cjsStage.removeChild(enemyActor.mainCjs);
    formChangeDurationSum += formChangeDuration + formChangeEntryDuration;

    let totalEndTime = formChangeDurationSum;
    return new Promise(resolve => setTimeout(function () {
        console.log('[processFormChange] DONE totalEndTime = ', totalEndTime);
        resolve();
    }, totalEndTime + 200));
}

async function loadNextEnemyActor() {
    let memberId = $('#memberInfo').data('member-id');
    let assetInfo = null;
    let actorName = null;
    $.ajax({
        url: '/api/enemy-src?memberId=' + memberId,
        type: 'GET',
        async: false,
        success: function (response) {
            assetInfo = response.assetInfo;
            actorName = response.actorName;
            console.log('assetInfo', assetInfo);
        },
        error: function (response) {
            console.log(response);
        }
    });

    if (!assetInfo) {
        alert('적의 정보를 받아오지 못했습니다. 새로고침합니다.');
        location.reload();
        return;
    }
    // 서버에서 받아온 다음 폼 정보 (actor-01 로 임시 설정 후 폼체인지 처리중에 actor-0 로 변경)
    let enemyAnimation = new Animation('actor-01', {
        cjs: assetInfo.asset.mainCjs,
        weapon: assetInfo.asset.weaponId,
        attacks: assetInfo.asset.attackCjses,
        abilities: assetInfo.asset.abilityCjses,
        specials: assetInfo.asset.specialCjses,
        additionalCjs: assetInfo.asset.additionalMainCjs,
        additionalSpecials: assetInfo.asset.additionalSpecialCjses,
        chargeAttackStartFrame: assetInfo.asset.chargeAttackStartFrame,
        summons: assetInfo.asset.summonCjses,
        isEnemy: assetInfo.isEnemy,
        isLeaderCharacter: assetInfo.isLeaderCharacter,
        isChargeAttackSkip: assetInfo.isChargeAttackSkip,
        startMotion: assetInfo.startMotion,
    });
    gameStateManager.setState('enemyMainCjsName', assetInfo.asset.mainCjs);
    console.log('enemyAnimation = ', enemyAnimation);

    loadActor(enemyAnimation); // 스테이지에 로드

    // 적 이름 set
    gameStateManager.setState('enemyActorName', actorName);

    return new Promise(resolve => {
        let interval = setInterval(() => { // 스테이지에 로드되면 resolve
            let found = player.m_stage.children.find(child => child.name === assetInfo.asset.mainCjs);
            if (found) {
                clearInterval(interval);
                resolve();
            }
        }, 500);
    });
}

async function processEnemyDead(response) {
    console.log('[processEnemyDead resp = ', response);

    // 모션 재생
    await player.play(Player.playRequest('actor-0', Player.c_animations.DEAD), true);

    window.gameStateManager.setState('isQuestCleared', true);
    player.lockPlayer(true);

    // 스테이지에서 제거
    player.removeActor(0);

    // 캐릭터 승리모션
    player.actors.values().filter(actor => actor.isCharacter()).forEach(actor => player.play(Player.playRequest(actor.actorId, Player.c_animations.WIN)));

    // 소리
    player.play(Player.playRequest('global', Player.c_animations.ABILITY_UI, {abilityType: 'QUEST_CLEAR'}));

    // 적이 죽으면 모든 처리를 즉시 종료
    return new Promise(resolve => setTimeout(() => resolve(), Constants.Delay.globalMoveDelay));
}

/**
 * 적의 데미지 발생관련 후처리
 * 적은 일반공격, (서포트)어빌리티, 차지어택의 데미지 표시방식 및 아군 피격처리가 거의 동일하므로 통합하여 사용
 *
 * @param response
 * @param damageDuration 데미지가 사용할 duration, 기본적으로 전체 모션 길이를 사용하며, 데미지 표시 전에 딜레이가 있는 경우 전체 모션 길이 - 딜레이 만큼 사용
 */
async function enemyDamagesPostProcess(response, damageDuration) {
    let attackCount = Number(response.moveType.attackCount) || 0;
    let uniqueTargetOrders = [...new Set(response.enemyAttackTargetOrders)];

    let characterCount = player.getCharacters().length;
    let isAllTarget = response.allTarget && characterCount > 1 // 캐릭터가 1명이면 allTarget 이어도 일반공격처럼 표시
    let isAllTargetSubstitute = isAllTarget && uniqueTargetOrders.length === 1; // 전체공격 감싸기 여부
    if (isAllTargetSubstitute) uniqueTargetOrders = Array(player.getCharacters().length).fill(uniqueTargetOrders[0]); // 전체공격 감싸기 상황. 파티 인원만큼 감싸기 캐릭터의 order 늘림.

    let perHitDuration = attackCount === 0
        ? damageDuration / response.damages.length // 어빌리티, 오의시 1타가 사용할 길이
        : damageDuration / attackCount; // 일반공격시 1타가 사용할 길이

    // 후행동 공격데미지와 겹치지 않도록 미리 데미지 래퍼 추가
    let $enemyDamageWrappers = new Map();
    uniqueTargetOrders.forEach(targetOrder => {
        $enemyDamageWrappers.set(targetOrder, $('<div>', {class: 'damage-wrapper enemy actor-' + targetOrder})); // CHECK 캐릭터랑 다르게 순서정보 없음. 차후 필요시 추가
    })

    // console.log('[enemyDamagePostProcess] perHitDuration = ', perHitDuration, ' damageDuration = ', damageDuration, ' isAllTarget =', isAllTarget);

    //  데미지 마다 반복 - 데미지삽입, 데미지표시, 피격이펙트 재생
    let lastDamageShowStartDelay = 0; // 마지막 데미지가 표시시작하는 딜레이
    response.damages.forEach(function (damage, index) {
        let damageShowStartDelay = isAllTarget
            ? perHitDuration * (Math.floor(index / uniqueTargetOrders.length)) + (index % uniqueTargetOrders.length * 100) // 전체공격시 1타 길이로 중복제거한 타겟수만큼 끊어서 시작 딜레이 설정 (123 / 123 / 123...), 타겟별로 100ms 씩 추가 딜레이
            : perHitDuration * index; // 전체공격 아니면 index 만큼 1타 길이 사용
        let targetOrder = response.enemyAttackTargetOrders[index];
        let elementType = response.elementTypes[index];
        lastDamageShowStartDelay = damageShowStartDelay;

        // console.log('[enemyDamagePostProcessForeach] damageShowStartDelay = ', damageShowStartDelay, ' targetOrder = ', targetOrder, ' index = ', index)

        // 데미지 채우기
        let $damageElements = getDamageElement(targetOrder, elementType, 'attack', response.damageTypes[index], index, damage, response.additionalDamages[index], true);
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
                window.gameStateManager.setState('hps', response.hps);
                window.gameStateManager.setState('hpRates', response.hpRates);
            } else {
                // 마지막이 아니면, 임시 hp 로 갱신 : 적의 타수가 많을때, 1번만 갱신하면 너무 빨리 갱신되서 임시로 여러번 갱신하게함.
                let beforeHps = window.gameStateManager.getState('hps');
                let hpDiffs = beforeHps.map((hp, index) => hp - response.hps[index]);
                let hpDiffsPerHit = hpDiffs.map(hpDiff => hpDiff / (response.damages.length - index));
                let tempHps = beforeHps.map((hp, index) => Math.floor(hp - hpDiffsPerHit[index]));
                window.gameStateManager.setState('hps', tempHps);
            }
            // 데미지 표시
            $damageElements.$damage.addClass('enemy-damage-show'); // 본 데미지 표시
            $damageElements.$additionalDamage.children().each(function (index, additionalDamage) { // 추가데미지 표시
                setTimeout(() => $(additionalDamage).addClass('enemy-damage-show'), (index + 1) * 50);
            });
            // 아군 피격 재생
            player.play(Player.playRequest('actor-' + targetOrder, Player.c_animations.DAMAGE));
        }, damageShowStartDelay)
    })

    return new Promise(resolve => setTimeout(() => resolve(), lastDamageShowStartDelay + Constants.Delay.damageShowToNext));
}


/**
 * 적의 데미지 발생관련 후처리
 * 적은 일반공격, (서포트)어빌리티, 차지어택의 데미지 표시방식 및 아군 피격처리가 거의 동일하므로 통합하여 사용
 * 기본적으로 이펙트(모션) 과 동시재생을 전제로함
 * @param response
 * @param effectDuration
 * @param isTurnDamage 턴데미지 스타일용
 */
async function enemyDamagesPostProcessLegacy(response, effectDuration, isTurnDamage = false) {
    let attackCount = Number(response.moveType.attackCount) || 0;
    let uniqueTargetOrders = [...new Set(response.enemyAttackTargetOrders)];

    let characterCount = player.getCharacters().length;
    let isAllTarget = characterCount > 1 && response.moveType !== MoveType.TURN_END_PROCESS
        ? response.allTarget // 캐릭터가 1명이면 allTarget 이어도 일반공격처럼 표시
        : response.totalHitCount >= 2 // 턴종 데미지시 데미지가 2회이상 발생하면 allTarget 으로 간주 (캐릭터별 딜레이 적용 위함)
    let isAllTargetSubstitute = isAllTarget && uniqueTargetOrders.length === 1; // 전체공격 감싸기 여부
    if (isAllTargetSubstitute) uniqueTargetOrders = Array(player.getCharacters().length).fill(uniqueTargetOrders[0]); // 전체공격 감싸기 상황. 파티 인원만큼 감싸기 캐릭터의 order 늘림.

    // let effectHitDelay = Constants.DIASPORA[currentPhase].hitDelay[response.motion] || 0; // 이펙트 시작 ~ 데미지 표시 까지 딜레이, 없으면 0
    let effectHitDelay = 0;
    // let effectHitDuration = effectDuration - effectHitDelay; // 실제로 히트이펙트가 발생하는 기간
    let effectHitDuration = effectDuration;
    let perHitDuration = attackCount === 0
        ? effectHitDuration / response.damages.length // 어빌리티, 오의시 1타가 사용할 길이
        : effectHitDuration / attackCount; // 일반공격시 1타가 사용할 길이
    let lastDamageShowStartDelay = 0; // 마지막 데미지가 표시시작하는 딜레이

    // 후행동 공격데미지와 겹치지 않도록 미리 데미지 래퍼 추가
    let $enemyDamageWrappers = new Map();
    uniqueTargetOrders.forEach(targetOrder => {
        $enemyDamageWrappers.set(targetOrder, $('<div>', {class: 'damage-wrapper enemy actor-' + targetOrder})); // CHECK 캐릭터랑 다르게 순서정보 없음. 차후 필요시 추가
    })

    //  데미지 마다 반복 - 데미지삽입, 데미지표시, 피격이펙트 재생
    response.damages.forEach(function (damage, index) {
        let damageShowStartDelay = isAllTarget
            ? effectHitDelay + perHitDuration * (Math.floor(index / uniqueTargetOrders.length)) + (index % uniqueTargetOrders.length * 100) // 전체공격시 1타 길이로 중복제거한 타겟수만큼 끊어서 시작 딜레이 설정 (123 / 123 / 123...), 타겟별로 100ms 씩 추가 딜레이
            : effectHitDelay + perHitDuration * index; // 전체공격 아니면 index 만큼 1타 길이 사용
        let targetOrder = response.enemyAttackTargetOrders[index];
        let elementType = response.elementTypes[index];
        lastDamageShowStartDelay = damageShowStartDelay;

        // 데미지 채우기
        let $damageElements = getDamageElement(targetOrder, elementType, 'attack', response.damageTypes[index], index, damage, response.additionalDamages[index], true);
        let $enemyDamageWrapper = $enemyDamageWrappers.get(targetOrder);
        $enemyDamageWrapper.append($damageElements.$damage, $damageElements.$additionalDamage);
        // 마지막에 돔에 추가
        // index >= response.damages.length - 1 ? $('#enemyAttackDamageContainer').append(Array.from($enemyDamageWrappers.values())) : null;
        if (index >= response.damages.length - 1) {
            $enemyDamageWrappers.entries().forEach(([key, value], index) => {
                $(`#actorContainer > .actor-${key}`).append(value);
            })
        }

        setTimeout(function () {
            // 상태갱신 (첫번째 데미지가 표시되기 시작하는 시점)
            if (index >= response.damages.length - 1) {
                // 마지막엔 제대로 적용
                window.gameStateManager.setState('hps', response.hps);
                window.gameStateManager.setState('hpRates', response.hpRates);
            } else {
                // 마지막이 아니면, 임시 hp 로 갱신 : 적의 타수가 많을때, 1번만 갱신하면 너무 빨리 갱신되서 임시로 여러번 갱신하게함.
                let beforeHps = window.gameStateManager.getState('hps');
                let hpDiffs = beforeHps.map((hp, index) => hp - response.hps[index]);
                let hpDiffsPerHit = hpDiffs.map(hpDiff => hpDiff / (response.damages.length - index));
                let tempHps = beforeHps.map((hp, index) => Math.floor(hp - hpDiffsPerHit[index]));
                window.gameStateManager.setState('hps', tempHps);
            }
            // 데미지 표시
            let damageShowClassname = isTurnDamage ? 'enemy-turn-damage-show' : 'enemy-damage-show';
            $damageElements.$damage.addClass(damageShowClassname); // 본 데미지 표시
            $damageElements.$additionalDamage.children().each(function (index, additionalDamage) { // 추가데미지 표시
                setTimeout(() => $(additionalDamage).addClass(damageShowClassname), (index + 1) * 50);
            });
            // 아군 피격 재생
            player.play(Player.playRequest('actor-' + targetOrder, Player.c_animations.DAMAGE));
        }, damageShowStartDelay)
    })

    return new Promise(resolve => setTimeout(() => resolve(), lastDamageShowStartDelay + Constants.Delay.damageShowToNext));
}