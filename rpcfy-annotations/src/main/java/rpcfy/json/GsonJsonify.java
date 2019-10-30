package rpcfy.json;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.lang.reflect.Type;

import rpcfy.JSONify;

/**
 * Default implementation of {@link rpcfy.JSONify} using {@link Gson}
 */
public class GsonJsonify implements JSONify {

    private Gson gson = new Gson();
    private JsonParser jsonParser = new JsonParser();

    @Override
    public JObject newJson() {
        return new GsonObject();
    }

    @Override
    public JElement toJson(final Object object) {
        return new JElement() {
            @Override
            public String toJson() {
                return gson.toJson(object);
            }
        };
    }

    @Override
    public <T> T fromJSON(String json, Class<T> type) {
        return gson.fromJson(json, type);
    }

    @Override
    public <T> T fromJSON(String json, String parameter, Class<T> type) {
        String jsonParam = getJSONElement(json, parameter);
        return jsonParam != null ? gson.fromJson(jsonParam, type) : null;
    }

    @Override
    public <T> T fromJSON(String json, String parameter, Type type) {
        String jsonParam = getJSONElement(json, parameter);
        return jsonParam != null ? gson.fromJson(jsonParam, type) : null;
    }


    @Override
    public String getJSONElement(String json, String parameter) {
        JsonElement element =jsonParser.parse(json).getAsJsonObject()
                .get(parameter);
        return element != null ? element.toString() : null;
    }
}
