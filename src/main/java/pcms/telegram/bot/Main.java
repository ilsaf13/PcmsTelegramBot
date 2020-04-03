package pcms.telegram.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.ApiContext;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;

@SpringBootApplication
public class Main {

    static PcmsBot bot;
    static DbService dbService;

    public static void main(String[] args) throws FileNotFoundException, UnsupportedEncodingException {

        String path = PcmsBot.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        path = path.substring(path.indexOf("file:/") + 6, path.indexOf(".jar!"));
        path = path.substring(0, path.lastIndexOf("/") + 1) + "bot.json";

        JsonReader reader = Json.createReader(new InputStreamReader(new FileInputStream(path), "UTF-8"));
        final JsonObject object = reader.readObject();

        //Initialize Api Context
        ApiContextInitializer.init();
        DefaultBotOptions botOptions = ApiContext.getInstance(DefaultBotOptions.class);

        // Create the Authenticator that will return auth's parameters for proxy authentication
        if (object.containsKey("proxy")) {
            final JsonObject proxy = object.getJsonObject("proxy");
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

        //Register our bot
        bot = new PcmsBot(object.getString("botUsername"), object.getString("botToken"), botOptions);
        bot.init();
        try {
            botsApi.registerBot(bot);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }

        RunListWatcher runListWatcher = new RunListWatcher(bot, object.getString("url"), object.getInt("timeout"));
        QuestionsWatcher questionsWatcher = new QuestionsWatcher(bot, object.getString("url"), object.getInt("timeout"));
        StandingsWatcher standingsWatcher = new StandingsWatcher(bot, object.getString("url"), object.getInt("timeout"));

        new Thread(runListWatcher).start();
        new Thread(questionsWatcher).start();
        new Thread(standingsWatcher).start();
    }
}