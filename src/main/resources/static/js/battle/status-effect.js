/**
 * 스테이터스 이펙트를 처리 (힐, 버프, 디버프)
 * @param response
 * @param {number} delayScale 가속을 위해 사용할 내부 처리 딜레이 스케일, 0.1 ~ 1.0 (0 및 음수 금지)
 * @return {Promise<number>} totalDuration 실행시간
 * @throws Error delayScale <= 0
 */
async function processStatusEffect(response, delayScale = 1.0) {
    if (delayScale <= 0.0) throw new Error('[processStatusEffect] invalid delayScale, delayScale = ' + delayScale);
    let startTime = performance.now();
    // 쿨다운 갱신
    gameStateManager.setState('abilityCoolDowns', response.abilityCoolDowns, {force: true}); // 쿨다운이 0 -> 0 으로 변경되는경우 (어빌리티 N회 사용가능시) 오버레이 제거를 위해 강제 update
    // 소환석 쿨다운 갱신
    gameStateManager.setState('summonCooldowns', response.summonCooldowns);
    // 사용가능여부 갱신
    gameStateManager.setState('abilitySealeds', response.abilitySealeds);

    let healDelay = processHealEffect(response.heals);
    healDelay = healDelay * delayScale;
    await wait(healDelay);

    // 힐, 슬립데미지 사이에서 hp 갱신 (데미지 처리 있을경우, 미리 갱신되었음)
    gameStateManager.setState('hps', response.hps);
    gameStateManager.setState('hpRates', response.hpRates);

    await processEffectDamageEffect(response); // 데미지 효과는 가속 X

    let buffDelay = processBuffEffect(response.addedBuffStatusesList, response.removedBuffStatusesList, response.removedDebuffStatusesList);
    buffDelay = buffDelay * delayScale;
    await wait(buffDelay);

    // 오의 게이지 갱신
    gameStateManager.setState('chargeGauges', response.chargeGauges);
    gameStateManager.setState('enemyMaxChargeGauge', response.enemyMaxChargeGauge);
    // 페이탈 체인 게이지 갱신
    gameStateManager.setState('fatalChainGauge', response.fatalChainGauge);
    // 상태효과 갱신
    gameStateManager.setState('currentStatusEffectsList', response.currentStatusEffectsList);

    let debuffDelay = processDebuffEffect(response.addedDebuffStatusesList);
    debuffDelay = debuffDelay * delayScale;
    await wait(debuffDelay);

    let levelDownEffectDelay = processLevelDownEffect(response.levelDownedBattleStatusesList);
    levelDownEffectDelay = levelDownEffectDelay * delayScale;
    await wait(levelDownEffectDelay);

    // 전조 [상태효과] 갱신
    if (response.omen.updateTiming === 'statusEffect') {
        gameStateManager.setState('omen', response.omen);
    }

    let endTime = performance.now();
    let totalDuration = Math.floor(endTime - startTime);
    console.debug('[processStatusEffect] delayScale = ', delayScale , ' healDelay = ', healDelay, ' buffDelay = ', buffDelay, ' debuffDelay = ', debuffDelay, ' totalDuration = ', totalDuration);
    return totalDuration;
}

function processHealEffect(healArray) {
    let healEffectDuration = 300; // 실제 heal cjs.timeline 의 길이는 440ms 임.
    let perCharacterDelay = 100 // 캐릭터 별 추가딜레이
    let lastHealEffectEndTime = 0;
    let healDelay = 0; // 힐 처리 최종시간

    let healWrappers = [];
    healArray.forEach(function (heal, actorIndex) {
        // console.log('[processHealEffect] heal = ', heal, ' actorIndex = ', actorIndex);
        if (Number.isInteger(heal) && heal >= 0) { // 0인경우도 있음
            let $damageWrapper = actorIndex === 0
                ? $('<div>', {class: 'damage-wrapper ability'}) // 적은 어빌리티 데미지 래퍼를,
                : $('<div>', {class: 'damage-wrapper enemy actor-' + actorIndex}); // 아군은 적의 데미지 래퍼를 사용
            let startDelay = perCharacterDelay * actorIndex; // 캐릭터 순서대로 딜레이
            lastHealEffectEndTime = startDelay + healEffectDuration;

            setTimeout(async function () {
                player.play(Player.playRequest('actor-' + actorIndex, Player.c_animations.ABILITY_EFFECT_ONLY, {abilityType: 'HEAL'}))
                // 데미지(힐 수치) 채우기 및 돔추가
                let $healWrapper = $damageWrapper;
                healWrappers.push($healWrapper);
                let $actorContainer = $(`#actorContainer > .actor-${actorIndex}`);
                let isEnemyDamage = actorIndex !== 0; // 아군인 경우 적의 공격데미지 클래스를 사용하기 위함
                let $damageElements = getDamageElement(0, 'NONE', 'attack', 'normal', 0, heal, [], isEnemyDamage);
                let className = heal >= 0 ? 'heal heal-show' : 'enemy-damage-show';
                $healWrapper.append($damageElements.$damage.addClass(className)).appendTo($actorContainer);
                setTimeout(() => {
                    // 캐릭터 대기모션 갱신 (빈사 -> 일반)
                    if (actorIndex > 0) player.play(Player.playRequest('actor-' + actorIndex, player.getCharacterWaitMotion(actorIndex)));
                }, healEffectDuration / 2) // 이펙트 도중 갱신
            }, startDelay);
        }
    });
    // 삭제
    setTimeout(() => healWrappers.forEach((healWrapper) => $(healWrapper).remove()), lastHealEffectEndTime + Constants.Delay.damageShowDelete);

    healDelay += lastHealEffectEndTime; // 이전딜레이 + 이펙트딜레이
    console.log('[processHealEffect] healDelay = ', healDelay);
    return healDelay;
}


