-- How long a session may sit untouched before it ends (PD-38).
--
-- Null on every existing row, and null means never — so nobody's session changes because this
-- shipped. A company turns the policy on deliberately, in Settings.
alter table company_settings
    add column session_idle_minutes int;

-- Five minutes is the floor because anything shorter signs people out mid-form; a week is the
-- ceiling because "off" already has a spelling and a 60-day window is that answer in disguise.
alter table company_settings
    add constraint company_settings_session_idle_minutes_range
        check (session_idle_minutes is null
            or (session_idle_minutes >= 5 and session_idle_minutes <= 10080));
