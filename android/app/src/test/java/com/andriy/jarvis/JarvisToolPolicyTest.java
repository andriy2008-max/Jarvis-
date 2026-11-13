package com.andriy.jarvis;

import java.nio.file.Files;
import java.nio.file.Path;
import org.json.JSONObject;

/** Контракт без Android: валідація недовіреного JSON та одноразові підтвердження. */
public final class JarvisToolPolicyTest {
    private static int checks;
    private static void check(boolean value) {
        checks++;
        if (!value) throw new AssertionError("Перевірка " + checks);
    }
    private static JSONObject proposal(String tool, String args) throws Exception {
        return new JSONObject().put("intent", "test").put("tool", tool)
            .put("args", new JSONObject(args)).put("confidence", 0.95);
    }
    public static void main(String[] args) throws Exception {
        JarvisToolRegistry r = new JarvisToolRegistry(Files.readString(Path.of(args[0])));
        check(r.valid(proposal("volume_set", "{\"percent\":40}")));
        check(!r.valid(proposal("volume_set", "{\"percent\":\"40\"}")));
        check(!r.valid(proposal("volume_set", "{\"percent\":101}")));
        check(!r.valid(proposal("volume_set", "{\"percent\":-1}")));
        check(!r.valid(proposal("volume_set", "{\"percent\":1.5}")));
        check(!r.valid(proposal("volume_set", "{\"percent\":40,\"approved\":true}")));
        check(!r.valid(proposal("volume_set", "{}")));
        check(!r.valid(proposal("shell", "{\"command\":\"id\"}")));
        check(!r.valid(proposal("torch_set", "{\"enabled\":1}")));
        check(r.valid(proposal("torch_set", "{\"enabled\":false}")));
        check(!r.valid(proposal("timer_start", "{\"seconds\":10801}")));
        check(r.valid(proposal("timer_start", "{\"seconds\":10800}")));
        check(!r.valid(proposal("music_control", "{\"action\":\"delete\"}")));
        check(r.valid(proposal("music_control", "{\"action\":\"pause\"}")));
        check(!r.valid(proposal("timer_cancel", "{}").put("approved", true)));
        check(!r.valid(proposal("timer_cancel", "{}").put("confidence", "1")));
        check(!r.valid(proposal("timer_cancel", "{}").put("confidence", 1.1)));
        check(r.confirmationRequired(proposal("timer_cancel", "{}")));
        check(r.confirmationRequired(proposal("unknown", "{}")));

        JarvisToolSession session = new JarvisToolSession();
        String first = session.begin(1000);
        check(!session.claim("forged",1001));
        check(session.claim(first,1002));
        check(!session.claim(first,1003));
        first = session.begin(2000);
        String second = session.begin(2001);
        check(!session.claim(first,2002));
        check(session.claim(second,2002));
        first = session.begin(3000);
        check(!session.claim(first,48001));
        first = session.begin(4000);
        session.invalidate();
        check(!session.claim(first,4001));
        check(session.confirm(5000) == null);
        session.awaitConfirmation("exact frozen arguments",5000);
        check("exact frozen arguments".equals(session.confirm(5001)));
        check(session.confirm(5002) == null);
        session.awaitConfirmation("expired",6000);
        check(session.confirm(36001) == null);
        session.awaitConfirmation("cancelled",7000);
        session.invalidate();
        check(session.confirm(7001) == null);
        session.awaitConfirmation("old action",8000);
        session.begin(8001);
        check(session.confirm(8002) == null);
        System.out.println("JarvisToolPolicyTest: " + checks + " checks passed");
    }
}
