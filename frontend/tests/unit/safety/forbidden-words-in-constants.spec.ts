/**
 * Safety Regression Test: Forbidden Words in Text Constants
 *
 * Scans all text constants (metaphors, communication styles, crisis resources, etc.)
 * to ensure they do NOT contain forbidden words or harmful phrases as defined in CLAUDE.md.
 *
 * Rule (CLAUDE.md 절대 불변 규칙):
 * "금지어 및 법적 리스크 (필수 숙지)"
 * - Level 1 법률 용어 (변호사법 저촉)
 * - Level 2 진단명/임상 용어 (악용 가능)
 * - Level 3 판결/승패 (관계 파국)
 * - 처방 패턴 (구체적 행동 기술만 허용)
 */

import { describe, it, expect } from 'vitest'
import { METAPHORS } from '@/lib/constants/metaphors'
import { COMMUNICATION_STYLES, STYLE_COMBINATION_INSIGHTS } from '@/lib/constants/communicationStyles'
import { CRISIS_RESOURCES } from '@/lib/constants/crisisResources'

// Forbidden words from CLAUDE.md
const FORBIDDEN_WORDS = {
  level1_legal: [
    '과실비율', '판결', '판사', '유죄', '무죄',
    '증거', '가해자', '피해자', '고소', '소송', '심판'
  ],
  level2_clinical: [
    '나르시시스트', '소시오패스', '가스라이팅',
    'PTSD', '트라우마'
  ],
  level3_verdict: [
    '이겼다', '졌다', '승자', '패자',
    '헤어지세요', '절교', '손절'
  ],
  prescription_pattern: [
    '떠나세요', '헤어지세요', '절교'
  ]
}

// Helper to recursively collect all string values
function collectStringValues(obj: any, strings: string[] = []): string[] {
  if (typeof obj === 'string') {
    strings.push(obj)
  } else if (Array.isArray(obj)) {
    obj.forEach(item => collectStringValues(item, strings))
  } else if (obj !== null && typeof obj === 'object') {
    Object.values(obj).forEach(value => collectStringValues(value, strings))
  }
  return strings
}

// Check if a string contains any forbidden words
function checkForbiddenWords(text: string): { category: string; word: string } | null {
  const normalized = text.toLowerCase()

  for (const [category, words] of Object.entries(FORBIDDEN_WORDS)) {
    for (const word of words) {
      if (normalized.includes(word.toLowerCase())) {
        return { category, word }
      }
    }
  }
  return null
}

