package com.ex.final22c.controller.api;

import com.ex.final22c.service.weather.WeatherService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/weather")
public class WeatherApiController {
    private final WeatherService weather;

    @GetMapping("/now")
    public Map<String,Object> now(@RequestParam("lat") double lat, @RequestParam("lon") double lon){
        var wx = weather.fetchNow(lat, lon);
        return Map.of(
            "tempC", wx.tempC(),
            "humidity", wx.humidity(),
            "precipitation", wx.precipitation(),
            "cloud", wx.cloud(),
            "condition", wx.condition()
        );
    }
}
