-- =============================================
-- 다시봄 광장 Mock 사연 시드 데이터 (12개)
-- 블라인드·네이트판 스타일 실제 갈등 사연
-- =============================================

SET FOREIGN_KEY_CHECKS = 0;
DELETE FROM jurors WHERE post_id LIKE 'mock%';
DELETE FROM votes WHERE post_id LIKE 'mock%';
DELETE FROM vote_options WHERE post_id LIKE 'mock%';
DELETE FROM post_comments WHERE post_id LIKE 'mock%';
DELETE FROM posts WHERE id LIKE 'mock%';
SET FOREIGN_KEY_CHECKS = 1;

-- 유저 ID 변수
SET @u1 = 'b426f3f2448a4fde87c36032af40921e'; -- 서영 (test1)
SET @u2 = '6ac8e2a8ad4f4b1c86122e56390f0e3c'; -- 지훈 (test2)
SET @u3 = '5588602802044ae799c7d3fbd465afd1'; -- 수민 (test3)
SET @u4 = '272d2a2fed8a49d18035094075c9fbd2'; -- 정현 (test4)
SET @u5 = '67cf6646ac9d4a6596584471c87470c6'; -- 민수 (test5)

-- ============================================================
-- POSTS (12개)
-- ============================================================
INSERT INTO posts (
  id, author_id, user_title, title,
  body_raw, body_published,
  category, visibility, status,
  juror_count, neutralization_passed,
  partner_user_id, partner_body_raw, partner_body_published, partner_answered_at,
  publish_mode, vote_close_at, created_at, updated_at
) VALUES

-- [1] 부부·육아분담 — paired + 배심원3 (VOTING)
('mock_001', @u1, '주말에도 저만 쉬는 날이 없어요', '주말에도 저만 쉬는 날이 없어요',
 '맞벌이인데 주말에도 저 혼자 아이 보고 집안일 다 해요. 남편은 누워서 유튜브만 봐요. 서운하다고 하면 평일에 자기가 얼마나 힘든지 아냐고 해요. 저도 평일에 일하는데 왜 저만 주말에 일해야 하나요. 이제 지쳐서 더 이상 못 참겠어요.',
 '맞벌이 부부 상황에서 주말 가사·육아 분담에 대한 갈등입니다. 한쪽이 주말에도 가사와 육아를 도맡아 하며 지침을 느끼고, 상대는 평일 업무로 인한 피로를 이유로 들고 있습니다.',
 'MARRIED', 'PUBLIC', 'VOTING', 3, 1,
 @u2, '평일에 야근이 많아 주말엔 좀 쉬어야 다음 주를 버텨요. 돕고 싶은 마음은 있는데 몸이 안 따라줘요. 그리고 제가 아이랑 논다고 하면 아내가 마음에 안 든다고 해서 차라리 눕게 됐어요.',
 '평일 야근으로 인한 피로 누적과 육아 방식에 대한 갈등을 호소하고 있습니다. 상대방도 돕고 싶으나 피로와 의사소통 문제가 복합적으로 작용하고 있습니다.',
 NOW() - INTERVAL 2 HOUR,
 'PUBLISH_NOW', NOW() + INTERVAL 3 DAY,
 NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY),

-- [2] 직장·상사가 보고서 가로챔 — solo + 배심원5 (VOTING)
('mock_002', @u3, '제가 3주 걸려 만든 보고서를 팀장이 자기 거로 올렸어요', '제가 3주 걸려 만든 보고서를 팀장이 자기 거로 올렸어요',
 '입사 2년차입니다. 팀장이 신사업 기획 보고서 만들어 보라고 해서 3주 동안 주말도 반납하고 만들었어요. 근데 임원 보고에서 팀장이 "제가 기획했습니다"라고 하더라고요. 나중에 물어보니 "팀 성과는 팀장 성과"라고 했어요. 이게 맞는 건가요?',
 '입사 2년차 직원이 3주간 작성한 신사업 기획 보고서를 팀장이 임원 보고에서 자신의 것으로 발표한 상황입니다. 팀장은 팀 성과는 팀장의 성과라는 입장을 취하고 있습니다.',
 'WORK', 'PUBLIC', 'VOTING', 5, 1,
 NULL, NULL, NULL, NULL,
 'PUBLISH_NOW', NOW() + INTERVAL 7 DAY,
 NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 5 DAY),

-- [3] 연인·데이트 비용 — paired + 배심원0 (VOTING)
('mock_003', @u4, '1년 사귀었는데 데이트 비용을 항상 제가 냈어요', '1년 사귀었는데 데이트 비용을 항상 제가 냈어요',
 '남자친구가 저보다 연봉이 낮은 건 아는데 1년 동안 거의 제가 다 냈어요. 밥도 저, 영화도 저, 숙박도 저. 한번은 괜찮냐고 물어봤더니 자기가 나중에 다 갚겠다고 했는데 그게 8개월째예요. 이제 돈보다 성의가 없어 보여서 더 서운해요.',
 '연애 1년 동안 데이트 비용 대부분을 부담한 상황에 대한 갈등입니다. 상대방은 나중에 갚겠다는 약속을 했으나 이행되지 않고 있어 금전적 문제보다 상대에 대한 서운함이 생겼습니다.',
 'COUPLE', 'PUBLIC', 'VOTING', 0, 1,
 @u5, '제가 연봉이 적은 건 맞는데 최대한 맞추려고 노력했어요. 큰 데이트는 상대가 냈지만 소소한 건 제가 많이 냈거든요. 근데 그게 전혀 카운트가 안 되는 것 같아서 억울해요.',
 '데이트 비용에 관한 인식 차이를 호소합니다. 소소한 지출은 자신이 부담했으나 상대방이 이를 인정하지 않는다고 느끼고 있습니다.',
 NOW() - INTERVAL 1 DAY,
 'PUBLISH_NOW', NOW() + INTERVAL 5 DAY,
 NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 4 DAY),

-- [4] 친구·빌려준 돈 — solo + 배심원3 (CLOSED)
('mock_004', @u1, '친한 친구에게 빌려준 300만원, 2년째 소식 없어요', '친한 친구에게 빌려준 300만원, 2년째 소식 없어요',
 '10년 넘게 친한 친구가 급하다고 해서 300만원 빌려줬어요. 처음엔 3개월 안에 갚겠다고 했는데 2년이 됐어요. 처음엔 가끔 연락이 왔는데 요즘은 카톡도 읽씹이에요. 직접 찾아가야 하나요? 아니면 그냥 포기해야 하나요?',
 '10년 지기 친구에게 빌려준 300만원이 2년째 상환되지 않고 있는 상황입니다. 처음의 약속과 달리 현재는 연락마저 끊긴 상태로, 상황 해결 방법에 대한 고민을 토로하고 있습니다.',
 'FRIEND', 'PUBLIC', 'CLOSED', 3, 1,
 NULL, NULL, NULL, NULL,
 'PUBLISH_NOW', NOW() - INTERVAL 1 DAY,
 NOW() - INTERVAL 14 DAY, NOW() - INTERVAL 14 DAY),

