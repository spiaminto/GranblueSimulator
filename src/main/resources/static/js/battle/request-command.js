function requestAbility(charOrder, abilityOrder) {
    // console.log('[processAbility] start process charOrder = ' + charOrder + 'abilityOrder = ' + abilityOrder);

    // TODO 통신
    let characterId = $('#partyCommandContainer .battle-portrait').eq(charOrder - 1).data('character-id');
    let memberId = $('#memberInfo').data('member-id');
    let roomId = $('#roomInfo').data('room-id');
    let abilityId = $('#abilityInfo').data('ability-id');
    let moveType = abilityOrder > 1 ? MoveType.SECOND_ABILITY : MoveType.FIRST_ABILITY;
    moveType = abilityOrder > 2 ? MoveType.THIRD_ABILITY : moveType;
    let responseResults = null;
    $.ajax({
        url: '/api/ability',
        type: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({
            memberId: memberId,
            characterId: characterId,
            moveType: moveType.name,
            abilityId: abilityId,
            charOrder: charOrder,
            abilityOrder: abilityOrder,
            roomId: roomId
        }),
        async: false,
        success: function (response) {
            responseResults = response;
            console.log(responseResults);
            processResponseMoves(responseResults, abilityOrder);
        },
        error: function (response) {
            console.log(response);
        }
    });
}

function requestTurnProgress() {

    let memberId = $('#memberInfo').data('member-id');
    let roomId = $('#roomInfo').data('room-id');

    $.ajax({
        url: '/api/turn-progress',
        type: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({
            memberId: memberId,
            roomId: roomId
        }),
        async: false,
        success: function (response) {
            let responseResults = response;
            console.log(responseResults);
            processResponseMoves(responseResults); // TODO 나중에 파라미터 수정할것
        },
        error: function (error) {
            console.error('Error:', error);
        }
    });
}


async function processResponseMoves(responseResults) {
    for (const response of responseResults) {
        let moveType = MoveType.byName(response.moveType);
        let charOrder = response.charOrder; // mainActorOrder, 0: enemy, 1~ character
        // console.log('charOrder = ' + charOrder + ' moveType = ' + moveType.name);
        let parentMoveType = moveType.getParentType();
        switch (parentMoveType) {
            case MoveType.SUPPORT_ABILITY:
            case MoveType.ABILITY:
                await processAbility(response);
                break;
            case MoveType.ATTACK:
                charOrder !== 0 ? await processCharacterAttack(response) : await processEnemyAttack(response);
                break;
            case MoveType.CHARGE_ATTACK:
                charOrder !== 0 ? await processChargeAttack(response) : await  processEnemyChargeAttack(response);
                break;
            case MoveType.STANDBY:
                await processEnemyStandBy(response);
                break;
            case MoveType.BREAK:
                await processEnemyBreak(response);
                break;
            default:
                console.log('invalid type', parentMoveType);
        }
    }

    // 후처리
    console.log('im here')
    let firstMoveType = MoveType.byName(responseResults[0].moveType);
    let firstMoveCharOrder = responseResults[0].charOrder;
    if (firstMoveType.getParentType() === MoveType.ABILITY) {
        // 어빌리티 후처리 (서폿어빌 X) -> 어빌리티 레일 에서 삭제 및 오버레이
        let abilityOrder = firstMoveType === MoveType.FIRST_ABILITY ? 1 : MoveType.SECOND_ABILITY ? 2 : MoveType.THIRD_ABILITY ? 3 : -1;
        $('.ability-rail-wrapper .rail-ability').eq(0).remove();
        let $processedAbility = $('.ability-panel.actor-' + firstMoveCharOrder + ' .ability-' + abilityOrder);
        $processedAbility.find('.ability-overlay').show();
    }
}

