export function calculateDistanceLabel(
  positionA: { x: number; y: number },
  positionB: { x: number; y: number },
): { level: 1 | 2 | 3 | 4 | 5; label: string; emoji: string } {
  const distance = Math.sqrt(
    Math.pow(positionA.x - positionB.x, 2) +
    Math.pow(positionA.y - positionB.y, 2),
  );

  if (distance < 50)  return { level: 1, label: '매우 가까움', emoji: '💚' };
  if (distance < 100) return { level: 2, label: '가까움',     emoji: '🌱' };
  if (distance < 150) return { level: 3, label: '적당히 떨어짐', emoji: '🟡' };
  if (distance < 200) return { level: 4, label: '떨어짐',     emoji: '🟠' };
  return               { level: 5, label: '많이 떨어짐',       emoji: '🔴' };
}
