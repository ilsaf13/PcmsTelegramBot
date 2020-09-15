create database pcmstelegrambot_db;
create user 'botUser'@'%' identified by '123';
grant all on pcmstelegrambot_db.* to 'botUser'@'%';

# Migrating from older versions
alter table pcmstelegrambot_db.user_chats add bot_id bigint after id;
alter table pcmstelegrambot_db.user_chats add watch_standings bit(1) after watch_runs;