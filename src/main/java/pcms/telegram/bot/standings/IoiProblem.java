package pcms.telegram.bot.standings;

import javax.json.JsonObject;

public class IoiProblem extends Problem {
    int score;

    public IoiProblem(JsonObject object) {
        super(object);
        score = object.getJsonNumber("score").intValue();
    }
}
