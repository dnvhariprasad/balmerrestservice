package com.balmerlawrie.balmerrestservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.JdbcTemplateAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@ComponentScan({ "com.balmerlawrie.balmerrestservice"})
@SpringBootApplication
public class BalmerrestserviceApplication  extends CommandRunnerImpl{

	public static void main(String[] args) {
		SpringApplication.run(BalmerrestserviceApplication.class, args);
	}

}
