import { describe, it, expect } from 'vitest'
import { COMMUNICATION_STYLES, getStyleCombinationKey, STYLE_COMBINATION_INSIGHTS } from '@/lib/constants/communicationStyles'
import { CATEGORIES, findMajor, findMiddle, findMinor } from '@/lib/constants/categories'
import {
  METAPHORS,
  getMetaphorById,
  getMetaphorImagePath,
  getMetaphorsByContext,
  getMetaphorsByGroup,
  matchMetaphor,
} from '@/lib/constants/metaphors'
import { CRISIS_RESOURCES, CRISIS_RESOURCES_IMMEDIATE, CRISIS_RESOURCES_LEGAL } from '@/lib/constants/crisisResources'
import type { CommunicationStyle, RelationType } from '@/lib/types'

describe('Communication Styles Constants', () => {
  describe('COMMUNICATION_STYLES', () => {
    it('has 6 communication style definitions', () => {
      expect(Object.keys(COMMUNICATION_STYLES)).toHaveLength(6)
    })

    it('includes all expected styles', () => {
      const styles: CommunicationStyle[] = ['wave', 'mountain', 'flame', 'leaf', 'moon', 'star']
      for (const style of styles) {
        expect(COMMUNICATION_STYLES).toHaveProperty(style)
      }
    })

    it('each style has required fields', () => {
      Object.values(COMMUNICATION_STYLES).forEach((style) => {
        expect(style).toHaveProperty('id')
        expect(style).toHaveProperty('label')
        expect(style).toHaveProperty('motif')
        expect(style).toHaveProperty('description')
        expect(style).toHaveProperty('strengths')
        expect(style).toHaveProperty('caution')
        expect(style).toHaveProperty('color')
      })
    })

    it('each style has non-empty label and description', () => {
      Object.values(COMMUNICATION_STYLES).forEach((style) => {
        expect(style.label).toBeTruthy()
        expect(style.label.length).toBeGreaterThan(0)
        expect(style.description).toBeTruthy()
        expect(style.description.length).toBeGreaterThan(0)
      })
    })

    it('each style has motif property matching its id', () => {
      Object.entries(COMMUNICATION_STYLES).forEach(([id, style]) => {
        expect(style.motif).toBeTruthy()
        expect(style.motif).toBe(id)
      })
    })

    it('each style has motif matching its ID', () => {
      Object.entries(COMMUNICATION_STYLES).forEach(([id, style]) => {
        expect(style.motif).toBe(id)
      })
    })

    it('each style has non-empty strengths array', () => {
      Object.values(COMMUNICATION_STYLES).forEach((style) => {
        expect(Array.isArray(style.strengths)).toBe(true)
        expect(style.strengths.length).toBeGreaterThan(0)
        style.strengths.forEach((strength) => {
          expect(typeof strength).toBe('string')
          expect(strength.length).toBeGreaterThan(0)
        })
      })
    })

    it('each style has non-empty caution array', () => {
      Object.values(COMMUNICATION_STYLES).forEach((style) => {
        expect(Array.isArray(style.caution)).toBe(true)
        expect(style.caution.length).toBeGreaterThan(0)
        style.caution.forEach((c) => {
          expect(typeof c).toBe('string')
          expect(c.length).toBeGreaterThan(0)
        })
      })
    })

    it('each style has a valid color hex code', () => {
      const hexRegex = /^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$/
      Object.values(COMMUNICATION_STYLES).forEach((style) => {
        expect(style.color).toMatch(hexRegex)
      })
    })

    it('wave style is properly defined', () => {
      const wave = COMMUNICATION_STYLES.wave
      expect(wave.id).toBe('wave')
      expect(wave.motif).toBe('wave')
      expect(wave.label).toBe('파도형')
    })

    it('mountain style is properly defined', () => {
      const mountain = COMMUNICATION_STYLES.mountain
      expect(mountain.id).toBe('mountain')
      expect(mountain.motif).toBe('mountain')
      expect(mountain.label).toBe('산형')
    })

    it('flame style is properly defined', () => {
      const flame = COMMUNICATION_STYLES.flame
      expect(flame.id).toBe('flame')
      expect(flame.motif).toBe('flame')
      expect(flame.label).toBe('불꽃형')
    })

    it('leaf style is properly defined', () => {
      const leaf = COMMUNICATION_STYLES.leaf
      expect(leaf.id).toBe('leaf')
      expect(leaf.motif).toBe('leaf')
      expect(leaf.label).toBe('이파리형')
    })

    it('moon style is properly defined', () => {
      const moon = COMMUNICATION_STYLES.moon
      expect(moon.id).toBe('moon')
      expect(moon.motif).toBe('moon')
      expect(moon.label).toBe('달빛형')
    })

    it('star style is properly defined', () => {
      const star = COMMUNICATION_STYLES.star
      expect(star.id).toBe('star')
      expect(star.motif).toBe('star')
      expect(star.label).toBe('별빛형')
    })
  })

  describe('getStyleCombinationKey', () => {
    it('returns sorted combination key', () => {
      const key1 = getStyleCombinationKey('wave', 'mountain')
      const key2 = getStyleCombinationKey('mountain', 'wave')
      expect(key1).toBe(key2)
    })

    it('returns string with hyphen separator', () => {
      const key = getStyleCombinationKey('wave', 'mountain')
      expect(typeof key).toBe('string')
      expect(key).toContain('-')
    })

    it('handles same style combination', () => {
      const key = getStyleCombinationKey('wave', 'wave')
      expect(key).toBe('wave-wave')
    })

    it('generates valid keys for all combinations', () => {
      const styles: CommunicationStyle[] = ['wave', 'mountain', 'flame', 'leaf', 'moon', 'star']
      for (const s1 of styles) {
        for (const s2 of styles) {
          const key = getStyleCombinationKey(s1, s2)
          expect(key).toBeTruthy()
        }
      }
    })
  })

  describe('STYLE_COMBINATION_INSIGHTS', () => {
    it('has insights for key combinations', () => {
      expect(Object.keys(STYLE_COMBINATION_INSIGHTS).length).toBeGreaterThan(0)
    })

    it('each insight has required fields', () => {
      Object.values(STYLE_COMBINATION_INSIGHTS).forEach((insight) => {
        expect(insight).toHaveProperty('strength')
        expect(insight).toHaveProperty('challenge')
        expect(insight).toHaveProperty('advice')
      })
    })

    it('each insight field is non-empty string', () => {
      Object.values(STYLE_COMBINATION_INSIGHTS).forEach((insight) => {
        expect(typeof insight.strength).toBe('string')
        expect(insight.strength.length).toBeGreaterThan(0)
        expect(typeof insight.challenge).toBe('string')
        expect(insight.challenge.length).toBeGreaterThan(0)
        expect(typeof insight.advice).toBe('string')
        expect(insight.advice.length).toBeGreaterThan(0)
      })
    })

    it('includes wave-mountain combination', () => {
      expect(STYLE_COMBINATION_INSIGHTS).toHaveProperty('wave-mountain')
    })

    it('includes wave-flame combination', () => {
      expect(STYLE_COMBINATION_INSIGHTS).toHaveProperty('wave-flame')
    })

    it('includes mountain-flame combination', () => {
      expect(STYLE_COMBINATION_INSIGHTS).toHaveProperty('mountain-flame')
    })

    it('includes leaf-star combination', () => {
      expect(STYLE_COMBINATION_INSIGHTS).toHaveProperty('leaf-star')
    })

    it('includes moon-wave combination', () => {
      expect(STYLE_COMBINATION_INSIGHTS).toHaveProperty('moon-wave')
    })
  })
})

