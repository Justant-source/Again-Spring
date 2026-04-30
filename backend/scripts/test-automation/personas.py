# backend/scripts/test-automation/personas.py

PERSONAS = [
    {"email": "test1@again.com", "password": "test123", "nickname": "서영",
     "age": 28, "gender": "여", "style": "분석적·길게", "input_pattern": "60-120자·텀길음"},
    {"email": "test2@again.com", "password": "test123", "nickname": "지훈",
     "age": 35, "gender": "남", "style": "짧고무뚝뚝", "input_pattern": "20-50자·빠른연속"},
    {"email": "test3@again.com", "password": "test123", "nickname": "수민",
     "age": 24, "gender": "여", "style": "MZ톤", "input_pattern": "짧고빠르게"},
    {"email": "test4@again.com", "password": "test123", "nickname": "정현",
     "age": 42, "gender": "여", "style": "직설적", "input_pattern": "중간길이·결론빠름"},
    {"email": "test5@again.com", "password": "test123", "nickname": "민수",
     "age": 31, "gender": "남", "style": "분석적", "input_pattern": "긴메시지·텀김"},
    {"email": "test6@again.com", "password": "test123", "nickname": "다현",
     "age": 19, "gender": "여", "style": "어른과거리감", "input_pattern": "짧고머뭇거림"},
    {"email": "test7@again.com", "password": "test123", "nickname": "영희",
     "age": 55, "gender": "여", "style": "노년·느림", "input_pattern": "긴메시지·매우긴텀"},
    {"email": "test8@again.com", "password": "test123", "nickname": "동현",
     "age": 27, "gender": "남", "style": "화잘냄", "input_pattern": "짧고격함"},
    {"email": "test9@again.com", "password": "test123", "nickname": "지영",
     "age": 33, "gender": "여", "style": "우울톤·답늦음", "input_pattern": "짧음·매우긴텀"},
    {"email": "test10@again.com", "password": "test123", "nickname": "태우",
     "age": 38, "gender": "남", "style": "폭주형", "input_pattern": "긴메시지또는폭주"},
]

PERSONA_MAP = {p["email"]: p for p in PERSONAS}
