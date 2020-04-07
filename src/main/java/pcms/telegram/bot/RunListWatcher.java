package pcms.telegram.bot;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import pcms.telegram.bot.domain.User;

import javax.json.*;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RunListWatcher implements Runnable {
    PcmsBot bot;
    final long timeout;
    static String url;

    public static boolean canLogin(String login, String pass) {
        try {
            JsonObject ok = Utils.getJsonObject(new URL(String.format(url, login, pass, ""))).getJsonObject("ok");
            return ok != null;
        } catch (Exception ignored) {
        }
        return false;
    }

    public RunListWatcher(PcmsBot bot, String host, long timeoutSeconds) {
        this.bot = bot;
        timeout = timeoutSeconds * 1000L;
        url = String.format("%s/api/admin/run/list?login=%%s&password=%%s&format=json&outcome=%%s&flags=A", host);
        System.out.println("URL: " + url);
    }

    String getFailedRuns(User user) throws IOException {
        JsonObject ok = Utils.getJsonObject(new URL(String.format(url, user.getLogin(), user.getPass(), "FL"))).getJsonObject("ok");
        if (ok != null) {
            int total = ok.getInt("total");
            Set<String> userJobs = new HashSet<String>();
            if (total == 0) {
                if (user.failedJobs.size() > 0) {
                    user.failedJobs = userJobs;
                    return String.format("Login: %s. All previous failed runs are judged", user.getLogin());
                }
                return null;
            }
            JsonArray jobs = ok.getJsonArray("item");
            StringBuilder sb = new StringBuilder();
            sb.append("Login: ").append(user.getLogin()).append(". ");
            sb.append("Failed runs count: ").append(total).append("\n");
            total = 0;
            for (JsonValue o : jobs) {
                userJobs.add(o.asJsonObject().getString("job-id"));
                if (!user.failedJobs.contains(o.asJsonObject().getString("job-id"))) {
                    total++;
                    sb.append("job-id: ").append(o.asJsonObject().getString("job-id")).append("\n");
                    sb.append("outcome: ").append(o.asJsonObject().getString("outcome")).append("\n");
                    sb.append("\n");
                }
            }
            user.failedJobs = userJobs;
            if (total > 0) {
                return sb.toString();
            }
            return null;
        } else {
            return String.format("Login: %s. Couldn't get API response for failed jobs. " +
                    "Type /logout to stop watching all users or /logout <user> <pass> for one user", user.getLogin());
        }
    }

    String getUndefinedRuns(User user) throws IOException {
//        System.out.println("Jobs size: " + user.undefinedJobs.size());
        JsonReader reader = Json.createReader(new InputStreamReader(
                new URL(String.format(url, user.getLogin(), user.getPass(), "UD") + "&detail=job-times")
                .openStream(), "UTF-8"));
        JsonObject object = reader.readObject();
        JsonObject ok = object.getJsonObject("ok");
        if (ok != null) {
            int total = ok.getInt("total");
            Set<String> userJobs = new HashSet<String>();
            if (total == 0) {
                if (user.undefinedJobs.size() > 0) {
                    user.undefinedJobs = userJobs;
                    return String.format("Login: %s. All previous undefined runs are judged", user.getLogin());
                }
                return null;
            }
            JsonArray jobs = ok.getJsonArray("item");
            int cnt = 0;
            StringBuilder sb = new StringBuilder();
            long time = System.currentTimeMillis();
            for (JsonValue o : jobs) {
//                System.out.println(o.asJsonObject().toString());
                if (o.asJsonObject().getJsonNumber("evaluation-finished-time") == null) {
                    JsonNumber jsonNumber = o.asJsonObject().getJsonNumber("reevaluation-time");
                    long reeval = jsonNumber.longValue();
                    if (time - reeval > timeout) {
                        userJobs.add(o.asJsonObject().getString("job-id"));
                        if (!user.undefinedJobs.contains(o.asJsonObject().getString("job-id"))) {
                            cnt++;
                            sb.append("job-id: ").append(o.asJsonObject().getString("job-id")).append("\n");
                        }
                    }
                }
            }
            user.undefinedJobs = userJobs;
            if (cnt > 0)
                return String.format("Login: %s. Undefined for too long time runs count: %d\n\n%s",
                        user.getLogin(), total, sb.toString());

            return null;
        } else {
            return String.format("Login: %s. Couldn't get API response for undefined jobs. " +
                    "Type /logout to stop watching all users or /logout <user> <pass> for one user", user.getLogin());
        }
    }

    public void run() {
        while (true) {
            for (Map.Entry<Long, List<User>> entry : bot.chats.entrySet()) {
                for (User user : entry.getValue()) {
                    if (user.isWatchRuns()) {
                        try {
                            String failed = getFailedRuns(user);
                            if (failed != null) {
                                SendMessage message = new SendMessage().setChatId(entry.getKey()).setText(failed);
//                                bot.execute(message);
                                bot.offer(message);
                            }
                            String undef = getUndefinedRuns(user);
                            if (undef != null) {
                                SendMessage message = new SendMessage().setChatId(entry.getKey()).setText(undef);
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
