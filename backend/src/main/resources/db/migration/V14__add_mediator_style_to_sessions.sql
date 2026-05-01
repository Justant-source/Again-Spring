-- V14: Add mediator style preferences (X: fact↔empathy, Y: listening↔active)
ALTER TABLE sessions
  ADD COLUMN mediator_style_x TINYINT UNSIGNED NOT NULL DEFAULT 50,
  ADD COLUMN mediator_style_y TINYINT UNSIGNED NOT NULL DEFAULT 50;
