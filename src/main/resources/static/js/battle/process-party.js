async function processAttack(response) {
    let attackHitCount = response.damages.length; // 공격 데미지 발생수
    let attackMultiHitCount = response.attackMultiHitCount; // 난격 수
    let attackCount = attackHitCount / attackMultiHitCount; // 본 공격 카운트 (1 || 2 || 3, 난격제외)
    let elementType = response.elementTypes[0];

    let attackMotions = [Player.c_animations.ATTACK_SHORT, Player.c_animations.ATTACK_DOUBLE, Player.c_animations.ATTACK_TRIPLE];

    // 데미지 채우기
    let $targetActorContainer = $('#actorContainer>.actor-0');
    let currentDamageWrapperIndex = $targetActorContainer.find('damage-wrapper').length;
    let $damageWrapper = $('<div>', {class: 'damage-wrapper attack party actor-' + response.charOrder + ' damage-wrapper-index-' + currentDamageWrapperIndex});
    response.damages.forEach(function (damage, index) {
        let attackIndex = Math.floor(index / attackMultiHitCount); // 현재 인덱스의 기본타수 순서 (0, 1, 2 현재 트리플 어택까지 구현했으므로 여기까지)
        let multiAttackIndex = index % attackMultiHitCount; // 현재 인덱스의 난격 타수 순서 (공격마다 0-1-2-3-... 씩으로 진행, 0이면 난격이 아님)
        let missClassName = damage === 'MISS' ? ' damage-miss' : '';
        let attackDamageIndexClassName = ' damage-index-' + attackIndex + ' multi-' + multiAttackIndex; // 난격 여부 확인 후 클래스 설정 / [012 345 678] 3타 3난격 시 di-0 m-0, di-0 m-1, di-0 m-2, di-1 m-0, di-1 m-1, ...

        let $attackDamage = $('<div>', { // 본 공격 (+ 난격)
            class: 'attack-damage actor-' + response.charOrder + ' element-type-' + elementType.toLowerCase() + attackDamageIndexClassName + missClassName,
            text: damage,
        });
        $damageWrapper.append($attackDamage);
        let $additionalDamage = $('<div>', { // 추격
            class: 'additional-damage-wrapper actor-' + response.charOrder + ' element-type-' + elementType.toLowerCase() + attackDamageIndexClassName + missClassName,
            text: damage // 공간 사용을 위해
        }).append((response.additionalDamages[index] || []).map(additionalDamage =>
            $('<div>', {
                class: 'additional-damage element-type-' + elementType.toLowerCase(),
                text: additionalDamage
            })
        ))
        $damageWrapper.append($additionalDamage);
        // 마지막에 DOM 에 추가
        index >= attackHitCount - 1 ? $targetActorContainer.append($damageWrapper) : null;
    });

    // 데미지 표시
    let $attackDamages = $damageWrapper.find('.attack-damage');
    let $additionalDamages = $damageWrapper.find('.additional-damage-wrapper');
    let lastEffectDuration = 0;
    let attackPlayingPromise = null;
    let enemyDamageMotion = Player.getEnemyDamageMotion();
    for (let [index, damage] of response.damages.entries()) {
        let attackIndexExact = index / attackMultiHitCount; // 현재 인덱스의 공격 인덱스 (3타 3난격 9데미지 기준 0, 0.33, 0.66, 1, 1.33, ...)
        let attackIndex = Math.floor(attackIndexExact); // 실제 공격 인덱스 (0, 1, 2)
        let multiAttackIndex = index % attackMultiHitCount; // 현재 인덱스의 난격 타수 순서 (공격마다 0-1-2-3-... 씩으로 진행, 0이면 난격이 아님)
        let effectDuration = 0;
        let isLastAttack = attackIndex + 1 === attackCount;
        if (Number.isInteger(attackIndexExact)) { // 현재 attackIndexExact 가 정수이면 모션재생
            if (attackPlayingPromise) await attackPlayingPromise; // 이전 모션을 기다림
            let attackMotion = attackCount === 1 ? Player.c_animations.ATTACK : attackMotions[attackIndex];
            let attackRequest = Player.playRequest(`actor-${response.charOrder}`, attackMotion, {
                multiHitCount: attackMultiHitCount,
                isLastAttack: isLastAttack
            });
            let damageRequest = Player.playRequest('actor-0', enemyDamageMotion);
            effectDuration = await player.playWithOthers(attackRequest, [damageRequest]);
            lastEffectDuration = effectDuration;

            attackPlayingPromise = new Promise(resolve => setTimeout(function () {
                resolve(effectDuration); // 다음 모션 재생 전 대기 promise
            }, effectDuration))
        }
        let startDelay = 0;
        let multiAttackAdditionalDelay = multiAttackIndex * 115; // 난격마다 추가 딜레이 (이펙트 모션은 4프레임, 약 120ms 마다 재생됨)
        let totalDelay = startDelay + multiAttackAdditionalDelay; // 최종 딜레이
        console.log('processAttack, index = ', index, ' totalDelay = ', totalDelay, 'effectDuration = ', effectDuration, ' multiAttackAdditionalDelay = ', multiAttackAdditionalDelay, ' isLastAttack = ', isLastAttack)

        let $attackDamage = $attackDamages.eq(index);
        let $additionalDamage = $additionalDamages.eq(index);
        setTimeout(() => {
            $attackDamage.addClass('party-attack-damage-show'); // 본 데미지
            $additionalDamage.children().each(function (index, additionalDamage) { // 추가데미지
                setTimeout(() => $(additionalDamage).addClass('party-additional-damage-show'), 50 * (index + 1));
            });
        }, totalDelay);
    }

    // 데미지 제거
    setTimeout(() => $damageWrapper.remove(), lastEffectDuration + Constants.Delay.damageShowDelete)

    //  CHECK 일반공격후 스테이터스 갱신은 현재 없음.

    let totalEndTime = lastEffectDuration + 50 + (100 * (attackMultiHitCount - 1)); // 공격은 재공격때문에 약간 지연을 줘야함 (특히 난격상황에서 더)
    console.log("[processAttack] totalEndTime = " + totalEndTime)
    return new Promise(resolve => setTimeout(function () {
        console.log('ATTACK done');
        resolve();
    }, totalEndTime));
}

