package pcms.telegram.bot.standings;

import javax.json.JsonObject;
import javax.json.JsonValue;
import java.util.List;

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
    public String getUpdates(Session<Problem> old, List<Standings.ChallengeProblem> challengeProblems) {
        if (old == null) {
            if (score > 0) {
                StringBuilder res = new StringBuilder(String.format("%s score 0 -> %d\n", partyName, score));
                for (int i = 0; i < problems.size(); i++) {
                    String upd = problems.get(i).getUpdates(null);
                    if (upd != null) {
                        res.append(String.format("- %s. %s %s\n", challengeProblems.get(i).alias, challengeProblems.get(i).name, upd));
                    }
                }
                return res.toString();
            }
            return null;
        }
        if (!id.equals(old.getId())) return null;
        if (!(old instanceof IoiSession)) return null;
        IoiSession oldIoi = (IoiSession) old;
        if (score != oldIoi.score) {
            StringBuilder res = new StringBuilder();
            res.append(String.format("%s score %d -> %d\n", partyName, oldIoi.score, score));
            for (int i = 0; i < problems.size(); i++) {
                String upd = problems.get(i).getUpdates(old.problems.get(i));
                if (upd != null) {
                    res.append(String.format("- %s. %s %s\n", challengeProblems.get(i).alias, challengeProblems.get(i).name, upd));
                }
            }
            return res.toString();
        }
        return null;
    }
}