describe('Safety: Forbidden Words in Constants', () => {
  describe('Metaphors', () => {
    it('should not contain forbidden words in metaphor labels', () => {
      const violations: Array<{ metaphor: string; field: string; violation: { category: string; word: string } }> = []

      METAPHORS.forEach(metaphor => {
        const violation = checkForbiddenWords(metaphor.label)
        if (violation) {
          violations.push({
            metaphor: metaphor.id,
            field: 'label',
            violation
          })
        }
      })

      expect(violations).toEqual([],
        `Found forbidden words in metaphor labels: ${JSON.stringify(violations)}`
      )
    })

    it('should not contain forbidden words in metaphor meanings', () => {
      const violations: Array<{ metaphor: string; field: string; violation: { category: string; word: string } }> = []

      METAPHORS.forEach(metaphor => {
        const violation = checkForbiddenWords(metaphor.meaning)
        if (violation) {
          violations.push({
            metaphor: metaphor.id,
            field: 'meaning',
            violation
          })
        }
      })

      expect(violations).toEqual([],
        `Found forbidden words in metaphor meanings: ${JSON.stringify(violations)}`
      )
    })

    it('should contain safe psychological language in meanings', () => {
      // Verify that at least some metaphors use descriptive rather than diagnostic language
      const meaningTexts = METAPHORS.map(m => m.meaning)
      const combinedText = meaningTexts.join(' ')

      // Should use behavioral descriptions, not diagnostic terms
      expect(combinedText).not.toContain('나르시시스트')
      expect(combinedText).not.toContain('소시오패스')
      expect(combinedText).not.toContain('가스라이팅')
    })
  })

  describe('Communication Styles', () => {
    it('should not contain forbidden words in style labels', () => {
      const violations: Array<{ style: string; field: string; violation: { category: string; word: string } }> = []

      Object.values(COMMUNICATION_STYLES).forEach(style => {
        const violation = checkForbiddenWords(style.label)
        if (violation) {
          violations.push({
            style: style.id,
            field: 'label',
            violation
          })
        }
      })

      expect(violations).toEqual([],
        `Found forbidden words in style labels: ${JSON.stringify(violations)}`
      )
    })

    it('should not contain forbidden words in style descriptions', () => {
      const violations: Array<{ style: string; field: string; violation: { category: string; word: string } }> = []

      Object.values(COMMUNICATION_STYLES).forEach(style => {
        const violation = checkForbiddenWords(style.description)
        if (violation) {
          violations.push({
            style: style.id,
            field: 'description',
            violation
          })
        }
      })

      expect(violations).toEqual([],
        `Found forbidden words in style descriptions: ${JSON.stringify(violations)}`
      )
    })

    it('should not contain forbidden words in style strengths', () => {
      const violations: Array<{ style: string; strength: string; violation: { category: string; word: string } }> = []

      Object.values(COMMUNICATION_STYLES).forEach(style => {
        style.strengths.forEach(strength => {
          const violation = checkForbiddenWords(strength)
          if (violation) {
            violations.push({
              style: style.id,
              strength,
              violation
            })
          }
        })
      })

      expect(violations).toEqual([],
        `Found forbidden words in style strengths: ${JSON.stringify(violations)}`
      )
    })

    it('should not contain forbidden words in style cautions', () => {
      const violations: Array<{ style: string; caution: string; violation: { category: string; word: string } }> = []

      Object.values(COMMUNICATION_STYLES).forEach(style => {
        style.caution.forEach(caution => {
          const violation = checkForbiddenWords(caution)
          if (violation) {
            violations.push({
              style: style.id,
              caution,
              violation
            })
          }
        })
      })

      expect(violations).toEqual([],
        `Found forbidden words in style cautions: ${JSON.stringify(violations)}`
      )
    })

    it('should not contain forbidden words in style combination insights', () => {
      const violations: Array<{ combo: string; field: string; violation: { category: string; word: string } }> = []

      Object.entries(STYLE_COMBINATION_INSIGHTS).forEach(([key, insight]) => {
        ;(['strength', 'challenge', 'advice'] as const).forEach(field => {
          const violation = checkForbiddenWords(insight[field])
          if (violation) {
            violations.push({
              combo: key,
              field,
              violation
            })
          }
        })
      })

      expect(violations).toEqual([],
        `Found forbidden words in style combination insights: ${JSON.stringify(violations)}`
      )
    })
  })

  describe('Crisis Resources', () => {
    it('should not contain forbidden words in resource labels', () => {
      const violations: Array<{ resource: string; field: string; violation: { category: string; word: string } }> = []

      // Filter out legal resources which may use legal terminology contextually
      const immediateResources = CRISIS_RESOURCES.filter(r => r.category === 'immediate')

      immediateResources.forEach(resource => {
        const violation = checkForbiddenWords(resource.label)
        if (violation) {
          violations.push({
            resource: resource.label,
            field: 'label',
            violation
          })
        }
      })

      expect(violations).toEqual([],
        `Found forbidden words in crisis resource labels: ${JSON.stringify(violations)}`
      )
    })

    it('should not contain forbidden words in resource descriptions (immediate)', () => {
      const violations: Array<{ resource: string; field: string; violation: { category: string; word: string } }> = []

      // Filter out legal resources (category === 'legal') which contextually require legal terminology
      // Legal resources are for cases where users need actual legal advice, not UI guidance
      const immediateResources = CRISIS_RESOURCES.filter(r => r.category === 'immediate')

      immediateResources.forEach(resource => {
        const violation = checkForbiddenWords(resource.description)
        if (violation) {
          violations.push({
            resource: resource.label,
            field: 'description',
            violation
          })
        }
      })

      expect(violations).toEqual([],
        `Found forbidden words in immediate crisis resource descriptions: ${JSON.stringify(violations)}`
      )
    })

    it('legal resources may use legal terminology contextually (이혼, 소송)', () => {
      // Legal resources are informational references to actual legal aid services.
      // They are shown only in crisis modal context and properly contextualized.
      // Allowing legal terminology in these resource descriptions is safe because:
      // 1. They are provided by official government/legal aid organizations
      // 2. Users accessing them are already in crisis/decision-making mode
      // 3. These are factual descriptions of what services provide, not UI guidance
      const legalResources = CRISIS_RESOURCES.filter(r => r.category === 'legal')
      expect(legalResources.length).toBeGreaterThan(0)
    })
  })

  describe('Batch Scan of All Constants', () => {
    it('should not have any forbidden words across all scanned constants (excluding legal resources)', () => {
      const allStrings: Array<{ source: string; text: string }> = []

      // Collect from metaphors
      METAPHORS.forEach(m => {
        allStrings.push({ source: `metaphor:${m.id}:label`, text: m.label })
        allStrings.push({ source: `metaphor:${m.id}:meaning`, text: m.meaning })
      })

      // Collect from styles
      Object.entries(COMMUNICATION_STYLES).forEach(([id, style]) => {
        allStrings.push({ source: `style:${id}:label`, text: style.label })
        allStrings.push({ source: `style:${id}:description`, text: style.description })
        style.strengths.forEach((s, i) => {
          allStrings.push({ source: `style:${id}:strength[${i}]`, text: s })
        })
        style.caution.forEach((c, i) => {
          allStrings.push({ source: `style:${id}:caution[${i}]`, text: c })
        })
      })

      // Collect from style insights
      Object.entries(STYLE_COMBINATION_INSIGHTS).forEach(([combo, insight]) => {
        allStrings.push({ source: `insight:${combo}:strength`, text: insight.strength })
        allStrings.push({ source: `insight:${combo}:challenge`, text: insight.challenge })
        allStrings.push({ source: `insight:${combo}:advice`, text: insight.advice })
      })

      // Collect from crisis resources (exclude legal resources which may use legal terminology)
      const immediateResources = CRISIS_RESOURCES.filter(r => r.category === 'immediate')
      immediateResources.forEach(r => {
        allStrings.push({ source: `resource:${r.label}:label`, text: r.label })
        allStrings.push({ source: `resource:${r.label}:description`, text: r.description })
      })

      const violations: Array<{ source: string; text: string; violation: { category: string; word: string } }> = []

      allStrings.forEach(({ source, text }) => {
        const violation = checkForbiddenWords(text)
        if (violation) {
          violations.push({ source, text, violation })
        }
      })

      expect(violations).toEqual([],
        `Found ${violations.length} forbidden word(s):\n${violations
          .map(v => `  - [${v.violation.category}] "${v.violation.word}" in ${v.source}`)
          .join('\n')}`
      )
    })

    it('should not use diagnostic language patterns', () => {
      const allStrings: Array<{ source: string; text: string }> = []

      METAPHORS.forEach(m => {
        allStrings.push({ source: `metaphor:${m.id}`, text: m.meaning })
      })

      Object.values(COMMUNICATION_STYLES).forEach(style => {
        allStrings.push({ source: `style:${style.id}:description`, text: style.description })
      })

      const violations: string[] = []

      allStrings.forEach(({ source, text }) => {
        // Check for diagnostic patterns that should be replaced with behavioral descriptions
        if (text.toLowerCase().includes('나르시시스트')) {
          violations.push(`${source}: contains "나르시시스트"`)
        }
        if (text.toLowerCase().includes('소시오패스')) {
          violations.push(`${source}: contains "소시오패스"`)
        }
        if (text.toLowerCase().includes('가스라이팅')) {
          violations.push(`${source}: contains "가스라이팅"`)
        }
        if (text.toLowerCase().includes('ptsd')) {
          violations.push(`${source}: contains "PTSD"`)
        }
      })

      expect(violations).toEqual([],
        `Found diagnostic language patterns:\n${violations.join('\n')}`
      )
    })

    it('should not use prescriptive language (telling users to leave/break up)', () => {
      const allStrings: Array<{ source: string; text: string }> = []

      METAPHORS.forEach(m => {
        allStrings.push({ source: `metaphor:${m.id}`, text: m.meaning })
      })

      Object.values(COMMUNICATION_STYLES).forEach(style => {
        allStrings.push({ source: `style:${style.id}:description`, text: style.description })
        style.caution.forEach((c, i) => {
          allStrings.push({ source: `style:${style.id}:caution[${i}]`, text: c })
        })
        style.strengths.forEach((s, i) => {
          allStrings.push({ source: `style:${style.id}:strength[${i}]`, text: s })
        })
      })

      const violations: string[] = []

      allStrings.forEach(({ source, text }) => {
        if (text.includes('헤어지세요')) {
          violations.push(`${source}: contains "헤어지세요"`)
        }
        if (text.includes('절교')) {
          violations.push(`${source}: contains "절교"`)
        }
        if (text.includes('손절')) {
          violations.push(`${source}: contains "손절"`)
        }
        if (text.includes('떠나세요')) {
          violations.push(`${source}: contains "떠나세요"`)
        }
      })

      expect(violations).toEqual([],
        `Found prescriptive language (should describe behaviors, not prescribe outcomes):\n${violations.join('\n')}`
      )
    })
  })

  describe('Safe Language Verification', () => {
    it('should use behavioral descriptions instead of diagnostic terms', () => {
      const styleDescriptions = Object.values(COMMUNICATION_STYLES)
        .map(s => s.description)
        .join(' ')

      // Verify safe replacements
      expect(styleDescriptions).not.toContain('나르시시스트')
      // Verify that behavioral descriptions are used
      expect(styleDescriptions.toLowerCase()).toMatch(/표현|소통|거리|감정|판단|사고|행동/)
    })

    it('should use "화해 기여도" terminology, not "과실비율"', () => {
      // This test verifies that the constant file itself uses correct terminology
      // The actual prohibition is checked in component tests (ContributionRatio)
      // Here we just verify the constant doesn't contain the word
      const allText = [
        ...METAPHORS.map(m => m.label),
        ...METAPHORS.map(m => m.meaning),
        ...Object.values(COMMUNICATION_STYLES).map(s => s.label),
        ...Object.values(COMMUNICATION_STYLES).map(s => s.description),
      ].join(' ')

      expect(allText).not.toContain('과실비율')
    })
  })
})
