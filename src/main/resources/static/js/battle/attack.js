function processAttack(response) {
    let attackHitCount = response.damages.length; // 공격 데미지 발생수
    let multiAttackCount = response.attackMultiHitCount; // 난격 수
    let attackCount = attackHitCount / multiAttackCount; // 본 공격 카운트 (1 || 2 || 3, 난격제외)
    let elementType = response.elementTypes[0];

    // 준비 - 아군
    let $partyVideo = getVideo(response.charOrder, response.moveType);
    let effectDuration = $partyVideo.effect.get(0).duration * 1000;
    // 준비 - 아군 (난격) : 난격이 붙은경우 이펙트, 모션을 전체적으로 천천히 재생한다.
    let playBackRate = 1.0 - 0.1 * (multiAttackCount - 1); // 난격시 느리게
    $partyVideo.effect.get(0).playbackRate = playBackRate;
    if ($partyVideo.motion) $partyVideo.motion.get(0).playbackRate = playBackRate;
    effectDuration = Math.floor(effectDuration / playBackRate); // 재생 속도에 따른 길이 보정
    let attackDelay = Math.floor(attackHitCount / multiAttackCount >= 3 ? Math.max((effectDuration - 400), 1100) / attackCount : effectDuration / attackCount); // 기본 타수당 딜레이, 난격에 다른 속도 지정후 계산 (약 350ms, 보통 평타 이펙트의 길이가 350 350 900 쯤 된다), 3타시 최소 1100ms 보장
    // console.log('processAttack charOrder = ', charOrder, ' playbackRATE = ', playBackRate, ' attackDuration', attackDuration);

    // 준비 - 적
    let $enemyVideo = getEnemyDamagedVideo();

    // 오디오 재생
    let audioSrcs = [];
    audioSrcs.push($('#audioContainer .actor-' + response.charOrder + '.' + response.moveType.className + ".voice").attr('src')); // 보이스
    let effectSrc = $('#audioContainer .actor-' + response.charOrder + '.' + MoveType.SINGLE_ATTACK.className + ".effect").attr('src')
    audioSrcs.push(...Array(attackHitCount).fill(effectSrc)); // 타수만큼 싱글어택 사운드 준비
    window.effectAudioPlayer.loadSounds(audioSrcs).then(() => {
        window.effectAudioPlayer.playAttackSounds(attackHitCount, multiAttackCount, attackDelay);
    });

    // 아군 일반공격 이펙트 재생
    playVideo($partyVideo.effect, $partyVideo.motion, $partyVideo.idle);

    // 데미지 표시, 적 피격 모션 재생 (히트수 만큼 반복)
    let $attackDamageWrapper = $('<div>', {class: 'attack-damage-wrapper party actor-' + response.charOrder});
    $('#damageContainer').append($attackDamageWrapper);

    const damageFragment = document.createDocumentFragment();
    response.damages.forEach(function (damage, index) {
        // 데미지 채우기
        let attackIndex = Math.floor(index / multiAttackCount); // 현재 인덱스의 기본타수 순서 (0, 1, 2 현재 트리플 어택까지 구현했으므로 여기까지)
        let multiAttackIndex = index % multiAttackCount; // 현재 인덱스의 난격 타수 순서 (공격마다 0-1-2-3-... 씩으로 진행, 0이면 난격이 아님)
        let missClassName = damage === 'MISS' ? ' damage-miss' : '';

        // 난격 여부 확인 후 클래스 설정 / [012 345 678] 3타 3난격 시 di-0 m-0, di-0 m-1, di-0 m-2, di-1 m-0, di-1 m-1, ...
        let attackDamageIndexClassName = ' damage-index-' + attackIndex + ' multi-' + multiAttackIndex;

        let $attackDamage = $('<div>', { // 본 공격 + 난격
            class: 'attack-damage actor-' + response.charOrder + ' element-type-' + elementType.toLowerCase() + attackDamageIndexClassName + missClassName,
            text: damage,
        });
        damageFragment.append($attackDamage[0]);
        let $additionalDamage = $('<div>', { // 추격
            class: 'additional-damage-wrapper actor-' + response.charOrder + ' element-type-' + elementType.toLowerCase() + attackDamageIndexClassName  + missClassName,
            text: damage // 공간 사용을 위해
        }).append((response.additionalDamages[index] || []).map(additionalDamage =>
            $('<div>', {
                class: 'additional-damage element-type-' + elementType.toLowerCase(),
                text: additionalDamage
            })
        ))
        damageFragment.append($additionalDamage[0]);

        let startDelay = attackDelay * attackIndex; // 1타당 시작시 걸어줄 딜레이, 난격이 있을경우 난격마다 딜레이가 같아짐 ([1,2,3,4,5,6] 2회난격시 승수가 0, 0, 1, 1, 2, 2)
        let multiHitDelay = multiAttackIndex * 115; // 난격마다 추가 딜레이
        let totalDelay = startDelay + multiHitDelay; // 최종 딜레이
        // console.log('[processAttack] baseDelay = ', attackDelay, ' startDelay = ', startDelay, ' multiHitDealy = ', multiHitDelay, ' totalDelay = ', totalDelay);

        // 마지막에 데미지 요소 추가
        index >= attackHitCount - 1 ? $attackDamageWrapper.append(damageFragment) : null;

        setTimeout(function () {
            // 적 피격 이펙트 재생 (난격일땐 재생 x)
            multiAttackIndex === 0 ? playVideo($enemyVideo.effect, null, $enemyVideo.idle) : null;
            // 데미지 표시
            $attackDamage.addClass('damage-show');
            // 추가데미지 표시
            $additionalDamage.children().each(function (index, additionalDamage) {
                setTimeout(function () {
                    $(additionalDamage).addClass('damage-show'); // 추가데미지는 부모와의 fadeIn 겹침을 피하기 위해 display none 설정되어있음
                }, (index + 1) * 100);
            });

            if (index >= attackHitCount - 1) {
                // 데미지 제거 데미지갯수 * 2 (추격컨테이너 까지 지움)
                setTimeout(function () {
                    $('.attack-damage-wrapper.actor-' + response.charOrder).slice(0, attackHitCount * 2).remove();
                }, 1300); // 마지막 데미지 페이드아웃 딜레이 대기, 현재 1200ms
            }
        }, totalDelay)
    })

    // 일반공격후 스테이터스 갱신은 현재 없음.

    let totalEndTime = effectDuration;
    console.log("[processAttack] totalEndTime = " + totalEndTime)
    // 최종종료
    return new Promise(resolve => setTimeout(function () {
        console.log('ATTACK done');
        resolve();
    }, totalEndTime + 300));
}


