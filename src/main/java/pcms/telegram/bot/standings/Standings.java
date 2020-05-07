package pcms.telegram.bot.standings;

import javax.json.JsonObject;
import javax.json.JsonValue;
import java.util.*;

public class Standings {
    String scoringModelId;
    String contestName;
    boolean frozen;
    List<Session<Problem>> sessions;
    //Maps session.id to session
    Map<String, Session<Problem>> sessionMap;
    List<ChallengeProblem> problems;
    Clock clock;

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
        clock = new Clock(object.getJsonArray("clock").getJsonObject(0));
        problems = new ArrayList<>();
        if (object.getJsonArray("problems") != null) {
            for (JsonValue value : object.getJsonArray("problems")) {
                problems.add(new ChallengeProblem(value.asJsonObject()));
            }
        }

    }

    public List<StandingsUpdate> getUpdates(Standings old, ResourceBundle standingsMessages) {
        ArrayList<StandingsUpdate> updates = new ArrayList<>();
        if (old == null) return updates;
//        System.out.printf("DEBUG: Getting updates contest '%s'\n", contestName);
        if (!old.frozen && frozen) {
            updates.add(new StandingsUpdate(StandingsUpdate.Type.CLOCK, standingsMessages.getString("standingsFrozen") + "\n"));
        }
        String clockUpd = clock.getUpdates(old.clock, standingsMessages);
        if (clockUpd != null) {
//            System.out.printf("DEBUG: Clock updated contest '%s' message '%s'", contestName, clockUpd);
            updates.add(new StandingsUpdate(StandingsUpdate.Type.CLOCK, clockUpd));
        }
        if (old.getProblems().size() != problems.size()) return updates;
        for (Session<Problem> session : sessions) {
            Session<Problem> oldSession = old.getSession(session.getId());
            String update = session.getUpdates(oldSession, problems, standingsMessages);
            if (update != null) {
                updates.add(new StandingsUpdate(StandingsUpdate.Type.SESSION, update));
            }
        }
        return updates;
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

    public Clock getClock() {
        return clock;
    }
}

