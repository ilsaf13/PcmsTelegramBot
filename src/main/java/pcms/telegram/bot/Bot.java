package pcms.telegram.bot;

import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import pcms.telegram.bot.domain.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public abstract class Bot extends TelegramLongPollingBot implements Runnable {
    private BlockingQueue<SendMessage> msgQueue = new LinkedBlockingQueue<>();
    private String botUsername;
    private String botToken;
    static long type;
    long id;

    //maps chatId -> user list
    final HashMap<Long, List<User>> chats = new HashMap<>();

    public Bot() {
        super();
    }

    public Bot(String name, String token, long id, DefaultBotOptions botOptions) {
        super(botOptions);
        botUsername = name;
        botToken = token;
        this.id = id;
        Iterable<User> users = Main.dbService.findUsersByBotId(id);
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

    public abstract String stopNotifications(long chatId);

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
                System.out.println("DEBUG: Exception message '" + e.toString() + "'");
                if (e.toString().contains("bot was blocked by the user")) {
                    System.out.printf("INFO: Bot was blocked by the user. Stopping all notifications for chat id '%s'\n", msg.getChatId());
                    stopNotifications(Long.parseLong(msg.getChatId()));
                    errors = 0;
                }
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
