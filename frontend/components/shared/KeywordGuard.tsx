'use client';

import { useState, ReactNode } from 'react';
import { CrisisResourceModal } from './CrisisResourceModal';

// Stub implementation — keyword guard deleted
function checkKeywords(_text: string) {
  return { level: null as 1 | 2 | null };
}

/**
 * Hook for keyword guard with modal state.
 * Usage: const { level, modal, check } = useKeywordGuard();
 */
export function useKeywordGuard() {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [modalSeverity, setModalSeverity] = useState<'critical' | 'advisory'>('advisory');

  const check = (text: string) => {
    const result = checkKeywords(text);
    if (result.level === 1) {
      setModalSeverity('critical');
      setIsModalOpen(true);
    } else if (result.level === 2) {
      setModalSeverity('advisory');
      setIsModalOpen(true);
    }
    return result.level;
  };

  const modal = (
    <CrisisResourceModal
      open={isModalOpen}
      onClose={() => setIsModalOpen(false)}
      severity={modalSeverity}
    />
  );

  return {
    level: null as 1 | 2 | null,
    matchedKeyword: null as string | null,
    modal,
    close: () => setIsModalOpen(false),
    check,
  };
}

/**
 * Wrapper component that injects keyword checking + modal.
 * Usage:
 * <KeywordGuard>
 *   {({ check, modal }) => (
 *     <>
 *       <input onChange={(e) => check(e.target.value)} />
 *       {modal}
 *     </>
 *   )}
 * </KeywordGuard>
 */
export function KeywordGuard({
  children,
}: {
  children: (props: {
    check: (text: string) => 1 | 2 | null;
    modal: ReactNode;
  }) => ReactNode;
}) {
  const { check, modal } = useKeywordGuard();

  return children({ check, modal });
}
