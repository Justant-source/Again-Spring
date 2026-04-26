-- V8: Track per-turn psychology scores (4 Horsemen, NVC completion) on session
-- Used to feed back accumulated patterns into next-turn prompt so the mediator
-- can dial in stronger antidotes when patterns persist.

ALTER TABLE sessions
    ADD COLUMN horsemen_history JSON COMMENT 'Per-turn 4 Horsemen intensities: [{turn,sender,criticism,contempt,defensiveness,stonewalling}, ...]',
    ADD COLUMN nvc_completion_history JSON COMMENT 'Per-turn NVC 4-step completion: [{turn,sender,observation,feeling,need,request}, ...]',
    ADD COLUMN current_focus VARCHAR(50) COMMENT 'Current mediator focus: early_grounding | deepen | perspective | solution';
