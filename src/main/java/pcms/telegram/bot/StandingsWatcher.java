package pcms.telegram.bot;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import pcms.telegram.bot.domain.User;
import pcms.telegram.bot.standings.Standings;
import pcms.telegram.bot.standings.StandingsUpdate;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import java.io.IOException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.*;

public class StandingsWatcher implements Runnable {
    Bot bot;
    long timeout;
    static String listUrl;
    static String standingsUrl;
    //Maps site-id,contest-id -> standings
    private Map<String, Standings> standings;
    //Maps site-id,contest-id -> list of standings update
    private Map<String, List<StandingsUpdate>> updates;
    //Maps login and password pair -> site-id,contest-id
    private Map<LoginPass, Set<String>> userContests;
    private ResourceBundle standingsMessages;

    public StandingsWatcher(Bot bot, String host, long timeoutSeconds, ResourceBundle standingsMessages) {
        this(host);
        this.bot = bot;
        this.timeout = timeoutSeconds * 1000L;
        this.standingsMessages = standingsMessages;
        updates = new HashMap<>();
        userContests = new TreeMap<>();
    }

    public StandingsWatcher(String host) {
        listUrl = String.format("%s/api/party/contest/list?login=%%s&password=%%s&format=json", host);
        standingsUrl = String.format("%s/api/party/contest/standings?format=json&login=%%s&password=%%s&contest=%%s", host);
        standings = new HashMap<>();
        System.out.println("URL: " + standingsUrl);
    }

    public static boolean canLogin(String login, String pass) {
        try {
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
        long sTime = System.currentTimeMillis();
        System.out.println("DEBUG: Getting standings map. Chats are locked");
        synchronized (bot.chats) {
            //todo: this is a bad idea to lock all chats here, need to be fixed
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
                                Standings standings = getContestStandings(lp, s);
                                if (standings != null)
                                    standingsMap.put(s, standings);
                            }
                        }
                    }
                }
            }
        }
        System.out.printf("DEBUG: Got standings map. Chats were locked for %d ms\n", System.currentTimeMillis() - sTime);
        return standingsMap;
    }

    public Standings getContestStandings(LoginPass user, String contestId) {
        try {
            JsonObject ok = Utils.getJsonObject(new URL(String.format(standingsUrl, user.getLogin(), user.getPass(), contestId))).getJsonObject("ok");
            if (ok == null) return null;
            if (ok.getJsonArray("standings").size() == 0) return null;
            return new Standings(ok.getJsonArray("standings").get(0).asJsonObject());
        } catch (Exception e) {
            return null;
        }
    }

    void getUpdates(Map<String, Standings> standingsMap) {
//        System.out.println("DEBUG: getting updates");
        for (Map.Entry<String, Standings> now : standingsMap.entrySet()) {
            Standings old = standings.get(now.getKey());
            List<StandingsUpdate> upd = now.getValue().getUpdates(old, standingsMessages);
            if (upd.size() > 0) {
                if (!updates.containsKey(now.getKey())) {
                    updates.put(now.getKey(), upd);
                } else {
                    updates.get(now.getKey()).addAll(upd);
                }
            }
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
//                System.out.println("DEBUG: Updating standings");
                Map<String, Standings> standingsMap = getStandingsMap();
                getUpdates(standingsMap);
                standings = standingsMap;
                if (!updates.isEmpty()) {
//                    System.out.println("DEBUG: Updates found");
                    for (Map.Entry<Long, List<User>> entry : bot.chats.entrySet()) {
                        for (User user : entry.getValue()) {
                            if (!user.isWatchStandings()) continue;

                            for (String contestId : userContests.get(new LoginPass(user))) {
                                List<StandingsUpdate> contestUpdates = updates.get(contestId);
                                if (contestUpdates == null) continue;
                                if (bot instanceof PcmsStandingsBot) {
                                    contestUpdates = getFilteredUpdates(contestUpdates, ((PcmsStandingsBot) bot).filters.get(user.getChatId()));
                                }
                                if (contestUpdates.size() == 0) continue;

                                StringBuilder msg = new StringBuilder();
                                String contestName = MessageFormat.format(standingsMessages.getString("contestName"), standings.get(contestId).getContestName());
                                msg.append(contestName).append("\n\n");
                                for (StandingsUpdate su : contestUpdates) {
                                    msg.append(su.getMessage()).append("\n");
                                }
                                SendMessage message = new SendMessage();
                                message.setChatId(entry.getKey());
                                message.setText(msg.toString());
                                bot.offer(message);
                            }
                        }
                    }
                    updates.clear();
                }
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

    List<StandingsUpdate> getFilteredUpdates(List<StandingsUpdate> updates, Set<String> filters) {
        if (filters == null || filters.size() == 0)
            return updates;

        List<StandingsUpdate> res = new ArrayList<>();
        for (StandingsUpdate su : updates) {
            for (String filter : filters) {
                if (su.getMessage().toLowerCase().contains(filter.toLowerCase())) {
                    res.add(su);
                }
            }
        }
        return res;
    }

    public static class LoginPass implements Comparable<LoginPass> {
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

        public String getLogin() {
            return login;
        }

        public String getPass() {
            return pass;
        }
    }
}
