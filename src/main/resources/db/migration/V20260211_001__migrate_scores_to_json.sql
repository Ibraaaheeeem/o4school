-- Migration script: V20260211_001__migrate_scores_to_json.sql
-- Goal: Move data from ca1_score, ca2_score, and exam_score to scores_json 
--       using specific keys provided by the user.

UPDATE subject_scores
SET scores_json = (
    SELECT jsonb_strip_nulls(
        jsonb_build_object(
            '1st CA', ca1_score,
            '2nd CA', ca2_score,
            'Exam', exam_score
        )
    )::text
)
WHERE (ca1_score IS NOT NULL OR ca2_score IS NOT NULL OR exam_score IS NOT NULL)
  AND (scores_json IS NULL OR scores_json = '' OR scores_json = '{}');

-- Note: jsonb_strip_nulls ensures we don't store "key": null if the score is missing.
-- This keeps the scores_json clean and compatible with the app's Map<String, Int?> logic.
