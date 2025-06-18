/**
 * 커맨드 패널의 현재 스테이터스 아이콘 갱신 (이펙트 종료 직후)
 * @param currentBattleStatusesList 갱신할 현재 스테이터스리스트
 * @param effectVideoDuration 이펙트 길이 (시작딜레이로 사용)
 */
function processStatusIconSync(currentBattleStatusesList, effectVideoDuration) {
    // 스테이터스 아이콘 갱신 (어빌리티 이펙트 직후 즉시 갱신)
    setTimeout(function () {
        currentBattleStatusesList.forEach(function (currentBattleStatuses, actorIndex) {
            let $statusContainer = $('.status-container.actor-' + actorIndex);
            $statusContainer.find('.status').remove(); // 스테이터스 비움
            currentBattleStatuses.forEach(function (status, index) {
                // 어빌리티 패널에 갱신된 스테이터스 추가
                let $statusInfo = $('<div>', {class: 'status status', 'data-status-type': status.type})
                    .append(
                        $('<img>', {
                            src: status.imageSrc,
                            class: 'status-icon' + (status.imageSrc.length < 1 ? ' none-icon' : '')
                        }),
                        $('<div>', {class: 'status-name d-none', text: status.name}),
                        $('<div>', {class: 'status-info-text d-none', text: status.statusText}),
                        $('<div>', {class: 'status-duration d-none', text: status.duration})
                    );
                $statusContainer.append($statusInfo);
            })
        });
    }, effectVideoDuration);
}

/**
 * 버프 이펙트를 처리
 * @param addedBuffStatusesList 추가된 버프 스테이터스 리스트 (빈 배열 가능)
 * @param removedBuffStatusesList
 * @param removedDebuffStatusesList
 * @param effectVideoDuration 이펙트 비디오 길이 (시작 전 딜레이)
 * @returns longestBuffEndTime 가장 긴 버프 끝시간 (다음 시작 딜레이로 사용)
 */
function processBuffEffect(addedBuffStatusesList, removedBuffStatusesList, removedDebuffStatusesList, effectVideoDuration) {
    let longestBuffEndTime = effectVideoDuration; // 버프 없을땐 이전 딜레이 (이펙트 딜레이) 만큼
    let statusesList = addedBuffStatusesList.map((_, idx) => {
            const removedDebuffs = (removedDebuffStatusesList[idx] || []).map(x => ({value: x, type: 'removedDebuffs'}));
            const addedBuffes = (addedBuffStatusesList[idx] || []).map(x => ({value: x, type: 'addedBuffs'}));
            const removedBuffes = (removedBuffStatusesList[idx] || []).map(x => ({value: x, type: 'removedBuffs'}));
            return [...removedDebuffs, ...addedBuffes, ...removedBuffes];
        }
    );
    console.log('[processBuffEffect] statusesList = {}', statusesList);
    // 표시 순서 removedDebuff -> addedBuff -> removedBuff
    statusesList.forEach(function (statuses, actorIndex) { // [[적][아군][아군][아군][아군]]
        let statusRemovedEffectCount = 0;
        statuses.forEach(function (statusObject, statusIndex) {
            let status = statusObject.value;
            let $effectContainer = $('.status-effect-container.actor-' + actorIndex);
            let $statusEffect = $('<div>', {class: 'status-effect status-effect-' + statusIndex})
                .append(
                    $('<img>', {src: status.imageSrc, class: status.imageSrc.length < 1 ? 'none-icon' : ''}),
                    $('<span>', {class: 'status-effect-text', text: status.effectText})
                );
            $effectContainer.prepend($statusEffect);

            let type = statusObject.type;
            let audioPlayer = null;
            let $statusRemovedEffect = null;
            if (type === 'removedDebuffs' || type === 'removedBuffs') {
                audioPlayer = new AudioPlayer();
                audioPlayer.loadSound($('.global-audio-container .status-removed').attr('src')); // 일단 실행까지 여유가 있으니 놔둠

                let statusEffectPosition = $statusEffect.position();
                $statusRemovedEffect = $('<div>', {class: 'status-effect status-removed-effect'}).css({
                    top: statusEffectPosition.top - 5,
                    left: statusEffectPosition.left - 10,
                    width: $statusEffect.find('.status-effect-text').width() + 30, // img 크기까지
                    height: $statusEffect.find('.status-effect-text').height()
                });
                $effectContainer.prepend($statusRemovedEffect);
                statusRemovedEffectCount++;
            }

            let statusForPage = actorIndex === 0 ? 7 : 4; // 한 페이지에 표시할 스테이터스 이펙트 갯수 적은 한번에 7개, 아군은 4개까지 표시
            let statusPageCount = Math.floor(statusIndex / statusForPage); // 현재 표시할 스테이터스의 페이지 (0 부터)
            let startDelay = effectVideoDuration + (1100 * statusPageCount) + (50 * (statusIndex % statusForPage)); // 페이드 길이 1100,
            longestBuffEndTime = Math.max(longestBuffEndTime, startDelay + 1100); // 마지막 버프 이펙트 끝나는 시간

            setTimeout(() => {
                if (audioPlayer != null && $statusRemovedEffect != null) {
                    audioPlayer.playAllSounds();
                    $statusEffect.addClass('status-removed');
                    $statusRemovedEffect.addClass('active');
                } else {
                    $statusEffect.fadeTo(100, 1).delay(800).fadeTo(200, 0);
                }

                if (statusIndex >= statuses.length - 1 || statusIndex % statusForPage === statusForPage - 1) {
                    // 페이지의 마지막마다 제거
                    setTimeout(() => $effectContainer.find('.status-effect').slice(0, statusForPage + statusRemovedEffectCount).remove(), 1100);
                    statusRemovedEffectCount = 0;
                }
            }, startDelay);
        });
    });
    return longestBuffEndTime;
}

