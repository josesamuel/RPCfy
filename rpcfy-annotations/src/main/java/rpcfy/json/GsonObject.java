package rpcfy.json;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import rpcfy.JSONify;

/**
 * Default implementation of {@link rpcfy.JSONify.JObject} using {@link Gson}
 */

public class GsonObject implements JSONify.JObject {

    private JsonObject jsonObject = new JsonObject();

    @Override
    public String toJson() {
        return jsonObject.toString();
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
        jsonObject.add(name, JsonParser.parseString(value.toJson()));
    }
}