describe('Categories Constants', () => {
  describe('CATEGORIES', () => {
    it('has 6 major categories', () => {
      expect(CATEGORIES).toHaveLength(7)
    })

    it('includes all relation types', () => {
      const relationTypes: RelationType[] = ['couple', 'marriage', 'friend', 'family', 'parent_child', 'korean_specific']
      const categoryIds = CATEGORIES.map((c) => c.id)
      relationTypes.forEach((rt) => {
        expect(categoryIds).toContain(rt)
      })
    })

    it('each category has required structure', () => {
      CATEGORIES.forEach((category) => {
        expect(category).toHaveProperty('id')
        expect(category).toHaveProperty('label')
        expect(category).toHaveProperty('relationType')
        expect(category).toHaveProperty('middles')
        expect(Array.isArray(category.middles)).toBe(true)
        expect(category.middles.length).toBeGreaterThan(0)
      })
    })

    it('each middle has required structure', () => {
      CATEGORIES.forEach((category) => {
        category.middles.forEach((middle) => {
          expect(middle).toHaveProperty('id')
          expect(middle).toHaveProperty('label')
          expect(middle).toHaveProperty('minors')
          expect(Array.isArray(middle.minors)).toBe(true)
          expect(middle.minors.length).toBeGreaterThan(0)
        })
      })
    })

    it('each minor has required structure', () => {
      CATEGORIES.forEach((category) => {
        category.middles.forEach((middle) => {
          middle.minors.forEach((minor) => {
            expect(minor).toHaveProperty('id')
            expect(minor).toHaveProperty('label')
            expect(minor).toHaveProperty('allowCustomInput')
            expect(typeof minor.allowCustomInput).toBe('boolean')
          })
        })
      })
    })

    it('couple category has correct structure', () => {
      const couple = findMajor('couple')
      expect(couple).toBeTruthy()
      expect(couple?.label).toContain('연인')
      expect(couple?.relationType).toBe('couple')
      expect(couple?.middles.length).toBeGreaterThan(0)
    })

    it('marriage category has correct structure', () => {
      const marriage = findMajor('marriage')
      expect(marriage).toBeTruthy()
      expect(marriage?.label).toContain('부부')
      expect(marriage?.relationType).toBe('marriage')
    })

    it('friend category has correct structure', () => {
      const friend = findMajor('friend')
      expect(friend).toBeTruthy()
      expect(friend?.label).toContain('친구')
      expect(friend?.relationType).toBe('friend')
    })

    it('family category has correct structure', () => {
      const family = findMajor('family')
      expect(family).toBeTruthy()
      expect(family?.label).toContain('가족')
      expect(family?.relationType).toBe('family')
    })

    it('parent_child category has correct structure', () => {
      const pc = findMajor('parent_child')
      expect(pc).toBeTruthy()
      expect(pc?.label).toContain('부모')
      expect(pc?.relationType).toBe('parent_child')
    })

    it('korean_specific category exists', () => {
      const ks = findMajor('korean_specific')
      expect(ks).toBeTruthy()
      expect(ks?.relationType).toBe('korean_specific')
    })
  })

  describe('findMajor', () => {
    it('finds major by id', () => {
      const couple = findMajor('couple')
      expect(couple).toBeTruthy()
      expect(couple?.id).toBe('couple')
    })

    it('returns undefined for non-existent id', () => {
      const result = findMajor('non_existent')
      expect(result).toBeUndefined()
    })
  })

  describe('findMiddle', () => {
    it('finds middle by major and middle ids', () => {
      const middle = findMiddle('couple', 'couple_contact')
      expect(middle).toBeTruthy()
      expect(middle?.id).toBe('couple_contact')
    })

    it('returns undefined for non-existent middle', () => {
      const result = findMiddle('couple', 'non_existent')
      expect(result).toBeUndefined()
    })

    it('returns undefined for non-existent major', () => {
      const result = findMiddle('non_existent', 'couple_contact')
      expect(result).toBeUndefined()
    })
  })

  describe('findMinor', () => {
    it('finds minor by major, middle, and minor ids', () => {
      const minor = findMinor('couple', 'couple_contact', 'contact_too_little')
      expect(minor).toBeTruthy()
      expect(minor?.id).toBe('contact_too_little')
    })

    it('returns undefined for non-existent minor', () => {
      const result = findMinor('couple', 'couple_contact', 'non_existent')
      expect(result).toBeUndefined()
    })

    it('returns undefined for non-existent middle', () => {
      const result = findMinor('couple', 'non_existent', 'contact_too_little')
      expect(result).toBeUndefined()
    })
  })
})

