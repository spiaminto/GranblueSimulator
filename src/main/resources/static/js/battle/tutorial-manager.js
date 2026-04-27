class TutorialManager {

    static IMG_BASE_URL = '/static/gbf/img/tutorial/3993440000_';

    constructor(steps, saveIndex = 0) {
        this.steps = steps;
        this.idx = 0;
        this.active = false;
        this._typeTimer = null;
        this._currentTarget = null;
        this._currentObs = null;
        this._buildDOM();
    }

    // ── DOM 생성 ─────────────────────────────────────────────────

    _buildDOM() {
        const container = document.getElementById('container');

        // DIALOG용 풀 오버레이 (클릭 → 다음 스텝)
        this._fullOverlay = this._makeEl('tut-full-overlay', `
      position:absolute; inset:0;
      background:rgba(0,0,0,0.1);
      z-index:1000; pointer-events:all;
      display:none; cursor:pointer;
    `);

        // INTERACT용 4분할 패널
        this._panels = {};
        ['top', 'bottom', 'left', 'right'].forEach(side => {
            this._panels[side] = this._makeEl(`tut-panel-${side}`, `
        position:absolute;
        background:rgba(0,0,0,0.72);
        z-index:1000; pointer-events:all;
        display:none;
      `);
        });


        this._dialog = this._makeEl('tut-dialog', `
            position: absolute;
            z-index: 1001;
            pointer-events: none;
            background: rgba(15, 15, 25, 0.92);
            border: 1px solid rgba(255, 255, 255, 0.18);
            border-radius: 8px;
            padding: 6px 6px;
            color: rgb(240, 240, 240);
            font-size: 12px;
            line-height: 1.3;
            box-shadow: rgba(0, 0, 0, 0.6) 0px 4px 24px;
            display: flex;
            flex-direction: row;
            align-items: flex-start;
            gap: 10px;
            bottom: 288px;
            top: auto;
            left: 0px;
            width: 330px;
            height: 60px;
        `);

        // 왼쪽 캐릭터 이미지
        this._dialogImg = document.createElement('img');
        this._dialogImg.id = 'tut-dialog-img';
        this._dialogImg.style.cssText = `
              width: 75px;
              height: 110px;
              object-fit: cover;
              flex-shrink: 0;
              display: block;
              position: relative;
              bottom: 60px;
            `;

        // 오른쪽 텍스트
        this._dialogText = document.createElement('div');
        this._dialogText.id = 'tut-dialog-text';
        this._dialogText.style.whiteSpace = 'pre-wrap';


        this._dialog.appendChild(this._dialogImg);
        this._dialog.appendChild(this._dialogText);

        // 클릭 유도 힌트 텍스트
        this._hint = this._makeEl('tut-hint', `
            position: absolute;
            z-index: 1002;
            display: block;
            pointer-events: none;
            color: rgb(255, 255, 180);
            font-size: 10px;
            text-align: right;
            bottom: 273px;
            top: auto;
            left: 0px;
            width: 320px;
    `);
        this._hint.textContent = '탭하여 계속 ▶';

        [this._fullOverlay, ...Object.values(this._panels), this._dialog, this._hint]
            .forEach(el => container.appendChild(el));
    }

    _makeEl(id, cssText) {
        const el = document.createElement('div');
        el.id = id;
        el.style.cssText = cssText.replace(/\n\s+/g, '');
        return el;
    }

    // ── 공개 API ──────────────────────────────────────────────────

    start() {
        this.active = true;
        this.idx = 0;
        this._lockComponents();
        this._runStep();
    }

    // 특정 idx 부터 시작
    startFrom(idx) {
        if (idx < 0 || idx >= this.steps.length - 1) {
            this.end()
            return;
        }
        this.active = true;
        this.idx = idx;
        this._lockComponents();
        this._runStep();
    }

    end() {
        this.active = false;
        this._unlockComponents();
        this._hideAll();
    }

    retry() {
        if (!this.active) return;
        this._cleanupWaitListeners();
        this._hideAll();
        this._runStep(); // 현재 idx 로 스탭 재실행
    }

    // ── 스텝 실행 ────────────────────────────────────────────────

    _runStep() {
        const step = this.steps[this.idx];
        // console.log('[_runStep] started, step = ', step);
        if (!step) return this.end();

        step.onStart?.();

        // 다이얼로그 렌더링
        let showHint = step.type === 'DIALOG';
        step.text
            ? this._showDialog(step.text, {showHint: showHint, imgSrc: step.imgSrc, dialogBottom: step.dialogBottom})
            : this._hideDialog();

        // 추가처리
        if (step.type === 'DIALOG') {
            this._hideSpotlight();

            if (step.spotlight) {
                // spotlight 있으면 하이라이트
                const target = document.querySelector(step.spotlight);
                this._showSpotlight(target);
                Object.values(this._panels).forEach(panel => {
                    panel.addEventListener('click', this._advance, {once: true});
                });
            } else {
                this._showFullOverlay();
                this._fullOverlay.addEventListener('click', this._advance, {once: true}); // 풀 오버레이 클릭 → _advance 실행
            }

        } else if (step.type === 'INTERACT') {
            this._hideFullOverlay();
            const target = document.querySelector(step.target);
            this._showSpotlight(target);

            this._setupInteract(step, target);

        } else if (step.type === 'WAIT') {
            this._hideSpotlight();
            this._showFullOverlay();

            this._setupWait(step);
        }
    }

    _advance = () => {
        Object.values(this._panels).forEach(panel => {
            panel.removeEventListener('click', this._advance); // 방어용으로 패널 이벤트리스너 전부 삭제 (DIALOG, spotlight 있을때 4개 모두 설정됨)
        });

        this.idx++;

        let step = this.steps[this.idx];
        if (!!step?.savePoint) {
            this.saveProgress();
        }

        const delay = this.steps[this.idx - 1]?.nextDelay ?? 80; // 다음 스탭까지의 딜레이, 즉시 감지필요시 0 / 모달 등 애니메이션 대기시 300 설정
        setTimeout(() => this._runStep(), delay);
    }

    _setupInteract(step, target) {
        if (!step.waitFor || step.waitFor === 'click') {
            target.addEventListener('click', this._advance, {once: true});

        } else if (step.waitFor === 'event') {
            // 원본 코드가 발생시키는 커스텀 이벤트를 기다림
            document.addEventListener(step.eventName, this._advance, {once: true});

        } else if (step.waitFor === 'mutation') {
            // DOM 변화 감지 (애니메이션 종료 등)
            const obs = new MutationObserver(() => {
                if (!step.mutationCheck || step.mutationCheck()) {
                    obs.disconnect();
                    this._advance();
                }
            });

            // 문자열이면 querySelector, Node면 그대로 사용
            const mutationEl = typeof step.mutationTarget === 'string'
                ? document.querySelector(step.mutationTarget)
                : (step.mutationTarget ?? document.body);

            this._currentObs = obs;
            obs.observe(mutationEl, {childList: true, subtree: true, attributes: true});
        }
    }

    _setupWait(step) {
        if (step.waitFor === 'event') {
            document.addEventListener(step.eventName, this._advance, {once: true});

        } else if (step.waitFor === 'mutation') {
            const obs = new MutationObserver(() => {
                if (!step.mutationCheck || step.mutationCheck()) {
                    obs.disconnect();
                    this._advance();
                }
            });
            const mutationEl = typeof step.mutationTarget === 'string'
                ? document.querySelector(step.mutationTarget)
                : (step.mutationTarget ?? document.body);

            this._currentObs = obs;
            obs.observe(mutationEl, {childList: true, subtree: true, attributes: true});

        } else if (step.waitFor === 'time') {
            setTimeout(this._advance, step.timeout || 0);
        }
    }

    _cleanupWaitListeners() {
        // MutationObserver 정리
        if (this._currentObs) {
            this._currentObs.disconnect();
            this._currentObs = null;
        }
    }

    // ── 대화창 ───────────────────────────────────────────────────

    _showDialog(text, {showHint = true, imgSrc = '', dialogBottom = ''} = {}) {
        // imgSrc 있으면 이미지 표시
        if (imgSrc) {
            this._dialogImg.src = TutorialManager.IMG_BASE_URL + imgSrc;
            this._dialogImg.style.display = 'block';
        } else {
            this._dialogImg.style.display = 'none';
        }


        const cmd = document.getElementById('commandContainer');
        const container = document.getElementById('container');
        const fromBottom = dialogBottom ? dialogBottom : container.offsetHeight - cmd.offsetTop + 8; // 지정되어있지 않으면, commandContainer 위에 붙임

        Object.assign(this._dialog.style, {
            display: 'flex', // block → flex 로 변경
            bottom: fromBottom + 'px',
            top: 'auto',
            left: cmd.offsetLeft + 'px',
            width: cmd.offsetWidth + 'px',
        });

        this._typeText(text);

        Object.assign(this._hint.style, {
            display: showHint ? 'block' : 'none',
            bottom: (fromBottom - 15) + 'px',
            top: 'auto',
            left: cmd.offsetLeft + 'px',
            width: cmd.offsetWidth + 'px',
        });
    }

    _typeText(text) {
        clearInterval(this._typeTimer);
        this._dialogText.textContent = '';
        let i = 0;
        this._typeTimer = setInterval(() => {
            this._dialogText.textContent += text[i++];
            if (i >= text.length) clearInterval(this._typeTimer);
        }, 25);
    }

    _hideDialog() {
        this._dialog.style.display = 'none';
        this._hint.style.display = 'none';
        clearInterval(this._typeTimer);
    }

    // ── 스포트라이트 ─────────────────────────────────────────────

    _showSpotlight(targetEl, pad = 2) {

        // 타겟에 glow 효과 적용
        if (this._currentTarget) removeGlow(this._currentTarget); // 이전 삭제
        this._currentTarget = targetEl;
        applyGlow(targetEl, {color: '#ffe97a', spread: 6, blur: 10, duration: 1.5});


        const container = document.getElementById('container');
        const containerRect = container.getBoundingClientRect();
        const targetRect = targetEl.getBoundingClientRect();
        const s = window.scale || 1;

        // getBoundingClientRect 는 scale 이 반영된 viewport 좌표
        // → container 논리 좌표계로 역보정
        const t = {
            top: (targetRect.top - containerRect.top) / s,
            left: (targetRect.left - containerRect.left) / s,
            bottom: (targetRect.bottom - containerRect.top) / s,
            right: (targetRect.right - containerRect.left) / s,
            height: targetRect.height / s,
        };
        const W = container.offsetWidth;
        const H = container.offsetHeight;

        this._setPanel('top', {top: 0, left: 0, width: W, height: Math.max(0, t.top - pad)});
        this._setPanel('bottom', {top: t.bottom + pad, left: 0, width: W, height: Math.max(0, H - t.bottom - pad)});
        this._setPanel('left', {
            top: t.top - pad,
            left: 0,
            width: Math.max(0, t.left - pad),
            height: t.height + pad * 2
        });
        this._setPanel('right', {
            top: t.top - pad,
            left: t.right + pad,
            width: Math.max(0, W - t.right - pad),
            height: t.height + pad * 2
        });
    }

    _setPanel(side, pos) {
        Object.assign(this._panels[side].style, {
            display: 'block',
            top: pos.top + 'px',
            left: pos.left + 'px',
            width: pos.width + 'px',
            height: pos.height + 'px',
        });
    }

    // ── 숨기기 ───────────────────────────────────────────────────

    _hideSpotlight() {
        if (this._currentTarget) {
            // 현재타겟이 있을경우, glow 효과 삭제
            removeGlow(this._currentTarget);
            this._currentTarget = null;
        }
        Object.values(this._panels).forEach(p => p.style.display = 'none');
    }

    _hideFullOverlay() {
        this._fullOverlay.style.display = 'none';
    }

    _showFullOverlay() {
        this._fullOverlay.style.display = 'block';
    }

    _hideAll() {
        this._hideSpotlight();
        this._hideFullOverlay();
        this._hideDialog();
    }

    // --- 기타 요소 제어 -------------------------------------------------
    _lockComponents() {
        // 모달 backdrop 설정
        $(document).on('show.bs.modal.tutorial', '.modal', function () {
            const instance = bootstrap.Modal.getOrCreateInstance(this);
            instance._config.backdrop = 'static';
            instance._config.keyboard = false;
        });

        // 팝오버 외부클릭 dismiss 비활성화
        $(document).off('click.movePopoverDismiss');
    }

    _unlockComponents() {
        // 모달 backdrop 해제
        $(document).off('show.bs.modal.tutorial');

        // 팝오버 복구
        initChargeAttackPopovers();
    }

    // --- 서버 저장 -----------------------------------------------------

    saveProgress(tutorialIndex = 0) { // 현재 _advance() 에서 savePoint 시 또는 turnProgress success 응답 받은 직후 저장
        tutorialIndex = tutorialIndex || this.idx;
        $.ajax({
            url: '/members/me/tutorial/save',
            method: 'POST',
            headers: {'X-CSRF-TOKEN': $('#csrfToken').val()},
            data: JSON.stringify({
                tutorialIndex: tutorialIndex,
                roomId: $('#roomInfo').attr('data-room-id'),
            }),
            contentType: 'application/json',
            success: function (response) {
                // console.log('[saveProgress]', response);
            },
            error: function (error) {
                console.error(error);
                let errorObj = error.responseJSON;
                let errorCode = errorObj.code;
                if (errorCode) {
                    alert(errorObj.message);
                }
            }
        });
    }

}


