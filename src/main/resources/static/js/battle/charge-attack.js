async function processChargeAttack(response) {
    // 준비 - 아군
    let $partyVideo = getVideo(response.charOrder, response.moveType);
    let effectDuration = $partyVideo.effect.get(0).duration * 1000 - 100 + 1000; // 아군 오의 이펙트 종료 후 데미지 표시를 위해 + 1000
    // 준비 - 적
    let $enemyVideo = getEnemyDamagedVideo();

// EFFECT 이펙트 시작
    // 오디오 재생
    let audioSrcs = $('#audioContainer .actor-' + response.charOrder + '.' + response.moveType.className).toArray().map(audio => $(audio).attr('src'));
    window.effectAudioPlayer.loadSounds(audioSrcs).then(() => window.effectAudioPlayer.playAllSounds());

    // 아군 오의 이펙트 재생
    playVideo($partyVideo.effect, $partyVideo.motion, $partyVideo.idle);

    // 데미지 채우기
    let $damageElements = getDamageElement(response.charOrder, response.elementTypes[0], 'chargeAttack', 0, response.damages[0], []);
    $('<div>', {class: 'charge-attack-damage-wrapper'}).append($damageElements.$damage).appendTo($('#chargeAttackDamageContainer'))

    // 이펙트 종료 직전부터 데미지, 피격이펙트 재생
    let effectHitDelay = effectDuration - 250; // 히트 시점을 끝나기 0.25초전으로
    setTimeout(function () {
        // 적 피격 이펙트 재생
        playVideo($enemyVideo.effect, null, $enemyVideo.idle);
        // 화면 흔들기
        $('#videoContainer').addClass('push-left-down-effect');
        setTimeout(function () {
            $('#videoContainer').removeClass('push-left-down-effect');
        }, 150);
        // 데미지 표시 및 제거
        $('.charge-attack-damage-wrapper .charge-attack-damage').addClass('damage-show');
        setTimeout(function () {
            $('.charge-attack-damage-wrapper').empty();
        }, 1000);
    }, effectHitDelay);

    // 스테이터스 아이콘 갱신
    processStatusIconSync(response.currentBattleStatusesList, effectDuration + 500);
    // 힐 이펙트 처리
    let healEndTime = processHealEffect(response.heals, effectDuration + 500)
    // 버프 이펙트 처리
    let buffEndTime = processBuffEffect(response.addedBuffStatusesList, response.removedBuffStatusesList, response.removedDebuffStatusesList, healEndTime);
    // 디버프 이펙트 처리
    let debuffEndTime = processDebuffEffect(response.addedDebuffStatusesList, buffEndTime);

    let totalEndTime = Math.max(effectDuration, buffEndTime, debuffEndTime);
    console.log('[processChargeAttack] chargeAttackDuration ', effectDuration, 'buffEndTime ', buffEndTime, 'debuffEndTiem ', debuffEndTime, 'totalEndTime = ', totalEndTime);

    return new Promise(resolve => setTimeout(function () {
        console.log(response.moveType.name + ' done');
        resolve();
    }, totalEndTime + 300));
}

async function processEnemyChargeAttackPreEffect() {
    let $chargeAttackPreEffectVideo = $('.global-video-container .enemy-charge-attack-start');
    playVideo($chargeAttackPreEffectVideo, null, null);
    $('.global-audio-container .enemy-charge-attack-start').get(0).play();

    return new Promise(resolve => setTimeout(function () {
        console.log('[processEnemyChargeAttackPreEffect] DONE');
        resolve();
    }, $chargeAttackPreEffectVideo.get(0).duration * 1000));
}

async function processEnemyChargeAttack(response) {
    await processEnemyChargeAttackPreEffect();

    // 준비
    let $enemyVideo = getVideo(0, response.moveType, MoveType.IDLE_DEFAULT);
    let effectDuration = $enemyVideo.effect ? $enemyVideo.effect.get(0).duration * 1000 : 0;
    let standbyIdleClassName = MoveType.byClassName($('.enemy-video-container').attr('data-standby-move-class')).getIdleType().className;
    let $standbyIdleVideo = $('.enemy-video-container .' + standbyIdleClassName);
    // 준비 - 아군 (0번은 사용안함)
    let $partyVideos = [-1, 1, 2, 3, 4].map(number => getVideo(number, MoveType.DAMAGED_DEFAULT, MoveType.IDLE_DEFAULT));

    // 적 비디오 컨테이너에 스탠바이 상태 해제
    $('.enemy-video-container').attr('data-standby-move-class', MoveType.NONE.className);
    // 전조 컨테이너 deActivate
    setTimeout(function () {
        $(".omen-container").removeClass('activated')
            .find('.omen-text').removeClass().addClass('omen-text') // 해당 컨디션 찾기 귀찮아서 그냥 밀어버리고 omen-text 추가
            .find('.omen-prefix').text('')
            .find('.omen-value').text('');
    }, effectDuration);

// EFFECT 이펙트 시작
    // 오디오 재생
    let audioSrc = $('.enemy-audio-container .' + response.moveType.className).attr('src');
    window.effectAudioPlayer.loadSound(audioSrc).then(() => window.effectAudioPlayer.playAllSounds());

    // 적 일반공격 이펙트 재생 (스탠바이 -> 차지어택 -> idle Default순)
    $standbyIdleVideo.addClass('hidden').get(0).pause(); // standbyIdle 정지, 숨김
    playVideo($enemyVideo.effect, null, $enemyVideo.idle);

    // 데미지 후처리 (데미지 표시, 아군 피격 재생)
    enemyDamagesPostProcess(response, $enemyVideo, $partyVideos);

    // 스테이터스 아이콘 갱신
    processStatusIconSync(response.currentBattleStatusesList, effectDuration + 500);
    // 힐 이펙트 처리
    let healEndTime = processHealEffect(response.heals, effectDuration + 500);
    // 버프 이펙트 처리
    let buffEndTime = processBuffEffect(response.addedBuffStatusesList, response.removedBuffStatusesList, response.removedDebuffStatusesList, healEndTime);
    // 디버프 이펙트 처리
    let debuffEndTime = processDebuffEffect(response.addedDebuffStatusesList, buffEndTime);

    let totalEndTime = Math.max(effectDuration + 100, buffEndTime, debuffEndTime);
    console.log('[processEnemyChargeAttack] chargeAttackDuration ', effectDuration, 'buffEndTime ', buffEndTime, 'debuffEndTiem ', debuffEndTime, 'totalEndTime = ', totalEndTime);

    return new Promise(resolve => setTimeout(function () {
        console.log(response.moveType.name + ' done');
        resolve();
    }, totalEndTime + 300));
}