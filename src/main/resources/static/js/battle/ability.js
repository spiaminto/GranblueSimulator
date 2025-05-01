function processAbility(responseAbilityData) {
    let abilityData = responseAbilityData;
    let charOrder = responseAbilityData.charOrder;
    let moveType = MoveType.byName(responseAbilityData.moveType);
    let abilityOrder = moveType === MoveType.FIRST_ABILITY ? 1 : moveType === MoveType.SECOND_ABILITY ? 2 : MoveType.THIRD_ABILITY ? 3 : -1;
    let abilityHitCount = abilityData.hitCount; // 어빌리티 히트수 (피격모션, 데미지 표시관련)
    let abilityDamages = abilityData.damages;
    let elementType = abilityData.elementTypes[0];
    let chargeGauges = abilityData.chargeGauges;
    let hps = abilityData.hps;
    let hpRates = abilityData.hpRates;

    // 발생한 스테이터스 효과, [[적][아군][아군][아군][아군]]
    let addedBattleStatusesList = abilityData.addedBattleStatusList;
    let addedBuffStatusesList = addedBattleStatusesList.map(addedBattleStatuses => addedBattleStatuses.filter(status => status.type === 'BUFF'));
    let addedDebuffStatusesList = addedBattleStatusesList.map(addedBattleStatuses => addedBattleStatuses.filter(status => status.type === 'DEBUFF'));
    // 갱신된 전체 스테이터스 효과, [[적][아군][아군][아군][아군]]
    let currentBattleStatusesList = abilityData.battleStatusList;
    // 아군(시전자) 또는 적 에게 버프와 디버프 있는지 확인
    let hasBuff = addedBuffStatusesList[charOrder].length > 0 || addedBuffStatusesList[0].length > 0;
    let hasDebuff = addedDebuffStatusesList[charOrder].length > 0 || addedDebuffStatusesList[0].length > 0;

    // 준비
    let partySelector = '.party-' + charOrder;
    let abilityMotionVideo = $('.party-video-container ' + partySelector + ' .' + moveType.className + '.motion');
    let abilityEffectVideo = $('.party-video-container ' + partySelector + ' .' + moveType.className + '.effect');
    let idleMotionVideo = $('.party-video-container ' + partySelector + ' .' + MoveType.IDLE.className);

    let audioPlayers = new Map();
    audioPlayers.set('char', new AudioPlayer());
    audioPlayers.set('enemy', new AudioPlayer());
    audioPlayers.set('global', new AudioPlayer());
    let charAudioPlayer = audioPlayers.get('char');

// EFFECT 시작
// 오디오 재생
    let audioInfos = $('.party-audio-container ' + partySelector + ' .' + moveType.className).toArray().map(element => ({
        url: $(element).attr('src'),
        delay: $(element).data('delay') || 0
    }));
    if (audioInfos.length > 0)
        charAudioPlayer.loadSounds(audioInfos).then(() => {
            charAudioPlayer.playAllSounds();
        });


    // 데미지 채우기
    abilityDamages.forEach(function (item, index) {
        $('.ability-damage-wrapper').prepend($('<div>', {class: 'ability-damage ability-damage-' + index + ' element-type-' + elementType.toLowerCase(), text: item}));
    })

    // 어빌리티 영상 종료 이후 버프 / 버프 작업 위해 delay 계산용 어빌리티 재생길이 구함
    let abilityDuration = abilityEffectVideo.length > 0 ? abilityEffectVideo.get(0).duration * 1000 - 100 : 0; // 어빌리티 이펙트 재생 길이, seconds to milliseconds, 영상 딜레이 100ms 보정
    // 어빌리티의 히트에 따른 데미지 표시 및 모션 딜레이를 반복하기 위해 필요
    let abilityHitDuration = abilityDuration > 0 ? abilityDuration / abilityHitCount : 0;

// 데미지표시, 적 피격모션 재생 (히트수 만큼 반복)
    let abilityHitPlayCount = 0;
    if (abilityHitCount > 0) {
        // 적 idle 및 damaged 모션 클래스 찾기
        let standbyMoveClassName = $('.enemy-video-container').data('standby-move-class');
        let idleMoveClassName = standbyMoveClassName === 'none' ?
            MoveType.IDLE_DEFAULT.className : MoveType.byClassName(standbyMoveClassName).getIdleType().className;
        let damagedMoveClassName = standbyMoveClassName === 'none' ?
            MoveType.DAMAGED_DEFAULT.className : MoveType.byClassName(standbyMoveClassName).getDamagedType().className;
        // 클래스로 비디오 찾기
        let $enemyIdleVideo = $('.enemy-video-container .' + idleMoveClassName);
        let $enemyDamagedVideo = $('.enemy-video-container .' + damagedMoveClassName);

        // 히트수만큼 실행
        let abilityHitProcessInterval = setInterval(function () {
            // idle 숨기고 damaged 재생
            !$enemyIdleVideo.hasClass('hidden') && $enemyIdleVideo.addClass('hidden'); // idle 숨김 (인터벌 전에 숨기면 플리커)
            let enemyDamagedVideoElement = $enemyDamagedVideo.removeClass('hidden').get(0);
            enemyDamagedVideoElement.currentTime = 0; // 빼면 부자연스러워짐
            enemyDamagedVideoElement.play();

            // 데미지 표시
            $('.ability-damage-wrapper .ability-damage-' + abilityHitPlayCount).fadeTo(10, 0.8).delay(600).fadeTo(400, 0);

            if (++abilityHitPlayCount >= abilityHitCount) {
                // 어빌리티 히트수만큼 재생 완료했으면 모션 정상화, 어빌리티 데미지 전체 제거 후 인터벌 클리어
                $enemyIdleVideo.removeClass('hidden').get(0).play(); // 가끔 멈춰서 재생갱신
                $enemyDamagedVideo.addClass('hidden');
                setTimeout(function () {
                    $('.ability-damage-wrapper').children().remove();
                }, 1000);
                clearInterval(abilityHitProcessInterval);
            }
        }, abilityHitDuration);
    }

// 아군 어빌리티 이펙트 및 모션 재생 (오디오 속도가 느리므로, 100ms 정도 딜레이 걸고 재생
    setTimeout(function () {
        if (abilityMotionVideo.length < 1 || abilityEffectVideo.length < 1) return; // 서폿어빌은 모션 없으므로 바로리턴
        idleMotionVideo.addClass('hidden'); // idle 모션 숨김
        abilityMotionVideo.removeClass('hidden').get(0).play();
        abilityEffectVideo.removeClass('hidden').get(0).play();

        // 끝나고 모션 정상화
        $(abilityMotionVideo).one('ended', function () {
            $(this).addClass('hidden');
            idleMotionVideo.removeClass('hidden');
        });

        // 보통 이펙트가 더 길어서 이펙트 끝나면 다음시퀀스로
        $(abilityEffectVideo).one('ended', function () {
            $(this).addClass('hidden');
        });
    }, 100);

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
    }, abilityDuration);

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
                // 하나하나 페이드
                $statusEffect.fadeTo(100, 0.9).delay(600).fadeTo(400, 0);
            }, abilityDuration + additionalStartDelay + (buffIndex * 50))
            setTimeout(() => {
                // 버프 스테이터스 3개단위 종료시 한꺼번에 삭제
                $statusEffect.remove();
            }, abilityDuration + additionalStartDelay + removeDelay);
        });
    });
    let buffEndTime = abilityDuration + longestBuffDelay;

// DEBUFF 디버프 처리 시작
    // 디버프 이펙트 요소 내용 채우고 페이드 걸기
    let debuffStartDelay = hasDebuff ? abilityDuration + 1000 : abilityDuration; // 버프 없으면 즉시시작
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
    // 디버프 이펙트 처리 끝

    let totalEndTime = Math.max(abilityDuration + 100, buffEndTime, debuffEndTime);
    console.log('totalTime', totalEndTime, 'abilityDuration ', abilityDuration, 'buffEndTime ', buffEndTime, 'debuffEndTiem ', debuffEndTime);

    return new Promise(resolve => setTimeout(function () {
        syncHpsAndChargeGauges(hps, hpRates, chargeGauges);
        console.log(moveType.name + ' done');
        resolve();
    }, totalEndTime + 500));
}