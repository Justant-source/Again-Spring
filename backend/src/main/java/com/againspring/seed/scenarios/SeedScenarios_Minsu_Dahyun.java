package com.againspring.seed.scenarios;

import com.againspring.seed.dto.SeedMessage;
import com.againspring.seed.dto.SeedReport;
import com.againspring.seed.dto.SeedScenario;
import java.util.List;

/**
 * V4 시드 데이터: 민수(남성) + 다현(여성) 시나리오
 * - 민수: 직장 동료 갑질, 사업 동업, 막 시작한 1턴, 명시 종료, 카드빚 재정 문제
 * - 다현: 위기 자해 키워드, 부모 게임시간, 4턴 진행 중, 4단계 풀 진로 상담
 * 각 인물별 5개 시나리오 (총 9개)
 * 참조: shared/docs/categories.yml
 */
public class SeedScenarios_Minsu_Dahyun {
    public static List<SeedScenario> get() {
        return List.of(
            // ========== 민수 (test5@again.com) ==========

            // S15 — 직장 동료 갑질 Solo 완료
            new SeedScenario(
                "S15",
                "test5@again.com",
                "FRIEND",
                "friend",
                "friend_words",
                "custom",
                "COMPLETED",
                true,
                17280,  // 12일 전
                null,
                null,
                true,
                true,
                false,
                List.of(),
                null,
                List.of(
                    new SeedMessage("USER_A", "팀장이 회의에서 내 발표를 계속 끊어. 다른 팀원 앞에서.", 0, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "여러 사람 앞에서 반복되는 상황이라면 위축되기 쉽겠어요. 언제부터 이런 패턴이 생겼어요?", 1, false, false, null),
                    new SeedMessage("USER_A", "입사하고 나서 계속 그랬어. 나만 그런 것 같아.", 3, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "나만 이런 대우를 받는 것 같다는 생각이 드셨군요. 그게 어떤 느낌으로 오나요?", 4, false, false, null),
                    new SeedMessage("USER_A", "무시당하는 기분. 근데 항의할 수 있는 상황이 아냐.", 6, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "항의가 어려운 구조에서 느끼는 무력감이 있으신 것 같아요. 지금 가장 원하는 게 뭐예요?", 7, false, false, null),
                    new SeedMessage("USER_A", "적어도 공개적으로 창피당하지 않았으면.", 9, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "최소한의 존중을 바라시는 거네요. 이 상황에서 현실적으로 해볼 수 있는 방법 몇 가지가 있어요. 첫째, 팀장에게 1:1로 \"발표 중 끊기면 집중이 어렵다\"고 조심스럽게 말해보기. 둘째, HR이나 상위 관리자에게 상황을 기록해두기. 셋째, 지금은 상황을 메모해두면서 때를 살피기. 어느 쪽이 지금 상황에 맞을 것 같으세요?", 10, false, false, null),
                    new SeedMessage("USER_A", "첫번째는 역효과 날 것 같고. 두번째를 해볼게.", 12, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "기록을 남기는 건 냉정하면서도 현명한 선택이에요.", 13, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "이만큼 이야기 나눠주셔서 고마워요. 지금까지 정리해보면 어떨까요?", 14, true, false, null)
                ),
                new SeedReport(
                    3, 2, 4, 4,
                    List.of("상황 기록하기", "1:1 대화 준비하기"),
                    "팀장이 회의에서 발표를 반복적으로 끊었어",
                    "여러 사람 앞에서 무시당하는 기분이 들었어",
                    "최소한의 존중과 공정한 대우를 받고 싶어",
                    "발표 중에는 끝까지 들어줄 수 있을까요?",
                    null, null, null, null,
                    "회의에서 반복적으로 끊기는 게 무시당하는 기분이었어. 최소한 발표가 끝날 때까지 들어줬으면 해. 1:1로 이 부분을 전달해볼게.",
                    null, null,
                    "DIFFERENCE"
                )
            ),

            // S20 — 사업 동업 Duo 완료
            new SeedScenario(
                "S20",
                "test5@again.com",
                "FRIEND",
                "friend",
                "friend_money",
                "loan",
                "COMPLETED",
                false,
                28800,  // 20일 전
                25L,  // 세션 시작 25분 후
                "친구",
                true,
                true,
                true,
                List.of(),
                null,
                List.of(
                    new SeedMessage("USER_A", "친구랑 같이 시작한 사업이 흔들리고 있어. 역할 분담이 안 돼.", 0, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "함께 시작한 일인데 기대와 다른 상황이 생긴 거군요. 어떤 부분이 가장 어긋났어요?", 1, false, false, null),
                    new SeedMessage("USER_A", "내가 실무를 다 하는데 친구는 아이디어만 내고 끝이야.", 3, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "기여 방식의 차이가 불공평하게 느껴지시는 거군요.", 4, false, false, null),
                    new SeedMessage("USER_A", "맞아. 근데 원래부터 역할을 명확히 안 정했어.", 6, false, false, null),
                    new SeedMessage("MEDIATOR_TO_B", "함께 와주셔서 고마워요. 상대방이 적으신 내용은 따로 듣고 있어요. 천천히 이야기해주세요.", 25, false, true, null),
                    new SeedMessage("USER_B", "저도 실무를 하고 싶은데 기회를 안 줘요.", 26, false, false, null),
                    new SeedMessage("MEDIATOR_TO_B", "참여하고 싶은데 여지가 없다는 답답함이 있으신 것 같아요.", 27, false, false, null),
                    new SeedMessage("USER_B", "제가 아이디어를 내면 무시하거나 혼자 결정해버려요.", 29, false, false, null),
                    new SeedMessage("MEDIATOR_TO_B", "의견이 반영되지 않는 경험이 반복됐군요. 그게 어떻게 느껴졌어요?", 30, false, false, null),
                    new SeedMessage("USER_A", "무시한 게 아니라 속도가 느려서 먼저 한 거야.", 32, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "이만큼 이야기 나눠주셔서 고마워요. 지금까지 정리해보면 어떨까요?", 34, true, false, null),
                    new SeedMessage("MEDIATOR_TO_B", "이만큼 이야기 나눠주셔서 고마워요. 지금까지 정리해보면 어떨까요?", 34, true, false, null)
                ),
                new SeedReport(
                    4, 2, 3, 3,
                    List.of("역할 분담 문서로 명확히 정하기", "정기 회의 만들기"),
                    "내가 실무 대부분을 맡아왔어",
                    "기여가 불공평하다는 생각에 지치고 억울해",
                    "각자 역할을 명확히 나눠서 공평하게 일하고 싶어",
                    "역할과 기여 방식을 문서로 정리해볼 수 있을까?",
                    "아이디어를 내도 무시되거나 혼자 결정이 됐어",
                    "참여하고 싶은데 소외되는 것 같아 답답해",
                    "나도 의사결정에 참여하고 싶어",
                    "중요한 결정 전에 미리 나한테도 물어봐줄 수 있어?",
                    "서로 역할이 불명확해서 생긴 오해가 쌓인 것 같아. 각자 뭘 할지 문서로 한번 정리하면 훨씬 나아질 것 같아. 정기 회의를 만들어서 진행 상황을 공유하면 어떨까?",
                    55, 45,
                    "DIFFERENCE"
                )
            ),

            // S23 — 막 시작한 1턴 Solo
            new SeedScenario(
                "S23",
                "test5@again.com",
                "FRIEND",
                "friend",
                "friend_trust",
                "break_promise_loop",
                "CHATTING_SOLO",
                true,
                30,  // 30분 전
                null,
                null,
                false,
                false,
                false,
                List.of(),
                null,
                List.of(
                    new SeedMessage("USER_A", "친한 친구한테 빌려준 돈을 두 달째 못 받고 있어.", 0, false, false, null)
                ),
                null
            ),

            // S28 — TERMINATED 사용자 명시 종료
            new SeedScenario(
                "S28",
                "test5@again.com",
                "FRIEND",
                "friend",
                "friend_values",
                "custom",
                "TERMINATED",
                true,
                43200,  // 30일 전
                null,
                null,
                false,
                false,
                false,
                List.of(),
                null,
                List.of(
                    new SeedMessage("USER_A", "오래된 친구랑 가치관이 너무 달라졌어.", 0, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "오랜 시간을 함께한 사람인데 거리가 느껴지는 게 아쉬우셨겠어요. 어떤 부분이 달라진 것 같아요?", 1, false, false, null),
                    new SeedMessage("USER_A", "돈이랑 시간 쓰는 방식이 너무 달라. 같이 있으면 불편해.", 3, false, false, null),
                    new SeedMessage("USER_A", "그냥 됐어. 생각해도 답이 없네.", 5, false, false, null)
                ),
                null
            ),

            // S30 — 재정 1차 조언 Solo 완료
            new SeedScenario(
                "S30",
                "test5@again.com",
                "FAMILY",
                "family",
                "family_money",
                "loan",
                "COMPLETED",
                true,
                38880,  // 27일 전
                null,
                null,
                true,
                true,
                false,
                List.of(),
                null,
                List.of(
                    new SeedMessage("USER_A", "카드빚이 쌓였어. 매달 최소 납부만 하다 보니까 원금이 줄지 않아.", 0, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "매달 반복되는 부담이 쌓이는 상황이군요. 지금 가장 무거운 게 어떤 부분이에요?", 1, false, false, null),
                    new SeedMessage("USER_A", "이자가 너무 높아. 어떻게 해야 할지 모르겠어.", 3, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "이자 부담이 출구가 없는 것처럼 느껴지는 거군요. 금액은 감당 가능한 범위 안인가요?", 4, false, false, null),
                    new SeedMessage("USER_A", "아직은 버틸 수 있는데 계속 이러면 안 될 것 같아.", 6, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "지금 바로 해결할 수 있는 현실적인 방법 몇 가지 있어요. 첫째, 카드사에 직접 연락해 저금리 전환이나 분할 납부 협의 요청하기. 둘째, 신용회복위원회(1600-5500)에서 무료 채무 상담을 받아볼 수 있어요. 셋째, 급전 없이 이자율이 높은 카드부터 집중 상환하는 방식도 있어요. 어느 방향이 맞을 것 같으세요?", 7, false, false, null),
                    new SeedMessage("USER_A", "신용회복위원회는 신용에 문제 생기는 거 아냐?", 9, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "상담만 받는 건 신용에 영향이 없어요. 실제 프로그램 등록은 별개로 선택할 수 있어요.", 10, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "이만큼 이야기 나눠주셔서 고마워요. 지금까지 정리해보면 어떨까요?", 11, true, false, null)
                ),
                new SeedReport(
                    5, 3, 3, 4,
                    List.of("신용회복위원회 상담 요청"),
                    "카드 이자가 매달 쌓여서 원금이 줄지 않아",
                    "출구가 없는 것 같아 막막하고 무기력해",
                    "이 상황에서 현실적인 방법을 찾고 싶어",
                    "신용회복위원회에 무료 상담 전화를 해볼게요.",
                    null, null, null, null,
                    "매달 최소 납부만 하다 보니 이자가 계속 쌓여서 답답했어. 신용회복위원회(1600-5500) 무료 상담이 현실적인 첫걸음이 될 것 같아.",
                    null, null,
                    "FACTUAL"
                )
            ),

            // ========== 다현 (test6@again.com) ==========

            // S08 — TERMINATED 위기 차단, 자해 키워드
            new SeedScenario(
                "S08",
                "test6@again.com",
                "PARENT_CHILD",
                "parent_child",
                "pc_career",
                "custom",
                "TERMINATED",
                true,
                4320,  // 3일 전
                null,
                null,
                false,
                false,
                false,
                List.of("self_harm"),
                null,
                List.of(
                    new SeedMessage("USER_A", "엄마랑 진로 문제로 또 싸웠어. 의대 가라는데 난 미술 하고 싶어.", 0, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "본인이 원하는 길과 부모님이 원하는 길이 갈리는 게 무겁네요. 미술을 하고 싶다는 마음이 어디서부터 시작됐어요?", 1, false, false, null),
                    new SeedMessage("USER_A", "그냥 다 죽고 싶다는 생각이 들어.", 2, false, false, 1)
                ),
                null
            ),

            // S16 — 부모 게임시간 갈등 Solo 완료
            new SeedScenario(
                "S16",
                "test6@again.com",
                "PARENT_CHILD",
                "parent_child",
                "pc_autonomy",
                "custom",
                "COMPLETED",
                true,
                11520,  // 8일 전
                null,
                null,
                true,
                true,
                false,
                List.of(),
                null,
                List.of(
                    new SeedMessage("USER_A", "부모님이 게임 그만하라고 매일 싸워. 공부하라고 잔소리야.", 0, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "매일 같은 갈등이 반복되니 피곤하셨겠어요. 부모님이 제일 걱정하시는 부분이 뭔지 알 것 같아요?", 1, false, false, null),
                    new SeedMessage("USER_A", "성적 떨어진다고. 근데 나는 게임이 스트레스 푸는 거야.", 3, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "스트레스를 풀고 싶은 마음과 성적에 대한 걱정이 부딪히는 거군요.", 4, false, false, null),
                    new SeedMessage("USER_A", "하루에 한 시간도 안 되는데 너무하지 않아?", 6, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "한 시간도 안 된다는 게 많이 억울하게 느껴지는 거네요. 부모님께 그 마음을 어떻게 전달해봤어요?", 7, false, false, null),
                    new SeedMessage("USER_A", "말해봤는데 그냥 안 된다고만 해.", 9, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "이만큼 이야기 나눠주셔서 고마워요. 지금까지 정리해보면 어떨까요?", 10, true, false, null)
                ),
                new SeedReport(
                    2, 1, 3, 5,
                    List.of(),
                    "매일 게임 그만하라는 잔소리를 들어",
                    "이해받지 못하고 억울해",
                    "스트레스를 풀 수 있는 나만의 공간이 필요해",
                    "하루에 1시간은 게임할 수 있게 해줄 수 있어요?",
                    null, null, null, null,
                    "게임이 스트레스를 푸는 유일한 방법인데 그것도 못 하게 하면 너무 힘들어. 하루 1시간은 내 시간으로 가질 수 있으면 좋겠어.",
                    null, null,
                    "DIFFERENCE"
                )
            ),

            // S24 — 4턴까지 진행 중 Solo
            new SeedScenario(
                "S24",
                "test6@again.com",
                "PARENT_CHILD",
                "parent_child",
                "pc_career",
                "custom",
                "CHATTING_SOLO",
                true,
                1440,  // 1일 전
                null,
                null,
                false,
                false,
                false,
                List.of(),
                null,
                List.of(
                    new SeedMessage("USER_A", "부모님이 원하는 대학교랑 내가 원하는 대학교가 달라.", 0, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "원하는 방향이 달라서 갈등이 생기고 있군요. 어떤 대학교를 원하시는 거예요?", 1, false, false, null),
                    new SeedMessage("USER_A", "나는 예술대 가고 싶은데 부모님은 취업 잘 되는 데 가야 한대.", 3, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "하고 싶은 것과 현실적인 것 사이에서 끼인 것 같아요. 예술을 하고 싶다는 마음이 얼마나 강해요?", 4, false, false, null)
                ),
                null
            ),

            // S31 — 4단계 모델 풀 시나리오 Solo 완료
            new SeedScenario(
                "S31",
                "test6@again.com",
                "PARENT_CHILD",
                "parent_child",
                "pc_career",
                "custom",
                "COMPLETED",
                true,
                20160,  // 14일 전
                null,
                null,
                true,
                true,
                false,
                List.of(),
                null,
                List.of(
                    new SeedMessage("USER_A", "미술 하고 싶다고 했더니 엄마가 절대 안 된다고 했어.", 0, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "원하는 것에 바로 반대부터 들으셨군요. 그 말 들었을 때 어떤 마음이었어요?", 1, false, false, null),
                    new SeedMessage("USER_A", "화나고 억울했어. 왜 내 의견은 중요하지 않은 거야.", 3, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "내 의견이 중요하게 다뤄지지 않는다는 느낌이 많이 상처였겠어요.", 4, false, false, null),
                    new SeedMessage("USER_A", "그냥 한 번이라도 진지하게 들어줬으면.", 6, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "진지하게 들어주는 것, 그 자체가 얼마나 바라는 것인지가 느껴져요. 그 바람 뒤에는 어떤 마음이 있을까요?", 7, false, false, null),
                    new SeedMessage("USER_A", "엄마한테 인정받고 싶어. 내 꿈이 말이 된다고.", 9, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "인정받고 싶은 마음이 있군요. 지금 이 상황에서 현실적으로 해볼 수 있는 방법 몇 가지 있어요. 첫째, 미술로 진로를 잡은 사람들의 실제 사례를 찾아서 엄마한테 보여주기. 둘째, '내가 왜 원하는지'를 글로 정리해서 차분히 대화 요청하기. 셋째, 지금 당장 결론 내지 말고 둘 다 조사해보기로 시간 벌기. 어느 방법이 맞을 것 같아요?", 10, false, false, null),
                    new SeedMessage("USER_A", "두번째 해봤는데 엄마가 감정적으로 됐어.", 12, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "솔직하게 이야기했는데 감정적인 반응을 받은 게 또 상처였겠어요. 그 다음에 어떻게 하셨어요?", 13, false, false, null),
                    new SeedMessage("USER_A", "그냥 포기하려다가 다시 생각해보고 싶어.", 15, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "포기하지 않고 다시 생각해보고 싶다는 마음이 있군요. 그 마음이 사라지지 않는 이유가 있을까요?", 16, false, false, null),
                    new SeedMessage("USER_A", "그게 진짜 내 꿈인 것 같아서.", 18, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "이만큼 이야기 나눠주셔서 고마워요. 지금까지 정리해보면 어떨까요?", 19, true, false, null)
                ),
                new SeedReport(
                    3, 1, 4, 3,
                    List.of("사례 찾아서 보여주기", "글로 정리해서 대화 요청", "시간 두고 조사 먼저 해보기"),
                    "미술 진로를 말했을 때 엄마가 절대 안 된다고 했어",
                    "내 의견이 중요하지 않은 것 같아 억울하고 상처받았어",
                    "내 꿈을 진지하게 들어주고 인정받고 싶어",
                    "감정 없이 한 번만 진지하게 이야기해줄 수 있어요?",
                    null, null, null, null,
                    "미술이 진짜 내 꿈인 것 같아서 포기가 안 돼. 엄마한테 인정받고 싶은 마음도 있어. 실제 미술 진로 사례를 찾아서 차분히 대화를 다시 요청해볼게.",
                    null, null,
                    "DIFFERENCE"
                )
            )
        );
    }
}
