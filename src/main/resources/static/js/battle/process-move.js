async function processResponseMoves(responses) {
    // 파싱
    let moveResponses = parseMoveResponseList(responses);
    console.log('===[processResponseMoves]============== \n moveResponses = \n', moveResponses);

    // 결과 리스트에 폼체인지가 있을경우 비디오 미리로드
    if (moveResponses.some(response => response.moveType === MoveType.FORM_CHANGE.name)) {
        waitingProcess(true);
        await preloadNextEnemyVideo();
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
                await processFormChange(response);
                break;
            case MoveType.GUARD:
                break; // 가드시 아무것도 안함
            case MoveType.FATAL_CHAIN:
                await processFatalChain(response);
                break;
            case MoveType.SUMMON:
                await processSummon(response);
                break;
            case MoveType.TURN_END:
                await processTurnEndProcess(response);
                break;
            case MoveType.ROOT:
            default:
                console.log('[processResponseMoves] invalid type]', response.moveType);
        }
        syncHpsAndChargeGauges(response.hps, response.hpRates, response.chargeGauges, response.fatalChainGauge);
    }

    // 어빌리티 커맨드시 후처리
    let firstMoveResponse = moveResponses[0];
    let firstMoveCharOrder = firstMoveResponse.charOrder;
    if (firstMoveResponse.moveType.getParentType() === MoveType.ABILITY) {
        // 어빌리티 후처리 (서폿어빌 X) -> 어빌리티 레일 에서 삭제 및 오버레이
        $('.ability-rail-wrapper .rail-ability').eq(0).remove();
        let $processedAbility = $('.ability-panel.actor-' + firstMoveCharOrder + ' [data-ability-id=' + +']');
        $processedAbility.find('.ability-overlay').show();
    }
}

