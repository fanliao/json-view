package com.monitorjbl.json;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

public class JsonResultSerializer extends JsonSerializer<JsonResult> {

  @Override
  public void serialize(JsonResult result, JsonGenerator jgen, SerializerProvider serializers) throws IOException, JsonProcessingException {
    new Writer(jgen, result).write(null, result.getValue());
  }

  private static class Writer {
    Stack<String> path = new Stack<>();
    String currentPath = "";
    Match currentMatch = null;

    JsonGenerator jgen;
    JsonResult result;

    public Writer(JsonGenerator jgen, JsonResult result) {
      this.jgen = jgen;
      this.result = result;
    }

    boolean writePrimitive(Object obj) throws IOException {
      if (obj instanceof String) {
        jgen.writeString((String) obj);
      } else if (Integer.class.isInstance(obj)) {
        jgen.writeNumber((Integer) obj);
      } else if (Double.class.isInstance(obj)) {
        jgen.writeNumber((Double) obj);
      } else if (Float.class.isInstance(obj)) {
        jgen.writeNumber((Float) obj);
      } else if (Boolean.class.isInstance(obj)) {
        jgen.writeBoolean((Boolean) obj);
      } else {
        return false;
      }
      return true;
    }

    @SuppressWarnings("unchecked")
    boolean writeList(Object obj) throws IOException {
      if (obj instanceof List || obj instanceof Set || obj.getClass().isArray()) {
        Iterable<Object> iter;
        if (obj.getClass().isArray()) {
          iter = Arrays.asList((Object[]) obj);
        } else {
          iter = (Iterable<Object>) obj;
        }

        jgen.writeStartArray();
        for (Object o : iter) {
          write(null, o);
        }
        jgen.writeEndArray();
      } else {
        return false;
      }
      return true;
    }

    boolean writeMap(Object obj) throws IOException {
      if (obj instanceof Map) {
        jgen.writeObject(obj);
      } else {
        return false;
      }
      return true;
    }

    void writeObject(Object obj) throws IOException {
      jgen.writeStartObject();

      Class cls = obj.getClass();
      while (!cls.equals(Object.class)) {
        Field[] fields = cls.getDeclaredFields();
        for (Field field : fields) {
          try {
            field.setAccessible(true);
            Object val = field.get(obj);

            if (val != null && fieldAllowed(field)) {
              String name = field.getName();
              jgen.writeFieldName(name);
              write(name, val);
            }
          } catch (IllegalArgumentException | IllegalAccessException e) {
            e.printStackTrace();
          }
        }
        cls = cls.getSuperclass();
      }

      jgen.writeEndObject();
    }

    boolean fieldAllowed(Field field) {
      String name = field.getName();
      String prefix = currentPath.length() > 0 ? currentPath + "." : "";

      //if there is a match specifically for this class, use it
      Match match = result.getMatch(field.getDeclaringClass());
      if (match == null) {
        match = currentMatch;
      }

      if (match != null) {
        //if there is a match, respect it
        currentMatch = match;
        return (match.getIncludes().contains(prefix + name) || !annotatedWithIgnore(field)) && !match.getExcludes().contains(prefix + name);
      } else {
        //else, respect JsonIgnore only
        return !annotatedWithIgnore(field);
      }
    }

    //TODO: respect class inheritance
    boolean annotatedWithIgnore(Field f) {
      JsonIgnore jsonIgnore = f.getAnnotation(JsonIgnore.class);
      JsonIgnoreProperties ignoreProperties = f.getDeclaringClass().getAnnotation(JsonIgnoreProperties.class);
      return (jsonIgnore != null && jsonIgnore.value()) ||
          (ignoreProperties != null && Arrays.asList(ignoreProperties.value()).contains(f.getName()));
    }

    void write(String fieldName, Object value) throws IOException {
      if (fieldName != null) {
        path.push(fieldName);
        updateCurrentPath();
      }

      //try to handle all primitives before treating this as json object
      if (value != null && !writePrimitive(value) && !writeList(value) && !writeMap(value)) {
        writeObject(value);
      }

      if (fieldName != null) {
        path.pop();
        updateCurrentPath();
      }
    }

    void updateCurrentPath() {
      StringBuilder builder = new StringBuilder();
      for (String s : path) {
        builder.append(".");
        builder.append(s);
      }
      currentPath = builder.length() > 0 ? builder.toString().substring(1) : "";
    }
  }
}