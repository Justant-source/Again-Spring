/**
 * 실 BE 테스트 페르소나 정의.
 * BE 권위본: backend/scripts/test-automation/personas.py — 변경 시 함께 갱신.
 * SeedDataLoader(@Profile dev)가 dev 스택 시작 시 자동 시드.
 * 전원 초기 roles=["USER"]; TESTER/ADMIN은 global-setup이 부여.
 */
export interface Persona {
  email: string
  password: string
  nickname: string
  age: number
  gender: string
  roles: string[]
}

export const PERSONAS: Persona[] = [
  { email: 'test1@again.com', password: 'test123', nickname: '서영', age: 28, gender: '여', roles: ['USER'] },
  { email: 'test2@again.com', password: 'test123', nickname: '지훈', age: 35, gender: '남', roles: ['USER'] },
  { email: 'test3@again.com', password: 'test123', nickname: '수민', age: 24, gender: '여', roles: ['USER'] },
  { email: 'test4@again.com', password: 'test123', nickname: '정현', age: 42, gender: '여', roles: ['USER'] },
  { email: 'test5@again.com', password: 'test123', nickname: '민수', age: 31, gender: '남', roles: ['USER'] },
  { email: 'test6@again.com', password: 'test123', nickname: '다현', age: 19, gender: '여', roles: ['USER'] },
  { email: 'test7@again.com', password: 'test123', nickname: '영희', age: 55, gender: '여', roles: ['USER'] },
  { email: 'test8@again.com', password: 'test123', nickname: '동현', age: 27, gender: '남', roles: ['USER'] },
  { email: 'test9@again.com', password: 'test123', nickname: '지영', age: 33, gender: '여', roles: ['USER'] },
  { email: 'test10@again.com', password: 'test123', nickname: '태우', age: 38, gender: '남', roles: ['USER'] },
]

/** 기본 Solo 테스트 페르소나 */
export const PERSONA_TEST1 = PERSONAS[0]
/** Duo 테스트용 A (globalSetup이 TESTER 부여) */
export const PERSONA_TESTER_A = PERSONAS[1]
/** Duo 테스트용 B (globalSetup이 TESTER 부여) */
export const PERSONA_TESTER_B = PERSONAS[2]

export const PERSONA_MAP = Object.fromEntries(PERSONAS.map((p) => [p.email, p]))

/**
 * globalSetup이 storageState를 저장하는 페르소나만 선별.
 * - test1: Solo 불변 spec (crisis-modal, crisis-dual-defense, contribution-ratio)
 * - test2 (TESTER_A), test3 (TESTER_B): Duo 격리 spec
 * - test5: flows/02-permissions (/admin 접근 registered user 테스트)
 * Rate Limit(5/min): 4개 × 13s 간격 → 안전
 */
export const PRELOGIN_PERSONAS: Persona[] = [
  PERSONAS[0], // test1
  PERSONAS[1], // test2
  PERSONAS[2], // test3
  PERSONAS[4], // test5
]