function processSummon(response) {
    // 준비
    let $effectVideo = $('.summon-video-container .summon-video[data-summon-id=' + response.summonId + ']')
    let effectDuration = $effectVideo.get(0).duration * 1000;
    let effectHitDelay = effectDuration - 250;
    // 준비 - 적
    let $enemyVideo = getEnemyDamagedVideo();

// EFFECT 시작
// 오디오 재생
    let audioSrc = $('.summon-audio-container .summon-audio[data-summon-id=' + response.summonId + ']').attr('src');
    window.effectAudioPlayer.loadSound(audioSrc).then(() => {
        window.effectAudioPlayer.playAllSounds();
    });

    // 데미지 삽입 -> 어빌리티에 채움
    let currentAbilityDamageWrapperIndex = $('.ability-damage-wrapper').length;
    let $abilityDamageWrapper = $('<div>', {class: 'ability-damage-wrapper ability-index-' + currentAbilityDamageWrapperIndex});
    response.damages.forEach(function (damage, damageIndex) {
        let $damageElements = getDamageElement(response.charOrder, response.elementTypes[0], 'ability', damageIndex, damage, []);
        $abilityDamageWrapper.prepend($damageElements.$damage);
    })
    $('#abilityDamageContainer').append($abilityDamageWrapper);

    // 소환 이펙트 재생
    playVideo($effectVideo, null, null);

    // 피격 이펙트, 데미지 표시
    setTimeout(function () {
        // 적 피격 이펙트 재생
        playVideo($enemyVideo.effect, null, $enemyVideo.idle);
        // 화면 흔들기
        $('#videoContainer').addClass('push-left-down-effect');
        setTimeout(function () {
            $('#videoContainer').removeClass('push-left-down-effect');
        }, 150);
        // 데미지 표시
        let damageShowClass = response.damages.length > 2 ? 'multiple-damage-show' : 'damage-show';
        let $abilityDamages = $abilityDamageWrapper.find('.ability-damage')
        $abilityDamages.each(function (index, abilityDamage) {
            setTimeout(function () {
                $(abilityDamage).addClass(damageShowClass);
            }, index * 100)
            if (index >= $abilityDamages.length - 1) {
                // 마지막에 제거
                setTimeout(function () {
                    $abilityDamageWrapper.remove();
                }, 1000);
            }
        })
    }, effectHitDelay);

    // 스테이터스 아이콘 갱신
    processStatusIconSync(response.currentBattleStatusesList, effectDuration);
    // 힐 이펙트 처리
    let healEndTime = processHealEffect(response.heals, effectDuration + 500);
    // 버프 이펙트 처리
    let buffEndTime = processBuffEffect(response.addedBuffStatusesList, response.removedBuffStatusesList, response.removedDebuffStatusesList, healEndTime);
    // 디버프 이펙트 처리
    let debuffEndTime = processDebuffEffect(response.addedDebuffStatusesList, buffEndTime);

    let totalEndTime = Math.max(effectDuration, buffEndTime, debuffEndTime);
    console.log('[processSummon] totalTime', totalEndTime, 'effectDuration ', effectDuration, 'buffEndTime ', buffEndTime, 'debuffEndTime ', debuffEndTime);

    return new Promise(resolve => setTimeout(function () {
        console.log(response.moveType.name + ' done');
        resolve();
    }, totalEndTime + 300));
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
    let $nextIdleDefaultVideo = $enemyVideoContainerNext.find('.idle-default');

    $formChangeVideo.removeClass('hidden').get(0)?.play();
    $enemyAudioContainer.find('.form-change').get(0)?.play();
    $idleDefaultVideo.addClass('hidden');
    $formChangeVideo.one('ended', function () {
        // 폼체인지 끝나면 폼체인지 엔트리
        $formChangeEntryVideo.removeClass('hidden').get(0)?.play();
        $formChangeEntryAudio.get(0)?.play();
        $(this).addClass('hidden');
        $formChangeEntryVideo.one('ended', function () {
            // 폼체인지 엔트리 끝나면 idle-default
            $nextIdleDefaultVideo.removeClass('hidden').get(0)?.play();
            $(this).addClass('hidden');
            // 적 이전 폼 컨테이너 제거, 다음 폼 컨테이너의 next 제거
            $enemyVideoContainer.remove();
            $enemyAudioContainer.remove();
            $enemyVideoContainerNext.attr('class', 'enemy-video-container');
            $enemyAudioContainerNext.attr('class', 'enemy-audio-container');
        })
    })

    let totalEndTime = $formChangeVideo.get(0).duration * 1000 + $formChangeEntryVideo.get(0).duration * 1000 + 100;
    return new Promise(resolve => setTimeout(function () {
        console.log('FORM_CHANGE and FORM_CHANGE_ENTRY done', formChangeResponse.moveType);
        resolve();
    }, totalEndTime + 200));

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
            video.classList.add('hidden', 'enemy-video', 'actor-0');

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
        let baseClasses = 'audio actor-0';
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

function processEnemyStandBy(response) {
    let $defaultIdleVideo = $('.enemy-video-container .' + MoveType.IDLE_DEFAULT.className);
    if ($defaultIdleVideo.hasClass('hidden')) {
        // 적이 현재 스탠바이 상태일경우 전조 갱신후 아래의 내용은 무시
        $('.omen-container-top').find('.omen-text .omen-value').text(response.omenValue);
        return;
    }

    let $standbyVideo = $('.enemy-video-container .' + response.moveType.className);
    let $standbyIdleVideo = $('.enemy-video-container .' + response.moveType.getIdleType().className);

    // 적 비디오 컨테이너에 스탠바이 상태 추가
    $('.enemy-video-container').attr('data-standby-move-class', response.moveType.className);
    // 전조 컨테이너 activate
    $('.omen-container-top').addClass('activated')
        .find('.omen-text').addClass(response.omenType.className)
        .find('.omen-prefix').text(response.omenCancelCondInfo).end()
        .find('.omen-value').text(response.omenValue).end()
        .find('.omen-info').text(response.omenInfo);
    $('.omen-container-bottom.enemy').addClass('activated')
        .find('.omen-text').addClass(response.omenType.className)
        .find('.omen-prefix').text(response.omenName);

    // 오디오 재생
    let audioSrc = $('.enemy-audio-container').find('.' + response.moveType.className).attr('src');
    window.effectAudioPlayer.loadSound(audioSrc).then(() => {
        window.effectAudioPlayer.playAllSounds();
    })

    // 비디오 재생
    $standbyVideo.one('playing', function () {
        $defaultIdleVideo.addClass('hidden').get(0).pause();
        $defaultIdleVideo.get(0).currentTime = 0;
    }) // 스탠바이 이후 스탠바이에 맞는 idle 재생, idle_default 는 숨김
    playVideo($standbyVideo, null, $standbyIdleVideo);

    let totalEndTime = $standbyVideo.get(0).duration * 1000 + 100;
    return new Promise(resolve => setTimeout(function () {
        console.log('STANDBY done', response.moveType.name);
        resolve();
    }, totalEndTime + 200));

}

function processEnemyBreak(response) {
    let $standbyIdleVideo = $('.enemy-video-container video:not(.hidden)');
    let $effectVideo = $('.enemy-video-container .' + response.moveType.className);
    let $defaultIdleVideo = $('.enemy-video-container .' + MoveType.IDLE_DEFAULT.className);

    // 적 비디오 컨테이너에 스탠바이 상태 해제
    $('.enemy-video-container').attr('data-standby-move-class', MoveType.NONE.className);

    // 차지턴 갱신
    syncEnemyChargeTurn(response.chargeGauges);

    // 전조 컨테이너 deactivate
    $('.omen-container-top').removeClass('activated')
        .find('.omen-text').removeClass(response.omenType.className)
        .find('.omen-prefix').text('').end()
        .find('.omen-value').text('').end()
        .find('.omen-info').text('');
    $('.omen-container-bottom.enemy').removeClass('activated')
        .find('.omen-text').removeClass(response.omenType.className)
        .find('.omen-prefix').text('');

    // 오디오 재생
    let audioSrc = $('.enemy-audio-container').find('.' + response.moveType.className).attr('src');
    window.effectAudioPlayer.loadSound(audioSrc).then(() => window.effectAudioPlayer.playAllSounds());

    // 비디오 재생
    $effectVideo.one('playing', function () {
        $standbyIdleVideo.addClass('hidden').get(0).pause();
        $standbyIdleVideo.get(0).currentTime = 0;
    })
    playVideo($effectVideo, null, $defaultIdleVideo);

    // 화면 흔들기
    $('#videoContainer').addClass('shake-left-effect');
    setTimeout(function () {
        $('#videoContainer').removeClass('shake-left-effect');
    }, 200);

    let totalEndTime = $effectVideo.get(0).duration * 1000 + 100;
    return new Promise(resolve => setTimeout(function () {
        console.log('BREAK done', response.moveType);
        resolve();
    }, totalEndTime + 200));

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
        let $partyVideos = [-1, 1, 2, 3, 4]
            .map(number => getVideo(number, MoveType.DAMAGED_DEFAULT, MoveType.IDLE_DEFAULT));
        enemyDamagesPostProcess(response, null, $partyVideos);
        window.effectAudioPlayer.loadAndPlay(GlobalSrc.TURN_DAMAGE.audio);
        totalEndTime = 600;
    }

    return new Promise(resolve => setTimeout(function () {
        console.log('TURN_END_PROCESS done', response.moveType);
        resolve();
    }, totalEndTime));

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