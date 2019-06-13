create database pcmstelegrambot_db;
create user 'botUser'@'%' identified by '123';
grant all on pcmstelegrambot_db.* to 'botUser'@'%';