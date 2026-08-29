-- V28: users.password_change_required - drives the "Force Change Password" screen after an
-- Admin/CEO-initiated password reset (temporary password). This app has no email/SMS delivery
-- infrastructure and a small, fixed employee base, so Admin/CEO Password Reset (the CEO already
-- administers every user account) is the primary reset path for this internal app - see
-- UserAdminService#resetPasswordByAdmin. Purely additive; defaults FALSE so no existing account is
-- retroactively forced to change anything.
ALTER TABLE users ADD COLUMN password_change_required BOOLEAN NOT NULL DEFAULT FALSE;