async function processAbility(response) {
    let effectDuration = await player.play(Player.playRequest('actor-' + response.charOrder, response.motion, {abilityType: response.moveType.name}));

    let enemyDamageMotion = Player.getEnemyDamageMotion();
    if (response.damages.length > 0) {
        let $damageWrapper = fillDamage(response, 0);
        // 데미지 표시
        let damageShowClass = response.damages.length > 3 ? 'multiple-ability-damage-show' : 'ability-damage-show'
        let effectHitDuration = (effectDuration - 200) / response.damages.length; // 약간 가속하는게 나음
        response.damages.forEach(function (damage, damageIndex, damageArray) {
            let startDelay = effectHitDuration * damageIndex;
            setTimeout(function () {
                player.play(Player.playRequest('actor-0', enemyDamageMotion));
                $damageWrapper.find('.ability-damage').eq(response.damages.length - 1 - damageIndex).addClass(damageShowClass);
            }, startDelay);
        });
        // 데미지 제거
        setTimeout(() => $damageWrapper.remove(), effectDuration + Constants.Delay.damageShowDelete);
    }

    // 스테이터스 아이콘 갱신
    processStatusIconSync(response.currentBattleStatusesList, effectDuration);
    // 힐 이펙트 처리
    let healEndTime = processHealEffect(response.heals, effectDuration);
    // 버프 이펙트 처리
    let buffEndTime = processBuffEffect(response.addedBuffStatusesList, response.removedBuffStatusesList, response.removedDebuffStatusesList, healEndTime);
    // 디버프 이펙트 처리
    let debuffEndTime = processDebuffEffect(response.addedDebuffStatusesList, buffEndTime);

    let totalEndTime = Math.max(effectDuration, healEndTime, buffEndTime, debuffEndTime);
    // console.log('[processAbility] totalTime', totalEndTime, 'abilityDuration ', effectDuration, 'buffEndTime ', buffEndTime, 'debuffEndTiem ', debuffEndTime);

    return new Promise(resolve => setTimeout(function () {
        console.log(response.moveType.name + ' done');
        resolve();
    }, totalEndTime));
}

