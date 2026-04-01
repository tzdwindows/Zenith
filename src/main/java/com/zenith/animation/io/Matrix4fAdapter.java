package com.zenith.animation.io;

import com.google.gson.*;
import org.joml.Matrix4f;
import java.lang.reflect.Type;

public class Matrix4fAdapter implements JsonSerializer<Matrix4f>, JsonDeserializer<Matrix4f> {
    @Override
    public JsonElement serialize(Matrix4f src, Type typeOfSrc, JsonSerializationContext context) {
        float[] floats = new float[16];
        src.get(floats);
        return context.serialize(floats);
    }

    @Override
    public Matrix4f deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        float[] floats = context.deserialize(json, float[].class);
        return new Matrix4f().set(floats);
    }
}