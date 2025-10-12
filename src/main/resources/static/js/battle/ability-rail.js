$(function () {

    $('#showAbilityInfoCheck').on('change', function () {
        console.log($(this).is(':checked'));
        let isShowAbilityInfoCheck = $(this).is(':checked');
        let $abilityIcons = $('#abilitySlider .ability-icon');
        if (isShowAbilityInfoCheck) {
            $abilityIcons.off('click');
            $abilityIcons.on('click', function () {
                openAbilityInfoModal($(this));
            });
        } else {
            $abilityIcons.off('click');
            $abilityIcons.on('click', processAbilityClick);
        }
    })

    $('.fatal-chain-gauge-wrapper').on('click', function () {
        openAbilityInfoModal($(this));
    })

    function openAbilityInfoModal($abilityIcon) {
        let iconSrc = $abilityIcon.find('img').attr('src');
        let abilityInfoText = $abilityIcon.find('.ability-info-text').text();
        let isFatalChain = abilityInfoText === '';
        if (isFatalChain) {
            // 페이탈 체인
            abilityInfoText = $($abilityIcon).find('.fatal-chain-gauge-info').text();
        }
        let isNotReady = $abilityIcon.find('.ability-overlay').hasClass('not-ready');
        // 모달에 내용 채우기
        $('#abilityInfoModal').find('.ability-info-icon').attr('src', iconSrc).end()
            .find('.ability-info-text').text(abilityInfoText).end()
            .find('.use-ability-button').prop('disabled', isNotReady);
        // 모달 닫기 버튼에 클릭 이벤트 핸들러 제거하는 이벤트 등록 (닫을때 내용은 사라져도 이벤트는 해제안됨)
        $('#abilityInfoModal .close-ability-info-modal-button').one('click', function () {
            $('#abilityInfoModal .use-ability-button').off('click');
        })
        $('#abilityInfoModal .use-ability-button').one('click', function () {
            $('#abilityInfoModal .close-ability-info-modal-button').click();
            if (isFatalChain) {
                let characterId = $('#partyCommandContainer .battle-portrait').eq(0).data('character-id');
                requestMove(characterId, $abilityIcon.find('.fatal-chain-gauge-info').attr('data-fatal-chain-move-id'));
            } else
                processAbilityClick($abilityIcon);
        });
        $('.ability-info-modal-button').click();
    }

    // 어빌리티 슬라이더에서 어빌리티 클릭
    $('#abilitySlider .ability-icon').on('click', processAbilityClick);

    function processAbilityClick($abilityIcon) {
        if (player.locked) return;
        // console.log($abilityIcon); // ability-icon 에 직접 연결하지 않으면 이벤트 정보가 들어옴. type 속성으로 'click' 등을 짐
        $abilityIcon = $abilityIcon.type ? $(this) : $abilityIcon; // type 속성이 있으면 ability-icon과 직접연결됨, 없으면 파라미터로 ability-icon 받아옴

        // 어빌리티 사용 가능한 상태가 아님 DEV 개발중에는 어빌리티 오버레이에 상관없이 실행
        // if ($abilityIcon.find('.ability-overlay').hasClass('not-ready')) return false;

        let charOrder = $abilityIcon.closest('.ability-panel').data('characterOrder');
        let abilityId = $abilityIcon.attr('data-ability-id');
        let currentAbilityRailLength = $('#abilityRail img').length;

        // 어빌리티 not-ready 로 변경 (오버레이)
        let $processedAbility = $('.ability-panel.character-' + charOrder + ' [data-ability-id=' + abilityId + ']');
        $processedAbility.find('.ability-overlay').addClass('not-ready');

        // 어빌리티 레일에 등록
        $('<div>', {
            'class': 'rail-item rail-item-' + currentAbilityRailLength,
            'data-rail-item-type': 'ABILITY',
            'data-character-order': charOrder,
            'data-ability-id': abilityId,
        }).append($abilityIcon.find('img').clone()) // 이미지를 어빌리티 레일에 넣을 새 div 로 감싸서 생성
            .on('click', function () { // 클릭시 어빌리티 레일에서 제거 이벤트
                if ($(this).index() === 1) return; // 자신이 첫번째 어빌리티면 클릭 불가 (더미 = 0)
                $(this).find('.ability-overlay').removeClass('not-ready'); // 오버레이 해제
                $(this).remove(); // 제거
            })
            .appendTo($('#abilityRail'));
    }

    // 공격버튼 클릭
    $('#attackButton').on('click', processAttackClick)

    function processAttackClick() {
        let isCanceling = $('#attackButtonWrapper').hasClass('cancel');
        if (isCanceling) {
            cancelAttack();
            return;
        }

        if (player.locked) return;
        player.lockPlayer(true); // 플레이어 잠금
        $('#attackButtonWrapper').addClass('cancel');
        effectAudioPlayer.loadAndPlay(GlobalSrc.REQUEST_ATTACK.audio);
        $('#attackButtonWrapper img').attr('src', '/static/assets/img/ui/ui-attack-cancel.png')
        let currentAbilityRailLength = $('#abilityRail img').length;
        $('<div>')
            .addClass('rail-item-attack rail-item rail-item-' + currentAbilityRailLength)
            .attr('data-rail-item-type', 'ATTACK')
            .append($('<img>')
                .attr('src', '/static/assets/img/ui/ui-attack-icon.png'))
            .on('click', function () { // 클릭시 어빌리티 레일에서 제거 이벤트
                if ($(this).index() === 1) return; // 자신이 첫번째 어빌리티면 클릭 불가 (더미 = 0)
                $(this).find('.ability-overlay').removeClass('not-ready'); // 오버레이 해제
                $(this).remove(); // 제거
            })
            .appendTo($('#abilityRail'));
    }

    const abilityRailMutationObserver = new MutationObserver((entries) => {
        console.log(entries);
        let $abilityRail = $(entries[0].target); // #abilityRail
        let $addedRailItem = $(entries[0].addedNodes);
        let $removedRailItem = $(entries[0].removedNodes);
        let isExecuted = false; // 처리 수행이 있었다면 true 로 반환

        if ($abilityRail.children('.rail-item').length < 1) {
            return false; // 남은 아이템 없음
        } else {
            let $latestRailItem = $abilityRail.children('.rail-item').first();
            let railItemType = $latestRailItem.attr('data-rail-item-type');

            if (($addedRailItem.hasClass('rail-item-1')) // 첫 아이템 추가
                || $removedRailItem.hasClass('active')) { // || 아이템 실행 완료후 삭제됨 -> 다음 아이템 실행
                switch (railItemType) {
                    case 'ABILITY':
                        let charOrder = $latestRailItem.attr('data-character-order');
                        let abilityId = $latestRailItem.attr('data-ability-id');
                        $latestRailItem.addClass('active');
                        setTimeout(() => {
                            let characterId = $('#partyCommandContainer .battle-portrait').eq(charOrder - 1).data('character-id');
                            requestMove(characterId, abilityId);
                        }, 300);
                        isExecuted = true;
                        break;

                    case 'ATTACK':
                        $('.ability-rail-wrapper .rail-item').eq(0).remove(); // 공격은 실행즉시 레일에서 삭제
                        requestTurnProgress();
                        isExecuted = true;
                        break;

                    default:
                        console.log('invalid railItemType ', railItemType);
                        break;
                }
            } else { // 레일에서 삭제된 아이템이 실행 없이 삭제 -> 사용자가 직접 삭제함
                if ($removedRailItem.attr('data-rail-item-type') === 'ATTACK') cancelAttack();
                isExecuted = false;
            }
        }
        return isExecuted;
    })
    abilityRailMutationObserver.observe(document.querySelector('#abilityRail'), {childList: true});


})
