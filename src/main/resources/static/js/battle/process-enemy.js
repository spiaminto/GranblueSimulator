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
    }, totalEndTime));
}

async function processEnemyAbility(response) {
    let isEnemyPowerUp = response.enemyPowerUp;
    let isEnemyCtMax = response.enemyCtMax; // CHECK 일단 미구현
    
    // 이펙트 재생
    let motion = response.motion || 'none';
    let isEffectOnly = motion.includes('ab_');// CHECK 적은 어빌리티 모션이 없으므로, ab_ 어빌리티 모션이 주어지면 effectOnly 로 간주 (나중에 다른 모션을 사용한는 어빌리티의 경우 해당 모션을 고려해야 하므로 ab_ 만 따로 설정)

    let effectDuration = await player.play(Player.playRequest('actor-0', motion, {abilityType: response.moveType.name, isEffectOnly: isEffectOnly}));

    // 데미지 후처리 (데미지 표시, 아군 피격 재생)
    if (response.damages.length > 0) enemyDamagesPostProcess(response, effectDuration);

    // 스테이터스 처리
    let totalEndTime = await processStatusEffect(response, effectDuration);

    console.log('[processEnemyAbility] DONE totalTime', totalEndTime, 'effectDuration ', effectDuration);
    return new Promise(resolve => setTimeout(() => resolve(), Constants.Delay.globalMoveDelay));
}

async function processEnemyChargeAttackPreEffect() {
    let effectDuration = await player.play(Player.playRequest('actor-0', Player.c_animations.ABILITY_EFFECT_ONLY, {abilityType: BASE_ABILITY.ENEMY_AB_START, isEffectOnly: true}));
    return new Promise(resolve => setTimeout(function () {
        console.log('[processEnemyChargeAttackPreEffect] DONE');
        resolve();
    }, effectDuration));
}

async function processEnemyChargeAttack(response) {
    await processEnemyChargeAttackPreEffect();

    let effectDuration = await player.play(Player.playRequest('actor-0', response.motion));

    // 적 스탠바이 해제
    $('.enemy-info-container').attr('data-standby-motion', 'none');
    $('.enemy-info-container').attr('data-standby-move-class', 'none');
    // 전조 컨테이너 deActivate
    setTimeout(function () {
        $('.omen-container-top').removeClass('activated')
            .find('.omen-text').attr('class', 'omen-text')
            .find('.omen-prefix').text('').end()
            .find('.omen-value').text('').end()
            .find('.omen-info').text('');
        $('.omen-container-bottom.enemy').removeClass('activated')
            .find('.omen-text').attr('class', 'omen-text')
            .find('.omen-prefix').text('');
    }, effectDuration);

    // 데미지 후처리 (데미지 표시, 아군 피격 재생)
    enemyDamagesPostProcess(response, effectDuration);

    // 스테이터스 처리
    let totalEndTime = await processStatusEffect(response, effectDuration);

    console.log('[processEnemyChargeAttack] DONE totalTime', totalEndTime, 'effectDuration ', effectDuration);
    return new Promise(resolve => setTimeout(() => resolve(), Constants.Delay.globalMoveDelay));
}

