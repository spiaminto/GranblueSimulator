$(function () {
    $('.get-status-modifier-type-button').on('click', function () {
        requestInfo("/status-modifiers");
    });
    $('.get-basic-status-effect-info-button').on('click', function () {
        requestInfo("/basic-status-effects");
    });

    $('form').on('click', '.paste-button', function (e) {
        let $next = $(e.target).next();
        if ($next.is('input[type="text"]'))
            $next.val(localStorage.getItem('clipBoard'));
    })

    // basicStatusEffect 모달 열기
    let $basicStatusEffectModalTargetWrapper = null;
    document.getElementById('basicStatusEffectInfoModal').addEventListener('show.bs.modal', function (event) {
        const button = event.relatedTarget; // 모달을 연 버튼
        $basicStatusEffectModalTargetWrapper = $(button).closest('.status-input-wrapper');
        this.querySelectorAll('.form-check-input').forEach(element => element.checked = false);
    });

    // basicStatusEffect 모달 렌더링
    function renderBasicStatusEffectInfoModal(basicStatusEffects) {
        if (!basicStatusEffects) return;
        let $listItemWrapper = $(`<div class="info-list-wrapper"></div>`);
        basicStatusEffects.forEach((basicEffect, index) => {
            $listItemWrapper.append($(`
                            <div class="form-check info-list-item">
                              <input class="form-check-input basic-status-effect-info basic-status-effect-info-${index}"
                              type="checkbox" name="basicStatusEffectInfo" id="basic-status-effect-info-${index}" value="${basicEffect.statusText}">
                              <label class="form-check-label" for="basic-status-effect-info-${index}">
                                <img width="40px" src="${basicEffect.iconSrc}">
                                <b class="fs-5">${basicEffect.name}</b><span class="fs-5"> [${basicEffect.effectText}]</span> 
                                <br>
                                <span class="ms-2">${basicEffect.statusText}</span>
                              </label>
                            </div>
                          `).on('click', function (e) {
                let $check = $(e.target);
                if ($check.is(':checked')) {
                    // 모달 아이템 적용
                    let wrapperIndex = $basicStatusEffectModalTargetWrapper.closest('.status-input-container').find('.status-input-wrapper').index($basicStatusEffectModalTargetWrapper);

                    let $inputs = [
                        $basicStatusEffectModalTargetWrapper.find(`input[name="statuses[${wrapperIndex}].type"]`).val(basicEffect.type),
                        $basicStatusEffectModalTargetWrapper.find(`input[name="statuses[${wrapperIndex}].name"]`).val(basicEffect.name),
                        $basicStatusEffectModalTargetWrapper.find(`input[name="statuses[${wrapperIndex}].statusText"]`).val(basicEffect.statusText),

                        $basicStatusEffectModalTargetWrapper.find(`input[name="statuses[${wrapperIndex}].maxLevel"]`).val(basicEffect.level),
                        $basicStatusEffectModalTargetWrapper.find(`select[name="statuses[${wrapperIndex}].durationType"]`).val(basicEffect.durationType),

                        $basicStatusEffectModalTargetWrapper.find(`input[name="statuses[${wrapperIndex}].gid"]`).val(basicEffect.gid),
                    ];
                    autofilled($inputs);

                    if (basicEffect.durationType === 'TIME' || basicEffect.durationType === 'TURN_INFINITE' || basicEffect.durationType === 'LEVEL_INFINITE') {
                        // 시간제 또는 영속인경우 적용
                        $basicStatusEffectModalTargetWrapper.find(`input[name="statuses[${wrapperIndex}].duration"]`).val(basicEffect.duration);
                    }

                    $check.closest('.modal').find('.modal-close-button').click();
                }
            }));
        });
        $('#basicStatusEffectInfoModal .modal-body').empty().append($listItemWrapper);
    }

    function autofilled($inputs) {
        $inputs.forEach($element => {
            $element.addClass('autofilled').one('change', function () {
                $(this).removeClass('autofilled');
            });
        });
    }


    // modifier 모달 아이템 열기
    let currentModifierModalTargetInput;
    document.getElementById('modifierInfoModal').addEventListener('show.bs.modal', function (event) {
        const button = event.relatedTarget; // 모달을 연 버튼
        currentModifierModalTargetInput = button.nextElementSibling;
    });

    // modifier 모달 렌더링
    function renderModifierModal(obj) {
        if (!obj) return;
        let $listItemWrapper = $(`<div class="info-list-wrapper"></div>`);
        let lastPrefix = '_';
        let secondPrefix = '';
        Object.entries(obj).forEach(([key, value], index) => {
            let $listItem = $(`
                            <div class="form-check info-list-item">
                              <input class="form-check-input modifier-info modifier-info-${index}"
                              type="checkbox" name="modifier-info" id="modifier-info-${index}" value="${key}">
                              <label class="form-check-label" for="modifier-info-${index}">
                                <b>${key}</b> 
                                <br>
                                <span class="ms-2">${value}</span>
                              </label>
                            </div>
                          `)
                .on('click', function (e) {
                    let $check = $(e.target);
                    if ($check.is(':checked')) {
                        $('#modifierInfoModal .status-modifier-apply-wrapper').append($(
                            `<div class="input-group apply-info modifier-info-${index}">
                               <input type="text" class="form-control" value="${$check.val()}">
                               <span class="input-group-text"> 값: </span>
                               <input type="number" class="form-control">
                             </div>
                               `));
                    } else {
                        $(`#modifierInfoModal .status-modifier-apply-wrapper .modifier-info-${index}`).remove();
                    }
                });
            if (!key.includes(secondPrefix) || !key.includes(lastPrefix)) {
                $listItem.css({
                    'margin-top': '2rem',
                    'border-top': 'solid 2px black'
                });
            }
            $listItemWrapper.append($listItem);

            if (key.includes('TAKEN_')) {
                let replacedKey = key.replace('TAKEN_', '');
                secondPrefix = replacedKey.substring(0, key.indexOf('_') + 1);
                console.log(secondPrefix)
            } else {
                secondPrefix = '';
            }
        });
        $('#modifierInfoModal .modal-body').empty().append($listItemWrapper);
    }

    // modifier 모달 아이템 클릭
    $('#modifierInfoModal .info-list-wrapper .modifier-info').on('click', function (e) {
        let $check = $(e.target);
        if ($check.is(':checked')) {
            $('#modifierInfoModal .status-modifier-apply-wrapper').append($(
                `<div class="input-group apply-info">
                        <input type="text" class="form-control" value="${$check.val()}">
                        <span class="input-group-text"> 값: </span>
                        <input type="number" class="form-control">
                      </div>
                  `))
        }
    });
    // modifier 모달 아이템 적용
    $('#modifierInfoModal .modifier-apply-button').on('click', function (e) {
        let value = '';
        let hasEmptyValue = false;
        $('.status-modifier-apply-wrapper .apply-info').each(function (index, element) {
            let modifierType = $(element).find('input[type="text"]').val();
            let modifierValue = $(element).find('input[type="number"]').val();
            if (!modifierValue) {
                hasEmptyValue = true;
                return;
            }
            value += (modifierType + ', ' + modifierValue + '\n');
        })
        if (hasEmptyValue) {
            alert('값을 입력하세요');
            return;
        }
        // 적용
        currentModifierModalTargetInput.value = value;
        currentModifierModalTargetInput = null;
        // 정리후 닫기
        $('.status-modifier-apply-wrapper').empty();
        $('#modifierInfoModal .info-list-wrapper .modifier-info').attr('checked', false);
        $('.status-modifier-apply-wrapper .form-check-input:checked').attr('checked', false);
        $(this).closest('.modal').find('.modal-close-button').click();
    });
    // modifier 모달 아이템 클리어
    $('#modifierInfoModal .modal-clear-button').on('click', function (e) {
        $('.status-modifier-apply-wrapper').empty();
        $('.status-modifier-apply-wrapper .form-check-input:checked').attr('checked', false);
    })

    // 요청
    function requestInfo(url) {
        $.ajax({
            type: "GET",
            url: url,
            success: function (data) {
                console.log('[ajaxRequest] response data = ', data);
                let modifiers = data.modifiers;
                modifiers && renderModifierModal(modifiers);
                let basicStatusEffects = data.basicStatusEffects;
                basicStatusEffects && renderBasicStatusEffectInfoModal(basicStatusEffects);
            },
            error: function (xhr) {
                console.error('[ajaxRequest] response xhr = ', xhr);
            }
        })
    }

});


function copyToLocalStorage(text) {
    localStorage.setItem('clipBoard', text);
}