-- [5] 가족·명절 음식 준비 — solo + 배심원2 (VOTING)
('mock_005', @u2, '명절마다 처가에서 아내 혼자 음식 준비, 저는 어떡해야 하나요', '명절마다 처가에서 아내 혼자 음식 준비, 저는 어떡해야 하나요',
 '결혼 5년차인데 명절마다 처가에 가면 장모님이랑 아내가 둘이서 음식을 다 준비해요. 저는 거실에서 처남들이랑 티비 보게 되는데 이게 맞는 건지 모르겠어요. 도와드리겠다고 해도 괜찮다고 하시는데 그냥 있으면 아내 눈치가 보여요.',
 '명절 시댁 방문 시 음식 준비에 참여하고 싶으나 장모님의 거절과 처남들의 시선 사이에서 어떻게 행동해야 할지 고민하는 상황입니다.',
 'FAMILY', 'PUBLIC', 'VOTING', 2, 1,
 NULL, NULL, NULL, NULL,
 'PUBLISH_NOW', NOW() + INTERVAL 5 DAY,
 NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY),

-- [6] 직장·야근 강요 — paired + 배심원0 (VOTING)
('mock_006', @u3, '매주 금요일 칼퇴하면 팀장이 눈치를 줘요', '매주 금요일 칼퇴하면 팀장이 눈치를 줘요',
 '저는 계약서에 명시된 퇴근시간에 나가는데 팀장이 항상 한마디 해요. "요즘 일이 적나봐요?" 이런 식으로요. 야근 수당도 없고 자발적으로 하는 야근인데 저는 이 문화가 납득이 안 가요. 팀장한테 직접 말해야 할까요?',
 '계약서 상 퇴근 시간에 퇴근하는 직원에게 팀장이 부정적인 반응을 보이는 상황입니다. 야근 수당 없이 야근 문화를 당연시하는 팀 분위기에 대한 갈등을 토로하고 있습니다.',
 'WORK', 'PUBLIC', 'VOTING', 0, 1,
 @u4, '팀장 입장에서 생각해봐도 이해가 안 가요. 다들 바쁜데 혼자 나가면 팀 분위기가 어수선해지긴 해요. 그래도 계약대로 나가는 게 잘못은 아닌데 표현 방식이 잘못된 것 같아요.',
 '팀 전체의 업무량과 분위기를 고려했을 때 개인의 정시 퇴근이 미치는 영향에 대해 복잡한 감정을 가지고 있습니다.',
 NOW() - INTERVAL 3 HOUR,
 'PUBLISH_NOW', NOW() + INTERVAL 7 DAY,
 NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),

-- [7] 연인·전 남자친구와 연락 — solo + 배심원9 (VOTING)
('mock_007', @u5, '여자친구가 전 남자친구랑 아직도 연락해요', '여자친구가 전 남자친구랑 아직도 연락해요',
 '사귄 지 8개월 됐는데 여자친구가 전 남자친구랑 계속 연락하더라고요. 친구로 지낸다고 하는데 이해하려고 노력하는데 솔직히 불편해요. 직접 그만 연락하라고 하면 제가 너무 좁은 사람이 되는 건가요? 어떻게 하는 게 맞는 건지 모르겠어요.',
 '교제 8개월째 파트너가 전 교제 상대와 연락을 유지하는 상황에 대한 갈등입니다. 상대를 신뢰하면서도 불편함을 느끼는 복잡한 감정을 호소하고 있습니다.',
 'COUPLE', 'PUBLIC', 'VOTING', 9, 1,
 NULL, NULL, NULL, NULL,
 'PUBLISH_NOW', NOW() + INTERVAL 7 DAY,
 NOW() - INTERVAL 6 DAY, NOW() - INTERVAL 6 DAY),

-- [8] 친구·결혼식 사진 SNS 무단 공유 — paired + 배심원3 (VOTING)
('mock_008', @u1, '친구가 제 결혼식 사진을 허락도 없이 인스타에 올렸어요', '친구가 제 결혼식 사진을 허락도 없이 인스타에 올렸어요',
 '결혼식 사진을 SNS에 공유하지 않기로 했는데 친한 친구가 제 드레스 입은 사진을 자기 인스타에 올렸어요. 팔로워가 2000명이 넘는데 그중엔 제가 모르는 사람도 많아요. 내려달라고 했더니 "예쁜데 왜요" 하더라고요. 화가 나는 게 당연한 거 맞죠?',
 '결혼식 사진을 SNS에 게시하지 않기로 한 약속에도 불구하고 친구가 무단으로 공유한 상황입니다. 당사자의 동의 없이 개인 사진이 공개된 것에 대한 불쾌함을 호소하고 있습니다.',
 'FRIEND', 'PUBLIC', 'VOTING', 3, 1,
 @u3, '결혼식이 너무 예쁘고 축하하고 싶어서 올렸는데 이렇게까지 화낼 줄 몰랐어요. 좋은 마음으로 한 건데 서운해요. 바로 내렸는데 이미 사이가 어색해진 것 같아서 저도 속상해요.',
 '축하하는 마음으로 사진을 공유했으나 상대방이 강하게 반응한 것에 당혹감을 느끼고 있습니다. 이미 사진은 삭제했으나 관계가 어색해진 것을 걱정하고 있습니다.',
 NOW() - INTERVAL 5 HOUR,
 'PUBLISH_NOW', NOW() + INTERVAL 4 DAY,
 NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY),

-- [9] 가족·부모님 용돈 형제 갈등 — solo + 배심원0 (CLOSED)
('mock_009', @u4, '형은 부모님 용돈 안 드리고 저만 드리는데 이게 맞나요', '형은 부모님 용돈 안 드리고 저만 드리는데 이게 맞나요',
 '부모님이 경제적으로 어려우셔서 매달 30만원씩 드리고 있는데 형은 한 푼도 안 드려요. 형이 저보다 연봉도 높은데 "나는 부모님이랑 따로 사니까"라고 해요. 부모님도 형한테는 말을 못 꺼내세요. 제가 이상한 건가요?',
 '부모님에 대한 경제적 지원을 형제 중 한쪽만 부담하는 상황입니다. 형제 간 소득 차이가 있음에도 부담이 한쪽에 집중되는 것에 대한 불만을 토로하고 있습니다.',
 'FAMILY', 'PUBLIC', 'CLOSED', 0, 1,
 NULL, NULL, NULL, NULL,
 'PUBLISH_NOW', NOW() - INTERVAL 2 DAY,
 NOW() - INTERVAL 20 DAY, NOW() - INTERVAL 20 DAY),

-- [10] 직장·아이디어 도용 — solo + 배심원1 (VOTING)
('mock_010', @u2, '회의에서 제 아이디어가 동료 거로 채택됐어요', '회의에서 제 아이디어가 동료 거로 채택됐어요',
 '팀 회의에서 제가 새 마케팅 안을 말했는데 팀장이 크게 반응 안 했어요. 근데 10분 뒤에 옆자리 동료가 거의 똑같은 아이디어를 말하니까 팀장이 "오 좋은데요!"라고 했어요. 나중에 그 동료 아이디어가 공식 채택됐고 저는 아무 말도 못 했어요.',
 '팀 회의에서 자신이 먼저 제안한 아이디어가 주목받지 못하다가 동료가 유사한 내용을 말했을 때 채택된 상황입니다. 아이디어 도용 여부와 대응 방법에 대해 고민하고 있습니다.',
 'WORK', 'PUBLIC', 'VOTING', 1, 1,
 NULL, NULL, NULL, NULL,
 'PUBLISH_NOW', NOW() + INTERVAL 6 DAY,
 NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),

