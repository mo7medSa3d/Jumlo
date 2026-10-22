import com.jumlo.app.printing.PrintDraftPolicy

fun main() {
    check(PrintDraftPolicy.parse("marginLeft", "١٢٫٥") == 12.5)
    check(PrintDraftPolicy.parse("marginTop", "۱۲,۵") == 12.5)
    check(PrintDraftPolicy.parse("customWidth", "10") == null)
    check(PrintDraftPolicy.parse("customWidth", "210") == 210.0)
    check(PrintDraftPolicy.parse("imageDpi", "1201") == null)
    check(PrintDraftPolicy.parse("imageDpi", "300") == 300.0)
    check(PrintDraftPolicy.parse("imageDpi", "300.5") == null)
    check(PrintDraftPolicy.parse("marginRight", "--") == null)
    check(PrintDraftPolicy.parse("marginRight", "Infinity") == null)
    check(PrintDraftPolicy.parse("other", "10") == null)
    check(PrintDraftPolicy.errors(mapOf("marginLeft" to "??", "imageDpi" to "300")) == setOf("marginLeft"))
    println("PrintDraftPolicy: 11/11 passed")
}
