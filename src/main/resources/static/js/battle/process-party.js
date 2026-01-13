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

        if (response.additionalDamages.length > 0 && response.additionalDamages[index].length > 0) {
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

        // 마지막 공격 데미지 모션 직후 전조 갱신 및 전조모션 강제재생
        if (isLastAttack && response.omen.updateTiming === 'damage') {
            setTimeout(() => gameStateManager.setState('omen', response.omen), 400); // 0.27s (8fps)
        }
    }

    // 마지막 모션 대기
    let damageDelay = await wait(lastDamageEffectDuration); // 모션, 데미지 동시에 나가므로 모션만 대기
    //  상태갱신
    let statusEffectDelay = await processStatusEffect(response);
    let totalEndTime = damageDelay + statusEffectDelay;
    console.log("[processAttack] DONE totalEndTime = " + totalEndTime);

    return totalEndTime;
}

async function processAbility(response) {
    let startTime = performance.now();

    // 속도를 빠르게 하기위해 다음과 같이 설정 [N]: 대기시간 scale / 모션 + 이펙트 의 경우 일반적으로 이펙트가 긺, 긴쪽을 따라감
    // 어빌리티 : <모션[1] 이펙트[1]> 데미지[1] 상태효과[1]
    // 서포트 어빌리티 (모션 + 이펙트 + 데미지 + 상태효과) : <모션[X] 이펙트[0.5]> 데미지[0.5] 상태효과[0.5]
    // 서포트 어빌리티 (모션 + 이펙트 + 상태효과) : <모션[X] 이펙트[0.5]> 상태효과[0.5]
    // 서포트 어빌리티 (ab_motion_effect_only + 이펙트 + 상태효과) : <모션[X] 이펙트[0.5]> 상태효과 [0.5] -> 모션 없는것임
    // 서포트 어빌리티 (모션 + 상태효과) : 모션[0.05] 상태효과[0.05] -> 사실상 스킵

    let hasDamage = response.damages.length > 0;
    let isSupportAbility = response.moveType.getParentType() === MoveType.SUPPORT_ABILITY;
    let hasEffect = player.actors.get(`actor-${response.actorOrder}`).animation.abilities.hasOwnProperty(response.moveId);
    hasEffect = Player.c_animations.isAttack(response.motion) ? true : hasEffect; // 공격모션(이펙트)인 경우도 있음

    // 모션, 이펙트 처리
    let animationDuration = await player.play(Player.playRequest('actor-' + response.actorOrder, response.motion, {
        abilityType: response.moveId,
    })); // 기본적으로 긴 이펙트쪽의 duration 을 반환
    let animationDurationScale = !hasEffect ? 0.05 : isSupportAbility ? 0.5 : 1;
    if (!hasDamage) { // 데미지 처리가 없을시, 모션을 기다림
        await wait(animationDuration * animationDurationScale);
    }

    // 데미지 처리
    let damageDurationScale = isSupportAbility ? 0.5 : 1;
    let damageDelay = 0;
    if (hasDamage) {
        let damageShowClass = response.damages.length > 2 ? 'multiple-ability-damage-show' : 'ability-damage-show'
        damageDelay = await postProcessPartyDamage(response, damageShowClass, damageDurationScale);
    }

    // 상태효과 처리
    let statusEffectDelayScale = !hasEffect ? 0.05 : isSupportAbility ? 0.5 : 1;
    let statusDelay = await processStatusEffect(response, statusEffectDelayScale);

    let leftOverMotionDelay = animationDuration - (damageDelay + statusDelay);
    console.log('[processAbility] animationDuration = ', animationDuration + ' leftOverMotionDelay = ', leftOverMotionDelay);
    if (hasEffect && leftOverMotionDelay > 0) {
        // 만약, 후처리 딜레이가 이펙트 딜레이보다 짧을경우 보정
        await wait(leftOverMotionDelay);
    }

    let endTime = performance.now();
    let totalDelay = endTime - startTime;
    console.log('[processAbility] DONE name = ', response.moveName, ' animationDuration = ', animationDuration, ' animationDurationScale = ', animationDurationScale, ' damageDelay = ', damageDelay, ' statusDelay = ', statusDelay, ' totalDelay = ', totalDelay);
    return totalDelay;
}

