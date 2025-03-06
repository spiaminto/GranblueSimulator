function processChargeAttack(responseChargeAttackData) {
    // 변수초기화

    let chargeAttackData = responseChargeAttackData;
    let charOrder = chargeAttackData.charOrder;
    let partySelector = '.party-' + charOrder
    let moveType = MoveType.byName(chargeAttackData.moveType);
    let chargeAttackHitCount = chargeAttackData.length; // 히트수 (피격모션, 데미지 표시관련)
    let damages = chargeAttackData.damages;

    // 발생한 스테이터스 효과, [[적][아군][아군][아군][아군]]
    let addedBattleStatusesList = chargeAttackData.addedBattleStatusList;
    let addedBuffStatusesList = addedBattleStatusesList.map(addedBattleStatuses => addedBattleStatuses.filter(status => status.type === 'BUFF'));
    addedBuffStatusesList
    let addedDebuffStatusesList = addedBattleStatusesList.map(addedBattleStatuses => addedBattleStatuses.filter(status => status.type === 'DEBUFF'));
    // 갱신된 전체 스테이터스 효과, [[적][아군][아군][아군][아군]]
    let currentBattleStatusesList = chargeAttackData.battleStatusList;
    // 아군(시전자) 또는 적 에게 버프와 디버프 있는지 확인
    let hasBuff = addedBuffStatusesList.some(arr => arr.length > 0);
    let hasDebuff = addedDebuffStatusesList.some(arr => arr.length > 0);


    // 준비
    let $chargeAttackVideo = $('.party-video-container ' + partySelector + ' .' + moveType.className);
    let $idleMotionVideo = $('.party-video-container ' + partySelector + ' .' + MoveType.IDLE.className);
    console.log($chargeAttackVideo, $idleMotionVideo)

// EFFECT 이펙트 시작

    // 오디오 재생
    let charAudioPlayer = new AudioPlayer();
    let audioInfos = $('.party-audio-container ' + partySelector + ' .' + moveType.className).toArray().map(element => ({
        url: $(element).attr('src'),
        delay: $(element).data('delay') || 0
    }));
    if (audioInfos.length > 0)
        charAudioPlayer.loadSounds(audioInfos).then(() => {
            charAudioPlayer.playAllSounds();
        });

    // 데미지 채우기
    $('.charge-attack-damage-wrapper').append($('<div>', {class: 'charge-attack-damage', text: damages[0]}));

    // 아군 오의 이펙트 재생 (오디오 속도가 느리므로, 100ms 정도 딜레이 걸고 재생
    setTimeout(function () {
        $idleMotionVideo.addClass('hidden'); // idle 모션 숨김
        $chargeAttackVideo.removeClass('hidden').get(0).play();

        // 끝나고 모션 정상화
        $chargeAttackVideo.one('ended', function () {
            $(this).addClass('hidden');
            $idleMotionVideo.removeClass('hidden');
        });
    }, 100);

    // 아군 오의 이펙트 종료 후 데미지 표시를 위해 + 1000, 적 피격모션 재생 -> 나중에 시간제로 앞당겨서 할수잇음
    let chargeAttackDuration = $chargeAttackVideo.get(0).duration * 1000 - 100 + 1000;

    $chargeAttackVideo.one('ended', function () {
        // 적 idle 및 damaged 모션 클래스 찾기
        let standbyMoveClassName = $('.enemy-video-container').data('standby-move-class');
        let idleMoveClassName = standbyMoveClassName === 'none' ?
            MoveType.IDLE_DEFAULT.className : MoveType.byClassName(standbyMoveClassName).getIdleType().className;
        let damagedMoveClassName = standbyMoveClassName === 'none' ?
            MoveType.DAMAGED_DEFAULT.className : MoveType.byClassName(standbyMoveClassName).getDamagedType().className;
        // 클래스로 비디오 찾기
        let $enemyIdleVideo = $('.enemy-video-container .' + idleMoveClassName);
        let $enemyDamagedVideo = $('.enemy-video-container .' + damagedMoveClassName);

        // idle 숨기고 damaged 재생
        !$enemyIdleVideo.hasClass('hidden') && $enemyIdleVideo.addClass('hidden'); // idle 숨김 (인터벌 전에 숨기면 플리커)
        let enemyDamagedVideoElement = $enemyDamagedVideo.removeClass('hidden').get(0);
        enemyDamagedVideoElement.currentTime = 0; // 빼면 부자연스러워짐
        enemyDamagedVideoElement.play();

        // 데미지 표시
        $('.charge-attack-damage-wrapper .charge-attack-damage').fadeTo(10, 0.8).delay(600).fadeTo(400, 0);

        $enemyIdleVideo.removeClass('hidden').get(0).play(); // 가끔 멈춰서 재생갱신
        $enemyDamagedVideo.addClass('hidden');

        setTimeout(function () {
            $('.charge-attack-damage-wrapper').children().remove();
        }, 1000);
    });

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
    }, chargeAttackDuration);

    // BUFF 버프처리 시작
    // 버프 이펙트 요소 내용 채우고 페이드 걸기
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
                $statusEffect.fadeTo(100, 0.9).delay(600).fadeTo(400, 0);
            }, chargeAttackDuration + additionalStartDelay + (buffIndex * 50))
            setTimeout(() => {
                $statusEffect.remove();
            }, chargeAttackDuration + additionalStartDelay + removeDelay);
        });
    });
    let buffEndTime = chargeAttackDuration + longestBuffDelay;

// DEBUFF 디버프 처리 시작
    // 디버프 이펙트 요소 내용 채우고 페이드 걸기
    let debuffStartDelay = hasDebuff ? chargeAttackDuration + 1000 : chargeAttackDuration; // 버프 없으면 즉시시작
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
                $statusEffect.fadeTo(100, 0.9).delay(600).fadeTo(400, 0);
            }, debuffStartDelay + additionalStartDelay + (buffIndex * 50))
            setTimeout(() => {
                $statusEffect.remove();
            }, debuffStartDelay + additionalStartDelay + removeDelay);
        });
    });
    let debuffEndTime = debuffStartDelay + longestDebuffDelay;

    console.log('chargeAttackDuration ', chargeAttackDuration, 'buffEndTime ', buffEndTime, 'debuffEndTiem ', debuffEndTime)
    let totalEndTime = Math.max(chargeAttackDuration, buffEndTime, debuffEndTime);
    console.log("total = " + totalEndTime)

    return new Promise(resolve => setTimeout(function () {
        console.log(moveType.name + ' done');
        resolve();
    }, totalEndTime));
}