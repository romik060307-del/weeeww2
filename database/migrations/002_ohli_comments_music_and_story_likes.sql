-- OHLI Social feature expansion: comment moderation, audio catalog and Story likes.
-- Applies to the optional PostgreSQL reference schema. The Android Java host persists
-- the same concepts in app-private JSON files; this migration is NOT applied by Android.
BEGIN;
CREATE TABLE music_tracks (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  media_id uuid NOT NULL REFERENCES media_objects(id) ON DELETE CASCADE,
  title varchar(100) NOT NULL,
  artist varchar(80) NOT NULL DEFAULT '',
  mime_type varchar(120) NOT NULL,
  file_name varchar(160) NOT NULL DEFAULT '',
  use_count bigint NOT NULL DEFAULT 0 CHECK (use_count >= 0),
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX music_tracks_owner_idx ON music_tracks(owner_id, created_at DESC);
CREATE INDEX music_tracks_title_idx ON music_tracks(lower(title));

CREATE TABLE music_favorites (
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  music_id uuid NOT NULL REFERENCES music_tracks(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(user_id, music_id)
);
CREATE INDEX music_favorites_music_idx ON music_favorites(music_id, created_at DESC);

ALTER TABLE posts ADD COLUMN IF NOT EXISTS comments_disabled boolean NOT NULL DEFAULT false;
ALTER TABLE posts ADD COLUMN IF NOT EXISTS music_id uuid REFERENCES music_tracks(id) ON DELETE SET NULL;
CREATE INDEX posts_music_idx ON posts(music_id, created_at DESC) WHERE music_id IS NOT NULL;

CREATE TABLE story_likes (
  story_id uuid NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(story_id, user_id),
  CHECK (story_id IS NOT NULL)
);
CREATE INDEX story_likes_user_idx ON story_likes(user_id, created_at DESC);

COMMIT;
