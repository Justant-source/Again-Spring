-- V106: drop unused AI jury feature (product path never used jurors)
DROP TABLE IF EXISTS jurors;

ALTER TABLE posts DROP COLUMN juror_count;
