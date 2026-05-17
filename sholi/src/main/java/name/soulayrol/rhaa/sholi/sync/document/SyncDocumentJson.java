package name.soulayrol.rhaa.sholi.sync.document;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SyncDocumentJson {

    private SyncDocumentJson() {
    }

    public static String serialize(SyncDocument document) {
        if (document == null) {
            throw new IllegalArgumentException("document must not be null");
        }
        List<SyncItem> sortedItems = new ArrayList<SyncItem>(document.getItems());
        Collections.sort(sortedItems, new Comparator<SyncItem>() {
            @Override
            public int compare(SyncItem left, SyncItem right) {
                return left.getSyncId().compareTo(right.getSyncId());
            }
        });

        StringBuilder builder = new StringBuilder();
        builder.append('{');
        builder.append("\"schema_version\":").append(SyncDocument.SCHEMA_VERSION).append(',');
        builder.append("\"items\":[");
        for (int i = 0; i < sortedItems.size(); ++i) {
            if (i > 0) {
                builder.append(',');
            }
            appendItem(builder, sortedItems.get(i));
        }
        builder.append("]}");
        return builder.toString();
    }

    public static SyncDocument parse(String json) throws SyncDocumentParseException {
        Object root = new Parser(json).parse();
        Map<String, Object> rootObject = requireObject(root, "$", SyncDocumentParseException.Reason.MALFORMED_JSON);
        long schemaVersion = requireLong(rootObject, "schema_version", "$.schema_version");
        if (schemaVersion > SyncDocument.SCHEMA_VERSION) {
            throw new SyncDocumentParseException(
                    SyncDocumentParseException.Reason.UNSUPPORTED_SCHEMA_VERSION,
                    "$.schema_version",
                    schemaVersion > Integer.MAX_VALUE
                            ? null
                            : Integer.valueOf((int) schemaVersion));
        }
        if (schemaVersion != SyncDocument.SCHEMA_VERSION) {
            throw new SyncDocumentParseException(
                    SyncDocumentParseException.Reason.INVALID_FIELD_TYPE,
                    "$.schema_version");
        }

        List<Object> itemValues = requireArray(rootObject, "items", "$.items");
        List<SyncItem> items = new ArrayList<SyncItem>(itemValues.size());
        Set<String> syncIds = new HashSet<String>();
        for (int i = 0; i < itemValues.size(); ++i) {
            SyncItem item = parseItem(itemValues.get(i), i);
            if (!syncIds.add(item.getSyncId())) {
                throw new SyncDocumentParseException(
                        SyncDocumentParseException.Reason.DUPLICATE_SYNC_ID,
                        "$.items[" + i + "].sync_id");
            }
            items.add(item);
        }
        return new SyncDocument(items);
    }

    private static void appendItem(StringBuilder builder, SyncItem item) {
        builder.append('{');
        builder.append("\"sync_id\":\"").append(escape(item.getSyncId())).append("\",");
        builder.append("\"name\":\"").append(escape(item.getName())).append("\",");
        builder.append("\"status\":").append(item.getStatus()).append(',');
        builder.append("\"deleted\":").append(item.isDeleted()).append(',');
        builder.append("\"modified_at\":").append(item.getModifiedAt()).append(',');
        builder.append("\"modified_by\":{");
        builder.append("\"name\":\"").append(escape(item.getModifiedBy().getName())).append('"');
        if (item.getModifiedBy().getClientId() != null) {
            builder.append(',');
            builder.append("\"client_id\":\"").append(escape(item.getModifiedBy().getClientId())).append('"');
        }
        builder.append("}}");
    }

    private static SyncItem parseItem(Object itemValue, int index) throws SyncDocumentParseException {
        String itemPath = "$.items[" + index + "]";
        Map<String, Object> itemObject = requireObject(
                itemValue, itemPath, SyncDocumentParseException.Reason.INVALID_FIELD_TYPE);
        String syncId = requireString(itemObject, "sync_id", itemPath + ".sync_id", true);
        String name = requireString(itemObject, "name", itemPath + ".name", true);
        int status = requireStatus(itemObject, "status", itemPath + ".status");
        boolean deleted = requireBoolean(itemObject, "deleted", itemPath + ".deleted");
        long modifiedAt = requireLong(itemObject, "modified_at", itemPath + ".modified_at");
        if (modifiedAt < 0L) {
            throw new SyncDocumentParseException(
                    SyncDocumentParseException.Reason.INVALID_FIELD_VALUE,
                    itemPath + ".modified_at");
        }
        Map<String, Object> modifiedByObject = requireObject(
                require(itemObject, "modified_by", itemPath + ".modified_by"),
                itemPath + ".modified_by",
                SyncDocumentParseException.Reason.INVALID_FIELD_TYPE);
        String modifiedByName = requireString(
                modifiedByObject, "name", itemPath + ".modified_by.name", true);
        String clientId = optionalString(
                modifiedByObject, "client_id", itemPath + ".modified_by.client_id");
        return new SyncItem(
                syncId,
                name,
                status,
                deleted,
                modifiedAt,
                new ModifiedBy(modifiedByName, clientId));
    }

    private static Object require(Map<String, Object> object, String field, String path)
            throws SyncDocumentParseException {
        if (!object.containsKey(field)) {
            throw new SyncDocumentParseException(
                    SyncDocumentParseException.Reason.MISSING_REQUIRED_FIELD,
                    path);
        }
        return object.get(field);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> requireObject(
            Object value, String path, SyncDocumentParseException.Reason reason)
            throws SyncDocumentParseException {
        if (!(value instanceof Map)) {
            throw new SyncDocumentParseException(reason, path);
        }
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> requireArray(Map<String, Object> object, String field, String path)
            throws SyncDocumentParseException {
        Object value = require(object, field, path);
        if (!(value instanceof List)) {
            throw new SyncDocumentParseException(
                    SyncDocumentParseException.Reason.INVALID_FIELD_TYPE,
                    path);
        }
        return (List<Object>) value;
    }

    private static String requireString(
            Map<String, Object> object, String field, String path, boolean nonEmpty)
            throws SyncDocumentParseException {
        Object value = require(object, field, path);
        if (!(value instanceof String) || (nonEmpty && ((String) value).length() == 0)) {
            throw new SyncDocumentParseException(
                    SyncDocumentParseException.Reason.INVALID_FIELD_TYPE,
                    path);
        }
        return (String) value;
    }

    private static String optionalString(Map<String, Object> object, String field, String path)
            throws SyncDocumentParseException {
        if (!object.containsKey(field)) {
            return null;
        }
        Object value = object.get(field);
        if (!(value instanceof String) || ((String) value).length() == 0) {
            throw new SyncDocumentParseException(
                    SyncDocumentParseException.Reason.INVALID_FIELD_TYPE,
                    path);
        }
        return (String) value;
    }

    private static long requireLong(Map<String, Object> object, String field, String path)
            throws SyncDocumentParseException {
        Object value = require(object, field, path);
        if (!(value instanceof Long)) {
            throw new SyncDocumentParseException(
                    SyncDocumentParseException.Reason.INVALID_FIELD_TYPE,
                    path);
        }
        return ((Long) value).longValue();
    }

    private static int requireStatus(Map<String, Object> object, String field, String path)
            throws SyncDocumentParseException {
        long value = requireLong(object, field, path);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE
                || !SyncItem.isValidStatus((int) value)) {
            throw new SyncDocumentParseException(
                    SyncDocumentParseException.Reason.INVALID_FIELD_VALUE,
                    path);
        }
        return (int) value;
    }

    private static boolean requireBoolean(Map<String, Object> object, String field, String path)
            throws SyncDocumentParseException {
        Object value = require(object, field, path);
        if (!(value instanceof Boolean)) {
            throw new SyncDocumentParseException(
                    SyncDocumentParseException.Reason.INVALID_FIELD_TYPE,
                    path);
        }
        return ((Boolean) value).booleanValue();
    }

    private static String escape(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 16);
        for (int i = 0; i < value.length(); ++i) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    builder.append("\\\"");
                    break;
                case '\\':
                    builder.append("\\\\");
                    break;
                case '\b':
                    builder.append("\\b");
                    break;
                case '\f':
                    builder.append("\\f");
                    break;
                case '\n':
                    builder.append("\\n");
                    break;
                case '\r':
                    builder.append("\\r");
                    break;
                case '\t':
                    builder.append("\\t");
                    break;
                default:
                    if (c <= 0x1f) {
                        builder.append("\\u");
                        String hex = Integer.toHexString(c);
                        for (int pad = hex.length(); pad < 4; ++pad) {
                            builder.append('0');
                        }
                        builder.append(hex);
                    } else {
                        builder.append(c);
                    }
                    break;
            }
        }
        return builder.toString();
    }

    private static final class Parser {
        private final String json;
        private int position;

        Parser(String json) throws SyncDocumentParseException {
            if (json == null) {
                throw new SyncDocumentParseException(
                        SyncDocumentParseException.Reason.MALFORMED_JSON,
                        "$");
            }
            this.json = json;
        }

        Object parse() throws SyncDocumentParseException {
            skipWhitespace();
            Object value = parseValue();
            skipWhitespace();
            if (position != json.length()) {
                throw malformed();
            }
            return value;
        }

        private Object parseValue() throws SyncDocumentParseException {
            if (position >= json.length()) {
                throw malformed();
            }
            char c = json.charAt(position);
            if (c == '{') {
                return parseObject();
            }
            if (c == '[') {
                return parseArray();
            }
            if (c == '"') {
                return parseString();
            }
            if (c == 't') {
                consumeLiteral("true");
                return Boolean.TRUE;
            }
            if (c == 'f') {
                consumeLiteral("false");
                return Boolean.FALSE;
            }
            if (c == 'n') {
                consumeLiteral("null");
                return null;
            }
            if (c == '-' || (c >= '0' && c <= '9')) {
                return parseNumber();
            }
            throw malformed();
        }

        private Map<String, Object> parseObject() throws SyncDocumentParseException {
            LinkedHashMap<String, Object> object = new LinkedHashMap<String, Object>();
            ++position;
            skipWhitespace();
            if (peek('}')) {
                ++position;
                return object;
            }
            while (true) {
                skipWhitespace();
                if (!peek('"')) {
                    throw malformed();
                }
                String key = parseString();
                skipWhitespace();
                expect(':');
                skipWhitespace();
                if (object.containsKey(key)) {
                    throw malformed();
                }
                object.put(key, parseValue());
                skipWhitespace();
                if (peek('}')) {
                    ++position;
                    return object;
                }
                expect(',');
            }
        }

        private List<Object> parseArray() throws SyncDocumentParseException {
            ArrayList<Object> array = new ArrayList<Object>();
            ++position;
            skipWhitespace();
            if (peek(']')) {
                ++position;
                return array;
            }
            while (true) {
                skipWhitespace();
                array.add(parseValue());
                skipWhitespace();
                if (peek(']')) {
                    ++position;
                    return array;
                }
                expect(',');
            }
        }

        private String parseString() throws SyncDocumentParseException {
            expect('"');
            StringBuilder builder = new StringBuilder();
            while (position < json.length()) {
                char c = json.charAt(position++);
                if (c == '"') {
                    return builder.toString();
                }
                if (c == '\\') {
                    if (position >= json.length()) {
                        throw malformed();
                    }
                    char escaped = json.charAt(position++);
                    switch (escaped) {
                        case '"':
                            builder.append('"');
                            break;
                        case '\\':
                            builder.append('\\');
                            break;
                        case '/':
                            builder.append('/');
                            break;
                        case 'b':
                            builder.append('\b');
                            break;
                        case 'f':
                            builder.append('\f');
                            break;
                        case 'n':
                            builder.append('\n');
                            break;
                        case 'r':
                            builder.append('\r');
                            break;
                        case 't':
                            builder.append('\t');
                            break;
                        case 'u':
                            builder.append(parseUnicodeEscape());
                            break;
                        default:
                            throw malformed();
                    }
                } else {
                    if (c <= 0x1f) {
                        throw malformed();
                    }
                    builder.append(c);
                }
            }
            throw malformed();
        }

        private char parseUnicodeEscape() throws SyncDocumentParseException {
            if (position + 4 > json.length()) {
                throw malformed();
            }
            int value = 0;
            for (int i = 0; i < 4; ++i) {
                char c = json.charAt(position++);
                int hex = Character.digit(c, 16);
                if (hex < 0) {
                    throw malformed();
                }
                value = (value << 4) + hex;
            }
            return (char) value;
        }

        private Number parseNumber() throws SyncDocumentParseException {
            int start = position;
            if (peek('-')) {
                ++position;
            }
            consumeDigits();
            boolean floatingPoint = false;
            if (peek('.')) {
                floatingPoint = true;
                ++position;
                consumeDigits();
            }
            if (peek('e') || peek('E')) {
                floatingPoint = true;
                ++position;
                if (peek('+') || peek('-')) {
                    ++position;
                }
                consumeDigits();
            }
            String number = json.substring(start, position);
            try {
                if (floatingPoint) {
                    return Double.valueOf(number);
                }
                return Long.valueOf(number);
            } catch (NumberFormatException e) {
                throw malformed();
            }
        }

        private void consumeDigits() throws SyncDocumentParseException {
            int start = position;
            while (position < json.length()) {
                char c = json.charAt(position);
                if (c < '0' || c > '9') {
                    break;
                }
                ++position;
            }
            if (start == position) {
                throw malformed();
            }
        }

        private void consumeLiteral(String literal) throws SyncDocumentParseException {
            if (!json.startsWith(literal, position)) {
                throw malformed();
            }
            position += literal.length();
        }

        private void expect(char expected) throws SyncDocumentParseException {
            if (!peek(expected)) {
                throw malformed();
            }
            ++position;
        }

        private boolean peek(char expected) {
            return position < json.length() && json.charAt(position) == expected;
        }

        private void skipWhitespace() {
            while (position < json.length()) {
                char c = json.charAt(position);
                if (c != ' ' && c != '\n' && c != '\r' && c != '\t') {
                    return;
                }
                ++position;
            }
        }

        private SyncDocumentParseException malformed() {
            return new SyncDocumentParseException(
                    SyncDocumentParseException.Reason.MALFORMED_JSON,
                    "$");
        }
    }
}
