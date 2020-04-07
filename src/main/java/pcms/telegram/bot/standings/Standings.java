package pcms.telegram.bot.standings;

import javax.json.JsonObject;
import javax.json.JsonValue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Standings {
    String scoringModelId;
    String contestName;
    boolean frozen;
    List<Session<Problem>> sessions;
    //Maps session.id to session
    Map<String, Session<Problem>> sessionMap;
    List<ChallengeProblem> problems;

    public Standings(JsonObject object) {
        scoringModelId = object.getString("scoring-model-id");
        contestName = object.getString("contest-name");
        frozen = object.getBoolean("frozen");
        sessions = new ArrayList<>();
        sessionMap = new HashMap<>();
//        System.out.printf("DEBUG: Contest name '%s'\n", contestName);
        if (object.getJsonArray("sessions") != null) {
            if (scoringModelId.endsWith("ioi")) {
                for (JsonValue value : object.getJsonArray("sessions")) {
                    Session<Problem> session = new IoiSession(value.asJsonObject());
                    sessions.add(session);
                    sessionMap.put(session.id, session);
                }
            } else if (scoringModelId.endsWith("icpc")) {
                for (JsonValue value : object.getJsonArray("sessions")) {
                    Session<Problem> session = new IcpcSession(value.asJsonObject());
                    sessions.add(session);
                    sessionMap.put(session.id, session);
                }
            } else {
                System.out.printf("WARNING: Unknown scoring model '%s'\n", scoringModelId);
            }
        }
        problems = new ArrayList<>();
        if (object.getJsonArray("problems") != null) {
            for (JsonValue value : object.getJsonArray("problems")) {
                problems.add(new ChallengeProblem(value.asJsonObject()));
            }
        }
    }

    static class ChallengeProblem {
        String alias, name;

        public ChallengeProblem(JsonObject object) {
            alias = object.getString("alias");
            name = object.getString("name");
        }

    }

    public List<Session<Problem>> getSessions() {
        return sessions;
    }

    public Session<Problem> getSession(String sessionId) {
        return sessionMap.get(sessionId);
    }

    public String getContestName() {
        return contestName;
    }

    public List<ChallengeProblem> getProblems() {
        return problems;
    }
}