-- [11] 연인·과거 이야기 숨김 — paired + 배심원3 (VOTING)
('mock_011', @u5, '남자친구가 전 여자친구 얘기를 완전히 숨기고 있어요', '남자친구가 전 여자친구 얘기를 완전히 숨기고 있어요',
 '사귄 지 1년이 됐는데 남자친구가 전 여자친구 얘기를 철저히 숨겨요. 이름도, 언제 헤어졌는지도요. 저는 그냥 궁금한 거고 숨긴다는 게 더 찜찜해요. 물어보면 "왜 그런 거 궁금해"하고 화를 내요. 이게 이상한 건지 모르겠어요.',
 '교제 1년이 되었으나 파트너가 전 교제 상대에 대한 정보를 전혀 공유하지 않는 상황입니다. 정보를 숨기는 행동 자체가 불안감을 유발하고 있습니다.',
 'COUPLE', 'PUBLIC', 'VOTING', 3, 1,
 @u1, '전 연애 얘기를 굳이 꺼내야 하나 싶었어요. 현재 사귀는 사람한테 집중하고 싶었던 거고 숨긴다기보다 중요하지 않다고 생각했어요. 근데 이게 상대를 불안하게 한다는 걸 몰랐네요.',
 '전 교제에 대해 공유하지 않은 것이 현재 교제 상대에게 불안감을 줄 것이라 예상하지 못했습니다. 현재 관계에 집중하려는 의도였음을 설명하고 있습니다.',
 NOW() - INTERVAL 4 HOUR,
 'PUBLISH_NOW', NOW() + INTERVAL 5 DAY,
 NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY),

-- [12] 친구·단체 여행 비용 분쟁 — solo + 배심원5 (VOTING)
('mock_012', @u3, '친구들이랑 여행 갔다가 돈 계산 때문에 사이가 틀어졌어요', '친구들이랑 여행 갔다가 돈 계산 때문에 사이가 틀어졌어요',
 '5명이서 제주도 여행을 갔는데 제가 거의 다 카드로 결제했어요. 나중에 정산할 때 한 명이 "나는 그 식당 안 먹었잖아"하면서 자기가 먹은 것만 내겠다고 했어요. 다들 각자 결제하기 귀찮아서 제가 한 거였는데 이런 식으로 나올 줄 몰랐어요.',
 '5인 여행에서 편의를 위해 한 명이 대표로 결제했으나, 정산 시 일부 인원이 자신이 직접 소비한 금액만 지불하겠다고 주장하는 상황입니다. 여행 비용 정산 방식에 대한 갈등이 발생했습니다.',
 'FRIEND', 'PUBLIC', 'VOTING', 5, 1,
 NULL, NULL, NULL, NULL,
 'PUBLISH_NOW', NOW() + INTERVAL 4 DAY,
 NOW() - INTERVAL 8 DAY, NOW() - INTERVAL 8 DAY);

-- ============================================================
-- VOTE OPTIONS (각 게시글마다 "작성자", "상대방" 2개)
-- ============================================================
INSERT INTO vote_options (post_id, label, order_idx) VALUES
('mock_001', '작성자', 0), ('mock_001', '상대방', 1),
('mock_002', '작성자', 0), ('mock_002', '상대방', 1),
('mock_003', '작성자', 0), ('mock_003', '상대방', 1),
('mock_004', '작성자', 0), ('mock_004', '상대방', 1),
('mock_005', '작성자', 0), ('mock_005', '상대방', 1),
('mock_006', '작성자', 0), ('mock_006', '상대방', 1),
('mock_007', '작성자', 0), ('mock_007', '상대방', 1),
('mock_008', '작성자', 0), ('mock_008', '상대방', 1),
('mock_009', '작성자', 0), ('mock_009', '상대방', 1),
('mock_010', '작성자', 0), ('mock_010', '상대방', 1),
('mock_011', '작성자', 0), ('mock_011', '상대방', 1),
('mock_012', '작성자', 0), ('mock_012', '상대방', 1);

-- vote_options ID 조회용 변수
SET @vo_001_a = (SELECT id FROM vote_options WHERE post_id='mock_001' AND order_idx=0);
SET @vo_001_b = (SELECT id FROM vote_options WHERE post_id='mock_001' AND order_idx=1);
SET @vo_002_a = (SELECT id FROM vote_options WHERE post_id='mock_002' AND order_idx=0);
SET @vo_002_b = (SELECT id FROM vote_options WHERE post_id='mock_002' AND order_idx=1);
SET @vo_003_a = (SELECT id FROM vote_options WHERE post_id='mock_003' AND order_idx=0);
SET @vo_003_b = (SELECT id FROM vote_options WHERE post_id='mock_003' AND order_idx=1);
SET @vo_004_a = (SELECT id FROM vote_options WHERE post_id='mock_004' AND order_idx=0);
SET @vo_004_b = (SELECT id FROM vote_options WHERE post_id='mock_004' AND order_idx=1);
SET @vo_005_a = (SELECT id FROM vote_options WHERE post_id='mock_005' AND order_idx=0);
SET @vo_005_b = (SELECT id FROM vote_options WHERE post_id='mock_005' AND order_idx=1);
SET @vo_006_a = (SELECT id FROM vote_options WHERE post_id='mock_006' AND order_idx=0);
SET @vo_006_b = (SELECT id FROM vote_options WHERE post_id='mock_006' AND order_idx=1);
SET @vo_007_a = (SELECT id FROM vote_options WHERE post_id='mock_007' AND order_idx=0);
SET @vo_007_b = (SELECT id FROM vote_options WHERE post_id='mock_007' AND order_idx=1);
SET @vo_008_a = (SELECT id FROM vote_options WHERE post_id='mock_008' AND order_idx=0);
SET @vo_008_b = (SELECT id FROM vote_options WHERE post_id='mock_008' AND order_idx=1);
SET @vo_009_a = (SELECT id FROM vote_options WHERE post_id='mock_009' AND order_idx=0);
SET @vo_009_b = (SELECT id FROM vote_options WHERE post_id='mock_009' AND order_idx=1);
SET @vo_010_a = (SELECT id FROM vote_options WHERE post_id='mock_010' AND order_idx=0);
SET @vo_010_b = (SELECT id FROM vote_options WHERE post_id='mock_010' AND order_idx=1);
SET @vo_011_a = (SELECT id FROM vote_options WHERE post_id='mock_011' AND order_idx=0);
SET @vo_011_b = (SELECT id FROM vote_options WHERE post_id='mock_011' AND order_idx=1);
SET @vo_012_a = (SELECT id FROM vote_options WHERE post_id='mock_012' AND order_idx=0);
SET @vo_012_b = (SELECT id FROM vote_options WHERE post_id='mock_012' AND order_idx=1);

