package pcms.telegram.bot.standings;

import javax.json.JsonObject;
import javax.json.JsonValue;
import java.text.MessageFormat;
import java.util.List;
import java.util.ResourceBundle;

public class IcpcSession extends Session<Problem>{
    int solved;
    long penalty;
    long time;

    public IcpcSession(JsonObject object) {
        super(object);
        solved = object.getJsonNumber("solved").intValue();
        penalty = object.getJsonNumber("penalty").longValue();
        time = object.getJsonNumber("time").longValue();
        for (JsonValue value : object.getJsonArray("problems")) {
            problems.add(new IcpcProblem(value.asJsonObject()));
        }
    }

    @Override
    public String getUpdates(Session<Problem> old, List<Standings.ChallengeProblem> challengeProblems, ResourceBundle standingsMessages) {
        //{0} solved {1} -> {2}
        String sessionSolved = standingsMessages.getString("sessionSolved");
        if (old == null) {
            if (solved > 0) {
                StringBuilder res = new StringBuilder(MessageFormat.format(sessionSolved, partyName, 0, solved)).append("\n");
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
        if (!(old instanceof IcpcSession)) return null;
        IcpcSession oldIcpc = (IcpcSession) old;
        if (solved != oldIcpc.solved) {
            StringBuilder res = new StringBuilder(MessageFormat.format(sessionSolved, partyName, oldIcpc.solved, solved)).append("\n");
            for (int i = 0; i < problems.size(); i++) {
                String upd = problems.get(i).getUpdates(oldIcpc.problems.get(i));
                if (upd != null) {
                    res.append(String.format("- %s. %s %s\n", challengeProblems.get(i).alias, challengeProblems.get(i).name, upd));
                }
            }
            return res.toString();
        }
        return null;
    }

    public int getSolved() {
        return solved;
    }
}
