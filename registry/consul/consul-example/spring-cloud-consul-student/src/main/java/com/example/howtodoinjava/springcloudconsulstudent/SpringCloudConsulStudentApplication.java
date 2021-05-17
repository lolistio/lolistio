package com.example.howtodoinjava.springcloudconsulstudent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class SpringCloudConsulStudentApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringCloudConsulStudentApplication.class, args);
	}
}