const tutorialSteps = [
    /*


     */

    // 1턴
    {
        type: 'DIALOG',
        text: '안녕하세요 루리아입니다! 지금부터 튜토리얼을 시작할게요.',
        imgSrc: 'laugh3_up.png',
        savePoint: true,
    },
    {
        type: 'DIALOG',
        text: '그랑블루의 전투는 턴제로 진행되며, 턴이 시작되면 아군의 어빌리티를 사용할 수 있어요.',
        imgSrc: 'laugh2_up.png'
    },
    {
        type: 'INTERACT',
        text: '우선 어빌리티를 하나 사용해볼까요? 주인공의 초상화를 터치해 보세요.',
        imgSrc: 'laugh2_up.png',
        target: '.battle-portrait.actor-1',
        waitFor: 'click',
    },
    {
        type: 'INTERACT',
        text: '어빌리티 아이콘을 눌러 보세요.',
        imgSrc: 'laugh2_up.png',
        target: '.slick-slide:not(.slick-cloned) .ability-panel.actor-1 .ability-icon[data-order="3"] img',
        waitFor: 'mutation',
        mutationTarget: '#abilityInfoModal',
        mutationCheck: () => {
            return $('#abilityInfoModal').hasClass('show')
        },
        nextDelay: 250,
    },
    {
        type: 'DIALOG',
        text: '어빌리티 아이콘을 누르면, 어빌리티의 상세 내용을 확인할 수 있어요.',
        imgSrc: 'laugh3_up.png',
        spotlight: '#abilityInfoModal .command-info-container',
        dialogBottom: 120
    },
    {
        type: 'DIALOG',
        text: '어빌리티로 인해 부여되는 효과는 위와 같아요. 적을 약화시키는 "약화효과" 는 파란색 으로 표시돼요.',
        imgSrc: 'laugh3_up.png',
        spotlight: '#abilityInfoModal .status-effect-info-wrapper',
        dialogBottom: 120
    },
    {
        type: 'INTERACT',
        text: '이 어빌리티는 현재 쿨타임 중이라 사용할수 없네요. 다른 어빌리티를 확인해볼까요?',
        imgSrc: 'laugh2_up.png',
        target: '#abilityInfoModal .close-ability-info-modal-button',
        waitFor: 'mutation',
        mutationTarget: '#abilityInfoModal',
        mutationCheck: () => {
            return !($('#abilityInfoModal').hasClass('show'))
        },
        nextDelay: 250,
    },
    {
        type: 'INTERACT',
        target: '.slick-slide:not(.slick-cloned) .ability-panel.actor-1 .ability-icon[data-order="2"] img',
        waitFor: 'mutation',
        mutationTarget: '#abilityInfoModal',
        mutationCheck: () => {
            return $('#abilityInfoModal').hasClass('show')
        },
        nextDelay: 250,
    },
    {
        type: 'DIALOG',
        text: '이 어빌리티는 사용가능하네요. 사용 버튼을 누르면 어빌리티 레일에 등록되고, 순서대로 사용됩니다.',
        imgSrc: 'laugh2_up.png',
        dialogBottom: 120
    },
    {
        type: 'INTERACT',
        target: '#abilityInfoModal .use-ability-button',
        waitFor: 'click',
        dialogBottom: 120
    },
    {
        type: 'WAIT',
        waitFor: 'mutation',
        mutationTarget: '#abilityRail',
        mutationCheck: () => {
            return $('#abilityRail .rail-item').length <= 0;
        },
    },
    {
        type: 'DIALOG',
        text: '필요한 어빌리티를 사용하고나면, 공격 버튼을 눌러 턴을 진행해요.',
        imgSrc: 'motivation_up.png',
    },
    {
        type: 'DIALOG',
        text: '턴 진행시, 캐릭터는 각각 1회 공격행동을 수행하며, 일반공격시 확률에 따라 싱글 / 더블 / 트리플 어택을 사용해요.',
        imgSrc: 'motivation_up.png',
    },
    {
        type: 'INTERACT',
        text: '그럼 이제 공격 버튼을 눌러 턴을 진행해 볼게요.',
        imgSrc: 'motivation_up.png',
        target: '#attackButtonWrapper #attackButton',
        waitFor: 'click',
        dialogBottom: 120
    },
    {
        type: 'WAIT',
        waitFor: 'mutation',
        mutationTarget: '.turn-indicator-container',
        mutationCheck: () => {
            return $('.turn-indicator-container').hasClass('show');
        }
    },

    // 2턴
    {
        type: 'DIALOG',
        text: '캐릭터는 일반공격시 10%(싱글), 22%(더블), 37%(트리플) 순으로 오의게이지가 상승해요.',
        imgSrc: 'laugh2_up.png',
        savePoint: true,
        onStart: () => {
            $('.sub-command-container #chargeAttackActiveCheck').prop('checked', false).trigger('change');
        }
    },
    {
        type: 'DIALOG',
        text: '오의게이지는 체력 아래의 노란색 게이지를 통해 확인이 가능해요.',
        spotlight: '.battle-portrait .charge-gauge-wrapper .progress',
        imgSrc: 'laugh2_up.png',
    },
    {
        type: 'DIALOG',
        text: '그리고 오의게이지를 100% 이상 채우면, 100% 를 소모하여 턴 진행시 오의를 사용할 수 있습니다!',
        imgSrc: 'motivation_up.png',
    },
    {
        type: 'DIALOG',
        text: '다만, 적 역시 아군의 오의에 해당하는 특수기라는 강력한 기술을 사용할 수 있어요.',
        imgSrc: 'angry2_up.png',
    },
    {
        type: 'DIALOG',
        text: '적이 일반공격시 CT(차지 턴)가 한 칸씩 차오릅니다.',
        spotlight: '.enemy-info-container .charge-turn-container',
        imgSrc: 'weak_up.png',
    },
    {
        type: 'DIALOG',
        text: '이 CT 가 전부 차오르면, 다음 턴에 적이 "CT기" 라는 특수기를 사용해요.',
        spotlight: '.enemy-info-container .charge-turn-container',
        imgSrc: 'weak_up.png',
    },
    {
        type: 'DIALOG',
        text: '이대로면 다음 턴에 "CT기"를 사용할 것 같네요... 우선 아군 캐릭터의 오의를 사용해봐요.',
        spotlight: '.enemy-info-container .charge-turn-container',
        imgSrc: 'sad_up.png',
    },
    {
        type: 'INTERACT',
        text: '오의 사용 스위치를 클릭하여 활성화 해 보세요.',
        imgSrc: 'weak_up.png',
        target: '.charge-attack-check-wrapper #chargeAttackActiveCheck',
        waitFor: 'click',
    },
    {
        type: 'WAIT',
        waitFor: 'time',
        timeout: 1000
    },
    {
        type: 'INTERACT',
        text: '어빌리티 패널에서 오의정보도 확인이 가능해요. 확인해볼까요?',
        imgSrc: 'laugh2_up.png',
        target: '.battle-portrait.actor-1',
        waitFor: 'click',
    },
    {
        type: 'INTERACT',
        text: '오의 이름을 터치해보세요.',
        imgSrc: 'laugh2_up.png',
        target: '.slick-slide:not(.slick-cloned) .ability-panel.actor-1 .move-popover-label.charge-attack',
        waitFor: 'click',
        nextDelay: 0
    },
    {
        type: 'WAIT',
        waitFor: 'event',
        eventName: 'shown.bs.popover',
    },
    {
        type: 'DIALOG',
        text: '캐릭터가 오의를 사용하면, 오의를 사용하지 않은 나머지 캐릭터의 오의게이지가 10% 상승합니다.',
        imgSrc: 'laugh2_up.png',
        spotlight: '.command-popover',
        dialogBottom: 110,
    },
    {
        type: 'DIALOG',
        text: '이 캐릭터는 오의 효과에 10% 상승이 추가로 있으니, 나머지 캐릭터들의 오의게이지가 총 20% 상승 하겠네요!',
        imgSrc: 'motivation_up.png',
        spotlight: '.command-popover',
        dialogBottom: 110,
    },
    {
        type: 'INTERACT',
        text: '그럼 이제 오의를 사용해 볼까요?',
        imgSrc: 'laugh2_up.png',
        waitFor: 'click',
        target: '.command-popover .move-popover-close',
        dialogBottom: 110,
    },
    {
        type: 'INTERACT',
        text: '공격 버튼을 눌러 턴을 진행해봐요.',
        imgSrc: 'motivation_up.png',
        waitFor: 'click',
        target: '#attackButtonWrapper #attackButton',
    },
    {
        type: 'WAIT',
        waitFor: 'mutation',
        mutationTarget: '.turn-indicator-container',
        mutationCheck: () => {
            return $('.turn-indicator-container').hasClass('show');
        }
    },

    // 3턴
    {
        type: 'DIALOG',
        text: '특수기 전조가 발생했어요! "CT기"의 전조가 발생했네요.',
        imgSrc: 'surprise_up.png',
        dialogBottom: 200,
        savePoint: true,
        onStart: () => {
            $('.sub-command-container #chargeAttackActiveCheck').prop('checked', false).trigger('change');
        }
    },
    {
        type: 'DIALOG',
        text: '전조를 해제하지 못하면 적이 공격시 강력한 특수기를 사용해요.',
        imgSrc: 'surprise_up.png',
        dialogBottom: 200,
        onStart: () => {
            $('.sub-command-container #chargeAttackActiveCheck').prop('checked', false).trigger('change');
        }
    },
    {
        type: 'DIALOG',
        text: '하지만 전조를 해제할 경우 공격시 특수기 사용이 취소되고, 일반공격을 사용합니다.',
        imgSrc: 'angry_up.png',
        dialogBottom: 200,
    },
    {
        type: 'DIALOG',
        text: '적의 전조 정보나 부여된 상태효과는 적의 체력바를 터치하여 확인할 수 있어요.',
        imgSrc: 'angry_up.png',
        dialogBottom: 200,
    },
    {
        type: 'INTERACT',
        target: '.enemy-info-container',
        waitFor: 'click',
    },
    {
        type: 'DIALOG',
        text: '상태창의 전조 정보에서는 전조의 해제조건, 해제 실패시 발생하는 특수기의 상세정보를 확인할 수 있어요.',
        imgSrc: 'laugh2_up.png',
        dialogBottom: 100,
    },
    {
        type: 'DIALOG',
        text: '상태창에서는 그 외에도 적의 예상데미지와,',
        imgSrc: 'laugh2_up.png',
        spotlight: '#statusInfoModal .estimated-atk-wrapper',
        dialogBottom: 100,
    },
    {
        type: 'DIALOG',
        text: '부여된 상태효과를 확인할 수 있어요.',
        imgSrc: 'laugh2_up.png',
        spotlight: '#statusInfoModal .status-info.status-buff',
        dialogBottom: 100,
    },
    {
        type: 'DIALOG',
        text: '특정 전조를 발생시키는 효과, 데미지 무효화 효과 등 주요 정보를 확인할수 있으니 자주 확인해보세요.',
        imgSrc: 'motivation_up.png',
        spotlight: '#statusInfoModal .status-info.status-buff',
        dialogBottom: 100,
    },
    {
        type: 'INTERACT',
        text: '이제 전조를 해제 해볼까요?',
        imgSrc: 'angry_up.png',
        target: '.close-status-info-modal-button',
        waitFor: 'click',
        dialogBottom: 80,
    },
    {
        type: 'DIALOG',
        text: '이번 전조는 해제를 위해 지정된 히트수만큼 데미지를 발생시켜야 해요.',
        imgSrc: 'angry_up.png',
        dialogBottom: 200,
    },
    {
        type: 'INTERACT',
        text: '와무듀스의 어빌리티를 확인해볼게요.',
        imgSrc: 'angry_up.png',
        target: '.battle-portrait.actor-2',
        waitFor: 'click',
    },
    {
        type: 'INTERACT',
        target: '.slick-slide:not(.slick-cloned) .ability-panel.actor-2 .ability-icon[data-order="2"] img',
        waitFor: 'mutation',
        mutationTarget: '#abilityInfoModal',
        mutationCheck: () => {
            return $('#abilityInfoModal').hasClass('show')
        },
        nextDelay: 250,
    },
    {
        type: 'DIALOG',
        text: '와무듀스의 2번째 어빌리티를 사용하면 턴 진행시 2회 공격하며, 히트수가 크게 증가해요.',
        imgSrc: 'motivation_up.png',
        spotlight: '#abilityInfoModal .command-info-container',
        dialogBottom: 120
    },
    {
        type: 'INTERACT',
        text: '사용 버튼을 눌러 보세요.',
        imgSrc: 'motivation_up.png',
        target: '#abilityInfoModal .use-ability-button',
        waitFor: 'click',
        dialogBottom: 100
    },
    {
        type: 'WAIT',
        waitFor: 'mutation',
        mutationTarget: '#abilityRail',
        mutationCheck: () => {
            return $('#abilityRail .rail-item').length <= 0;
        },
    },

    {
        type: 'DIALOG',
        text: '어빌리티를 사용한 와무듀스의 상태 정보를 확인해 볼게요.',
        imgSrc: 'laugh2_up.png',
        target: '.battle-portrait.actor-1',
        waitFor: 'click',
    },
    {
        type: 'INTERACT',
        target: '.slick-slide:not(.slick-cloned) .ability-panel.actor-2 .show-status-info-button',
        waitFor: 'mutation',
        mutationTarget: '#statusInfoModal',
        mutationCheck: () => {
            return $('#statusInfoModal').hasClass('show')
        },
        nextDelay: 250,
    },
    {
        type: 'DIALOG',
        text: '현재 상태정보에요. 여기서 현재 적용중인 효과와 일반공격의 더블, 트리플어택 확률 등을 확인할 수 있어요.',
        imgSrc: 'laugh2_up.png',
        dialogBottom: 120
    },
    {
        type: 'DIALOG',
        text: '"반드시 트리플 어택" 효과로 인해 트리플 어택 확률이 "확정" 으로 바뀌었네요.',
        imgSrc: 'motivation_up.png',
        spotlight: '#statusInfoModal .multi-attack-rate-wrapper',
        dialogBottom: 120
    },
    {
        type: 'DIALOG',
        text: '상태 효과는 전투 상황에 큰 영향을 주니 종종 확인해보세요!',
        imgSrc: 'motivation_up.png',
        dialogBottom: 120
    },
    {
        type: 'INTERACT',
        waitFor: 'click',
        target: '#statusInfoModal .close-status-info-modal-button',
    },
    {
        type: 'INTERACT',
        text: '이제 공격 버튼을 눌러 턴을 진행해봐요.',
        imgSrc: 'motivation_up.png',
        waitFor: 'click',
        target: '#attackButtonWrapper #attackButton',
    },
    {
        type: 'WAIT',
        waitFor: 'mutation',
        mutationTarget: '.turn-indicator-container',
        mutationCheck: () => {
            return $('.turn-indicator-container').hasClass('show');
        }
    },

    // 4턴
    {
        type: 'INTERACT',
        text: '다음은 소환을 진행해볼까요? 마침 합체소환이 가능하네요. 소환 버튼을 눌러봐요.',
        imgSrc: 'laugh2_up.png',
        target: '.summon-display-button',
        waitFor: 'event',
        eventName: 'shown.bs.collapse',
        savePoint: true,
    },
    {
        type: 'DIALOG',
        text: '소환은 어빌리티와 비슷하게 데미지, 효과를 부여하며 쿨타임이 존재해요.',
        imgSrc: 'laugh2_up.png',
    },
    {
        type: 'INTERACT',
        text: '소환석을 터치해봐요.',
        imgSrc: 'laugh2_up.png',
        target: '.summon-display-wrapper .summon-list .summon-list-item:nth-child(1)',
        waitFor: 'mutation',
        mutationTarget: '#abilityInfoModal',
        mutationCheck: () => {
            return $('#abilityInfoModal').hasClass('show')
        }
    },
    {
        type: 'DIALOG',
        text: '지금처럼 다른 참전자가 소환한 뒤, 자신이 소환할 경우 합체소환을 진행할수 있고,',
        imgSrc: 'laugh2_up.png',
        dialogBottom: 290
    },
    {
        type: 'DIALOG',
        text: '이 경우 다른 참전자의 소환석 + 내 소환석 모두의 데미지와 효과를 부여할 수 있어요.',
        imgSrc: 'laugh3_up.png',
        dialogBottom: 290
    },
    {
        type: 'INTERACT',
        text: '이제 사용 버튼을 눌러 소환해봐요.',
        imgSrc: 'motivation_up.png',
        target: '#abilityInfoModal .use-ability-button',
        waitFor: 'click',
        dialogBottom: 25
    },
    {
        type: 'WAIT',
        waitFor: 'mutation',
        mutationTarget: '#abilityRail',
        mutationCheck: () => {
            return $('#abilityRail .rail-item').length <= 0;
        },
    },
    {
        type: 'DIALOG',
        text: '소환석은 주인공 (첫번째 캐릭터) 가 전투불능이 되면 사용할수 없게돼요.',
        imgSrc: 'sad2_up.png',
    },
    {
        type: 'DIALOG',
        text: '그만큼 강력한 효과를 지니고 있기 때문에 적극적으로 활용하면 전투를 유리하게 이끌어 갈 수 있을거에요.',
        imgSrc: 'laugh2_up.png',
    },
    {
        type: 'INTERACT',
        waitFor: 'click',
        target: '#attackButtonWrapper #attackButton',
    },
    {
        type: 'WAIT',
        waitFor: 'mutation',
        mutationTarget: '.turn-indicator-container',
        mutationCheck: () => {
            return $('.turn-indicator-container').hasClass('show');
        }
    },

    // 5턴
    {
        type: 'DIALOG',
        text: '이번엔 영창기 전조가 발생했네요! 전조 정보를 확인해봐요.',
        imgSrc: 'surprise_up.png',
        dialogBottom: 200,
        savePoint: true,
    },
    {
        type: 'INTERACT',
        target: '.enemy-info-container',
        waitFor: 'click',
        dialogBottom: 200,
        nextDelay: 500,
    },
    {
        type: 'DIALOG',
        text: '"인자 발생" 효과로 인해 전조가 발생했네요.',
        spotlight: '#statusInfoModal .status-info.status-buff',
        imgSrc: 'surprise_up.png',
        dialogBottom: 140,
    },
    {
        type: 'DIALOG',
        text: '"영창기"는 이처럼 특수한 효과 등의 조건을 만족한 경우 발생하는 특수기에요.',
        imgSrc: 'weak_up.png',
        dialogBottom: 140,
    },
    {
        type: 'DIALOG',
        text: '"영창기" 외에도 체력이 일정 이하로 감소하면 발생하는 "HP트리거" 특수기가 존재해요.',
        imgSrc: 'weak_up.png',
        dialogBottom: 140,
    },
    {
        type: 'DIALOG',
        text: '현재 적은 "HP트리거"가 없어 표시되지 않지만, "HP트리거"는 적의 HP바에 타이밍이 표시되어 있어요.',
        imgSrc: 'weak_up.png',
        dialogBottom: 140,
    },
    {
        type: 'DIALOG',
        text: '이렇게 다양한 적의 특수기 전조를 해제하며 적의 쓰러뜨리는것이 기본적인 공략법이에요.',
        imgSrc: 'angry_up.png',
        dialogBottom: 140,
    },
    {
        type: 'INTERACT',
        text: '다만...이번 전조는 현재 상태로선 해제가 불가능하네요. 가드를 사용하여 턴을 진행해볼께요.',
        imgSrc: 'sad_up.png',
        target: '.close-status-info-modal-button',
        waitFor: 'click',
        dialogBottom: 60,
    },
    {
        type: 'INTERACT',
        text: '가드 활성화시, 공격을 수행하지 않는 대신 방어력이 1000% 상승해요. 주인공의 가드를 활성화 해봐요.',
        imgSrc: 'weak_up.png',
        target: '.guard-button-wrapper .guard-button.party-1',
        waitFor: 'mutation',
        mutationTarget: '#battleCanvas #actorContainer',
        mutationCheck: () => {
            return $('#battleCanvas #actorContainer .actor-1 .guard-on').length >= 1;
        },
    },
    {
        type: 'INTERACT',
        text: '가드 버튼을 길게 누르면 한번에 전부 활성화 하거나 해제할 수 있어요. 모두 활성해봐요.',
        imgSrc: 'laugh2_up.png',
        target: '.guard-button-wrapper',
        waitFor: 'mutation',
        mutationTarget: '#battleCanvas #actorContainer',
        mutationCheck: () => {
            return $('#battleCanvas #actorContainer .guard-on').length >= 3;
        },
    },
    {
        type: 'INTERACT',
        text: '이제 턴을 진행해봐요.',
        imgSrc: 'angry_up.png',
        waitFor: 'click',
        target: '#attackButtonWrapper #attackButton',
    },
    {
        type: 'WAIT',
        waitFor: 'mutation',
        mutationTarget: '.turn-indicator-container',
        mutationCheck: () => {
            return $('.turn-indicator-container').hasClass('show');
        }
    },


    // 6턴
    {
        type: 'DIALOG',
        text: '좋아요. 이제 몇가지 간단한 커맨드 설명 후 튜토리얼을 종료할게요.',
        imgSrc: 'laugh2_up.png',
        savePoint: true,
    },
    {
        type: 'INTERACT',
        text: '우선 페이탈 체인 입니다. 아래의 FC 라고 쓰인 게이지를 터치해볼까요?',
        imgSrc: 'laugh2_up.png',
        target: '.advanced-command-container .fatal-chain-gauge-wrapper',
        waitFor: 'mutation',
        mutationTarget: '#abilityInfoModal',
        mutationCheck: () => {
            return $('#abilityInfoModal').hasClass('show')
        }
    },
    {
        type: 'DIALOG',
        text: '페이탈 체인 게이지는 아군이 오의를 사용할때마다 15% 씩 차올라요.',
        imgSrc: 'motivation_up.png',
        dialogBottom: 150
    },
    {
        type: 'DIALOG',
        text: '100% 를 채우면 이를 소모하여 적에게 강력한 페이탈체인 효과를 부여할수 있어요.',
        imgSrc: 'motivation_up.png',
        spotlight: '#abilityInfoModal .command-info-container',
        dialogBottom: 150
    },
    {
        type: 'INTERACT',
        text: '페이탈 체인을 사용... 하기전에 잠시 와무듀스의 서포트 어빌리티를 확인해볼까요?',
        imgSrc: 'laugh2_up.png',
        target: '#abilityInfoModal .close-ability-info-modal-button',
        waitFor: 'click',
        dialogBottom: 150,
        nextLine: 100,
    },
    {
        type: 'INTERACT',
        target: '.battle-portrait.actor-2',
        waitFor: 'click',
    },
    {
        type: 'INTERACT',
        target: '.slick-slide:not(.slick-cloned) .ability-panel.actor-2 .open-move-popover.support-ability-2',
        waitFor: 'click',
        nextDelay: 0,
    },
    {
        type: 'WAIT',
        waitFor: 'event',
        eventName: 'shown.bs.popover',
    },
    {
        type: 'DIALOG',
        text: '서포트 어빌리티는 일종의 패시브 스킬과 같은 개념이에요.',
        imgSrc: 'laugh2_up.png',
        dialogBottom: 120,
    },
    {
        type: 'DIALOG',
        text: '와무듀스의 이 서포트 어빌리티는 페이탈 체인 발동시 약화효과를 1개 없애고, 피격데미지를 감소시켜줘요.',
        imgSrc: 'laugh2_up.png',
        spotlight: '.command-popover',
        dialogBottom: 120,
    },
    {
        type: 'DIALOG',
        text: '이처럼 캐릭터는 각각 유용한 서포트 어빌리티를 갖고 있으니 종종 확인해보세요.',
        imgSrc: 'motivation_up.png',
        spotlight: '.command-popover',
        dialogBottom: 120,
    },
    {
        type: 'INTERACT',
        text: '그럼 이제 페이탈 체인을 사용해볼게요.',
        imgSrc: 'laugh_up.png',
        waitFor: 'click',
        target: '.command-popover .move-popover-close',
        dialogBottom: 120,
        delay: 100,
    },
    {
        type: 'INTERACT',
        waitFor: 'click',
        target: '#battleCanvas .ability-back-button',
    },
    {
        type: 'INTERACT',
        target: '.advanced-command-container .fatal-chain-gauge-wrapper',
        waitFor: 'mutation',
        mutationTarget: '#abilityInfoModal',
        mutationCheck: () => {
            return $('#abilityInfoModal').hasClass('show')
        },
        nextDelay: 300,
    },
    {
        type: 'INTERACT',
        imgSrc: 'shout_up.png',
        target: '#abilityInfoModal .use-ability-button',
        waitFor: 'click',
        dialogBottom: 30
    },

    {
        type: 'WAIT',
        waitFor: 'mutation',
        mutationTarget: '#abilityRail',
        mutationCheck: () => {
            return $('#abilityRail .rail-item').length <= 0;
        },
    },

    {
        type: 'INTERACT',
        text: '마지막으로 포션을 사용해봐요. 회복 버튼을 터치 해 볼게요.',
        imgSrc: 'laugh2_up.png',
        target: '.sub-command-container .potion-modal-button',
        waitFor: 'mutation',
        mutationTarget: '#potionModal',
        mutationCheck: () => {
            return $('#potionModal').hasClass('show')
        },
    },
    {
        type: 'DIALOG',
        text: '포션을 사용하여 캐릭터의 HP 를 회복하거나 부활시킬 수 있어요.',
        imgSrc: 'motivation_up.png',
        dialogBottom: 200,
    },
    {
        type: 'INTERACT',
        text: '올 포션을 사용해 캐릭터 전체의 체력을 회복해볼게요.',
        target: '#potionModal .potion-icon-wrapper[data-potion-type="ALL_POTION"]',
        waitFor: 'click',
        imgSrc: 'laugh2_up.png',
        dialogBottom: 200,
    },
    {
        type: 'INTERACT',
        target: '#potionModal #usePotionButton',
        waitFor: 'click',
    },
    {
        type: 'WAIT',
        target: '#potionModal #usePotionButton',
        waitFor: 'time',
        timeout: 2000,
    },

    {
        type: 'DIALOG',
        text: '모든 튜토리얼이 끝났어요. 수고하셧습니다!',
        imgSrc: 'laugh_up.png',
    },
    {
        type: 'INTERACT',
        text: '참고로... 이 버튼 눌러보실래요?',
        imgSrc: 'laugh2_up.png',
        target: '#battleCanvas .chat-modal-button',
        waitFor: 'mutation',
        mutationTarget: '#chatModal',
        mutationCheck: () => {
            return $('#chatModal').hasClass('show')
        },
        dialogBottom: 140
    },
    {
        type: 'DIALOG',
        text: '다른 참전자와 채팅, 스탬프를 통해 소통할수 있어요.',
        imgSrc: 'laugh2_up.png',
        dialogBottom: 120,
    },
    {
        type: 'INTERACT',
        text: '참전자 전원에게 효과가 있는 어빌리티나, 합체 소환이 필요한 경우에 편리하게 요청해보세요.',
        imgSrc: 'motivation_up.png',
        target: '#chatModal .short-message-button:nth-child(1)',
        waitFor: 'click',
        dialogBottom: 120,
    },
    {
        type: 'WAIT',
        waitFor: 'time',
        timeout: 1000,
    },
    {
        type: 'DIALOG',
        text: '이제 적을 직접 쓰러뜨려 보거나, 상단의 메뉴버튼을 눌러 튜토리얼을 종료 할 수 있어요.',
        imgSrc: 'laugh2_up.png',
    },
    {
        type: 'DIALOG',
        text: '정말 수고하셨습니다!',
        imgSrc: 'laugh_up.png',
    },
    {
        type: 'DIALOG',
        text: '- 튜토리얼 종료 -',
        savePoint: true,
    },
];