package pcms.telegram.bot.standings;

import javax.json.JsonObject;
import javax.json.JsonValue;
import java.text.MessageFormat;
import java.util.List;
import java.util.ResourceBundle;

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
    public String getUpdates(Session<Problem> old, List<Standings.ChallengeProblem> challengeProblems, ResourceBundle standingsMessages) {
        //{0} score {1} -> {2}
        String sessionScore = standingsMessages.getString("sessionScore");
        if (old == null) {
            if (score > 0) {
                StringBuilder res = new StringBuilder(MessageFormat.format(sessionScore, partyName, 0, score)).append("\n");
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
            StringBuilder res = new StringBuilder(MessageFormat.format(sessionScore, partyName, oldIoi.score, score)).append("\n");
//            res.append(String.format("%s score %d -> %d\n", partyName, oldIoi.score, score));
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
