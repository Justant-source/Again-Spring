import { userHandlers } from './user';
import { communityHandlers } from './community';
import { notificationHandlers } from './notifications';

export const handlers = [
  ...userHandlers,
  ...communityHandlers,
  ...notificationHandlers,
];
