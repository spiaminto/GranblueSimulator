package com.gbf.granblue_simulator.web.config;

import com.gbf.granblue_simulator.web.interceptor.HttpLogInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private String resourceRequestUrl = "/static/**"; // 브라우저에서 이미지 요청 url
    private String resourceLocation = "classpath:/static/"; // static 로컬 요청 경로

    /**
     * 브라우저에서 (로컬)이미지 요청시 url 을 로컬 요청으로 변경
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(resourceRequestUrl)
                .addResourceLocations(resourceLocation);
    }

    /**
     * 인터셉터 등록
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {

        registry.addInterceptor(new HttpLogInterceptor())
                .order(1)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/static/**",
                        "/gbf/**",
                        "/assets/**",
                        "/js/**",
//                        "/css/**",
                        "https://maxcdn.bootstrapcdn.com/**"
                );
    }

}
