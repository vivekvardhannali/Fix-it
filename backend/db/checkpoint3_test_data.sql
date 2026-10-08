-- Checkpoint 3: manual relational check. Runs in a transaction and ROLLS BACK, leaving the DB empty.
BEGIN;
INSERT INTO users (smail) VALUES ('alice@smail.iitm.ac.in'), ('bob@smail.iitm.ac.in');
INSERT INTO tags (name) VALUES ('Networking'), ('WiFi'), ('Hostel');
INSERT INTO questions (author_id, title, body)
    SELECT user_id, 'WiFi drops in hostel', 'Keeps disconnecting' FROM users WHERE smail LIKE 'alice%';
INSERT INTO question_tags SELECT 1 + 0*q.question_id, t.tag_id FROM questions q, tags t WHERE q.question_id = (SELECT min(question_id) FROM questions);
INSERT INTO answers (question_id, author_id, body)
    SELECT q.question_id, u.user_id, 'Restart the router' FROM questions q, users u WHERE u.smail LIKE 'bob%';
INSERT INTO question_comments (question_id, author_id, body)
    SELECT q.question_id, u.user_id, 'Try DNS 8.8.8.8' FROM questions q, users u WHERE u.smail LIKE 'bob%';
INSERT INTO question_comments (question_id, author_id, parent_comment_id, body)
    SELECT c.question_id, u.user_id, c.comment_id, 'Did not help' FROM question_comments c, users u WHERE u.smail LIKE 'alice%';
INSERT INTO question_comment_votes (comment_id, user_id, vote_type)
    SELECT c.comment_id, u.user_id, 'UP' FROM question_comments c, users u WHERE c.parent_comment_id IS NULL AND u.smail LIKE 'alice%';
INSERT INTO question_interests (question_id, user_id) SELECT q.question_id, u.user_id FROM questions q, users u WHERE u.smail LIKE 'bob%';
INSERT INTO notifications (user_id, type, question_id, comment_id)
    SELECT q.author_id, 'COMMENT_ON_QUESTION', q.question_id, c.comment_id FROM questions q JOIN question_comments c USING (question_id) WHERE c.parent_comment_id IS NULL;
UPDATE questions SET accepted_answer_id = (SELECT answer_id FROM answers), is_resolved = TRUE;

\echo '--- tags per question (expect 3)'
SELECT q.title, string_agg(t.name, ', ' ORDER BY t.name) AS tags FROM questions q JOIN question_tags qt USING (question_id) JOIN tags t USING (tag_id) GROUP BY q.title;
\echo '--- comment thread (expect 1 comment + 1 reply)'
SELECT c.comment_id, c.parent_comment_id, c.body FROM question_comments c ORDER BY 1;
\echo '--- question/answer/accepted'
SELECT q.title, a.body AS accepted_answer, q.is_resolved FROM questions q JOIN answers a ON a.answer_id = q.accepted_answer_id;
\echo '--- votes, interests, notifications'
SELECT (SELECT count(*) FROM question_comment_votes) votes, (SELECT count(*) FROM question_interests) interests, (SELECT count(*) FROM notifications) notifications;

\echo '--- negative checks (each must fail)'
SAVEPOINT s1; INSERT INTO question_comment_votes VALUES (1, 2, 'SIDEWAYS'); ROLLBACK TO s1;
SAVEPOINT s2; INSERT INTO question_interests (question_id, user_id) SELECT question_id, user_id FROM question_interests; ROLLBACK TO s2;
SAVEPOINT s3; INSERT INTO users (smail) VALUES ('alice@smail.iitm.ac.in'); ROLLBACK TO s3;
ROLLBACK;
