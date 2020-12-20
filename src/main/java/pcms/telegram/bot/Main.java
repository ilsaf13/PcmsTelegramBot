package pcms.telegram.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.ApiContext;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import javax.json.JsonObject;
import javax.json.JsonValue;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.util.ArrayList;
import java.util.Locale;
import java.util.ResourceBundle;

@SpringBootApplication
public class Main {

    static DbService dbService;
    static ArrayList<Bot> bots;

    public static void main(String[] args) throws FileNotFoundException, UnsupportedEncodingException {

        Locale.setDefault(new Locale("en", "US"));
        String path = PcmsBot.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        path = path.substring(path.indexOf("file:/") + 6, path.indexOf(".jar!"));
        path = path.substring(0, path.lastIndexOf("/") + 1);
        File dir = new File(path);

        final JsonObject botsJson = Utils.readJsonObject(new File(dir, "bot.json"));

        //Initialize Api Context
        ApiContextInitializer.init();
        DefaultBotOptions botOptions = ApiContext.getInstance(DefaultBotOptions.class);

        // Create the Authenticator that will return auth's parameters for proxy authentication
        if (botsJson.containsKey("proxy")) {
            final JsonObject proxy = botsJson.getJsonObject("proxy");
            if (proxy.containsKey("user")) {
                Authenticator.setDefault(new Authenticator() {
                    @Override
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(proxy.getString("user"),
                                proxy.getString("password").toCharArray());
                    }
                });
            }
            botOptions.setProxyHost(proxy.getString("host"));
            botOptions.setProxyPort(proxy.getInt("port"));
            // Select proxy type: [HTTP|SOCKS4|SOCKS5] (default: NO_PROXY)
            switch (proxy.getString("type", "NO_PROXY")) {
                case "HTTP":
                    botOptions.setProxyType(DefaultBotOptions.ProxyType.HTTP);
                    break;
                case "SOCKS4":
                    botOptions.setProxyType(DefaultBotOptions.ProxyType.SOCKS4);
                    break;
                case "SOCKS5":
                    botOptions.setProxyType(DefaultBotOptions.ProxyType.SOCKS5);
                    break;
                default:
                    botOptions.setProxyType(DefaultBotOptions.ProxyType.NO_PROXY);
            }
        }

        SpringApplication.run(Main.class, args);

        //Instantiate Telegram Bots API
        TelegramBotsApi botsApi = new TelegramBotsApi();

        //Register our bots
        bots = new ArrayList<>();
        for (JsonValue value : botsJson.getJsonArray("bots")) {
            JsonObject botJson = value.asJsonObject();
            switch (botJson.getInt("type")) {
                case 1: {
                    PcmsBot bot = new PcmsBot(botJson.getString("botUsername"), botJson.getString("botToken"),
                            botJson.getJsonNumber("id").longValue(), botOptions);
                    try {
                        botsApi.registerBot(bot);
                    } catch (TelegramApiException e) {
                        e.printStackTrace();
                    }
                    new Thread(bot).start();

                    RunListWatcher runListWatcher = new RunListWatcher(bot, botsJson.getString("url"), botJson.getInt("timeout"));
                    QuestionsWatcher questionsWatcher = new QuestionsWatcher(bot, botsJson.getString("url"), botJson.getInt("timeout"));
                    ResourceBundle standingsMessages = ResourceBundle.getBundle("StandingsMessages", new Locale(botJson.getString("language", "en")));
                    StandingsWatcher standingsWatcher = new StandingsWatcher(bot, botsJson.getString("url"), botJson.getInt("timeout"), standingsMessages);

                    new Thread(runListWatcher).start();
                    new Thread(questionsWatcher).start();
                    new Thread(standingsWatcher).start();

                    bots.add(bot);
                    break;
                }
                case 2: {
                    ResourceBundle standingsMessages = ResourceBundle.getBundle("StandingsMessages", new Locale(botJson.getString("language", "en")));
                    File standingsJson = new File(dir, botJson.getString("public-standings"));
                    PcmsStandingsBot bot = new PcmsStandingsBot(botJson.getString("botUsername"), botJson.getString("botToken"),
                            botJson.getJsonNumber("id").longValue(), botOptions, standingsJson, standingsMessages);
                    try {
                        botsApi.registerBot(bot);
                    } catch (TelegramApiException e) {
                        e.printStackTrace();
                    }
                    new Thread(bot).start();

                    StandingsWatcher standingsWatcher = new StandingsWatcher(bot, botsJson.getString("url"), botJson.getInt("timeout"), standingsMessages);

                    new Thread(standingsWatcher).start();

                    bots.add(bot);
                    break;
                }
                case 3: {
                    PcmsLoginPassBot bot = new PcmsLoginPassBot(botJson.getString("botUsername"), botJson.getString("botToken"),
                            botJson.getJsonNumber("id").longValue(), botOptions, new File(botJson.getString("namesFile")));
                    try {
                        botsApi.registerBot(bot);
                    } catch (TelegramApiException e) {
                        e.printStackTrace();
                    }
                    new Thread(bot).start();

                    bots.add(bot);
                    break;
                }
            }

        }
    }
}