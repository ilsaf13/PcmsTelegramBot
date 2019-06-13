package pcms.telegram.bot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

@SpringBootApplication
public class Main {

    static PcmsBot bot;
    public static void main(String[] args) throws FileNotFoundException, UnsupportedEncodingException {
        //Initialize Api Context
        ApiContextInitializer.init();

        SpringApplication.run(Main.class, args);

        String path = PcmsBot.class.getProtectionDomain().getCodeSource().getLocation().getPath();
        path = path.substring(path.indexOf("file:/") + 6, path.indexOf(".jar!"));
        path = path.substring(0, path.lastIndexOf("/") + 1) + "bot.json";

        JsonReader reader = Json.createReader(new InputStreamReader(new FileInputStream(path), "UTF-8"));
        JsonObject object = reader.readObject();

        //Instantiate Telegram Bots API
        TelegramBotsApi botsApi = new TelegramBotsApi();

        //Register our bot
        bot.init(object.getString("botUsername"), object.getString("botToken"));
        try {
            botsApi.registerBot(bot);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }

        RunListWatcher watcher = new RunListWatcher(bot, object.getString("url"), object.getInt("timeout"));

        new Thread(watcher).start();
    }
}