async function processChargeAttack(response) {
    // 이펙트 재생
    await player.play(Player.playRequest('actor-' + response.actorOrder, response.motion), true, 100);
    // 데미지 처리
    await postProcessPartyDamage(response, 'party-charge-attack-damage-show');
    // 상태효과 처리
    let totalEndTime = await processStatusEffect(response);

    console.log('[processChargeAttack] DONE totalTime', totalEndTime);
    return totalEndTime;
}

async function processSummon(response) {
    // 본 소환: hasUnionSummon = [true / false], isUnionSummon = false
    // 합체 소환: hasUnionSummon = true, isUnionSummon = true
    let hasUnionSummon = response.hasUnionSummon;
    let isUnionSummon = response.isUnionSummon;
    let summonId = response.moveId;

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

    let damageShowClass = response.damages.length > 2 ? 'multiple-ability-damage-show' : 'ability-damage-show'
    await postProcessPartyDamage(response, damageShowClass);

    let totalEndTime = await processStatusEffect(response);
    console.log('[processSummon] DONE totalTime', totalEndTime);
    return totalEndTime;
}

async function processFatalChain(response) {
    let firstActor = player.actors.values().find(actor => actor.isCharacter());
    let fatalChainRequest = Player.playRequest(firstActor.actorId, Player.c_animations.ABILITY_EFFECT_ONLY, {abilityType: response.moveId});
    let mainCharacterAttackRequest = Player.playRequest(firstActor.actorId, Player.c_animations.ATTACK_MOTION_ONLY);
    let partyMembersActorIndexArray = player.actors.values().filter(actor => actor.isCharacter() && actor.actorId !== firstActor.actorId).map(actor => actor.actorIndex);
    let partyAttackRequests = partyMembersActorIndexArray.map(actorIndex => Player.playRequest('actor-' + actorIndex, Player.c_animations.ATTACK)).toArray();
    // 아군전체 어택 이펙트
    let partyAttackDuration = await player.playWithOthers(mainCharacterAttackRequest, partyAttackRequests)
    await new Promise(resolve => setTimeout(() => resolve(partyAttackDuration), partyAttackDuration / 3));
    // 페이탈 체인 이펙트
    await player.play(fatalChainRequest, true);
    // 데미지 처리
    await postProcessPartyDamage(response, 'party-attack-damage-show');

    let totalEndTime = await processStatusEffect(response);
    console.log('[processFatalChain] DONE totalTime', totalEndTime);
    return totalEndTime;
}

async function processCharacterDead(response) {
    console.log('[processCharacterDead] resp = ', response);
    // actorOrder 가 사망 처리된 순서로 넘어옴에 주의
    let actorOrder = response.actorOrder - 100;
    let deadActorId = gameStateManager.getState('actorIds')[actorOrder];

    // 캐릭터 사망시 알아차리기 쉽게 약간 딜레이
    let preDelay = 500;
    await wait(preDelay);

    // 이펙트 처리
    playSe(Sounds.global.CHARACTER_DEAD.src);
    let effectDuration = await player.play(Player.playRequest('actor-' + actorOrder, Player.c_animations.DEAD), true);

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
    let deadCharacterAbilityPanel = abilityPanels.filter(`[data-actor-order="${actorOrder}"]`);
    $('#abilitySlider').slick('slickRemove', abilityPanels.index(deadCharacterAbilityPanel));
    window.abilityPanels[actorOrder] = $();

    // player.actor 삭제
    player.removeActor(actorOrder);

    // 가드 표시 있으면 삭제
    $('#actorContainer .guard-status').eq(actorOrder - 1).removeClass('guard-on-processing');

    // 상태 갱신
    if (gameStateManager.getState('leaderActorId') === deadActorId) {
        // 리더 사망시 null 로 갱신
        gameStateManager.setState('leaderActorId', null);
    }

    // 모든 캐릭터 사망시
    let characterHps = gameStateManager.getState('hps').slice(1);
    if (characterHps.every(hp => hp <= 0) && player.getCharacters().length === 0) {
        await player.play(Player.playRequest('global', Player.c_animations.ABILITY_UI, {abilityType: 'QUEST_FAILED'}), true);

        player.lockPlayer(true);
        gameStateManager.setState('isQuestFailed', true); // rejoin -> 미구현
    }


    console.log('[processCharacterDead] DONE move = ', response.moveType.name, 'isQuestFailed = ', gameStateManager.getState('isQuestFailed'));
    return preDelay + effectDuration;
}

