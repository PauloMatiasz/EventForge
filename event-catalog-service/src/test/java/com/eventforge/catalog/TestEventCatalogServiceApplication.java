package com.eventforge.catalog;

import org.springframework.boot.SpringApplication;

public class TestEventCatalogServiceApplication {

	public static void main(String[] args) {
		SpringApplication.from(EventCatalogServiceApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
