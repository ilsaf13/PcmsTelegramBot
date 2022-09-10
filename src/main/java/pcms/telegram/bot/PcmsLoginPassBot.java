package pcms.telegram.bot;

import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import pcms.telegram.bot.domain.User;

import java.io.*;
import java.util.*;

public class PcmsLoginPassBot extends Bot {

    //maps chatId -> User info
    final Map<Long, String> chatUsers = new TreeMap<>();
    //maps chatId -> time in millis of last /change request
    final Map<Long, Long> lastChange = new HashMap<>();
    LoginPassUpdater logins;

    static {
        type = 3;
    }

    public PcmsLoginPassBot(String name, String token, long id, DefaultBotOptions botOptions, LoginPassUpdater lpu) {
        super(name, token, id, botOptions);
        this.logins = lpu;
        try {
            updateLogins();
            BufferedReader br = new BufferedReader(new FileReader("chatUserNames.txt"));
            String s;
            while ((s = br.readLine()) != null) {
                String[] parts = s.split("\t");
                chatUsers.put(Long.parseLong(parts[0]), parts[1]);
            }
            br.close();
        } catch (Exception e) {
            System.out.printf("ERROR: Couldn't get logins from '%s'\n", "f");
        }
    }



    void updateLogins() {
        synchronized (chats) {
            for (List<User> userList : chats.values()) {
                for (User user : userList) {
                    String pass = logins.getPassword(user.getLogin());
                    if (pass == null) {
                        stopNotifications(user.getChatId());
                    } else if (!pass.equals(user.getPass())) {
                        user.setPass(pass);
                        Main.dbService.saveUser(user);
                        SendMessage message = new SendMessage();
                        message.setChatId(user.getChatId());
                        message.setText("У вас новый пароль от PCMS. Нажмите /show чтобы увидеть его");
                        offer(message);
                    }
                }
            }
        }
    }



    @Override
    public String stopNotifications(long chatId) {
        Main.dbService.deleteUser(id, chatId);
        System.out.println("LOGOUT: " + User.getLoginList(chats.get(chatId)));
        synchronized (chats) {
            chats.remove(chatId);
        }
        return "Stopped watching all users";
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText())
            return;

        String message_text = update.getMessage().getText();
        long chatId = update.getMessage().getChatId();
        if (!chatUsers.containsKey(chatId)) {
            try {
                String userName = update.getMessage().getFrom().toString();
                synchronized (chatUsers) {
                    chatUsers.put(chatId, userName);
                    PrintWriter pw = new PrintWriter("chatUserNames.txt");
                    for (Map.Entry<Long, String> entry : chatUsers.entrySet()) {
                        pw.println(entry.getKey() + "\t" + entry.getValue());
                    }
                    pw.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("ERROR: Error writing chat users to file");
            }
        }
        SendMessage message = new SendMessage();
        message.setChatId(chatId);

        if (message_text.startsWith("/login")) {
            message.setText(login(chatId, message_text));
        } else if (message_text.startsWith("/logout")) {
            message.setText(logout(chatId, message_text));
        } else if (message_text.equals("/help")) {
            message.setText(help());
        } else if (message_text.equals("/show")) {
            message.setText(show(chatId));
        } else if (message_text.startsWith("/change")) {
            message.setText(change(chatId, message_text));
        } else if (message_text.equals("/update")) {
            if (logins.updateLoginsIfModified()) {
                updateLogins();
                message.setText("Updated");
            } else {
                message.setText("Not updated");
            }
        }else {
            //todo: other commands
            if (chatId < 0) return;
            message.setText("Привет! Напиши команду или нажми /help, если не знаешь как");
        }
        offer(message);
    }

