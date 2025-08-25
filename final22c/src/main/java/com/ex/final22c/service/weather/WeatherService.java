package com.ex.final22c.service.weather;

import lombok.RequiredArgsConstructor;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;

@Service

public class WeatherService {
    private final WebClient wxWebClient;
    
    public WeatherService(@Qualifier("wxWebClient") WebClient wxWebClient) {
        this.wxWebClient = wxWebClient;
    }
    
    public record WxNow(double tempC, int humidity, double precipitation, int cloud, String condition) {}

    public WxNow fetchNow(double lat, double lon){
        String uri = UriComponentsBuilder.fromPath("/forecast")
            .queryParam("latitude", lat)
            .queryParam("longitude", lon)
            .queryParam("current","temperature_2m,relative_humidity_2m,precipitation,rain,cloud_cover")
            .queryParam("timezone","Asia/Seoul")
            .build().toUriString();

        Map resp = wxWebClient.get().uri(uri).retrieve().bodyToMono(Map.class).block();
        Map current = (Map) resp.get("current");

        double t  = asD(current.get("temperature_2m"));
        int hum   = (int)Math.round(asD(current.getOrDefault("relative_humidity_2m",0)));
        double pr = asD(current.getOrDefault("precipitation",0));
        int cc    = (int)Math.round(asD(current.getOrDefault("cloud_cover",0)));

        String cond = classify(t, hum, pr, cc);
        return new WxNow(t, hum, pr, cc, cond);
    }

    private static double asD(Object o){ return o==null?0:(o instanceof Number n?n.doubleValue():Double.parseDouble(String.valueOf(o))); }

    /** 간단 분류 룰 */
    private static String classify(double tC, int humidity, double precip, int cloud){
        if (precip > 0.1) return "RAIN";
        if (tC >= 26)     return "HOT";
        if (tC <= 10)     return "COLD";
        if (humidity >= 75) return "HUMID";
        if (cloud >= 60)  return "CLOUDY";
        return "MILD";
    }
}
