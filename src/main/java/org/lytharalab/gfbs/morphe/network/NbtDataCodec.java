package org.lytharalab.gfbs.morphe.network;

import net.minecraft.nbt.ByteTag;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.LongTag;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class NbtDataCodec {
    private static final int MAX_DEPTH = 16;
    private static final int MAX_ENTRIES = 256;

    private NbtDataCodec() {
    }

    public static Map<String, Object> toMap(CompoundTag tag) {
        return compoundToMap(tag, 0);
    }

    public static CompoundTag fromMap(Map<String, ?> values) {
        return mapToCompound(values, 0);
    }

    private static Map<String, Object> compoundToMap(CompoundTag tag, int depth) {
        requireDepth(depth);
        if (tag.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("NBT compound exceeds " + MAX_ENTRIES + " entries");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : tag.getAllKeys()) {
            Tag value = tag.get(key);
            if (value != null) {
                result.put(key, fromTag(value, depth + 1));
            }
        }
        return result;
    }

    private static Object fromTag(Tag tag, int depth) {
        requireDepth(depth);
        if (tag instanceof CompoundTag compound) {
            return compoundToMap(compound, depth);
        }
        if (tag instanceof ListTag list) {
            if (list.size() > MAX_ENTRIES) {
                throw new IllegalArgumentException("NBT list exceeds " + MAX_ENTRIES + " entries");
            }
            List<Object> result = new ArrayList<>(list.size());
            for (Tag value : list) {
                result.add(fromTag(value, depth + 1));
            }
            return result;
        }
        if (tag instanceof ByteTag byteTag) {
            byte value = byteTag.getAsByte();
            return value == 0 || value == 1 ? value == 1 : value;
        }
        if (tag instanceof IntTag intTag) {
            return intTag.getAsInt();
        }
        if (tag instanceof LongTag longTag) {
            return longTag.getAsLong();
        }
        if (tag instanceof NumericTag numeric) {
            return numeric.getAsDouble();
        }
        if (tag instanceof StringTag string) {
            return string.getAsString();
        }
        return tag.getAsString();
    }

    private static CompoundTag mapToCompound(Map<String, ?> values, int depth) {
        requireDepth(depth);
        if (values.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("Payload exceeds " + MAX_ENTRIES + " entries");
        }
        CompoundTag result = new CompoundTag();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank() || key.length() > 128) {
                throw new IllegalArgumentException("Payload keys must contain 1-128 characters");
            }
            Tag value = toTag(entry.getValue(), depth + 1);
            if (value != null) {
                result.put(key, value);
            }
        }
        return result;
    }

    private static Tag toTag(Object value, int depth) {
        requireDepth(depth);
        if (value == null) {
            return null;
        }
        if (value instanceof Boolean bool) {
            return ByteTag.valueOf(bool);
        }
        if (value instanceof Byte || value instanceof Short || value instanceof Integer) {
            return IntTag.valueOf(((Number) value).intValue());
        }
        if (value instanceof Long number) {
            return LongTag.valueOf(number);
        }
        if (value instanceof Number number) {
            return DoubleTag.valueOf(number.doubleValue());
        }
        if (value instanceof CharSequence text) {
            String string = text.toString();
            if (string.length() > 8192) {
                throw new IllegalArgumentException("Payload string exceeds 8192 characters");
            }
            return StringTag.valueOf(string);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> converted = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                converted.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return mapToCompound(converted, depth);
        }
        if (value instanceof Iterable<?> iterable) {
            ListTag list = new ListTag();
            int count = 0;
            for (Object item : iterable) {
                if (++count > MAX_ENTRIES) {
                    throw new IllegalArgumentException("Payload list exceeds " + MAX_ENTRIES + " entries");
                }
                Tag encoded = toTag(item, depth + 1);
                if (encoded != null && (list.isEmpty() || encoded.getId() == list.getElementType())) {
                    list.add(encoded);
                } else if (encoded != null) {
                    throw new IllegalArgumentException("NBT arrays must contain one value type");
                }
            }
            return list;
        }
        return StringTag.valueOf(value.toString());
    }

    private static void requireDepth(int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalArgumentException("Payload nesting exceeds " + MAX_DEPTH + " levels");
        }
    }
}