    String login(long chatId, String message) {
        String[] parts = message.split(" ");
        if (parts.length < 3) {
            System.out.println("LOGIN FAILED: " + message);
            return "Извините. Мне нужен ваш логин и пароль вот в таком формате: /login логин_от_PCMS пароль_от_PCMS";
        }

        User user = new User();
        user.setBotId(id);
        user.setChatId(chatId);
        user.setLogin(parts[1]);
        user.setPass(parts[2]);
        List<User> loginList = Main.dbService.findUsersByLoginAndPass(user.getLogin(), user.getPass());
        if (loginList.size() > 1) {
            System.out.println("ERROR: More than one user has this login " + user.getLogin());
            return "Ошибка в системе, сообщите об этом моему Хозяину";
        }
        if (loginList.size() == 1) {
            if (loginList.get(0).getChatId() == chatId) {
                return "Этот логин и пароль я уже записал";
            }
            System.out.println("WARNING: Someone tries to login with another user credentials! '" + message + "' chat-id " + chatId);

            SendMessage msg = new SendMessage();
            msg.setChatId(loginList.get(0).getChatId());
            msg.setText("Другой пользователь пытается использовать Ваш логин и пароль для авторизации у меня. " +
                    "Рекомендую немедленно поменять пароль! /help");
            offer(msg);
            return "Извините. Ваш логин уже использован другим человеком. Если это точно Ваш логин, " +
                    "обратитесь к моему Хозяину.";
        }
        user.setWatchPasswords(user.getPass().equals(logins.getPassword(user.getLogin())));

        if (user.isWatchPasswords()) {
            List<User> userList = chats.get(chatId);
            if (userList == null) {
                userList = new ArrayList<>();
                synchronized (chats) {
                    chats.put(chatId, userList);
                }
            }
            User orig = null;
            for (User other : userList) {
                if (other.getLogin().equals(user.getLogin())) {
                    orig = other;
                    break;
                }
            }

            if (orig != null) {
                orig.setPass(user.getPass());
                user = orig;
            } else {
                synchronized (chats) {
                    userList.add(user);
                }
            }
            Main.dbService.saveUser(user);
            System.out.println("LOGIN: " + user.toString());
            return user.toString() + " Можете нажать /logout чтобы отписаться";
        }

        System.out.println("LOGIN FAILED: " + user.getLogin());
        return "Извините. Мне нужен ваш логин и пароль вот в таком формате: /login логин_от_PCMS пароль_от_PCMS";
    }

    String logout(long chatId, String message_text) {
        String[] parts = message_text.split(" ");
        if (parts.length == 1) {
            return stopNotifications(chatId);
        }

        if (parts.length == 3) {
            User user = new User();
            user.setChatId(chatId);
            user.setLogin(parts[1]);
            user.setPass(parts[2]);
            List<User> userList = chats.get(chatId);
            boolean found;
            synchronized (chats) {
                found = userList.remove(user);
                if (userList.isEmpty()) {
                    chats.remove(chatId);
                }
            }
            if (found) {
                Main.dbService.deleteUser(id, user.getChatId(), user.getLogin(), user.getPass());
                System.out.println("LOGOUT: " + user.getLogin());
                return "Больше не слежу за " + user.getLogin();
            }
            return "Не нашел ваш логин и пароль: " + user.getLogin() + " " + user.getPass();
        }

        return "Нажмите /logout чтобы отписаться";
    }

    String show(long chatId) {
        List<User> userList = chats.get(chatId);
        if (userList == null) {
            return "Я не знаю ваших логинов и паролей :(. Попробуйте подписаться на обновления: /login логин_от_PCMS пароль_от_PCMS";
        }
        StringBuilder sb = new StringBuilder("Вот ваши логины:\n");
        for (User user : userList) {
            sb.append("логин: ").append(user.getLogin());
            sb.append(" пароль: ").append(user.getPass()).append("\n\n");
        }
        return sb.toString();
    }

    String change(long chatId, String message_text) {
        String[] parts = message_text.split(" ");
        if (parts.length != 3) {
            return "Чтобы изменить пароль напишите /change логин_от_PCMS новый_пароль";
        }
        if (parts[2].length() < 8 || !parts[2].matches("[a-zA-Z0-9]*")) {
            return "Ваш новый пароль не удовлетворяет одному из условий:\n"+
                    "- длина пароля должна быть не менее 8 символов\n" +
                    "- пароль может содержать только большие и маленькие буквы латинского алфавита и цифры";
        }
        List<User> userList = chats.get(chatId);
        if (userList == null) {
            return "Я не знаю ваших логинов и паролей :(. Попробуйте подписаться на обновления: /login логин_от_PCMS пароль_от_PCMS";
        }
        if (System.currentTimeMillis() - lastChange.getOrDefault(chatId, 0L) < 60 * 60 * 1000) {
            return "Вы можете изменять пароль не чаще чем один раз в час";
        }
        User user = null;
        for (User u : userList) {
            if (u.getLogin().equals(parts[1])) {
                user = u;
                break;
            }
        }
        if (user == null) {
            return "Такого логина у вас нет. Попробуйте подписаться на обновления: /login логин_от_PCMS пароль_от_PCMS";
        }
        if (user.getPass().equals(parts[2])) {
            return "Ваш новый пароль совпадает со старым";
        }
        synchronized (chats) {
            user.setPass(parts[2]);
            Main.dbService.saveUser(user);
        }
        System.out.printf("CHANGE: chat id %d, new password %s\n", chatId, parts[2]);
        logins.putPassword(parts[1], parts[2]);

        synchronized (lastChange) {
            lastChange.put(chatId, System.currentTimeMillis());
        }
        return "changing";
    }

    String help() {
        return "Доступные команды:\n" +
                "- подписаться на обновления: /login логин_от_PCMS пароль_от_PCMS\n" +
                "- отписаться от обновлений: /logout\n" +
                "- показать логины и пароли: /show\n" +
                "- поменять пароль: /change логин_от_PCMS новый_пароль_от_PCMS";

    }
}
