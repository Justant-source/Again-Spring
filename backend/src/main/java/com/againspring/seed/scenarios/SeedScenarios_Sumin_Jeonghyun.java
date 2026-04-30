package com.againspring.seed.scenarios;

import com.againspring.seed.dto.SeedMessage;
import com.againspring.seed.dto.SeedReport;
import com.againspring.seed.dto.SeedScenario;
import java.util.List;

/**
 * V4 시드 데이터: 수민(여성) + 정현(여성) 시나리오
 * - 수민: 친구 절교 후회, 남친 신뢰 의심, 커플 시간 분담, SNS 질투, 룸메이트 갈등, 폭력 위기
 * - 정현: 시댁 차별, 동생 돈 빌려달라, 시어머니 봉양, 동생과 봉양 분담, 형의 무책임, 남편 소통 부족
 * 각 인물별 12개 시나리오 (총 12개)
 * 참조: shared/docs/categories.yml
 */
public class SeedScenarios_Sumin_Jeonghyun {
    public static List<SeedScenario> get() {
        return List.of(
            // ========== 수민 (test3@again.com) ==========

            // S02 — 친구 절교 후회 Solo 완료
            new SeedScenario(
                "S02",
                "test3@again.com",
                "FRIEND",
                "friend",
                "friend_trust",
                "secrets",
                "COMPLETED",
                true,
                7200,  // 5일 전
                null,
                null,
                true,
                true,
                false,
                List.of(),
                null,
                List.of(
                    new SeedMessage("USER_A", "10년지기 친구랑 절교했어. 후회되긴 하는데 미안하다는 말은 못하겠어.", 0, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "10년이라는 시간을 함께 했던 분이라면, 결정 자체가 가벼웠을 리 없어요. 절교를 결심하게 된 일이 있었어요?", 1, false, false, null),
                    new SeedMessage("USER_A", "내 비밀을 다른 친구한테 말했더라고. 작은 거였지만 신뢰가 깨졌어.", 3, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "비밀이 작더라도 신뢰가 깨지는 건 무게가 다르죠. 후회되시는 마음은 어떤 부분이세요?", 4, false, false, null),
                    new SeedMessage("USER_A", "너무 차갑게 끊었나 싶어. 한 번 더 기회를 줄 수도 있었는데.", 6, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "결정의 강도와 마음의 강도가 다를 수 있어요. 절교는 했지만 마음 한구석에는 그 친구를 향한 정이 남아있는 거네요.", 7, false, false, null),
                    new SeedMessage("USER_A", "맞아. 어떻게 해야 해?", 9, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "지금 떠오르는 방법 몇 가지 있어요. 첫째, 짧은 메시지 하나로 마음을 전하기. 둘째, 공통 친구를 통해 근황을 자연스럽게 듣기. 셋째, 시간을 더 두고 마음이 정리된 후에 결정하기. 어느 쪽이 마음에 와닿으세요?", 10, false, false, null),
                    new SeedMessage("USER_A", "첫번째가 좋을 것 같은데 너무 가벼워보이지 않을까?", 12, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "가벼워 보일까봐 망설이시는 마음이 보여요. 진심이 담긴 한 줄은 길이와 상관없이 무겁게 닿을 수 있어요.", 13, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "이만큼 이야기 나눠주셔서 고마워요. 지금까지 정리해보면 어떨까요?", 14, true, false, null)
                ),
                new SeedReport(
                    3, 1, 4, 5,
                    List.of("짧은 메시지로 마음 전하기", "공통 친구 통해 근황 듣기", "좀 더 시간 두기"),
                    "내 비밀을 다른 친구에게 말한 걸 알게 됐어",
                    "신뢰가 깨졌다는 느낌에 너무 차갑게 끊었어",
                    "사실은 다시 회복되고 싶은 마음이 있어",
                    "한 번 짧게라도 만나서 이야기해볼 수 있을까?",
                    null, null, null, null,
                    "비밀을 말한 게 작은 일이었어도 신뢰가 깨지는 건 달랐어. 너무 차갑게 끊었나 싶고 후회가 됐어. 진심이 담긴 짧은 메시지라도 한 번 보내볼까?",
                    null, null,
                    "MIXED"
                )
            ),

            // S07 — AWAITING_FINALIZATION (수민 + 남친 게스트)
            new SeedScenario(
                "S07",
                "test3@again.com",
                "COUPLE",
                "couple",
                "couple_trust",
                "suspicion_other",
                "AWAITING_FINALIZATION",
                false,
                120,  // 2시간 전
                15L,  // 세션 시작 후 15분
                "남친",
                true,
                true,
                false,
                List.of(),
                null,
                List.of(
                    new SeedMessage("USER_A", "남친 SNS에 모르는 여자가 댓글 달았어. 뭔가 이상해.", 0, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "신경이 쓰이는 부분이 있으신 것 같아요. 어떤 점이 이상하게 느껴졌어요?", 1, false, false, null),
                    new SeedMessage("USER_A", "친근하게 댓글 다는 게 좀 많아. 물어봤더니 그냥 친구래.", 3, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "그냥 친구라는 말에 완전히 안심이 되지 않는 거군요.", 4, false, false, null),
                    new SeedMessage("USER_A", "맞아. 예전에도 한 번 비슷한 일이 있었거든.", 6, false, false, null),
                    new SeedMessage("MEDIATOR_TO_B", "함께 와주셔서 고마워요. 상대방이 적으신 내용은 따로 듣고 있어요. 천천히 이야기해주세요.", 15, false, true, null),
                    new SeedMessage("USER_B", "그냥 오래된 친구인데 왜 의심하는지 이해가 안 돼요.", 16, false, false, null),
                    new SeedMessage("MEDIATOR_TO_B", "억울하고 답답하신 마음이 있으신 것 같아요. 상대방에게 어떻게 이해받고 싶으세요?", 17, false, false, null),
                    new SeedMessage("USER_A", "의심하는 게 아니라 불안한 거야.", 19, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "이만큼 이야기 나눠주셔서 고마워요. 지금까지 정리해보면 어떨까요?", 20, true, false, null),
                    new SeedMessage("MEDIATOR_TO_B", "이만큼 이야기 나눠주셔서 고마워요. 지금까지 정리해보면 어떨까요?", 20, true, false, null)
                ),
                null
            ),

            // S13 — 4단계 풀 시나리오 Solo 완료
            new SeedScenario(
                "S13",
                "test3@again.com",
                "COUPLE",
                "couple",
                "couple_time",
                "friends_first",
                "COMPLETED",
                true,
                8640,  // 6일 전
                null,
                null,
                true,
                true,
                false,
                List.of(),
                null,
                List.of(
                    new SeedMessage("USER_A", "남친이 주말마다 친구들이랑만 놀아. 나한테는 시간이 없대.", 0, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "주말이 소중한 시간인데 계속 빠지는 게 서운하셨겠어요. 가장 속상했던 순간이 언제였어요?", 1, false, false, null),
                    new SeedMessage("USER_A", "지난 주에도 약속 잡아놨다가 친구 생일이라고 취소했어.", 3, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "약속을 먼저 잡아놓고 취소된 거라면 더 힘들었겠어요.", 4, false, false, null),
                    new SeedMessage("USER_A", "사실 그냥 나도 우선순위에 있고 싶어.", 6, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "우선순위에 있고 싶은 마음이 자연스럽게 드는 거네요. 그 마음 뒤에는 어떤 바람이 있을까요?", 7, false, false, null),
                    new SeedMessage("USER_A", "그냥 나를 소중하게 여겨주는 게 느껴지면 좋겠어.", 9, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "지금 이 상황에서 떠오르는 방법 몇 가지 있어요. 첫째, 직접 '나도 중요한 사람으로 느껴지고 싶다'고 솔직하게 말해보기. 둘째, 주말 중 하루만 둘이 보내는 날로 미리 잡기. 셋째, 지금은 남친의 우선순위가 어디 있는지 좀 더 지켜보기. 어느 쪽이 와닿으세요?", 10, false, false, null),
                    new SeedMessage("USER_A", "첫번째 해봤는데 남친이 예민하다고 했어.", 12, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "솔직하게 말했는데 예민하다는 반응을 들었다면 많이 상처받으셨겠어요. 그 말이 남아있는 것 같아요.", 13, false, false, null),
                    new SeedMessage("USER_A", "응. 그래서 말하기가 더 어려워.", 15, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "이만큼 이야기 나눠주셔서 고마워요. 지금까지 정리해보면 어떨까요?", 16, true, false, null)
                ),
                new SeedReport(
                    4, 2, 4, 3,
                    List.of("솔직한 대화 다시 시도하기"),
                    "주말 약속이 친구 일정으로 자주 취소됐어",
                    "우선순위에서 밀리는 것 같아 속상하고 외로웠어",
                    "나를 소중하게 여겨준다는 걸 느끼고 싶어",
                    "주말 중 하루는 우리 둘만의 날로 미리 잡아줄 수 있어?",
                    null, null, null, null,
                    "주말 약속이 계속 취소되면서 우선순위에서 밀리는 것 같아 외로웠어. 나를 소중하게 여겨준다는 게 느껴지면 좋겠어. 주말 중 하루는 미리 우리 둘만의 날로 잡아줄 수 있어?",
                    null, null,
                    "DIFFERENCE"
                )
            ),

            // S18 — SNS 질투 Solo 완료
            new SeedScenario(
                "S18",
                "test3@again.com",
                "COUPLE",
                "couple",
                "couple_contact",
                "sns_friction",
                "COMPLETED",
                true,
                25200,  // 17.5일 전 (약 2.5주)
                null,
                null,
                true,
                true,
                false,
                List.of(),
                null,
                List.of(
                    new SeedMessage("USER_A", "남친 인스타에 이성 팔로워가 너무 많아. 신경 쓰여.", 0, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "신경이 쓰이는 마음이 드셨군요. 어떤 부분이 제일 마음에 걸려요?", 1, false, false, null),
                    new SeedMessage("USER_A", "모르는 여자들이 '좋아요' 누르고 댓글도 달고. 나는 별로 없는데.", 3, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "비교하는 마음이 드셨군요. 그 차이가 어떤 감정으로 이어지나요?", 4, false, false, null),
                    new SeedMessage("USER_A", "불안해. 내가 부족한가 싶고.", 6, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "불안함 뒤에 '나는 충분한가'라는 질문이 있는 것 같아요. 그 질문은 사실 SNS가 아니라 관계에서 오는 것 같은데, 어떻게 생각하세요?", 7, false, false, null),
                    new SeedMessage("USER_A", "맞는 것 같아. 남친이 날 좋아하는 게 맞나 싶어.", 9, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "이만큼 이야기 나눠주셔서 고마워요. 지금까지 정리해보면 어떨까요?", 10, true, false, null)
                ),
                new SeedReport(
                    2, 1, 5, 2,
                    List.of("남친에게 불안한 마음 솔직하게 말해보기"),
                    "남친 SNS에 이성 팔로워와 댓글이 많아",
                    "내가 부족한가 싶고 불안해",
                    "남친과의 관계에서 안심하고 싶어",
                    "나를 소중하게 여기는 마음을 가끔 표현해줄 수 있어?",
                    null, null, null, null,
                    "인스타 댓글이 신경 쓰이는 게 사실은 관계에 대한 불안이었어. 내가 충분한지 모르겠다는 마음이 드는 거야. 가끔이라도 나를 소중히 여긴다는 걸 보여줄 수 있어?",
                    null, null,
                    "DIFFERENCE"
                )
            ),

            // S22 — 룸메이트 Duo 완료
            new SeedScenario(
                "S22",
                "test3@again.com",
                "FRIEND",
                "friend",
                "friend_social",
                "custom",
                "COMPLETED",
                false,
                14400,  // 10일 전
                20L,  // 세션 시작 후 20분
                "룸메이트",
                true,
                true,
                true,
                List.of(),
                null,
                List.of(
                    new SeedMessage("USER_A", "룸메랑 생활 패턴이 너무 달라. 밤에 시끄럽게 해서 잠을 못 자.", 0, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "생활 리듬이 맞지 않아 피로가 쌓이셨겠어요. 가장 불편한 게 어떤 부분이에요?", 1, false, false, null),
                    new SeedMessage("USER_A", "밤 12시 넘어서 통화하고 음악도 틀어. 나는 일찍 자야 하는데.", 3, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "수면이 영향받는 게 제일 힘든 부분이군요.", 4, false, false, null),
                    new SeedMessage("USER_A", "맞아. 말은 했는데 또 그래.", 5, false, false, null),
                    new SeedMessage("MEDIATOR_TO_B", "함께 와주셔서 고마워요. 상대방이 적으신 내용은 따로 듣고 있어요. 천천히 이야기해주세요.", 20, false, true, null),
                    new SeedMessage("USER_B", "저도 배려하려고 하는데 기준이 다른 것 같아요.", 21, false, false, null),
                    new SeedMessage("MEDIATOR_TO_B", "기준이 달라서 생기는 오해인 것 같군요. 어떤 기준이 불편하게 느껴지셨어요?", 22, false, false, null),
                    new SeedMessage("USER_B", "밤 11시면 아직 이른데 그것도 조용히 해야 하면 답답해요.", 24, false, false, null),
                    new SeedMessage("MEDIATOR_TO_B", "자신에게 늦은 밤이 아닌데 조용히 해야 하는 게 답답하셨군요.", 25, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "이만큼 이야기 나눠주셔서 고마워요. 지금까지 정리해보면 어떨까요?", 27, true, false, null),
                    new SeedMessage("MEDIATOR_TO_B", "이만큼 이야기 나눠주셔서 고마워요. 지금까지 정리해보면 어떨까요?", 27, true, false, null)
                ),
                new SeedReport(
                    4, 2, 3, 6,
                    List.of("서로의 수면 타임 명확히 정하기"),
                    "룸메이트가 밤 12시 이후에도 통화하고 음악을 틀어",
                    "잠을 못 자서 매일 피곤하고 답답해",
                    "밤에 충분히 자고 싶어",
                    "밤 11시 이후에는 헤드폰을 쓰거나 밖에서 통화해줄 수 있어?",
                    "룸메이트가 밤 11시부터 조용히 해달라고 해",
                    "내 생활 방식을 존중받지 못하는 것 같아 답답해",
                    "내 시간도 자유롭게 쓰고 싶어",
                    "서로 타협점을 찾아서 규칙을 정해줄 수 있어?",
                    "밤에 잠을 못 자서 매일 피곤해. 서로 생활 시간이 달라서 그런 거라는 건 아는데, 최소한 취침 전 1시간은 조용해줄 수 있으면 좋겠어.",
                    50, 50,
                    "DIFFERENCE"
                )
            ),

            // S27 — TERMINATED 위기 차단, 폭력 키워드
            new SeedScenario(
                "S27",
                "test3@again.com",
                "COUPLE",
                "couple",
                "couple_trust",
                "break_promise_loop",
                "TERMINATED",
                true,
                4320,  // 3일 전
                null,
                null,
                false,
                false,
                false,
                List.of("domestic_violence"),
                null,
                List.of(
                    new SeedMessage("USER_A", "남친이랑 또 싸웠어. 이번엔 좀 심하게.", 0, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "심하게 다투셨군요. 지금 괜찮으신가요?", 1, false, false, null),
                    new SeedMessage("USER_A", "남친이 나를 때렸어.", 2, false, false, 1)
                ),
                null
            ),

            // ========== 정현 (test4@again.com) ==========

            // S04 — 진행 중 Solo
            new SeedScenario(
                "S04",
                "test4@again.com",
                "KOREAN_SPECIFIC",
                "korean_specific",
                "in_law",
                "custom",
                "CHATTING_SOLO",
                true,
                60,  // 1시간 전
                null,
                null,
                false,
                false,
                false,
                List.of(),
                null,
                List.of(
                    new SeedMessage("USER_A", "엄마가 형이랑 차별 대우 한 게 너무 많아. 말하면 또 히스테리 부린다고 할 거야.", 0, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "오랜 시간 쌓인 답답함이 있으신 것 같아요. 가장 최근에 그렇게 느낀 일은 어떤 거였어요?", 1, false, false, null),
                    new SeedMessage("USER_A", "지난 주 식구들 모일 때. 형 챙기는 건 다 하면서 나한테는 아예 묻지도 않더라.", 3, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "그 자리에서 투명한 사람이 된 듯한 기분이 들었겠어요.", 4, false, false, null)
                ),
                null
            ),

            // S09 — TERMINATED 사용자 명시 종료
            new SeedScenario(
                "S09",
                "test4@again.com",
                "FAMILY",
                "family",
                "family_money",
                "loan",
                "TERMINATED",
                true,
                14400,  // 10일 전
                null,
                null,
                false,
                false,
                false,
                List.of(),
                null,
                List.of(
                    new SeedMessage("USER_A", "동생이 또 돈 빌려달라네. 100만원.", 0, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "반복되는 요청이 부담스러우셨겠어요. 이번에는 어떻게 하실 생각이세요?", 1, false, false, null),
                    new SeedMessage("USER_A", "이번에는 단호하게 거절했지.", 3, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "결정을 내리신 게 보여요. 그 결정이 이전과는 어떻게 달랐어요?", 4, false, false, null),
                    new SeedMessage("USER_A", "됐어. 그만할래. 짜증나.", 6, false, false, null)
                ),
                null
            ),

            // S14 — 시댁 봉양 Solo 완료
            new SeedScenario(
                "S14",
                "test4@again.com",
                "KOREAN_SPECIFIC",
                "korean_specific",
                "in_law",
                "custom",
                "COMPLETED",
                true,
                21600,  // 15일 전
                null,
                null,
                true,
                true,
                false,
                List.of(),
                null,
                List.of(
                    new SeedMessage("USER_A", "시어머니 모시고 사는데 한계가 왔어. 주말이 없어.", 0, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "쉬는 시간 없이 계속 챙겨드리는 게 많이 지치셨겠어요. 언제부터 이렇게 됐어요?", 1, false, false, null),
                    new SeedMessage("USER_A", "같이 산 지 3년인데 갈수록 힘들어. 며느리가 다 해야 한다는 생각이 강해.", 3, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "3년 동안 기준이 맞지 않았는데 말하기도 어려우셨겠어요.", 4, false, false, null),
                    new SeedMessage("USER_A", "남편이 엄마 편이야. 내 말을 들어주질 않아.", 6, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "남편에게도 이해받지 못하는 상황이라면 더 외로우셨겠어요. 지금 제일 원하는 게 뭐예요?", 7, false, false, null),
                    new SeedMessage("USER_A", "그냥 일주일에 하루라도 내 시간이 있으면 좋겠어.", 9, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "아주 작은 요구인데 그게 안 되는 상황이 답답하셨겠어요.", 10, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "이만큼 이야기 나눠주셔서 고마워요. 지금까지 정리해보면 어떨까요?", 11, true, false, null)
                ),
                new SeedReport(
                    5, 2, 4, 7,
                    List.of(),
                    "주말 없이 시어머니를 3년째 혼자 챙기고 있어",
                    "지치고 내 삶이 없어진 것 같아 막막해",
                    "일주일에 하루만이라도 내 시간이 필요해",
                    "남편과 함께 어머니 돌봄을 나눌 방법을 이야기해줄 수 있어?",
                    null, null, null, null,
                    "주말도 없이 3년을 혼자 챙기다 보니 지치고 막막해. 내 삶이 없어진 것 같아. 일주일에 하루라도 내 시간을 가질 수 있도록 같이 방법을 찾아줄 수 있어?",
                    null, null,
                    "DIFFERENCE"
                )
            ),

            // S19 — 동생과 봉양 분담 Duo 완료
            new SeedScenario(
                "S19",
                "test4@again.com",
                "FAMILY",
                "family",
                "family_care",
                "custom",
                "COMPLETED",
                false,
                43200,  // 30일 전
                30L,  // 세션 시작 후 30분
                "동생",
                true,
                true,
                true,
                List.of(),
                null,
                List.of(
                    new SeedMessage("USER_A", "부모님 모시는 거 동생이 더 부담해야 해. 내가 10년을 했어.", 0, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "오랫동안 혼자 감당해오신 게 지치셨겠어요. 10년이라는 시간이 무거워요.", 1, false, false, null),
                    new SeedMessage("USER_A", "동생은 멀리 살아서 못 온다고만 하는데, 그러면 돈이라도 더 보내야 하는 거 아니야?", 3, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "거리와 돈, 어느 방식으로든 분담이 이루어져야 한다고 느끼시는 거군요.", 4, false, false, null),
                    new SeedMessage("USER_A", "맞아. 나는 몸으로 하는데 동생은 연락도 안 해.", 6, false, false, null),
                    new SeedMessage("MEDIATOR_TO_B", "함께 와주셔서 고마워요. 상대방이 적으신 내용은 따로 듣고 있어요. 천천히 이야기해주세요.", 30, false, true, null),
                    new SeedMessage("USER_B", "저도 신경은 써요. 그냥 거리가 멀어서 자주 못 갈 뿐이에요.", 31, false, false, null),
                    new SeedMessage("MEDIATOR_TO_B", "신경을 쓰고 싶은데 거리 때문에 한계가 있다는 답답함이 있으신 것 같아요.", 32, false, false, null),
                    new SeedMessage("USER_B", "누나가 너무 많이 했다는 건 알아요. 근데 직장 때문에 이사가 어려워요.", 34, false, false, null),
                    new SeedMessage("MEDIATOR_TO_B", "직장 상황과 부모님 돌봄 사이에서 어떻게 해야 할지 막막하셨겠어요.", 35, false, false, null),
                    new SeedMessage("USER_A", "말이라도 자주 하면 되잖아.", 37, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "연락이라도 자주 해주기를 원하시는 마음이 있군요. 그게 인정받는 것처럼 느껴지는 것 같아요.", 38, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "이만큼 이야기 나눠주셔서 고마워요. 지금까지 정리해보면 어떨까요?", 40, true, false, null),
                    new SeedMessage("MEDIATOR_TO_B", "이만큼 이야기 나눠주셔서 고마워요. 지금까지 정리해보면 어떨까요?", 40, true, false, null)
                ),
                new SeedReport(
                    6, 3, 2, 2,
                    List.of("월 1회 전화 정기적으로 하기"),
                    "10년째 부모님 돌봄을 혼자 해왔어",
                    "지치고 동생이 무책임하다는 생각에 억울해",
                    "동생과 분담해서 함께 책임지고 싶어",
                    "매달 한 번이라도 전화해서 근황을 나눠줄 수 있어?",
                    "누나가 부모님 돌봄을 대부분 해왔어",
                    "미안하고 도움이 안 되는 것 같아 죄책감이 들어",
                    "거리가 있어도 가족의 일부로 기여하고 싶어",
                    "어떻게 하면 내가 도움이 될 수 있는지 같이 이야기해줄 수 있어?",
                    "10년 동안 혼자 해왔는데 동생도 함께해주길 바랐어. 거리가 있어도 연락 한 번이라도 자주 해주면 혼자가 아닌 느낌이 들 것 같아. 매달 정기적으로 전화하는 것부터 시작할 수 있을까?",
                    45, 55,
                    "DIFFERENCE"
                )
            ),

            // S25 — 진행 중 Solo, 자동 권유 직전
            new SeedScenario(
                "S25",
                "test4@again.com",
                "FAMILY",
                "family",
                "family_intrusion",
                "custom",
                "CHATTING_SOLO",
                true,
                180,  // 3시간 전
                null,
                null,
                false,
                false,
                false,
                List.of(),
                null,
                List.of(
                    new SeedMessage("USER_A", "형이 엄마 생일에도 안 왔어. 핑계도 안 대고.", 0, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "중요한 날에 없었던 게 서운하셨겠어요. 형과의 관계가 어떻게 됐어요?", 1, false, false, null),
                    new SeedMessage("USER_A", "원래 자기 중심적이야. 어릴 때부터 그랬어.", 3, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "오래된 패턴에서 오는 답답함이 있으신 것 같아요. 지금 제일 힘든 게 뭐예요?", 4, false, false, null),
                    new SeedMessage("USER_A", "엄마가 상처받았을 텐데 내가 또 어떻게 해야 하나.", 6, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "엄마를 걱정하는 마음과 형에 대한 답답함이 동시에 있으시군요.", 7, false, false, null),
                    new SeedMessage("USER_A", "맞아. 항상 내가 수습하는 역할이야.", 9, false, false, null)
                ),
                null
            ),

            // S29 — Duo 초대 미합류
            new SeedScenario(
                "S29",
                "test4@again.com",
                "MARRIAGE",
                "marriage",
                "marriage_trust",
                "communication",
                "CHATTING_SOLO",
                true,
                2880,  // 2일 전
                null,
                "남편",
                false,
                false,
                false,
                List.of(),
                "inv_test29_jeonghyun",
                List.of(
                    new SeedMessage("USER_A", "남편이 집에서 아예 말을 안 해. 퇴근하면 핸드폰만 봐.", 0, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "집에 있어도 연결이 안 되는 느낌이 드셨겠어요. 언제부터 이렇게 됐어요?", 1, false, false, null),
                    new SeedMessage("USER_A", "작년에 크게 다투고 나서부터야. 화해는 했는데 예전 같지 않아.", 3, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "표면적으로는 화해했지만 깊이 남아있는 게 있으신 것 같아요.", 4, false, false, null),
                    new SeedMessage("USER_A", "맞아. 말해봐야 또 싸울까봐 먼저 안 해.", 6, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "먼저 말을 꺼내는 게 두려운 마음이 생긴 거네요.", 7, false, false, null),
                    new SeedMessage("USER_A", "남편도 이 서비스 써볼 수 있다고 해서 초대했는데 아직 안 왔어.", 9, false, false, null)
                ),
                null
            )
        );
    }
}
