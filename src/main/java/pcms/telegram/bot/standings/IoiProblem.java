package pcms.telegram.bot.standings;

import javax.json.JsonObject;

public class IoiProblem extends Problem {
    int score;

    public IoiProblem(JsonObject object) {
        super(object);
        score = object.getJsonNumber("score").intValue();
    }

    @Override
    public String getUpdates(Problem old) {
        if (old == null) {
            if (score > 0)
                return String.format("0 -> %d", score);
            return null;
        }
        if (!(old instanceof IoiProblem)) {
            return null;
        }
        IoiProblem oldIoi = (IoiProblem) old;
        if (score != oldIoi.score) {
            return String.format("%d -> %d", oldIoi.score, score);
        }
        return null;
    }
}