function processEnemyAttack(response) {
    let attackCount = response.moveType === MoveType.SINGLE_ATTACK ? 1 : response.moveType === MoveType.DOUBLE_ATTACK ? 2 : 3;
    let targetOrders = response.enemyAttackTargetOrders;
    let uniqueTargetOrders = [...new Set(targetOrders)];
    let isAllTarget = response.allTarget; // 전체공격여부

    // 준비 - 적
    let standbyMoveClassName = $('.enemy-video-container').attr('data-standby-move-class');
    let idleMoveType = standbyMoveClassName === 'none' ? MoveType.IDLE_DEFAULT : MoveType.byClassName(standbyMoveClassName).getIdleType();
    let $enemyVideo = getVideo(0, MoveType.SINGLE_ATTACK, idleMoveType)
    let effectDuration = $enemyVideo.effect.get(0).duration * 1000 * attackCount;
    // 준비 - 아군 (0번은 사용안함)
    let $partyVideos = [-1, 1, 2, 3, 4]
        .map(number => getVideo(number, MoveType.DAMAGED_DEFAULT, MoveType.IDLE_DEFAULT));

// EFFECT 이펙트 시작
    // 오디오 재생
    let audioSrc = $('.enemy-audio-container .' + response.moveType.className).attr('src');
    window.effectAudioPlayer.loadSound(audioSrc).then(() => window.effectAudioPlayer.playAllSounds());

    // 적 일반공격 이펙트 재생
    $enemyVideo.effect.removeClass('hidden');
    $enemyVideo.idle.addClass('left-hidden');
    $enemyVideo.idle.get(0).pause();
    $enemyVideo.idle.get(0).currentTime = 0;
    for (let i = 0; i < attackCount; i++) {
        setTimeout(function () {
            $enemyVideo.effect.get(0).currentTime = 0;
            $enemyVideo.effect.get(0).play();

            if (i === attackCount - 1) { // 마지막
                setTimeout(function () {
                    requestAnimationFrame(function () {
                        $enemyVideo.idle.removeClass('left-hidden');
                        $enemyVideo.idle.get(0).play();
                        setTimeout(function () {
                            $enemyVideo.effect.addClass('hidden');
                        }, 50)
                    })
                }, effectDuration / attackCount)
            }
        }, (effectDuration / attackCount) * i)
    }

    // 후행동 공격데미지와 겹치지 않도록 미리 데미지 래퍼 추가
    let $appendedEnemyDamageWrappers = [];
    uniqueTargetOrders.forEach(targetOrder => {
        $appendedEnemyDamageWrappers.push($('<div>', {class: 'enemy-damage-wrapper actor-' + targetOrder}).appendTo($('#damageContainer')));
    })
    //  데미지 마다 반복 - 데미지삽입, 데미지표시, 피격이펙트 재생
    let effectHitDuration = effectDuration / attackCount; // 1타가 사용할 길이
    response.damages.forEach(function (damage, index) {
        let startDelay = isAllTarget ?
            effectHitDuration * (index / response.enemyAttackTargetOrders.length) :
            effectHitDuration * index;
        let targetOrder = isAllTarget ?
            response.enemyAttackTargetOrders[index % targetOrders.length] :
            response.enemyAttackTargetOrders[index];
        let elementType = response.elementTypes[index];

        // 데미지 채우기
        let $damageElements = getDamageElement(targetOrder, elementType, 'attack', index, damage, response.additionalDamages[index], true);
        let $enemyDamageWrapper = $('.enemy-damage-wrapper.actor-' + targetOrder).last(); // 이전 행동 공격데미지와 겹치지 않도록 추가한 래퍼 사용
        $enemyDamageWrapper.append($damageElements.$damage, $damageElements.$additionalDamage);

        // 데미지 표시
        setTimeout(function () {
            // 데미지 및 표시
            $damageElements.$damage.addClass('enemy-damage-show');
            // 추가데미지 표시
            $damageElements.$additionalDamage.children().each(function (index, additionalDamage) {
                setTimeout(function () {
                    $(additionalDamage).addClass('enemy-damage-show');
                }, index + 100);
            });
            // 아군 피격 재생
            playVideo($partyVideos[targetOrder].effect, null, $partyVideos[targetOrder].idle);
            // 마지막 공격시 종료후 데미지 삭제.
            if (index >= response.damages.length - 1) {
                setTimeout(function () {
                    $appendedEnemyDamageWrappers.forEach(function (wrapper) {
                        $(wrapper).remove();
                    })
                }, 1000);
            }
        }, startDelay)
    })

    let totalEndTime = isAllTarget ? effectDuration + 100 : effectDuration * response.damages.length + 100;
    console.log("[processEnemyAttack] total = " + totalEndTime)
    return new Promise(resolve => setTimeout(function () {
        console.log('ENEMY ATTACK done');
        resolve();
    }, totalEndTime + 300));

// 현재 일반공격의 스테이터스 갱신은 없음
}
