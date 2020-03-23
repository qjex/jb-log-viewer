package xyz.qjex.test.lw.api

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import xyz.qjex.test.lw.service.FilesService

private const val FILE_NAME_PARAM = "fileName"
private const val LINE_PARAM = "line"
private const val FILES_PARAM = "files"

@Controller
class LogController
@Autowired constructor(private val filesService: FilesService) {

    @GetMapping("/log/{$FILE_NAME_PARAM}")
    fun getLogPage(@PathVariable(FILE_NAME_PARAM) fileName: String, @RequestParam(LINE_PARAM) line: Int?, model: Model): String {
        with(model) {
            addAttribute(FILE_NAME_PARAM, fileName)
            line?.let { addAttribute(LINE_PARAM, it) }
        }
        return "log"
    }

    @GetMapping("/log")
    fun getLogDefaultPage(): String {
        return "redirect:/logs"
    }

    @GetMapping("/logs")
    fun getLogs(model: Model): String {
        model.addAttribute(FILES_PARAM, filesService.getFiles())
        return "logs"
    }
}