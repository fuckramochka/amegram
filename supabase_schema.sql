-- ==========================================================
-- Miogram Supabase Database Schema
-- Table: miogram_badges
-- Purpose: Global badge resolution, community presence & badge lore
-- ==========================================================

create table if not exists public.miogram_badges (
    user_id bigint primary key,
    badge_id text not null default 'original',
    title text not null default 'Miogram Community ໒꒱',
    obtained_reason text not null default 'Верифікований учасник спільноти Miogram',
    obtained_at timestamp with time zone default timezone('utc'::text, now()) not null,
    is_active boolean not null default true,
    client_version text default 'Miogram 1.0',
    created_at timestamp with time zone default timezone('utc'::text, now()) not null,
    updated_at timestamp with time zone default timezone('utc'::text, now()) not null
);

-- Safely add any new columns if the table already existed
alter table public.miogram_badges add column if not exists title text default 'Miogram Community ໒꒱';
alter table public.miogram_badges add column if not exists obtained_reason text default 'Верифікований учасник спільноти Miogram';
alter table public.miogram_badges add column if not exists obtained_at timestamp with time zone default timezone('utc'::text, now());

alter table public.miogram_badges enable row level security;

drop policy if exists "Allow public read of active badges" on public.miogram_badges;
create policy "Allow public read of active badges"
    on public.miogram_badges
    for select
    using (true);

drop policy if exists "Allow public insert or upsert" on public.miogram_badges;
create policy "Allow public insert or upsert"
    on public.miogram_badges
    for insert
    with check (true);

drop policy if exists "Allow public update" on public.miogram_badges;
create policy "Allow public update"
    on public.miogram_badges
    for update
    using (true);

create index if not exists idx_miogram_badges_lookup on public.miogram_badges (user_id, is_active);

-- Pre-seed Founder badge (8011880648) with official lore & obtain reason
insert into public.miogram_badges (user_id, badge_id, title, obtained_reason, obtained_at, is_active, client_version)
values (
    8011880648,
    'original',
    'Засновник & Архітектор Miogram ໒꒱',
    'Створено автором Miogram як першу канонічну відзнаку екосистеми з моменту заснування проекту (01.09.2026).',
    '2026-09-01T00:00:00Z',
    true,
    'Founder Edition'
)
on conflict (user_id) do update
set badge_id = excluded.badge_id,
    title = excluded.title,
    obtained_reason = excluded.obtained_reason,
    is_active = excluded.is_active,
    updated_at = timezone('utc'::text, now());

-- ==========================================================
-- Table: miogram_users
-- Purpose: Real-time user presence tracking & live community counter
-- ==========================================================
create table if not exists public.miogram_users (
    user_id bigint primary key,
    registered_at timestamp with time zone default timezone('utc'::text, now()) not null,
    last_seen_at timestamp with time zone default timezone('utc'::text, now()) not null,
    client_version text default 'Miogram 1.0'
);

alter table public.miogram_users enable row level security;

drop policy if exists "Allow public read users" on public.miogram_users;
create policy "Allow public read users"
    on public.miogram_users
    for select
    using (true);

drop policy if exists "Allow public insert users" on public.miogram_users;
create policy "Allow public insert users"
    on public.miogram_users
    for insert
    with check (true);

drop policy if exists "Allow public update users" on public.miogram_users;
create policy "Allow public update users"
    on public.miogram_users
    for update
    using (true);

create index if not exists idx_miogram_users_last_seen on public.miogram_users (last_seen_at desc);

-- ==========================================================
-- MIGRATION 2026-09-10: anti-abuse hardening + community stats
-- Run this whole file in the Supabase SQL editor (idempotent).
--
-- Threat model: the anon key ships inside the public APK, so ANYONE can
-- call PostgREST. Before this migration RLS was `using (true)` on
-- insert/update => anybody could upsert ANY user_id with founder title
-- and steal the badge ("абуз стрічки"). After this migration:
--   * anon can still upsert rows (presence must work keyless) BUT
--   * `verified` can only be set by service_role/dashboard,
--   * the founder row is immutable for anon,
--   * badge_id is constrained to the canonical allowlist,
--   * text lengths are capped (spam control),
--   * clients treat unverified founder claims as plain member rows.
-- ==========================================================

-- 0. Founder id shared by trigger + seed (change here if it ever moves)
--    NOTE: keep in sync with MiogramBadgeManager.FOUNDER_USER_ID in app code.
create or replace function public.miogram_founder_id()
returns bigint language sql immutable as $$ select 8011880648::bigint $$;

