package pcms.telegram.bot;

import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import pcms.telegram.bot.domain.User;

import java.io.*;
import java.util.*;

public class PcmsLoginPassBot extends Bot {
    //maps login -> password
    Map<String, String> logins = new HashMap<>();
    Map<Long, String> chatUsers = new TreeMap<>();
    File namesFile;
    long namesFileModified;

    static {
        type = 3;
    }

    public PcmsLoginPassBot(String name, String token, long id, DefaultBotOptions botOptions, File namesFile) {
        super(name, token, id, botOptions);
        this.namesFile = namesFile;
        try {
            logins = getLoginsFromFile();
            namesFileModified = namesFile.lastModified();
            updateLogins();
            BufferedReader br = new BufferedReader(new FileReader("chatUserNames.txt"));
            String s;
            while ((s = br.readLine()) != null) {
                String[] parts = s.split("\t");
                chatUsers.put(Long.parseLong(parts[0]), parts[1]);
            }
            br.close();
        } catch (Exception e) {
            System.out.printf("ERROR: Couldn't get logins from '%s'\n", namesFile.getAbsolutePath());
        }
    }

    Map<String, String> getLoginsFromFile() throws Exception {
        Map<String, String> logins = new HashMap<>();
        BufferedReader br = new BufferedReader(new FileReader(namesFile));
        String s;
        while ((s = br.readLine()) != null) {
            String[] parts = s.split("\t");
            logins.put(parts[1], parts[2]);
        }
        return logins;
    }

    void updateLogins() {
        for (List<User> userList : chats.values()) {
            for (User user : userList) {
                String pass = logins.get(user.getLogin());
                if (pass == null) {
                    stopNotifications(user.getChatId());
                } else if (!pass.equals(user.getPass())) {
                    user.setPass(pass);
                    Main.dbService.saveUser(user);
                    SendMessage message = new SendMessage().setChatId(user.getChatId());
                    message.setText("У вас новый пароль от PCMS. Нажмите /show чтобы увидеть его");
                    offer(message);
                }
            }
        }
    }

    boolean isNamesFileUpdated() {
        return namesFile.lastModified() > namesFileModified;
    }

    boolean updateLoginsIfModified() {
        if (isNamesFileUpdated()) {
            try {
                logins = getLoginsFromFile();
                namesFileModified = namesFile.lastModified();
                updateLogins();
                return true;
            } catch (Exception e) {
                System.out.printf("ERROR: Couldn't get logins from '%s'\n", namesFile.getAbsolutePath());
            }
        }
        return false;
    }

    @Override
    public String stopNotifications(long chatId) {
        Main.dbService.deleteUser(id, chatId);
        synchronized (chats) {
            chats.remove(chatId);
        }
        System.out.println("LOGOUT: " + User.getLoginList(chats.get(chatId)));
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
                chatUsers.put(chatId, userName);
                PrintWriter pw = new PrintWriter("chatUserNames.txt");
                for (Map.Entry<Long, String> entry : chatUsers.entrySet()) {
                    pw.println(entry.getKey() + "\t" + entry.getValue());
                }
                pw.close();
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("ERROR: Error writing chat users to file");
            }
        }
        SendMessage message = new SendMessage().setChatId(chatId);
        if (message_text.startsWith("/login")) {
            message.setText(login(chatId, message_text));
        } else if (message_text.startsWith("/logout")) {
            message.setText(logout(chatId, message_text));
        } else if (message_text.equals("/help")) {
            message.setText(help());
        } else if (message_text.equals("/show")) {
            message.setText(show(chatId));
        } else if (message_text.equals("/update")) {
            if (updateLoginsIfModified()) {
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
        user.setWatchPasswords(user.getPass().equals(logins.get(user.getLogin())));

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
            return "Не нашел ваш логи и пароль: " + user.getLogin() + " " + user.getPass();
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

    String help() {
        return "Доступные команды:\n" +
                "- подписаться на обновления: /login логин_от_PCMS пароль_от_PCMS\n" +
                "- отписаться от обновлений: /logout\n" +
                "- показать логины и пароли: /show";

    }
}
