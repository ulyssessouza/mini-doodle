package com.doodle.doodlecodingchallenge.common;

import java.util.Locale;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.doodle.doodlecodingchallenge.slot.SlotStatus;

@Configuration
public class EnumParamConversionConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new Converter<String, SlotStatus>() {
            @Override
            public SlotStatus convert(String source) {
                return SlotStatus.valueOf(source.toUpperCase(Locale.ROOT));
            }
        });
    }
}