-- 1. New columns
alter table public.miogram_badges add column if not exists verified boolean not null default false;
alter table public.miogram_badges add column if not exists last_seen_at timestamp with time zone;

-- 2. Canonical badge allowlist (matches MiogramBadgeType ids in app code)
alter table public.miogram_badges drop constraint if exists chk_badge_id_allowlist;
alter table public.miogram_badges add constraint chk_badge_id_allowlist
    check (badge_id in ('original','pink','cyan','dark','angel','devil','rainbow','outline','glitch','premium'));

-- 3. Length caps (spam control)
alter table public.miogram_badges drop constraint if exists chk_title_len;
alter table public.miogram_badges add constraint chk_title_len check (char_length(title) <= 120);
alter table public.miogram_badges drop constraint if exists chk_reason_len;
alter table public.miogram_badges add constraint chk_reason_len check (char_length(obtained_reason) <= 500);

-- 4. Guard trigger: anon can never verify, never touch the founder row,
--    never backdate; verified rows keep server-side lore on hostile writes.
create or replace function public.miogram_badges_guard()
returns trigger language plpgsql as $$
begin
    if auth.role() = 'anon' then
        -- founder row is untouchable
        if (TG_OP = 'UPDATE' or TG_OP = 'DELETE') and OLD.user_id = public.miogram_founder_id() then
            raise exception 'founder row is immutable';
        end if;
        if (TG_OP = 'INSERT') and NEW.user_id = public.miogram_founder_id() then
            raise exception 'founder row is immutable';
        end if;
        -- verification is staff-only
        NEW.verified := false;
        -- hostile lore on an already-verified row is dropped, style kept
        if TG_OP = 'UPDATE' then
            if OLD.verified then
                NEW.title := OLD.title;
                NEW.obtained_reason := OLD.obtained_reason;
                NEW.badge_id := OLD.badge_id;
                NEW.verified := true;
            end if;
        end if;
    end if;
    NEW.updated_at := timezone('utc'::text, now());
    return NEW;
end;
$$;

drop trigger if exists trg_miogram_badges_guard on public.miogram_badges;
create trigger trg_miogram_badges_guard
    before insert or update or delete on public.miogram_badges
    for each row execute function public.miogram_badges_guard();

-- 5. Mark the seeded founder row verified (runs as owner, bypasses trigger intent)
update public.miogram_badges set verified = true where user_id = public.miogram_founder_id();

-- 6. Users table: same guard spirit (fake rows can't verify anything there,
--    PK already dedupes; stats below only count recently-seen users)
alter table public.miogram_users add column if not exists client_version text default 'Miogram 1.0';

-- 7. Public community stats (used by the website counter + app).
--    SECURITY DEFINER so anon gets counts without scanning tables.
--    Only users seen in the last 30 days count => injected dead rows age out.
create or replace function public.miogram_community_stats()
returns jsonb language plpgsql security definer stable as $$
declare
    u bigint;
    b bigint;
begin
    select count(*) into u from public.miogram_users
        where last_seen_at > now() - interval '30 days';
    select count(*) into b from public.miogram_badges
        where is_active = true;
    return jsonb_build_object(
        'users_count', coalesce(u, 0),
        'badges_count', coalesce(b, 0),
        'updated_at', timezone('utc'::text, now())
    );
end;
$$;

grant execute on function public.miogram_community_stats() to anon, authenticated;
grant execute on function public.miogram_founder_id() to anon, authenticated;

-- 8. Grantor tracking (founder grants from the app client)
alter table public.miogram_badges add column if not exists grantor_id bigint not null default 0;

-- ==========================================================
-- 9. Table: miogram_steam
-- Purpose: Safe Steam Profile & Gaming Status Sync across Miogram users
-- ==========================================================
create table if not exists public.miogram_steam (
    user_id bigint primary key,
    steam_id text not null,
    persona_name text default '',
    avatar_url text default '',
    profile_url text default '',
    game_id text default '',
    game_name text default '',
    game_icon_url text default '',
    game_hours_2weeks text default '',
    is_in_game boolean default false,
    state_message text default '',
    updated_at timestamp with time zone default timezone('utc'::text, now()) not null
);

alter table public.miogram_steam enable row level security;

drop policy if exists "Allow public read steam profiles" on public.miogram_steam;
create policy "Allow public read steam profiles"
    on public.miogram_steam
    for select
    using (true);

drop policy if exists "Allow public insert steam profiles" on public.miogram_steam;
create policy "Allow public insert steam profiles"
    on public.miogram_steam
    for insert
    with check (true);

