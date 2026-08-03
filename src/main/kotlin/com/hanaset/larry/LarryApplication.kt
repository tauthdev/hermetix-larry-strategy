package com.hanaset.larry

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class LarryApplication

fun main(args: Array<String>) {
    runApplication<LarryApplication>(*args)
}
