package pcms.telegram.bot.standings;

import javax.json.JsonObject;
import javax.json.JsonValue;

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
    public String getUpdates(Session<Problem> old) {
        if (old == null) {
            if (solved > 0)
                return String.format("%s solved %d -> %d\n", partyName, 0, solved);
            return null;
        }
        if (!id.equals(old.getId())) return null;
        if (!(old instanceof IcpcSession)) return null;
        IcpcSession oldIcpc = (IcpcSession) old;
        if (solved != oldIcpc.solved) {
            return String.format("%s solved %d -> %d\n", partyName, oldIcpc.solved, solved);
        }
        return null;
    }
}
