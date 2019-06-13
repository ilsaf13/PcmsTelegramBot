package pcms.telegram.bot;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import pcms.telegram.bot.domain.User;
import pcms.telegram.bot.repos.UserRepo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Service
public class PcmsBot extends TelegramLongPollingBot {
    @Autowired
    UserRepo userRepo;

    //chatId -> List<User>
    final HashMap<Long, List<User>> chats = new HashMap<Long, List<User>>();
    private String botUsername;
    private String botToken;

    public PcmsBot() {
        Main.bot = this;
    }

    public void init(String name, String token) {
        botUsername = name;
        botToken = token;
        Iterable<User> users = userRepo.findAll();
        for (User u : users) {
            List<User> userList = chats.get(u.getId());
            if (userList == null) {
                userList = new ArrayList<User>();
                synchronized (chats) {
                    chats.put(u.getChatId(), userList);
                }
            }
            userList.add(u);
        }
    }

    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String message_text = update.getMessage().getText();
            long chatId = update.getMessage().getChatId();

            SendMessage message = new SendMessage().setChatId(chatId);

            User user = new User();
            user.setChatId(chatId);

            if (message_text.startsWith("/login")) {
                String[] parts = message_text.split(" ");
                if (parts.length == 3) {
                    user.setLogin(parts[1]);
                    user.setPass(parts[2]);

                    if (RunListWatcher.canLogin(user.getLogin(), user.getPass())) {
                        List<User> userList = chats.get(chatId);
                        if (userList == null) {
                            userList = new ArrayList<User>();
                            synchronized (chats) {
                                chats.put(chatId, userList);
                            }
                        }
                        User other = User.get(userList, user);
                        if (other != null) {
                            user = other;
                            message.setText("Already watching your undefined and failed Jobs");
                        } else {
                            userRepo.save(user);
                            synchronized (userList) {
                                userList.add(user);
                            }
                            message.setText("Starting to watch undefined and failed Jobs. Type /logout to stop");
                            System.out.println("LOGIN: " + user.getLogin());
                        }
                    } else {
                        message.setText("Sorry, couldn't login. Provide your login and password by typing /login <user> <pass>");
                        System.out.println("LOGIN FAILED: " + user.getLogin());
                    }

                } else {
                    message.setText("Sorry. Provide your login and password by typing /login <user> <pass>");
                    System.out.println("LOGIN FAILED: " + message_text);
                }

            } else if (message_text.startsWith("/logout")) {
                synchronized (chats) {
                    chats.remove(chatId);
                }
                userRepo.deleteByChatId(user.getChatId());
                message.setText("Stopped watching your jobs");
                System.out.println("LOGOUT: " + user.getLogin());
            } else {
                //todo: other commands
                if (chats.containsKey(chatId)) {
                    message.setText("Already watching your undefined and failed Jobs. Logins: " +
                            User.getLoginList(chats.get(chatId)));
                } else {
                    message.setText("Provide your login and password by typing /login <user> <pass>");
                }
            }

            try {
                execute(message);
            } catch (TelegramApiException e) {
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
