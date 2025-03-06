function requestAbility(charOrder, abilityOrder) {
    // console.log('[processAbility] start process charOrder = ' + charOrder + 'abilityOrder = ' + abilityOrder);

    // TODO 통신
    let characterId = $('#battleMemberPresentContainer .battle-portrait').eq(charOrder - 1).data('character-id');
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
                await processChargeAttack(response);
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
}

function processEnemyStandBy(standbyResponse) {
    let $enemyDefaultIdleVideo = $('.enemy-video-container .' + MoveType.IDLE_DEFAULT.className);
    let omenType = OmenType.byName(standbyResponse.omenType);
    let omenValue = standbyResponse.omenValue;
    let omenCancelCondInfo = standbyResponse.omenCancelCondInfo;
    let $omenContainer = $('.omen-container.enemy');
    if ($enemyDefaultIdleVideo.hasClass('hidden')) {
        // 적이 현재 스탠바이 상태일경우 전조 갱신후 아래의 내용은 무시
        $omenContainer.find('.omen-text .omen-value').text(omenValue);
        return;   
    } 

    let moveType = MoveType.byName(standbyResponse.moveType);
    let standByIdleType = moveType.getIdleType();
    let $enemyStandByVideo = $('.enemy-video-container .' + moveType.className);
    let $enemyStandByIdleVideo = $('.enemy-video-container .' + standByIdleType.className);

    // 적 비디오 컨테이너에 스탠바이 상태 추가
    $('.enemy-video-container').data('standby-move-class', moveType.className);

    // 전조 컨테이너 activate
    $omenContainer.addClass('activated')
        .find('.omen-text').addClass(omenType.className)
        .find('.omen-prefix').text(omenCancelCondInfo).end()
        .find('.omen-value').text(omenValue);


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

    // TODO 전조추가

}

function processEnemyBreak(breakResponse) {
    let $enemyStandByIdleVideo = $('.enemy-video-container video:not(.hidden)');
    let breakType = MoveType.byName(breakResponse.moveType);
    let $enemyBreakVideo = $('.enemy-video-container .' + breakType.className);
    let $enemyIdleDefaultVideo = $('.enemy-video-container .' + MoveType.IDLE_DEFAULT.className);

    // 적 비디오 컨테이너에 스탠바이 상태 해제
    $('.enemy-video-container').data('standby-move-class', MoveType.NONE.className);

    // 전조 컨테이너 deactivate
    $(".omen-container").removeClass()
        .find('.omen-text').removeClass()
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