async function processEnemyStandBy(response) {
    let omenValue = response.omenCancelCondInfo.indexOf('해제불가') >= 0 ? '' : response.omenValue; // 해제불가인경우 렌더 x

    if ($('.omen-container-top').hasClass('activated')) { // 이미 전조 발생중
        let beforeOmenValue = Number($('.omen-container-top').find('.omen-text .omen-value').text());
        let effectDuration = 0;
        if (omenValue && omenValue !== beforeOmenValue) { // 전조갱신시 standby 재생 (허수 몽핵 처리는 나중에)
            $('.omen-container-top').find('.omen-text .omen-value').text(omenValue);
            effectDuration = await player.play(Player.playRequest('actor-0', response.motion));
            effectDuration /= 3; // 원본도 감소시키는듯
        }
        return new Promise(resolve => setTimeout(() => resolve(), effectDuration));
    }

    // 적 스탠바이 상태 추가
    $('.enemy-info-container').attr('data-standby-motion', response.motion);
    // 전조 컨테이너 activate
    $('.omen-container-top').addClass('activated')
        .find('.omen-text').addClass(response.omenType.className)
        .find('.omen-prefix').text(response.omenCancelCondInfo).end()
        .find('.omen-value').text(omenValue).end()
        .find('.omen-info').text(response.omenInfo);
    $('.omen-container-bottom.enemy').addClass('activated')
        .find('.omen-text').addClass(response.omenType.className)
        .find('.omen-prefix').text(response.omenName);

    // 이펙트 재생
    let effectDuration = await player.play(Player.playRequest('actor-0', response.motion));
    effectAudioPlayer.playAdditionalSound(response.charName, response.motion);

    let totalEndTime = effectDuration
    return new Promise(resolve => setTimeout(function () {
        console.log('[processEnemyStandBy] DONE, move = ', response.moveType.name);
        resolve();
    }, totalEndTime));
}

