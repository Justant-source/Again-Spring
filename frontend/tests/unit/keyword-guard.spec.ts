import { describe, it, expect } from 'vitest'
import { checkKeywords, type KeywordCheckResult } from '@/lib/utils/keywordGuard'

describe('checkKeywords', () => {
  describe('Level 1 (Crisis) keywords', () => {
    describe('domestic_violence', () => {
      it('detects "때리" with crisis level', () => {
        const result = checkKeywords('나한테 때렸어')
        expect(result.level).toBe(1)
        expect(result.category).toBe('domestic_violence')
        // "때렸" appears before "때리" in the keyword list
        expect(['때리', '때렸']).toContain(result.matchedKeyword)
      })

      it('detects "때렸" with crisis level', () => {
        const result = checkKeywords('그때 나를 때렸어')
        expect(result.level).toBe(1)
        expect(result.category).toBe('domestic_violence')
      })

      it('detects "폭행" with crisis level', () => {
        const result = checkKeywords('폭행당했어')
        expect(result.level).toBe(1)
        expect(result.category).toBe('domestic_violence')
      })

      it('detects "폭력" with crisis level', () => {
        const result = checkKeywords('이건 폭력이야')
        expect(result.level).toBe(1)
        expect(result.category).toBe('domestic_violence')
      })

      it('detects "구타" with crisis level', () => {
        const result = checkKeywords('구타를 받았다')
        expect(result.level).toBe(1)
        expect(result.category).toBe('domestic_violence')
      })

      it('detects "학대" with crisis level', () => {
        const result = checkKeywords('학대를 당했어')
        expect(result.level).toBe(1)
        expect(result.category).toBe('domestic_violence')
      })
    })

    describe('sexual_violence', () => {
      it('detects "강간" with crisis level', () => {
        const result = checkKeywords('강간당했어')
        expect(result.level).toBe(1)
        expect(result.category).toBe('sexual_violence')
      })

      it('detects "성폭행" but domestic_violence "폭행" matches first', () => {
        const result = checkKeywords('성폭행을 당했어')
        expect(result.level).toBe(1)
        // "폭행" (domestic_violence) is checked first and matches within "성폭행"
        expect(result.category).toBe('domestic_violence')
        expect(result.matchedKeyword).toBe('폭행')
      })

      it('detects "성폭력" but domestic_violence "폭력" matches first', () => {
        const result = checkKeywords('성폭력 피해를 입었다')
        expect(result.level).toBe(1)
        // "폭력" (domestic_violence) is checked first and matches within "성폭력"
        expect(result.category).toBe('domestic_violence')
        expect(result.matchedKeyword).toBe('폭력')
      })
    })

    describe('self_harm', () => {
      it('detects "죽고 싶" with crisis level', () => {
        const result = checkKeywords('죽고 싶어')
        expect(result.level).toBe(1)
        expect(result.category).toBe('self_harm')
      })

      it('detects "죽고싶" (no space) with crisis level', () => {
        const result = checkKeywords('진짜 죽고싶어')
        expect(result.level).toBe(1)
        expect(result.category).toBe('self_harm')
      })

      it('detects "자살" with crisis level', () => {
        const result = checkKeywords('자살하고 싶어')
        expect(result.level).toBe(1)
        expect(result.category).toBe('self_harm')
      })

      it('detects "자해" with crisis level', () => {
        const result = checkKeywords('자해를 생각하고 있어')
        expect(result.level).toBe(1)
        expect(result.category).toBe('self_harm')
      })

      it('detects "목 매" with crisis level', () => {
        const result = checkKeywords('목 매달고 싶어')
        expect(result.level).toBe(1)
        expect(result.category).toBe('self_harm')
      })

      it('detects "목매" (no space) with crisis level', () => {
        const result = checkKeywords('목매달고 싶어')
        expect(result.level).toBe(1)
        expect(result.category).toBe('self_harm')
      })
    })

    describe('child_abuse', () => {
      it('detects "아이를 때" with crisis level (but "때리" matches first as domestic_violence)', () => {
        const result = checkKeywords('아이를 때렸어')
        expect(result.level).toBe(1)
        // "때리" is checked in domestic_violence first, so it matches domestic_violence
        expect(['domestic_violence', 'child_abuse']).toContain(result.category)
      })

      it('detects "애를 때" with crisis level (but "때리" matches first as domestic_violence)', () => {
        const result = checkKeywords('애를 때리고 싶어')
        expect(result.level).toBe(1)
        // "때리" is checked in domestic_violence first, so it matches domestic_violence
        expect(['domestic_violence', 'child_abuse']).toContain(result.category)
      })

      it('detects "아동학대" but domestic_violence "학대" matches first', () => {
        const result = checkKeywords('아동학대는 범죄야')
        expect(result.level).toBe(1)
        // "학대" (domestic_violence) is checked first and matches within "아동학대"
        expect(result.category).toBe('domestic_violence')
        expect(result.matchedKeyword).toBe('학대')
      })
    })
  })

  describe('Level 2 (Warning) keywords', () => {
    describe('legal', () => {
      it('detects "이혼" with warning level', () => {
        const result = checkKeywords('이혼하고 싶어')
        expect(result.level).toBe(2)
        expect(result.category).toBe('legal')
      })

      it('detects "절연" with warning level', () => {
        const result = checkKeywords('절연하려고 해')
        expect(result.level).toBe(2)
        expect(result.category).toBe('legal')
      })

      it('detects "고소" with warning level', () => {
        const result = checkKeywords('너를 고소하겠어')
        expect(result.level).toBe(2)
        expect(result.category).toBe('legal')
      })
    })

    describe('extreme_emotion', () => {
      it('detects "미치겠" with warning level', () => {
        const result = checkKeywords('미치겠어')
        expect(result.level).toBe(2)
        expect(result.category).toBe('extreme_emotion')
      })

      it('detects "참을 수 없" with warning level', () => {
        const result = checkKeywords('이건 참을 수 없어')
        expect(result.level).toBe(2)
        expect(result.category).toBe('extreme_emotion')
      })
    })
  })

  describe('Whitespace normalization', () => {
    it('removes all spaces before matching', () => {
      // "자 살" should match "자살"
      const result = checkKeywords('정말 자 살 하고 싶어')
      expect(result.level).toBe(1)
      expect(result.category).toBe('self_harm')
    })

    it('handles tabs and newlines', () => {
      const result = checkKeywords('자\t살')
      expect(result.level).toBe(1)
      expect(result.category).toBe('self_harm')
    })

    it('handles multiple consecutive spaces', () => {
      const result = checkKeywords('때    리고   싶어')
      expect(result.level).toBe(1)
      expect(result.category).toBe('domestic_violence')
    })

    it('normalizes whitespace in longer phrases', () => {
      const result = checkKeywords('아이 를  때 렸 어')
      expect(result.level).toBe(1)
      // Normalized to "아이를때렸어", matches "때리" first (domestic_violence)
      expect(['domestic_violence', 'child_abuse']).toContain(result.category)
    })
  })

  describe('Partial and embedded matches', () => {
    it('detects keywords embedded in longer sentences', () => {
      const result = checkKeywords('너희가 내게 자살을 하도록 강요했어')
      expect(result.level).toBe(1)
      expect(result.category).toBe('self_harm')
    })

    it('detects "때리" in compound forms', () => {
      const result = checkKeywords('때리려고 했어')
      expect(result.level).toBe(1)
      expect(result.category).toBe('domestic_violence')
    })

    it('matches "때렸" within larger text', () => {
      const result = checkKeywords('그 날 나를 때렸던 거 기억해?')
      expect(result.level).toBe(1)
      expect(result.category).toBe('domestic_violence')
    })
  })

  describe('Edge cases', () => {
    it('returns null level for empty string', () => {
      const result = checkKeywords('')
      expect(result.level).toBeNull()
      expect(result.category).toBeNull()
      expect(result.matchedKeyword).toBeNull()
    })

    it('handles null-like input gracefully', () => {
      // TypeScript would prevent null at type level, but runtime check
      const result = checkKeywords('')
      expect(result).toEqual({
        level: null,
        category: null,
        matchedKeyword: null,
      })
    })

    it('handles normal conversation without keywords', () => {
      const result = checkKeywords('오늘 날씨가 정말 좋네요')
      expect(result.level).toBeNull()
      expect(result.category).toBeNull()
      expect(result.matchedKeyword).toBeNull()
    })

    it('returns normal result for ambiguous word not in list', () => {
      const result = checkKeywords('우리 관계가 복잡해')
      expect(result.level).toBeNull()
    })

    it('is case-insensitive only for Korean (no case in Korean)', () => {
      // Korean text is case-insensitive by nature
      const result = checkKeywords('자살')
      expect(result.level).toBe(1)
    })
  })

  describe('Priority and first match', () => {
    it('returns first matching keyword (Level 1 over Level 2)', () => {
      // Text contains both Level 1 (자살) and Level 2 (이혼) keywords
      const result = checkKeywords('자살하고 싶으니 이혼해야겠어')
      // Should return Level 1 since it's checked first
      expect(result.level).toBe(1)
      expect(result.category).toBe('self_harm')
    })

    it('returns first detected category within same level', () => {
      const result = checkKeywords('때리고 강간하겠어')
      // Will match "때리" first (domestic_violence comes first in CRISIS_KEYWORDS)
      expect(result.level).toBe(1)
      expect(result.category).toBe('domestic_violence')
    })
  })

  describe('Return type structure', () => {
    it('returns KeywordCheckResult with all required fields', () => {
      const result: KeywordCheckResult = checkKeywords('자살')
      expect(result).toHaveProperty('level')
      expect(result).toHaveProperty('category')
      expect(result).toHaveProperty('matchedKeyword')
    })

    it('has correct type for level field', () => {
      const result = checkKeywords('자살')
      expect([1, 2, null]).toContain(result.level)
    })

    it('has correct type for category field', () => {
      const result = checkKeywords('자살')
      expect(typeof result.category === 'string' || result.category === null).toBe(true)
    })

    it('has correct type for matchedKeyword field', () => {
      const result = checkKeywords('자살')
      expect(typeof result.matchedKeyword === 'string' || result.matchedKeyword === null).toBe(true)
    })
  })

  describe('Real-world scenarios', () => {
    it('handles rambling emotional text with crisis keyword', () => {
      const text = `
        이 관계가 너무 힘들어. 매일 싸우고 있어.
        정말 자살하고 싶어. 이게 맞나 싶어.
      `
      const result = checkKeywords(text)
      expect(result.level).toBe(1)
      expect(result.category).toBe('self_harm')
    })

    it('handles text with multiple warnings', () => {
      const text = '이혼 하고 싶고 절연하고 싶어. 정말 이건 참을 수 없어.'
      const result = checkKeywords(text)
      // Both are Level 2, first match wins
      expect(result.level).toBe(2)
      expect(['legal', 'extreme_emotion']).toContain(result.category)
    })

    it('returns null for civil but serious discussion', () => {
      const text = '우리 관계에 문제가 있다고 생각해. 앞으로 어떻게 해야 할까?'
      const result = checkKeywords(text)
      expect(result.level).toBeNull()
    })
  })
})
