package com.ironpulse.data;
import com.google.gson.*;
import java.lang.reflect.Type;
import java.time.LocalDate;
public class LocalDateAdapter implements JsonSerializer<LocalDate>, JsonDeserializer<LocalDate> {
    public JsonElement serialize(LocalDate src, Type t, JsonSerializationContext ctx) { return new JsonPrimitive(src.toString()); }
    public LocalDate deserialize(JsonElement json, Type t, JsonDeserializationContext ctx) { return LocalDate.parse(json.getAsString()); }
}
