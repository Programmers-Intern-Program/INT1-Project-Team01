package back.standard.util;

import tools.jackson.databind.ObjectMapper;

public final class Ut {
    private Ut() {
    }

    public static final class Json {
        private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

        private Json() {
        }

        public static String toString(Object object) {
            return toString(object, null);
        }

        public static String toString(Object object, String defaultValue) {
            try {
                return OBJECT_MAPPER.writeValueAsString(object);
            } catch (Exception e) {
                return defaultValue;
            }
        }
    }
}
