package com.gbf.granblue_simulator.web.config;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * LocalDateTime 에 대한 GSON 직렬화 어댑터, null 처리는 config 등록시 nullSafe() 옵션 사용
 */
public class GsonLocalDateTimeAdapter extends TypeAdapter<LocalDateTime> {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    @Override
    public void write(JsonWriter jsonWriter, LocalDateTime localDateTime) throws IOException {
        jsonWriter.value(localDateTime.format(formatter));
    }

    @Override
    public LocalDateTime read(JsonReader jsonReader) throws IOException {
        return LocalDateTime.parse(jsonReader.nextString(), formatter);
    }
}

