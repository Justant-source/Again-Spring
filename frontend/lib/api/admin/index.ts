// Re-export all existing admin API functions from the original file for backward compat
export * from '../admin';

// New module-specific files — only export NEW symbols not already in ../admin to avoid ambiguity:
export * from './dashboard';
// users: re-export only new management functions (listUsers/AdminUserListItem/PageResponse come from ../admin)
export { suspendUser, unsuspendUser, forceLogoutUser, anonymizeUser, exportUsersAsCSV } from './users';
export * from './content';
export * from './reports';
export * from './inquiries';
export * from './stats';
export * from './announcements';
export * from './notifications';
export * from './audit';
export * from './system';
