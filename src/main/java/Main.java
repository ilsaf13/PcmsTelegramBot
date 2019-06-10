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

public class Main {
    public static void main(String[] args) throws FileNotFoundException, UnsupportedEncodingException {
        JsonReader reader = Json.createReader(new InputStreamReader(new FileInputStream("bot.json"), "UTF-8"));
        JsonObject object = reader.readObject();
        // TODO Initialize Api Context
        ApiContextInitializer.init();

        // TODO Instantiate Telegram Bots API
        TelegramBotsApi botsApi = new TelegramBotsApi();

        // TODO Register our bot
        PcmsBot bot = new PcmsBot(object.getString("botUsername"), object.getString("botToken"));
        try {
            botsApi.registerBot(bot);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }

        RunListWatcher watcher = new RunListWatcher(bot, object.getString("url"), object.getInt("timeout"));

        new Thread(watcher).start();
    }
}