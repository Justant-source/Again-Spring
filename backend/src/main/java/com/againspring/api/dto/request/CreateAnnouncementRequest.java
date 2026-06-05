package com.againspring.api.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * 공지사항 작성 요청 DTO
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAnnouncementRequest {

    /** 공지사항 제목 */
    private String title;

    /** 공지사항 본문 */
    private String body;

    /** 레벨 (INFO, WARN) */
    private String level;

    /** 공지사항 시작 시간 (null이면 즉시 시작) */
    private Instant startsAt;

    /** 공지사항 종료 시간 (null이면 무기한) */
    private Instant endsAt;
}
