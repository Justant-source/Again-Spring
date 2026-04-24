import { sessionHandlers } from './session';
import { mediationHandlers } from './mediation';
import { userHandlers } from './user';

export const handlers = [
  ...sessionHandlers,
  ...mediationHandlers,
  ...userHandlers,
];
