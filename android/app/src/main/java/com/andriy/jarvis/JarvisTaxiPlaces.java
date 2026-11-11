package com.andriy.jarvis;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** User-editable private on-device aliases for Bolt destinations. */
public final class JarvisTaxiPlaces {
    private static final String PREFS="jarvis_taxi_places";
    private static final String KEY="saved_destinations_v1";
    public static final class Place {
        public final String label,address;
        Place(String label,String address){this.label=label;this.address=address;}
    }
    private JarvisTaxiPlaces(){}
    private static SharedPreferences prefs(Context c){
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
    private static void put(JSONArray a,String name,String address){
        JSONObject entry=new JSONObject();
        try{entry.put("name",name);entry.put("address",address);a.put(entry);}
        catch(Exception ignored){}
    }
    private static synchronized void ensureDefaults(Context c){
        if(prefs(c).contains(KEY))return;
        JSONArray a=new JSONArray();
        put(a,"Стебник дім","Стебник, вулиця Михайла Грушевського, 14А, Львівська область, Україна");
        put(a,"Борислав дім","Борислав, вулиця Героїв ОУН-УПА, 21, Львівська область, Україна");
        put(a,"Робота","Apartel Skhidnytsya Wellness Resort, Східниця, Львівська область, Україна");
        prefs(c).edit().putString(KEY,a.toString()).commit();
    }
    public static synchronized List<Place> all(Context c){
        ensureDefaults(c);
        ArrayList<Place> out=new ArrayList<>();
        try{
            JSONArray a=new JSONArray(prefs(c).getString(KEY,"[]"));
            for(int i=0;i<a.length();i++){
                JSONObject o=a.optJSONObject(i);
                if(o==null)continue;
                String name=o.optString("name","").trim();
                String address=o.optString("address","").trim();
                if(!name.isEmpty()&&!address.isEmpty())out.add(new Place(name,address));
            }
        }catch(Exception ignored){}
        return out;
    }
    private static String normal(String input){
        return input.toLowerCase(new Locale("uk","UA")).trim()
            .replaceAll("[\\s.,]+"," ").trim();
    }
    public static String resolve(Context c,String request){
        if(request==null)return null;
        String n=normal(request);
        if(n.equals("роботу")||n.equals("роботи"))n="робота";
        for(Place p:all(c))
            if(normal(p.label).equals(n))return p.address;
        return request.trim();
    }
    /** Null means saved; nonnull means a validation or storage problem. */
    public static synchronized String save(Context c,String oldName,String name,String address){
        name=name==null?"":name.trim();
        address=address==null?"":address.trim();
        if(name.isEmpty()||address.isEmpty())return "Вкажи назву і адресу.";
        if(name.length()>70||address.length()>250)return "Назва до 70 символів, адреса до 250.";
        if(name.contains("\n")||address.contains("\n"))return "Прибери переноси рядків.";
        List<Place> items=all(c);
        boolean existing=false;
        for(Place p:items){
            if(normal(p.label).equals(normal(name)) &&
                (oldName==null||!normal(p.label).equals(normal(oldName))))
                return "Така голосова назва вже існує.";
            if(oldName!=null&&normal(p.label).equals(normal(oldName)))existing=true;
        }
        if(oldName!=null&&!existing)return "Адреса була змінена. Онови список.";
        if(oldName==null&&items.size()>=50)return "Максимум 50 адрес.";
        JSONArray a=new JSONArray();
        for(Place p:items){
            if(oldName!=null&&normal(p.label).equals(normal(oldName)))put(a,name,address);
            else put(a,p.label,p.address);
        }
        if(oldName==null)put(a,name,address);
        return prefs(c).edit().putString(KEY,a.toString()).commit()
            ?null:"Не вдалося зберегти адресу.";
    }
    public static synchronized boolean remove(Context c,String name){
        JSONArray a=new JSONArray();
        boolean removed=false;
        for(Place p:all(c)){
            if(normal(p.label).equals(normal(name)))removed=true;
            else put(a,p.label,p.address);
        }
        return removed&&prefs(c).edit().putString(KEY,a.toString()).commit();
    }
}
