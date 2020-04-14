package pcms.telegram.bot;

import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import pcms.telegram.bot.domain.User;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PcmsBot extends TelegramLongPollingBot implements Runnable {

    final HashMap<Long, List<User>> chats = new HashMap<>();
    private BlockingQueue<SendMessage> msgQueue = new LinkedBlockingQueue<>();
    private String botUsername;
    private String botToken;
    private static Set<String> defaultNotifications = Stream.of("runs", "questions", "standings")
            .collect(Collectors.toCollection(HashSet::new));

    public PcmsBot() {
    }

    public PcmsBot(String name, String token, DefaultBotOptions botOptions) {
        super(botOptions);
        botUsername = name;
        botToken = token;
    }

    public void init() {
        Iterable<User> users = Main.dbService.findUsers();
        for (User u : users) {
            List<User> userList = chats.get(u.getChatId());
            if (userList == null) {
                userList = new ArrayList<>();
                synchronized (chats) {
                    chats.put(u.getChatId(), userList);
                }
            }
            userList.add(u);
        }
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
            Main.dbService.deleteUserByChatId(chatId);
            synchronized (chats) {
                chats.remove(chatId);
            }
            System.out.println("LOGOUT: " + User.getLoginList(chats.get(chatId)));
            return "Stopped watching all users";
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
                Main.dbService.deleteUserByChatIdAndLoginAndPass(user.getChatId(), user.getLogin(), user.getPass());
                System.out.println("LOGOUT: " + user.getLogin());
                return "Stopped watching user " + user.getLogin();
            }
            return "Not found login and password pair: " + user.getLogin() + " " + user.getPass();
        }

        return "Type /logout to stop watching all users or /logout <user> <pass> for one user";
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
        } else {
            //todo: other commands
            if (chats.containsKey(chatId)) {
                StringBuilder sb = new StringBuilder("Watching your users: ");
                sb.append(User.getLoginList(chats.get(chatId))).append("\n");
                message.setText(sb.toString());
            } else {
                message.setText("Provide your login and password by typing /login <user> <pass>");
            }
        }

        msgQueue.offer(message);
//            try {
//                execute(message);
//            } catch (TelegramApiException e) {
//                e.printStackTrace();
//            }
    }

    public boolean offer(SendMessage msg) {
        return msgQueue.offer(msg);
    }

    @Override
    public void run() {
        int errors = 0;
        SendMessage msg = null;
        while (true) {
            try {
                if (errors == 0) {
                    msg = msgQueue.take();
                }
                System.out.printf("DEBUG: Executing message '%s'\n", msg.getText());
                execute(msg);
                errors = 0;
            } catch (TelegramApiException e) {
                errors++;
                int timeout = Math.min(errors, 5);
                System.out.printf("ERROR: Sending message failed %d times. Waiting %d minutes to retry\n", errors, timeout);
                if (errors > 10) {
                    System.out.printf("\tchat-id %s text '%s'", msg.getChatId(), msg.getText());
                    e.printStackTrace();
                }
                try {
                    Thread.sleep(timeout * 60L * 1000L);
                } catch (InterruptedException ignored) {
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public String getBotUsername() {
        return botUsername;
    }

    public String getBotToken() {
        return botToken;
    }
}