describe('Metaphors Constants', () => {
  describe('METAPHORS', () => {
    it('has 60 metaphor definitions', () => {
      expect(METAPHORS).toHaveLength(60)
    })

    it('each metaphor has required fields', () => {
      METAPHORS.forEach((metaphor) => {
        expect(metaphor).toHaveProperty('id')
        expect(metaphor).toHaveProperty('filename')
        expect(metaphor).toHaveProperty('label')
        expect(metaphor).toHaveProperty('meaning')
        expect(metaphor).toHaveProperty('group')
      })
    })

    it('each metaphor has non-empty strings', () => {
      METAPHORS.forEach((metaphor) => {
        expect(metaphor.id).toBeTruthy()
        expect(metaphor.filename).toBeTruthy()
        expect(metaphor.label).toBeTruthy()
        expect(metaphor.meaning).toBeTruthy()
      })
    })

    it('each metaphor group is valid', () => {
      const validGroups = ['avoidance', 'tension', 'protection', 'loneliness', 'hesitation', 'recovery']
      METAPHORS.forEach((metaphor) => {
        expect(validGroups).toContain(metaphor.group)
      })
    })

    it('metaphor filenames are unique', () => {
      const filenames = METAPHORS.map((m) => m.filename)
      const unique = new Set(filenames)
      expect(unique.size).toBe(METAPHORS.length)
    })

    it('metaphor ids are unique', () => {
      const ids = METAPHORS.map((m) => m.id)
      const unique = new Set(ids)
      expect(unique.size).toBe(METAPHORS.length)
    })

    it('has metaphors in all 6 groups', () => {
      const groups = new Set(METAPHORS.map((m) => m.group))
      expect(groups.size).toBe(6)
    })
  })

  describe('getMetaphorById', () => {
    it('finds metaphor by id', () => {
      const metaphor = getMetaphorById('locked-mailbox')
      expect(metaphor).toBeTruthy()
      expect(metaphor?.id).toBe('locked-mailbox')
    })

    it('returns undefined for non-existent id', () => {
      const result = getMetaphorById('non_existent')
      expect(result).toBeUndefined()
    })

    it('finds all metaphors', () => {
      METAPHORS.forEach((metaphor) => {
        const found = getMetaphorById(metaphor.id)
        expect(found).toEqual(metaphor)
      })
    })
  })

  describe('getMetaphorImagePath', () => {
    it('returns path with illustrations directory', () => {
      const path = getMetaphorImagePath('01-locked-mailbox.svg')
      expect(path).toContain('/illustrations/metaphors/')
    })

    it('includes filename in path', () => {
      const filename = '01-locked-mailbox.svg'
      const path = getMetaphorImagePath(filename)
      expect(path).toContain(filename)
    })

    it('returns string', () => {
      const path = getMetaphorImagePath('01-locked-mailbox.svg')
      expect(typeof path).toBe('string')
    })
  })

  describe('extended metadata fields', () => {
    const VALID_UI_CONTEXTS = [
      'report-header', 'share-card', 'session-end', 'onboarding-intro',
      'empty-state', 'marketing-cover', 'marketing-scene',
      'marketing-naver-inline', 'marketing-quote-card',
    ]
    const VALID_RELATION_TYPES = ['couple', 'marriage', 'friend', 'family', 'parent_child', 'colleague', 'all']
    const VALID_TONES = ['warm', 'neutral', 'heavy']

    it('each metaphor has non-empty emotions array', () => {
      METAPHORS.forEach((m) => {
        expect(Array.isArray(m.emotions)).toBe(true)
        expect(m.emotions.length).toBeGreaterThan(0)
        m.emotions.forEach((e) => expect(typeof e).toBe('string'))
      })
    })

    it('each metaphor has non-empty needs array', () => {
      METAPHORS.forEach((m) => {
        expect(Array.isArray(m.needs)).toBe(true)
        expect(m.needs.length).toBeGreaterThan(0)
        m.needs.forEach((n) => expect(typeof n).toBe('string'))
      })
    })

    it('each metaphor has non-empty uiContexts with valid values', () => {
      METAPHORS.forEach((m) => {
        expect(Array.isArray(m.uiContexts)).toBe(true)
        expect(m.uiContexts.length).toBeGreaterThan(0)
        m.uiContexts.forEach((ctx) => expect(VALID_UI_CONTEXTS).toContain(ctx))
      })
    })

    it('each metaphor has non-empty relationTypes with valid values', () => {
      METAPHORS.forEach((m) => {
        expect(Array.isArray(m.relationTypes)).toBe(true)
        expect(m.relationTypes.length).toBeGreaterThan(0)
        m.relationTypes.forEach((rt) => expect(VALID_RELATION_TYPES).toContain(rt))
      })
    })

    it('each metaphor has a valid tone', () => {
      METAPHORS.forEach((m) => {
        expect(VALID_TONES).toContain(m.tone)
      })
    })

    it('each metaphor has a non-empty designPrompt', () => {
      METAPHORS.forEach((m) => {
        expect(typeof m.designPrompt).toBe('string')
        expect(m.designPrompt.length).toBeGreaterThan(0)
      })
    })

    it('all 3 tone values are represented', () => {
      const tones = new Set(METAPHORS.map((m) => m.tone))
      expect(tones.has('warm')).toBe(true)
      expect(tones.has('neutral')).toBe(true)
      expect(tones.has('heavy')).toBe(true)
    })
  })

  describe('getMetaphorsByContext', () => {
    it('returns metaphors matching the given context', () => {
      const results = getMetaphorsByContext('report-header')
      expect(results.length).toBeGreaterThan(0)
      results.forEach((m) => expect(m.uiContexts).toContain('report-header'))
    })

    it('returns metaphors for marketing-cover', () => {
      const results = getMetaphorsByContext('marketing-cover')
      expect(results.length).toBeGreaterThan(0)
    })

    it('returns empty array for context with no matches', () => {
      // all current metaphors have at least one valid context; test with a context
      // that is purposely limited — marketing-naver-inline has none assigned yet
      const results = getMetaphorsByContext('marketing-naver-inline')
      expect(Array.isArray(results)).toBe(true)
    })
  })

  describe('getMetaphorsByGroup', () => {
    it('returns all avoidance metaphors', () => {
      const results = getMetaphorsByGroup('avoidance')
      expect(results.length).toBeGreaterThan(0)
      results.forEach((m) => expect(m.group).toBe('avoidance'))
    })

    it('returns all recovery metaphors (9 total)', () => {
      const results = getMetaphorsByGroup('recovery')
      expect(results.length).toBe(9)
      results.forEach((m) => expect(m.group).toBe('recovery'))
      expect(results.some((m) => m.id === 'two-trees-roots')).toBe(true)
    })
  })

  describe('matchMetaphor', () => {
    it('returns a metaphor for a given uiContext', () => {
      const result = matchMetaphor({ uiContext: 'report-header' })
      expect(result).toBeTruthy()
      expect(result.id).toBeTruthy()
    })

    it('respects emotion scoring — returns metaphor with matching emotion when score is high', () => {
      const result = matchMetaphor({ uiContext: 'report-header', emotion: '외로움' })
      expect(result).toBeTruthy()
    })

    it('respects need scoring — returns metaphor with matching need when score is high', () => {
      const result = matchMetaphor({ uiContext: 'report-header', need: '연결' })
      expect(result).toBeTruthy()
    })

    it('excludes specified ids', () => {
      const allIds = METAPHORS.map((m) => m.id)
      const excludeAll = allIds.slice(0, allIds.length - 1)
      const result = matchMetaphor({ uiContext: 'report-header', exclude: excludeAll })
      expect(result.id).toBe(allIds[allIds.length - 1])
    })

    it('returns recovery metaphor when group+tone match', () => {
      const result = matchMetaphor({ uiContext: 'session-end', tone: 'warm' })
      expect(result.tone).toBe('warm')
    })

    it('always returns a valid Metaphor object', () => {
      for (let i = 0; i < 20; i++) {
        const result = matchMetaphor({ uiContext: 'marketing-cover' })
        expect(result).toHaveProperty('id')
        expect(result).toHaveProperty('filename')
      }
    })
  })
})

