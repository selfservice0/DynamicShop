package org.minecraftsmp.dynamicshop.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.*;
import java.net.URI;
import java.util.*;

/** Public website preferences, stored separately from credentials and shop configuration. */
public final class WebAppearanceStore {
    private final Path file;
    private final ObjectMapper json = new ObjectMapper();
    private Map<String,Object> settings;
    private static final Set<String> DESIGNS=Set.of("classic","market","inventory","nova","chest","dsx");
    private static final Set<String> FONTS=Set.of("modern","rounded","system","mono","clean","geometric","soft","condensed","tech","serif","editorial","pixel","legible");
    private static final Set<String> STYLE=Set.of("accent","mode","background","panel","sidebar","font","radius","density","motion","hero");
    public WebAppearanceStore(Path file) throws IOException {
        this.file=file;
        settings=defaults();
        if(Files.exists(file)) settings=validate(json.readValue(file.toFile(),Map.class));
    }
    public static Map<String,Object> defaults() {
        Map<String,Object> m=new LinkedHashMap<>();
        m.put("title","DynamicShop");m.put("subtitle","YOUR SERVER MARKETPLACE");m.put("design","chest");
        m.put("accent","#e3b21c");m.put("mode","custom");m.put("background","#1d1e22");m.put("panel","#c6c6c6");m.put("sidebar","#16171b");
        m.put("font","legible");m.put("radius",0);m.put("density","compact");m.put("motion",false);m.put("hero",false);
        m.put("homeEnabled",false);m.put("homeLabel","Home");m.put("homeUrl","");m.put("homeNewTab",false);m.put("layoutStyles",Map.of());return m;
    }
    public synchronized Map<String,Object> get(){return new LinkedHashMap<>(settings);}
    public synchronized Map<String,Object> save(Map<String,Object> input) throws IOException {
        Map<String,Object> next=validate(input);
        Files.createDirectories(file.getParent());
        Path temp=Files.createTempFile(file.getParent(),"website-",".tmp");
        try {
            json.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(),next);
            try { Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException e){Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING);}
        } finally {Files.deleteIfExists(temp);}
        settings=next;return get();
    }
    public static Map<String,Object> validate(Map<String,Object> input) {
        if(input==null)throw new IllegalArgumentException("Appearance settings are required");
        Map<String,Object> m=defaults();
        for(String key:m.keySet())if(input.containsKey(key))m.put(key,input.get(key));
        text(m,"title",40,true);text(m,"subtitle",60,false);text(m,"homeLabel",32,true);text(m,"homeUrl",2048,false);
        choice(m,"design",DESIGNS);style(m);
        bool(m,"homeEnabled");bool(m,"homeNewTab");
        String url=(String)m.get("homeUrl");
        if(!url.isEmpty()) {
            try {URI uri=URI.create(url);if(!Set.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||url.contains("\\"))throw new IllegalArgumentException();}
            catch(Exception e){throw new IllegalArgumentException("Home URL must be a full http:// or https:// website address");}
        }
        if(Boolean.TRUE.equals(m.get("homeEnabled"))&&url.isEmpty())throw new IllegalArgumentException("Enter a Home URL before enabling the button");
        if(!(m.get("layoutStyles") instanceof Map<?,?> layouts))throw new IllegalArgumentException("Invalid design preferences");
        Map<String,Object> safeLayouts=new LinkedHashMap<>();
        for(var entry:layouts.entrySet()) {
            if(!(entry.getKey() instanceof String id)||!DESIGNS.contains(id)||!(entry.getValue() instanceof Map<?,?> value))throw new IllegalArgumentException("Invalid design preferences");
            Map<String,Object> v=defaults();for(String key:STYLE)if(value.containsKey(key))v.put(key,value.get(key));style(v);
            Map<String,Object> safe=new LinkedHashMap<>();for(String key:STYLE)safe.put(key,v.get(key));safeLayouts.put(id,safe);
        }
        m.put("layoutStyles",safeLayouts);return m;
    }
    private static void style(Map<String,Object> m) {
        for(String key:List.of("accent","background","panel","sidebar"))if(!(m.get(key) instanceof String s)||!s.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException("Invalid color: "+key);
        choice(m,"mode",Set.of("dark","light","custom"));choice(m,"font",FONTS);choice(m,"density",Set.of("comfortable","compact"));
        if(!(m.get("radius") instanceof Number n)||!Set.of(0d,10d,20d).contains(n.doubleValue()))throw new IllegalArgumentException("Invalid corner style");
        bool(m,"motion");bool(m,"hero");
    }
    private static void text(Map<String,Object> m,String key,int max,boolean required){if(!(m.get(key) instanceof String s)||s.length()>max||(required&&s.isBlank())||s.chars().anyMatch(c->c<32))throw new IllegalArgumentException("Invalid "+key);m.put(key,((String)m.get(key)).trim());}
    private static void bool(Map<String,Object> m,String key){if(!(m.get(key) instanceof Boolean))throw new IllegalArgumentException("Invalid "+key);}
    private static void choice(Map<String,Object> m,String key,Set<String> values){if(!(m.get(key) instanceof String s)||!values.contains(s))throw new IllegalArgumentException("Invalid "+key);}
}