async function processEffectDamageEffect(response) {
    let effectDamages = response.effectDamages;
    if (effectDamages.length <= 0) return 0;

    let partyEffectDamageDelay = 0;
    let enemyEffectDamageDuration = 0;

    // 아군에 대한 슬립 데미지
    let partyEffectDamageShowStartDelay = 0;
    let partyEffectDamages = effectDamages.slice(1);
    if (partyEffectDamages.find(damage => damage > 0)) {
        // 후행동 공격데미지와 겹치지 않도록 미리 데미지 래퍼 추가
        let $enemyDamageWrappers = new Map();
        partyEffectDamages.map((effectDamage, index) => effectDamage > 0 ? index + 1 : null)
            .filter(actorIndex => actorIndex != null)
            .forEach(actorIndex => {
                $enemyDamageWrappers.set(actorIndex, $('<div>', {class: 'damage-wrapper enemy actor-' + actorIndex}));
            })
        //  데미지 마다 반복 - 데미지삽입, 데미지표시, 피격이펙트 재생
        let lastDamageShowStartDelay = 0;
        partyEffectDamages.forEach(function (damage, index) {
            if (damage === null) return;
            partyEffectDamageShowStartDelay = index * 100;
            let targetOrder = index + 1;
            lastDamageShowStartDelay = partyEffectDamageShowStartDelay;

            // 데미지 채우기
            let $damageElements = getDamageElement(targetOrder, 'PLAIN', 'attack', 'NORMAL', index, damage, [], true);
            let $enemyDamageWrapper = $enemyDamageWrappers.get(targetOrder);
            $enemyDamageWrapper.append($damageElements.$damage, $damageElements.$additionalDamage);
            // 마지막에 돔에 추가
            if (index >= $enemyDamageWrappers.size - 1) {
                $enemyDamageWrappers.entries().forEach(([key, value], index) => {
                    $(`#actorContainer > .actor-${key}`).append(value);
                })
            }

            setTimeout(function () {
                // 데미지 표시
                $damageElements.$damage.addClass('enemy-turn-damage-show'); // 본 데미지 표시
                // 소리재생
                playSe(Sounds.global.DEBUFF.src);
                // 아군 피격 재생
                player.play(Player.playRequest('actor-' + targetOrder, Player.c_animations.DAMAGE));
            }, partyEffectDamageShowStartDelay)
        })
        // 데미지 제거
        setTimeout(() => $enemyDamageWrappers.values().forEach((wrapper) => $(wrapper).remove()), lastDamageShowStartDelay + Constants.Delay.damageShowDelete);
        // 대기
        partyEffectDamageDelay = lastDamageShowStartDelay + Constants.Delay.damageShowToNext;
    }

    // 적에 대한 슬립 데미지 (ability wrapper, ability damage 사용)
    let enemyEffectDamage = effectDamages[0];
    if (enemyEffectDamage > 0) {
        playSe(Sounds.global.DEBUFF.src);
        enemyEffectDamageDuration = await postProcessPartyDamage(response, 'party-turn-damage-show');
        enemyEffectDamageDuration /= 2; // 가속
    }

    let totalEffectDamageDuration = partyEffectDamageDelay +  enemyEffectDamageDuration;
    console.log('[processEffectDamageEffect] effectDamages = ', effectDamages, ' partyEffectDamageDuration = ', partyEffectDamageDelay, ' enemyEffectDamageDuration = ', enemyEffectDamageDuration);
    return totalEffectDamageDuration;
}

/**
 * 버프 이펙트를 처리
 * @param addedBuffStatusesList 추가된 버프 스테이터스 리스트 (빈 배열 가능)
 * @param removedBuffStatusesList
 * @param removedDebuffStatusesList
 * @return {number} 버프 효과로 인한 딜레이
 */
