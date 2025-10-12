async function processResponseMoves(responses) {
    // 파싱
    let moveResponses = parseMoveResponseList(responses);
    console.log('===[processResponseMoves]============== \n moveResponses = \n', moveResponses);

    // 결과 리스트에 폼체인지가 있을경우 비디오 미리로드
    if (moveResponses.some(response => response.moveType === MoveType.FORM_CHANGE_DEFAULT)) {
        waitingProcess(true);
        await loadNextEnemyActor();
        waitingProcess(false);
    }

    for (const response of moveResponses) {
        let charOrder = response.charOrder; // mainActorOrder, 0: enemy, 1~ character
        // console.log('charOrder = ' + charOrder + ' response = ' + response);

        switch (response.moveType.getParentType()) {
            case MoveType.SUPPORT_ABILITY:
            case MoveType.ABILITY:
                charOrder !== 0 ? await processAbility(response) : await processEnemyAbility(response);
                break;
            case MoveType.ATTACK:
                charOrder !== 0 ? await processAttack(response) : await processEnemyAttack(response);
                break;
            case MoveType.CHARGE_ATTACK:
                charOrder !== 0 ? await processChargeAttack(response) : await processEnemyChargeAttack(response);
                break;
            case MoveType.STANDBY:
                await processEnemyStandBy(response);
                break;
            case MoveType.BREAK:
                await processEnemyBreak(response);
                break;
            case MoveType.FORM_CHANGE:
                response.moveType === MoveType.FORM_CHANGE_DEFAULT ? await processFormChange(response) : null; // FORM_CHANGE_ENTRY 는 무시
                break;
            case MoveType.GUARD:
                $(`#actorContainer > .actor-${response.charOrder} .guard-status`).removeClass('guard-on').addClass('guard-on-processing'); // 가드 이펙트 연하게
                break;
            case MoveType.FATAL_CHAIN:
                await processFatalChain(response);
                break;
            case MoveType.SUMMON:
                await processSummon(response);
                break;
            case MoveType.TURN_END:
                await processTurnEndProcess(response);
                $('#actorContainer .guard-status').removeClass('guard-on-processing'); // 가드 해제
                break;
            case MoveType.DEAD:
                await processDead(response);
                break;
            case MoveType.ETC:
                if (response.moveType === MoveType.STRIKE_SEALED) await processStrikeSealed(response);
                if (response.moveType === MoveType.SYNC) await processSync(response);
                break;
            case MoveType.ROOT:
            default:
                console.log('[processResponseMoves] invalid type]', response.moveType);
        }
        syncHpsAndChargeGauges(response.hps, response.hpRates, response.chargeGauges, response.fatalChainGauge);
    }

    // 가드 해제
    $('#actorContainer .guard-status').removeClass('guard-on-processing');

    // 어빌리티 커맨드시 후처리
    if (moveResponses[0].moveType === MoveType.SYNC && moveResponses[1]?.moveType.getParentType() === MoveType.ABILITY) {
        // 첫번째가 sync, 두번째가 ABILITY 인 경우 직접 눌러 발동한 어빌리티 취급, CHECK 나중에 조건 개선 필요, 자동발동 어빌리티와 구분해야함.
        let abilityResponse = moveResponses[1];
        let abilityCharOrder = abilityResponse.charOrder;
        // 어빌리티 후처리 (서폿어빌 X) -> 어빌리티 레일 에서 삭제 및 오버레이
        $('.ability-rail-wrapper .rail-item').eq(0).remove();
        let $processedAbility = $('.ability-panel.actor-' + abilityCharOrder + ' [data-ability-id=' + +']');
        $processedAbility.find('.ability-overlay').show();
    } else {
        // 공격 후처리
        cancelAttack();
    }

}

