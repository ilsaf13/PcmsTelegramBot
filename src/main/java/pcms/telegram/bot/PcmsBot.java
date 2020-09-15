package pcms.telegram.bot;

import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import pcms.telegram.bot.domain.User;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PcmsBot extends Bot {

    static {
        type = 1;
    }

    private static Set<String> defaultNotifications = Stream.of("runs", "questions", "standings")
            .collect(Collectors.toCollection(HashSet::new));

    public PcmsBot(String name, String token, long id, DefaultBotOptions botOptions) {
        super(name, token, id, botOptions);
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

    String login(long chatId, String message) {
        String[] parts = message.split(" ");
        if (parts.length < 3) {
            System.out.println("LOGIN FAILED: " + message);
            return "Sorry. Provide your login and password by typing /login <user> <pass> [runs] [questions] [standings]";
        }
        Set<String> notifications;
        if (parts.length == 3) {
            notifications = defaultNotifications;
        } else {
            notifications = new HashSet<>();
            for (int i = 3; i < parts.length; i++) {
                notifications.add(parts[i]);
            }
        }
        User user = new User();
        user.setBotId(id);
        user.setChatId(chatId);
        user.setLogin(parts[1]);
        user.setPass(parts[2]);
        user.setWatchRuns(notifications.contains("runs") && RunListWatcher.canLogin(user.getLogin(), user.getPass()));
        user.setWatchQuestions(notifications.contains("questions") && QuestionsWatcher.canLogin(user.getLogin(), user.getPass()));
        user.setWatchStandings(notifications.contains("standings") && StandingsWatcher.canLogin(user.getLogin(), user.getPass()));

        if (user.isWatchRuns() || user.isWatchQuestions() || user.isWatchStandings()) {
            List<User> userList = chats.get(chatId);
            if (userList == null) {
                userList = new ArrayList<>();
                synchronized (chats) {
                    chats.put(chatId, userList);
                }
            }
            int index = userList.indexOf(user);
            if (index != -1) {
                User orig = userList.get(index);
                orig.setWatchRuns(user.isWatchRuns());
                orig.setWatchQuestions(user.isWatchQuestions());
                orig.setWatchStandings(user.isWatchStandings());
                user = orig;
            } else {
                synchronized (chats) {
                    userList.add(user);
                }
            }
            Main.dbService.saveUser(user);
            System.out.println("LOGIN: " + user.toString());
            return user.toString() + " Type /logout <user> <pass> to stop";
        }

        System.out.println("LOGIN FAILED: " + user.getLogin());
        return "Sorry, couldn't login. Provide your login and password by typing /login <user> <pass> [runs] [questions] [standings]";
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
                return "Stopped watching user " + user.getLogin();
            }
            return "Not found login and password pair: " + user.getLogin() + " " + user.getPass();
        }

        return "Type /logout to stop watching all users or /logout <user> <pass> for one user";
    }

    String help() {
        StringBuilder hlp = new StringBuilder("");
        try {
            BufferedReader br = new BufferedReader(new FileReader("help.txt"));
            String s;
            while ((s = br.readLine()) != null) {
                hlp.append(s).append("\n");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return hlp.toString();
    }

    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText())
            return;

        String message_text = update.getMessage().getText();
        long chatId = update.getMessage().getChatId();

        SendMessage message = new SendMessage().setChatId(chatId);

        User user = new User();
        user.setChatId(chatId);

        if (message_text.startsWith("/login")) {
            message.setText(login(chatId, message_text));
        } else if (message_text.startsWith("/logout")) {
            message.setText(logout(chatId, message_text));
        } else if (message_text.equals("/help")) {
            message.setText(help());
        } else if (message_text.equals("/list")) {
            message.setText(list(chatId));
        } else {
            //todo: other commands
            if (chatId < 0) return;
            message.setText("Hello! Type some command or get /help if you don't know how");
        }

        offer(message);
    }

    String list(long chatId) {
        if (chats.containsKey(chatId)) {
            StringBuilder sb = new StringBuilder("Watching your users: ");
            sb.append(User.getLoginList(chats.get(chatId))).append("\n");
            return sb.toString();
        } else {
            return "You don't have any users. Provide your login and password by typing /login <user> <pass>";
        }
    }

}
