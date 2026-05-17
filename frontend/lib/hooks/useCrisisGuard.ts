'use client';

import { useState } from 'react';

export function useCrisisGuard() {
  const [crisisLevel1, setCrisisLevel1] = useState(false);
  const [showCrisisResource, setShowCrisisResource] = useState(false);
  return { crisisLevel1, setCrisisLevel1, showCrisisResource, setShowCrisisResource };
}
