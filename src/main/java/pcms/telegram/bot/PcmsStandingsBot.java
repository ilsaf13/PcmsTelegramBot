package pcms.telegram.bot;

import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import pcms.telegram.bot.domain.StandingsFilter;
import pcms.telegram.bot.domain.User;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import java.io.File;
import java.text.MessageFormat;
import java.util.*;

public class PcmsStandingsBot extends Bot {
    private File jsonFile;
    private JsonObject standingsJson;
    //maps chatId -> set of StandingsFilter.text
    final Map<Long, Set<String>> filters;
    final ResourceBundle standingsMessages;

    static {
        type = 2;
    }

    public PcmsStandingsBot(String name, String token, long id, DefaultBotOptions botOptions, File jsonFile, ResourceBundle standingsMessages) {
        super(name, token, id, botOptions);
        this.standingsMessages = standingsMessages;
        filters = new HashMap<>();
        Iterable<StandingsFilter> list = Main.dbService.findStandingsFiltersByBotId(id);
        for (StandingsFilter sf : list) {
            Set<String> set = filters.get(sf.getChatId());
            if (set == null) {
                set = new HashSet<>();
                synchronized (filters) {
                    filters.put(sf.getChatId(), set);
                }
            }
            set.add(sf.getText());
        }
        updateStandings(jsonFile);
    }

    void updateStandings(File jsonFile) {
        this.jsonFile = jsonFile;
        try {
            standingsJson = Utils.readJsonObject(jsonFile);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText())
            return;

        String message_text = update.getMessage().getText();
        System.out.printf("DEBUG: got message '%s'\n", message_text);
        long chatId = update.getMessage().getChatId();
        SendMessage message = new SendMessage().setChatId(chatId);

        if (message_text.matches("/\\d+")) {
            message.setText(selectContest(chatId, message_text));
        } else if (message_text.startsWith("/stop")) {
            message.setText(stopNotifications(chatId));
        } else if (message_text.startsWith("/filter")) {
            message.setText(filter(chatId, message_text));
        } else if (message_text.equals("/removeFilters")) {
            message.setText(removeFilters(chatId));
        } else {
            //group chat
            if (chatId < 0) return;
            //personal chat
            message.setText(contestList());
        }
        offer(message);
    }

    String removeFilters(long chatId) {
        Main.dbService.deleteStandingsFilters(chatId, id);
        synchronized (filters) {
            filters.remove(chatId);
        }
        return standingsMessages.getString("filtersRemoved");
    }
    String filter(long chatId, String text) {
        String name = text.substring("/filter".length()).trim();
        Set<String> chatFilters = filters.get(chatId);
        if (name.length() != 0) {
            if (chatFilters == null) {
                chatFilters = new HashSet<>();
                synchronized (filters) {
                    filters.put(chatId, chatFilters);
                }
            }
            if (chatFilters.contains(name)) {
                return standingsMessages.getString("filterAlreadyExist");
            } else {
                chatFilters.add(name);
                StandingsFilter filter = new StandingsFilter();
                filter.setBotId(id);
                filter.setChatId(chatId);
                filter.setText(name);
                Main.dbService.saveStandingsFilter(filter);
                return MessageFormat.format(standingsMessages.getString("filterAdded"), name);
            }
        }
        StringBuilder sb = new StringBuilder();
        if (chatFilters != null && !chatFilters.isEmpty()) {
            sb.append(standingsMessages.getString("filtersList")).append(" ");
            int i = 0;
            for (String t : chatFilters) {
                if (i != 0) sb.append(", ");
                sb.append("'").append(t).append("'");
                i++;
            }
            sb.append("\n\n");
        }
        sb.append(standingsMessages.getString("filterAddHelp"));
        return sb.toString();
    }

    String selectContest(long chatId, String text) {
        int index = 0;
        try {
            index = Integer.parseInt(text.substring(1));
            JsonObject jo = standingsJson.getJsonArray("standings").getJsonObject(index - 1);
            User user = new User();
            user.setBotId(type);
            user.setChatId(chatId);
            user.setLogin(jo.getString("login"));
            user.setPass(jo.getString("password"));
            user.setWatchStandings(true);
            List<User> userList = chats.get(chatId);
            if (userList == null) {
                userList = new ArrayList<>();
                synchronized (chats) {
                    chats.put(chatId, userList);
                }
            }
            int ind = userList.indexOf(user);
            if (ind != -1) {
                user.setId(userList.get(ind).getId());
            } else {
                synchronized (chats) {
                    userList.add(user);
                }
                Main.dbService.saveUser(user);
                System.out.println("LOGIN: " + user.toString());
            }
            return MessageFormat.format(standingsMessages.getString("selectedContest"), index);
        } catch (Exception e) {
            e.printStackTrace();
            return standingsMessages.getString("noSuchContest");
        }
    }

    String contestList() {
        JsonArray standings = standingsJson.getJsonArray("standings");
        StringBuilder ans = new StringBuilder(standingsMessages.getString("selectContest")).append("\n\n");
        int i = 1;
        for (JsonValue value : standings) {
            ans.append("/").append(i).append(" ").append(value.asJsonObject().getString("name")).append("\n");
            i++;
        }
        ans.append("\n").append(standingsMessages.getString("stopNotificationsHelp"));
        return ans.toString();
    }

    public String stopNotifications(long chatId) {
        Main.dbService.deleteUser(id, chatId);
        synchronized (chats) {
            chats.remove(chatId);
        }
        Main.dbService.deleteStandingsFilters(id, chatId);
        synchronized (filters) {
            filters.remove(chatId);
        }
        return standingsMessages.getString("stoppedNotifications");
    }
}
