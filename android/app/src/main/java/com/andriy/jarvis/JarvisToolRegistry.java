package com.andriy.jarvis;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashMap;
import java.util.Map;

/** Спільні схеми з Worker; жоден параметр моделі не надає дозволу виконання. */
public final class JarvisToolRegistry {
    public static final int VERSION = 1;
    private final Map<String, JSONObject> tools = new HashMap<>();

    public JarvisToolRegistry(String catalog) throws Exception {
        JSONObject root = new JSONObject(catalog);
        if (root.getInt("version") != VERSION) throw new IllegalArgumentException("Версія каталогу");
        JSONArray entries = root.getJSONArray("tools");
        for (int i = 0; i < entries.length(); i++) {
            JSONObject tool = entries.getJSONObject(i);
            if (tools.put(tool.getString("name"), tool) != null)
                throw new IllegalArgumentException("Повторний інструмент");
        }
    }

    public boolean valid(JSONObject d) {
        try {
            if (!keys(d, "intent", "tool", "args", "confidence")) return false;
            if (!(d.get("intent") instanceof String) || d.getString("intent").trim().isEmpty() ||
                d.getString("intent").length() > 120 || !(d.get("tool") instanceof String)) return false;
            Object confidence = d.get("confidence");
            if (!(confidence instanceof Number)) return false;
            double value = ((Number) confidence).doubleValue();
            if (!Double.isFinite(value) || value < 0 || value > 1) return false;
            JSONObject tool = tools.get(d.getString("tool"));
            if (tool == null || !(d.get("args") instanceof JSONObject)) return false;
            JSONObject args = d.getJSONObject("args"), rules = tool.getJSONObject("parameters");
            if (args.length() != rules.length()) return false;
            java.util.Iterator<String> names = rules.keys();
            while (names.hasNext()) {
                String name = names.next();
                if (!args.has(name) || !validValue(args.get(name), rules.getJSONObject(name))) return false;
            }
            return true;
        } catch (Exception invalid) { return false; }
    }

    public boolean confirmationRequired(JSONObject d) {
        if (!valid(d)) return true;
        return tools.get(d.optString("tool")).optBoolean("confirmation", true);
    }

    private static boolean validValue(Object value, JSONObject rule) throws Exception {
        switch (rule.getString("type")) {
            case "integer":
                if (!(value instanceof Number)) return false;
                double number = ((Number) value).doubleValue();
                return Double.isFinite(number) && number == Math.rint(number) &&
                    number >= rule.getDouble("minimum") && number <= rule.getDouble("maximum");
            case "boolean": return value instanceof Boolean;
            case "string":
                if (!(value instanceof String) || ((String) value).trim().isEmpty()) return false;
                JSONArray choices = rule.optJSONArray("enum");
                if (choices != null) {
                    for (int i = 0; i < choices.length(); i++) if (value.equals(choices.get(i))) return true;
                    return false;
                }
                return ((String) value).length() >= rule.getInt("minLength") &&
                    ((String) value).length() <= rule.getInt("maxLength");
            default: return false;
        }
    }

    public static boolean keys(JSONObject value, String... names) {
        if (value == null || value.length() != names.length) return false;
        for (String name : names) if (!value.has(name)) return false;
        return true;
    }
}