/**
 * 가드처리
 * @param response
 */
function processGuard(response) {
    let isGuardActivated = response.guardActivated;
    let src = isGuardActivated ? Sounds.ui.GUARD_ON.src : Sounds.ui.GUARD_OFF.src;
    playSe(src);

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

    // 모션 갱신
    player.getCharacters().forEach(actor => player.play(Player.playRequest(actor.actorId, player.getCharacterWaitMotion(actor.actorIndex))));

    // 레일에서 삭제
    $('.ability-rail-wrapper .rail-item').eq(0).remove();

    let totalEndTime = healDelay;
    console.log('[processPotion] DONE totalTime', totalEndTime);
    return totalEndTime;
}

/**
 * 데미지 발생에 따른 후처리 수행 (일반공격을 제외한 모든 캐릭터 데미지 처리에서 사용)
 * @param response
 * @param damageShowClass
 * @param delayScale 대기시간 스케일
 * @return {Promise<unknown>} 자신의 수행시간을 딜레이하여 resolve() (페이드아웃 시점)
 */
async function postProcessPartyDamage(response, damageShowClass, delayScale = 1.0) {
    // 데미지 채우기
    let $damageWrapper = fillPartyDamage(response, 0);
    // 데미지 별 딜레이
    let delays = response.damages.map((damage, index) => index * 100); // 슬립데미지는 여러개 없음, 0 (response.effectDamages)
    let lastDelay = delays[delays.length - 1];
    // 적 모션
    let enemyDamageMotion = player.getEnemyDamageMotion();
    console.log('[postProcessPartyDamage] $damageWrapper = ', $damageWrapper, ' delays = ', delays, ' damageShowClass = ', damageShowClass);

    // 데미지 요소마다
    $damageWrapper.find('.damage').get().reverse().forEach(function (element, index) {
            setTimeout(() => {
                // 적 피격 모션
                player.play(Player.playRequest('actor-0', enemyDamageMotion));
                // 데미지 표시
                element.classList.add(damageShowClass);
            }, delays[index]);
        }
    )

    // 적의 전조 조건이 [데미지] 일 경우 갱신
    if (response.omen.updateTiming === 'damage') {
        setTimeout(() => {
            gameStateManager.setState('omen', response.omen);
        }, lastDelay + 400); // 0.27s (8fps) + 마진
    }

    let toNextDelay = lastDelay + Constants.Delay.damageShowToNext; // 마지막 데미지 표시 ~ 페이드아웃 시작 시점에 resolve()
    return wait(toNextDelay * delayScale);
}

/**
 * 데미지를 targetActor 에 맞게 삽입
 * @param response
 * @param targetActorIndex
 * @return {*|jQuery|HTMLElement} $damageWrapper 반환
 */
function fillPartyDamage(response, targetActorIndex) {
    let moveParentType = response.moveType.getParentType();
    let damages = moveParentType === MoveType.TURN_END ? [response.effectDamages[0]] : response.damages; // 슬립데미지 대응
    if (damages.length === 0) return $();

    let moveType = "none";
    let isMultipleAbilityDamage = false;
    let elementType = response.elementTypes[0];
    switch (moveParentType) {
        case MoveType.ATTACK:
            moveType = "attack";
            break;
        case MoveType.SUMMON:
        case MoveType.FATAL_CHAIN:
        case MoveType.SUPPORT_ABILITY:
        case MoveType.ABILITY:
            moveType = "ability";
            isMultipleAbilityDamage = response.damages.length > 2;
            break;
        case MoveType.TURN_END:
            moveType = "ability";
            elementType = 'NONE'; // 슬립 데미지 무속성 고정
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
    damages.forEach(function (damage, damageIndex) {
        let $damageElements = getDamageElement(response.actorOrder, elementType, moveType, response.damageTypes[0], damageIndex, damage, []);
        if (isMultipleAbilityDamage) $damageElements.$damage.addClass('multiple');
        $damageWrapper.prepend($damageElements.$damage);
    })
    $targetActorContainer.append($damageWrapper);
    return $damageWrapper;
}