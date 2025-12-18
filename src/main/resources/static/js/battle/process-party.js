async function processAttack(response) {
    let attackHitCount = response.damages.length; // 공격 데미지 발생수
    let attackMultiHitCount = response.attackMultiHitCount; // 난격 수
    let attackCount = attackHitCount / attackMultiHitCount; // 본 공격 카운트 (1 || 2 || 3, 난격제외)
    let elementType = response.elementTypes[0];

    let attackMotions = [Player.c_animations.ATTACK_SHORT, Player.c_animations.ATTACK_DOUBLE, Player.c_animations.ATTACK_TRIPLE];

    // 데미지 채우기
    let $targetActorContainer = $('#actorContainer>.actor-0');
    let currentDamageWrapperIndex = $targetActorContainer.find('damage-wrapper').length;
    let $damageWrapper = $(`
        <div class="damage-wrapper attack party actor-${response.actorOrder} damage-wrapper-index-${currentDamageWrapperIndex}">
    `)
    response.damages.forEach(function (damage, index) {
        let attackIndex = Math.floor(index / attackMultiHitCount); // 현재 인덱스의 기본타수 순서 (0, 1, 2 현재 트리플 어택까지 구현했으므로 여기까지)
        let multiAttackIndex = index % attackMultiHitCount; // 현재 인덱스의 난격 타수 순서 (공격마다 0-1-2-3-... 씩으로 진행, 0이면 난격이 아님)
        let missClassName = damage === 'MISS' ? ' damage-miss' : '';
        let damageTypeClassName = ' ' + response.damageTypes[0]?.toLowerCase();
        // damage-index-N, multi-N : 난격 여부 확인 후 클래스 설정 / [012 345 678] 3타 3난격 시 di-0 m-0, di-0 m-1, di-0 m-2, di-1 m-0, di-1 m-1, ...

        let $attackDamage = $(`
            <div class="attack-damage actor-${response.actorOrder} element-type-${elementType.toLowerCase()} damage-index-${attackIndex} multi-${multiAttackIndex} ${missClassName} ${damageTypeClassName}">
              ${damage}
            </div>
        `) // 본 공격 (+ 난격)

        $damageWrapper.append($attackDamage);

        if (response.additionalDamages[index].length > 0) {
            let $additionalDamageWrapper = $(` 
                <div class="additional-damage-wrapper actor-${response.actorOrder} element-type-${elementType.toLowerCase()} damage-index-${attackIndex} multi-${multiAttackIndex} ${missClassName} ${damageTypeClassName}">
                  ${damage} 
                </div>
            `) // 공간 확보를 위해 ${damage} 채워넣음
            $additionalDamageWrapper.append((response.additionalDamages[index] || []).map(additionalDamage =>
                $(`
                    <div class="additional-damage element-type-${elementType.toLowerCase()}">
                      ${additionalDamage}
                    </div>
                `)
            ));
            $damageWrapper.append($additionalDamageWrapper);
        }

        // $damageWrapper.append($additionalDamage);
        // 마지막에 DOM 에 추가
        index >= attackHitCount - 1 ? $targetActorContainer.append($damageWrapper) : null;
    });

    // 데미지 표시
    let $attackDamages = $damageWrapper.find('.attack-damage');
    let $additionalDamages = $damageWrapper.find('.additional-damage-wrapper');
    let lastDamageEffectDuration = 0;
    let attackPlayingPromise = null;
    let enemyDamageMotion = player.getEnemyDamageMotion();
    for (let [index, damage] of response.damages.entries()) {
        if (index === 0) { // 상태 갱신
            window.gameStateManager.setState('hps', response.hps);
            window.gameStateManager.setState('hpRates', response.hpRates);
        }
        let attackIndexExact = index / attackMultiHitCount; // 현재 인덱스의 공격 인덱스 (3타 3난격 9데미지 기준 0, 0.33, 0.66, 1, 1.33, 1.66, 2, 2.33, 2.66)
        let attackIndex = Math.floor(attackIndexExact); // 실제 공격 인덱스 (0, 1, 2)
        let multiAttackIndex = index % attackMultiHitCount; // 현재 인덱스의 난격 타수 순서 (공격마다 0-1-2-3-... 씩으로 진행, 0이면 난격이 아님)
        let effectDuration = 0;
        let isLastAttack = attackIndex + 1 === attackCount;
        
        // 모션 재생
        if (Number.isInteger(attackIndexExact)) { // 현재 attackIndexExact 가 정수이면 모션재생
            if (attackPlayingPromise) await attackPlayingPromise; // 이전 모션을 기다림
            let attackMotion = attackCount === 1 ? Player.c_animations.ATTACK : attackMotions[attackIndex];
            let attackRequest = Player.playRequest(`actor-${response.actorOrder}`, attackMotion, {
                attackMultiHitCount: attackMultiHitCount,
                isLastAttack: isLastAttack
            });
            let damageRequest = Player.playRequest('actor-0', enemyDamageMotion);
            effectDuration = await player.playWithOthers(attackRequest, [damageRequest]);
            lastDamageEffectDuration = effectDuration;

            attackPlayingPromise = new Promise(resolve => setTimeout(function () {
                resolve(effectDuration); // 다음 모션 재생 전 대기 promise
            }, effectDuration))
        }
        
        // 데미지 표시 (위의 모션 재생에서 대기하는 만큼 동기화됨. 1타 모션 - 1타 데미지1, 2, 3 ... / 2타 모션 - 2타 데미지1, 2, 3 ...)
        let startDelay = 0; // N 번째 모션의 데미지 1 부터 시작, effectDuration 선반영
        let multiAttackAdditionalDelay = multiAttackIndex * 115; // 난격마다 추가 딜레이 (이펙트 모션은 4프레임, 약 120ms 마다 재생됨)
        let totalDelay = startDelay + multiAttackAdditionalDelay; // 최종 딜레이
        console.log('[processAttack], index = ', index, ' totalDelay = ', totalDelay, 'effectDuration = ', effectDuration, ' multiAttackAdditionalDelay = ', multiAttackAdditionalDelay, ' isLastAttack = ', isLastAttack)

        let $attackDamage = $attackDamages.eq(index);
        let $additionalDamage = $additionalDamages.eq(index);
        setTimeout(() => {
            $attackDamage.addClass('party-attack-damage-show'); // 본 데미지
            $additionalDamage.children().each(function (index, additionalDamage) { // 추가데미지
                setTimeout(() => $(additionalDamage).addClass('party-additional-damage-show'), 50 * (index + 1));
            });
        }, totalDelay);
    }

    // 마지막 모션 대기
    let damageDelay = await wait(lastDamageEffectDuration); // 모션, 데미지 동시에 나가므로 모션만 대기
    //  상태갱신
    let statusEffectDelay = await processStatusEffect(response);
    let totalEndTime = damageDelay + statusEffectDelay;
    console.log("[processAttack] DONE totalEndTime = " + totalEndTime);

    let moveDelay = (Constants.Delay.globalMoveDelay / 2) + (100 * (attackMultiHitCount - 1)); // 공격은 재공격때문에 약간 지연을 줘야함 (특히 난격상황에서 더)
    return new Promise(resolve => setTimeout(() => resolve(), moveDelay));
}