async function processChargeAttack(response) {
    // 데미지 채우기
    let $damageWrapper = fillDamage(response, 0);

    // 이펙트 재생
    let effectDuration = await player.play(Player.playRequest('actor-' + response.charOrder, response.motion));
    let effectHitDelay = effectDuration - 100; // 직전부터 데미지 표시시작
    // 적 피격 이펙트 재생
    setTimeout(() => player.play(Player.playRequest('actor-0', Player.getEnemyDamageMotion())), effectHitDelay + 50); // 데미지보다 약간 느리게
    // 데미지 표시
    setTimeout(() => $damageWrapper.find('.charge-attack-damage').addClass('party-attack-damage-show'), effectHitDelay);
    // 데미지 제거
    setTimeout(() => $damageWrapper.remove(), effectHitDelay + Constants.Delay.damageShowDelete);
    // 스테이터스 아이콘 갱신
    processStatusIconSync(response.currentBattleStatusesList, effectHitDelay + Constants.Delay.damageShowToNext);
    // 힐 이펙트 처리
    let healEndTime = processHealEffect(response.heals, effectHitDelay + Constants.Delay.damageShowToNext);
    // 버프 이펙트 처리
    let buffEndTime = processBuffEffect(response.addedBuffStatusesList, response.removedBuffStatusesList, response.removedDebuffStatusesList, healEndTime);
    // 디버프 이펙트 처리
    let debuffEndTime = processDebuffEffect(response.addedDebuffStatusesList, buffEndTime);

    let totalEndTime = debuffEndTime;
    // console.log('[processChargeAttack] chargeAttackDuration ', effectDuration, 'buffEndTime ', buffEndTime, 'debuffEndTiem ', debuffEndTime, 'totalEndTime = ', totalEndTime);
    return new Promise(resolve => setTimeout(function () {
        console.log(response.moveType.name + ' done');
        resolve();
    }, totalEndTime));
}

async function processSummon(response) {
    // 데미지 삽입 (어빌리티)
    let $damageWrapper = fillDamage(response, 0);

    // 소환 이펙트 재생
    let effectDuration = 0;
    let summonDuration = await player.play(Player.playRequest('actor-1', Player.c_animations.SUMMON));
    await new Promise(resolve => setTimeout(() => resolve(summonDuration), summonDuration))
    let summonAttackDuration = await player.play(Player.playRequest('actor-1', Player.c_animations.SUMMON_ATTACK));
    await new Promise(resolve => setTimeout(() => resolve(summonAttackDuration), summonAttackDuration))
    let summonDamageDuration = await player.play(Player.playRequest('actor-1', Player.c_animations.SUMMON_DAMAGE));
    effectDuration = summonDamageDuration;
    let effectHitDelay = effectDuration - 100;

    // 피격 이펙트, 데미지 표시
    setTimeout(function () {
        // 화면 흔들기
        $('#videoContainer').addClass('push-left-down-effect');
        setTimeout(function () {
            $('#videoContainer').removeClass('push-left-down-effect');
        }, 150);

        let damageShowClass = response.damages.length > 2 ? 'multiple-damage-show' : 'damage-show';
        let enemyDamageMotion = Player.getEnemyDamageMotion();
        $damageWrapper.find('.ability-damage').each(function (index, abilityDamage) {
            setTimeout(() => {
                // 피격재생
                player.play(Player.playRequest('actor-0', enemyDamageMotion));
                // 데미지 표시
                $(abilityDamage).addClass(damageShowClass)
            }, index * 100);
        })
    }, effectHitDelay);
    // 데미지 제거
    setTimeout(() => $damageWrapper.remove(), effectDuration + Constants.Delay.damageShowDelete);
    // 스테이터스 아이콘 갱신
    processStatusIconSync(response.currentBattleStatusesList, effectHitDelay + Constants.Delay.damageShowToNext);
    // 힐 이펙트 처리
    let healEndTime = processHealEffect(response.heals, effectHitDelay + Constants.Delay.damageShowToNext);
    // 버프 이펙트 처리
    let buffEndTime = processBuffEffect(response.addedBuffStatusesList, response.removedBuffStatusesList, response.removedDebuffStatusesList, healEndTime);
    // 디버프 이펙트 처리
    let debuffEndTime = processDebuffEffect(response.addedDebuffStatusesList, buffEndTime);

    let totalEndTime = debuffEndTime;
    // console.log('[processSummon] totalTime', totalEndTime, 'effectDuration ', effectDuration, 'buffEndTime ', buffEndTime, 'debuffEndTime ', debuffEndTime);
    return new Promise(resolve => setTimeout(function () {
        console.log(response.moveType.name + ' done');
        resolve();
    }, totalEndTime));
}

