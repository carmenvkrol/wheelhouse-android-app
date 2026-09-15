package mockserver;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Deliberately minimal JSON writer/reader — no external dependency, so the mock server
 * runs with nothing but a JDK. Writing handles every shape this API needs (nested
 * objects/arrays, strings, numbers, booleans, null). Parsing only needs to handle the
 * flat {"key":"value"} request bodies this API's POSTs actually send, so that's all it
 * does — it is not a general JSON parser.
 */
final class Json {
    private Json() {}

    static Map<String, Object> obj(Object... kv) {
        if (kv.length % 2 != 0) throw new IllegalArgumentException("obj() needs an even number of arguments");
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            map.put((String) kv[i], kv[i + 1]);
        }
        return map;
    }

    static List<Object> arr(Object... items) {
        return Arrays.asList(items);
    }

    static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(value, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(Object value, StringBuilder sb) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String) {
            writeString((String) value, sb);
        } else if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
            sb.append(value.toString());
        } else if (value instanceof Double || value instanceof Float) {
            sb.append(((Number) value).doubleValue());
        } else if (value instanceof Map) {
            writeObject((Map<String, Object>) value, sb);
        } else if (value instanceof List) {
            writeArray((List<Object>) value, sb);
        } else {
            throw new IllegalArgumentException("Unsupported JSON value type: " + value.getClass());
        }
    }

    private static void writeObject(Map<String, Object> map, StringBuilder sb) {
        sb.append('{');
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            writeString(e.getKey(), sb);
            sb.append(':');
            writeValue(e.getValue(), sb);
        }
        sb.append('}');
    }

    private static void writeArray(List<Object> list, StringBuilder sb) {
        sb.append('[');
        boolean first = true;
        for (Object v : list) {
            if (!first) sb.append(',');
            first = false;
            writeValue(v, sb);
        }
        sb.append(']');
    }

    private static void writeString(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        sb.append('"');
    }

    /** Parses a flat {"key":"value", "key2": null} object — exactly what this API's POST bodies send. */
    static Map<String, String> parseFlatObject(String body) {
        Map<String, String> result = new LinkedHashMap<>();
        if (body == null) return result;
        int n = body.length();
        int i = skipWhitespace(body, 0);
        if (i >= n || body.charAt(i) != '{') return result;
        i++;
        while (true) {
            i = skipWhitespace(body, i);
            if (i < n && body.charAt(i) == '}') { i++; break; }
            if (i >= n || body.charAt(i) != '"') break;
            int[] cursor = new int[1];
            String key = parseString(body, i, cursor);
            i = skipWhitespace(body, cursor[0]);
            if (i < n && body.charAt(i) == ':') i++;
            i = skipWhitespace(body, i);
            String value;
            if (i < n && body.charAt(i) == '"') {
                value = parseString(body, i, cursor);
                i = cursor[0];
            } else {
                int start = i;
                while (i < n && body.charAt(i) != ',' && body.charAt(i) != '}') i++;
                String raw = body.substring(start, i).trim();
                value = raw.equals("null") ? null : raw;
            }
            result.put(key, value);
            i = skipWhitespace(body, i);
            if (i < n && body.charAt(i) == ',') { i++; continue; }
            if (i < n && body.charAt(i) == '}') { i++; }
            break;
        }
        return result;
    }

    private static int skipWhitespace(String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        return i;
    }

    /** Assumes s.charAt(i) == '"'. Writes the index just past the closing quote to endOut[0]. */
    private static String parseString(String s, int i, int[] endOut) {
        StringBuilder sb = new StringBuilder();
        i++;
        while (i < s.length() && s.charAt(i) != '"') {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(i + 1);
                switch (next) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case '"': sb.append('"'); break;
                    case '\\': sb.append('\\'); break;
                    default: sb.append(next);
                }
                i += 2;
            } else {
                sb.append(c);
                i++;
            }
        }
        endOut[0] = i + 1;
        return sb.toString();
    }
}
