function processChargeAttack(charOrder, audioPlayers) {

    let partySelector = '.party-' + charOrder;

    // 아군 공격모션
    let $chargeAttackMotionVideo = $(partySelector + '.motion-charge-attack');
    console.log($chargeAttackMotionVideo)
    setTimeout(function () {
        $(partySelector + '.motion-idle').addClass('hidden');
        if (charOrder === 1) $(partySelector + '.motion-attack.motion-attack-1').removeClass('hidden').get(0).play(); // 주인공은 오의모션에 자신이 포함되어 있지 않기 떄문에 1번 공격모션도 같이재생
        $chargeAttackMotionVideo.removeClass('hidden').addClass('motion-attack-active').get(0).play();
    }, 300);

    // 공격모션 종료
    $chargeAttackMotionVideo.one('ended', function () {
        console.log('video-ended')

        // 데미지 표시
        $('.enemy-charge-attack-damage').fadeTo(100, 0.8).delay(600).fadeTo(200, 0);

        // 모션 idle 로 변경
        if (charOrder === 1) $(partySelector + '.motion-attack.motion-attack-1').addClass('hidden'); // 주인공이면 공격모션 추가로 숨김
        $(this).addClass('hidden').removeClass('motion-attack-active');
        $(partySelector + '.motion-idle').removeClass('hidden');

        // 데미지 표시 종료후
        setTimeout(() => {

            // TODO 버프/ 디버프 처리

            $("#processingTask input[name = 'processingTask']:checked").next().next().next().click(); // 라벨, br 건너뛰기위해 3번
        }, 1000);
    })

    // 효과음 재생
    let audioInfos = $(partySelector + '-audio.charge-attack').toArray().map(element => ({
        url: $(element).attr('src'),
        delay: $(element).data('delay') || 0
    }));
    console.log(audioInfos);
    audioPlayers.get(charOrder).loadSounds(audioInfos).then(() => {
        audioPlayers.get(charOrder).playAllSounds();
    })
}