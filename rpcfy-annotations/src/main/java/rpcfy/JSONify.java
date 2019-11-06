package rpcfy;


import java.lang.reflect.Type;
import java.util.Set;

/**
 * Converts an object to and from JSON.
 * <p/>
 * A default implementation of this is provided by {@link rpcfy.json.GsonJsonify} which uses {@link com.google.gson.Gson}.
 */
public interface JSONify {


    /**
     * Returns a new {@link JObject}
     */
    JObject newJson();

    /**
     * Converts given Object to {@link JElement}
     */
    JElement toJson(Object object);

    /**
     * Converts given JSON string to {@link JElement}
     */
    JElement fromJson(String json);

    /**
     * Convert given json message to object of given type
     */
    <T> T fromJSON(String json, Class<T> type);

    /**
     * Convert given parameter from json message to object of given type
     */
    <T> T fromJSON(String json, String parameter, Class<T> type);

    /**
     * Convert given parameter from json message to object of given type
     */
    <T> T fromJSON(String json, String parameter, Type type);

    /**
     * Returns the json element representing the given parameter from given json
     */
    String getJSONElement(String json, String parameter);


    /**
     * Represents a JSON element
     */
    interface JElement {

        /**
         * Returns the JSON representation
         */
        String toJson();

        /**
         * Returns the list of parameters in this element
         */
        Set<String> getKeys();

        /**
         * Returns the value of parameter in json in this element if any
         */
        String getJsonValue(String parameter);

    }


    /**
     * Represents a JSON object
     */
    interface JObject extends JElement {

        /**
         * Adds the given json string as value of given parameter
         */
        void putJson(String name, String value);

        /**
         * Adds a name value parameter
         */
        void put(String name, String value);

        /**
         * Adds a name value parameter
         */
        void put(String name, int value);

        /**
         * Adds a name value parameter
         */
        void put(String name, JElement value);

    }
}