async function processDead(response) {
    console.log('[processDead] resp = ', response);
    // charOrder 가 처리된 순서로 넘어옴에 주의
    let charOrder = response.charOrder - 100;

    // 사망
    let effectDuration = await player.play(Player.playRequest('actor-' + charOrder, Player.c_animations.DEAD));
    await new Promise(resolve => setTimeout(() => resolve(effectDuration), effectDuration))

    // battle-portrait 삭제
    let $emptyBattlePortrait = $('<div>').append(
        $('<div>').addClass('battle-portrait empty').append(
            $('<img>')
                .attr('src', '/static/assets/img/gl/ch-empty.jpg')
                .attr('data-seq', charOrder))); // 이거 어따쓰는건지?
    let $deadBattlePortrait = $('.battle-portrait').eq(charOrder - 1); // empty 까지 포함해야 제대로 순서구해짐
    console.log('[processDead] $deadBattlePortrait = ', $deadBattlePortrait, ' $emptyBattlePortrait = ', $emptyBattlePortrait)
    $deadBattlePortrait.before($emptyBattlePortrait);
    $deadBattlePortrait.remove();

    // abilityPanel 삭제 (되도록 slickRemove 로 지우기)
    let abilityPanels = $('#abilitySlider .slick-slide:not(.slick-cloned) .ability-panel');
    let deadCharacterAbilityPanel = abilityPanels.filter(`[data-character-order="${charOrder}"]`);
    $('#abilitySlider').slick('slickRemove', abilityPanels.index(deadCharacterAbilityPanel));

    // player.actor 삭제
    player.removeActor(charOrder);

    // 가드 표시 있으면 삭제
    $('#actorContainer .guard-status').eq(charOrder - 1).removeClass('guard-on-processing');

    let totalEndTime = effectDuration + 200;
    return new Promise(resolve => setTimeout(function () {
        console.log(response.moveType.name + ' done');
        resolve();
    }, totalEndTime));
}

