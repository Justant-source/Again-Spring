-- persona-diversity-v4 후속(2026-09-06): 직군(job_field) 축 신설.
--
-- 배경: job_type은 조직 형태(대기업·중견·스타트업·공공·전문직·자영업·프리랜서·구직·육아휴직)만
-- 나누고 "무슨 일을 하는가"를 정하지 않았다. 그래서 프로필 생성 LLM이 job_title을 자유롭게
-- 지어냈고, prod 실측에서 6명 중 4명이 마케팅·구매팀으로 몰렸다. 직군을 명시적 쿼터 축으로
-- 올려 개발·디자인·사무·제조·건설·영업·회계·의료·교육·물류·서비스·연구로 갈라놓는다.
ALTER TABLE personas
    ADD COLUMN job_field VARCHAR(24) NULL COMMENT '직군(DEV/DESIGN/OFFICE/MANUFACTURING/CONSTRUCTION/SALES/FINANCE/HEALTHCARE/EDUCATION/LOGISTICS/SERVICE/RESEARCH). NULL이면 미배정 — 재생성 대상.';
