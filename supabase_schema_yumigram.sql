-- ==============================================================================
-- Yumigram (Юміграм) Supabase Compatibility & Alias Migration
-- ==============================================================================
-- This script creates views and RPC function aliases so that Yumigram clients
-- querying yumigram_* endpoints seamlessly interact with existing miogram_* tables.
-- Run this in your Supabase SQL Editor.
-- ==============================================================================

-- 1. Views for Badges & Users
CREATE OR REPLACE VIEW yumigram_badges AS 
    SELECT * FROM miogram_badges;

CREATE OR REPLACE VIEW yumigram_users AS 
    SELECT * FROM miogram_users;

-- 2. Grant Badge RPC Alias
CREATE OR REPLACE FUNCTION yumigram_grant_badge(
    p_secret text,
    p_target bigint,
    p_badge_id text,
    p_title text,
    p_reason text
) RETURNS json LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
    RETURN miogram_grant_badge(p_secret, p_target, p_badge_id, p_title, p_reason);
END;
$$;

-- 3. Revoke Badge RPC Alias
CREATE OR REPLACE FUNCTION yumigram_revoke_badge(
    p_secret text,
    p_target bigint
) RETURNS json LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
    RETURN miogram_revoke_badge(p_secret, p_target);
END;
$$;

-- 4. Community Stats RPC Alias
CREATE OR REPLACE FUNCTION yumigram_community_stats()
RETURNS json LANGUAGE plpgsql SECURITY DEFINER AS $$
BEGIN
    RETURN miogram_community_stats();
END;
$$;

-- 5. Reload PostgREST schema cache
NOTIFY pgrst, 'reload schema';