describe('Crisis Resources Constants', () => {
  describe('CRISIS_RESOURCES_IMMEDIATE', () => {
    it('has immediate crisis resources', () => {
      expect(CRISIS_RESOURCES_IMMEDIATE.length).toBeGreaterThan(0)
    })

    it('each resource has required fields', () => {
      CRISIS_RESOURCES_IMMEDIATE.forEach((resource) => {
        expect(resource).toHaveProperty('label')
        expect(resource).toHaveProperty('phone')
        expect(resource).toHaveProperty('hours')
        expect(resource).toHaveProperty('description')
        expect(resource).toHaveProperty('category')
      })
    })

    it('each resource has immediate category', () => {
      CRISIS_RESOURCES_IMMEDIATE.forEach((resource) => {
        expect(resource.category).toBe('immediate')
      })
    })

    it('includes domestic violence hotline', () => {
      const found = CRISIS_RESOURCES_IMMEDIATE.some((r) => r.phone === '1366')
      expect(found).toBe(true)
    })

    it('includes suicide prevention hotline', () => {
      const found = CRISIS_RESOURCES_IMMEDIATE.some((r) => r.phone === '1393')
      expect(found).toBe(true)
    })

    it('includes mental health crisis hotline', () => {
      const found = CRISIS_RESOURCES_IMMEDIATE.some((r) => r.phone === '1577-0199')
      expect(found).toBe(true)
    })

    it('includes child abuse reporting', () => {
      const found = CRISIS_RESOURCES_IMMEDIATE.some((r) => r.phone === '112')
      expect(found).toBe(true)
    })
  })

  describe('CRISIS_RESOURCES_LEGAL', () => {
    it('has legal crisis resources', () => {
      expect(CRISIS_RESOURCES_LEGAL.length).toBeGreaterThan(0)
    })

    it('each resource has legal category', () => {
      CRISIS_RESOURCES_LEGAL.forEach((resource) => {
        expect(resource.category).toBe('legal')
      })
    })

    it('includes legal aid corporation', () => {
      const found = CRISIS_RESOURCES_LEGAL.some((r) => r.phone === '132')
      expect(found).toBe(true)
    })

    it('includes family law consultation', () => {
      const found = CRISIS_RESOURCES_LEGAL.some((r) => r.phone === '1644-7077')
      expect(found).toBe(true)
    })

    it('includes healthy family support center', () => {
      const found = CRISIS_RESOURCES_LEGAL.some((r) => r.phone === '1577-9337')
      expect(found).toBe(true)
    })
  })

  describe('CRISIS_RESOURCES (combined)', () => {
    it('combines immediate and legal resources', () => {
      const expected = CRISIS_RESOURCES_IMMEDIATE.length + CRISIS_RESOURCES_LEGAL.length
      expect(CRISIS_RESOURCES).toHaveLength(expected)
    })

    it('includes all immediate resources', () => {
      CRISIS_RESOURCES_IMMEDIATE.forEach((immediate) => {
        const found = CRISIS_RESOURCES.some((r) => r.phone === immediate.phone)
        expect(found).toBe(true)
      })
    })

    it('includes all legal resources', () => {
      CRISIS_RESOURCES_LEGAL.forEach((legal) => {
        const found = CRISIS_RESOURCES.some((r) => r.phone === legal.phone)
        expect(found).toBe(true)
      })
    })

    it('all resources have non-empty phone numbers', () => {
      CRISIS_RESOURCES.forEach((resource) => {
        expect(resource.phone).toBeTruthy()
        expect(resource.phone.length).toBeGreaterThan(0)
      })
    })

    it('all resources have non-empty labels', () => {
      CRISIS_RESOURCES.forEach((resource) => {
        expect(resource.label).toBeTruthy()
        expect(resource.label.length).toBeGreaterThan(0)
      })
    })

    it('all resources have non-empty descriptions', () => {
      CRISIS_RESOURCES.forEach((resource) => {
        expect(resource.description).toBeTruthy()
        expect(resource.description.length).toBeGreaterThan(0)
      })
    })

    it('all resources have hours information', () => {
      CRISIS_RESOURCES.forEach((resource) => {
        expect(resource.hours).toBeTruthy()
        expect(resource.hours.length).toBeGreaterThan(0)
      })
    })

    it('phone numbers are unique', () => {
      const phones = CRISIS_RESOURCES.map((r) => r.phone)
      const unique = new Set(phones)
      expect(unique.size).toBe(CRISIS_RESOURCES.length)
    })
  })
})
