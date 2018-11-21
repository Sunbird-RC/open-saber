package io.opensaber.registry.app;

import io.opensaber.registry.interceptor.handler.APIMessage;
import io.opensaber.registry.interceptor.handler.RequestWrapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.context.request.RequestContextListener;

@SpringBootApplication
@ComponentScan(basePackages = { "io.opensaber.registry", "io.opensaber.registry.interceptor.handler"})
public class OpenSaberApplication {
	public static void main(String[] args) {
		ApplicationContext context = SpringApplication.run(OpenSaberApplication.class, args);
		System.out.println("Contains A  "+ context.containsBeanDefinition(APIMessage.class.toString()));
		System.out.println("Contains bean " + context.containsBean("apiMessage"));

		System.out.println("Contains A  "+ context.containsBeanDefinition(RequestWrapper.class.toString()));
		System.out.println("Contains bean " + context.containsBean("requestWrapper"));
	}
}