-- ============================================================
-- VOTES (각 게시글 투표)
-- voter_user_id를 다양하게 배분 — 작성자 본인은 못 투표
-- ============================================================
INSERT INTO votes (post_id, option_id, voter_user_id, created_at) VALUES
-- mock_001: 작성자=서영, 투표자=지훈/수민/정현/민수 → 작성자 편 3, 상대 편 1
('mock_001', @vo_001_a, @u2, NOW()), ('mock_001', @vo_001_a, @u3, NOW()), ('mock_001', @vo_001_a, @u4, NOW()),
('mock_001', @vo_001_b, @u5, NOW()),
-- mock_002: 작성자=수민 → 작성자 편 多
('mock_002', @vo_002_a, @u1, NOW()), ('mock_002', @vo_002_a, @u2, NOW()), ('mock_002', @vo_002_a, @u4, NOW()),
('mock_002', @vo_002_b, @u5, NOW()),
-- mock_003: 작성자=정현, 파트너=민수 → 비슷하게
('mock_003', @vo_003_a, @u1, NOW()), ('mock_003', @vo_003_a, @u3, NOW()),
('mock_003', @vo_003_b, @u2, NOW()),
-- mock_004: CLOSED 작성자=서영
('mock_004', @vo_004_a, @u2, NOW()), ('mock_004', @vo_004_a, @u3, NOW()), ('mock_004', @vo_004_a, @u4, NOW()),
('mock_004', @vo_004_a, @u5, NOW()),
-- mock_005: 작성자=지훈
('mock_005', @vo_005_a, @u1, NOW()), ('mock_005', @vo_005_a, @u3, NOW()), ('mock_005', @vo_005_a, @u4, NOW()),
('mock_005', @vo_005_b, @u5, NOW()),
-- mock_006: 작성자=수민, 파트너=정현
('mock_006', @vo_006_a, @u1, NOW()), ('mock_006', @vo_006_a, @u2, NOW()), ('mock_006', @vo_006_a, @u5, NOW()),
('mock_006', @vo_006_b, @u4, NOW()),
-- mock_007: 작성자=민수 → 비슷
('mock_007', @vo_007_a, @u1, NOW()), ('mock_007', @vo_007_a, @u2, NOW()), ('mock_007', @vo_007_a, @u4, NOW()),
('mock_007', @vo_007_b, @u3, NOW()),
-- mock_008: 작성자=서영, 파트너=수민
('mock_008', @vo_008_a, @u2, NOW()), ('mock_008', @vo_008_a, @u4, NOW()), ('mock_008', @vo_008_a, @u5, NOW()),
('mock_008', @vo_008_b, @u3, NOW()),
-- mock_009: CLOSED 작성자=정현
('mock_009', @vo_009_a, @u1, NOW()), ('mock_009', @vo_009_a, @u2, NOW()), ('mock_009', @vo_009_a, @u3, NOW()),
('mock_009', @vo_009_b, @u5, NOW()),
-- mock_010: 작성자=지훈
('mock_010', @vo_010_a, @u1, NOW()), ('mock_010', @vo_010_a, @u3, NOW()), ('mock_010', @vo_010_a, @u4, NOW()), ('mock_010', @vo_010_a, @u5, NOW()),
-- mock_011: 작성자=민수, 파트너=서영
('mock_011', @vo_011_a, @u2, NOW()), ('mock_011', @vo_011_a, @u3, NOW()), ('mock_011', @vo_011_a, @u4, NOW()),
('mock_011', @vo_011_b, @u5, NOW()),
-- mock_012: 작성자=수민
('mock_012', @vo_012_a, @u1, NOW()), ('mock_012', @vo_012_a, @u2, NOW()), ('mock_012', @vo_012_a, @u4, NOW()),
('mock_012', @vo_012_b, @u5, NOW());

-- ============================================================
-- JURORS (배심원 있는 게시글만)
-- ============================================================
INSERT INTO jurors (post_id, persona, chosen_option_id, empathy_comment, created_at) VALUES
-- mock_001 (배심원3)
('mock_001', '{"ageGroup":"30대","gender":"여성","disposition":"공감형","valueOrientation":"관계중시"}',
 @vo_001_a, '맞벌이 상황에서 주말 육아와 가사를 혼자 감당하는 것은 분명히 불균형합니다. 평일 피로는 양쪽 다 있을 텐데 주말 분담이 한쪽에 집중되는 건 논의가 필요해 보여요.', NOW()),
('mock_001', '{"ageGroup":"40대","gender":"남성","disposition":"분석형","valueOrientation":"균형중시"}',
 @vo_001_b, '평일 야근이 많으면 주말에 체력적으로 한계가 올 수 있어요. 다만 배우자의 소진 신호를 너무 오래 무시한 건 문제입니다. 서로의 피로를 인정하는 대화가 먼저 필요해 보입니다.', NOW()),
('mock_001', '{"ageGroup":"20대","gender":"여성","disposition":"직관형","valueOrientation":"자기표현중시"}',
 @vo_001_a, '육아와 가사의 총량 자체를 함께 계산해봐야 해요. 말로만 도와주고 싶다고 하는 게 아니라 구체적인 역할 분담표를 만들어보는 것도 방법입니다.', NOW()),

-- mock_002 (배심원5)
('mock_002', '{"ageGroup":"30대","gender":"여성","disposition":"공감형","valueOrientation":"공정중시"}',
 @vo_002_a, '3주를 투자한 보고서를 자신의 것으로 발표한 건 명백히 잘못된 행동입니다. 팀 성과가 팀장의 성과라는 논리도 그 아이디어를 만든 사람에 대한 인정이 있어야 성립합니다.', NOW()),
('mock_002', '{"ageGroup":"40대","gender":"남성","disposition":"경험형","valueOrientation":"조직이해"}',
 @vo_002_b, '조직 논리상 팀장이 발표하는 건 일반적이지만, 내부에서 기여자를 언급하지 않은 건 별개 문제입니다. 직접 대화로 크레딧을 요청해볼 수 있어요.', NOW()),
('mock_002', '{"ageGroup":"30대","gender":"남성","disposition":"분석형","valueOrientation":"성과중시"}',
 @vo_002_a, '이런 상황은 서면 기록을 남기는 것이 중요합니다. 앞으로 기획안을 제출할 때 날짜를 남기고, HR이나 임원에게 직접 어필하는 방법도 있습니다.', NOW()),
('mock_002', '{"ageGroup":"20대","gender":"여성","disposition":"공감형","valueOrientation":"자존감중시"}',
 @vo_002_a, '이건 단순한 오해가 아닌 명백한 크레딧 도용입니다. 당신이 화가 나는 건 지극히 정상적인 반응이에요.', NOW()),
('mock_002', '{"ageGroup":"50대","gender":"남성","disposition":"경험형","valueOrientation":"실용중시"}',
 @vo_002_b, '조직에서 비슷한 일이 반복될 수 있습니다. 억울함을 참기보다는 이번 기회에 팀장과 명확한 업무 크레딧 기준을 이야기해보는 것이 장기적으로 낫습니다.', NOW()),

-- mock_003 없음 (배심원0)

-- mock_004 (배심원3)
('mock_004', '{"ageGroup":"30대","gender":"여성","disposition":"현실형","valueOrientation":"관계중시"}',
 @vo_004_a, '10년 우정에 금이 가는 건 슬프지만, 300만원을 2년 넘게 갚지 않고 연락까지 끊은 건 이미 그 친구가 관계를 등진 것입니다.', NOW()),
('mock_004', '{"ageGroup":"40대","gender":"남성","disposition":"분석형","valueOrientation":"공정중시"}',
 @vo_004_a, '법적 절차(소액재판)나 내용증명 발송을 고려해볼 시점입니다. 관계가 중요하다면 마지막으로 만남을 시도해보고, 거절당하면 법적 절차를 밟는 것이 현실적입니다.', NOW()),
