/**
 * 버프 이펙트를 처리
 * @param addedBuffStatusesList 추가된 버프 스테이터스 리스트 (빈 배열 가능)
 * @param effectVideoDuration 이펙트 비디오 길이 (시작 전 딜레이)
 * @returns longestBuffDelay 가장 긴 버프 딜레이 (다음 시작 딜레이로 사용)
 */
function processBuffEffect(addedBuffStatusesList, effectVideoDuration) {
    let longestBuffDelay = 0;
    addedBuffStatusesList.forEach(function (addedBuffStatuses, actorIndex) { // [[적][아군][아군][아군][아군]]
        addedBuffStatuses.forEach(function (buffStatus, buffIndex) {
            let $effectContainer = $('.status-effect-container.actor-' + actorIndex);
            let $statusEffect = $('<div>', {class: 'status-effect status-effect-' + buffIndex})
                .append(
                    $('<img>', {src: buffStatus.imageSrc, class: buffStatus.imageSrc.length < 1 ? 'none-icon' : ''}),
                    $('<span>', {class: 'status-effect-text', text: buffStatus.effectText})
                );
            $effectContainer.prepend($statusEffect);

            // 페이드 길이 1100 + index * 50 , 3번째 길이 1250
            let additionalStartDelay = buffIndex / 3 >= 1 ? 1250 + 100 : 0; // 3개 이상일경우 딜레이 추가 (3번째가 사라지는 시간 + 안전마진)
            let removeDelay = (1100 * (Math.floor(buffIndex / 3) + 1)) + (50 * buffIndex) // 페이드 딜레이 * 횟수 + 어빌리티 갯수만큼 딜레이
            longestBuffDelay = Math.max(longestBuffDelay, additionalStartDelay + removeDelay);

            setTimeout(() => {
                $statusEffect.fadeTo(100, 1).delay(600).fadeTo(400, 0);
            }, effectVideoDuration + additionalStartDelay + (buffIndex * 50))
            setTimeout(() => {
                $statusEffect.remove();
            }, effectVideoDuration + additionalStartDelay + removeDelay);
            setTimeout(() => {
                // 마지막 버프효과까지 모두 끝난후
            }, longestBuffDelay)
        });
    });
    return longestBuffDelay;
}

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
 * 디버프 이펙트 처리
 * @param addedDebuffStatusesList
 * @param debuffStartDelay 시작딜레이 (이펙트딜레이 + 버프딜레이)
 * @returns longestDebuffDelay 가장 긴 디버프 딜레이 (다음 시작 딜레이로 사용)
 */
function processDebuffEffect(addedDebuffStatusesList, debuffStartDelay) {
    let longestDebuffDelay = 0;
    addedDebuffStatusesList.forEach(function (addedDebuffStatuses, actorIndex) { // [1번째 캐릭] [2번째 캐릭]...
        addedDebuffStatuses.forEach(function (debuffStatus, buffIndex) {
            if (debuffStatus.type === 'DISPEL') { // 디스펠의 경우 별도처리
                return;
            }

            let $effectContainer = $('.status-effect-container.actor-' + actorIndex);
            let $statusEffect = $('<div>', {class: 'status-effect status-effect-' + buffIndex})
                .append(
                    $('<img>', {
                        src: debuffStatus.imageSrc,
                        class: debuffStatus.imageSrc.length < 1 ? 'none-icon' : ''
                    }),
                    $('<span>', {class: 'status-effect-text', text: debuffStatus.effectText})
                );
            $effectContainer.prepend($statusEffect);

            // 페이드 길이 1100 + index * 50 , 3번째 길이 1250
            let additionalStartDelay = buffIndex / 3 >= 1 ? 1250 + 100 : 0; // 3개 이상일경우 딜레이 추가 (3번째가 사라지는 시간 + 안전마진)
            let removeDelay = (1100 * (Math.floor(buffIndex / 3) + 1)) + (50 * buffIndex) // 페이드 딜레이 * 횟수 + 어빌리티 갯수만큼 딜레이
            longestDebuffDelay = Math.max(longestDebuffDelay, additionalStartDelay + removeDelay);

            setTimeout(() => {
                $statusEffect.fadeTo(100, 1).delay(600).fadeTo(400, 0);
            }, debuffStartDelay + additionalStartDelay + (buffIndex * 50))
            setTimeout(() => {
                $statusEffect.remove();
            }, debuffStartDelay + additionalStartDelay + removeDelay);
        });
    });
    return longestDebuffDelay;
}

/**
 * 비디오를 재생하는 메서드
 * effectVideo, motionVideo 의 hidden 클래스 제거, 종료시 hidden 클래스 다시 붙이는 이벤트 리스너 등록, 재생 을 수행
 * idleVideo 가 주어질경우, idleVideo 를 먼저 hidden 추가 후 effectVideo 종료시 idleVideo 에 hidden 제거후 play
 * @param $effectVideo
 * @param $motionVideo optional
 * @param $idleVideo optional
 */
function playVideo($effectVideo, $motionVideo, $idleVideo) {
    if ($idleVideo) {
        let idleVideoElement = $idleVideo.addClass('hidden').get(0);
        idleVideoElement.pause();
        idleVideoElement.currentTime = 0;
    }
    if ($motionVideo) {
        $motionVideo.one('ended', function () {
            $(this).addClass('hidden');
            if ($idleVideo) $idleVideo.removeClass('hidden').get(0).play(); // 모션이 이펙트보다 보통 짧아서 idle 갱신 우선순위 높음
        }).removeClass('hidden').get(0).play();
    }
    $effectVideo.one('ended', function () {
        $(this).addClass('hidden'); // 이펙트는 loop 없기 때문에 stop 안함
        if ($idleVideo && $idleVideo.hasClass('hidden')) $idleVideo.removeClass('hidden').get(0).play(); // 멈추는 경우가 있어서 play 로 갱신
    }).removeClass('hidden').get(0).play();
}