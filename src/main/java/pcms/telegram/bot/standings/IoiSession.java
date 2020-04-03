package pcms.telegram.bot.standings;

import javax.json.JsonObject;
import javax.json.JsonValue;

public class IoiSession extends Session<Problem> {
    int score;

    public IoiSession(JsonObject object) {
        super(object);
        score = object.getJsonNumber("score").intValue();
        for (JsonValue value : object.getJsonArray("problems")) {
            problems.add(new IoiProblem(value.asJsonObject()));
        }
    }

    @Override
    public String getUpdates(Session<Problem> old) {
        if (old == null) {
            if (score > 0)
                return String.format("%s score %d -> %d\n", partyName, 0, score);
            return null;
        }
        if (!id.equals(old.getId())) return null;
        if (!(old instanceof IoiSession)) return null;
        IoiSession oldIoi = (IoiSession) old;
        if (score != oldIoi.score) {
            return String.format("%s score %d -> %d\n", partyName, oldIoi.score, score);
        }
        return null;
    }
}
