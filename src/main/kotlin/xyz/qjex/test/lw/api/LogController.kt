package xyz.qjex.test.lw.api

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable

private const val FILE_NAME_VAR = "fileName"
private const val LINE_VAR = "line"

@Controller
class LogController {

    @GetMapping("log/{$FILE_NAME_VAR}/{$LINE_VAR}")
    fun getLogPage(@PathVariable(FILE_NAME_VAR) fileName: String, @PathVariable(LINE_VAR) line: Int?, model: Model) {

    }
}