function processBuffEffect(addedBuffStatusesList, removedBuffStatusesList, removedDebuffStatusesList) {
    let buffEffectDelay = 0;

    let buffCounts = []; // actorIndex 기준으로 버프 갯수
    let buffesLists = _.range(0, 5).map(actorIndex => {
        let removedDebuffs = removedDebuffStatusesList[actorIndex] || [];
        let addedBuffes = addedBuffStatusesList[actorIndex] || [];
        addedBuffes = [...new Map(addedBuffes.map(statusDto => [statusDto.name, statusDto])).values()]; // name 기준 중복제거 (Map.key 중복시 덮어쓰기), 버프는 같은 이름으로 여러개 들어오는 경우가 있음
        let removedBuffes = removedBuffStatusesList[actorIndex] || [];
        removedBuffes = [...new Map(removedBuffes.map(statusDto => [statusDto.name, statusDto])).values()];

        let resultBuffes = [...removedBuffes, ...removedDebuffs, ...addedBuffes]; // 표시 순서 제거된 버프 -> 제거된 디버프 -> 새로걸리는 버프
        buffCounts[actorIndex] = resultBuffes.length;
        return resultBuffes;
    });
    console.log('[processBuffEffect] buffesLists = {}', buffesLists);

    let partyBuffCountSum = buffCounts.slice(1, buffCounts.length).reduce((acc, count) => acc + count, 0);
    let enemyDelay = partyBuffCountSum > 0 ? 800 : 0; // 파티쪽에 표시할 버프 있는경우 적은 800 늦춤

    buffesLists.forEach(function (statuses, actorIndex) { // [[적][아군][아군][아군][아군]]
        let $statusWrappers = fillStatusEffect(statuses, actorIndex);
        let additionalDelay = actorIndex === 0 ? enemyDelay : 0;
        let lastBuffFadeoutStartTime = showStatusEffect($statusWrappers, actorIndex, additionalDelay);
        buffEffectDelay = Math.max(buffEffectDelay, lastBuffFadeoutStartTime); // 각 actor 별 딜레이중 제일 긴쪽을 반영
    });

    console.log('[processBuffEffect] buffEffectDelay = ', buffEffectDelay);
    return buffEffectDelay;
}

/**
 * 디버프 이펙트 처리
 * @param addedDebuffStatusesList
 * @return {number} 디버프 효과 발생으로 인한 딜레이
 */
function processDebuffEffect(addedDebuffStatusesList) {
    let debuffDelay = 0;
    let partyDebuffCountSum = addedDebuffStatusesList.slice(1, addedDebuffStatusesList.length).reduce((acc, debuffStatuses) => acc + debuffStatuses.length, 0);
    let enemyDelay = partyDebuffCountSum > 0 ? 800 : 0; // 파티쪽에 표시할 디버프 있는경우 적은 800 늦춤
    // console.log('[processDebuffEffect] partyDebuffCountSum = ', partyDebuffCountSum);

    addedDebuffStatusesList.forEach(function (statuses, actorIndex) { // [[적][아군][아군][아군][아군]]
        if (statuses.length === 0) return;
        let $statusWrappers = fillStatusEffect(statuses, actorIndex);
        let additionalDelay = actorIndex === 0 ? enemyDelay : 0;
        let lastFadeoutStartTime = showStatusEffect($statusWrappers, actorIndex, additionalDelay);
        debuffDelay = Math.max(lastFadeoutStartTime, debuffDelay);
    });

    console.log('[processDebuffEffect] debuffDelay = ', debuffDelay);
    return debuffDelay;
}

/**
 * 레벨 감소 이펙트 별도로 처리
 * @param levelDownStatusEffectsList
 * @return {number} 사용할 duration
 */
function processLevelDownEffect(levelDownStatusEffectsList) {
    let levelDownEffectDuration = 0;
    let partyLevelDownCountSum = levelDownStatusEffectsList.slice(1, levelDownStatusEffectsList.length).reduce((acc, levelDownStatusEffects) => acc + levelDownStatusEffects.length, 0);
    let enemyDelay = partyLevelDownCountSum > 0 ? 800 : 0; // 파티쪽에 표시할 이펙트 있는경우 적은 800 늦춤

    levelDownStatusEffectsList.forEach(function (statusEffects, actorIndex) { // [[적][아군][아군][아군][아군]]
        if (statusEffects.length === 0) return;
        let $statusWrappers = fillStatusEffect(statusEffects, actorIndex);
        let additionalDelay = actorIndex === 0 ? enemyDelay : 0;
        let lastFadeoutStartTime = showStatusEffect($statusWrappers, actorIndex, additionalDelay);
        levelDownEffectDuration = Math.max(lastFadeoutStartTime, levelDownEffectDuration);
    });

    console.log('[processLevelDownEffect] levelDownEffectDuration = ', levelDownEffectDuration);
    return levelDownEffectDuration;
}

