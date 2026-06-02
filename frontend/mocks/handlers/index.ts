import { sessionHandlers } from './session';
import { mediationHandlers } from './mediation';
import { userHandlers } from './user';
import { chatHandlers } from './chat';
import { communityHandlers } from './community';
import { threeWayHandlers } from './threeWay';
import { notificationHandlers } from './notifications';

export const handlers = [
  ...sessionHandlers,
  ...mediationHandlers,
  ...chatHandlers,
  ...userHandlers,
  ...communityHandlers,
  ...threeWayHandlers,
  ...notificationHandlers,
];