async function processAbility(response) {
    let motionCustomDuration = response.motionCustomDuration;
    let hasDamage = response.damages.length > 0;
    let startTime = performance.now();

    // 속도를 빠르게 하기위해 다음과 같이 설정
    // 어빌리티 : 모션[표준] 데미지[표준] 상태효과[표준]
    // 서포트 어빌리티 : 모션[가속] 데미지[가속] 상태효과[가속]
    // 지정 길이 모션(motionCustomDuration > 10fps) : 모션[motionCustomDuration] 데미지[표준] 상태효과[가속]
    // 지정 길이 모션(motionCustomDuration <= 10fps, skipped) : 모션[motionCustomDuration, 거의 스킵] 데미지[없음] 상태효과[거의 스킵]

    let isSupportAbility = response.moveType.getParentType() === MoveType.SUPPORT_ABILITY;
    let isCustomMotionDuration = motionCustomDuration > 0; // 지정길이 모션
    let isCustomMotionDurationSkipped = isCustomMotionDuration && motionCustomDuration <= 10; // 지정길이 모션 skipped
    console.log('[processAbility] moveName = ', response.moveName, ' isSupportAbility = ', isSupportAbility, ' isCustomMotionDuration = ', isCustomMotionDuration);

    // 모션, 이펙트 처리
    let motionAccelerated = isSupportAbility;
    let motionDuration = await player.play(Player.playRequest('actor-' + response.actorOrder, response.motion, {
        abilityType: response.moveType.name,
    }));
    motionDuration = isCustomMotionDuration ? motionCustomDuration : motionDuration; // customMotionDuration 이 있을시 기존 motionDuration 무시
    if (!hasDamage) { // 데미지 처리가 없을시, 모션을 기다림
        await wait(motionAccelerated ? motionDuration * 0.5 : motionDuration);
    }

    // 데미지 처리
    let damageAccelerated = isSupportAbility;
    let damageDelay = 0;
    if (hasDamage) {
        let $damageWrapper = fillDamage(response, 0);
        let damageShowClass = response.damages.length > 2 ? 'multiple-ability-damage-show' : 'ability-damage-show'
        let effectHitDuration = (motionDuration - 200) / response.damages.length; // 약간 가속하는게 나음
        let delays = response.damages.map((damage, index) => effectHitDuration * index);
        damageDelay = await postProcessPartyDamage($damageWrapper, delays, damageShowClass, damageAccelerated);
    }

    // 상태효과 처리
    let statusEffectAccelerated = isSupportAbility || isCustomMotionDuration;
    let statusEffectDelayScale = statusEffectAccelerated ? 0.5 : 1;
    statusEffectDelayScale = isCustomMotionDurationSkipped ? 0.1 : statusEffectDelayScale;
    let statusDelay = await processStatusEffect(response, statusEffectDelayScale);

    let leftOverMotionDelay = motionDuration - (damageDelay + statusDelay);
    if (leftOverMotionDelay > 0) {
        // 만약, 후처리 딜레이가 모션딜레이 보다 짧을경우 모션 딜레이만큼 보정
        await wait(leftOverMotionDelay);
    }

    let endTime = performance.now();
    let totalDelay = endTime - startTime;
    console.log('[processAbility] DONE name = ', response.moveName, ' motionDelay = ', motionDuration, ' motionSkipDuration =', motionCustomDuration, 'damageDelay = ', damageDelay, ' statusDelay = ', statusDelay, ' totalDelay = ', totalDelay);
    await wait(Constants.Delay.railMoveDelay);
    return totalDelay;
}

