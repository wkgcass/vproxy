/*
 * The MIT License
 *
 * Copyright 2019 wkgcass (https://github.com/wkgcass)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package vjson;

import vjson.deserializer.DeserializeParserListener;
import vjson.deserializer.rule.Rule;
import vjson.parser.ParserMode;
import vjson.parser.ParserOptions;
import vjson.parser.ParserUtils;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.NoSuchElementException;

public class JSON {
    public static Instance parse(java.lang.String json) throws RuntimeException {
        return parse(CharStream.from(json));
    }

    public static Instance parse(CharStream cs) throws RuntimeException {
        return ParserUtils.buildFrom(cs);
    }

    public static <T> T deserialize(java.lang.String json, Rule<T> rule) throws RuntimeException {
        return deserialize(CharStream.from(json), rule);
    }

    public static <T> T deserialize(CharStream cs, Rule<T> rule) throws RuntimeException {
        DeserializeParserListener<T> listener = new DeserializeParserListener<>(rule);
        ParserUtils.buildFrom(cs, new ParserOptions().setListener(listener)
            .setMode(ParserMode.JAVA_OBJECT)
            .setNullArraysAndObjects(true));
        return listener.get();
    }

    public static java.lang.Object parseToJavaObject(java.lang.String json) throws RuntimeException {
        return parseToJavaObject(CharStream.from(json));
    }

    public static java.lang.Object parseToJavaObject(CharStream cs) throws RuntimeException {
        return ParserUtils.buildJavaObject(cs);
    }

    private JSON() {
    }

    public interface Instance<T> extends Serializable {
        T toJavaObject();

        java.lang.String stringify();

        java.lang.String pretty();

        void stringify(StringBuilder builder, Stringifier sfr);
    }

    public interface Object extends Instance<LinkedHashMap<java.lang.String, java.lang.Object>> {
        LinkedHashSet<java.lang.String> keySet();

        List<java.lang.String> keyList();

        int size();

        boolean containsKey(java.lang.String key);

        Instance get(java.lang.String key) throws NullPointerException, NoSuchElementException;

        List<Instance> getAll(java.lang.String key) throws NullPointerException, NoSuchElementException;

        default boolean getBool(java.lang.String key) {
            return ((JSON.Bool) get(key)).booleanValue();
        }

        default int getInt(java.lang.String key) {
            Instance inst = get(key);
            if (inst instanceof Integer) {
                return ((Integer) inst).intValue();
            } else if (inst instanceof Double) {
                return (int) ((Double) inst).doubleValue();
            } else if (inst instanceof Long) {
                return (int) ((Long) inst).longValue();
            } else if (inst instanceof Number) {
                return ((Number) inst).toJavaObject().intValue();
            } else
                throw new ClassCastException(inst.getClass() + " cannot be cast to " + Number.class);
        }

        default double getDouble(java.lang.String key) {
            Instance inst = get(key);
            if (inst instanceof Integer) {
                return ((Integer) inst).intValue();
            } else if (inst instanceof Double) {
                return ((Double) inst).doubleValue();
            } else if (inst instanceof Long) {
                return ((Long) inst).longValue();
            } else if (inst instanceof Number) {
                return ((Number) inst).toJavaObject().doubleValue();
            } else
                throw new ClassCastException(inst.getClass() + " cannot be cast to " + Number.class);
        }

        default long getLong(java.lang.String key) {
            Instance inst = get(key);
            if (inst instanceof Integer) {
                return ((Integer) inst).intValue();
            } else if (inst instanceof Double) {
                return (long) ((Double) inst).doubleValue();
            } else if (inst instanceof Long) {
                return ((Long) inst).longValue();
            } else if (inst instanceof Number) {
                return ((Number) inst).toJavaObject().longValue();
            } else
                throw new ClassCastException(inst.getClass() + " cannot be cast to " + Number.class);
        }

        default java.lang.String getString(java.lang.String key) {
            return ((String) get(key)).toJavaObject();
        }

        default java.lang.String getNullableString(java.lang.String key) {
            Instance inst = get(key);
            if (inst instanceof Null) {
                return null;
            } else {
                return ((String) inst).toJavaObject();
            }
        }

        default Object getObject(java.lang.String key) {
            return (Object) get(key);
        }

        default Object getNullableObject(java.lang.String key) {
            Instance inst = get(key);
            if (inst instanceof Null) {
                return null;
            } else {
                return (Object) inst;
            }
        }

        default Array getArray(java.lang.String key) {
            return (JSON.Array) get(key);
        }

        default Array getNullableArray(java.lang.String key) {
            Instance inst = get(key);
            if (inst instanceof Null) {
                return null;
            } else {
                return (JSON.Array) inst;
            }
        }
    }

    public interface Array extends Instance<List<java.lang.Object>> {
        int length();

        Instance get(int idx) throws IndexOutOfBoundsException;

        default boolean getBool(int idx) {
            return ((JSON.Bool) get(idx)).booleanValue();
        }

        default int getInt(int idx) {
            Instance inst = get(idx);
            if (inst instanceof Integer) {
                return ((Integer) inst).intValue();
            } else if (inst instanceof Double) {
                return (int) ((Double) inst).doubleValue();
            } else if (inst instanceof Long) {
                return (int) ((Long) inst).longValue();
            } else if (inst instanceof Number) {
                return ((Number) inst).toJavaObject().intValue();
            } else
                throw new ClassCastException(inst.getClass() + " cannot be cast to " + Number.class);
        }

        default double getDouble(int idx) {
            Instance inst = get(idx);
            if (inst instanceof Integer) {
                return ((Integer) inst).intValue();
            } else if (inst instanceof Double) {
                return ((Double) inst).doubleValue();
            } else if (inst instanceof Long) {
                return ((Long) inst).longValue();
            } else if (inst instanceof Number) {
                return ((Number) inst).toJavaObject().doubleValue();
            } else
                throw new ClassCastException(inst.getClass() + " cannot be cast to " + Number.class);
        }

        default long getLong(int idx) {
            Instance inst = get(idx);
            if (inst instanceof Integer) {
                return ((Integer) inst).intValue();
            } else if (inst instanceof Double) {
                return (long) ((Double) inst).doubleValue();
            } else if (inst instanceof Long) {
                return ((Long) inst).longValue();
            } else if (inst instanceof Number) {
                return ((Number) inst).toJavaObject().longValue();
            } else
                throw new ClassCastException(inst.getClass() + " cannot be cast to " + Number.class);
        }

        default java.lang.String getString(int idx) {
            return ((String) get(idx)).toJavaObject();
        }

        default java.lang.String getNullableString(int idx) {
            Instance inst = get(idx);
            if (inst instanceof Null) {
                return null;
            } else {
                return ((String) inst).toJavaObject();
            }
        }

        default Object getObject(int idx) {
            return (Object) get(idx);
        }

        default Object getNullableObject(int idx) {
            Instance inst = get(idx);
            if (inst instanceof Null) {
                return null;
            } else {
                return (Object) inst;
            }
        }

        default Array getArray(int idx) {
            return (JSON.Array) get(idx);
        }

        default Array getNullableArray(int idx) {
            Instance inst = get(idx);
            if (inst instanceof Null) {
                return null;
            } else {
                return (JSON.Array) inst;
            }
        }
    }

    public interface String extends Instance<java.lang.String> {
        static java.lang.String stringify(java.lang.String s) {
            StringBuilder sb = new StringBuilder();
            sb.append("\"");
            char[] chars = s.toCharArray();
            for (char c : chars) {
                if (31 < c && c < 127) { // printable characters
                    switch (c) {
                        case '\"':
                            sb.append("\\\"");
                            break;
                        case '\\':
                            sb.append("\\\\");
                            break;
                        // case '/':
                        //     sb.append("\\/");
                        //     break;
                        default:
                            sb.append(c);
                            break;
                    }
                } else if (c < 128) {
                    switch (c) {
                        case '\b':
                            sb.append("\\b");
                            break;
                        case '\f':
                            sb.append("\\f");
                            break;
                        case '\n':
                            sb.append("\\n");
                            break;
                        case '\r':
                            sb.append("\\r");
                            break;
                        case '\t':
                            sb.append("\\t");
                            break;
                        default:
                            java.lang.String foo = java.lang.Integer.toHexString(c);
                            if (foo.length() < 2) {
                                sb.append("\\u000").append(foo);
                            } else {
                                sb.append("\\u00").append(foo);
                            }
                            break;
                    }
                } else {
                    java.lang.String foo = java.lang.Integer.toHexString(c);
                    if (foo.length() < 3) {
                        sb.append("\\u00").append(foo);
                    } else if (foo.length() < 4) {
                        sb.append("\\u0").append(foo);
                    } else {
                        sb.append("\\u").append(foo);
                    }
                }
            }
            sb.append("\"");
            return sb.toString();
        }
    }

    public interface Number<T extends java.lang.Number> extends Instance<T> {
        @Override
        T toJavaObject();
    }

    public interface Integer extends Number<java.lang.Integer> {
        int intValue();
    }

    public interface Long extends Number<java.lang.Long> {
        long longValue();
    }

    public interface Double extends Number<java.lang.Double> {
        double doubleValue();
    }

    public interface Exp extends Double {
        double base();

        int exponent();
    }

    public interface Bool extends Instance<Boolean> {
        boolean booleanValue();

        @Override
        Boolean toJavaObject();
    }

    public interface Null extends Instance<java.lang.Object> {
        @Override
        default Object toJavaObject() {
            return null;
        }
    }
}
