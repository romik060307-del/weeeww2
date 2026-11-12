-- OHLI Social PostgreSQL reference migration.
-- IMPORTANT: The current Android-hosted Java server does NOT apply this file;
-- it still stores data in app-private JSON files. This schema is for the documented
-- migration to a persistent hosted backend and must be paired with an importer/API.

BEGIN;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  phone_e164 varchar(16) UNIQUE,
  username varchar(32) UNIQUE,
  password_hash text NOT NULL,
  display_name varchar(80) NOT NULL,
  about varchar(500) NOT NULL DEFAULT '',
  phone_visibility varchar(16) NOT NULL DEFAULT 'nobody'
    CHECK (phone_visibility IN ('nobody','contacts','followers','public')),
  account_private boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz
);
CREATE INDEX users_display_name_idx ON users (lower(display_name));

CREATE TABLE user_sessions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  token_hash bytea NOT NULL UNIQUE,
  device_label varchar(120) NOT NULL DEFAULT '',
  created_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  revoked_at timestamptz
);
CREATE INDEX user_sessions_active_idx ON user_sessions(user_id, expires_at) WHERE revoked_at IS NULL;

CREATE TABLE follows (
  follower_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  following_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  status varchar(12) NOT NULL DEFAULT 'accepted' CHECK (status IN ('pending','accepted')),
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (follower_id, following_id),
  CHECK (follower_id <> following_id)
);
CREATE INDEX follows_following_idx ON follows(following_id, status, created_at DESC);

CREATE TABLE blocks (
  blocker_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  blocked_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(blocker_id, blocked_id), CHECK(blocker_id <> blocked_id)
);

CREATE TABLE media_objects (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  storage_key text NOT NULL UNIQUE,
  original_name varchar(160) NOT NULL DEFAULT '',
  mime_type varchar(120) NOT NULL,
  byte_size bigint NOT NULL CHECK(byte_size > 0),
  sha256 bytea,
  width integer CHECK(width IS NULL OR width > 0),
  height integer CHECK(height IS NULL OR height > 0),
  duration_ms bigint CHECK(duration_ms IS NULL OR duration_ms >= 0),
  created_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz
);
CREATE INDEX media_owner_idx ON media_objects(owner_id, created_at DESC);

CREATE TABLE posts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  author_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  body varchar(2000) NOT NULL DEFAULT '',
  audience varchar(16) NOT NULL DEFAULT 'public' CHECK(audience IN ('public','followers','private')),
  media_id uuid REFERENCES media_objects(id) ON DELETE SET NULL,
  media_kind varchar(12) CHECK(media_kind IS NULL OR media_kind IN ('image','video')),
  archived_at timestamptz,
  pinned_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  CHECK(length(body) > 0 OR media_id IS NOT NULL)
);
CREATE INDEX posts_feed_idx ON posts(created_at DESC, id DESC) WHERE archived_at IS NULL;
CREATE INDEX posts_author_idx ON posts(author_id, created_at DESC);
CREATE INDEX posts_audience_idx ON posts(audience, created_at DESC) WHERE archived_at IS NULL;

CREATE TABLE post_likes (
  post_id uuid NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(post_id,user_id)
);
CREATE INDEX post_likes_user_idx ON post_likes(user_id, created_at DESC);

CREATE TABLE post_comments (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  post_id uuid NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
  author_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  parent_id uuid REFERENCES post_comments(id) ON DELETE CASCADE,
  body varchar(500) NOT NULL CHECK(length(trim(body)) > 0),
  created_at timestamptz NOT NULL DEFAULT now(),
  edited_at timestamptz
);
CREATE INDEX post_comments_thread_idx ON post_comments(post_id, created_at, id);
CREATE INDEX post_comments_parent_idx ON post_comments(parent_id, created_at);

CREATE TABLE bookmarks (
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  post_id uuid NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
  collection_name varchar(80) NOT NULL DEFAULT 'Tersimpan',
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(user_id, post_id)
);
CREATE INDEX bookmarks_collection_idx ON bookmarks(user_id, collection_name, created_at DESC);

CREATE TABLE stories (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  media_id uuid REFERENCES media_objects(id) ON DELETE SET NULL,
  body varchar(500) NOT NULL DEFAULT '',
  audience varchar(20) NOT NULL DEFAULT 'contacts'
    CHECK(audience IN ('public','contacts','followers','close_friends','selected')),
  expires_at timestamptz NOT NULL,
  archived_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK(length(body)>0 OR media_id IS NOT NULL)
);
CREATE INDEX stories_active_idx ON stories(expires_at, created_at DESC) WHERE archived_at IS NULL;
CREATE INDEX stories_owner_idx ON stories(owner_id, created_at DESC);

