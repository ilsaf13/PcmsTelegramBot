package pcms.telegram.bot;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import pcms.telegram.bot.domain.User;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class QuestionsWatcher implements Runnable{
    PcmsBot bot;
    final long timeout;
    static String url;

    public QuestionsWatcher(PcmsBot bot, String host, long timeout) {
        this.bot = bot;
        this.timeout = timeout * 1000;
        url = String.format("%s/api/admin/questions?login=%%s&password=%%s&format=json&answer-type=null", host);
    }

    public static boolean canLogin(String login, String pass) {
        try {
            JsonObject ok = Utils.getJsonObject(new URL(String.format(url, login, pass))).getJsonObject("ok");
            return ok != null;
        } catch (Exception ignored) {
        }
        return false;
    }

    String getQuestions(User user) throws IOException {
        JsonObject ok = Utils.getJsonObject(new URL(String.format(url, user.getLogin(), user.getPass()))).getJsonObject("ok");
        if (ok != null) {
            int total = ok.getInt("total");
            Set<Long> userQuestions = new HashSet<Long>();
            if (total == 0) {
                if (user.questions.size() > 0) {
                    user.questions = userQuestions;
                    return String.format("User %s. All previous questions are answered", user.getLogin());
                }
                return null;
            }

            JsonArray questions = ok.getJsonArray("item");
            StringBuilder sb = new StringBuilder();
            sb.append("Login: ").append(user.getLogin()).append(". ");
            sb.append("Unanswered questions count: ").append(total);
            for (JsonValue jv : questions) {
                JsonObject jo = jv.asJsonObject();
                userQuestions.add(jo.getJsonNumber("id").longValue());
                if (!user.questions.contains(jo.getJsonNumber("id").longValue())) {
                    sb.append("\n\nsession-id: ").append(jo.getString("session-id"));
                    sb.append("\nparty-name: ").append(jo.getString("party-name"));
                    JsonObject problem = jo.getJsonArray("problem").get(0).asJsonObject();
                    sb.append("\nproblem: ").append(problem.getString("alias")).append(". ").
                            append(problem.getString("name"));
                    sb.append("\ntext: ").append(jo.getString("text"));
                }
            }
            total = user.questions.size();
            user.questions = userQuestions;
            if (total != userQuestions.size()) {
                return sb.toString();
            }
            return null;
        } else {
            return String.format("Login: %s. Couldn't get API response for questions.\n" +
                    "Type /logout to stop watching all users or /logout <user> <pass> for one user", user.getLogin());
        }
    }

    public void run() {
        while (true) {
            for (Map.Entry<Long, List<User>> entry : bot.chats.entrySet()) {
                for (User user : entry.getValue()) {
                    if (user.isWatchQuestions()) {
                        try {
                            String questions = getQuestions(user);
                            if (questions != null) {
                                SendMessage message = new SendMessage().setChatId(entry.getKey()).setText(questions);
//                                bot.execute(message);
                                bot.offer(message);
                            }
                        } catch (Exception e) {
                            System.out.printf("Error sending message! Chat-id %d login %s\n", user.getChatId(), user.getLogin());
                            e.printStackTrace();
                        }
                    }
                }
            }

            try {
                Thread.sleep(timeout);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