/**
 *
 * @param statusDtos
 * @param actorIndex
 * @return {*[]}  $statusEffectWrappers actorIndex 에 해당하는 actor 의 wrappers
 */
function fillStatusEffect(statusDtos, actorIndex) {
    if (statusDtos.length === 0) return $();

    let $actorContainer = $(`#actorContainer > .actor-${actorIndex}`); // 반드시 depth 1 로 할것
    let $statusEffectWrappers = [];
    let statusCountPerWrapper = actorIndex === 0 ? 7 : 4; // 한 페이지에 표시할 스테이터스 이펙트 갯수 적은 한번에 7개, 아군은 4개까지 표시
    let random = Math.floor(Math.random() * 10000);

    // 요소 DOM 에 채우기
    statusDtos.forEach(function (statusDto, statusIndex) {
        let currentStatusWrapperIndex = Math.floor(statusIndex / statusCountPerWrapper); // 현재 표시할 스테이터스의 페이지 (0 부터)
        let $statusEffectWrapper = $statusEffectWrappers[currentStatusWrapperIndex];
        if (!$statusEffectWrapper) { // 없으면 하나 추가
            $statusEffectWrapper = $(`<div class="status-effect-wrapper actor-${actorIndex} status-index-${currentStatusWrapperIndex} ${actorIndex === 0 ? 'enemy' : 'party'} random-${random}">`);
            $statusEffectWrappers.push($statusEffectWrapper);
        }

        let statusTypeName = statusDto.type.toLowerCase();
        statusTypeName = ['NONE', 'NO EFFECT', "MISS", 'RESIST'].includes(statusDto.name) ? 'none' : statusTypeName;
        let iconClassName = statusDto.imageSrc.length < 1 ? 'none-icon' : '';
        let statusRemovedClassName = statusDto.removed === true ? 'status-removed' : '';
        let $statusEffect = $(`
              <div class="status-effect status-effect-${statusIndex} ${statusTypeName} ${statusRemovedClassName}">
                <img src="${statusDto.imageSrc}" class="${iconClassName}">
                <span class="status-effect-text">${statusDto.effectText}</span>
              </div>
            `);
        $statusEffectWrapper.append($statusEffect);
    });
    $actorContainer.append(...$statusEffectWrappers);
    return $statusEffectWrappers;
}

/**
 *
 * @param $statusEffectWrappers {[]} actorIndex 에 해당하는 actor 의 wrappers
 * @param actorIndex
 * @param additionalDelay 추가 딜레이. [아군상태효과 1개 이상 일때 적 상태효과 +800]
 * @return 첫번째 상태효과가 fadeout 하는 시간 반환
 */
function showStatusEffect($statusEffectWrappers, actorIndex, additionalDelay = 0) {
    if ($statusEffectWrappers.length === 0) return 0;
    let iconShowDuration = 1000;0;

    // 요소 표시 타이머 지정
    let firstShowStartDelay = 0;
    $statusEffectWrappers.forEach(($statusEffectWrapper, wrapperIndex) => {
        if (!$statusEffectWrapper) return;
        let $statusEffects = $statusEffectWrapper.find('.status-effect');
        $statusEffects.each(function (statusEffectIndex, statusEffect) {
            let isRemovedEffect = statusEffect.classList.contains('status-removed');
            let showStartDelayPerIndex = isRemovedEffect ? 125 : 75; // 효과 1개당 선딜레이
            let showStartDelay = additionalDelay + (wrapperIndex * iconShowDuration) + (showStartDelayPerIndex * statusEffectIndex);
            if (statusEffectIndex === 0) firstShowStartDelay = showStartDelay;
            let maxOpacity = isRemovedEffect ? 0.7 : 1.0;

            setTimeout(() => {
                // console.log('[showStatusEffect] statusIndex = ', statusEffectIndex, ' statusName = ', $(statusEffect).find('.status-effect-text').text(), ' showStartDelay = ', showStartDelay);
                // 제거 이팩트 재생
                if (isRemovedEffect) {
                    player.play(Player.playRequest(`actor-${actorIndex}`, Player.c_animations.ABILITY_EFFECT_ONLY, {abilityType: BASE_ABILITY.DISPEL}));
                }
                // 스테이터스 이펙트표시
                $(statusEffect).fadeTo(100, maxOpacity).delay(750).fadeTo(150, 0); // iconShowDuration 1000
            }, showStartDelay);
        });
    });
    let firstFadeOutStartTime = firstShowStartDelay + 850; // fadeTo maxOpacity + delay
    return firstFadeOutStartTime;
}