import { sessionHandlers } from './session';
import { mediationHandlers } from './mediation';
import { userHandlers } from './user';
import { chatHandlers } from './chat';

export const handlers = [
  ...sessionHandlers,
  ...mediationHandlers,
  ...chatHandlers,
  ...userHandlers,
];