('mock_004', '{"ageGroup":"20대","gender":"여성","disposition":"공감형","valueOrientation":"감정우선"}',
 @vo_004_a, '돈보다 배신감이 더 클 것 같아요. 10년 친구라면 최소한 "지금 어렵다"는 말이라도 했어야 했는데 그게 없다는 게 슬픕니다.', NOW()),

-- mock_005 (배심원2)
('mock_005', '{"ageGroup":"30대","gender":"여성","disposition":"공감형","valueOrientation":"관계중시"}',
 @vo_005_a, '도와드리겠다고 했는데 괜찮다고 하셔서 자리를 피한 건 맞는 행동입니다. 억지로 들어가면 오히려 부담이 될 수 있어요.', NOW()),
('mock_005', '{"ageGroup":"40대","gender":"남성","disposition":"균형형","valueOrientation":"공정중시"}',
 @vo_005_b, '처가 명절 문화를 이해하면서도 아내와 사전에 "내가 어떻게 하면 좋겠어?"라고 물어보는 방법이 있습니다. 아내도 불편함을 느끼고 있을 수 있어요.', NOW()),

-- mock_006 없음 (배심원0)

-- mock_007 (배심원9)
('mock_007', '{"ageGroup":"20대","gender":"여성","disposition":"공감형","valueOrientation":"신뢰중시"}',
 @vo_007_a, '연락 자체보다 그 연락을 숨기거나 불투명하게 하는 게 더 문제입니다. 솔직하게 "불편하다"고 이야기할 권리가 있어요.', NOW()),
('mock_007', '{"ageGroup":"30대","gender":"남성","disposition":"이성형","valueOrientation":"자유존중"}',
 @vo_007_b, '전 연인과 친구로 지내는 건 개인의 자유입니다. 다만 현재 파트너가 불편하다면 서로의 감정을 조율할 필요가 있어요.', NOW()),
('mock_007', '{"ageGroup":"30대","gender":"여성","disposition":"분석형","valueOrientation":"균형중시"}',
 @vo_007_a, '불편함을 표현하지 않고 혼자 참으면 갈등이 더 커질 수 있습니다. 비난 없이 자신의 감정을 전달하는 대화가 필요합니다.', NOW()),
('mock_007', '{"ageGroup":"40대","gender":"남성","disposition":"경험형","valueOrientation":"실용중시"}',
 @vo_007_b, '전 연애가 끝났다면 연락 자체는 문제가 아닐 수 있지만, 현재 연인이 불편하다고 표현했을 때 어떻게 반응하는지가 더 중요합니다.', NOW()),
('mock_007', '{"ageGroup":"20대","gender":"남성","disposition":"공감형","valueOrientation":"감정우선"}',
 @vo_007_a, '8개월이 됐는데 이런 부분에서 불편함을 느끼는 건 자연스러운 감정이에요. 좁은 사람이 아닌 솔직한 사람인 겁니다.', NOW()),
('mock_007', '{"ageGroup":"30대","gender":"여성","disposition":"직관형","valueOrientation":"신뢰중시"}',
 @vo_007_a, '연락 금지를 요구하기보다 어떤 형태의 연락인지, 상대가 어떻게 반응하는지를 파악하는 게 먼저입니다. 투명성이 핵심이에요.', NOW()),
('mock_007', '{"ageGroup":"40대","gender":"여성","disposition":"균형형","valueOrientation":"관계중시"}',
 @vo_007_b, '현재 관계에 집중하는 모습을 보여달라는 방식으로 접근하는 게 나을 수 있어요. "그 사람과 연락하지 마"보다는 "우리에게 더 집중해줬으면 해"가 더 전달이 잘 됩니다.', NOW()),
('mock_007', '{"ageGroup":"20대","gender":"여성","disposition":"직설형","valueOrientation":"공정중시"}',
 @vo_007_a, '파트너가 불편하다고 하면 배려해주는 게 관계에서 기본 아닐까요? 이건 과도한 요구가 아닙니다.', NOW()),
('mock_007', '{"ageGroup":"50대","gender":"남성","disposition":"경험형","valueOrientation":"실용중시"}',
 @vo_007_b, '이 문제를 계속 덮어두면 결국 더 큰 갈등이 됩니다. 서로 편하게 이야기할 수 있는 환경을 먼저 만드세요.', NOW()),

-- mock_008 (배심원3)
('mock_008', '{"ageGroup":"30대","gender":"여성","disposition":"공감형","valueOrientation":"동의중심"}',
 @vo_008_a, '결혼식 사진 공유는 당사자의 동의가 필수입니다. 예쁘다는 이유로 허락 없이 올리는 건 상대의 의사를 무시한 것입니다.', NOW()),
('mock_008', '{"ageGroup":"20대","gender":"여성","disposition":"공감형","valueOrientation":"친밀감중시"}',
 @vo_008_b, '친구는 축하하는 마음으로 한 행동이었을 거예요. 다만 내려달라는 요청을 받고 바로 내린 건 나쁘지 않은 반응입니다. 사과를 구체적으로 받는 게 남은 과제예요.', NOW()),
('mock_008', '{"ageGroup":"30대","gender":"남성","disposition":"법적사고형","valueOrientation":"권리중시"}',
 @vo_008_a, '초상권은 개인의 기본 권리입니다. 공개 계정에 동의 없이 타인의 사진을 올리는 것은 명백히 잘못된 행동이고, 화가 나는 건 당연합니다.', NOW()),

-- mock_009 없음 (배심원0)

-- mock_010 (배심원1)
('mock_010', '{"ageGroup":"30대","gender":"남성","disposition":"분석형","valueOrientation":"공정중시"}',
 @vo_010_a, '회의에서 비슷한 아이디어가 연이어 나왔다면 팀장이 구별하지 못했을 수도 있습니다. 하지만 침묵했다면 다음번엔 적극적으로 "저도 비슷한 방향으로 생각했는데요"라고 말하세요.', NOW()),

-- mock_011 (배심원3)
('mock_011', '{"ageGroup":"30대","gender":"여성","disposition":"공감형","valueOrientation":"신뢰중시"}',
 @vo_011_a, '과거 연애를 숨기는 것보다 그 이유를 말해주지 않는 것이 더 불안하게 만듭니다. 투명성이 관계의 안정감을 만들어요.', NOW()),
('mock_011', '{"ageGroup":"40대","gender":"남성","disposition":"경험형","valueOrientation":"현재중시"}',
 @vo_011_b, '과거 연애 정보를 공유해야 할 의무는 없습니다. 현재에 집중하려는 의도는 이해할 수 있어요. 다만 파트너가 불안해한다면 안심시켜주는 노력이 필요합니다.', NOW()),
('mock_011', '{"ageGroup":"20대","gender":"여성","disposition":"직관형","valueOrientation":"관계중시"}',
 @vo_011_a, '화를 내며 회피하는 건 오히려 더 큰 의심을 불러일으킵니다. 단순하게라도 "끝난 관계야, 신경 안 써도 돼"라고 말해줬으면 좋았을 거예요.', NOW()),

-- mock_012 (배심원5)
('mock_012', '{"ageGroup":"30대","gender":"여성","disposition":"공감형","valueOrientation":"공정중시"}',
 @vo_012_a, '다 같이 먹고 대표로 결제했으면 N분의 1이 기본입니다. 그 자리에서 항의하지 않으면 나중에 혼자 억울함을 감당해야 해요.', NOW()),
