package xyz.qjex.test.lw.config

import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class MvcConfig : WebMvcConfigurer {

    override fun addViewControllers(registry: ViewControllerRegistry): Unit = with(registry) {
        addViewController("/logs").setViewName("logs")
        addViewController("/login").setViewName("login")
        addRedirectViewController("/", "/logs")
    }
}