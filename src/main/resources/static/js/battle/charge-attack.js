function processChargeAttack(responseChargeAttackData) {
    // 변수초기화
    let chargeAttackData = responseChargeAttackData;
    let charOrder = chargeAttackData.charOrder;
    let partySelector = '.party-' + charOrder
    let moveType = MoveType.byName(chargeAttackData.moveType);
    let chargeAttackHitCount = chargeAttackData.length; // 히트수 (피격모션, 데미지 표시관련)
    let damages = chargeAttackData.damages;
    let elementType = chargeAttackData.elementTypes[0];
    let chargeGauges = chargeAttackData.chargeGauges;
    let hps = chargeAttackData.hps;
    let hpRates = chargeAttackData.hpRates;

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
    $('.charge-attack-damage-wrapper').append($('<div>', {class: 'charge-attack-damage element-type-' + elementType.toLowerCase(), text: damages[0]}));

    // 아군 오의 이펙트 재생 (오디오 속도가 느리므로, 100ms 정도 딜레이 걸고 재생
    setTimeout(function () {
        $idleMotionVideo.addClass('hidden'); // idle 모션 숨김
        $chargeAttackVideo.removeClass('hidden').get(0).play();

        // 끝나고 모션 정상화
        $chargeAttackVideo.one('ended', function () {
            $(this).addClass('hidden');
            $idleMotionVideo.removeClass('hidden');
            // 버프 없으면 바로 오의게이지 갱신
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
        $enemyIdleVideo.addClass('hidden'); // idle 숨김 (인터벌 전에 숨기면 플리커)
        $enemyDamagedVideo.removeClass('hidden').one('ended', function () {
            $enemyIdleVideo.removeClass('hidden').get(0).play(); // 가끔 멈춰서 재생갱신
            $enemyDamagedVideo.addClass('hidden');
        }).get(0).play();

        // 데미지 표시
        $('.charge-attack-damage-wrapper .charge-attack-damage').fadeTo(10, 0.8).delay(600).fadeTo(400, 0);

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
            setTimeout(() => {
                // 마지막 버프효과까지 모두 끝난후
            }, longestBuffDelay)
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
        syncHpsAndChargeGauges(hps, hpRates, chargeGauges);
        console.log(moveType.name + ' done');
        resolve();
    }, totalEndTime + 500));
}


function processEnemyChargeAttack(responseChargeAttackData) {
    // 변수초기화
    let chargeAttackData = responseChargeAttackData;
    let charOrder = chargeAttackData.charOrder;
    let partySelector = '.party-' + charOrder
    let moveType = MoveType.byName(chargeAttackData.moveType);
    let chargeAttackHitCount = chargeAttackData.hitCount; // 히트수 (피격모션, 데미지 표시관련)
    let damages = chargeAttackData.damages;
    let elementTypes = chargeAttackData.elementTypes;
    let chargeGauges = chargeAttackData.chargeGauges;
    let hps = chargeAttackData.hps;
    let hpRates = chargeAttackData.hpRates;
    // 적 한정
    let targetOrders = chargeAttackData.enemyAttackTargetOrders;
    let isAllTarget = chargeAttackData.allTarget; // 전체공격여부
    let isAllTargetSubstituted = isAllTarget && targetOrders.every(target => target === targetOrders[0]) // 전체공격, 모든타겟 동일한경우


    // 발생한 스테이터스 효과, [[적][아군][아군][아군][아군]]
    let addedBattleStatusesList = chargeAttackData.addedBattleStatusList;
    let addedBuffStatusesList = addedBattleStatusesList.map(addedBattleStatuses => addedBattleStatuses.filter(status => status.type === 'BUFF'));
    addedBuffStatusesList
    let addedDebuffStatusesList = addedBattleStatusesList.map(addedBattleStatuses => addedBattleStatuses.filter(status => status.type === 'DEBUFF'));
    // 갱신된 전체 스테이터스 효과, [[적][아군][아군][아군][아군]]
    let currentBattleStatusesList = chargeAttackData.battleStatusList;
    // 아군 또는 적 에게 버프와 디버프 있는지 확인
    let hasBuff = addedBuffStatusesList.some(arr => arr.length > 0);
    let hasDebuff = addedDebuffStatusesList.some(arr => arr.length > 0);


    // 준비
    let $chargeAttackVideo = $('.enemy-video-container .' + moveType.className);
    let $idleDefaultVideo = $('.enemy-video-container .' + MoveType.IDLE_DEFAULT.className); // 차지어택 종료후 idle_default
    let $standByIdleVideo = $('.enemy-video-container video:not(.hidden)');
    console.log('ready', $chargeAttackVideo, $idleDefaultVideo)

// EFFECT 이펙트 시작

    // 오디오 재생
    let enemyAudioPlayer = new AudioPlayer();
    let audioInfos = $('.enemy-audio-container .' + moveType.className).toArray().map(element => ({
        url: $(element).attr('src'),
        delay: $(element).data('delay') || 0
    }));
    if (audioInfos.length > 0)
        enemyAudioPlayer.loadSounds(audioInfos).then(() => {
            enemyAudioPlayer.playAllSounds();
        });

    // TODO 아군 피격 보이스 재생

    // 적 오의 이펙트 재생 (오디오 속도가 느리므로, 100ms 정도 딜레이 걸고 재생
    setTimeout(function () {
        $standByIdleVideo.addClass('hidden').get(0).pause(); // standby idle 모션 숨김
        $chargeAttackVideo.removeClass('hidden').one('ended', function () {
            $(this).addClass('hidden');
            $idleDefaultVideo.removeClass('hidden').get(0).play(); // default idle 로 변경
            // 데미지 엘리먼트 제거
            setTimeout(function () {
                $('.enemy-damage-wrapper').children().remove();
            }, 1000);
        }).get(0).play();
    }, 100);

    // 적 오의 길이 및 히트당 길이
    let chargeAttackDuration = $chargeAttackVideo.get(0).duration * 1000 - 100;
    let chargeAttackHitDuration = chargeAttackDuration / chargeAttackHitCount; // 히트당 길이는 데미지 표시용 1000ms 제외

    //  데미지 마다 반복
    console.log(targetOrders, isAllTarget)
    damages.every(function (damage, damageIndex) {
        let effectDelay = isAllTarget ?
            chargeAttackHitDuration : // 오의가 전체공격인 경우 이펙트 종료후 1회만 데미지 발생
            chargeAttackHitDuration * damageIndex; // 단일 타겟의 경우 공격
        let targetOrder = isAllTarget ? targetOrders[damageIndex % targetOrders.length] : targetOrders[damageIndex]; // 데미지가 발생한 타겟순서, 전체타겟의 경우 1,2,3,4 만옴
        console.log('targetorder', targetOrder, 'effectdelay', effectDelay);

        if (isAllTargetSubstituted && damageIndex % targetOrders.length !== 0) {
            // 전체공격 이면서 감싸기 && 데미지가 해당 타수의 첫번째가 아닌경우 ex) targetOrders = [1, 1, 1, 1, 1, 1, 1, 1] 전체공격, 타겟 4명, 타수2회, 조건 진입은 index 가 1, 2, 3, 5, 6, 7
            let $attackDamage = $('.enemy-damage-wrapper .enemy-attack-damage.actor-' + targetOrder);
            $attackDamage.text(Number.parseInt($attackDamage.text()) + damage);
            let $additionalDamages = $attackDamage.find('.additional-damage:first');
            $additionalDamages.text(Number.parseInt($additionalDamages));
            return true; // 이후 처리 무시 (모션)
        }

        // 데미지 채우기 및 표시
        let $attackDamage = $('<div>', {
            class: 'damage enemy-attack-damage actor-' + targetOrder + ' element-type-' + elementTypes[damageIndex].toLowerCase(),
            text: damage
        });
        $attackDamage.delay(effectDelay + 100 * damageIndex) // 2번부터 앞캐릭보다 100ms 딜레이 추가
            .fadeTo(10, 0.8).delay(400).fadeTo(400, 0).appendTo($('.enemy-damage-wrapper'));

        // 아군의 피격 이펙트 재생
        let $targetIdleVideo = $('.party-video-container .party-' + targetOrder + ' .' + MoveType.IDLE.className);
        let $targetDamagedVideo = $('.party-video-container .party-' + targetOrder + ' .' + MoveType.DAMAGED.className);
        setTimeout(function () {
            $targetDamagedVideo.removeClass('hidden').get(0).play(); // 재생할때 순서별로 약간씩 딜레이 100 추가
            $targetIdleVideo.addClass('hidden'); // idle 보일경우 숨김
            // 아군 피격 모션을 idle 로 되돌림
            setTimeout(function () {
                $targetIdleVideo.removeClass('hidden');
                $targetDamagedVideo.addClass('hidden');
            }, isAllTarget ? 500 : chargeAttackHitDuration - 100); // 이건 이미 이펙트별 딜레이가 적용되어잇으로 공격 횟수별로 걸어주면 됨, 전체공격이면 500ms 만
        }, effectDelay + 100 * damageIndex);

        return true;
    });

    // 적 비디오 컨테이너에 스탠바이 상태 해제
    $('.enemy-video-container').data('standby-move-class', MoveType.NONE.className);

    // 이펙트 직후 실행 타이머
    setTimeout(function () {
        // 스테이터스 아이콘 갱신
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
        // 전조 컨테이너 deactivate
        $(".omen-container").removeClass('activated')
            .find('.omen-text').removeClass().addClass('omen-text') // 해당 컨디션 찾기 귀찮아서 그냥 밀어버리고 omen-text 추가
            .find('.omen-prefix').text('')
            .find('.omen-value').text('');
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
                // 하나하나 페이드
                $statusEffect.fadeTo(100, 0.9).delay(600).fadeTo(400, 0);
            }, debuffStartDelay + additionalStartDelay + (buffIndex * 50))
            setTimeout(() => {
                // 3개단위 종료시 삭제
                $statusEffect.remove();
            }, debuffStartDelay + additionalStartDelay + removeDelay);
            setTimeout(() => {
                // 마지막 디버프 이펙트 종료후
            }, longestDebuffDelay)
        });
    });
    let debuffEndTime = debuffStartDelay + longestDebuffDelay;

    console.log('enemy chargeAttackDuration ', chargeAttackDuration, 'buffEndTime ', buffEndTime, 'debuffEndTiem ', debuffEndTime)
    let totalEndTime = Math.max(chargeAttackDuration + 100, buffEndTime, debuffEndTime);
    console.log("total = " + totalEndTime)

    return new Promise(resolve => setTimeout(function () {
        syncHpsAndChargeGauges(hps, hpRates, chargeGauges);
        console.log(moveType.name + ' done');
        resolve();
    }, totalEndTime + 500)); // 오의는 처리가 좀 늦음
}