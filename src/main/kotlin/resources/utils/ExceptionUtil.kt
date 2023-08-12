package resources.utils

import java.io.PrintWriter
import java.io.StringWriter

class ExceptionUtil {
    companion object {
        fun getStackTrace(e: Exception): String {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            e.printStackTrace(pw)
            pw.flush()
            return sw.toString()
        }
    }
}