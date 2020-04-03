package pcms.telegram.bot.standings;

import javax.json.JsonObject;

public class IcpcProblem extends Problem {
    long penalty;
    public IcpcProblem(JsonObject object) {
        super(object);
        penalty = object.getJsonNumber("penalty").longValue();
    }
}