('mock_012', '{"ageGroup":"30대","gender":"남성","disposition":"분석형","valueOrientation":"개인권리"}',
 @vo_012_b, '각자 먹은 것만 내겠다는 주장도 이해할 수 있지만, 그랬다면 처음부터 각자 결제해야 했어요. 편의를 위해 한 명에게 결제를 맡겨놓고 나중에 분리 정산은 앞뒤가 안 맞습니다.', NOW()),
('mock_012', '{"ageGroup":"20대","gender":"여성","disposition":"공감형","valueOrientation":"관계중시"}',
 @vo_012_a, '여행을 준비하고 결제까지 도맡아 한 사람에게 이런 식으로 나오는 건 피로감을 유발합니다. 단순히 돈 문제가 아닌 배려의 문제예요.', NOW()),
('mock_012', '{"ageGroup":"40대","gender":"남성","disposition":"경험형","valueOrientation":"갈등회피"}',
 @vo_012_a, '다음 여행부터는 정산 방식을 미리 합의하세요. 이번 건은 아쉽지만 관계가 더 중요하다면 절충안을 찾는 게 나을 수도 있습니다.', NOW()),
('mock_012', '{"ageGroup":"30대","gender":"여성","disposition":"직설형","valueOrientation":"공정중시"}',
 @vo_012_a, '5명 중 한 명이 이런 식으로 나오면 다음 여행에서 그 사람과 가기 싫어지죠. 명확하게 왜 N분의 1이 공평한지 설명해보세요.', NOW());

-- ============================================================
-- COMMENTS (각 게시글 3~5개 댓글 + 대댓글)
-- ============================================================

-- [mock_001] 부부·육아분담
INSERT INTO post_comments (post_id, parent_comment_id, author_id, body, like_count, status, created_at, updated_at) VALUES
('mock_001', NULL, @u3, '저도 비슷한 상황이었어요. 절대 예민한 게 아니에요. 번아웃 오기 전에 꼭 얘기하세요.', 24, 'ACTIVE', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY),
('mock_001', NULL, @u4, '맞벌이면 주말 가사·육아도 50대50이 기본 아닌가요? 대화로 해결이 안 되면 부부 상담도 방법이에요.', 18, 'ACTIVE', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),
('mock_001', NULL, @u1, '오늘 얘기 꺼내보려고요. 다들 응원해줘서 고마워요.', 31, 'ACTIVE', NOW() - INTERVAL 12 HOUR, NOW() - INTERVAL 12 HOUR),
('mock_001', NULL, @u5, '남편분도 본인이 힘든 건 알지만 표현이 부족하신 것 같아요. 둘 다 힘든 게 맞는데 해법이 필요하네요.', 9, 'ACTIVE', NOW() - INTERVAL 6 HOUR, NOW() - INTERVAL 6 HOUR);

SET @c001_3 = (SELECT id FROM post_comments WHERE post_id='mock_001' AND body LIKE '오늘 얘기%');
INSERT INTO post_comments (post_id, parent_comment_id, author_id, body, like_count, status, created_at, updated_at) VALUES
('mock_001', @c001_3, @u4, '잘 됐으면 좋겠어요. 화이팅!', 7, 'ACTIVE', NOW() - INTERVAL 10 HOUR, NOW() - INTERVAL 10 HOUR),
('mock_001', @c001_3, @u5, '결과 알려주세요. 응원합니다.', 5, 'ACTIVE', NOW() - INTERVAL 9 HOUR, NOW() - INTERVAL 9 HOUR);

-- [mock_002] 직장·보고서 도용
INSERT INTO post_comments (post_id, parent_comment_id, author_id, body, like_count, status, created_at, updated_at) VALUES
('mock_002', NULL, @u1, '저도 입사 초에 이런 일 당했어요. HR에 얘기해봤는데 "원래 그런 거"라고 하더라고요. 증거 남겨두세요.', 42, 'ACTIVE', NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 4 DAY),
('mock_002', NULL, @u2, '팀장한테 직접 "다음번엔 제 기여도 언급해주시면 좋겠습니다"라고 조용히 말해보세요. 적어도 의사 표현은 해야 해요.', 29, 'ACTIVE', NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY),
('mock_002', NULL, @u3, '화가 나도 당연해요. 이건 명백한 크레딧 도용이에요. 이직 생각도 있으시면 이 경험을 포트폴리오 정리 계기로 삼아보세요.', 15, 'ACTIVE', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY),
('mock_002', NULL, @u4, '다음 기획부터는 드래프트 단계에서 메일로 팀장에게 보내서 타임스탬프 남기세요. 나중에 증거가 됩니다.', 38, 'ACTIVE', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY);

SET @c002_4 = (SELECT id FROM post_comments WHERE post_id='mock_002' AND body LIKE '다음 기획부터는%');
INSERT INTO post_comments (post_id, parent_comment_id, author_id, body, like_count, status, created_at, updated_at) VALUES
('mock_002', @c002_4, @u3, '이거 진짜 좋은 팁이에요. 저도 지금부터 해야겠다.', 12, 'ACTIVE', NOW() - INTERVAL 20 HOUR, NOW() - INTERVAL 20 HOUR);

-- [mock_003] 연인·데이트비용
INSERT INTO post_comments (post_id, parent_comment_id, author_id, body, like_count, status, created_at, updated_at) VALUES
('mock_003', NULL, @u1, '1년 동안 참은 거 대단해요. 솔직하게 얘기했는데 변화가 없으면 그게 답이에요.', 19, 'ACTIVE', NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY),
('mock_003', NULL, @u2, '연봉이 낮아도 마음은 보일 수 있잖아요. 커피 한 잔이라도 사줄 수 있는데 그게 없는 게 더 문제인 것 같아요.', 33, 'ACTIVE', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY),
('mock_003', NULL, @u4, '저는 남자 입장인데, 이런 상황 오래 방치하면 관계가 불균형해져요. 파트너가 부담 느끼기 전에 먼저 말했어야 했는데 아쉽네요.', 8, 'ACTIVE', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY);

-- [mock_004] 친구·돈
INSERT INTO post_comments (post_id, parent_comment_id, author_id, body, like_count, status, created_at, updated_at) VALUES
('mock_004', NULL, @u2, '300만원에 2년이면 소액재판 고려해보세요. 법원 가기 전에 내용증명 한 번 보내는 것도 효과가 있어요.', 56, 'ACTIVE', NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 10 DAY),
('mock_004', NULL, @u3, '10년 친구라도 이건 아니에요. 연락까지 끊은 건 이미 마음이 없는 거예요. 스스로를 위해 포기하는 것도 방법이에요.', 44, 'ACTIVE', NOW() - INTERVAL 9 DAY, NOW() - INTERVAL 9 DAY),
('mock_004', NULL, @u1, '제가 작성자예요. 결국 내용증명 보냈어요. 친구가 연락이 왔는데 아직 어렵다고 하더라고요. 조금 더 기다려보려고요.', 28, 'ACTIVE', NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 5 DAY),
('mock_004', NULL, @u5, '사람은 돈 앞에서 본성이 나와요. 이 기회에 상대를 제대로 알게 된 거라고 생각하세요.', 21, 'ACTIVE', NOW() - INTERVAL 7 DAY, NOW() - INTERVAL 7 DAY);

