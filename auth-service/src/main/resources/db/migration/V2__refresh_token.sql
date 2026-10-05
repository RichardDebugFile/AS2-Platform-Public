-- Refresh tokens emitidos. Se guarda el hash SHA-256 del token, nunca el token: quien lea la tabla
-- no puede reutilizar las sesiones.
create table refresh_token (
  id                 uuid        primary key,
  user_id            uuid        not null references user_account (id) on delete cascade,
  token_hash         varchar(64) not null,
  expires_at         timestamptz not null,
  revoked            boolean     not null default false,
  -- Instante del login que abrio la sesion. Se hereda al rotar: ancla del techo absoluto.
  session_started_at timestamptz not null,
  created_at         timestamptz not null default now()
);

create unique index uq_refresh_token_hash on refresh_token (token_hash);
create index ix_refresh_token_user on refresh_token (user_id);
create index ix_refresh_token_expires on refresh_token (expires_at);
