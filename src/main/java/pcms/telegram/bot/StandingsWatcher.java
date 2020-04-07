package pcms.telegram.bot;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import pcms.telegram.bot.domain.User;
import pcms.telegram.bot.standings.Problem;
import pcms.telegram.bot.standings.Session;
import pcms.telegram.bot.standings.Standings;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import java.io.IOException;
import java.net.URL;
import java.util.*;

public class StandingsWatcher implements Runnable {
    PcmsBot bot;
    final long timeout;
    static String listUrl;
    static String standingsUrl;
    //Maps site-id,contest-id -> standings
    private static Map<String, Standings> standings;
    //Maps site-id,contest-id -> list of standings update
    private static Map<String, List<StandingsUpdate>> updates;
    //Maps login and password pair -> site-id,contest-id
    private static Map<LoginPass, Set<String>> userContests;

    public StandingsWatcher(PcmsBot bot, String host, long timeoutSeconds) {
        this.bot = bot;
        this.timeout = timeoutSeconds * 1000L;
        listUrl = String.format("%s/api/party/contest/list?login=%%s&password=%%s&format=json", host);
        standingsUrl = String.format("%s/api/party/contest/standings?format=json&login=%%s&password=%%s&contest=%%s", host);
        standings = new HashMap<>();
        updates = new HashMap<>();
        userContests = new TreeMap<>();
        System.out.println("URL: " + standingsUrl);
    }

    public static boolean canLogin(String login, String pass) {
        try {
//            System.out.println(String.format(listUrl, login, pass));
            JsonObject ok = Utils.getJsonObject(new URL(String.format(listUrl, login, pass))).getJsonObject("ok");
            return ok != null;
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }
        return false;
    }

    Map<String, Standings> getStandingsMap() throws IOException {
        //Maps site-id,contest-id to Standings
        Map<String, Standings> standingsMap = new HashMap<>();
        for (Map.Entry<Long, List<User>> entry : bot.chats.entrySet()) {
            for (User user : entry.getValue()) {
                if (user.isWatchStandings()) {
                    LoginPass lp = new LoginPass(user);
                    JsonObject ok = Utils.getJsonObject(new URL(String.format(listUrl, user.getLogin(), user.getPass()))).getJsonObject("ok");
                    if (ok == null) {
                        System.out.printf("WARNING: Couldn't get API response for contests list. Chat-id: %d, login: %s\n", user.getChatId(), user.getLogin());
                        continue;
                    }
                    JsonArray arr = ok.getJsonArray("result");
                    if (!userContests.containsKey(lp)) userContests.put(lp, new HashSet<>());
                    Set<String> uc = userContests.get(lp);
                    for (JsonValue o : arr) {
                        String s = o.asJsonObject().getString("contest-id");
                        uc.add(s);
                        if (!standingsMap.containsKey(s)) {
                            JsonObject standingsJson = getContestStandings(user, s);
                            if (standingsJson != null)
                                standingsMap.put(s, new Standings(standingsJson));
                        }
                    }
                }
            }
        }
        return standingsMap;
    }

    JsonObject getContestStandings(User user, String contestId) throws IOException {
        JsonObject ok = Utils.getJsonObject(new URL(String.format(standingsUrl, user.getLogin(), user.getPass(), contestId))).getJsonObject("ok");
        if (ok == null) return null;
        if (ok.getJsonArray("standings").size() == 0) return null;
        return ok.getJsonArray("standings").get(0).asJsonObject();
    }

    void getUpdates(Map<String, Standings> standingsMap) {
        for (Map.Entry<String, Standings> entry : standingsMap.entrySet()) {
            Standings old = standings.get(entry.getKey());
            if (old == null) continue;
            if (old.getProblems().size() != entry.getValue().getProblems().size()) continue;

            for (Session<Problem> session : entry.getValue().getSessions()) {
                Session<Problem> oldSession = old.getSession(session.getId());
                String update = session.getUpdates(oldSession, entry.getValue().getProblems());
                if (update != null) {
                    if (!updates.containsKey(entry.getKey())) {
                        updates.put(entry.getKey(), new ArrayList<>());
                    }
                    updates.get(entry.getKey()).add(new StandingsUpdate(session, update));
                }
            }
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                System.out.println("DEBUG: Updating standings");
                Map<String, Standings> standingsMap = getStandingsMap();
                getUpdates(standingsMap);
                standings = standingsMap;
                for (Map.Entry<Long, List<User>> entry : bot.chats.entrySet()) {
                    for (User user : entry.getValue()) {
                        if (!user.isWatchStandings()) continue;

                        for (String contestId : userContests.get(new LoginPass(user))) {
                            if (!updates.containsKey(contestId)) continue;
                            StringBuilder msg = new StringBuilder();
                            msg.append("Contest: ").append(standings.get(contestId).getContestName()).append("\n");
                            for (StandingsUpdate su : updates.get(contestId)) {
                                msg.append(su.message);
                            }
                            SendMessage message = new SendMessage().setChatId(entry.getKey()).setText(msg.toString());
                            bot.offer(message);
//                            try {
//                                System.out.println("DEBUG: Sending message " + msg.toString());
//                                bot.execute(message);
//                            } catch (TelegramApiException e) {
//                                e.printStackTrace();
//                            }
                        }
                    }
                }
                updates.clear();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                Thread.sleep(timeout);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    static class StandingsUpdate {
        Session<Problem> session;
        String message;

        public StandingsUpdate(Session<Problem> session, String message) {
            this.session = session;
            this.message = message;
        }
    }

    static class LoginPass implements Comparable<LoginPass> {
        String login, pass;

        public LoginPass(String login, String pass) {
            this.login = login;
            this.pass = pass;
        }

        public LoginPass(User user) {
            login = user.getLogin();
            pass = user.getPass();
        }

        @Override
        public int compareTo(LoginPass o) {
            if (login.equals(o.login)) return pass.compareTo(o.pass);
            return login.compareTo(o.login);
        }
    }
}