SET @c004_3 = (SELECT id FROM post_comments WHERE post_id='mock_004' AND body LIKE '제가 작성자예요%');
INSERT INTO post_comments (post_id, parent_comment_id, author_id, body, like_count, status, created_at, updated_at) VALUES
('mock_004', @c004_3, @u2, '힘드실 텐데 잘 대응하셨어요. 계속 연락은 유지하세요.', 9, 'ACTIVE', NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 4 DAY),
('mock_004', @c004_3, @u3, '어떻게 됐는지 나중에 알려주세요. 응원해요.', 11, 'ACTIVE', NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY);

-- [mock_005] 가족·명절
INSERT INTO post_comments (post_id, parent_comment_id, author_id, body, like_count, status, created_at, updated_at) VALUES
('mock_005', NULL, @u3, '아내분이랑 미리 얘기해서 "내가 뭘 도와줄까?"를 물어보세요. 직접 들어가는 것보다 아내가 중재해주는 게 나을 수 있어요.', 17, 'ACTIVE', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),
('mock_005', NULL, @u1, '사위가 적극적으로 돕는 걸 부담스러워하는 장모님도 계세요. 아내한테 먼저 물어보는 게 제일 나아요.', 22, 'ACTIVE', NOW() - INTERVAL 18 HOUR, NOW() - INTERVAL 18 HOUR),
('mock_005', NULL, @u4, '남자들끼리 티비 보는 게 여전히 당연한 집이 많아요. 처남들한테 "우리도 설거지라도 하자"고 먼저 이야기해보세요.', 14, 'ACTIVE', NOW() - INTERVAL 12 HOUR, NOW() - INTERVAL 12 HOUR);

-- [mock_006] 직장·야근
INSERT INTO post_comments (post_id, parent_comment_id, author_id, body, like_count, status, created_at, updated_at) VALUES
('mock_006', NULL, @u1, '계약서대로 퇴근하는 건 잘못이 아니에요. 팀장이 불편하면 그건 팀장이 해결해야 할 문제예요.', 67, 'ACTIVE', NOW() - INTERVAL 20 HOUR, NOW() - INTERVAL 20 HOUR),
('mock_006', NULL, @u2, '저도 퇴근하면 눈치 보는 팀에 있었는데 결국 이직했어요. 문화 자체가 바뀌지 않으면 개인이 바꾸기 어려워요.', 43, 'ACTIVE', NOW() - INTERVAL 15 HOUR, NOW() - INTERVAL 15 HOUR),
('mock_006', NULL, @u5, '팀장한테 "퇴근 이후에도 대기해야 하는 업무인가요?"라고 직접 물어보세요. 말로 답할 수 없으면 강요할 근거도 없어요.', 38, 'ACTIVE', NOW() - INTERVAL 10 HOUR, NOW() - INTERVAL 10 HOUR),
('mock_006', NULL, @u4, '이게 정말 힘들어요. 같은 팀으로서 솔직히 불편하긴 한데 그게 강요할 이유는 아니죠.', 5, 'ACTIVE', NOW() - INTERVAL 5 HOUR, NOW() - INTERVAL 5 HOUR);

SET @c006_3 = (SELECT id FROM post_comments WHERE post_id='mock_006' AND body LIKE '팀장한테 "퇴근%');
INSERT INTO post_comments (post_id, parent_comment_id, author_id, body, like_count, status, created_at, updated_at) VALUES
('mock_006', @c006_3, @u3, '이거 진짜 좋은 방법이다. 저도 써봐야겠어요.', 16, 'ACTIVE', NOW() - INTERVAL 8 HOUR, NOW() - INTERVAL 8 HOUR);

-- [mock_007] 연인·전 연인 연락
INSERT INTO post_comments (post_id, parent_comment_id, author_id, body, like_count, status, created_at, updated_at) VALUES
('mock_007', NULL, @u1, '불편하면 불편하다고 말해야 해요. 참으면 혼자 쌓이거든요. 좁은 사람이 아니에요.', 52, 'ACTIVE', NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 5 DAY),
('mock_007', NULL, @u4, '전 연애가 완전히 정리됐는지 여자친구한테 확인하는 게 먼저예요. 연락 자체보다 그 관계의 성격이 중요해요.', 38, 'ACTIVE', NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 4 DAY),
('mock_007', NULL, @u2, '"연락 끊어줘"보다 "나는 이게 불편해"를 먼저 말해보세요. 상대의 반응을 보고 판단하는 게 나아요.', 29, 'ACTIVE', NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY),
('mock_007', NULL, @u3, '저는 전 남자친구랑 연락하는 사람인데, 현재 파트너 불편하다고 하면 끊을 것 같아요. 여친분한테 솔직하게 물어보세요.', 21, 'ACTIVE', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY);

SET @c007_4 = (SELECT id FROM post_comments WHERE post_id='mock_007' AND body LIKE '저는 전 남자친구%');
INSERT INTO post_comments (post_id, parent_comment_id, author_id, body, like_count, status, created_at, updated_at) VALUES
('mock_007', @c007_4, @u5, '이런 솔직한 댓글 감사해요. 도움이 됩니다.', 8, 'ACTIVE', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY);

-- [mock_008] 친구·결혼식 사진
INSERT INTO post_comments (post_id, parent_comment_id, author_id, body, like_count, status, created_at, updated_at) VALUES
('mock_008', NULL, @u2, '화난 거 당연해요. 결혼식 사진은 특히 민감한데 동의 없이 공개 계정에 올리는 건 명백히 잘못이에요.', 44, 'ACTIVE', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),
('mock_008', NULL, @u4, '내린 거 다행이지만 진심 어린 사과 없이 "예쁜데 왜요"가 문제예요. 사과를 요구할 권리가 있어요.', 37, 'ACTIVE', NOW() - INTERVAL 20 HOUR, NOW() - INTERVAL 20 HOUR),
('mock_008', NULL, @u3, '저도 작성자 쪽이에요. 당시에 더 화났지만 사과 받고 나서 좀 풀렸어요. 그래도 앞으로 이런 일 없도록 분명히 말해둘 거예요.', 18, 'ACTIVE', NOW() - INTERVAL 10 HOUR, NOW() - INTERVAL 10 HOUR),
('mock_008', NULL, @u5, '축하하는 마음은 알지만 동의 없이 공유하는 건 배려 부족이에요. 10년 친구라도 이건 설명이 필요해요.', 25, 'ACTIVE', NOW() - INTERVAL 8 HOUR, NOW() - INTERVAL 8 HOUR);

-- [mock_009] 가족·용돈
INSERT INTO post_comments (post_id, parent_comment_id, author_id, body, like_count, status, created_at, updated_at) VALUES
('mock_009', NULL, @u1, '형한테 직접 얘기하기 어려우면 부모님이 공평하게 말씀드릴 수 있도록 부탁드려보세요. 혼자 감당하면 나중에 억울함이 쌓여요.', 31, 'ACTIVE', NOW() - INTERVAL 15 DAY, NOW() - INTERVAL 15 DAY),
('mock_009', NULL, @u2, '이상한 게 아니에요. 능력이 더 있는 쪽이 더 하는 게 맞는데 형이 외면하는 게 문제예요.', 48, 'ACTIVE', NOW() - INTERVAL 14 DAY, NOW() - INTERVAL 14 DAY),
('mock_009', NULL, @u3, '부모님이 형한테 말 못 꺼내시는 이유도 있을 거예요. 혹시 형이 경제적으로 어려운 상황일 수도 있으니 확인해보세요.', 16, 'ACTIVE', NOW() - INTERVAL 12 DAY, NOW() - INTERVAL 12 DAY),
('mock_009', NULL, @u4, '결국 형제간 대화가 필요한 상황이에요. 부모님이 안 계실 때 조용히 물어봐도 좋아요.', 22, 'ACTIVE', NOW() - INTERVAL 10 DAY, NOW() - INTERVAL 10 DAY);

