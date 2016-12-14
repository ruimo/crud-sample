# First evolution.

# --- !Ups

create table users (
  id bigint not null auto_increment primary key,
  user_name varchar(24) not null unique,
  password_hash bigint not null,
  salt bigint not null
);

create index ix_users_user_name on users(user_name);

# --- !Downs

drop index ix_users_user_name;

drop table users;
