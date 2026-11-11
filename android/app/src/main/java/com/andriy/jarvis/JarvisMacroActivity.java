package com.andriy.jarvis;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Phone-only, local macro tab. All steps remain editable; none are AI-executed. */
public final class JarvisMacroActivity extends Activity {
    private static final int BG = Color.rgb(8,14,29);
    private static final int TEXT = Color.rgb(237,246,255);
    private static final int CYAN = Color.rgb(80,223,251);
    private static final int MUTED = Color.rgb(161,179,208);
    private static final String[] TRIGGERS = {
        "Голосова команда", "Щодня о певній годині",
        "Підключено зарядку", "Відключено зарядку"
    };
    private static final String[] TRIGGER_KEYS = {
        "voice", "time", "charger_on", "charger_off"
    };
    private static final String[] ACTIONS = {
        "Команда керування Джарвіса", "Пауза (мілісекунди)",
        "Сповіщення", "Вібрація (мілісекунди)", "Root shell (su -c)"
    };
    private static final String[] ACTION_KEYS = {
        "command", "wait", "notification", "vibrate", "root"
    };

    private LinearLayout root;
    private JSONObject editing;
    private EditText name, triggerValue, date, days, timeFrom, timeTo, battery, ssid,
        latitude, longitude, radius;
    private Spinner trigger, charging;
    private LinearLayout actionList;
    private final ArrayList<ActionRow> actionRows = new ArrayList<>();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        showList();
    }

    private int dp(float number) {
        return (int)(getResources().getDisplayMetrics().density * number + .5f);
    }
    private TextView label(String value, int size, boolean bold) {
        TextView text = new TextView(this);
        text.setText(value); text.setTextColor(TEXT); text.setTextSize(size);
        if (bold) text.setTypeface(Typeface.DEFAULT_BOLD);
        text.setPadding(0,dp(6),0,dp(5));
        return text;
    }
    private Button button(String value, Runnable action) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        button.setTextColor(CYAN);
        button.setOnClickListener(v -> action.run());
        return button;
    }
    private EditText field(LinearLayout parent, String title, String hint, String value) {
        parent.addView(label(title, 14, false));
        EditText field = new EditText(this);
        field.setTextColor(TEXT);
        field.setHintTextColor(MUTED);
        field.setTextSize(15);
        field.setHint(hint);
        field.setSingleLine(true);
        field.setText(value == null ? "" : value);
        parent.addView(field, new LinearLayout.LayoutParams(-1,-2));
        return field;
    }
    private Spinner spinner(LinearLayout parent, String label, String[] options, int selected) {
        parent.addView(label(label, 14, false));
        Spinner s = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(adapter);
        s.setSelection(Math.max(0, selected));
        parent.addView(s);
        return s;
    }
    private int index(String[] keys, String selected) {
        for (int i=0;i<keys.length;i++) if(keys[i].equals(selected)) return i;
        return 0;
    }
    private void screen(String title) {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        root = new LinearLayout(this);
        root.setPadding(dp(18),dp(24),dp(18),dp(30));
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root, new ScrollView.LayoutParams(-1,-2));
        setContentView(scroll);
        root.addView(label(title, 24, true));
        root.addView(label("Локальні автоматизації · Тригер → Дії → Обмеження",
            12, false));
    }

    private void showList() {
        editing = null;
        name = null;
        screen("⚙ Макроси");
        root.addView(button("← Джарвіс", this::finish));
        root.addView(button("＋ Створити макрос", () -> edit(null)));
        root.addView(label("Макроси зберігаються тільки на цьому пристрої.", 13, false));
        List<JSONObject> all = JarvisMacros.all(this);
        if (all.isEmpty()) root.addView(label("Поки немає макросів.", 16, false));
        for (JSONObject m : all) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setPadding(dp(8),dp(9),dp(8),dp(9));
            item.setBackgroundColor(Color.rgb(17,27,48));
            LinearLayout.LayoutParams outer = new LinearLayout.LayoutParams(-1,-2);
            outer.topMargin = dp(10);
            root.addView(item,outer);
            item.addView(label(m.optString("name","Без назви"),18,true));
            String triggerText = m.optString("trigger");
            item.addView(label("Тригер: " +
                TRIGGERS[index(TRIGGER_KEYS,triggerText)] +
                (m.optString("triggerValue").isEmpty() ? "" : " · " +
                    m.optString("triggerValue")), 13,false));
            CheckBox active = new CheckBox(this);
            active.setText("Увімкнено");
            active.setTextColor(TEXT);
            active.setChecked(m.optBoolean("enabled",true));
            active.setOnCheckedChangeListener((x, enabled) -> {
                try {m.put("enabled",enabled); JarvisMacros.save(this,m);}
                catch (Exception e) { Toast.makeText(this,"Помилка збереження",Toast.LENGTH_SHORT).show(); }
            });
            item.addView(active);
            item.addView(button("Редагувати", () -> edit(m)));
            item.addView(button("▶ Перевірити зараз", () ->
                JarvisMacros.execute(this,m,result ->
                    Toast.makeText(this,result,Toast.LENGTH_LONG).show())));
            item.addView(button("Видалити", () ->
                new AlertDialog.Builder(this).setTitle("Видалити макрос?")
                    .setMessage(m.optString("name"))
                    .setNegativeButton("Скасувати",null)
                    .setPositiveButton("Видалити",(dialog,which)->{
                        JarvisMacros.delete(this,m.optString("id"));
                        showList();
                    }).show()));
        }
    }

    private static final class ActionRow {
        LinearLayout view;
        Spinner type;
        EditText value;
        CheckBox approved;
    }
    private void addAction(JSONObject source) {
        ActionRow row = new ActionRow();
        row.view = new LinearLayout(this);
        row.view.setOrientation(LinearLayout.VERTICAL);
        row.view.setPadding(dp(8),dp(10),dp(8),dp(10));
        row.view.setBackgroundColor(Color.rgb(17,27,48));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2);
        p.topMargin = dp(8);
        actionList.addView(row.view,p);
        row.type = spinner(row.view,"Тип дії",ACTIONS,
            index(ACTION_KEYS,source == null ? "command" : source.optString("type")));
        row.value = field(row.view,"Параметри дії",
            "Напр.: увімкни ліхтарик / 1500 / текст",
            source == null ? "" : source.optString("value"));
        row.value.setSingleLine(false);
        row.value.setMinLines(2);
        row.approved = new CheckBox(this);
        row.approved.setText("Я дозволяю запуск цієї root-команди від мого імені");
        row.approved.setTextColor(TEXT);
        row.approved.setChecked(source != null && source.optBoolean("rootApproved"));
        row.view.addView(row.approved);
        row.approved.setVisibility("root".equals(
            ACTION_KEYS[row.type.getSelectedItemPosition()]) ? View.VISIBLE : View.GONE);
        row.type.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v,
                                                 int position, long id) {
                row.approved.setVisibility("root".equals(ACTION_KEYS[position])
                    ? View.VISIBLE : View.GONE);
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        row.view.addView(button("↑ Вище", () -> move(row,-1)));
        row.view.addView(button("↓ Нижче", () -> move(row,1)));
        row.view.addView(button("✕ Прибрати дію",()-> {
            actionRows.remove(row); actionList.removeView(row.view);
        }));
        actionRows.add(row);
    }
    private void move(ActionRow row, int direction) {
        int idx = actionRows.indexOf(row);
        int target = idx + direction;
        if (idx < 0 || target < 0 || target >= actionRows.size()) return;
        actionRows.remove(idx); actionRows.add(target,row);
        actionList.removeView(row.view);
        actionList.addView(row.view,target);
    }

    private void edit(JSONObject previous) {
        editing = previous;
        actionRows.clear();
        JSONObject c = previous == null ? null : previous.optJSONObject("constraints");
        if(c == null) c = new JSONObject();
        screen(previous == null ? "Новий макрос" : "Редагування макросу");
        root.addView(button("← До списку",this::showList));
        root.addView(label("ТРИГЕР",18,true));
        name = field(root,"Назва","Наприклад, Приходжу додому",
            previous == null ? "" : previous.optString("name"));
        trigger = spinner(root,"Подія або голосова команда",TRIGGERS,
            previous == null ? 0 : index(TRIGGER_KEYS,previous.optString("trigger")));
        triggerValue = field(root,"Голосова фраза або час (HH:mm)",
            "Наприклад, режим роботи / 08:30",
            previous == null ? "" : previous.optString("triggerValue"));
        root.addView(label("ДІЇ · виконуються по черзі",18,true));
        root.addView(label("Команда Джарвіса: ліхтарик, звук, Wi-Fi, застосунки, "
            +"екранні команди. Для екранних послідовностей потрібен дозвіл Accessibility.",
            13,false));
        actionList = new LinearLayout(this);
        actionList.setOrientation(LinearLayout.VERTICAL);
        root.addView(actionList);
        JSONArray prevSteps = previous == null ? null : previous.optJSONArray("actions");
        if(prevSteps != null) {
            for (int i=0;i<prevSteps.length();i++) addAction(prevSteps.optJSONObject(i));
        }
        if(actionRows.isEmpty()) addAction(null);
        root.addView(button("＋ Додати дію",()->addAction(null)));

        root.addView(label("ОБМЕЖЕННЯ · усі необов'язкові",18,true));
        root.addView(label("Якщо обмеження не заповнене — воно не перевіряється. "
            +"Коли заповнено декілька, усі вони мають бути істинними (AND).",13,false));
        timeFrom = field(root,"Час від (HH:mm)","09:00",c.optString("timeFrom"));
        timeTo = field(root,"Час до (HH:mm)","21:00",c.optString("timeTo"));
        date = field(root,"Точна дата (РРРР-ММ-ДД)","2026-11-11",c.optString("date"));
        days = field(root,"Дні тижня (1=Пн … 7=Нд, через кому)","1,2,3,4,5",
            c.optString("days"));
        battery = field(root,"Мінімальний заряд, % (0–100)","30",
            c.has("minBattery") ? Integer.toString(c.optInt("minBattery")) : "");
        charging = spinner(root,"Стан зарядки",
            new String[]{"Неважливо","Заряджається","Не заряджається"},
            "yes".equals(c.optString("charging")) ? 1 :
                "no".equals(c.optString("charging")) ? 2 : 0);
        ssid = field(root,"Підключений Wi-Fi (точний SSID)","HomeWiFi",c.optString("ssid"));
        root.addView(label("Геозона: використовує останню геопозицію до 10 хв. "
            +"Без дозволу геолокації макрос не виконається.",13,false));
        latitude = field(root,"Широта","49.0",c.has("lat") ?
            Double.toString(c.optDouble("lat")) : "");
        longitude = field(root,"Довгота","23.0",c.has("lon") ?
            Double.toString(c.optDouble("lon")) : "");
        radius = field(root,"Радіус геозони в метрах","200",
            c.has("radius") ? Double.toString(c.optDouble("radius")) : "");
        root.addView(button("📍 Взяти мої поточні координати", this::useLastLocation));
        root.addView(label("Root доступний тільки для вручну схвалених shell-дій. "
            +"Команди можуть змінити систему або видалити дані.",13,false));
        root.addView(button("💾 Зберегти макрос", this::confirmSave));
    }

    private void useLastLocation() {
        if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION},207);
            return;
        }
        LocationManager manager =
            (LocationManager)getSystemService(Context.LOCATION_SERVICE);
        Location best = null;
        try {
            if(manager != null) for(String provider : manager.getProviders(true)) {
                Location candidate = manager.getLastKnownLocation(provider);
                if(candidate != null && (best == null || candidate.getTime() > best.getTime()))
                    best = candidate;
            }
        } catch (SecurityException ignored) { }
        if(best == null) {
            Toast.makeText(this,"Немає відомої геопозиції — увімкни GPS.",
                Toast.LENGTH_LONG).show(); return;
        }
        latitude.setText(Double.toString(best.getLatitude()));
        longitude.setText(Double.toString(best.getLongitude()));
        if(radius.getText().toString().trim().isEmpty()) radius.setText("200");
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions,
                                                       int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if(request == 207 && checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) useLastLocation();
    }

    private String value(EditText field) { return field.getText().toString().trim(); }
    private void optional(JSONObject object, String key, EditText field) throws Exception {
        String v = value(field);
        if (!v.isEmpty()) object.put(key,v);
    }
    private void confirmSave() {
        for(ActionRow step:actionRows) {
            if("root".equals(ACTION_KEYS[step.type.getSelectedItemPosition()])) {
                if(!step.approved.isChecked()) {
                    Toast.makeText(this,"Root-дія потребує окремого дозволу.",
                        Toast.LENGTH_LONG).show();
                    return;
                }
                new AlertDialog.Builder(this).setTitle("Підтвердити root-доступ?")
                    .setMessage("Збережена shell-команда запускатиметься з правами root "
                        +"при виконанні макросу. Не додавай неперевірені команди.")
                    .setNegativeButton("Скасувати",null)
                    .setPositiveButton("Підтверджую", (d,w)->save())
                    .show();
                return;
            }
        }
        save();
    }
    private void save() {
        try {
            if(value(name).isEmpty() || actionRows.isEmpty())
                throw new IllegalArgumentException("Вкажи назву і хоча б одну дію.");
            String chosen = TRIGGER_KEYS[trigger.getSelectedItemPosition()];
            String triggerText = value(triggerValue);
            if("voice".equals(chosen) && triggerText.isEmpty())
                throw new IllegalArgumentException("Вкажи голосову команду.");
            if("time".equals(chosen) && !JarvisMacros.validTime(triggerText))
                throw new IllegalArgumentException("Час тригера має формат HH:mm.");
            JSONObject constraints = new JSONObject();
            if(!value(timeFrom).isEmpty() || !value(timeTo).isEmpty()) {
                if(!JarvisMacros.validTime(value(timeFrom)) ||
                    !JarvisMacros.validTime(value(timeTo)))
                    throw new IllegalArgumentException("Вкажи обидві межі часу HH:mm.");
                optional(constraints,"timeFrom",timeFrom);
                optional(constraints,"timeTo",timeTo);
            }
            if(!value(date).isEmpty() && !value(date).matches("\\d{4}-\\d{2}-\\d{2}"))
                throw new IllegalArgumentException("Дата: РРРР-ММ-ДД.");
            optional(constraints,"date",date);
            if(!value(days).isEmpty() && !value(days).matches("[1-7](,[1-7])*"))
                throw new IllegalArgumentException("Дні: 1,2,3,4,5,6,7.");
            optional(constraints,"days",days);
            if(!value(battery).isEmpty()) {
                int min = Integer.parseInt(value(battery));
                if(min < 0 || min > 100)
                    throw new IllegalArgumentException("Заряд: від 0 до 100%.");
                constraints.put("minBattery",min);
            }
            constraints.put("charging",charging.getSelectedItemPosition() == 1 ? "yes" :
                charging.getSelectedItemPosition() == 2 ? "no" : "any");
            optional(constraints,"ssid",ssid);
            if(!value(latitude).isEmpty() || !value(longitude).isEmpty()
                || !value(radius).isEmpty()) {
                double lat = Double.parseDouble(value(latitude));
                double lon = Double.parseDouble(value(longitude));
                double m = Double.parseDouble(value(radius));
                if(lat < -90 || lat > 90 || lon < -180 || lon > 180 ||
                    m < 30 || m > 100000)
                    throw new IllegalArgumentException("Невірна геозона (30–100000 м).");
                constraints.put("lat",lat);
                constraints.put("lon",lon);
                constraints.put("radius",m);
            }
            JSONArray actions = new JSONArray();
            for(ActionRow row:actionRows) {
                String action = ACTION_KEYS[row.type.getSelectedItemPosition()];
                String actionValue = value(row.value);
                if(actionValue.isEmpty())
                    throw new IllegalArgumentException("Заповни параметри кожної дії.");
                if("wait".equals(action) || "vibrate".equals(action)) {
                    int number = Integer.parseInt(actionValue);
                    int max = "wait".equals(action) ? 60000 : 5000;
                    if(number < 0 || number > max)
                        throw new IllegalArgumentException("Недопустима тривалість дії.");
                }
                JSONObject step = new JSONObject();
                step.put("type",action);
                step.put("value",actionValue);
                step.put("rootApproved",
                    "root".equals(action) && row.approved.isChecked());
                actions.put(step);
            }
            JSONObject macro = new JSONObject();
            macro.put("id",editing == null ?
                UUID.randomUUID().toString() : editing.optString("id"));
            macro.put("enabled",editing == null || editing.optBoolean("enabled",true));
            macro.put("name",value(name));
            macro.put("trigger",chosen);
            macro.put("triggerValue",triggerText);
            macro.put("actions",actions);
            macro.put("constraints",constraints);
            JarvisMacros.save(this,macro);
            Toast.makeText(this,"Макрос збережено",Toast.LENGTH_SHORT).show();
            showList();
        } catch (Exception error) {
            Toast.makeText(this,"Не збережено: " + error.getMessage(),
                Toast.LENGTH_LONG).show();
        }
    }
    @Override public void onBackPressed() {
        if(name != null) showList();
        else super.onBackPressed();
    }
}
