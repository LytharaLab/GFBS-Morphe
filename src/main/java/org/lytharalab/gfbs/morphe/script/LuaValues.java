package org.lytharalab.gfbs.morphe.script;

import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.lytharalab.gfbs.morphe.core.UiColor;
import org.lytharalab.gfbs.morphe.core.UiInsets;
import org.lytharalab.gfbs.morphe.core.UiLength;
import org.lytharalab.gfbs.morphe.core.UiRect;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class LuaValues {
    private LuaValues() {
    }

    static Object toJava(LuaValue value) {
        if (value.isnil()) {
            return null;
        }
        if (value.isboolean()) {
            return value.toboolean();
        }
        if (value.isstring()) {
            return value.tojstring();
        }
        if (value.isnumber()) {
            return value.todouble();
        }
        if (value.isuserdata()) {
            return value.touserdata();
        }
        if (value.istable()) {
            return tableToJava(value.checktable(), 0);
        }
        return value.tojstring();
    }

    static LuaValue toLua(Object value) {
        if (value == null) {
            return LuaValue.NIL;
        }
        if (value instanceof LuaValue lua) {
            return lua;
        }
        if (value instanceof Boolean bool) {
            return LuaValue.valueOf(bool);
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            long number = ((Number) value).longValue();
            return number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE
                ? LuaValue.valueOf((int) number)
                : LuaValue.valueOf((double) number);
        }
        if (value instanceof Number number) {
            return LuaValue.valueOf(number.doubleValue());
        }
        if (value instanceof String text) {
            return LuaValue.valueOf(text);
        }
        if (value instanceof UiLength length) {
            return LuaValue.userdataOf(length);
        }
        if (value instanceof UiColor color) {
            return LuaValue.userdataOf(color);
        }
        if (value instanceof UiInsets insets) {
            LuaTable table = new LuaTable();
            table.set("top", insets.top());
            table.set("right", insets.right());
            table.set("bottom", insets.bottom());
            table.set("left", insets.left());
            return table;
        }
        if (value instanceof UiRect rect) {
            LuaTable table = new LuaTable();
            table.set("x", rect.x());
            table.set("y", rect.y());
            table.set("width", rect.width());
            table.set("height", rect.height());
            return table;
        }
        if (value instanceof Map<?, ?> map) {
            LuaTable table = new LuaTable();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                table.set(String.valueOf(entry.getKey()), toLua(entry.getValue()));
            }
            return table;
        }
        if (value instanceof Iterable<?> iterable) {
            LuaTable table = new LuaTable();
            int index = 1;
            for (Object item : iterable) {
                table.set(index++, toLua(item));
            }
            return table;
        }
        if (value instanceof Enum<?> enumeration) {
            return LuaValue.valueOf(enumeration.name().toLowerCase());
        }
        return LuaValue.valueOf(value.toString());
    }

    static Map<String, Object> payload(LuaValue value) {
        if (value.isnil()) {
            return Map.of();
        }
        if (!value.istable()) {
            return Map.of("value", toJava(value));
        }
        Object converted = tableToJava(value.checktable(), 0);
        if (converted instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return result;
        }
        return Map.of("value", converted);
    }

    private static Object tableToJava(LuaTable table, int depth) {
        if (depth >= 16) {
            throw new IllegalArgumentException("Lua value nesting exceeds 16 levels");
        }
        int length = table.length();
        boolean array = length > 0;
        int entries = 0;
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = table.next(key);
            key = next.arg1();
            if (key.isnil()) {
                break;
            }
            entries++;
            if (entries > 256) {
                throw new IllegalArgumentException("Lua table exceeds 256 entries");
            }
            if (!key.isint() || key.toint() < 1 || key.toint() > length) {
                array = false;
            }
        }

        if (array) {
            List<Object> result = new ArrayList<>(length);
            for (int i = 1; i <= length; i++) {
                LuaValue value = table.get(i);
                result.add(value.istable() ? tableToJava(value.checktable(), depth + 1) : toJava(value));
            }
            return result;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        key = LuaValue.NIL;
        while (true) {
            Varargs next = table.next(key);
            key = next.arg1();
            if (key.isnil()) {
                break;
            }
            LuaValue value = next.arg(2);
            result.put(
                key.tojstring(),
                value.istable() ? tableToJava(value.checktable(), depth + 1) : toJava(value)
            );
        }
        return result;
    }
}