async function processSync(response) {
    // 데미지 처리 X

    // 이펙트는 공통 이펙트 사용 (버프 있거나 힐 있을때만)
    let effectDuration = 0;
    if (response.addedBuffStatusesList.find(addedBuffStatuses => addedBuffStatuses.length > 0)
        || response.heals.find(heal => heal > 0)) {
        effectDuration = await player.play(Player.playRequest('global', Player.c_animations.ABILITY_MOTION, {abilityType: 'buffForAll'}));
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


function processStrikeSealed(response) {
    // let charOrder = response.charOrder;
    // let $effectVideo = $(`.global-effect-video-wrapper.actor-${charOrder} .global-effect-video`);
    // let $damagedVideo = charOrder === 0
    //     ? getEnemyDamagedVideo()
    //     : getVideo(charOrder, MoveType.DAMAGED_DEFAULT);
    // $effectVideo.attr('src', GlobalSrc.SHOCKED.video);
    //
    // // 이펙트 재생
    // playVideo($damagedVideo.effect, null, $damagedVideo.idle);
    // playVideo($effectVideo, null, null);
    // // 사운드 재생
    // window.effectAudioPlayer.loadAndPlay(GlobalSrc.SHOCKED.audio);
    //
    // processBuffEffect([new StatusDto({
    //     type: 'BUFF',
    //     name: '공격불가',
    //     imageSrc: '',
    //     effectText: '공격불가',
    //     duration: '1'
    // })], [], [], 0);
    //
    // let totalEndTime = $effectVideo.get(0).duration * 1000;
    // return new Promise(resolve => setTimeout(function () {
    //     console.log(response.moveType.name + ' done');
    //     resolve();
    // }, totalEndTime));
}

function processTurnEndProcess(response) {
    let totalEndTime = 0; // 기본적으로 아래의 처리들 중 1개만 실행된다.

    // 힐 값 있으면 턴종힐
    if (response.heals.reduce((acc, item) => acc + item, 0) > 0)
        totalEndTime = processHealEffect(response.heals, 0);

    // 추가된 버프 있으면 오의게이지 증가 (일반 버프 스테이터스는 턴종이 아닌 캐릭터 로직으로 처리된다)
    if (response.addedBuffStatusesList.reduce((acc, item) => acc + item.length, 0) > 0) {
        totalEndTime = processBuffEffect(response.addedBuffStatusesList, response.removedBuffStatusesList, response.removedDebuffStatusesList, 0);
        totalEndTime /= 2; // 가속
    }

    // 데미지 있으면 턴종데미지
    if (response.damages.reduce((acc, item) => acc + item, 0) > 0) {
        if (response.enemyAttackTargetOrders.length === 0) {
            // 적에 대한 턴종 데미지 (1개만 발생, 타겟 오더 없음)
            let $damageWrapper = $('<div>', {class: 'damage-wrapper ability'});
            let $damageElements = getDamageElement(0, response.elementTypes[0], 'ability', 0, response.damages[0], []);
            $damageWrapper.append($damageElements.$damage.addClass('party-turn-damage-show')).appendTo($('#actorContainer>.actor-0'));
            window.effectAudioPlayer.loadAndPlay(GlobalSrc.DEBUFF.audio);
            setTimeout(() => player.play(Player.playRequest('actor-0', Player.c_animations.DAMAGE)), 100); // 약간 늦게
            setTimeout(() => $damageWrapper.remove(), 1000);
            totalEndTime = 600;
        } else {
            enemyDamagesPostProcess(response, 0, true);
            window.effectAudioPlayer.loadAndPlay(GlobalSrc.DEBUFF.audio);
            totalEndTime = 600;
        }

        return new Promise(resolve => setTimeout(function () {
            console.log('TURN_END_PROCESS done', response.moveType.name);
            resolve();
        }, totalEndTime));

    }
}

// 체력 및 오의게이지 갱신 메서드 -> 나중에 버프, 디버프로 아군 적군의 갱신타이밍이 달라지는 지점이 있으니 미리 분리
// 체력회복, 자신에게 데미지, 적의 차지턴증가, 내 오의게이지 증가 등 나중에 미리 구분 처리
// 구분처리 -> 공통처리 순으로 진행하고 공통처리를 해당 무브 종료시 호출.
function syncHpsAndChargeGauges(responseHps, responseHpRates, responseChargeGauges, fatalChainGauge) {
    syncPartyHp(responseHps, responseHpRates);
    syncPartyChargeGauge(responseChargeGauges);
    syncEnemyHp(responseHps, responseHpRates);
    syncEnemyChargeTurn(responseChargeGauges);
    syncFatalChainGauge(fatalChainGauge)
}

function syncFatalChainGauge(fatalChainGauge) {
    $('.fatal-chain-gauge-value').find('.value').text(fatalChainGauge);
    $('.fatal-chain-gauge .progress-bar').css('width', fatalChainGauge + '%');
}

/**
 * 아군의 오의 게이지 갱신
 * @param chargeGauges
 */
function syncPartyChargeGauge(chargeGauges) {
    let partyMemberChargeGauges = chargeGauges.splice(1);
    partyMemberChargeGauges.forEach(function (chargeGauge, index) {
        let charOrder = index + 1;
        $('.battle-portrait.actor-' + charOrder).find('.charge-gauge')
            .find('.value').text(chargeGauge)
            .end().find('.progress-bar').css('width', chargeGauge + '%');
        $('.ability-panel.actor-' + charOrder).find('.charge-gauge')
            .find('.value').text(chargeGauge)
            .end().find('.progress-bar').css('width', chargeGauge + '%');
    });
}

/**
 * 적의 차지턴 갱신
 * @param chargeGauges
 */
function syncEnemyChargeTurn(chargeGauges) {
    let enemyChargeTurn = chargeGauges[0];
    $('.charge-turn-container.enemy .charge-turn').removeClass('active').each(function (index, element) {
        enemyChargeTurn > index && $(element).addClass('active');
    });
}

/**
 * 아군의 체력 갱신
 */
function syncPartyHp(responseHps, responseHpRates) {
    let partyMemberHps = responseHps.splice(1);
    let partyMemberHpRates = responseHpRates.splice(1);

    partyMemberHps.forEach(function (hp, index) {
        let hpRate = partyMemberHpRates[index];
        let charOrder = index + 1;
        $('.battle-portrait.actor-' + charOrder).find('.hp-gauge')
            .find('.value').text(hp)
            .end().find('.progress-bar').css('width', hpRate + '%');
        $('.ability-panel.actor-' + charOrder).find('.hp-gauge')
            .find('.value').text(hp)
            .end().find('.progress-bar').css('width', hpRate + '%');
    });
}

/**
 * 적의 체력 갱신
 */
function syncEnemyHp(responseHps, responseHpRates) {
    let enemyHp = responseHps[0];
    let enemyHpRate = responseHpRates[0];

    $('.hp-container.enemy')
        .find('.value-hp').text(enemyHpRate + '%')
        .end().find('.progress-bar').css('width', enemyHpRate + '%');
}

/**
 * 웨이팅 띄우기
 * @param toWaiting true: to waiting, false: to normal
 */
function waitingProcess(toWaiting) {
    if (toWaiting) {
        $('.waiting-video-container').css('visibility', 'visible').find('.waiting-video').get(0).play();
        $('#container').addClass('deActivated');
    } else {
        $('.waiting-video-container').css('visibility', 'hidden').find('.waiting-video').get(0).pause();
        $('#container').removeClass('deActivated');
    }
}