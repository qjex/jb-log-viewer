package xyz.qjex.test.lw.config

import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.web.servlet.error.ErrorMvcAutoConfiguration
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@EnableAutoConfiguration(exclude = [ErrorMvcAutoConfiguration::class])
class MvcConfig : WebMvcConfigurer {

    override fun addViewControllers(registry: ViewControllerRegistry): Unit = with(registry) {
        addViewController("/logs").setViewName("logs")
        addViewController("/login").setViewName("login")
        addRedirectViewController("/", "/logs")
    }
}