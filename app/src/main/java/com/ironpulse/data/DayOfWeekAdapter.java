package com.ironpulse.data;
import com.google.gson.*;
import java.lang.reflect.Type;
import java.time.DayOfWeek;
public class DayOfWeekAdapter implements JsonSerializer<DayOfWeek>, JsonDeserializer<DayOfWeek> {
    public JsonElement serialize(DayOfWeek src, Type t, JsonSerializationContext ctx) { return new JsonPrimitive(src.name()); }
    public DayOfWeek deserialize(JsonElement json, Type t, JsonDeserializationContext ctx) { return DayOfWeek.valueOf(json.getAsString()); }
}
