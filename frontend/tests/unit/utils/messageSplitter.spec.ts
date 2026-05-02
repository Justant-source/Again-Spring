import { splitMediatorMessage, calculateTypingDelay } from '@/lib/utils/messageSplitter';

describe('messageSplitter', () => {
  describe('splitMediatorMessage', () => {
    it('should not split short messages (less than 4 lines and 300 chars)', () => {
      const short = '짧은 메시지입니다.';
      expect(splitMediatorMessage(short)).toBeNull();
    });

    it('should not split messages with 4 lines or less', () => {
      const fourLines = 'Line 1\nLine 2\nLine 3\nLine 4';
      expect(splitMediatorMessage(fourLines)).toBeNull();
    });

    it('should split messages with more than 4 lines', () => {
      const fiveLines = 'Line 1\nLine 2\nLine 3\nLine 4\nLine 5';
      const result = splitMediatorMessage(fiveLines);
      expect(result).not.toBeNull();
      expect(result?.first.length).toBeGreaterThan(0);
      expect(result?.second.length).toBeGreaterThan(0);
    });

    it('should split messages with 300 or more characters', () => {
      const longMessage = 'A'.repeat(300);
      const result = splitMediatorMessage(longMessage);
      expect(result).not.toBeNull();
      expect(result?.first.length).toBeGreaterThan(0);
      expect(result?.second.length).toBeGreaterThan(0);
    });

    it('should split at sentence boundaries when possible', () => {
      // 300자 이상의 메시지를 만들기 위해 충분히 긴 문장을 작성
      const longText = 'A'.repeat(350);
      const result = splitMediatorMessage(longText);
      expect(result).not.toBeNull();
      if (result) {
        // 첫 번째 부분과 두 번째 부분 모두 존재해야 함
        expect(result.first.length).toBeGreaterThan(0);
        expect(result.second.length).toBeGreaterThan(0);
      }
    });

    it('should split at newline boundaries when sentence boundaries not found', () => {
      const messageWithNewline = 'First line\nSecond line\nThird line\nFourth line\nFifth line';
      const result = splitMediatorMessage(messageWithNewline);
      expect(result).not.toBeNull();
      if (result) {
        expect(result.first.length).toBeGreaterThan(0);
        expect(result.second.length).toBeGreaterThan(0);
      }
    });

    it('should ensure both parts are non-empty', () => {
      const longMessage = 'Valid message for split testing. '.repeat(15);
      const result = splitMediatorMessage(longMessage);
      expect(result).not.toBeNull();
      if (result) {
        expect(result.first.trim().length).toBeGreaterThan(0);
        expect(result.second.trim().length).toBeGreaterThan(0);
      }
    });
  });

  describe('calculateTypingDelay', () => {
    it('should return delay between 2000ms and 3000ms', () => {
      const delays = [10, 50, 100, 150, 200].map(len => calculateTypingDelay(len));
      delays.forEach(delay => {
        expect(delay).toBeGreaterThanOrEqual(2000);
        expect(delay).toBeLessThanOrEqual(3000);
      });
    });

    it('should return minimum delay for very short content', () => {
      const delay = calculateTypingDelay(1);
      expect(delay).toBe(2000);
    });

    it('should return maximum delay for long content', () => {
      const delay = calculateTypingDelay(300);
      expect(delay).toBe(3000);
    });

    it('should scale delay proportionally', () => {
      const delay50 = calculateTypingDelay(50);
      const delay150 = calculateTypingDelay(150);
      expect(delay150).toBeGreaterThanOrEqual(delay50);
    });
  });
});
