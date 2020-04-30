package pcms.telegram.bot;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonReader;
import java.io.*;
import java.net.URL;

public class Utils {
    public static JsonObject getJsonObject(URL url) throws IOException {
        JsonReader reader = Json.createReader(new InputStreamReader(
                url.openStream(),"UTF-8"));
        return reader.readObject();
    }

    public static JsonObject readJsonObject(File file) throws FileNotFoundException, UnsupportedEncodingException {
        JsonReader reader = Json.createReader(new InputStreamReader(new FileInputStream(file), "UTF-8"));
        JsonObject object = reader.readObject();
        return object;
    }
}
