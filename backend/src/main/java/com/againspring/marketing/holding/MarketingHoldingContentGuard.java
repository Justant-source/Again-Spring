package com.againspring.marketing.holding;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Filters non-conflict content out of the marketing holding pool.
 *
 * <p><b>배경 (2026-08-29)</b> — X 노출 상위 5건 중 2건이 "A vs B 공감 투표"라는 서비스 핵심과
 * 무관한 글이었다: "덕혜옹주가 일본 친구한테 털어놓은 고종 독살 얘기"(역사 트리비아), "여초회사
 * 1년 근무자가 쓰는 장단점"(장단점 리스트형 후기). 둘 다 AI-user가 원본 커뮤니티 글({@code
 * source_community})의 문체를 흉내내 생성한 글인데, 원본 자체가 갈등 서사가 아니어서 결과물도
 * 갈등 서사가 아니게 됐다.
 *
 * <p><b>왜 이 두 시그널인가</b> — DB로 실측한 결과 {@code posts.category}, 투표 옵션 구조("작성자
 * | 상대방"은 모든 글에 동일하게 붙는다), {@code partner_body_published} 유무는 문제 글과 정상
 * 글을 전혀 가르지 못했다(정상 사연도 파트너 답변 전에는 다수가 파트너 본문이 비어 있다). 1인칭
 * 대명사 존재 여부도 시도했으나 오탐이 너무 많았다(정상 글도 "나만", "제가" 등 다양한 형태를
 * 써서 규칙에 걸리지 않거나, "남아있다" 같은 무관한 단어의 "나" 음절에 걸려 무의미했다).
 *
 * <p>실제 prod DB 전수 조사(전체 기간)에서 아래 두 패턴은 각각 정확히 그 문제 글 1건씩만
 * 매칭하고 다른 글은 단 한 건도 걸리지 않았다(오탐 0건 확인, 2026-08-29):
 * <ul>
 *   <li>{@code YEAR_TRIVIA_PATTERN} — 4자리 연도 표기(예: "1919년"). 개인 갈등 사연은 "3년째",
 *       "작년에" 같은 상대적 시점을 쓰지 사학적 연도를 인용하지 않는다. 역사/시사 트리비아
 *       공유물에서만 나타났다.</li>
 *   <li>{@code PROS_CONS_LISTICLE} — "장점"과 "단점"이 함께 등장. 개인 갈등 서사는 사건을
 *       서술하지 항목별 장단점을 정리하지 않는다.</li>
 * </ul>
 *
 * <p><b>의도적으로 좁게 설계</b> — 오탐(false positive, 정상 사연을 잘못 거르는 것)이 미탐보다
 * 비싸다(홀딩 풀이 마르면 발행이 아예 멈춘다). 그래서 이 가드는 "확실히 아닌 것"만 정밀하게
 * 잡고, 애매한 경우는 통과시킨다. 새 오염 패턴이 발견되면 여기에 룰을 추가하되, 반드시 전체
 * DB에 대해 오탐 0건을 먼저 확인한 뒤 추가할 것.
 */
public final class MarketingHoldingContentGuard {

    public static final String REASON_YEAR_TRIVIA_PATTERN = "YEAR_TRIVIA_PATTERN";
    public static final String REASON_PROS_CONS_LISTICLE = "PROS_CONS_LISTICLE";

    private static final Pattern YEAR_PATTERN = Pattern.compile("\\d{4}년");

    private MarketingHoldingContentGuard() {}

    /**
     * @param title full post title (or promo/user title fallback)
     * @param body   published body text
     * @return exclusion reason code, or empty if the post looks like ordinary content
     */
    public static Optional<String> exclusionReason(String title, String body) {
        String text = (title == null ? "" : title) + " " + (body == null ? "" : body);
        if (text.isBlank()) {
            return Optional.empty();
        }
        if (YEAR_PATTERN.matcher(text).find()) {
            return Optional.of(REASON_YEAR_TRIVIA_PATTERN);
        }
        if (text.contains("장점") && text.contains("단점")) {
            return Optional.of(REASON_PROS_CONS_LISTICLE);
        }
        return Optional.empty();
    }
}
