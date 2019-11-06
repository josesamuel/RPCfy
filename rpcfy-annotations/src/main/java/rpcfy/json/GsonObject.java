package rpcfy.json;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import rpcfy.JSONify;

import java.util.Set;

/**
 * Default implementation of {@link rpcfy.JSONify.JObject} using {@link Gson}
 */

public class GsonObject implements JSONify.JObject {

    private JsonObject jsonObject = new JsonObject();
    private JsonParser jsonParser = new JsonParser();

    /**
     * Creates an empty instance
     */
    public GsonObject() {
    }

    /**
     * Creates an instance from given json
     */
    public GsonObject(String json) {
        JsonElement jsonElement = jsonParser.parse(json);
        if (jsonElement instanceof JsonObject) {
            jsonObject = (JsonObject) jsonElement;
        }
    }

    @Override
    public String toJson() {
        return jsonObject.toString();
    }

    @Override
    public String toString() {
        return toJson();
    }

    @Override
    public Set<String> getKeys() {
        return jsonObject.keySet();
    }

    @Override
    public String getJsonValue(String parameter) {
        return jsonObject.get(parameter).toString();
    }

    @Override
    public void putJson(String name, String value) {
        jsonObject.add(name, jsonParser.parse(value));
    }

    @Override
    public void put(String name, String value) {
        jsonObject.addProperty(name, value);
    }

    @Override
    public void put(String name, int value) {
        jsonObject.addProperty(name, value);
    }

    @Override
    public void put(String name, JSONify.JElement value) {
        jsonObject.add(name, jsonParser.parse(value.toJson()));
    }
}
