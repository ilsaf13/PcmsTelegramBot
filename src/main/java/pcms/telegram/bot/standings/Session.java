package pcms.telegram.bot.standings;

import javax.json.JsonObject;
import java.util.ArrayList;
import java.util.List;

public abstract class Session<T extends Problem> {
    String id;
    String partyName;
    int rank;
    List<T> problems;

    public Session(JsonObject object) {
        id = object.getString("id");
        partyName = object.getString("party-name");
        rank = object.getJsonNumber("rank").intValue();
        problems = new ArrayList<>();
    }

    public String getId(){
        return id;
    }

    public abstract String getUpdates(Session<Problem> old);
}
