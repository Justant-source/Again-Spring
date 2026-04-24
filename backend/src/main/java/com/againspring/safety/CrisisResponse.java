package com.againspring.safety;

import java.util.ArrayList;
import java.util.List;

import lombok.Builder;
import lombok.Getter;

/**
 * Response body to return when a crisis is detected.
 *
 * Contains crisis resource hotline information, an empathetic message,
 * and recommended session action. Designed to be returned directly to client.
 */
@Getter
@Builder
public class CrisisResponse {

	private final String title;
	private final String message;
	private final List<HotlineInfo> hotlines;
	private final String recommendedAction; // e.g., "FORCE_END"
	private final String sessionAction; // e.g., "TERMINATE"

	@Getter
	@Builder
	public static class HotlineInfo {
		private final String name;
		private final String number;
		private final String hours;
		private final String description;
	}

	/**
	 * Factory method to create a standard crisis response with Korean hotline numbers.
	 */
	public static CrisisResponse createStandardCrisisResponse() {
		List<HotlineInfo> hotlines = new ArrayList<>();

		hotlines.add(HotlineInfo.builder()
			.name("여성긴급전화")
			.number("1366")
			.hours("24시간")
			.description("가정폭력·성폭력")
			.build());

		hotlines.add(HotlineInfo.builder()
			.name("정신건강위기상담")
			.number("1577-0199")
			.hours("24시간")
			.description("정신건강 위기상담")
			.build());

		hotlines.add(HotlineInfo.builder()
			.name("아동보호전문기관")
			.number("1391")
			.hours("24시간")
			.description("아동학대 신고")
			.build());

		hotlines.add(HotlineInfo.builder()
			.name("청소년상담 1388")
			.number("1388")
			.hours("24시간")
			.description("청소년 상담")
			.build());

		hotlines.add(HotlineInfo.builder()
			.name("자살예방상담")
			.number("1393")
			.hours("24시간")
			.description("자살예방상담")
			.build());

		hotlines.add(HotlineInfo.builder()
			.name("경찰신고")
			.number("112")
			.hours("24시간")
			.description("긴급 상황 신고")
			.build());

		hotlines.add(HotlineInfo.builder()
			.name("법률구조공단")
			.number("132")
			.hours("업무시간")
			.description("법률 상담")
			.build());

		return CrisisResponse.builder()
			.title("중요한 안내")
			.message(
				"말씀해주신 상황은 저희 서비스의 범위를 넘어서는 "
					+ "매우 중요한 문제예요. 지금 바로 전문 기관의 도움을 받아주세요."
			)
			.hotlines(hotlines)
			.recommendedAction("FORCE_END")
			.sessionAction("TERMINATE")
			.build();
	}
}