async function processChargeAttack(response) {
    // 데미지 채우기
    let $damageWrapper = fillDamage(response, 0);
    // 이펙트 재생
    await player.play(Player.playRequest('actor-' + response.actorOrder, response.motion), true, 100);
    // 데미지 처리
    await postProcessPartyDamage($damageWrapper, [0], 'party-charge-attack-damage-show');
    // 상태효과 처리
    let totalEndTime = await processStatusEffect(response);

    console.log('[processChargeAttack] DONE totalTime', totalEndTime);
    await wait(Constants.Delay.railMoveDelay);
    return totalEndTime;
}

async function processSummon(response) {
    // 본 소환: hasUnionSummon = [true / false], isUnionSummon = false
    // 합체 소환: hasUnionSummon = true, isUnionSummon = true
    let hasUnionSummon = response.hasUnionSummon;
    let isUnionSummon = response.isUnionSummon;
    let summonId = response.moveId;

    let $damageWrapper = fillDamage(response, 0);

    // 소환 이펙트 재생
    if (hasUnionSummon && !isUnionSummon) {
        // 합체소환 컷인
        window.gameStateManager.setState('raid_union_summon_name', response.moveName);
        await player.play(Player.playRequest('global', Player.c_animations.ABILITY_MOTION, {
            abilityType: 'UNION_SUMMON',
            summonId: summonId,
            unionSummonId: gameStateManager.getState('unionSummonId'), // response.unionSummonId 쓰면 안됨. MoveType.SUMMON 이면 이미 갱신된거 넘어옴
        })); // 합체소환 컷인은 기다리지 않아도 될듯. (SUMMON 과 동시재생)
    }
    let leaderActor = player.actors.values().find(actor => actor.isLeaderCharacter);
    if (!isUnionSummon) {
        // 본소환
        await player.play(Player.playRequest(leaderActor.actorId, Player.c_animations.SUMMON), true);
        await player.play(Player.playRequest(leaderActor.actorId, Player.c_animations.SUMMON_ATTACK, {summonId: summonId}), true);
        await player.play(Player.playRequest(leaderActor.actorId, Player.c_animations.SUMMON_DAMAGE, {summonId: summonId}), true, 300);
    } else {
        // 합체소환
        await player.play(Player.playRequest('global', Player.c_animations.ABILITY_MOTION, {abilityType: BASE_ABILITY.RAID_BUFF}), true);
        // 쓰고나서 null 로 갱신
        gameStateManager.setState('unionSummonId', null);
    }

    let delays = response.damages.map((damage, index) => index * 100);
    let damageShowClass = response.damages.length > 2 ? 'multiple-ability-damage-show' : 'ability-damage-show'
    await postProcessPartyDamage($damageWrapper, delays, damageShowClass);

    let totalEndTime = await processStatusEffect(response);
    console.log('[processSummon] DONE totalTime', totalEndTime);
    await wait(Constants.Delay.railMoveDelay);
    return totalEndTime;
}