async function processEnemyBreak(response) {
    // 적 비디오 컨테이너에 스탠바이 상태 해제
    $('.enemy-info-container').attr('data-standby-motion', 'none');
    // 전조 컨테이너 deactivate
    $('.omen-container-top').removeClass('activated')
        .find('.omen-text').removeClass(response.omenType.className)
        .find('.omen-prefix').text('').end()
        .find('.omen-value').text('').end()
        .find('.omen-info').text('');
    $('.omen-container-bottom.enemy').removeClass('activated')
        .find('.omen-text').removeClass(response.omenType.className)
        .find('.omen-prefix').text('');
    // 차지턴 갱신
    syncEnemyChargeTurn(response.chargeGauges);

    // 이펙트 재생
    let effectDuration = await player.play(Player.playRequest('actor-0', response.motion));
    effectAudioPlayer.playAdditionalSound(response.charName, response.motion);

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
    player.setBackgroundImage(Constants.DIASPORA[1].backgroundImage);
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
    $.ajax({
        url: '/api/enemy-src?memberId=' + memberId,
        type: 'GET',
        async: false,
        success: function (response) {
            assetInfo = response.assetInfo;
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
    console.log('enemyAnimation = ', enemyAnimation);
    loadActor(enemyAnimation, loadActorConfig);

    return new Promise(resolve => { // TODO 위에서 await 해서, 그냥 안기다려도 될듯
        let interval = setInterval(() => { // 스테이지에 로드되면 resolve
            let found = player.m_stage.children.find(child => child.name === assetInfo.asset.mainCjs);
            if (found) {
                clearInterval(interval);
                resolve();
            }
        }, 500);
    });
}


/**
 * 적의 데미지 발생관련 후처리
 * 적은 일반공격, (서포트)어빌리티, 차지어택의 데미지 표시방식 및 아군 피격처리가 거의 동일하므로 통합하여 사용
 * @param response
 * @param effectDuration
 * @param isTurnDamage 턴데미지 스타일용
 */
function enemyDamagesPostProcess(response, effectDuration, isTurnDamage = false) {
    let attackCount = Number(response.moveType.attackCount) || 0;
    let uniqueTargetOrders = [...new Set(response.enemyAttackTargetOrders)];
    let currentPhase = $('.enemy-info-container').attr('data-phase');

    let characterCount = player.getCharacterCount();
    let isAllTarget = characterCount > 1 && response.moveType !== MoveType.TURN_END_PROCESS
        ? response.allTarget // 캐릭터가 1명이면 allTarget 이어도 일반공격처럼 표시
        : response.totalHitCount >= 2 // 턴종 데미지시 데미지가 2회이상 발생하면 allTarget 으로 간주 (캐릭터별 딜레이 적용 위함)
    let isAllTargetSubstitute = isAllTarget && uniqueTargetOrders.length === 1; // 전체공격 감싸기 여부
    if (isAllTargetSubstitute) uniqueTargetOrders = Array(player.getCharacterCount()).fill(uniqueTargetOrders[0]); // 전체공격 감싸기 상황. 파티 인원만큼 감싸기 캐릭터의 order 늘림.

    let effectHitDelay = Constants.DIASPORA[currentPhase].hitDelay[response.motion] || 0; // 이펙트 시작 ~ 데미지 표시 까지 딜레이, 없으면 0
    let effectHitDuration = effectDuration - effectHitDelay; // 실제로 히트이펙트가 발생하는 기간
    let perHitDuration = attackCount === 0
        ? effectHitDuration / response.damages.length // 어빌리티, 오의시 1타가 사용할 길이
        : effectHitDuration / attackCount; // 일반공격시 1타가 사용할 길이
    let lastStartDelay = 0; // 데미지 래퍼 지우기용 마지막 시작 딜레이

    // 후행동 공격데미지와 겹치지 않도록 미리 데미지 래퍼 추가
    let $enemyDamageWrappers = new Map();
    uniqueTargetOrders.forEach(targetOrder => {
        $enemyDamageWrappers.set(targetOrder, $('<div>', {class: 'damage-wrapper enemy actor-' + targetOrder})); // CHECK 캐릭터랑 다르게 순서정보 없음. 차후 필요시 추가
    })
    //  데미지 마다 반복 - 데미지삽입, 데미지표시, 피격이펙트 재생
    response.damages.forEach(function (damage, index) {
        let startDelay = isAllTarget
            ? effectHitDelay + perHitDuration * (Math.floor(index / uniqueTargetOrders.length)) + (index % uniqueTargetOrders.length * 100) // 전체공격시 1타 길이로 중복제거한 타겟수만큼 끊어서 시작 딜레이 설정 (123 / 123 / 123...), 타겟별로 100ms 씩 추가 딜레이
            : effectHitDelay + perHitDuration * index; // 전체공격 아니면 index 만큼 1타 길이 사용
        let targetOrder = response.enemyAttackTargetOrders[index];
        let elementType = response.elementTypes[index];
        lastStartDelay = startDelay;

        // 데미지 채우기
        let $damageElements = getDamageElement(targetOrder, elementType, 'attack', response.damageTypes[index], index, damage, response.additionalDamages[index], true);
        let $enemyDamageWrapper = $enemyDamageWrappers.get(targetOrder);
        $enemyDamageWrapper.append($damageElements.$damage, $damageElements.$additionalDamage);
        // 마지막에 돔에 추가
        index >= response.damages.length - 1 ? $('#enemyAttackDamageContainer').append(Array.from($enemyDamageWrappers.values())) : null;
        if (index >= response.damages.length - 1) {
            $enemyDamageWrappers.entries().forEach(([key, value], index) => {
                $(`#actorContainer > .actor-${key}`).append(value);
            })
        }

        setTimeout(function () {
            // 데미지 표시
            let damageShowClassname = isTurnDamage ? 'enemy-turn-damage-show' : 'enemy-damage-show';
            $damageElements.$damage.addClass(damageShowClassname); // 본 데미지 표시
            $damageElements.$additionalDamage.children().each(function (index, additionalDamage) { // 추가데미지 표시
                setTimeout(() => $(additionalDamage).addClass(damageShowClassname), (index + 1) * 50);
            });
            // 아군 피격 재생
            player.play(Player.playRequest('actor-' + targetOrder, Player.c_animations.DAMAGE));
        }, startDelay)
    })
    // 데미지 제거
    // setTimeout(() => $enemyDamageWrappers.values().forEach((wrapper) => $(wrapper).remove()), lastStartDelay + Constants.Delay.damageShowDelete);
}