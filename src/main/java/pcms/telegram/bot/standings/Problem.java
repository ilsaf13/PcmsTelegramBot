package pcms.telegram.bot.standings;

import javax.json.JsonObject;

public abstract class Problem {
    String accepted;
    long time;
    int attempts;

    public Problem(JsonObject object) {
        accepted = object.getString("accepted");
        time = object.getJsonNumber("time").longValue();
        attempts = object.getJsonNumber("attempts").intValue();
    }

    public abstract String getUpdates(Problem old);
}