/**
 * 디버프 이펙트 처리
 * @param addedDebuffStatusesList
 * @param debuffStartDelay 시작딜레이 (이펙트딜레이 + 버프딜레이)
 * @returns longestDebuffEndTime 가장 긴 디버프 딜레이 (다음 시작 딜레이로 사용)
 */
function processDebuffEffect(addedDebuffStatusesList, debuffStartDelay) {
    let longestDebuffEndTime = debuffStartDelay; // 디버프 없을댄 이전 딜레이 (이펙트 + 버프 딜레이) 만큼
    addedDebuffStatusesList.forEach(function (addedDebuffStatuses, actorIndex) { // [1번째 캐릭] [2번째 캐릭]...
        addedDebuffStatuses.forEach(function (debuffStatus, debuffIndex) {
            let $effectContainer = $('.status-effect-container.actor-' + actorIndex);
            let $statusEffect = $('<div>', {class: 'status-effect status-effect-' + debuffIndex})
                .append(
                    $('<img>', {
                        src: debuffStatus.imageSrc,
                        class: debuffStatus.imageSrc.length < 1 ? 'none-icon' : ''
                    }),
                    $('<span>', {class: 'status-effect-text', text: debuffStatus.effectText})
                );
            $effectContainer.prepend($statusEffect);

            let debuffForPage = actorIndex === 0 ? 7 : 4; // 한 페이지에 표시할 디버프 이펙트 갯수 적은 한번에 7개, 아군은 4개까지 표시
            let debuffPageCount = Math.floor(debuffIndex / debuffForPage); // 현재 표시할 디버프의 페이지 (0 부터)
            let startDelay = debuffStartDelay + (1100 * debuffPageCount) + (50 * (debuffIndex % debuffForPage)); // 페이드 길이 1100
            longestDebuffEndTime = Math.max(longestDebuffEndTime, startDelay + 1100); // 마지막 디버프 이펙트 끝나는 시간

            setTimeout(() => {
                $statusEffect.fadeTo(100, 1).delay(800).fadeTo(200, 0, function () {
                    $effectContainer.find('.status-effect').slice(0, debuffForPage).remove();
                });
            }, startDelay);
        });
    });
    return longestDebuffEndTime;
}


/**
 * 비디오를 재생하는 메서드
 * effectVideo, motionVideo 의 hidden 클래스 제거, 종료시 hidden 클래스 다시 붙이는 이벤트 리스너 등록, 재생 을 수행
 * idleVideo 가 주어질경우, idleVideo 를 먼저 hidden 추가 후 effectVideo 종료시 idleVideo 에 hidden 제거후 play
 *
 * 적의 경우 IDLE 을 유지한 상태로 이펙트만 별도로 재생해야하는 (POWER UP, CT MAX 및 기타 서포트 어빌리티) 경우가 있어 isEnemyEffectOnly  사용
 * CHECK 여기서 한번 더 분기필요하면 아얘 분리
 * @param $effectVideo
 * @param $motionVideo optional
 * @param $idleVideo optional
 * @param isEnemyEffectOnly boolean, 적에 한해 이펙트만 존재하는지 여부
 */