function processEnemyStandBy(standbyResponse) {
    console.log('[processEnemyStandBy]', standbyResponse);
    let $enemyDefaultIdleVideo = $('.enemy-video-container .' + MoveType.IDLE_DEFAULT.className);
    let omenType = OmenType.byName(standbyResponse.omenType);
    console.log(omenType)
    let omenName = standbyResponse.omenName;
    let omenValue = standbyResponse.omenValue;
    let omenCancelCondInfo = standbyResponse.omenCancelCondInfo;
    let $omenContainerTop = $('.omen-container-top.enemy');
    let $omenContainerBottom = $('.omen-container-bottom.enemy');
    if ($enemyDefaultIdleVideo.hasClass('hidden')) {
        // 적이 현재 스탠바이 상태일경우 전조 갱신후 아래의 내용은 무시
        $omenContainerTop.find('.omen-text .omen-value').text(omenValue);
        return;   
    }

    let moveType = MoveType.byName(standbyResponse.moveType);
    let standByIdleType = moveType.getIdleType();
    let $enemyStandByVideo = $('.enemy-video-container .' + moveType.className);
    let $enemyStandByIdleVideo = $('.enemy-video-container .' + standByIdleType.className);

    // 적 비디오 컨테이너에 스탠바이 상태 추가
    $('.enemy-video-container').data('standby-move-class', moveType.className);

    // 전조 컨테이너 activate
    $omenContainerTop.addClass('activated')
        .find('.omen-text').addClass(omenType.className)
        .find('.omen-prefix').text(omenCancelCondInfo).end()
        .find('.omen-value').text(omenValue);

    $omenContainerBottom.addClass('activated')
        .find('.omen-text').addClass(omenType.className)
        .find('.omen-prefix').text(omenName);


    // 오디오 재생
    let audioPlayer = new AudioPlayer();
    let audioSrc = $('.enemy-audio-container').find('.' + moveType.className).attr('src');
    audioPlayer.loadSound(audioSrc, 0).then(() => {
        audioPlayer.playAllSounds();
    })
    // 비디오 재생
    $enemyDefaultIdleVideo.addClass('hidden');
    $enemyStandByVideo.removeClass('hidden').one('ended', function () {
        // 스탠바이 종료되면 스탠바이idle 로 전환
        $enemyStandByIdleVideo.removeClass('hidden').get(0).play();
        $enemyStandByVideo.addClass('hidden');
    }).get(0).play();

    let totalEndTime = $enemyStandByVideo.get(0).duration * 1000 + 100;
    return new Promise(resolve => setTimeout(function () {
        console.log('STANDBY done', moveType, standByIdleType);
        resolve();
    }, totalEndTime));

}

function processEnemyBreak(breakResponse) {
    let $enemyStandByIdleVideo = $('.enemy-video-container video:not(.hidden)');
    let breakType = MoveType.byName(breakResponse.moveType);
    let omenType = OmenType.byName(breakResponse.omenType);
    let $enemyBreakVideo = $('.enemy-video-container .' + breakType.className);
    let $enemyIdleDefaultVideo = $('.enemy-video-container .' + MoveType.IDLE_DEFAULT.className);
    let chargeGauges = breakResponse.chargeGauges;

    // 적 비디오 컨테이너에 스탠바이 상태 해제
    $('.enemy-video-container').data('standby-move-class', MoveType.NONE.className);

    // 차지턴 갱신
    syncEnemyChargeTurn(chargeGauges);

    // 전조 컨테이너 deactivate
    $(".omen-container").removeClass('activated')
        .find('.omen-text').removeClass(omenType.className)
        .find('.omen-prefix').text('')
        .find('.omen-value').text('');

    // 오디오 재생
    let audioPlayer = new AudioPlayer();
    let audioSrc = $('.enemy-audio-container').find('.' + breakType.className).attr('src');
    audioPlayer.loadSound(audioSrc, 0).then(() => {
        audioPlayer.playAllSounds();
    })

    // 비디오 재생
    $enemyStandByIdleVideo.addClass('hidden').get(0).pause();
    $enemyBreakVideo.removeClass('hidden').one('ended', function () {
        // 브레이크 끝나면 idle_default 로 복귀
        $enemyIdleDefaultVideo.removeClass('hidden').get(0).play();
        $(this).addClass('hidden');
    }).get(0).play();

    let totalEndTime = $enemyBreakVideo.get(0).duration * 1000 + 100;
    return new Promise(resolve => setTimeout(function () {
        console.log('BREAK done', breakType);
        resolve();
    }, totalEndTime));

    // TODO 전조해제

}

// 체력 및 오의게이지 갱신 메서드 -> 나중에 버프, 디버프로 아군 적군의 갱신타이밍이 달라지는 지점이 있으니 미리 분리
// 체력회복, 자신에게 데미지, 적의 차지턴증가, 내 오의게이지 증가 등 나중에 미리 구분 처리
// 구분처리 -> 공통처리 순으로 진행하고 공통처리를 해당 무브 종료시 호출.

function syncHpsAndChargeGauges(responseHps, responseHpRates, responseChargeGauges) {
    syncPartyHp(responseHps, responseHpRates);
    syncPartyChargeGauge(responseChargeGauges);
    syncEnemyHp(responseHps, responseHpRates);
    syncEnemyChargeTurn(responseChargeGauges);
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