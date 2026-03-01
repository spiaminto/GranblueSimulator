$(function () {
    const tutorialData = [
        // 1턴
        [
            {
                title: "커맨드: 어빌리티",
                videoSrc: "/static/assets/video/tutorial/ability.mp4",
                descriptions: [
                    "아군 캐릭터의 초상화를 터치하면 어빌리티 슬라이더가 열립니다.",
                    "사용하고자 하는 어빌리티 아이콘을 누르면 순서대로 어빌리티 레일에 등록되고, 사용됩니다.",
                    "어빌리티 레일에 등록된 아이콘을 누르면 사용이 취소됩니다.",
                    "전투 ui 하단의 어빌리티 설명 스위치를 끄면 어빌리티 설명 팝업 표시가 생략됩니다.",
                ]
            },
            {
                title: "상태효과: 기본",
                videoSrc: "/static/assets/video/tutorial/enemy_status.mp4",
                descriptions: [
                    "어빌리티 등 커맨드는 대상에 다양한 상태 효과를 부여할 수 있습니다.",
                    "상태효과는 <span style='color:skyblue'>약화효과</span>, <span style='color:yellow'>강화효과</span> 로 구분할수 있습니다.",
                    "적에게 부여되는 상태효과중, 테두리가 원형 인것은 전투에 참여중인 참전자 모두에게 효과가 적용됩니다.(마름모는 개인)",
                    "부여된 상태효과의 상세내역은 아군의 경우 어빌리티 슬라이더 내에서, 적의 경우 체력바를 눌러 확인할 수 있습니다."
                ],
            },
            {
                title: "전투 흐름",
                videoSrc: "/static/assets/video/tutorial/turn_progress.mp4",
                descriptions: [
                    "전투는 [아군 커맨드 사용] -> [아군 공격 턴] -> [적 공격 턴] -> [턴 종료] 의 순서로 진행됩니다.",
                    "사용하고자 하는 커맨드를 모두 사용한 후 ATTACK 버튼을 누르면 [아군 공격 턴] 이 시작됩니다.",
                    "[아군 공격 턴] 에는 아군이 배치된 순서대로 공격행동을 1회 수행합니다.",
                ]
            },
            {
                title: "공격 행동: 일반공격",
                videoSrc: "/static/assets/video/tutorial/normal_attack.mp4",
                descriptions: [
                    "아군 캐릭터의 공격행동은 <strong>일반공격</strong>과 <strong>오의</strong>로 나뉩니다.",
                    "일반공격시, 캐릭터의 연속공격 확률에 따라 싱글/더블/트리플 어택을 수행합니다.",
                    "일반공격 횟수에 비례해 캐릭터의 오의 게이지가 상승합니다. (싱글 10%, 더블 22%, 트리플어택 37% 상승)",
                ],
                footer: "어빌리티를 사용하고 턴을 진행해보세요."
            },
        ],
        
        // 2턴
        [
            {
                title: "공격행동: 오의",
                videoSrc: "/static/assets/video/tutorial/charge_attack.mp4",
                descriptions: [
                    "캐릭터는 공격행동 시작시, 오의 게이지가 100% 인 경우 이를 소모하여 강력한 오의를 사용합니다.",
                    "하단 UI 의 오의 사용 스위치가 꺼져있을경우, 캐릭터는 오의 사용가능여부와 상관없이 일반공격을 수행합니다.",
                    "캐릭터 하나하나의 오의 사용여부는 설정할 수 없으며, 오의 사용 스위치가 켜진 경우 오의 사용이 가능한 캐릭터 전원이 오의를 사용합니다.",
                    "캐릭터가 오의를 사용할 경우, 아직 오의를 사용하지 않은 나머지 캐릭터의 오의게이지가 10% 상승합니다.",
                ]
            },
            {
                title: "커맨드: 페이탈 체인",
                videoSrc: "/static/assets/video/tutorial/fatal_chain.mp4",
                descriptions: [
                    "아군 캐릭터가 오의사용시 하단 UI 오른쪽의 페이탈 체인(FC) 게이지가 15% 상승합니다.",
                    "페이탈 체인 사용시 적의 피격 데미지가 증가하는 강력한 약화효과가 부여됩니다.",
                ],
                footer: "어빌리티를 사용해 오의 게이지를 채우고, 오의 사용 스위치가 켜진상태에서 턴을 진행해 오의를 사용해보세요."
            },
        ],

        // 3턴
        [
            {
                title: "적의 특수기",
                videoSrc: "/static/assets/video/tutorial/enemy_charge_attack.mp4",
                descriptions: [
                    "적은 특수기 사용 조건을 만족한 턴 시작시, 특수기 사용을 위한 전조를 발생시킵니다.",
                    "적의 전조를 해제하지 못한 경우 [적 공격 턴] 에 강력한 특수기를 사용합니다.",
                    "전조 발생시 전조의 이름과 해제조건이 화면에 표시되며, 적의 체력바를 눌러 전조와 특수기의 상세 내용을 확인할 수 있습니다.",
                ]
            },
            {
                title: "적의 특수기2",
                videoSrc: "/static/assets/video/tutorial/omen_check.mp4",
                descriptions: [
                    "적의 특수기는 [CT 기], [HP 트리거], [영창기], 3종류로 나뉩니다.",
                    "<span class='omen-text charge-attack'>[CT 기] 는 적의 체력바 아래의 CT가 모두 활성화 되면 전조를 발생시킵니다.</span>",
                    "CT(차지턴)는 적이 공격행동을 1회 수행할때마다 1개씩 증가합니다.([CT 기] 해제시 초기화)",
                    "<span class='omen-text hp-trigger'>[HP 트리거] 는 적의 체력이 일정 수치에 도달할 경우 전조를 발생시킵니다.</span>",
                    "트리거 되는 HP는 적의 체력바에 노란색으로 표시되어 있습니다.",
                    "<span class='omen-text incant-attack'>[영창기] 는 적의 상태효과 등 특수한 조건이 만족되면 전조를 발생시킵니다.</span>",
                    "일부 영창기의 발동 조건은 적의 체력바를 눌러 표시되는 적의 상태 상세내역에서 확인할 수 있습니다.",
                ]
            },
            {
                title: "커맨드: 소환석 소환",
                videoSrc: "/static/assets/video/tutorial/summon.mp4",
                descriptions: [
                    "캐릭터 초상화 오른쪽의 SUMMON 버튼을 클릭하여 소환석 리스트를 열고, 소환석을 소환할 수 있습니다.",
                    "소환석은 어빌리티와 비슷하게 데미지와 함께 상태효과를 부여할 수 있습니다.",
                ],
            },
            {
                title: "상태효과: 공격행동 불가",
                videoSrc: "/static/assets/video/tutorial/move_disable.mp4",
                descriptions: [
                    "장악, 마비 등 공격행동을 불가능 하게 만드는 효과가 적에게 부여된 경우, 적은 전조가 발생중이더라도 특수기를 사용할 수 없습니다.",
                    "이를 통해 전조를 해당 턴에 해제하지 못하더라도 다음 턴에 계속 대응이 가능합니다.",
                ],
                footer: "소환석 제우스를 소환하여 장악효과를 부여해 턴 진행시 적의 공격행동을 막아보세요."
            },
        ],

        // 4턴
        [
            {
                title: "상태효과: 추격, 난격",
                videoSrc: "/static/assets/video/tutorial/multi_hit_buff.mp4",
                descriptions: [
                    "<img src='/static/gbf/img/status/status_1383.png' width='22px'>추격 효과는 캐릭터의 일반공격에 추가데미지를 발생시킵니다.",
                    "추격 효과는 같은 이름의 항에 적용되는 경우, 중복적용되지 않습니다. (어빌리티항, 특수항...)",
                    "<img src='/static/gbf/img/status/status_7608.png' width='22px'>난격 효과는 지정됫 횟수만큼 일반공격의 데미지가 나뉘어 발생합니다.",
                    "난격 3회 효과와 1개의 추격효과가 부여된 아군이 트리플 어택시 데미지 히트수는 기존 3회에서 18회로 증가합니다.",
                ]
            },
            {
                title: "상태효과: 공격 행동",
                videoSrc: "/static/assets/video/tutorial/hit_buff.mp4",
                descriptions: [
                    "<img src='/static/gbf/img/status/status_6107.png' width='22px'>재행동, 3회행동, 4회행동 등의 효과가 부여된 캐릭터는 [아군 공격 턴] 진행시 수행하는 공격행동의 횟수가 증가합니다.",
                    "증가한 횟수의 공격행동 시작시에도 오의 사용조건이 만족되었다면 캐릭터는 오의를 사용합니다."
                ],
                footer: "아군 캐릭터의 다양한 효과를 활용하여 적의 전조를 해제해 보세요."
            },
        ],

        // 5턴
        [
            {
                title: "상태효과: 참전자 효과, 데미지컷",
                videoSrc: "/static/assets/video/tutorial/multi_buff.mp4",
                descriptions: [
                    "참전자 전체를 대상으로 효과를 부여하는 어빌리티의 경우 해당 전투에 참여중인 모든 참전자를 대상으로 효과를 부여합니다.",
                    "예시로, 팔라딘의 '팔랑크스' 어빌리티는 참전자 전체가 받는 피격데미지를 70% 컷(감소) 합니다.",
                    "특정 효과가 필요한 경우 채팅창을 열어 다른 참전자에게 효과를 요청해볼 수 있습니다.",
                ]
            },
            {
                title: "커맨드: 가드",
                videoSrc: "/static/assets/video/tutorial/guard.mp4",
                descriptions: [
                    "적의 전조를 해제할 수 없는 등의 상황에서, 가드를 활성화하여 턴을 진행할 수 있습니다.",
                    "캐릭터 초상화 아래의 가드버튼을 눌러 가드를 활성화할 경우 해당 캐릭터의 방어력이 900% 증가하지만, 턴 진행시 공격행동을 수행하지 않습니다.",
                    "가드버튼을 길게 누르면 캐릭터 전체의 가드가 활성화 또는 해제 됩니다."
                ],
                footer: "데미지컷 효과와 가드를 활성화후 턴을 진행해 적의 특수기를 맞아 보세요."
            },
        ],

        // 6턴
        [
            {
                title: "멀티플레이: 소환석 합체소환",
                videoSrc: "/static/assets/video/tutorial/union_summon.mp4",
                descriptions: [
                    "소환석 소환시 해당 소환석이 합체소환용 소환석으로 등록되며, 나머지 참전자가 합체소환할 수 있습니다.",
                    "합체소환시 자신의 소환석과 합체소환한 소환석 모두의 효과가 전부 발동됩니다.",
                    "소환석 상세 팝업에서 합체소환 스위치를 끄면 합체소환 하지 않습니다.",
                ]
            },
            // {
            //     title: "멀티플레이: 참전제 전체 영향 효과",
            //     videoSrc: "/static/gbf/video/tutorial_1.mp4",
            //     descriptions: [
            //         "적의 일부 특수기 또는 패시브 효과는 참전자 전체에게 영향을 줄 수 있습니다.",
            //         "예를들어 '자신의 모든 약화효과를 해제' 하는 경우, 참전자 전원이 해당 효과를 받을때 마다 약화효과가 모두 해제되며, 이로인해 진행도가 다른 참전자가 플레이에 영향을 받을수 있습니다.",
            //         "해당 효과 발동전 대기하거나 채팅 또는 스탬프 등으로 자신의 상황을 알려 다른 참전자의 진행을 원활히 할 수 있습니다.",
            //     ]
            // },
            {
                title: "커맨드: 포션",
                videoSrc: "/static/assets/video/tutorial/potion.mp4",
                descriptions: [
                    "하단 UI 왼쪽 회복버튼을 눌러 포션을 사용할 수 있습니다.",
                    "포션을 사용하여 아군 캐릭터의 체력을 회복할 수 있습니다.",
                ],
                footer: "튜토리얼이 끝났습니다. 턴을 진행해 전투를 완료해보세요."
            },
        ],

        
    ];

    let currentTutorialPage = 0;

    // 2. 렌더링 함수
    function renderTutorialPage(pageIndex) {
        let tutorialIndex = gameStateManager.getState('tutorialIndex');

        // 방어적 코드: 인덱스가 범위를 벗어날 경우 에러 방지
        if (!tutorialData[tutorialIndex] || !tutorialData[tutorialIndex][pageIndex]) {
            console.error('[renderTutorialPage] 유효하지 않은 튜토리얼 데이터 인덱스입니다.');
            return;
        }

        const data = tutorialData[tutorialIndex][pageIndex];
        const totalPages = tutorialData[tutorialIndex].length;

        // 헤더 갱신
        $('#tutorialModalTitle').text(`[${pageIndex + 1}/${totalPages}] ${data.title}`);

        // --- 1. 템플릿 리터럴을 이용한 HTML 문자열 조립 ---

        // descriptions 배열을 <li> 문자열로 변환 후 결합 (.text() 제거로 HTML 렌더링 허용)
        const descriptionsHtml = data.descriptions
            .map(desc => `<li>${desc}</li>`)
            .join('');

        // footer가 존재할 경우에만 <li> 추가
        const footerHtml = data.footer
            ? `<li style="font-weight: bold;">${data.footer}</li>`
            : '';

        // 전체 본문 구조 조립
        const contentHtml = `
        <div class="tutorial-video-wrapper">
            <video id="tutorialVideo" src="${data.videoSrc}" autoplay muted loop playsinline></video>
            <div class="tutorial-video-progress-bg">
                <div class="tutorial-video-progress-bar" id="tutorialVideoProgress"></div>
            </div>
        </div>
        <ul>
            ${descriptionsHtml}
            ${footerHtml}
        </ul>
    `;

        // --- 2. DOM 업데이트 및 애니메이션 제어 ---
        const $wrapper = $('#tutorialContentWrapper').removeClass('tutorial-page-active');

        // 단 한 번의 DOM 주입
        $wrapper.html(contentHtml);

        // 애니메이션 재시작을 위한 리플로우 강제 트리거
        void $wrapper[0].offsetWidth;
        $wrapper.addClass('tutorial-page-active');

        // --- 3. 이벤트 바인딩 및 UI 제어 ---
        // 비디오 진행도 실시간 반영 리스너 (제이쿼리로 통일하여 메모리 릭 방지)
        $('#tutorialVideo').on('timeupdate', function () {
            if (this.duration) {
                const progressPercent = (this.currentTime / this.duration) * 100;
                $('#tutorialVideoProgress').css('width', `${progressPercent}%`);
            }
        });

        // 푸터 버튼 상태 제어
        if (pageIndex >= totalPages - 1) {
            $('#tutorialNextBtn').hide();
            $('#tutorialConfirmBtn').show().prop('disabled', false);
        } else {
            $('#tutorialNextBtn').show();
            $('#tutorialConfirmBtn').hide().prop('disabled', true);
        }

        // 새 페이지 로드 시 모달 스크롤 맨 위로
        $('#tutorialModal .modal-body').scrollTop(0);
    }

    // 3. 이벤트 리스너 등록
    $('#tutorialNextBtn').on('click', function () {
        let tutorialIndex = gameStateManager.getState('tutorialIndex');
        if (currentTutorialPage < tutorialData[tutorialIndex].length - 1) {
            currentTutorialPage++;
            renderTutorialPage(currentTutorialPage);
        }
    });

    $('#tutorialBeforeBtn').on('click', function () {
        if (currentTutorialPage > 0) {
            currentTutorialPage--;
            renderTutorialPage(currentTutorialPage);
        }
    });

    // 모달이 열릴 때 무조건 첫 페이지로 초기화
    $('#tutorialModal').on('show.bs.modal', function () {
        currentTutorialPage = 0;
        renderTutorialPage(currentTutorialPage);
    });

    // 모달이 닫힐 때 비디오 메모리 해제 및 정지
    $('#tutorialModal').on('hidden.bs.modal', function () {
        const videoElement = document.getElementById('tutorialVideo');
        if (videoElement) {
            videoElement.pause();
            videoElement.removeAttribute('src'); // 리소스 다운로드 즉시 중단
            videoElement.load();
        }
        $('#tutorialContentWrapper').empty();
    });



});