async function processFatalChain(response) {
    let firstActor = player.actors.values().find(actor => actor.isCharacter());
    let fatalChainRequest = Player.playRequest(firstActor.actorId, Player.c_animations.ABILITY_EFFECT_ONLY, {abilityType: MoveType.FATAL_CHAIN_DEFAULT.name});
    let mainCharacterAttackRequest = Player.playRequest(firstActor.actorId, Player.c_animations.ATTACK_MOTION_ONLY);
    let partyMembersActorIndexArray = player.actors.values().filter(actor => actor.isCharacter() && actor.actorId !== firstActor.actorId).map(actor => actor.actorIndex);
    let partyAttackRequests = partyMembersActorIndexArray.map(actorIndex => Player.playRequest('actor-' + actorIndex, Player.c_animations.ATTACK)).toArray();
    // 아군전체 어택 이펙트
    let partyAttackDuration = await player.playWithOthers(mainCharacterAttackRequest, partyAttackRequests)
    await new Promise(resolve => setTimeout(() => resolve(partyAttackDuration), partyAttackDuration / 3));
    // 페이탈 체인 이펙트
    await player.play(fatalChainRequest, true);
    // 데미지 삽입 (페이탈 체인은 데미지 1회)
    let $damageWrapper = fillDamage(response, 0);
    // 데미지 처리
    await postProcessPartyDamage($damageWrapper, [0], 'party-attack-damage-show');

    let totalEndTime = await processStatusEffect(response);
    console.log('[processFatalChain] DONE totalTime', totalEndTime);
    await wait(Constants.Delay.railMoveDelay);
    return totalEndTime;
}

async function processCharacterDead(response) {
    console.log('[processCharacterDead] resp = ', response);
    // actorOrder 가 사망 처리된 순서로 넘어옴에 주의
    let actorOrder = response.actorOrder - 100;
    let actorIds = gameStateManager.getState('actorIds')[actorOrder];

    // 캐릭터 사망시 알아차리기 쉽게 약간 딜레이
    await wait(500); 

    // 이펙트 처리
    window.effectAudioPlayer.loadAndPlay(Sounds.global.CHARACTER_DEAD.src);
    await player.play(Player.playRequest('actor-' + actorOrder, Player.c_animations.DEAD), true);

    // battle-portrait 삭제
    let $emptyBattlePortrait = $('<div>').append(
        $('<div>').addClass('battle-portrait empty').append(
            $('<img>')
                .attr('src', '/static/assets/img/gl/ch-empty.jpg')
                .attr('data-seq', actorOrder))); // 이거 어따쓰는건지?
    let $deadBattlePortrait = $('.battle-portrait').eq(actorOrder - 1); // empty 까지 포함해야 제대로 순서구해짐
    console.log('[processCharacterDead] $deadBattlePortrait = ', $deadBattlePortrait, ' $emptyBattlePortrait = ', $emptyBattlePortrait)
    $deadBattlePortrait.before($emptyBattlePortrait);
    $deadBattlePortrait.remove();

    // abilityPanel 삭제 (되도록 slickRemove 로 지우기)
    let abilityPanels = $('#abilitySlider .slick-slide:not(.slick-cloned) .ability-panel');
    let deadCharacterAbilityPanel = abilityPanels.filter(`[data-character-order="${actorOrder}"]`);
    $('#abilitySlider').slick('slickRemove', abilityPanels.index(deadCharacterAbilityPanel));
    window.abilityPanels[actorOrder] = $();

    // player.actor 삭제
    player.removeActor(actorOrder);

    // 가드 표시 있으면 삭제
    $('#actorContainer .guard-status').eq(actorOrder - 1).removeClass('guard-on-processing');

    // 상태 갱신
    gameStateManager.setState('leaderActorId', null);

    // 모든 캐릭터 사망시
    let characterHps = gameStateManager.getState('hps').slice(1);
    if (characterHps.every(hp => hp <= 0) && player.getCharacters().length === 0) {
        await player.play(Player.playRequest('global', Player.c_animations.ABILITY_UI, {abilityType: 'QUEST_FAILED'}), true);

        player.lockPlayer(true);
        gameStateManager.setState('isQuestFailed', true); // rejoin -> 미구현
    }

    let endDelay = Constants.Delay.globalMoveDelay;
    return new Promise(resolve => setTimeout(function () {
        console.log('[processCharacterDead] DONE move = ', response.moveType.name);
        resolve();
    }, endDelay));
}

