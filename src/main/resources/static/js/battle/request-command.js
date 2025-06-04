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
    // 결과 리스트에 폼체인지가 있을경우 비디오 미리로드
    if (responseResults.some(response => response.moveType === MoveType.FORM_CHANGE.name)) {
        waitingProcess(true);
        await preloadNextEnemyVideo();
        waitingProcess(false);
    }

    for (const response of responseResults) {
        let moveType = MoveType.byName(response.moveType);
        let charOrder = response.charOrder; // mainActorOrder, 0: enemy, 1~ character
        // console.log('charOrder = ' + charOrder + ' moveType = ' + moveType.name);
        let parentMoveType = moveType.getParentType();

        switch (parentMoveType) {
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
            case MoveType.ROOT:
                if (moveType === MoveType.FORM_CHANGE) {
                    await processFormChange(response);
                } else {
                    console.log('[processResponseMoves] invalid type]', moveType)
                }
                break;
            default:
                console.log('[processResponseMoves] invalid type]', moveType)
        }
    }

    // 후처리
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

async function preloadNextEnemyVideo() {
    let memberId = $('#memberInfo').data('member-id');
    // 서버와 통신하여 적의 다음 폼 비디오, 오디오 맵을 가져옴
    let videoMap = null;
    let audioMap = null;
    $.ajax({
        url: '/api/enemy-src?memberId=' + memberId,
        type: 'GET',
        async: false,
        success: function (response) {
            videoMap = response.video;
            audioMap = response.audio;
            console.log('video', videoMap);
            console.log('audio', audioMap);
        },
        error: function (response) {
            console.log(response);
        }
    });

    let $videoContainer = $('#videoContainer');
    let $enemyNextVideoContainer = $('<div>', {
        class: 'enemy-video-container-next',
        'data-phase': '2',
        'data-standby-move-class': 'none'
    })
    $videoContainer.append($enemyNextVideoContainer);

    const videoPromises = Object.entries(videoMap).map(([src, classList]) => {
        return new Promise((resolve, reject) => {
            const video = document.createElement('video');
            video.preload = 'auto';
            video.muted = true;
            video.playsinline = true;
            classList.forEach(cls => video.classList.add(cls)); // 클래스추가
            video.dataset.moveType = classList.join(' ');
            video.loop = classList.some(className => className === 'idle');
            video.classList.add('hidden', 'enemy-video')

            // src가 비어있지 않으면 할당, 비엇으면 즉시 리졸브 (로드 이벤트 발생x)
            if (!src) return resolve(video);
            video.src = src;

            // 로드 완료 이벤트
            video.addEventListener('canplaythrough', () => resolve(video), {once: true});
            video.addEventListener('error', () => reject(new Error(`Failed to load: ${src}`)), {once: true});

            $enemyNextVideoContainer.append($(video));
            video.load(); // 강제 로드 트리거
        });
    });

    let $audioContainer = $('#audioContainer');
    let $enemyNextAudioContainer = $('<div>', {
        class: 'enemy-audio-container-next'
    })
    $audioContainer.append($enemyNextAudioContainer);

    $.each(audioMap, function (src, classList) {
        // classList가 배열이 아니라면 배열로 변환
        // if (!Array.isArray(classList)) classList = [classList];

        // 기본 클래스
        let baseClasses = 'audio';
        // 추가 클래스
        let extraClasses = classList.join(' ');

        // audio 태그 생성
        let $audio = $('<audio>', {
            'class': baseClasses + (extraClasses ? ' ' + extraClasses : ''),
            'src': src
        });

        $enemyNextAudioContainer.append($audio);
    });

    return await Promise.all(videoPromises); // 비디오 로드 다되면 리턴
}

function processFormChange(formChangeResponse) {
    let $enemyVideoContainer = $('.enemy-video-container');
    let $enemyVideoContainerNext = $('.enemy-video-container-next');
    let $enemyAudioContainer = $('.enemy-audio-container');
    let $enemyAudioContainerNext = $('.enemy-audio-container-next');
    let $formChangeVideo = $enemyVideoContainer.find('.form-change');
    let $idleDefaultVideo = $enemyVideoContainer.find('.idle-default');
    let $formChangeEntryVideo = $enemyVideoContainerNext.find('.form-change-entry');
    let $formChangeEntryAudio = $enemyAudioContainerNext.find('.form-change-entry');

    $formChangeVideo.one('ended', function () {
        // 폼체인지 끝나면 폼체인지 엔트리
        $(this).addClass('hidden');
        $formChangeEntryVideo.one('ended', function () {
            // 폼체인지 엔트리 끝나면 idle-default
            $enemyVideoContainerNext.find('.idle-default').removeClass('hidden').get(0).play();
            $(this).addClass('hidden');
            // 적 이전 폼 컨테이너 전체 제거
            $enemyVideoContainer.remove();
            $enemyAudioContainer.remove();
            // 다음 폼 컨테이너 next 제거
            $enemyVideoContainerNext.attr('class', 'enemy-video-container');
            $enemyAudioContainerNext.attr('class', 'enemy-audio-container');
        })
        $formChangeEntryVideo.removeClass('hidden').get(0)?.play();
        $formChangeEntryAudio.removeClass('hidden').get(0)?.play();
    })
    $idleDefaultVideo.addClass('hidden');
    $formChangeVideo.removeClass('hidden').get(0)?.play();
    $enemyAudioContainer.find('.form-change').get(0)?.play();

    let totalEndTime = $formChangeVideo.get(0).duration * 1000 + $formChangeEntryVideo.get(0).duration * 1000 + 100;
    return new Promise(resolve => setTimeout(function () {
        console.log('FORM_CHANGE and FORM_CHANGE_ENTRY done', formChangeResponse.moveType);
        resolve();
    }, totalEndTime));

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
    $('.enemy-video-container').attr('data-standby-move-class', moveType.className);

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
    $('.enemy-video-container').attr('data-standby-move-class', MoveType.NONE.className);

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

    // 화면 흔들기
    $('#videoContainer').addClass('shake-left-effect');
    setTimeout(function () {
        $('#videoContainer').removeClass('shake-left-effect');
    }, 200);

    let totalEndTime = $enemyBreakVideo.get(0).duration * 1000 + 100;
    return new Promise(resolve => setTimeout(function () {
        console.log('BREAK done', breakType);
        resolve();
    }, totalEndTime));

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