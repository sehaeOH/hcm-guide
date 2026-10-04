-- 호치민 탐방 가이드: 여행자 제보 + 관리자 삭제
-- Supabase 대시보드 → SQL Editor → 이 파일 내용을 전부 붙여 넣고 Run 하세요.
-- 아래 'admin@example.com'을 모두 관리자 이메일로 꼭 바꾸세요. (메모장의 '바꾸기' 기능으로 한 번에 바꾸면 편해요)
-- 이미 실행한 적이 있어도 다시 실행해도 괜찮아요.

create table if not exists public.user_places (
  id         bigint generated always as identity primary key,
  created_at timestamptz not null default now(),
  area       text not null check (char_length(area)     between 1 and 30),
  category   text not null check (char_length(category) between 1 and 20),
  name       text not null check (char_length(name)     between 1 and 60),
  name_vi    text check (char_length(name_vi) <= 80),
  address    text check (char_length(address) <= 160),
  note       text not null check (char_length(note)     between 1 and 500),
  price      text check (char_length(price) <= 60),
  nickname   text check (char_length(nickname) <= 20),
  is_admin   boolean not null default false
);

-- 규칙(RLS)을 켜면, 아래에 적은 일만 허용돼요.
alter table public.user_places enable row level security;

-- 한·중·베 구분 칸 (이미 표를 만들었어도 이 줄로 칸이 추가돼요)
alter table public.user_places add column if not exists origin text
  check (origin in ('한국','중국','베트남'));

-- 1) 누구나 볼 수 있어요 (로그인 없이)
drop policy if exists "anyone can read" on public.user_places;
create policy "anyone can read" on public.user_places
  for select using (true);

-- 2) 누구나 새 장소를 올릴 수 있어요 (로그인 없이)
--    단, 'Sixman' 표시(is_admin = true)는 관리자만 붙일 수 있어요.
drop policy if exists "anyone can submit" on public.user_places;
create policy "anyone can submit" on public.user_places
  for insert to anon, authenticated
  with check (is_admin = false or (auth.jwt() ->> 'email') = 'admin@example.com');

-- 3) 삭제는 관리자만 할 수 있어요
drop policy if exists "only admin can delete" on public.user_places;
create policy "only admin can delete" on public.user_places
  for delete to authenticated
  using ((auth.jwt() ->> 'email') = 'admin@example.com');

-- 4) 수정도 관리자만 할 수 있어요
drop policy if exists "only admin can update" on public.user_places;
create policy "only admin can update" on public.user_places
  for update to authenticated
  using ((auth.jwt() ->> 'email') = 'admin@example.com')
  with check ((auth.jwt() ->> 'email') = 'admin@example.com');

-- ===== 제보 장소 사진 칸 =====
alter table public.user_places add column if not exists photo text
  check (char_length(photo) <= 2000);

-- ===== 기본 장소(구글 시트·샘플) 고친 내용 =====
-- 관리자가 앱에서 기본 장소의 사진·내용을 고치면 여기에 저장돼요.
create table if not exists public.place_overrides (
  key        text primary key check (char_length(key) <= 200),
  data       jsonb not null default '{}'::jsonb,
  updated_at timestamptz not null default now()
);
alter table public.place_overrides enable row level security;

drop policy if exists "anyone can read overrides" on public.place_overrides;
create policy "anyone can read overrides" on public.place_overrides
  for select using (true);

drop policy if exists "only admin can write overrides" on public.place_overrides;
create policy "only admin can write overrides" on public.place_overrides
  for all to authenticated
  using ((auth.jwt() ->> 'email') = 'admin@example.com')
  with check ((auth.jwt() ->> 'email') = 'admin@example.com');

-- ===== 사진 저장소 =====
-- 누구나 사진을 볼 수 있고, 올리기·지우기는 관리자만 할 수 있어요.
insert into storage.buckets (id, name, public)
values ('place-photos', 'place-photos', true)
on conflict (id) do update set public = true;

drop policy if exists "admin can upload place photos" on storage.objects;
create policy "admin can upload place photos" on storage.objects
  for insert to authenticated
  with check (bucket_id = 'place-photos' and (auth.jwt() ->> 'email') = 'admin@example.com');

drop policy if exists "admin can delete place photos" on storage.objects;
create policy "admin can delete place photos" on storage.objects
  for delete to authenticated
  using (bucket_id = 'place-photos' and (auth.jwt() ->> 'email') = 'admin@example.com');

-- ===== 예약·연락 칸 =====
alter table public.user_places add column if not exists phone text check (char_length(phone) <= 30);
alter table public.user_places add column if not exists zalo text check (char_length(zalo) <= 30);
alter table public.user_places add column if not exists kakao text check (char_length(kakao) <= 120);
alter table public.user_places add column if not exists booking_url text check (char_length(booking_url) <= 300);
