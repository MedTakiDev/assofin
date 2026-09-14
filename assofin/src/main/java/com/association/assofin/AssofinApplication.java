package com.association.assofin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AssofinApplication {

	public static void main(String[] args) {
		SpringApplication.run(AssofinApplication.class, args);
	}

}