async function processFatalChain(response) {
    let fatalChainRequest = Player.playRequest('actor-1', Player.c_animations.ABILITY_EFFECT_ONLY, {abilityType: MoveType.FATAL_CHAIN_DEFAULT.name});
    let mainCharacterAttackRequest = Player.playRequest('actor-1', Player.c_animations.ATTACK_MOTION_ONLY);
    let partyMembersActorIndexArray = player.actors.values().filter(actor => actor.isCharacter() && actor.actorId !== 'actor-1').map(actor => actor.actorIndex);
    let partyAttackRequests = partyMembersActorIndexArray.map(actorIndex => Player.playRequest('actor-' + actorIndex, Player.c_animations.ATTACK)).toArray();
    // 아군전체 어택 -> 페이탈체인 이펙트
    let partyAttackDuration = await player.playWithOthers(mainCharacterAttackRequest, partyAttackRequests)
    await new Promise(resolve => setTimeout(() => resolve(partyAttackDuration), partyAttackDuration / 3));
    let fatalChainEffectDuration = await player.play(fatalChainRequest);

    // 데미지 삽입 (페이탈 체인은 데미지 1회)
    let $damageWrapper = fillDamage(response, 0);
    // 피격 이펙트, 데미지 표시
    let effectHitDelay = fatalChainEffectDuration - 100; // 데미지 히트 딜레이
    setTimeout(function () {
        // 적 피격 이펙트 재생
        player.play(Player.playRequest('actor-0', Player.getEnemyDamageMotion()));
        // 데미지 표시
        $damageWrapper.find('.ability-damage').addClass('party-attack-damage-show');
        // 제거
        setTimeout(() => $damageWrapper.remove(), Constants.Delay.damageShowDelete);
    }, effectHitDelay);

    // 스테이터스 아이콘 갱신
    processStatusIconSync(response.currentBattleStatusesList, fatalChainEffectDuration + Constants.Delay.damageShowToNext);
    // 버프 이펙트 처리
    let buffEndTime = processBuffEffect(response.addedBuffStatusesList, response.removedBuffStatusesList, response.removedDebuffStatusesList, fatalChainEffectDuration + Constants.Delay.damageShowToNext);
    // 디버프 이펙트 처리
    let debuffEndTime = processDebuffEffect(response.addedDebuffStatusesList, buffEndTime);

    let totalEndTime = debuffEndTime;
    // console.log('[processFatalChain] totalTime', totalEndTime, 'effectDuration ', fatalChainEffectDuration, 'buffEndTime ', buffEndTime, 'debuffEndTime ', debuffEndTime);

    return new Promise(resolve => setTimeout(function () {
        console.log(response.moveType.name + ' done');
        resolve();
    }, totalEndTime));
}

/**
 * 가드처리
 * @param response
 */
function processGuard(response) {
    console.log('[processGuard] response = ', response);
    let isGuardActivated = response.guardActivated; //
    response.guardResults.forEach(function (guardResult) {
        if (guardResult.guardOn) {
            $(`#actorContainer > .actor-${guardResult.currentOrder}`).find('.guard-status').addClass('guard-on');
        } else {
            $(`#actorContainer > .actor-${guardResult.currentOrder}`).find('.guard-status').removeClass('guard-on');
        }
    });

    let audioPlayer = new AudioPlayer().init();
    let src = isGuardActivated ? $('.global-audio-container .guard-on').attr('src') : $('.global-audio-container .guard-off').attr('src');
    audioPlayer.loadSound(src).then(() => {
        audioPlayer.playAllSounds();
    })
}

// 데미지 삽입 (미리 삽입해놔야됨)
function fillDamage(response, targetActorIndex) {
    let damageType = "none";
    switch (response.moveType.getParentType()) {
        case MoveType.ATTACK:
            damageType = "attack";
            break;
        case MoveType.SUMMON:
        case MoveType.FATAL_CHAIN:
        case MoveType.SUPPORT_ABILITY:
        case MoveType.ABILITY:
            damageType = "ability";
            break;
        case MoveType.CHARGE_ATTACK:
            damageType = "charge-attack";
            break;
        default:
            damageType = "none";
    }

    let $targetActorContainer = $(`#actorContainer > .actor-${targetActorIndex}`);
    let currentDamageWrapperIndex = $targetActorContainer.find('.damage-wrapper').length;
    let $damageWrapper = $('<div>', {class: `damage-wrapper ${damageType} damage-wrapper-index-${currentDamageWrapperIndex}`});
    response.damages.forEach(function (damage, damageIndex) {
        let $damageElements = getDamageElement(response.charOrder, response.elementTypes[0], damageType, damageIndex, damage, []);
        $damageWrapper.prepend($damageElements.$damage);
    })
    $targetActorContainer.append($damageWrapper);
    return $damageWrapper;
}