drop policy if exists "Allow public update steam profiles" on public.miogram_steam;
create policy "Allow public update steam profiles"
    on public.miogram_steam
    for update
    using (true);

create index if not exists idx_miogram_steam_lookup on public.miogram_steam (user_id);

-- ==========================================================
-- MIGRATION 2026-09-18: founder-only badge identity
-- Run this whole file in the Supabase SQL editor (idempotent).
--
-- Threat model: the anon key ships inside the public APK, so ANY old
-- client (or curl) could POST/DELETE miogram_badges: mint badges for
-- self/others, rewrite citations, revoke anyone (except founder row).
-- After this migration:
--   * anon keeps SELECT (reads) and UPDATE of presence-only columns
--     (client_version, last_seen_at) => presence keeps working keyless.
--   * anon INSERT and DELETE are gone => old clients fail closed:
--     self-mint POST -> 403, citation PATCH -> 403, revoke DELETE -> 403.
--   * badge identity changes ONLY via the RPCs below, which check
--     the grant secret. The secret ships in NEW builds only, so old
--     builds physically cannot grant/revoke/edit badges.
--   * new clients must use PATCH (not upsert) for presence and the
--     RPCs for grant/revoke (client code already updated).
--
-- SETUP: replace MIO-GRANT-mxxFB90ocCzVW9Fh8-AKiko5mANKNO5T below with your own secret and put
-- the SAME value into MiogramSupabaseBridge.GRANT_SECRET in app code.
-- ==========================================================

-- 1. Drop permissive write policies on badge identity.
drop policy if exists "Allow public insert or upsert" on public.miogram_badges;
drop policy if exists "Allow public update" on public.miogram_badges;

-- 2. Presence-only update stays available to anon (RLS row gate).
drop policy if exists "Allow anon presence update" on public.miogram_badges;
create policy "Allow anon presence update"
    on public.miogram_badges
    for update
    using (true)
    with check (true);

-- 3. Column-level grants: anon touches presence columns only.
--    (RLS evaluates first; column grants then reject identity writes.)
revoke all on public.miogram_badges from anon;
grant select on public.miogram_badges to anon;
grant update (client_version, last_seen_at) on public.miogram_badges to anon;

-- 4. Founder-only grant/revoke RPCs (SECURITY DEFINER bypasses RLS;
--    the shared secret is the gate).
create or replace function public.miogram_grant_badge(
    p_secret text, p_target bigint, p_badge_id text, p_title text, p_reason text
)
returns jsonb language plpgsql security definer as $$
begin
    if p_secret is distinct from 'MIO-GRANT-mxxFB90ocCzVW9Fh8-AKiko5mANKNO5T' then
        raise exception 'forbidden';
    end if;
    if p_target is null or p_target <= 0 then
        raise exception 'bad target';
    end if;
    if p_badge_id is null
        or p_badge_id not in ('original','pink','cyan','dark','angel','devil','rainbow','outline','glitch','premium') then
        raise exception 'bad badge';
    end if;
    insert into public.miogram_badges(
        user_id, badge_id, title, obtained_reason, obtained_at,
        is_active, verified, grantor_id, client_version
    )
    values (
        p_target, p_badge_id,
        left(coalesce(nullif(p_title, ''), 'Miogram Community ໒꒱'), 120),
        left(coalesce(nullif(p_reason, ''), 'Granted by Miogram Founder'), 500),
        timezone('utc'::text, now()),
        true, true, public.miogram_founder_id(), 'Miogram grant'
    )
    on conflict (user_id) do update set
        badge_id = excluded.badge_id,
        title = excluded.title,
        obtained_reason = excluded.obtained_reason,
        is_active = true,
        verified = true,
        grantor_id = public.miogram_founder_id(),
        updated_at = timezone('utc'::text, now());
    return jsonb_build_object('ok', true);
end;
$$;

create or replace function public.miogram_revoke_badge(p_secret text, p_target bigint)
returns jsonb language plpgsql security definer as $$
begin
    if p_secret is distinct from 'MIO-GRANT-mxxFB90ocCzVW9Fh8-AKiko5mANKNO5T' then
        raise exception 'forbidden';
    end if;
    if p_target is null or p_target <= 0 then
        raise exception 'bad target';
    end if;
    if p_target = public.miogram_founder_id() then
        raise exception 'founder row is immutable';
    end if;
    delete from public.miogram_badges where user_id = p_target;
    return jsonb_build_object('ok', true);
end;
$$;

grant execute on function public.miogram_grant_badge(text, bigint, text, text, text) to anon, authenticated;
grant execute on function public.miogram_revoke_badge(text, bigint) to anon, authenticated;