/**
 * 가드처리
 * @param response
 */
function processGuard(response) {
    let isGuardActivated = response.guardActivated;
    let src = isGuardActivated ? Sounds.global.GUARD_ON.src : Sounds.global.GUARD_OFF.src;
    window.effectAudioPlayer.loadSound(src).then(() => {
        window.effectAudioPlayer.playAllSounds();
    })

    window.gameStateManager.setState('guardStates', response.guardStates);
}

/**
 * 포션 처리
 * @param response
 */
async function processPotion(response) {
    console.log('[processPotion] response = ', response);

    // 이펙트
    let healDelay = processHealEffect(response.heals);
    await wait(healDelay);

    let potionCounts = [response.potionCount, response.allPotionCount, response.elixirCount];
    window.gameStateManager.setState('potion.counts', potionCounts);
    window.gameStateManager.setState('hps', response.hps);
    window.gameStateManager.setState('hpRates', response.hpRates);

    // 레일에서 삭제
    $('.ability-rail-wrapper .rail-item').eq(0).remove();

    let totalEndTime = healDelay;
    console.log('[processPotion] DONE totalTime', totalEndTime);
    await wait(Constants.Delay.railMoveDelay);
    return totalEndTime;
}

/**
 * 데미지를 targetActor 에 맞게 삽입
 * @param response
 * @param targetActorIndex
 * @return {*|jQuery|HTMLElement} $damageWrapper 반환
 */
function fillDamage(response, targetActorIndex) {
    if (response.damages.length === 0) return $();
    let moveType = "none";
    let isMultipleAbilityDamage = false;
    switch (response.moveType.getParentType()) {
        case MoveType.ATTACK:
            moveType = "attack";
            break;
        case MoveType.SUMMON:
        case MoveType.FATAL_CHAIN:
        case MoveType.SUPPORT_ABILITY:
        case MoveType.ABILITY:
        case MoveType.TURN_END:
            moveType = "ability";
            isMultipleAbilityDamage = response.damages.length > 2;
            break;
        case MoveType.CHARGE_ATTACK:
            moveType = "charge-attack";
            break;
        default:
            moveType = "none";
    }

    let $targetActorContainer = $(`#actorContainer > .actor-${targetActorIndex}`);
    let currentDamageWrapperIndex = $targetActorContainer.find('.damage-wrapper').length;
    let $damageWrapper = $(`
        <div class="damage-wrapper ${moveType} damage-wrapper-index-${currentDamageWrapperIndex}"></div>
    `);
    response.damages.forEach(function (damage, damageIndex) {
        let $damageElements = getDamageElement(response.actorOrder, response.elementTypes[0], moveType, response.damageTypes[0], damageIndex, damage, []);
        if (isMultipleAbilityDamage) $damageElements.$damage.addClass('multiple');
        $damageWrapper.prepend($damageElements.$damage);
    })
    $targetActorContainer.append($damageWrapper);
    return $damageWrapper;
}

/**
 * 데미지 발생에 따른 후처리 수행
 * @param $damageWrapper
 * @param delays {Array<Number>} 각 데미지들의 표시딜레이 (ms)
 * @param damageShowClass
 * @param isAccelerated 가속 여부 (true 시 딜레이 절반)
 * @return {Promise<unknown>} 자신의 수행시간을 딜레이하여 resolve() (페이드아웃 시점)
 */
async function postProcessPartyDamage($damageWrapper, delays, damageShowClass, isAccelerated = false) {
    console.log('showDamage $damageWrapper = ', $damageWrapper, ' delays = ', delays, ' damageShowClass = ', damageShowClass);

    // 상태변경
    window.gameStateManager.setState('hps', window.stage.processingResponse.hps);
    window.gameStateManager.setState('hpRates', window.stage.processingResponse.hpRates);

    // 모션 재생
    let enemyDamageMotion = player.getEnemyDamageMotion();

    // 데미지 요소마다
    $damageWrapper.find('.damage').get().reverse().forEach(function (element, index) {
        setTimeout(() => {
            // 적 피격 모션
            player.play(Player.playRequest('actor-0', enemyDamageMotion));
            // 데미지 표시
            element.classList.add(damageShowClass);
        }, delays[index]);
    })

    let toNextDelay = delays[delays.length - 1] + Constants.Delay.damageShowToNext; // 마지막 데미지 표시 ~ 페이드아웃 시작 시점에 resolve()
    if (isAccelerated) toNextDelay *= 0.5;
    return new Promise(resolve => setTimeout(() => resolve(toNextDelay), toNextDelay));
}