function playVideo($effectVideo, $motionVideo, $idleVideo, isEnemyEffectOnly = false) {
    console.log('[playVideo] $effectVideo = ', $effectVideo, ' $motionVideo = ', $motionVideo, ' $idleVideo = ', $idleVideo, ' isEnemyEffectOnly = ', isEnemyEffectOnly)
    if ($effectVideo == null) {
        console.error('effectVideo is null');
    }
    if ($effectVideo?.length === 0 || $motionVideo?.length === 0 || $idleVideo?.length === 0) {
        console.error('video length is 0'); // 비디오는 있거나, null 이거나 둘중하나
    }

    let idleVideoElement = $idleVideo?.get(0);
    let effectVideoElement = $effectVideo?.get(0);
    let motionVideoElement = $motionVideo?.get(0);

    if (motionVideoElement) {
        motionVideoElement.play();
        $motionVideo.one('ended', function () {
            // 모션이 있으면 idle 반드시 존재
            idleVideoElement.play();
            $idleVideo.removeClass('hidden');
            $motionVideo.addClass('hidden');
        })
    }

    effectVideoElement.play();
    $effectVideo.one('ended', function () {
        if (idleVideoElement) {
            idleVideoElement.play();
        }
        $idleVideo?.removeClass('hidden');
        $effectVideo.addClass('hidden');

    })

    $motionVideo?.removeClass('hidden');
    $effectVideo?.removeClass('hidden');
    $idleVideo?.addClass('hidden');

    if (idleVideoElement && !isEnemyEffectOnly) {
        idleVideoElement.pause();
        idleVideoElement.currentTime = 0;
    }

}

/*
// on ended 기반
function playVideo($effectVideo, $motionVideo, $idleVideo, isEnemyEffectOnly = false) {
    let idleVideoExists = $idleVideo && $idleVideo.length > 0;
    let motionVideoExists = $motionVideo && $motionVideo.length > 0;
    let effectVideoExists = $effectVideo && $effectVideo.length > 0;
    console.log('[playVideo] $effectVideo = ', $effectVideo, ' $motionVideo = ', $motionVideo, ' $idleVideo = ', $idleVideo, ' isEnemyEffectOnly = ', isEnemyEffectOnly)
    if (!effectVideoExists) return; // 비정상
    if (motionVideoExists) {
        $motionVideo.one('ended', function () {
            $(this).addClass('hidden');
            if (idleVideoExists) $idleVideo.removeClass('hidden').get(0).play(); // 모션이 이펙트보다 보통 짧아서 idle 갱신 우선순위 높음
        }).removeClass('hidden').get(0).play();
    }
    $effectVideo.one('ended', function () {
        if (idleVideoExists && !isEnemyEffectOnly) $idleVideo.removeClass('hidden').get(0).play(); // 멈추는 경우가 있어서 play 로 갱신
        $(this).addClass('hidden'); // 이펙트는 loop 없기 때문에 stop 안함, 깜빡임 방지 딜레이
    }).removeClass('hidden').get(0).play();
    if (idleVideoExists && !isEnemyEffectOnly) {
        let idleVideoElement = $idleVideo.addClass('hidden').get(0);
        idleVideoElement.pause();
        idleVideoElement.currentTime = 0;
    }
}
*/

function flashVideo() {
    let $idleVideo = $('.enemy-video-container .enemy-video.idle-default').eq(0);
    let $effectVideo = $('.enemy-video.damaged-default').eq(0);
    setInterval(function () {
        if ($effectVideo.hasClass('hidden')) {
            $effectVideo.removeClass('hidden');
            $effectVideo.get(0).play();
            $idleVideo.addClass('hidden');
            $idleVideo.get(0).pause();
            $idleVideo.get(0).currentTime = 0;
        } else {
            $effectVideo.addClass('hidden');
            $effectVideo.get(0).pause();
            $effectVideo.get(0).currentTime = 0;
            $idleVideo.removeClass('hidden');
            $idleVideo.get(0).play();

        }
    }, 50)

}
