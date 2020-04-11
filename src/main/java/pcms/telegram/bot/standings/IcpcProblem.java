package pcms.telegram.bot.standings;

import javax.json.JsonObject;

public class IcpcProblem extends Problem {
    long penalty;
    public IcpcProblem(JsonObject object) {
        super(object);
        penalty = object.getJsonNumber("penalty").longValue();
    }

    @Override
    public String getUpdates(Problem old) {
        if (old == null) {
            if ("YES".equals(accepted)) {
                return String.format("+%d", attempts - 1);
            }
            return null;
        }
        if (!(old instanceof IcpcProblem)) return null;
        if (!accepted.equals(old.accepted)) {
            if ("YES".equals(accepted))
                if (attempts == 1)
                    return "+";
                else
                    return String.format("+%d", attempts - 1);
            else
                return String.format("-%d", attempts);
        }
        return null;
    }
}
