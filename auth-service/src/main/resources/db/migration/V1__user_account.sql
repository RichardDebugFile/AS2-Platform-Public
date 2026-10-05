-- Cuentas de usuario de la consola.
create table user_account (
  id            uuid         primary key,
  username      varchar(80)  not null,
  email         varchar(180),
  password_hash varchar(120) not null,
  roles         varchar(200) not null,  -- CSV ordenado: ADMIN,AUDITOR,OPERATOR
  mfa_enabled   boolean      not null default false,
  mfa_secret    varchar(256),           -- secreto TOTP cifrado con AES-GCM
  active        boolean      not null default true,
  created_at    timestamptz  not null default now(),
  updated_at    timestamptz  not null default now()
);

-- El nombre de usuario es unico sin distinguir mayusculas (RN-01).
create unique index uq_user_account_username_lower on user_account (lower(username));