CREATE TABLE story_audience_users (
  story_id uuid NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  rule varchar(12) NOT NULL DEFAULT 'allow' CHECK(rule IN ('allow','hide')),
  PRIMARY KEY(story_id,user_id)
);
CREATE TABLE story_views (
  story_id uuid NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
  viewer_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  first_seen_at timestamptz NOT NULL DEFAULT now(),
  last_seen_at timestamptz NOT NULL DEFAULT now(),
  view_count integer NOT NULL DEFAULT 1 CHECK(view_count > 0),
  PRIMARY KEY(story_id,viewer_id)
);
CREATE INDEX story_views_owner_audit_idx ON story_views(story_id, first_seen_at);

CREATE TABLE conversations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  kind varchar(12) NOT NULL CHECK(kind IN ('direct','group')),
  title varchar(100) NOT NULL DEFAULT '',
  created_by uuid REFERENCES users(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  archived_at timestamptz
);
CREATE TABLE conversation_members (
  conversation_id uuid NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role varchar(12) NOT NULL DEFAULT 'member' CHECK(role IN ('owner','admin','moderator','member')),
  pinned_at timestamptz,
  archived_at timestamptz,
  muted_until timestamptz,
  joined_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(conversation_id,user_id)
);
CREATE INDEX conversation_members_user_idx ON conversation_members(user_id, archived_at, joined_at DESC);

CREATE TABLE messages (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  conversation_id uuid NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  sender_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  body varchar(10000) NOT NULL DEFAULT '',
  media_id uuid REFERENCES media_objects(id) ON DELETE SET NULL,
  reply_to_id uuid REFERENCES messages(id) ON DELETE SET NULL,
  forwarded_from_id uuid REFERENCES messages(id) ON DELETE SET NULL,
  edited_at timestamptz,
  deleted_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK(length(body)>0 OR media_id IS NOT NULL)
);
CREATE INDEX messages_history_idx ON messages(conversation_id, created_at DESC, id DESC);
CREATE INDEX messages_sender_idx ON messages(sender_id, created_at DESC);
CREATE TABLE message_receipts (
  message_id uuid NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  delivered_at timestamptz,
  read_at timestamptz,
  PRIMARY KEY(message_id,user_id)
);

CREATE TABLE communities (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  kind varchar(12) NOT NULL CHECK(kind IN ('group','community','channel')),
  owner_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name varchar(70) NOT NULL,
  description varchar(500) NOT NULL DEFAULT '',
  privacy varchar(10) NOT NULL DEFAULT 'public' CHECK(privacy IN ('public','private')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX communities_discovery_idx ON communities(kind, privacy, created_at DESC);
CREATE TABLE community_members (
  community_id uuid NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role varchar(12) NOT NULL DEFAULT 'member' CHECK(role IN ('owner','admin','moderator','member','follower')),
  notifications_enabled boolean NOT NULL DEFAULT true,
  joined_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY(community_id,user_id)
);
CREATE INDEX community_members_user_idx ON community_members(user_id, joined_at DESC);
CREATE TABLE channel_posts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  channel_id uuid NOT NULL REFERENCES communities(id) ON DELETE CASCADE,
  author_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  body varchar(4000) NOT NULL DEFAULT '',
  media_id uuid REFERENCES media_objects(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK(length(body)>0 OR media_id IS NOT NULL)
);
CREATE INDEX channel_posts_feed_idx ON channel_posts(channel_id, created_at DESC);

CREATE TABLE notifications (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  recipient_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  actor_id uuid REFERENCES users(id) ON DELETE SET NULL,
  kind varchar(32) NOT NULL,
  object_type varchar(24) NOT NULL DEFAULT '',
  object_id uuid,
  payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  read_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX notifications_inbox_idx ON notifications(recipient_id, created_at DESC);
CREATE INDEX notifications_unread_idx ON notifications(recipient_id, created_at DESC) WHERE read_at IS NULL;

CREATE TABLE reports (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  reporter_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  target_user_id uuid REFERENCES users(id) ON DELETE CASCADE,
  target_post_id uuid REFERENCES posts(id) ON DELETE CASCADE,
  reason varchar(80) NOT NULL,
  details varchar(1000) NOT NULL DEFAULT '',
  status varchar(16) NOT NULL DEFAULT 'open' CHECK(status IN ('open','reviewing','resolved','rejected')),
  created_at timestamptz NOT NULL DEFAULT now(),
  CHECK(target_user_id IS NOT NULL OR target_post_id IS NOT NULL)
);
CREATE INDEX reports_status_idx ON reports(status, created_at);

COMMIT;
