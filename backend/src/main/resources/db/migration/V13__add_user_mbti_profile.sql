ALTER TABLE users
    ADD COLUMN mbti_profile JSON NULL COMMENT 'MBTI 4축 비율 {e_i,s_n,t_f,j_p} 0~100';