SET @c009_3 = (SELECT id FROM post_comments WHERE post_id='mock_009' AND body LIKE '부모님이 형한테%');
INSERT INTO post_comments (post_id, parent_comment_id, author_id, body, like_count, status, created_at, updated_at) VALUES
('mock_009', @c009_3, @u5, '맞아요. 알고 보면 형이 사업 실패나 다른 이유로 어려울 수도 있어요. 일단 물어보는 게 나을 것 같아요.', 9, 'ACTIVE', NOW() - INTERVAL 9 DAY, NOW() - INTERVAL 9 DAY);

-- [mock_010] 직장·아이디어 도용
INSERT INTO post_comments (post_id, parent_comment_id, author_id, body, like_count, status, created_at, updated_at) VALUES
('mock_010', NULL, @u3, '다음번엔 "저도 비슷한 방향으로 먼저 생각했는데요"라고 바로 말하세요. 침묵하면 나중에 더 억울해요.', 55, 'ACTIVE', NOW() - INTERVAL 22 HOUR, NOW() - INTERVAL 22 HOUR),
('mock_010', NULL, @u1, '회의록을 꼭 남기세요. 이메일로 아이디어 요약을 미리 팀장에게 보내두면 기록이 됩니다.', 41, 'ACTIVE', NOW() - INTERVAL 18 HOUR, NOW() - INTERVAL 18 HOUR),
('mock_010', NULL, @u4, '저도 비슷한 경험 있어요. 동료가 의도적으로 한 건지 타이밍이 겹친 건지 구별이 어렵더라고요. 일단 기록 남기는 게 최선이에요.', 19, 'ACTIVE', NOW() - INTERVAL 12 HOUR, NOW() - INTERVAL 12 HOUR),
('mock_010', NULL, @u5, '회의 때 발언 타이밍을 선점하는 연습도 필요해요. 좋은 아이디어는 더 크고 자신 있게 말하세요.', 28, 'ACTIVE', NOW() - INTERVAL 6 HOUR, NOW() - INTERVAL 6 HOUR);

-- [mock_011] 연인·과거 숨김
INSERT INTO post_comments (post_id, parent_comment_id, author_id, body, like_count, status, created_at, updated_at) VALUES
('mock_011', NULL, @u2, '숨기는 게 아니라 말하기 싫다는 건 다른 문제예요. 근데 화를 내며 회피하는 건 파트너를 더 불안하게 만들어요.', 38, 'ACTIVE', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY),
('mock_011', NULL, @u4, '과거 다 공유해야 할 의무는 없지만 불안해하는 파트너를 안심시키는 건 해줄 수 있잖아요.', 29, 'ACTIVE', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),
('mock_011', NULL, @u5, '저도 작성자 측 파트너예요. 이렇게 불안하게 할 생각은 없었는데 많이 반성하게 됐어요.', 45, 'ACTIVE', NOW() - INTERVAL 16 HOUR, NOW() - INTERVAL 16 HOUR),
('mock_011', NULL, @u3, '파트너의 반응에서 이미 많은 걸 알 수 있어요. 화를 내며 대화를 막는 사람과는 관계 지속 여부를 고민해봐야 해요.', 22, 'ACTIVE', NOW() - INTERVAL 8 HOUR, NOW() - INTERVAL 8 HOUR);

SET @c011_3 = (SELECT id FROM post_comments WHERE post_id='mock_011' AND body LIKE '저도 작성자 측 파트너%');
INSERT INTO post_comments (post_id, parent_comment_id, author_id, body, like_count, status, created_at, updated_at) VALUES
('mock_011', @c011_3, @u5, '솔직하게 말씀해주셔서 감사해요. 오늘 대화 더 해볼게요.', 13, 'ACTIVE', NOW() - INTERVAL 12 HOUR, NOW() - INTERVAL 12 HOUR),
('mock_011', @c011_3, @u2, '이런 댓글 달아주는 파트너 분 대단해요. 잘 해결되길 바랍니다.', 8, 'ACTIVE', NOW() - INTERVAL 10 HOUR, NOW() - INTERVAL 10 HOUR);

-- [mock_012] 친구·여행비용
INSERT INTO post_comments (post_id, parent_comment_id, author_id, body, like_count, status, created_at, updated_at) VALUES
('mock_012', NULL, @u1, '여행 갔다 와서 정산할 때 그런 말 나오면 진짜 허탈하죠. 다음엔 정산 방식 먼저 정하세요.', 47, 'ACTIVE', NOW() - INTERVAL 6 DAY, NOW() - INTERVAL 6 DAY),
('mock_012', NULL, @u2, '"나는 그 식당 안 먹었잖아"는 처음부터 각자 내기로 했을 때나 할 수 있는 말이에요. 도의적으로 이상한 거 맞아요.', 63, 'ACTIVE', NOW() - INTERVAL 5 DAY, NOW() - INTERVAL 5 DAY),
('mock_012', NULL, @u4, '카드 내역 캡처해두고 정확히 얼마씩 내야 하는지 계산해서 보내세요. 할 말 없게 숫자로 정리해주는 게 최선이에요.', 34, 'ACTIVE', NOW() - INTERVAL 4 DAY, NOW() - INTERVAL 4 DAY),
('mock_012', NULL, @u5, '그 친구랑 다음 여행은 없겠네요. 돈 문제로 이렇게 되면 관계도 정리 되는 것 같아요.', 18, 'ACTIVE', NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY),
('mock_012', NULL, @u3, '저도 비슷한 상황 당했는데 그냥 포기했어요. 돈 받는 것보다 스트레스가 더 크더라고요. 그 사람이랑 거리 두는 게 나을 수도 있어요.', 26, 'ACTIVE', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY);

SET @c012_4 = (SELECT id FROM post_comments WHERE post_id='mock_012' AND body LIKE '카드 내역 캡처%');
INSERT INTO post_comments (post_id, parent_comment_id, author_id, body, like_count, status, created_at, updated_at) VALUES
('mock_012', @c012_4, @u1, '이거 진짜 실용적인 조언이에요. 감사합니다.', 11, 'ACTIVE', NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY),
('mock_012', @c012_4, @u2, '숫자로 정리하면 변명할 여지가 없죠. 좋은 방법이에요.', 7, 'ACTIVE', NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY);

SELECT 'Mock 시드 데이터 삽입 완료!' AS result;
SELECT COUNT(*) AS total_posts FROM posts WHERE id LIKE 'mock%';
SELECT COUNT(*) AS total_comments FROM post_comments WHERE post_id LIKE 'mock%';
SELECT COUNT(*) AS total_votes FROM votes WHERE post_id LIKE 'mock%';
SELECT COUNT(*) AS total_jurors FROM jurors WHERE post_id LIKE 'mock%';
