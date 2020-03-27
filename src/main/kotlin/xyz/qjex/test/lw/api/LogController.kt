package xyz.qjex.test.lw.api

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import xyz.qjex.test.lw.l10n.FILE_DOES_NOT_EXISTS
import xyz.qjex.test.lw.service.FilesService

private const val FILE_NAME_PARAM = "fileName"
private const val LINE_PARAM = "line"
private const val FILES_PARAM = "files"
private const val ERROR_PARAM = "error"

@Controller
class LogController
@Autowired constructor(private val filesService: FilesService) {

    @GetMapping("/log/{$FILE_NAME_PARAM}")
    fun getLogPage(@PathVariable(FILE_NAME_PARAM) fileName: String, @RequestParam(LINE_PARAM) line: Int?, model: Model): String {
        with(model) {
            if (filesService.validFileName(fileName)) {
                addAttribute(FILE_NAME_PARAM, fileName)
                line?.let { addAttribute(LINE_PARAM, it) }
            } else {
                addAttribute(ERROR_PARAM, FILE_DOES_NOT_EXISTS)
            }
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