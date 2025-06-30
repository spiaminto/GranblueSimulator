$(function () {

    $('#showAbilityInfoCheck').on('change', function () {
        console.log($(this).is(':checked'));
        let isShowAbilityInfoCheck = $(this).is(':checked');
        if (isShowAbilityInfoCheck) {
            $('#abilitySlider .ability-icon').off('click');
            $('#abilitySlider .ability-icon').on('click', function () {
                openAbilityInfoModal($(this));
            });
        } else {
            $('#abilitySlider .ability-icon').off('click');
            $('#abilitySlider .ability-icon').on('click', processAbilityClick);
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
        // console.log($abilityIcon); // ability-icon 에 직접 연결하지 않으면 이벤트 정보가 들어옴. type 속성으로 'click' 등을 짐
        $abilityIcon = $abilityIcon.type ? $(this) : $abilityIcon; // type 속성이 있으면 ability-icon과 직접연결됨, 없으면 파라미터로 ability-icon 받아옴

        // 어빌리티 사용 가능한 상태가 아님 DEV 개발중에는 어빌리티 오버레이에 상관없이 실행
        // if ($abilityIcon.find('.ability-overlay').hasClass('not-ready')) return false;

        let charOrder = $abilityIcon.closest('.ability-panel').data('characterOrder');
        let abilityId = $abilityIcon.attr('data-ability-id');
        let currentAbilityRailLength = $('.ability-rail-wrapper img').length;

        // 어빌리티 not-ready 로 변경 (오버레이)
        let $processedAbility = $('.ability-panel.character-' + charOrder + ' [data-ability-id=' + abilityId + ']');
        $processedAbility.find('.ability-overlay').addClass('not-ready');

        // 어빌리티 레일에 등록
        $('<div>', {
            'class': 'rail-ability rail-ability-' + currentAbilityRailLength,
            'data-character-order': charOrder,
            'data-ability-id': abilityId,
        }).append($abilityIcon.find('img').clone()) // 이미지를 어빌리티 레일에 넣을 새 div 로 감싸서 생성
            .on('click', function () { // 클릭시 어빌리티 레일에서 제거 이벤트
                if ($(this).index() === 1) return; // 자신이 첫번째 어빌리티면 클릭 불가 (더미 = 0)
                $(this).find('.ability-overlay').removeClass('not-ready'); // 오버레이 해제
                $(this).remove(); // 제거
            })
            .appendTo($('.ability-rail-wrapper'));
    }


    const abilityRailMutationObserver = new MutationObserver((entries) => {
        // callback : processAbility() 를 호출했다면 true 반환
        console.log(entries);
        let abilityRailWrapper = entries[0].target;
        let addedAbility = entries[0].addedNodes;
        let removedAbility = entries[0].removedNodes;

        // 첫번째 어빌리티가 추가됨
        if ($(addedAbility).hasClass('rail-ability-1')) {
            // 어빌리티 실행
            let charOrder = $(addedAbility).attr('data-character-order');
            let abilityId = $(addedAbility).attr('data-ability-id');
            $(addedAbility).addClass('active');
            setTimeout(() => {
                let characterId = $('#partyCommandContainer .battle-portrait').eq(charOrder - 1).data('character-id');
                requestMove(characterId, abilityId);
            }, 500);
            return true;
        }

        // 삭제된 레일 어빌리티 존재
        if ($(removedAbility).length > 0) {
            if ($(abilityRailWrapper).children('.rail-ability').length < 1) {
                // 삭제후 남은 어빌리티가 더미밖에 없음 -> 어빌리티 실행없이 종료
                return false;
            }
            if ($(removedAbility).hasClass('active')) {
                // 어빌리티가 실행 후 삭제됨 -> 다음 어빌리티(제일 왼쪽) 실행
                let $latestAbility = $(abilityRailWrapper).children('.rail-ability').first();
                let charOrder = $latestAbility.attr('data-character-order');
                let abilityId = $latestAbility.attr('data-ability-id');
                $latestAbility.addClass('active');
                setTimeout(() => {
                    let characterId = $('#partyCommandContainer .battle-portrait').eq(charOrder - 1).data('character-id');
                    requestMove(characterId, abilityId);
                }, 700);
                return true;
            } else {
                // 레일에서 삭제된 어빌리티가 최근 실행한 어빌리티가 아님 (사용자가 클릭해서 삭제) -> 어빌리티 실행없이종료
                return false;
            }
        } else {
            console.log('has removed ability but invalid ', $(removedAbility));
        }
        return false;
    })

    abilityRailMutationObserver.observe(document.querySelector('.ability-rail-wrapper'), {childList: true});


})
