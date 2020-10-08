package rpcfy.json;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import rpcfy.JSONify;

import java.lang.reflect.Type;
import java.util.Set;

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
            private JsonObject jsonObject;

            @Override
            public String toJson() {
                return gson.toJson(object);
            }

            @Override
            public Set<String> getKeys() {
                if (jsonObject == null) {
                    JsonParser jsonParser = new JsonParser();
                    JsonElement jsonElement = jsonParser.parse(toJson());
                    if (jsonElement instanceof JsonObject) {
                        jsonObject = (JsonObject) jsonElement;
                        return jsonObject.keySet();
                    }
                } else {
                    return jsonObject.keySet();
                }
                return null;
            }

            @Override
            public String getJsonValue(String parameter) {
                if (jsonObject == null) {
                    JsonParser jsonParser = new JsonParser();
                    JsonElement jsonElement = jsonParser.parse(toJson());
                    if (jsonElement instanceof JsonObject) {
                        jsonObject = (JsonObject) jsonElement;
                        return jsonObject.get(parameter).toString();
                    }
                } else {
                    return jsonObject.get(parameter).toString();
                }
                return null;
            }

            @Override
            public String getStringValue(String parameter) {
                if (jsonObject == null) {
                    JsonParser jsonParser = new JsonParser();
                    JsonElement jsonElement = jsonParser.parse(toJson());
                    if (jsonElement instanceof JsonObject) {
                        jsonObject = (JsonObject) jsonElement;
                    }
                }

                try {
                    return jsonObject.get(parameter).getAsString();
                } catch (Exception ex) {
                    return getJsonValue(parameter);
                }
            }
        };
    }

    @Override
    public JElement fromJson(String json) {
        return new GsonObject(json);
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
        JsonElement element = jsonParser.parse(json).getAsJsonObject()
                .get(parameter);
        return element != null ? element.toString() : null;
    }
}
