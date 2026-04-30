package com.againspring.seed.scenarios;

import com.againspring.seed.dto.SeedMessage;
import com.againspring.seed.dto.SeedReport;
import com.againspring.seed.dto.SeedScenario;
import java.util.List;

/**
 * V4 시드 데이터: 서영(여성) + 지훈(남성) 시나리오
 * - 서영: 시댁 명절 갈등, 친구 거리감, 유산 분쟁, 가사 분담, 대화 부족
 * - 지훈: 양육관, 친구 결혼식, 불면, 초대 미합류
 * 각 인물별 10개 시나리오 (총 10개)
 * 참조: shared/docs/categories.yml
 */
public class SeedScenarios_Yeong_Jihun {
    public static List<SeedScenario> get() {
        return List.of(
            // S01 — 정상 완료 Solo, 시댁 명절 갈등 (서영)
            new SeedScenario(
                "S01",
                "test1@again.com",
                "KOREAN_SPECIFIC",
                "korean_specific",
                "in_law",
                "marriage_chores",
                "COMPLETED",
                true,
                2880,  // 2일 전
                null,
                null,
                true,
                true,
                false,
                List.of(),
                null,
                List.of(
                    new SeedMessage("USER_A", "어제 시댁 명절에 갔는데 또 시어머니가 나만 부엌에서 일 시키더라.", 0, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "그러셨군요. 또 그 자리에 있으셨던 게 어떤 마음이셨어요?", 1, false, false, null),
                    new SeedMessage("USER_A", "너무 화나고 서러워. 남편은 옆에서 그냥 보고만 있었어.", 3, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "그 순간 가장 기대했던 건 남편이 옆에 와주는 거였을 것 같아요. 매번 그게 안 되니까 더 무거우셨겠어요.", 4, false, false, null),
                    new SeedMessage("USER_A", "맞아 매번 그래. 시어머니도 일부러 그러는 것 같고.", 6, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "오랜 시간 같은 패턴이 반복되니 화가 쌓이는 게 당연해요. 그 화의 밑바닥에는 어떤 마음이 있을까요?", 7, false, false, null),
                    new SeedMessage("USER_A", "내가 가족이 아닌 것 같은 느낌. 항상 일하는 사람.", 9, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "내가 이 가족의 일부로 여겨지고 싶은 마음이 있으셨던 것 같아요. 그 마음 뒤에는 인정받고 싶은 욕구가 있을 수 있어요.", 10, false, false, null),
                    new SeedMessage("USER_A", "맞아. 어떻게 풀어야 할지 모르겠어.", 12, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "이만큼 이야기 나눠주셔서 고마워요. 지금까지 정리해보면 어떨까요?", 13, true, false, null)
                ),
                new SeedReport(
                    4, 2, 3, 3,
                    List.of(),
                    "명절 때마다 부엌일을 혼자 하게 됐어",
                    "혼자 남겨진 것 같고 서러웠어",
                    "내가 이 가족의 일부로 여겨지길 바라는 마음이야",
                    "다음에는 옆에서 한마디라도 거들어줄 수 있어?",
                    null, null, null, null,
                    "명절 때마다 내가 부엌일을 혼자 하게 되는 게 반복되니까, 혼자 남겨진 기분이 들고 서러웠어. 사실은 내가 이 가족의 일부로 여겨지길 바라는 마음이야. 다음 명절에는 짧게라도 내 옆에서 거들어주거나 한마디라도 같이 해줄 수 있어?",
                    null, null,
                    "DIFFERENCE"
                )
            ),

            // S05 — 진행 중 Solo (서영)
            new SeedScenario(
                "S05",
                "test1@again.com",
                "MARRIAGE",
                "marriage",
                "marriage_lifestyle",
                "hobby_time",
                "CHATTING_SOLO",
                true,
                360,  // 6시간 전
                null,
                null,
                false,
                false,
                false,
                List.of(),
                null,
                List.of(
                    new SeedMessage("USER_A", "남편이 또 게임만 해. 말도 안 해.", 0, false, false, null)
                ),
                null
            ),

            // S11 — 법적 1차 조언 (서영)
            new SeedScenario(
                "S11",
                "test1@again.com",
                "FAMILY",
                "family",
                "family_money",
                "loan",
                "COMPLETED",
                true,
                20160,  // 2주일 전
                null,
                null,
                true,
                true,
                false,
                List.of(),
                null,
                List.of(
                    new SeedMessage("USER_A", "엄마가 결혼하면 6억 도와주겠다고 했는데 약속이 바뀌었어. 8억 양보한 게 너무 억울해.", 0, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "그 배신감 정말 크겠어요. 자신의 것을 양보했는데 약속이 바뀐 거니까요. 그 약속이 없었다면 어떻게 계획하셨을 거예요?", 1, false, false, null),
                    new SeedMessage("USER_A", "아버지 돌아가셨을 때 형한테 다 양보했지. 형이 일을 못 하니까.", 3, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "형을 돕기 위해 자신의 몫을 내려놓으신 거였군요. 그 결정에는 어떤 마음이 있으셨어요?", 4, false, false, null),
                    new SeedMessage("USER_A", "엄마가 형은 아픈 손가락이라고 항상 그랬으니까. 근데 지금은 후회돼.", 6, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "엄마의 마음을 헤아리는 데 자신의 마음은 뒤로 미뤄두셨던 거네요.", 7, false, false, null),
                    new SeedMessage("USER_A", "그럼 지금 어떻게 해야 할까?", 9, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "지금 떠오르는 방법 몇 가지 있어요. 첫째, 어머니와 차분히 약속의 변경 사유를 한 번 더 듣는 것. 둘째, 형제와 함께 가족회의를 요청해 다 같이 정리하는 것. 셋째, 양보분에 대한 법적 검토는 변호사 상담이 함께 필요할 수 있어요. 대한법률구조공단(132)에서 무료 상담을 받아볼 수 있어요. 저는 그 과정에서 마음이 무너지지 않도록 옆에 있을게요. 어느 쪽이 먼저 떠오르세요?", 10, false, false, null),
                    new SeedMessage("USER_A", "변호사 상담 받으면 엄마한테 너무 매정해 보이지 않을까?", 12, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "매정해 보일까봐 망설이시는 마음이 무겁네요. 엄마와의 관계가 그동안 쌓아온 게 많으니까요.", 13, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "이만큼 이야기 나눠주셔서 고마워요. 지금까지 정리해보면 어떨까요?", 15, true, false, null)
                ),
                new SeedReport(
                    3, 1, 4, 2,
                    List.of("어머니의 입장을 한 번 더 들어보기", "법률 상담 먼저 받아보기"),
                    "아버지 유산 때 내 몫을 양보했는데 엄마의 약속도 바뀌었어",
                    "오랫동안 참고 양보했는데 또 무시당한 것 같아 억울해",
                    "내 희생이 인정받고 공정하게 대우받고 싶어",
                    "엄마와 한 번 약속에 대해 솔직하게 이야기할 수 있을까요?",
                    null, null, null, null,
                    "아버지 유산 때 내 몫을 양보한 건 형을 생각한 마음이었는데, 이번에는 엄마의 약속까지 바뀌니 억울하고 무기력했어. 사실은 내 희생이 인정받고 공정하게 대우받고 싶은 마음이야. 엄마와 한 번 솔직하게 그 약속에 대해 이야기 나눌 수 있을까요?",
                    null, null,
                    "DIFFERENCE"
                )
            ),

            // S17 — 친구 결혼 후 멀어짐 Solo (서영)
            new SeedScenario(
                "S17",
                "test1@again.com",
                "FRIEND",
                "friend",
                "friend_distance",
                "custom",
                "COMPLETED",
                true,
                10080,  // 1주일 전
                null,
                null,
                true,
                true,
                false,
                List.of(),
                null,
                List.of(
                    new SeedMessage("USER_A", "친한 친구가 결혼하고 나서 부쩍 연락이 없어졌어.", 0, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "친했던 분이 결혼 후 멀어지는 게 서운하셨겠어요. 언제부터 그렇게 느끼셨어요?", 1, false, false, null),
                    new SeedMessage("USER_A", "결혼 6개월 됐는데 이제 거의 못 봐. 바쁜 건 알지만 아쉬워.", 3, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "바쁜 사정은 이해하면서도, 그 공백이 서운하게 느껴지는 거네요.", 4, false, false, null),
                    new SeedMessage("USER_A", "나만 그러는 건지 모르겠어. 내가 너무 집착하는 건가.", 6, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "집착이 아니에요. 오래된 사이에서 변화를 감지하는 건 자연스러워요. 그 아쉬움 뒤에는 어떤 마음이 있을까요?", 7, false, false, null),
                    new SeedMessage("USER_A", "그냥 예전처럼 가끔이라도 편하게 만나고 싶어.", 9, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "이만큼 이야기 나눠주셔서 고마워요. 지금까지 정리해보면 어떨까요?", 10, true, false, null)
                ),
                new SeedReport(
                    2, 1, 2, 3,
                    List.of("짧게라도 근황 연락 먼저 해보기", "부담 없이 커피 한 번 제안하기", "결혼 후 변화를 이해하며 기다리기"),
                    "결혼 후 6개월 동안 거의 못 봤어",
                    "예전처럼 친했던 사이가 변한 것 같아서 아쉽고 서운해",
                    "오랜 친구와 가끔이라도 편하게 시간을 보내고 싶어",
                    "바쁘더라도 가끔 짧게 만날 수 있을까?",
                    null, null, null, null,
                    "결혼 후 거의 못 만나게 되니 우리 사이가 변한 것 같아 아쉽고 서운했어. 바쁜 건 이해하는데, 가끔이라도 편하게 만나고 싶은 마음이야. 부담 없이 커피 한 잔만 해도 좋아, 한 번 만날 수 있을까?",
                    null, null,
                    "DIFFERENCE"
                )
            ),

            // S21 — 가사 분담 Duo 완료 (서영 + 남편)
            new SeedScenario(
                "S21",
                "test1@again.com",
                "MARRIAGE",
                "marriage",
                "marriage_chores",
                "cleanliness",
                "COMPLETED",
                false,
                4320,  // 3일 전
                25L,  // 세션 시작 25분 후
                "남편",
                true,
                true,
                true,
                List.of(),
                null,
                List.of(
                    new SeedMessage("USER_A", "남편이 집안일을 거의 안 해. 퇴근해서 쉰다는 건 알지만 너무 지쳐.", 0, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "매일 혼자 집안일을 감당하시는 게 많이 지치셨겠어요. 제일 힘든 부분이 뭐예요?", 1, false, false, null),
                    new SeedMessage("USER_A", "설거지, 청소, 빨래 다 나 혼자야. 한 번도 먼저 하는 법이 없어.", 3, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "한 번도 먼저 나서지 않는 것처럼 느껴지는 게 제일 속상하시군요.", 4, false, false, null),
                    new SeedMessage("USER_A", "맞아. 부탁해야 겨우 하는데 그것도 대충 해.", 6, false, false, null),
                    new SeedMessage("MEDIATOR_TO_B", "함께 정리하러 와주셔서 고마워요. 상대방이 적으신 내용은 제가 따로 듣고 있어요. 천천히 마음을 들려주세요.", 25, false, true, null),
                    new SeedMessage("USER_B", "저도 퇴근하면 피곤한데 매번 잔소리를 들어요.", 26, false, false, null),
                    new SeedMessage("MEDIATOR_TO_B", "퇴근 후 피곤한 상태에서 잔소리처럼 들리는 게 부담스러우셨겠어요. 집안일에 대해서 어떻게 생각하세요?", 27, false, false, null),
                    new SeedMessage("USER_B", "저도 나름대로 하려고는 해요. 그냥 방식이 달라서 그런 것 같아요.", 29, false, false, null),
                    new SeedMessage("MEDIATOR_TO_B", "방식의 차이가 오해로 이어진 부분이 있는 것 같네요.", 30, false, false, null),
                    new SeedMessage("USER_A", "방식이 달라서가 아니라 그냥 안 하는 거야.", 32, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "그 답답함이 느껴져요. 지금까지 얼마나 참아왔는지가 보여요.", 33, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "이만큼 이야기 나눠주셔서 고마워요. 지금까지 정리해보면 어떨까요?", 35, true, false, null),
                    new SeedMessage("MEDIATOR_TO_B", "이만큼 이야기 나눠주셔서 고마워요. 지금까지 정리해보면 어떨까요?", 35, true, false, null)
                ),
                new SeedReport(
                    6, 3, 4, 2,
                    List.of("서로의 집안일 기준 나눠보기"),
                    "매일 집안일을 혼자 해왔어",
                    "지치고 무시당하는 느낌이야",
                    "공평하게 나눠서 함께 살고 싶어",
                    "이번 주에 집안일 역할 분담 이야기 한 번 해줄 수 있어?",
                    "퇴근 후 잔소리를 들었어",
                    "피곤한 상태에서 비판받는 것 같아 억울해",
                    "내 노력도 인정받고 싶어",
                    "방식 차이를 먼저 이야기하고 나서 정해줄 수 있어?",
                    "매일 혼자 집안일을 감당해왔는데 지치고 무시당하는 느낌이었어. 공평하게 나눠서 함께 사는 게 내 바람이야. 이번 주에 집안일 역할 분담 이야기 한 번 해줄 수 있어?",
                    40, 60,
                    "DIFFERENCE"
                )
            ),

            // S26 — Duo 진행 중 (서영 + 남편)
            new SeedScenario(
                "S26",
                "test1@again.com",
                "MARRIAGE",
                "marriage",
                "marriage_affection",
                "no_verbal",
                "CHATTING_DUO",
                false,
                90,  // 1시간30분 전
                40L,  // 세션 시작 40분 후
                "남편",
                false,
                false,
                false,
                List.of(),
                null,
                List.of(
                    new SeedMessage("USER_A", "요즘 남편이랑 말을 잘 안 해. 대화가 없어.", 0, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "대화가 줄어든 걸 먼저 느끼셨군요. 언제부터 그랬어요?", 1, false, false, null),
                    new SeedMessage("USER_A", "몇 달 됐어. 피곤하다고 말하면 말 안 해.", 3, false, false, null),
                    new SeedMessage("MEDIATOR_TO_B", "함께 와주셔서 고마워요. 상대방이 적으신 내용은 따로 듣고 있어요. 천천히 이야기해주세요.", 40, false, true, null),
                    new SeedMessage("USER_B", "저도 할 말이 없는 게 아니라 뭘 말해야 할지 모르겠어요.", 41, false, false, null),
                    new SeedMessage("MEDIATOR_TO_B", "무슨 말을 해야 할지 모르는 막막함이 있으신 것 같아요. 요즘 대화에서 어떤 게 제일 어렵게 느껴지세요?", 43, false, false, null),
                    new SeedMessage("USER_A", "내가 말 걸면 짧게만 대답해. 관심이 없는 것 같아.", 45, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "짧은 대답이 무관심으로 느껴지는 거군요. 그 느낌이 얼마나 쌓여왔는지 들려요.", 46, false, false, null)
                ),
                null
            ),

            // S03 — 양육관 충돌 Duo 완료 (지훈 + 아내)
            new SeedScenario(
                "S03",
                "test2@again.com",
                "MARRIAGE",
                "marriage",
                "marriage_children",
                "edu_direction",
                "COMPLETED",
                false,
                10080,  // 1주일 전
                30L,  // 세션 시작 30분 후
                "아내",
                true,
                true,
                true,
                List.of(),
                null,
                List.of(
                    new SeedMessage("USER_A", "아내랑 큰 애 학원 문제로 또 싸웠다.", 0, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "큰 결정 앞에서 부부가 부딪히는 일이 있으셨군요. 어떤 부분에서 갈렸어요?", 1, false, false, null),
                    new SeedMessage("USER_A", "아내는 영어학원 더 늘리자고 하고, 나는 지금도 충분하다고 생각해.", 3, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "두 분의 기준이 다른 거네요. 더 늘리지 않아도 된다고 생각하시는 이유가 있어요?", 4, false, false, null),
                    new SeedMessage("USER_A", "애가 벌써 지쳐 보여. 학원을 더 보내면 스트레스만 쌓인다고.", 6, false, false, null),
                    new SeedMessage("MEDIATOR_TO_B", "함께 와주셔서 고마워요. 상대방이 적으신 내용은 따로 듣고 있어요. 천천히 이야기해주세요.", 30, false, true, null),
                    new SeedMessage("USER_B", "남편이 아이에게 충분히 신경을 안 쓰는 것 같아요.", 31, false, false, null),
                    new SeedMessage("MEDIATOR_TO_B", "남편분의 무관심으로 느껴지는 부분이 있으신 것 같아요. 그게 가장 힘드셨던 부분이세요?", 32, false, false, null),
                    new SeedMessage("USER_B", "네. 저는 아이 미래가 걱정되는데 남편은 그냥 놔두면 된다고만 해요.", 34, false, false, null),
                    new SeedMessage("MEDIATOR_TO_B", "아이 미래에 대한 걱정과 남편의 여유로움 사이에서 혼자 불안을 감당하셨던 거군요.", 35, false, false, null),
                    new SeedMessage("USER_A", "애 의견도 안 묻고 학원 넣으면 애가 힘들다고.", 37, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "아이의 감정과 여유를 중요하게 생각하시는 마음이 보여요.", 38, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "이만큼 이야기 나눠주셔서 고마워요. 지금까지 정리해보면 어떨까요?", 40, true, false, null),
                    new SeedMessage("MEDIATOR_TO_B", "이만큼 이야기 나눠주셔서 고마워요. 지금까지 정리해보면 어떨까요?", 40, true, false, null)
                ),
                new SeedReport(
                    3, 2, 6, 5,
                    List.of("아이에게 직접 의견 물어보기"),
                    "아내가 내 동의 없이 영어학원을 추가하려 해",
                    "아이 의사도 안 물어보고 결정하는 게 답답해",
                    "아이가 지치지 않고 건강하게 성장하길 바라",
                    "학원 결정 전에 셋이 함께 이야기해볼 수 있어?",
                    "남편이 아이 교육에 소극적이야",
                    "혼자 아이 미래를 걱정하는 것 같아 외롭고 불안해",
                    "남편과 함께 아이 미래를 계획하고 싶어",
                    "학원 문제를 나랑 같이 진지하게 생각해줄 수 있어?",
                    "아이가 이미 지쳐 보이는데 학원을 더 보내면 안 된다고 생각했어. 아이 의사도 물어보지 않고 결정하는 게 답답했고. 함께 아이에게 어떻게 하고 싶은지 물어보고 결정할 수 있을까?",
                    35, 65,
                    "DIFFERENCE"
                )
            ),

            // S06 — Duo 진행 중 (지훈 + 아내)
            new SeedScenario(
                "S06",
                "test2@again.com",
                "MARRIAGE",
                "marriage",
                "couple_time",
                "friends_first",
                "CHATTING_DUO",
                false,
                30,  // 30분 전
                5L,  // 세션 시작 5분 후
                "아내",
                false,
                false,
                false,
                List.of(),
                null,
                List.of(
                    new SeedMessage("USER_A", "어젯밤에 또 다퉜다. 아내 친구 결혼식에 같이 가기 싫어.", 0, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "그 자리가 부담되시는 마음이 있는 것 같아요. 어떤 부분이 불편하세요?", 1, false, false, null),
                    new SeedMessage("USER_A", "모르는 사람들 사이에서 몇 시간을 있어야 해. 그게 힘들어.", 2, false, false, null),
                    new SeedMessage("MEDIATOR_TO_B", "함께 와주셔서 고마워요. 상대방이 적으신 내용은 따로 듣고 있어요. 천천히 이야기해주세요.", 5, false, true, null),
                    new SeedMessage("USER_B", "남편이 또 자기 일정 핑계를 대요.", 6, false, false, null),
                    new SeedMessage("MEDIATOR_TO_B", "핑계처럼 느껴지는 게 서운하셨겠어요. 이번 결혼식이 어떤 의미인지 들려주세요.", 7, false, false, null),
                    new SeedMessage("USER_A", "핑계가 아니라 진짜 힘들어. 사람 많은 곳이 어렵다고.", 9, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "낯선 자리가 힘든 게 핑계가 아니라는 걸 이해받고 싶으신 것 같아요.", 10, false, false, null)
                ),
                null
            ),

            // S10 — Duo 초대 미합류 (지훈)
            new SeedScenario(
                "S10",
                "test2@again.com",
                "MARRIAGE",
                "marriage",
                "marriage_trust",
                "break_promise_loop",
                "CHATTING_SOLO",
                true,
                1440,  // 1일 전
                null,
                "아내",
                false,
                false,
                false,
                List.of(),
                "inv_test10_jihun",
                List.of(
                    new SeedMessage("USER_A", "아내랑 한 달째 말 안 하고 있어.", 0, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "한 달이라는 시간이 무거우셨겠어요. 어떻게 시작된 일이었나요?", 1, false, false, null),
                    new SeedMessage("USER_A", "작은 말다툼이었는데 서로 피하다 보니 이렇게 됐어.", 3, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "피하다 보면 더 굳어지는 패턴이 생기죠. 먼저 다가가기가 어려우셨어요?", 4, false, false, null),
                    new SeedMessage("USER_A", "내가 먼저 하면 지는 것 같은 느낌이야.", 6, false, false, null)
                ),
                null
            ),

            // S12 — 의료 1차 조언 (지훈)
            new SeedScenario(
                "S12",
                "test2@again.com",
                "FAMILY",
                "family",
                "family_intrusion",
                "custom",
                "COMPLETED",
                true,
                30240,  // 3주일 전
                null,
                null,
                true,
                true,
                false,
                List.of(),
                null,
                List.of(
                    new SeedMessage("USER_A", "한 달째 잠을 잘 못 자. 일하기도 힘들고.", 0, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "한 달이라는 시간을 혼자 견디고 계셨네요. 잠 못 드시는 시간에 어떤 생각이 들어요?", 1, false, false, null),
                    new SeedMessage("USER_A", "회사 일도 그렇고 집안 일도 다 무겁게 느껴져.", 3, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "여러 무게가 동시에 쌓인 것 같아요. 그 중에 가장 큰 무게가 어디서 오는 것 같으세요?", 4, false, false, null),
                    new SeedMessage("USER_A", "어떻게 해야 할까.", 6, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "지금 떠오르는 것 몇 가지 있어요. 첫째, 카페인을 줄이고 일정한 시간에 누워보기. 둘째, 짧은 산책이라도 햇빛을 받는 시간 만들기. 다만 한 달 이상 이어지는 불면은 정신건강의학과 상담이 함께 필요할 수 있어요. 정신건강복지센터(1577-0199)에서 가까운 병원도 안내받을 수 있어요. 저는 그 과정에서 마음이 무너지지 않도록 옆에 있을게요. 어떻게 시작해보고 싶으세요?", 7, false, false, null),
                    new SeedMessage("USER_A", "병원 가는 게 좀 부담스럽다.", 9, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "병원 문턱을 넘는 게 무거우신 것 같아요. 그 마음이 자연스러워요.", 10, false, false, null),
                    new SeedMessage("MEDIATOR_TO_A", "이만큼 이야기 나눠주셔서 고마워요. 지금까지 정리해보면 어떨까요?", 11, true, false, null)
                ),
                new SeedReport(
                    2, 1, 3, 6,
                    List.of("정신건강복지센터 안내"),
                    "한 달째 잠을 못 자고 일하기도 힘들어",
                    "무기력하고 아무것도 해결이 안 되는 것 같아",
                    "이 무거움을 혼자 감당하지 않았으면 해",
                    "가까운 정신건강의학과에 한 번 가봐도 괜찮겠어요?",
                    null, null, null, null,
                    "한 달째 잠을 못 자면서 무기력해졌는데, 혼자 감당하려고 버텨온 것 같아요. 이 무거움을 혼자 지지 않아도 된다는 걸 기억해주세요. 정신건강복지센터(1577-0199)에서 가까운 병원을 안내받을 수 있어요.",
                    null, null,
                    "FACTUAL"
                )
            )
        );
    }
}
